-- Salu-Salo: suspend accounts (Phase 6; PROJECT_STATUS.md §36).
--
-- Run by hand in the Supabase SQL Editor, after 20261016_push_notifications.sql.
-- Every app build keeps working for accounts that aren't suspended. Run it before
-- installing the §36 build (which calls get_my_account_status). Then run
-- supabase/tests/admin_suspend_accounts_live_safety_check.sql (expect 18 passed).
--
-- A suspended account is refused everywhere by one check: is_salusalo_firebase_jwt(),
-- which every client-reachable policy and function already uses, now also requires
-- that the caller isn't suspended. get_my_account_status() is the only function that
-- skips it, so the app can show why.

-- ---------------------------------------------------------------------------
-- 1. Where a suspension is kept. The admin's note and name go only into the
-- audit log; nothing private is in the person's own row.
-- ---------------------------------------------------------------------------

alter table public.users
    add column if not exists suspended_at timestamptz,
    add column if not exists suspension_reason text;
alter table public.users drop constraint if exists users_suspension_check;
alter table public.users add constraint users_suspension_check check (
    (suspended_at is null and suspension_reason is null)
    or (suspended_at is not null
        and suspension_reason in ('NO_SHOWS', 'UNSAFE_FOOD', 'HARASSMENT', 'SPAM', 'OTHER')));

-- Clients may insert their own users row, so a new profile always starts
-- unsuspended, whatever the insert sends: the 20261002 function (email_verified
-- false) plus the two suspension columns. Only admin_suspend_user and
-- admin_restore_user change them afterwards. The trigger itself is unchanged.
create or replace function public.users_start_unverified()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    new.email_verified := false;
    new.suspended_at := null;
    new.suspension_reason := null;
    return new;
end; $$;

revoke all on function public.users_start_unverified() from public;
revoke all on function public.users_start_unverified() from anon;
revoke all on function public.users_start_unverified() from authenticated;

-- ---------------------------------------------------------------------------
-- 2. The lock.
-- ---------------------------------------------------------------------------

-- The caller's own row only, by the token. SECURITY DEFINER because the users
-- policies themselves call is_salusalo_firebase_jwt() (no recursion through RLS).
-- Executable by the client roles because policies run as the client.
create or replace function public.salusalo_caller_suspended()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (select 1 from public.users u
                   where u.firebase_uid = auth.jwt() ->> 'sub' and u.suspended_at is not null);
$$;
revoke all on function public.salusalo_caller_suspended() from public;
revoke all on function public.salusalo_caller_suspended() from anon;
grant execute on function public.salusalo_caller_suspended() to authenticated, service_role;

-- The 20260921 check plus "not suspended". Same signature: every policy and
-- function that uses it now refuses a suspended account. Keep this check.
create or replace function public.is_salusalo_firebase_jwt()
returns boolean
language sql
stable
set search_path = ''
as $$
    select coalesce(
        auth.jwt() ->> 'iss' = 'https://securetoken.google.com/salusalo'
        and auth.jwt() ->> 'aud' = 'salusalo'
        and auth.jwt() ->> 'role' = 'authenticated',
        false)
       and not public.salusalo_caller_suspended();
$$;

-- The one thing a suspended account can still ask: whether it is suspended, and why.
-- It checks the token itself, NOT through is_salusalo_firebase_jwt().
create or replace function public.get_my_account_status()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_user public.users;
begin
    if not coalesce(auth.jwt() ->> 'iss' = 'https://securetoken.google.com/salusalo'
                    and auth.jwt() ->> 'aud' = 'salusalo'
                    and auth.jwt() ->> 'role' = 'authenticated', false) then
        raise exception 'Not authenticated' using errcode = '28000';
    end if;
    select * into v_user from public.users u where u.firebase_uid = auth.jwt() ->> 'sub';
    return jsonb_build_object('suspended', v_user.suspended_at is not null,
                              'reason', v_user.suspension_reason);
end; $$;
revoke all on function public.get_my_account_status() from public;
revoke all on function public.get_my_account_status() from anon;
grant execute on function public.get_my_account_status() to authenticated;

-- ---------------------------------------------------------------------------
-- 3. Allowed values.
-- ---------------------------------------------------------------------------

