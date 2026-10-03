-- Salu-Salo admin dashboard: Analytics (PROJECT_STATUS.md §26).
--
-- Run by hand in the Supabase SQL Editor after 20261012_admin_dashboard.sql, then
-- run supabase/tests/admin_analytics_live_safety_check.sql. Safe to run again;
-- nothing else changes, and app builds are unaffected.
--
-- admin_analytics(p_weeks) gives the dashboard's Analytics page its numbers for
-- the last 4, 8 or 12 weeks (weeks end now): seven totals, the same totals for the
-- period before when the app already existed then, weekly series and how the
-- listings posted in the period ended. Counts only: no names, emails or IDs.
-- Like every admin function it is SECURITY DEFINER and first refuses anyone whose
-- own users.role isn't ADMIN (admin_require, 20261012).

-- The seven totals for [p_from, p_to). Internal: no client may run it.
-- A pickup counts when it was marked picked up (a CLAIMED reservation's
-- updated_at); a listing expired at its expiry or pickup end, whichever came first.
create or replace function public.admin_analytics_kpis(p_from timestamptz, p_to timestamptz)
returns jsonb language sql stable security definer set search_path = public as $$
    select jsonb_build_object(
        'listings', (select count(*) from public.food_posts p
                     where p.created_at >= p_from and p.created_at < p_to),
        'reservations', (select count(*) from public.reservations r
                         where r.created_at >= p_from and r.created_at < p_to),
        'completed', (select count(*) from public.reservations r
                      where r.status = 'CLAIMED' and r.updated_at >= p_from and r.updated_at < p_to),
        'itemsRescued', coalesce((select sum(r.quantity) from public.reservations r
                                  where r.status = 'CLAIMED' and r.updated_at >= p_from and r.updated_at < p_to), 0),
        'expired', (select count(*) from public.food_posts p
                    where public.admin_listing_status(p) = 'EXPIRED'
                      and least(p.expiration_date, coalesce(p.pickup_end, p.expiration_date)) >= p_from
                      and least(p.expiration_date, coalesce(p.pickup_end, p.expiration_date)) < p_to),
        'activeDonors', (select count(distinct p.donor_id) from public.food_posts p
                         where p.created_at >= p_from and p.created_at < p_to),
        'activeRecipients', (select count(distinct r.recipient_id) from public.reservations r
                             where r.created_at >= p_from and r.created_at < p_to));
$$;

create or replace function public.admin_analytics(p_weeks integer)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    -- The real current time, not now() (the start of the transaction), so the
    -- period includes everything written before this call.
    v_to timestamptz := clock_timestamp();
    v_from timestamptz;
    v_prev timestamptz;
begin
    if p_weeks is null or p_weeks not in (4, 8, 12) then
        raise exception 'Choose 4, 8 or 12 weeks.';
    end if;
    v_from := v_to - p_weeks * interval '7 days';
    v_prev := v_from - p_weeks * interval '7 days';

    return jsonb_build_object(
        'weekStarts', (select jsonb_agg(public.admin_ms(v_from + w.i * interval '7 days') order by w.i)
                       from generate_series(0, p_weeks - 1) w(i)),
        'kpis', public.admin_analytics_kpis(v_from, v_to),
        -- Compared only when the first account is older than the period before,
        -- so the change is never measured against weeks before anyone used the app.
        'previous', case when (select min(created_at) from public.users) <= v_prev
                         then public.admin_analytics_kpis(v_prev, v_from) end,
        'series', (select jsonb_build_object(
                'listings', jsonb_agg(x.listings order by x.i),
                'reservations', jsonb_agg(x.reservations order by x.i),
                'completed', jsonb_agg(x.completed order by x.i),
                'cancelled', jsonb_agg(x.cancelled order by x.i),
                'itemsRescued', jsonb_agg(x.items order by x.i))
            from (
                select w.i,
                    (select count(*) from public.food_posts p
                     where p.created_at >= s.a and p.created_at < s.b) as listings,
                    (select count(*) from public.reservations r
                     where r.created_at >= s.a and r.created_at < s.b) as reservations,
                    (select count(*) from public.reservations r
                     where r.status = 'CLAIMED' and r.updated_at >= s.a and r.updated_at < s.b) as completed,
                    (select count(*) from public.reservations r
                     where r.status = 'CANCELLED' and r.updated_at >= s.a and r.updated_at < s.b) as cancelled,
                    coalesce((select sum(r.quantity) from public.reservations r
                              where r.status = 'CLAIMED' and r.updated_at >= s.a and r.updated_at < s.b), 0) as items
                from generate_series(0, p_weeks - 1) w(i)
                cross join lateral (select v_from + w.i * interval '7 days' as a,
                                           v_from + (w.i + 1) * interval '7 days' as b) s
            ) x),
        'statusCounts', (select jsonb_build_object(
                'AVAILABLE', count(*) filter (where st = 'AVAILABLE'),
                'RESERVED', count(*) filter (where st = 'RESERVED'),
                'CLAIMED', count(*) filter (where st = 'CLAIMED'),
                'EXPIRED', count(*) filter (where st = 'EXPIRED'),
                'REMOVED', count(*) filter (where st = 'REMOVED'))
            from (select public.admin_listing_status(p) as st from public.food_posts p
                  where p.created_at >= v_from and p.created_at < v_to) y));
end; $$;

revoke all on function public.admin_analytics(integer) from public;
revoke all on function public.admin_analytics(integer) from anon;
grant execute on function public.admin_analytics(integer) to authenticated;
revoke all on function public.admin_analytics_kpis(timestamptz, timestamptz) from public;
revoke all on function public.admin_analytics_kpis(timestamptz, timestamptz) from anon;
revoke all on function public.admin_analytics_kpis(timestamptz, timestamptz) from authenticated;
