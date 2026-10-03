# Admin Dashboard with Real Data: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the existing `admin/` web dashboard to the live Salu-Salo data through admin-only Supabase functions, behind a Firebase sign-in, hosted on Firebase Hosting so the developer can open it on a phone.

**Architecture:** One new migration adds `is_salusalo_admin()`, internal JSON helpers, admin-only `SECURITY DEFINER` read and write functions, report statuses and notes, an append-only audit log, and an admin branch in the listing-photo rule. The static site keeps its pages; `js/data.js` is rewritten to call those functions through supabase-js (Firebase ID token as the access token), a new `js/auth.js` gates the app behind sign-in, and a few pages lose sample-only features. `firebase.json` serves `admin/`.

**Tech Stack:** PostgreSQL / Supabase (PostgREST RPC, Storage), plain JavaScript (no build), Firebase Auth compat SDK 10.14.1 and supabase-js 2.49.4 from CDNs, Firebase Hosting through `npx firebase-tools`, Node 24 for one unit check, a portable PostgreSQL 16 for the local dry run.

**Spec:** `docs/superpowers/specs/2026-10-03-admin-real-data-design.md`

## Global Constraints

- Admin = `users.role = 'ADMIN'`, set by hand in SQL. Never set or change roles from the dashboard or the app.
- Every new client-callable function: `SECURITY DEFINER`, `set search_path = public`, first statement `public.admin_require()`, then `revoke all … from public`, `revoke all … from anon`, `grant execute … to authenticated`. Internal helpers: revoked from `public`, `anon` and `authenticated`.
- No table grants, no RLS widening, no service-role key, no Firebase Admin credentials anywhere in the site or repo.
- Admin read functions never return `firebase_uid`, `phone_number` or `profile_image_url`.
- Listings are never deleted by an admin: `status = 'REMOVED'`, quantity unchanged, open reservations cancelled with `cancel_reason = 'LISTING_REMOVED'`.
- Alerts use only the existing type `RESERVATION_REMOVED` (the app's `NotificationType` is a strict enum).
- `admin/js/config.js` is git-ignored; never print or commit the Supabase URL, publishable key or Firebase web config values.
- Signed photo URLs: 1 hour, never logged, never shown in an error.
- Errors on screen: only the pages' fixed texts or the functions' own short `raise exception` texts; never PostgREST `details`/`hint`.
- The Android app does not change. `./gradlew assembleDebug` runs once as the usual gate.
- Migrations are applied by the developer, by hand, in the SQL Editor. Never apply them yourself (the Supabase MCP must not be used to write to the live project).

## Review Focus

1. **A non-admin who signs in** (any app account): must be refused at `admin_whoami` and signed out with "This account doesn't have admin access.", not shown an empty dashboard. Covered by safety check 2 and manual step M2.
2. **An admin's role removed while the page is open:** the next call returns `42501`; the page must sign out with "Your admin access ended. Please sign in again." (`data.js` `rpc()` → `onAccessLost`). Manual step M7.
3. **Search text with `%`, `_` or `\`:** must match literally, not as wildcards (`admin_like`). Covered by local check L3 in Task 3.
4. **A listing removed twice (double tap or two admins):** the second call must be refused with "This listing was already removed." and send no second alert (row lock + status check). Safety check 15.
5. **A page number past the end** (rows deleted between loads): the server clamps to the last page instead of returning an empty page (`admin_paging`). Covered by local check L4 in Task 3.

---

## File Structure

| File | Responsibility |
|---|---|
| `supabase/migrations/20261012_admin_dashboard.sql` (new) | All database changes for this slice |
| `supabase/tests/admin_dashboard_check.sql` (new) | Read-only check: objects and privileges |
| `supabase/tests/admin_dashboard_live_safety_check.sql` (new) | Behaviour check that rolls itself back |
| `admin/js/config.example.js` (new), `admin/js/config.js` (new, git-ignored) | Supabase and Firebase web settings |
| `admin/js/auth.js` (new) | Sign-in screen, Firebase Auth, Supabase client, starting the app |
| `admin/js/timeline.js` (new) | Pure: a reservation's timeline steps |
| `admin/tests/timeline.test.js` (new) | Node check for `timeline.js` |
| `admin/js/data.js` (rewrite) | The only data access: RPCs, photo signing, cached counts |
| `admin/js/app.js`, `admin/index.html`, `admin/js/ui.js`, `admin/css/admin.css` (edit) | Shell: start after sign-in, sign-in markup, `photo()`, `fmt.ref()` |
| `admin/js/pages/*.js` (edit; `users.js`, `settings.js`, `analytics.js`, `notifications.js` rewritten) | Pages on real data |
| `admin/js/sample-data.js` (delete) | — |
| `firebase.json`, `.firebaserc` (new), `.gitignore` (edit) | Hosting |
| `admin/README.md`, `supabase/README.md`, `docs/PROJECT_STATUS.md`, `CLAUDE.md` (edit) | Docs |

Spec adjustment made while planning: the Users page loses its "Verification requests" tab entirely (instead of a "Not connected yet" tab), since nothing links to it any more; Analytics and Notifications keep their pages with a "Not connected yet" state.

---

### Task 1: Local PostgreSQL dry-run harness and the failing safety check

**Files:**
- Create: `<scratchpad>/pg/standins.sql`, `<scratchpad>/pg/seed.sql`, `<scratchpad>/pg/run.sh` (scratchpad = the session's scratchpad directory; not committed)
- Create: `supabase/tests/admin_dashboard_live_safety_check.sql`

**Interfaces:**
- Produces: `run.sh` applies every migration in `supabase/migrations/` in name order to a fresh database `salusalo_test` on port 5433, then runs any extra SQL files given as arguments, printing their output.
- Produces: the safety check (Tasks 2–4 make it pass), with the function signatures listed in Task 2 and Task 3.

- [ ] **Step 1: Download a portable PostgreSQL 16**

```bash
cd "$SCRATCH" && mkdir -p pg && cd pg
curl -L -o pg16.zip https://get.enterprisedb.com/postgresql/postgresql-16.4-1-windows-x64-binaries.zip
powershell -NoProfile -Command "Expand-Archive -Path pg16.zip -DestinationPath . -Force"
./pgsql/bin/initdb.exe -U postgres -A trust -E UTF8 -D data
./pgsql/bin/pg_ctl.exe -D data -o "-p 5433" -l pg.log start
```
Expected: `server started`. (`$SCRATCH` = the scratchpad path from the system prompt.)

- [ ] **Step 2: Write the Supabase stand-ins** (`pg/standins.sql`)

```sql
-- Stand-ins for the parts of Supabase the migrations use. Local dry run only.
create role anon nologin;
create role authenticated nologin;
create role service_role nologin;
grant anon, authenticated, service_role to postgres;
create schema auth;
create function auth.jwt() returns jsonb language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), ''), '{}')::jsonb
$$;
grant usage on schema auth to anon, authenticated;
grant execute on function auth.jwt() to anon, authenticated;
create schema storage;
create table storage.buckets (id text primary key, name text, public boolean,
  file_size_limit bigint, allowed_mime_types text[]);
create table storage.objects (id uuid primary key default gen_random_uuid(), bucket_id text,
  name text, owner uuid, created_at timestamptz default now());
alter table storage.objects enable row level security;
create function storage.foldername(name text) returns text[] language sql immutable as $$
  select (string_to_array(name, '/'))[1:array_length(string_to_array(name, '/'), 1) - 1]
$$;
grant usage on schema storage to anon, authenticated;
grant all on storage.objects to authenticated;
grant usage on schema public to anon, authenticated, service_role;
alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
create publication supabase_realtime;
create schema cron;
create table cron.job (jobid serial, jobname text unique, schedule text, command text, active boolean default true);
create function cron.schedule(p_name text, p_schedule text, p_command text) returns bigint language sql as $$
  insert into cron.job(jobname, schedule, command) values (p_name, p_schedule, p_command)
  on conflict (jobname) do update set schedule = excluded.schedule, command = excluded.command
  returning jobid
$$;
```

- [ ] **Step 3: Write the seed** (`pg/seed.sql`): three accounts, as the live project has.

```sql
insert into public.users (firebase_uid, name, email, created_at) values
  ('uid-donor-0001', 'Dana Donor', 'dana@example.com', now() - interval '3 days'),
  ('uid-recip-0002', 'Rico Recipient', 'rico@example.com', now() - interval '2 days'),
  ('uid-admin-0003', 'Ada Admin', 'ada@example.com', now() - interval '1 day');
insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
  expiration_date, status, price, pickup_start, pickup_end)
select gen_random_uuid(), id, 'Seed bread', 'Bread & pastries', 4, 'packs', 'Seed plaza',
  now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours'
from public.users where firebase_uid = 'uid-donor-0001';
```

- [ ] **Step 4: Write the runner** (`pg/run.sh`)

```bash
#!/usr/bin/env bash
# Usage: run.sh [extra.sql ...]  — fresh database, all migrations, then the extras.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="/c/Users/pasto/AndroidStudioProjects/SaluSaloZip/SaluSalo"
PSQL="$HERE/pgsql/bin/psql.exe -h localhost -p 5433 -U postgres -v ON_ERROR_STOP=1 -q"
$PSQL -d postgres -c "drop database if exists salusalo_test" -c "create database salusalo_test"
$PSQL -d postgres -c "drop role if exists anon" -c "drop role if exists authenticated" -c "drop role if exists service_role" 2>/dev/null || true
$PSQL -d salusalo_test -f "$HERE/standins.sql"
for f in "$REPO"/supabase/migrations/*.sql; do
  echo "== $(basename "$f")"
  sed '/create extension if not exists pg_cron/d' "$f" | $PSQL -d salusalo_test -f -
done
$PSQL -d salusalo_test -f "$HERE/seed.sql"
for x in "$@"; do
  echo "== $(basename "$x")"
  $PSQL -d salusalo_test -v ON_ERROR_STOP=0 -f "$x" 2>&1 || true
done
```
Run: `bash "$SCRATCH/pg/run.sh"`.
Expected: one `== <file>` line per migration and no `ERROR`. If a migration fails on a missing Supabase object, add that object to `standins.sql` (same style as above) and rerun; never edit an existing migration.

Roles are cluster-wide, so a second run will find them already there. If `drop role` fails because objects depend on the roles, drop the database first (the script does) and rerun.

- [ ] **Step 5: Write the safety check** (`supabase/tests/admin_dashboard_live_safety_check.sql`)

```sql
-- Salu-Salo admin dashboard: safety check for the live database (PROJECT_STATUS.md §25).
--
-- Run it in the Supabase dashboard after migrations/20261012_admin_dashboard.sql:
-- SQL Editor -> New query -> paste -> Run. Leave the role on "postgres".
--
-- It takes the donor of the newest listing (or the newest account), the newest
-- other account (the "recipient") and, if there is one, a third account (the
-- "admin"; otherwise the recipient is also the admin). Inside the check it sets
-- those accounts' roles itself (the donor never admin). As the donor it posts two
-- test listings, as the recipient it reserves and reports them, and as the admin
-- it uses the admin functions. The "Sanity" checks must succeed: they prove the
-- pretend sign-in works, so the refusals are real. A check that cannot run
-- reports SKIP. The recipient makes 1 reservation and 2 reports.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the check
-- did (roles, listings, reservation, reports, notes, alerts, audit rows), so
-- nothing is saved and no phone gets an alert. The error text is the report; it
-- starts with "SAFETY CHECK FINISHED". Any other error means the check itself did
-- not run.

do $$
declare
  v_donor   public.users;
  v_rec     public.users;
  v_admin   public.users;
  c_donor   text;
  c_rec     text;
  c_admin   text;
  c_ghost   text;
  c_iss     text := 'https://securetoken.google.com/salusalo';
  v_l1      uuid := gen_random_uuid();
  v_l2      uuid := gen_random_uuid();
  v_path    text;
  v_res     public.reservations;
  v_rep1    uuid;
  v_rep2    uuid;
  v_j       jsonb;
  v_qty     integer;
  v_status  text;
  v_reason  text;
  v_bool    boolean;
  v_bool2   boolean;
  n         bigint;
  n2        bigint;
  v_label   text;
  v_ok      boolean;
  v_note    text;
  a_label   text[] := '{}';
  a_ok      boolean[] := '{}';
  a_note    text[] := '{}';
  v_report  text := '';
  v_pass    integer := 0;
  v_fail    integer := 0;
  v_skip    integer := 0;
begin
  select u.* into v_donor from public.users u
  join public.food_posts p on p.donor_id = u.id
  order by p.created_at desc limit 1;
  if v_donor.id is null then
    select * into v_donor from public.users order by created_at desc limit 1;
  end if;
  select * into v_rec from public.users
  where id is distinct from v_donor.id order by created_at desc limit 1;
  select * into v_admin from public.users
  where id is distinct from v_donor.id and id is distinct from v_rec.id order by created_at desc limit 1;
  if v_admin.id is null then
    v_admin := v_rec;
  end if;

  -- Known roles for the check (undone by the final error).
  update public.users set role = 'RECIPIENT' where id in (v_donor.id, v_rec.id, v_admin.id);

  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_rec   := json_build_object('sub', v_rec.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_admin := json_build_object('sub', v_admin.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_ghost := json_build_object('sub', 'safety-check-no-profile', 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  v_path  := v_donor.firebase_uid || '/' || v_l1 || '/image.jpg';

  -- 1
  v_label := 'Sanity: the donor posts two listings; the recipient reserves 2 of the first and reports both';
  v_ok := null; v_note := 'fewer than two accounts';
  if v_donor.id is not null and v_rec.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end, image_url)
      values (v_l1, v_donor.id, 'Safety check: admin 1', 'Food', 10, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours', v_path);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l2, v_donor.id, 'Safety check: admin 2', 'Food', 5, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours');
      perform set_config('request.jwt.claims', c_rec, true);
      select * into v_res from public.create_reservation_with_hold(v_l1, 2, 0, 30);
      perform public.submit_report('LISTING', v_l1, 'UNSAFE_FOOD', null);
      perform public.submit_report('LISTING', v_l2, 'MISLEADING', null);
      perform set_config('role', 'postgres', true);
      select id into v_rep1 from public.reports where food_post_id = v_l1 and status = 'OPEN';
      select id into v_rep2 from public.reports where food_post_id = v_l2 and status = 'OPEN';
      v_ok := v_res.id is not null and v_rep1 is not null and v_rep2 is not null;
      v_note := format('reservation %s, reports %s and %s', v_res.id is not null, v_rep1 is not null, v_rep2 is not null);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2
  v_label := 'A signed-in account that is not an admin is refused (admin_overview)';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_donor, true);
    perform set_config('role', 'authenticated', true);
    perform public.admin_overview();
    perform set_config('role', 'postgres', true);
    v_note := 'it answered';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'Admin access required';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 3
  v_label := 'A token without a profile is refused (admin_whoami)';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_ghost, true);
    perform set_config('role', 'authenticated', true);
    perform public.admin_whoami();
    perform set_config('role', 'postgres', true);
    v_note := 'it answered';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'Admin access required';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 4
  v_label := 'No one signed out (anon) can run an admin function';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', '{}', true);
    perform set_config('role', 'anon', true);
    perform public.admin_overview();
    perform set_config('role', 'postgres', true);
    v_note := 'it answered';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm like 'permission denied for function admin_overview%';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5
  v_label := 'Clients cannot read the audit log or report notes directly';
  v_ok := true; v_note := '';
  for i in 1 .. 2 loop
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      if i = 1 then perform count(*) from public.admin_audit_log; else perform count(*) from public.report_notes; end if;
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || case i when 1 then 'audit log readable; ' else 'notes readable; ' end;
    exception when others then
      perform set_config('role', 'postgres', true);
      if sqlerrm not like 'permission denied%' then v_ok := false; end if;
      v_note := v_note || sqlerrm || '; ';
    end;
  end loop;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 6
  v_label := 'A non-admin cannot remove a listing, and the listing stays';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_donor, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_remove_listing(v_l2, 'Safety check');
      perform set_config('role', 'postgres', true);
      v_note := 'it was removed';
    exception when others then
      perform set_config('role', 'postgres', true);
      select status into v_status from public.food_posts where id = v_l2;
      v_note := sqlerrm || ', status ' || coalesce(v_status, 'missing');
      v_ok := sqlerrm = 'Admin access required' and v_status = 'AVAILABLE';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 7
  v_label := 'Sanity: after role = ADMIN, admin_whoami names the admin';
  v_ok := false;
  begin
    update public.users set role = 'ADMIN' where id = v_admin.id;
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_whoami();
    perform set_config('role', 'postgres', true);
    v_ok := (v_j -> 'me' ->> 'id')::uuid = v_admin.id and v_j -> 'me' ? 'email' and not (v_j -> 'me' ? 'firebase_uid');
    v_note := v_j -> 'me' ->> 'name';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'Overview counts match the tables';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_overview();
    perform set_config('role', 'postgres', true);
    select count(*) into n from public.users;
    select count(*) into n2 from public.reports where status = 'OPEN';
    v_ok := (v_j -> 'counts' ->> 'users')::bigint = n and (v_j -> 'counts' ->> 'pendingReports')::bigint = n2
        and jsonb_typeof(v_j -> 'activity') = 'array' and jsonb_array_length(v_j -> 'rescue' -> 'weeklyItems') = 12;
    v_note := format('users %s/%s, open reports %s/%s', v_j -> 'counts' ->> 'users', n, v_j -> 'counts' ->> 'pendingReports', n2);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9
  v_label := 'admin_listings finds listing 1 with its quantities, reservation and report';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_listings('Safety check: admin 1', 'ALL', 'ANY', null, 1, 10);
      perform set_config('role', 'postgres', true);
      v_ok := (v_j ->> 'total')::int = 1
          and v_j -> 'rows' -> 0 ->> 'status' = 'AVAILABLE'
          and (v_j -> 'rows' -> 0 ->> 'quantity')::int = 8
          and (v_j -> 'rows' -> 0 ->> 'initialQuantity')::int = 10
          and (v_j -> 'rows' -> 0 ->> 'openReservations')::int = 1
          and (v_j -> 'rows' -> 0 ->> 'openReports')::int = 1
          and v_j -> 'rows' -> 0 ->> 'coverPath' = v_path;
      v_note := left(v_j::text, 300);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 10
  v_label := 'Photo rule: the admin may read listing 1''s photo; a token without a profile may not';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_bool := public.can_read_food_image(v_path);
    perform set_config('request.jwt.claims', c_ghost, true);
    v_bool2 := public.can_read_food_image(v_path);
    perform set_config('role', 'postgres', true);
    v_ok := v_bool and not v_bool2;
    v_note := format('admin %s, no profile %s', v_bool, v_bool2);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 11
  v_label := 'The admin adds a note to report 1; the report shows it with the admin''s name';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_add_report_note(v_rep1, 'Safety check note');
      v_j := public.admin_report(v_rep1);
      perform set_config('role', 'postgres', true);
      v_ok := jsonb_array_length(v_j -> 'notes') = 1 and v_j -> 'notes' -> 0 ->> 'by' = v_admin.name
          and v_j -> 'notes' -> 0 ->> 'text' = 'Safety check note' and v_j -> 'reporterDetail' ->> 'id' = v_rec.id::text;
      v_note := left((v_j -> 'notes')::text, 200);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 12
  v_label := 'Removing without a reason is refused';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_remove_listing(v_l1, '   ');
      perform set_config('role', 'postgres', true);
      v_note := 'it was removed';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
      v_ok := sqlerrm = 'Give a reason (up to 500 characters).';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 13
  v_label := 'The admin removes listing 1: REMOVED, quantity kept, reservation cancelled, report resolved';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_remove_listing(v_l1, 'Safety check removal');
      perform set_config('role', 'postgres', true);
      select status, quantity into v_status, v_qty from public.food_posts where id = v_l1;
      select r.status || '/' || coalesce(r.cancel_reason, '-') into v_reason from public.reservations r where r.id = v_res.id;
      select count(*) into n from public.reports
      where id = v_rep1 and status = 'RESOLVED' and resolution_action = 'LISTING_REMOVED'
        and resolved_by = v_admin.id and resolution_note = 'Safety check removal';
      v_ok := (v_j ->> 'cancelled')::int = 1 and (v_j ->> 'closedReports')::int = 1
          and v_status = 'REMOVED' and v_qty = 8 and v_reason = 'CANCELLED/LISTING_REMOVED' and n = 1;
      v_note := format('%s; listing %s qty %s; reservation %s; report resolved %s', v_j, v_status, v_qty, v_reason, n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 14
  v_label := 'One "Listing removed" alert for the recipient and one "Your listing was removed" for the donor';
  v_ok := null; v_note := 'check 13 did not pass';
  if a_ok[13] then
    select coalesce(bool_and(reservation_alerts), true) into v_bool from public.notification_preferences
    where user_id in (v_donor.id, v_rec.id);
    if not v_bool then
      v_note := 'one of the two accounts has Reservation updates turned off';
    else
      select count(*) into n from public.notifications
      where user_id = v_rec.id and food_post_id = v_l1 and type = 'RESERVATION_REMOVED' and title = 'Listing removed'
        and reservation_id = v_res.id;
      select count(*) into n2 from public.notifications
      where user_id = v_donor.id and food_post_id = v_l1 and type = 'RESERVATION_REMOVED' and title = 'Your listing was removed'
        and body = 'Salu-Salo removed Safety check: admin 1: Safety check removal';
      v_ok := n = 1 and n2 = 1;
      v_note := format('recipient %s, donor %s', n, n2);
    end if;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 15
  v_label := 'Removing it again is refused and sends no second alert';
  v_ok := null; v_note := 'check 13 did not pass';
  if a_ok[13] then
    v_ok := false;
    select count(*) into n from public.notifications where food_post_id = v_l1;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_remove_listing(v_l1, 'Again');
      perform set_config('role', 'postgres', true);
      v_note := 'it was removed again';
    exception when others then
      perform set_config('role', 'postgres', true);
      select count(*) into n2 from public.notifications where food_post_id = v_l1;
      v_note := sqlerrm || format(', alerts %s -> %s', n, n2);
      v_ok := sqlerrm = 'This listing was already removed.' and n = n2;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 16
  v_label := 'Dismissing listing 2''s reports closes report 2; dismissing it again is refused';
  v_ok := null; v_note := 'check 1 did not pass';
  if a_ok[1] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_dismiss_listing_reports(v_l2, null);
      perform set_config('role', 'postgres', true);
      select status || '/' || resolution_note into v_reason from public.reports where id = v_rep2;
      v_note := format('%s, %s', v_j, v_reason);
      begin
        perform set_config('request.jwt.claims', c_admin, true);
        perform set_config('role', 'authenticated', true);
        perform public.admin_dismiss_report(v_rep2, null);
        perform set_config('role', 'postgres', true);
        v_note := v_note || ', dismissed twice';
      exception when others then
        perform set_config('role', 'postgres', true);
        v_ok := (v_j ->> 'dismissed')::int = 1 and v_reason = 'DISMISSED/No problem found.'
            and sqlerrm = 'This report is already closed.';
        v_note := v_note || ', ' || sqlerrm;
      end;
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 17
  v_label := 'Listing details: reviewed, removed by the admin with the reason, photo path listed';
  v_ok := null; v_note := 'check 13 did not pass';
  if a_ok[13] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_mark_listing_reviewed(v_l2, null);
      v_bool := (public.admin_listing(v_l2) ->> 'reviewedAt') is not null;
      v_j := public.admin_listing(v_l1);
      perform set_config('role', 'postgres', true);
      v_ok := v_bool and v_j ->> 'removedByName' = v_admin.name and v_j ->> 'removedReason' = 'Safety check removal'
          and v_j -> 'photoPaths' = jsonb_build_array(v_path) and v_j ->> 'status' = 'REMOVED'
          and not (v_j -> 'donor' ? 'firebase_uid');
      v_note := format('reviewed %s, removed by %s, reason %s', v_bool, v_j ->> 'removedByName', v_j ->> 'removedReason');
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 18
  v_label := 'The audit log has the four admin actions, newest first';
  v_ok := null; v_note := 'check 17 did not pass';
  if a_ok[17] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_audit(null, v_admin.id, 'ALL', 'TODAY', 1, 50);
      perform set_config('role', 'postgres', true);
      select count(distinct e ->> 'action') into n
      from jsonb_array_elements(v_j -> 'rows') e
      where (e ->> 'targetId')::uuid in (v_l1, v_l2, v_rep1);
      v_ok := n = 4 and (v_j -> 'rows' -> 0 ->> 'at')::bigint >= (v_j -> 'rows' -> 1 ->> 'at')::bigint;
      v_note := format('%s distinct actions', n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 19
  v_label := 'Reservation, user and resolved-report pages read the new state';
  v_ok := null; v_note := 'check 13 did not pass';
  if a_ok[13] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_reservation(v_res.id);
      v_bool := v_j ->> 'cancelReason' = 'LISTING_REMOVED' and v_j -> 'recipient' ->> 'id' = v_rec.id::text;
      v_j := public.admin_user(v_donor.id);
      v_bool := v_bool and (v_j -> 'stats' ->> 'listings')::int >= 2 and v_j ->> 'role' = 'DONOR';
      v_j := public.admin_reports('Safety check: admin 1', 'RESOLVED', 'ALL', 'ALL', 1, 10);
      perform set_config('role', 'postgres', true);
      v_ok := v_bool and (v_j ->> 'total')::int = 1 and v_j -> 'rows' -> 0 -> 'resolution' ->> 'action' = 'LISTING_REMOVED';
      v_note := left(v_j::text, 200);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 20
  v_label := 'Paging: one user per page; a page past the end gives the last page';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_users(null, 'ALL', 9999, 1);
    perform set_config('role', 'postgres', true);
    select count(*) into n from public.users;
    v_ok := (v_j ->> 'pageSize')::int = 1 and jsonb_array_length(v_j -> 'rows') = 1
        and (v_j ->> 'pages')::bigint = n and (v_j ->> 'page')::bigint = n;
    v_note := format('page %s of %s, %s users', v_j ->> 'page', v_j ->> 'pages', n);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- Report. Raising an error here undoes everything above.
  for i in 1 .. coalesce(array_length(a_label, 1), 0) loop
    if a_ok[i] is null then
      v_skip := v_skip + 1;
    elsif a_ok[i] then
      v_pass := v_pass + 1;
    else
      v_fail := v_fail + 1;
    end if;
    v_report := v_report || format(E'\n%s %s. %s: %s',
      case when a_ok[i] is null then 'SKIP' when a_ok[i] then 'PASS' else 'FAIL' end,
      i, a_label[i], coalesce(a_note[i], '-'));
  end loop;

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Recipient: %s. Admin: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_rec.name, 'none'), coalesce(v_admin.name, 'none'), v_report);
end $$;
```

- [ ] **Step 6: Run it to see it fail**

Run: `bash "$SCRATCH/pg/run.sh" supabase/tests/admin_dashboard_live_safety_check.sql`
Expected: the migrations apply cleanly, then `ERROR:  SAFETY CHECK FINISHED: 1 passed, …` with check 1 PASS and most others FAIL, their notes saying `function public.admin_… does not exist`.

- [ ] **Step 7: Commit**

```bash
git add supabase/tests/admin_dashboard_live_safety_check.sql
git commit -m "Add the admin dashboard safety check (fails until the migration exists)"
```

---

### Task 2: Migration part 1: admin check, tables, helpers, photo rule

**Files:**
- Create: `supabase/migrations/20261012_admin_dashboard.sql` (sections 1–4; Task 3 appends section 5, Task 4 section 6 and the privileges)
- Create: `supabase/tests/admin_dashboard_check.sql`

**Interfaces:**
- Produces (internal, used by Tasks 3–4): `admin_require() returns public.users`; `admin_ms(timestamptz) → bigint`; `admin_like(text) → text`; `admin_since(text) → timestamptz`; `admin_listing_status(public.food_posts) → text`; `admin_qty(integer, text) → text`; `admin_reason_label(text, text) → text`; `admin_who(uuid) → jsonb {id,name,org}`; `admin_user_json(public.users) → jsonb`; `admin_listing_json(public.food_posts) → jsonb`; `admin_reservation_json(public.reservations) → jsonb`; `admin_report_json(public.reports) → jsonb`; `admin_paging(integer, integer, bigint) → (page, size, pages, skip)`; `admin_log(public.users, text, text, uuid, text, text)`.
- Produces (client): `is_salusalo_admin() → boolean`.

- [ ] **Step 1: Write sections 1–4 of the migration**

```sql
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
```

Before saving, diff the `can_read_food_image` body against `supabase/migrations/20261005_bugfix_security_integrity.sql` lines 144–192: the only difference must be the added `or public.is_salusalo_admin()` line.

- [ ] **Step 2: Add a temporary privileges block at the end** (Task 4 replaces it with the final one)

```sql
-- ---------------------------------------------------------------------------
-- 7. Privileges
-- ---------------------------------------------------------------------------
revoke all on function public.is_salusalo_admin() from public;
revoke all on function public.is_salusalo_admin() from anon;
grant execute on function public.is_salusalo_admin() to authenticated;
```

- [ ] **Step 3: Write the read-only check** (`supabase/tests/admin_dashboard_check.sql`)

```sql
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
```

- [ ] **Step 4: Run the migration and the read-only check locally**

Run: `bash "$SCRATCH/pg/run.sh" supabase/tests/admin_dashboard_check.sql`
Expected: no `ERROR` while applying `20261012_admin_dashboard.sql`; the check shows rows 3, 4, 5, 6 `t`, rows 1, 2 `f` (functions from Tasks 3–4 missing), row 7 `f`.

- [ ] **Step 5: Run the migration twice** (it must be safe to run again)

Run: `"$SCRATCH/pg/pgsql/bin/psql.exe" -h localhost -p 5433 -U postgres -d salusalo_test -v ON_ERROR_STOP=1 -q -f supabase/migrations/20261012_admin_dashboard.sql`
Expected: no output, exit code 0.

- [ ] **Step 6: Commit**

```bash
git add supabase/migrations/20261012_admin_dashboard.sql supabase/tests/admin_dashboard_check.sql
git commit -m "Add the admin check, report statuses, notes, audit log and admin photo access"
```

---

### Task 3: Migration part 2: admin read functions

**Files:**
- Modify: `supabase/migrations/20261012_admin_dashboard.sql` (insert section 5 before section 7)

**Interfaces:**
- Consumes: Task 2's helpers.
- Produces (client, all `returns jsonb`; paged ones return `{rows, total, page, pageSize, pages}`):
  `admin_whoami()` → `{me:{id,name,email}, admins:[{id,name}]}`;
  `admin_counts()` → `{openReports}`;
  `admin_overview()` → `{counts, activity, verification:[], reports, reservations, rescue}`;
  `admin_users(p_q text, p_filter text, p_page int, p_page_size int)` (filter `ALL|ADMIN|DONOR|RECIPIENT`);
  `admin_user(p_id uuid)` → user json + `stats`, `openReports`, `events`, or `null`;
  `admin_listings(p_q text, p_status text, p_posted text, p_donor uuid, p_page int, p_page_size int)` (+ `donorName`);
  `admin_listing(p_id uuid)` → listing json + details, `photoPaths`, `reservations`, `reports`, review and removal info, or `null`;
  `admin_reservations(p_q text, p_status text, p_date text, p_page int, p_page_size int)`;
  `admin_reservation(p_id uuid)` → reservation json + pickup fields and `listingStatus`, or `null`;
  `admin_reports(p_q text, p_status text, p_target text, p_reason text, p_page int, p_page_size int)`;
  `admin_report(p_id uuid)` → report json + `details`, `listingName`, `notes`, `reporterDetail`, `targetDetail`, `otherReports`, or `null`;
  `admin_audit(p_q text, p_admin uuid, p_action text, p_range text, p_page int, p_page_size int)`.

- [ ] **Step 1: Insert section 5 (above the "7. Privileges" header)**

```sql
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
```

- [ ] **Step 2: Write local checks L1–L4** (`$SCRATCH/pg/reads_check.sql`; local only)

```sql
-- L1 the reads answer for the seeded admin and refuse the donor.
update public.users set role = 'ADMIN' where firebase_uid = 'uid-admin-0003';
set role authenticated;
select set_config('request.jwt.claims', '{"sub":"uid-admin-0003","role":"authenticated","iss":"https://securetoken.google.com/salusalo","aud":"salusalo"}', false);
select 'L1 overview users = 3' as check, (public.admin_overview() -> 'counts' ->> 'users')::int = 3 as ok;
-- L2 listing filters
select 'L2 seed listing is ACTIVE', (public.admin_listings(null, 'ACTIVE', 'ANY', null, 1, 10) ->> 'total')::int = 1;
-- L3 wildcards are literal: "%" matches nothing, "Seed_bread" does not match "Seed bread"
select 'L3 % is literal', (public.admin_listings('%', 'ALL', 'ANY', null, 1, 10) ->> 'total')::int = 0;
select 'L3 _ is literal', (public.admin_listings('Seed_bread', 'ALL', 'ANY', null, 1, 10) ->> 'total')::int = 0;
-- L4 paging clamps to the last page
select 'L4 page 50 of 3 users gives page 3', (public.admin_users(null, 'ALL', 50, 1) ->> 'page')::int = 3;
select 'L4 page size capped at 50', (public.admin_users(null, 'ALL', 1, 500) ->> 'pageSize')::int = 50;
select set_config('request.jwt.claims', '{"sub":"uid-donor-0001","role":"authenticated","iss":"https://securetoken.google.com/salusalo","aud":"salusalo"}', false);
select 'L1 donor refused (expect ERROR: Admin access required next)' as check;
select public.admin_users(null, 'ALL', 1, 10);
reset role;
```

- [ ] **Step 3: Add the read functions to the temporary privileges block** (so the safety check can call them; Task 4 makes it final)

```sql
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
```

- [ ] **Step 4: Run the checks**

Run: `bash "$SCRATCH/pg/run.sh" "$SCRATCH/pg/reads_check.sql" supabase/tests/admin_dashboard_live_safety_check.sql`
Expected: every L row `t`, then `ERROR:  Admin access required`; the safety check now passes 1–5, 7–11, 20 and fails 6 and 12–19 (write functions missing).

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations/20261012_admin_dashboard.sql
git commit -m "Add the admin read functions"
```

---

### Task 4: Migration part 3: admin actions and final privileges

**Files:**
- Modify: `supabase/migrations/20261012_admin_dashboard.sql` (insert section 6; replace section 7)

**Interfaces:**
- Produces (client, `returns jsonb`): `admin_remove_listing(p_post uuid, p_reason text)` → `{cancelled, closedReports}`; `admin_dismiss_listing_reports(p_post uuid, p_note text)` → `{dismissed}`; `admin_dismiss_report(p_report uuid, p_note text)` → `{dismissed: 1}`; `admin_add_report_note(p_report uuid, p_note text)` → `{id}`; `admin_mark_listing_reviewed(p_post uuid, p_note text)` → `{reviewed: true}`.

- [ ] **Step 1: Insert section 6 (above "7. Privileges")**

```sql
-- ---------------------------------------------------------------------------
-- 6. Admin actions. Each writes its audit row in the same transaction.
-- ---------------------------------------------------------------------------

-- Like remove_my_food_post, but never deletes: the listing stays as REMOVED with
-- its quantity, its open reservations are cancelled (LISTING_REMOVED, which the
-- reservations trigger does not alert about), each recipient gets one alert and
-- the donor one, saved rows go, and its open reports are resolved.
create or replace function public.admin_remove_listing(p_post uuid, p_reason text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_reason text := nullif(btrim(coalesce(p_reason, '')), '');
    v_post public.food_posts;
    v_cancelled integer := 0;
    v_closed integer := 0;
    v_recipient uuid;
    v_count integer;
    v_reservation uuid;
begin
    if v_reason is null or char_length(v_reason) > 500 then
        raise exception 'Give a reason (up to 500 characters).';
    end if;

    -- Lock the open reservations, then the listing (the order remove_my_food_post uses).
    perform 1 from public.reservations r
    where r.food_post_id = p_post and r.status = 'RESERVED'
    order by r.id
    for update;
    select * into v_post from public.food_posts p where p.id = p_post for update;
    if not found then
        raise exception 'Listing not found';
    end if;
    if v_post.status = 'REMOVED' then
        raise exception 'This listing was already removed.';
    end if;

    update public.food_posts p set status = 'REMOVED' where p.id = p_post;

    update public.reservations r
    set status = 'CANCELLED', cancel_reason = 'LISTING_REMOVED'
    where r.food_post_id = p_post and r.status = 'RESERVED';
    get diagnostics v_cancelled = row_count;

    -- The listing was not REMOVED before this call, so every LISTING_REMOVED
    -- reservation on it was cancelled just now.
    for v_recipient, v_count, v_reservation in
        select r.recipient_id, count(*)::integer, (array_agg(r.id order by r.created_at desc))[1]
        from public.reservations r
        where r.food_post_id = p_post and r.cancel_reason = 'LISTING_REMOVED'
        group by r.recipient_id
    loop
        perform public.notify_user(v_recipient, 'RESERVATION_REMOVED', 'Listing removed',
            v_post.name || ' was removed by Salu-Salo, so your '
            || case when v_count = 1 then 'reservation was' else v_count || ' reservations were' end
            || ' cancelled.',
            v_post.id, null, v_reservation);
    end loop;

    perform public.notify_user(v_post.donor_id, 'RESERVATION_REMOVED', 'Your listing was removed',
        'Salu-Salo removed ' || v_post.name || ': ' || v_reason, v_post.id, null, null);

    delete from public.saved_listings s where s.food_post_id = p_post;

    update public.reports rp
    set status = 'RESOLVED', resolved_by = v_admin.id, resolved_at = now(),
        resolution_action = 'LISTING_REMOVED', resolution_note = v_reason
    where rp.target_type = 'LISTING' and rp.food_post_id = p_post and rp.status = 'OPEN';
    get diagnostics v_closed = row_count;

    perform public.admin_log(v_admin, 'LISTING_REMOVED', 'LISTING', v_post.id, v_post.name, v_reason);
    return jsonb_build_object('cancelled', v_cancelled, 'closedReports', v_closed);
end; $$;

create or replace function public.admin_dismiss_listing_reports(p_post uuid, p_note text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_note text := coalesce(nullif(btrim(coalesce(p_note, '')), ''), 'No problem found.');
    v_post public.food_posts;
    v_n integer;
begin
    if char_length(v_note) > 1000 then
        raise exception 'Keep the note under 1,000 characters.';
    end if;
    select * into v_post from public.food_posts p where p.id = p_post;
    if not found then
        raise exception 'Listing not found';
    end if;
    update public.reports rp
    set status = 'DISMISSED', resolved_by = v_admin.id, resolved_at = now(),
        resolution_action = 'DISMISSED', resolution_note = v_note
    where rp.target_type = 'LISTING' and rp.food_post_id = p_post and rp.status = 'OPEN';
    get diagnostics v_n = row_count;
    if v_n > 0 then
        perform public.admin_log(v_admin, 'REPORTS_DISMISSED', 'LISTING', v_post.id, v_post.name, v_note);
    end if;
    return jsonb_build_object('dismissed', v_n);
end; $$;

create or replace function public.admin_dismiss_report(p_report uuid, p_note text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_note text := coalesce(nullif(btrim(coalesce(p_note, '')), ''), 'No problem found.');
    v_report public.reports;
begin
    if char_length(v_note) > 1000 then
        raise exception 'Keep the note under 1,000 characters.';
    end if;
    select * into v_report from public.reports rp where rp.id = p_report for update;
    if not found then
        raise exception 'Report not found';
    end if;
    if v_report.status <> 'OPEN' then
        raise exception 'This report is already closed.';
    end if;
    update public.reports rp
    set status = 'DISMISSED', resolved_by = v_admin.id, resolved_at = now(),
        resolution_action = 'DISMISSED', resolution_note = v_note
    where rp.id = p_report;
    perform public.admin_log(v_admin, 'REPORTS_DISMISSED', 'REPORT', v_report.id,
        '#' || upper(left(v_report.id::text, 8)), v_note);
    return jsonb_build_object('dismissed', 1);
end; $$;

create or replace function public.admin_add_report_note(p_report uuid, p_note text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_note text := nullif(btrim(coalesce(p_note, '')), '');
    v_id uuid;
begin
    if v_note is null or char_length(v_note) > 1000 then
        raise exception 'Write a note (up to 1,000 characters).';
    end if;
    if not exists (select 1 from public.reports rp where rp.id = p_report) then
        raise exception 'Report not found';
    end if;
    insert into public.report_notes (report_id, admin_id, note)
    values (p_report, v_admin.id, v_note)
    returning id into v_id;
    perform public.admin_log(v_admin, 'REPORT_NOTE_ADDED', 'REPORT', p_report,
        '#' || upper(left(p_report::text, 8)), left(v_note, 200));
    return jsonb_build_object('id', v_id);
end; $$;

create or replace function public.admin_mark_listing_reviewed(p_post uuid, p_note text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_note text := coalesce(nullif(btrim(coalesce(p_note, '')), ''), 'Checked; no change needed.');
    v_post public.food_posts;
begin
    if char_length(v_note) > 1000 then
        raise exception 'Keep the note under 1,000 characters.';
    end if;
    select * into v_post from public.food_posts p where p.id = p_post;
    if not found then
        raise exception 'Listing not found';
    end if;
    perform public.admin_log(v_admin, 'LISTING_REVIEWED', 'LISTING', v_post.id, v_post.name, v_note);
    return jsonb_build_object('reviewed', true);
end; $$;
```

- [ ] **Step 2: Replace the whole "7. Privileges" section with the final one**

```sql
-- ---------------------------------------------------------------------------
-- 7. Privileges: client functions for authenticated only; helpers for no client.
-- ---------------------------------------------------------------------------
do $$
declare f text;
begin
    foreach f in array array[
        'is_salusalo_admin()', 'admin_whoami()', 'admin_counts()', 'admin_overview()',
        'admin_users(text,text,integer,integer)', 'admin_user(uuid)',
        'admin_listings(text,text,text,uuid,integer,integer)', 'admin_listing(uuid)',
        'admin_reservations(text,text,text,integer,integer)', 'admin_reservation(uuid)',
        'admin_reports(text,text,text,text,integer,integer)', 'admin_report(uuid)',
        'admin_audit(text,uuid,text,text,integer,integer)',
        'admin_remove_listing(uuid,text)', 'admin_dismiss_listing_reports(uuid,text)',
        'admin_dismiss_report(uuid,text)', 'admin_add_report_note(uuid,text)',
        'admin_mark_listing_reviewed(uuid,text)']
    loop
        execute format('revoke all on function public.%s from public', f);
        execute format('revoke all on function public.%s from anon', f);
        execute format('grant execute on function public.%s to authenticated', f);
    end loop;
    foreach f in array array[
        'admin_require()', 'admin_ms(timestamptz)', 'admin_like(text)', 'admin_since(text)',
        'admin_listing_status(public.food_posts)', 'admin_qty(integer,text)', 'admin_reason_label(text,text)',
        'admin_who(uuid)', 'admin_user_json(public.users)', 'admin_listing_json(public.food_posts)',
        'admin_reservation_json(public.reservations)', 'admin_report_json(public.reports)',
        'admin_paging(integer,integer,bigint)', 'admin_log(public.users,text,text,uuid,text,text)',
        'admin_user_matches(public.users,text,text)',
        'admin_listing_matches(public.food_posts,text,text,timestamptz,uuid)',
        'admin_reservation_matches(public.reservations,text,text,timestamptz)',
        'admin_report_matches(public.reports,text,text,text,text)',
        'admin_audit_matches(public.admin_audit_log,text,uuid,text,timestamptz)']
    loop
        execute format('revoke all on function public.%s from public', f);
        execute format('revoke all on function public.%s from anon', f);
        execute format('revoke all on function public.%s from authenticated', f);
    end loop;
end $$;
```

- [ ] **Step 3: Run everything**

Run: `bash "$SCRATCH/pg/run.sh" supabase/tests/admin_dashboard_check.sql supabase/tests/admin_dashboard_live_safety_check.sql supabase/tests/bugfix_anon_grants_check.sql`
Expected: check rows 1–6 `t`, row 7 `f` (no admin in the seed); `SAFETY CHECK FINISHED: 20 passed, 0 failed, 0 skipped.`; the anon-grants check passes as before.

- [ ] **Step 4: Run the earlier safety checks** (the migration must not break them)

Run: `bash "$SCRATCH/pg/run.sh" supabase/tests/phase3_live_safety_check.sql supabase/tests/bugfix_security_live_safety_check.sql supabase/tests/reservation_limits_live_safety_check.sql supabase/tests/phase5_maps_live_safety_check.sql`
Expected: each `SAFETY CHECK FINISHED` with 0 failed (SKIPs for data the seed lacks are fine; compare with a run of the same command on `master`'s migrations if a FAIL appears, to tell new failures from seed gaps).

- [ ] **Step 5: Run the migration a second time on the same database**

Run: the psql command from Task 2 Step 5. Expected: exit code 0.

- [ ] **Step 6: Commit**

```bash
git add supabase/migrations/20261012_admin_dashboard.sql
git commit -m "Add admin listing removal, report dismissal, notes and reviews"
```

---

### Task 5: Timeline helper with its Node check

**Files:**
- Create: `admin/js/timeline.js`
- Test: `admin/tests/timeline.test.js`

**Interfaces:**
- Produces: `window.SaluTimeline.build(reservation, now?) → [{title, at, meta, state, icon}]` (in Node: `module.exports.build`). Reservation fields used: `status`, `cancelReason`, `quantity`, `listing.unit`, `recipient.name`, `donor.name`, `createdAt`, `updatedAt`, `pickupDeadline`, `pickupEnd`.

- [ ] **Step 1: Write the failing test** (`admin/tests/timeline.test.js`)

```js
// Run: node admin/tests/timeline.test.js
const assert = require('node:assert');
const T = require('../js/timeline.js');

const now = Date.UTC(2026, 9, 3, 6, 0);
const base = { quantity: 2, listing: { unit: 'packs' }, recipient: { name: 'Ana' }, donor: { name: 'Ben' }, createdAt: now - 7200e3, updatedAt: now - 60e3, pickupDeadline: null, pickupEnd: null };

let s = T.build(Object.assign({}, base, { status: 'RESERVED', pickupDeadline: now + 3600e3 }), now);
assert.strictEqual(s.length, 3);
assert.strictEqual(s[0].meta, 'Ana reserved 2 packs.');
assert.strictEqual(s[1].state, 'current');
assert.match(s[1].meta, /Pick up by /);
assert.strictEqual(s[2].state, 'pending');

s = T.build(Object.assign({}, base, { status: 'RESERVED', pickupEnd: now - 1 }), now);
assert.match(s[1].meta, /The pickup time has passed\./);

for (const [reason, title, re] of [
  ['LISTING_REMOVED', 'Cancelled: listing removed', /listing was removed/],
  ['DONOR_RELEASED', 'Released by the donor', /Released by Ben/],
  ['PICKUP_EXPIRED', 'Expired', /Not picked up in time/],
  [null, 'Cancelled', /Cancelled by Ana in the app/]
]) {
  s = T.build(Object.assign({}, base, { status: 'CANCELLED', cancelReason: reason }), now);
  assert.strictEqual(s.length, 2);
  assert.strictEqual(s[1].state, 'bad');
  assert.strictEqual(s[1].title, title);
  assert.match(s[1].meta, re);
  assert.strictEqual(s[1].at, base.updatedAt);
}

s = T.build(Object.assign({}, base, { quantity: 1, status: 'CLAIMED' }), now);
assert.strictEqual(s[0].meta, 'Ana reserved 1 pack.');
assert.strictEqual(s[1].title, 'Claimed');
assert.strictEqual(s[1].meta, 'Marked as picked up by Ben.');

console.log('timeline: all checks passed');
```

- [ ] **Step 2: Run it to see it fail**

Run: `node admin/tests/timeline.test.js`
Expected: `Error: Cannot find module '../js/timeline.js'`.

- [ ] **Step 3: Write `admin/js/timeline.js`**

```js
/* A reservation's steps, from what the database stores. Pure; tested by admin/tests/timeline.test.js. */
(function (root) {
  'use strict';

  const SINGULAR = { pcs: 'pc', packs: 'pack', meals: 'meal', boxes: 'box' };
  const qty = (n, unit) => n + ' ' + (n === 1 ? SINGULAR[unit] || unit : unit);
  const when = (t) => new Date(t).toLocaleString('en-US', { weekday: 'short', hour: 'numeric', minute: '2-digit' });

  const CANCEL_TITLE = { LISTING_REMOVED: 'Cancelled: listing removed', DONOR_RELEASED: 'Released by the donor', PICKUP_EXPIRED: 'Expired' };
  function cancelMeta(r) {
    if (r.cancelReason === 'LISTING_REMOVED') return 'Cancelled because the listing was removed; the recipient was alerted.';
    if (r.cancelReason === 'DONOR_RELEASED') return 'Released by ' + r.donor.name + '; the quantity went back to the listing.';
    if (r.cancelReason === 'PICKUP_EXPIRED') return 'Not picked up in time, so it expired by itself; the quantity went back to the listing.';
    return 'Cancelled by ' + r.recipient.name + ' in the app; the quantity went back to the listing.';
  }

  function build(r, now) {
    const t = now || Date.now();
    const steps = [{ title: 'Reservation created', at: r.createdAt, meta: r.recipient.name + ' reserved ' + qty(r.quantity, r.listing.unit) + '.', state: 'done', icon: 'bookmark' }];
    if (r.status === 'RESERVED') {
      const until = r.pickupDeadline || r.pickupEnd;
      const due = until ? (until > t ? ' Pick up by ' + when(until) + '.' : ' The pickup time has passed.') : '';
      steps.push({ title: 'Waiting for pickup', at: null, meta: r.donor.name + ' marks it picked up at the handover.' + due, state: 'current', icon: 'hourglassBottom' });
      steps.push({ title: 'Claimed', at: null, meta: 'Not yet.', state: 'pending', icon: 'taskAlt' });
    } else if (r.status === 'CLAIMED') {
      steps.push({ title: 'Claimed', at: r.updatedAt, meta: 'Marked as picked up by ' + r.donor.name + '.', state: 'done', icon: 'taskAlt' });
    } else {
      steps.push({ title: CANCEL_TITLE[r.cancelReason] || 'Cancelled', at: r.updatedAt, meta: cancelMeta(r), state: 'bad', icon: 'highlightOff' });
    }
    return steps;
  }

  const api = { build };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.SaluTimeline = api;
})(this);
```

- [ ] **Step 4: Run the test**

Run: `node admin/tests/timeline.test.js && node --check admin/js/timeline.js`
Expected: `timeline: all checks passed`.

- [ ] **Step 5: Commit**

```bash
git add admin/js/timeline.js admin/tests/timeline.test.js
git commit -m "Add the reservation timeline helper for the admin dashboard"
```

---

### Task 6: `data.js` on the admin functions; sign-in; shell

**Files:**
- Rewrite: `admin/js/data.js`
- Create: `admin/js/auth.js`, `admin/js/config.example.js`, `admin/js/config.js` (git-ignored; filled by the developer in Task 9)
- Modify: `admin/index.html`, `admin/js/app.js`, `admin/js/ui.js`, `admin/css/admin.css`, `.gitignore`
- Delete: `admin/js/sample-data.js`

**Interfaces:**
- Consumes: Task 3/4 RPC names and argument names (`p_*`), Task 5 `SaluTimeline.build`.
- Produces: `window.SaluData` = `{ text, DataError, AccessError, subscribe, start(client, onLost), counts(), me(), overview(), users(q), user(id), listings(q), listing(id), removeListing(id, reason), dismissListingReports(id, note), markListingReviewed(id, note), reservations(q), reservation(id), reports(q), report(id), dismissReport(id, note), addReportNote(id, note), audit(q), auditFilters() }`; `window.SaluAuth.signOut()`; `window.App.start()`; `UI.photo(url, category, big?, label?)`; `UI.fmt.ref(id) → '#ABCD1234'`. Rows carry `photoUrl` (lists), `listing.photoUrl` (reservations), `photoUrls` (listing details).

- [ ] **Step 1: Pin and check the CDN files**

Run:
```bash
for u in https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js https://www.gstatic.com/firebasejs/10.14.1/firebase-auth-compat.js https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.49.4/dist/umd/supabase.js; do
  printf '%s ' "$u"; curl -sL "$u" -o /tmp/f.js -w '%{http_code} ' && echo "sha384-$(openssl dgst -sha384 -binary /tmp/f.js | openssl base64 -A)"; done
grep -c "accessToken" /tmp/f.js
```
Expected: three `200` lines with a hash each, and a count ≥ 1 (supabase-js 2.49.4 supports the `accessToken` option). Use these hashes as `integrity` values in Step 5. If supabase-js returns 404, pick the newest 2.x from `npm view @supabase/supabase-js version` and repeat.

- [ ] **Step 2: Rewrite `admin/js/data.js`**

```js
/*
 * The dashboard's only way to its data: admin-only Supabase functions
 * (supabase/migrations/20261012_admin_dashboard.sql). Each one refuses anyone who
 * isn't an admin; this page holds only the publishable key, so it can't get around
 * that. Times are epoch milliseconds. Photos are signed here for 1 hour and never logged.
 */
window.SaluData = (function () {
  'use strict';

  /* Labels shared with the app (model/Review.kt, model/FoodPost.kt, PostScreen.kt). */
  const text = {
    reasons: {
      LISTING: { UNSAFE_FOOD: 'Unsafe or spoiled food', MISLEADING: 'Wrong or misleading details', SPAM_FAKE: 'Spam or fake listing', OTHER: 'Other' },
      USER: { ABUSIVE: 'Rude or abusive', NO_SHOW: 'Didn’t show up', SCAM: 'Scam or fraud', SPAM: 'Spam', OTHER: 'Other' }
    },
    sources: { LISTING: 'From the listing page', DONOR_PAGE: 'From the donor’s page', CHAT: 'From a chat' },
    handover: { MEET_AT_PICKUP_LOCATION: 'Meet at pickup location', COLLECT_FROM_PROVIDER: 'Collect from provider' },
    roles: { DONOR: 'Donor', RECIPIENT: 'Recipient', ADMIN: 'Admin' },
    actions: { LISTING_REMOVED: 'Removed listing', REPORTS_DISMISSED: 'Dismissed report', LISTING_REVIEWED: 'Reviewed listing', REPORT_NOTE_ADDED: 'Added note' }
  };

  class DataError extends Error {}
  class AccessError extends DataError {}

  let sb = null;
  let me = { id: null, name: '', email: '' };
  let admins = [];
  let openReports = 0;
  let onLost = () => {};
  const listeners = new Set();
  const emit = () => listeners.forEach((fn) => { try { fn(); } catch (e) { console.error(e); } });

  async function rpc(name, args) {
    const { data, error } = await sb.rpc(name, args || {});
    if (error) {
      // 42501/28000: not (or no longer) an admin; PGRST30x: the token was refused.
      if (error.code === '42501' || error.code === '28000' || /^PGRST30/.test(error.code || '')) {
        const e = new AccessError('Admin access required');
        onLost(e);
        throw e;
      }
      throw new DataError(error.message || 'Request failed'); // our own short texts; never details or hints
    }
    return data;
  }

  const labelReport = (r) => { if (r) r.reasonLabel = (text.reasons[r.targetType] || {})[r.reason] || r.reason; return r; };
  const markYou = (p) => { if (p && p.id === me.id) p.isYou = true; return p; };
  const paging = (q) => ({ p_page: q.page || 1, p_page_size: q.pageSize || 10 });

  async function signPaths(paths) {
    const list = Array.from(new Set(paths.filter(Boolean)));
    if (!list.length) return {};
    try {
      const { data, error } = await sb.storage.from('food-images').createSignedUrls(list, 3600);
      if (error || !data) return {};
      const out = {};
      data.forEach((d) => { if (d && d.signedUrl && !d.error) out[d.path] = d.signedUrl; });
      return out;
    } catch (e) {
      return {}; // photos are optional; the tile shows instead
    }
  }
  async function withCovers(items, pick) {
    const urls = await signPaths(items.map((x) => pick(x) && pick(x).coverPath));
    items.forEach((x) => { const t = pick(x); if (t) t.photoUrl = urls[t.coverPath] || null; });
    return items;
  }
  async function refreshCounts() {
    const c = await rpc('admin_counts');
    openReports = c.openReports;
    emit();
  }

  return {
    text, DataError, AccessError,

    subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); },
    /** Checks the admin and loads the counts; onLostAccess runs if a later call is refused. */
    async start(client, onLostAccess) {
      sb = client;
      const w = await rpc('admin_whoami');
      me = w.me;
      admins = w.admins || [];
      await refreshCounts();
      onLost = onLostAccess;
    },
    counts: () => ({ openReports, pendingVerifications: 0, unreadAlerts: 0 }),
    me: () => Object.assign({ isYou: true }, me),

    async overview() {
      const o = await rpc('admin_overview');
      o.reports.forEach(labelReport);
      await withCovers(o.reservations, (r) => r.listing);
      return o;
    },

    async users(q) {
      const res = await rpc('admin_users', Object.assign({ p_q: q.q || '', p_filter: q.role || 'ALL' }, paging(q)));
      res.rows.forEach(markYou);
      return res;
    },
    async user(id) {
      const u = await rpc('admin_user', { p_id: id });
      if (u) { markYou(u); u.openReports.forEach(labelReport); }
      return u;
    },

    async listings(q) {
      const res = await rpc('admin_listings', Object.assign({
        p_q: q.q || '', p_status: q.status || 'ALL', p_posted: q.posted || 'ANY', p_donor: q.donorId || null
      }, paging(q)));
      await withCovers(res.rows, (l) => l);
      return res;
    },
    async listing(id) {
      const l = await rpc('admin_listing', { p_id: id });
      if (!l) return null;
      l.reports.forEach(labelReport);
      const urls = await signPaths(l.photoPaths);
      l.photoUrls = l.photoPaths.map((p) => urls[p]).filter(Boolean);
      return l;
    },
    async removeListing(id, reason) {
      const r = await rpc('admin_remove_listing', { p_post: id, p_reason: reason });
      await refreshCounts();
      return r;
    },
    async dismissListingReports(id, note) {
      const r = await rpc('admin_dismiss_listing_reports', { p_post: id, p_note: note || null });
      await refreshCounts();
      return r;
    },
    markListingReviewed(id, note) { return rpc('admin_mark_listing_reviewed', { p_post: id, p_note: note || null }); },

    async reservations(q) {
      const res = await rpc('admin_reservations', Object.assign({ p_q: q.q || '', p_status: q.status || 'ALL', p_date: q.date || 'ANY' }, paging(q)));
      await withCovers(res.rows, (r) => r.listing);
      return res;
    },
    async reservation(id) {
      const r = await rpc('admin_reservation', { p_id: id });
      if (!r) return null;
      await withCovers([r], (x) => x.listing);
      r.timeline = window.SaluTimeline.build(r);
      return r;
    },

    async reports(q) {
      const res = await rpc('admin_reports', Object.assign({
        p_q: q.q || '', p_status: q.status || 'OPEN', p_target: q.target || 'ALL', p_reason: q.reason || 'ALL'
      }, paging(q)));
      res.rows.forEach(labelReport);
      return res;
    },
    async report(id) {
      const r = await rpc('admin_report', { p_id: id });
      if (!r) return null;
      labelReport(r);
      r.otherReports.forEach(labelReport);
      if (r.targetType === 'LISTING' && r.targetDetail) await withCovers([r.targetDetail], (x) => x);
      return r;
    },
    async dismissReport(id, note) {
      const r = await rpc('admin_dismiss_report', { p_report: id, p_note: note || null });
      await refreshCounts();
      return r;
    },
    addReportNote(id, note) { return rpc('admin_add_report_note', { p_report: id, p_note: note }); },

    async audit(q) {
      const code = Object.keys(text.actions).find((k) => text.actions[k] === q.action) || 'ALL';
      const res = await rpc('admin_audit', Object.assign({
        p_q: q.q || '', p_admin: q.adminId && q.adminId !== 'ALL' ? q.adminId : null, p_action: code, p_range: q.range || 'ANY'
      }, paging(q)));
      res.rows.forEach((a) => { a.action = text.actions[a.action] || a.action; markYou(a.admin); });
      return res;
    },
    auditFilters() {
      return { admins: admins.map((a) => Object.assign({ isYou: a.id === me.id }, a)), actions: Object.values(text.actions) };
    }
  };
})();
```

- [ ] **Step 3: Write `admin/js/config.example.js` and ignore the real one**

```js
/*
 * Copy to config.js (git-ignored) and fill in. These values are public in any web
 * app; security comes from the admin check in every Supabase function. See README.md.
 */
