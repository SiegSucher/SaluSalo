-- Salu-Salo Phase 3 demo fixes: read-only check of the live database.
--
-- Run it in the Supabase SQL Editor (role "postgres") after
-- migrations/20260930_phase3_demo_fixes.sql. It only reads the catalog and
-- changes nothing. It returns one row per check: PASS or FAIL, with details.

with
status_checks as (
    select con.conname, pg_get_constraintdef(con.oid) as def
    from pg_constraint con
    where con.conrelid = 'public.food_posts'::regclass and con.contype = 'c'
      and con.conkey = array[(select attnum from pg_attribute
                               where attrelid = 'public.food_posts'::regclass and attname = 'status')]::smallint[]
),
type_checks as (
    select con.conname, pg_get_constraintdef(con.oid) as def
    from pg_constraint con
    where con.conrelid = 'public.notifications'::regclass and con.contype = 'c'
      and con.conkey = array[(select attnum from pg_attribute
                               where attrelid = 'public.notifications'::regclass and attname = 'type')]::smallint[]
),
fn as (
    select p.proname, p.prosecdef, p.proconfig, p.prosrc,
           has_function_privilege('anon', p.oid, 'execute') as anon_x,
           has_function_privilege('authenticated', p.oid, 'execute') as auth_x
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
),
checks(n, check_name, ok, details) as (
    select 1, 'food_posts.status has exactly one check constraint, and it allows REMOVED',
        (select count(*) = 1 and bool_and(def like '%''REMOVED''%') from status_checks),
        (select coalesce(string_agg(conname || ': ' || def, ' | '), 'none') from status_checks)
    union all
    select 2, 'notifications.type has exactly one check constraint, and it allows RESERVATION_REMOVED',
        (select count(*) = 1 and bool_and(def like '%''RESERVATION_REMOVED''%') from type_checks),
        (select coalesce(string_agg(conname || ': ' || def, ' | '), 'none') from type_checks)
    union all
    select 3, 'reservations.cancel_reason exists with its check constraint',
        exists (select 1 from information_schema.columns
                where table_schema = 'public' and table_name = 'reservations' and column_name = 'cancel_reason')
        and exists (select 1 from pg_constraint where conname = 'reservations_cancel_reason_check'),
        (select coalesce(pg_get_constraintdef(oid), 'constraint missing') from pg_constraint
         where conname = 'reservations_cancel_reason_check')
    union all
    select 4, 'food_posts has no DELETE or UPDATE policy',
        not exists (select 1 from pg_policy where polrelid = 'public.food_posts'::regclass and polcmd in ('d', 'w', '*')),
        (select string_agg(polname || ' (' || polcmd::text || ')', ', ' order by polname) from pg_policy
         where polrelid = 'public.food_posts'::regclass)
    union all
    select 5, 'authenticated and anon have no DELETE or UPDATE privilege on food_posts',
        not (has_table_privilege('authenticated', 'public.food_posts', 'DELETE')
             or has_table_privilege('authenticated', 'public.food_posts', 'UPDATE')
             or has_table_privilege('anon', 'public.food_posts', 'DELETE')
             or has_table_privilege('anon', 'public.food_posts', 'UPDATE')),
        'authenticated: ' || coalesce((select string_agg(privilege_type, ', ' order by privilege_type)
            from information_schema.role_table_grants
            where table_schema = 'public' and table_name = 'food_posts' and grantee = 'authenticated'), 'none')
    union all
    select 6, 'remove_my_food_post: SECURITY DEFINER, search_path=public, authenticated only',
        coalesce((select prosecdef and proconfig @> array['search_path=public'] and not anon_x and auth_x
                  from fn where proname = 'remove_my_food_post'), false),
        coalesce((select format('definer=%s, anon=%s, authenticated=%s', prosecdef, anon_x, auth_x)
                  from fn where proname = 'remove_my_food_post'), 'function missing')
    union all
    select 7, 'get_my_saved_listing_names: SECURITY DEFINER, search_path=public, authenticated only',
        coalesce((select prosecdef and proconfig @> array['search_path=public'] and not anon_x and auth_x
                  from fn where proname = 'get_my_saved_listing_names'), false),
        coalesce((select format('definer=%s, anon=%s, authenticated=%s', prosecdef, anon_x, auth_x)
                  from fn where proname = 'get_my_saved_listing_names'), 'function missing')
    union all
    select 8, 'create_reservation refuses a REMOVED listing',
        coalesce((select prosrc like '%The donor removed this listing.%' and not anon_x and auth_x
                  from fn where proname = 'create_reservation'), false),
        'checks the function text and its grants'
    union all
    select 9, 'update_my_food_post refuses a REMOVED listing',
        coalesce((select prosrc like '%This listing was removed and can no longer be edited.%' and not anon_x and auth_x
                  from fn where proname = 'update_my_food_post'), false),
        'checks the function text and its grants'
    union all
    select 10, 'notify_reservation_change skips LISTING_REMOVED cancellations; reservations_notify trigger exists',
        coalesce((select prosrc like '%LISTING_REMOVED%' from fn where proname = 'notify_reservation_change'), false)
        and exists (select 1 from pg_trigger where tgname = 'reservations_notify'
                    and tgrelid = 'public.reservations'::regclass),
        'checks the function text and the trigger'
    union all
    select 11, 'notify_user and notify_reservation_change are not executable by anon or authenticated',
        coalesce((select bool_and(not anon_x and not auth_x) from fn
                  where proname in ('notify_user', 'notify_reservation_change')), false),
        (select string_agg(format('%s: anon=%s, authenticated=%s', proname, anon_x, auth_x), '; ')
         from fn where proname in ('notify_user', 'notify_reservation_change'))
    union all
    select 12, 'saved_listings insert policy refuses REMOVED listings',
        coalesce((select pg_get_expr(polwithcheck, polrelid) like '%REMOVED%' from pg_policy
                  where polrelid = 'public.saved_listings'::regclass
                    and polname = 'Users may save listings they can see'), false),
        'checks the policy "Users may save listings they can see"'
    union all
    select 13, 'saved_listings: no UPDATE privilege or policy, nothing for anon',
        not has_table_privilege('authenticated', 'public.saved_listings', 'UPDATE')
        and not has_table_privilege('anon', 'public.saved_listings', 'SELECT')
        and not exists (select 1 from pg_policy where polrelid = 'public.saved_listings'::regclass and polcmd in ('w', '*')),
        'authenticated: ' || coalesce((select string_agg(privilege_type, ', ' order by privilege_type)
            from information_schema.role_table_grants
            where table_schema = 'public' and table_name = 'saved_listings' and grantee = 'authenticated'), 'none')
)
select n as "#", check_name as "check",
       case when ok then 'PASS' else 'FAIL' end as result,
       details
from checks
order by n;
