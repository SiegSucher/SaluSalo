# Reservation Limits and Pickup Deadlines Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let donors cap how much one person may reserve of a listing, and make every new reservation expire by itself (food back on the listing, both people alerted) when it isn't picked up within the time the recipient chose.

**Architecture:** One SQL migration adds `food_posts.max_per_recipient`, `reservations.pickup_deadline`, a limit check in `create_reservation`, a new `create_reservation_with_hold` RPC, a late-claim refusal, an internal `expire_overdue_reservations()` run every minute by Supabase Cron, and expiry alerts through the existing trigger. The Android app sends the limit on Post, the chosen hold on Reserve, and shows limits, deadlines and expiry; the phone never changes data by itself.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose (Material 3), supabase-kt 3.5 (PostgREST RPC), PostgreSQL on Supabase with `pg_cron`, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-02-reservation-limits-design.md` (read it with this plan).

## Global Constraints

- Branch `claude/reservation-limits` from `master` at `10270e7`. Commit only with the developer's OK (CLAUDE.md); each task's commit step assumes that OK was given when execution was chosen.
- Migration name: `supabase/migrations/20261011_reservation_limits_and_expiry.sql`. No explicit `begin`/`commit` in it. Safe to run again.
- Hold choices exactly 15, 30, 60, 120 minutes; 30 selected first.
- Deadline = earliest of (later of now and pickup start) + hold, pickup end (if set), expiry.
- Limit: NULL or 1..10,000 (constraint), and at most the quantity **on insert only** (trigger).
- Counted toward the limit: the caller's `RESERVED` + `CLAIMED` quantity on that listing.
- Expiry: `status = 'CANCELLED'`, `cancel_reason = 'PICKUP_EXPIRED'`; alerts type `RESERVATION_CANCELLED`, title "Reservation expired"; no clock times in alert texts.
- Every new function: `revoke all … from public`, `revoke all … from anon`; `grant execute … to authenticated` only for `create_reservation_with_hold`; nothing for `expire_overdue_reservations`.
- Keep in `create_reservation`: listing lock, every 20261009 check and text, the rate limit (`salusalo-reserve:` advisory lock, 10 per 10 minutes).
- `NewFoodPost.maxPerRecipient` has no default (always sent).
- Exact user texts (copy verbatim):
  - "You've reserved the most this listing allows per person (3)." (number varies)
  - "This listing allows 3 per person. You can reserve 1 more."
  - "The maximum per person must be from 1 to the quantity."
  - "Choose how soon you'll pick up: 15, 30, 60 or 120 minutes."
  - "The pickup time for this reservation has passed."
  - "Enter a maximum per person from 1 to the quantity, or leave it blank."
  - "If it isn't marked as picked up by then, the reservation expires and the food goes back on the listing."
  - "You didn't pick up in time, so the reservation expired and the food went back on the listing."
  - "<name> didn't pick up in time, so the reservation expired."
  - "The pickup time has passed."
- No new library. Java isn't on PATH: build with
  `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat <tasks> --offline` (PowerShell, repo root).

## Review Focus

1. A listing whose pickup has already started but ends in under 15 minutes: every choice gives the same deadline; the page must say "…, when pickup ends." (pinned in Task 2 and Task 3 tests).
2. A listing without a pickup window (older listings): deadline capped at the expiry, text "…, when the food expires." (Task 2, Task 3 tests).
3. The person already holds or picked up exactly the limit: the stepper must offer 0 and the page shows the "most this listing allows" text instead of Reserve, never a stepper at 1 (Task 2 test `reservableNow` with held = limit and held > limit).
4. The quantity left drops below the limit after other people reserve: the listing's own reservations must keep working. The limit is compared with the quantity only in the insert branch of `food_posts_check_values()`, never in a table constraint (Task 1 Step 5 read-check; two-phone test step 4 reserves until 3 of 5 are left with a limit of 2, and step 7 adds the quantity back).
5. A deadline that passed while Activity is open but the job hasn't run yet: show "Time's up", hide Mark as picked up, reload once ~70 s later, never loop reloads (Task 3 test for the text; Task 7 uses a single keyed effect).

---

## File Structure

| File | Change |
|---|---|
| `supabase/migrations/20261011_reservation_limits_and_expiry.sql` | Create: all database changes. |
| `supabase/tests/reservation_limits_live_safety_check.sql` | Create: refusal and expiry checks; always ends with an error that rolls everything back. |
| `supabase/tests/reservation_limits_check.sql` | Create: read-only check after applying. |
| `supabase/README.md` | Modify: run order, Cron step. |
| `app/src/main/java/com/example/salusalo/model/FoodPost.kt` | Modify: limit fields; `HOLD_MINUTES`, `pickupDeadlineEstimate`, `reservableNow`. |
| `app/src/main/java/com/example/salusalo/model/Reservation.kt` | Modify: `pickupDeadline`, `PICKUP_EXPIRED`. |
| `app/src/main/java/com/example/salusalo/UiText.kt` | Modify: `pickUpByTime`, `holdEstimateText`, `deadlineLine`. |
| `app/src/test/java/com/example/salusalo/ReservationLimitsTest.kt` | Create: tests for the rules and texts. |
| `app/src/main/java/com/example/salusalo/data/ReservationRepository.kt` | Modify: `create_reservation_with_hold`, `heldQuantity`. |
| `app/src/main/java/com/example/salusalo/data/FoodRepository.kt` | Modify: send the limit. |
| `app/src/main/java/com/example/salusalo/viewmodel/ReservationViewModel.kt` | Modify: `reserve(…, holdMinutes)`, `heldQuantity`. |
| `app/src/main/java/com/example/salusalo/PostScreen.kt` | Modify: limit field, check, checklist, Edit note. |
| `app/src/main/java/com/example/salusalo/MainActivity.kt` | Modify: details page limit and stepper; pass hold. |
| `app/src/main/java/com/example/salusalo/FoodActions.kt` | Modify: "Pick up within" card, estimate, receipt line. |
| `app/src/main/java/com/example/salusalo/ActivityScreen.kt` | Modify: deadline lines, minute ticker, one reload, Expired badge and notes, hide late Mark as picked up. |
| `docs/PROJECT_STATUS.md`, `CLAUDE.md`, `README.md` | Modify: §24, state, rules, test list. |

---

### Task 1: The migration and its SQL checks

**Files:**
- Create: `supabase/migrations/20261011_reservation_limits_and_expiry.sql`
- Create: `supabase/tests/reservation_limits_live_safety_check.sql`
- Create: `supabase/tests/reservation_limits_check.sql`
- Modify: `supabase/README.md` (append a section)

**Interfaces:**
- Produces (SQL): `food_posts.max_per_recipient integer`, `reservations.pickup_deadline timestamptz`, `create_reservation_with_hold(p_food_post_id uuid, p_quantity integer, p_unit_price numeric, p_hold_minutes integer) returns reservations`, `get_donor_reservations()` with an extra `pickup_deadline timestamptz` column, `expire_overdue_reservations() returns integer`, Cron job `salusalo-expire-reservations`.

There is no local PostgreSQL; these files are tested by the developer in the SQL Editor (Task 8 lists the order). The steps here write them and check them by reading.

- [ ] **Step 1: Write the migration**

Create `supabase/migrations/20261011_reservation_limits_and_expiry.sql`:

```sql
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
```

- [ ] **Step 2: Write the live safety check**

Create `supabase/tests/reservation_limits_live_safety_check.sql`:

```sql
-- Salu-Salo reservation limits and pickup deadlines: safety check for the live
-- database (PROJECT_STATUS.md §24).
--
-- Run it in the Supabase dashboard after migrations/20261011_reservation_limits_and_expiry.sql
-- (or pasted right below that file for the trial run): SQL Editor -> New
-- query -> paste -> Run. Leave the role on "postgres".
--
-- It takes the donor of the newest listing (or the newest account) and the
-- newest other account (the "recipient"). As the donor it posts three test
-- listings; as the recipient it reserves; as postgres it moves deadlines into
-- the past and runs the expiry job. The "Sanity" checks must succeed: they
-- prove that the pretend sign-in works, so the refusals are real. A check that
-- cannot run reports SKIP. The recipient makes 4 reservations, so it fails if
-- that account already made 7 or more in the last 10 minutes (rate limit).
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the
-- check did (and, in the trial run, the migration pasted above it), so nothing
-- is saved, no alert is kept and no phone gets a live update. The error text is
-- the report; it starts with "SAFETY CHECK FINISHED". Any other error means the
-- check itself did not run.