window.SaluConfig = {
  supabaseUrl: 'https://YOUR-PROJECT.supabase.co',
  supabaseKey: 'YOUR-PUBLISHABLE-KEY',
  firebase: {
    apiKey: 'FROM-FIREBASE-CONSOLE',
    authDomain: 'salusalo.web.app',
    projectId: 'salusalo',
    appId: 'FROM-FIREBASE-CONSOLE'
  }
};
```
Append to `.gitignore`:
```
# Admin dashboard settings (Supabase URL and key, Firebase web config).
/admin/js/config.js
# Firebase CLI cache.
.firebase/
```
Copy `config.example.js` to `config.js` unchanged for now (the developer fills it in Task 9). Run `git check-ignore admin/js/config.js`; expected: the path is printed.

- [ ] **Step 4: Write `admin/js/auth.js`**

```js
/* Sign-in for admins: Firebase Auth (the app's project), then the admin check on Supabase. */
(function () {
  'use strict';
  const cfg = window.SaluConfig;
  const box = document.getElementById('signin');
  const form = document.getElementById('signin-form');
  const msg = document.getElementById('signin-msg');
  const submit = document.getElementById('signin-submit');
  const google = document.getElementById('signin-google');
  const appEl = document.getElementById('app');
  const LOST = 'salusalo-admin:signin-message';

  function setBusy(on) {
    submit.disabled = on;
    google.disabled = on;
    submit.textContent = on ? 'Signing in…' : 'Sign in';
  }
  function show(message) {
    appEl.hidden = true;
    box.hidden = false;
    msg.textContent = message || '';
    msg.hidden = !message;
    setBusy(false);
  }
  function remember(message) { try { sessionStorage.setItem(LOST, message); } catch (e) { /* storage blocked */ } }
  function recall() {
    try { const m = sessionStorage.getItem(LOST); sessionStorage.removeItem(LOST); return m; } catch (e) { return null; }
  }

  if (!cfg || !cfg.supabaseUrl || !cfg.supabaseKey || !cfg.firebase || /YOUR-|FROM-/.test(JSON.stringify(cfg))) {
    show('This copy of the dashboard has no settings yet (js/config.js). See admin/README.md.');
    form.querySelectorAll('input, button').forEach((el) => { el.disabled = true; });
    return;
  }

  firebase.initializeApp(cfg.firebase);
  const auth = firebase.auth();
  const sb = window.supabase.createClient(cfg.supabaseUrl, cfg.supabaseKey, {
    accessToken: async () => (auth.currentUser ? auth.currentUser.getIdToken() : null)
  });
  let started = false;

  window.SaluAuth = { signOut: () => auth.signOut().then(() => { location.hash = ''; location.reload(); }) };

  function authError(e) {
    const c = e && e.code;
    if (c === 'auth/popup-closed-by-user' || c === 'auth/cancelled-popup-request') return '';
    if (c === 'auth/invalid-credential' || c === 'auth/wrong-password' || c === 'auth/user-not-found' || c === 'auth/invalid-email') return 'Wrong email or password.';
    if (c === 'auth/too-many-requests') return 'Too many attempts. Try again in a few minutes.';
    if (c === 'auth/network-request-failed') return 'Couldn’t reach the server. Check your connection and try again.';
    if (c === 'auth/user-disabled') return 'This account is disabled.';
    return 'Couldn’t sign in. Please try again.';
  }

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const email = document.getElementById('signin-email').value.trim();
    const password = document.getElementById('signin-password').value;
    if (!email || !password) { show('Enter your email and password.'); return; }
    setBusy(true);
    try { await auth.signInWithEmailAndPassword(email, password); } catch (e) { show(authError(e)); }
  });
  google.addEventListener('click', async () => {
    setBusy(true);
    const provider = new firebase.auth.GoogleAuthProvider();
    try {
      await auth.signInWithPopup(provider);
    } catch (e) {
      if (e && e.code === 'auth/popup-blocked') { await auth.signInWithRedirect(provider); return; }
      show(authError(e));
    }
  });
  auth.getRedirectResult().catch((e) => show(authError(e)));

  auth.onAuthStateChanged(async (user) => {
    if (!user) { show(recall()); return; }
    if (started) return;
    setBusy(true);
    try {
      await window.SaluData.start(sb, () => {
        remember('Your admin access ended. Please sign in again.');
        window.SaluAuth.signOut();
      });
    } catch (e) {
      if (e instanceof window.SaluData.AccessError) {
        remember('This account doesn’t have admin access.');
        await auth.signOut(); // onAuthStateChanged(null) shows the message
        return;
      }
      show('Couldn’t reach Salu-Salo. Check your connection, then reload the page.');
      return;
    }
    started = true;
    box.hidden = true;
    appEl.hidden = false;
    window.App.start();
  });
})();
```

- [ ] **Step 5: Edit `admin/index.html`**

Replace `<div class="app" id="app">` with:
```html
  <div class="signin" id="signin" hidden>
    <form class="signin__card" id="signin-form" novalidate>
      <span class="logo signin__logo"><img src="assets/salusalo-logo.webp" alt="" width="56" height="56"></span>
      <h1 class="signin__title">Salu-Salo Admin</h1>
      <p class="signin__sub">Sign in with your admin account.</p>
      <p class="signin__msg" id="signin-msg" role="alert" hidden></p>
      <label class="field"><span class="field__label">Email</span><input class="input" id="signin-email" type="email" autocomplete="username" required></label>
      <label class="field"><span class="field__label">Password</span><input class="input" id="signin-password" type="password" autocomplete="current-password" required></label>
      <button class="btn btn--primary" id="signin-submit" type="submit">Sign in</button>
      <div class="signin__or"><span>or</span></div>
      <button class="btn btn--secondary" id="signin-google" type="button">Continue with Google</button>
    </form>
  </div>

  <div class="app" id="app" hidden>
