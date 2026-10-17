# Suspend Accounts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admins suspend and restore accounts from the dashboard. A suspended account is locked out by the server and sees only a suspended screen in the app. Its active listings and open reservations close.

**Architecture:**
- **Server:** one migration adds two `users` columns and makes the existing identity gate `is_salusalo_firebase_jwt()` also refuse a suspended caller. Every policy, RPC, Storage rule and Realtime check already goes through that gate. `get_my_account_status()` is the only function that skips it. Two admin functions do the suspension and the restore.
- **App:** it asks `get_my_account_status()` at sign-in setup and whenever it comes back to the screen.
- **Dashboard:** it gets Suspend and Restore on the Users page.

**Tech Stack:** PostgreSQL 15/16 (Supabase), Kotlin + Jetpack Compose (supabase-kt 3.5), plain JavaScript (the `admin/` dashboard, no build step), Node checks, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-17-suspend-accounts-design.md` (approved 2026-10-17, commit `2233f0d`).

**Branch:** `claude/suspend-accounts`, from `claude/push-notifications` at `f04c77c` (PR #32, still open).

## Global Constraints

- **Reason codes and wording** (never rename a code; the app, the SQL and the dashboard must match):

  | Code | Wording |
  | --- | --- |
  | `NO_SHOWS` | Repeated no-shows at pickup |
  | `UNSAFE_FOOD` | Unsafe or misleading food |
  | `HARASSMENT` | Harassment or abuse |
  | `SPAM` | Spam or fake account |
  | `OTHER` | Breaking the community rules |

  An unknown code or NULL shows "Breaking the community rules".
- **Messages, exactly:**

  | Where | Text |
  | --- | --- |
  | App screen title | "Your account has been suspended" |
  | Chat refusal | "This person can't receive messages right now." |
  | Donor alert title | "Reservation cancelled" |
  | Donor alert body | "<recipient name>'s reservation of <listing> was cancelled. The quantity is available again." |
  | Dashboard toasts | "Account suspended." / "Account restored." |
  | Audit labels | "Account suspended" / "Account restored" |
  | Timeline ending | "Cancelled: account suspended" |
- **Admin refusals, exactly:**

  | Case | Text |
  | --- | --- |
  | No such user | "User not found" |
  | The caller themselves | "You can't suspend your own account." |
  | An admin | "Admins can't be suspended." |
  | Already suspended | "This account is already suspended." |
  | Bad reason | "Choose a reason." |
  | Blank or long note | "Write a note (up to 1,000 characters)." |
  | Restoring a non-suspended account | "This account isn't suspended." |
  | Restore note too long | "Keep the note under 1,000 characters." |
- **Function privileges (CLAUDE.md "Function privileges"):** every new function in `public` gets `revoke all … from public` and `revoke all … from anon`.
  - Client-callable ones also get `grant execute … to authenticated`.
  - `salusalo_caller_suspended()` also gets `service_role`, like `is_salusalo_firebase_jwt()` in `20261010`.
  - Internal helpers get no grant.
- **No new alert type** (`notifications.type`). Use only `RESERVATION_CANCELLED` and `RESERVATION_REMOVED`.
- **Keep the existing rules when redefining:** `send_message` keeps its rate limit (20 per minute under `pg_advisory_xact_lock`). `notify_reservation_change()` keeps every existing branch.
- **App errors:** never show or log an exception's own message. `getMyAccountStatus()` returns null on any failure except `CancellationException`.
- **Build commands (PowerShell):**
  - Android: `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`
  - Dashboard (Bash): `for t in admin/tests/*.test.js; do node "$t"; done`
- **Migrations are run by hand by the developer.** Never apply them to the live project from this plan; only the local PostgreSQL dry run.

## Review Focus

1. **No answer when the app returns** (offline, or the migration not applied): the app must stay where it is, neither flipping to the suspended screen nor leaving it. Pinned by `SuspensionTest.noAnswerChangesNothing` (Task 3).
2. **A note that is only spaces, or 1,001 characters:** refused with "Write a note (up to 1,000 characters).". Pinned by safety check 6 (Task 1).
3. **A listing that has already expired but still has an open reservation:** removed anyway, and its reservation cancelled. Pinned by safety check 8 (Task 1, listing L2).
4. **A reservation the suspended person already picked up (CLAIMED):** stays CLAIMED; only RESERVED ones are cancelled. Pinned by safety check 9 (Task 1, `r_b1`).
5. **A suspension that worked while the count reload failed:** the dashboard reports it as done, not as an error. Pinned by `data.test.js` (Task 5).

---

## File map

| File | What changes |
| --- | --- |
| `supabase/tests/admin_suspend_accounts_live_safety_check.sql` | **New.** The 18 checks (Task 1). |
| `supabase/migrations/20261017_admin_suspend_accounts.sql` | **New.** All server changes (Task 2). |
| `supabase/README.md` | The 20261017 entry (Task 2). |
| `app/src/main/java/com/example/salusalo/model/Suspension.kt` | **New.** `SUPPORT_EMAIL`, `AccountStatus`, `suspensionReasonLabel()`, `StatusChange`, `statusChange()` (Task 3). |
| `app/src/test/java/com/example/salusalo/SuspensionTest.kt` | **New.** 7 tests (Task 3). |
| `app/src/main/java/com/example/salusalo/data/UserRepository.kt` | `getMyAccountStatus()` and `ProfileException.Suspended` (Task 3). |
| `app/src/main/java/com/example/salusalo/MainActivity.kt` | The `"suspended"` screen, the setup check, the return hook and the shade clearing (Task 4). |
| `admin/js/data.js` | Labels, `suspendUser()`, `restoreUser()` (Task 5). |
| `admin/js/timeline.js` | The `ACCOUNT_SUSPENDED` ending (Task 5). |
| `admin/tests/data.test.js`, `admin/tests/timeline.test.js` | New cases (Task 5). |
| `admin/js/ui.js` | `confirm()` gets an optional `choice` select and `reason.maxLength` (Task 6). |
| `admin/js/pages/users.js` | Badge, filter, suspension panel, Suspend and Restore (Task 6). |
| `docs/PROJECT_STATUS.md`, `CLAUDE.md` | §36 and the rules (Task 7). |

---

### Task 1: The safety check, failing first (local PostgreSQL)

**Files:**
- Create: `supabase/tests/admin_suspend_accounts_live_safety_check.sql`
- Scratch (not in the repo): `<scratchpad>/pg/run.sh`. `<scratchpad>` is this session's scratchpad directory.

**Interfaces:**
- Consumes: the functions that already exist (`create_reservation(uuid, integer)`, `claim_reservation(uuid)`, `start_listing_conversation(uuid)`, `send_message(uuid, text)`, `register_my_push_token(text)`, `can_read_food_image(text)`).
- Produces: the check that Task 2's migration must turn to 18 passed. It calls `admin_suspend_user(uuid, text, text) returns jsonb`, `admin_restore_user(uuid, text) returns jsonb` and `get_my_account_status() returns jsonb`.

- [ ] **Step 1: Set up the local PostgreSQL harness from the push work**

The binaries and an initialised cluster from earlier sessions are still on disk. Copy the harness into this session's scratchpad and point it at the repo:

```bash
OLD=/c/Users/pasto/AppData/Local/Temp/claude/C--Users-pasto-AndroidStudioProjects-SaluSaloZip-SaluSalo/58191a6f-3412-491f-8173-0c019e554863/scratchpad/pg
BIN=/c/Users/pasto/AppData/Local/Temp/claude/C--Users-pasto-AndroidStudioProjects-SaluSaloZip-SaluSalo/a6aa6c48-84fe-47d7-9ec6-03a09030af13/scratchpad/pg/pgsql/bin
NEW="<scratchpad>/pg"
mkdir -p "$NEW"
cp "$OLD"/standins.sql "$OLD"/seed.sql "$OLD"/run.sh "$NEW"/
sed -i "s#^HERE=.*#HERE=\"$NEW\"#" "$NEW/run.sh"
"$BIN/pg_ctl.exe" -D "$OLD/data" -o "-p 5434" -l "$NEW/pg.log" start || true
"$BIN/psql.exe" -h localhost -p 5434 -U postgres -d postgres -c "select version()"
```

Expected: a `PostgreSQL 16.4` version row. If `pg_ctl` says the server is already running, that's fine.

- [ ] **Step 2: Confirm every existing migration still applies**

Run: `bash "<scratchpad>/pg/run.sh"`
Expected: `migrations: 25 applied`.

- [ ] **Step 3: Write the safety check**

Create `supabase/tests/admin_suspend_accounts_live_safety_check.sql`:

```sql
-- Salu-Salo suspend accounts: safety check for the live database (PROJECT_STATUS.md §36).
--
-- Run it in the Supabase dashboard after migrations/20261017_admin_suspend_accounts.sql:
-- SQL Editor -> New query -> paste -> Run. Leave the role on "postgres".
--
-- It takes the three newest accounts: A (made an admin inside the check), B (the
-- account it suspends) and C (another account). Inside the check, B posts two
-- listings and C reserves 1 of each (the second listing is then made to look
-- expired); C posts a listing and B reserves twice (C marks the first picked up);
-- B opens a chat about C's listing, C reports B, and B gets a made-up push token.
-- Then A suspends B, checks what that did and what B can still do, and restores B.
-- The "Sanity" checks must succeed: they prove the pretend sign-in works, so the
-- refusals are real. A check that cannot run reports SKIP.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the check
-- did (roles, listings, reservations, the chat, the report, alerts, tokens, the
-- suspension and the audit rows), so nothing is saved and no phone gets a push.
-- The error text is the report; it starts with "SAFETY CHECK FINISHED". Any other
-- error means the check itself did not run.

do $$
declare
  v_a        public.users;
  v_b        public.users;
  v_c        public.users;
  c_iss      text := 'https://securetoken.google.com/salusalo';
  c_a        text;
  c_b        text;
  c_c        text;
  v_l1       uuid := gen_random_uuid();
  v_l2       uuid := gen_random_uuid();
  v_l3       uuid := gen_random_uuid();
  v_path     text;
  v_r        public.reservations;
  r_c1       uuid;
  r_c2       uuid;
  r_b1       uuid;
  r_b2       uuid;
  v_conv     uuid;
  v_report   uuid;
  v_tok      text := 'safety-check-suspend-' || replace(gen_random_uuid()::text, '-', '');
  v_json     jsonb;
  v_text     text;
  v_bool     boolean;
  n          bigint;
  m          bigint;
  v_label    text;
  v_ok       boolean;
  v_note     text;
  a_label    text[] := '{}';
  a_ok       boolean[] := '{}';
  a_note     text[] := '{}';
  v_out      text := '';
  v_pass     integer := 0;
  v_fail     integer := 0;
  v_skip     integer := 0;
  v_ready    boolean := false;
  v_suspended boolean := false;
begin
  select * into v_a from public.users order by created_at desc limit 1;
  select * into v_b from public.users where id is distinct from v_a.id order by created_at desc limit 1;
  select * into v_c from public.users
  where id is distinct from v_a.id and id is distinct from v_b.id order by created_at desc limit 1;

  if v_c.id is not null then
    -- Known roles and alert switches for the check (undone by the final error).
    update public.users set role = 'RECIPIENT', suspended_at = null, suspension_reason = null
    where id in (v_b.id, v_c.id);
    update public.users set role = 'ADMIN', suspended_at = null, suspension_reason = null where id = v_a.id;
    insert into public.notification_preferences (user_id) values (v_c.id) on conflict (user_id) do nothing;
    update public.notification_preferences set reservation_alerts = true, message_alerts = true where user_id = v_c.id;
    c_a := json_build_object('sub', v_a.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
    c_b := json_build_object('sub', v_b.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
    c_c := json_build_object('sub', v_c.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
    v_path := v_b.firebase_uid || '/' || v_l1 || '/image.jpg';
  end if;

  -- 1
  v_label := 'Sanity: B posts two listings, C reserves both, C posts one and B reserves it twice (one picked up), a chat, a report, a token';
  v_ok := null; v_note := 'needs three accounts';
  if v_c.id is not null then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end, image_url)
      values (v_l1, v_b.id, 'Safety check: suspend 1', 'Food', 3, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours', v_path);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l2, v_b.id, 'Safety check: suspend 2', 'Food', 3, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours');
      perform set_config('request.jwt.claims', c_c, true);
      insert into public.food_posts (id, donor_id, name, category, quantity, unit, pickup_location,
        expiration_date, status, price, pickup_start, pickup_end)
      values (v_l3, v_c.id, 'Safety check: suspend 3', 'Food', 5, 'packs', 'Safety check plaza',
        now() + interval '1 day', 'AVAILABLE', 0, now() - interval '1 hour', now() + interval '3 hours');
      select * into v_r from public.create_reservation(v_l1, 1); r_c1 := v_r.id;
      select * into v_r from public.create_reservation(v_l2, 1); r_c2 := v_r.id;
      perform set_config('request.jwt.claims', c_b, true);
      select * into v_r from public.create_reservation(v_l3, 1); r_b1 := v_r.id;
      select * into v_r from public.create_reservation(v_l3, 1); r_b2 := v_r.id;
      select id into v_conv from public.start_listing_conversation(v_l3);
      perform set_config('request.jwt.claims', c_c, true);
      perform public.claim_reservation(r_b1);
      perform set_config('role', 'postgres', true);
      -- Listing 2 now looks expired, with C's reservation still open.
      update public.food_posts set pickup_start = now() - interval '2 hours', pickup_end = now() - interval '1 hour',
        expiration_date = now() - interval '30 minutes' where id = v_l2;
      insert into public.reports (reporter_id, reported_user_id, target_type, source, conversation_id, reason)
      values (v_c.id, v_b.id, 'USER', 'CHAT', v_conv, 'NO_SHOW') returning id into v_report;
      insert into public.push_tokens (token, user_id) values (v_tok, v_b.id);
      v_ready := r_c1 is not null and r_c2 is not null and r_b1 is not null and r_b2 is not null
                 and v_conv is not null and v_report is not null;
      v_ok := v_ready;
      v_note := case when v_ready then 'all fixtures made' else 'a fixture is missing' end;
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2
  v_label := 'admin_suspend_user refuses a caller who is not an admin';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_c, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_suspend_user(v_b.id, 'NO_SHOWS', 'Safety check');
      perform set_config('role', 'postgres', true);
      v_note := 'it suspended';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm; v_ok := sqlerrm = 'Admin access required';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 3
  v_label := 'An admin cannot suspend themselves';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_suspend_user(v_a.id, 'NO_SHOWS', 'Safety check');
      perform set_config('role', 'postgres', true);
      v_note := 'it suspended';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm; v_ok := sqlerrm = 'You can''t suspend your own account.';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 4
  v_label := 'Another admin cannot be suspended';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := false;
    begin
      update public.users set role = 'ADMIN' where id = v_c.id;
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_suspend_user(v_c.id, 'NO_SHOWS', 'Safety check');
      perform set_config('role', 'postgres', true);
      update public.users set role = 'RECIPIENT' where id = v_c.id;
      v_note := 'it suspended';
    exception when others then
      perform set_config('role', 'postgres', true);
      update public.users set role = 'RECIPIENT' where id = v_c.id;
      v_note := sqlerrm; v_ok := sqlerrm = 'Admins can''t be suspended.';
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5
  v_label := 'An unknown or missing reason is refused';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := true; v_note := '';
    foreach v_text in array array['MEAN', ''] loop
      begin
        perform set_config('request.jwt.claims', c_a, true);
        perform set_config('role', 'authenticated', true);
        perform public.admin_suspend_user(v_b.id, nullif(v_text, ''), 'Safety check');
        perform set_config('role', 'postgres', true);
        v_ok := false; v_note := v_note || format('[%s] suspended; ', v_text);
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'Choose a reason.' then v_ok := false; end if;
        v_note := v_note || format('[%s] %s; ', v_text, sqlerrm);
      end;
    end loop;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 6
  v_label := 'A note of only spaces, or of 1,001 characters, is refused';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := true; v_note := '';
    foreach v_text in array array['   ', repeat('x', 1001)] loop
      begin
        perform set_config('request.jwt.claims', c_a, true);
        perform set_config('role', 'authenticated', true);
        perform public.admin_suspend_user(v_b.id, 'NO_SHOWS', v_text);
        perform set_config('role', 'postgres', true);
        v_ok := false; v_note := v_note || format('[%s chars] suspended; ', length(v_text));
      exception when others then
        perform set_config('role', 'postgres', true);
        if sqlerrm <> 'Write a note (up to 1,000 characters).' then v_ok := false; end if;
        v_note := v_note || format('[%s chars] %s; ', length(v_text), sqlerrm);
      end;
    end loop;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 7
  v_label := 'Sanity: the admin suspends B; it reports 2 listings removed and 1 reservation cancelled';
  v_ok := null; v_note := 'needs the fixtures';
  if v_ready then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      v_json := public.admin_suspend_user(v_b.id, 'NO_SHOWS', 'Safety check');
      perform set_config('role', 'postgres', true);
      v_suspended := true;
      v_ok := (v_json ->> 'removedListings')::int = 2 and (v_json ->> 'cancelledReservations')::int = 1
              and (v_json ->> 'closedReports')::int >= 1;
      v_note := v_json::text;
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'Both of B''s listings are REMOVED (also the expired one) and C''s two reservations on them are LISTING_REMOVED';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    select count(*) into n from public.food_posts where id in (v_l1, v_l2) and status = 'REMOVED';
    select count(*) into m from public.reservations
    where id in (r_c1, r_c2) and status = 'CANCELLED' and cancel_reason = 'LISTING_REMOVED';
    v_ok := n = 2 and m = 2;
    v_note := format('%s listing(s) removed, %s reservation(s) cancelled', n, m);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9
  v_label := 'B''s open reservation is ACCOUNT_SUSPENDED with its quantity back; the picked-up one stays CLAIMED';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    select quantity into n from public.food_posts where id = v_l3;
    select count(*) into m from public.reservations
    where (id = r_b2 and status = 'CANCELLED' and cancel_reason = 'ACCOUNT_SUSPENDED')
       or (id = r_b1 and status = 'CLAIMED');
    v_ok := n = 4 and m = 2;
    v_note := format('listing 3 has %s (expected 4); %s of 2 reservations as expected', n, m);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 10
  v_label := 'C, the donor, gets "Reservation cancelled" with the exact text';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    select count(*) into n from public.notifications
    where user_id = v_c.id and reservation_id = r_b2 and type = 'RESERVATION_CANCELLED'
      and title = 'Reservation cancelled'
      and body = v_b.name || '''s reservation of Safety check: suspend 3 was cancelled. The quantity is available again.';
    v_ok := n = 1;
    v_note := format('%s matching alert(s)', n);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 11
  v_label := 'B has no push tokens; the report on B is RESOLVED as USER_SUSPENDED; the audit log has USER_SUSPENDED';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    select count(*) into n from public.push_tokens where user_id = v_b.id;
    select count(*) into m from public.reports
    where id = v_report and status = 'RESOLVED' and resolution_action = 'USER_SUSPENDED';
    v_bool := exists (select 1 from public.admin_audit_log
                      where action = 'USER_SUSPENDED' and target_type = 'USER' and target_id = v_b.id);
    v_ok := n = 0 and m = 1 and v_bool;
    v_note := format('%s token(s), report resolved %s, audit row %s', n, m = 1, v_bool);
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 12
  v_label := 'A second suspension of B is refused, and restoring C (not suspended) is refused';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_suspend_user(v_b.id, 'SPAM', 'Again');
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'suspended twice; ';
    exception when others then
      perform set_config('role', 'postgres', true);
      if sqlerrm <> 'This account is already suspended.' then v_ok := false; end if;
      v_note := sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_restore_user(v_c.id, null);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'restored C';
    exception when others then
      perform set_config('role', 'postgres', true);
      if sqlerrm <> 'This account isn''t suspended.' then v_ok := false; end if;
      v_note := v_note || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 13
  v_label := 'Suspended B sees neither its own profile row nor any listing';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.users where id = v_b.id;
      select count(*) into m from public.food_posts;
      perform set_config('role', 'postgres', true);
      v_ok := n = 0 and m = 0;
      v_note := format('%s profile row(s), %s listing(s)', n, m);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := true; v_note := 'refused: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 14
  v_label := 'Suspended B cannot reserve or save a push token';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    v_ok := true; v_note := '';
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      perform public.create_reservation(v_l3, 1);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := 'reserved; ';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := 'reserve: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      perform public.register_my_push_token(v_tok);
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'token saved';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := v_note || 'token: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 15
  v_label := 'Suspended B cannot read its own listing photo (can_read_food_image)';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      v_bool := public.can_read_food_image(v_path);
      perform set_config('role', 'postgres', true);
      v_ok := not v_bool;
      v_note := format('can_read_food_image = %s', v_bool);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := true; v_note := 'refused: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 16
  v_label := 'Suspended B can still ask get_my_account_status (suspended, NO_SHOWS) but cannot send a message';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      v_json := public.get_my_account_status();
      perform set_config('role', 'postgres', true);
      v_ok := (v_json ->> 'suspended')::boolean and v_json ->> 'reason' = 'NO_SHOWS';
      v_note := v_json::text || '; ';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := 'status: ' || sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_b, true);
      perform set_config('role', 'authenticated', true);
      perform public.send_message(v_conv, 'Safety check from B');
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'B sent a message';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := v_note || 'send: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 17
  v_label := 'C''s message to suspended B is refused with the exact text; after a restore B sees its row and is not suspended';
  v_ok := null; v_note := 'needs the suspension';
  if v_suspended then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_c, true);
      perform set_config('role', 'authenticated', true);
      perform public.send_message(v_conv, 'Safety check from C');
      perform set_config('role', 'postgres', true);
      v_note := 'C sent it; ';
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := sqlerrm = 'This person can''t receive messages right now.';
      v_note := sqlerrm || '; ';
    end;
    begin
      perform set_config('request.jwt.claims', c_a, true);
      perform set_config('role', 'authenticated', true);
      perform public.admin_restore_user(v_b.id, 'Safety check');
      perform set_config('request.jwt.claims', c_b, true);
      select count(*) into n from public.users where id = v_b.id;
      v_json := public.get_my_account_status();
      perform set_config('role', 'postgres', true);
      v_ok := v_ok and n = 1 and not (v_json ->> 'suspended')::boolean
              and exists (select 1 from public.admin_audit_log where action = 'USER_RESTORED' and target_id = v_b.id);
      v_note := v_note || format('after restore: %s row(s), %s', n, v_json::text);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_ok := false; v_note := v_note || 'restore: ' || sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 18
  v_label := 'anon cannot run any of the new functions';
  v_ok := false;
  begin
    v_ok := not has_function_privilege('anon', 'public.salusalo_caller_suspended()', 'execute')
        and not has_function_privilege('anon', 'public.get_my_account_status()', 'execute')
        and not has_function_privilege('anon', 'public.admin_suspend_user(uuid,text,text)', 'execute')
        and not has_function_privilege('anon', 'public.admin_restore_user(uuid,text)', 'execute')
        and not has_function_privilege('anon', 'public.admin_suspension_listings(uuid)', 'execute')
        and not has_function_privilege('authenticated', 'public.admin_suspension_listings(uuid)', 'execute');
    v_note := case when v_ok then 'none for anon; the internal helper none for clients' else 'a privilege is too wide' end;
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- ---------------------------------------------------------------------------
  -- Report. Raising an error here undoes everything above.
  -- ---------------------------------------------------------------------------

  for i in 1 .. coalesce(array_length(a_label, 1), 0) loop
    if a_ok[i] is null then
      v_skip := v_skip + 1;
    elsif a_ok[i] then
      v_pass := v_pass + 1;
    else
      v_fail := v_fail + 1;
    end if;
    v_out := v_out || format(E'\n%s %s. %s: %s',
      case when a_ok[i] is null then 'SKIP' when a_ok[i] then 'PASS' else 'FAIL' end,
      i, a_label[i], coalesce(a_note[i], '-'));
  end loop;

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nA: %s. B: %s. C: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_a.name, 'none'), coalesce(v_b.name, 'none'), coalesce(v_c.name, 'none'), v_out);
end $$;
```

- [ ] **Step 4: Run it before the migration and check that it fails**

Run: `bash "<scratchpad>/pg/run.sh" supabase/tests/admin_suspend_accounts_live_safety_check.sql`
Expected: `SAFETY CHECK FINISHED: … passed, … failed, … skipped.` with failures. The first line may also be an error such as `column "suspended_at" … does not exist`, because the check's set-up `update` names the new columns. Either way it must **not** report 18 passed.

- [ ] **Step 5: Commit**

```bash
git add supabase/tests/admin_suspend_accounts_live_safety_check.sql
git commit -m "Suspend accounts: live safety check (fails until the migration)"
```

---

### Task 2: The migration

**Files:**
- Create: `supabase/migrations/20261017_admin_suspend_accounts.sql`
- Modify: `supabase/README.md` (add the 20261017 entry after the 20261016 one)

**Interfaces:**
- Consumes: `admin_require()`, `admin_log(users, text, text, uuid, text, text)`, `admin_remove_listing(uuid, text) returns jsonb`, `notify_user(...)`, `admin_ms(timestamptz)`, and the `push_tokens` table (20261016).
- Produces:
  - `get_my_account_status() returns jsonb {"suspended": boolean, "reason": text|null}`;
  - `admin_suspend_user(p_user uuid, p_reason text, p_note text) returns jsonb {"removedListings", "cancelledReservations", "closedReports"}`;
  - `admin_restore_user(p_user uuid, p_note text) returns jsonb {"restored": true}`;
  - `admin_users` accepts filter `SUSPENDED`;
  - `admin_user_json` adds `suspendedAt` (ms or null), `suspensionReason` (code or null), `suspension` ({`byName`, `at`, `details`} or null) and `suspendCounts` ({`listings`, `reservations`});
  - `reservations.cancel_reason` may be `ACCOUNT_SUSPENDED`;
  - `reports.resolution_action` may be `USER_SUSPENDED`.

  Note: `suspendCounts` is the spec's "activeListings and openReservations". It sits under its own key so it doesn't clash with `admin_user`'s existing `stats.activeListings`.

- [ ] **Step 1: Write the migration**

Create `supabase/migrations/20261017_admin_suspend_accounts.sql`:

```sql
-- Salu-Salo: suspend accounts (Phase 6; PROJECT_STATUS.md §36).
--
-- Run by hand in the Supabase SQL Editor, after 20261016_push_notifications.sql.
-- Every app build keeps working for accounts that aren't suspended. Run it before
-- installing the §36 build (which calls get_my_account_status). Then run
-- supabase/tests/admin_suspend_accounts_live_safety_check.sql (expect 18 passed).
--
-- A suspended account is refused everywhere by one check: is_salusalo_firebase_jwt(),
-- which every client-reachable policy and function already uses, now also requires
-- that the caller isn't suspended. get_my_account_status() is the only function that
-- skips it, so the app can show why.

-- ---------------------------------------------------------------------------
-- 1. Where a suspension is kept. The admin's note and name go only into the
-- audit log; nothing private is in the person's own row.
-- ---------------------------------------------------------------------------

alter table public.users
    add column if not exists suspended_at timestamptz,
    add column if not exists suspension_reason text;
alter table public.users drop constraint if exists users_suspension_check;
alter table public.users add constraint users_suspension_check check (
    (suspended_at is null and suspension_reason is null)
    or (suspended_at is not null
        and suspension_reason in ('NO_SHOWS', 'UNSAFE_FOOD', 'HARASSMENT', 'SPAM', 'OTHER')));

-- ---------------------------------------------------------------------------
-- 2. The lock.
-- ---------------------------------------------------------------------------

-- The caller's own row only, by the token. SECURITY DEFINER because the users
-- policies themselves call is_salusalo_firebase_jwt() (no recursion through RLS).
-- Executable by the client roles because policies run as the client.
create or replace function public.salusalo_caller_suspended()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (select 1 from public.users u
                   where u.firebase_uid = auth.jwt() ->> 'sub' and u.suspended_at is not null);
$$;
revoke all on function public.salusalo_caller_suspended() from public;
revoke all on function public.salusalo_caller_suspended() from anon;
grant execute on function public.salusalo_caller_suspended() to authenticated, service_role;

-- The 20260921 check plus "not suspended". Same signature: every policy and
-- function that uses it now refuses a suspended account. Keep this check.
create or replace function public.is_salusalo_firebase_jwt()
returns boolean
language sql
stable
set search_path = ''
as $$
    select coalesce(
        auth.jwt() ->> 'iss' = 'https://securetoken.google.com/salusalo'
        and auth.jwt() ->> 'aud' = 'salusalo'
        and auth.jwt() ->> 'role' = 'authenticated',
        false)
       and not public.salusalo_caller_suspended();
$$;

-- The one thing a suspended account can still ask: whether it is suspended, and why.
-- It checks the token itself, NOT through is_salusalo_firebase_jwt().
create or replace function public.get_my_account_status()
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_user public.users;
begin
    if not coalesce(auth.jwt() ->> 'iss' = 'https://securetoken.google.com/salusalo'
                    and auth.jwt() ->> 'aud' = 'salusalo'
                    and auth.jwt() ->> 'role' = 'authenticated', false) then
        raise exception 'Not authenticated' using errcode = '28000';
    end if;
    select * into v_user from public.users u where u.firebase_uid = auth.jwt() ->> 'sub';
    return jsonb_build_object('suspended', v_user.suspended_at is not null,
                              'reason', v_user.suspension_reason);
end; $$;
revoke all on function public.get_my_account_status() from public;
revoke all on function public.get_my_account_status() from anon;
grant execute on function public.get_my_account_status() to authenticated;

-- ---------------------------------------------------------------------------
-- 3. Allowed values.
-- ---------------------------------------------------------------------------

alter table public.reservations drop constraint if exists reservations_cancel_reason_check;
alter table public.reservations add constraint reservations_cancel_reason_check check (
    cancel_reason is null
    or (cancel_reason in ('LISTING_REMOVED', 'DONOR_RELEASED', 'PICKUP_EXPIRED', 'ACCOUNT_SUSPENDED')
        and status = 'CANCELLED'));

alter table public.reports drop constraint if exists reports_resolution_check;
alter table public.reports add constraint reports_resolution_check check (
    (status = 'OPEN' and resolved_by is null and resolved_at is null
        and resolution_action is null and resolution_note is null)
    or (status = 'RESOLVED' and resolved_at is not null and resolution_action in ('LISTING_REMOVED', 'USER_SUSPENDED'))
    or (status = 'DISMISSED' and resolved_at is not null and resolution_action = 'DISMISSED'));

alter table public.admin_audit_log drop constraint if exists admin_audit_log_action_check;
alter table public.admin_audit_log add constraint admin_audit_log_action_check check (action in
    ('LISTING_REMOVED', 'REPORTS_DISMISSED', 'LISTING_REVIEWED', 'REPORT_NOTE_ADDED', 'ANNOUNCEMENT_SENT',
     'USER_SUSPENDED', 'USER_RESTORED'));
alter table public.admin_audit_log drop constraint if exists admin_audit_log_target_type_check;
alter table public.admin_audit_log add constraint admin_audit_log_target_type_check
    check (target_type in ('LISTING', 'REPORT', 'ANNOUNCEMENT', 'USER'));

-- ---------------------------------------------------------------------------
-- 4. Reservation alerts: the 20261011 function plus the ACCOUNT_SUSPENDED branch
-- (the donor only; nobody is told the account was suspended).
-- ---------------------------------------------------------------------------

create or replace function public.notify_reservation_change()
returns trigger
language plpgsql security definer set search_path = public as $$
declare v_post public.food_posts; v_recipient_name text; v_donor_name text;
begin
  if tg_op = 'UPDATE' and new.status = 'CANCELLED' and new.cancel_reason = 'LISTING_REMOVED' then
    return new;
  end if;
  select * into v_post from public.food_posts where id = new.food_post_id;
  if not found then return new; end if;
  if tg_op = 'UPDATE' and new.status = 'CANCELLED' and new.cancel_reason = 'DONOR_RELEASED' then
    if new.status is distinct from old.status then
      select name into v_donor_name from public.users where id = v_post.donor_id;
      perform public.notify_user(new.recipient_id, 'RESERVATION_CANCELLED', 'Reservation released',
        coalesce(v_donor_name, 'The donor') || ' released your reservation of ' || v_post.name || '. It is no longer held for you.',
        v_post.id, null, new.id);
    end if;
    return new;
  end if;
  select name into v_recipient_name from public.users where id = new.recipient_id;
  if tg_op = 'UPDATE' and new.status = 'CANCELLED' and new.cancel_reason = 'ACCOUNT_SUSPENDED' then
    if new.status is distinct from old.status then
      perform public.notify_user(v_post.donor_id, 'RESERVATION_CANCELLED', 'Reservation cancelled',
        coalesce(v_recipient_name, 'Someone') || '''s reservation of ' || v_post.name ||
        ' was cancelled. The quantity is available again.',
        v_post.id, null, new.id);
    end if;
    return new;
  end if;
  if tg_op = 'UPDATE' and new.status = 'CANCELLED' and new.cancel_reason = 'PICKUP_EXPIRED' then
    if new.status is distinct from old.status then
      perform public.notify_user(new.recipient_id, 'RESERVATION_CANCELLED', 'Reservation expired',
        'Your reservation of ' || v_post.name || ' expired because it wasn''t picked up in time.',
        v_post.id, null, new.id);
      perform public.notify_user(v_post.donor_id, 'RESERVATION_CANCELLED', 'Reservation expired',
        coalesce(v_recipient_name, 'Someone') || '''s reservation of ' || v_post.name ||
        ' expired because it wasn''t picked up in time. The quantity is available again.',
        v_post.id, null, new.id);
    end if;
    return new;
  end if;
  if tg_op = 'INSERT' then
    perform public.notify_user(v_post.donor_id, 'RESERVATION_CREATED', 'New reservation',
      coalesce(v_recipient_name, 'Someone') || ' reserved ' || new.quantity || ' ' || v_post.unit || ' of ' || v_post.name || '.',
      v_post.id, null, new.id);
  elsif new.status is distinct from old.status then
    if new.status = 'CANCELLED' then
      perform public.notify_user(v_post.donor_id, 'RESERVATION_CANCELLED', 'Reservation cancelled',
        coalesce(v_recipient_name, 'Someone') || ' cancelled their reservation of ' || v_post.name || '. The quantity is available again.',
        v_post.id, null, new.id);
    elsif new.status = 'CLAIMED' then
      perform public.notify_user(new.recipient_id, 'RESERVATION_CLAIMED', 'Reservation claimed',
        'The donor marked your reservation of ' || v_post.name || ' as claimed.',
        v_post.id, null, new.id);
    end if;
  end if;
  return new;
end; $$;
revoke all on function public.notify_reservation_change() from public;
revoke all on function public.notify_reservation_change() from anon;
revoke all on function public.notify_reservation_change() from authenticated;

-- ---------------------------------------------------------------------------
-- 5. send_message: the 20261009 function (rate limit kept) plus the refusal
-- when the other participant is suspended.
-- ---------------------------------------------------------------------------

create or replace function public.send_message(p_conversation_id uuid, p_body text)
returns public.messages
language plpgsql security definer set search_path = public as $$
declare v_profile uuid; v_conversation public.conversations; v_message public.messages;
        v_body text; v_sender_name text; v_food_name text; v_other uuid;
begin
  if not public.is_salusalo_firebase_jwt() then raise exception 'Not authenticated'; end if;
  v_profile := public.current_salusalo_profile_id();
  if v_profile is null then raise exception 'Profile not found'; end if;
  v_body := trim(coalesce(p_body, ''));
  if length(v_body) = 0 then raise exception 'Type a message first'; end if;
  if length(v_body) > 2000 then raise exception 'Messages can be at most 2000 characters'; end if;
  select * into v_conversation from public.conversations where id = p_conversation_id for update;
  if not found then raise exception 'Conversation not found'; end if;
  if v_profile <> v_conversation.recipient_id and v_profile <> v_conversation.donor_id then
    raise exception 'You are not part of this conversation';
  end if;
  v_other := case when v_profile = v_conversation.donor_id then v_conversation.recipient_id else v_conversation.donor_id end;
  if exists (select 1 from public.users u where u.id = v_other and u.suspended_at is not null) then
    raise exception 'This person can''t receive messages right now.';
  end if;
  perform pg_advisory_xact_lock(hashtextextended('salusalo-message:' || v_profile::text, 0));
  if (select count(*) from public.messages m
      where m.sender_id = v_profile and m.created_at > now() - interval '1 minute') >= 20 then
    raise exception 'You are sending messages too quickly. Wait a minute and try again.';
  end if;
  insert into public.messages(conversation_id, sender_id, body)
  values (p_conversation_id, v_profile, v_body) returning * into v_message;
  update public.conversations set
    last_message_at = v_message.created_at,
    recipient_last_read_at = case when v_profile = recipient_id then v_message.created_at else recipient_last_read_at end,
    donor_last_read_at = case when v_profile = donor_id then v_message.created_at else donor_last_read_at end
  where id = p_conversation_id;
  select name into v_sender_name from public.users where id = v_profile;
  select name into v_food_name from public.food_posts where id = v_conversation.food_post_id;
  perform public.notify_user(v_other, 'MESSAGE',
    'New message from ' || coalesce(v_sender_name, 'a Salu-Salo user'),
    coalesce(v_food_name || ': ', '') || v_body,
    v_conversation.food_post_id, p_conversation_id, null);
  return v_message;
end; $$;
revoke all on function public.send_message(uuid, text) from public;
revoke all on function public.send_message(uuid, text) from anon;
grant execute on function public.send_message(uuid, text) to authenticated;

-- ---------------------------------------------------------------------------
-- 6. The admin side.
-- ---------------------------------------------------------------------------

-- What a suspension closes (and the dialog counts): listings not REMOVED that are
-- still up, or still have an open reservation. Internal; no client may run it.
create or replace function public.admin_suspension_listings(p_user uuid)
returns setof uuid
language sql
stable
security definer
set search_path = public
as $$
    select p.id from public.food_posts p
    where p.donor_id = p_user and p.status <> 'REMOVED'
      and ((p.expiration_date > now() and (p.pickup_end is null or p.pickup_end > now()))
           or exists (select 1 from public.reservations r where r.food_post_id = p.id and r.status = 'RESERVED'));
$$;

create or replace function public.admin_suspend_user(p_user uuid, p_reason text, p_note text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_note text := nullif(btrim(coalesce(p_note, '')), '');
    v_user public.users;
    v_post uuid;
    v_res public.reservations;
    v_removed integer := 0;
    v_cancelled integer := 0;
    v_closed integer := 0;
    v_n integer;
    v_result jsonb;
begin
    select * into v_user from public.users u where u.id = p_user for update;
    if not found then raise exception 'User not found'; end if;
    if v_user.id = v_admin.id then raise exception 'You can''t suspend your own account.'; end if;
    if v_user.role = 'ADMIN' then raise exception 'Admins can''t be suspended.'; end if;
    if v_user.suspended_at is not null then raise exception 'This account is already suspended.'; end if;
    if p_reason is null or p_reason not in ('NO_SHOWS', 'UNSAFE_FOOD', 'HARASSMENT', 'SPAM', 'OTHER') then
        raise exception 'Choose a reason.';
    end if;
    if v_note is null or char_length(v_note) > 1000 then
        raise exception 'Write a note (up to 1,000 characters).';
    end if;

    update public.users u set suspended_at = now(), suspension_reason = p_reason where u.id = p_user;
    -- Before any alert below, so no pop-up reaches them.
    delete from public.push_tokens t where t.user_id = p_user;

    -- Their listings: the admin removal (alerts to recipients, reports resolved).
    for v_post in select l from public.admin_suspension_listings(p_user) l order by l loop
        v_result := public.admin_remove_listing(v_post, 'Account suspended');
        v_removed := v_removed + 1;
        v_closed := v_closed + coalesce((v_result ->> 'closedReports')::integer, 0);
    end loop;

    -- The reservations they hold: cancelled, quantity back (as cancel_reservation:
    -- the reservation first, then its listing). The trigger alerts each donor.
    for v_res in
        select * from public.reservations r
        where r.recipient_id = p_user and r.status = 'RESERVED'
        order by r.id
        for update
    loop
        perform 1 from public.food_posts p where p.id = v_res.food_post_id for update;
        update public.reservations r set status = 'CANCELLED', cancel_reason = 'ACCOUNT_SUSPENDED'
        where r.id = v_res.id;
        update public.food_posts p set quantity = p.quantity + v_res.quantity, status = 'AVAILABLE'
        where p.id = v_res.food_post_id and p.status <> 'REMOVED';
        v_cancelled := v_cancelled + 1;
    end loop;

    update public.reports rp
    set status = 'RESOLVED', resolved_by = v_admin.id, resolved_at = now(),
        resolution_action = 'USER_SUSPENDED', resolution_note = v_note
    where rp.target_type = 'USER' and rp.reported_user_id = p_user and rp.status = 'OPEN';
    get diagnostics v_n = row_count;
    v_closed := v_closed + v_n;

    perform public.admin_log(v_admin, 'USER_SUSPENDED', 'USER', v_user.id, v_user.name,
        case p_reason when 'NO_SHOWS' then 'Repeated no-shows at pickup'
                      when 'UNSAFE_FOOD' then 'Unsafe or misleading food'
                      when 'HARASSMENT' then 'Harassment or abuse'
                      when 'SPAM' then 'Spam or fake account'
                      else 'Breaking the community rules' end || ': ' || v_note);
    return jsonb_build_object('removedListings', v_removed, 'cancelledReservations', v_cancelled,
                              'closedReports', v_closed);
end; $$;

create or replace function public.admin_restore_user(p_user uuid, p_note text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_note text := coalesce(nullif(btrim(coalesce(p_note, '')), ''), 'Restored.');
    v_user public.users;
begin
    if char_length(v_note) > 1000 then
        raise exception 'Keep the note under 1,000 characters.';
    end if;
    select * into v_user from public.users u where u.id = p_user for update;
    if not found then raise exception 'User not found'; end if;
    if v_user.suspended_at is null then raise exception 'This account isn''t suspended.'; end if;
    update public.users u set suspended_at = null, suspension_reason = null where u.id = p_user;
    perform public.admin_log(v_admin, 'USER_RESTORED', 'USER', v_user.id, v_user.name, v_note);
    return jsonb_build_object('restored', true);
end; $$;

-- The 20261012 filter plus SUSPENDED.
create or replace function public.admin_user_matches(u public.users, p_like text, p_filter text)
returns boolean language sql stable security definer set search_path = public as $$
    select (p_like is null or u.name ilike p_like or u.email ilike p_like)
       and case coalesce(p_filter, 'ALL')
             when 'ADMIN' then u.role = 'ADMIN'
             when 'DONOR' then exists (select 1 from public.food_posts p where p.donor_id = u.id)
             when 'RECIPIENT' then u.role <> 'ADMIN'
                  and not exists (select 1 from public.food_posts p where p.donor_id = u.id)
             when 'SUSPENDED' then u.suspended_at is not null
             else true end;
$$;

-- The 20261012 JSON plus the suspension and the counts the Suspend dialog shows.
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
                        where r.status = 'OPEN' and r.target_type = 'USER' and r.reported_user_id = u.id),
        'suspendedAt', public.admin_ms(u.suspended_at),
        'suspensionReason', u.suspension_reason,
        'suspension', case when u.suspended_at is null then null else (
            select jsonb_build_object('byName', a.admin_name, 'at', public.admin_ms(a.created_at), 'details', a.details)
            from public.admin_audit_log a
            where a.target_id = u.id and a.action = 'USER_SUSPENDED'
            order by a.created_at desc limit 1) end,
        'suspendCounts', jsonb_build_object(
            'listings', (select count(*) from public.admin_suspension_listings(u.id)),
            'reservations', (select count(*) from public.reservations r
                             where r.recipient_id = u.id and r.status = 'RESERVED')));
$$;

-- ---------------------------------------------------------------------------
-- 7. Privileges.
-- ---------------------------------------------------------------------------
do $$
declare f text;
begin
    foreach f in array array['admin_suspend_user(uuid,text,text)', 'admin_restore_user(uuid,text)'] loop
        execute format('revoke all on function public.%s from public', f);
        execute format('revoke all on function public.%s from anon', f);
        execute format('grant execute on function public.%s to authenticated', f);
    end loop;
    foreach f in array array['admin_suspension_listings(uuid)', 'admin_user_matches(public.users,text,text)',
                             'admin_user_json(public.users)'] loop
        execute format('revoke all on function public.%s from public', f);
        execute format('revoke all on function public.%s from anon', f);
        execute format('revoke all on function public.%s from authenticated', f);
    end loop;
end $$;
```

- [ ] **Step 2: Run the new check and the earlier ones on a fresh local database**

Run (one line):

```bash
bash "<scratchpad>/pg/run.sh" supabase/tests/admin_suspend_accounts_live_safety_check.sql supabase/tests/phase3_live_safety_check.sql supabase/tests/phase4_live_safety_check.sql supabase/tests/phase5_live_safety_check.sql supabase/tests/phase5_maps_live_safety_check.sql supabase/tests/admin_dashboard_live_safety_check.sql supabase/tests/admin_announcements_live_safety_check.sql supabase/tests/push_notifications_live_safety_check.sql supabase/tests/bugfix_anon_grants_check.sql
```

Expected:
- `migrations: 26 applied`;
- the new check `SAFETY CHECK FINISHED: 18 passed, 0 failed, 0 skipped.`;
- each earlier check reports the same pass count as before this migration (run the same command on `f04c77c` with `git stash` if a count looks different);
- `bugfix_anon_grants_check.sql` returns PASS rows only.

`phase5_live_safety_check` may SKIP checks that need claimed reservations; skips are not failures.

- [ ] **Step 3: Apply it twice**

Run: `"$BIN/psql.exe" -h localhost -p 5434 -U postgres -d salusalo_test -v ON_ERROR_STOP=1 -q -f supabase/migrations/20261017_admin_suspend_accounts.sql && echo second-apply-ok`
Expected: `second-apply-ok`.

- [ ] **Step 4: Add the README entry**

In `supabase/README.md`, after the 20261016 (push notifications) entry, add:

```markdown
### 20261017 — Suspend accounts (§36)

Run after 20261016, before installing the §36 app build. Builds before §36 keep
working for everyone who isn't suspended.

1. SQL Editor → paste `migrations/20261017_admin_suspend_accounts.sql` → Run ("Success. No rows returned").
2. Paste `tests/admin_suspend_accounts_live_safety_check.sql` → Run. It ends with an error on purpose;
   the text must say `SAFETY CHECK FINISHED: 18 passed, 0 failed, 0 skipped.`

It adds `users.suspended_at` and `users.suspension_reason`, makes `is_salusalo_firebase_jwt()` refuse
a suspended account everywhere, adds `get_my_account_status()` (the only function that skips that),
`admin_suspend_user()` and `admin_restore_user()`, the `ACCOUNT_SUSPENDED` cancel reason, the
`USER_SUSPENDED` report resolution, and the audit actions `USER_SUSPENDED` and `USER_RESTORED`.
```

- [ ] **Step 5: Commit**

```bash
git add supabase/migrations/20261017_admin_suspend_accounts.sql supabase/README.md
git commit -m "Suspend accounts: migration 20261017 (one lock in is_salusalo_firebase_jwt)"
```

---

### Task 3: App model and repository (TDD)

**Files:**
- Create: `app/src/main/java/com/example/salusalo/model/Suspension.kt`
- Create: `app/src/test/java/com/example/salusalo/SuspensionTest.kt`
- Modify: `app/src/main/java/com/example/salusalo/data/UserRepository.kt`. Add `getMyAccountStatus()` after `syncEmailVerified()` (around line 199), and `Suspended` in `ProfileException` (line 341).

**Interfaces:**
- Consumes: `get_my_account_status()` from Task 2.
- Produces:
  - `const val SUPPORT_EMAIL: String`;
  - `@Serializable data class AccountStatus(val suspended: Boolean = false, val reason: String? = null)`;
  - `fun suspensionReasonLabel(code: String?): String`;
  - `enum class StatusChange { NONE, SHOW_SUSPENDED, RESUME_SETUP }`;
  - `fun statusChange(onSuspendedScreen: Boolean, status: AccountStatus?): StatusChange`;
  - `suspend fun UserRepository.getMyAccountStatus(): AccountStatus?`;
  - `class ProfileException.Suspended(val reason: String?)`.

- [ ] **Step 1: The support address**

The developer gave the support address on 2026-10-17: `aiiiiii12241@gmail.com`. Step 4 uses it as `SUPPORT_EMAIL`, exactly as written.

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/com/example/salusalo/SuspensionTest.kt`:

```kotlin
package com.example.salusalo

import com.example.salusalo.model.AccountStatus
import com.example.salusalo.model.StatusChange
import com.example.salusalo.model.statusChange
import com.example.salusalo.model.suspensionReasonLabel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §36: the suspended screen's wording and when the app moves to or from it. */
class SuspensionTest {
    @Test
    fun eachCodeHasItsWording() {
        assertEquals("Repeated no-shows at pickup", suspensionReasonLabel("NO_SHOWS"))
        assertEquals("Unsafe or misleading food", suspensionReasonLabel("UNSAFE_FOOD"))
        assertEquals("Harassment or abuse", suspensionReasonLabel("HARASSMENT"))
        assertEquals("Spam or fake account", suspensionReasonLabel("SPAM"))
        assertEquals("Breaking the community rules", suspensionReasonLabel("OTHER"))
    }

