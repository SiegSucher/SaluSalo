-- Salu-Salo: per-person limits and pickup deadlines (PROJECT_STATUS.md §24).
-- Run after 20261010_bugfix_revoke_anon_execute.sql and BEFORE installing the
-- app build that reserves through create_reservation_with_hold(). Safe to run
-- again, and safe on the live data: existing listings get no limit and
-- existing reservations no deadline (they never expire, as before).
-- No begin/commit on purpose: the trial run (§24) pastes this file together
-- with reservation_limits_live_safety_check.sql, whose closing error then
-- undoes both.
--
-- Older app builds keep working: create_reservation(uuid, integer) and
-- create_reservation_at_price() keep their arguments, results and texts (they
-- only also enforce a listing's limit, and set no deadline);
-- get_donor_reservations() only gains a column; an expired reservation reaches
-- them as an ordinary CANCELLED one with RESERVATION_CANCELLED alerts.

-- ---------------------------------------------------------------------------
-- 1. The per-person limit, set on Post only (clients have no UPDATE).
-- ---------------------------------------------------------------------------

alter table public.food_posts add column if not exists max_per_recipient integer;
alter table public.food_posts drop constraint if exists food_posts_max_per_recipient_check;
alter table public.food_posts add constraint food_posts_max_per_recipient_check
    check (max_per_recipient is null or max_per_recipient between 1 and 10000);

-- The 20261005 checks, plus the limit against the quantity on insert only: the
-- quantity goes down as people reserve, so a table constraint would refuse the
-- reservations' own updates.
create or replace function public.food_posts_check_values()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if tg_op = 'INSERT' then
        new.created_at := now();
        new.updated_at := now();
        if new.quantity is null or new.quantity < 1 or new.quantity > 10000 then
            raise exception 'Enter a quantity from 1 to 10,000.';
        end if;
        if new.max_per_recipient is not null and new.max_per_recipient > new.quantity then
            raise exception 'The maximum per person must be from 1 to the quantity.';
        end if;
        if new.expiration_date is null or new.expiration_date <= now() then
            raise exception 'Choose a future expiration date and time.';
        end if;
        if new.pickup_end is not null and new.pickup_end <= now() then
            raise exception 'Pickup must end in the future.';
        end if;
    end if;
    if (tg_op = 'INSERT' or new.description is distinct from old.description)
       and char_length(new.description) > 2000 then
        raise exception 'The description must be 2,000 characters or fewer.';
    end if;
    return new;
end; $$;

revoke all on function public.food_posts_check_values() from public;
revoke all on function public.food_posts_check_values() from anon;
revoke all on function public.food_posts_check_values() from authenticated;

-- ---------------------------------------------------------------------------
-- 2. The pickup deadline and the new cancel reason.
-- ---------------------------------------------------------------------------

alter table public.reservations add column if not exists pickup_deadline timestamptz;
create index if not exists reservations_open_deadline_idx on public.reservations (pickup_deadline)
    where status = 'RESERVED' and pickup_deadline is not null;

alter table public.reservations drop constraint if exists reservations_cancel_reason_check;
alter table public.reservations add constraint reservations_cancel_reason_check check (
    cancel_reason is null
    or (cancel_reason in ('LISTING_REMOVED', 'DONOR_RELEASED', 'PICKUP_EXPIRED') and status = 'CANCELLED'));

-- ---------------------------------------------------------------------------
-- 3. create_reservation: the 20261009 version plus the limit, after the
-- quantity check and before the rate limit (a refused listing keeps its usual
-- message). The listing lock taken first makes one person's simultaneous
-- reservations count correctly.
-- ---------------------------------------------------------------------------

create or replace function public.create_reservation(p_food_post_id uuid, p_quantity integer)
returns public.reservations
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_post public.food_posts; v_reservation public.reservations; v_held bigint;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  if p_quantity is null or p_quantity <= 0 then raise exception 'Choose a quantity greater than zero'; end if;
  select * into v_post from public.food_posts where id = p_food_post_id for update;
  if not found or v_post.expiration_date <= now() then raise exception 'Food post is no longer available'; end if;
  if v_post.status = 'REMOVED' then raise exception 'The donor removed this listing.'; end if;
  if v_post.pickup_end is not null and v_post.pickup_end <= now() then
    raise exception 'The pickup time for this listing has ended.';
  end if;
  v_profile := public.current_salusalo_profile_id();
  if v_profile is null then raise exception 'Profile not found'; end if;
  if v_post.donor_id = v_profile then raise exception 'You cannot reserve your own food post'; end if;
  if v_post.status <> 'AVAILABLE' or v_post.quantity < p_quantity then
    raise exception 'Not enough food available. Only % remains.', greatest(v_post.quantity, 0);
  end if;
  if v_post.max_per_recipient is not null then
    select coalesce(sum(r.quantity), 0) into v_held from public.reservations r
    where r.food_post_id = p_food_post_id and r.recipient_id = v_profile
      and r.status in ('RESERVED', 'CLAIMED');
    if v_held >= v_post.max_per_recipient then
      raise exception 'You''ve reserved the most this listing allows per person (%).', v_post.max_per_recipient;
    end if;
    if v_held + p_quantity > v_post.max_per_recipient then
      raise exception 'This listing allows % per person. You can reserve % more.',
        v_post.max_per_recipient, v_post.max_per_recipient - v_held;
    end if;
  end if;
  perform pg_advisory_xact_lock(hashtextextended('salusalo-reserve:' || v_profile::text, 0));
  if (select count(*) from public.reservations r
      where r.recipient_id = v_profile and r.created_at > now() - interval '10 minutes') >= 10 then
    raise exception 'You made many reservations in a short time. Wait a few minutes and try again.';
  end if;
  insert into public.reservations(food_post_id, recipient_id, quantity)
  values (p_food_post_id, v_profile, p_quantity) returning * into v_reservation;
  update public.food_posts set quantity = quantity - p_quantity,
    status = case when quantity - p_quantity = 0 then 'RESERVED' else 'AVAILABLE' end
  where id = p_food_post_id;
  return v_reservation;
end; $$;
revoke all on function public.create_reservation(uuid, integer) from public;
revoke all on function public.create_reservation(uuid, integer) from anon;
grant execute on function public.create_reservation(uuid, integer) to authenticated;

-- ---------------------------------------------------------------------------
-- 4. Reserve with a pickup hold (the new app build). The price check and every
-- reservation check run first (create_reservation_at_price, which locks the
-- listing); then the deadline is set in the same transaction. Updating only
-- pickup_deadline sends no alert (the trigger alerts on status changes).
-- ---------------------------------------------------------------------------

create or replace function public.create_reservation_with_hold(
    p_food_post_id uuid,
    p_quantity integer,
    p_unit_price numeric,
    p_hold_minutes integer
)
returns public.reservations
language plpgsql
security definer
set search_path = public
as $$
declare
    v_reservation public.reservations;
    v_post public.food_posts;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    if p_hold_minutes is null or p_hold_minutes not in (15, 30, 60, 120) then
        raise exception 'Choose how soon you''ll pick up: 15, 30, 60 or 120 minutes.';
    end if;
    v_reservation := public.create_reservation_at_price(p_food_post_id, p_quantity, p_unit_price);
    select * into v_post from public.food_posts where id = p_food_post_id;
    update public.reservations
    set pickup_deadline = least(
        greatest(now(), coalesce(v_post.pickup_start, now())) + make_interval(mins => p_hold_minutes),
        coalesce(v_post.pickup_end, v_post.expiration_date),
        v_post.expiration_date)
    where id = v_reservation.id
    returning * into v_reservation;
    return v_reservation;
end; $$;

revoke all on function public.create_reservation_with_hold(uuid, integer, numeric, integer) from public;
revoke all on function public.create_reservation_with_hold(uuid, integer, numeric, integer) from anon;
grant execute on function public.create_reservation_with_hold(uuid, integer, numeric, integer) to authenticated;

-- ---------------------------------------------------------------------------
-- 5. claim_reservation: the 20261005 version plus no claim after the deadline.
-- ---------------------------------------------------------------------------

create or replace function public.claim_reservation(p_reservation_id uuid)
returns public.reservations
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile uuid;
    v_reservation public.reservations;
    v_donor uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    select * into v_reservation
    from public.reservations
    where id = p_reservation_id
    for update;
    if not found then
        raise exception 'Reservation not found';
    end if;

    select donor_id into v_donor
    from public.food_posts
    where id = v_reservation.food_post_id
    for update;
    if not found then
        raise exception 'Food post not found';
    end if;
    if v_donor is distinct from v_profile then
        raise exception 'Only the food donor can mark this reservation as claimed';
    end if;
    if v_reservation.status <> 'RESERVED' then
        raise exception 'Only an active reservation can be claimed';
    end if;
    if v_reservation.pickup_deadline is not null and v_reservation.pickup_deadline <= now() then
        raise exception 'The pickup time for this reservation has passed.';
    end if;

    update public.reservations
    set status = 'CLAIMED'
    where id = p_reservation_id
    returning * into v_reservation;

    return v_reservation;
end; $$;

revoke all on function public.claim_reservation(uuid) from public;
revoke all on function public.claim_reservation(uuid) from anon;
grant execute on function public.claim_reservation(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 6. The expiry job's function. No client may run it; Cron runs it as its
-- owner. Locks: the reservation, then its listing (the project's order), both
-- SKIP LOCKED, so it never waits and never deadlocks; a skipped row is retried
-- next minute. A REMOVED listing stays REMOVED (as release_reservation does).
-- ---------------------------------------------------------------------------

create or replace function public.expire_overdue_reservations()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
    r record;
    v_post_id uuid;
    v_count integer := 0;
begin
    for r in
        select id, food_post_id, quantity
        from public.reservations
        where status = 'RESERVED' and pickup_deadline <= now()
        order by pickup_deadline
        limit 500
        for update skip locked
    loop
        select id into v_post_id from public.food_posts where id = r.food_post_id for update skip locked;
        if not found then
            continue;
        end if;
        update public.reservations
        set status = 'CANCELLED', cancel_reason = 'PICKUP_EXPIRED'
        where id = r.id;
        update public.food_posts
        set quantity = quantity + r.quantity,
            status = case when status = 'REMOVED' then status else 'AVAILABLE' end
        where id = r.food_post_id;
        v_count := v_count + 1;
    end loop;
    return v_count;
end; $$;

revoke all on function public.expire_overdue_reservations() from public;
revoke all on function public.expire_overdue_reservations() from anon;
revoke all on function public.expire_overdue_reservations() from authenticated;

-- ---------------------------------------------------------------------------
-- 7. Alerts: the 20261006 trigger function plus expiry, which alerts both
-- people (type RESERVATION_CANCELLED, known to every build; no clock times,
-- the server's zone is UTC).
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
-- 8. The donor's list: the 20261006 function plus pickup_deadline. The result
-- type changes, so it is dropped and recreated. Names only, as before.
-- ---------------------------------------------------------------------------

drop function if exists public.get_donor_reservations();
create function public.get_donor_reservations()
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
    where p.donor_id = public.current_salusalo_profile_id()
    order by r.created_at desc';

revoke all on function public.get_donor_reservations() from public;
revoke all on function public.get_donor_reservations() from anon;
grant execute on function public.get_donor_reservations() to authenticated;

-- ---------------------------------------------------------------------------
-- 9. Supabase Cron (pg_cron), as in Supabase's install guide, and the job.
-- Scheduling an existing job name replaces it, so running this again is safe.
-- If the SQL Editor refuses the extension, switch on Integrations -> Cron in
-- the dashboard and run this file again.
-- ---------------------------------------------------------------------------

create extension if not exists pg_cron with schema pg_catalog;
grant usage on schema cron to postgres;
grant all privileges on all tables in schema cron to postgres;

select cron.schedule('salusalo-expire-reservations', '* * * * *',
                     'select public.expire_overdue_reservations()');
