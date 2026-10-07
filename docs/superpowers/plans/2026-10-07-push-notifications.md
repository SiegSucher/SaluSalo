# Push Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every in-app alert also pops up as an Android notification (app closed, in the background or open, except the open chat), a tap opens the alert's own place, and accounts stay signed in until Log out ("Keep me signed in" removed).

**Architecture:** An AFTER INSERT trigger on `notifications` posts each new alert, its account's Firebase UID and push tokens to a new Edge Function `send-push` through `pg_net`; the function sends FCM HTTP v1 data messages. The app registers its FCM token through a new RPC, shows each pushed alert itself from a `FirebaseMessagingService`, and routes a tapped notification through the same `openAlert()` the Alerts page uses.

**Tech Stack:** Kotlin + Jetpack Compose, Firebase Cloud Messaging (`firebase-messaging` from the existing Firebase BoM 34.19.0), supabase-kt 3.5 (PostgREST RPC), Supabase Postgres (`pg_net`, Vault), a Deno Edge Function with no dependencies.

**Spec:** `docs/superpowers/specs/2026-10-07-push-notifications-design.md`

## Global Constraints

- Branch `claude/push-notifications` (from `claude/portrait-only` at `387e0bb`). Commit after each task; never push or merge without the developer asking.
- Identity comes only from the token: `FirebaseAuth.currentUser` in Kotlin, `is_salusalo_firebase_jwt()` / `current_salusalo_profile_id()` in SQL. Error texts: `'Not authenticated'`, `'Profile not found'`, `'Invalid push token.'`.
- Every new function in `public`: `revoke all … from public`, `revoke all … from anon`, then `grant execute … to authenticated` (or nothing, for the trigger function). Clients get **no** privileges on `push_tokens`.
- The migration is a **new** file, `supabase/migrations/20261016_push_notifications.sql`, run by hand in the SQL Editor; note it in `supabase/README.md` and tell the developer.
- Secrets: `FIREBASE_PUSH_KEY` and `PUSH_WEBHOOK_SECRET` (Edge Function secrets), Vault `push_function_url` and `push_webhook_secret`. Never in the app, the repo, the docs, a chat or a log.
- `send-push` logs only counts and HTTP statuses: never a token, the alert's text, the key, an access token, Google's error body or more than 6 characters of a UID.
- FCM message: data only; `android: {priority: "HIGH", ttl: "86400s"}`; `body` at most 300 characters (code points).
- Channel ID `alerts`, name "Alerts", importance HIGH.
- Sound: the pop-up's channel sound; silent when the phone's "Notification sound" switch (`notificationSoundOn()`) is off. The in-app `AlertSoundPlayer` plays only while `phoneNotificationsOn()` is false.
- Never log or show an exception's own message (it can hold URLs); quiet failures for token registration.
- Build on Windows (PowerShell): `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat <tasks>`. The **first** build after adding `firebase-messaging` must run **without** `--offline` (it downloads the library); later ones may use `--offline`.
- Deno is not on PATH: install it into the session's scratchpad (`npm install --prefix "<scratchpad>/deno" deno@2.9.6`) and run `"<scratchpad>/deno/node_modules/.bin/deno"`; never into the repo.
- Make minimal, targeted changes in the surrounding style; no other new library.

## Review Focus

- **A tap on a pop-up that cold-starts the app:** the tap must wait through the loading screen and account setup, then open its chat, reservation or announcement exactly once (not repeated by Android restoring the app). Pinned by phone-test step 3 (Task 7); the "consumed once" rule is `onPushTapHandled()` before routing (Task 4) and `savedInstanceState == null` in `onCreate`.
- **A push reaching a phone where its account isn't signed in** (signed out, or another account signed in since, or `deleteToken()` failed offline): nothing may show, and an old pop-up tapped afterwards only opens the app. Pinned by `PushRulesTest` (Task 3) and phone-test step 8.
- **The push side broken or not set up** (no Vault secrets, function down, key missing): alerts, messages and reservations must still work. Pinned by live safety check 10 (Task 1) and the Deno setup-error tests (Task 2).
- **Phone notifications turned off** (permission denied, app or channel off): the in-app sound must still play while the app is open, and Profile → Notifications must say so with Turn on. Pinned by phone-test step 7 (Task 7).
- **Long or emoji-heavy alert text:** cut to 300 characters without breaking an emoji. Pinned by the Deno test "a long text is cut…" (Task 2).

---

## File Structure

| File | Task | Responsibility |
|---|---|---|
| `supabase/migrations/20261016_push_notifications.sql` (new) | 1 | `pg_net`, `push_tokens`, `register_my_push_token`, trigger `notifications_push` |
| `supabase/tests/push_notifications_live_safety_check.sql` (new) | 1 | 10 checks, always ends with an error that rolls back |
| `supabase/functions/send-push/index.ts` (new) | 2 | Checks the shared secret and the body, sends FCM v1 messages |
| `supabase/functions/tests/send-push-test.ts` (new) | 2 | Deno tests with a fake Google |
| `supabase/README.md` | 2 | One-time setup: key, secrets, Vault, deploy, migration |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | 3 | `firebase-messaging` |
| `app/src/main/AndroidManifest.xml` | 3 | `POST_NOTIFICATIONS`, `PushService` |
| `app/src/main/java/com/example/salusalo/PushNotifications.kt` (new) | 3, 4, 5 | `PushTap`, `pushTapFrom`, `shouldShowPush`, `AlertPresence`, `showAlertNotification`, `PushService`; then `phoneNotificationsOn`, `cancelChatNotification`; then the permission flag |
| `model/AppNotification.kt` | 3 | `notificationTypeOf()` (shared with the serializer) |
| `data/NotificationRepository.kt`, `viewmodel/NotificationViewModel.kt` | 3, 4 | `registerPushToken()`; register at start; `markRead` for alerts not loaded yet |
| `MainActivity.kt` | 3, 4, 5, 6 | `deleteToken()` on sign-out; tap intent; `openAlert`; presence; sound; permission; "Keep me signed in" removed |
| `AlertScreen.kt` | 4 | `onOpenAlert` instead of four callbacks; opens a requested announcement |
| `ProfileSections.kt` | 5 | "Phone notifications are off" with Turn on; new caption |
| `AlertSound.kt` | 6 | Comment only |
| `app/src/test/java/com/example/salusalo/PushRulesTest.kt` (new) | 3 | Pure rules |
| `docs/PROJECT_STATUS.md`, `CLAUDE.md` | 7 | §35, rules, counts |

---

### Task 1: Database: push tokens, the RPC and the trigger

**Files:**
- Create: `supabase/migrations/20261016_push_notifications.sql`
- Create: `supabase/tests/push_notifications_live_safety_check.sql`

**Interfaces:**
- Produces: RPC `register_my_push_token(p_token text) returns void` (Task 3 calls it as `postgrest.rpc("register_my_push_token", {"p_token": token})`). The trigger posts this JSON to `send-push` (Task 2 reads it): `{"uid": text, "tokens": [text, …≤5], "alert": {"id", "type", "title", "body", "conversation_id", "reservation_id", "food_post_id"}}` (ids are uuid strings or JSON null) with header `x-push-secret`.

- [ ] **Step 1: Write the live safety check first** (it is this task's test; it fails before the migration because `register_my_push_token` doesn't exist).

Create `supabase/tests/push_notifications_live_safety_check.sql`:

```sql
-- Salu-Salo push notifications: safety check for the live database
-- (PROJECT_STATUS.md §35).
--
-- Run it in the Supabase dashboard after migrations/20261016_push_notifications.sql:
-- SQL Editor -> New query -> paste -> Run. Leave the role on "postgres".
--
-- It pretends to be the two newest accounts (A and B), registers made-up push
-- tokens and adds one test alert for A. The "Sanity" check must succeed: it
-- proves that the pretend sign-in works. A check that cannot run reports SKIP.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did, so no token or alert is kept and no phone gets a push (pg_net
-- sends only requests whose transaction committed). The error text is the
-- report; it starts with "SAFETY CHECK FINISHED". Any other error means the
-- check itself did not run.

do $$
declare
  v_a        public.users;
  v_b        public.users;
  c_iss      text := 'https://securetoken.google.com/salusalo';
  c_a        text;
  c_b        text;
  c_nobody   text;
  v_tok      text := 'safety-check:token-' || replace(gen_random_uuid()::text, '-', '');
  v_owner    uuid;
  v_bad      text;
  v_alert    uuid;
  v_max      bigint;
  v_vault    boolean;
  n          bigint;
  v_label    text;
  v_ok       boolean;
  v_note     text;
  a_label    text[] := '{}';
  a_ok       boolean[] := '{}';
  a_note     text[] := '{}';
  v_report   text := '';
  v_pass     integer := 0;
  v_fail     integer := 0;
  v_skip     integer := 0;
begin
  select * into v_a from public.users order by created_at desc limit 1;
  select * into v_b from public.users where id is distinct from v_a.id order by created_at desc limit 1;
  c_a := json_build_object('sub', v_a.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_b := json_build_object('sub', v_b.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_nobody := json_build_object('sub', 'safety-check-no-profile', 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;

  -- 1
  v_label := 'Sanity: account A registers a push token';
  v_ok := null; v_note := 'needs an account';
  if v_a.id is not null then
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.register_my_push_token(v_tok);
      perform set_config('role', 'postgres', true);
      select user_id into v_owner from public.push_tokens where token = v_tok;
      v_ok := v_owner is not distinct from v_a.id;
      v_note := case when v_ok then 'saved for A' else 'not saved for A' end;
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2
  v_label := 'The same token registered by account B moves to B (one phone, another account)';
  v_ok := null; v_note := 'needs two accounts';
  if v_a.id is not null and v_b.id is not null then
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      perform public.register_my_push_token(v_tok);
      perform set_config('role', 'postgres', true);
      select user_id into v_owner from public.push_tokens where token = v_tok;
      select count(*) into n from public.push_tokens where token = v_tok;
      v_ok := v_owner is not distinct from v_b.id and n = 1;
      v_note := format('owner is %s, %s row(s)', case when v_owner = v_b.id then 'B' else 'not B' end, n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 3
  v_label := 'An account keeps at most 5 tokens';
  v_ok := null; v_note := 'needs an account';
  if v_a.id is not null then
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      for i in 1 .. 7 loop
        perform public.register_my_push_token(v_tok || '-' || i);
      end loop;
      perform set_config('role', 'postgres', true);
      select count(*) into n from public.push_tokens where user_id = v_a.id;
      v_ok := n = 5;
      v_note := format('%s token(s) kept', n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 4
  v_label := 'Refused without a Salu-Salo sign-in';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', '{}', true);
    perform set_config('role', 'authenticated', true);
    perform public.register_my_push_token(v_tok || '-anon');
    perform set_config('role', 'postgres', true);
    v_note := 'it was allowed';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'Not authenticated';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5
  v_label := 'Refused for a sign-in without a profile';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_nobody, true);
    perform set_config('role', 'authenticated', true);
    perform public.register_my_push_token(v_tok || '-nobody');
    perform set_config('role', 'postgres', true);
    v_note := 'it was allowed';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'Profile not found';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 6
  v_label := 'An empty or malformed token is refused';
  v_ok := null; v_note := 'needs an account';
  if v_a.id is not null then
    v_ok := true; v_note := 'both refused';
    foreach v_bad in array array['', 'bad token!'] loop
      begin
        perform set_config('request.jwt.claims', c_a, true);
        perform set_config('role', 'authenticated', true);
        perform public.register_my_push_token(v_bad);
        perform set_config('role', 'postgres', true);
        v_ok := false; v_note := format('"%s" was allowed', v_bad);
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'Invalid push token.' then v_ok := false; v_note := sqlerrm; end if;
      end;
    end loop;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 7
  v_label := 'push_tokens: no privileges for anon or authenticated';
  v_ok := not has_table_privilege('authenticated', 'public.push_tokens', 'select,insert,update,delete')
    and not has_table_privilege('anon', 'public.push_tokens', 'select,insert,update,delete');
  v_note := case when v_ok then 'as expected' else 'a client has a privilege' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'register_my_push_token for signed-in callers only; the trigger function for no client';
  v_ok := has_function_privilege('authenticated', 'public.register_my_push_token(text)', 'execute')
    and not has_function_privilege('anon', 'public.register_my_push_token(text)', 'execute')
    and not has_function_privilege('authenticated', 'public.push_new_notification()', 'execute')
    and not has_function_privilege('anon', 'public.push_new_notification()', 'execute');
  v_note := case when v_ok then 'as expected' else 'wrong privileges' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9
  v_label := 'The trigger notifications_push is on notifications and enabled';
  select count(*) into n from pg_trigger
  where tgrelid = 'public.notifications'::regclass and tgname = 'notifications_push'
    and tgenabled <> 'D' and not tgisinternal;
  v_ok := n = 1;
  v_note := case when v_ok then 'found' else 'missing or disabled' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 10
  v_label := 'A new alert still saves; one push request is queued only when both Vault secrets exist';
  v_ok := null; v_note := 'needs an account';
  if v_a.id is not null then
    begin
      v_vault := exists (select 1 from vault.decrypted_secrets where name = 'push_function_url')
        and exists (select 1 from vault.decrypted_secrets where name = 'push_webhook_secret');
      -- Only rows added after this point: pg_net's worker may delete older ones meanwhile.
      select coalesce(max(id), 0) into v_max from net.http_request_queue;
      insert into public.notifications(user_id, type, title, body)
      values (v_a.id, 'ANNOUNCEMENT', 'Safety check', 'Safety check alert')
      returning id into v_alert;
      select count(*) into n from net.http_request_queue where id > v_max;
      v_ok := v_alert is not null and n = case when v_vault then 1 else 0 end;
      v_note := format('Vault secrets %s, %s request(s) queued (undone below)',
        case when v_vault then 'present' else 'not added yet' end, n);
    exception when others then
      v_ok := false; v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Report. Raising an error here undoes everything above.
  -- ---------------------------------------------------------------------------

  for i in 1 .. coalesce(array_length(a_label, 1), 0) loop
    if a_ok[i] is null then
      v_skip := v_skip + 1;
    elsif a_ok[i] then
      v_pass := v_pass + 1;
    else
      v_fail := v_fail + 1;
    end if;
    v_report := v_report || format(E'\n%s %s. %s: %s',
      case when a_ok[i] is null then 'SKIP' when a_ok[i] then 'PASS' else 'FAIL' end,
      i, a_label[i], coalesce(a_note[i], '-'));
  end loop;

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nA: %s. B: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_a.name, 'none'), coalesce(v_b.name, 'none'), v_report);
end $$;
```