    @Test
    fun unknownOrMissingCodeIsTheGeneralRule() {
        assertEquals("Breaking the community rules", suspensionReasonLabel("SOMETHING_NEW"))
        assertEquals("Breaking the community rules", suspensionReasonLabel(null))
    }

    @Test
    fun statusDecodesBothAnswers() {
        val suspended = Json.decodeFromString(AccountStatus.serializer(), """{"suspended":true,"reason":"SPAM"}""")
        assertTrue(suspended.suspended)
        assertEquals("SPAM", suspended.reason)
        val active = Json.decodeFromString(AccountStatus.serializer(), """{"suspended":false,"reason":null}""")
        assertFalse(active.suspended)
        assertNull(active.reason)
    }

    @Test
    fun noAnswerChangesNothing() {
        assertEquals(StatusChange.NONE, statusChange(onSuspendedScreen = false, status = null))
        assertEquals(StatusChange.NONE, statusChange(onSuspendedScreen = true, status = null))
    }

    @Test
    fun suspendedWhileOnHomeShowsTheScreen() {
        assertEquals(StatusChange.SHOW_SUSPENDED, statusChange(false, AccountStatus(true, "NO_SHOWS")))
    }

    @Test
    fun restoredWhileOnTheScreenResumesSetup() {
        assertEquals(StatusChange.RESUME_SETUP, statusChange(true, AccountStatus(false, null)))
    }

