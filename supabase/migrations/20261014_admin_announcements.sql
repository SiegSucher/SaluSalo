-- Salu-Salo admin dashboard: announcements (PROJECT_STATUS.md §27;
-- docs/superpowers/specs/2026-10-04-admin-announcements-design.md).
--
-- Run by hand in the Supabase SQL Editor after 20261013_admin_analytics.sql, and
-- ONLY ONCE EVERY PHONE HAS THE APP BUILD THAT KNOWS ANNOUNCEMENT ALERTS (§27):
-- older builds can't load Alerts once they have one. Then run
-- supabase/tests/admin_announcements_live_safety_check.sql. Safe to run again.
--
-- An admin sends an in-app announcement from the dashboard to Everyone, Donors
-- (has posted a listing) or Recipients (never posted, not an admin): one alert of
-- type ANNOUNCEMENT per account, the history row and the audit row in one
-- transaction. Every function starts with admin_require() (20261012).

-- ---------------------------------------------------------------------------
-- 1. The alert type and the audit log's new action
-- ---------------------------------------------------------------------------

do $$
declare c record;
begin
    for c in
        select con.conname from pg_constraint con
        where con.conrelid = 'public.notifications'::regclass and con.contype = 'c'
          and con.conkey = array[(select attnum from pg_attribute
                                   where attrelid = 'public.notifications'::regclass
                                     and attname = 'type')]::smallint[]
    loop
        execute format('alter table public.notifications drop constraint %I', c.conname);
    end loop;
    alter table public.notifications add constraint notifications_type_check check (type in (
        'MESSAGE', 'RESERVATION_CREATED', 'RESERVATION_CANCELLED', 'RESERVATION_CLAIMED',
        'RESERVATION_REMOVED', 'ANNOUNCEMENT'
    ));

    for c in
        select distinct con.conname from pg_constraint con
        join pg_attribute a on a.attrelid = con.conrelid and a.attnum = any (con.conkey)
        where con.conrelid = 'public.admin_audit_log'::regclass and con.contype = 'c'
          and a.attname in ('action', 'target_type')
    loop
        execute format('alter table public.admin_audit_log drop constraint %I', c.conname);
    end loop;
    alter table public.admin_audit_log add constraint admin_audit_log_action_check check (action in
        ('LISTING_REMOVED', 'REPORTS_DISMISSED', 'LISTING_REVIEWED', 'REPORT_NOTE_ADDED', 'ANNOUNCEMENT_SENT'));
    alter table public.admin_audit_log add constraint admin_audit_log_target_type_check
        check (target_type in ('LISTING', 'REPORT', 'ANNOUNCEMENT'));
end $$;

-- ---------------------------------------------------------------------------
-- 2. What was sent. Only the functions below write or read it.
-- ---------------------------------------------------------------------------

create table if not exists public.announcements (
    id uuid primary key default gen_random_uuid(),
    admin_id uuid references public.users(id) on delete set null,
    admin_name text not null,
    title text not null check (char_length(title) between 1 and 80),
    message text not null check (char_length(message) between 1 and 500),
    audience text not null check (audience in ('ALL', 'DONORS', 'RECIPIENTS')),
    reach integer not null check (reach > 0),
    created_at timestamptz not null default now()
);
create index if not exists announcements_created_idx on public.announcements (created_at desc);
create index if not exists announcements_admin_created_idx on public.announcements (admin_id, created_at);
alter table public.announcements enable row level security;
revoke all on public.announcements from anon;
revoke all on public.announcements from authenticated;

-- ---------------------------------------------------------------------------
-- 3. Functions
-- ---------------------------------------------------------------------------

-- Internal: one announcement as the dashboard shows it.
create or replace function public.admin_announcement_json(a public.announcements)
returns jsonb language sql immutable set search_path = public as $$
    select jsonb_build_object('id', a.id, 'at', public.admin_ms(a.created_at), 'title', a.title,
        'message', a.message, 'audience', a.audience, 'reach', a.reach,
        'admin', jsonb_build_object('id', a.admin_id, 'name', a.admin_name));
$$;

-- The form's counts. The audiences are the Users page's filters (admin_user_matches).
create or replace function public.admin_audience_sizes()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
begin
    return (select jsonb_build_object(
                'ALL', count(*),
                'DONORS', count(*) filter (where public.admin_user_matches(u, null, 'DONOR')),
                'RECIPIENTS', count(*) filter (where public.admin_user_matches(u, null, 'RECIPIENT')))
            from public.users u);
end; $$;