- [ ] **Step 2: Write the migration.**

Create `supabase/migrations/20261016_push_notifications.sql`:

```sql
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
```

- [ ] **Step 3: Check the SQL locally if a PostgreSQL is available** (optional; Vault and `pg_net` exist only on Supabase).

Run: `where psql` (PowerShell) or `command -v psql` (Bash). If none is found, skip to Step 4 and say so in the report: the live safety check after the developer runs the migration is then the only SQL check.

If `psql` exists: in a throwaway database, apply all migrations in filename order after creating stand-ins first:

```sql
create schema if not exists vault;
create table vault.decrypted_secrets(name text, decrypted_secret text);
create schema if not exists net;
create table net.http_request_queue(id bigserial primary key, url text, body jsonb, headers jsonb);
create function net.http_post(url text, body jsonb, headers jsonb, timeout_milliseconds int)
returns bigint language sql as $$ insert into net.http_request_queue(url, body, headers) values (url, body, headers) returning id $$;
```

(and drop the `create extension pg_net` line from the copy you apply). Then run the safety check; expected: `SAFETY CHECK FINISHED: 10 passed, 0 failed, 0 skipped` (or SKIPs when the database has fewer than two `users` rows). Delete the throwaway database afterwards.

- [ ] **Step 4: Commit**

```bash
git add supabase/migrations/20261016_push_notifications.sql supabase/tests/push_notifications_live_safety_check.sql
git commit -m "Add push tokens, register_my_push_token and the notifications push trigger

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Edge Function `send-push`, its tests and the setup steps

**Files:**
- Create: `supabase/functions/send-push/index.ts`
- Create: `supabase/functions/tests/send-push-test.ts`
- Modify: `supabase/README.md` (append a section)

**Interfaces:**
- Consumes: the trigger's request from Task 1 (body and `x-push-secret` header).
- Produces: FCM data keys the app reads (Task 3): `alert_id`, `type`, `title`, `body`, `uid`, and when present `conversation_id`, `reservation_id`, `food_post_id` (absent keys, never empty strings).

- [ ] **Step 1: Install Deno into the scratchpad (once)**

Run (Bash): `npm install --prefix "<scratchpad>/deno" deno@2.9.6` then `"<scratchpad>/deno/node_modules/.bin/deno" --version`
Expected: `deno 2.9.6`.

- [ ] **Step 2: Write the failing tests**

Create `supabase/functions/tests/send-push-test.ts`:

```ts
// Tests for supabase/functions/send-push, with a locally generated key and a
// fake Google. Run from the repository root:
//   deno test supabase/functions/tests/send-push-test.ts
// Nothing here calls Google or Supabase.

import { assert, assertEquals, assertFalse } from "jsr:@std/assert@1.0.19";
import { type Deps, fcmMessage, handle, KEY_SECRET, sameSecret, WEBHOOK_SECRET } from "../send-push/index.ts";

const NOW = 1_790_000_000;
const RSA = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" };
const WEBHOOK = "test-webhook-secret-0123456789";
const ACCESS_TOKEN = "ya29.test-push-access-token";
const FCM_URL = "https://fcm.googleapis.com/v1/projects/salusalo/messages:send";

const serviceAccountKey = await crypto.subtle.generateKey(
  { ...RSA, modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]) },
  true,
  ["sign", "verify"],
);

// Each test that needs a fresh Google access token uses its own client email:
// the function caches the token per service account.
async function keySecret(clientEmail: string, projectId = "salusalo"): Promise<string> {
  const der = new Uint8Array(await crypto.subtle.exportKey("pkcs8", serviceAccountKey.privateKey));
  let binary = "";
  for (const byte of der) binary += String.fromCharCode(byte);
  const pem = `-----BEGIN PRIVATE KEY-----\n${btoa(binary).replace(/(.{64})/g, "$1\n")}\n-----END PRIVATE KEY-----\n`;
  return JSON.stringify({
    type: "service_account",
    project_id: projectId,
    private_key_id: "abc",
    private_key: pem,
    client_email: clientEmail,
  });
}

const ALERT = {
  id: "alert-1",
  type: "MESSAGE",
  title: "New message from Ana",
  body: "Pandesal: Is it still there?",
  conversation_id: "conv-1",
  reservation_id: null,
  food_post_id: "post-1",
};

function pushBody(overrides: Record<string, unknown> = {}) {
  return { uid: "firebase-uid-1", tokens: ["token-a", "token-b"], alert: ALERT, ...overrides };
}

function decodeBase64Url(part: string): Uint8Array {
  const base64 = part.replace(/-/g, "+").replace(/_/g, "/");
  return Uint8Array.from(atob(base64 + "=".repeat((4 - (base64.length % 4)) % 4)), (c) => c.charCodeAt(0));
}

/** A fake Google: the OAuth token endpoint and FCM's messages:send. */
function fakeGoogle(fcmStatus: (token: string) => number = () => 200, oauthStatus = 200) {
  const oauth: Array<Record<string, unknown>> = [];
  const sent: Array<{ auth: string | null; body: Record<string, any> }> = [];
  const fetchFn = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
    const url = String(input);
    if (url === "https://oauth2.googleapis.com/token") {
      const assertion = new URLSearchParams(String(init?.body)).get("assertion")!;
      const [head, claims, signature] = assertion.split(".");
      const signed = await crypto.subtle.verify(
        RSA,
        serviceAccountKey.publicKey,
        decodeBase64Url(signature),
        new TextEncoder().encode(`${head}.${claims}`),
      );
      oauth.push({ ...JSON.parse(new TextDecoder().decode(decodeBase64Url(claims))), signed });
      if (oauthStatus !== 200) return new Response(`{"error":"google says no: secret-detail"}`, { status: oauthStatus });
      return Response.json({ access_token: ACCESS_TOKEN, expires_in: 3600 });
    }
    if (url === FCM_URL) {
      const body = JSON.parse(String(init?.body));
      sent.push({ auth: new Headers(init?.headers).get("Authorization"), body });
      const status = fcmStatus(body.message.token);
      return new Response(
        status === 200
          ? `{"name":"projects/salusalo/messages/1"}`
          : `{"error":{"status":"UNREGISTERED","message":"secret-detail"}}`,
        { status },
      );
    }
    throw new Error(`unexpected fetch ${url}`);
  };
  return { fetchFn: fetchFn as typeof fetch, oauth, sent };
}

function deps(fetchFn: typeof fetch, secrets: Record<string, string | undefined>): Deps {
  return { fetch: fetchFn, nowSeconds: () => NOW, secret: (name) => secrets[name] };
}

function request(body: unknown, secret: string | null = WEBHOOK, method = "POST"): Request {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (secret !== null) headers["x-push-secret"] = secret;
  return new Request("http://localhost/send-push", {
    method,
    headers,
    body: method === "POST" ? (typeof body === "string" ? body : JSON.stringify(body)) : undefined,
  });
}

/** Runs [action] with console.log and console.error captured. */
async function captureLogs<T>(action: () => Promise<T>): Promise<{ result: T; logs: string }> {
  const lines: string[] = [];
  const { log, error } = console;
  console.log = (...args: unknown[]) => void lines.push(args.join(" "));
  console.error = (...args: unknown[]) => void lines.push(args.join(" "));
  try {
    return { result: await action(), logs: lines.join("\n") };
  } finally {
    console.log = log;
    console.error = error;
  }
}