    @Test
    fun sameStateChangesNothing() {
        assertEquals(StatusChange.NONE, statusChange(true, AccountStatus(true, "SPAM")))
        assertEquals(StatusChange.NONE, statusChange(false, AccountStatus(false, null)))
    }
}
```

- [ ] **Step 3: Run it to check that it fails**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:testDebugUnitTest --tests "com.example.salusalo.SuspensionTest" --offline`
Expected: compilation FAILS with `Unresolved reference 'AccountStatus'` (and the other names).

- [ ] **Step 4: Write the model**

Create `app/src/main/java/com/example/salusalo/model/Suspension.kt`:

```kotlin
package com.example.salusalo.model

import kotlinx.serialization.Serializable

/** The appeal address on the suspended screen (§36), chosen by the developer. */
const val SUPPORT_EMAIL = "aiiiiii12241@gmail.com"

/** get_my_account_status (§36): the one question a suspended account can still ask. */
@Serializable
data class AccountStatus(val suspended: Boolean = false, val reason: String? = null)

/** What the suspended person sees; the codes are admin_suspend_user's (never rename one). */
fun suspensionReasonLabel(code: String?): String = when (code) {
    "NO_SHOWS" -> "Repeated no-shows at pickup"
    "UNSAFE_FOOD" -> "Unsafe or misleading food"
    "HARASSMENT" -> "Harassment or abuse"
    "SPAM" -> "Spam or fake account"
    else -> "Breaking the community rules"
}

/** What the app does with an answer when it comes back to the screen. */
enum class StatusChange { NONE, SHOW_SUSPENDED, RESUME_SETUP }

/**
 * No answer (offline, or the migration not applied yet) changes nothing: the
 * server still refuses a suspended account everything else.
 */
fun statusChange(onSuspendedScreen: Boolean, status: AccountStatus?): StatusChange = when {
    status == null -> StatusChange.NONE
    status.suspended && !onSuspendedScreen -> StatusChange.SHOW_SUSPENDED
    !status.suspended && onSuspendedScreen -> StatusChange.RESUME_SETUP
    else -> StatusChange.NONE
}
```