do $$
declare
  v_donor      public.users;
  v_rec        public.users;
  v_r1         public.reservations;   -- 2 on listing 1, 30-minute hold
  v_r2         public.reservations;   -- 1 on listing 1, old function
  v_r4         public.reservations;   -- listing 2, pickup starts later
  v_r5         public.reservations;   -- listing 3, pickup ends soon
  v_tmp        public.reservations;
  c_donor      text;
  c_rec        text;
  c_iss        text := 'https://securetoken.google.com/salusalo';
  v_l1         uuid := gen_random_uuid();
  v_l2         uuid := gen_random_uuid();
  v_l3         uuid := gen_random_uuid();
  v_qty        integer;
  v_qty2       integer;
  v_status     text;
  v_reason     text;
  n            bigint;
  v_label      text;
  v_ok         boolean;
  v_note       text;
  a_label      text[] := '{}';
  a_ok         boolean[] := '{}';
  a_note       text[] := '{}';
  v_report     text := '';
  v_pass       integer := 0;
  v_fail       integer := 0;
  v_skip       integer := 0;
begin
  select u.* into v_donor from public.users u
  join public.food_posts p on p.donor_id = u.id
  order by p.created_at desc limit 1;
  if v_donor.id is null then
    select * into v_donor from public.users order by created_at desc limit 1;
  end if;
  select * into v_rec from public.users
  where id is distinct from v_donor.id order by created_at desc limit 1;

  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_rec := json_build_object('sub', v_rec.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;

  -- 1
  v_label := 'Sanity: the donor posts 10 packs with a limit of 3 per person';
  v_ok := null; v_note := 'fewer than two accounts';
  if v_donor.id is not null and v_rec.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end, max_per_recipient)
      values (v_l1, v_donor.id, 'Safety check: limits 1', 'Food', 10, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours', 3);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l2, v_donor.id, 'Safety check: limits 2', 'Food', 5, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() + interval '1 hour', now() + interval '3 hours');
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l3, v_donor.id, 'Safety check: limits 3', 'Food', 5, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '10 minutes');
      perform set_config('role', 'postgres', true);
      select max_per_recipient into v_qty from public.food_posts where id = v_l1;
      v_ok := v_qty = 3;
      v_note := format('stored limit %s', v_qty);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2, 3
  for i in 1 .. 2 loop
    v_label := case i when 1 then 'A limit of 0 is refused' else 'A limit above the quantity (11 of 10) is refused' end;
    v_ok := null; v_note := 'fewer than two accounts';
    if v_donor.id is not null then
      v_ok := false;
      begin
        perform set_config('request.jwt.claims', c_donor, true);
        perform set_config('role', 'authenticated', true);
        insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
          expiration_date, status, price, max_per_recipient)
        values (gen_random_uuid(), v_donor.id, 'Safety check: bad limit', 'Food', 10, 'packs', 'Safety check plaza',
          now() + interval '1 day', 'AVAILABLE', 0, case i when 1 then 0 else 11 end);
        perform set_config('role', 'postgres', true);
        v_note := 'it was saved';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
        v_ok := case i when 1 then sqlerrm like '%food_posts_max_per_recipient_check%'
                       else sqlerrm = 'The maximum per person must be from 1 to the quantity.' end;
      end;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  -- 4
  v_label := 'Sanity: the recipient reserves 2 with a 30-minute hold; the deadline is now + 30 minutes';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      select * into v_r1 from public.create_reservation_with_hold(v_l1, 2, 0, 30);
      perform set_config('role', 'postgres', true);
      v_ok := v_r1.status = 'RESERVED' and v_r1.pickup_deadline = now() + interval '30 minutes';
      v_note := format('%s, deadline %s', v_r1.status, v_r1.pickup_deadline);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5, 6
  for i in 1 .. 2 loop
    v_label := case i when 1 then 'A 20-minute hold is refused'
                      else 'Reserving 2 more (4 of a limit of 3) is refused with "You can reserve 1 more."' end;
    v_ok := null; v_note := 'check 4 did not pass';
    if v_r1.id is not null then
      v_ok := false;
      begin
        perform set_config('request.jwt.claims', c_rec, true);
        perform set_config('role', 'authenticated', true);
        perform public.create_reservation_with_hold(v_l1, case i when 1 then 1 else 2 end, 0, case i when 1 then 20 else 30 end);
        perform set_config('role', 'postgres', true);
        v_note := 'it was reserved';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
        v_ok := sqlerrm = case i when 1 then 'Choose how soon you''ll pick up: 15, 30, 60 or 120 minutes.'
                                 else 'This listing allows 3 per person. You can reserve 1 more.' end;
      end;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  -- 7
  v_label := 'Sanity: an old-style create_reservation of 1 still works, sets no deadline, and the donor''s list has pickup_deadline';
  v_ok := null; v_note := 'check 4 did not pass';
  if v_r1.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      select * into v_r2 from public.create_reservation(v_l1, 1);
      perform set_config('request.jwt.claims', c_donor, true);
      select count(*) into n from public.get_donor_reservations() d
      where d.id = v_r1.id and d.pickup_deadline = v_r1.pickup_deadline;
      perform set_config('role', 'postgres', true);
      v_ok := v_r2.status = 'RESERVED' and v_r2.pickup_deadline is null and n = 1;
      v_note := format('%s, deadline %s, donor rows %s', v_r2.status, coalesce(v_r2.pickup_deadline::text, 'none'), n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'At the limit (3 of 3), one more is refused with "the most this listing allows"';
  v_ok := null; v_note := 'check 7 did not pass';
  if v_r2.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      perform public.create_reservation_with_hold(v_l1, 1, 0, 15);
      perform set_config('role', 'postgres', true);
      v_note := 'it was reserved';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
      v_ok := sqlerrm = 'You''ve reserved the most this listing allows per person (3).';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9, 10
  for i in 1 .. 2 loop
    v_label := case i when 1 then 'Sanity: pickup starts in 1 hour, 60-minute hold: the deadline is now + 2 hours'
                      else 'Sanity: pickup ends in 10 minutes, 120-minute hold: the deadline is the end of pickup' end;
    v_ok := null; v_note := 'check 1 did not pass';
    if a_ok[1] then
      v_ok := false;
      begin
        perform set_config('request.jwt.claims', c_rec, true);
        perform set_config('role', 'authenticated', true);
        select * into v_tmp from public.create_reservation_with_hold(case i when 1 then v_l2 else v_l3 end, 1, 0,
          case i when 1 then 60 else 120 end);
        perform set_config('role', 'postgres', true);
        v_ok := v_tmp.pickup_deadline = now() + case i when 1 then interval '2 hours' else interval '10 minutes' end;
        v_note := format('deadline %s', v_tmp.pickup_deadline);
        if i = 1 then v_r4 := v_tmp; else v_r5 := v_tmp; end if;
      exception when others then
        perform set_config('role', 'postgres', true);
        v_note := sqlerrm;
      end;
    end if;
    a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);
  end loop;

  -- 11
  v_label := 'A signed-in app can''t change pickup_deadline directly';
  v_ok := null; v_note := 'check 4 did not pass';
  if v_r1.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      update public.reservations set pickup_deadline = now() + interval '1 day' where id = v_r1.id;
      perform set_config('role', 'postgres', true);
      v_note := 'the update was allowed';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
      v_ok := sqlerrm like 'permission denied%';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 12
  v_label := 'No app (anon or authenticated) may run expire_overdue_reservations(); authenticated may run create_reservation_with_hold, anon may not';
  v_ok := not has_function_privilege('anon', 'public.expire_overdue_reservations()', 'execute')
      and not has_function_privilege('authenticated', 'public.expire_overdue_reservations()', 'execute')
      and has_function_privilege('authenticated', 'public.create_reservation_with_hold(uuid,integer,numeric,integer)', 'execute')
      and not has_function_privilege('anon', 'public.create_reservation_with_hold(uuid,integer,numeric,integer)', 'execute');
  v_note := case when v_ok then 'as expected' else 'a grant is wrong' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 13
  v_label := 'After the deadline, the donor can''t mark it picked up';
  v_ok := null; v_note := 'check 4 did not pass';
  if v_r1.id is not null then
    v_ok := false;
    update public.reservations set pickup_deadline = now() - interval '1 minute' where id = v_r1.id;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.claim_reservation(v_r1.id);
      perform set_config('role', 'postgres', true);
      v_note := 'it was claimed';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
      v_ok := sqlerrm = 'The pickup time for this reservation has passed.';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 14, 15
  v_label := 'The job expires the overdue reservation (PICKUP_EXPIRED) and gives its 2 packs back';
  v_ok := null; v_note := 'check 13 did not run';
  if v_r1.id is not null then
    v_ok := false;
    select quantity into v_qty from public.food_posts where id = v_l1;
    begin
      perform public.expire_overdue_reservations();
      select status, cancel_reason into v_status, v_reason from public.reservations where id = v_r1.id;
      select quantity into v_qty2 from public.food_posts where id = v_l1;
      v_ok := v_status = 'CANCELLED' and v_reason = 'PICKUP_EXPIRED' and v_qty2 = v_qty + 2;
      v_note := format('%s / %s, quantity %s -> %s', v_status, coalesce(v_reason, 'none'), v_qty, v_qty2);
    exception when others then
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  v_label := 'Running the job again gives nothing back twice';
  v_ok := null; v_note := 'check 14 did not pass';
  if a_ok[14] then
    v_ok := false;
    perform public.expire_overdue_reservations();
    select quantity into v_qty from public.food_posts where id = v_l1;
    v_ok := v_qty = v_qty2;
    v_note := format('quantity %s', v_qty);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 16
  v_label := 'Expiry alerts: one "Reservation expired" each for the recipient and the donor (if their reservation alerts are on)';
  v_ok := null; v_note := 'check 14 did not pass';
  if a_ok[14] then
    select count(*) into n from public.notifications
    where reservation_id = v_r1.id and title = 'Reservation expired'
      and user_id in (v_rec.id, v_donor.id);
    select count(*) into v_qty from public.notification_preferences
    where user_id in (v_rec.id, v_donor.id) and not reservation_alerts;
    v_ok := n = 2 - v_qty;
    v_note := format('%s alerts, %s of the two have reservation alerts off', n, v_qty);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 17
  v_label := 'A REMOVED listing stays REMOVED when its reservation expires';
  v_ok := null; v_note := 'check 10 did not pass';
  if v_r5.id is not null then
    v_ok := false;
    update public.food_posts set status = 'REMOVED' where id = v_l3;
    update public.reservations set pickup_deadline = now() - interval '1 minute' where id = v_r5.id;
    perform public.expire_overdue_reservations();
    select status into v_status from public.food_posts where id = v_l3;
    select status into v_reason from public.reservations where id = v_r5.id;
    v_ok := v_status = 'REMOVED' and v_reason = 'CANCELLED';
    v_note := format('listing %s, reservation %s', v_status, v_reason);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 18
  v_label := 'A cancel after the deadline but before the job gives the quantity back once';
  v_ok := null; v_note := 'check 9 did not pass';
  if v_r4.id is not null then
    v_ok := false;
    update public.reservations set pickup_deadline = now() - interval '1 minute' where id = v_r4.id;
    select quantity into v_qty from public.food_posts where id = v_l2;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      perform public.cancel_reservation(v_r4.id);
      perform set_config('role', 'postgres', true);
      perform public.expire_overdue_reservations();
      select quantity into v_qty2 from public.food_posts where id = v_l2;
      select cancel_reason into v_reason from public.reservations where id = v_r4.id;
      v_ok := v_qty2 = v_qty + 1 and v_reason is null;
      v_note := format('quantity %s -> %s, reason %s', v_qty, v_qty2, coalesce(v_reason, 'none'));
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 19
  v_label := 'create_reservation still has the rate limit, the pickup-end check and the limit';
  select prosrc like '%salusalo-reserve:%' and prosrc like '%pickup_end%' and prosrc like '%max_per_recipient%'
  into v_ok from pg_proc where oid = 'public.create_reservation(uuid,integer)'::regprocedure;
  v_note := case when v_ok then 'all three present' else 'one is missing' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 20
  v_label := 'The Cron job salusalo-expire-reservations exists and is active';
  v_ok := false;
  begin
    execute 'select count(*) from cron.job where jobname = $1 and active and schedule = $2'
      into n using 'salusalo-expire-reservations', '* * * * *';
    v_ok := n = 1;
    v_note := format('%s job(s)', n);
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- Report. Raising an error here undoes everything above.
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

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Recipient: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_rec.name, 'none'), v_report);
end $$;
```

- [ ] **Step 3: Write the read-only check**

Create `supabase/tests/reservation_limits_check.sql`:

```sql
-- Salu-Salo reservation limits and pickup deadlines: read-only check
-- (PROJECT_STATUS.md §24). Changes nothing. Run in the SQL Editor after the
-- trial run (every row should say MISSING/false: nothing stayed) and after
-- applying migrations/20261011_reservation_limits_and_expiry.sql (every "ok"
-- should be true).

