-- Salu-Salo bug fix pass, round 2, group 1: one rating per reviewer and donor (audit #12).
-- Run after 20261007_bugfix_profile_relink.sql. Safe to run again, and safe on
-- the live data: it changes four read functions and no table or row. It can
-- run before or after installing any build; every build keeps working.
--
-- Before, every claimed reservation allowed its own review (that stays: one
-- review per reservation, changeable), and every review counted toward the
-- donor's rating, so ten 1-pack pickups by the same person, or by the donor's
-- own second account, counted ten times. Now the ratings and review lists
-- count only each reviewer's latest review of a donor (newest updated_at).
-- No review is deleted: an older review of the same donor stays stored and
-- becomes visible again only if it becomes that reviewer's latest.
--
-- The four functions keep their names, arguments, result columns, checks and
-- grants; only which reviews they count changes.

-- The donor's rating next to listings: average and count of each reviewer's latest review.
create or replace function public.get_listing_donor_profiles(p_food_post_ids uuid[])
returns table (
    food_post_id uuid,
    donor_name text,
    email_verified boolean,
    rating_average numeric,
    rating_count integer
)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    return query
    select p.id, u.name, u.email_verified, s.rating_average, s.rating_count
    from public.food_posts p
    join public.users u on u.id = p.donor_id
    cross join lateral (
        select round(avg(l.rating), 1) as rating_average, count(*)::integer as rating_count
        from (
            select distinct on (r.reviewer_id) r.rating
            from public.reviews r
            where r.reviewee_id = p.donor_id
            order by r.reviewer_id, r.updated_at desc, r.created_at desc, r.id
        ) l
    ) s
    where p.id = any(p_food_post_ids)
      and public.caller_can_view_food_post(p.id);
end; $$;

create or replace function public.get_listing_donor_reviews(p_food_post_id uuid, p_limit integer default 20)
returns table (
    reviewer_name text,
    rating integer,
    comment text,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    if not public.caller_can_view_food_post(p_food_post_id) then
        return;
    end if;

    return query
    select u.name, l.rating, l.comment, l.created_at, l.updated_at
    from (
        select distinct on (r.reviewer_id) r.*
        from public.food_posts p
        join public.reviews r on r.reviewee_id = p.donor_id
        where p.id = p_food_post_id
        order by r.reviewer_id, r.updated_at desc, r.created_at desc, r.id
    ) l
    join public.users u on u.id = l.reviewer_id
    order by l.updated_at desc, l.id
    limit least(greatest(coalesce(p_limit, 20), 1), 50);
end; $$;

create or replace function public.get_my_rating_summary()
returns table (rating_average numeric, rating_count integer)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    return query
    select round(avg(l.rating), 1), count(*)::integer
    from (
        select distinct on (r.reviewer_id) r.rating
        from public.reviews r
        where r.reviewee_id = v_profile
        order by r.reviewer_id, r.updated_at desc, r.created_at desc, r.id
    ) l;
end; $$;

create or replace function public.get_my_received_reviews(p_limit integer default 50)
returns table (
    reviewer_name text,
    rating integer,
    comment text,
    created_at timestamptz,
    updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = public
as $$
#variable_conflict use_column
declare
    v_profile uuid;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated';
    end if;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        raise exception 'Profile not found';
    end if;

    return query
    select u.name, l.rating, l.comment, l.created_at, l.updated_at
    from (
        select distinct on (r.reviewer_id) r.*
        from public.reviews r
        where r.reviewee_id = v_profile
        order by r.reviewer_id, r.updated_at desc, r.created_at desc, r.id
    ) l
    join public.users u on u.id = l.reviewer_id
    order by l.updated_at desc, l.id
    limit least(greatest(coalesce(p_limit, 50), 1), 100);
end; $$;

revoke all on function public.get_listing_donor_profiles(uuid[]) from public;
revoke all on function public.get_listing_donor_profiles(uuid[]) from anon;
grant execute on function public.get_listing_donor_profiles(uuid[]) to authenticated;
revoke all on function public.get_listing_donor_reviews(uuid, integer) from public;
revoke all on function public.get_listing_donor_reviews(uuid, integer) from anon;
grant execute on function public.get_listing_donor_reviews(uuid, integer) to authenticated;
revoke all on function public.get_my_rating_summary() from public;
revoke all on function public.get_my_rating_summary() from anon;
grant execute on function public.get_my_rating_summary() to authenticated;
revoke all on function public.get_my_received_reviews(integer) from public;
revoke all on function public.get_my_received_reviews(integer) from anon;
grant execute on function public.get_my_received_reviews(integer) to authenticated;