- [ ] **Step 5: Run the test to check that it passes**

Run: the same command as Step 3.
Expected: `SuspensionTest` 7 tests PASS.

- [ ] **Step 6: Add the repository call and the exception**

In `data/UserRepository.kt`, add the import `import com.example.salusalo.model.AccountStatus`. After `syncEmailVerified()` add:

```kotlin
    /**
     * §36: whether this account is suspended, and why; the one call a suspended
     * account can still make. Null when there's no answer (offline, or the
     * migration not applied yet): the caller carries on, as the server still
     * refuses a suspended account everything else. The error is never shown or logged.
     */
    suspend fun getMyAccountStatus(): AccountStatus? = try {
        supabase.postgrest.rpc("get_my_account_status").decodeAs<AccountStatus>()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }
```

In `sealed class ProfileException`, after `EmailInUse`, add:

```kotlin
    /** §36: an admin suspended this account; [reason] is the code it shows. */
    class Suspended(val reason: String?) : ProfileException("This account is suspended.")
```

`profileErrorMessage()` needs no change: its `else` covers it, and the setup handles `Suspended` before using it (Task 4).

- [ ] **Step 7: Run all unit tests**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:testDebugUnitTest --offline`
Expected: BUILD SUCCESSFUL; 114 tests (107 + 7), 0 failures.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/salusalo/model/Suspension.kt app/src/test/java/com/example/salusalo/SuspensionTest.kt app/src/main/java/com/example/salusalo/data/UserRepository.kt
git commit -m "Suspend accounts: account status model, reason labels and repository call"
```

