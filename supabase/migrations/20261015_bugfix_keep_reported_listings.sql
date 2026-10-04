-- Salu-Salo bug fix pass, round 4 (PROJECT_STATUS.md §30, audit #36): a listing
-- that someone reported or messaged about is never deleted.
--
-- Run after 20261014_admin_announcements.sql, in the Supabase SQL Editor, then
-- run supabase/tests/bugfix_keep_reported_listings_live_safety_check.sql. Safe
-- to run again. Any app build keeps working: the function's name, argument,
-- result and texts are unchanged; such a listing only comes back as REMOVED
-- instead of DELETED, which every build already shows ("Listing removed").
--
-- Before, remove_my_food_post() deleted every listing nobody had reserved. A
-- donor could so erase a listing that was reported (the admin then saw only
-- "Deleted listing", without photos or details), and the cascade deleted the
-- other person's chat about it, with its messages, without telling them. Now a
-- listing is deleted only when nobody reserved it, reported it or started a
-- chat about it; otherwise it is kept as REMOVED like a reserved one: hidden
-- from Home and from the donor's listings, its photos kept (the app deletes
-- photos only for DELETED), its chats and reports kept.
-- Everything else is the 20260930 function unchanged.

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

    -- Never reserved, reported or talked about: delete it (saved rows go with it).
    if not exists (select 1 from public.reservations r where r.food_post_id = p_food_post_id)
       and not exists (select 1 from public.reports rp where rp.food_post_id = p_food_post_id)
       and not exists (select 1 from public.conversations c where c.food_post_id = p_food_post_id) then
        delete from public.food_posts p where p.id = p_food_post_id;
        return query select 'DELETED'::text, 0;
        return;
    end if;

    -- Otherwise keep it, hidden, with its quantity unchanged.
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