Deno.test("only POST", async () => {
  const google = fakeGoogle();
  const response = await handle(
    request(null, WEBHOOK, "GET"),
    deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("get@salusalo.iam.gserviceaccount.com") }),
  );
  assertEquals(response.status, 405);
  assertEquals(google.oauth.length + google.sent.length, 0);
});

Deno.test("a missing or wrong secret sends nothing", async () => {
  const google = fakeGoogle();
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("wrong@salusalo.iam.gserviceaccount.com") });
  for (const secret of [null, "", "wrong", WEBHOOK + "x", WEBHOOK.slice(0, -1)]) {
    const { result: response, logs } = await captureLogs(() => handle(request(pushBody(), secret), d));
    assertEquals(response.status, 401, String(secret));
    assertFalse(logs.includes(WEBHOOK));
  }
  assertEquals(google.oauth.length + google.sent.length, 0);
});

Deno.test("without the webhook secret set up, nothing is sent (an empty one never matches)", async () => {
  const google = fakeGoogle();
  const key = await keySecret("nowebhook@salusalo.iam.gserviceaccount.com");
  for (const webhook of [undefined, ""]) {
    const response = await handle(request(pushBody(), ""), deps(google.fetchFn, { [WEBHOOK_SECRET]: webhook, [KEY_SECRET]: key }));
    assertEquals(response.status, 500);
    assertEquals(await response.json(), { error: "Server setup incomplete." });
  }
  assertEquals(google.oauth.length + google.sent.length, 0);
});

Deno.test("a body that isn't a push request is refused", async () => {
  const google = fakeGoogle();
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("body@salusalo.iam.gserviceaccount.com") });
  const bodies: unknown[] = [
    "not json",
    {},
    pushBody({ tokens: [] }),
    pushBody({ tokens: ["a", "b", "c", "d", "e", "f"] }),
    pushBody({ tokens: ["a", 5] }),
    pushBody({ tokens: ["a", ""] }),
    pushBody({ uid: "" }),
    pushBody({ alert: null }),
    pushBody({ alert: { ...ALERT, title: 7 } }),
    pushBody({ alert: { id: "a", type: "MESSAGE", body: "no title" } }),
  ];
  for (const body of bodies) {
    const response = await handle(request(body), d);
    assertEquals(response.status, 400, JSON.stringify(body));
  }
  assertEquals(google.oauth.length + google.sent.length, 0);
});

Deno.test("sends one data message per phone, and logs no token, text or UID", async () => {
  const google = fakeGoogle();
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("send@salusalo.iam.gserviceaccount.com") });
  const { result: response, logs } = await captureLogs(() => handle(request(pushBody()), d));
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { sent: 2, failed: 0 });
  assertEquals(google.oauth.length, 1);
  assertEquals(google.oauth[0].scope, "https://www.googleapis.com/auth/firebase.messaging");
  assertEquals(google.oauth[0].iss, "send@salusalo.iam.gserviceaccount.com");
  assert(google.oauth[0].signed);
  assertEquals(google.sent.map((s) => s.body.message.token), ["token-a", "token-b"]);
  assertEquals(google.sent[0].auth, `Bearer ${ACCESS_TOKEN}`);
  assertEquals(google.sent[0].body, {
    message: {
      token: "token-a",
      data: {
        alert_id: "alert-1",
        type: "MESSAGE",
        title: "New message from Ana",
        body: "Pandesal: Is it still there?",
        uid: "firebase-uid-1",
        conversation_id: "conv-1",
        food_post_id: "post-1",
      },
      android: { priority: "HIGH", ttl: "86400s" },
    },
  });
  for (const secret of ["token-a", "token-b", "Pandesal", "Ana", ACCESS_TOKEN, "firebase-uid-1", WEBHOOK]) {
    assertFalse(logs.includes(secret), secret);
  }
});

Deno.test("one failing phone doesn't stop the others; Google's error body isn't logged", async () => {
  const google = fakeGoogle((token) => (token === "token-a" ? 404 : 200));
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("partial@salusalo.iam.gserviceaccount.com") });
  const { result: response, logs } = await captureLogs(() => handle(request(pushBody()), d));
  assertEquals(response.status, 200);
  assertEquals(await response.json(), { sent: 1, failed: 1 });
  assertEquals(google.sent.length, 2);
  assert(logs.includes("404"));
  assertFalse(logs.includes("secret-detail"));
  assertFalse(logs.includes("UNREGISTERED"));
  assertFalse(logs.includes("token-a"));
});

Deno.test("the access token is reused", async () => {
  const google = fakeGoogle();
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("reuse@salusalo.iam.gserviceaccount.com") });
  await handle(request(pushBody()), d);
  await handle(request(pushBody()), d);
  assertEquals(google.oauth.length, 1);
  assertEquals(google.sent.length, 4);
});

Deno.test("a missing or invalid key is a setup error and sends nothing", async () => {
  const keys = [undefined, "", "not json", await keySecret("other@other.iam.gserviceaccount.com", "other-project")];
  for (const key of keys) {
    const google = fakeGoogle();
    const { result: response, logs } = await captureLogs(() =>
      handle(request(pushBody()), deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: key }))
    );
    assertEquals(response.status, 500);
    assertEquals(await response.json(), { error: "Server setup incomplete." });
    assertEquals(google.sent.length, 0);
    assert(logs.includes(KEY_SECRET));
  }
});

Deno.test("Google refusing the access token: 502, nothing sent, its error body not logged", async () => {
  const google = fakeGoogle(() => 200, 400);
  const d = deps(google.fetchFn, { [WEBHOOK_SECRET]: WEBHOOK, [KEY_SECRET]: await keySecret("refused@salusalo.iam.gserviceaccount.com") });
  const { result: response, logs } = await captureLogs(() => handle(request(pushBody()), d));
  assertEquals(response.status, 502);
  assertEquals(google.sent.length, 0);
  assertFalse(logs.includes("secret-detail"));
});

Deno.test("a long text is cut to 300 characters without breaking an emoji", () => {
  const message = fcmMessage("token-a", { uid: "u", tokens: ["token-a"], alert: { ...ALERT, body: "🍞".repeat(350) } });
  const chars = Array.from(message.message.data.body);
  assertEquals(chars.length, 300);
  assertEquals(chars[299], "…");
  assert(chars.slice(0, 299).every((c) => c === "🍞"));
  const short = fcmMessage("token-a", { uid: "u", tokens: ["token-a"], alert: { ...ALERT, body: "x".repeat(300) } });
  assertEquals(short.message.data.body, "x".repeat(300));
});

Deno.test("alerts without a chat, reservation or listing leave those keys out", () => {
  const message = fcmMessage("t", {
    uid: "u",
    tokens: ["t"],
    alert: { ...ALERT, type: "ANNOUNCEMENT", conversation_id: null, reservation_id: null, food_post_id: null },
  });
  assertEquals(Object.keys(message.message.data).sort(), ["alert_id", "body", "title", "type", "uid"]);
});

Deno.test("sameSecret compares whole strings", () => {
  assert(sameSecret("abc", "abc"));
  assertFalse(sameSecret("abc", "abd"));
  assertFalse(sameSecret("abc", "abcd"));
  assertFalse(sameSecret("abcd", "abc"));
  assertFalse(sameSecret("", "abc"));
});
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `"<scratchpad>/deno/node_modules/.bin/deno" test supabase/functions/tests/send-push-test.ts`
Expected: FAIL: module `../send-push/index.ts` not found.

- [ ] **Step 4: Write the function**

Create `supabase/functions/send-push/index.ts`. The service-account part (`readServiceAccount`, `googleAccessToken`, `base64UrlEncode`) is copied from `set-firebase-role/index.ts` with the FCM scope, because functions deployed from the dashboard editor can't share files:

```ts
// Supabase Edge Function "send-push".
//
// Sends a new Salu-Salo alert to the phones of its account as Firebase Cloud
// Messaging (FCM HTTP v1) data messages; the app shows them as notifications.
// The database's trigger notifications_push calls it through pg_net for every
// new row in notifications, with the alert, the account's Firebase UID and its
// push tokens (at most 5).
//
// - Deploy with JWT verification OFF: the database's call carries no Supabase
//   JWT. A call is accepted only with the header x-push-secret equal to the
//   secret PUSH_WEBHOOK_SECRET (the same value is in Vault for the trigger).
// - It uses a service account whose only role is a custom one with the
//   permission cloudmessaging.messages.create. Its JSON key is the Edge
//   Function secret FIREBASE_PUSH_KEY.
// - It never logs or returns a token, the alert's text, the key, the Google
//   access token or Google's error bodies; logs hold counts and HTTP statuses.
// - Dead tokens are not deleted here (no database access by design); the
//   trigger sends only tokens used in the last 60 days, at most 5 per account.

const FIREBASE_PROJECT_ID = "salusalo";
const OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
const FCM_SEND_URL = `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`;
const OAUTH_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
export const KEY_SECRET = "FIREBASE_PUSH_KEY";
export const WEBHOOK_SECRET = "PUSH_WEBHOOK_SECRET";
const MAX_TOKENS = 5;
const MAX_BODY_CHARACTERS = 300;

export class SetupError extends Error {}
/** A failed call to Google: only the step and the HTTP status are kept. */
export class GoogleError extends Error {
  constructor(readonly step: string, readonly status: number) {
    super(`${step} failed with HTTP ${status}`);
  }
}

export interface Deps {
  fetch: typeof fetch;
  nowSeconds: () => number;
  /** An Edge Function secret by name, or undefined. */
  secret: (name: string) => string | undefined;
}

const encoder = new TextEncoder();
const RSA = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" };

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** Compares two strings in a time that depends only on their lengths. */
export function sameSecret(given: string, expected: string): boolean {
  const a = encoder.encode(given);
  const b = encoder.encode(expected);
  let difference = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) difference |= (a[i] ?? 0) ^ (b[i] ?? 0);
  return difference === 0;
}

// ---- Service account (copied from set-firebase-role) -----------------------

interface ServiceAccount {
  clientEmail: string;
  privateKey: string;
}

/** The service account from the secret, or SetupError if it is missing or unusable. */
function readServiceAccount(raw: string | undefined): ServiceAccount {
  if (!raw || raw.trim() === "") throw new SetupError();
  let key: Record<string, unknown>;
  try {
    key = JSON.parse(raw);
  } catch {
    throw new SetupError();
  }
  if (
    key?.type !== "service_account" || key.project_id !== FIREBASE_PROJECT_ID ||
    typeof key.client_email !== "string" || typeof key.private_key !== "string"
  ) {
    throw new SetupError();
  }
  return { clientEmail: key.client_email, privateKey: key.private_key };
}

let accessTokenCache: { email: string; token: string; expiresAt: number } | null = null;

async function googleAccessToken(account: ServiceAccount, deps: Deps): Promise<string> {
  const now = deps.nowSeconds();
  if (accessTokenCache && accessTokenCache.email === account.clientEmail &&
    accessTokenCache.expiresAt - 300 > now) {
    return accessTokenCache.token;
  }
  let signingKey: CryptoKey;
  try {
    const pem = account.privateKey.replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
    const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
    signingKey = await crypto.subtle.importKey("pkcs8", der, RSA, false, ["sign"]);
  } catch {
    throw new SetupError();
  }
  const header = base64UrlEncode(encoder.encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claims = base64UrlEncode(encoder.encode(JSON.stringify({
    iss: account.clientEmail,
    scope: OAUTH_SCOPE,
    aud: OAUTH_TOKEN_URL,
    iat: now,
    exp: now + 3600,
  })));
  const signature = new Uint8Array(
    await crypto.subtle.sign(RSA, signingKey, encoder.encode(`${header}.${claims}`)),
  );
  const response = await deps.fetch(OAUTH_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claims}.${base64UrlEncode(signature)}`,
    }),
  });
  if (!response.ok) {
    await response.body?.cancel();
    throw new GoogleError("access token", response.status);
  }
  const body = await response.json() as { access_token?: unknown; expires_in?: unknown };
  if (typeof body.access_token !== "string") throw new GoogleError("access token", response.status);
  const lifetime = typeof body.expires_in === "number" ? body.expires_in : 3600;
  accessTokenCache = { email: account.clientEmail, token: body.access_token, expiresAt: now + lifetime };
  return body.access_token;
}

