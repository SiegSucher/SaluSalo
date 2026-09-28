# Salu-Salo Supabase setup

This folder contains the Phase 1 database and Storage foundation.

## Apply the database migration

In the Supabase dashboard for qzctlsyaksurvrykzhwf:

1. Go to SQL Editor.
2. Open migrations/20260921_phase1_foundation.sql.
3. Run the entire file.
4. Confirm that public.users, food-images, and profile-images exist.

The migration enables RLS and gives no access to anonymous clients. It does not
grant users any way to create an ADMIN profile.

## Required Firebase third-party authentication setup

Before the Android app can read or create user profiles:

1. In Supabase, open Authentication > Third-Party Auth.
2. Add a Firebase Auth integration with Firebase project ID salusalo.
3. Every Firebase user needs the custom claim role: authenticated in the ID
   token. Deploy the `set-firebase-role` Edge Function (next section): the app
   calls it once for an account whose token lacks the claim, then refreshes the
   token. The developer's local Admin SDK script (not in this repo) stays as a
   backup; the app picks up a claim it set on the next token refresh.
4. Existing Firebase users that already have the claim need nothing new; any
   without it get it the same way on their next sign-in.

Never put a Firebase Admin credential or Supabase service-role key in this app.

## Edge Function `set-firebase-role` (the role for new accounts)

`functions/set-firebase-role/index.ts` gives the **calling** Firebase account
the custom claim `role: "authenticated"` and nothing else. It verifies the
caller's Firebase ID token itself (signature against Google's keys, issuer and
audience `salusalo`, expiry), takes the account only from that token, refuses
anonymous, disabled, deleted and revoked accounts, keeps every other custom
claim, and answers at once without calling Google when the token already has
the role. It has no dependencies. It needs a Google Cloud service account whose
only role is a custom role with two permissions, and that account's JSON key as
the Edge Function secret `FIREBASE_AUTH_ADMIN_KEY`. The key must never be put in
the app, this repo, the docs, a chat or a log. There is no SQL.

The app build that calls it (branch `claude/auto-role-claim`) can be installed
before or after the setup: until the function works, a new account gets the
error with **Try again**, and the backup script still works.

### One-time setup

**A. Custom role (Google Cloud console)**

