-- Salu-Salo Phase 3 demo fixes: removing a listing that has reservations,
-- and the names and photos of saved listings.
-- Run after 20260929_phase2_listing_details.sql. Safe to run again.
--
-- IMPORTANT: install the new app build on every phone right after running this
-- file. Older builds break: they cannot read a REMOVED listing or a
-- RESERVATION_REMOVED alert, and their Delete button (a direct DELETE, which is
-- now refused) silently does nothing.
--
-- Deleting a listing now goes through remove_my_food_post():
--   * never reserved: the row is deleted (the app then deletes its photos);
--   * reserved at least once: the listing gets status REMOVED and stays in the
--     database, so past reservations, photos and chats are kept. Open
--     reservations are cancelled (cancel_reason 'LISTING_REMOVED') and each of
--     their recipients gets one RESERVATION_REMOVED alert. Saved rows are deleted.
-- A REMOVED listing can no longer be seen by other users (it is not AVAILABLE),
-- reserved, edited or saved. Its donor and its past recipients can still read it.

-- 1. New status REMOVED. Drop every check constraint on food_posts.status
-- (whatever its name), then add the one below.
do $$
declare c record;
begin
    for c in
        select con.conname
        from pg_constraint con
        where con.conrelid = 'public.food_posts'::regclass
          and con.contype = 'c'
          and con.conkey = array[(select attnum from pg_attribute
                                   where attrelid = 'public.food_posts'::regclass
                                     and attname = 'status')]::smallint[]
    loop
        execute format('alter table public.food_posts drop constraint %I', c.conname);
    end loop;
    alter table public.food_posts add constraint food_posts_status_check
        check (status in ('AVAILABLE', 'RESERVED', 'CLAIMED', 'EXPIRED', 'REMOVED'));
end $$;

-- 2. Why a reservation was cancelled. NULL = cancelled by its recipient.
alter table public.reservations add column if not exists cancel_reason text;
do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'reservations_cancel_reason_check') then
        alter table public.reservations add constraint reservations_cancel_reason_check
            check (cancel_reason is null or (cancel_reason = 'LISTING_REMOVED' and status = 'CANCELLED'));
    end if;
end $$;

-- 3. New alert type RESERVATION_REMOVED (follows the "Reservation updates"
-- switch, like every RESERVATION_% type in notify_user()).
do $$
declare c record;
begin
    for c in
        select con.conname
        from pg_constraint con
        where con.conrelid = 'public.notifications'::regclass
          and con.contype = 'c'
          and con.conkey = array[(select attnum from pg_attribute
                                   where attrelid = 'public.notifications'::regclass
                                     and attname = 'type')]::smallint[]
    loop
        execute format('alter table public.notifications drop constraint %I', c.conname);
    end loop;
    alter table public.notifications add constraint notifications_type_check check (type in (
        'MESSAGE', 'RESERVATION_CREATED', 'RESERVATION_CANCELLED', 'RESERVATION_CLAIMED',
        'RESERVATION_REMOVED'
    ));
end $$;

-- 4. No direct DELETE any more. Listings are deleted or removed only through
-- remove_my_food_post(). Do not run 20260923_phase2_food_posts.sql again after
-- this file: it would restore the delete policy.
drop policy if exists "Users may delete their own food posts" on public.food_posts;
revoke delete on public.food_posts from authenticated;

-- 5. A removed listing cannot be saved. Same policy as 20260929, plus the status.
drop policy if exists "Users may save listings they can see" on public.saved_listings;
create policy "Users may save listings they can see"
on public.saved_listings for insert to authenticated
with check (
    (select public.is_salusalo_firebase_jwt())
    and user_id = (select public.current_salusalo_profile_id())
    and exists (
        select 1 from public.food_posts p
        where p.id = food_post_id
          and p.status <> 'REMOVED'
          and p.donor_id <> (select public.current_salusalo_profile_id())
    )
);

-- 6. create_reservation: the Phase 3 version, plus the REMOVED check.
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

