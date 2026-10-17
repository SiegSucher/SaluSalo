-- Salu-Salo: push notifications (PROJECT_STATUS.md §35).
--
-- Every new alert (a row in notifications) is also sent to the phones of its
-- account as a phone notification: the trigger below calls the Edge Function
-- send-push through pg_net, and send-push sends it with Firebase Cloud
-- Messaging. The app saves each phone's FCM token with register_my_push_token().
--
-- Run it once in the SQL Editor; the app build of §35 can be installed before
-- or after (until then its token call fails quietly; older builds never
-- register a token, so they get no pushes). Until the developer adds the two
-- Vault secrets (supabase/README.md, "Push notifications"), the trigger sends
-- nothing. A push problem can never make an alert, a message or a reservation
-- fail. Check: supabase/tests/push_notifications_live_safety_check.sql.

create extension if not exists pg_net with schema extensions;

-- ---------------------------------------------------------------------------
-- 1. Push tokens: one row per phone (its FCM token), owned by the account
--    signed in there. No client may read or change them.
-- ---------------------------------------------------------------------------

create table if not exists public.push_tokens (
    token text primary key check (length(token) between 1 and 4096 and token ~ '^[A-Za-z0-9_:.-]+$'),
    user_id uuid not null references public.users(id) on delete cascade,
    updated_at timestamptz not null default now()
);
create index if not exists push_tokens_user_idx on public.push_tokens(user_id, updated_at desc);

alter table public.push_tokens enable row level security;
revoke all on public.push_tokens from public;
revoke all on public.push_tokens from anon;
revoke all on public.push_tokens from authenticated;

-- ---------------------------------------------------------------------------
-- 2. The app saves this phone's token for the signed-in account (the caller
--    from the token only). A token saved for another account moves to the
--    caller: one phone, a new account signed in there. Each account keeps its
--    newest 5 phones.
-- ---------------------------------------------------------------------------

create or replace function public.register_my_push_token(p_token text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile uuid;
    v_token text := btrim(coalesce(p_token, ''));
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;
    if length(v_token) not between 1 and 4096 or v_token !~ '^[A-Za-z0-9_:.-]+$' then
        raise exception 'Invalid push token.';
    end if;

    insert into public.push_tokens(token, user_id, updated_at)
    values (v_token, v_profile, now())
    on conflict (token) do update set user_id = excluded.user_id, updated_at = excluded.updated_at;

    delete from public.push_tokens
    where user_id = v_profile and token not in (
        select token from public.push_tokens
        where user_id = v_profile
        order by updated_at desc
        limit 5);
end; $$;
revoke all on function public.register_my_push_token(text) from public;
revoke all on function public.register_my_push_token(text) from anon;
grant execute on function public.register_my_push_token(text) to authenticated;

-- ---------------------------------------------------------------------------
-- 3. Each new alert goes to send-push with the account's Firebase UID and its
--    tokens used in the last 60 days (newest 5). pg_net sends the request only
--    after the alert's transaction commits. The function's URL and the shared
--    secret come from Vault, added by hand (never in this repo); either one
--    missing means no push. Any error here is only a warning with its code.
-- ---------------------------------------------------------------------------

create or replace function public.push_new_notification()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_url text;
    v_secret text;
    v_uid text;
    v_tokens text[];
begin
    begin
        select decrypted_secret into v_url from vault.decrypted_secrets where name = 'push_function_url';
        select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'push_webhook_secret';
        if v_url is null or v_secret is null then
            return null;
        end if;

        select array_agg(t.token order by t.updated_at desc) into v_tokens
        from (
            select token, updated_at from public.push_tokens
            where user_id = new.user_id and updated_at > now() - interval '60 days'
            order by updated_at desc
            limit 5
        ) t;
        if v_tokens is null then
            return null;
        end if;

        select firebase_uid into v_uid from public.users where id = new.user_id;
        if v_uid is null then
            return null;
        end if;

        -- ponytail: one request per alert, so an announcement makes one per account; batch them if the user base grows large.
        perform net.http_post(
            url := v_url,
            body := jsonb_build_object(
                'uid', v_uid,
                'tokens', to_jsonb(v_tokens),
                'alert', jsonb_build_object(
                    'id', new.id,
                    'type', new.type,
                    'title', new.title,
                    'body', new.body,
                    'conversation_id', new.conversation_id,
                    'reservation_id', new.reservation_id,
                    'food_post_id', new.food_post_id)),
            headers := jsonb_build_object('Content-Type', 'application/json', 'x-push-secret', v_secret),
            timeout_milliseconds := 10000);
    exception when others then
        raise warning 'push_new_notification: %', sqlstate;
    end;
    return null;
end; $$;
revoke all on function public.push_new_notification() from public;
revoke all on function public.push_new_notification() from anon;
revoke all on function public.push_new_notification() from authenticated;

drop trigger if exists notifications_push on public.notifications;
create trigger notifications_push
after insert on public.notifications
for each row execute function public.push_new_notification();