alter table public.reservations drop constraint if exists reservations_cancel_reason_check;
alter table public.reservations add constraint reservations_cancel_reason_check check (
    cancel_reason is null
    or (cancel_reason in ('LISTING_REMOVED', 'DONOR_RELEASED', 'PICKUP_EXPIRED', 'ACCOUNT_SUSPENDED')
        and status = 'CANCELLED'));

alter table public.reports drop constraint if exists reports_resolution_check;
alter table public.reports add constraint reports_resolution_check check (
    (status = 'OPEN' and resolved_by is null and resolved_at is null
        and resolution_action is null and resolution_note is null)
    or (status = 'RESOLVED' and resolved_at is not null and resolution_action in ('LISTING_REMOVED', 'USER_SUSPENDED'))
    or (status = 'DISMISSED' and resolved_at is not null and resolution_action = 'DISMISSED'));

alter table public.admin_audit_log drop constraint if exists admin_audit_log_action_check;
alter table public.admin_audit_log add constraint admin_audit_log_action_check check (action in
    ('LISTING_REMOVED', 'REPORTS_DISMISSED', 'LISTING_REVIEWED', 'REPORT_NOTE_ADDED', 'ANNOUNCEMENT_SENT',
     'USER_SUSPENDED', 'USER_RESTORED'));
alter table public.admin_audit_log drop constraint if exists admin_audit_log_target_type_check;
alter table public.admin_audit_log add constraint admin_audit_log_target_type_check
    check (target_type in ('LISTING', 'REPORT', 'ANNOUNCEMENT', 'USER'));

-- ---------------------------------------------------------------------------
-- 4. Reservation alerts: the 20261011 function plus the ACCOUNT_SUSPENDED branch
-- (the donor only; nobody is told the account was suspended).
-- ---------------------------------------------------------------------------

create or replace function public.notify_reservation_change()
returns trigger
language plpgsql security definer set search_path = public as $$
declare v_post public.food_posts; v_recipient_name text; v_donor_name text;
begin
  if tg_op = 'UPDATE' and new.status = 'CANCELLED' and new.cancel_reason = 'LISTING_REMOVED' then
    return new;
  end if;
  select * into v_post from public.food_posts where id = new.food_post_id;
  if not found then return new; end if;
  if tg_op = 'UPDATE' and new.status = 'CANCELLED' and new.cancel_reason = 'DONOR_RELEASED' then
    if new.status is distinct from old.status then
      select name into v_donor_name from public.users where id = v_post.donor_id;
      perform public.notify_user(new.recipient_id, 'RESERVATION_CANCELLED', 'Reservation released',
        coalesce(v_donor_name, 'The donor') || ' released your reservation of ' || v_post.name || '. It is no longer held for you.',
        v_post.id, null, new.id);
    end if;
    return new;
  end if;
  select name into v_recipient_name from public.users where id = new.recipient_id;
  if tg_op = 'UPDATE' and new.status = 'CANCELLED' and new.cancel_reason = 'ACCOUNT_SUSPENDED' then
    if new.status is distinct from old.status then
      perform public.notify_user(v_post.donor_id, 'RESERVATION_CANCELLED', 'Reservation cancelled',
        coalesce(v_recipient_name, 'Someone') || '''s reservation of ' || v_post.name ||
        ' was cancelled. The quantity is available again.',
        v_post.id, null, new.id);
    end if;
    return new;
  end if;
  if tg_op = 'UPDATE' and new.status = 'CANCELLED' and new.cancel_reason = 'PICKUP_EXPIRED' then
    if new.status is distinct from old.status then
      perform public.notify_user(new.recipient_id, 'RESERVATION_CANCELLED', 'Reservation expired',
        'Your reservation of ' || v_post.name || ' expired because it wasn''t picked up in time.',
        v_post.id, null, new.id);
      perform public.notify_user(v_post.donor_id, 'RESERVATION_CANCELLED', 'Reservation expired',
        coalesce(v_recipient_name, 'Someone') || '''s reservation of ' || v_post.name ||
        ' expired because it wasn''t picked up in time. The quantity is available again.',
        v_post.id, null, new.id);
    end if;
    return new;
  end if;
  if tg_op = 'INSERT' then
    perform public.notify_user(v_post.donor_id, 'RESERVATION_CREATED', 'New reservation',
      coalesce(v_recipient_name, 'Someone') || ' reserved ' || new.quantity || ' ' || v_post.unit || ' of ' || v_post.name || '.',
      v_post.id, null, new.id);
  elsif new.status is distinct from old.status then
    if new.status = 'CANCELLED' then
      perform public.notify_user(v_post.donor_id, 'RESERVATION_CANCELLED', 'Reservation cancelled',
        coalesce(v_recipient_name, 'Someone') || ' cancelled their reservation of ' || v_post.name || '. The quantity is available again.',
        v_post.id, null, new.id);
    elsif new.status = 'CLAIMED' then
      perform public.notify_user(new.recipient_id, 'RESERVATION_CLAIMED', 'Reservation claimed',
        'The donor marked your reservation of ' || v_post.name || ' as claimed.',
        v_post.id, null, new.id);
    end if;
  end if;
  return new;