---

### Task 4: App screen and when it shows

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/MainActivity.kt`:
  - the state block after `var canRelink` (around line 254);
  - `openAuthenticatedHome()` (lines 304-353);
  - after `LaunchedEffect(authUid)` (line 433);
  - the `when (currentScreen)` (line 505);
  - a new composable after `StartupScreen()` (around line 830).

**Interfaces:**
- Consumes: `UserRepository.getMyAccountStatus()`, `ProfileException.Suspended`, `statusChange()`, `StatusChange`, `suspensionReasonLabel()` and `SUPPORT_EMAIL` (Task 3).
- Produces: the screen value `"suspended"`.

Note on the spec (§2): the spec puts the return check in `HomeScreen` through an `onSuspended` callback, and a second check on the suspended screen. This plan uses **one** `LifecycleStartEffect` in `SaluSaloApp` that covers both screens (`statusChange()` decides). The behaviour is the same, it lives in one place, and `HomeScreen`'s signature doesn't change.

- [ ] **Step 1: Hold the reason**

After `var canRelink by rememberSaveable { mutableStateOf(false) }` add:

```kotlin
    // The reason code on the suspended screen (§36); saved with the screen.
    var suspendedReason by rememberSaveable { mutableStateOf<String?>(null) }
```

- [ ] **Step 2: Check at setup**

In `openAuthenticatedHome()`, replace:

```kotlin
                profileRepository.ensureSupabaseRole()
                profileRepository.loadOrCreateCurrentProfile(displayNameOverride)