```
Delete the `<button class="sample-chip" …>…</button>` line. Replace the bell line with:
```html
          <a class="icon-btn bell" id="bell" href="#/reports" aria-label="Reports"><span data-icon="flag"></span><span class="bell__dot" id="bell-dot" hidden></span></a>
```
Replace the script list (from `<script src="js/icons.js">` to `<script src="js/app.js"></script>`) with (integrity values from Step 1):
```html
  <script src="https://www.gstatic.com/firebasejs/10.14.1/firebase-app-compat.js" integrity="sha384-…" crossorigin="anonymous"></script>
  <script src="https://www.gstatic.com/firebasejs/10.14.1/firebase-auth-compat.js" integrity="sha384-…" crossorigin="anonymous"></script>
  <script src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.49.4/dist/umd/supabase.js" integrity="sha384-…" crossorigin="anonymous"></script>
  <script src="js/config.js"></script>
  <script src="js/icons.js"></script>
  <script src="js/timeline.js"></script>
  <script src="js/data.js"></script>
  <script src="js/ui.js"></script>
  <script src="js/charts.js"></script>
  <script src="js/list-view.js"></script>
  <script src="js/pages/overview.js"></script>
  <script src="js/pages/users.js"></script>
  <script src="js/pages/listings.js"></script>
  <script src="js/pages/reservations.js"></script>
  <script src="js/pages/reports.js"></script>
  <script src="js/pages/analytics.js"></script>
  <script src="js/pages/notifications.js"></script>
  <script src="js/pages/audit.js"></script>
  <script src="js/pages/settings.js"></script>
  <script src="js/app.js"></script>
  <script src="js/auth.js"></script>
