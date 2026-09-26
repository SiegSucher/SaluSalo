# Salu-Salo — Project Status

_Last updated: 2026-09-26 (Phase 4 signed off, §8; Purchases, Claims and listing prices merged into `master` through PR #3, migrated, and passed on two phones except the "Reserved" dialog, §9; Phase 1 demo fixes — Google sign-in, Keep me signed in, profile name and photo, password change — merged through PR #3, migrated, and passed on two phones, §10; Phase 2 demo fixes — real donor names, listing details, up to 4 photos, editing, saved listings, expiry — built on branch `claude/phase2-demo-fixes` (PR #5), migrated, and passed on two phones with a regression round, §11). Based on the code at commit `d4f65fe` on `master`, plus the image changes described in §3.2 and §3.3 and the load-failure fixes in §3.4, which were committed together with this document on 2026-09-25, and the known-issue fixes in §3.5 (commit `85c9263`). Manual test results reported on 2026-09-25 are in the [results log](#results-log)._

This is the handoff status document. It was first written from static inspection of the Kotlin code and the Supabase SQL at `d4f65fe`, plus build checks (see [Build verification](#build-verification)). Manual results are recorded only as reported by the tester. Keep it up to date, and never mark an item fixed or tested unless it actually was.

**Legend**
- **Implemented (code):** present in the code at `d4f65fe`, confirmed by reading it.
- **Manually tested:** a recorded run on a device or emulator, listed in the [results log](#results-log). The first results were reported on 2026-09-25; anything not listed there is still unverified.
- **Not yet verified:** not confirmed by a build, a manual run, or both.

Unless stated otherwise, Kotlin paths are relative to `app/src/main/java/com/example/salusalo/`. Line numbers (`L…`) refer to `d4f65fe` and will drift as code changes.

---

## 1. Project status

| Phase | Scope | Status |
|---|---|---|
| Phase 1: Backend Foundation | Firebase email/password auth, Supabase client using Firebase tokens, `users` profiles, private Storage buckets | **Complete.** Do not redo. Authentication/profile regression passed on 2026-09-25 ([results log](#results-log)). |
| Phase 2: Food Listings | `food_posts`, create listing with image upload to `food-images`, feed, My Listings, delete | **Complete.** Do not redo. Regression passed on 2026-09-25 for both accounts: Home/feed, create listing, delete listing ([results log](#results-log)). |
| Phase 3: Reservations | Reserve, cancel and claim with atomic quantity; recipient and donor views | **Complete.** Signed off by the developer on 2026-09-25. The A/B reservation test, the missing-image fallback/feed check, and the cancel, claim and Recommended for You tests passed; the remaining checks without an individual test record are listed under the [results log](#results-log). The changes since `d4f65fe` were committed on 2026-09-25 (§6). The known-issue fixes made after sign-off (§3.5) were built and manually tested on 2026-09-25. |
| Phase 4: Messaging and Notifications | Chat per listing (recipient ↔ donor), in-app alerts for messages and reservation changes, live updates through Supabase Realtime, notification preferences stored in Supabase (§8) | **Complete.** Signed off by the developer on 2026-09-25. Migration applied to the live project. At `266325b` the one-phone A/B test and regression passed, except saving a preference switched back on. At `c7323b8` the two preference fixes and all the live two-phone checks passed, including live alerts after more than 1 hour, and a new account signed up. The live safety check (negative tests) passed 21 of 21. Not tested: the password-reset email, whose code Phase 4 did not change (§8). |
| Activity: Purchases, Claims, listing prices (between Phases 4 and 5; not part of Phase 5) | Stored listing prices, the price locked into each reservation, real Purchases and Claims tabs, real Activity totals (§9) | **Merged into `master` through PR #3 on 2026-09-26, together with the Phase 1 demo fixes, before its own device test. Built with AGP and migration applied to the live project on 2026-09-26. The §9.4 two-phone test passed on 2026-09-26, except the "Reserved" dialog after Reserve, which never appears: a Phase 3 bug present since `d4f65fe`, left for the Phase 3 demo fixes (§9.6).** |
| Phase 1 demo fixes: accounts, profile, security (fixes to finished Phase 1 features; not part of Phase 5) | Google sign-in, a working "Keep me signed in", honest sign-in texts, profile name and photo saved to Supabase, real password change, real Member since; location, Privacy and Signed-in devices removed (§10) | **Complete.** Merged into `master` through PR #3 on 2026-09-26; built with AGP (`googleid` 1.2.0, new `google-services.json`) and migration applied the same day. The developer ran the Phase 1 checks on two phones on 2026-09-26 and reported that they passed ([results log](#results-log)). "Keep me signed in" starting unchecked was changed after PR #3; the tested build included it (§10). |
| Phase 2 demo fixes: listings (fixes to finished Phase 2 features; not part of Phase 5) | Real donor names, no ratings or distances, newest-first feed, the listing's own pickup address, pickup window, handover, allergens and storage notes saved and shown, up to 4 photos, Edit listing, real Saved Listings, "Expired" from the expiry date (§11) | **Passed.** Built with AGP on branch `claude/phase2-demo-fixes` at `4b6aac6` (PR #5), and migration `20260929_phase2_listing_details.sql` applied to the live project on 2026-09-26. The §11.4 two-phone test (steps 1–25) and its regression round (steps 26–39) passed the same day ([results log](#results-log), §11.6). New small issues are listed in §11.5. |
| Phase 5: Maps, Reviews, Reports | Known only by its roadmap title; not yet specified in the repo | **Not started.** |
| Phase 6: Admin and Final Release | Known only by its roadmap title; not yet specified in the repo | **Not started.** |

- The roadmap has six phases (confirmed by the developer on 2026-09-25); earlier versions of this document went only up to Phase 4. Phases 1–3 are described in §1–§3 and Phase 4 in §8.

### Build verification

- **Result:** `BUILD SUCCESSFUL`.
  - Run on 2026-09-25 01:37 (+08:00), during this documentation pass.
  - Command: `gradlew.bat :app:assembleDebug --offline`, with `JAVA_HOME` set to the Gradle-provisioned JDK 21.
  - `compileDebugKotlin` was already up to date, which means the current sources had already compiled in an earlier IDE build. Manifest, resources, dex and packaging re-ran.
  - No unit tests, instrumented tests or lint were run.
- **After the §3.2 and §3.3 image changes:** `BUILD SUCCESSFUL in 47s` on 2026-09-25, same command.
  - `compileDebugKotlin` re-ran, so the changed sources compiled.
  - The only compiler warning, a deprecated `Icons.Outlined.ArrowBack` in `MainActivity.kt`, was already present at `d4f65fe`.
  - No tests or lint were run, and the app was not installed or run.
- **After the §3.4 load-failure fixes:** `BUILD SUCCESSFUL in 16s` on 2026-09-25, same command.
  - `compileDebugKotlin` re-ran. Its only warning, a deprecated `Icons.Outlined.FormatListBulleted` in `ActivityScreen.kt`, was already present at `d4f65fe`; it appears because that file was recompiled.
  - No tests or lint were run, and the app was not installed or run.
- **After the §3.5 known-issue fixes (commit `85c9263`):** `BUILD SUCCESSFUL in 29s` on 2026-09-25, same command.
  - `compileDebugKotlin` re-ran. Its only warnings, the deprecated `Icons.Outlined.FormatListBulleted` (`ActivityScreen.kt`) and `Icons.Outlined.ArrowBack` (`MainActivity.kt`), were already present.
  - No tests or lint were run. The developer then installed this code from Android Studio and ran the §3.5 checks ([results log](#results-log)).
- **Phase 4 (commit `266325b`):** `gradlew :app:assembleDebug` passed locally on 2026-09-25. The only warnings were the deprecations already present (`FormatListBulleted`, `ArrowBack`, `Logout`).
- **After the §8.7 preference fixes (commit `c7323b8`, which includes `5c1e89c`):** `BUILD SUCCESSFUL in 29s` on 2026-09-25 21:21 (+08:00), `gradlew.bat :app:assembleDebug`.
  - Its only warning, the deprecated `Icons.Outlined.ArrowBack` (`MainActivity.kt`), was already present.
  - No tests or lint were run. The developer then installed this code from Android Studio and ran the retest ([results log](#results-log)).
- **Purchases/Claims and Phase 1 demo fixes (branch `claude/phase1-demo-fixes` at `8908548`, merged as PR #3):** `assembleDebug` BUILD SUCCESSFUL on 2026-09-26 after pinning `googleid` 1.2.0 (§10.6), and again with the "starts unchecked" change. The developer then installed it from Android Studio and ran the Phase 1 checks ([results log](#results-log)).
- **Automated tests:** only the Android Studio templates `ExampleUnitTest` and `ExampleInstrumentedTest` exist.

### Phase 1: what exists (summary)

- **Auth flows.** Sign-up (with display name), sign-in, password-reset email and sign-out through Firebase, in `SaluSaloApp()` (`MainActivity.kt` L113–325).
  - An existing session is restored on launch (`LaunchedEffect(firebaseAuth.currentUser?.uid)`, L156–160).
  - System Back on the Home page signs the user out (L162–171).
- **Profile creation.** After sign-up the ID token is force-refreshed so it includes the `role: authenticated` claim, which a Cloud Function adds outside this repo. `UserRepository.loadOrCreateCurrentProfile()` then finds or creates the `public.users` row.
  - New rows are always `RECIPIENT`: the DB default plus the RLS insert check.
- **Supabase tokens.** `SupabaseClientProvider` sends the Firebase ID token to Supabase, and RLS checks it via `is_salusalo_firebase_jwt()`.
- **Storage.** The private buckets `food-images` and `profile-images` are created by `20260921_phase1_foundation.sql`.
- **Not part of the implemented scope** (current placeholders):
  - The Google, Apple and Facebook buttons only show a message. **Since §10 (in code, not tested): real Google sign-in; Apple and Facebook removed.**
  - "Keep me signed in" has no effect. **Since §10 (in code, not tested): it works.**
  - Profile name/location edits, the profile photo and settings are saved only to local SharedPreferences. Reviews are sample data. **Since §10 (in code, not tested): the name and photo are saved to Supabase and the location is removed; other settings are still local, reviews still sample data.**
  - Only the displayed profile **name** comes from Supabase.
  - `StorageRepository.profileImagePath()` is unused. **Since §10 it is used for profile photos.**

### Phase 2: what exists (summary)

- **Create listing.** `FoodRepository.createFoodPost()` uploads the image to `food-images/<firebaseUid>/<postId>/image.<ext>` with `upsert = false`, then inserts the `food_posts` row with `image_url = <storage path>`. If the insert fails, the uploaded image is deleted.
- **Feed.** `getAvailableFoodPosts()` returns posts with `status = AVAILABLE` and `expiration_date > now`.
  - Each post gets a 1-hour signed URL in `displayImageUrl`.
  - The Home list (`AvailableFoodCard`) shows it with `AsyncImage`.
- **Own posts.** `getUserFoodPosts()` feeds PostScreen → "My posts" and Activity → My Listings, both with Delete. Delete removes the row, then the image.
- **Not part of the implemented scope:**
  - **No edit UI.** `FoodRepository.updateFoodPost()` exists but is unused. **Since §11: Edit listing through `update_my_food_post()`, and `updateFoodPost()` is removed (passed on 2026-09-26).**
  - **Only the first selected photo is uploaded**, even though up to four can be picked. **Since §11 all of them, up to 4 (passed on 2026-09-26).**
  - **Form fields that are collected but not saved:** price ("I want to sell"; saved since §9, passed on 2026-09-26), pickup start/end, distribution mode, partial claims and maximum per recipient, allergens, storage notes. **Since §11 the pickup window, handover (the former distribution mode), allergens and storage notes are saved, and partial claims and maximum per recipient are removed (passed on 2026-09-26).**
  - **Hard-coded display values:** every listing shows "Free", provider "Food donor", rating "New", and uses a fixed distance of 0 for the "Nearby" filter. **The price is real since §9 and the donor name since §11; the rating and the Nearby filter were removed in §11 (passed on 2026-09-26).**

---

## 2. Phase 3 — Reservations: actual implementation

Code involved:
- `supabase/migrations/20260924_phase3_reservations.sql`
- `supabase/migrations/20260924_phase3_donor_recipient_names.sql`
- `data/ReservationRepository.kt`
- `model/Reservation.kt`
- `viewmodel/ReservationViewModel.kt`
- UI: `FoodDetailsScreen` in `MainActivity.kt`, and `ActivityScreen.kt`

### 2.1 Data model

- **`public.reservations` table.**
  - `id` (uuid).
  - `food_post_id` → `food_posts.id`, **on delete cascade**.
  - `recipient_id` → `users.id`, on delete restrict.
  - `quantity`, which must be > 0.
  - `status`: `RESERVED`, `CLAIMED` or `CANCELLED` (default `RESERVED`).
  - `created_at` and `updated_at` (kept current by a trigger).
  - Indexes on `recipient_id` and `food_post_id`.
- **Kotlin (`model/Reservation.kt`).**
  - `Reservation` and `ReservationStatus` mirror the table.
  - `ReservationDisplay(reservation, foodPost, recipientName?)` is the row the UI renders.
  - `DonorReservationRow` holds the `get_donor_reservations()` result, including `recipient_name`.

### 2.2 Creating reservations

- **UI.** `FoodDetailsScreen` (`MainActivity.kt` L2228) opens from any feed or recommendation card.
  - **Reserve** (L2700–2714) calls `reservationViewModel.reserve(food.id, quantity)`.
  - On the user's own post (`isOwnFood`, L1958) it shows "You cannot reserve your own food post." instead.
- **Call chain.** `ReservationViewModel.reserve()` → `ReservationRepository.createReservation()` → RPC `create_reservation(p_food_post_id, p_quantity)`. Only the post ID and quantity are sent; the recipient is derived from the JWT.
- **On success.**
  - The message "Reservation created. Please coordinate pickup with the donor." is set.
  - `myReservations` is reloaded.
  - `FoodDetailsScreen` calls `onReserved()`, which reloads the feed and closes the dialog (L1960–1963, L2279–2287). This also closes the "Reserved" notice set just before, so it is never seen (found on 2026-09-26, §9.6).
- **On failure.** The raw exception message is shown in a "Reservation" dialog.
- **Not a reservation.** "Claim Now" / "Buy Now" on the same screen opens `FoodActionsDialog` (`FoodActions.kt`). That flow is **preview-only**; it states "No request was sent, no stock was held, and no payment was taken."

### 2.3 Quantity handling

- **Client.**
  - The stepper runs from 1 to `available` (L2642–2668).
  - `available` is parsed from the display string `"<quantity> <unit>"` (L2235–2238) and falls back to 1 if parsing fails.
  - `ReservationRepository.createReservation()` also `require`s quantity > 0.
  - Client values come from the last feed load and may be stale. The server is authoritative.
- **Server (`create_reservation`) rejects:**
  - a quantity ≤ 0;
  - a missing or expired post ("Food post is no longer available");
  - a caller with no profile ("Profile not found");
  - the caller's own post;
  - `status <> 'AVAILABLE'` or `quantity < requested` ("Not enough food available. Only N remains.").
- **Constraint change.** Phase 3 relaxes `food_posts_quantity_check` from `quantity > 0` to `quantity >= 0`, so a fully reserved listing can reach 0.

### 2.4 Atomic quantity protection

- **`create_reservation`** runs everything in one function call, which is one transaction.
  - It row-locks the post (`SELECT … FOR UPDATE`), validates, inserts the reservation, and decrements `food_posts.quantity`.
  - The post's `status` becomes `RESERVED` when the quantity reaches 0, otherwise `AVAILABLE`.
  - Concurrent reservations on the same post are serialized by the lock. The later one reads the reduced quantity and fails the check.
- **`cancel_reservation`** locks the reservation row and restores quantity with a single `UPDATE … SET quantity = quantity + n`.
- **`claim_reservation`** locks both the reservation row and the post row.
- **Client permissions.** Clients have **no** INSERT/UPDATE/DELETE grants on `reservations`, only SELECT. Every write goes through the SECURITY DEFINER RPCs.
- **Not yet verified.** No concurrency test (two recipients reserving the last units at the same moment) has been run.

### 2.5 Cancellation

- **UI.** Activity → **My Reservations** → open a record → **Cancel**.
  - It is shown only for non-donor records whose status is `Reserved` (`ActivityScreen.kt` L698–699).
- **RPC `cancel_reservation(p_reservation_id)`.**
  - Only the reservation's recipient may cancel.
  - `CLAIMED` or already `CANCELLED` reservations are rejected.
  - It sets the reservation to `CANCELLED`, adds its quantity back to the post, and sets the post's `status = 'AVAILABLE'` unconditionally.
- **After success.** The ViewModel reloads both reservation lists. It does **not** reload the Home feed.

### 2.6 Claiming (the donor marks a reservation as claimed)

- **UI.** Activity → **My Donations** → open a record → **Mark claimed**.
  - It is shown only for donor records whose status is `Reserved` (`ActivityScreen.kt` L696–697).
- **RPC `claim_reservation(p_reservation_id)`.**
  - Only the donor of the reservation's food post may claim, and only a `RESERVED` reservation.
  - It sets the reservation to `CLAIMED`.
  - **It does not change the food post's quantity or status.**
- **Not related:** the Activity **"Claims"** tab is demo data and does not show claimed reservations.

### 2.7 Recipient reservations ("My Reservations")

- **Query.** `ReservationRepository.getMyReservations()` selects from `reservations` where `recipient_id` equals the caller's profile id.
  - This explicit filter matters: RLS alone would also return reservations on the caller's own posts.
- **Joining posts.** Each reservation is matched in memory with its `FoodPost` by `toDisplays()`. It fetches **every** `food_posts` row visible to the caller. Since the 2026-09-25 change (§3.3) it keeps only the posts in `ids` and signs each one's image; before that, `ids` was computed but unused.
- **Visibility.** The recipient can still read a post they reserved after it becomes `RESERVED` with quantity 0. This works through the Phase 3 `food_posts` SELECT policy that uses `is_reservation_recipient()`.
- **Order.** The client does not sort the list.

### 2.8 Donor reservation management ("My Donations")

- **Query.** `ReservationRepository.getReservationsForMyFoodPosts()` calls `get_donor_reservations()`.
  - That SECURITY DEFINER function returns reservations on the caller's own posts, newest first, plus `recipient_name` (`users.name`) only.
  - The same function is defined in `20260924_phase3_donor_recipient_names.sql` and also appended to the end of `20260924_phase3_reservations.sql`. Both use `create or replace`, so running both is harmless.
- **Joining posts.** Rows are matched in memory with the donor's visible `food_posts`; every visible row is fetched. Since the 2026-09-25 change (§3.3), only the posts the rows reference are kept, and each one's image is signed.
- **Display.** The UI shows "Recipient: <name>" on the card and in the details dialog.
  - **Recipient information is limited to the display name.** No email or phone is exposed.

### 2.9 Ownership / security (as implemented)

- **Reservation visibility.** The `reservations` RLS SELECT policy allows the caller when they are the recipient **or** the donor of the reservation's post. There are no write policies or write grants.
- **All four RPCs:**
  - are `SECURITY DEFINER` with `search_path = public`;
  - have `EXECUTE` revoked from `public` and granted to `authenticated`;
  - derive the caller via `current_salusalo_profile_id()` from the JWT.
- **Token check.** `create_reservation`, `cancel_reservation` and `claim_reservation` also check `is_salusalo_firebase_jwt()`. `get_donor_reservations()` and `is_reservation_recipient()` do not; they only filter by the caller's profile.
- **UI rules** mirror the server: no Reserve on your own post, Cancel only on the recipient's `Reserved` records, Mark claimed only on the donor's `Reserved` records. The server is authoritative.
- **Profiles.** `users` RLS is unchanged (users can select only their own row). Recipient names reach donors only through `get_donor_reservations()`.
- **Storage.** `food-images` SELECT is allowed for any authenticated Salu-Salo user (Phase 2 policy). A recipient can therefore create signed URLs for a donor's listing image; the §3.3 change relies on this.
- **Hardening notes** (from reading the SQL; not tested):
  - **Null-unsafe ownership checks.**
    - `cancel_reservation` compares `recipient_id <> v_profile`, and `claim_reservation` compares `v_donor <> current_salusalo_profile_id()`.
    - If the caller has a valid Salu-Salo token but **no** `users` row, that comparison yields NULL, so the ownership check does not raise.
    - `create_reservation` checks `v_profile is null` explicitly; these two do not.
    - Exploiting this requires knowing a reservation UUID, which RLS does not expose to such a caller, so the risk is low.
    - A future migration could use `IS DISTINCT FROM` or an explicit null check.
  - **Donor can edit quantity and status directly.**
    - The `food_posts` UPDATE policy lets a donor change **any** column of their own post, including `quantity` and `status`.
    - So the rule that only the RPCs change quantity is not enforced against the owner.
    - No UI does this today (`updateFoodPost()` is unused), but a future edit-listing feature must account for it.
  - **Silent cascade delete.** Deleting a listing cascade-deletes its reservations, including active ones. The recipient gets no notice.

### 2.10 Food listing quantity / status

- **Quantity.** `food_posts.quantity` is the **remaining** quantity. The originally posted quantity is not stored.
- **Status values.** `AVAILABLE`, `RESERVED`, `CLAIMED`, `EXPIRED` (DB check), mirrored by the `FoodPostStatus` enum.
- **Transitions that actually happen:**

| Event | `quantity` | `status` |
|---|---|---|
| Create listing | As entered. The form requires > 0; the DB allows ≥ 0 after Phase 3. | `AVAILABLE` (required by the insert policy) |
| `create_reservation(n)` | − n | `RESERVED` if it reaches 0, otherwise `AVAILABLE` |
| `cancel_reservation` | + n | `AVAILABLE` (always) |
| `claim_reservation` | unchanged | unchanged |

- **Statuses never set.** `CLAIMED` and `EXPIRED` are **not set by any code path**. Expiry is enforced only by `expiration_date > now()` filters in the feed query and in RLS.
- **Visibility.**
  - The feed shows only `AVAILABLE`, unexpired posts.
  - The donor always sees their own posts in My Listings. After a full reservation that looks like `0 <unit>` with status "Reserved".
  - Other users cannot see a `RESERVED` post unless they hold a reservation on it.

### 2.11 Status matrix

| Item | Implemented (code) | Manually tested | Not yet verified |
|---|---|---|---|
| Create reservation (RPC + Reserve button) | Yes | Passed 2026-09-25 (§4 A/B test) | — |
| Quantity stepper + server quantity checks | Yes | Reserving the full quantity passed 2026-09-25 (§4 A/B test) | Over-quantity / stale-quantity error path |
| Atomic quantity protection (row lock) | Yes (SQL) | None recorded | Concurrency test |
| Cancellation | Yes | Passed 2026-09-25 (cancel test: Activity message, status Cancelled). Restored quantity on Home after a partial cancel, and a fully reserved listing's return to Home after its cancel, 2026-09-25 (§3.5 check 4) | — |
| Claiming | Yes | Passed 2026-09-25 (claim test: Activity message, both accounts see Claimed, B has no Cancel) | — |
| My Reservations (recipient's own only) | Yes (separate tab) | Passed 2026-09-25 (§4 A/B test) | — |
| My Donations (donor's posts only, recipient name) | Yes (separate tab) | Passed 2026-09-25 (§4 A/B test; the record showing the recipient's name was used in the claim test) | — |
| My Listings (own posts) | Yes | Create and delete listing passed for both accounts, 2026-09-25 (Phase 2 regression) | — |
| Server-side ownership rules | Yes (SQL) | None recorded | Negative tests (acting on another user's reservation) |
| Listing quantity/status transitions | Yes (SQL) | Seen on Home, 2026-09-25 (§3.5 check 4): the quantity after a partial reserve and after its cancel; a fully reserved listing left Home and came back with its full quantity after the cancel | The fully reserved listing in the donor's My Listings (quantity 0, "Reserved") |
| Reservation images | Yes, in the 2026-09-25 change (§3.3); builds | Passed 2026-09-25 (§4 A/B test: image in both reservation views) | — |
| Recommended for You real image | Yes, in the 2026-09-25 change (§3.2); builds | Passed 2026-09-25 (Recommended for You test) | — |
| Load-failure fixes (per-post image signing, queued loads, independent reservation lists, Activity messages, tab reload) | Yes, in the 2026-09-25 change (§3.4); builds | Missing-image fallback/feed check passed 2026-09-25; the §4 A/B test also passed on this build; Activity messages passed 2026-09-25 (cancel and claim tests) | Tab reload (§4, §3.4 check 5) |
| Known-issue fixes (food details data, reservation dialog text, feed reload after cancel) | Yes, in commit `85c9263` (§3.5); builds | Passed 2026-09-25 (§3.5 checks 1–4, including the "Image unavailable" header) | — |
| Migrations applied to live Supabase | Partly confirmed: the §3.4 SQL checks (2026-09-25) show `food_posts`, `reservations` and `get_donor_reservations()` | n/a | The full list of applied migrations |

---

## 3. Phase 3 work items (complete)

Phase 3 was signed off on 2026-09-25. These sections are kept as the record of that work; follow-ups carried forward are listed in §6.

### 3.1 Activity UI separation

**Desired structure**

- **My Reservations**
  - only reservations created by the signed-in recipient
  - Cancel action
- **My Donations / Manage Reservations**
  - only reservations made on the signed-in donor's food posts
  - recipient information
  - Mark Claimed action
- **My Listings**
  - the donor's own food listings

**Handoff description vs. actual code.** The handoff says `myReservations` and `donorReservations` are combined in the same Reservations UI. **The code at `d4f65fe` already keeps them separate.** That commit replaced the old single sample-data "Reservations" tab with separate tabs:

| Desired | Actual code in `ActivityScreen.kt` |
|---|---|
| My Reservations: own reservations only, Cancel | Tab `"My Reservations"`. Data flows `getMyReservations()` (filtered by `recipient_id`) → `ReservationViewModel.myReservations` → `myReservationRecords` (L277–290). Cancel is in the details dialog (L698–699). |
| My Donations / Manage Reservations: reservations on own posts, recipient info, Mark Claimed | Tab `"My Donations"`. Data flows `get_donor_reservations()` → `donorReservations` → `donorReservationRecords` (L293–307). The card shows "Recipient: <name>" (L790–797); Mark claimed is at L696–697. |
| My Listings: own listings | Tab `"My Listings"`. Data flows `getUserFoodPosts()` → `FoodViewModel.myPosts` → `myListingRecords` (L249–274). Delete action. |

- The tab list is at L505–511.
- Sample `"Reservations"` and `"My Listings"` records are filtered out (L310–312). The "Purchases" and "Claims" tabs showed demo data until §9 (in code, not yet tested).

**What remains for this item**

1. **Verified on 2026-09-25.** The §4 A/B test passed: B sees the reservation under My Reservations, A does not, and A sees it under My Donations.
2. **Decide the tab label.** It is "My Donations" in code; it could become "Manage Reservations" or "My Donations / Manage Reservations". This is a label-only change.
3. **Related issues seen in the same screen** (not fixed; fix only if in scope):
   - `ActivityScreen` never displays `reservationViewModel.message`, so success **and error** messages from Cancel and Mark claimed are not shown there. The message is not cleared either, so it pops up as a "Reservation" notice the next time any food details dialog opens (`MainActivity.kt` L2279–2287). **Changed in code on 2026-09-25 (§3.4): the Activity screen now shows and clears it. Verified on a device on 2026-09-25 (cancel and claim tests).**
   - The details dialog for reservation records ends with "This is your live Supabase listing." (L686). That text was meant for listings. **Changed in code on 2026-09-25 (§3.5). Verified on a device on 2026-09-25.**
   - The reservation lists have no loading or error state.

### 3.2 Recommended for You image

**Status (2026-09-25): verified on a device. The Recommended for You test passed: each card showed its listing's uploaded photo.**
- `RecommendationCard` now takes `imageUrl` (the call site passes `food.imageUrl`). It shows the image with `AsyncImage` and uses the demo drawable only when `imageUrl` is null. If loading fails it shows an "Image unavailable" box, as `AvailableFoodCard` does. The card's size, layout and badge are unchanged.
- `:app:assembleDebug` passes.

**Issue at `d4f65fe` (confirmed in code)**
- `MainActivity.kt` already has the real image URL: `FoodPost.toHomeFood()` (L1970–1988) sets `imageUrl = displayImageUrl` (the signed URL) and `photo = R.drawable.food_produce` (demo).
- The "Recommended for you" row (L1654–1668) passes only `photo = food.photo` to `RecommendationCard`.
- `RecommendationCard` (L2006–2082) draws `Image(painterResource(photo))` (L2027–2034), so it always shows the demo `food_produce` drawable.
- Only the two cards in the horizontal row on the Home page are affected. The "See all" recommendations page uses `AvailableFoodCard`, which already shows real images.

**Desired**
- Display the actual uploaded image: pass `food.imageUrl` into `RecommendationCard` and render it with `AsyncImage`. `coil3.compose.AsyncImage` is already imported in `MainActivity.kt`.
- Use the demo image only as a genuine fallback (no URL).
- Follow the existing `AvailableFoodCard` pattern (L2105–2126): `AsyncImage` with `onError`, the drawable when `imageUrl == null`, and an "Image unavailable" box when loading fails.

### 3.3 Reservation image

**Status (2026-09-25): verified on a device. The §4 A/B test passed, including the uploaded image in both reservation views.**
- `ReservationRepository` now has a private `withSignedImageUrl()`, a copy of `FoodRepository`'s: the same `food-images` bucket and 1-hour signed URL. Both list methods keep only the posts their reservations reference and sign those. `ActivityScreen.kt` is unchanged.
- Since the §3.4 change, both repositories use one shared `withSignedImageUrl(bucket)`, and a failed signature leaves only that post without an image instead of failing the whole list load.
- **Regression found on 2026-09-25 (§3.4):** before that fix, this signing made Account A's My Reservations load fail (A had reservations on two listings whose images are missing), and `refresh()` then skipped My Donations.
- `:app:assembleDebug` passes.

**Issue at `d4f65fe` (confirmed in code)**
- `ReservationRepository.kt` loads the related `FoodPost` rows directly: `getReservationsForMyFoodPosts()` at L40 and `toDisplays()` at L55. It never generates a signed Storage URL for the image.
- `FoodPost.displayImageUrl` is `@Transient` and defaults to null, so reservation records get `uri = null` (`ActivityScreen.kt` L287, L303).
- `RecordPhoto` (L901–935) then draws an **empty** box, with neither an image nor a placeholder. This affects both My Reservations and My Donations.
- `FoodRepository.kt` already has the correct approach in `withSignedImageUrl()` (L102–105): `bucket.createSignedUrl(path, 1.hours)` on the `food-images` bucket, then `copy(displayImageUrl = …)`.

**Desired**
- Use the existing `food-images` bucket and follow the `FoodRepository` signed-URL implementation, keeping the 1-hour expiry.
- Populate `FoodPost.displayImageUrl` before building each `ReservationDisplay`, for both the recipient and donor lists. The existing `RecordPhoto` then shows the actual uploaded image, with its error fallback.
- **Implementation notes:**
  - `withSignedImageUrl()` is `private` in `FoodRepository`. Reuse it via a small shared helper or an equivalent short extension in `ReservationRepository`.
  - Both repository methods currently fetch every visible `food_posts` row. Sign only the posts the reservations actually reference (the unused `ids` set in `toDisplays()`), so there is no Storage call for every listing in the feed.
  - No migration should be needed, because `food-images` read access for authenticated users already exists (Phase 2 policy). This comes from reading the policy; it has not been verified at runtime.

### 3.4 Listings missing from Home / My Listings, and donor reservations not showing

**Status (2026-09-25): code changes made and committed; `assembleDebug` passes. Manually verified on 2026-09-25: the missing-image fallback/feed check passed, and the §4 A/B test passed on this build, and the Activity messages passed in the cancel and claim tests. Not reported: tab reload (§4, §3.4 check 5). The live-database checks below were run by the user on 2026-09-25; the results confirm both causes (see "SQL results").**

**Reported in live tests (2026-09-25)**
- A listing created by Account A appeared in A's My Listings but not on Home, for either account.
- A listing created by Account B appeared neither on Home nor in B's My Listings.
- After B reserved all 8 of A's listing (B's record showed correctly, with its image), A's My Donations was empty.

**Diagnosis**
- Confirmed in code:
  - Each list load signed every post's image, one request at a time, with no per-post error handling. `createSignedUrl` throws when the object is missing or the request times out (supabase-kt 3.5.0, 10 s request timeout), so one bad image failed the whole feed, My Listings or reservation list.
  - On failure the ViewModels kept the previous list. Home showed an error only when its list was empty; Activity showed none.
  - `createFoodPost()` deleted the uploaded image when anything after the upload failed, including signing the row it had already inserted. That can leave a listing whose image no longer exists. While it is `AVAILABLE` and unexpired, it fails every account's feed, and it always fails its owner's My Listings.
  - `createPost()` reloaded the feed before My Listings, so a failed feed reload skipped My Listings. Loads requested while another `FoodViewModel` request ran were silently dropped.
  - `ReservationViewModel.refresh()` loaded My Reservations first; if that failed, My Donations was never requested. Errors went only to `message`, which Activity did not show. The UI does not filter donor records.

**SQL results (run by the user on 2026-09-25; screenshots, not copied here)**
- Check 1: six listings. Two of Account B's listings (both named "lemon dou", quantity 1, created 2026-09-24 06:55 and 07:22 UTC) are `AVAILABLE`, unexpired, and have `image_exists = false`. Every other listing's image exists.
- Check 2: `get_donor_reservations()` exists. The visible part of its result type matches the repo; the rest was cut off in the screenshot.
- Check 3: Account B holds three `RESERVED` reservations on Account A's "glasses" listing (1 + 1 + 8, so it started at 10 and is now `RESERVED` with quantity 0). Account A holds four `CANCELLED` reservations on Account B's "lemon dou" listings.

**Confirmed causes (SQL results plus the code before §3.4)**
- Listings: the two "lemon dou" listings are in every feed and their images cannot be signed, so every feed load failed for both accounts. They are also in B's My Listings, so B's list failed; all of A's listings have images, so A's list loaded.
- Donor reservations: `refresh()` loaded A's My Reservations first. That list includes the four cancelled "lemon dou" reservations, and the §3.3 change signs those listings' images, so the load threw and My Donations was never requested. The donor RPC was not the cause.
- Not confirmed: how the two images disappeared. The app's create cleanup does not fit the first listing (A reserved it about 20 minutes after it was created, which required its image to sign then). The app's delete flow does not fit either, because both rows and their reservations still exist.

**Code changes** (paths relative to `app/src/main/java/com/example/salusalo/`)
- `data/FoodRepository.kt`
  - `withSignedImageUrl(bucket)` is now a shared top-level function. A failed signature leaves only that post's `displayImageUrl` null; coroutine cancellation is still rethrown. The existing null-URL fallback then applies: the demo drawable on Home cards and an empty photo box in Activity.
  - `createFoodPost()` deletes the uploaded image only when the insert itself fails. Decoding and signing run after the try/catch.
- `data/ReservationRepository.kt`: uses the shared `withSignedImageUrl(bucket)`; its private copy is removed.
- `viewmodel/FoodViewModel.kt`
  - `loadAvailablePosts()` and `loadMyPosts()` requested while a request is running are queued and run right after it, one at a time, My Listings first. Each queued load succeeds or fails on its own; `error` keeps the first failure.
  - `createPost()` reloads My Listings, then the feed, as queued loads.
  - `createPost()` and `deletePost()` are still ignored while any request is running (unchanged).
- `viewmodel/ReservationViewModel.kt`: `refresh()`, and the reload after cancel or claim, load My Reservations and My Donations independently and report the first failure through `message`.
- `ActivityScreen.kt`
  - Shows `reservationViewModel.message` in a "Reservation" dialog and clears it, so it no longer appears later in the food details dialog.
  - Tapping the My Reservations or My Donations tab reloads the reservation lists.
- No SQL, RLS, RPC, authentication or storage-policy changes.

### 3.5 Known-issue fixes (after Phase 3 sign-off)

**Status (2026-09-25): verified on a device.** The change was made in a Claude Code web session that had no Android SDK (commit `85c9263`, branch `claude/focused-dirac-oy16ck`). `assembleDebug` then passed locally, and the developer ran the §4 "Checks for the §3.5 fixes" on the phone: all four passed. No listing with a missing image was left on Home, so check 2 and the "Image unavailable" header were run with a staged test listing. Merged into `master` through pull request #1 on 2026-09-25.

- **Reservation dialog text** (`ActivityScreen.kt`): the details dialog's closing line now depends on the record. My Listings keeps "This is your live Supabase listing."; My Donations shows "This is a live reservation on your listing."; My Reservations shows "This is your live reservation."
- **Food details** (`MainActivity.kt`, `FoodDetailsScreen`):
  - The header shows the listing's signed image, with the `AvailableFoodCard` fallback: the demo drawable when there is no URL, an "Image unavailable" box when loading fails.
  - The description is the post's own `description` (`HomeFood` now carries it), or "No description provided."
  - "Pickup Address" shows the post's pickup location instead of "Del Rosarrio, Balilihan".
  - Unchanged: allergens ("Not provided — ask the provider", since allergens are not saved), Storage Note, Pickup Window, and the preview-only `FoodActionsDialog`, whose map view still has its own demo address (`FoodActions.kt`).
- **Home feed after cancel** (`ActivityScreen.kt`): when the Activity screen receives the "Reservation cancelled" message, it also calls `foodViewModel.loadAvailablePosts()`, so Home shows the restored quantity. Claim changes neither quantity nor status, so it does not reload the feed. There is still no realtime update across devices.
- **Home greeting** (`MainActivity.kt`, `HomeScreen`): "Good evening, Mika 👋" is replaced by "Good morning / Good afternoon / Good evening, <first name> 👋". The first name is the first word of the Supabase profile name (no name if it is blank). The period uses the device's local time (morning 05:00–11:59, afternoon 12:00–17:59, evening 18:00–04:59) and is re-read every minute while Home is open. The other demo "Mika" values (Profile screen default, preview-only "Requesting as") are unchanged. This change came after pull request #1 (commit `851e8cb`), so it was not part of the §3.5 test. It was built at `266325b`, and the Phase 4 test on 2026-09-25 showed "Good evening, <first name> 👋". The morning and afternoon greetings were not checked.
- No SQL, RLS, RPC, authentication or storage-policy changes.

**Live-database checks** (read-only; run in the Supabase SQL Editor)

```sql
-- 1. Listings whose image object is missing
select p.created_at, u.name as donor, p.name as listing, p.status, p.quantity,
       p.expiration_date > now() as unexpired, p.image_url, o.id is not null as image_exists
from public.food_posts p
join public.users u on u.id = p.donor_id
left join storage.objects o on o.bucket_id = 'food-images' and o.name = p.image_url
order by p.created_at desc;

-- 2. Does the donor RPC exist, and what does it return?
select p.oid::regprocedure as function, pg_get_function_result(p.oid) as returns
from pg_proc p join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public' and p.proname = 'get_donor_reservations';

-- 3. Reservations with their donor and recipient
select r.created_at, r.status, r.quantity, fp.name as listing, d.name as donor, u.name as recipient
from public.reservations r
join public.food_posts fp on fp.id = r.food_post_id
join public.users d on d.id = fp.donor_id
join public.users u on u.id = r.recipient_id
order by r.created_at desc;
```

- **Check 1:** a row with `image_exists = false` is a listing that now loads without its image. If it is `AVAILABLE` and unexpired, it explains the feed failure. Its owner can delete it from My Listings.
- **Check 2:** no row, or a result other than `TABLE(id uuid, food_post_id uuid, recipient_id uuid, quantity integer, status text, created_at timestamp with time zone, updated_at timestamp with time zone, recipient_name text)`, means the existing `supabase/migrations/20260924_phase3_donor_recipient_names.sql` must be run manually in the SQL Editor.
- **Check 3:** B's reservation should list A as the donor.

---

## 4. Manual test: Account A / Account B

### Preconditions (not verifiable from the repo)

- All four migrations are applied to the live Supabase project in order:
  1. `20260921_phase1_foundation.sql`
  2. `20260923_phase2_food_posts.sql`
  3. `20260924_phase3_reservations.sql`
  4. `20260924_phase3_donor_recipient_names.sql`
- Supabase Third-Party Auth is configured for Firebase project `salusalo`, and both accounts receive the `role: authenticated` custom claim.
- A current debug build is installed, with a valid `local.properties` (`SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`).
- There are two accounts with different emails. Use two devices/emulators, or sign out between steps (Profile → Log out; system Back on the Home page also signs out).
- There is no pull-to-refresh:
  - **Feed:** the Home feed reloads on sign-in, after a reservation, and when leaving Post with its on-screen **Back** button.
  - **Activity:** the Activity tab reloads each time it is opened.

### The test

**Account A:**
- creates a food listing (Post → fill in the required fields → Post Listing)
- quantity = 8
- uploads an image

**Account B:**
- logs in
- reserves all 8 (Home → open A's listing → tap "+" until 8 → Reserve)

**Expected:**
- B sees it under **My Reservations**.
- A does **NOT** see B's reservation under **My Reservations**.
- A sees B's reservation under **My Donations / Manage Reservations** (the tab label in code is "My Donations").
- The uploaded image appears correctly in both relevant views.

**Prediction from the code, not a test result:**
- The first three expectations should pass.
- With the §3.3 change, both reservation cards should show the uploaded image. At `d4f65fe` they rendered none.
- For §3.2, check the Home "Recommended for you" row **before** B reserves all 8. The row shows only the first two posts of the AVAILABLE feed, in no fixed order, and a fully reserved post leaves that feed. Each card in the row should show its listing's uploaded photo, not the demo produce image.

### Optional follow-up checks (for Phase 3 sign-off)

- A → My Listings shows the listing with quantity 0 and status "Reserved", and it is gone from B's feed.
- B cancels: B's record shows "Cancelled", and the listing returns to the feed with 8 available.
- B reserves again, then A taps Mark claimed: both see "Claimed", and B no longer gets a Cancel action.
- With a stale details screen, B requests more than remains: "Not enough food available. Only N remains."
- A opens their own listing: the Reserve button shows "Your listing / You cannot reserve it".

### Checks for the §3.4 fixes

Run the §3.4 SQL checks first, then install a build that contains §3.4.

0. **Existing data (from the 2026-09-25 SQL results):** Home shows five listings for both accounts, and the two "lemon dou" listings use the demo produce photo. A's My Donations shows three `Reserved` records on "glasses" (8, 1, 1) with "Recipient: <B's name>" and the photo. A's My Reservations shows four `Cancelled` "lemon dou" records with no photo. B's My Listings shows all four of B's listings.
1. **Feed with a bad image** (only if check 1 found an `AVAILABLE`, unexpired listing with `image_exists = false`): Home loads for both accounts without "Could not load listings. Try again."; that listing shows the demo produce photo; its owner's My Listings loads.
2. **A's new listing:** A creates a listing with a photo, then taps "Back to home": it is on Home with its photo, and Activity → My Listings shows it. After switching to B, B's Home shows it.
3. **B's new listing:** step 2 with the accounts swapped. Also, right after "Listing published", go straight to Activity with the bottom bar: My Listings shows it.
4. **Donor view:** B reserves all 8 of A's listing. A → Activity → My Donations shows it with "Recipient: <B's name>" and the photo. If a "Reservation" dialog with an error appears instead, record its exact text (see check 2).
5. **Tab reload (two devices):** A keeps Activity open while B reserves, then taps My Donations: the new reservation appears.
6. **Messages:** A taps Mark claimed: a "Reservation" dialog on Activity says "Reservation marked as claimed." After OK, opening any listing on Home shows no leftover notice. Same for B → Cancel ("Reservation cancelled. The quantity is available again.").

### Checks for the §3.5 fixes

1. **Food details:** open a listing that has a photo and a description. The header shows the uploaded photo, the text is the listing's description, and "Pickup Address" is its pickup location. A listing without a description shows "No description provided."
2. **Missing image:** open one of the "lemon dou" listings (image missing from Storage). The header shows the demo produce photo. If no such listing is on Home, stage one; this also covers the "Image unavailable" header:
   1. Account A posts a listing named `IMAGE TEST` with any photo.
   2. Upload a text file named `broken.jpg` to the top level of `food-images` (dashboard → Storage). Then run `update public.food_posts set image_url = 'broken.jpg' where name = 'IMAGE TEST' returning id, name, image_url;`, which should return 1 row.
   3. Reload Home (Post → **Back**). The card and the details header show "Image unavailable".
   4. Delete `broken.jpg` in Storage and reload Home again. The card and the details header show the demo produce photo.
   5. Clean up in this order: run the SQL below to point the listing back at its photo, open Activity (which reloads My Listings), then delete `IMAGE TEST` there. Deleting it also removes its photo.

   ```sql
   update public.food_posts p
   set image_url = o.name
   from storage.objects o
   where p.name = 'IMAGE TEST'
     and o.bucket_id = 'food-images'
     and o.name like ('%/' || p.id::text || '/%')
   returning p.id, p.name, p.image_url;
   ```
3. **Dialog text:** in Activity, open one record per tab. My Listings ends with "This is your live Supabase listing.", My Reservations with "This is your live reservation.", My Donations with "This is a live reservation on your listing."
4. **Feed after cancel:** B reserves part of A's listing, cancels it in Activity → My Reservations, then goes to Home with the bottom bar. The listing shows the restored quantity (and is back on the feed if B had reserved all of it).
5. **Greeting:** sign in as A: Home says "Good morning/afternoon/evening, <A's first name> 👋" matching the device time. Sign in as B: it shows B's first name.

### Phase 4 test: messaging and alerts (Account A / Account B)

Preconditions: `20260926_phase4_messaging_notifications.sql` has been run in the SQL Editor, `messages` and `notifications` appear under Database → Publications → `supabase_realtime`, and a build with Phase 4 is installed. Two devices make the live parts visible; with one device, sign out between steps and check the result after signing in.

1. **Start a chat:** B opens A's listing → "Message Donor" → sends "Is this still available?". A (Home open) sees the chat icon badge and the bell badge increase without refreshing; Home → chat icon lists the thread with B's name, the listing and an unread count.
2. **Live reply:** A opens the thread and replies. B, with the chat open, sees the reply appear without refreshing. A's thread shows no unread count after opening.
3. **Prefilled questions:** B taps the Allergens row on A's listing: the chat opens with "Hi! What allergens does <listing> contain?" in the input.
4. **Own listing:** A opens their own listing: the button reads "Your listing", and tapping it (or Allergens/Storage) shows "You cannot message yourself…".
5. **Reservation alerts:** B reserves part of A's listing → A gets "New reservation" in Alerts. B cancels → A gets "Reservation cancelled". B reserves again and A marks it claimed → B gets "Reservation claimed". Tapping a reservation alert opens Activity on My Donations (A). Since §9 (not yet tested), B's "Reservation claimed" alert opens that reservation in Purchases or Claims instead of My Reservations.
6. **Chat from Activity:** A → Activity → My Donations → record → "Message recipient", and B → My Reservations → record → "Message donor": both land in the same thread as step 1.
7. **Preferences:** A → Profile → Preferences → Notifications → turns off "New messages" → Save ("Preferences saved to your account."). B sends a message: it arrives in A's chat, but A gets no new MESSAGE alert. Turn it back on and Save, leave the page and reopen it: the switch is still on (§8.7 bug 1). While the page loads, it shows "Loading your alert settings…" rather than switches (§8.7 bug 2). Repeat both checks in Alerts → settings icon.
8. **Mark read:** Alerts → "Mark shown as read": the unread count and the bell badge go to 0 and stay 0 after reopening the app.
9. **Token refresh:** leave the app open on Home for more than an hour, then have the other account send a message: it still arrives live. (Realtime is re-authenticated every 5 minutes; §8.3.)
10. **Regression:** because the Ktor engine changed to OkHttp, repeat the Phase 1/2 regression and the A/B reservation test above.

### Results log

| Date | Build / commit | Tester | Result | Notes |
|---|---|---|---|---|
| 2026-09-25 | `d4f65fe` + uncommitted §3.2–§3.4 changes | Developer | Passed | Phase 1: authentication/profile regression (reported as one result). |
| 2026-09-25 | same | Developer | Passed | Phase 2, Account A: Home/feed, create listing, delete listing. |
| 2026-09-25 | same | Developer | Passed | Phase 2, Account B: Home/feed, create listing, delete listing. |
| 2026-09-25 | same | Developer | Passed | Phase 3: the A/B reservation test ("The test" above). |
| 2026-09-25 | same | Developer | Passed | Phase 3: missing-image fallback/feed (check 1 in "Checks for the §3.4 fixes"). |
| 2026-09-25 | same (no code changes since 10:04) | Developer | Passed | Phase 3: cancel (Account B). A "Reservation" dialog on Activity showed "Reservation cancelled. The quantity is available again."; the record then showed Cancelled with no Cancel button; no leftover popup on Home. |
| 2026-09-25 | same | Developer | Passed | Phase 3: mark claimed (Account A). A "Reservation" dialog on Activity showed "Reservation marked as claimed."; the record then showed Claimed with no Mark claimed button; B saw Claimed with no Cancel button; no leftover popup on Home. |
| 2026-09-25 | same | Developer | Passed | Phase 3: Recommended for You image. Each card showed its listing's uploaded photo, matching the "Available near you" list. |
| 2026-09-25 | `85c9263` (branch `claude/focused-dirac-oy16ck`) | Developer | Passed | §3.5 checks 1, 3 and 4. Food details showed the listing's own photo, description and pickup location, and "No description provided." for a listing without one. The Activity dialogs ended with the new lines in My Reservations and My Donations; My Listings kept its line. After B cancelled a partial reservation, Home showed the full quantity again without a new sign-in. After B reserved a whole listing it left Home, and after the cancel it came back with its full quantity. |
| 2026-09-25 | same | Developer | Passed | Regression on this build: switching accounts (sign-out, sign-in); create a listing with an image (shown on Home); delete a listing (gone from Home and My Listings); own listing shows "Your listing / You cannot reserve it"; reserve (Home quantity dropped); the cancel and Mark claimed messages. |
| 2026-09-25 | same | Developer | Passed | §3.5 check 2 with a staged listing (`IMAGE TEST`, steps under check 2). While its photo path pointed at a file that is not an image, the Home card and the details header showed "Image unavailable". After that file was deleted, both showed the demo produce photo. |
| 2026-09-25 | `266325b` (branch `claude/focused-dirac-oy16ck`), migration applied | Developer | Passed, with one failure | Phase 4 test on one phone, signing out between accounts. Passed: greeting "Good evening, <first name> 👋"; the chat icon and the Messages inbox; "Message Donor" chat; the prefilled Allergens question; "Your listing" on your own listing; "Message donor"/"Message recipient" from Activity; the New reservation, Reservation cancelled and Reservation claimed alerts, each opening the right Activity tab; turning "New messages" off suppressed message alerts; "Mark shown as read"; deleting the listing removed its thread. **Failed:** turning "New messages" back on did not save (§8.7 bug 1). |
| 2026-09-25 | same | Developer | Passed | Regression on the Phase 4 build (OkHttp engine): feed, create a listing with a photo, reserve, cancel, claim, delete. |
| 2026-09-25 | same | Developer | Partly checked | Phase 4 live test on two phones. Realtime connected ("Connected to realtime websocket!"). `realtime.subscription` held one `notifications` subscription per phone, with `claims_role` = authenticated and issuer `https://securetoken.google.com/salusalo`. Replaying the RLS check as account A passed: with `request.jwt.claims` set from the subscription and role authenticated, `is_salusalo_firebase_jwt()` was true, `current_salusalo_profile_id()` returned A's profile, and A's alerts were visible. The API logs show the sender's open chat receiving its own message live (`send_message`, then `mark_conversation_read` about 1 s later). The "live alert" step failed only because A's `message_alerts` was still false (bug 1). **Not yet confirmed:** live chat between the two phones, the live reservation alert, and the token refresh after more than 1 hour. |
| 2026-09-25 | `c7323b8` (branch `claude/focused-dirac-oy16ck`; includes the §8.7 fixes, `5c1e89c`) | Developer | Passed | §8.7 retest, Account A. Profile → Preferences → Notifications first showed "Loading your alert settings…" with Save preferences greyed out, then the saved switches. "New messages" turned on, saved ("Preferences saved to your account.") and reopened was still on; turned off and on again, each state was kept after reopening. Alerts → settings icon: "Loading your alert settings…", then "New messages" on; "Reservation updates" turned off and back on, each state kept after reopening. |
| 2026-09-25 | same | Developer | Passed | Phase 4 live test on two phones, both on Home. B sent a message from one of A's listings: A's chat icon and bell badges went up without anyone touching A's phone. With the chat open on both phones, each side's messages appeared on the other phone by themselves. With A back on Home, B reserved 1: A's bell badge went up by itself and Alerts showed "New reservation". |
| 2026-09-25 | same | Developer | Passed | Token refresh (Phase 4 test step 9). A's app stayed open on Home for more than 65 minutes (the steps asked for the screen to stay on, with the phone charging and unused). B then sent a message, and A's chat icon and bell badges went up live. |
| 2026-09-25 | same | Developer | Passed | Sign-up with the OkHttp engine, on the second phone: a new account was created, the role-claim script (outside this repo) was run, and the account signed in again. Reported as done. The password-reset email was not reported. |
| 2026-09-25 | live project (SQL Editor); script `supabase/tests/phase4_live_safety_check.sql` | Developer | Passed | Live safety check (negative tests): 21 passed, 0 failed, 0 skipped. It used the most recent chat, with its donor and recipient (the two test accounts), and the newest other account as the outsider; the three sanity checks confirmed the simulated sign-ins. Refused as expected: the outsider reading the chat (directly or through `get_my_conversations`), sending in it, marking it read, opening a chat from someone else's reservation, and seeing or changing other people's alerts and alert settings; a chat member inserting or editing messages directly, creating alerts directly, calling `notify_user`, starting a chat about their own listing, or reading the other person's `users` row; a token from another Firebase project; no login (all four tables, and `send_message`). The script ends with an error on purpose, which rolled everything back. |
| 2026-09-26 | `8908548` (branch `claude/phase1-demo-fixes`, merged as PR #3) plus the uncommitted "starts unchecked" change; both migrations applied | Developer | Passed | Phase 1 demo fixes (§10) on two phones, from a summary checklist of §10.4, reported as one result ("phase 1 demo fixes passed"). The checklist covered: the Welcome and sign-in texts with only "Continue with Google"; Keep me signed in (ticked: still signed in after closing the app; unticked: signed out); Profile with no location and a real Member since; the name change with its errors, then saved, with the Home greeting updating at once; a photo kept after signing out and in, then replaced by a second photo; the password change (a wrong current password and a too-short new one refused, then changed; afterwards the old password failed and the new one worked); Settings without Privacy or the devices list; B seeing A's new name in Messages and My Donations and sending a chat message; the message arriving live; reserving and cancelling one of B's listings; renaming A back; a new Google account (closing the picker shows no error; the first sign-in fails until the role script runs, then works; the Gmail address in Account Settings; no password to change). The checklist's regression row (email sign-up, the password-reset email, create and delete a listing, the feed, reserving, chat and alerts) was not reported separately. |
| 2026-09-26 | same build | Developer | Passed, with one known issue | Purchases, Claims and listing prices: the §9.4 two-phone test, given in parts (§9.6). Every step passed except the "Reserved" dialog after Reserve (steps 8, 9, 19), which never appears: a Phase 3 bug present since `d4f65fe`, left for the Phase 3 demo fixes. Step 4 passed on a redo (the first try typed the price into Quantity). A0/A1/B0/B1 = 2/2/2/2; the totals then matched (4 and 7). The new price showed on Home after a reload. |
| 2026-09-26 | `4b6aac6` (branch `claude/phase2-demo-fixes`, PR #5); migration `20260929_phase2_listing_details.sql` applied | Developer | Passed | Phase 2 demo fixes (§11): the §11.4 two-phone test, steps 1–25, given in parts in the order §11.4 now shows. Every step passed. Observed: B's saved `EXPIRY TEST`, once expired, showed only "No longer available" and "Remove", with no name or photo (step 21; §11.5); after the edit, My Donations showed the new name with the old price, "2 packs • ₱40.00" (step 17), while B's new reservation of 4 used the new price, "4 packs • ₱100.00" (step 22); deleting `GALLERY TEST 2` also deleted B's two reservations on it (step 23; the known cascade, §9.6); the "Reserved" dialog never appeared after Reserve (the known §9.6 bug). |
| 2026-09-26 | same | Developer | Passed | Regression round on the same build (§11.4 steps 26–39): a one-photo donation posted and later a one-photo listing deleted; A's renamed display name shown as the donor name on B's Home after a reload, then renamed back; the Donation, For Sale, Expiring Soon and All filters; reserve, cancel ("Reservation cancelled. The quantity is available again."), reserve again and Mark claimed ("Reservation marked as claimed."), with the New reservation (twice), Reservation cancelled and Reservation claimed alerts; a chat message both ways, the reply arriving live in B's open chat, with the message alerts; the claimed free reservation in Claims and gone from My Reservations. Not re-run: email sign-up, Keep me signed in, Google sign-in, the profile photo, the password change, a priced claim in Purchases, and the password-reset email (never tested). |
| 2026-09-26 | live project (SQL Editor), after the test | Developer | Passed | Read-only check of the Phase 2 migration: all 8 rows true. The 6 new `food_posts` columns, `update_my_food_post`, `get_listing_donor_names` and `saved_listings` exist; `authenticated` has no UPDATE privilege on `food_posts` and no UPDATE policy remains, and still has INSERT; `authenticated` can execute both functions, `anon` cannot (§11.6). |

- **Build:** the test phone's app code was deployed on 2026-09-25 at 10:37 (+08:00), after the last source change (10:04), so it matches the working tree above. That same code was committed on 2026-09-25, with no source changes after 10:04. The build on any second device was not checked.
- **Phase 4 retest build:** `c7323b8`, built at 21:21 (+08:00) with no local source changes and installed from Android Studio. A's phone showed the new "Loading your alert settings…" text, so it ran this build; which build the second phone ran was not checked.
- **Phase 1 demo fix test build:** installed from Android Studio on 2026-09-26 from `claude/phase1-demo-fixes` at `8908548` plus the uncommitted "starts unchecked" change, which was committed afterwards on branch `claude/phase1-demo-fixes-results`. Which build each phone ran was not checked.
- **Phase 2 demo fix test build:** `assembleDebug` of `claude/phase2-demo-fixes` at `4b6aac6` on the developer's PC (2026-09-26, BUILD SUCCESSFUL in 1m 34s, only the three old deprecation warnings), installed on both phones after the migration. Both phones showed screens that exist only on this branch (A the 4-photo Create Listing, B the gallery and Saved Listings), so both ran a build of it; the exact build on each phone was not checked.
- **§3.5 test build:** the developer installed commit `85c9263` from Android Studio, with no local source changes, and followed a step-by-step script covering §3.5 checks 1, 3 and 4 (including a full reservation in check 4) plus the regression steps above. §3.5 check 2 could not be run as written: the "lemon dou" listings were no longer on Home; only the listings created during this test were. The reason was not checked; the feed hides expired and fully reserved listings. Check 2 was then run with a staged listing (row above).
- **Phase 3 sign-off:** on 2026-09-25 the developer declared Phases 1–3 complete and manually verified. The cancel, claim and Recommended for You tests were then run and passed (rows above). Two optional follow-up checks passed later, in the §3.5 test: reserving your own listing ("Your listing / You cannot reserve it") and a cancelled listing's return to the feed. These checks still have no individual test record:
  - from the optional follow-up checks above: the fully reserved listing in My Listings, and over-quantity;
  - §3.4 check 5 (tab reload) and the bottom-bar step in check 3;
  - the concurrency and negative ownership tests (§2.11).

---

## 5. Relevant files

| Area | Path | Notes |
|---|---|---|
| Main activity, auth, navigation, Home, food details | `app/src/main/java/com/example/salusalo/MainActivity.kt` | `MainActivity`, `SaluSaloApp()` (auth + top-level `screen`), `WelcomeScreen`, `SignInScreen`, `HomeScreen()` (`page` + bottom bar), `RecommendationCard`, `AvailableFoodCard`, `FoodDetailsScreen` |
| Activity tabs | `app/src/main/java/com/example/salusalo/ActivityScreen.kt` | My Listings, My Reservations (open and cancelled), My Donations, Purchases and Claims (claimed reservations, §9); real totals; `RecordPhoto` |
| Food post model | `app/src/main/java/com/example/salusalo/model/FoodPost.kt` | `FoodPost` (with `@Transient displayImageUrl` and, since §11, `@Transient donorName`), `NewFoodPost`, `FoodPostDraft`, `FoodPostEdit`, `ListingHandover`, `FoodPostStatus`, `isExpired()` |
| Saved listings (§11) | `model/SavedListing.kt`, `data/SavedListingRepository.kt`, `viewmodel/SavedListingViewModel.kt` | Bookmarks in `saved_listings`; loaded once when Home starts, reloaded when Saved Listings opens |
| Reservation model | `app/src/main/java/com/example/salusalo/model/Reservation.kt` | `Reservation`, `ReservationStatus`, `ReservationDisplay`, `DonorReservationRow` |
| Food repository | `app/src/main/java/com/example/salusalo/data/FoodRepository.kt` | Feed (newest first, with donor names since §11), own posts, create with up to 4 photos, `updateMyFoodPost()`, delete, `withSignedImageUrl()`, `signedExtraPhotoUrls()` |
| Reservation repository | `app/src/main/java/com/example/salusalo/data/ReservationRepository.kt` | RPC calls, `getMyReservations()`, `getReservationsForMyFoodPosts()` |
| Reservation ViewModel | `app/src/main/java/com/example/salusalo/viewmodel/ReservationViewModel.kt` | `myReservations`, `donorReservations`, `message`, `reserve` / `cancel` / `claim` / `refresh` |
| Food ViewModel | `app/src/main/java/com/example/salusalo/viewmodel/FoodViewModel.kt` | `availablePosts`, `myPosts`, `createPost`, `updatePost`, `deletePost`; since §11 writes wait for the running request instead of being dropped |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/UserRepository.kt` | Find-or-create profile; `ProfileException`; since §10 `updateMyName`, `uploadProfilePhoto`, `profilePhotoUrl`, `changePassword`, `supabaseErrorText` |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/GoogleSignIn.kt`, `data/ProfilePhoto.kt` | Google ID token through Credential Manager; profile photo shrinking (§10) |
| Auth / profile | `app/src/main/java/com/example/salusalo/model/UserProfile.kt` | `UserProfile`, `UserRole`, `NewUserProfile` |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/SupabaseClientProvider.kt` | Supabase client using the Firebase ID token |
| Auth / profile | `app/src/main/java/com/example/salusalo/ProfileScreen.kt`, `ProfileSections.kt` | Name, photo, member since and password change are real (§10, passed on 2026-09-26), as are the notification preferences (§8) and saved listings (§11, passed on 2026-09-26); stats, reviews, verification and report are still demo |
| Storage paths | `app/src/main/java/com/example/salusalo/data/StorageRepository.kt` | `foodImagePath()`, `profileImagePath()` (`<uid>/profile-<millis>.jpg` since §10) |
| Create listing | `app/src/main/java/com/example/salusalo/PostScreen.kt` | Create Listing form, "My posts", `EditListingDialog` (§11), `PostPhoto` |
| Messaging (Phase 4) | `ChatScreen.kt`, `data/MessageRepository.kt`, `data/RealtimeInserts.kt`, `model/Conversation.kt`, `viewmodel/MessageViewModel.kt` | Chat dialog and Messages inbox; RPC calls; Realtime insert stream |
| Alerts (Phase 4) | `AlertScreen.kt`, `data/NotificationRepository.kt`, `model/AppNotification.kt`, `viewmodel/NotificationViewModel.kt` | Real alerts, unread counts, preferences |
| Demo screens | `app/src/main/java/com/example/salusalo/FoodActions.kt` | Preview-only pickup and checkout dialogs; its demo chat is now only a fallback |
| Theme | `app/src/main/java/com/example/salusalo/ui/theme/` | `SaluSaloTheme` is **unused**; `MainActivity` builds its own `MaterialTheme` |
| Database / Storage SQL | `supabase/migrations/` (8 files), `supabase/README.md` | Applied manually in the Supabase SQL Editor |
| Build / config | `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/google-services.json`, `local.properties` (git-ignored) | Supabase URL and publishable key are read from `local.properties` into `BuildConfig` |

- **Navigation files:** there are none; there is no Navigation-Compose `NavHost`. Navigation is string state in `MainActivity.kt`:
  - `SaluSaloApp` → `screen` (L114, L208–307)
  - `HomeScreen` → `page` (L1257, L1324–1358), with the bottom bar at L1808–1932
- **Authentication:** Firebase calls live in `SaluSaloApp()` (L113–325), `SignInScreen` (L593) and `authErrorMessage` (L327) in `MainActivity.kt`. Profile creation is in `UserRepository.kt`.

---

## 6. Development order

### Phase 3 (complete, signed off on 2026-09-25)

1. **Separate My Reservations and My Donations / Manage Reservations.** The code at `d4f65fe` already separates them (§3.1). **Verified on 2026-09-25** (§4 A/B test). The tab label decision is still open.
2. **Fix the Recommended for You image** (§3.2). Code changed on 2026-09-25. **Verified on 2026-09-25** (Recommended for You test).
3. **Fix the Reservation image** (§3.3). Code changed on 2026-09-25. **Verified on 2026-09-25** (§4 A/B test).
4. **Build the project** (`assembleDebug`). Passed on 2026-09-25 after steps 2 and 3, and again after the §3.4 load-failure fixes.
5. **Manually test with two accounts** (§4), including the §3.4 checks, and record the outcome in the results log. The §3.4 SQL checks were run on 2026-09-25. **On 2026-09-25** the A/B reservation test and the missing-image fallback/feed check passed. Not reported: the items listed under the results log.
6. **Regression-test Phase 1/2:**
   - sign-up (creates the profile), sign-in, sign-out, password-reset email;
   - create a listing with an image and see it in the feed and in My Listings;
   - delete a listing, which also deletes its image.
   - **Passed on 2026-09-25, as reported:** authentication/profile regression; Home/feed, create listing and delete listing for both accounts.
7. **Phase 3 signed off as complete by the developer on 2026-09-25.** Carried forward, not blocking:
   - The checks without an individual test record (listed under the results log, §4).
   - The tab label decision (§3.1).
   - How the two "lemon dou" images went missing (§3.4).
8. **Commit the work since `d4f65fe`.** Done on 2026-09-25: the §3.2–§3.4 code changes, `docs/` and `CLAUDE.md`.
9. **Known-issue fixes (§3.5).** Made after sign-off in a Claude Code web session (commit `85c9263`). Built and manually tested on 2026-09-25 (results log), then merged into `master` through pull request #1 on 2026-09-25 (merge commit `85e2bf4`).

### Phase 4 — Messaging and Notifications (complete, signed off on 2026-09-25)

Started on 2026-09-25 at the developer's request; scope and status are in §8. Remaining steps:

1. Apply `supabase/migrations/20260926_phase4_messaging_notifications.sql` in the Supabase SQL Editor and check the Realtime publication. **Done on 2026-09-25.**
2. Build with `assembleDebug`. **Passed at `266325b` on 2026-09-25.**
3. Run the Phase 4 test in §4 and the regression, and record the results in the results log. **Done on 2026-09-25 on one phone**, with one failure (§8.7 bug 1).
4. Fix the two preference bugs (§8.7). **Fixed on 2026-09-25 (`5c1e89c`).**
5. Build again, retest the preference switches on both screens, and finish the live checks: chat between the two phones, a live reservation alert, and the token refresh after more than 1 hour. **Done on 2026-09-25 at `c7323b8`; all passed.** Sign-up of a new account also passed.
6. Run the negative tests on the live project. **Passed on 2026-09-25** (21 of 21, `supabase/tests/phase4_live_safety_check.sql`). Not yet done: the password-reset email (part of the step 10 regression in §4).
7. Sign off Phase 4. **Signed off by the developer on 2026-09-25.** The password-reset email was not tested; Phase 4 did not change its code.

Phase 5 (Maps, Reviews, Reports) and Phase 6 (Admin and Final Release) follow; neither has started.

---

## 7. Other observations (not scheduled; verify before acting)

- **Food details screen.** Since §3.5 (verified on 2026-09-25) the header shows the uploaded image, and the description and "Pickup Address" come from the post. **Changed in §11 (passed on two phones on 2026-09-26, §11.6):** allergens, storage notes, pickup window and handover are saved and shown, the Maps search and Donor page use the listing, and "Distance" became "Expires".
- **Dropped requests.** Since §3.4, feed and My Listings loads requested during another `FoodViewModel` request are queued, not dropped. **Since §11** `FoodViewModel` writes (create, edit, delete) wait for the running request instead; only a second write before the first has finished is ignored, and the buttons stay disabled until then. In the §11.4 test on 2026-09-26 every Post Listing, Save changes and Delete ended with its result; a write started during a running load was not tested on purpose. Still ignored while any request is running: every `ReservationViewModel` request (for example a tab-tap reload during a running refresh).
- **No realtime updates outside Phase 4.** Since §3.5 (verified on 2026-09-25) the Home feed reloads after a cancel. Claim does not change the feed. Apart from Phase 4's open chat and alerts (§8), nothing updates in real time across devices.
- **Placeholder or hard-coded UI values:**
  - the greeting "Good evening, Mika". **Changed on 2026-09-25 (§3.5, commit `851e8cb`): it now uses the signed-in profile's first name and the time of day. Built; the evening greeting was seen in the Phase 4 test on 2026-09-25.**
  - the location "Del Rosarrio, Balilihan". **The profile location was removed on 2026-09-26 (§10; passed on two phones).** The Home and Activity location pickers still show sample places.
  - "Browse all 10 food listings". **Removed in §11 with the Recommended page; the Home checks of §11.4 step 1 passed on 2026-09-26.**
  - Activity metrics 48 / 128. **Replaced by real totals (§9); passed on two phones on 2026-09-26 (§9.6).**
  - sample Purchases, Claims and reviews (sample Alerts were replaced by real alerts in Phase 4). **The sample Purchases and Claims were replaced (§9); passed on two phones on 2026-09-26 (§9.6).** Sample reviews remain.
  - The "Nearby" filter relies on a fixed distance. **Removed in §11, with the Nearest sort, distances, ratings and the Home location row (passed on two phones on 2026-09-26, §11.4 steps 1 and 7–9).** The "For Sale" filter matched nothing, because every live listing was "Free"; since §9 it matches listings with a price (passed on 2026-09-26, §9.4 step 5).
- **Read access after cancel.** `is_reservation_recipient()` ignores reservation status, so a recipient keeps read access to a post after cancelling.
- **Signed URL expiry.** Signed image URLs expire after 1 hour and are not refreshed while a screen stays open.
- **Raw error messages.** User-facing errors are raw exception messages from supabase-kt and Firebase. How readable the RPC error text is has not been checked.

---

## 8. Phase 4 — Messaging and Notifications

**Status: complete. Signed off by the developer on 2026-09-25.**
- `assembleDebug` passed at `266325b`, and again at `c7323b8` with the §8.7 fixes. The migration was applied to the live project, and `messages` and `notifications` are in the `supabase_realtime` publication.
- The one-phone Phase 4 test and the regression passed at `266325b`, except saving a preference switched back on.
- At `c7323b8`, on two phones: the §8.7 retest passed, and so did the live checks (message alerts, chat between the phones, a reservation alert, and alerts after more than 1 hour on Home). A new account signed up ([results log](#results-log)).
- The live safety check (`supabase/tests/phase4_live_safety_check.sql`, §8.6) passed 21 of 21 on 2026-09-25.
- Not tested: the password-reset email (§8.5). Phase 4 did not change its code.
 Scope chosen by the developer: chat per listing; in-app alerts only (no push); live updates with Supabase Realtime; notification preferences stored in Supabase.

### 8.1 Database (`supabase/migrations/20260926_phase4_messaging_notifications.sql`)

- **`conversations`**: one thread per `(food_post_id, recipient_id)`; `donor_id` is copied from the listing by the RPCs. Per-side read times (`recipient_last_read_at`, `donor_last_read_at`) and `last_message_at`. Deleted with the listing (cascade).
- **`messages`**: `conversation_id`, `sender_id`, `body` (1–2000 characters after trimming), `created_at`.
- **`notifications`**: per-user alerts of type `MESSAGE`, `RESERVATION_CREATED`, `RESERVATION_CANCELLED` or `RESERVATION_CLAIMED`, with optional links to the listing, conversation and reservation, and `read_at`.
- **`notification_preferences`**: one row per user (`message_alerts`, `reservation_alerts`, `recommendations`, `pickup_reminders`). A missing row means everything is on. Only `message_alerts` and `reservation_alerts` affect alerts today.
- **Access:** clients may only SELECT conversations and messages they take part in, and their own alerts and preferences. `notification_preferences` also allows INSERT/UPDATE of the caller's own row. Every other write goes through SECURITY DEFINER functions that take the caller from the JWT:
  - `start_listing_conversation(p_food_post_id)`: get or create the caller's thread with the donor. Rejects the caller's own listing, and listings that are not available unless the caller has a reservation on them.
  - `open_reservation_conversation(p_reservation_id)`: get or create the thread of a reservation; either its recipient or the listing's donor may call it.
  - `send_message(p_conversation_id, p_body)`: participants only; also updates the thread and alerts the other participant.
  - `mark_conversation_read(p_conversation_id)`: also marks that thread's message alerts read.
  - `mark_notifications_read(p_ids)`: the caller's alerts; `null` marks all.
  - `get_my_conversations(p_conversation_id)`: the caller's threads with the listing name and image path, the other participant's **display name only**, the last message and the unread count.
- **Alerts** are created only by the database: `notify_user()` (not executable by clients) respects the preferences, and a trigger on `reservations` alerts the donor when a reservation is created or cancelled and the recipient when it is claimed. The Phase 3 RPCs are unchanged.
- **Realtime:** `messages` and `notifications` are added to the `supabase_realtime` publication; Realtime applies the SELECT policies to what each client receives.

### 8.2 Privacy change

Before Phase 4, recipients never saw the donor's name. In a conversation, **both participants see each other's display name** (through `get_my_conversations()`), never email or phone. `users` RLS is unchanged.

### 8.3 App

- **Dependencies:** `realtime-kt` added; the Ktor engine changed from `ktor-client-android` to `ktor-client-okhttp` (the Android engine has no WebSocket support) and Ktor aligned to 3.4.2, the version supabase-kt 3.5.0 uses.
- **Realtime auth:** Realtime uses the same Firebase ID token as PostgREST (the client's `accessToken` lambda), read when a channel joins. Without the Supabase Auth plugin it is never refreshed automatically, so `NotificationViewModel` calls `realtime.setAuth()` every 5 minutes (confirmed by reading the realtime-kt 3.5.0 sources). On 2026-09-25 alerts still arrived live after the app had been open on Home for more than 65 minutes ([results log](#results-log)).
- **Data layer:** `MessageRepository`, `NotificationRepository`, `realtimeInserts()` (a channel per stream, removed when collection stops), `MessageViewModel` and `NotificationViewModel` (created per profile like the Phase 3 ViewModels and cleared on sign-out). `signedUrlOrNull()` is shared with `withSignedImageUrl()`.
- **UI:**
  - Home header: new chat icon (badge = unread message alerts) → Messages inbox (`MessagesScreen`); bell badge = all unread alerts.
  - `ChatScreen`: full-screen conversation over any screen, live updates, marks the thread read.
  - Food details: "Message Donor", the Allergens and Storage rows, and the details dialog's "Ask for exact location"/"Message provider" links open the real chat (prefilled where the demo chat was). On your own listing the button reads "Your listing".
  - Activity: "Message donor"/"Message recipient" in reservation details; the bell dot only shows with unread alerts.
  - Alerts: real alerts, filters All/Messages/Reservations, Today/Earlier, mark read, tap to open the chat or the Activity tab, settings dialog saved to Supabase. The sample alerts and the "Personalized for you" card were removed.
  - Profile → Preferences → Notifications: "New messages", "Reservation updates", "Food recommendations" and "Pickup reminders" are saved to Supabase; "Notification sound" stays on the device.

### 8.4 Known limits

- No push notifications (the app must be open for live updates; alerts are still stored and shown later).
- No blocking or reporting of users (Phase 5), and no editing or deleting of messages.
- No rate limit on messages beyond the 2000-character limit.
- Deleting a listing deletes its conversations, messages and related alerts.
- The chat icon badge counts unread message **alerts**, so it stays empty when message alerts are turned off; the inbox still shows unread counts.
- The Profile screen's bell still opens settings and always shows its dot (unchanged demo UI).

### 8.5 Status matrix

| Item | Implemented (code) | Verified | Not yet verified |
|---|---|---|---|
| Migration applies and re-runs | Yes | Local Postgres 16 with Supabase-like stubs; applied to the live project (2026-09-25) | — |
| RPCs, RLS denials, alerts, preferences | Yes (SQL) | Local three-account SQL test (§8.6); the one-phone Phase 4 test and the two-phone retest; the live safety check, 21 of 21 (2026-09-25) | — |
| Realtime with Firebase Third-Party Auth | Yes | Connects; the subscriptions carry the Firebase claims; the RLS replay as account A passes; the sender's own message arrives live; on two phones, live message alerts, live chat, a live reservation alert, and live alerts after more than 1 hour on Home (2026-09-25) | — |
| Kotlin data layer and ViewModels | Yes | AGP build at `266325b` and `c7323b8`; device runs (2026-09-25) | — |
| Chat, inbox, alerts, preferences UI | Yes | The one-phone Phase 4 test and the two-phone retest (2026-09-25) | — |
| Preference fixes (§8.7) | Yes (2026-09-25) | Type-checked; JSON encoding checked; AGP build at `c7323b8`; device retest on both screens (2026-09-25) | — |
| Ktor engine change (OkHttp) | Yes | The regression on the Phase 4 build; sign-up of a new account (2026-09-25) | The password-reset email was not reported |

### 8.6 How the Phase 4 code was checked (2026-09-25)

The cloud session that wrote Phase 4 has no Android SDK, and Google's Maven repository is blocked there, so no AGP build was possible.
- **SQL:** all five migrations were run in order on a local PostgreSQL 16 with stand-ins for Supabase's `auth.jwt()`, roles, Storage tables and the `supabase_realtime` publication; the Phase 4 file was then re-run without errors. A test with three accounts (A donor, B recipient, C outsider) confirmed: starting a thread and messaging; unread counts and read marking; MESSAGE and reservation alerts (created, cancelled, claimed); the same thread from the listing and from the reservation; the preference switch suppressing message alerts; the preferences upsert; and the denials (messaging your own listing, direct inserts into `messages`/`notifications`, calling `notify_user`, a non-participant reading, sending or opening a thread, writing another user's preferences, calls without a valid token).
- **Kotlin:** every app source file was compiled with kotlinc 2.2.10 plus the Compose and serialization compiler plugins, against the real supabase-kt 3.5.0 JVM jars and Compose Multiplatform desktop jars, with small stand-ins for Android, AndroidX Activity/Lifecycle and Firebase APIs. It compiled with no errors and no warnings in the new code. This checks types and API usage only; it is not an Android build.
- **Live safety check** (`supabase/tests/phase4_live_safety_check.sql`, written 2026-09-25): a single `do` block for the SQL Editor. It simulates the Firebase token claims of the most recent chat's two participants and of the newest other account, tries the forbidden actions, and always ends with an error on purpose, so everything it did is rolled back. The report is the error text.
  - Before it ran on the live project, it was dry-run on an in-memory Postgres (PGlite 0.3) built from the five migrations plus stand-ins for `auth.jwt()`, the Supabase roles, Storage and the Realtime publication: 21 of 21 passed and the data was unchanged.
  - With one rule deliberately broken at a time, the matching checks reported FAIL: direct message inserts allowed, `notify_user` executable, chats readable by any user, token issuer not checked, messages readable without a login, alert settings open to anyone, all profile rows readable.
  - Result on the live project: 21 of 21 passed (results log).

### 8.7 Bugs found in the device test (2026-09-25)

**Status: fixed on 2026-09-25 (commit `5c1e89c`), then built at `c7323b8` and retested on a device the same day: passed ([results log](#results-log)).** No SQL or migration change.

1. **A preference switched back on is never saved.** In Profile → Preferences → Notifications (and in Alerts → settings), turning "New messages" off saved, but turning it back on reported "Preferences saved to your account." while `message_alerts` stayed false.
   - **Cause.** supabase-kt 3.5.0's default serializer is `Json { ignoreUnknownKeys = true }`, without `encodeDefaults`. kotlinx.serialization therefore leaves out properties equal to their default. `NotificationPreferences` defaults all four booleans to `true`, so a `true` value was missing from the upsert body in `NotificationRepository.savePreferences()`. PostgREST's merge-duplicates updates only the columns it receives.
   - This affected all four switches on both screens.
   - **Fix** (`model/AppNotification.kt`): `@EncodeDefault` on `messageAlerts`, `reservationAlerts`, `recommendations` and `pickupReminders`. The defaults stay, for the all-on fallback in `getPreferences()`.
   - Checked here: encoding `NotificationPreferences(userId = "x")` with that `Json` now gives all four keys.
   - `encodeDefaults` was not turned on globally, because that would change the inserts of other models.
2. **The switches show on before the saved settings load.**
   - **Fix.** The Alerts settings dialog (`AlertScreen.kt`) and the Notifications page (`ProfileSections.kt`) show "Loading your alert settings…" instead of the account switches until the settings have loaded. Save stays disabled until then. "Notification sound" (on the device) is still shown.
   - `SectionButton` gained an optional `enabled` parameter.
   - If loading fails, the page keeps showing "Loading…" until it is reopened; the error is not shown there.

**Not fixed (noted only).** The unused `FoodRepository.updateFoodPost()` sends a whole `FoodPost` through the same serializer. Any field equal to its default would be left out of the update. Fix it before anything calls it.

---

## 9. Activity: Purchases, Claims and listing prices

**Status (2026-09-26): written on branch `claude/activity-purchases-claims` (from `master` at `5eaee72`) and merged into `master` through PR #3 on 2026-09-26, together with the Phase 1 demo fixes (§10), before its own device test. Built with AGP on 2026-09-26 as part of the §10 build (§10.6), and the migration was applied to the live project the same day. The §9.4 two-phone test passed on 2026-09-26 (reported by the developer), except the "Reserved" dialog after Reserve, which never appears (§9.6).** This is not Phase 5: no reviews, ratings, maps, reports or online payments. Payment is always in person at pickup.

### 9.1 What the tabs show (the developer chose "split by stage")

- **My Reservations:** the recipient's reservations that are still `RESERVED`, and `CANCELLED` ones. **Changed from Phase 3**, where it also listed `CLAIMED` ones.
- **Purchases:** the recipient's `CLAIMED` reservations with a price (`unit_price > 0`). Card: "3 packs • ₱135.30". Details: "Price: ₱45.10 per unit", "Total: ₱135.30, paid at pickup.", closing line "This is your live purchase, paid at pickup."
- **Claims:** the recipient's `CLAIMED` free reservations. Card: "2 pcs • Free". Details: "Free food you picked up.", closing line "This is free food you picked up."
- A reservation moves from My Reservations to Purchases or Claims when the donor marks it claimed. Each reservation appears in exactly one recipient tab.
- **My Donations:** unchanged, plus the total ("• ₱135.30" or "• Free") and, for priced reservations, "Price: … per unit" and "Collect … at pickup." in the details.
- Tapping Purchases or Claims reloads the reservation lists, like My Reservations and My Donations.
- **"Reservation claimed" alert:** opens Activity on Claims, and once the reloaded list shows the reservation as claimed, switches to Purchases if it has a price and opens its details. If it cannot be found, Activity stays on Claims.
- The six sample Purchases and Claims records were removed. The other demo UI is unchanged.
- **Completed / Items Rescued** are computed from data Activity already loads: the number of `CLAIMED` reservations where the user is the recipient plus those on the user's listings, and the sum of their quantities (each unit — pack, piece, kg — counts as one item). The notices explain this.

### 9.2 Prices (`supabase/migrations/20260927_activity_prices.sql`)

- `food_posts.price numeric(10,2) not null default 0`, check 0–99,999.99. 0 = free donation; existing listings become free.
- `reservations.unit_price numeric(10,2) not null default 0`, same check. Set only by the BEFORE INSERT trigger `reservations_set_unit_price` (function `set_reservation_unit_price()`, SECURITY DEFINER, not executable by clients), which always overwrites it with the listing's price. `create_reservation` already holds `FOR UPDATE` on the listing, so the price is the one in effect when the quantity was checked. A later price change does not change existing reservations.
- `get_donor_reservations()` dropped and recreated with `unit_price` (still `stable security definer set search_path = public`, recipient names only, executable by `authenticated` only).
- Unchanged: the Phase 3 RPCs (still the only way to write reservations), the Phase 4 trigger and RPCs, and all RLS. The migration is safe to run again. Running the Phase 3 files again after it fails (`supabase/README.md`).
- The app never sends a reservation price. Totals come from the reservation's `unit_price × quantity`, computed with `BigDecimal.valueOf(unitPrice).multiply(BigDecimal(quantity))` and shown with two decimals (`formatPeso()`, `reservationTotal()` in `model/FoodPost.kt`).

### 9.3 App changes

- `model/FoodPost.kt`: `FoodPost.price` (default 0.0 when reading); `NewFoodPost.price` has no default, so 0.0 is always sent; `FoodPostDraft.price`; the money helpers.
- `model/Reservation.kt`: `unitPrice` on `Reservation` and `DonorReservationRow`.
- `PostScreen.kt`: Donation saves 0, For Sale saves the validated price; new error "Enter a price of at most ₱99,999.99."
- `MainActivity.kt`: Home cards show "₱45.10" or "Free", so the "For Sale" and "Donation" chips now match live listings. Food details show the price, and the note under Reserve reads "Pay ₱<unit price × chosen quantity> at pickup. The app takes no payments." for a priced listing (free: unchanged).
- `viewmodel/ReservationViewModel.kt`: after reserving a priced listing the message is "Reservation created. Pay ₱<total> at pickup.", from the `unit_price` the database returned (free: "Reservation created. Please coordinate pickup with the donor.").
- `ActivityScreen.kt`, `AlertScreen.kt`: §9.1.
- **Order:** run the migration before installing this build. Until the `price` column exists, Create Listing fails.

### 9.4 Two-phone test (Account A = donor on phone 1, Account B = recipient on phone 2)

Preconditions: run `20260927_activity_prices.sql` in the SQL Editor (check: `select table_name, column_name from information_schema.columns where column_name in ('price','unit_price') and table_schema = 'public';` returns `food_posts.price` and `reservations.unit_price`; run on 2026-09-26). Build `master` (PR #3 or later) and install it on both phones.

**A, part 1**
1. [Regression] Sign in as A. Home shows "Good <morning/afternoon/evening>, <A's first name> 👋".
2. [New] Post → "I want to sell", name `PRICE TEST`, a photo, any category, quantity `5`, unit `packs`, price `45.10`, future dates → Post Listing. Back on Home, its card shows "₱45.10".
3. [New] Post → "I want to donate", name `FREE TEST`, a photo, quantity `4`, unit `pcs` → Post Listing. Its card shows "Free".
4. [New] Post → "I want to sell", filled in as in step 2 but with `100000` in "Price per unit (₱)" (not in Quantity) → Post Listing shows "Enter a price of at most ₱99,999.99." Leave without posting.
5. [New] Home chips: "For Sale" lists `PRICE TEST` and not `FREE TEST`; "Donation" lists `FREE TEST` and not `PRICE TEST`.
6. [New] Activity: write down Completed and Items Rescued (A0, A1). Tap Completed: "<A0> completed: your reservations that donors marked claimed, plus claimed reservations on your listings." The Purchases and Claims tabs show no sample records.

**B, part 1**
7. [New] Sign in as B. Activity: write down Completed and Items Rescued (B0, B1).
8. [New] Home → `PRICE TEST`: the price box shows "₱45.10" / "For sale". Set quantity 3: the small grey line under the three buttons reads "Pay ₱135.30 at pickup. The app takes no payments." Reserve → dialog "Reserved": "Reservation created. Pay ₱135.30 at pickup." (**Known issue:** this dialog never appears; the details close and Home reloads, §9.6.)
9. [Regression] Home → `FREE TEST`: "This is a free donation. Please be respectful and pick up on time." Quantity 2 → Reserve → "Reservation created. Please coordinate pickup with the donor." (never appears, §9.6).
10. [New] Activity → My Reservations: "`PRICE TEST` 3 packs • ₱135.30", status Reserved; details "Your reservation.", "Price: ₱45.10 per unit", "Pay ₱135.30 at pickup.", closing "This is your live reservation.", buttons Cancel and "Message donor". `FREE TEST`: "2 pcs • Free". Purchases and Claims do not list either one (Claims can show older claimed reservations as "Free").

**A, part 2**
11. [New] Activity → My Donations: `PRICE TEST` "3 packs • ₱135.30"; details "Recipient: <B's name>", "Reservation for your listing.", "Price: ₱45.10 per unit", "Collect ₱135.30 at pickup.". `FREE TEST` "2 pcs • Free" with no price lines. [Regression] Alerts has two "New reservation" alerts.
12. [New] Price lock: in the SQL Editor run `update public.food_posts set price = 99 where id = (select id from public.food_posts where name = 'PRICE TEST' order by created_at desc limit 1) returning name, price, created_at;` (only the newest `PRICE TEST`) → 1 row, `99.00`. Tap My Donations again: still "₱135.30". Home shows the new price only after it reloads (step 19).
13. [Regression] Open `PRICE TEST` in My Donations → "Mark claimed" → "Reservation" dialog "Reservation marked as claimed.". Same for `FREE TEST`.
14. [New] Completed = A0 + 2, Items Rescued = A1 + 5.

**B, part 2**
15. [Regression] Alerts shows two "Reservation claimed" alerts ("The donor marked your reservation of PRICE TEST as claimed." and the same for FREE TEST).
16. [New] Tap the `PRICE TEST` alert: Activity opens on **Purchases** with its details open: "3 packs • ₱135.30", "Status: Claimed", "Picked up: <time>", "Price: ₱45.10 per unit", "Total: ₱135.30, paid at pickup.", "This is your live purchase, paid at pickup." Only Close and "Message donor" (no Cancel). The total is not ₱297.00, so the reserved price was kept.
17. [New] Back to Alerts, tap the `FREE TEST` alert: Activity opens on **Claims** with "2 pcs • Free", "Free food you picked up.", "This is free food you picked up."
18. [New] My Reservations no longer lists these two. Completed = B0 + 2, Items Rescued = B1 + 5.
19. [New] Home (open Post, then Back, to reload): `PRICE TEST` shows "₱99.00". Reserve 1 → "Reservation created. Pay ₱99.00 at pickup." (never appears, §9.6). [Regression] Activity → My Reservations → it → Cancel → "Reservation cancelled. The quantity is available again."; it stays in My Reservations as Cancelled, "1 packs • ₱99.00".
20. [Regression] Purchases → `PRICE TEST` → "Message donor" → send "Thanks!". On A's phone, the chat badge goes up without refreshing; A opens the thread and replies; B sees the reply live.

**A, part 3**
21. [Regression] Alerts has "Reservation cancelled" for `PRICE TEST`; tapping it opens My Donations.
22. [Regression] Delete `PRICE TEST` and `FREE TEST` in Activity → My Listings; they leave Home and My Listings. Sign out, sign in again. Deleting a listing also deletes its reservations, chat and alerts (§9.6), so B's Purchases and Claims lose these two and both totals drop back.

### 9.5 How this was checked (2026-09-25)

- **SQL:** all migrations run in order on a local PostgreSQL 16 with stand-ins for Supabase's roles and `auth.jwt()`, then `20260927_activity_prices.sql` run twice more without errors. A two-account test confirmed: the price check rejects −1 and 100000; a donor's own insert saves a price; `create_reservation` returns `unit_price` 45.10 for a priced listing and 0.00 for a free one; clients still cannot insert reservations; after the donor changed the price to 99 the earlier reservation kept 45.10 and a new one got 99.00; a directly supplied `unit_price` is overwritten; `get_donor_reservations()` returns names and `unit_price`; claim and cancel still work and still create the Phase 4 alerts; clients cannot execute the trigger function; `anon` cannot execute `get_donor_reservations()`. Running `20260924_phase3_donor_recipient_names.sql` afterwards fails with "cannot change return type of existing function", as documented.
- **Kotlin:** all app sources compiled with kotlinc 2.2.10 and the Compose and serialization plugins against the supabase-kt 3.5.0 JVM jars and Compose desktop jars, with small stand-ins for Android and Firebase APIs: no errors; the only warning is the deprecated `FormatListBulleted` that was already there. A small JVM check: `formatPeso(45.1)` = ₱45.10, 0.1 + 0.2 = ₱0.30, `reservationTotal(45.1, 3)` = ₱135.30, `NewFoodPost` encodes `"price":0.0`, and `unit_price: 45.10` decodes. This is not an Android build.
- **Since then (2026-09-26):** built with AGP as part of the §10 build (§10.6); the developer ran the migration on the live project; merged into `master` through PR #3; device test in §9.6.

### 9.6 Device test (2026-09-26) and known issues

- **Result:** the developer ran §9.4 on two phones (A = donor on phone 1, B = recipient on phone 2) with the §10 build, given in parts, and reported that every step passed except the "Reserved" dialog below. Recorded values: A0 = 2, A1 = 2, B0 = 2, B1 = 2; steps 14 and 18 then expected Completed 4 and Items Rescued 7, and were reported as passed.
  - Step 4: the first try put `100000` into Quantity, leaving the price empty, and correctly got "Enter a price greater than zero, with up to two decimal places."; the redo with the price field showed "Enter a price of at most ₱99,999.99.".
  - Step 12: A had two `PRICE TEST` listings (another account had reserved the first one); the newest-only update changed the tested one and returned 1 row, `99.00`. Home showed "₱99.00" only after a reload (Post → Back), since the feed does not update live (§7).
- **Known issue, not fixed: the "Reserved" dialog never appears.** After a successful Reserve, `FoodDetailsScreen` sets its "Reserved" notice ("Reservation created. Pay ₱… at pickup." or "…Please coordinate pickup with the donor.") and at once calls `onReserved()`. `HomeScreen`'s `onReserved` reloads the feed and sets `selectedFood = null`, which closes the details dialog and the notice inside it before it is drawn. Present since `d4f65fe`, so not caused by §9 or §10. It affects §9.4 steps 8, 9 and 19 and §10.4 step 17. The reservation itself is created correctly; the amount still shows in My Reservations and in the line under the buttons. The developer chose on 2026-09-26 to fix it with the Phase 3 demo fixes, together with Buy Now / Claim Now. Proposed fix: keep the details open until the dialog's OK, then close them; the feed can still reload at once.
- **Observations for later (not bugs in §9):**
  - The "Pay ₱… at pickup" line under the buttons is 10 sp grey text and was missed at first.
  - The Buy Now / Claim Now demo checkout looks like a receipt and was taken for the real flow.
  - Deleting a listing deletes its reservations (`on delete cascade`), with their chats and alerts, so a buyer's Purchases and Claims history disappears when the donor deletes the listing.

---

## 10. Phase 1 demo fixes: accounts, profile, security

**Status (2026-09-26): complete.** Written on branch `claude/phase1-demo-fixes` (from `claude/activity-purchases-claims` at `32915d1`) and merged into `master` through PR #3 on 2026-09-26, which also brought in §9. Built with AGP (§10.6); the migration was applied to the live project, and the developer ran the Phase 1 checks on two phones and reported that they passed (§10.6, [results log](#results-log)). "Keep me signed in" starting unchecked was changed after PR #3, before the test, and reached `master` in a later pull request. The new `google-services.json` is committed (§10.2). Fixes to finished Phase 1 features, found in the developer's audit; not part of Phase 5.

### 10.1 What changed

- **Google sign-in** (`data/GoogleSignIn.kt`, `MainActivity.kt`). One "Continue with Google" button on the Sign In and Create Account tabs; Apple and Facebook are removed. Credential Manager (`androidx.credentials` 1.6.0, `googleid` 1.2.0; 1.2.1 is built with Kotlin 2.4 metadata, which this project's Kotlin 2.2.10 cannot read) returns a Google ID token for the server client ID `R.string.default_web_client_id`, which the Google Services plugin generates from `google-services.json` (never hard-coded). Firebase signs in with `GoogleAuthProvider.getCredential(idToken, null)`, then the app opens Home as after sign-up (`openAuthenticatedHome(refreshFirebaseToken = true)`), creating the profile with the Google name and email.
  - Cancelling the picker shows nothing. No Google account: "No Google account was found on this phone. Add one in your phone's Settings, then try again." Firebase errors use the existing texts (for example "An account already exists for this email address."). Anything else: "Google sign-in failed. Please try again." The no-account and failure messages add a second line "Details: <message>" so setup errors (wrong SHA-1 or client ID) can be read without logcat.
  - A new Google account without the role claim gets "We could not load your profile. Check your internet connection and try again." and stays signed in to Firebase, as after an email sign-up. After the role script runs, tapping Google again works.
  - Logging out also clears the Credential Manager state (best effort).
- **Keep me signed in.** The checkbox starts **unchecked** (the developer's choice, 2026-09-26). Stored on the device (`salusalo_session`, `keep_signed_in`; a device that never saved it keeps its session). Sign In tab (email or Google): the checkbox value; sign-up and Google from the Create Account tab: kept. When unchecked, a fresh start of the app (for example after swiping it away from Recents) signs out and shows the Welcome screen. Rotation and restores by Android keep the session.
- **Honest texts.** Welcome page 1 left card: "Find surplus food" / "Browse food shared in your community."; page 2: "Add a photo, quantity, and pickup location."; page 3 body: "Hold food for pickup, message the provider, and collect it at the agreed place.", right card "Message before pickup" / "Chat with the donor to arrange pickup."; sign-in footer: "Agree on pickup details in the chat and check the food before you take it." The illustrations are unchanged.
- **Display name** saved through `update_my_profile_name`. Account Details shows the sign-in email read-only ("Email (used to sign in)"). Texts: "Enter your name.", "Your name must be 120 characters or fewer.", "Saved to your account."; errors show the database's short text, or "Your name could not be saved. Check your internet connection and try again." for network errors. The new profile goes up to the app, so the Home greeting and Profile header change at once. The "Mika" default and the device-only name are gone.
- **Profile location removed:** the header line, the Location field, the Preferences → Privacy page and its switch.
- **Profile photo.** Shrunk to at most 512 px (upright, using EXIF), JPEG, off the main thread; uploaded to the private `profile-images` bucket at a new path each time (`<uid>/profile-<millis>.jpg`, no upsert, because overwriting one path can leave the CDN serving the old picture); saved through `set_my_profile_image`; the previous file is then deleted (best effort), and a failed save deletes the new upload. Shown with a 1-hour signed URL, falling back to the initial. Texts: "Profile photo updated.", "This photo could not be read. Please choose another photo.", "Your photo could not be uploaded. Check your internet connection and try again." (or the database's short error).
- **Account Security.** The false "not connected to authentication" text and the Signed-in devices page are removed. Change password: "Current password", "New password", "Confirm new password", button "Change password". Checks: "Enter your current password.", "Your password must be at least 6 characters.", "Your passwords do not match.". Reauthentication failure: "Your current password is incorrect."; weak new password: "Choose a stronger new password." (mapped separately, so it is never reported as a wrong current password); "Too many attempts. Please wait a few minutes and try again."; otherwise "Your password could not be changed. Check your internet connection and try again."; success: "Your password was changed.". A Google-only account sees "This account signs in with Google, so it has no password to change."
- **Member since** from `users.created_at`: the stat shows e.g. "Sep 2026", the Membership page "Member since September 2026". The "sample value" text is gone.
- **Settings captions:** "Edit your display name", "Change your password", "Notifications and app settings"; Profile menu "Notification and app preferences".
- **Unchanged (later tasks):** Buy Now / Claim Now, Home and listing demos, the other Profile stats, Ratings & Reviews, the Identity Verified badge and verification pages, Saved Listings, Report a concern, notification sound and app preferences, Activity.

### 10.2 Setup (developer)

1. **Firebase console:** Authentication → Sign-in method → enable **Google** (choose a support email). Project settings → Your apps → the Android app `com.example.salusalo` → **Add fingerprint** with the debug SHA-1 (`./gradlew signingReport`, or `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`). Download the new `google-services.json` and commit it to this branch as `app/google-services.json`. It must now contain an OAuth client; without it `R.string.default_web_client_id` does not exist and the build fails. **Done on 2026-09-26:** Google enabled, the debug SHA-1 added, and the new `google-services.json` (Android and web OAuth clients) committed to this branch.
2. **Supabase:** run `supabase/migrations/20260927_activity_prices.sql` (if not yet run), then `supabase/migrations/20260928_profile_updates.sql`, by hand in the SQL Editor. Check: `select proname from pg_proc where proname in ('update_my_profile_name','set_my_profile_image');` returns 2 rows. **Both run on 2026-09-26.**
3. Build and install on both phones.

### 10.3 Database (`supabase/migrations/20260928_profile_updates.sql`)

`update_my_profile_name(p_name)` and `set_my_profile_image(p_path)`: SECURITY DEFINER, `set search_path = public`, executable by `authenticated` only. Both find the caller's row from `auth.jwt() ->> 'sub'` after `is_salusalo_firebase_jwt()`, never from an argument, and change only `name` or `profile_image_url` (never `role`, `email` or `firebase_uid`). The name is trimmed and must be 1–120 characters. The photo path must start with `<sub>/`, contain no `..`, be at most 512 characters and exist in `profile-images`. `users` still has no UPDATE grant or policy for clients. Safe to run again.

### 10.4 Two-phone test

**Preconditions:** §10.2 done. A (phone 1) and B (phone 2) are existing email/password accounts. A has a reservation on one of B's listings (so B's My Donations shows "Recipient: <A's name>") and a chat with B. A is signed in only on phone 1. G is a Google account on phone 1 (phone Settings → Accounts) that has **no** Salu-Salo account yet and no role claim. Write down A's current name and password. Do not use a Google account whose email already has an email/password Salu-Salo account (§10.5).

**A (phone 1)**
1. [New] Signed out, swipe through Welcome: page 1 left card "Find surplus food" / "Browse food shared in your community."; page 2 left card "Add a photo, quantity, and pickup location."; page 3 "Hold food for pickup, message the provider, and collect it at the agreed place." with the right card "Message before pickup" / "Chat with the donor to arrange pickup.".
2. [New] Sign In tab: under "or continue with" there is one button, "Continue with Google" (no Apple or Facebook); the same on Create Account. Footer: "Your safety matters" / "Agree on pickup details in the chat and check the food before you take it."
3. [New] Sign In tab: "Keep me signed in" starts **unchecked**. Tick it and sign in as A. Swipe the app away from Recents and open it again: Home opens.
4. [New] Profile → Log out. Sign in as A leaving it **unchecked**. Rotate the phone: still Home. Swipe the app away from Recents and open it again: the Welcome screen shows.
5. [Regression] Sign in as A with it ticked.
6. [New] Profile: no location line under the name. The Member Since stat shows the month A signed up, e.g. "Sep 2026"; tapping it shows "Member since September 2026" and "Account name: <A's name>", with no "sample value" text.
7. [New] Profile → Account Settings: "Display name" shows A's name; "Email (used to sign in)" shows A's email, greyed out and not editable. Clear the name → Save account details → "Enter your name.". Type "  Ana Test  " → Save → "Saved to your account.". Back: the Profile header shows "Ana Test"; Home says "Good <morning/afternoon/evening>, Ana 👋".
8. [New] Profile → camera button → choose photo 1: a spinner, then "Profile photo updated."; the circle shows photo 1. Log out and sign in again (checked): photo 1 still shows.
9. [New] Camera button → choose a different photo 2: "Profile photo updated."; the circle shows photo 2, not photo 1.
10. [New] Account Settings → Account security → Change password:
    - a wrong current password, new "abc123" twice → "Your current password is incorrect.";
    - the right current password, new "abc12" twice → "Your password must be at least 6 characters.";
    - the right current password, two different new passwords → "Your passwords do not match.";
    - the right current password, a new password P2 twice → "Your password was changed." and the fields are empty.
11. [New] Log out. Sign in with the old password → "The email address or password is incorrect.". Sign in with P2 → Home.
12. [New] Profile → bell icon at the top (opens Settings): "Account details / Edit your display name", "Security / Change your password", "Preferences / Notifications and app settings". Preferences has no Privacy; Security has only "Change password"; there is no Signed-in devices page and no "The app is not connected to authentication yet" text.

**B (phone 2)**
13. [New] Home → chat icon (Messages): the thread with A shows "Ana Test"; opening it shows "Ana Test".
14. [New] Activity → My Donations → A's reservation → "Recipient: Ana Test".
15. [Regression] In the chat with A, send "Name check".

**A (phone 1)**
16. [Regression] The chat badge and a message alert appear without refreshing. Open the chat and reply "Got it"; B sees it live.
17. [Regression] Open one of B's listings → Reserve 1 → "Reservation created…" (this dialog never appears, §9.6). Activity → My Reservations → it → Cancel → "Reservation cancelled. The quantity is available again."
18. [New] Account Settings → set the name back to A's original name → "Saved to your account.". Optionally change the password back (current P2, new = the old password). Log out.

**G (phone 1, signed out)**
19. [New] Sign In → Continue with Google → close the account picker: no dialog, still on Sign In.
20. [New] Continue with Google → choose G → "We could not load your profile. Check your internet connection and try again." (no role claim yet). A "Details:" line instead means a setup problem (§10.2).
21. [New] Run the role script for G (outside this repo). Continue with Google → G → Home greets G's Google first name. Profile → Account Settings: the email field shows G's Gmail address.
22. [New] Account Settings → Account security → Change password: "This account signs in with Google, so it has no password to change."
23. [New] Log out → Continue with Google → the account picker appears again → G → Home. Log out.

**Regression (Phases 1–4, and §9 if not yet tested):** sign-up of a new email account (with the role script); the password-reset email; create and delete a listing with a photo; the Home feed; the §4 A/B reservation test (reserve, cancel, claim); the §4 Phase 4 chat and alerts test; the §9.4 test.

### 10.5 Known limits

- **Google sign-in can take over an unverified email account.** Signing in with Google using the email of an existing email/password account whose email was never verified makes Firebase drop that account's password: it keeps its UID and profile but becomes Google-only. The test uses a Google account with no Salu-Salo account for this reason.
- After a password change, Firebase ends that account's session on any other phone within about an hour. The app does not notice, so that phone shows load errors until someone signs in again.
- The profile photo is visible only to its owner (the Phase 1 `profile-images` policy allows only the owner's folder). There is no way to remove a photo, only to replace it. Deleting the previous file is best effort, so an old file can remain.
- Other people see a new name on their next load, not live.
- "Keep me signed in" is per device and applies only to a fresh start.
- If Firebase rejects a Google sign-in as an invalid credential, the message is the email/password text "The email address or password is incorrect.", because `authErrorMessage()` in `MainActivity.kt` is shared with email sign-in. Found by reading the code; not seen on a device.

### 10.6 How this was checked (2026-09-26)

- **SQL:** all migrations run in order on a local PostgreSQL 16 with stand-ins for Supabase's roles, `auth.jwt()` and `storage.objects`, then `20260928_profile_updates.sql` twice more without errors. Confirmed: a user renames themselves and the name is saved trimmed ("  Ana Test \n" → "Ana Test"); blank, null and 121-character names are refused ("Enter your name.", "Your name must be 120 characters or fewer."), 120 characters are accepted; photo paths in another user's folder, with `..`, with a lookalike prefix (`uidAX/…`), null, or for a missing object are refused; an own existing path is saved; `role`, `email` and `firebase_uid` do not change and other users are untouched; a direct `update public.users` is still permission denied; a token from another issuer gets "Not authenticated"; `anon` gets permission denied; `get_donor_reservations()` and `get_my_conversations()` show the new name; `supabase/tests/phase4_live_safety_check.sql` passed 21 of 21 on the local data.
- **Kotlin:** all sources compiled with kotlinc 2.2.10 and the Compose and serialization plugins, against the supabase-kt 3.5.0 JVM jars and Compose desktop jars. Google's Maven repository is blocked in this session, so `androidx.credentials` 1.6.0, `googleid` 1.2.1, the new Firebase calls (`GoogleAuthProvider`, `EmailAuthProvider`, `reauthenticate`, `updatePassword`, `providerData`, `FirebaseAuthWeakPasswordException`) and the Android graphics and `ExifInterface` APIs were checked only against hand-written stubs of their signatures. No errors; only the three deprecation warnings that were already there. This is not an Android build.
- **AGP build (2026-09-26, the developer's PC):** with `googleid` 1.2.1, `compileDebugKotlin` failed: "Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.4.0, expected version is 2.2.0." `googleid` 1.2.0 (metadata 2.1.0) builds: `assembleDebug` BUILD SUCCESSFUL, with only the three old deprecation warnings. `gradle/libs.versions.toml` now pins 1.2.0.
- **Live migration and device test (2026-09-26):** the developer ran `20260927_activity_prices.sql` and `20260928_profile_updates.sql` in the SQL Editor, installed the build from Android Studio (this branch at `8908548` plus the uncommitted "starts unchecked" change, which also built with AGP), and ran the Phase 1 checks on two phones from a summary of §10.4. Reported result: passed ([results log](#results-log)).
- **Not reported:** the regression list at the end of §10.4 (including the password-reset email, never tested) and the §9.4 test.

---

## 11. Phase 2 demo fixes: listings

**Status (2026-09-26): passed on two phones.** Written on branch `claude/phase2-demo-fixes` (from `master` at `8cc18e3`, which contains PR #4; PR #5). **Built with AGP at `4b6aac6`; migration `20260929_phase2_listing_details.sql` applied to the live project; the §11.4 two-phone test and its regression round passed on 2026-09-26 (§11.6).** Fixes to finished Phase 2 features, found in the developer's audit; not part of Phase 5.

### 11.1 What changed

- **Real donor names** on the Home cards, the details (donor chip and Donor/Seller row), the Donor page, Saved Listings and the Home search ("Search food or donors"). They come from `get_listing_donor_names(uuid[])` (§11.2), looked up by post IDs after the feed loads. If that lookup fails, the listings still load and show "Donor name unavailable". The donor line uses a plain person icon instead of the verified-user icon, since nothing verifies donors.
- **No ratings or distances.** Removed: "★ New" on cards and details, "★ … — sample rating", "This profile uses the selected listing's sample information." and the address on the Donor page; the Home location row ("Del Rosarrio, Balilihan"); the Nearby chip and its line in the filter help; "Within 1.5 km"; the Nearest sort; the Nearby/Popular badge. Sorts are "Newest" (default, by `created_at`) and "Name". "Available near you" is now "Available food".
- **Newest listings.** The feed loads newest first. The "Recommended for you" row is now "Newest listings" (the two newest); its See all opens the normal all-listings page sorted by Newest. The separate Recommended page and "Browse all 10 food listings" are gone. The Home list is keyed by listing ID.
- **Pickup Address and Maps** use the listing's `pickup_location`. "The sample listing has no exact address or map pin yet." is removed. "Search area in Maps" searches the location as entered, adding ", Philippines" when the text doesn't already contain "Philippines"; "No maps app is available on this device." stays. The Buy/Claim checkout's "Pickup area" line uses the same value, so it now shows the listing's location.
- **Donor page:** title "Donor" or "Seller", the name, "Food donor"/"Food seller", button "Message donor"/"Message seller", and the listing's real cover photo (with the same fallbacks as the cards).
- **Create Listing** saves the pickup window, the handover choice (the field is now called "Handover": "Meet at pickup location" / "Collect from provider"), allergens and storage notes (blank = not provided). Removed: the "Smart options" panel (partial claims, maximum per recipient, whole batch) and its check, its lines in Recommendation insights, and "Recipient distances cannot be calculated from this form yet."; the pickup location starts empty. New checks: "The food name must be 160 characters or fewer.", "The pickup location must be 300 characters or fewer.", "Allergens must be 300 characters or fewer.", "Storage notes must be 300 characters or fewer." The published panel shows "Photos: N".
- **Up to 4 photos.** Each is shrunk to at most 1600 px (upright), re-encoded as JPEG off the main thread, and uploaded to `<firebaseUid>/<postId>/image.jpg` (the cover, `image_url`) and `photo-2.jpg`–`photo-4.jpg` (`extra_image_paths`). If an upload or the insert fails, the photos already uploaded are deleted (best effort). A photo that can't be read: "One of the photos could not be read. Please choose another photo." Deleting a listing deletes all its photos (best effort); the delete dialogs say "This permanently removes this listing and its photos."
- **Details:** a swipeable gallery (`HorizontalPager`) with a "1 / 4" counter when there is more than one photo; photos 2–4 get their own 1-hour signed URLs when the details open, and each photo has its own "Image unavailable" fallback. The summary's third column is the expiry, e.g. "Sep 27, 6:00 PM", captioned "Expires"; the last caption is "Status". Rows: Allergens (the saved text or "Not provided — ask the donor"), Pickup Window (e.g. "Sep 27, 3:00 PM – 5:00 PM", or "Not set — ask the donor"; it still opens the demo picker), **Handover** (new; the label or "Not set — ask the donor"; tapping it opens the chat with "Hi! How will the handover for <name> work?"), Pickup Address, Storage Note (the saved text or "Not provided — ask the donor"), Donor/Seller. The allergen guess from the demo picture is gone. Blank allergens never read as allergen-free.
- **Edit listing.** "Edit listing" in the Activity → My Listings dialog and "Edit" in Post → My posts, only for the signed-in user's own, unexpired listings. The full-screen "Edit Listing" page has the donate/sell choice and price, name, category, description, expiration, pickup location, pickup start and end (with "Clear pickup times"), handover, allergens and storage notes, prefilled, and "Quantity, unit and photos can't be changed." Buttons "Save changes" ("Saving…" while it runs) and "Cancel". Checks match the server's: the pickup window may already have started, and both times may be empty, but not just one ("Choose both pickup times, or neither."). Result: "Listing updated." (My Listings and the feed reload), or the server's short error (for example "Pickup must end on or before expiration.", "You can edit only your own listing.", "This listing no longer exists.", "This listing has expired and can no longer be edited."), or "Your listing could not be updated. Check your internet connection and try again." The unused `FoodRepository.updateFoodPost()` is removed.
- **No dropped saves.** `FoodViewModel` runs its requests one at a time: Post Listing, Save changes and Delete wait for a running feed or My Listings load instead of being ignored, so "Posting…" / "Saving…" always ends in a result or an error. A second write before the first has finished (a double tap) is ignored and its button is disabled.
- **Saved Listings.** The bookmark icons on the cards and the details save or remove at once and show the saved state; they are hidden on your own listings. If the server refuses, the icon switches back and "Your bookmark could not be saved. Check your internet connection and try again." appears. Bookmarks load once when Home starts (per profile, like the alerts). Profile menu: "Saved Listings" / "Listings you bookmarked". The page: "Listings you bookmarked, newest first."; each card has the cover, name, donor name, "quantity • price", "View listing" (opens the details by the listing's ID, from the row just loaded) and "Remove"; a listing that is gone, fully reserved or expired shows "No longer available" and only "Remove". Empty: "You have no saved listings yet. Tap the bookmark on a listing to save it here."; loading: "Loading your saved listings…"; error: "Your saved listings could not be loaded. Check your internet connection and try again." The sample items, "Restore examples", the "Example saved…" texts and the sample food page are gone.
- **Expired.** My Listings and Post → My posts show "Expired" once `expiration_date` has passed, with "Expired on" instead of "Expires". "Active Listings" and its filter count only unexpired, available listings.
- **Unchanged (later tasks or not in scope):** Buy Now / Claim Now and the checkout, the pickup-time picker, the "Reserved" dialog (§9.6), Delete erasing Purchases and Claims (§9.6), the Activity location picker and footer card, the Profile stats, Reviews, the Identity Verified badge, Report, notification settings and the Profile bell, the Home filter help (minus the Nearby line), the "Help reduce food waste" card.

### 11.2 Database (`supabase/migrations/20260929_phase2_listing_details.sql`)

Run it by hand in the SQL Editor after `20260928_profile_updates.sql` and **before** installing a build of this branch: Create Listing sends the new columns and fails without them. Older builds keep working after it (supabase-kt ignores unknown columns). Safe to run again. Check: `select column_name from information_schema.columns where table_schema = 'public' and table_name = 'food_posts' and column_name in ('pickup_start','pickup_end','handover','allergens','storage_notes','extra_image_paths');` returns 6 rows, and `select proname from pg_proc where proname in ('update_my_food_post','get_listing_donor_names');` returns 2.

- **New `food_posts` columns**, nullable or with a default: `pickup_start`, `pickup_end` (both or neither; end after start; end ≤ `expiration_date`), `handover` (`MEET_AT_PICKUP_LOCATION` or `COLLECT_FROM_PROVIDER`), `allergens` and `storage_notes` (≤ 300 characters), `extra_image_paths text[] not null default '{}'` (≤ 3 paths, no nulls).
- **Insert policy** recreated with the same rules plus: `image_url` and every extra path must be inside `<token sub>/<post id>/` and contain no `..`.
- **Direct updates closed:** the Phase 2 policy "Users may update their own food posts" is dropped and UPDATE on `food_posts` is revoked from `authenticated`. Listings now change only by insert, the reservation functions and `update_my_food_post`. **Do not run `20260923_phase2_food_posts.sql` again:** it would restore both.
- **`update_my_food_post(p_food_post_id, p_name, p_description, p_category, p_price, p_pickup_location, p_expiration_date, p_pickup_start, p_pickup_end, p_handover, p_allergens, p_storage_notes)`**: SECURITY DEFINER, `search_path = public`, caller from the token only. It locks the row, refuses a missing listing ("This listing no longer exists."), someone else's ("You can edit only your own listing.") and an expired one, checks the same limits as the table and Create Listing, and changes only those fields (never quantity, unit, status, donor, photos or `created_at`; `updated_at` comes from the existing trigger). Existing reservations keep their `unit_price`.
- **`get_listing_donor_names(p_food_post_ids uuid[])`**: SECURITY DEFINER; returns `(food_post_id, donor_name)` only, and only for listings the caller may see under the `food_posts` SELECT policy (available and unexpired, their own, or one they reserved). `users` RLS is not widened.
- **`saved_listings`** (`user_id`, `food_post_id`, `created_at`; primary key (user, post); both foreign keys cascade on delete): RLS select/insert/delete only your own rows; insert only for a listing you can see that is not your own; no UPDATE; revoked from `anon`.
- Both functions are revoked from `public` and `anon` and executable by `authenticated`. `create_reservation`, `cancel_reservation`, `claim_reservation`, the Phase 4 triggers and the price trigger are unchanged.

### 11.3 How this was checked (2026-09-26)

- **SQL:** all migrations run in order on a local PostgreSQL 16 with stand-ins for Supabase's roles and `auth.jwt()`, a listing inserted before the new file ("old listing"), then `20260929_phase2_listing_details.sql` run three times without errors. Confirmed with three accounts:
  - the old listing stays readable, with null new fields and an empty photo list;
  - a listing with all new fields and 3 extra photos saves; one-time-only, end-before-start and end-after-expiry windows, a bad handover code, 301-character allergens or storage notes and 4 extra photos are refused;
  - photo paths in another user's folder, for another post ID, with `..` or with a lookalike folder (`uidBX/…`) are refused; a listing without photos saves;
  - a direct UPDATE of `food_posts` is refused for the owner too (`permission denied`);
  - `get_listing_donor_names` returns only the name, for available listings, the caller's own (also expired) and ones they reserved (also fully reserved); nothing for an outsider on a fully reserved or expired listing, a foreign token or a fake ID; `anon` is refused;
  - `update_my_food_post`: the owner's edit changes name, price, window, handover and notes (blank allergens become null), trims the name, sets `updated_at`, and leaves quantity, unit, status, donor and photos unchanged; a window that already started is accepted; all 15 validation errors appear with their texts; a missing listing, another user's listing, an expired listing, `anon` and a foreign token are refused; the old listing is edited with both times empty;
  - after the price changed from 45.10 to 50.00, the existing reservation kept `unit_price` 45.10; reserve, cancel and claim still work and create their Phase 4 alerts (`RESERVATION_CREATED`, `RESERVATION_CANCELLED`, `RESERVATION_CLAIMED`, `MESSAGE`);
  - `saved_listings`: saving works; a duplicate with `on conflict do nothing` is ignored; saving for another user, an invisible (expired) listing, an outsider's view of a fully reserved listing and your own listing are refused; UPDATE is refused; each user sees and deletes only their own rows; rows cascade when the listing is deleted;
  - `supabase/tests/phase4_live_safety_check.sql` on this data: 21 passed, 0 failed, 0 skipped (with one saved alert-settings row added, so check 11 does not skip).
- **Kotlin:** all sources compiled with kotlinc 2.2.10 and the Compose and serialization plugins against the supabase-kt 3.5.0 JVM jars and the Compose desktop jars (foundation 1.9.1 has `HorizontalPager`), with small stand-ins for Android and Firebase APIs: no errors, only the three old deprecation warnings. A JVM check: `NewFoodPost` encodes `"pickup_start":null, … "extra_image_paths":[]` (every new field is sent); a `food_posts` row with `extra_image_paths` and `handover` decodes; supabase-kt's default `Json` ignores unknown keys and keeps nulls. **This is not an Android build**; the AGP build is the real check.
- **No new libraries.**

### 11.4 Two-phone test (A on phone 1, B on phone 2)

**Preconditions:** run `20260929_phase2_listing_details.sql` and its check (§11.2). Build `claude/phase2-demo-fixes` and install it on both phones. A has at least one **unexpired listing posted before the migration** (the "old listing") that stays unexpired for the whole test; B has not reserved it. Have 4 photos on phone 1. Write down the old listing's name. The steps are in the order they were run on 2026-09-26: `EXPIRY TEST` expires 20 minutes after it is posted, so its edit (step 5) and B's bookmark (step 6) come first.

**A, part 1 (phone 1)**
1. [New] Home: no location row under the greeting. Chips: All, Donation, For Sale, Expiring Soon (no Nearby). The filter button's text has no "Nearby" line. The row is "Newest listings", with no Nearby/Popular badge on its cards. The list title is "Available food" and "Sort: Newest"; the sort menu has Newest and Name. No "★" anywhere on the cards. Your own listings have no bookmark icon.
2. [New] Post: the pickup location field is empty; there is no "Smart options" panel; the handover field is called "Handover". "I want to sell", name `GALLERY TEST`, **4 photos**, any category, quantity `6`, unit `packs`, price `20.00`, expiration tomorrow 6:00 PM, pickup location `Balilihan Town Plaza, Bohol`, pickup starts tomorrow 3:00 PM, ends tomorrow 5:00 PM, Handover "Collect from provider", allergens `Contains peanuts`, storage notes `Keep chilled` → Post Listing. "Listing published" shows "Photos: 4".
3. [New] Supabase dashboard → Storage → `food-images` → A's UID folder → the new post's folder has `image.jpg`, `photo-2.jpg`, `photo-3.jpg`, `photo-4.jpg`. Write down the folder name (the post ID).
4. [New] Post: `EXPIRY TEST`, "I want to donate", 1 photo, quantity `1`, any unit, pickup location `Plaza`, expiration **20 minutes from now**, pickup starts 4 minutes from now, ends 15 minutes from now → Post Listing.
5. [New] After `EXPIRY TEST`'s pickup has started (4 minutes after posting) and before it expires: Post → My posts → `EXPIRY TEST` → "Edit". The Edit Listing page shows its values and "Quantity, unit and photos can't be changed." Change the description → Save changes → "Listing updated.". Then go straight to phone 2.

**B, part 1 (phone 2)**
6. [New] First, before `EXPIRY TEST` expires: bookmark it on its Home card; the icon fills.
7. [New] Home: `GALLERY TEST`'s card shows A's real name next to a person icon (not "Food donor"), and "Available  •  Balilihan Town Plaza, Bohol". Typing A's first name in the search box shows A's listings.
8. [New] Open `GALLERY TEST`: the header shows "1 / 4"; swipe through the 4 photos. The donor chip shows A's name; there is no "⭐" chip. The four columns: "6 packs" / Available, "₱20.00" / For sale, "<tomorrow>, 6:00 PM" / Expires, "Available" / Status. Rows: Allergens "Contains peanuts"; Pickup Window "<tomorrow>, 3:00 PM – 5:00 PM"; Handover "Collect from provider"; Pickup Address "Balilihan Town Plaza, Bohol"; Storage Note "Keep chilled"; Seller "<A's name>".
9. [New] Tap Pickup Address: the page shows "Balilihan Town Plaza, Bohol" and no "sample listing" sentence. "Search area in Maps" opens Maps searching "Balilihan Town Plaza, Bohol, Philippines". Back. Tap Seller: the title is "Seller", with A's name, "Food seller", no address, no rating, no "sample information" text, the button "Message seller", and `GALLERY TEST`'s real cover under "Listing from this provider". Close.
10. [New] Open A's old listing: one photo and no counter; Allergens "Not provided — ask the donor", Pickup Window "Not set — ask the donor", Handover "Not set — ask the donor", Storage Note "Not provided — ask the donor". Tap Handover: the chat with A opens with "Hi! How will the handover for <name> work?" ready to send. Close the chat without sending.
11. [New] Bookmark `GALLERY TEST` on its Home card: the icon fills. Open it: the bookmark at the top right is filled too. Also bookmark the old listing.
12. [New] Profile: "Saved Listings" / "Listings you bookmarked". Open it: "Listings you bookmarked, newest first.", with the three listings, newest saved first; each shows its cover, name, A's name, and quantity • price (e.g. "6 packs • ₱20.00"). No example items, no "Restore examples". If `EXPIRY TEST` has expired by now, it shows only "No longer available" and "Remove"; leave it. Tap "View listing" on `GALLERY TEST`: its details open.
13. [New] Log out and sign in as B again. On Home, the bookmark icons of `GALLERY TEST` and the old listing are already filled, without opening Saved Listings.
14. [Regression] Reserve 2 of `GALLERY TEST` (the "Reserved" dialog does not appear, §9.6). Activity → My Reservations: "2 packs • ₱40.00".

**A, part 2 (phone 1)**
15. [New] Activity → My Listings → `GALLERY TEST` → "Edit listing". Set the pickup end after the expiration (tomorrow 7:00 PM) → Save changes → "Pickup must end on or before expiration.". Tap "Clear pickup times", then set only Pickup starts → "Choose both pickup times, or neither.".
16. [New] Now set the name `GALLERY TEST 2`, price `25.00`, pickup starts tomorrow 3:00 PM, ends tomorrow 5:30 PM → Save changes → an "Edit Listing" dialog: "Listing updated.". My Listings shows `GALLERY TEST 2`.
17. [New] Post → My posts → the old listing → "Edit": Pickup starts and Pickup ends show their "e.g." examples (empty), Handover shows its example. Change only the description → Save changes → "Listing updated.".
18. [Regression] Activity → My Donations: B's reservation, now named `GALLERY TEST 2`, still shows "2 packs • ₱40.00"; its details say "Price: ₱20.00 per unit" and "Collect ₱40.00 at pickup."
19. [New] After `EXPIRY TEST`'s expiration: Activity → My Listings shows it as "Expired"; its dialog shows "Expired on: …" and no "Edit listing" button. The Active Listings number does not count it, and tapping Active Listings does not list it. Post → My posts: "Status: Expired" and no Edit.

**B, part 2 (phone 2)**
20. [New] Reload Home (Post → Back): the card shows `GALLERY TEST 2` and "4 packs  •  ₱25.00"; its Pickup Window reads "<tomorrow>, 3:00 PM – 5:30 PM". Activity → My Reservations still shows "2 packs • ₱40.00".
21. [New] Saved Listings: `EXPIRY TEST`'s card shows only "No longer available" and "Remove", with no photo or name (B can no longer see an expired listing it did not reserve). Tap Remove: it disappears.
22. [New] Reserve the remaining 4 of `GALLERY TEST 2` ("Pay ₱100.00 at pickup"). It leaves Home, and My Reservations shows "2 packs • ₱40.00" and "4 packs • ₱100.00" (the new price). Saved Listings: its card shows its cover, name, A's name and "0 packs • ₱25.00", then "No longer available" and only "Remove". Leave it saved.

**A, part 3 (phone 1)**
23. [New] Activity → My Listings → `GALLERY TEST 2` → Delete → "This permanently removes this listing and its photos." → Delete. This also deletes B's two reservations on it (§9.6). Supabase Storage → `food-images` → A's UID folder: the folder from step 3 is gone.

**B, part 3 (phone 2)**
24. [New] Saved Listings: `GALLERY TEST 2` is gone; the old listing is still there. Remove it: the list shows "You have no saved listings yet. Tap the bookmark on a listing to save it here."
25. [New] Home: the old listing's bookmark icon is empty again. Activity → My Reservations no longer shows the two `GALLERY TEST 2` reservations.

**Regression (Phases 1–4 and §9), A (phone 1)**
26. Post `REGRESSION TEST`: "I want to donate", 1 photo, quantity `3`, unit `packs`, pickup location `Plaza`, expiration 2 hours from now, pickup from 30 minutes to 1 hour 30 minutes from now → "Listing published".
27. Profile → Account Details: add " Test" to your display name → "Save account details" → "Saved to your account."

**Regression, B (phone 2)**
28. Reload Home: `REGRESSION TEST` shows A's new name, "3 packs  •  Free" and "Expires soon  •  Plaza". Donation shows it and no priced listings; For Sale does not show it; Expiring Soon shows it; All shows everything.
29. Reserve 1: My Reservations shows "1 packs • Free". Cancel it: "Reservation cancelled. The quantity is available again."
30. Reserve 1 again.
31. In its details tap Handover: the chat opens with "Hi! How will the handover for REGRESSION TEST work?" → Send. Leave the chat open.

**Regression, A (phone 1)**
32. Alerts: "New message from <B's name>" ("REGRESSION TEST: Hi! How will the handover for REGRESSION TEST work?"), "New reservation" twice ("<B's name> reserved 1 packs of REGRESSION TEST."), and "Reservation cancelled" ("<B's name> cancelled their reservation of REGRESSION TEST. The quantity is available again.").
33. Messages → the chat with B → reply `Meet at the plaza gate.`
34. Activity → My Donations → B's reservation ("1 packs • Free") → Mark claimed → "Reservation marked as claimed."
35. Profile → Account Details: remove " Test" → "Saved to your account."
36. Activity → My Listings → `EXPIRY TEST` → Delete → Delete: it disappears.

**Regression, B (phone 2)**
37. The open chat shows "Meet at the plaza gate." without being reopened.
38. Alerts: "Reservation claimed" ("The donor marked your reservation of REGRESSION TEST as claimed.") and "New message from <A's name>" ("REGRESSION TEST: Meet at the plaza gate.").
39. Activity → Claims: `REGRESSION TEST`, "1 packs • Free"; My Reservations no longer lists it. Reload Home: A's name no longer ends with "Test".

Not covered by this round (use §4, §9.4 and §10.4 when those areas change): email sign-up, "Keep me signed in", Google sign-in, the profile photo, the password change, a priced claim in Purchases, and the password-reset email.

### 11.5 Known limits

- Recipients are not told when a listing they reserved is edited, and nothing updates live: others see an edit after their next reload.
- Photos can't be edited; to change them, post a new listing. Expired listings can't be edited either.
- Saved Listings doesn't update live; it reloads when opened, and the bookmark icons load once per session.
- Listings made before the migration have no pickup window or handover until they are edited.
- Nothing on the server sets the `EXPIRED` status; the app works it out from `expiration_date`.
- The Maps button is a text search for the pickup location, not a map pin. Maps is Phase 5.
- Deleting photos (after a failed post, or with a listing) is best effort; a failure can leave files in Storage.
- The pickup-time picker in the details (the "Pickup Window" row) is still the demo picker and does not reserve anything.
- `get_listing_donor_names` repeats the `food_posts` SELECT rule in SQL; if that policy changes, the function must change with it. (The `saved_listings` insert policy reads `food_posts` under its RLS, so it follows automatically.)
- **Blank Saved Listings card.** A saved listing the user can no longer see (expired, and not reserved by them) is hidden by the `food_posts` SELECT policy, so its card shows only "No longer available" and "Remove", without its name or photo. Seen in the §11.4 test (step 21). Small follow-up.
- **Dates follow the phone's language.** The details times (`listingTimeFormat` and `listingClockFormat` in `MainActivity.kt`), the time buttons of Create and Edit Listing (`postTime()` in `PostScreen.kt`), Activity, Alerts and Chat format dates with `Locale.getDefault()`, while "Member since" uses English; on a phone set to another language the month names and AM/PM change. Found in review; not tested.
- **Long error text on Post Listing and Delete.** `FoodViewModel.errorText()` shows the whole exception message, which for a Supabase error includes the request URL (headers masked); Edit uses the short `supabaseErrorText()`. Found in review; not tested.

### 11.6 Results (2026-09-26)

- **Build:** `assembleDebug` of `4b6aac6` on the developer's PC: BUILD SUCCESSFUL in 1m 34s, only the three old deprecation warnings. Installed on both phones after the migration.
- **Migration:** the developer ran `20260929_phase2_listing_details.sql` in the SQL Editor before installing, and the test used every new column, both new functions and `saved_listings`. After the test, a read-only check query on the live project returned true for all 8 of its checks: the 6 new columns, both functions and `saved_listings` exist; `authenticated` has no UPDATE privilege on `food_posts` and no UPDATE policy is left on it, while INSERT is still granted; `authenticated` can execute both functions and `anon` cannot.
- **Two-phone test:** §11.4 steps 1–25 passed, given in parts in the order §11.4 now shows. The first draft had `EXPIRY TEST` expire 15 minutes after posting, with its edit in A's part 2; it was changed so that the edit and B's bookmark happen before it expires.
- **Regression round:** §11.4 steps 26–39 passed. What it did not cover is listed after step 39.
- **Expected, not new:** the "Reserved" dialog never appeared after Reserve, and deleting `GALLERY TEST 2` also deleted B's two reservations on it (both §9.6). A reservation kept its price after the listing's price changed, and a new one used the new price (steps 18 and 22).
- **New small issue:** the blank Saved Listings card (§11.5).
- **Not checked on the live project:** the other negative checks of §11.3 (the photo-path rules, the saved-listing refusals, who can see donor names, the refusals of `update_my_food_post`) ran only on the local PostgreSQL copy. The missing UPDATE privilege and policy were confirmed live (above), but no direct UPDATE was attempted there.