-- 7. update_my_food_post: the 20260929 version, plus the REMOVED check.
create or replace function public.update_my_food_post(
    p_food_post_id uuid,
    p_name text,
    p_description text,
    p_category text,
    p_price numeric,
    p_pickup_location text,
    p_expiration_date timestamptz,
    p_pickup_start timestamptz,
    p_pickup_end timestamptz,
    p_handover text,
    p_allergens text,
    p_storage_notes text
)
returns public.food_posts
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile uuid;
    v_post public.food_posts;
    v_name text := btrim(coalesce(p_name, ''), E' \t\r\n');
    v_description text := nullif(btrim(coalesce(p_description, ''), E' \t\r\n'), '');
    v_category text := btrim(coalesce(p_category, ''), E' \t\r\n');
    v_location text := btrim(coalesce(p_pickup_location, ''), E' \t\r\n');
    v_allergens text := nullif(btrim(coalesce(p_allergens, ''), E' \t\r\n'), '');
    v_storage text := nullif(btrim(coalesce(p_storage_notes, ''), E' \t\r\n'), '');
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    select * into v_post from public.food_posts where id = p_food_post_id for update;
    if not found then
        raise exception 'This listing no longer exists.';
    end if;
    if v_post.donor_id <> v_profile then
        raise exception 'You can edit only your own listing.';
    end if;
    if v_post.status = 'REMOVED' then
        raise exception 'This listing was removed and can no longer be edited.';
    end if;
    if v_post.expiration_date <= now() then
        raise exception 'This listing has expired and can no longer be edited.';
    end if;

    if char_length(v_name) = 0 then raise exception 'Enter the food name.'; end if;
    if char_length(v_name) > 160 then raise exception 'The food name must be 160 characters or fewer.'; end if;
    if char_length(v_category) = 0 then raise exception 'Select a category.'; end if;
    if char_length(v_category) > 80 then raise exception 'The category must be 80 characters or fewer.'; end if;
    if p_price is null or p_price < 0 or p_price > 99999.99 or p_price <> round(p_price, 2) then
        raise exception 'Enter a price from 0 to 99,999.99, with up to two decimal places.';
    end if;
    if char_length(v_location) = 0 then raise exception 'Enter the pickup location.'; end if;
    if char_length(v_location) > 300 then raise exception 'The pickup location must be 300 characters or fewer.'; end if;
    if p_expiration_date is null or p_expiration_date <= now() then
        raise exception 'Choose a future expiration date and time.';
    end if;
    if (p_pickup_start is null) <> (p_pickup_end is null) then
        raise exception 'Choose both pickup times, or neither.';
    end if;
    if p_pickup_start is not null and p_pickup_end <= p_pickup_start then
        raise exception 'Pickup must end after it starts.';
    end if;
    if p_pickup_end is not null and p_pickup_end > p_expiration_date then
        raise exception 'Pickup must end on or before expiration.';
    end if;
    if p_handover is not null and p_handover not in ('MEET_AT_PICKUP_LOCATION', 'COLLECT_FROM_PROVIDER') then
        raise exception 'Choose a valid handover option.';
    end if;
    if char_length(coalesce(v_allergens, '')) > 300 then
        raise exception 'Allergens must be 300 characters or fewer.';
    end if;
    if char_length(coalesce(v_storage, '')) > 300 then
        raise exception 'Storage notes must be 300 characters or fewer.';
    end if;

    update public.food_posts set
        name = v_name,
        description = v_description,
        category = v_category,
        price = p_price,
        pickup_location = v_location,
        expiration_date = p_expiration_date,
        pickup_start = p_pickup_start,
        pickup_end = p_pickup_end,
        handover = p_handover,
        allergens = v_allergens,
        storage_notes = v_storage
    where id = p_food_post_id
    returning * into v_post;
    return v_post;
end; $$;

revoke all on function public.update_my_food_post(uuid, text, text, text, numeric, text, timestamptz, timestamptz, timestamptz, text, text, text) from public;
revoke all on function public.update_my_food_post(uuid, text, text, text, numeric, text, timestamptz, timestamptz, timestamptz, text, text, text) from anon;
grant execute on function public.update_my_food_post(uuid, text, text, text, numeric, text, timestamptz, timestamptz, timestamptz, text, text, text) to authenticated;

-- 8. Reservation alerts: the Phase 4 version, except that a cancellation caused
-- by removing the listing sends nothing here (remove_my_food_post sends one
-- alert per recipient instead, and the donor gets none).
create or replace function public.notify_reservation_change()
returns trigger
language plpgsql security definer set search_path = public as $$
declare v_post public.food_posts; v_recipient_name text;
begin
  if tg_op = 'UPDATE' and new.status = 'CANCELLED' and new.cancel_reason = 'LISTING_REMOVED' then
    return new;
  end if;
  select * into v_post from public.food_posts where id = new.food_post_id;
  if not found then return new; end if;
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

