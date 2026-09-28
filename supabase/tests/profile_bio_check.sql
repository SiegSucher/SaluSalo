-- Read-only check for 20261004_profile_bio.sql. It changes nothing and
-- returns one row per check: PASS or FAIL, with details.
with
fn as (
    select p.oid, p.prosecdef, p.proconfig, p.prosrc,
           has_function_privilege('anon', p.oid, 'execute') as anon_x,
           has_function_privilege('authenticated', p.oid, 'execute') as auth_x,
           (p.proacl is null or exists (select 1 from aclexplode(p.proacl) a
                                        where a.grantee = 0 and a.privilege_type = 'EXECUTE')) as public_x
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname = 'update_my_profile_details'
      and pg_get_function_identity_arguments(p.oid) = 'p_name text, p_bio text'
),
bio_col as (
    select format_type(a.atttypid, a.atttypmod) as type, a.attnotnull, a.atthasdef
    from pg_attribute a
    where a.attrelid = 'public.users'::regclass and a.attname = 'bio' and not a.attisdropped
),
bio_check as (
    select c.convalidated, pg_get_constraintdef(c.oid) as def
    from pg_constraint c
    where c.conrelid = 'public.users'::regclass and c.conname = 'users_bio_length_check'
),
checks(n, check_name, ok, details) as (
    select 1, 'users.bio exists: text, nullable, no default',
        coalesce((select type = 'text' and not attnotnull and not atthasdef from bio_col), false),
        coalesce((select format('%s, not null=%s, default=%s', type, attnotnull, atthasdef) from bio_col), 'column missing')
    union all
    select 2, 'users_bio_length_check: a bio has at most 160 characters',
        coalesce((select convalidated and def like '%char_length(bio) <= 160%' from bio_check), false),
        coalesce((select def from bio_check), 'constraint missing')
    union all
    select 3, 'update_my_profile_details: SECURITY DEFINER, search_path=public, executable by authenticated only',
        coalesce((select prosecdef and proconfig @> array['search_path=public'] and auth_x and not anon_x and not public_x from fn), false),
        coalesce((select format('definer=%s, config=%s, authenticated=%s, anon=%s, public=%s',
                                prosecdef, proconfig, auth_x, anon_x, public_x) from fn), 'function missing')
    union all
    select 4, 'update_my_profile_details: caller from the token; never sets role, email or firebase_uid',
        coalesce((select prosrc like '%is_salusalo_firebase_jwt()%'
                         and prosrc like '%auth.jwt() ->> ''sub''%'
                         and prosrc not like '%role =%'
                         and prosrc not like '%email%'
                         and prosrc not like '%set firebase_uid%' from fn), false),
        'checked in the function source'
    union all
    select 5, 'users: clients still have SELECT and INSERT only (no UPDATE or DELETE, not even on a column); anon has nothing',
        has_table_privilege('authenticated', 'public.users', 'SELECT')
        and has_table_privilege('authenticated', 'public.users', 'INSERT')
        and not has_table_privilege('authenticated', 'public.users', 'UPDATE')
        and not has_table_privilege('authenticated', 'public.users', 'DELETE')
        and not has_any_column_privilege('authenticated', 'public.users', 'UPDATE')
        and not exists (select 1 from unnest(array['SELECT', 'INSERT', 'UPDATE', 'DELETE']) p(priv)
                        where has_table_privilege('anon', 'public.users', p.priv)),
        'authenticated: ' || coalesce((select string_agg(privilege_type, ', ' order by privilege_type)
            from information_schema.role_table_grants
            where table_schema = 'public' and table_name = 'users' and grantee = 'authenticated'), 'none')
    union all
    select 6, 'update_my_profile_name (used by older builds) still exists for authenticated',
        exists (select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace
                where n.nspname = 'public' and p.proname = 'update_my_profile_name'
                  and has_function_privilege('authenticated', p.oid, 'execute')),
        'checked in the catalog'
)
select n as "#", check_name as "check",
       case when ok then 'PASS' else 'FAIL' end as result,
       details
from checks
order by n;
