-- Salu-Salo admin dashboard: read-only check (PROJECT_STATUS.md §25). Changes
-- nothing. Run in the SQL Editor after migrations/20261012_admin_dashboard.sql:
-- every "ok" should be true (the last row only once an admin is set).

with client_fns(sig) as (values
    ('public.is_salusalo_admin()'), ('public.admin_whoami()'), ('public.admin_counts()'),
    ('public.admin_overview()'), ('public.admin_users(text,text,integer,integer)'), ('public.admin_user(uuid)'),
    ('public.admin_listings(text,text,text,uuid,integer,integer)'), ('public.admin_listing(uuid)'),
    ('public.admin_reservations(text,text,text,integer,integer)'), ('public.admin_reservation(uuid)'),
    ('public.admin_reports(text,text,text,text,integer,integer)'), ('public.admin_report(uuid)'),
    ('public.admin_audit(text,uuid,text,text,integer,integer)'),
    ('public.admin_remove_listing(uuid,text)'), ('public.admin_dismiss_listing_reports(uuid,text)'),
    ('public.admin_dismiss_report(uuid,text)'), ('public.admin_add_report_note(uuid,text)'),
    ('public.admin_mark_listing_reviewed(uuid,text)')),
internal_fns(sig) as (values
    ('public.admin_require()'), ('public.admin_ms(timestamptz)'), ('public.admin_like(text)'),
    ('public.admin_since(text)'), ('public.admin_listing_status(public.food_posts)'),
    ('public.admin_qty(integer,text)'), ('public.admin_reason_label(text,text)'), ('public.admin_who(uuid)'),
    ('public.admin_user_json(public.users)'), ('public.admin_listing_json(public.food_posts)'),
    ('public.admin_reservation_json(public.reservations)'), ('public.admin_report_json(public.reports)'),
    ('public.admin_paging(integer,integer,bigint)'), ('public.admin_log(public.users,text,text,uuid,text,text)'),
    ('public.admin_user_matches(public.users,text,text)'),
    ('public.admin_listing_matches(public.food_posts,text,text,timestamptz,uuid)'),
    ('public.admin_reservation_matches(public.reservations,text,text,timestamptz)'),
    ('public.admin_report_matches(public.reports,text,text,text,text)'),
    ('public.admin_audit_matches(public.admin_audit_log,text,uuid,text,timestamptz)'))
select 'All 18 client functions exist: authenticated yes, anon no' as check_name,
       (select count(*) from client_fns f
        where to_regprocedure(f.sig) is not null
          and has_function_privilege('authenticated', f.sig, 'execute')
          and not has_function_privilege('anon', f.sig, 'execute')) = 18 as ok
union all
select 'All 19 internal helpers exist and no client can run them',
       (select count(*) from internal_fns f
        where to_regprocedure(f.sig) is not null
          and not has_function_privilege('authenticated', f.sig, 'execute')
          and not has_function_privilege('anon', f.sig, 'execute')) = 19
union all
select 'report_notes and admin_audit_log: RLS on, no client privileges',
       (select bool_and(c.relrowsecurity) from pg_class c
        where c.oid in ('public.report_notes'::regclass, 'public.admin_audit_log'::regclass))
       and not exists (select 1 from unnest(array['anon', 'authenticated']) r(role),
                                     unnest(array['public.report_notes', 'public.admin_audit_log']) t(tbl),
                                     unnest(array['select', 'insert', 'update', 'delete']) p(priv)
                       where has_table_privilege(r.role, t.tbl, p.priv))
union all
select 'reports: statuses OPEN, RESOLVED, DISMISSED and the resolution columns',
       coalesce((select pg_get_constraintdef(oid) like '%DISMISSED%' from pg_constraint
                 where conrelid = 'public.reports'::regclass and conname = 'reports_status_check'), false)
       and (select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'reports'
            and column_name in ('resolved_by', 'resolved_at', 'resolution_action', 'resolution_note')) = 4
union all
select 'Clients still have no privileges on reports',
       not exists (select 1 from unnest(array['anon', 'authenticated']) r(role),
                                 unnest(array['select', 'insert', 'update', 'delete']) p(priv)
                   where has_table_privilege(r.role, 'public.reports', p.priv))
union all
select 'The listing-photo rule lets admins read photos',
       (select prosrc like '%is_salusalo_admin()%' from pg_proc
        where oid = 'public.can_read_food_image(text)'::regprocedure)
union all
select 'At least one admin account exists (set it by hand)',
       exists (select 1 from public.users where role = 'ADMIN');