```
If `admin/index.html` has a comment saying it shows sample data, change it to say the dashboard reads Salu-Salo through admin-only functions.

- [ ] **Step 6: Edit `admin/js/app.js`**

1. Replace
```js
    bellDot.hidden = !c.unreadAlerts;
    bell.setAttribute('aria-label', c.unreadAlerts ? 'Your alerts, ' + c.unreadAlerts + ' unread' : 'Your alerts');
```
with
```js
    bellDot.hidden = !c.openReports;
    bell.setAttribute('aria-label', c.openReports ? 'Reports, ' + c.openReports + ' open' : 'Reports');
```
2. Delete the whole `/* ---------- Sample data explanation ---------- */` block (the `sample-chip` click handler through its closing `});`).
3. Replace `  let firstRoute = true;` with
```js
  let firstRoute = true;
  let started = false;
```
and `window.addEventListener('hashchange', () => { prevHash = currHash; currHash = location.hash; route(); });` with
```js
  window.addEventListener('hashchange', () => { prevHash = currHash; currHash = location.hash; if (started) route(); });
```
4. Replace the last three statements
```js
  renderProfile();
  applyMode();
  route();
```
with
```js
  applyMode();
  /** Called by auth.js once the signed-in account passed the admin check. */
  window.App.start = function () {
    started = true;
    renderProfile();
    route();
  };