select 'food_posts.max_per_recipient exists' as check_name,
       exists (select 1 from information_schema.columns where table_schema = 'public'
               and table_name = 'food_posts' and column_name = 'max_per_recipient') as ok
union all
select 'reservations.pickup_deadline exists',
       exists (select 1 from information_schema.columns where table_schema = 'public'
               and table_name = 'reservations' and column_name = 'pickup_deadline')
union all
select 'cancel reasons include PICKUP_EXPIRED',
       coalesce((select pg_get_constraintdef(oid) like '%PICKUP_EXPIRED%' from pg_constraint
                 where conrelid = 'public.reservations'::regclass and conname = 'reservations_cancel_reason_check'), false)
union all
select 'create_reservation_with_hold: authenticated yes, anon no',
       to_regprocedure('public.create_reservation_with_hold(uuid,integer,numeric,integer)') is not null
       and has_function_privilege('authenticated', 'public.create_reservation_with_hold(uuid,integer,numeric,integer)', 'execute')
       and not has_function_privilege('anon', 'public.create_reservation_with_hold(uuid,integer,numeric,integer)', 'execute')
union all
select 'expire_overdue_reservations: no app may run it',
       to_regprocedure('public.expire_overdue_reservations()') is not null
       and not has_function_privilege('authenticated', 'public.expire_overdue_reservations()', 'execute')
       and not has_function_privilege('anon', 'public.expire_overdue_reservations()', 'execute')
union all
select 'create_reservation keeps the rate limit and pickup-end check, and has the limit',
       (select prosrc like '%salusalo-reserve:%' and prosrc like '%pickup_end%' and prosrc like '%max_per_recipient%'
        from pg_proc where oid = 'public.create_reservation(uuid,integer)'::regprocedure)
union all
select 'claim_reservation refuses after the deadline',
       (select prosrc like '%pickup_deadline%' from pg_proc where oid = 'public.claim_reservation(uuid)'::regprocedure)
union all
select 'get_donor_reservations returns pickup_deadline',
       (select pg_get_function_result(oid) like '%pickup_deadline%' from pg_proc
        where oid = 'public.get_donor_reservations()'::regprocedure)
union all
select 'pg_cron is installed',
       exists (select 1 from pg_extension where extname = 'pg_cron');

-- Only when the last row is true, run this too: the job and its latest runs.
-- select jobname, schedule, active from cron.job where jobname = 'salusalo-expire-reservations';
-- select status, return_message, start_time from cron.job_run_details d
-- join cron.job j on j.jobid = d.jobid where j.jobname = 'salusalo-expire-reservations'
-- order by start_time desc limit 5;
```

- [ ] **Step 4: Add the README note**

Append to `supabase/README.md` a section in the style of the existing entries:

```markdown
### 20261011_reservation_limits_and_expiry.sql (per-person limits and pickup deadlines, PROJECT_STATUS.md §24)

