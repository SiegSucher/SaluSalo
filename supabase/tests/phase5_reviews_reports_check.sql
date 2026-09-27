-- Salu-Salo Phase 5 part 1 (reviews, reports, Email verified): read-only check
-- of the live database.
--
-- Run it in the Supabase SQL Editor (role "postgres") after
-- migrations/20261002_phase5_reviews_reports.sql. It only reads the catalog and
-- changes nothing. It returns one row per check: PASS or FAIL, with details.

with
fn as (
    select p.oid, p.proname, p.prosecdef, p.proconfig, p.proacl,
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
app_fn(n, proname, args) as (
    values (7, 'sync_my_email_verified', ''),
           (8, 'submit_review', 'p_reservation_id uuid, p_rating integer, p_comment text'),
           (9, 'get_my_given_reviews', ''),
           (10, 'get_listing_donor_profiles', 'p_food_post_ids uuid[]'),
           (11, 'get_listing_donor_reviews', 'p_food_post_id uuid, p_limit integer'),
           (12, 'get_my_rating_summary', ''),
           (13, 'get_my_received_reviews', 'p_limit integer'),
           (14, 'submit_report', 'p_target text, p_target_id uuid, p_reason text, p_details text')
),
new_tables(t) as (
    values ('public.reviews'::regclass), ('public.reports'::regclass)
),
constraint_defs as (
    select conrelid, string_agg(pg_get_constraintdef(oid), ' | ' order by conname) as defs
    from pg_constraint
    where conrelid in ('public.reviews'::regclass, 'public.reports'::regclass)
    group by conrelid
),
index_defs as (
    select string_agg(pg_get_indexdef(indexrelid), ' | ' order by indexrelid::regclass::text) as defs
    from pg_index where indrelid = 'public.reports'::regclass and indisunique and not indisprimary
),
select_policy as (
    select pg_get_expr(polqual, polrelid) as expr
    from pg_policy
    where polrelid = 'public.food_posts'::regclass and polcmd = 'r'
),
checks(n, check_name, ok, details) as (
    select 1, 'users.email_verified exists: boolean, not null, default false',
        coalesce((select format_type(a.atttypid, a.atttypmod) = 'boolean' and a.attnotnull
                         and pg_get_expr(d.adbin, d.adrelid) = 'false'
                  from pg_attribute a
                  left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
                  where a.attrelid = 'public.users'::regclass and a.attname = 'email_verified'
                    and not a.attisdropped), false),
        coalesce((select format('%s, not null=%s', format_type(a.atttypid, a.atttypmod), a.attnotnull)
                  from pg_attribute a
                  where a.attrelid = 'public.users'::regclass and a.attname = 'email_verified'
                    and not a.attisdropped), 'column missing')
    union all
    select 2, 'users_start_unverified: BEFORE INSERT trigger on users, enabled; its function executable by no client',
        coalesce((select t.tgenabled = 'O' and (t.tgtype & 2) = 2 and (t.tgtype & 4) = 4
                  from pg_trigger t
                  where t.tgrelid = 'public.users'::regclass and t.tgname = 'users_start_unverified'), false)
        and coalesce((select not anon_x and not auth_x and not public_x from fn
                      where proname = 'users_start_unverified' and args = ''), false),
        coalesce((select format('function: anon=%s, authenticated=%s, public=%s', anon_x, auth_x, public_x)
                  from fn where proname = 'users_start_unverified' and args = ''), 'function missing')
    union all
    select 3, 'reviews and reports: RLS on, no policies, and no privileges at all for anon or authenticated',
        (select count(*) = 2 and bool_and(c.relrowsecurity) from pg_class c where c.oid in (select t from new_tables))
        and not exists (select 1 from pg_policy where polrelid in (select t from new_tables))
        and not exists (select 1 from new_tables, unnest(array['anon', 'authenticated']) r(role),
                               unnest(array['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE', 'REFERENCES', 'TRIGGER']) p(priv)
                        where has_table_privilege(r.role, t, p.priv)),
        coalesce((select string_agg(table_name || ' ' || grantee || ' ' || privilege_type, ', ')
                  from information_schema.role_table_grants
                  where table_schema = 'public' and table_name in ('reviews', 'reports')
                    and grantee in ('anon', 'authenticated', 'PUBLIC')), 'no client grants')
        || '; policies: ' || (select count(*) from pg_policy where polrelid in (select t from new_tables))
    union all
    select 4, 'reviews: one per reservation, rating 1-5, comment 1-500 characters, reviewer is not the reviewee',
        coalesce((select defs like '%UNIQUE (reservation_id)%'
                         and defs like '%rating >= 1%' and defs like '%rating <= 5%'
                         and defs like '%char_length(comment) <= 500%'
                         and defs like '%reviewer_id <> reviewee_id%'
                  from constraint_defs where conrelid = 'public.reviews'::regclass), false),
        coalesce((select defs from constraint_defs where conrelid = 'public.reviews'::regclass), 'table missing')
    union all
    select 5, 'reports: each reason only for its target, OTHER needs details, details up to 1000, status only OPEN',
        coalesce((select defs like '%''UNSAFE_FOOD''%' and defs like '%''NO_SHOW''%'
                         and defs like '%reason <> ''OTHER''%details IS NOT NULL%'
                         and defs like '%char_length(details) <= 1000%'
                         and defs like '%status = ''OPEN''%'
                         and defs like '%reporter_id <> reported_user_id%'
                  from constraint_defs where conrelid = 'public.reports'::regclass), false),
        coalesce((select defs from constraint_defs where conrelid = 'public.reports'::regclass), 'table missing')
    union all
    select 6, 'reports: one open report per reporter and listing, and per reporter and person (partial unique indexes)',
        coalesce((select defs like '%reports_open_listing_key%(reporter_id, food_post_id)%OPEN%LISTING%'
                         and defs like '%reports_open_user_key%(reporter_id, reported_user_id)%OPEN%USER%'
                  from index_defs), false),
        coalesce((select defs from index_defs), 'indexes missing')
    union all
    select a.n, a.proname || ': SECURITY DEFINER, search_path=public, executable by authenticated only',
        coalesce((select f.prosecdef and f.proconfig @> array['search_path=public']
                         and f.auth_x and not f.anon_x and not f.public_x
                  from fn f where f.proname = a.proname and f.args = a.args), false),
        coalesce((select format('definer=%s, config=%s, authenticated=%s, anon=%s, public=%s',
                                f.prosecdef, f.proconfig, f.auth_x, f.anon_x, f.public_x)
                  from fn f where f.proname = a.proname and f.args = a.args), 'function missing')
    from app_fn a
    union all
    select 15, 'caller_can_view_food_post (helper): SECURITY DEFINER, search_path=public, executable by no client',
        coalesce((select prosecdef and proconfig @> array['search_path=public']
                         and not anon_x and not auth_x and not public_x
                  from fn where proname = 'caller_can_view_food_post' and args = 'p_food_post_id uuid'), false),
        coalesce((select format('definer=%s, config=%s, authenticated=%s, anon=%s, public=%s',
                                prosecdef, proconfig, auth_x, anon_x, public_x)
                  from fn where proname = 'caller_can_view_food_post' and args = 'p_food_post_id uuid'),
                 'function missing')
    union all
    select 16, 'the food_posts SELECT policy still has the parts the helper copies (if not, update the helper)',
        coalesce((select count(*) = 1
                         and bool_and(expr like '%is_salusalo_firebase_jwt()%'
                                      and expr like '%status = ''AVAILABLE''%'
                                      and expr like '%expiration_date > now()%'
                                      and expr like '%donor_id = %current_salusalo_profile_id()%'
                                      and expr like '%is_reservation_recipient(%')
                  from select_policy), false),
        coalesce((select string_agg(expr, ' || ') from select_policy), 'no SELECT policy')
    union all
    select 17, 'get_listing_donor_names is unchanged for older builds (same result columns, authenticated only)',
        coalesce((select result = 'TABLE(food_post_id uuid, donor_name text)' and auth_x and not anon_x
                  from fn where proname = 'get_listing_donor_names' and args = 'p_food_post_ids uuid[]'), false),
        coalesce((select format('%s, authenticated=%s, anon=%s', result, auth_x, anon_x)
                  from fn where proname = 'get_listing_donor_names' and args = 'p_food_post_ids uuid[]'),
                 'function missing')
    union all
    select 18, 'no UPDATE on users, no UPDATE or DELETE on food_posts, no client writes on reservations, conversations, messages, notifications',
        not exists (select 1 from unnest(array['anon', 'authenticated']) r(role),
                           (values ('public.users'::regclass, 'UPDATE'),
                                   ('public.food_posts'::regclass, 'UPDATE'), ('public.food_posts'::regclass, 'DELETE'),
                                   ('public.reservations'::regclass, 'INSERT'), ('public.reservations'::regclass, 'UPDATE'),
                                   ('public.reservations'::regclass, 'DELETE'),
                                   ('public.conversations'::regclass, 'INSERT'), ('public.conversations'::regclass, 'UPDATE'),
                                   ('public.conversations'::regclass, 'DELETE'),
                                   ('public.messages'::regclass, 'INSERT'), ('public.messages'::regclass, 'UPDATE'),
                                   ('public.messages'::regclass, 'DELETE'),
                                   ('public.notifications'::regclass, 'INSERT'), ('public.notifications'::regclass, 'UPDATE'),
                                   ('public.notifications'::regclass, 'DELETE')) w(t, priv)
                    where has_table_privilege(r.role, w.t, w.priv))
        and not exists (select 1 from pg_policy
                        where (polrelid = 'public.users'::regclass and polcmd in ('w', 'd', '*'))
                           or (polrelid = 'public.food_posts'::regclass and polcmd in ('w', 'd', '*'))
                           or (polrelid in ('public.reservations'::regclass, 'public.conversations'::regclass,
                                            'public.messages'::regclass, 'public.notifications'::regclass)
                               and polcmd <> 'r')),
        'users: ' || coalesce((select string_agg(privilege_type, ', ' order by privilege_type)
            from information_schema.role_table_grants
            where table_schema = 'public' and table_name = 'users' and grantee = 'authenticated'), 'none')
        || '; food_posts: ' || coalesce((select string_agg(privilege_type, ', ' order by privilege_type)
            from information_schema.role_table_grants
            where table_schema = 'public' and table_name = 'food_posts' and grantee = 'authenticated'), 'none')
)
select n as "#", check_name as "check",
       case when ok then 'PASS' else 'FAIL' end as result,
       details
from checks
order by n;