-- 9. Delete or remove the caller's own listing.
-- Locks are taken in the same order as cancel_reservation and
-- claim_reservation (reservations first, then the listing), so running at the
-- same time as either of them waits instead of deadlocking.
create or replace function public.remove_my_food_post(p_food_post_id uuid)
returns table (outcome text, cancelled_reservations integer)
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile uuid;
    v_donor uuid;
    v_post public.food_posts;
    v_cancelled integer := 0;
    v_donor_name text;
    v_recipient uuid;
    v_count integer;
    v_reservation uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    -- The listing exists and is the caller's (no lock yet).
    select p.donor_id into v_donor from public.food_posts p where p.id = p_food_post_id;
    if not found then
        raise exception 'This listing no longer exists.';
    end if;
    if v_donor <> v_profile then
        raise exception 'You can delete only your own listing.';
    end if;

    -- Lock the open reservations, then the listing.
    perform 1 from public.reservations r
    where r.food_post_id = p_food_post_id and r.status = 'RESERVED'
    order by r.id
    for update;

    select * into v_post from public.food_posts p where p.id = p_food_post_id for update;
    if not found then
        raise exception 'This listing no longer exists.';
    end if;

    if v_post.status = 'REMOVED' then
        return query select 'REMOVED'::text, 0;
        return;
    end if;

    -- Never reserved: delete it (saved rows and chats go with it).
    if not exists (select 1 from public.reservations r where r.food_post_id = p_food_post_id) then
        delete from public.food_posts p where p.id = p_food_post_id;
        return query select 'DELETED'::text, 0;
        return;
    end if;

    -- Reserved at least once: keep it, hidden, with its quantity unchanged.
    update public.food_posts p set status = 'REMOVED' where p.id = p_food_post_id;

    with cancelled as (
        update public.reservations r
        set status = 'CANCELLED', cancel_reason = 'LISTING_REMOVED'
        where r.food_post_id = p_food_post_id and r.status = 'RESERVED'
        returning r.id
    )
    select count(*)::integer into v_cancelled from cancelled;

    select u.name into v_donor_name from public.users u where u.id = v_post.donor_id;

    -- One alert per recipient, linked to their newest cancelled reservation.
    for v_recipient, v_count, v_reservation in
        select r.recipient_id, count(*)::integer, (array_agg(r.id order by r.created_at desc))[1]
        from public.reservations r
        where r.food_post_id = p_food_post_id and r.cancel_reason = 'LISTING_REMOVED'
        group by r.recipient_id
    loop
        perform public.notify_user(v_recipient, 'RESERVATION_REMOVED', 'Listing removed',
            coalesce(v_donor_name, 'The donor') || ' removed ' || v_post.name ||
            case when v_count = 1 then ', so your reservation was cancelled.'
                 else ', so your ' || v_count || ' reservations were cancelled.' end,
            v_post.id, null, v_reservation);
    end loop;

    delete from public.saved_listings s where s.food_post_id = p_food_post_id;

    return query select 'REMOVED'::text, v_cancelled;
end; $$;

revoke all on function public.remove_my_food_post(uuid) from public;
revoke all on function public.remove_my_food_post(uuid) from anon;
grant execute on function public.remove_my_food_post(uuid) to authenticated;

-- 10. Name and cover path of the caller's own saved listings, including ones
-- the caller can no longer read (expired or fully reserved by someone else).
-- Removed listings have no saved rows (remove_my_food_post deletes them).
create or replace function public.get_my_saved_listing_names()
returns table (food_post_id uuid, name text, image_path text)
language sql
stable
security definer
set search_path = public
as $$
    select p.id, p.name, p.image_url
    from public.saved_listings s
    join public.food_posts p on p.id = s.food_post_id
    where public.is_salusalo_firebase_jwt()
      and s.user_id = public.current_salusalo_profile_id()
$$;

revoke all on function public.get_my_saved_listing_names() from public;
revoke all on function public.get_my_saved_listing_names() from anon;
grant execute on function public.get_my_saved_listing_names() to authenticated;