end; $$;
revoke all on function public.notify_reservation_change() from public;
revoke all on function public.notify_reservation_change() from anon;
revoke all on function public.notify_reservation_change() from authenticated;

-- ---------------------------------------------------------------------------
-- 5. send_message: the 20261009 function (rate limit kept) plus the refusal
-- when the other participant is suspended.
-- ---------------------------------------------------------------------------

create or replace function public.send_message(p_conversation_id uuid, p_body text)
returns public.messages
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_conversation public.conversations; v_message public.messages;
        v_body text; v_sender_name text; v_food_name text; v_other uuid;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  v_profile := public.current_salusalo_profile_id();
  if v_profile is null then raise exception 'Profile not found'; end if;
  v_body := trim(coalesce(p_body, ''));
  if length(v_body) = 0 then raise exception 'Type a message first'; end if;
  if length(v_body) > 2000 then raise exception 'Messages can be at most 2000 characters'; end if;
  select * into v_conversation from public.conversations where id = p_conversation_id for update;
  if not found then raise exception 'Conversation not found'; end if;
  if v_profile <> v_conversation.recipient_id and v_profile <> v_conversation.donor_id then
    raise exception 'You are not part of this conversation';
  end if;
  v_other := case when v_profile = v_conversation.donor_id then v_conversation.recipient_id else v_conversation.donor_id end;
  if exists (select 1 from public.users u where u.id = v_other and u.suspended_at is not null) then
    raise exception 'This person can''t receive messages right now.';
  end if;
  perform pg_advisory_xact_lock(hashtextextended('salusalo-message:' || v_profile::text, 0));
  if (select count(*) from public.messages m
      where m.sender_id = v_profile and m.created_at > now() - interval '1 minute') >= 20 then
    raise exception 'You are sending messages too quickly. Wait a minute and try again.';
  end if;
  insert into public.messages(conversation_id, sender_id, body)
  values (p_conversation_id, v_profile, v_body) returning * into v_message;
  update public.conversations set
    last_message_at = v_message.created_at,
    recipient_last_read_at = case when v_profile = recipient_id then v_message.created_at else recipient_last_read_at end,
    donor_last_read_at = case when v_profile = donor_id then v_message.created_at else donor_last_read_at end
  where id = p_conversation_id;
  select name into v_sender_name from public.users where id = v_profile;
  select name into v_food_name from public.food_posts where id = v_conversation.food_post_id;
  perform public.notify_user(v_other, 'MESSAGE',
    'New message from ' || coalesce(v_sender_name, 'a Salu-Salo user'),
    coalesce(v_food_name || ': ', '') || v_body,
    v_conversation.food_post_id, p_conversation_id, null);
  return v_message;
end; $$;
revoke all on function public.send_message(uuid, text) from public;
revoke all on function public.send_message(uuid, text) from anon;
grant execute on function public.send_message(uuid, text) to authenticated;

-- ---------------------------------------------------------------------------
-- 6. The admin side.
-- ---------------------------------------------------------------------------

-- What a suspension closes (and the dialog counts): listings not REMOVED that are
-- still up, or still have an open reservation. Internal; no client may run it.
create or replace function public.admin_suspension_listings(p_user uuid)
returns setof uuid
language sql
stable
security definer
set search_path = public
as $$
    select p.id from public.food_posts p
    where p.donor_id = p_user and p.status <> 'REMOVED'
      and ((p.expiration_date > now() and (p.pickup_end is null or p.pickup_end > now()))
           or exists (select 1 from public.reservations r where r.food_post_id = p.id and r.status = 'RESERVED'));
$$;

