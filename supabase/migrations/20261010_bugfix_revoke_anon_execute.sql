-- Salu-Salo bug fix pass, round 2, group 4: no function is executable without a login (audit #32).
-- Run after 20261009_bugfix_storage_and_rate_limits.sql. Safe to run again,
-- and safe on the live data: it changes only function privileges. Every app
-- build keeps working: the app calls functions only after sign-in, as
-- `authenticated`.
--
-- Supabase's default privileges give `anon` (a request without a login)
-- EXECUTE on new functions, and PUBLIC has EXECUTE unless revoked. The later
-- migrations revoked both; the Phase 1-4 ones often only revoked PUBLIC, so
-- `anon` could still call, for example, is_reservation_recipient() and the
-- Phase 4 chat functions. They all refused (each checks the Firebase token
-- first), so nothing leaked; this removes the privilege itself.
--
-- For every function in the public schema that doesn't belong to an
-- extension:
--   * EXECUTE is revoked from anon;
--   * if anon could still run it through PUBLIC, EXECUTE is granted to
--     `authenticated` and `service_role` explicitly first, then revoked from
--     PUBLIC, so signed-in users keep exactly the access they had.
-- Functions already closed to clients (notify_user, caller_can_view_food_post,
-- food_posts_check_values, ...) are not granted anything.

do $$
declare
    r record;
begin
    for r in
        select p.oid::regprocedure as fn
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace
        where n.nspname = 'public'
          and not exists (select 1 from pg_depend d
                          where d.classid = 'pg_proc'::regclass and d.objid = p.oid and d.deptype = 'e')
    loop
        execute format('revoke execute on function %s from anon', r.fn);
        if has_function_privilege('anon', r.fn, 'EXECUTE') then
            execute format('grant execute on function %s to authenticated, service_role', r.fn);
            execute format('revoke execute on function %s from public', r.fn);
        end if;
    end loop;
end $$;
