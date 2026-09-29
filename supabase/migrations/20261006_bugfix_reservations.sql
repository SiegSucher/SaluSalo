-- Salu-Salo bug fix pass, group C: reservation correctness (audit #8, #9, #23).
-- Run after 20261005_bugfix_security_integrity.sql and BEFORE installing the
-- app build of this fix pass: that build reserves through
-- create_reservation_at_price() and releases through release_reservation(), so
-- without this file its Reserve and Release fail. Safe to run again, and safe
-- on the live data: no existing row changes.
--
-- Older app builds keep working: create_reservation(uuid, integer) keeps its
-- arguments, result and texts (it only also refuses a listing whose pickup
-- time has ended), get_donor_reservations() only gains a column they ignore,
-- and a released reservation reaches them as an ordinary cancelled one with an
-- ordinary "cancelled" alert.

-- ---------------------------------------------------------------------------
-- 1. (#23) No reservation after the pickup window has ended.
-- The 20260930 create_reservation, plus the pickup_end check. Listings without
-- a pickup window (older ones) are unaffected.
-- ---------------------------------------------------------------------------

create or replace function public.create_reservation(p_food_post_id uuid, p_quantity integer)
returns public.reservations
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_post public.food_posts; v_reservation public.reservations;
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
-- 2. (#8) Reserve at the price the recipient saw.
-- The app sends the unit price shown on the review page. The listing is locked
-- first, so the price can't change before create_reservation() (same
-- transaction) takes the same lock; if it differs, nothing is reserved and the
-- error names the current price. The recorded price still comes only from the
-- listing (the reservations_set_unit_price trigger), never from the app. The
-- price is compared only for listings anyone may see; for any other listing
-- create_reservation() gives its usual refusal.
-- ---------------------------------------------------------------------------

create or replace function public.create_reservation_at_price(
    p_food_post_id uuid,
    p_quantity integer,
    p_unit_price numeric
)
returns public.reservations
language plpgsql
security definer
set search_path = public
as $$
declare
    v_post public.food_posts;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    select * into v_post from public.food_posts where id = p_food_post_id for update;
    if found and v_post.status = 'AVAILABLE' and v_post.expiration_date > now()
       and (p_unit_price is null or round(p_unit_price, 2) <> v_post.price) then
        if v_post.price = 0 then
            raise exception 'The price changed: this food is now free. Check the details before reserving.';
        end if;
        raise exception 'The price changed to ₱% per unit. Check the details before reserving.',
            to_char(v_post.price, 'FM999,990.00');
    end if;
    return public.create_reservation(p_food_post_id, p_quantity);
end; $$;

revoke all on function public.create_reservation_at_price(uuid, integer, numeric) from public;
revoke all on function public.create_reservation_at_price(uuid, integer, numeric) from anon;
grant execute on function public.create_reservation_at_price(uuid, integer, numeric) to authenticated;

-- ---------------------------------------------------------------------------
-- 3. (#9) The donor can release a reservation that will not be picked up.
-- Before, only the recipient could cancel, so a recipient who never came kept
-- the food reserved for ever. release_reservation() cancels an open
-- reservation on the caller's own listing, marks it DONOR_RELEASED, gives the
-- quantity back to the listing and tells the recipient. Locks: the
-- reservation, then the listing, like cancel_reservation and claim_reservation.
-- Nothing expires reservations automatically; the donor decides.
-- ---------------------------------------------------------------------------

alter table public.reservations drop constraint if exists reservations_cancel_reason_check;
alter table public.reservations add constraint reservations_cancel_reason_check check (
    cancel_reason is null
    or (cancel_reason in ('LISTING_REMOVED', 'DONOR_RELEASED') and status = 'CANCELLED'));

create or replace function public.release_reservation(p_reservation_id uuid)
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
        raise exception 'Only the food donor can release this reservation';
    end if;
    if v_reservation.status = 'CLAIMED' then
        raise exception 'A picked-up reservation cannot be released';
    end if;
    if v_reservation.status = 'CANCELLED' then
        raise exception 'This reservation was already cancelled';
    end if;

    update public.reservations
    set status = 'CANCELLED', cancel_reason = 'DONOR_RELEASED'
    where id = p_reservation_id
    returning * into v_reservation;

    update public.food_posts
    set quantity = quantity + v_reservation.quantity,
        status = case when status = 'REMOVED' then status else 'AVAILABLE' end
    where id = v_reservation.food_post_id;

    return v_reservation;
end; $$;

revoke all on function public.release_reservation(uuid) from public;
revoke all on function public.release_reservation(uuid) from anon;
grant execute on function public.release_reservation(uuid) to authenticated;

-- The alerts: the 20260930 trigger function, plus a released reservation
-- alerting its recipient (type RESERVATION_CANCELLED, which every app build
-- knows; tapping it opens their reservation) instead of the donor.
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

-- The donor's reservation list: the 20260927 function plus cancel_reason, so
-- the donor's app can tell a release from a recipient's cancel. Names only, as
-- before. The result type changes, so the function is dropped and recreated.
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
    cancel_reason text
)
language sql
stable
security definer
set search_path = public
as 'select r.id, r.food_post_id, r.recipient_id, r.quantity, r.status,
           r.created_at, r.updated_at, u.name, r.unit_price, r.cancel_reason
    from public.reservations r
    join public.food_posts p on p.id = r.food_post_id
    join public.users u on u.id = r.recipient_id
    where p.donor_id = public.current_salusalo_profile_id()
    order by r.created_at desc';

revoke all on function public.get_donor_reservations() from public;
revoke all on function public.get_donor_reservations() from anon;
grant execute on function public.get_donor_reservations() to authenticated;
