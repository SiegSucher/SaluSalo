-- Salu-Salo admin dashboard, first slice (PROJECT_STATUS.md §25;
-- docs/superpowers/specs/2026-10-03-admin-real-data-design.md).
--
-- Run by hand in the Supabase SQL Editor after 20261011, then run
-- supabase/tests/admin_dashboard_check.sql and
-- supabase/tests/admin_dashboard_live_safety_check.sql. Safe to run again.
-- App builds are unaffected: nothing they call changes, and the only alert type
-- used is the existing RESERVATION_REMOVED.
--
-- Make an account an admin (it must have signed into the app once):
--   update public.users set role = 'ADMIN' where email = 'you@example.com';
-- Take it back:
--   update public.users set role = 'RECIPIENT' where email = 'you@example.com';
--
-- Every admin function is SECURITY DEFINER, takes the caller from the token and
-- first refuses anyone who isn't an admin (admin_require). Clients get no table
-- privileges; the web dashboard holds only the publishable key.

-- ---------------------------------------------------------------------------
-- 1. Who is an admin
-- ---------------------------------------------------------------------------

-- Storage runs the photo policy as the client, so authenticated needs EXECUTE.
-- It answers only whether the caller is an admin.
create or replace function public.is_salusalo_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.is_salusalo_firebase_jwt()
       and exists (select 1 from public.users u
                   where u.firebase_uid = auth.jwt() ->> 'sub' and u.role = 'ADMIN');
$$;

-- The caller's admin profile, or an error the dashboard recognises by its code.
create or replace function public.admin_require()
returns public.users
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_admin public.users;
begin
    if not public.is_salusalo_firebase_jwt() then
        raise exception 'Not authenticated' using errcode = '28000';
    end if;
    select * into v_admin from public.users u
    where u.firebase_uid = auth.jwt() ->> 'sub' and u.role = 'ADMIN';
    if not found then
        raise exception 'Admin access required' using errcode = '42501';
    end if;
    return v_admin;
end; $$;

-- ---------------------------------------------------------------------------
-- 2. Report statuses, admin notes and the audit log
-- ---------------------------------------------------------------------------

-- Reports could only be OPEN; admins now close them. Drop every check on the
-- status column (whatever its name), then add the new one.
do $$
declare c record;
begin
    for c in
        select con.conname
        from pg_constraint con
        where con.conrelid = 'public.reports'::regclass
          and con.contype = 'c'
          and con.conkey = array[(select attnum from pg_attribute
                                  where attrelid = 'public.reports'::regclass
                                    and attname = 'status')]::smallint[]
    loop
        execute format('alter table public.reports drop constraint %I', c.conname);
    end loop;
end $$;
alter table public.reports add constraint reports_status_check
    check (status in ('OPEN', 'RESOLVED', 'DISMISSED'));

alter table public.reports
    add column if not exists resolved_by uuid references public.users(id) on delete set null,
    add column if not exists resolved_at timestamptz,
    add column if not exists resolution_action text,
    add column if not exists resolution_note text;
alter table public.reports drop constraint if exists reports_resolution_check;
alter table public.reports add constraint reports_resolution_check check (
    (status = 'OPEN' and resolved_by is null and resolved_at is null
        and resolution_action is null and resolution_note is null)
    or (status = 'RESOLVED' and resolved_at is not null and resolution_action = 'LISTING_REMOVED')
    or (status = 'DISMISSED' and resolved_at is not null and resolution_action = 'DISMISSED'));
alter table public.reports drop constraint if exists reports_resolution_note_check;
alter table public.reports add constraint reports_resolution_note_check
    check (resolution_note is null or char_length(resolution_note) between 1 and 1000);
create index if not exists reports_status_created_idx on public.reports (status, created_at desc);
create index if not exists reports_food_post_idx on public.reports (food_post_id);

-- Notes only admins write and read, through the functions below.
create table if not exists public.report_notes (
    id uuid primary key default gen_random_uuid(),
    report_id uuid not null references public.reports(id) on delete cascade,
    admin_id uuid references public.users(id) on delete set null,
    note text not null check (char_length(note) between 1 and 1000),
    created_at timestamptz not null default now()
);
create index if not exists report_notes_report_idx on public.report_notes (report_id, created_at);
alter table public.report_notes enable row level security;
revoke all on public.report_notes from anon;
revoke all on public.report_notes from authenticated;

-- Every admin action. Only the admin functions insert; nothing updates or
-- deletes. admin_name keeps the name the admin had at the time.
create table if not exists public.admin_audit_log (
    id uuid primary key default gen_random_uuid(),
    admin_id uuid references public.users(id) on delete set null,
    admin_name text not null,
    action text not null check (action in
        ('LISTING_REMOVED', 'REPORTS_DISMISSED', 'LISTING_REVIEWED', 'REPORT_NOTE_ADDED')),
    target_type text not null check (target_type in ('LISTING', 'REPORT')),
    target_id uuid not null,
    target_name text not null,
    details text,
    created_at timestamptz not null default now()
);
create index if not exists admin_audit_log_created_idx on public.admin_audit_log (created_at desc);
create index if not exists admin_audit_log_target_idx on public.admin_audit_log (target_id, action, created_at desc);
alter table public.admin_audit_log enable row level security;
revoke all on public.admin_audit_log from anon;
revoke all on public.admin_audit_log from authenticated;