Run **before** installing the build that reserves with a pickup time (`create_reservation_with_hold`). Older builds keep working.

1. **Trial run (nothing is kept):** paste this migration and, below it, `tests/reservation_limits_live_safety_check.sql` into one SQL Editor query and run it. It ends with "SAFETY CHECK FINISHED: …" on purpose. Then run `tests/reservation_limits_check.sql`: every row should be false (nothing stayed). If rows are true, the editor committed the migration; that is harmless and counts as step 2.
2. **Apply:** run this file alone. It also turns on Supabase Cron (`pg_cron`) and schedules the job `salusalo-expire-reservations` (every minute). If the extension is refused, switch on Integrations → Cron in the dashboard and run the file again.
3. **Check:** run `tests/reservation_limits_check.sql` (every row true), then the two commented queries at its end (the job is active and has succeeded at least once), then `tests/reservation_limits_live_safety_check.sql` alone (all PASS).

Never grant EXECUTE on `expire_overdue_reservations()` to any client role; keep the limit check and the rate limit in `create_reservation`.
```

- [ ] **Step 5: Read-check the SQL**

Check by reading: every function in the migration has the revoke/grant lines from Global Constraints; the migration has no `begin`/`commit`; the texts match Global Constraints exactly; check numbers 1–20 in the safety check match the `a_ok[n]` indexes they reference (`a_ok[1]`, `a_ok[14]`).

- [ ] **Step 6: Commit**

```bash
git add supabase/migrations/20261011_reservation_limits_and_expiry.sql supabase/tests/reservation_limits_live_safety_check.sql supabase/tests/reservation_limits_check.sql supabase/README.md
git commit -m "Add per-person limits, pickup deadlines and the expiry job (SQL)"
```

---

### Task 2: Model fields and the pure rules

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/model/FoodPost.kt`
- Modify: `app/src/main/java/com/example/salusalo/model/Reservation.kt`
- Test: `app/src/test/java/com/example/salusalo/ReservationLimitsTest.kt` (create)

**Interfaces:**
- Produces: `FoodPost.maxPerRecipient: Int?`, `NewFoodPost.maxPerRecipient: Int?` (no default), `FoodPostDraft.maxPerRecipient: Int? = null`, `val HOLD_MINUTES: List<Int>`, `const val DEFAULT_HOLD_MINUTES = 30`, `fun pickupDeadlineEstimate(now: Instant, pickupStart: Instant?, pickupEnd: Instant?, expiration: Instant, holdMinutes: Int): Instant`, `fun reservableNow(limit: Int?, available: Int, held: Int): Int`, `Reservation.pickupDeadline: String?`, `DonorReservationRow.pickupDeadline`, `const val CANCEL_REASON_PICKUP_EXPIRED`, `Reservation.expiredUnpicked: Boolean`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/example/salusalo/ReservationLimitsTest.kt`:

```kotlin
package com.example.salusalo

import com.example.salusalo.model.pickupDeadlineEstimate
import com.example.salusalo.model.reservableNow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The phone's deadline estimate and "how many can you still reserve" follow the server's rules. */
class ReservationLimitsTest {
    private val now = Instant.parse("2026-10-03T04:00:00Z")
    private val expiry = Instant.parse("2026-10-04T04:00:00Z")
    private fun at(minutesFromNow: Long) = now.plusSeconds(minutesFromNow * 60)

    @Test
    fun theHoldStartsNowWhenPickupHasStarted() {
        assertEquals(at(30), pickupDeadlineEstimate(now, at(-60), at(180), expiry, 30))
    }

    @Test
    fun theHoldStartsAtPickupStartWhenReservingAhead() {
        assertEquals(at(120), pickupDeadlineEstimate(now, at(60), at(180), expiry, 60))
    }

    @Test
    fun theDeadlineNeverPassesTheEndOfPickup() {
        assertEquals(at(10), pickupDeadlineEstimate(now, at(-60), at(10), expiry, 120))
    }

    @Test
    fun withoutAPickupWindowTheExpiryCapsIt() {
        assertEquals(at(20), pickupDeadlineEstimate(now, null, null, at(20), 60))
        assertEquals(at(15), pickupDeadlineEstimate(now, null, null, expiry, 15))
    }

    @Test
    fun withoutALimitEverythingLeftCanBeReserved() {
        assertEquals(7, reservableNow(limit = null, available = 7, held = 5))
    }

    @Test
    fun theLimitCountsWhatThePersonAlreadyHolds() {
        assertEquals(1, reservableNow(limit = 3, available = 7, held = 2))
        assertEquals(3, reservableNow(limit = 3, available = 7, held = 0))
        assertEquals(2, reservableNow(limit = 3, available = 2, held = 0))
    }