// ---- The push ---------------------------------------------------------------

export interface PushAlert {
  id: string;
  type: string;
  title: string;
  body: string;
  conversation_id: string | null;
  reservation_id: string | null;
  food_post_id: string | null;
}

export interface PushRequest {
  uid: string;
  tokens: string[];
  alert: PushAlert;
}

/** The trigger's request body, or null when it isn't one. */
function readPushRequest(body: unknown): PushRequest | null {
  if (body === null || typeof body !== "object" || Array.isArray(body)) return null;
  const { uid, tokens, alert } = body as Record<string, unknown>;
  if (typeof uid !== "string" || uid === "") return null;
  if (
    !Array.isArray(tokens) || tokens.length === 0 || tokens.length > MAX_TOKENS ||
    !tokens.every((token) => typeof token === "string" && token !== "")
  ) {
    return null;
  }
  if (alert === null || typeof alert !== "object" || Array.isArray(alert)) return null;
  const fields = alert as Record<string, unknown>;
  for (const name of ["id", "type", "title", "body"]) {
    if (typeof fields[name] !== "string") return null;
  }
  const optional = (name: string) => (typeof fields[name] === "string" ? fields[name] as string : null);
  return {
    uid,
    tokens: tokens as string[],
    alert: {
      id: fields.id as string,
      type: fields.type as string,
      title: fields.title as string,
      body: fields.body as string,
      conversation_id: optional("conversation_id"),
      reservation_id: optional("reservation_id"),
      food_post_id: optional("food_post_id"),
    },
  };
}

/** The text cut to 300 characters (code points, so an emoji is never split). */
function shortText(text: string): string {
  const characters = Array.from(text);
  if (characters.length <= MAX_BODY_CHARACTERS) return text;
  return characters.slice(0, MAX_BODY_CHARACTERS - 1).join("") + "…";
}

/**
 * The FCM v1 message for one phone: data only (the app builds the
 * notification), high priority, kept by FCM for a day at most. IDs the alert
 * doesn't have are left out.
 */
export function fcmMessage(token: string, push: PushRequest) {
  const { alert } = push;
  const data: Record<string, string> = {
    alert_id: alert.id,
    type: alert.type,
    title: alert.title,
    body: shortText(alert.body),
    uid: push.uid,
  };
  if (alert.conversation_id) data.conversation_id = alert.conversation_id;
  if (alert.reservation_id) data.reservation_id = alert.reservation_id;
  if (alert.food_post_id) data.food_post_id = alert.food_post_id;
  return { message: { token, data, android: { priority: "HIGH", ttl: "86400s" } } };
}

// ---- Handler ---------------------------------------------------------------

function reply(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

const SETUP_INCOMPLETE = { error: "Server setup incomplete." };

export async function handle(request: Request, deps: Deps): Promise<Response> {
  if (request.method !== "POST") return reply(405, { error: "Use POST." });

  const expected = deps.secret(WEBHOOK_SECRET);
  if (!expected) {
    console.error(`send-push: the ${WEBHOOK_SECRET} secret is missing`);
    return reply(500, SETUP_INCOMPLETE);
  }
  if (!sameSecret(request.headers.get("x-push-secret") ?? "", expected)) {
    console.log("send-push: refused (secret)");
    return reply(401, { error: "Not allowed." });
  }

  let push: PushRequest | null;
  try {
    push = readPushRequest(await request.json());
  } catch {
    push = null;
  }
  if (!push) {
    console.log("send-push: refused (body)");
    return reply(400, { error: "Not a push request." });
  }

  try {
    const account = readServiceAccount(deps.secret(KEY_SECRET));
    const accessToken = await googleAccessToken(account, deps);
    const pushRequest = push;
    const statuses = await Promise.all(pushRequest.tokens.map(async (token) => {
      try {
        const response = await deps.fetch(FCM_SEND_URL, {
          method: "POST",
          headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
          body: JSON.stringify(fcmMessage(token, pushRequest)),
        });
        // Google's answer is never read, logged or returned.
        await response.body?.cancel();
        return response.status;
      } catch {
        return 0;
      }
    }));
    const sent = statuses.filter((status) => status === 200).length;
    const failed = statuses.length - sent;
    const failures = statuses.filter((status) => status !== 200);
    console.log(`send-push: ${sent} sent, ${failed} failed${failed ? ` (HTTP ${failures.join(", ")})` : ""}`);
    return reply(200, { sent, failed });
  } catch (error) {
    if (error instanceof SetupError) {
      console.error(`send-push: the ${KEY_SECRET} secret is missing or invalid`);
      return reply(500, SETUP_INCOMPLETE);
    }
    if (error instanceof GoogleError) {
      console.error(`send-push: ${error.message}`);
      return reply(502, { error: "Could not send." });
    }
    // Only the error's type: its text could contain request details.
    console.error(`send-push: unexpected ${error instanceof Error ? error.name : typeof error}`);
    return reply(500, { error: "Could not send." });
  }
}

const nowSeconds = () => Math.floor(Date.now() / 1000);

export default {
  fetch: (request: Request) =>
    handle(request, { fetch, nowSeconds, secret: (name) => Deno.env.get(name) }),
};
```

- [ ] **Step 5: Run the tests, type check and lint**

Run (from the repo root):
`"<scratchpad>/deno/node_modules/.bin/deno" test supabase/functions/tests/send-push-test.ts`
Expected: `ok | 12 passed | 0 failed`.
Then: `"<scratchpad>/deno/node_modules/.bin/deno" check supabase/functions/send-push/index.ts` and `"<scratchpad>/deno/node_modules/.bin/deno" lint supabase/functions/send-push/index.ts supabase/functions/tests/send-push-test.ts`
Expected: no errors. Also re-run `deno test supabase/functions/tests/set-firebase-role-test.ts`: 21 passed.

- [ ] **Step 6: Write the setup steps in `supabase/README.md`**

Append this section at the end of `supabase/README.md`:

````markdown
## Push notifications (`send-push`, §35)

Every new alert (a row in `notifications`) is also sent to the phones of its
account: the trigger `notifications_push` (migration
`20261016_push_notifications.sql`) calls the Edge Function `send-push`
through `pg_net`, and `send-push` sends it with Firebase Cloud Messaging. The
app saves each phone's FCM token with `register_my_push_token()`. Nothing here
can make an alert, a message or a reservation fail: without the setup below,
alerts simply aren't pushed.

The function uses a **new** service account that can only send FCM messages
(the `set-firebase-role` one keeps its two Auth permissions and nothing else).
Its key, `FIREBASE_PUSH_KEY`, and the shared secret `PUSH_WEBHOOK_SECRET` must
never be put in the app, this repo, the docs, a chat or a log.

### One-time setup

**A. API (Google Cloud console)**

1. Open https://console.cloud.google.com/apis/library/fcm.googleapis.com?project=salusalo
   and check that the box at the top left says **salusalo**. **Firebase Cloud
   Messaging API** must say "API Enabled"; if not, click **Enable**.

**B. Custom role**

2. Open https://console.cloud.google.com/iam-admin/roles?project=salusalo →
   **+ Create role**.
3. Title `Salu-Salo push sender`, ID `salusaloPushSender`, Role launch stage
   **General Availability**.
4. **+ Add permissions** → **Filter permissions by role** → **Firebase Cloud
   Messaging API Admin** → tick only `cloudmessaging.messages.create` → **Add**
   → **Create**.

**C. Service account and key**

5. Open https://console.cloud.google.com/iam-admin/serviceaccounts?project=salusalo
   → **+ Create service account**. Name `salusalo-push-sender`, description
   `Sends phone notifications for the send-push Edge Function` → **Create and
   continue**.
6. **Select a role** → **Custom** → **Salu-Salo push sender** → **Continue** →
   **Done**. Add no other role.
7. Check that its email ends with `@salusalo.iam.gserviceaccount.com`. Click it
   → **Keys** → **Add key** → **Create new key** → **JSON** → **Create**. The
   browser downloads a `.json` file.

**D. Supabase secrets**

8. Supabase dashboard → the Salu-Salo project → **Edge Functions** →
   **Secrets**:
   - Name `FIREBASE_PUSH_KEY`; value: the whole downloaded file, from the
     first `{` to the last `}` → **Save**. Then delete the downloaded file
     (and empty the Recycle Bin).
   - Name `PUSH_WEBHOOK_SECRET`; value: a long random password. Make one in
     PowerShell with
     `-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 48 | % {[char]$_})`
     and keep it in the clipboard for step 10 → **Save**.

**E. Deploy with JWT verification off**

9. **Edge Functions** → **Deploy a new function** → **Via Editor**. Set the
   name to `send-push` **before the first deploy**. Paste the whole of
   `functions/send-push/index.ts`, open the function's **Details** (or the
   deploy options) and turn **Verify JWT** (Enforce JWT Verification)
   **off**, then **Deploy**. The URL column must end with
   `/functions/v1/send-push`.

**F. Database (SQL Editor)**

10. Run `migrations/20261016_push_notifications.sql`. Then, in a new query,
    put the function's URL and the same password into Vault (replace both
    values; nothing else):

    ```sql
    select vault.create_secret('https://<project-ref>.supabase.co/functions/v1/send-push', 'push_function_url');
    select vault.create_secret('<the PUSH_WEBHOOK_SECRET password>', 'push_webhook_secret');
    ```

    Clear that query's text afterwards and don't save it as a snippet. To
    change a value later, use **Project Settings** → **Vault** (or
    `vault.update_secret`).
11. Run `tests/push_notifications_live_safety_check.sql`. Expected: an error
    starting with `SAFETY CHECK FINISHED: 10 passed, 0 failed, 0 skipped`.

### Checks

- **Edge Functions** → `send-push` → **Logs**: one line per alert, like
  `send-push: 1 sent, 0 failed`. `refused (secret)` means the Vault value and
  `PUSH_WEBHOOK_SECRET` differ; `the FIREBASE_PUSH_KEY secret is missing or
  invalid` means step 8's key; `access token failed with HTTP 400` or
  `HTTP 403` on sends means step 4's permission or step 1's API.
- No log line at all after an alert: check the Vault names in step 10
  (`push_function_url`, `push_webhook_secret`) and that the app registered the
  phone (`select count(*) from push_tokens;` in the SQL Editor).
````

- [ ] **Step 7: Commit**

```bash
git add supabase/functions/send-push/index.ts supabase/functions/tests/send-push-test.ts supabase/README.md
git commit -m "Add the send-push Edge Function, its tests and setup steps

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: App: receive pushes and show them as notifications

