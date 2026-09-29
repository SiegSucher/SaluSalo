-- Salu-Salo bug fix pass, group A: security and data integrity (audit #1, #10, #11).
-- Run after 20261004_profile_bio.sql. Safe to run again, and safe on the live
-- data: it changes two functions, one Storage read policy and adds one trigger;
-- no table, column or existing row changes.
--
-- Older app builds keep working: the function names, arguments, results and
-- error texts they use are the same, every photo the app shows stays readable,
-- and the new listing checks match what Post Listing already requires (except
-- the new description and quantity limits, which the Post form never offered
-- to exceed in normal use).

-- ---------------------------------------------------------------------------
-- 1. (#1) cancel_reservation and claim_reservation require a SaluSalo profile.
-- Before, a valid Firebase token with no users row gave a NULL profile, and in
-- plpgsql "recipient_id <> NULL" is NULL, which IF treats as false: the owner
-- check passed. Now a NULL profile is refused first ("Profile not found", the
-- text the other RPCs use) and the owner checks use IS DISTINCT FROM, which is
-- never NULL. Everything else (locks, texts, quantity restore) is unchanged.
-- ---------------------------------------------------------------------------

create or replace function public.cancel_reservation(p_reservation_id uuid)
returns public.reservations
language plpgsql
security definer
set search_path = public
as $$
declare
    v_profile uuid;
    v_reservation public.reservations;
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
    if v_reservation.recipient_id is distinct from v_profile then
        raise exception 'You can cancel only your own reservation';
    end if;
    if v_reservation.status = 'CLAIMED' then
        raise exception 'A claimed reservation cannot be cancelled';
    end if;
    if v_reservation.status = 'CANCELLED' then
        raise exception 'This reservation was already cancelled';
    end if;

    update public.reservations
    set status = 'CANCELLED'
    where id = p_reservation_id
    returning * into v_reservation;

    update public.food_posts
    set quantity = quantity + v_reservation.quantity,
        status = 'AVAILABLE'
    where id = v_reservation.food_post_id;

    return v_reservation;
end; $$;

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

    update public.reservations
    set status = 'CLAIMED'
    where id = p_reservation_id
    returning * into v_reservation;

    return v_reservation;
end; $$;

revoke all on function public.cancel_reservation(uuid) from public;
revoke all on function public.cancel_reservation(uuid) from anon;
grant execute on function public.cancel_reservation(uuid) to authenticated;
revoke all on function public.claim_reservation(uuid) from public;
revoke all on function public.claim_reservation(uuid) from anon;
grant execute on function public.claim_reservation(uuid) to authenticated;

-- ---------------------------------------------------------------------------
-- 2. (#10) Listing photos are readable only where the app shows them.
-- Before, any signed-in token could read (and so sign or list) every object in
-- food-images, including photos of removed, expired and deleted listings. Now
-- a photo is readable by:
--   * its uploader (the first folder is their Firebase UID): My Listings, and
--     what Storage needs for upload with upsert and for delete;
--   * anyone else only when the photo is the cover or an extra photo of a
--     listing, and the caller may see that listing (the food_posts SELECT
--     policy: available and unexpired, their own, or reserved by them), saved
--     it (Saved Listings shows saved covers even after the listing is hidden),
--     or has a chat about it (the Messages thumbnails).
-- Signed URLs work as before: Storage checks this policy when it signs, and a
-- signed URL keeps working until it expires. The bucket stays private.
-- Listing photo paths have always been <firebaseUid>/<postId>/<file>, so the
-- listing is found by the second folder; a photo not referenced by its listing
-- (for example left over after a failed post) is readable by its uploader only.
-- ---------------------------------------------------------------------------

create or replace function public.can_read_food_image(p_name text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_folders text[];
    v_post_id uuid;
    v_profile uuid;
begin
    if p_name is null or not public.is_salusalo_firebase_jwt() then
        return false;
    end if;
    v_folders := storage.foldername(p_name);
    if v_folders[1] = auth.jwt() ->> 'sub' then
        return true;
    end if;
    if coalesce(v_folders[2], '') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
        return false;
    end if;
    v_post_id := v_folders[2]::uuid;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        return false;
    end if;

    return exists (
        select 1 from public.food_posts p
        where p.id = v_post_id
          and (p.image_url = p_name or p_name = any(p.extra_image_paths))
          and ((p.status = 'AVAILABLE' and p.expiration_date > now())
               or p.donor_id = v_profile
               or exists (select 1 from public.reservations r
                          where r.food_post_id = p.id and r.recipient_id = v_profile)
               or exists (select 1 from public.saved_listings s
                          where s.food_post_id = p.id and s.user_id = v_profile)
               or exists (select 1 from public.conversations c
                          where c.food_post_id = p.id
                            and (c.recipient_id = v_profile or c.donor_id = v_profile)))
    );
end; $$;

-- Storage evaluates the policy as the signed-in client, so authenticated needs
-- EXECUTE. It answers only true or false about one path.
revoke all on function public.can_read_food_image(text) from public;
revoke all on function public.can_read_food_image(text) from anon;
grant execute on function public.can_read_food_image(text) to authenticated;

drop policy if exists "Authenticated users may read food listing images" on storage.objects;
drop policy if exists "Users may read food images they can see" on storage.objects;
create policy "Users may read food images they can see"
on storage.objects for select to authenticated
using (
    bucket_id = 'food-images'
    and public.can_read_food_image(name)
);

-- ---------------------------------------------------------------------------
-- 3. (#11) The database checks new listings itself instead of trusting the app.
-- On insert: created_at and updated_at are always the server's time (a client
-- could send any date and jump to the top of the newest-first feed); quantity
-- 1 to 10,000 (0 was allowed since the Phase 3 check became quantity >= 0);
-- the expiry and the end of pickup in the future; a description of at most
-- 2,000 characters (it had no limit). On an edit, only a changed description
-- is checked, so reservations, removals and older rows are never refused by it.
-- Name, category, unit, location, price, pickup window, handover, allergens,
-- storage notes and photo paths were already checked by constraints and the
-- insert policy.
-- ---------------------------------------------------------------------------

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

drop trigger if exists food_posts_check_values on public.food_posts;
create trigger food_posts_check_values
before insert or update of description on public.food_posts
for each row execute function public.food_posts_check_values();