```

- [ ] **Step 7: Edit `admin/js/ui.js`**

1. Replace `  const fmt = {\n    num: (n) =>` with
```js
  const fmt = {
    /** A short reference for a UUID, like "#3F2A9C01". */
    ref: (id) => '#' + String(id || '').slice(0, 8).toUpperCase(),
    num: (n) =>
```
2. Replace `  function identity(visual, name, meta, href) {` with
```js
  /** A listing photo from a signed URL, or the category tile when there is none. */
  function photo(url, category, big, label) {
    if (!url) return thumb(category, big, label);
    const img = h('img', { src: url, alt: big ? (label || category) : '', loading: 'lazy', referrerpolicy: 'no-referrer' });
    return big ? h('div', { class: 'thumb thumb--lg thumb--photo' }, img) : h('span', { class: 'thumb thumb--photo', 'aria-hidden': 'true' }, img);
  }
  function identity(visual, name, meta, href) {
```
3. In the returned object replace `avatar, thumb, identity,` with `avatar, thumb, photo, identity,`.

- [ ] **Step 8: Edit `admin/css/admin.css`**

Delete the `.sample-chip { … }`, `.sample-chip svg { … }` and `.sample-chip:hover { … }` rules and the two `.sample-chip…` lines inside the phone media query. Append:
```css
/* ---------- Listing photos (signed URLs) ---------- */
.thumb--photo { padding: 0; overflow: hidden; }
.thumb--photo img { width: 100%; height: 100%; object-fit: cover; display: block; }
.photo-strip { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 8px; }
.photo-strip a { display: block; border-radius: var(--r-md); overflow: hidden; background: var(--neutral); }
.photo-strip img { width: 100%; aspect-ratio: 1; object-fit: cover; display: block; }

/* ---------- Sign in ---------- */
.signin { min-height: 100vh; min-height: 100dvh; display: grid; place-items: center; padding: 24px 16px; background: var(--bg); }
.signin__card { width: 100%; max-width: 400px; display: grid; gap: 14px; padding: 32px 24px; background: var(--surface); border: 1px solid var(--line); border-radius: var(--r-lg); box-shadow: var(--shadow-md); }
.signin__logo { justify-self: center; width: 56px; height: 56px; }
.signin__title { margin: 0; text-align: center; font-size: var(--t-2xl); color: var(--ink); }
.signin__sub { margin: 0 0 4px; text-align: center; color: var(--ink-2); }
.signin__msg { margin: 0; padding: 10px 12px; border-radius: var(--r-md); background: var(--error-soft); color: var(--error); font-size: var(--t-sm); }
.signin__or { display: flex; align-items: center; gap: 10px; color: var(--ink-2); font-size: var(--t-sm); }
.signin__or::before, .signin__or::after { content: ""; flex: 1; height: 1px; background: var(--line); }
.signin .btn { width: 100%; justify-content: center; }
```

- [ ] **Step 9: Delete the sample and check the scripts**

Run:
```bash
git rm admin/js/sample-data.js
for f in admin/js/*.js admin/js/pages/*.js; do node --check "$f" || echo "FAIL $f"; done
grep -rn "SaluSample\|sample-chip\|setFailMode\|getFailMode" admin/ || echo "no sample references"
```
Expected: no `FAIL`; the grep may still list `admin/js/pages/settings.js`, `analytics.js`, `notifications.js`, `users.js` (rewritten in Task 7). Nothing else.

- [ ] **Step 10: Commit**

```bash
git add -A admin/ .gitignore
git commit -m "Connect the admin dashboard's data layer to Supabase and add admin sign-in"
```

---

### Task 7: Pages on real data

**Files:**
- Rewrite: `admin/js/pages/users.js`, `admin/js/pages/settings.js`, `admin/js/pages/analytics.js`, `admin/js/pages/notifications.js`
- Modify: `admin/js/pages/overview.js`, `admin/js/pages/listings.js`, `admin/js/pages/reports.js`, `admin/js/pages/reservations.js`, `admin/js/pages/audit.js`

**Interfaces:**
- Consumes: Task 6 `SaluData`, `UI.photo`, `UI.fmt.ref`, `SaluAuth.signOut`.

- [ ] **Step 1: Rewrite `admin/js/pages/users.js`**

```js
/* Users: everyone on Salu-Salo and what they've done. Read-only in this version. */
window.Pages = window.Pages || {};
window.Pages.users = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, fmt, btn, badge, avatar, identity, notice, dl, section, empty, errorState, skeletonPanel } = UI;
  const data = window.SaluData;

  let s = null; // page state while mounted

  const ROLE_OPTIONS = [['ALL', 'Everyone'], ['DONOR', 'Has posted'], ['RECIPIENT', 'Never posted'], ['ADMIN', 'Admins']];

  function youTag(row) { return row.isYou ? h('span', { class: 'you-tag' }, 'You') : null; }
  function emailBadge(u) {
    return u.emailVerified ? badge('verification', 'VERIFIED', 'Email verified') : badge('verification', 'NONE', 'Email not verified');
  }

  function mount(main) {
    s = { panelFor: null };
    s.list = window.ListView({
      what: 'users', noun: 'user', caption: 'Users',
      defaults: { q: '', role: 'ALL' },
      filters: [
        { key: 'q', type: 'search', label: 'Search users', placeholder: 'Search by name or email' },
        { key: 'role', type: 'select', label: 'Show', options: ROLE_OPTIONS }
      ],
      fetch: (q) => data.users(q),
      open: (row) => window.App.go('#/users/' + row.id),
      columns: [
        { label: 'User', className: 'cell-wide', cell: (u) => identity(avatar(u), [u.name, youTag(u)], 'Joined ' + fmt.date(u.joinedAt), '#/users/' + u.id) },
        { label: 'Email', hide: 'tablet', cell: (u) => h('span', { class: 'muted' }, u.email) },
        { label: 'Role', cell: (u) => badge('role', u.role) },
        { label: 'Email check', cell: emailBadge },
        { label: 'Reports', className: 'cell-num', cell: (u) => u.openReports ? UI.flagBadge(u.openReports) : h('span', { class: 'muted' }, 'None') },
        { label: h('span', { class: 'sr-only' }, 'Actions'), className: 'cell-actions', cell: (u) => rowMenu(u) }
      ],
      card: (u) => UI.rowCard({
        visual: avatar(u), title: u.name, href: '#/users/' + u.id,
        meta: { sub: u.email, items: [{ icon: 'event', text: 'Joined ' + fmt.date(u.joinedAt) }] },
        badges: [badge('role', u.role), emailBadge(u), u.openReports ? UI.flagBadge(u.openReports) : null]
      }),
      emptyState: (q, filtered) => filtered
        ? { iconName: 'search', title: q.q ? 'No users match “' + q.q + '”' : 'No users match this filter', text: 'Try another name or email, or show everyone.' }
        : { iconName: 'group', title: 'No users yet', text: 'People appear here once they create a Salu-Salo account.' }
    });
    main.append(
      UI.pageHead({ title: 'Users', sub: 'Everyone with a Salu-Salo account. “Donor” means they have posted food.' }),
      s.list.el);
    s.list.load();
  }

  function rowMenu(u) {
    return UI.menuButton('Actions for ' + u.name, () => [
      { label: 'View details', icon: 'visibility', onClick: () => window.App.go('#/users/' + u.id) },
      u.role === 'DONOR' ? { label: 'View listings', icon: 'lunchDining', onClick: () => viewListings(u) } : null
    ]);
  }
  function viewListings(u) {
    window.App.intent.listingsDonor = { id: u.id, name: u.name };
    window.App.go('#/listings');
  }

  async function openUser(id, keep) {
    s.panelFor = id;
    const onClose = () => window.App.closeTo('#/users');
    if (!keep) UI.openPanel({ label: 'User', title: 'Loading…', body: skeletonPanel(), onClose });
    let u;
    try { u = await data.user(id); } catch (err) {
      UI.openPanel({ label: 'User', title: 'Couldn’t load this user', body: errorState(() => openUser(id, true), 'this user'), onClose });
      return;
    }
    if (!s || s.panelFor !== id) return;
    if (!u) { UI.openPanel({ label: 'User', title: 'User not found', body: empty({ iconName: 'personOff', title: 'This account doesn’t exist', text: 'It may have been deleted, or the link is wrong.' }), onClose }); return; }

    const body = [
      h('div', { class: 'panel-hero' }, avatar(u, 'lg'),
        h('div', { class: 'panel-hero__text' },
          h('div', { class: 'badge-row' }, badge('role', u.role), emailBadge(u)),
          h('span', { class: 'muted' }, 'Joined ' + fmt.date(u.joinedAt)))),
      u.openReports.length ? notice({ tone: 'warning', iconName: 'flag', title: fmt.plural(u.openReports.length, 'open report') + ' about them or their listings', text: u.openReports.map((r) => r.reasonLabel).join(', '), action: h('a', { class: 'btn btn--secondary btn--sm', href: '#/reports/' + u.openReports[0].id }, 'Review') }) : null,
      section('Profile', dl([
        ['Email', u.email],
        ['Role', data.text.roles[u.role]],
        ['Joined', fmt.dateTime(u.joinedAt)]
      ])),
      section('Activity', h('div', { class: 'mini-stats' },
        miniStat(u.stats.listings, 'Listings posted'),
        miniStat(u.stats.reservations, 'Reservations made'),
        miniStat(u.stats.completed, 'Completed pickups')),
      h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)' } },
        fmt.plural(u.stats.activeListings, 'listing') + ' up now · ' + fmt.plural(u.stats.handedOver, 'reservation') + ' handed over to others · ' + fmt.plural(u.stats.reportsAgainst, 'report') + ' about them')),
      section('Recent activity', u.events.length
        ? h('ul', { class: 'list' }, u.events.map((ev) => {
          const inner = [h('span', { class: 'icon-bubble icon-bubble--neutral' }, icon(ev.icon, 18)),
            h('span', { class: 'list__main' }, h('span', { class: 'list__title' }, ev.text), h('span', { class: 'list__meta' }, (ev.meta ? ev.meta + ' · ' : '') + fmt.relative(ev.at)))];
          return h('li', null, ev.link ? h('a', { class: 'list__item list__item--button', href: ev.link, style: { paddingInline: '0' } }, inner) : h('div', { class: 'list__item', style: { paddingInline: '0' } }, inner));
        }))
        : h('p', { class: 'muted' }, 'Nothing yet.'))
    ];
    const foot = u.stats.listings ? [btn({ label: 'View listings', icon: 'lunchDining', variant: 'secondary', onClick: () => viewListings(u) })] : null;
    UI.openPanel({ label: 'User', title: [u.name, youTag(u)], sub: u.email, body, foot, onClose });
  }
  function miniStat(value, label) {
    return h('div', { class: 'mini-stat' }, h('span', { class: 'mini-stat__value' }, fmt.num(value)), h('span', { class: 'mini-stat__label' }, label));
  }

  function update(route) {
    if (!s) return;
    if (route.id && route.id !== 'requests') openUser(route.id);
    else if (UI.panelIsOpen()) { s.panelFor = null; UI.closePanel(); }
  }

  return { title: 'Users', mount, update, unmount() { s = null; } };
})();
```

- [ ] **Step 2: Rewrite `settings.js`, `analytics.js`, `notifications.js`**

`admin/js/pages/settings.js`:
```js
/* Settings: the signed-in admin, and Log out. */
window.Pages = window.Pages || {};
window.Pages.settings = (function () {
  'use strict';
  const UI = window.UI;
  const { h, btn, notice, dl, card } = UI;
  const data = window.SaluData;

  function mount(main) {
    const me = data.me();
    main.append(
      UI.pageHead({ title: 'Settings', sub: 'Your admin account.' }),
      card({ title: 'Account', body: h('div', { style: { display: 'grid', gap: '16px' } },
        dl([['Name', me.name], ['Email', me.email], ['Role', 'Administrator']]),
        notice({ tone: 'neutral', text: 'Change your name or password in the Salu-Salo app. Admin access is given and taken back in the database.' }),
        h('div', null, btn({ label: 'Log out', icon: 'logout', variant: 'secondary', onClick: () => window.SaluAuth.signOut() }))) }));
  }
  return { title: 'Settings', mount };
})();
```
`admin/js/pages/analytics.js`:
```js
/* Analytics: not connected yet. Overview has the main numbers. */
window.Pages = window.Pages || {};
window.Pages.analytics = (function () {
  'use strict';
  const { h, pageHead, empty } = window.UI;
  return {
    title: 'Analytics',
    mount(main) {
      main.append(pageHead({ title: 'Analytics', sub: 'Trends over time.' }),
        h('div', { class: 'card' }, empty({ iconName: 'monitoring', title: 'Not connected yet', text: 'Charts over time come in a later update. Overview shows the main numbers and the last 30 days of food rescued.', action: { label: 'Go to Overview', href: '#/overview' } })));
    }
  };
})();
```
`admin/js/pages/notifications.js`:
```js
/* Notifications (announcements to app users): not connected yet. */
window.Pages = window.Pages || {};
window.Pages.notifications = (function () {
  'use strict';
  const { h, pageHead, empty } = window.UI;
  return {
    title: 'Notifications',
    mount(main) {
      main.append(pageHead({ title: 'Notifications', sub: 'Announcements to people using the app.' }),
        h('div', { class: 'card' }, empty({ iconName: 'notifications', title: 'Not connected yet', text: 'Sending announcements needs a new kind of alert in the Salu-Salo app first. It comes in a later update.' })));
    }
  };
})();
```
`empty()` passes `action` to `btn()`, so it is an options object (`{ label, href }`), not a node.

- [ ] **Step 3: Edit `admin/js/pages/overview.js`**

1. Delete the lines from `    let sampleNote = null;` through the closing `    }` of `if (!dismissed) { … }`, and the `      sampleNote,` line inside `main.append(`.
2. Delete `    const verifBody = h('div', null, skeletonList(3));`.
3. Delete the Verification requests card:
```js
          card({ title: 'Verification requests', sub: 'Waiting for a decision', className: 'card--flush', body: verifBody,
            actions: h('a', { class: 'btn btn--ghost btn--sm', href: '#/users/requests' }, 'All', icon('chevronRight', 18)) }),
```
4. Replace
```js
          card({ title: 'Food rescue', sub: 'The last 30 days', body: rescueBody,
            actions: h('a', { class: 'btn btn--ghost btn--sm', href: '#/analytics' }, 'Analytics', icon('chevronRight', 18)) }))));
    refs = { stats, activityBody, verifBody, reportsBody, resBody, rescueBody };
