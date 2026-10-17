# Suspend accounts (Phase 6, next slice)

Agreed with the developer on 2026-10-17. Branch `claude/suspend-accounts`, from `claude/push-notifications` at `f04c77c` (PR #32, not merged yet). The migration needs §35's `push_tokens`, so this branch carries §34 and §35. Once PR #32 is merged, this branch's PR into `master` contains only this slice.

## Goal

An admin can suspend an account from the dashboard and restore it later. A suspended person is locked out of Salu-Salo completely. After sign-in they see only a suspended screen with the reason, an appeal email and Log out. The server refuses everything else for them, whatever app build they use. Their active listings close and their open reservations are cancelled, with the usual alerts to the other people.

## Decisions

- **Suspend accounts first;** ID verification is a later slice.
- **Locked out, not read-only.**
- **Their items close now:**
  - active listings are removed, kept as `REMOVED`, like an admin removal;
  - open reservations are cancelled on both sides: other people's reservations on their listings, and the ones they hold.
  - Restore gives the account back, but not those listings or reservations.
- **Until an admin restores.** No automatic end.
- **The person sees a reason from a list.** The admin's note stays private, so it can't reveal a reporter.
- **An appeal email on the suspended screen.** The address is the developer's, kept as one constant (`SUPPORT_EMAIL`); the developer gives the value before the phone-test build.
- **Approach A: one lock at the front door.** `is_salusalo_firebase_jwt()`, the identity check that every client-reachable policy and function already uses (81 uses across the migrations), also refuses a suspended account. Rejected:
  - B (disable the Firebase account): no reason can be shown, it needs a new key and function, and open sessions last up to 1 hour.
  - C (a check in every function): 80+ places, easy to miss one.
- **Assumptions confirmed by the developer:**
  - Admins can't be suspended, and an admin can't suspend themselves.
  - Other people are never told someone was suspended.
  - Ratings, reviews, chats and history stay.
  - Open reports about the person are resolved by the suspension.
  - Messages to a suspended person are refused with "This person can't receive messages right now."

### Reasons

| Code | What the person and the dashboard show |
| --- | --- |
| `NO_SHOWS` | Repeated no-shows at pickup |
| `UNSAFE_FOOD` | Unsafe or misleading food |
| `HARASSMENT` | Harassment or abuse |
| `SPAM` | Spam or fake account |
| `OTHER` | Breaking the community rules |

The app shows an unknown code as "Breaking the community rules".

## 1. Database (one migration, `supabase/migrations/20261017_admin_suspend_accounts.sql`, run by hand)

### 1.1 Where a suspension is kept

- **`users` gets `suspended_at timestamptz` and `suspension_reason text`.**
  - The check `users_suspension_check`: both NULL, or both set with the reason one of the five codes.
  - Only the admin functions below change them. Clients still have no UPDATE on `users`.
- **The note and the admin go only into `admin_audit_log`**, which clients can't read:
  - action `USER_SUSPENDED` (detail = the reason code, then the note);
  - action `USER_RESTORED` (detail = the note, if any);
  - target type `USER`.

  Nothing private is in the person's own row.

### 1.2 The lock

- **New internal `salusalo_caller_suspended()`**:
  - Definition: SECURITY DEFINER, STABLE, `search_path = ''`.
  - Returns `exists (select 1 from public.users where firebase_uid = auth.jwt() ->> 'sub' and suspended_at is not null)`: the caller's own row only, by the token.
  - Grants: EXECUTE for `authenticated` only, because policies run as the client (like `can_read_food_image`); revoked from `public` and `anon`.
  - It reveals nothing but the caller's own state.
- **`is_salusalo_firebase_jwt()` is replaced with the same three token checks `and not public.salusalo_caller_suspended()`.**
  - Same signature, so every policy and function that uses it keeps working and now refuses a suspended account.
  - What that covers: `users`, `food_posts`, `reservations`, `saved_listings`, `conversations`, `messages`, `notifications`, `notification_preferences`, both Storage buckets (`can_read_food_image` and the profile-image policies), Realtime (RLS), every RPC including `register_my_push_token`, and `admin_require()`.
  - Policies already wrap it in `(select …)`, so the lookup runs once per statement (an indexed `firebase_uid` lookup).
- **New `get_my_account_status()`**:
  - Definition: SECURITY DEFINER, STABLE, EXECUTE for `authenticated` only.
  - It checks the token itself (issuer, audience, `role`), **not** through `is_salusalo_firebase_jwt()`, and raises `Not authenticated` otherwise.
  - It returns `jsonb {"suspended": boolean, "reason": text|null}` for the caller's own row. No row (a new account) means `suspended: false`.
  - It is the only thing a suspended account can still use.

### 1.3 Admin functions (SECURITY DEFINER, each starts with `admin_require()`, EXECUTE for `authenticated` only)

**What counts as "active".** These are the items a suspension closes, and the dialog counts the same ones:
- **active listing:** the user's `food_posts` with `status <> 'REMOVED'` that either have an open (`RESERVED`) reservation, or have `expiration_date > now()` and (`pickup_end` is NULL or `> now()`);
- **open reservation:** the user's own `reservations` with `status = 'RESERVED'`.

**`admin_suspend_user(p_user uuid, p_reason text, p_note text) returns jsonb`**

1. Refuses each of these:

   | Case | Message |
   | --- | --- |
   | The user doesn't exist | "User not found" |
   | The user is the caller | "You can't suspend your own account." |
   | The user is an admin | "Admins can't be suspended." |
   | Already suspended | "This account is already suspended." |
   | `p_reason` not one of the five codes | "Choose a reason." |
   | Blank `p_note`, or over 1,000 characters | "Write a note (up to 1,000 characters)." |

   It locks the user's row first.
2. Sets `suspended_at = now()` and `suspension_reason = p_reason`.
3. Deletes the user's `push_tokens` rows, before any alert below is inserted, so no pop-up reaches them.
4. For each active listing, calls `public.admin_remove_listing(id, 'Account suspended')`. That marks it `REMOVED`, cancels its open reservations as `LISTING_REMOVED`, sends "Listing removed" to each recipient and "Your listing was removed" to the donor (who sees it after a restore), deletes the saved rows, and resolves that listing's open reports.
5. For each open reservation the user holds, in `id` order:
   - lock the reservation, then its listing (the order `cancel_reservation` uses);
   - set `status = 'CANCELLED'` and `cancel_reason = 'ACCOUNT_SUSPENDED'`;
   - give the quantity back with `quantity = quantity + q`, and set `status = 'AVAILABLE'` when the listing isn't `REMOVED` (as `cancel_reservation` does).
   The trigger alerts the donor (§1.4).
6. Updates the open reports with `target_type = 'USER'` and `reported_user_id = p_user`: `status = 'RESOLVED'`, `resolved_by`, `resolved_at`, `resolution_action = 'USER_SUSPENDED'`, `resolution_note = p_note`.
7. Writes the audit entry with `admin_log(…, 'USER_SUSPENDED', 'USER', p_user, <name>, p_reason || ': ' || p_note)`.
8. Returns `{"removedListings": n, "cancelledReservations": m, "closedReports": k}`.

**`admin_restore_user(p_user uuid, p_note text) returns jsonb`**
- Refuses: "User not found"; "This account isn't suspended."; a note over 1,000 characters.
- Clears both columns and logs `USER_RESTORED` with the note, or "Restored." when there is none.
- Returns `{"restored": true}`.

**`admin_users`** (same signature) accepts `SUSPENDED` in its existing role filter argument, through `admin_user_matches`: it lists only suspended accounts.

**`admin_user_json` adds:**
- `suspendedAt` and `suspensionReason`;
- `suspension`: the latest `USER_SUSPENDED` audit entry for a suspended user (admin name, time, detail);
- `activeListings` and `openReservations`, the counts above, which the dialog shows.

### 1.4 Rule updates

- **`reservations_cancel_reason_check`** also allows `ACCOUNT_SUSPENDED`, with `status = 'CANCELLED'`.
- **`notify_reservation_change()`** is the 20261011 function plus one branch: `CANCELLED` with `ACCOUNT_SUSPENDED` (status changed) sends the donor `RESERVATION_CANCELLED` / "Reservation cancelled" / "<recipient name>'s reservation of <listing> was cancelled. The quantity is available again." The recipient gets nothing.
- **`reports_resolution_check`** also allows `status = 'RESOLVED'` with `resolution_action = 'USER_SUSPENDED'`.
- **`admin_audit_log`:**
  - `admin_audit_log_action_check` gains `USER_SUSPENDED` and `USER_RESTORED`;
  - `admin_audit_log_target_type_check` gains `USER`.
- **`send_message`** is the 20261009 function (its rate limit kept) plus a check after the participant check: when the other participant is suspended, `raise exception 'This person can''t receive messages right now.'`.
- **No new alert type.** Older builds therefore keep working. `Reservation.cancelReason` is a plain `String`, so the new value decodes.
- **Privileges:**
  - every new function `revoke all … from public`, `from anon`;
  - `grant execute … to authenticated` for `salusalo_caller_suspended()`, `get_my_account_status()`, `admin_suspend_user`, `admin_restore_user`;
  - `admin_user_json` and `admin_user_matches` stay non-executable by clients.

### 1.5 Check: `supabase/tests/admin_suspend_accounts_live_safety_check.sql`

Like the earlier live safety checks, it runs as three existing accounts (an admin A, a donor B to suspend, a recipient C) and ends with an error on purpose, which rolls back everything it did. 18 checks:

1. Suspended B: its own `users` row isn't visible.
2. Suspended B: `food_posts` shows nothing.
3. Suspended B: `create_reservation` is refused ("Not authenticated").
4. Suspended B: `send_message` is refused.
5. Suspended B: `can_read_food_image` is false for its own photo.
6. Suspended B: `register_my_push_token` is refused.
7. Suspended B: `get_my_account_status()` returns `suspended: true` with the reason.
8. `admin_suspend_user` is refused for a non-admin caller.
9. It is refused for the caller themselves and for an admin target.
10. It is refused for an unknown reason and for a blank note.
11. A second suspension of B is refused, and `admin_restore_user` is refused for an account that isn't suspended.
12. B's active listing is `REMOVED`, and C's reservation on it is `CANCELLED` / `LISTING_REMOVED`.
13. B's own reservation is `CANCELLED` / `ACCOUNT_SUSPENDED`, and its quantity is back on the listing.
14. The donor's alert has the exact title and text from §1.4.
15. B has no `push_tokens` rows.
16. An open USER report on B is `RESOLVED` / `USER_SUSPENDED`, and the `USER_SUSPENDED` audit row exists.
17. C's `send_message` to B is refused with "This person can't receive messages right now."; after `admin_restore_user`, B sees its row again and `get_my_account_status()` says `suspended: false`.
18. `anon` can't execute any new function.

The earlier checks must still pass on the dry run: `phase3_live_safety_check`, `phase4_live_safety_check`, `phase5_live_safety_check`, `phase5_maps_live_safety_check`, `admin_dashboard_live_safety_check`, `admin_announcements_live_safety_check`, `push_notifications_live_safety_check` and `bugfix_anon_grants_check`.

`supabase/README.md` gets a "20261017" entry: run the migration, then the check (expect 18 passed).

## 2. App (Android)

- **`model/Suspension.kt`** (new):
  - `AccountStatus(suspended: Boolean, reason: String?)`, `@Serializable`;
  - `suspensionReasonLabel(code: String?)`: the table above, unknown or NULL → "Breaking the community rules";
  - `SUPPORT_EMAIL`.
  - Tested by `SuspensionTest` (each code, an unknown code, NULL).
- **`UserRepository.getMyAccountStatus(): AccountStatus?`** calls `get_my_account_status`. On any failure except cancellation it returns null; it never shows or logs the exception.
- **`MainActivity.kt`:**
  - **New screen value `"suspended"`.** `suspendedReason` is held with `rememberSaveable`.
  - **`SuspendedScreen(reason, onLogout)`** shows:
    - `SaluLogo`;
    - "Your account has been suspended";
    - "Reason: <label>";
    - "If you think this is a mistake, email <SUPPORT_EMAIL>.": the address is tappable and opens the mail app with `ACTION_SENDTO mailto:`;
    - **Log out**.

    It uses the auth screens' style. System Back closes the app, as on Sign In.
  - **Entering it** clears Salu-Salo's notifications from the shade (`NotificationManagerCompat.cancelAll()`).
  - **`openAuthenticatedHome()`:** after `ensureSupabaseRole()`, it calls `getMyAccountStatus()`.
    - `suspended == true` → `screen = "suspended"` with the reason; no profile load.
    - Null (no answer) → it carries on as today.
  - **`HomeScreen` takes `onSuspended: (String?) -> Unit`.** In the existing hook that runs when the app comes back to the screen (where the alerts reload quietly), it also asks `getMyAccountStatus()`. On `suspended == true` it calls `onSuspended`, which:
    - clears `currentProfile`;
    - sets `screen = "suspended"`.

    Home is disposed, so the ViewModels' `clearSession()` runs as after Log out.
  - **On the suspended screen,** coming back to the app asks again. `suspended == false` → `openAuthenticatedHome()`, so a restored account continues to Home.
  - **Log out from it:** the same Firebase sign-out as Profile's Log out, back to Sign In, and no "session has ended" message.
- **Nothing else changes in the app:**
  - the chat refusal shows through the existing short error text;
  - a donor's reservation cancelled by a suspension shows as "Cancelled";
  - after a restore, Home's start registers the push token again.

## 3. Dashboard (`admin/`)

- **`js/data.js`:** `suspendUser(id, reason, note)` and `restoreUser(id, note)`. Each calls its RPC, then reloads the counts, and is reported as done even when only the count reload fails, like the existing actions. Also the reason labels and the `SUSPENDED` filter option.
- **`js/pages/users.js`:**
  - **List:** a red "Suspended" badge in the list and on the details page, and "Suspended" in the Show filter.
  - **Details, not suspended:** the danger button **Suspend account**. Its dialog has:
    - the reason (a select of the 5 labels);
    - a private note (required, up to 1,000 characters, "Only admins see this note.");
    - "This will remove N active listing(s) and cancel M open reservation(s)."
    - Suspend, with a spinner and the dialog kept open on an error.

    Toast "Account suspended."
  - **Details, suspended:** a panel ("Suspended <date> by <admin>: <reason label>", the note) and **Restore account**, which asks to confirm with an optional note. Toast "Account restored."
  - Neither button is offered for an admin or for the signed-in admin.
- **Labels elsewhere:**
  - `audit.js`: "Account suspended" and "Account restored", target type User;
  - `reports.js`: the resolution "Account suspended";
  - `timeline.js`: the ending "Cancelled: account suspended".
- **Tests:**
  - `tests/data.test.js` gets suspend and restore cases: the RPC names and arguments, success, an RPC error, and a count-reload failure after a success;
  - `tests/timeline.test.js` gets the new ending.
- **Deploy:** `npx firebase-tools deploy --only hosting`.

## 4. Testing

### 4.1 Local, before the phone test

- **SQL dry run:** on a local PostgreSQL 16 with stand-ins for Supabase's roles, `auth.jwt()`, Storage and Cron, all migrations apply on an empty database, and this one applies twice. §1.5 must fail before the migration and pass 18/18 after it, and the earlier checks must still pass.
- **App:** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --offline`. All tests pass (107 plus `SuspensionTest`), with 0 lint errors.
- **Dashboard:** `for t in admin/tests/*.test.js; do node $t; done` passes, plus `node --check` on every changed script.
- **Review:** a fresh-context review of the branch before the phone test.

### 4.2 Rollout order (the developer)

1. Run the migration in the SQL Editor, then §1.5 (expect 18 passed).
2. Deploy the dashboard.
3. Install the build on both phones.

Older builds keep working for everyone who isn't suspended.

### 4.3 Phone test (two phones and the dashboard on the PC)

Phone 1 is A, the admin account, also used as a normal user. Phone 2 is B, the account to suspend. The dashboard is at https://salusalo.web.app, signed in as A. Every reservation uses the 2-hour pickup.

**Setup**
1. [Setup] Phone 2: B posts a free listing ("Suspend test B", 3 units, no per-person limit). Phone 1: A reserves 1 of it.
2. [Setup] Phone 1: A posts a free listing ("Suspend test A", 3 units, no per-person limit). Phone 2: B reserves 1 of it.
3. [Setup] Phone 1: A messages B about "Suspend test B" ("Before suspend"), then reports B with the chat's flag (any reason).

**Suspend**
4. [New] Dashboard: Users → B → Suspend account.
   - The dialog says "This will remove 1 active listing and cancel 1 open reservation."
   - Pick "Repeated no-shows at pickup", type the note "Suspend test", then Suspend.
   - Expected: the toast "Account suspended.", B with the Suspended badge, and Show → Suspended listing B.
5. [New] Phone 1 gets two pop-ups:
   - "Listing removed" / "Suspend test B was removed by Salu-Salo, so your reservation was cancelled.";
   - "Reservation cancelled" / "<B>'s reservation of Suspend test A was cancelled. The quantity is available again."

   "Suspend test B" is gone from Home, and "Suspend test A" shows 3 available again.
6. [New] Phone 2 (it was on Home): press the Home button, then open Salu-Salo again.
   - Expected: "Your account has been suspended", "Reason: Repeated no-shows at pickup", and the email line.
   - Tapping the address opens the mail app with it filled in.
   - Phone 2 got no pop-up from steps 4–5.
7. [New] Phone 2: Log out → Sign In. Sign in as B: the suspended screen again, not Home.

**While suspended**
8. [New] Phone 1: in the chat with B, send "After suspend". Expected: "This person can't receive messages right now."
9. [New] Dashboard:
   - Audit log: "Account suspended" for B with the note, and "Listing removed" for "Suspend test B".
   - Reports → Resolved: A's report on B, "Account suspended".
   - Reservations: B's reservation of "Suspend test A" ends "Cancelled: account suspended".

**Restore**
10. [New] Dashboard: B → Restore account → confirm. Expected: the toast "Account restored.", and the Audit log shows "Account restored".
11. [New] Phone 2: press the Home button, then open Salu-Salo again.
    - Expected: Home.
    - Activity → My Listings → Closed: "Suspend test B" Removed.
    - Alerts: "Your listing was removed" / "Salu-Salo removed Suspend test B: Account suspended".
    - B reserves 1 of "Suspend test A".
12. [New] Phone 1: A messages B ("After restore"). Phone 2: the pop-up "New message from <A>".

**Regression**
13. [Regression] Phone 1:
    - A posts a listing and deletes it;
    - the dashboard's Overview, Listings, Reports and Notifications pages load;
    - A non-admin account signing in to the dashboard still gets "This account doesn't have admin access."

**Clean-up:** remove "Suspend test A" and cancel B's reservation of it.

**Left out:** an old app build while suspended; suspending while B is in the middle of an action; a suspended account receiving an announcement.

## 5. Docs

- `docs/PROJECT_STATUS.md` §36 (what the user sees, what changed, checks, the phone test, known limits), the status rows, and the results log.
- `CLAUDE.md`: the roadmap line, the Supabase objects entry, and the security rule: *users' suspension columns change only through `admin_suspend_user` and `admin_restore_user`; `is_salusalo_firebase_jwt()` must keep the suspension check; `get_my_account_status()` is the only function that skips it*.
- `supabase/README.md`: the 20261017 entry.
- The PR #32 merge is recorded on this branch once it happens.

## Known limits

- Someone suspended can sign up again with another email.
- While Home is open, a suspended person's actions fail with the usual error texts until the app next comes back to the screen.
- Their past reviews, ratings and old chats stay visible to others.
- The Firebase account stays enabled; only Salu-Salo's server refuses it.
- Only an admin's Restore ends a suspension.
- Restore doesn't bring back the removed listings or cancelled reservations.
- Every admin can suspend and restore (no per-admin permissions, as in §25).