```

with:

```kotlin
                profileRepository.ensureSupabaseRole()
                // A suspended account sees only the suspended screen (§36). No answer
                // (offline, or the migration not applied) carries on as before.
                profileRepository.getMyAccountStatus()?.takeIf { it.suspended }?.let {
                    throw ProfileException.Suspended(it.reason)
                }
                profileRepository.loadOrCreateCurrentProfile(displayNameOverride)
```

In the same function, replace:

```kotlin
            }.onFailure { error ->
                // Nothing to report when an overlapping attempt already reached Home.
                if (currentProfile == null) {
```

with:

```kotlin
            }.onFailure { error ->
                if (error is ProfileException.Suspended) {
                    message = ""
                    canRetrySetup = false
                    canRelink = false
                    currentProfile = null
                    suspendedReason = error.reason
                    screen = "suspended"
                // Nothing to report when an overlapping attempt already reached Home.
                } else if (currentProfile == null) {
```

- [ ] **Step 3: Check again when the app comes back, and clear the shade**

After the block `LaunchedEffect(authUid) { … }` add:

```kotlin
    // §36: a suspension, or a restore, made while the app was away takes effect
    // when it comes back to the screen. No answer changes nothing.
    var appWasStopped by remember { mutableStateOf(false) }
    LifecycleStartEffect(profileRepository) {
        if (appWasStopped && firebaseAuth.currentUser != null && (screen == "home" || screen == "suspended")) {
            appScope.launch {
                val status = profileRepository.getMyAccountStatus()
                // Log out (or a sign-out) may have happened meanwhile.
                if (firebaseAuth.currentUser == null || (screen != "home" && screen != "suspended")) return@launch
                when (statusChange(onSuspendedScreen = screen == "suspended", status = status)) {
                    StatusChange.SHOW_SUSPENDED -> {
                        currentProfile = null
                        suspendedReason = status?.reason
                        screen = "suspended"
                    }
                    StatusChange.RESUME_SETUP -> openAuthenticatedHome()
                    StatusChange.NONE -> Unit
                }
            }
        }
        appWasStopped = false
        onStopOrDispose { appWasStopped = true }
    }
    // The suspended screen leaves no Salu-Salo pop-ups in the shade.
    LaunchedEffect(screen) {
        if (screen == "suspended") NotificationManagerCompat.from(context).cancelAll()
    }
```

`SHOW_SUSPENDED` sets `currentProfile = null`. That disposes `HomeScreen`, so the ViewModels' `clearSession()` runs as it does after Log out.

- [ ] **Step 4: Draw the screen**

In `when (currentScreen) {`, before `else -> SignInScreen(`, add:

```kotlin
                    "suspended" -> SuspendedScreen(
                        reason = suspendedReason,
                        onLogout = { logOut() }
                    )
```

After the `StartupScreen()` composable, add:

```kotlin
/**
 * All a suspended account sees after sign-in (§36): why, where to appeal, and Log out.
 * The server refuses it everything else.
 */
@Composable
private fun SuspendedScreen(reason: String?, onLogout: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaluColors.Cream)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SaluLogo(size = 88.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            "Your account has been suspended",
            style = SaluText.ScreenTitle,
            color = SaluColors.DarkGreen,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Reason: " + suspensionReasonLabel(reason),
            style = SaluText.Body,
            color = SaluColors.DarkGreen,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "If you think this is a mistake, email",
            style = SaluText.Supporting,
            color = SaluColors.Gray,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = {
            // No mail app: nothing happens; the address stays readable on screen.
            runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))) }
        }) {
            Text(SUPPORT_EMAIL, style = SaluText.Body, color = SaluColors.Green)
        }
        Spacer(Modifier.height(24.dp))
        SaluPrimaryButton(text = "Log out", onClick = onLogout)
    }
}
```

Add any imports the compiler reports as missing:
- `android.content.Intent`, `android.net.Uri`;
- `androidx.core.app.NotificationManagerCompat`;
- `androidx.compose.material3.TextButton`;
- `androidx.compose.foundation.verticalScroll`, `androidx.compose.foundation.rememberScrollState`;
- `com.example.salusalo.model.StatusChange`, `statusChange`, `suspensionReasonLabel`, `SUPPORT_EMAIL`.

System Back on this screen closes the app: `BackHandler` handles only `signup` and `forgot`, so nothing to add.

- [ ] **Step 5: Build, test and lint**

Run: `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`
Expected: BUILD SUCCESSFUL; 114 tests pass; lint `0 errors` (warnings 25, or more only if a new one is on a line from this task, which must then be fixed).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/salusalo/MainActivity.kt
git commit -m "Suspend accounts: the suspended screen, checked at sign-in and when the app comes back"
```

---

### Task 5: Dashboard data layer and timeline (TDD with Node)

**Files:**
- Modify: `admin/js/data.js` (the `text` object, and the return object after `addReportNote`)
- Modify: `admin/js/timeline.js:9-14`
- Test: `admin/tests/data.test.js`, `admin/tests/timeline.test.js`

**Interfaces:**
- Consumes: `admin_suspend_user`, `admin_restore_user`, `admin_users` with `SUSPENDED` (Task 2).
- Produces:
  - `SaluData.text.suspension` (code → wording);
  - `SaluData.text.actions.USER_SUSPENDED = 'Account suspended'` and `USER_RESTORED = 'Account restored'`;
  - `SaluData.suspendUser(id, reason, note) → Promise<{removedListings, cancelledReservations, closedReports}>`;
  - `SaluData.restoreUser(id, note) → Promise<{restored}>`.