**Files:**
- Modify: `gradle/libs.versions.toml` (one library line)
- Modify: `app/build.gradle.kts` (one dependency)
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/salusalo/model/AppNotification.kt`
- Create: `app/src/main/java/com/example/salusalo/PushNotifications.kt`
- Modify: `app/src/main/java/com/example/salusalo/data/NotificationRepository.kt`
- Modify: `app/src/main/java/com/example/salusalo/viewmodel/NotificationViewModel.kt`
- Modify: `app/src/main/java/com/example/salusalo/MainActivity.kt` (the `AuthStateListener` in `SaluSaloApp`)
- Test: `app/src/test/java/com/example/salusalo/PushRulesTest.kt`

**Interfaces:**
- Consumes: FCM data keys from Task 2; RPC `register_my_push_token(p_token)` from Task 1.
- Produces (used by Tasks 4 and 5):
  - `data class PushTap(val alertId: String, val type: NotificationType, val conversationId: String?, val reservationId: String?, val uid: String?)`
  - `internal fun pushTapFrom(data: Map<String, String>): PushTap?` and `internal fun pushTapFrom(intent: Intent?): PushTap?`
  - `internal fun shouldShowPush(tap: PushTap, signedInUid: String?, appResumed: Boolean, openChatId: String?): Boolean`
  - `internal object AlertPresence { @Volatile var resumed: Boolean; @Volatile var openChatId: String? }`
  - `internal const val ALERTS_CHANNEL = "alerts"`
  - `fun notificationTypeOf(name: String?): NotificationType` (in `model/AppNotification.kt`)
  - `suspend fun NotificationRepository.registerPushToken(token: String)`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/salusalo/PushRulesTest.kt`:

```kotlin
package com.example.salusalo

import com.example.salusalo.model.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which pushed alerts become phone notifications, and what a tap carries (§35). */
class PushRulesTest {
    private val message = mapOf(
        "alert_id" to "a1", "type" to "MESSAGE", "title" to "New message from Ana",
        "body" to "Pandesal: Is it still there?", "conversation_id" to "c1", "uid" to "u1"
    )
    private val tap = PushTap("a1", NotificationType.MESSAGE, "c1", null, "u1")

    @Test
    fun readsTheTapFromTheAlertData() {
        assertEquals(tap, pushTapFrom(message))
    }

    @Test
    fun noAlertIdMeansNoTap() {
        assertNull(pushTapFrom(message - "alert_id"))
        assertNull(pushTapFrom(message + ("alert_id" to "")))
    }

    @Test
    fun aTypeThisBuildDoesNotKnowIsUnknown() {
        assertEquals(NotificationType.UNKNOWN, pushTapFrom(message + ("type" to "SOMETHING_NEW"))?.type)
        assertEquals(NotificationType.UNKNOWN, pushTapFrom(message - "type")?.type)
        assertEquals(NotificationType.ANNOUNCEMENT, pushTapFrom(message + ("type" to "ANNOUNCEMENT"))?.type)
    }

    @Test
    fun emptyIdsAreMissing() {
        val read = pushTapFrom(message + ("conversation_id" to "") + ("reservation_id" to ""))!!
        assertNull(read.conversationId)
        assertNull(read.reservationId)
    }

    @Test
    fun showsForTheAccountSignedInOnThisPhone() {
        assertTrue(shouldShowPush(tap, "u1", appResumed = false, openChatId = null))
        assertTrue(shouldShowPush(tap, "u1", appResumed = true, openChatId = null))
    }

    @Test
    fun dropsWhenSignedOutOrForAnotherAccount() {
        assertFalse(shouldShowPush(tap, null, appResumed = false, openChatId = null))
        assertFalse(shouldShowPush(tap, "u2", appResumed = false, openChatId = null))
        assertFalse(shouldShowPush(tap.copy(uid = null), "u1", appResumed = false, openChatId = null))
    }

    @Test
    fun dropsAMessageOfTheChatOpenOnScreen() {
        assertFalse(shouldShowPush(tap, "u1", appResumed = true, openChatId = "c1"))
    }

    @Test
    fun showsThatChatsMessageWhileTheAppIsInTheBackground() {
        assertTrue(shouldShowPush(tap, "u1", appResumed = false, openChatId = "c1"))
    }

    @Test
    fun showsOtherChatsAndOtherAlertsWhileAChatIsOpen() {
        assertTrue(shouldShowPush(tap.copy(conversationId = "c2"), "u1", appResumed = true, openChatId = "c1"))
        assertTrue(
            shouldShowPush(
                tap.copy(type = NotificationType.RESERVATION_CREATED),
                "u1", appResumed = true, openChatId = "c1"
            )
        )
    }
}
```

- [ ] **Step 2: Add the library, so the test can compile against it later**

In `gradle/libs.versions.toml`, under `[libraries]`, right after the `firebase-auth` line, add:

```toml
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging" }
```

In `app/build.gradle.kts`, right after `implementation(libs.firebase.auth)`, add:

```kotlin
    // Phone notifications for alerts (§35): FCM data messages from the send-push Edge Function.
    implementation(libs.firebase.messaging)
```

- [ ] **Step 3: Run the test to see it fail** (first build online: it downloads `firebase-messaging`)

Run (PowerShell): `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.salusalo.PushRulesTest"`
Expected: FAIL to compile: `Unresolved reference: PushTap`, `pushTapFrom`, `shouldShowPush`.

- [ ] **Step 4: Share the type lookup in `model/AppNotification.kt`**

Replace the body of `NotificationTypeSerializer.deserialize` and add `notificationTypeOf` above the serializer:

```kotlin
/** The alert type with this name; UNKNOWN for a name this build doesn't know (or none). */
fun notificationTypeOf(name: String?): NotificationType =
    NotificationType.entries.firstOrNull { it.name == name && it != NotificationType.UNKNOWN }
        ?: NotificationType.UNKNOWN
```

```kotlin
    override fun deserialize(decoder: Decoder): NotificationType = notificationTypeOf(decoder.decodeString())
```

- [ ] **Step 5: Create `PushNotifications.kt`**

Create `app/src/main/java/com/example/salusalo/PushNotifications.kt`:

```kotlin
package com.example.salusalo

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.salusalo.data.NotificationRepository
import com.example.salusalo.model.NotificationType
import com.example.salusalo.model.notificationTypeOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

// Phone notifications for alerts (§35). The send-push Edge Function sends every
// new alert as an FCM data message; PushService shows it as a notification, and
// a tap opens MainActivity, which hands the tap to Home.

/** The alert a pushed message (and its notification's tap) carries. */
data class PushTap(
    val alertId: String,
    val type: NotificationType,
    val conversationId: String?,
    val reservationId: String?,
    /** The Firebase UID of the account the alert is for. */
    val uid: String?
)

// The FCM data keys (send-push) a tap carries; the tap's Intent uses the same names.
private val TAP_KEYS = listOf("alert_id", "type", "conversation_id", "reservation_id", "uid")

/** The alert in a pushed message's data; null without an alert ID. */
internal fun pushTapFrom(data: Map<String, String>): PushTap? {
    val alertId = data["alert_id"]?.takeIf { it.isNotEmpty() } ?: return null
    return PushTap(
        alertId = alertId,
        type = notificationTypeOf(data["type"]),
        conversationId = data["conversation_id"]?.takeIf { it.isNotEmpty() },
        reservationId = data["reservation_id"]?.takeIf { it.isNotEmpty() },
        uid = data["uid"]?.takeIf { it.isNotEmpty() }
    )
}

/** The alert of a tapped notification that opened MainActivity, or null for any other start. */
internal fun pushTapFrom(intent: Intent?): PushTap? =
    intent?.let { pushTapFrom(TAP_KEYS.mapNotNull { key -> it.getStringExtra(key)?.let { value -> key to value } }.toMap()) }

/**
 * Whether a pushed alert becomes a phone notification: only for the account
 * signed in on this phone, and never for a message of the chat that is open on
 * screen (the user is reading it).
 */
internal fun shouldShowPush(tap: PushTap, signedInUid: String?, appResumed: Boolean, openChatId: String?): Boolean =
    signedInUid != null && tap.uid == signedInUid &&
        !(appResumed && tap.type == NotificationType.MESSAGE &&
            tap.conversationId != null && tap.conversationId == openChatId)

/** What PushService needs to know about the screen; HomeScreen keeps it current. */
internal object AlertPresence {
    /** True while Home is on screen (resumed). */
    @Volatile var resumed = false
    /** The chat open on Home, if any. */
    @Volatile var openChatId: String? = null
}

internal const val ALERTS_CHANNEL = "alerts"

/**
 * Shows a pushed alert as a notification that pops up (channel importance high),
 * silent when this phone's "Notification sound" is off. A chat's newest message
 * replaces its older one; other alerts each get their own. Tapping it opens the
 * app on the alert's own place (MainActivity → Home).
 */
internal fun showAlertNotification(context: Context, tap: PushTap, data: Map<String, String>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) return
    val manager = NotificationManagerCompat.from(context)
    // Cheap, and nothing happens once it exists.
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(ALERTS_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Alerts")
            .setDescription("Messages, reservation updates and announcements")
            .build()
    )
    val open = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    TAP_KEYS.forEach { key -> data[key]?.let { open.putExtra(key, it) } }
    val pending = PendingIntent.getActivity(
        context, tap.alertId.hashCode(), open,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val body = data["body"].orEmpty()
    val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher_monochrome)
        .setContentTitle(data["title"].orEmpty())
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        // Android 7 has no channels: these make it pop up and sound there.
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setSilent(!notificationSoundOn(context))
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()
    val tag = if (tap.type == NotificationType.MESSAGE) tap.conversationId ?: tap.alertId else tap.alertId
    try {
        manager.notify(tag, 0, notification)
    } catch (_: SecurityException) {
        // Notifications were turned off in the meantime.
    }
}

/**
 * Receives Salu-Salo's pushed alerts and shows them (see [shouldShowPush]), and
 * saves a new FCM token for the account signed in on this phone.
 */
class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        // FCM calls this on a background thread. A failure stays quiet: Home
        // registers the token again on its next start.
        runBlocking {
            withTimeoutOrNull(10_000L) { runCatching { NotificationRepository().registerPushToken(token) } }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val tap = pushTapFrom(message.data) ?: return
        val signedIn = FirebaseAuth.getInstance().currentUser?.uid
        if (!shouldShowPush(tap, signedIn, AlertPresence.resumed, AlertPresence.openChatId)) return
        showAlertNotification(this, tap, message.data)
    }
}
```