```
with
```js
          card({ title: 'Food rescue', sub: 'The last 30 days', body: rescueBody }))));
    refs = { stats, activityBody, reportsBody, resBody, rescueBody };
```
5. Replace `      [r.activityBody, r.verifBody, r.reportsBody, r.resBody, r.rescueBody]` with `      [r.activityBody, r.reportsBody, r.resBody, r.rescueBody]`.
6. Delete `    renderVerification(r.verifBody, res.verification);` and the whole `function renderVerification(body, rows) { … }`.
7. In `renderReservations`, replace `      thumb(r.listing.category),` with `      window.UI.photo(r.listing.photoUrl, r.listing.category),`.

- [ ] **Step 4: Edit `admin/js/pages/listings.js`**

1. `identity(thumb(l.category), l.name,` → `identity(UI.photo(l.photoUrl, l.category), l.name,`
2. `visual: thumb(l.category), title: l.name,` → `visual: UI.photo(l.photoUrl, l.category), title: l.name,`
3. Replace
```js
    const photoLabel = l.photos ? fmt.plural(l.photos, 'photo') + ' · not shown in sample data' : 'No photo; the app shows a plain tile';
    const body = [
      thumb(l.category, true, photoLabel),
```
with
```js
    const body = [
      l.photoUrls.length
        ? h('div', { class: 'photo-strip' }, l.photoUrls.map((url, i) => h('a', { href: url, target: '_blank', rel: 'noopener noreferrer', 'aria-label': 'Open photo ' + (i + 1) + ' of ' + l.photoUrls.length },
          h('img', { src: url, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer' }))))
        : thumb(l.category, true, l.photos ? 'The photos couldn’t be loaded' : 'No photo; the app shows a plain tile'),
```
4. `h('span', { class: 'list__meta' }, r.id + ' · by ' + r.reporter.name` → `h('span', { class: 'list__meta' }, fmt.ref(r.id) + ' · by ' + r.reporter.name`

- [ ] **Step 5: Edit `admin/js/pages/reports.js`**

1. `h('a', { class: 'row-link mono', href: '#/reports/' + r.id }, r.id)` → `h('a', { class: 'row-link mono', href: '#/reports/' + r.id }, fmt.ref(r.id))`
2. `'aria-label': (r.status === 'OPEN' ? 'Review ' : 'View ') + r.id` → `'aria-label': (r.status === 'OPEN' ? 'Review report ' : 'View report ') + fmt.ref(r.id)`
3. `h('span', { class: 'badge badge--outline mono' }, r.id)` → `h('span', { class: 'badge badge--outline mono' }, fmt.ref(r.id))`
4. Delete the whole `async function suspend(r, person) { … }`.
5. `identity(thumb(t.category), t.name,` → `identity(UI.photo(t.photoUrl, t.category), t.name,`
6. `h('div', { class: 'badge-row' }, badge('listing', t.status), badge('account', t.donorAccountStatus, t.donorAccountStatus === 'ACTIVE' ? 'Donor active' : 'Donor suspended')))` → `h('div', { class: 'badge-row' }, badge('listing', t.status)))`
7. `h('div', { class: 'badge-row' }, badge('role', t.role), badge('verification', t.verification), badge('account', t.accountStatus)));` → `h('div', { class: 'badge-row' }, badge('role', t.role)));`
8. `h('span', { class: 'list__meta' }, o.id + ' · ' + fmt.relative(o.createdAt))` → `h('span', { class: 'list__meta' }, fmt.ref(o.id) + ' · ' + fmt.relative(o.createdAt))`
9. Delete these three lines in the foot:
```js
      const person = isListing ? (t ? t.donor : null) : t;
      const personActive = isListing ? t && t.donorAccountStatus === 'ACTIVE' : t && t.accountStatus === 'ACTIVE' && t.role !== 'ADMIN';
      if (person && personActive) foot.push(btn({ label: isListing ? 'Suspend donor' : 'Suspend user', icon: 'block', variant: 'danger-outline', onClick: () => suspend(r, person) }));
```
10. `UI.openPanel({ label: 'Report ' + r.id,` → `UI.openPanel({ label: 'Report ' + fmt.ref(r.id),`
11. `maxlength: '500', placeholder: 'Only admins see notes'` → `maxlength: '1000', placeholder: 'Only admins see notes'`
12. `} else if (!isListing) {` → `} else if (!isListing && t) {`

- [ ] **Step 6: Edit `admin/js/pages/reservations.js`**

1. Replace every `thumb(r.listing.category)` (column, card, panel hero: 3 places) with `UI.photo(r.listing.photoUrl, r.listing.category)`.
2. Replace
```js
        ['Status', ({ RESERVED: 'Reserved, waiting for pickup', CLAIMED: 'Claimed (picked up)', CANCELLED: 'Cancelled' })[r.status]],
```
with
```js
        ['Status', r.status === 'CANCELLED' ? r.timeline[r.timeline.length - 1].title : ({ RESERVED: 'Reserved, waiting for pickup', CLAIMED: 'Claimed (picked up)' })[r.status]],
        ['Pick up by', r.pickupDeadline ? fmt.dateTime(r.pickupDeadline) : null],
```
3. `['Handover', data.text.handover[r.handover]],` → `['Handover', data.text.handover[r.handover] || null],`
4. Replace the closing note text `'Monitoring only. The recipient can cancel in the app while it’s reserved; the donor marks it picked up at the handover.'` with `'Monitoring only. The recipient can cancel in the app while it’s reserved; the donor marks it picked up or releases it, and it expires by itself after its pickup time.'`

- [ ] **Step 7: Edit `admin/js/pages/audit.js`**

1. In `ACTION_ICON` add `'Added note': 'editNote',` after `'Reviewed listing': 'taskAlt',`.
2. Delete the line `      notice({ tone: 'neutral', iconName: 'info', text: 'Sample log. When connected, entries would come from the server, so nobody could edit or delete them.' }),`.
3. Replace `'Each admin action (approving, suspending, removing, sending, changing settings) is recorded here.'` with `'Each admin action (removing a listing, dismissing a report, adding a note, marking a listing reviewed) is recorded here, and nobody can edit or delete an entry.'`
4. Replace `'Search a person, listing, report or detail'` with `'Search a listing, report, admin or detail'`.

- [ ] **Step 8: Check scripts and leftovers**

Run:
```bash
for f in admin/js/*.js admin/js/pages/*.js; do node --check "$f" || echo "FAIL $f"; done
grep -rn "SaluSample\|sample data\|Sample data\|suspendUser\|verificationQueue\|accountStatus\|\.verification\b\|lastActiveAt\|\.city\b\|barangay" admin/js || echo "clean"
node admin/tests/timeline.test.js
```
Expected: no `FAIL`; `clean` (or only harmless matches inside `ui.js`'s `BADGES` table, which stay); `timeline: all checks passed`.

- [ ] **Step 9: Commit**

```bash
git add -A admin/js/pages
git commit -m "Show real users, listings, reservations, reports and audit entries in the admin pages"
```

---

### Task 8: Hosting config and docs

**Files:**
- Create: `firebase.json`, `.firebaserc`
- Modify: `admin/README.md`, `supabase/README.md`, `docs/PROJECT_STATUS.md`, `CLAUDE.md`

- [ ] **Step 1: Write `firebase.json`**

```json
{
  "hosting": {
    "public": "admin",
    "ignore": ["README.md", "tests/**", "js/config.example.js", "**/.*"],
    "headers": [
      {
        "source": "**",
        "headers": [
          { "key": "Content-Security-Policy", "value": "default-src 'self'; script-src 'self' https://www.gstatic.com https://cdn.jsdelivr.net https://apis.google.com https://accounts.google.com; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob: https://*.supabase.co https://*.googleusercontent.com; connect-src 'self' https://*.supabase.co wss://*.supabase.co https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://www.googleapis.com https://apis.google.com; frame-src 'self' https://salusalo.firebaseapp.com https://accounts.google.com https://apis.google.com; frame-ancestors 'self'; base-uri 'self'; form-action 'self'; object-src 'none'" },
          { "key": "X-Content-Type-Options", "value": "nosniff" }
        ]
      },
      {
        "source": "**/*.@(html|js|css)",
        "headers": [{ "key": "Cache-Control", "value": "no-cache" }]
      }
    ]
  }
}
```
`.firebaserc`:
```json
{ "projects": { "default": "salusalo" } }
```

- [ ] **Step 2: Rewrite `admin/README.md`**

Replace "It shows sample data", "Why it can't show real data yet" and "Notes for the sample" with these sections (keep "What's where" and "Screen sizes", updating the table: no `sample-data.js`; add `config.example.js`, `auth.js`, `timeline.js`, `tests/timeline.test.js`):
```markdown
**It shows the live Salu-Salo data** to admins only. Every request goes to an admin-only Supabase function (`supabase/migrations/20261012_admin_dashboard.sql`) that checks the caller's Firebase token and `users.role = 'ADMIN'`; the page holds only the publishable key. What works: Overview, Users, Listings (with photos), Reservations, Reports (dismiss, notes, remove the listing), Audit log, Settings (Log out). Not connected yet: Analytics, Notifications (announcements), verification requests, suspending accounts.

## One-time setup

1. Run `supabase/migrations/20261012_admin_dashboard.sql` in the Supabase SQL Editor, then `supabase/tests/admin_dashboard_check.sql` and `supabase/tests/admin_dashboard_live_safety_check.sql` (see `supabase/README.md`).
2. Make your account an admin (it must have signed into the app once): `update public.users set role = 'ADMIN' where email = 'you@example.com';`
3. Firebase console → Project settings → Your apps → Add app → Web (no Firebase Hosting setup needed there). Copy `apiKey` and `appId`.
4. Copy `admin/js/config.example.js` to `admin/js/config.js` (git-ignored) and fill in the Supabase URL and publishable key (the same values as `local.properties`) and the Firebase values. Keep `authDomain: 'salusalo.web.app'`.
5. Authentication → Settings → Authorized domains: `salusalo.web.app` and `localhost` are there by default; keep them.

## Try it on this computer

`npx http-server admin -p 8080 -c-1`, then open http://localhost:8080 and sign in.

## Put it online (open it on your phone)

`npx firebase-tools login` once, then `npx firebase-tools deploy --only hosting` from the repo root. Open https://salusalo.web.app on the phone; in the browser menu choose "Add to Home screen" to open it like an app. Deploy again after every change. Undo a deploy in the Firebase console → Hosting → release history.

## Admins

Give: `update public.users set role = 'ADMIN' where email = '…';` Take back: `… set role = 'RECIPIENT' …`. A signed-in dashboard notices on its next request and signs out. Every admin action is in the Audit log; nobody can edit or delete an entry.
```

- [ ] **Step 3: Add the migration to `supabase/README.md`**

Following the existing per-migration entries, add an entry for `20261012_admin_dashboard.sql`: what it adds (admin check, admin read and write functions, report statuses `RESOLVED`/`DISMISSED`, `report_notes`, `admin_audit_log`, admin access to listing photos), that it runs after `20261011`, is safe to run again, leaves app builds unaffected, the two checks with their expected results (read-only: all `true`, the last once an admin is set; safety check: `20 passed, 0 failed`), and the `update public.users set role = 'ADMIN' …` line.

- [ ] **Step 4: Update `docs/PROJECT_STATUS.md` and `CLAUDE.md`**

`docs/PROJECT_STATUS.md`: add `## 25. Admin dashboard with real data (Phase 6, first slice)` with status "written on branch `claude/admin-real`; built and checked locally only" (the local checks that actually passed, with their counts), what changed, the database step, the manual test (Task 9's list), and known limits (spec §6). Update the §1 table row for Phase 6 to "Started: first slice (§25) on branch `claude/admin-real`, not yet merged", the §20 row to "replaced by §25 once merged", the top "_Last updated_" line and "Current state". Never write that anything passed on a phone or live until the developer reports it.

`CLAUDE.md`: in the roadmap's Phase 6 line say the first slice is on branch `claude/admin-real` (§25); in "Supabase objects" add the 20261012 entry (functions, tables, report statuses, photo rule); in "Security and ownership rules" add: "**Admin functions (§25).** Only `users.role = 'ADMIN'`, set by hand, may run the `admin_*` functions; each starts with `admin_require()`. Clients have no privileges on `report_notes` and `admin_audit_log`, and the audit log is insert-only through the admin functions. Admins remove listings with `admin_remove_listing` (never delete). `can_read_food_image` also lets admins read listing photos. The dashboard holds only the publishable key; `admin/js/config.js` is git-ignored."; in "Commands" add `node admin/tests/timeline.test.js` and the deploy command; in "Testing expectations" add the Node check.

- [ ] **Step 5: Gate build and commit**

Run (PowerShell): `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:assembleDebug --offline`
Expected: `BUILD SUCCESSFUL` (no app code changed).

```bash
git add firebase.json .firebaserc admin/README.md supabase/README.md docs/PROJECT_STATUS.md CLAUDE.md
git commit -m "Add Firebase Hosting for the admin dashboard and document the admin slice"
```

---

### Task 9: Developer setup, live checks and the manual test (the developer runs these)

No code. Hand the developer this list, wait for each result, and record what they report in `docs/PROJECT_STATUS.md` §25 (dates from `Get-Date`).

- [ ] **D1.** In the Supabase SQL Editor run `supabase/migrations/20261012_admin_dashboard.sql` (expect "Success. No rows returned"), then `supabase/tests/admin_dashboard_check.sql` (rows 1–6 `true`, row 7 `false`), then `update public.users set role = 'ADMIN' where email = '<their email>';`, then the check again (all `true`).
- [ ] **D2.** Run `supabase/tests/admin_dashboard_live_safety_check.sql`. Expect the error `SAFETY CHECK FINISHED: 20 passed, 0 failed, 0 skipped.` (check 14 may SKIP if an account has Reservation updates off).
- [ ] **D3.** Firebase console → add the Web app; fill `admin/js/config.js` as in `admin/README.md` (they paste the values; never echo them back).
- [ ] **D4.** Local try: `! npx http-server admin -p 8080 -c-1`, open http://localhost:8080 on the PC.

Manual test (PC first, then the phone; A = admin account, B = an ordinary app account; tag New):

- **M1 (New)** Open the site signed out → Sign In screen with the logo, "Salu-Salo Admin", Email, Password, Sign in, Continue with Google. Wrong password → "Wrong email or password."
- **M2 (New)** Sign in as B → "This account doesn't have admin access." and back on Sign In.
- **M3 (New)** Sign in as A (email or Google) → Overview: Total users, Active listings, Open reservations and Pending reports match what they know; Recent activity lists real names; no "Sample data" anywhere.
- **M4 (New)** In the app as B: post "Admin test" with a photo, then (as A in the app, or another account) reserve 1, and report it from the listing page ("Unsafe or spoiled food").
- **M5 (New)** Dashboard as A: the Reports badge shows the count; Reports → the new report → the listing's photo, the reporter; Add note "Checked" → "Note added." and the note with A's name; Listings → "Admin test" → photos in a strip, the reservation, the report.
- **M6 (New)** Remove listing with reason "Admin test removal" → "Listing removed successfully." with "1 reservation cancelled · 1 report resolved."; the listing shows Removed "by <A>"; Reports → Resolved tab has it; Audit log has "Removed listing" and "Added note". In the app: the reserver gets "Listing removed" ("Admin test was removed by Salu-Salo, so your reservation was cancelled."); B gets "Your listing was removed" ("Salu-Salo removed Admin test: Admin test removal"); "Admin test" is gone from Home.
- **M7 (New)** In the SQL Editor set A back to `RECIPIENT`; in the open dashboard click any page → signed out with "Your admin access ended. Please sign in again."; set A to `ADMIN` again.
- **D5.** Deploy: `! npx firebase-tools login`, then `npx firebase-tools deploy --only hosting` (expect "Deploy complete!" and `https://salusalo.web.app`).
- **M8 (New)** On the phone's browser open https://salusalo.web.app, sign in as A; Overview, Reports and a listing's photos show; the menu button opens the drawer; nothing scrolls sideways. Add to Home screen and open it from the icon (still signed in).
- **Regression (app):** B signs in, posts a listing, A (another account) reserves and cancels, chat and alerts still work. (Nothing in the app changed; this is the usual guard.)

Left out (not tested): Safari/iOS, screen readers, two admins at once, the Google redirect fallback when popups are blocked.

- [ ] **Step: Record and commit** the reported results in `docs/PROJECT_STATUS.md` §25 and the results log, then commit `Record the admin dashboard checks` and ask the developer whether to push and open the PR into `master`.
