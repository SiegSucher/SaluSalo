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
-- 7. Privileges
-- ---------------------------------------------------------------------------
revoke all on function public.is_salusalo_admin() from public;
revoke all on function public.is_salusalo_admin() from anon;
grant execute on function public.is_salusalo_admin() to authenticated;