- [ ] **Step 6: Run the test to see it pass**

Run (PowerShell): `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.salusalo.PushRulesTest" --tests "com.example.salusalo.NotificationTypeTest"`
Expected: PASS (9 + 3 tests). If `registerPushToken` is unresolved, do Step 7 first and run again.

- [ ] **Step 7: Register the token**

In `data/NotificationRepository.kt`, add the import `import kotlinx.serialization.json.put` and this function after `markRead`:

```kotlin
    /** Saves this phone's FCM token for the signed-in account (§35), so its alerts are pushed here. */
    suspend fun registerPushToken(token: String) {
        postgrest.rpc("register_my_push_token", buildJsonObject { put("p_token", token) })
    }
```

In `viewmodel/NotificationViewModel.kt`, add the imports `import com.google.firebase.messaging.FirebaseMessaging` and `import kotlinx.coroutines.tasks.await`, and inside `start()`, in `sessionJob = viewModelScope.launch { … }`, right after `launch { load() }`, add:

```kotlin
            launch {
                // This phone's push token, for this account (§35). Quiet: a failure
                // (offline, or the migration not run yet) tries again next start.
                runCatching { repository.registerPushToken(FirebaseMessaging.getInstance().token.await()) }
                    .onFailure { if (it is CancellationException) throw it }
            }
```

- [ ] **Step 8: Delete the token on sign-out**

In `MainActivity.kt`, add `import com.google.firebase.messaging.FirebaseMessaging` and change the start of the `AuthStateListener` in `SaluSaloApp` from:

```kotlin
        val listener = FirebaseAuth.AuthStateListener { auth ->
            authUid = auth.currentUser?.uid
```

to:

```kotlin
        val listener = FirebaseAuth.AuthStateListener { auth ->
            // Signed out (Log out, or the session ended): this phone's push token
            // is deleted, so the old account's alerts stop popping up here; the
            // next sign-in registers a new one (§35).
            if (auth.currentUser == null && authUid != null) FirebaseMessaging.getInstance().deleteToken()
            authUid = auth.currentUser?.uid
```

- [ ] **Step 9: Declare the permission and the service**

In `app/src/main/AndroidManifest.xml`, after the location permissions add:

```xml
    <!-- Android 13+: asked once on Home (§35); alerts pop up as phone notifications. -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

and inside `<application>`, after the `</activity>` of `MainActivity`, add:

```xml
        <!-- Receives pushed alerts (FCM data messages from the send-push Edge Function). -->
        <service
            android:name=".PushService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>
```

- [ ] **Step 10: Build, test and lint**

Run (PowerShell): `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
Expected: BUILD SUCCESSFUL; `testDebugUnitTest` 104 of 104 (95 + 9); `lintDebug` 0 errors. Read any new lint warning on a changed line and fix it if it is real.

- [ ] **Step 11: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/example/salusalo/PushNotifications.kt app/src/main/java/com/example/salusalo/model/AppNotification.kt app/src/main/java/com/example/salusalo/data/NotificationRepository.kt app/src/main/java/com/example/salusalo/viewmodel/NotificationViewModel.kt app/src/main/java/com/example/salusalo/MainActivity.kt app/src/test/java/com/example/salusalo/PushRulesTest.kt
git commit -m "Show pushed alerts as phone notifications and register the FCM token

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: A tap opens the alert's place; the open chat; one sound

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/MainActivity.kt` (`MainActivity` class, `SaluSaloApp`, `HomeScreen`)
- Modify: `app/src/main/java/com/example/salusalo/AlertScreen.kt` (`AlertsScreen`)
- Modify: `app/src/main/java/com/example/salusalo/viewmodel/NotificationViewModel.kt` (`markRead`)
- Modify: `app/src/main/java/com/example/salusalo/PushNotifications.kt` (two helpers)

**Interfaces:**
- Consumes: `PushTap`, `pushTapFrom(intent)`, `AlertPresence`, `ALERTS_CHANNEL` from Task 3.
- Produces: `internal fun phoneNotificationsOn(context: Context): Boolean` (used by Task 5) and `internal fun cancelChatNotification(context: Context, conversationId: String)`. `SaluSaloApp(pushTap: PushTap?, onPushTapHandled: () -> Unit)`; `HomeScreen(…, pushTap: PushTap?, onPushTapHandled: () -> Unit)`; `AlertsScreen(notificationViewModel, onOpenAlert: (AppNotification) -> Unit, openAnnouncement: String?, onAnnouncementOpened: () -> Unit)`.

No new unit test: this task is Compose wiring and Android calls (covered by phone-test steps 3–6). The existing tests must keep passing.

- [ ] **Step 1: Add the two helpers to `PushNotifications.kt`** (after `ALERTS_CHANNEL`):

```kotlin
/** Whether this phone shows Salu-Salo's notifications: allowed for the app, and the Alerts channel not turned off. */
internal fun phoneNotificationsOn(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    return manager.areNotificationsEnabled() &&
        manager.getNotificationChannelCompat(ALERTS_CHANNEL)?.importance != NotificationManagerCompat.IMPORTANCE_NONE
}

