-- Salu-Salo Phase 5, part 2: Maps. A pickup pin (latitude and longitude) on
-- each listing: sent with a new listing, and added or moved on Edit.
-- Run after 20261002_phase5_reviews_reports.sql and BEFORE installing the app
-- build that uses it: that build sends the pin with every new listing and saves
-- edits through update_my_food_post_with_pin(), so without this file its Post
-- Listing and Save changes fail.
-- Safe to run again.
--
-- Older builds keep working after it: supabase-kt ignores the two new columns,
-- their new listings get no pin (both columns NULL), and they still edit through
-- update_my_food_post(), which this file does not change and which never
-- touches the pin.
--
-- The pin is part of the listing row, so exactly the people who may see the
-- listing under the existing food_posts SELECT policy see its pin. No policy
-- changes, and clients still have no UPDATE or DELETE on food_posts. The
-- phone's location is used on the phone for distances and is never sent here.
-- The one exception is "Use my location" on Post and Edit: it puts the pin at
-- the phone's position, and the donor then saves that pin with the listing.

-- 1. The pin. Both set or both NULL; latitude from -90 to 90, longitude from
-- -180 to 180, stored with 6 decimals (about 0.1 m). Existing listings get NULL.
alter table public.food_posts add column if not exists pickup_latitude numeric(8,6);
alter table public.food_posts add column if not exists pickup_longitude numeric(9,6);

do $$
begin
    if not exists (select 1 from pg_constraint
                   where conrelid = 'public.food_posts'::regclass
                     and conname = 'food_posts_pickup_pin_check') then
        alter table public.food_posts add constraint food_posts_pickup_pin_check check (
            (pickup_latitude is null and pickup_longitude is null)
            or (pickup_latitude is not null and pickup_longitude is not null
                and pickup_latitude between -90 and 90
                and pickup_longitude between -180 and 180));
    end if;
end $$;

-- 2. Edit your own listing, including its pin, in one call. The same caller,
-- owner, REMOVED and expiry checks as update_my_food_post() (same texts), then
-- the pin checks, then update_my_food_post() itself for every other field and
-- its checks, then the pin. It all runs in one transaction: when anything is
-- refused, nothing changes. A listing without a pin can get one and a pin can
-- be moved, but not removed: with both pin arguments NULL the stored pin (or
-- its absence) stays as it is.
create or replace function public.update_my_food_post_with_pin(
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
    p_storage_notes text,
    p_pickup_latitude numeric,
    p_pickup_longitude numeric
)
returns public.food_posts
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile uuid;
    v_post public.food_posts;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    select p.* into v_post from public.food_posts p where p.id = p_food_post_id for update;
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

    if (p_pickup_latitude is null) <> (p_pickup_longitude is null) then
        raise exception 'Send both pickup pin coordinates, or neither.';
    end if;
    if p_pickup_latitude is not null
       and (p_pickup_latitude not between -90 and 90
            or p_pickup_longitude not between -180 and 180) then
        raise exception 'The pickup pin is not a valid map position.';
    end if;

    -- Every other field, with all of update_my_food_post()'s checks and texts.
    v_post := public.update_my_food_post(p_food_post_id, p_name, p_description, p_category,
        p_price, p_pickup_location, p_expiration_date, p_pickup_start, p_pickup_end,
        p_handover, p_allergens, p_storage_notes);

    if p_pickup_latitude is not null then
        update public.food_posts p
        set pickup_latitude = round(p_pickup_latitude, 6),
            pickup_longitude = round(p_pickup_longitude, 6)
        where p.id = p_food_post_id
        returning p.* into v_post;
    end if;
    return v_post;
end; $$;

revoke all on function public.update_my_food_post_with_pin(uuid, text, text, text, numeric, text, timestamptz, timestamptz, timestamptz, text, text, text, numeric, numeric) from public;
revoke all on function public.update_my_food_post_with_pin(uuid, text, text, text, numeric, text, timestamptz, timestamptz, timestamptz, text, text, text, numeric, numeric) from anon;
grant execute on function public.update_my_food_post_with_pin(uuid, text, text, text, numeric, text, timestamptz, timestamptz, timestamptz, text, text, text, numeric, numeric) to authenticated;