1. Open https://console.cloud.google.com and pick the project **salusalo** (the
   Firebase project) in the project picker at the top.
   **Check that the box at the top left says salusalo before creating anything:**
   the console opens on the project used last, and a key from another project
   is refused (the function answers 500 "the FIREBASE_AUTH_ADMIN_KEY secret is
   missing or invalid"). These links open the right project directly:
   https://console.cloud.google.com/iam-admin/roles?project=salusalo and
   https://console.cloud.google.com/iam-admin/serviceaccounts?project=salusalo
2. Menu ☰ → **IAM & Admin** → **Roles** → **+ Create role**.
3. Title `Salu-Salo role setter`, ID `salusaloRoleSetter`, Role launch stage
   **General Availability**.
4. **+ Add permissions** → **Filter permissions by role** → **Firebase
   Authentication Admin** → tick `firebaseauth.users.get` and
   `firebaseauth.users.update` → **Add**. (Typing the permission names into the
   filter didn't find them on 2026-09-28.) The role lists exactly these two
   permissions → **Create**.
   - `firebaseauth.users.get` is what `accounts:lookup` (reading the account's
     claims) needs, and `firebaseauth.users.update` what `accounts:update`
     (writing them) needs. Nothing else is needed.
   - If either permission isn't offered for custom roles, use the last resort
     under "If the function answers 502" below.

**B. Service account**

5. ☰ → **IAM & Admin** → **Service Accounts** → **+ Create service account**.
6. Service account name `salusalo-role-setter` (the ID fills in), description
   `Adds role: authenticated for the set-firebase-role Edge Function` →
   **Create and continue**.
7. **Select a role** → **Custom** → **Salu-Salo role setter** → **Continue** →
   **Done**. Add no other role and no principals.

**C. JSON key**

8. Check that the account's email ends with `@salusalo.iam.gserviceaccount.com`;
   any other ending means it is in another project (delete it and start again
   at step 1). Click `salusalo-role-setter@salusalo.iam.gserviceaccount.com` → **Keys** tab
   → **Add key** → **Create new key** → **JSON** → **Create**. The browser
   downloads a file named like `salusalo-<12 characters>.json`.
   - If Google Cloud says key creation is disabled, an organisation policy
     (`iam.disableServiceAccountKeyCreation`) blocks it: someone with the
     Organization Policy Administrator role must allow it for project salusalo
     (☰ → **IAM & Admin** → **Organization Policies**).

**D. API**

9. ☰ → **APIs & Services** → **Enabled APIs & services**: **Identity Toolkit
   API** must be in the list (Firebase Authentication normally turned it on). If
   it isn't: **+ Enable APIs and services** → search `Identity Toolkit API` →
   **Enable**. No other API is needed.

**E. Supabase secret**

10. Supabase dashboard → the Salu-Salo project → **Edge Functions** →
    **Secrets**. Name `FIREBASE_AUTH_ADMIN_KEY`; value: open the downloaded file
    in a text editor and copy all of it, from the first `{` to the last `}` →
    **Save**. A later change to the secret needs no redeploy.

**F. Deploy with JWT verification off**

11. **Edge Functions** → **Deploy a new function** → **Via Editor**. Set the
    function's name to `set-firebase-role` **before the first deploy** (exactly
    this: the app calls it at that address). Renaming it later changes only its
    label, not its address (on 2026-09-28 the editor's random `quick-api`), and
    the app then gets 404 "Requested function was not found". In the Edge
    Functions list, the **URL** column must end with
    `/functions/v1/set-firebase-role`; if not, delete the function and deploy
    again.
    Replace all of the sample code in `index.ts` with the whole of
    `functions/set-firebase-role/index.ts` from this repo → **Deploy function**.
12. Open the function (**Edge Functions** → `set-firebase-role`) and, in its
    settings, turn **off** the switch that verifies the JWT (enforces a
    Supabase-signed JWT) → save.
    - It must be off: Supabase's gateway accepts only JWTs signed by this
      Supabase project, and the app sends a Firebase ID token, which the
      function verifies itself. With it on, every call from the app gets 401.
    - With the Supabase CLI this would be the following in
      `supabase/config.toml`. This repo has no CLI config, so the dashboard
      switch is the setting.

      ```toml
      [functions.set-firebase-role]
      verify_jwt = false
      ```

**G. The downloaded key**

13. Delete the downloaded JSON file and empty the Recycle Bin or Trash, or keep
    it only outside every repository folder (for example in a password
    manager). `.gitignore` ignores `salusalo-*.json` and
    `*service-account*.json` as a safety net, not as a place to keep it.

**H. Check**

14. **Edge Functions** → `set-firebase-role` → **Test** (the button at the top
    right of the function's page): send a POST (any
    headers the panel adds are not a Firebase token). Expected: HTTP **401**
    with the body `{"error":"Invalid or missing Firebase ID token."}`, and the
    function's **Logs** show one line `set-firebase-role: refused (...)` with a
    reason such as `no_token`, `token_format` or `token_header`.
    - A boot error instead (the function doesn't start) means this Edge Runtime
      doesn't take the `export default { fetch }` form, although Supabase's
      docs say it is equivalent to `Deno.serve`. Then, in the editor, replace
      the last block of `index.ts`, from `export default {` to the end, with
      the block below, deploy again and repeat this check:

      ```ts
      Deno.serve((request: Request) =>
        handle(request, {
          fetch,
          nowSeconds,
          keyFor: googleKeyLookup(fetch, nowSeconds),
          secret: () => Deno.env.get(SECRET_NAME),
        })
      );
      ```

### Logs and what they mean

The function logs one line per call and never the token, the key, the Google
access token, Google's error body or a full UID (at most its first 6
characters):

- `role set (uid abc123…)`: a new account got the role. Expect one per new
  account.
- `role was already set (uid …)` / `role already in the token (uid …)`: nothing
  was changed.
- `refused (<reason>)`: `method`, `no_token`, `token_<check>` (for example
  `token_expired`, `token_signature`, `token_project`), `anonymous`,
  `account_not_found`, `session_revoked`, `account_disabled`.

### If the function answers 500 or 502

- `the FIREBASE_AUTH_ADMIN_KEY secret is missing or invalid` (500): step 10 —
  the whole JSON key of a service account of project `salusalo`.
- `access token failed with HTTP 400` or `401` (502): Google refused the key.
  It was deleted or disabled, or the secret holds an old key: create a new key
  (step 8) and replace the secret (step 10).
- `accounts:lookup failed with HTTP 403` (502): the Identity Toolkit API isn't
  enabled (step 9), the role lacks `firebaseauth.users.get` (step 4), or the
  role was granted only a few minutes ago (IAM changes can take a few minutes).
- `accounts:update failed with HTTP 403` (502): the role lacks
  `firebaseauth.users.update` (step 4).
- Last resort, if a 403 stays with both permissions in the role: ☰ → **IAM &
  Admin** → **IAM** → the pencil next to `salusalo-role-setter` → replace the
  custom role with **Firebase Authentication Admin** → **Save**. That role can
  also create and delete accounts, so a leaked key could do more; go back to
  the custom role when the cause is found.

### The key

- It doesn't expire. It can read any account of the Firebase project and change
  its details and claims, but (with the custom role) can't create or delete
  accounts. Treat it like a password.
- If it ever leaks (committed, pasted in a chat, sent anywhere): ☰ → **IAM &
  Admin** → **Service Accounts** → `salusalo-role-setter` → **Keys** → the
  key's delete (trash) icon → **Delete**. Then create a new key (step 8), put it
  in the secret (step 10) and delete the file (step 13). The old key stops
  working at once: it can't get new Google access tokens. An access token
  already issued with it expires within 1 hour at the latest.
- Rotating the key changes nothing for users: tokens that already carry the
  role keep it.
## Phase 2: food listings

Run `migrations/20260923_phase2_food_posts.sql` in the Supabase SQL Editor after
Phase 1. It creates `food_posts`, enables RLS, limits writes to the logged-in
Firebase user's matching profile, and updates the existing private `food-images`
bucket policies. Images remain private; the app creates short-lived signed URLs
for authenticated users to view listing images.

## Phase 3: reservations

Run `migrations/20260924_phase3_reservations.sql` after Phases 1 and 2. It creates the `reservations` table and the `create_reservation`, `cancel_reservation`, and `claim_reservation` RPCs. Do not create reservations by inserting rows from the client: the RPCs lock the food post and change quantity atomically.

Run migrations/20260924_phase3_donor_recipient_names.sql after Phase 3 if Phase 3 was already applied. It securely exposes the recipient name only to the donor of that reservation's food post.

## Phase 4: messaging and notifications

Run `migrations/20260926_phase4_messaging_notifications.sql` after Phases 1–3 (including the Phase 3 donor-names patch). It creates `conversations`, `messages`, `notifications` and `notification_preferences`, the messaging RPCs (`start_listing_conversation`, `open_reservation_conversation`, `send_message`, `mark_conversation_read`, `mark_notifications_read`, `get_my_conversations`) and a trigger on `reservations` that creates reservation alerts. It also adds `messages` and `notifications` to the `supabase_realtime` publication; check that both appear under Database → Publications → `supabase_realtime` afterwards.

Clients can only read conversations, messages and alerts. Messages are sent only through `send_message`, and alerts are created only by the database (`notify_user` is not callable by clients). Participants see each other's display name only, through `get_my_conversations`; `users` RLS is unchanged. Deleting a listing also deletes its conversations, messages and alerts. The migration is safe to run again.

## Listing prices, Purchases and Claims

Run `migrations/20260927_activity_prices.sql` by hand in the SQL Editor after Phases 1–4, **before installing the app build that uses it** (Create Listing sends `price` and fails until the column exists). It adds:

- `food_posts.price` (per unit, `numeric(10,2)`, 0–99,999.99; 0 = free donation, so existing listings become free);
- `reservations.unit_price`, set only by the `reservations_set_unit_price` trigger from the listing's price when the reservation is created. A later price change does not affect existing reservations. The Phase 3 RPCs are unchanged and remain the only way to write reservations;
- `unit_price` in `get_donor_reservations()` (recreated; still recipient names only).

The migration is safe to run again. **Do not run `20260924_phase3_reservations.sql` or `20260924_phase3_donor_recipient_names.sql` again after it:** they recreate `get_donor_reservations()` with the old return type and fail ("cannot change return type of existing function"). If you ever need to, drop the function first, run them, then run this migration again.

## Profile name and photo

Run `migrations/20260928_profile_updates.sql` by hand in the SQL Editor after `20260927_activity_prices.sql`. It adds two SECURITY DEFINER functions, `update_my_profile_name(p_name)` and `set_my_profile_image(p_path)`, which change only the caller's own `users` row, found from the token's `sub`. The name is trimmed and must be 1–120 characters; the photo path must be an existing `profile-images` object inside the caller's own folder. They never change `role`, `email` or `firebase_uid`. `users` still has no UPDATE grant or policy for clients, so these functions are the only way the app changes a profile. The migration is safe to run again.

## Listing details, editing and saved listings

Run `migrations/20260929_phase2_listing_details.sql` by hand in the SQL Editor after `20260928_profile_updates.sql`, **before** installing the app build that uses it (Create Listing sends the new columns and would fail without them). It is safe to run again.

- **New `food_posts` columns**, all optional, so older listings stay readable: `pickup_start`, `pickup_end` (both or neither; the end after the start and on or before `expiration_date`), `handover` (`MEET_AT_PICKUP_LOCATION` or `COLLECT_FROM_PROVIDER`), `allergens` and `storage_notes` (up to 300 characters each) and `extra_image_paths` (up to 3 more `food-images` paths; `image_url` stays the cover).
- **Photo paths.** The insert policy is recreated with one extra rule: `image_url` and every extra path must be inside the caller's own `<firebaseUid>/<postId>/` folder and contain no `..`.
- **No direct updates.** The Phase 2 policy "Users may update their own food posts" is dropped and UPDATE on `food_posts` is revoked from `authenticated`. Listings now change only by insert, the reservation functions and `update_my_food_post(...)`. **Do not run `20260923_phase2_food_posts.sql` again after this file**: it would restore the UPDATE grant and policy.
- **`update_my_food_post(...)`** (SECURITY DEFINER) edits the caller's own, unexpired listing: name, description, category, price, pickup location, expiry, pickup window, handover, allergens and storage notes. It locks the row, and never changes quantity, unit, status, donor, photos or `created_at`. Existing reservations keep their `unit_price`.
- **`get_listing_donor_names(uuid[])`** (SECURITY DEFINER) returns only the donor's name, and only for listings the caller may see under the `food_posts` SELECT policy (available and unexpired, their own, or one they reserved). `users` RLS is not widened.
- **`saved_listings`** (bookmarks): the caller's own rows only (select, insert, delete; no update; nothing for `anon`). A listing can be saved only if the caller can see it and it is not their own. Rows are deleted with their listing or user.

## Phase 3 demo fixes: removing listings, saved names

Run `migrations/20260930_phase3_demo_fixes.sql` by hand in the SQL Editor after `20260929_phase2_listing_details.sql`. It is safe to run again. **Install the new app build on both phones right after running it: older builds break.** They cannot read a `REMOVED` listing or a `RESERVATION_REMOVED` alert, so any list that contains one (My Listings, My Reservations, Alerts) fails to load, and their Delete button uses a direct DELETE, which is now refused and silently does nothing.

- **New values.** `food_posts.status` may be `REMOVED`, and `notifications.type` may be `RESERVATION_REMOVED`. Every earlier check constraint on those two columns is dropped (whatever its name) and one new one is added.
- **`reservations.cancel_reason`** (new, nullable): `LISTING_REMOVED` when the reservation was cancelled because its donor removed the listing; NULL otherwise. Allowed only on `CANCELLED` rows.
- **No direct DELETE.** The policy "Users may delete their own food posts" is dropped and DELETE on `food_posts` is revoked from `authenticated`. **Do not run `20260923_phase2_food_posts.sql` again after this file**: it would restore the delete policy (and the update policy).
- **`remove_my_food_post(p_food_post_id)`** (SECURITY DEFINER) is the only way to delete a listing, and only the caller's own:
  - never reserved: the row is deleted, with its saved rows, chats and alerts; the app then deletes its photos (best effort). Returns `('DELETED', 0)`;
  - reserved at least once: the status becomes `REMOVED` and the quantity stays as it was. Open reservations are cancelled with `cancel_reason = 'LISTING_REMOVED'`, each of their recipients gets one `RESERVATION_REMOVED` alert (it follows the "Reservation updates" switch; the donor gets none), and saved rows are deleted. Past reservations, photos and chats are kept. Returns `('REMOVED', n)`, n = open reservations cancelled;
  - already removed: returns `('REMOVED', 0)` and changes nothing.
  It locks the listing's open reservations first and then the listing, the same order as `cancel_reservation` and `claim_reservation`, so running at the same time as either waits instead of deadlocking.
- **A removed listing** is hidden from everyone except its donor and its past recipients (it is no longer `AVAILABLE`). `create_reservation` and `update_my_food_post` refuse it, `start_listing_conversation` refuses a new chat about it, and it cannot be saved. `cancel_reservation`, `claim_reservation`, the price trigger and the other Phase 4 functions are unchanged. `notify_reservation_change` sends nothing for a `LISTING_REMOVED` cancellation.
- **`get_my_saved_listing_names()`** (SECURITY DEFINER) returns the name and cover path of the caller's own saved listings, including ones the caller can no longer read (expired or fully reserved), so Saved Listings can still show them.
- **Known limit:** someone who only chatted about a listing (no reservation) keeps that chat after the listing is removed.

## Phase 4 demo fixes: response rate

Run `migrations/20261001_phase4_demo_fixes.sql` by hand in the SQL Editor after `20260930_phase3_demo_fixes.sql`. It is safe to run again, and it can run before or after installing the new app build: it only adds a function that older builds never call. Until it has run, the new build's Profile shows "—" for Response Rate and the Response Rate page shows its "could not be loaded" message; everything else works.

- **`get_my_response_rate()`** returns one row, `(received, replied, period_days)`, for the caller only (taken from the token; the function has no arguments). Over the last 30 days: `received` counts the caller's conversations whose first message was sent by the other participant within the last 30 days; `replied` counts those in which the caller sent a message after that first message. Messages are ordered by `created_at`, then `id`. The app shows `round(replied / received × 100)`, or no rate when `received` is 0.
- **SECURITY INVOKER**, `stable`, `search_path = public`. It filters explicitly on the caller being the conversation's recipient or donor, and the existing `conversations` and `messages` RLS applies on top, so it needs no extra rights. Executable by `authenticated` only (revoked from PUBLIC and `anon`).
- **Nothing else changes:** no table, column, policy, table grant or trigger, and no Phase 3 or Phase 4 function. Clients still only read `conversations`, `messages` and `notifications`.
- **Known limits:** a note sent with a reservation starts a chat, so it counts for the donor; chats of a listing deleted before anyone reserved it are deleted with it and drop out of the rate; a reply counts however late it comes.

After running it, run `tests/phase4_demo_fixes_check.sql` (below).

## Phase 5 part 1: reviews, reports, Email verified

Run `migrations/20261002_phase5_reviews_reports.sql` by hand in the SQL Editor after `20261001_phase4_demo_fixes.sql`, **before** installing the app build that uses it: that build loads donor names through the new `get_listing_donor_profiles`, so without the migration it shows "Donor name unavailable" and its review, report and verification calls fail. It is safe to run again. **Older builds are unaffected:** supabase-kt ignores the new `users` column, they never call the new functions, and `get_listing_donor_names` is unchanged.

- **Every new function the app calls** is SECURITY DEFINER with `search_path = public`, checks `is_salusalo_firebase_jwt()` first ("Not authenticated"), takes the caller only from `current_salusalo_profile_id()` ("Profile not found"), and is executable by `authenticated` only (revoked from PUBLIC and `anon`). The helper `caller_can_view_food_post` and the trigger function `users_start_unverified` are executable by no client.
- **`users.email_verified`** (boolean, default false). A BEFORE INSERT trigger always stores false, whatever an insert sends; the users insert policy is unchanged. Only **`sync_my_email_verified()`** changes it: true only when the caller's verified token has `email_verified` = JSON true and its `email` equals the profile's email (trimmed, ignoring case). It changes nothing else (`updated_at` only when the value changes) and returns the stored value. Clients still have no UPDATE on `users`.
- **`caller_can_view_food_post(uuid)`** (internal) is exactly the `food_posts` SELECT policy, like `get_listing_donor_names`: available and unexpired, the caller's own, or one the caller reserved. **If that policy changes, change both functions.**
- **`reviews`**: one row per reservation (`reservation_id` unique), rating 1–5, comment up to 500 characters (trimmed; empty = none), reviewer and reviewee (the listing's donor). RLS on, and no privileges at all for `anon` or `authenticated` (not even SELECT).
  - **`submit_review(p_reservation_id, p_rating, p_comment)`**: only the recipient of a CLAIMED reservation (a removed listing's too); the person rated is the listing's `donor_id`, never the caller. Sending again changes the review; the result's `created` says whether it was new.
  - **`get_my_given_reviews()`**: the caller's own reviews, by reservation, for "Change your review".
  - **`get_listing_donor_profiles(uuid[])`**: for a list of listings the caller may see, in one call: the donor's name, `email_verified`, and the rating average (one decimal) and count.
  - **`get_listing_donor_reviews(p_food_post_id, p_limit default 20)`** (at most 50), **`get_my_rating_summary()`** and **`get_my_received_reviews(p_limit default 50)`** (at most 100). They return only the reviewer's current name, the stars, the comment and the dates.
- **`reports`**: reporter, reported person, `target_type` (`LISTING` or `USER`), `source` (`LISTING`, `DONOR_PAGE` or `CHAT`), `food_post_id` and `conversation_id` (set to NULL if a never-reserved listing is deleted later), `listing_name` (the name when reported), `reason`, `details` (up to 1000 characters; required for `OTHER`), `status` (only `OPEN` for now, for the Phase 6 admin screen), `created_at`. A check constraint allows each reason only for its target: `UNSAFE_FOOD`, `MISLEADING`, `SPAM_FAKE`, `OTHER` for a listing; `ABUSIVE`, `NO_SHOW`, `SCAM`, `SPAM`, `OTHER` for a person. RLS on, no privileges at all for clients: they can't read, change or delete reports.
  - **`submit_report(p_target, p_target_id, p_reason, p_details)`**: `LISTING` or `DONOR` with a listing the caller may see and that is not their own (reports the listing, or its donor), or `CHAT` with one of the caller's own conversations (reports the other participant). The reported person is found on the server and is never the caller. One open report per reporter and listing, and per reporter and person (a check, plus partial unique indexes; a race gets the same "You already reported this."). At most 10 reports per reporter in any 24 hours, counted under a per-reporter advisory lock. The reported person is not told.
  - To read the reports, run `tests/phase5_reports_query.sql` (read-only) in the SQL Editor.
- **Nothing else changes:** no Phase 3 or Phase 4 function, trigger or policy, and no RLS is widened.

After running it, run `tests/phase5_reviews_reports_check.sql` and `tests/phase5_live_safety_check.sql` (below).

## Phase 5 part 2: maps (pickup pin)

Run `migrations/20261003_phase5_maps.sql` by hand in the SQL Editor after `20261002_phase5_reviews_reports.sql`, **before** installing the app build that uses it: that build sends the pin with every new listing and saves edits through `update_my_food_post_with_pin`, so without the migration its Post Listing and Save changes fail. It is safe to run again. **Older builds are unaffected:** supabase-kt ignores the two new columns, their new listings get no pin, and they still edit through `update_my_food_post`, which this file does not change and which never touches the pin.

- **`food_posts.pickup_latitude numeric(8,6)` and `pickup_longitude numeric(9,6)`**, both nullable; existing listings get NULL. The check constraint `food_posts_pickup_pin_check` requires both set or both NULL, latitude from -90 to 90 and longitude from -180 to 180 (it also refuses NaN).
- **Who sees the pin:** it is part of the listing row, so exactly the people the existing `food_posts` SELECT policy lets see the listing. No policy or grant changes; clients still have SELECT and INSERT only on `food_posts`, and the insert policy is unchanged (a new listing sends its pin in the insert).
- **`update_my_food_post_with_pin(...)`**: the 12 arguments of `update_my_food_post` plus `p_pickup_latitude` and `p_pickup_longitude`. SECURITY DEFINER with `search_path = public`, executable by `authenticated` only (revoked from PUBLIC and `anon`). It takes the caller from the token, locks the listing, and refuses someone else's, a `REMOVED` and an expired listing with the same texts as `update_my_food_post`; then it refuses a half-set pin ("Send both pickup pin coordinates, or neither.") or one out of range ("The pickup pin is not a valid map position."); then it calls `update_my_food_post` for every other field and its checks, and stores the pin rounded to 6 decimals. One transaction: when anything is refused, nothing changes. A pin can be added or moved but not removed: with both pin arguments NULL the stored pin (or its absence) stays.
- **The phone's location** is used on the phone for distances and is never sent to Supabase or stored. The one exception is "Use my location" on Post and Edit, which puts the pin at the phone's position; the donor then saves that pin with the listing.
- **Nothing else changes:** no Phase 3, 4 or 5 function, trigger or policy, and no RLS is widened. The server does not require a pin, so older builds can still post; the new build requires one on Post.

After running it, run `tests/phase5_maps_check.sql` and `tests/phase5_maps_live_safety_check.sql` (below).

## Profile bio (Mika's PR #10)

Run `migrations/20261004_profile_bio.sql` by hand in the SQL Editor after `20261003_phase5_maps.sql`, **before** installing the build with the new Edit Profile page: its Save changes calls `update_my_profile_details`, so without the migration it fails. It is safe to run again. **Older builds are unaffected:** they ignore the new column and still rename through `update_my_profile_name`, which this file does not change.

- **`users.bio text`**, nullable, with the check `users_bio_length_check` (at most 160 characters), which also covers a profile's first insert.
- **`update_my_profile_details(p_name, p_bio)`**: SECURITY DEFINER with `search_path = public`, executable by `authenticated` only. It takes the caller from the token (`auth.jwt() ->> 'sub'`), changes only that user's name (1–120 characters) and bio (at most 160; blank is stored as NULL), and never changes `role`, `email` or `firebase_uid`.
- **Who sees the bio:** only its owner, because `users` rows can be read only by their owner. No policy or grant changes; clients still have SELECT and INSERT only on `users`.

After running it (in the same run is fine), run `tests/profile_bio_check.sql`: it only reads the catalog and returns 6 rows (PASS or FAIL): the column, the check, the function's settings and grants, that it takes the caller from the token and never sets `role`, `email` or `firebase_uid`, that clients still have no UPDATE on `users`, and that `update_my_profile_name` still exists.

## Safety checks (`tests/`)

The files in `tests/` are not migrations and change nothing. `tests/phase4_live_safety_check.sql` checks that the live Phase 4 rules still refuse what they should. Paste it into the SQL Editor (role `postgres`) and run it. It pretends to be the two participants of the most recent chat and the newest other account, tries forbidden actions, and reports each check as PASS, FAIL or SKIP. It always ends with an error on purpose (`SAFETY CHECK FINISHED: …`); the error rolls back everything the check did. It needs at least one chat with messages, and a third account for the outsider checks.

After running `20260930_phase3_demo_fixes.sql`, run two more files the same way:

- `tests/phase3_demo_fixes_check.sql` only reads the catalog and returns one row per check (PASS or FAIL): the new constraints, that `food_posts` has no DELETE or UPDATE policy or privilege, and the grants of the new and changed functions.
- `tests/phase3_live_safety_check.sql` works like the Phase 4 check and also always ends with an error that undoes everything. It picks the newest listing that is unexpired and not removed, its donor and the newest other account. It checks that a direct DELETE by the donor, a removal by the outsider, without a login or with another project's token are refused, that the outsider's saved names are their own only, and, after marking the listing `REMOVED`, that it can no longer be edited, reserved, chatted about, saved or seen. Checks it cannot run are reported as SKIP.

After running `20261001_phase4_demo_fixes.sql`, run `tests/phase4_demo_fixes_check.sql`. It only reads the catalog and returns one row per check (PASS or FAIL): that `get_my_response_rate()` exists with its result columns, is SECURITY INVOKER with `search_path=public` and is executable by `authenticated` but not by `anon` or PUBLIC; that clients still have no INSERT, UPDATE or DELETE and only SELECT policies on `conversations`, `messages` and `notifications`; that `notify_user` is still not executable by clients; that `messages` and `notifications` are still in `supabase_realtime`; and that there is still no UPDATE on `users` and no UPDATE or DELETE on `food_posts` for clients.

After running `20261002_phase5_reviews_reports.sql`, run two more files the same way:

- `tests/phase5_reviews_reports_check.sql` only reads the catalog and returns one row per check (PASS or FAIL): the new column, trigger, tables, constraints and indexes; that clients have no privileges or policies on `reviews` and `reports`; that each new function is SECURITY DEFINER with `search_path=public` and executable by `authenticated` only, and the helper and trigger function by no client; that `get_listing_donor_names` still returns its two columns; and that clients still have no UPDATE on `users`, no UPDATE or DELETE on `food_posts`, and no writes on `reservations`, `conversations`, `messages` and `notifications`.
- `tests/phase5_live_safety_check.sql` works like the Phase 3 and Phase 4 checks and also always ends with an error that undoes everything. It picks the newest claimed reservation, its recipient and donor, and the newest other account (the outsider). It checks that only the recipient can rate, only once per reservation, only while it is claimed and only with valid stars and comments; that nobody reads `reviews` or `reports` directly; that an outsider gets no donor details or reviews for a listing they can't see; that the report refusals work (own listing, yourself, someone else's chat, a listing you can't see, a duplicate, an unknown reason, the daily limit); and that `sync_my_email_verified` sets only the caller's own `email_verified` from the token, while inserts always start unverified.

After running `20261003_phase5_maps.sql`, run two more files the same way:

- `tests/phase5_maps_check.sql` only reads the catalog and the listings and returns one row per check (PASS or FAIL): the two columns and their types, the pin constraint, that `update_my_food_post_with_pin` is SECURITY DEFINER with `search_path=public` and executable by `authenticated` only, that `update_my_food_post` still takes its 12 arguments and never touches the pin, that clients still have only SELECT and INSERT on `food_posts` and no UPDATE or DELETE policy, that the insert and SELECT policies are unchanged, and that no stored pin is half set or out of range (its details give the number of listings with a pin).
- `tests/phase5_maps_live_safety_check.sql` works like the other live checks and also always ends with an error that undoes everything. It picks the donor of the newest listing (or the newest account) and the newest other account, and inserts two test listings as that donor through RLS, like the app. It checks that a pin is stored rounded, that a half-set or out-of-range pin is refused on insert and on edit with nothing changed, that a pin can be added and moved but an edit without a pin (old or new function) keeps it, that the function refuses the outsider, an expired and a `REMOVED` listing, no login and another project's token, that a direct UPDATE is refused, and that the outsider sees the pin only while the listing is visible to them.
