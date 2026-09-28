-- Salu-Salo Phase 5 part 2 (Maps, the pickup pin): read-only check of the live
-- database.
--
-- Run it in the Supabase SQL Editor (role "postgres") after
-- migrations/20261003_phase5_maps.sql. It only reads the catalog and the
-- listings' pin columns and changes nothing. It returns one row per check:
-- PASS or FAIL, with details.

with
fn as (
    select p.oid, p.proname, p.prosecdef, p.proconfig, p.prosrc,
           pg_get_function_identity_arguments(p.oid) as args,
           pg_get_function_result(p.oid) as result,
           has_function_privilege('anon', p.oid, 'execute') as anon_x,
           has_function_privilege('authenticated', p.oid, 'execute') as auth_x,
           -- EXECUTE granted to PUBLIC (grantee 0); a NULL acl means the default, which includes PUBLIC.
           (p.proacl is null or exists (select 1 from aclexplode(p.proacl) a
                                        where a.grantee = 0 and a.privilege_type = 'EXECUTE')) as public_x
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
),
edit_args(old_args, new_args) as (
    values ('p_food_post_id uuid, p_name text, p_description text, p_category text, p_price numeric, '
            || 'p_pickup_location text, p_expiration_date timestamp with time zone, '
            || 'p_pickup_start timestamp with time zone, p_pickup_end timestamp with time zone, '
            || 'p_handover text, p_allergens text, p_storage_notes text',
            'p_food_post_id uuid, p_name text, p_description text, p_category text, p_price numeric, '
            || 'p_pickup_location text, p_expiration_date timestamp with time zone, '
            || 'p_pickup_start timestamp with time zone, p_pickup_end timestamp with time zone, '
            || 'p_handover text, p_allergens text, p_storage_notes text, '
            || 'p_pickup_latitude numeric, p_pickup_longitude numeric')
),
pin_col as (
    select a.attname, format_type(a.atttypid, a.atttypmod) as type, a.attnotnull,
           pg_get_expr(d.adbin, d.adrelid) as default_expr
    from pg_attribute a
    left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
    where a.attrelid = 'public.food_posts'::regclass
      and a.attname in ('pickup_latitude', 'pickup_longitude')
      and not a.attisdropped
),
pin_check as (
    select c.convalidated, pg_get_constraintdef(c.oid) as def
    from pg_constraint c
    where c.conrelid = 'public.food_posts'::regclass and c.conname = 'food_posts_pickup_pin_check'
),
policies as (
    select polcmd, pg_get_expr(polqual, polrelid) as qual, pg_get_expr(polwithcheck, polrelid) as withcheck
    from pg_policy
    where polrelid = 'public.food_posts'::regclass
),
pins as (
    select count(*) filter (where pickup_latitude is not null and pickup_longitude is not null) as pinned,
           count(*) filter (where (pickup_latitude is null) <> (pickup_longitude is null)
                               or pickup_latitude not between -90 and 90
                               or pickup_longitude not between -180 and 180) as bad,
           count(*) as total
    from public.food_posts
),
checks(n, check_name, ok, details) as (
    select 1, 'food_posts.pickup_latitude exists: numeric(8,6), nullable, no default',
        coalesce((select type = 'numeric(8,6)' and not attnotnull and default_expr is null
                  from pin_col where attname = 'pickup_latitude'), false),
        coalesce((select format('%s, not null=%s, default=%s', type, attnotnull, default_expr)
                  from pin_col where attname = 'pickup_latitude'), 'column missing')
    union all
    select 2, 'food_posts.pickup_longitude exists: numeric(9,6), nullable, no default',
        coalesce((select type = 'numeric(9,6)' and not attnotnull and default_expr is null
                  from pin_col where attname = 'pickup_longitude'), false),
        coalesce((select format('%s, not null=%s, default=%s', type, attnotnull, default_expr)
                  from pin_col where attname = 'pickup_longitude'), 'column missing')
    union all
    select 3, 'food_posts_pickup_pin_check: validated; both set or both NULL, latitude -90 to 90, longitude -180 to 180',
        coalesce((select convalidated
                         and def like '%pickup_latitude IS NULL) AND (pickup_longitude IS NULL)%'
                         and def like '%pickup_latitude IS NOT NULL) AND (pickup_longitude IS NOT NULL)%'
                         -- Postgres prints the bounds with casts, e.g. ('-90'::integer)::numeric.
                         and def ~ 'pickup_latitude >= \D*-90\D' and def ~ 'pickup_latitude <= \D*90\D'
                         and def ~ 'pickup_longitude >= \D*-180\D' and def ~ 'pickup_longitude <= \D*180\D'
                  from pin_check), false),
        coalesce((select format('validated=%s; %s', convalidated, def) from pin_check), 'constraint missing')
    union all
    select 4, 'update_my_food_post_with_pin: SECURITY DEFINER, search_path=public, returns food_posts, executable by authenticated only',
        coalesce((select f.prosecdef and f.proconfig @> array['search_path=public'] and f.result = 'food_posts'
                         and f.auth_x and not f.anon_x and not f.public_x
                  from fn f, edit_args e where f.proname = 'update_my_food_post_with_pin' and f.args = e.new_args), false),
        coalesce((select format('definer=%s, config=%s, returns=%s, authenticated=%s, anon=%s, public=%s',
                                f.prosecdef, f.proconfig, f.result, f.auth_x, f.anon_x, f.public_x)
                  from fn f, edit_args e where f.proname = 'update_my_food_post_with_pin' and f.args = e.new_args),
                 'function missing (or different arguments)')
    union all
    select 5, 'update_my_food_post_with_pin: takes the caller from the token and calls update_my_food_post for the other fields',
        coalesce((select f.prosrc like '%is_salusalo_firebase_jwt()%'
                         and f.prosrc like '%current_salusalo_profile_id()%'
                         and f.prosrc like '%for update%'
                         and f.prosrc like '%public.update_my_food_post(p_food_post_id,%'
                  from fn f, edit_args e where f.proname = 'update_my_food_post_with_pin' and f.args = e.new_args), false),
        'checked in the function source'
    union all
    select 6, 'update_my_food_post (older builds) unchanged: same 12 arguments, SECURITY DEFINER, authenticated only, never touches the pin',
        coalesce((select f.prosecdef and f.proconfig @> array['search_path=public'] and f.result = 'food_posts'
                         and f.auth_x and not f.anon_x and not f.public_x
                         and f.prosrc not like '%pickup_latitude%' and f.prosrc not like '%pickup_longitude%'
                  from fn f, edit_args e where f.proname = 'update_my_food_post' and f.args = e.old_args), false)
        and (select count(*) = 1 from fn where proname = 'update_my_food_post'),
        coalesce((select format('definer=%s, returns=%s, authenticated=%s, anon=%s, public=%s, mentions pin=%s',
                                f.prosecdef, f.result, f.auth_x, f.anon_x, f.public_x,
                                f.prosrc like '%pickup_latitude%' or f.prosrc like '%pickup_longitude%')
                  from fn f, edit_args e where f.proname = 'update_my_food_post' and f.args = e.old_args),
                 'function missing (or different arguments)')
        || '; versions: ' || (select count(*) from fn where proname = 'update_my_food_post')
    union all
    select 7, 'food_posts: anon has nothing; authenticated has SELECT and INSERT only (no UPDATE or DELETE, not even on a column)',
        not exists (select 1 from unnest(array['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER']) p(priv)
                    where has_table_privilege('anon', 'public.food_posts', p.priv))
        and has_table_privilege('authenticated', 'public.food_posts', 'SELECT')
        and has_table_privilege('authenticated', 'public.food_posts', 'INSERT')
        and not exists (select 1 from unnest(array['UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER']) p(priv)
                        where has_table_privilege('authenticated', 'public.food_posts', p.priv))
        and not has_any_column_privilege('authenticated', 'public.food_posts', 'UPDATE')
        and not has_any_column_privilege('anon', 'public.food_posts', 'UPDATE'),
        'authenticated: ' || coalesce((select string_agg(privilege_type, ', ' order by privilege_type)
            from information_schema.role_table_grants
            where table_schema = 'public' and table_name = 'food_posts' and grantee = 'authenticated'), 'none')
        || '; anon: ' || coalesce((select string_agg(privilege_type, ', ' order by privilege_type)
            from information_schema.role_table_grants
            where table_schema = 'public' and table_name = 'food_posts' and grantee = 'anon'), 'none')
    union all
    select 8, 'food_posts policies: one SELECT and one INSERT, no UPDATE, DELETE or ALL policy',
        (select count(*) filter (where polcmd = 'r') = 1 and count(*) filter (where polcmd = 'a') = 1
                and count(*) filter (where polcmd in ('w', 'd', '*')) = 0
         from policies),
        (select string_agg(polcmd::text, ', ' order by polcmd) from policies)
    union all
    select 9, 'the food_posts INSERT policy is unchanged: own donor_id from the token, AVAILABLE, photo paths in the uploader''s folder, no pin rule',
        coalesce((select withcheck like '%is_salusalo_firebase_jwt()%'
                         and withcheck like '%donor_id = %current_salusalo_profile_id()%'
                         and withcheck like '%status = ''AVAILABLE''%'
                         and withcheck like '%image_url IS NULL%'
                         and withcheck like '%extra_image_paths%'
                         and withcheck like '%''..''%'
                         and withcheck not like '%pickup_latitude%' and withcheck not like '%pickup_longitude%'
                  from policies where polcmd = 'a'), false),
        coalesce((select left(withcheck, 200) || '…' from policies where polcmd = 'a'), 'no INSERT policy')
    union all
    select 10, 'the food_posts SELECT policy is unchanged (available and unexpired, own, or reserved), so it decides who sees a pin',
        coalesce((select qual like '%is_salusalo_firebase_jwt()%'
                         and qual like '%status = ''AVAILABLE''%'
                         and qual like '%expiration_date > now()%'
                         and qual like '%donor_id = %current_salusalo_profile_id()%'
                         and qual like '%is_reservation_recipient(%'
                         and qual not like '%pickup_latitude%' and qual not like '%pickup_longitude%'
                  from policies where polcmd = 'r'), false),
        coalesce((select qual from policies where polcmd = 'r'), 'no SELECT policy')
    union all
    select 11, 'no listing has a half-set or out-of-range pin',
        (select bad = 0 from pins),
        (select format('%s of %s listings have a pin; %s invalid', pinned, total, bad) from pins)
)
select n as "#", check_name as "check",
       case when ok then 'PASS' else 'FAIL' end as result,
       details
from checks
order by n;