- [ ] **Step 1: Write the failing tests**

In `admin/tests/data.test.js`, extend the first block's `load({ … })` answers with:

```js
      admin_suspend_user: (args) => ({ data: { removedListings: 1, cancelledReservations: 1, closedReports: 2, args }, error: null }),
      admin_restore_user: (args) => ({ data: { restored: true, args }, error: null })
```

and after `assert.deepStrictEqual(await t.data.dismissReport('r1', null), { dismissed: 1 });` add:

```js
    // Suspend and restore send exactly these arguments and also survive a failed count reload.
    const sus = await t.data.suspendUser('u1', 'NO_SHOWS', 'Test note');
    assert.deepStrictEqual(sus.args, { p_user: 'u1', p_reason: 'NO_SHOWS', p_note: 'Test note' });
    assert.strictEqual(sus.removedListings, 1);
    const res = await t.data.restoreUser('u1', '');
    assert.deepStrictEqual(res.args, { p_user: 'u1', p_note: null });
    assert.strictEqual(t.data.text.suspension.OTHER, 'Breaking the community rules');
    assert.strictEqual(t.data.text.actions.USER_SUSPENDED, 'Account suspended');
```

In the second block (refused action), add the answer `admin_suspend_user: () => ({ data: null, error: { code: 'P0001', message: 'Admins can\u2019t be suspended.' } })` and the assertion:

```js
    await assert.rejects(t.data.suspendUser('u2', 'SPAM', 'x'), (e) => e instanceof t.data.DataError && e.code === 'P0001');
```

In `admin/tests/timeline.test.js`, add one row to the reasons table, after the `PICKUP_EXPIRED` row:

```js
  ['ACCOUNT_SUSPENDED', 'Cancelled: account suspended', /account was suspended/],
```

- [ ] **Step 2: Run them to check that they fail**

Run: `node admin/tests/data.test.js; node admin/tests/timeline.test.js`
Expected:
- `data.test.js` fails with `TypeError: t.data.suspendUser is not a function`;
- `timeline.test.js` fails with an `AssertionError` (`'Cancelled'` !== `'Cancelled: account suspended'`).

- [ ] **Step 3: Implement**

In `admin/js/data.js`, inside `const text = {`, add after `audience`:

```js
    ,
    /* Suspension reasons (model/Suspension.kt, migration 20261017); never rename a code. */
    suspension: {
      NO_SHOWS: 'Repeated no-shows at pickup', UNSAFE_FOOD: 'Unsafe or misleading food',
      HARASSMENT: 'Harassment or abuse', SPAM: 'Spam or fake account', OTHER: 'Breaking the community rules'
    }
```