create or replace function public.admin_suspend_user(p_user uuid, p_reason text, p_note text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_note text := nullif(btrim(coalesce(p_note, '')), '');
    v_user public.users;
    v_post uuid;
    v_res public.reservations;
    v_removed integer := 0;
    v_cancelled integer := 0;
    v_closed integer := 0;
    v_n integer;
    v_result jsonb;
begin
    select * into v_user from public.users u where u.id = p_user for no key update;
    if not found then raise exception 'User not found'; end if;
    if v_user.id = v_admin.id then raise exception 'You can''t suspend your own account.'; end if;
    if v_user.role = 'ADMIN' then raise exception 'Admins can''t be suspended.'; end if;
    if v_user.suspended_at is not null then raise exception 'This account is already suspended.'; end if;
    if p_reason is null or p_reason not in ('NO_SHOWS', 'UNSAFE_FOOD', 'HARASSMENT', 'SPAM', 'OTHER') then
        raise exception 'Choose a reason.';
    end if;
    if v_note is null or char_length(v_note) > 1000 then
        raise exception 'Write a note (up to 1,000 characters).';
    end if;

    update public.users u set suspended_at = now(), suspension_reason = p_reason where u.id = p_user;
    -- Before any alert below, so no pop-up reaches them.
    delete from public.push_tokens t where t.user_id = p_user;

    -- Their listings: the admin removal (alerts to recipients, reports resolved).
    for v_post in select l from public.admin_suspension_listings(p_user) l order by l loop
        v_result := public.admin_remove_listing(v_post, 'Account suspended');
        v_removed := v_removed + 1;
        v_closed := v_closed + coalesce((v_result ->> 'closedReports')::integer, 0);
    end loop;

    -- The reservations they hold: cancelled, quantity back (as cancel_reservation:
    -- the reservation first, then its listing). The trigger alerts each donor.
    for v_res in
        select * from public.reservations r
        where r.recipient_id = p_user and r.status = 'RESERVED'
        order by r.id
        for update
    loop
        perform 1 from public.food_posts p where p.id = v_res.food_post_id for update;
        update public.reservations r set status = 'CANCELLED', cancel_reason = 'ACCOUNT_SUSPENDED'
        where r.id = v_res.id;
        update public.food_posts p set quantity = p.quantity + v_res.quantity, status = 'AVAILABLE'
        where p.id = v_res.food_post_id and p.status <> 'REMOVED';
        v_cancelled := v_cancelled + 1;
    end loop;

    update public.reports rp
    set status = 'RESOLVED', resolved_by = v_admin.id, resolved_at = now(),
        resolution_action = 'USER_SUSPENDED', resolution_note = v_note
    where rp.target_type = 'USER' and rp.reported_user_id = p_user and rp.status = 'OPEN';
    get diagnostics v_n = row_count;
    v_closed := v_closed + v_n;

    perform public.admin_log(v_admin, 'USER_SUSPENDED', 'USER', v_user.id, v_user.name,
        case p_reason when 'NO_SHOWS' then 'Repeated no-shows at pickup'
                      when 'UNSAFE_FOOD' then 'Unsafe or misleading food'
                      when 'HARASSMENT' then 'Harassment or abuse'
                      when 'SPAM' then 'Spam or fake account'
                      else 'Breaking the community rules' end || ': ' || v_note);
    return jsonb_build_object('removedListings', v_removed, 'cancelledReservations', v_cancelled,
                              'closedReports', v_closed);
end; $$;

create or replace function public.admin_restore_user(p_user uuid, p_note text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_note text := coalesce(nullif(btrim(coalesce(p_note, '')), ''), 'Restored.');
    v_user public.users;
begin
    if char_length(v_note) > 1000 then
        raise exception 'Keep the note under 1,000 characters.';
    end if;
    select * into v_user from public.users u where u.id = p_user for no key update;
    if not found then raise exception 'User not found'; end if;
    if v_user.suspended_at is null then raise exception 'This account isn''t suspended.'; end if;
    update public.users u set suspended_at = null, suspension_reason = null where u.id = p_user;
    perform public.admin_log(v_admin, 'USER_RESTORED', 'USER', v_user.id, v_user.name, v_note);
    return jsonb_build_object('restored', true);
end; $$;

-- The 20261012 filter plus SUSPENDED.
create or replace function public.admin_user_matches(u public.users, p_like text, p_filter text)
returns boolean language sql stable security definer set search_path = public as $$
    select (p_like is null or u.name ilike p_like or u.email ilike p_like)
       and case coalesce(p_filter, 'ALL')
             when 'ADMIN' then u.role = 'ADMIN'
             when 'DONOR' then exists (select 1 from public.food_posts p where p.donor_id = u.id)
             when 'RECIPIENT' then u.role <> 'ADMIN'
                  and not exists (select 1 from public.food_posts p where p.donor_id = u.id)
             when 'SUSPENDED' then u.suspended_at is not null
             else true end;
$$;

-- The 20261012 JSON plus the suspension and the counts the Suspend dialog shows.
create or replace function public.admin_user_json(u public.users)
returns jsonb language sql stable security definer set search_path = public as $$
    select jsonb_build_object(
        'id', u.id, 'name', u.name, 'email', u.email, 'org', false,
        'role', case when u.role = 'ADMIN' then 'ADMIN'
                     when exists (select 1 from public.food_posts p where p.donor_id = u.id) then 'DONOR'
                     else 'RECIPIENT' end,
        'emailVerified', u.email_verified,
        'joinedAt', public.admin_ms(u.created_at),
        'openReports', (select count(*) from public.reports r
                        where r.status = 'OPEN' and r.target_type = 'USER' and r.reported_user_id = u.id),
        'suspendedAt', public.admin_ms(u.suspended_at),
        'suspensionReason', u.suspension_reason,
        'suspension', case when u.suspended_at is null then null else (
            select jsonb_build_object('byName', a.admin_name, 'at', public.admin_ms(a.created_at), 'details', a.details)
            from public.admin_audit_log a
            where a.target_id = u.id and a.action = 'USER_SUSPENDED'
            order by a.created_at desc limit 1) end,
        'suspendCounts', jsonb_build_object(
            'listings', (select count(*) from public.admin_suspension_listings(u.id)),
            'reservations', (select count(*) from public.reservations r
                             where r.recipient_id = u.id and r.status = 'RESERVED')));
$$;

-- ---------------------------------------------------------------------------
-- 7. DEFINER functions that read through current_salusalo_profile_id() need the
-- gate themselves (it runs as the table owner there). The 20261011 and 20260924
-- definitions, plus is_salusalo_firebase_jwt().
-- ---------------------------------------------------------------------------

create or replace function public.get_donor_reservations()
returns table (
    id uuid,
    food_post_id uuid,
    recipient_id uuid,
    quantity integer,
    status text,
    created_at timestamptz,
    updated_at timestamptz,
    recipient_name text,
    unit_price numeric,
    cancel_reason text,
    pickup_deadline timestamptz
)
language sql
stable
security definer
set search_path = public
as 'select r.id, r.food_post_id, r.recipient_id, r.quantity, r.status,
           r.created_at, r.updated_at, u.name, r.unit_price, r.cancel_reason, r.pickup_deadline
    from public.reservations r
    join public.food_posts p on p.id = r.food_post_id
    join public.users u on u.id = r.recipient_id
    where public.is_salusalo_firebase_jwt() and p.donor_id = public.current_salusalo_profile_id()
    order by r.created_at desc';
revoke all on function public.get_donor_reservations() from public;
revoke all on function public.get_donor_reservations() from anon;
grant execute on function public.get_donor_reservations() to authenticated;

create or replace function public.is_reservation_recipient(p_food_post_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as 'select public.is_salusalo_firebase_jwt() and exists (select 1 from public.reservations where food_post_id = p_food_post_id and recipient_id = public.current_salusalo_profile_id())';
revoke all on function public.is_reservation_recipient(uuid) from public;
revoke all on function public.is_reservation_recipient(uuid) from anon;
grant execute on function public.is_reservation_recipient(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 8. Privileges.
-- ---------------------------------------------------------------------------
do $$
declare f text;
begin
    foreach f in array array['admin_suspend_user(uuid,text,text)', 'admin_restore_user(uuid,text)'] loop
        execute format('revoke all on function public.%s from public', f);
        execute format('revoke all on function public.%s from anon', f);
        execute format('grant execute on function public.%s to authenticated', f);
    end loop;
    foreach f in array array['admin_suspension_listings(uuid)', 'admin_user_matches(public.users,text,text)',
                             'admin_user_json(public.users)'] loop
        execute format('revoke all on function public.%s from public', f);
        execute format('revoke all on function public.%s from anon', f);
        execute format('revoke all on function public.%s from authenticated', f);
    end loop;
end $$;
