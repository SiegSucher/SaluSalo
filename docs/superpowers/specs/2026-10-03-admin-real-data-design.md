# Admin dashboard with real data: foundation and moderation

_Design approved in conversation on 2026-10-03. Branch `claude/admin-real` (from `master` at `e24c8d7`). Phase 6, first slice._

## Goal

The web dashboard in `admin/` (today sample data only, `PROJECT_STATUS.md` §20) shows the live Salu-Salo data to one or more admins and lets them moderate listings and reports. The developer opens it on their phone's browser (and on a PC) from anywhere.

**In this slice:** admin sign-in, the admin check, hosting, real Overview, Users, Listings, Reservations, Reports and Audit log, and three moderation actions: remove a listing, dismiss reports, add report notes (plus "Mark as reviewed").

**Not in this slice** (each its own later spec): suspend and restore users, ID verification requests, announcements, Analytics, and any change to the Android app. Pages and buttons for them show "Not connected yet" or are hidden; no sample data is shown anywhere.

## Decisions

| Decision | Choice |
|---|---|
| Where the admin UI lives | Website only. The Android app is unchanged; an admin account sees the normal app there. |
| Hosting | Firebase Hosting on project `salusalo` (`https://salusalo.web.app`), deployed by the developer with `npx firebase-tools deploy --only hosting`. |
| Who is admin | `users.role = 'ADMIN'`, set by hand in the SQL Editor (`update public.users set role = 'ADMIN' where email = '…';`). The account must have signed into the app once. Undo: set it back to `'RECIPIENT'`. The app never sets roles. |
| Listing photos | Admins can read every listing photo (`can_read_food_image` gains "or the caller is an admin"), to judge reports. |
| Donor told about a removal | Yes, one alert of the existing type `RESERVATION_REMOVED` (the app's `NotificationType` is a strict enum; a new type would break current builds). |

## 1. Web site (`admin/`)

- Stays static: no build step, no npm dependencies in the repo. Two libraries load as classic `<script>` tags from cdn.jsdelivr.net / gstatic: Firebase Auth (compat build, `firebase-app-compat.js`, `firebase-auth-compat.js`) and supabase-js v2 (UMD), pinned to exact versions.
- `admin/js/config.js` (git-ignored) holds the Supabase URL, the publishable key and the Firebase web config; `admin/js/config.example.js` (committed) shows its shape. The site shows "Missing js/config.js" if it is absent.
- Supabase client: `supabase.createClient(url, key, { accessToken: () => firebase.auth().currentUser?.getIdToken() ?? null })`, the same Third-Party Auth trust the app uses. The admin's account already has `role: authenticated` (§18); if not, the page says so (the dashboard does not call `set-firebase-role`; signing into the app once fixes it).
- **Sign In screen** (new, in the dashboard's style): email and password, and "Continue with Google" (`signInWithPopup`). Firebase persistence is the default (local), so the phone stays signed in. After sign-in the page calls `admin_whoami()`; if it refuses, the page signs out and shows "This account doesn't have admin access." Log out is in the sidebar's profile.
- `js/data.js` keeps its function names and the row shapes the pages read, and its bodies call the admin RPCs. Fields that don't exist in the database are dropped from the pages instead of invented: city, barangay, "last active", verification status, account status. `js/sample-data.js` and the Settings "Sample data" tools are deleted; the "Sample data" chip goes.
- Listing photos: the RPCs return storage paths; `data.js` signs them in one `createSignedUrls` call per list, 1 hour, never logged (same rules as the app).
- Profile photos are not shown (the bucket stays owner-only); avatars stay initials.
- Errors: never show a raw exception; the existing error states and toasts show short texts. A token refused by Supabase (expired session) returns to Sign In.

## 2. Pages

| Page | Shows | Actions |
|---|---|---|
| Overview | Users (total, new in 7 days), active listings (total, new today), open reservations (total, made today), open reports (total, new today); activity of the last 3 days (joins, posts, reservations, reports), newest 8; 3 newest open reports; 6 newest reservations; food rescued in 30 days (items, completed, cancelled, active donors, weekly items for 12 weeks) | none |
| Users | Search name/email; filter Admin / Has posted / Never posted; name, email, Email verified, role, joined, open reports; paged 10. Details: stats (listings, active listings, reservations, completed, handed over, reports against, reports on their listings), open reports, 6 recent events | none |
| Listings | Search name/donor/ID; status filter (All, Active, Available, Reserved, Claimed, Expired, Removed, Reported); posted (Any, Today, 7 days, 30 days); donor filter from a user's page; photos. Details: description, allergens, storage notes, pickup window, location, handover, expiry, all photos, reservations, reports, last review and removal (by, reason) from the audit log | Remove listing, Dismiss reports, Mark as reviewed |
| Reservations | Search; status; date. Details: timeline Created → Claimed / Cancelled, with the real reason: cancelled by the recipient, released by the donor (`DONOR_RELEASED`), listing removed (`LISTING_REMOVED`), pickup time expired (`PICKUP_EXPIRED`); pickup deadline when set | none |
| Reports | Tabs Open, Resolved, Dismissed, All; filters target and reason; search. Details: report, details text, reporter (with reports filed), target (listing or person), other reports on the same target, admin notes, resolution | Dismiss, Add note, Remove listing (listing reports) |
| Audit log | Every admin action, newest first: admin, action, target, details; filters admin, action, date; search | none |
| Settings | The signed-in admin's name and email; Log out | none |

The bell shows the open-reports count and links to Reports (the sample "admin alerts" are dropped). Notifications, Analytics and the Users "Verification requests" tab show "Not connected yet"; Verify, Suspend and Restore buttons are hidden.

## 3. Database (one migration, `supabase/migrations/20261012_admin_dashboard.sql`, run by hand)

**Helper.** `is_salusalo_admin()`: SECURITY DEFINER, STABLE; true only for `is_salusalo_firebase_jwt()` and a profile from `current_salusalo_profile_id()` with `role = 'ADMIN'`. `revoke all from public, anon`; EXECUTE for `authenticated` only because the storage policy runs as the client (like `can_read_food_image`); it reveals only whether the caller is an admin.

**Tables.**
- `reports`: the status check becomes `OPEN`, `RESOLVED`, `DISMISSED`; new nullable columns `resolved_by uuid references users(id) on delete set null`, `resolved_at timestamptz`, `resolution_action text check in ('LISTING_REMOVED', 'DISMISSED')`, `resolution_note text (≤ 1000)`; check: OPEN ⇔ all four null. The unique "one open report" indexes are unchanged (a reporter may report again after a resolution). `submit_report` is unchanged.
- `report_notes` (id, report_id → reports on delete cascade, admin_id → users on delete set null, note text 1–1000, created_at): RLS on, no client privileges.
- `admin_audit_log` (id, admin_id → users on delete set null, admin_name text snapshot, action text check in ('LISTING_REMOVED', 'REPORTS_DISMISSED', 'LISTING_REVIEWED', 'REPORT_NOTE_ADDED'), target_type text check in ('LISTING', 'REPORT'), target_id uuid, target_name text, details text, created_at): RLS on, no client privileges; rows are only inserted, by the admin functions. No UPDATE or DELETE path exists.

**Read functions** (SECURITY DEFINER, STABLE, `set search_path = public`; each first refuses: no token "Not authenticated", not admin "Admin access required"; return `jsonb`; paging `p_page`, `p_page_size` capped at 50, returning `{rows, total, page, pages}`):
`admin_whoami()`, `admin_counts()` (open reports, for the sidebar and bell), `admin_overview()`, `admin_users(q, filter, page, size)`, `admin_user(id)`, `admin_listings(q, status, posted, donor_id, page, size)`, `admin_listing(id)`, `admin_reservations(q, status, date, page, size)`, `admin_reservation(id)`, `admin_reports(q, status, target, reason, page, size)`, `admin_report(id)`, `admin_audit(q, admin_id, action, range, page, size)`, `admin_audit_filters()`.
They return names and the user's email (admins need it to identify accounts) but never `firebase_uid`, phone number or profile photo path.

**Write functions** (SECURITY DEFINER, volatile; same two refusals first; each writes its `admin_audit_log` row in the same transaction):
- `admin_remove_listing(p_post uuid, p_reason text)`: reason required, 1–500 characters. Locks the listing (`for update`); refuses a missing listing ("Listing not found") or one already `REMOVED` ("This listing was already removed."). Sets `status = 'REMOVED'` (never deletes); cancels its `RESERVED` reservations with `cancel_reason = 'LISTING_REMOVED'`, leaving the listing's quantity unchanged, exactly as `remove_my_food_post` does (it locks the open reservations first, then the listing); alerts each affected recipient once with `notify_user(…, 'RESERVATION_REMOVED', 'Listing removed', '<listing> was removed by Salu-Salo, so your reservation was cancelled.' …)`; alerts the donor once (`'RESERVATION_REMOVED', 'Your listing was removed', 'Salu-Salo removed <listing>: <reason>'`); deletes its `saved_listings` rows; resolves its open reports (`RESOLVED`, `LISTING_REMOVED`, note = reason). Returns `{cancelled, closedReports}`. The reservation trigger sends nothing for `LISTING_REMOVED` (checked in `notify_reservation_change()`), so there is no double alert; the recipient alert counts only the reservations this call cancelled.
- `admin_dismiss_listing_reports(p_post uuid, p_note text)`: open LISTING reports on it → `DISMISSED`; returns `{dismissed}`.
- `admin_dismiss_report(p_report uuid, p_note text)`: one open report → `DISMISSED`; refuses a closed one ("This report is already closed.").
- `admin_add_report_note(p_report uuid, p_note text)`: 1–1000 characters.
- `admin_mark_listing_reviewed(p_post uuid, p_note text)`: audit row only.
Notes default to "No problem found." where the dashboard already does so.

**Photos.** `can_read_food_image(name)` returns true also when `is_salusalo_admin()` and the path belongs to a listing (same path check as today). Nothing else in Storage changes; `profile-images` stays owner-only.

**Privileges.** Every new function: `revoke all … from public`, `revoke all … from anon`, `grant execute … to authenticated`. No table grants, no RLS widening, no service-role key. Rate limits: not needed (admin-only, and alerts are bounded by a listing's reservations).

## 4. Hosting

- `firebase.json` (repo root): `hosting.public = "admin"`, ignore `README.md`, headers `Cache-Control: no-cache` for `*.html` and `js/config.js`, and a `Content-Security-Policy` limited to self, the two CDNs, the Supabase project URL, Google's auth domains and fonts.
- `.firebaserc`: default project `salusalo`.
- One-time developer setup (in `admin/README.md`): register a Web app in the Firebase console and copy its config into `admin/js/config.js`; check that `salusalo.web.app` is an authorized domain (it is by default) and that Google sign-in is enabled (it is); `npx firebase-tools login`; set the admin role in SQL.

## 5. Testing

- **Local:** `node --check` on every script; the migration on a local PostgreSQL 16 (stand-ins as in §21.3), applied twice; the existing safety checks still pass.
- **Live (developer, SQL Editor):** `supabase/tests/admin_dashboard_check.sql` (read-only: objects, privileges, no `anon` EXECUTE) and `supabase/tests/admin_dashboard_live_safety_check.sql` (ends with an error so it rolls back): a non-admin and a token without profile are refused by every admin function; an admin removes a listing with an open reservation → `REMOVED`, reservation cancelled `LISTING_REMOVED`, one alert each for recipient and donor, reports resolved, one audit row; dismiss and notes; photo rule for admin and non-admin.
- **Manual (developer):** a short checklist on the phone's browser and the app: sign in as a non-admin (refused), as the admin (Overview numbers plausible), report a test listing from account B in the app, see it in Reports with its photo, add a note, remove the listing, check both alerts in the app and the Audit log; dismiss another report.
- `assembleDebug` is not affected (no app change) but runs once as the usual gate.

## 6. Known limits

- Single-role model: every admin can do everything; no per-admin permissions.
- The donor's removal alert follows their "Reservation updates" switch (as all `RESERVATION_%` alerts do).
- No live updates: pages reload on open and on Try again.
- The publishable key and Firebase web config are visible to anyone who opens the site, as in any web app; security rests on the token and the admin check in each function.
- Suspend, verification, announcements and analytics remain sample-free placeholders until their specs.