(Put the comma after the `audience` line's closing brace, so the object stays valid.) Extend `actions` with `USER_SUSPENDED: 'Account suspended', USER_RESTORED: 'Account restored'`. After `addReportNote(...)`, add:

```js
    /** Suspend and restore (20261017). The count reload after either may fail without failing the action. */
    async suspendUser(id, reason, note) {
      const r = await rpc('admin_suspend_user', { p_user: id, p_reason: reason, p_note: note });
      await refreshCountsAfterAction();
      return r;
    },
    async restoreUser(id, note) {
      const r = await rpc('admin_restore_user', { p_user: id, p_note: note || null });
      await refreshCountsAfterAction();
      return r;
    },
```

Update the header comment's migration list to add `20261017 (suspend accounts)`.

In `admin/js/timeline.js`, add `ACCOUNT_SUSPENDED: 'Cancelled: account suspended'` to `CANCEL_TITLE`, and in `cancelMeta(r)` before the final `return`:

```js
    if (r.cancelReason === 'ACCOUNT_SUSPENDED') return 'Cancelled because the recipient’s account was suspended; the quantity went back to the listing.';
```

- [ ] **Step 4: Run every dashboard check**

Run: `for t in admin/tests/*.test.js; do node "$t" || echo "FAILED $t"; done; for f in admin/js/*.js admin/js/pages/*.js; do node --check "$f" || echo "SYNTAX $f"; done`
Expected: no `FAILED` and no `SYNTAX` lines.

- [ ] **Step 5: Commit**

```bash
git add admin/js/data.js admin/js/timeline.js admin/tests/data.test.js admin/tests/timeline.test.js
git commit -m "Dashboard: suspend and restore in the data layer, the suspended timeline ending"
```

---

### Task 6: Dashboard Users page

**Files:**
- Modify: `admin/js/ui.js`, the `confirm(o)` function (lines 500-570) and its doc comment.
- Modify: `admin/js/pages/users.js`.

**Interfaces:**
- Consumes:
  - `SaluData.suspendUser` / `restoreUser` / `text.suspension` (Task 5);
  - the user JSON fields `suspendedAt`, `suspensionReason`, `suspension`, `suspendCounts` (Task 2);
  - `BADGES.account.SUSPENDED` (already in `ui.js`).
- Produces: `UI.confirm` accepts `choice: { label, placeholder, options: [[value, label], …] }` and `reason.maxLength`; `action` is called as `action(reason, choice)`.

- [ ] **Step 1: Give `confirm()` an optional select and a longer note**

In `admin/js/ui.js`, update the doc comment's signature to:

```js
   * confirm({ title, text, confirmLabel, tone: 'danger'|'primary', iconName, choice: { label,
   * placeholder, options: [[value, label]] }, reason: { label, required, placeholder, hint,
   * maxLength }, action: async (reason, choice) => {}, errorText: string | (err) => string })
```

After `const errorBox = h('div', { hidden: true });` add:

```js
      let choiceEl = null;
      let choiceErr = null;
      if (o.choice) {
        const cid = titleId + '-choice';
        choiceEl = h('select', { class: 'select', id: cid },
          [h('option', { value: '' }, o.choice.placeholder || 'Choose…')]
            .concat(o.choice.options.map(([v, label]) => h('option', { value: v }, label))));
        choiceErr = h('span', { class: 'field__error', id: cid + '-err', hidden: true }, 'Choose a reason.');
        choiceEl.addEventListener('change', () => { choiceErr.hidden = true; choiceEl.classList.remove('is-invalid'); choiceEl.removeAttribute('aria-invalid'); });
      }
```

In the textarea creation, replace `maxlength: '300'` with `maxlength: String((o.reason && o.reason.maxLength) || 300)`. In the dialog's children, before `o.reason ? h('div', { class: 'field' },`, add:

```js
        o.choice ? h('div', { class: 'field' },
          h('label', { class: 'field__label', for: choiceEl.id }, o.choice.label),
          choiceEl, choiceErr) : null,
```

Change the first-focus line to `setTimeout(() => (choiceEl || reasonEl || cancelBtn).focus(), 40);`.

In `submit()`, at its start, add:

```js
        const choice = choiceEl ? choiceEl.value : null;
        if (choiceEl && !choice) {
          choiceErr.hidden = false;
          choiceEl.classList.add('is-invalid');
          choiceEl.setAttribute('aria-invalid', 'true');
          choiceEl.setAttribute('aria-describedby', choiceErr.id);
          choiceEl.focus();
          return;
        }
```

and change `() => (o.action ? o.action(reason) : null)` to `() => (o.action ? o.action(reason, choice) : null)`. Existing callers pass one-argument actions and keep working.

- [ ] **Step 2: The Users page**

In `admin/js/pages/users.js`:
- **Header comment:** change it to `/* Users: everyone on Salu-Salo and what they've done; suspend and restore (§36). */`.
- **Helpers:** add `confirm, toast` to the destructured `UI` helpers.
- **`ROLE_OPTIONS`:** add `['SUSPENDED', 'Suspended']` as the last item.
- **Badge helper:** after `emailBadge`, add:

```js
  function suspendedBadge(u) { return u.suspendedAt ? badge('account', 'SUSPENDED') : null; }
```

- **Badges in the list:** the Role column becomes `cell: (u) => h('div', { class: 'badge-row' }, badge('role', u.role), suspendedBadge(u))`. The card's `badges` array gets `suspendedBadge(u)` after `badge('role', u.role)`. In `openUser`, the hero's `badge-row` gets `suspendedBadge(u)` after `badge('role', u.role)`.
- **Suspension notice:** in `openUser`'s `body` array, right after the `panel-hero` element, add:

```js
      u.suspendedAt ? notice({
        tone: 'error', iconName: 'block',
        title: 'Suspended ' + fmt.date(u.suspendedAt) + (u.suspension ? ' by ' + u.suspension.byName : ''),
        text: u.suspension ? u.suspension.details : data.text.suspension[u.suspensionReason] || 'Suspended'
      }) : null,
```

- **Footer buttons:** replace the `const foot = …` line with:

```js
    const foot = [];
    if (u.stats.listings) foot.push(btn({ label: 'View listings', icon: 'lunchDining', variant: 'secondary', onClick: () => viewListings(u) }));
    // Never for an admin or yourself (the server refuses those too).
    if (u.role !== 'ADMIN' && !u.isYou) {
      foot.push(u.suspendedAt
        ? btn({ label: 'Restore account', icon: 'restartAlt', variant: 'secondary', onClick: () => restoreUser(u) })
        : btn({ label: 'Suspend account', icon: 'block', variant: 'danger', onClick: () => suspendUser(u) }));
    }
```

  and pass `foot: foot.length ? foot : null` to `UI.openPanel`.
- **The two actions:** after `miniStat`, add:

```js
  // The server's own refusal (P0001) is shown as it is; anything else gets a general text.
  const serverText = (fallback) => (err) => (err && err.code === 'P0001' && err.message) || fallback;

  async function suspendUser(u) {
    const c = u.suspendCounts || { listings: 0, reservations: 0 };
    let result = null;
    const ok = await confirm({
      title: 'Suspend ' + u.name + '?',
      text: 'They’ll be locked out of Salu-Salo until an admin restores the account. This will remove ' +
        fmt.plural(c.listings, 'active listing') + ' and cancel ' + fmt.plural(c.reservations, 'open reservation') + '.',
      confirmLabel: 'Suspend account', tone: 'danger', iconName: 'block',
      choice: { label: 'Reason they’ll see', placeholder: 'Choose a reason', options: Object.entries(data.text.suspension) },
      reason: { label: 'Private note', required: true, maxLength: 1000, placeholder: 'What happened, for other admins', hint: 'Only admins see this note.' },
      errorText: serverText('Unable to suspend this account. Please try again.'),
      action: async (note, reason) => { result = await data.suspendUser(u.id, reason, note); }
    });
    if (ok) {
      const bits = [];
      if (result && result.removedListings) bits.push(fmt.plural(result.removedListings, 'listing') + ' removed');
      if (result && result.cancelledReservations) bits.push(fmt.plural(result.cancelledReservations, 'reservation') + ' cancelled');
      toast('Account suspended.', { sub: bits.length ? bits.join(' · ') + '.' : null });
      refreshAfterAction(u.id);
    }
  }
  async function restoreUser(u) {
    const ok = await confirm({
      title: 'Restore ' + u.name + '’s account?',
      text: 'They can sign in and use Salu-Salo again. Removed listings and cancelled reservations stay as they are.',
      confirmLabel: 'Restore account', tone: 'primary', iconName: 'restartAlt',
      reason: { label: 'Note', required: false, maxLength: 1000, placeholder: 'For example: appeal accepted' },
      errorText: serverText('Unable to restore this account. Please try again.'),
      action: (note) => data.restoreUser(u.id, note)
    });
    if (ok) { toast('Account restored.'); refreshAfterAction(u.id); }
  }
  function refreshAfterAction(id) {
    if (!s) return;
    s.list.reload();
    if (UI.panelIsOpen() && s.panelFor === id) openUser(id, true);
  }
```

- [ ] **Step 3: Syntax and the Node checks**

Run: `for t in admin/tests/*.test.js; do node "$t" || echo "FAILED $t"; done; for f in admin/js/*.js admin/js/pages/*.js; do node --check "$f" || echo "SYNTAX $f"; done`
Expected: no `FAILED` or `SYNTAX` lines.

- [ ] **Step 4: Look at it in a browser (sample-free, needs the developer's live login)**

This page needs the live admin functions, which aren't applied until the developer runs the migration. So the browser check is part of the phone test (spec §4.3 steps 4, 9, 10). No local run here.

- [ ] **Step 5: Commit**

```bash
git add admin/js/ui.js admin/js/pages/users.js
git commit -m "Dashboard: Suspend and Restore on the Users page, a Suspended badge and filter"
```

---

### Task 7: Docs

**Files:**
- Modify: `docs/PROJECT_STATUS.md`:
  - the status table row (around line 44, Phase 6);
  - "Current state" (around line 134);
  - a new `## 36.` section after §35;
  - the results log is left for the phone test.
- Modify: `CLAUDE.md`:
  - the Phase 6 roadmap bullet;
  - the "Supabase objects" list;
  - the "Security and ownership rules" list;
  - "Known current work / issues" item 28.

**Interfaces:** none.

- [ ] **Step 1: Check whether PR #32 has merged**

Run: `& "C:\Program Files\GitHub CLI\gh.exe" pr view 32 --repo SiegSucher/SaluSalo --json state,mergeCommit` (with `$env:GH_PROMPT_DISABLED = '1'`).
If it says `MERGED`, record it in §35's header and CLAUDE.md's §35 lines: "merged into `master` through PR #32 on <date> (merge commit `<first 7 of oid>`)". If not, leave §35 as it is.

- [ ] **Step 2: Write §36 in `docs/PROJECT_STATUS.md`**

Add after §35's last subsection, in the file's style (bold status line first):

```markdown
## 36. Suspend accounts (Phase 6)

**Written on branch `claude/suspend-accounts` (from `claude/push-notifications` at `f04c77c`, so it carries §34 and §35 until PR #32 is merged) on 2026-10-17, at the developer's request. Design: `docs/superpowers/specs/2026-10-17-suspend-accounts-design.md`; plan: `docs/superpowers/plans/2026-10-17-suspend-accounts.md`. Built and checked locally (§36.3). Migration `20261017_admin_suspend_accounts.sql` not applied to the live project yet; dashboard not deployed; phone test (§36.4) not run.**

### 36.1 What the user sees

- **Admins (dashboard → Users):**
  - A red Suspended badge and a Suspended filter.
  - On a user's page, **Suspend account**: a reason from a list, a private note, the listings and reservations it will close, then Suspend. Toast "Account suspended."
  - A suspended user's page shows who suspended them, when and why, and **Restore account** (toast "Account restored.").
  - Neither is offered for an admin or yourself.
  - Both go into the Audit log.
- **The suspended person:**
  - After sign-in, or when the app comes back to the screen, only "Your account has been suspended", the reason, "If you think this is a mistake, email <address>" (the address opens the mail app) and Log out.
  - No pop-ups.
  - Older builds simply can't load anything.
- **Others:**
  - Someone with a reservation on their listing gets "Listing removed".
  - A donor whose listing they had reserved gets "Reservation cancelled" / "<name>'s reservation of <listing> was cancelled. The quantity is available again."
  - Messages to them are refused with "This person can't receive messages right now."
  - Nobody is told about the suspension.
- **Restore:** the account works again; removed listings and cancelled reservations stay as they are.

### 36.2 What changed

- **Database (`20261017`):**
  - `users.suspended_at` and `users.suspension_reason`;
  - `salusalo_caller_suspended()`, with `is_salusalo_firebase_jwt()` now also requiring "not suspended";
  - `get_my_account_status()`, the only function that skips it;
  - `admin_suspend_user`, `admin_restore_user` and the internal `admin_suspension_listings`;
  - the `ACCOUNT_SUSPENDED` cancel reason and its donor alert;
  - the `USER_SUSPENDED` report resolution;
  - the audit actions `USER_SUSPENDED` and `USER_RESTORED` (target `USER`);
  - `send_message`'s refusal;
  - the `SUSPENDED` filter, and the suspension fields in the admin user JSON.

  Check: `supabase/tests/admin_suspend_accounts_live_safety_check.sql` (18).
- **App:**
  - `model/Suspension.kt` (tested by `SuspensionTest`, 7);
  - `UserRepository.getMyAccountStatus()` and `ProfileException.Suspended`;
  - `MainActivity`: the `"suspended"` screen (`SuspendedScreen`), the check in `openAuthenticatedHome()` and when the app comes back to the screen, and the shade cleared.
- **Dashboard:** `data.js` (`suspendUser`, `restoreUser`, labels), `timeline.js`, `ui.js` (`confirm()` with a reason select), `pages/users.js`.

### 36.3 Checks run (local)

Fill in from the actual runs:
- the SQL dry run, with the new check's result and the earlier checks;
- `testDebugUnitTest` count, `lintDebug`;
- the Node checks.

### 36.4 Phone test (two phones and the dashboard)

Not run yet. The 13 steps are in the spec, §4.3; copy them here word for word.

### 36.5 Known limits

- Someone suspended can sign up again with another email.
- While Home is open, a suspended person's actions fail with the usual error texts until the app next comes back to the screen.
- Their past reviews, ratings and old chats stay visible to others.
- The Firebase account stays enabled; only Salu-Salo's server refuses it.
- Only an admin's Restore ends a suspension.
- Restore doesn't bring back removed listings or cancelled reservations.
- Every admin can suspend and restore.
```

Fill §36.3 with the real numbers from Tasks 2–6, and §36.4 with the spec's 13 steps verbatim. Add a status-table row and a "Current state" bullet for §36 in the same wording as the §35 ones ("not applied, not deployed, phone test not run").

- [ ] **Step 3: Update `CLAUDE.md`**

- **Roadmap:** add to the Phase 6 bullet: "**Fourth slice, suspend accounts: built on branch `claude/suspend-accounts` (from `claude/push-notifications` at `f04c77c`); migration `20261017_admin_suspend_accounts.sql` not applied yet; phone test not run** (§36)."
- **"Supabase objects":** a new first bullet: "Suspend accounts (`20261017_admin_suspend_accounts.sql`, §36; not applied yet): `users.suspended_at`/`suspension_reason`; `is_salusalo_firebase_jwt()` also requires `not salusalo_caller_suspended()` (SECURITY DEFINER, the caller's own row); `get_my_account_status()` (checks the token itself; the only function a suspended account can run); admin-only `admin_suspend_user` (removes their active listings through `admin_remove_listing`, cancels their open reservations as `ACCOUNT_SUSPENDED`, deletes their push tokens, resolves reports about them as `USER_SUSPENDED`) and `admin_restore_user`; `send_message` refuses messages to a suspended person. Check: `supabase/tests/admin_suspend_accounts_live_safety_check.sql` (18)."
- **"Security and ownership rules":** a new bullet: "**Suspension (§36).** `users.suspended_at` and `suspension_reason` change only through `admin_suspend_user` and `admin_restore_user`. `is_salusalo_firebase_jwt()` must keep the suspension check when redefined; `get_my_account_status()` is the only function that skips it and returns only the caller's own state. Never suspend an admin, never tell other people someone was suspended, and keep the admin's note only in the audit log."
- **"Known current work / issues":** add item 28 summarising §36 and its state.

- [ ] **Step 4: Commit**

```bash
git add docs/PROJECT_STATUS.md CLAUDE.md
git commit -m "Docs: suspend accounts (§36)"
```

---

### Task 8: Whole-branch review and handoff

**Files:** none changed unless the review finds something.

- [ ] **Step 1: Full local re-run**

Run all of these:
1. Task 2 Step 2 (the SQL dry run with every check);
2. `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`;
3. the Node checks.

Expected: the new check 18 passed; the earlier checks unchanged; 114 unit tests; lint 0 errors; no Node failures.

- [ ] **Step 2: Fresh-context review**

Dispatch one reviewer (most capable model, read-only) on `f04c77c..HEAD`, with the spec and this plan. Ask specifically about:
- any client-reachable function or policy that doesn't go through `is_salusalo_firebase_jwt()`;
- lock order in `admin_suspend_user`;
- the push-token deletion happening before any alert;
- the app's return hook racing with Log out.

Fix every Critical or Important finding in its own commit, re-running the affected checks.

- [ ] **Step 3: Hand off to the developer**

Tell the developer, in this order:
1. Run the migration and the safety check (`supabase/README.md`, 20261017; expect 18 passed).
2. Deploy the dashboard: `npx firebase-tools deploy --only hosting`.
3. Install the build on both phones.
4. Run §36.4.

Mention that the PR into `master` waits for PR #32. Record the results in `docs/PROJECT_STATUS.md` §36.4 and the results log only after the developer reports them.
