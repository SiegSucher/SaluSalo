-- Salu-Salo Phase 4 demo fixes: read-only check of the live database.
--
-- Run it in the Supabase SQL Editor (role "postgres") after
-- migrations/20261001_phase4_demo_fixes.sql. It only reads the catalog and
-- changes nothing. It returns one row per check: PASS or FAIL, with details.

with
fn as (
    select p.oid, p.proname, p.prosecdef, p.provolatile, p.proconfig, p.proacl,
           pg_get_function_identity_arguments(p.oid) as args,
           pg_get_function_result(p.oid) as result,
           has_function_privilege('anon', p.oid, 'execute') as anon_x,
           has_function_privilege('authenticated', p.oid, 'execute') as auth_x
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
),
rate as (
    select * from fn where proname = 'get_my_response_rate' and args = ''
),
chat_tables(t) as (
    values ('public.conversations'::regclass), ('public.messages'::regclass), ('public.notifications'::regclass)
),
checks(n, check_name, ok, details) as (
    select 1, 'get_my_response_rate() exists, takes no arguments and returns received, replied, period_days',
        coalesce((select result = 'TABLE(received integer, replied integer, period_days integer)' from rate), false),
        coalesce((select result from rate), 'function missing')
    union all
    select 2, 'get_my_response_rate: SECURITY INVOKER, STABLE, search_path=public',
        coalesce((select not prosecdef and provolatile = 's' and proconfig @> array['search_path=public'] from rate), false),
        coalesce((select format('definer=%s, volatility=%s, config=%s', prosecdef, provolatile, proconfig) from rate),
                 'function missing')
    union all
    select 3, 'get_my_response_rate: executable by authenticated, not by anon or PUBLIC',
        coalesce((select auth_x and not anon_x and proacl is not null
                         and not exists (select 1 from aclexplode(proacl) a
                                         where a.grantee = 0 and a.privilege_type = 'EXECUTE')
                  from rate), false),
        coalesce((select format('anon=%s, authenticated=%s, acl=%s', anon_x, auth_x, proacl) from rate),
                 'function missing')
    union all
    select 4, 'the helpers it calls are executable by authenticated',
        coalesce((select count(*) = 2 and bool_and(auth_x) from fn
                  where proname in ('is_salusalo_firebase_jwt', 'current_salusalo_profile_id') and args = ''), false),
        (select string_agg(format('%s: authenticated=%s', proname, auth_x), '; ' order by proname) from fn
         where proname in ('is_salusalo_firebase_jwt', 'current_salusalo_profile_id') and args = '')
    union all
    select 5, 'anon and authenticated have no INSERT, UPDATE or DELETE on conversations, messages, notifications',
        not exists (select 1 from chat_tables, unnest(array['anon', 'authenticated']) r(role),
                                  unnest(array['INSERT', 'UPDATE', 'DELETE']) p(priv)
                    where has_table_privilege(r.role, t, p.priv)),
        'authenticated: ' || coalesce((select string_agg(table_name || ' ' || privilege_type, ', '
                                                         order by table_name, privilege_type)
            from information_schema.role_table_grants
            where table_schema = 'public' and grantee = 'authenticated'
              and table_name in ('conversations', 'messages', 'notifications')), 'none')
    union all
    select 6, 'conversations, messages and notifications have SELECT policies only',
        not exists (select 1 from pg_policy where polrelid in (select t from chat_tables) and polcmd <> 'r'),
        (select coalesce(string_agg(polrelid::regclass || ': ' || polname || ' (' || polcmd::text || ')', ', '
                                    order by polrelid::regclass::text, polname), 'no policies')
         from pg_policy where polrelid in (select t from chat_tables))
    union all
    select 7, 'notify_user is not executable by anon or authenticated',
        coalesce((select bool_and(not anon_x and not auth_x) from fn where proname = 'notify_user'), false),
        coalesce((select string_agg(format('anon=%s, authenticated=%s', anon_x, auth_x), '; ')
                  from fn where proname = 'notify_user'), 'function missing')
    union all
    select 8, 'messages and notifications are in the supabase_realtime publication',
        (select count(*) = 2 from pg_publication_tables
         where pubname = 'supabase_realtime' and schemaname = 'public' and tablename in ('messages', 'notifications')),
        (select coalesce(string_agg(tablename, ', ' order by tablename), 'none') from pg_publication_tables
         where pubname = 'supabase_realtime' and schemaname = 'public' and tablename in ('messages', 'notifications'))
    union all
    select 9, 'no UPDATE on users, and no UPDATE or DELETE on food_posts, for clients (privileges and policies)',
        not (has_table_privilege('authenticated', 'public.users', 'UPDATE')
             or has_table_privilege('anon', 'public.users', 'UPDATE')
             or has_table_privilege('authenticated', 'public.food_posts', 'UPDATE')
             or has_table_privilege('anon', 'public.food_posts', 'UPDATE')
             or has_table_privilege('authenticated', 'public.food_posts', 'DELETE')
             or has_table_privilege('anon', 'public.food_posts', 'DELETE'))
        and not exists (select 1 from pg_policy where polrelid = 'public.users'::regclass and polcmd in ('w', '*'))
        and not exists (select 1 from pg_policy where polrelid = 'public.food_posts'::regclass and polcmd in ('w', 'd', '*')),
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
