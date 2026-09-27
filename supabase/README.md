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
3. In Firebase, use a trusted Admin SDK / Cloud Function to put
   role: authenticated into the ID token for every user.
4. Existing Firebase users must receive that same custom claim and sign in again
   or refresh their ID token.

Never put a Firebase Admin credential or Supabase service-role key in this app.
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

## Safety checks (`tests/`)

The files in `tests/` are not migrations and change nothing. `tests/phase4_live_safety_check.sql` checks that the live Phase 4 rules still refuse what they should. Paste it into the SQL Editor (role `postgres`) and run it. It pretends to be the two participants of the most recent chat and the newest other account, tries forbidden actions, and reports each check as PASS, FAIL or SKIP. It always ends with an error on purpose (`SAFETY CHECK FINISHED: …`); the error rolls back everything the check did. It needs at least one chat with messages, and a third account for the outsider checks.

After running `20260930_phase3_demo_fixes.sql`, run two more files the same way:

- `tests/phase3_demo_fixes_check.sql` only reads the catalog and returns one row per check (PASS or FAIL): the new constraints, that `food_posts` has no DELETE or UPDATE policy or privilege, and the grants of the new and changed functions.
- `tests/phase3_live_safety_check.sql` works like the Phase 4 check and also always ends with an error that undoes everything. It picks the newest listing that is unexpired and not removed, its donor and the newest other account. It checks that a direct DELETE by the donor, a removal by the outsider, without a login or with another project's token are refused, that the outsider's saved names are their own only, and, after marking the listing `REMOVED`, that it can no longer be edited, reserved, chatted about, saved or seen. Checks it cannot run are reported as SKIP.

After running `20261001_phase4_demo_fixes.sql`, run `tests/phase4_demo_fixes_check.sql`. It only reads the catalog and returns one row per check (PASS or FAIL): that `get_my_response_rate()` exists with its result columns, is SECURITY INVOKER with `search_path=public` and is executable by `authenticated` but not by `anon` or PUBLIC; that clients still have no INSERT, UPDATE or DELETE and only SELECT policies on `conversations`, `messages` and `notifications`; that `notify_user` is still not executable by clients; that `messages` and `notifications` are still in `supabase_realtime`; and that there is still no UPDATE on `users` and no UPDATE or DELETE on `food_posts` for clients.