    @Test
    fun atOrOverTheLimitNothingIsLeft() {
        assertEquals(0, reservableNow(limit = 3, available = 7, held = 3))
        assertEquals(0, reservableNow(limit = 3, available = 7, held = 4))
        assertEquals(0, reservableNow(limit = null, available = 0, held = 0))
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.salusalo.ReservationLimitsTest" --offline` (with `JAVA_HOME` as in Global Constraints)
Expected: compilation FAILS with "Unresolved reference 'pickupDeadlineEstimate'".

- [ ] **Step 3: Add the model fields and rules**

In `model/FoodPost.kt`:

1. In `data class FoodPost`, after `@SerialName("pickup_longitude") val pickupLongitude: Double? = null,` add:
```kotlin
    /** The most one person may reserve (open plus picked up); null = no limit. Set on Post only. */
    @SerialName("max_per_recipient") val maxPerRecipient: Int? = null,
```
2. In `data class NewFoodPost`, after `@SerialName("pickup_longitude") val pickupLongitude: Double,` add:
```kotlin
    // No default either, so it is always sent (null for no limit).
    @SerialName("max_per_recipient") val maxPerRecipient: Int?,
```
3. In `data class FoodPostDraft`, change `val pickupPin: GeoPin` to:
```kotlin
    val pickupPin: GeoPin,
    val maxPerRecipient: Int? = null
```
4. At the end of the file add:
```kotlin
/** How long a recipient may ask for a reservation to be held (create_reservation_with_hold). */
val HOLD_MINUTES = listOf(15, 30, 60, 120)
const val DEFAULT_HOLD_MINUTES = 30

/**
 * The deadline create_reservation_with_hold() will set: the hold counted from
 * now or from the pickup start, whichever is later, but never after the end of
 * pickup or the expiry. The server's own value is what counts.
 */
fun pickupDeadlineEstimate(
    now: java.time.Instant,
    pickupStart: java.time.Instant?,
    pickupEnd: java.time.Instant?,
    expiration: java.time.Instant,
    holdMinutes: Int
): java.time.Instant {
    val start = if (pickupStart != null && pickupStart.isAfter(now)) pickupStart else now
    return listOfNotNull(start.plusSeconds(holdMinutes * 60L), pickupEnd, expiration).min()
}

/** How many one person may reserve now: what's left, within the listing's per-person limit. */
fun reservableNow(limit: Int?, available: Int, held: Int): Int =
    (if (limit == null) available else minOf(available, limit - held)).coerceAtLeast(0)
```

In `model/Reservation.kt`:

1. Change the end of `data class Reservation` to:
```kotlin
    @SerialName("cancel_reason") val cancelReason: String? = null,
    /** When it expires if not picked up; null for reservations made without a hold (never expire). */
    @SerialName("pickup_deadline") val pickupDeadline: String? = null
) {
    val cancelledByListingRemoval: Boolean get() = cancelReason == CANCEL_REASON_LISTING_REMOVED
    val releasedByDonor: Boolean get() = cancelReason == CANCEL_REASON_DONOR_RELEASED
    val expiredUnpicked: Boolean get() = cancelReason == CANCEL_REASON_PICKUP_EXPIRED
}
```
(update the KDoc above `cancelReason` to also name `PICKUP_EXPIRED`: "…, PICKUP_EXPIRED when not picked up by its deadline; null otherwise.")
2. After `const val CANCEL_REASON_DONOR_RELEASED = "DONOR_RELEASED"` add:
```kotlin
const val CANCEL_REASON_PICKUP_EXPIRED = "PICKUP_EXPIRED"
```
3. In `DonorReservationRow`, after `@SerialName("cancel_reason") val cancelReason: String? = null` add `,` and
```kotlin
    @SerialName("pickup_deadline") val pickupDeadline: String? = null
```
and change `toReservation()` to:
```kotlin
    fun toReservation() =
        Reservation(id, foodPostId, recipientId, quantity, status, createdAt, updatedAt, unitPrice, cancelReason, pickupDeadline)
```

- [ ] **Step 4: Make the app compile (FoodRepository) and run the tests**

In `data/FoodRepository.kt`, in the `NewFoodPost(` call, change the last argument line `pickupLongitude = pin.longitude` to:
```kotlin
            pickupLongitude = pin.longitude,
            maxPerRecipient = draft.maxPerRecipient
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.salusalo.ReservationLimitsTest" --offline`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/salusalo/model/FoodPost.kt app/src/main/java/com/example/salusalo/model/Reservation.kt app/src/main/java/com/example/salusalo/data/FoodRepository.kt app/src/test/java/com/example/salusalo/ReservationLimitsTest.kt
git commit -m "Add the limit and deadline fields and their rules"
```

---

### Task 3: Deadline texts

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/UiText.kt`
- Test: `app/src/test/java/com/example/salusalo/ReservationLimitsTest.kt`

**Interfaces:**
- Consumes: `pickupDeadlineEstimate` (Task 2).
- Produces: `internal fun pickUpByTime(deadline: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String`, `internal fun holdEstimateText(deadline: Instant, pickupStart: Instant?, pickupEnd: Instant?, expiration: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String`, `internal fun deadlineLine(deadline: Instant, pickupStart: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String`, `internal fun deadlineUrgent(deadline: Instant, now: Instant): Boolean`.

- [ ] **Step 1: Write the failing tests**

Append inside `class ReservationLimitsTest` (add `import java.time.ZoneId`):

```kotlin
    private val manila = ZoneId.of("Asia/Manila")   // now = 12:00 PM there

    @Test
    fun pickUpByShowsTheTimeTodayAndTheDayOtherwise() {
        assertEquals("12:30 PM", pickUpByTime(at(30), now, manila))
        assertEquals("Tomorrow, 9:00 AM", pickUpByTime(Instant.parse("2026-10-04T01:00:00Z"), now, manila))
    }

    @Test
    fun theEstimateSaysWhyTheDeadlineIsWhatItIs() {
        assertEquals("Pick up by 12:30 PM.", holdEstimateText(at(30), at(-60), at(180), expiry, now, manila))
        assertEquals(
            "Pickup starts at 1:00 PM, so pick up by 2:00 PM.",
            holdEstimateText(at(120), at(60), at(180), expiry, now, manila)
        )
        assertEquals("Pick up by 12:10 PM, when pickup ends.", holdEstimateText(at(10), at(-60), at(10), expiry, now, manila))
        assertEquals("Pick up by 12:20 PM, when the food expires.", holdEstimateText(at(20), null, null, at(20), now, manila))
    }

    @Test
    fun theActivityLineCountsDownAndThenSaysTimesUp() {
        assertEquals("Pick up by 12:30 PM · 30 min left", deadlineLine(at(30), at(-60), now, manila))
        assertEquals("Pick up by 1:30 PM · 1 h 30 min left", deadlineLine(at(90), at(-60), now, manila))
        assertEquals("Pick up by 12:00 PM · less than a minute left", deadlineLine(now.plusSeconds(20), null, now, manila))
        assertEquals("Pick up by 2:00 PM · pickup starts 1:00 PM", deadlineLine(at(120), at(60), now, manila))
        assertEquals("Pick up by 11:59 AM · Time's up", deadlineLine(at(-1), null, now, manila))
    }

    @Test
    fun underTenMinutesIsUrgent() {
        assertEquals(true, deadlineUrgent(at(9), now))
        assertEquals(false, deadlineUrgent(at(11), now))
        assertEquals(true, deadlineUrgent(at(-1), now))
    }
```

- [ ] **Step 2: Run them to see them fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.salusalo.ReservationLimitsTest" --offline`
Expected: compilation FAILS with "Unresolved reference 'pickUpByTime'".

- [ ] **Step 3: Add the texts**

Append to `UiText.kt`:

```kotlin
/** A pickup deadline: "5:30 PM" today, otherwise "Tomorrow, 9:30 AM" or "Oct 5, 9:30 AM". */
internal fun pickUpByTime(deadline: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = deadline.atZone(zone)
    return if (at.toLocalDate() == now.atZone(zone).toLocalDate()) clockTime.format(at)
    else dayLabel(at.toLocalDate(), now.atZone(zone).toLocalDate()) + ", " + clockTime.format(at)
}

/** Review reservation: the estimated deadline and why it is that time. */
internal fun holdEstimateText(
    deadline: Instant,
    pickupStart: Instant?,
    pickupEnd: Instant?,
    expiration: Instant,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val by = pickUpByTime(deadline, now, zone)
    return when {
        pickupEnd != null && deadline == pickupEnd -> "Pick up by $by, when pickup ends."
        deadline == expiration -> "Pick up by $by, when the food expires."
        pickupStart != null && pickupStart.isAfter(now) ->
            "Pickup starts at ${pickUpByTime(pickupStart, now, zone)}, so pick up by $by."
        else -> "Pick up by $by."
    }
}

/** Activity: "Pick up by 5:30 PM · 12 min left", "… · pickup starts 5:00 PM" or "… · Time's up". */
internal fun deadlineLine(deadline: Instant, pickupStart: Instant?, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val head = "Pick up by " + pickUpByTime(deadline, now, zone)
    if (!deadline.isAfter(now)) return "$head · Time's up"
    if (pickupStart != null && pickupStart.isAfter(now)) return "$head · pickup starts ${pickUpByTime(pickupStart, now, zone)}"
    val minutes = Duration.between(now, deadline).toMinutes()
    return head + " · " + when {
        minutes < 1 -> "less than a minute left"
        minutes < 60 -> "$minutes min left"
        else -> "${minutes / 60} h ${minutes % 60} min left"
    }
}

/** True under 10 minutes before the deadline, and after it. */
internal fun deadlineUrgent(deadline: Instant, now: Instant): Boolean =
    Duration.between(now, deadline).toMinutes() < 10
```

Note for the 30-minute case: `Duration.toMinutes()` of exactly 30 minutes is 30, and of 90 minutes is 90 → "1 h 30 min left"; of 9 minutes is 9 → urgent; of 11 → not urgent.

- [ ] **Step 4: Run the tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.salusalo.ReservationLimitsTest" --offline`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/salusalo/UiText.kt app/src/test/java/com/example/salusalo/ReservationLimitsTest.kt
git commit -m "Add the pickup deadline texts"
```

---

### Task 4: Reserve with a hold, and the held quantity

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/data/ReservationRepository.kt`
- Modify: `app/src/main/java/com/example/salusalo/viewmodel/ReservationViewModel.kt:116-139`
- Modify: `app/src/main/java/com/example/salusalo/MainActivity.kt:3303` (temporary call; Task 6 replaces it)

**Interfaces:**
- Consumes: `DEFAULT_HOLD_MINUTES` (Task 2).
- Produces: `ReservationRepository.createReservation(foodPostId: String, quantity: Int, unitPrice: Double, holdMinutes: Int): Reservation`, `ReservationRepository.heldQuantity(foodPostId: String): Int`, `ReservationViewModel.reserve(foodPostId: String, quantity: Int, unitPrice: Double, note: String, holdMinutes: Int): Boolean`, `suspend fun ReservationViewModel.heldQuantity(foodPostId: String): Int?` (null when it can't be read).

- [ ] **Step 1: Repository**

In `ReservationRepository.kt`, replace `createReservation` with:

```kotlin
    /**
     * Reserves at [unitPrice], the price the recipient saw, held for
     * [holdMinutes] (15, 30, 60 or 120). The server refuses if the price has
     * changed, records its own price and sets the pickup deadline.
     */
    suspend fun createReservation(foodPostId: String, quantity: Int, unitPrice: Double, holdMinutes: Int): Reservation {
        require(quantity > 0) { "Choose a quantity greater than zero." }
        return postgrest.rpc("create_reservation_with_hold", buildJsonObject {
            put("p_food_post_id", foodPostId); put("p_quantity", quantity); put("p_unit_price", unitPrice)
            put("p_hold_minutes", holdMinutes)
        }).decodeAs()
    }

    /** How much of a listing this account holds or has picked up (counts toward its per-person limit). */
    suspend fun heldQuantity(foodPostId: String): Int {
        val profileId = userRepository.getCurrentProfileId()
        return supabase.from("reservations").select(Columns.list("quantity")) {
            filter {
                filter("food_post_id", FilterOperator.EQ, foodPostId)
                filter("recipient_id", FilterOperator.EQ, profileId)
                isIn("status", listOf("RESERVED", "CLAIMED"))
            }
        }.decodeList<HeldRow>().sumOf { it.quantity }
    }
```

At the end of the file (top level) add:

```kotlin
@kotlinx.serialization.Serializable
private data class HeldRow(val quantity: Int)
```

and the import `import io.github.jan.supabase.postgrest.query.Columns`.

- [ ] **Step 2: ViewModel**

In `ReservationViewModel.kt`, change the `reserve` signature and its repository call:

```kotlin
    fun reserve(foodPostId: String, quantity: Int, unitPrice: Double, note: String, holdMinutes: Int): Boolean = launchWrite {
        _reserveResult.value = null
        _reserveResult.value = try {
            val reservation = repository.createReservation(foodPostId, quantity, unitPrice, holdMinutes)
```

(the rest of the function unchanged; update its KDoc: "…at [unitPrice], the price shown (the server refuses if it has changed), held for [holdMinutes], then sends [note]…"). After `refreshIfStale()` add:

```kotlin
    /** The details page's per-person count; null when it can't be read (the server still checks). */
    suspend fun heldQuantity(foodPostId: String): Int? = try {
        repository.heldQuantity(foodPostId)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }
```

- [ ] **Step 3: Keep the app compiling**

In `MainActivity.kt` line 3303 change the call to:
```kotlin
            onConfirm = { note -> reservationViewModel.reserve(food.id, quantity, food.post.price, note, DEFAULT_HOLD_MINUTES) },
```
and add `import com.example.salusalo.model.DEFAULT_HOLD_MINUTES`.

- [ ] **Step 4: Build**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/salusalo/data/ReservationRepository.kt app/src/main/java/com/example/salusalo/viewmodel/ReservationViewModel.kt app/src/main/java/com/example/salusalo/MainActivity.kt
git commit -m "Reserve through create_reservation_with_hold and read the held quantity"
```

---

### Task 5: Post and Edit Listing

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/PostScreen.kt`

**Interfaces:**
- Consumes: `FoodPostDraft.maxPerRecipient`, `FoodPost.maxPerRecipient` (Task 2).

- [ ] **Step 1: State and validation**

After `var quantity by rememberSaveable { mutableStateOf("") }` (line ~127) add:
```kotlin
    var maxPerPerson by rememberSaveable { mutableStateOf("") }
```
In `validate()`, right after the line `count > 10_000 -> "Enter a quantity of at most 10,000."` add:
```kotlin
            // The database checks the same (food_posts_max_per_recipient_check and the insert trigger).
            maxPerPerson.isNotBlank() && maxPerPerson.trim().toIntOrNull().let { it == null || it < 1 || it > count } ->
                "Enter a maximum per person from 1 to the quantity, or leave it blank."
```

- [ ] **Step 2: The field**

Immediately after the `Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { … }` that holds "Quantity" and "Price per unit" (it closes just before `PostChoice(label = "Unit", …)`), add:
```kotlin
                    PostField(
                        label = "Maximum per person",
                        value = maxPerPerson,
                        onChange = { maxPerPerson = it },
                        example = "e.g., 3",
                        hint = "Optional. Leave blank for no limit.",
                        keyboard = KeyboardType.Number
                    )
```

- [ ] **Step 3: Send it, and show it in "Check your listing"**

In the `FoodPostDraft(` call, after `pickupPin = pin` add `,` and `maxPerRecipient = maxPerPerson.trim().toIntOrNull()`.

In "Check your listing", right after the `CheckSection("Quantity", …)` block add:
```kotlin
            CheckSection(
                "Maximum per person",
                listOf(
                    when {
                        maxPerPerson.isBlank() -> "No limit."
                        maxPerPerson.trim().toIntOrNull()
                            .let { it == null || it < 1 || count == null || it > count } ->
                            "Enter a maximum per person from 1 to the quantity, or leave it blank."
                        else -> "Maximum per person: ${maxPerPerson.trim()}."
                    }
                )
            )
```

- [ ] **Step 4: Edit Listing shows it**

In `EditListingDialog`, change the notice:
```kotlin
                InlineNotice(
                    text = "Quantity, unit and photos can't be changed." +
                        (post.maxPerRecipient?.let { " Maximum per person: $it (can't be changed)." } ?: ""),
                    icon = Icons.Outlined.Info
                )
```

- [ ] **Step 5: Build**

Run: `.\gradlew.bat :app:assembleDebug --offline`
Expected: BUILD SUCCESSFUL. If `PostField` has no `hint` parameter use, check its signature (`PostScreen.kt:1394`: `hint: String? = null` exists).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/salusalo/PostScreen.kt
git commit -m "Add Maximum per person to Post and show it on Edit Listing"
```

---

### Task 6: Details page, Review reservation and the receipt

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/MainActivity.kt` (`FoodDetailsScreen`, ~3159–3790)
- Modify: `app/src/main/java/com/example/salusalo/FoodActions.kt` (`ReservationReviewDialog`, 455–650)

**Interfaces:**
- Consumes: `reservableNow`, `HOLD_MINUTES`, `DEFAULT_HOLD_MINUTES`, `pickupDeadlineEstimate` (Task 2); `holdEstimateText`, `pickUpByTime` (Task 3); `ReservationViewModel.heldQuantity`, `reserve(…, holdMinutes)` (Task 4).
- Produces: `ReservationReviewDialog(…, pickupStart: Instant?, pickupEnd: Instant?, expiration: Instant?, …, onConfirm: (note: String, holdMinutes: Int) -> Unit, …)`.

- [ ] **Step 1: The details page counts the person's allowance**

In `FoodDetailsScreen`, replace
```kotlin
    var quantity by rememberSaveable(food.id) {
        mutableStateOf(1)
    }
    // A reload may leave less than the amount chosen.
    LaunchedEffect(available) { if (quantity > available) quantity = available.coerceAtLeast(1) }
```
with
```kotlin
    val limit = food.post.maxPerRecipient
    // How much of this listing the person already holds or picked up; read once
    // when the page opens and only for a listing with a limit (null while unknown).
    val held by produceState<Int?>(null, food.id, limit) {
        value = if (limit == null || isOwnFood) 0 else reservationViewModel.heldQuantity(food.id)
    }
    // A failed read counts as 0; the server still refuses over the limit.
    val reservable = reservableNow(limit, available, held ?: 0)
    var quantity by rememberSaveable(food.id) {
        mutableStateOf(1)
    }
    // A reload (or the count above) may leave less than the amount chosen.
    LaunchedEffect(reservable) { if (quantity > reservable) quantity = reservable.coerceAtLeast(1) }
```
Add imports `com.example.salusalo.model.reservableNow`, `com.example.salusalo.model.HOLD_MINUTES` is not needed here.

- [ ] **Step 2: The limit line and the at-limit notice**

In the bottom bar, change
```kotlin
                        } else if (blocker != null) {
                            InlineNotice(text = blocker, icon = Icons.Outlined.Info)
                        } else {
                            Text(payLine, style = SaluText.Supporting, color = SaluColors.DarkGreen)
```
to
```kotlin
                        } else if (blocker != null) {
                            InlineNotice(text = blocker, icon = Icons.Outlined.Info)
                        } else if (limit != null && held != null && reservable == 0) {
                            InlineNotice(
                                text = "You've reserved the most this listing allows per person ($limit).",
                                icon = Icons.Outlined.Info
                            )
                        } else {
                            if (limit != null) {
                                Text("Limit: $limit per person", style = SaluText.Meta, color = SaluColors.Gray)
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(payLine, style = SaluText.Supporting, color = SaluColors.DarkGreen)
```
and in the `QuantityStepper(` call change `available = available,` to `available = reservable.coerceAtLeast(1),`.

- [ ] **Step 3: Pass the times and the hold to Review reservation**

In the `ReservationReviewDialog(` call in `FoodDetailsScreen`, after `expires = expires,` add:
```kotlin
            pickupStart = parseTimestamp(post.pickupStart),
            pickupEnd = parseTimestamp(post.pickupEnd),
            expiration = parseTimestamp(post.expirationDate),
```
and replace the `onConfirm` line (from Task 4) with:
```kotlin
            // The price shown and the chosen hold; the server refuses if the price has changed.
            onConfirm = { note, holdMinutes ->
                reservationViewModel.reserve(food.id, quantity, food.post.price, note, holdMinutes)
            },
```
Remove the now-unused `import com.example.salusalo.model.DEFAULT_HOLD_MINUTES` from `MainActivity.kt` if nothing else uses it.

- [ ] **Step 4: Review reservation: "Pick up within" and the estimate**

In `FoodActions.kt`, `ReservationReviewDialog` parameters: after `expires: String,` add
```kotlin
    // The listing's times, for the pickup deadline estimate (null when not set).
    pickupStart: java.time.Instant?,
    pickupEnd: java.time.Instant?,
    expiration: java.time.Instant?,
```
and change `onConfirm: (note: String) -> Unit,` to `onConfirm: (note: String, holdMinutes: Int) -> Unit,`.

After `var note by rememberSaveable(foodPostId) { mutableStateOf("") }` add:
```kotlin
    var holdMinutes by rememberSaveable(foodPostId) { mutableIntStateOf(DEFAULT_HOLD_MINUTES) }
```

In the review branch (`} else {`), right after the first `ActionCard { … }` (the one ending with the price lines) and before `OutlinedTextField(`, add:
```kotlin
            ActionCard {
                Text("Pick up within", style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HOLD_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = holdMinutes == minutes,
                            onClick = { holdMinutes = minutes },
                            enabled = !saving,
                            label = { Text(if (minutes < 60) "$minutes min" else "${minutes / 60} hour" + if (minutes > 60) "s" else "") }
                        )
                    }
                }
                if (expiration != null) {
                    val now = java.time.Instant.now()
                    val estimate = pickupDeadlineEstimate(now, pickupStart, pickupEnd, expiration, holdMinutes)
                    Text(
                        holdEstimateText(estimate, pickupStart, pickupEnd, expiration, now),
                        style = SaluText.Supporting,
                        color = SaluColors.DarkGreen
                    )
                }
                Text(
                    "If it isn't marked as picked up by then, the reservation expires and the food goes back on the listing.",
                    style = SaluText.Meta,
                    color = SaluColors.Gray
                )
            }
```
Change the Confirm button's `onClick = { onConfirm(note) }` to `onClick = { onConfirm(note, holdMinutes) }`.

Imports to add in `FoodActions.kt` if missing: `androidx.compose.foundation.layout.FlowRow`, `androidx.compose.material3.FilterChip`, `androidx.compose.runtime.mutableIntStateOf`, `com.example.salusalo.model.DEFAULT_HOLD_MINUTES`, `com.example.salusalo.model.HOLD_MINUTES`, `com.example.salusalo.model.pickupDeadlineEstimate`, `com.example.salusalo.model.parseTimestamp`. (`FlowRow` may need `@OptIn(ExperimentalLayoutApi::class)` on the function if the compiler asks; `ActivityScreen.kt` already uses `FlowRow` — copy its opt-in style.)

- [ ] **Step 5: The receipt shows the saved deadline**

In the receipt (`if (reserved != null) {`), after `ActionLine("Pickup window", pickupWindow)` add:
```kotlin
                parseTimestamp(reservation.pickupDeadline)?.let { deadline ->
                    ActionLine("Pick up by", pickUpByTime(deadline, java.time.Instant.now()))
                    Text(
                        "If it isn't marked as picked up by then, the reservation expires and the food goes back on the listing.",
                        style = SaluText.Meta,
                        color = SaluColors.Gray
                    )
                }
```

- [ ] **Step 6: Build and test**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/salusalo/MainActivity.kt app/src/main/java/com/example/salusalo/FoodActions.kt
git commit -m "Show the per-person limit and choose a pickup time when reserving"
```

---

### Task 7: Activity

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/ActivityScreen.kt`

**Interfaces:**
- Consumes: `deadlineLine`, `deadlineUrgent`, `pickUpByTime` (Task 3); `Reservation.pickupDeadline`, `Reservation.expiredUnpicked` (Task 2).

- [ ] **Step 1: A minute ticker and one reload after a deadline**

Replace line 210
```kotlin
    val now = remember(myPosts, myReservations, donorReservations) { Instant.now() }
```
with
```kotlin
    // Re-read on each load and once a minute, so "12 min left" counts down.
    val loadedAt = remember(myPosts, myReservations, donorReservations) { Instant.now() }
    var tick by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            tick = Instant.now()
        }
    }
    val now = maxOf(loadedAt, tick)
```
After `val onListing = received.groupBy { it.reservation.foodPostId }` add:
```kotlin
    // The expiry job runs every minute: reload once, about 70 seconds after the
    // next deadline shown here passes (never repeatedly for the same lists).
    val nextReload = everyReservation
        .filter { it.status == ReservationStatus.RESERVED }
        .mapNotNull { parseTimestamp(it.reservation.pickupDeadline)?.plusSeconds(70) }
        .filter { it.isAfter(loadedAt) }
        .minOrNull()
    LaunchedEffect(nextReload) {
        val at = nextReload ?: return@LaunchedEffect
        delay(java.time.Duration.between(Instant.now(), at).toMillis().coerceAtLeast(0L))
        reservationViewModel.refresh()
    }
```
Add `import kotlinx.coroutines.delay` and `import androidx.compose.runtime.setValue`/`getValue` if missing (they are used elsewhere in the file already).

- [ ] **Step 2: A shared deadline line**

Add next to `ReservationStatusBadge`:
```kotlin
/** "Pick up by 5:30 PM · 12 min left" for an open reservation with a deadline; nothing otherwise. */
@Composable
private fun DeadlineLine(item: ActivityReservation, now: Instant) {
    if (item.status != ReservationStatus.RESERVED) return
    val deadline = parseTimestamp(item.reservation.pickupDeadline) ?: return
    val urgent = deadlineUrgent(deadline, now)
    IconLine(
        Icons.Outlined.HourglassBottom,
        deadlineLine(deadline, parseTimestamp(item.post.pickupStart), now),
        tint = if (urgent) SaluColors.Error else null
    )
}

/** True when an open reservation's pickup time has passed (the job will expire it). */
private fun ActivityReservation.pastDeadline(now: Instant): Boolean =
    status == ReservationStatus.RESERVED &&
        parseTimestamp(reservation.pickupDeadline)?.let { !it.isAfter(now) } == true
```
Check `IconLine`'s signature first (`grep -n "fun IconLine" ActivityScreen.kt`). If it has no `tint` parameter, add one: `tint: Color? = null`, used for both icon and text colour (default = the current colours). Import `androidx.compose.material.icons.outlined.HourglassBottom` if missing.

- [ ] **Step 3: Show it on the cards and the reservation page**

1. `ActivityReservationCard`: right after `ReservationStatusBadge(item.status)` and its `Spacer(Modifier.height(2.dp))`, add `DeadlineLine(item, now)`.
2. `ManagedReservationCard`: right after `ReservationStatusBadge(item.status)` add `DeadlineLine(item, now)`. Change both `if (item.status == ReservationStatus.RESERVED) {` checks around "Mark as picked up" so that the primary button shows only when `item.status == ReservationStatus.RESERVED && !item.pastDeadline(now)`; when `item.pastDeadline(now)` show instead, inside the same `Row`, `Text("The pickup time has passed.", style = SaluText.Supporting, color = SaluColors.Gray, modifier = Modifier.weight(1.15f).align(Alignment.CenterVertically))`. "Release reservation" stays for any `RESERVED`.
3. `ReservationDetailsPage`: in the `DetailCard`, after the `when (item.status) { … }` block add:
```kotlin
            if (open) parseTimestamp(item.reservation.pickupDeadline)?.let { deadline ->
                DetailRow(Icons.Outlined.HourglassBottom, "Pick up by", pickUpByTime(deadline, now))
            }
```
   and in the `!item.mine && open ->` branch replace the `ActionButton(text = "Mark as picked up", …)` line with:
```kotlin
                if (item.pastDeadline(now)) {
                    InlineNotice(text = "The pickup time has passed.", icon = Icons.Outlined.Info)
                } else {
                    ActionButton(text = "Mark as picked up", enabled = !saving, onClick = onMarkPickedUp)
                }
```

- [ ] **Step 4: Expired in History and on the pages**

1. Change `ReservationStatusBadge` to take the reservation's reason:
```kotlin
/** A reservation's status: Reserved, Completed (marked picked up), Cancelled or Expired (not picked up in time). */
@Composable
private fun ReservationStatusBadge(status: ReservationStatus, modifier: Modifier = Modifier, expired: Boolean = false) {
    when (status) {
        ReservationStatus.RESERVED -> StatusBadge(SaluStatus.Reserved, modifier)
        ReservationStatus.CLAIMED -> StatusBadge(SaluStatus.Claimed, modifier, label = "Completed")
        ReservationStatus.CANCELLED -> if (expired) StatusBadge(SaluStatus.Expired, modifier)
                                       else StatusBadge(SaluStatus.Cancelled, modifier)
    }
}
```
   and pass `expired = item.reservation.expiredUnpicked` at lines ~1185, ~1252 and ~1638 (`ReservationStatusBadge(item.status, expired = item.reservation.expiredUnpicked)`). For the `PageFoodCard` call at ~1403 (`ReservationStatusBadge(status)`), add an `expired: Boolean = false` parameter to `PageFoodCard`, pass it through, and pass `expired = item.reservation.expiredUnpicked` from `ReservationDetailsPage`'s `PageFoodCard(` call.
2. In `reservationNote`, in the `ReservationStatus.CANCELLED -> when {` list, add as the first two lines:
```kotlin
            item.mine && item.reservation.expiredUnpicked ->
                "You didn't pick up in time, so the reservation expired and the food went back on the listing."
            item.reservation.expiredUnpicked -> (item.otherName ?: "The recipient") + " didn't pick up in time, so the reservation expired."
```
3. In `ManagedReservationCard`, the `moment` for `CANCELLED` becomes `momentLabel(item.lastChange, now)?.let { if (item.reservation.expiredUnpicked) "Expired $it" else "Cancelled $it" }`; in `ReservationDetailsPage` the `CANCELLED` `DetailRow` label becomes `if (item.reservation.expiredUnpicked) "Expired" else "Cancelled"`.

- [ ] **Step 5: Build, test, lint**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`
Expected: BUILD SUCCESSFUL, all tests pass (57 + 11 = 68), lint 0 errors.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/salusalo/ActivityScreen.kt
git commit -m "Show pickup deadlines and expired reservations in Activity"
```

---

### Task 8: Docs, the developer's database steps, and the phone test

**Files:**
- Modify: `docs/PROJECT_STATUS.md` (new §24, the summary table row, "Current state", "Current known issues", automated test counts)
- Modify: `CLAUDE.md` (roadmap line, Supabase objects, the rules from spec §4.5, testing counts, Known issues #21 "#9" entry)
- Modify: `README.md` (branch status line and test list)
- Add: `docs/superpowers/specs/2026-10-02-reservation-limits-design.md`, this plan

- [ ] **Step 1: Write §24 in PROJECT_STATUS.md**

Sections: 24.0 status (built, unit tests 68/68, lint 0, not yet applied/tested); 24.1 what it does (spec §2 table); 24.2 SQL objects (spec §4); 24.3 app changes (files from this plan); 24.4 the developer's database steps (copy of the README section); 24.5 the two-phone test (Step 2 below); 24.6 known limits (spec §10). Mark #9 in "Current known issues" as "fixed in the app and database, pending its test (§24)". Never mark anything tested.

- [ ] **Step 2: The two-phone test (put in §24.5, then give it to the developer)**

Phone 1 = A (donor), phone 2 = B. Preconditions: migration applied, read-only check all true, the Cron job has run; new build on both phones.

1. [New] A: Post "Limit test": free, quantity 5, Maximum per person 2, pickup start "Start now", pickup end **6 minutes from now**, expiry later today. "Check your listing" shows "Maximum per person: 2.". Post: "Listing published".
2. [New] A: Post "Bad limit": quantity 3, Maximum per person 4 → "Enter a maximum per person from 1 to the quantity, or leave it blank." Don't post it.
3. [New] B: open "Limit test": "Limit: 2 per person"; the stepper stops at 2. Reserve 1: Review reservation shows "Pick up within" with 30 min selected and "Pick up by <end time>, when pickup ends." Choose 15 min (same text). Confirm: the receipt shows "Pick up by <end time>".
4. [New] B: open "Limit test" again: the stepper stops at 1. Reserve 1 more → receipt. Open it again: "You've reserved the most this listing allows per person (2)." instead of Reserve.
5. [New] B: Activity → My Reservations: both show "Pick up by <end time> · N min left", red under 10 minutes.
6. [New] A: Activity → "Limit test" → Manage reservations: B's two reservations with the same line.
7. [New] Wait until the end time passes, both apps on Activity. Within about 2 minutes, without touching anything: B's reservations move to History with the "Expired" badge; A's Manage reservations shows them as Expired. Both phones get "Reservation expired" alerts (B: "Your reservation of Limit test expired because it wasn't picked up in time."; A: "<B>'s reservation of Limit test expired because it wasn't picked up in time. The quantity is available again.").
8. [New] B: open an expired reservation: "You didn't pick up in time, so the reservation expired and the food went back on the listing." A: "<B> didn't pick up in time, so the reservation expired."
9. [New] A: Edit "Limit test": the notice ends "Maximum per person: 2 (can't be changed)."
10. [New] A: post "Hold test": free, 3 packs, no limit, pickup start "Start now", pickup end in 2 hours. B: reserve 1 with 15 min: "Pick up by <now + 15 min>." Wait 15 minutes (do steps 11–13 meanwhile). Before it expires, A sees "Mark as picked up"; after the time passes A sees "The pickup time has passed." instead (if still shown before the reload); then it expires as in step 7.
11. [Regression] B: reserve 1 of an unlimited listing with 1 hour; A marks it picked up: "Marked as picked up. You'll find it under Completed."; B gets "Reservation claimed".
12. [Regression] B: reserve and cancel: "Reservation cancelled. The quantity is available again."; A releases another B reservation: "Reservation released. The quantity is available again, and the recipient was told."
13. [Regression] Chat message both ways; Home pull to refresh; Post a listing with a photo and delete it ("Listing deleted.").

- [ ] **Step 3: Update CLAUDE.md and README.md**

CLAUDE.md: a roadmap bullet for this work (branch, built, not tested); under "Supabase objects" a "Reservation limits (20261011, §24)" entry with the objects from Task 1; under "Security and ownership rules" the three rules from spec §4.5; testing counts 68 (`ReservationLimitsTest` 11); Known issues item 21: #9 now addressed, pending test. README: branch status line and add `ReservationLimitsTest` to the test list.

- [ ] **Step 4: Final verification**

Run: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`
Expected: BUILD SUCCESSFUL; 68 tests, 0 failures; lint 0 errors. Record the exact numbers in §24.0.

- [ ] **Step 5: Commit**

```bash
git add docs/PROJECT_STATUS.md CLAUDE.md README.md docs/superpowers/specs/2026-10-02-reservation-limits-design.md docs/superpowers/plans/2026-10-03-reservation-limits.md
git commit -m "Document reservation limits and pickup deadlines (section 24)"
```

- [ ] **Step 6: Hand over to the developer**

Give, in order: the trial run, apply, the read-only check and job queries, the safety check alone (README section); then install the build on both phones and run §24.5. Push and open the PR only when the developer asks.