-- ---------------------------------------------------------------------------
-- 3. Internal helpers (no client may run them)
-- ---------------------------------------------------------------------------

-- Times go to the dashboard as epoch milliseconds, as its pages expect.
create or replace function public.admin_ms(p_at timestamptz)
returns bigint language sql immutable set search_path = '' as $$
    select (extract(epoch from p_at) * 1000)::bigint;
$$;

-- A search box's text as an ILIKE pattern that matches it literally, or NULL.
create or replace function public.admin_like(p_q text)
returns text language sql immutable set search_path = '' as $$
    select case when nullif(btrim(coalesce(p_q, '')), '') is null then null
        else '%' || replace(replace(replace(left(btrim(p_q), 100), '\', '\\'), '%', '\%'), '_', '\_') || '%'
    end;
$$;

-- TODAY is the Philippine day.
create or replace function public.admin_since(p_range text)
returns timestamptz language sql stable set search_path = '' as $$
    select case p_range
        when 'TODAY' then date_trunc('day', now() at time zone 'Asia/Manila') at time zone 'Asia/Manila'
        when '7D' then now() - interval '7 days'
        when '30D' then now() - interval '30 days'
    end;
$$;

-- Nothing on the server marks listings EXPIRED; the dashboard shows what the
-- app shows.
create or replace function public.admin_listing_status(p public.food_posts)
returns text language sql stable set search_path = '' as $$
    select case when p.status = 'AVAILABLE'
                 and (p.expiration_date <= now() or p.pickup_end <= now()) then 'EXPIRED'
                else p.status end;
$$;

create or replace function public.admin_qty(p_n integer, p_unit text)
returns text language sql immutable set search_path = '' as $$
    select p_n || ' ' || case when p_n = 1 then
        case p_unit when 'pcs' then 'pc' when 'packs' then 'pack' when 'meals' then 'meal'
                    when 'boxes' then 'box' else p_unit end
        else p_unit end;
$$;

-- The app's labels (model/Review.kt).
create or replace function public.admin_reason_label(p_target text, p_reason text)
returns text language sql immutable set search_path = '' as $$
    select case p_reason
        when 'UNSAFE_FOOD' then 'Unsafe or spoiled food'
        when 'MISLEADING' then 'Wrong or misleading details'
        when 'SPAM_FAKE' then 'Spam or fake listing'
        when 'ABUSIVE' then 'Rude or abusive'
        when 'NO_SHOW' then 'Didn’t show up'
        when 'SCAM' then 'Scam or fraud'
        when 'SPAM' then 'Spam'
        else 'Other' end;
$$;

create or replace function public.admin_who(p_id uuid)
returns jsonb language sql stable security definer set search_path = public as $$
    select coalesce(
        (select jsonb_build_object('id', u.id, 'name', u.name, 'org', false) from public.users u where u.id = p_id),
        jsonb_build_object('id', null, 'name', 'Deleted account', 'org', false));
$$;

-- "DONOR" here means "has posted"; every account the app creates is RECIPIENT.
create or replace function public.admin_user_json(u public.users)
returns jsonb language sql stable security definer set search_path = public as $$
    select jsonb_build_object(
        'id', u.id, 'name', u.name, 'email', u.email, 'org', false,
        'role', case when u.role = 'ADMIN' then 'ADMIN'
                     when exists (select 1 from public.food_posts p where p.donor_id = u.id) then 'DONOR'
                     else 'RECIPIENT' end,
        'emailVerified', u.email_verified,
        'joinedAt', public.admin_ms(u.created_at),
        'openReports', (select count(*) from public.reports r
                        where r.status = 'OPEN' and r.target_type = 'USER' and r.reported_user_id = u.id));
$$;

-- initialQuantity: what is left plus what is held or was picked up, plus what a
-- removal cancelled (removal keeps the quantity).
create or replace function public.admin_listing_json(p public.food_posts)
returns jsonb language sql stable security definer set search_path = public as $$
    select jsonb_build_object(
        'id', p.id, 'name', p.name, 'category', p.category, 'unit', p.unit, 'price', p.price,
        'quantity', p.quantity,
        'initialQuantity', p.quantity + coalesce((select sum(r.quantity) from public.reservations r
            where r.food_post_id = p.id
              and (r.status in ('RESERVED', 'CLAIMED') or r.cancel_reason = 'LISTING_REMOVED')), 0),
        'pickupStart', public.admin_ms(p.pickup_start), 'pickupEnd', public.admin_ms(p.pickup_end),
        'status', public.admin_listing_status(p),
        'createdAt', public.admin_ms(p.created_at),
        'photos', (case when p.image_url is null then 0 else 1 end) + coalesce(cardinality(p.extra_image_paths), 0),
        'coverPath', p.image_url,
        'donor', public.admin_who(p.donor_id),
        'openReports', (select count(*) from public.reports r
                        where r.status = 'OPEN' and r.target_type = 'LISTING' and r.food_post_id = p.id),
        'openReservations', (select count(*) from public.reservations r
                             where r.food_post_id = p.id and r.status = 'RESERVED'));
$$;

create or replace function public.admin_reservation_json(r public.reservations)
returns jsonb language sql stable security definer set search_path = public as $$
    select jsonb_build_object(
        'id', r.id, 'quantity', r.quantity, 'unitPrice', r.unit_price, 'total', r.quantity * r.unit_price,
        'status', r.status, 'cancelReason', r.cancel_reason,
        'createdAt', public.admin_ms(r.created_at), 'updatedAt', public.admin_ms(r.updated_at),
        'pickupDeadline', public.admin_ms(r.pickup_deadline),
        'listing', jsonb_build_object('id', p.id, 'name', p.name, 'category', p.category, 'unit', p.unit,
                                      'status', public.admin_listing_status(p), 'coverPath', p.image_url),
        'donor', public.admin_who(p.donor_id),
        'recipient', public.admin_who(r.recipient_id))
    from public.food_posts p where p.id = r.food_post_id;
$$;

create or replace function public.admin_report_json(rp public.reports)
returns jsonb language sql stable security definer set search_path = public as $$
    select jsonb_build_object(
        'id', rp.id, 'status', rp.status, 'reason', rp.reason, 'targetType', rp.target_type,
        'source', rp.source, 'createdAt', public.admin_ms(rp.created_at),
        'reporter', public.admin_who(rp.reporter_id),
        'target', case when rp.target_type = 'LISTING' then
                coalesce((select jsonb_build_object('type', 'LISTING', 'id', p.id, 'name', p.name,
                                 'category', p.category, 'status', public.admin_listing_status(p),
                                 'donor', public.admin_who(p.donor_id))
                          from public.food_posts p where p.id = rp.food_post_id),
                         jsonb_build_object('type', 'LISTING', 'id', null,
                                            'name', coalesce(rp.listing_name, 'Deleted listing')))
            else coalesce((select jsonb_build_object('type', 'USER', 'id', u.id, 'name', u.name, 'org', false,
                                  'role', u.role)
                           from public.users u where u.id = rp.reported_user_id),
                          jsonb_build_object('type', 'USER', 'id', null, 'name', 'Deleted account'))
            end,
        'resolution', case when rp.status = 'OPEN' then null else jsonb_build_object(
            'action', rp.resolution_action, 'at', public.admin_ms(rp.resolved_at), 'note', rp.resolution_note,
            'byName', coalesce((select u.name from public.users u where u.id = rp.resolved_by), 'A former admin')) end);
$$;

-- Page size 1–50; a page past the end gives the last page.
create or replace function public.admin_paging(p_page integer, p_size integer, p_total bigint,
    out page integer, out size integer, out pages integer, out skip integer)
language sql immutable set search_path = '' as $$
    select least(greatest(coalesce(p_page, 1), 1), x.pages),
           x.size,
           x.pages,
           (least(greatest(coalesce(p_page, 1), 1), x.pages) - 1) * x.size
    from (select s.size, greatest(1, ceil(p_total::numeric / s.size)::integer) as pages
          from (select least(greatest(coalesce(p_size, 10), 1), 50) as size) s) x;
$$;

create or replace function public.admin_log(p_admin public.users, p_action text, p_target_type text,
    p_target_id uuid, p_target_name text, p_details text)
returns void language sql volatile security definer set search_path = public as $$
    insert into public.admin_audit_log (admin_id, admin_name, action, target_type, target_id, target_name, details)
    values (p_admin.id, p_admin.name, p_action, p_target_type, p_target_id, p_target_name, p_details);
$$;

-- ---------------------------------------------------------------------------
-- 4. Admins may read every listing photo, to judge reports
-- ---------------------------------------------------------------------------

-- Same as 20261005, plus "or the caller is an admin".
create or replace function public.can_read_food_image(p_name text)
returns boolean
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_folders text[];
    v_post_id uuid;
    v_profile uuid;
begin
    if p_name is null or not public.is_salusalo_firebase_jwt() then
        return false;
    end if;
    v_folders := storage.foldername(p_name);
    if v_folders[1] = auth.jwt() ->> 'sub' then
        return true;
    end if;
    if coalesce(v_folders[2], '') !~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' then
        return false;
    end if;
    v_post_id := v_folders[2]::uuid;
    v_profile := public.current_salusalo_profile_id();
    if v_profile is null then
        return false;
    end if;

    return exists (
        select 1 from public.food_posts p
        where p.id = v_post_id
          and (p.image_url = p_name or p_name = any(p.extra_image_paths))
          and ((p.status = 'AVAILABLE' and p.expiration_date > now())
               or p.donor_id = v_profile
               or public.is_salusalo_admin()
               or exists (select 1 from public.reservations r
                          where r.food_post_id = p.id and r.recipient_id = v_profile)
               or exists (select 1 from public.saved_listings s
                          where s.food_post_id = p.id and s.user_id = v_profile)
               or exists (select 1 from public.conversations c
                          where c.food_post_id = p.id
                            and (c.recipient_id = v_profile or c.donor_id = v_profile)))
    );
end; $$;
revoke all on function public.can_read_food_image(text) from public;
revoke all on function public.can_read_food_image(text) from anon;
grant execute on function public.can_read_food_image(text) to authenticated;

-- ---------------------------------------------------------------------------
-- 5. Admin reads
-- ---------------------------------------------------------------------------

-- Filters, kept in one place so a page's count and its rows always agree.
create or replace function public.admin_user_matches(u public.users, p_like text, p_filter text)
returns boolean language sql stable security definer set search_path = public as $$
    select (p_like is null or u.name ilike p_like or u.email ilike p_like)
       and case coalesce(p_filter, 'ALL')
             when 'ADMIN' then u.role = 'ADMIN'
             when 'DONOR' then exists (select 1 from public.food_posts p where p.donor_id = u.id)
             when 'RECIPIENT' then u.role <> 'ADMIN'
                  and not exists (select 1 from public.food_posts p where p.donor_id = u.id)
             else true end;
$$;

create or replace function public.admin_listing_matches(p public.food_posts, p_like text, p_status text,
    p_since timestamptz, p_donor uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select (p_donor is null or p.donor_id = p_donor)
       and (p_since is null or p.created_at >= p_since)
       and case coalesce(p_status, 'ALL')
             when 'ALL' then true
             when 'ACTIVE' then public.admin_listing_status(p) in ('AVAILABLE', 'RESERVED')
             when 'REPORTED' then exists (select 1 from public.reports r
                 where r.status = 'OPEN' and r.target_type = 'LISTING' and r.food_post_id = p.id)
             else public.admin_listing_status(p) = p_status end
       and (p_like is null or p.name ilike p_like or p.id::text ilike p_like
            or exists (select 1 from public.users d where d.id = p.donor_id and d.name ilike p_like));
$$;

create or replace function public.admin_reservation_matches(r public.reservations, p_like text, p_status text,
    p_since timestamptz)
returns boolean language sql stable security definer set search_path = public as $$
    select (coalesce(p_status, 'ALL') = 'ALL' or r.status = p_status)
       and (p_since is null or r.created_at >= p_since)
       and (p_like is null or r.id::text ilike p_like
            or exists (select 1 from public.food_posts p left join public.users d on d.id = p.donor_id
                       where p.id = r.food_post_id and (p.name ilike p_like or d.name ilike p_like))
            or exists (select 1 from public.users x where x.id = r.recipient_id and x.name ilike p_like));
$$;

create or replace function public.admin_report_matches(rp public.reports, p_like text, p_status text,
    p_target text, p_reason text)
returns boolean language sql stable security definer set search_path = public as $$
    select (coalesce(p_status, 'OPEN') = 'ALL' or rp.status = coalesce(p_status, 'OPEN'))
       and (coalesce(p_target, 'ALL') = 'ALL' or rp.target_type = p_target)
       and (coalesce(p_reason, 'ALL') = 'ALL' or rp.reason = p_reason)
       and (p_like is null or rp.id::text ilike p_like or rp.listing_name ilike p_like
            or exists (select 1 from public.users x
                       where x.id in (rp.reporter_id, rp.reported_user_id) and x.name ilike p_like)
            or exists (select 1 from public.food_posts p where p.id = rp.food_post_id and p.name ilike p_like));
$$;

create or replace function public.admin_audit_matches(a public.admin_audit_log, p_like text, p_admin uuid,
    p_action text, p_since timestamptz)
returns boolean language sql stable security definer set search_path = public as $$
    select (p_admin is null or a.admin_id = p_admin)
       and (coalesce(p_action, 'ALL') = 'ALL' or a.action = p_action)
       and (p_since is null or a.created_at >= p_since)
       and (p_like is null or a.target_name ilike p_like or a.details ilike p_like
            or a.target_id::text ilike p_like or a.admin_name ilike p_like);
$$;

create or replace function public.admin_whoami()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
begin
    return jsonb_build_object(
        'me', jsonb_build_object('id', v_admin.id, 'name', v_admin.name, 'email', v_admin.email),
        'admins', coalesce((select jsonb_agg(jsonb_build_object('id', u.id, 'name', u.name) order by u.name)
                            from public.users u
                            where u.role = 'ADMIN'
                               or exists (select 1 from public.admin_audit_log a where a.admin_id = u.id)),
                           '[]'::jsonb));
end; $$;

create or replace function public.admin_counts()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
begin
    return jsonb_build_object('openReports', (select count(*) from public.reports where status = 'OPEN'));
end; $$;

create or replace function public.admin_overview()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_today timestamptz := public.admin_since('TODAY');
    v_since timestamptz := now() - interval '3 days';
    v_counts jsonb;
    v_activity jsonb;
    v_reports jsonb;
    v_reservations jsonb;
    v_rescue jsonb;
begin
    v_counts := jsonb_build_object(
        'users', (select count(*) from public.users),
        'usersThisWeek', (select count(*) from public.users where created_at >= now() - interval '7 days'),
        'activeListings', (select count(*) from public.food_posts p
                           where public.admin_listing_status(p) in ('AVAILABLE', 'RESERVED')),
        'listingsToday', (select count(*) from public.food_posts where created_at >= v_today),
        'openReservations', (select count(*) from public.reservations where status = 'RESERVED'),
        'reservationsToday', (select count(*) from public.reservations where created_at >= v_today),
        'pendingReports', (select count(*) from public.reports where status = 'OPEN'),
        'reportsToday', (select count(*) from public.reports where status = 'OPEN' and created_at >= v_today),
        'pendingVerifications', 0);

    select coalesce(jsonb_agg(e.ev order by e.at desc), '[]'::jsonb) into v_activity
    from (
        select a.at, a.ev from (
            select u.created_at as at, jsonb_build_object('type', 'USER_JOINED', 'at', public.admin_ms(u.created_at),
                'parts', jsonb_build_array(jsonb_build_object('b', u.name), ' registered'),
                'meta', 'New account', 'link', '#/users/' || u.id) as ev
            from public.users u
            where u.created_at >= v_since and u.role <> 'ADMIN'
            union all
            select p.created_at, jsonb_build_object('type', 'LISTING', 'at', public.admin_ms(p.created_at),
                'parts', jsonb_build_array(jsonb_build_object('b', d.name), ' posted ', jsonb_build_object('b', p.name)),
                'meta', public.admin_qty(p.quantity, p.unit) || ' · '
                        || case when p.price > 0 then '₱' || to_char(p.price, 'FM999,990.00') || ' each' else 'Free' end,
                'link', '#/listings/' || p.id)
            from public.food_posts p join public.users d on d.id = p.donor_id
            where p.created_at >= v_since
            union all
            select r.created_at, jsonb_build_object('type', 'RESERVED', 'at', public.admin_ms(r.created_at),
                'parts', jsonb_build_array(jsonb_build_object('b', rec.name), ' reserved ' || public.admin_qty(r.quantity, p.unit)),
                'meta', p.name || ' · from ' || d.name, 'link', '#/reservations/' || r.id)
            from public.reservations r
            join public.food_posts p on p.id = r.food_post_id
            join public.users rec on rec.id = r.recipient_id
            join public.users d on d.id = p.donor_id
            where r.created_at >= v_since
            union all
            select r.updated_at, jsonb_build_object('type', 'CLAIMED', 'at', public.admin_ms(r.updated_at),
                'parts', jsonb_build_array('Reservation completed: ', jsonb_build_object('b', p.name)),
                'meta', 'Picked up by ' || rec.name, 'link', '#/reservations/' || r.id)
            from public.reservations r
            join public.food_posts p on p.id = r.food_post_id
            join public.users rec on rec.id = r.recipient_id
            where r.status = 'CLAIMED' and r.updated_at >= v_since
            union all
            select rp.created_at, jsonb_build_object('type', 'REPORT', 'at', public.admin_ms(rp.created_at),
                'parts', jsonb_build_array(jsonb_build_object('b', public.admin_report_json(rp) -> 'target' ->> 'name'),
                                           ' was reported'),
                'meta', public.admin_reason_label(rp.target_type, rp.reason), 'link', '#/reports/' || rp.id)
            from public.reports rp
            where rp.status = 'OPEN' and rp.created_at >= v_since
        ) a
        order by a.at desc
        limit 8
    ) e;

    select coalesce(jsonb_agg(public.admin_report_json(rp) order by rp.created_at desc), '[]'::jsonb) into v_reports
    from public.reports rp
    where rp.id in (select x.id from public.reports x where x.status = 'OPEN' order by x.created_at desc limit 3);

    select coalesce(jsonb_agg(public.admin_reservation_json(r) order by r.created_at desc), '[]'::jsonb) into v_reservations
    from public.reservations r
    where r.id in (select x.id from public.reservations x order by x.created_at desc limit 6);

    v_rescue := jsonb_build_object(
        'itemsRescued30', coalesce((select sum(quantity) from public.reservations
                                    where status = 'CLAIMED' and updated_at >= now() - interval '30 days'), 0),
        'completed30', (select count(*) from public.reservations
                        where status = 'CLAIMED' and updated_at >= now() - interval '30 days'),
        'cancelled30', (select count(*) from public.reservations
                        where status = 'CANCELLED' and updated_at >= now() - interval '30 days'),
        'activeDonors30', (select count(distinct donor_id) from public.food_posts
                           where created_at >= now() - interval '30 days'),
        'weeklyItems', (select jsonb_agg(coalesce((select sum(r.quantity) from public.reservations r
                                                   where r.status = 'CLAIMED'
                                                     and r.updated_at >= now() - (w.i + 1) * interval '7 days'
                                                     and r.updated_at < now() - w.i * interval '7 days'), 0)
                                         order by w.i desc)
                        from generate_series(0, 11) as w(i)),
        'weekStarts', (select jsonb_agg(public.admin_ms(now() - (w.i + 1) * interval '7 days') order by w.i desc)
                       from generate_series(0, 11) as w(i)));

    return jsonb_build_object('counts', v_counts, 'activity', v_activity, 'verification', '[]'::jsonb,
        'reports', v_reports, 'reservations', v_reservations, 'rescue', v_rescue);
end; $$;

create or replace function public.admin_users(p_q text, p_filter text, p_page integer, p_page_size integer)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_like text := public.admin_like(p_q);
    v_total bigint;
    v_pg record;
    v_rows jsonb;
begin
    select count(*) into v_total from public.users u where public.admin_user_matches(u, v_like, p_filter);
    select * into v_pg from public.admin_paging(p_page, p_page_size, v_total);
    select coalesce(jsonb_agg(public.admin_user_json(u) order by u.created_at desc, u.id), '[]'::jsonb) into v_rows
    from public.users u
    where u.id in (select u2.id from public.users u2
                   where public.admin_user_matches(u2, v_like, p_filter)
                   order by u2.created_at desc, u2.id limit v_pg.size offset v_pg.skip);
    return jsonb_build_object('rows', v_rows, 'total', v_total, 'page', v_pg.page,
                              'pageSize', v_pg.size, 'pages', v_pg.pages);
end; $$;

create or replace function public.admin_user(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    u public.users;
begin
    select * into u from public.users where id = p_id;
    if not found then
        return null;
    end if;
    return public.admin_user_json(u) || jsonb_build_object(
        'stats', jsonb_build_object(
            'listings', (select count(*) from public.food_posts p where p.donor_id = u.id),
            'activeListings', (select count(*) from public.food_posts p
                               where p.donor_id = u.id and public.admin_listing_status(p) in ('AVAILABLE', 'RESERVED')),
            'reservations', (select count(*) from public.reservations r where r.recipient_id = u.id),
            'completed', (select count(*) from public.reservations r where r.recipient_id = u.id and r.status = 'CLAIMED'),
            'handedOver', (select count(*) from public.reservations r join public.food_posts p on p.id = r.food_post_id
                           where p.donor_id = u.id and r.status = 'CLAIMED'),
            'reportsAgainst', (select count(*) from public.reports rp
                               where rp.target_type = 'USER' and rp.reported_user_id = u.id),
            'reportsOnListings', (select count(*) from public.reports rp
                                  where rp.target_type = 'LISTING' and rp.reported_user_id = u.id)),
        'openReports', coalesce((select jsonb_agg(public.admin_report_json(rp) order by rp.created_at desc)
                                 from public.reports rp where rp.status = 'OPEN' and rp.reported_user_id = u.id),
                                '[]'::jsonb),
        'events', coalesce((select jsonb_agg(e.ev order by e.at desc) from (
            select a.at, a.ev from (
                select p.created_at as at, jsonb_build_object('at', public.admin_ms(p.created_at), 'icon', 'lunchDining',
                    'text', 'Posted ' || p.name, 'meta', public.admin_qty(p.quantity, p.unit),
                    'link', '#/listings/' || p.id) as ev
                from public.food_posts p where p.donor_id = u.id
                union all
                select r.created_at, jsonb_build_object('at', public.admin_ms(r.created_at), 'icon', 'bookmark',
                    'text', 'Reserved ' || public.admin_qty(r.quantity, p.unit) || ' of ' || p.name,
                    'meta', 'From ' || coalesce(d.name, 'a deleted account'), 'link', '#/reservations/' || r.id)
                from public.reservations r join public.food_posts p on p.id = r.food_post_id
                left join public.users d on d.id = p.donor_id
                where r.recipient_id = u.id
                union all
                select r.updated_at, jsonb_build_object('at', public.admin_ms(r.updated_at), 'icon', 'taskAlt',
                    'text', 'Picked up ' || p.name,
                    'meta', 'From ' || coalesce(d.name, 'a deleted account'), 'link', '#/reservations/' || r.id)
                from public.reservations r join public.food_posts p on p.id = r.food_post_id
                left join public.users d on d.id = p.donor_id
                where r.recipient_id = u.id and r.status = 'CLAIMED'
                union all
                select u.created_at, jsonb_build_object('at', public.admin_ms(u.created_at), 'icon', 'personAdd',
                    'text', 'Joined Salu-Salo', 'meta', null, 'link', null)
            ) a
            order by a.at desc
            limit 6) e), '[]'::jsonb));
end; $$;

create or replace function public.admin_listings(p_q text, p_status text, p_posted text, p_donor uuid,
    p_page integer, p_page_size integer)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_like text := public.admin_like(p_q);
    v_since timestamptz := public.admin_since(p_posted);
    v_total bigint;
    v_pg record;
    v_rows jsonb;
begin
    select count(*) into v_total from public.food_posts p
    where public.admin_listing_matches(p, v_like, p_status, v_since, p_donor);
    select * into v_pg from public.admin_paging(p_page, p_page_size, v_total);
    select coalesce(jsonb_agg(public.admin_listing_json(p) order by p.created_at desc, p.id), '[]'::jsonb) into v_rows
    from public.food_posts p
    where p.id in (select p2.id from public.food_posts p2
                   where public.admin_listing_matches(p2, v_like, p_status, v_since, p_donor)
                   order by p2.created_at desc, p2.id limit v_pg.size offset v_pg.skip);
    return jsonb_build_object('rows', v_rows, 'total', v_total, 'page', v_pg.page,
                              'pageSize', v_pg.size, 'pages', v_pg.pages,
                              'donorName', (select name from public.users where id = p_donor));
end; $$;

create or replace function public.admin_listing(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    p public.food_posts;
    v_removed public.admin_audit_log;
    v_review public.admin_audit_log;
begin
    select * into p from public.food_posts where id = p_id;
    if not found then
        return null;
    end if;
    select * into v_removed from public.admin_audit_log a
    where a.target_id = p.id and a.action = 'LISTING_REMOVED' order by a.created_at desc limit 1;
    select * into v_review from public.admin_audit_log a
    where a.target_id = p.id and a.action = 'LISTING_REVIEWED' order by a.created_at desc limit 1;
    return public.admin_listing_json(p) || jsonb_build_object(
        'description', p.description, 'allergens', p.allergens, 'storageNotes', p.storage_notes,
        'pickupLocation', p.pickup_location, 'handover', p.handover,
        'expiresAt', public.admin_ms(p.expiration_date), 'updatedAt', public.admin_ms(p.updated_at),
        'photoPaths', to_jsonb(array_remove(array[p.image_url] || coalesce(p.extra_image_paths, '{}'::text[]), null)),
        'donor', public.admin_who(p.donor_id)
                 || coalesce((select jsonb_build_object('email', d.email, 'verification', 'NONE')
                              from public.users d where d.id = p.donor_id), '{}'::jsonb),
        'reviewedAt', public.admin_ms(v_review.created_at), 'reviewedByName', v_review.admin_name,
        'removedByName', case when p.status <> 'REMOVED' then null
                              when v_removed.id is not null then v_removed.admin_name
                              else 'the donor' end,
        'removedReason', v_removed.details,
        'reservations', coalesce((select jsonb_agg(public.admin_reservation_json(r) order by r.created_at desc)
                                  from public.reservations r where r.food_post_id = p.id), '[]'::jsonb),
        'reports', coalesce((select jsonb_agg(public.admin_report_json(rp) order by rp.created_at desc)
                             from public.reports rp where rp.target_type = 'LISTING' and rp.food_post_id = p.id),
                            '[]'::jsonb));
end; $$;

create or replace function public.admin_reservations(p_q text, p_status text, p_date text,
    p_page integer, p_page_size integer)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_like text := public.admin_like(p_q);
    v_since timestamptz := public.admin_since(p_date);
    v_total bigint;
    v_pg record;
    v_rows jsonb;
begin
    select count(*) into v_total from public.reservations r
    where public.admin_reservation_matches(r, v_like, p_status, v_since);
    select * into v_pg from public.admin_paging(p_page, p_page_size, v_total);
    select coalesce(jsonb_agg(public.admin_reservation_json(r) order by r.created_at desc, r.id), '[]'::jsonb) into v_rows
    from public.reservations r
    where r.id in (select r2.id from public.reservations r2
                   where public.admin_reservation_matches(r2, v_like, p_status, v_since)
                   order by r2.created_at desc, r2.id limit v_pg.size offset v_pg.skip);
    return jsonb_build_object('rows', v_rows, 'total', v_total, 'page', v_pg.page,
                              'pageSize', v_pg.size, 'pages', v_pg.pages);
end; $$;

create or replace function public.admin_reservation(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    r public.reservations;
begin
    select * into r from public.reservations where id = p_id;
    if not found then
        return null;
    end if;
    return public.admin_reservation_json(r) || (
        select jsonb_build_object('pickupStart', public.admin_ms(p.pickup_start),
            'pickupEnd', public.admin_ms(p.pickup_end), 'pickupLocation', p.pickup_location,
            'handover', p.handover, 'listingStatus', public.admin_listing_status(p))
        from public.food_posts p where p.id = r.food_post_id);
end; $$;

create or replace function public.admin_reports(p_q text, p_status text, p_target text, p_reason text,
    p_page integer, p_page_size integer)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_like text := public.admin_like(p_q);
    v_total bigint;
    v_pg record;
    v_rows jsonb;
begin
    select count(*) into v_total from public.reports rp
    where public.admin_report_matches(rp, v_like, p_status, p_target, p_reason);
    select * into v_pg from public.admin_paging(p_page, p_page_size, v_total);
    select coalesce(jsonb_agg(public.admin_report_json(rp) order by rp.created_at desc, rp.id), '[]'::jsonb) into v_rows
    from public.reports rp
    where rp.id in (select r2.id from public.reports r2
                    where public.admin_report_matches(r2, v_like, p_status, p_target, p_reason)
                    order by r2.created_at desc, r2.id limit v_pg.size offset v_pg.skip);
    return jsonb_build_object('rows', v_rows, 'total', v_total, 'page', v_pg.page,
                              'pageSize', v_pg.size, 'pages', v_pg.pages);
end; $$;

create or replace function public.admin_report(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    rp public.reports;
begin
    select * into rp from public.reports where id = p_id;
    if not found then
        return null;
    end if;
    return public.admin_report_json(rp) || jsonb_build_object(
        'details', rp.details, 'listingName', rp.listing_name,
        'notes', coalesce((select jsonb_agg(jsonb_build_object('at', public.admin_ms(n.created_at), 'text', n.note,
                                                               'by', coalesce(a.name, 'A former admin'))
                                            order by n.created_at)
                           from public.report_notes n left join public.users a on a.id = n.admin_id
                           where n.report_id = rp.id), '[]'::jsonb),
        'reporterDetail', (select public.admin_user_json(u)
                                  || jsonb_build_object('reportsFiled',
                                         (select count(*) from public.reports x where x.reporter_id = u.id))
                           from public.users u where u.id = rp.reporter_id),
        'targetDetail', case when rp.target_type = 'LISTING'
            then (select public.admin_listing_json(p) from public.food_posts p where p.id = rp.food_post_id)
            else (select public.admin_user_json(u) from public.users u where u.id = rp.reported_user_id) end,
        'otherReports', coalesce((select jsonb_agg(public.admin_report_json(x) order by x.created_at desc)
                                  from public.reports x
                                  where x.id <> rp.id and x.target_type = rp.target_type
                                    and case when rp.target_type = 'LISTING' then x.food_post_id = rp.food_post_id
                                             else x.reported_user_id = rp.reported_user_id end),
                                 '[]'::jsonb));
end; $$;

create or replace function public.admin_audit(p_q text, p_admin uuid, p_action text, p_range text,
    p_page integer, p_page_size integer)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_like text := public.admin_like(p_q);
    v_since timestamptz := public.admin_since(p_range);
    v_total bigint;
    v_pg record;
    v_rows jsonb;
begin
    select count(*) into v_total from public.admin_audit_log a
    where public.admin_audit_matches(a, v_like, p_admin, p_action, v_since);
    select * into v_pg from public.admin_paging(p_page, p_page_size, v_total);
    select coalesce(jsonb_agg(jsonb_build_object(
               'id', a.id, 'at', public.admin_ms(a.created_at), 'adminId', a.admin_id, 'action', a.action,
               'targetType', a.target_type, 'targetId', a.target_id, 'targetName', a.target_name,
               'details', a.details, 'admin', jsonb_build_object('id', a.admin_id, 'name', a.admin_name))
             order by a.created_at desc, a.id), '[]'::jsonb) into v_rows
    from public.admin_audit_log a
    where a.id in (select a2.id from public.admin_audit_log a2
                   where public.admin_audit_matches(a2, v_like, p_admin, p_action, v_since)
                   order by a2.created_at desc, a2.id limit v_pg.size offset v_pg.skip);
    return jsonb_build_object('rows', v_rows, 'total', v_total, 'page', v_pg.page,
                              'pageSize', v_pg.size, 'pages', v_pg.pages);
end; $$;

-- ---------------------------------------------------------------------------
-- 7. Privileges
-- ---------------------------------------------------------------------------
revoke all on function public.is_salusalo_admin() from public;
revoke all on function public.is_salusalo_admin() from anon;
grant execute on function public.is_salusalo_admin() to authenticated;

do $$
declare f text;
begin
    foreach f in array array[
        'admin_whoami()', 'admin_counts()', 'admin_overview()', 'admin_users(text,text,integer,integer)',
        'admin_user(uuid)', 'admin_listings(text,text,text,uuid,integer,integer)', 'admin_listing(uuid)',
        'admin_reservations(text,text,text,integer,integer)', 'admin_reservation(uuid)',
        'admin_reports(text,text,text,text,integer,integer)', 'admin_report(uuid)',
        'admin_audit(text,uuid,text,text,integer,integer)']
    loop
        execute format('revoke all on function public.%s from public', f);
        execute format('revoke all on function public.%s from anon', f);
        execute format('grant execute on function public.%s to authenticated', f);
    end loop;
end $$;
