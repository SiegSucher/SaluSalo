-- Salu-Salo Phase 2 fixes: listing details, extra photos, donor names,
-- editing through a function only, and saved listings.
-- Run after 20260928_profile_updates.sql, BEFORE installing the app build that
-- uses it (Create Listing sends the new columns). Safe to run again.
-- Do not run 20260923_phase2_food_posts.sql again afterwards: it would restore
-- the direct UPDATE grant and policy that this file removes.

-- 1. New listing columns. Existing listings get NULL / an empty array.
alter table public.food_posts add column if not exists pickup_start timestamptz;
alter table public.food_posts add column if not exists pickup_end timestamptz;
alter table public.food_posts add column if not exists handover text;
alter table public.food_posts add column if not exists allergens text;
alter table public.food_posts add column if not exists storage_notes text;
alter table public.food_posts add column if not exists extra_image_paths text[] not null default '{}';

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'food_posts_pickup_window_check') then
        alter table public.food_posts add constraint food_posts_pickup_window_check check (
            (pickup_start is null and pickup_end is null)
            or (pickup_start is not null and pickup_end is not null
                and pickup_end > pickup_start
                and pickup_end <= expiration_date));
    end if;
    if not exists (select 1 from pg_constraint where conname = 'food_posts_handover_check') then
        alter table public.food_posts add constraint food_posts_handover_check
            check (handover is null or handover in ('MEET_AT_PICKUP_LOCATION', 'COLLECT_FROM_PROVIDER'));
    end if;
    if not exists (select 1 from pg_constraint where conname = 'food_posts_allergens_check') then
        alter table public.food_posts add constraint food_posts_allergens_check
            check (allergens is null or char_length(allergens) <= 300);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'food_posts_storage_notes_check') then
        alter table public.food_posts add constraint food_posts_storage_notes_check
            check (storage_notes is null or char_length(storage_notes) <= 300);
    end if;
    if not exists (select 1 from pg_constraint where conname = 'food_posts_extra_images_check') then
        alter table public.food_posts add constraint food_posts_extra_images_check
            check (cardinality(extra_image_paths) <= 3 and array_position(extra_image_paths, null) is null);
    end if;
end $$;

-- 2. Photos must be in the uploader's own <firebaseUid>/<postId>/ folder.
-- Same insert rule as Phase 2, plus the path checks.
drop policy if exists "Users may create their own food posts" on public.food_posts;
create policy "Users may create their own food posts"
on public.food_posts for insert to authenticated
with check (
    (select public.is_salusalo_firebase_jwt())
    and donor_id = (select public.current_salusalo_profile_id())
    and status = 'AVAILABLE'
    and (image_url is null or (
        left(image_url, char_length((select auth.jwt() ->> 'sub')) + char_length(id::text) + 2)
            = (select auth.jwt() ->> 'sub') || '/' || id::text || '/'
        and position('..' in image_url) = 0))
    and not exists (
        select 1 from unnest(extra_image_paths) as x(path)
        where left(x.path, char_length((select auth.jwt() ->> 'sub')) + char_length(id::text) + 2)
                  <> (select auth.jwt() ->> 'sub') || '/' || id::text || '/'
           or position('..' in x.path) > 0)
);

-- 3. Close the direct-update gap: listings change only by insert, the
-- SECURITY DEFINER reservation functions and update_my_food_post().
drop policy if exists "Users may update their own food posts" on public.food_posts;
revoke update on public.food_posts from authenticated;

-- 4. Donor names (name only) for listings the caller may see. Same rule as the
-- food_posts SELECT policy: available and unexpired, own, or reserved by the caller.
create or replace function public.get_listing_donor_names(p_food_post_ids uuid[])
returns table (food_post_id uuid, donor_name text)
language sql
stable
security definer
set search_path = public
as $$
    select p.id, u.name
    from public.food_posts p
    join public.users u on u.id = p.donor_id
    where public.is_salusalo_firebase_jwt()
      and p.id = any(p_food_post_ids)
      and (
          (p.status = 'AVAILABLE' and p.expiration_date > now())
          or p.donor_id = public.current_salusalo_profile_id()
          or public.is_reservation_recipient(p.id)
      )
$$;

revoke all on function public.get_listing_donor_names(uuid[]) from public;
revoke all on function public.get_listing_donor_names(uuid[]) from anon;
grant execute on function public.get_listing_donor_names(uuid[]) to authenticated;

-- 5. Edit your own unexpired listing. Never changes quantity, unit, status,
-- donor, photos or created_at; updated_at is set by food_posts_set_updated_at.
-- Existing reservations keep their unit_price (reservations_set_unit_price
-- runs only when a reservation is inserted).
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

-- 6. Saved listings (bookmarks). Own rows only; no UPDATE.
create table if not exists public.saved_listings (
    user_id uuid not null references public.users(id) on delete cascade,
    food_post_id uuid not null references public.food_posts(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (user_id, food_post_id)
);
create index if not exists saved_listings_post_idx on public.saved_listings (food_post_id);

alter table public.saved_listings enable row level security;
revoke all on public.saved_listings from anon;
revoke all on public.saved_listings from authenticated;
grant select, insert, delete on public.saved_listings to authenticated;

drop policy if exists "Users may view their saved listings" on public.saved_listings;
create policy "Users may view their saved listings"
on public.saved_listings for select to authenticated
using (
    (select public.is_salusalo_firebase_jwt())
    and user_id = (select public.current_salusalo_profile_id())
);

-- Only a listing the caller can see (food_posts RLS applies inside this
-- subquery) and that is not their own.
drop policy if exists "Users may save listings they can see" on public.saved_listings;
create policy "Users may save listings they can see"
on public.saved_listings for insert to authenticated
with check (
    (select public.is_salusalo_firebase_jwt())
    and user_id = (select public.current_salusalo_profile_id())
    and exists (
        select 1 from public.food_posts p
        where p.id = food_post_id
          and p.donor_id <> (select public.current_salusalo_profile_id())
    )
);

drop policy if exists "Users may remove their saved listings" on public.saved_listings;
create policy "Users may remove their saved listings"
on public.saved_listings for delete to authenticated
using (
    (select public.is_salusalo_firebase_jwt())
    and user_id = (select public.current_salusalo_profile_id())
);