/** Removes a chat's notification once the chat is open. */
internal fun cancelChatNotification(context: Context, conversationId: String) {
    NotificationManagerCompat.from(context).cancel(conversationId, 0)
}
```

- [ ] **Step 2: `markRead` also sends alerts not loaded yet** (a tapped notification right after the app starts)

In `viewmodel/NotificationViewModel.kt`, change:

```kotlin
    fun markRead(ids: List<String>) {
        val unread = ids.filter { id -> _notifications.value.any { it.id == id && it.readAt == null } }
```

to:

```kotlin
    // Alerts not loaded yet (a notification tapped as the app starts) are sent
    // too; only those already shown as read are skipped.
    fun markRead(ids: List<String>) {
        val unread = ids.filter { id -> _notifications.value.none { it.id == id && it.readAt != null } }
```

- [ ] **Step 3: `AlertsScreen` takes one `onOpenAlert` and an announcement to open**

In `AlertScreen.kt`, replace the parameter list:

```kotlin
fun AlertsScreen(
    notificationViewModel: NotificationViewModel,
    onOpenConversation: (String) -> Unit,
    // A new or cancelled reservation on one of this account's listings.
    onOpenListingReservation: (String?) -> Unit,
    onOpenClaimedReservation: (String?) -> Unit,
    onOpenRemovedReservation: (String?) -> Unit
) {
```

with:

```kotlin
fun AlertsScreen(
    notificationViewModel: NotificationViewModel,
    // Where a tapped alert leads: HomeScreen's openAlert, which a tapped phone
    // notification uses too (§35).
    onOpenAlert: (AppNotification) -> Unit,
    // An announcement to open (tapped here or on its notification); then onAnnouncementOpened.
    openAnnouncement: String?,
    onAnnouncementOpened: () -> Unit
) {
```

After the line `var openAnnouncementId by rememberSaveable { mutableStateOf<String?>(null) }`, add:

```kotlin
    LaunchedEffect(openAnnouncement) {
        if (openAnnouncement != null) {
            openAnnouncementId = openAnnouncement
            onAnnouncementOpened()
        }
    }
```

and replace the row's `onClick`:

```kotlin
                        onClick = {
                            notificationViewModel.markRead(listOf(alert.id))
                            when (alert.type) {
                                NotificationType.MESSAGE ->
                                    alert.conversationId?.let(onOpenConversation)
                                NotificationType.RESERVATION_CLAIMED ->
                                    onOpenClaimedReservation(alert.reservationId)
                                NotificationType.RESERVATION_REMOVED ->
                                    onOpenRemovedReservation(alert.reservationId)
                                NotificationType.ANNOUNCEMENT ->
                                    openAnnouncementId = alert.id
                                else ->
                                    onOpenListingReservation(alert.reservationId)
                            }
                        }
```

with:

```kotlin
                        onClick = {
                            notificationViewModel.markRead(listOf(alert.id))
                            onOpenAlert(alert)
                        }
```

- [ ] **Step 4: `MainActivity` keeps a tapped notification for Home**

In `MainActivity.kt`, add the import `import android.content.Intent` if it isn't there. Change the class to:

```kotlin
class MainActivity : ComponentActivity() {
    // A tapped phone notification waiting for Home (§35); Home consumes it once.
    private var pushTap by mutableStateOf<PushTap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only on a fresh start: Android restoring the app hands back the old intent.
        if (savedInstanceState == null) pushTap = pushTapFrom(intent)
```

(keep the rest of `onCreate`) and change `setContent`'s body to:

```kotlin
        setContent {
            SaluTheme {
                SaluSaloApp(pushTap = pushTap, onPushTapHandled = { pushTap = null })
            }
        }
    }

    // A notification tapped while the app is open (single top: this activity is reused).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pushTapFrom(intent)?.let { pushTap = it }
    }
}
```

Change `private fun SaluSaloApp() {` to:

```kotlin
private fun SaluSaloApp(pushTap: PushTap?, onPushTapHandled: () -> Unit) {
```

and the `HomeScreen(` call in its `"home" ->` branch to:

```kotlin
                        HomeScreen(
                            onLogout = { logOut() },
                            currentProfile = profile,
                            onProfileUpdated = { currentProfile = it },
                            pushTap = pushTap,
                            onPushTapHandled = onPushTapHandled
                        )
```

- [ ] **Step 5: `HomeScreen`: parameters, `openAlert`, the tap, presence, sound**

Change the signature:

```kotlin
private fun HomeScreen(
    onLogout: () -> Unit,
    currentProfile: UserProfile?,
    onProfileUpdated: (UserProfile) -> Unit,
    // A tapped phone notification to open (§35), then onPushTapHandled.
    pushTap: PushTap?,
    onPushTapHandled: () -> Unit
) {
```

Replace the sound block (from `// A new alert plays the phone's notification sound while the app is on` to the end of `LaunchedEffect(notificationViewModel) { … }`) with:

```kotlin
    // A new alert plays the phone's notification sound while the app is on
    // screen, unless it is a message in the chat that is open. The switch is
    // read when the alert arrives. While the phone shows Salu-Salo's
    // notifications, the pop-up plays it instead (§35), so it never sounds twice.
    val alertSound = remember { AlertSoundPlayer(context) }
    var appResumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(alertSound) {
        appResumed = true
        AlertPresence.resumed = true
        onPauseOrDispose {
            appResumed = false
            AlertPresence.resumed = false
            alertSound.stop()
        }
    }
    val openChatId by rememberUpdatedState(activeChat?.id)
    LaunchedEffect(notificationViewModel) {
        notificationViewModel.newAlerts.collect { alert ->
            val inOpenChat = alert.type == NotificationType.MESSAGE &&
                    alert.conversationId != null && alert.conversationId == openChatId
            if (appResumed && !inOpenChat && notificationSoundOn(context) && !phoneNotificationsOn(context)) {
                alertSound.play()
            }
        }
    }
    // The chat open on screen: its pushed messages don't pop up, and opening it
    // clears its notification (§35).
    DisposableEffect(activeChat?.id) {
        val id = activeChat?.id
        AlertPresence.openChatId = id
        if (id != null) cancelChatNotification(context, id)
        onDispose { AlertPresence.openChatId = null }
    }
```

Right after the existing `LaunchedEffect(activeChat?.id, alerts) { … }` block, add:

```kotlin
    // The announcement the Alerts page should open (tapped there or on its notification).
    var announcementToOpen by rememberSaveable { mutableStateOf<String?>(null) }
    // Where an alert leads, for a tap on the Alerts page and on its phone notification alike.
    fun openAlert(type: NotificationType, alertId: String, conversationId: String?, reservationId: String?) {
        when (type) {
            NotificationType.MESSAGE -> conversationId?.let { messageViewModel.openConversation(it) }
            // History; Activity then opens the reservation (with Rate for a claimed one).
            NotificationType.RESERVATION_CLAIMED, NotificationType.RESERVATION_REMOVED -> {
                activityTab = "History"
                activityFocus = reservationId
                page = "activity"
            }
            NotificationType.ANNOUNCEMENT -> {
                announcementToOpen = alertId
                page = "alerts"
            }
            NotificationType.UNKNOWN -> Unit
            // A new or cancelled reservation on one of this account's listings:
            // My Listings; Activity then opens that listing's Manage reservations.
            else -> {
                activityTab = "My Listings"
                activityFocus = reservationId
                page = "activity"
            }
        }
    }
    // A tapped phone notification (§35), handled once Home is open.
    LaunchedEffect(pushTap) {
        val tap = pushTap ?: return@LaunchedEffect
        onPushTapHandled()
        // For another account (this phone signed in to someone else since): the app just opens.
        if (tap.uid != FirebaseAuth.getInstance().currentUser?.uid) return@LaunchedEffect
        notificationViewModel.markRead(listOf(tap.alertId))
        openAlert(tap.type, tap.alertId, tap.conversationId, tap.reservationId)
    }
```

Replace the `"alerts" -> AlertsScreen( … )` call (with its four callbacks) by:

```kotlin
                    "alerts" -> AlertsScreen(
                        notificationViewModel = notificationViewModel,
                        onOpenAlert = { alert -> openAlert(alert.type, alert.id, alert.conversationId, alert.reservationId) },
                        openAnnouncement = announcementToOpen,
                        onAnnouncementOpened = { announcementToOpen = null }
                    )
```

- [ ] **Step 6: Build, test and lint**

Run (PowerShell): `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`
Expected: BUILD SUCCESSFUL; 104 of 104; lint 0 errors. Fix any "unused parameter/import" warning this task created.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/salusalo/MainActivity.kt app/src/main/java/com/example/salusalo/AlertScreen.kt app/src/main/java/com/example/salusalo/viewmodel/NotificationViewModel.kt app/src/main/java/com/example/salusalo/PushNotifications.kt
git commit -m "Open a tapped notification's alert; skip the open chat; one sound per alert

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Ask for permission once; show when phone notifications are off

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/PushNotifications.kt` (permission flag)
- Modify: `app/src/main/java/com/example/salusalo/MainActivity.kt` (`HomeScreen`)
- Modify: `app/src/main/java/com/example/salusalo/ProfileSections.kt` (the `"notifications"` page)

**Interfaces:**
- Consumes: `phoneNotificationsOn(context)` from Task 4; `InlineNotice(text, icon, modifier, tone, actionLabel, onAction)` and `NoticeTone.Warning` from `SaluTheme.kt`.
- Produces: `internal fun pushPermissionAsked(context: Context): Boolean`, `internal fun markPushPermissionAsked(context: Context)`.

No unit test (Android permission and settings UI): phone-test steps 1 and 7.

- [ ] **Step 1: Add the "asked once" flag to `PushNotifications.kt`** (at the end of the file's top-level functions):

```kotlin
// The notification permission is asked once per phone; kept in salusalo_session
// like "Notification sound", which sign-out doesn't clear.
internal fun pushPermissionAsked(context: Context): Boolean =
    context.getSharedPreferences("salusalo_session", 0).getBoolean("push_permission_asked", false)

internal fun markPushPermissionAsked(context: Context) {
    context.getSharedPreferences("salusalo_session", 0).edit().putBoolean("push_permission_asked", true).apply()
}
```

- [ ] **Step 2: Ask on Home (Android 13+)**

In `MainActivity.kt` add the imports (skip any already present): `android.Manifest`, `android.content.pm.PackageManager`, `android.os.Build`, `androidx.activity.compose.rememberLauncherForActivityResult`, `androidx.activity.result.contract.ActivityResultContracts`, `androidx.core.content.ContextCompat`. In `HomeScreen`, right after the `DisposableEffect(activeChat?.id) { … }` block from Task 4, add:

```kotlin
    // Android 13+: asked once whether Salu-Salo may show notifications (§35).
    // Turned off later, Profile → Notifications offers the phone's settings.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !pushPermissionAsked(context)) {
            markPushPermissionAsked(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
```

- [ ] **Step 3: Profile → Notifications: the notice and the new caption**

In `ProfileSections.kt` add the imports `androidx.compose.material.icons.outlined.NotificationsOff` and `androidx.lifecycle.compose.LifecycleResumeEffect` (and any other the compiler reports missing, such as `remember`).

Next to `fun showSaved(message: String)` (same composable, so it can use `context` and `feedback`), add a local function with the body of the existing "Open device notification settings" button:

```kotlin
    // The phone's notification settings for Salu-Salo (app details on Android 7).
    fun openDeviceNotificationSettings() {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"))
        val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else fallback
        try { context.startActivity(target) }
        catch (_: Exception) {
            try { context.startActivity(fallback) }
            catch (_: Exception) { feedback = "Open your phone Settings, then Apps, then Salu-Salo." }
        }
    }
```

and change that button to:

```kotlin
                                SaluSecondaryButton(
                                    text = "Open device notification settings",
                                    onClick = { openDeviceNotificationSettings() }
                                )
```

At the start of the `"notifications" -> {` branch, before `SectionCard {`, add:

```kotlin
                                // Off for the whole app or its Alerts channel (§35); checked
                                // again each time the page is back on screen.
                                var phoneNotifications by remember { mutableStateOf(phoneNotificationsOn(context)) }
                                LifecycleResumeEffect(Unit) {
                                    phoneNotifications = phoneNotificationsOn(context)
                                    onPauseOrDispose { }
                                }
                                if (!phoneNotifications) {
                                    InlineNotice(
                                        text = "Phone notifications are off, so alerts show only inside Salu-Salo.",
                                        icon = Icons.Outlined.NotificationsOff,
                                        tone = NoticeTone.Warning,
                                        actionLabel = "Turn on",
                                        onAction = { openDeviceNotificationSettings() }
                                    )
                                }
```

and replace the caption:

```kotlin
                                Text(
                                    "Choose which updates appear in Alerts. Sound plays while " +
                                            "Salu-Salo is open. Notifications while the app is " +
                                            "closed are not available yet.",
                                    fontSize = 12.sp
                                )
```

with:

```kotlin
                                Text(
                                    "Choose which updates appear in Alerts and pop up on this " +
                                            "phone, also while Salu-Salo is closed. Notification " +
                                            "sound is saved on this phone only.",
                                    fontSize = 12.sp
                                )
```

- [ ] **Step 4: Build, test and lint**

Run (PowerShell): `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`
Expected: BUILD SUCCESSFUL; 104 of 104; lint 0 errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/salusalo/PushNotifications.kt app/src/main/java/com/example/salusalo/MainActivity.kt app/src/main/java/com/example/salusalo/ProfileSections.kt
git commit -m "Ask for the notification permission once; say when phone notifications are off

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Stay signed in until Log out ("Keep me signed in" removed)

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/MainActivity.kt`
- Modify: `app/src/main/java/com/example/salusalo/AlertSound.kt` (comment)

**Interfaces:**
- Produces: `SignInScreen(mode, onNavigate, isAuthenticating, onContinue: (email: String, password: String, confirmPassword: String, name: String) -> Unit, onGoogle: () -> Unit)`; `signInWithGoogle()` without parameters.

No unit test (UI and Firebase start-up): phone-test steps 1 and 2.

- [ ] **Step 1: No sign-out on a fresh start**

In `MainActivity.onCreate`, delete:

```kotlin
        // "Keep me signed in" unchecked: end the session when the app starts
        // fresh (for example after being swiped away), but not on rotation or
        // when Android restores the app, which pass a saved state.
        if (savedInstanceState == null) {
            val auth = FirebaseAuth.getInstance()
            if (!keepSignedIn(this) && auth.currentUser != null) {
                auth.signOut()
            }
        }
```

Replace:

```kotlin
// Device-only "Keep me signed in" choice. It survives sign-out, so the next
// fresh start can act on the choice made at the last sign-in. When it was never
// saved (a session from an older build), the session is kept.
private fun sessionPreferences(context: Context) =
    context.getSharedPreferences("salusalo_session", 0)

private fun keepSignedIn(context: Context): Boolean =
    sessionPreferences(context).getBoolean("keep_signed_in", true)

private fun setKeepSignedIn(context: Context, keep: Boolean) {
    sessionPreferences(context).edit().putBoolean("keep_signed_in", keep).apply()
}

// The welcome pages show only until this phone has seen them: Get Started,
// "Sign in" on them, or any account reaching Home. A saved "Keep me signed in"
// choice means someone signed in on an older build, so they count as seen too.
```

with:

```kotlin
// This phone's own settings; sign-out doesn't clear them. An account stays
// signed in until Log out (§35; "Keep me signed in" is gone).
private fun sessionPreferences(context: Context) =
    context.getSharedPreferences("salusalo_session", 0)

// The welcome pages show only until this phone has seen them: Get Started,
// "Sign in" on them, or any account reaching Home. A "Keep me signed in" choice
// saved by a build before §35 means someone signed in, so they count as seen too.
```

- [ ] **Step 2: Remove the choice from the sign-in code**

In `SaluSaloApp`:
- the comment `// start with a saved session ("Keep me signed in") until its setup has finished:` becomes `// start with a saved session until its setup has finished:`;
- `fun signInWithGoogle(keep: Boolean) {` becomes `fun signInWithGoogle() {`, and delete its line `setKeepSignedIn(context, keep)`;
- `onContinue = { email, password, confirmPassword, name, keep ->` becomes `onContinue = { email, password, confirmPassword, name ->`;
- in the `"signup"` branch delete `// A new account stays signed in.` and `setKeepSignedIn(context, true)`;
- in the sign-in branch delete `setKeepSignedIn(context, keep)`;
- `onGoogle = { keep -> signInWithGoogle(keep) }` becomes `onGoogle = { signInWithGoogle() }`.

- [ ] **Step 3: Remove the checkbox from `SignInScreen`**

Change the parameters:

```kotlin
    onContinue: (
        email: String,
        password: String,
        confirmPassword: String,
        name: String
    ) -> Unit,
    onGoogle: () -> Unit
```

Delete:

```kotlin
    // Starts unchecked: staying signed in is the user's choice at each sign-in.
    var keepSignedIn by rememberSaveable { mutableStateOf(false) }
```

Replace the whole `} else { Row( … ) { Row( … toggleable … ) { Checkbox … Text("Keep me signed in" …) } TextButton( … "Forgot password?" … ) } }` block after the password field (the `else` of `if (creatingAccount)`) with:

```kotlin
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { onNavigate("forgot") },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    "Forgot password?",
                                    style = SaluText.Supporting.copy(fontWeight = FontWeight.SemiBold),
                                    color = SaluColors.Green
                                )
                            }
                        }
                    }
```

Change `onContinue(email, password, confirmPassword, name, keepSignedIn)` to `onContinue(email, password, confirmPassword, name)`, and:

```kotlin
                    // A new account made on the Create Account tab stays signed in.
                    GoogleButton(enabled = !isAuthenticating) {
                        onGoogle(if (creatingAccount) true else keepSignedIn)
                    }
```

to:

```kotlin
                    GoogleButton(enabled = !isAuthenticating) { onGoogle() }
```

Delete the import `androidx.compose.foundation.selection.toggleable` if nothing else uses it (`grep -n "toggleable" MainActivity.kt` shows only the import). `Checkbox` and `CheckboxDefaults` come from the `material3.*` wildcard: nothing to delete. If `Arrangement` isn't imported, add `import androidx.compose.foundation.layout.Arrangement`.

- [ ] **Step 4: The comment in `AlertSound.kt`**

Change:

```kotlin
// "Notification sound" is a setting of this phone, not of the account: it is kept
// in salusalo_session (next to "Keep me signed in"), which sign-out does not clear.
```

to:

```kotlin
// "Notification sound" is a setting of this phone, not of the account: it is kept
// in salusalo_session (the phone's own settings), which sign-out does not clear.
```

- [ ] **Step 5: Check nothing else refers to it**

Run: `grep -rn "keepSignedIn\|setKeepSignedIn\|Keep me signed in" app/src`
Expected: only the comments in `MainActivity.kt` (the `welcomeSeen` comment) and `contains("keep_signed_in")` in `welcomeSeen()`.

- [ ] **Step 6: Build, test and lint**

Run (PowerShell): `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`
Expected: BUILD SUCCESSFUL; 104 of 104; lint 0 errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/salusalo/MainActivity.kt app/src/main/java/com/example/salusalo/AlertSound.kt
git commit -m "Stay signed in until Log out; remove Keep me signed in

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Docs, the final checks and the phone test

**Files:**
- Modify: `docs/PROJECT_STATUS.md` (header, §1 table row, current state and known issues, new §35)
- Modify: `CLAUDE.md`

- [ ] **Step 1: Run every check once more and note the numbers**

Run (PowerShell): `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`
Run (Bash): `"<scratchpad>/deno/node_modules/.bin/deno" test supabase/functions/tests/`
Expected: BUILD SUCCESSFUL; unit tests 104 of 104; lint 0 errors (note the warning count); Deno 33 of 33 (21 + 12). Use the real numbers below; if any differs, report it instead of writing the expected one.

- [ ] **Step 2: Write §35 in `docs/PROJECT_STATUS.md`** (after §34), with these subsections and contents:

- `## 35. Push notifications and staying signed in (2026-10-07)`
- `### 35.0 Status`: "Written on branch `claude/push-notifications` (from `claude/portrait-only` at `387e0bb`, so it also carries §34) on 2026-10-07. Built with AGP (§35.3). Migration `20261016_push_notifications.sql` and the `send-push` setup: **not done yet** (the developer's steps in `supabase/README.md`, "Push notifications"). Not tested on a phone yet (§35.4)." (Update when the developer reports.)
- `### 35.1 What the user sees`: the pop-ups (closed, background, open; not the open chat), the tap destinations, the permission question once, "Phone notifications are off" with Turn on, "Notification sound" making pop-ups silent, no "Keep me signed in" (signed in until Log out).
- `### 35.2 What changed`: the migration (table, RPC, trigger, Vault names), `send-push` (secrets, FCM data message, logs), the app files (`PushNotifications.kt`, `NotificationRepository.registerPushToken`, `NotificationViewModel.start`, `MainActivity` tap/`openAlert`/permission/`deleteToken`, `AlertsScreen` `onOpenAlert`, `ProfileSections` notice, Keep me signed in removed), the dependency `firebase-messaging`, the manifest.
- `### 35.3 Checks run (2026-10-07)`: the real numbers from Step 1, and whether the local SQL dry run ran (Task 1 Step 3).
- `### 35.4 Phone test (two phones)`: the checklist in Step 5 below, marked "Not run yet".
- `### 35.5 Known limits`: copy the spec's "Known limits", plus: before the setup is done, while phone notifications are on, no sound plays while the app is open (the in-app sound waits for the pop-up); `phoneNotificationsOn()` can't see a Xiaomi block.

Also: change the `_Last updated_` line's date to 2026-10-07 and add one sentence at its end ("Push notifications and staying signed in (§35, branch `claude/push-notifications`): built; setup and phone test pending."); add a §1 table row in the same style; in "Current state" replace "Nothing is pushed live: only chats and alerts use Realtime." with a sentence that alerts are also pushed as phone notifications since §35 (once set up); in "Current known issues" remove the "No push notifications" item if present and add the §35.5 limits in one line. Fix §13's sentence "The developer chose to test them in the later push-notification phase" by adding "(§35)".

- [ ] **Step 3: Update `CLAUDE.md`**

- Project bullet "Firebase Authentication": replace `"Keep me signed in" (unchecked by default) is a device-only setting checked on a fresh start (\`MainActivity.onCreate\`).` with `Since §35 an account stays signed in until Log out ("Keep me signed in" removed).`
- Supabase bullet: "Edge Functions" also `send-push` (§35), and `pg_net` and Vault.
- Roadmap: a new last bullet for §35 in the same style ("built on branch `claude/push-notifications` (from `claude/portrait-only` at `387e0bb`; unit tests 104/104, lint 0 errors); migration and setup not done; not tested on a phone yet").
- Architecture (keep the welcome sentence: `welcomeSeen()` still reads a saved `keep_signed_in`), add one bullet: `PushNotifications.kt` (§35): `PushService`, `PushTap`, `shouldShowPush`, `AlertPresence`, `showAlertNotification`, `phoneNotificationsOn`; a tapped notification reaches `HomeScreen` through `MainActivity.pushTap`, which routes it with `openAlert()` (shared with `AlertsScreen`).
- Supabase objects: a bullet for `20261016_push_notifications.sql` (table `push_tokens` with no client privileges; `register_my_push_token`; internal trigger function `push_new_notification` and trigger `notifications_push`; Vault `push_function_url`, `push_webhook_secret`; check `push_notifications_live_safety_check.sql`) and for the Edge Function `send-push`.
- Security rules: "**Push (§35).** `push_tokens` changes only through `register_my_push_token` and its trigger reads; clients have no privileges on it; never add a grant or policy. `send-push` accepts only calls with `x-push-secret` = `PUSH_WEBHOOK_SECRET`, sends only what the trigger passes, and never logs tokens, alert text, keys or Google's errors. `FIREBASE_PUSH_KEY` (a service account with only `cloudmessaging.messages.create`) and the webhook secret stay in Supabase secrets and Vault only. The push trigger must never make an alert fail."
- Commands: add `deno test supabase/functions/tests/send-push-test.ts`.
- Testing expectations: 104 JVM tests (9 in `PushRulesTest`); Deno 33 (21 + 12).
- Known current work: item 27 for §35, same content as the roadmap bullet.

- [ ] **Step 4: Commit**

```bash
git add docs/PROJECT_STATUS.md CLAUDE.md
git commit -m "Docs: push notifications and staying signed in (§35)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 5: Give the developer the setup steps and the phone test**

First: the setup in `supabase/README.md` → "Push notifications" (A–F), then the live safety check (expected 10/10). Then install the branch from Android Studio on both phones. Send this checklist (two phones: phone 1 = account A, the Xiaomi; phone 2 = account B; A has an active listing). Send steps 1–5 first and 6–10 after the developer reports.

1. [New] Phone 1: open the app → "Allow Salu-Salo to send you notifications?" → **Allow**. If you're signed out: the Sign In page has **no "Keep me signed in"** box. Sign in as A. Phone 2: the same as B → **Allow**.
2. [New] Phone 1: swipe Salu-Salo away from Recents, then open it again: still signed in as A (no Sign In page). Swipe it away again and leave it closed.
3. [New] Phone 2 (B): open A's listing → message A "Push test 1". Phone 1 (closed): within a few seconds a pop-up "New message from <B's name>" with "<listing>: Push test 1", with sound. Tap it: the app opens (loading screen) and then **that chat** opens. *If nothing arrives within 30 seconds:* phone Settings → Apps → Salu-Salo → **Autostart** on, and try again. *If it shows only in the notification shade without popping up:* Settings → Notifications → Salu-Salo → **Floating notifications** on.
4. [New] Phone 1: keep that chat open. Phone 2: send "Push test 2" → **no pop-up** on phone 1; the message appears in the chat. Phone 1: close the chat and stay on Home. Phone 2: send "Push test 3" → a pop-up **while the app is open**, one sound only.
5. [New] Phone 1: press Home (app in the background). Phone 2: reserve A's listing (2-hour pickup). Phone 1: pop-up "New reservation"; tap it → Activity → My Listings → that listing's Manage reservations. Phone 2: cancel the reservation.
6. [New] Admin dashboard (https://salusalo.web.app) → Notifications → New announcement to Everyone, "Push test announcement". Both phones: a pop-up. Phone 1: tap it → the Alerts page with the announcement's text open.
7. [New] Phone 1: Profile → Notifications. Turn **Notification sound** off → Save. Phone 2: message A → a **silent** pop-up. Turn it back on → Save. Then phone Settings → Apps → Salu-Salo → Notifications **off**, back to the app: Profile → Notifications shows "**Phone notifications are off, so alerts show only inside Salu-Salo.**" with **Turn on**. With notifications still off and Home open, phone 2 messages A → **no pop-up, but the app's own sound plays** (as before §35). Tap Turn on, switch notifications on, come back: the notice is gone.
8. [New] Phone 1: Profile → **Log out**. Phone 2: message A → **no pop-up** on phone 1. Phone 1: sign in as A again; Phone 2: message A → the pop-up comes again.
9. [Regression, §34] Phone 1, auto-rotate on, then off: turn the phone sideways on Home, Post and a listing's details, and on the full-screen pickup map and photos → the app stays upright each time.
10. [Regression] Phone 1: with the app open, the chat badge and the Alerts badge still go up live when phone 2 sends a message; "New messages" off → Save → phone 2's message makes **no** alert and **no** pop-up; turn it back on.

Left out (not tested): Android 7 phones, Do Not Disturb (Android handles it), a phone with two Salu-Salo accounts switching without Log out, more than 5 phones per account, a token unused for 60 days.

After the developer reports: record the results in `docs/PROJECT_STATUS.md` §35.0, §35.4 and the results log, update `CLAUDE.md`, commit, and offer the PR (it targets `master` and brings §34 too; say so before the merge).