create or replace function public.admin_send_announcement(p_title text, p_message text, p_audience text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    -- Spaces, tabs and line breaks around the text are trimmed, so blank-looking text counts as empty.
    v_title text := btrim(coalesce(p_title, ''), E' \t\r\n');
    v_message text := btrim(coalesce(p_message, ''), E' \t\r\n');
    v_filter text := case p_audience when 'ALL' then 'ALL' when 'DONORS' then 'DONOR'
                                     when 'RECIPIENTS' then 'RECIPIENT' end;
    v_id uuid := gen_random_uuid();
    v_reach integer;
begin
    if v_title = '' then raise exception 'Add a title.'; end if;
    if char_length(v_title) > 80 then raise exception 'The title can be at most 80 characters.'; end if;
    if v_message = '' then raise exception 'Write the message.'; end if;
    if char_length(v_message) > 500 then raise exception 'The message can be at most 500 characters.'; end if;
    if v_filter is null then raise exception 'Choose who gets it.'; end if;

    -- At most 3 announcements per admin per 10 minutes (round 2's rule for
    -- anything that alerts other people), counted under a per-admin lock.
    perform pg_advisory_xact_lock(hashtextextended('salusalo-announce:' || v_admin.id::text, 0));
    if (select count(*) from public.announcements a
        where a.admin_id = v_admin.id and a.created_at > now() - interval '10 minutes') >= 3 then
        raise exception 'You can send up to 3 announcements every 10 minutes. Please wait a few minutes.';
    end if;

    -- Straight into notifications, not through notify_user(): announcements
    -- ignore the alert switches, and notify_user() cuts the text to 300 characters.
    insert into public.notifications (user_id, type, title, body)
    select u.id, 'ANNOUNCEMENT', v_title, v_message
    from public.users u
    where public.admin_user_matches(u, null, v_filter);
    get diagnostics v_reach = row_count;
    if v_reach = 0 then raise exception 'No one is in this audience yet.'; end if;

    insert into public.announcements (id, admin_id, admin_name, title, message, audience, reach)
    values (v_id, v_admin.id, v_admin.name, v_title, v_message, p_audience, v_reach);
    perform public.admin_log(v_admin, 'ANNOUNCEMENT_SENT', 'ANNOUNCEMENT', v_id, v_title,
        format('To %s %s (%s).', v_reach, case when v_reach = 1 then 'person' else 'people' end,
               case p_audience when 'ALL' then 'everyone' when 'DONORS' then 'donors' else 'recipients' end));
    return jsonb_build_object('id', v_id, 'reach', v_reach);
end; $$;

create or replace function public.admin_announcements(p_page integer, p_page_size integer)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_total bigint;
    v_pg record;
    v_rows jsonb;
begin
    select count(*) into v_total from public.announcements;
    select * into v_pg from public.admin_paging(p_page, p_page_size, v_total);
    select coalesce(jsonb_agg(public.admin_announcement_json(a) order by a.created_at desc, a.id), '[]'::jsonb)
      into v_rows
    from public.announcements a
    where a.id in (select a2.id from public.announcements a2
                   order by a2.created_at desc, a2.id limit v_pg.size offset v_pg.skip);
    return jsonb_build_object('rows', v_rows, 'total', v_total, 'page', v_pg.page,
                              'pageSize', v_pg.size, 'pages', v_pg.pages);
end; $$;

create or replace function public.admin_announcement(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
begin
    return (select public.admin_announcement_json(a) from public.announcements a where a.id = p_id);
end; $$;

-- ---------------------------------------------------------------------------
-- 4. Privileges: the four for signed-in callers (admin_require decides), the
-- helper for no client.
-- ---------------------------------------------------------------------------

revoke all on function public.admin_audience_sizes() from public;
revoke all on function public.admin_audience_sizes() from anon;
grant execute on function public.admin_audience_sizes() to authenticated;
revoke all on function public.admin_send_announcement(text, text, text) from public;
revoke all on function public.admin_send_announcement(text, text, text) from anon;
grant execute on function public.admin_send_announcement(text, text, text) to authenticated;
revoke all on function public.admin_announcements(integer, integer) from public;
revoke all on function public.admin_announcements(integer, integer) from anon;
grant execute on function public.admin_announcements(integer, integer) to authenticated;
revoke all on function public.admin_announcement(uuid) from public;
revoke all on function public.admin_announcement(uuid) from anon;
grant execute on function public.admin_announcement(uuid) to authenticated;
revoke all on function public.admin_announcement_json(public.announcements) from public;
revoke all on function public.admin_announcement_json(public.announcements) from anon;
revoke all on function public.admin_announcement_json(public.announcements) from authenticated;
