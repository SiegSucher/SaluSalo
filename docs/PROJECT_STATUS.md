# Salu-Salo — Project Status

_Last updated: 2026-09-28 (Phase 4 signed off, §8; Purchases, Claims and listing prices merged into `master` through PR #3, migrated, and passed on two phones except the "Reserved" dialog, §9; Phase 1 demo fixes — Google sign-in, Keep me signed in, profile name and photo, password change — merged through PR #3, migrated, and passed on two phones, §10; Phase 2 demo fixes — real donor names, listing details, up to 4 photos, editing, saved listings, expiry — built on branch `claude/phase2-demo-fixes` (PR #5), migrated, and passed on two phones with a regression round, §11; PR #5 merged into `master` as `aa3e823`; Phase 3 demo fixes — Reserve review page and receipt, removing reserved listings, real Activity and Profile records — built on branch `claude/phase3-demo-fixes` at `24fe9f2`, migrated, passed on two phones on 2026-09-27, and merged into `master` through PR #7 on 2026-09-27 (merge commit `c184d9b`), §12; Phase 4 demo fixes — Profile bell and Settings, notification preferences, a real notification sound, a real response rate, a live Messages inbox and chat badge — built on branch `claude/phase4-demo-fixes` at `3288d02`, migrated, passed a short 13-step test on two phones on 2026-09-27, and merged into `master` through PR #8 on 2026-09-27 (merge commit `39b57b5`), §13; Phase 5 part 1 — ratings and reviews, reports, an Email verified badge — built on branch `claude/phase5-reviews-reports-v6ahqn` at `3bd038c`, migrated, passed on two phones on 2026-09-28, and merged into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`), §14; Phase 5 part 2 — Maps: a pickup pin on an OpenStreetMap map, distances, Nearby and Nearest, Directions — built on branch `claude/phase5-maps` at `47ed894`, migrated, passed on two phones on 2026-09-28, and merged into `master` through PR #11 on 2026-09-28 (merge commit `255b0f8`), §15; Profile redesign and settings (Mika's PR #10) — Edit Profile with a bio, a Settings hub, food preferences — migrated, passed a 6-step test on two phones on 2026-09-28, and merged into `master` through PR #10 on 2026-09-28 (merge commit `49ac319`), §16; fixes, clean-up and optimization after the second demo audit — a photo tile instead of the stock photo, "Check your listing", Start now, required allergens, Mika's list, batch photo signing, first-start welcome pages, pull to refresh — in code on branch `claude/cleanup-after-audit`, not built, not tested, §17). Based on the code at commit `d4f65fe` on `master`, plus the image changes described in §3.2 and §3.3 and the load-failure fixes in §3.4, which were committed together with this document on 2026-09-25, and the known-issue fixes in §3.5 (commit `85c9263`). Manual test results reported on 2026-09-25 are in the [results log](#results-log)._

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
| Phase 3 demo fixes: reservations, Activity, Profile (fixes to finished Phase 3 features; not part of Phase 5) | Reserve review page and receipt, pickup questions and notes through the chat, deleting reserved listings by removing them, real Activity records, real Profile claims and donations, Phase 2 leftovers (§12) | **Passed.** Built with AGP on branch `claude/phase3-demo-fixes` at `24fe9f2`. Migration `20260930_phase3_demo_fixes.sql` applied to the live project on 2026-09-26; its read-only check passed 13 of 13 and the live safety check 14 of 14. The two-phone test (§12.4, 32 steps) passed on 2026-09-27 ([results log](#results-log), §12.6). Merged into `master` through PR #7 on 2026-09-27 (merge commit `c184d9b`). |
| Phase 4 demo fixes: messages, alerts, Profile (fixes to finished Phase 4 features; not part of Phase 5) | The Profile bell opens Alerts and the gear opens Settings, the unused Food recommendations and Pickup reminders switches and Compact preview removed, a real notification sound, a real response rate, a live Messages inbox and a chat badge that counts unread messages (§13) | **Passed.** Written on branch `claude/phase4-demo-fixes` (from `master` at `c184d9b`) on 2026-09-27 and built with AGP at `3288d02`. Migration `20261001_phase4_demo_fixes.sql` applied to the live project on 2026-09-27; its read-only check passed 9 of 9. A short two-phone test (§13.4, 13 steps) passed the same day ([results log](#results-log), §13.6); the checks it left out are listed in §13.6. Merged into `master` through PR #8 on 2026-09-27 (merge commit `39b57b5`). |
| Phase 5: Maps, Reviews, Reports | Split by the developer into part 1 (ratings and reviews, reports, and an "Email verified" badge moved here from Phase 6; §14) and part 2 (Maps) | **Part 1: passed, and merged into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`).** Written on branch `claude/phase5-reviews-reports-v6ahqn` (from `master` at `39b57b5`) on 2026-09-27 and built with AGP at `3bd038c`. Migration `20261002_phase5_reviews_reports.sql` applied to the live project on 2026-09-27; its read-only check passed 18 of 18 and the live safety check 27 of 27. The two-phone test (§14.4, 15 steps) passed on 2026-09-28 ([results log](#results-log), §14.6). **Part 2 (Maps): passed, and merged into `master` through PR #11 on 2026-09-28 (merge commit `255b0f8`).** Written on branch `claude/phase5-maps` (from `master` at `711cc5e`) on 2026-09-28 and built with AGP at `47ed894`. Migration `20261003_phase5_maps.sql` applied to the live project on 2026-09-28; its read-only check passed 11 of 11 and the live safety check 22 of 22. The two-phone test (§15.4, 12 steps) passed on 2026-09-28 ([results log](#results-log), §15.6). |
| Fixes after the second demo audit (not part of the six phases) | A neutral photo tile, "Check your listing", "My posts" times, filter help, Read more, Learn more, welcome texts; Mika's Start now, required allergens, profile circle, full-screen photos, summary columns and keyboard fix; dead code, unused images and icons; one signing request per list, reservations by ID, WebP; welcome pages only on the first start and pull to refresh (§17) | **In code; not built, not tested.** Written on branch `claude/cleanup-after-audit` (from `master` at `49ac319`) on 2026-09-28. No SQL. Only a kotlinc type check and JVM unit tests were run (§17.3); the two-phone test is §17.4. |
| Phase 6: Admin and Final Release | ID verification and the admin screen (the Email verified badge moved to Phase 5 part 1) | **Not started.** |

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
- **Phase 3 demo fixes (branch `claude/phase3-demo-fixes` at `24fe9f2`):** `gradlew.bat :app:assembleDebug --offline` BUILD SUCCESSFUL in 3m 23s on 2026-09-26, with only the three old deprecation warnings (`FormatListBulleted`, `ArrowBack`, `Logout`). No tests or lint were run. The developer then installed it from Android Studio on both phones and ran the §12.4 test ([results log](#results-log)).
- **Phase 4 demo fixes (branch `claude/phase4-demo-fixes` at `3288d02`, §13):** `gradlew.bat :app:assembleDebug --offline` BUILD SUCCESSFUL in 43s on 2026-09-27, with only old deprecation warnings (`ArrowBack`, `Logout`). No tests or lint were run. Before that, only a kotlinc 2.2.10 type check against desktop Compose jars and hand-written Android stubs had been run (§13.3). The developer then installed it from Android Studio on both phones and ran the §13.4 test ([results log](#results-log)).
- **Phase 5 part 1 (branch `claude/phase5-reviews-reports-v6ahqn` at `3bd038c`, §14):** `gradlew.bat :app:assembleDebug --offline` BUILD SUCCESSFUL in 57s on 2026-09-27, with only the three old deprecation warnings (`FormatListBulleted`, `ArrowBack`, `Logout`). No tests or lint were run. Before that, only a kotlinc 2.2.10 type check against desktop Compose jars and hand-written Android stubs had been run (§14.3). The developer then installed it from Android Studio on both phones and ran the §14.4 test ([results log](#results-log)).
- **Phase 5 part 2 (branch `claude/phase5-maps` at `47ed894`, §15):** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL in 3m 43s on 2026-09-28, run online once to download osmdroid 6.1.20, with only the three old deprecation warnings (`FormatListBulleted`, `ArrowBack`, `Logout`). `testDebugUnitTest`: `GeoTest` 11 of 11 and `ExampleUnitTest` passed. No lint was run. The developer then installed it from Android Studio on both phones and ran the §15.4 test ([results log](#results-log)).
- **Mika's PR #10 (branch `mika/navigation-activity-settings` at `b2b04ca`, §16):** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` BUILD SUCCESSFUL in 36s on 2026-09-28, with only icon deprecation warnings; `GeoTest` 11 of 11. The developer then installed it from Android Studio on both phones and ran the §16 test ([results log](#results-log)).
- **Automated tests:** the Android Studio templates `ExampleUnitTest` and `ExampleInstrumentedTest`, and since §15 `GeoTest` (JVM tests of the distances, the distance labels, pin rounding and the pin fields' JSON, run by `testDebugUnitTest`).

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
7. **Preferences:** A → Profile → Notifications (Profile → Preferences → Notifications before §13) → turns off "New messages" → Save ("Preferences saved to your account."). B sends a message: it arrives in A's chat, but A gets no new MESSAGE alert. Turn it back on and Save, leave the page and reopen it: the switch is still on (§8.7 bug 1). While the page loads, it shows "Loading your alert settings…" rather than switches (§8.7 bug 2). Repeat both checks in Alerts → settings icon.
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
| 2026-09-26 | live project (SQL Editor): `20260930_phase3_demo_fixes.sql`, then `supabase/tests/phase3_demo_fixes_check.sql` in the same run | Developer | Passed | Phase 3 demo fix migration applied; the read-only check returned 13 of 13 PASS: exactly one check constraint each on `food_posts.status` (allows `REMOVED`) and `notifications.type` (allows `RESERVATION_REMOVED`), `reservations.cancel_reason` with its check, no DELETE or UPDATE policy or privilege on `food_posts`, both new functions SECURITY DEFINER and `authenticated`-only, the `REMOVED` checks in `create_reservation`, `update_my_food_post`, `notify_reservation_change` and the `saved_listings` insert policy, `notify_user` and `notify_reservation_change` not executable by clients, no UPDATE on `saved_listings` (§12.6). |
| 2026-09-26 | live project (SQL Editor); script `supabase/tests/phase3_live_safety_check.sql` | Developer | Passed | 14 passed, 0 failed, 0 skipped. Listing checked: `OLD TEST` (account A), outsider: the newest other account. The three sanity checks confirmed both simulated sign-ins and that A's own `remove_my_food_post` would have deleted `OLD TEST` (never reserved; undone at once). Refused as expected: A's direct DELETE ("permission denied for table food_posts"), the outsider's removal ("You can delete only your own listing."), removal and `get_my_saved_listing_names` without a login (permission denied), a token from another Firebase project ("Not authenticated"). The outsider's saved names were their own only. With the listing marked `REMOVED`: A's edit ("This listing was removed and can no longer be edited."), the outsider's reservation ("The donor removed this listing."), new chat ("This listing is no longer available"), bookmark (row-level security) and reading the listing or its donor name (0 rows) were refused. The closing error rolled everything back. |
| 2026-09-27 | `24fe9f2` (branch `claude/phase3-demo-fixes`); migration applied | Developer | Passed | Phase 3 demo fixes (§12): the §12.4 two-phone test, 32 steps, given in parts. Steps 1–15 were reported in a first session and step 16 (stale quantity, B signed in on both phones) and steps 17–32 in a second session, both on 2026-09-27. Every step passed, including: no Buy Now / Claim Now; the review page and the "Reserved" receipt with the total and "Your message was sent to <A>." (the missing "Reserved" dialog of §9.6 is gone); the Pickup Window row opening the chat; the real photo on the Pickup Address page; the stale reserve showing "Not enough food available. Only 0 remains." on the review page; "Listing deleted." for a never-reserved listing; "Listing removed. 2 open reservations were cancelled…" and "…1 open reservation was cancelled…"; one "Listing removed" alert per listing on B's phone, opening the cancelled reservation in My Reservations; Purchases, Claims, the Activity totals and the Profile counts keeping the claimed reservations of removed listings; no "Reservation cancelled" alert for A; the chat about a removed listing still working; the name and photo on an expired saved listing's card; the short Post Listing and reserve errors in flight mode. Observed: "Listing: Removed by you / by the donor" appears in a record's details dialog, not on its card (as coded). Not re-run: email sign-up, Google sign-in, Keep me signed in, the profile photo, the password change, the photo gallery, the Delete, Cancel and Mark claimed error texts, dates on a phone set to another language, and the password-reset email (never tested). |
| 2026-09-27 | live project (SQL Editor): `20261001_phase4_demo_fixes.sql`, then `supabase/tests/phase4_demo_fixes_check.sql` in the same run | Developer | Passed | Phase 4 demo fix migration applied; the read-only check returned 9 of 9 PASS: `get_my_response_rate()` with its result columns, SECURITY INVOKER, STABLE and `search_path=public`, executable by `authenticated` only; the two helpers it calls executable by `authenticated`; no INSERT, UPDATE or DELETE for clients on `conversations`, `messages` and `notifications`, which have SELECT policies only; `notify_user` not executable by clients; `messages` and `notifications` in `supabase_realtime`; no UPDATE on `users` and no UPDATE or DELETE on `food_posts` for clients (§13.6). |
| 2026-09-27 | `3288d02` (branch `claude/phase4-demo-fixes`); migration applied | Developer | Passed | Phase 4 demo fixes (§13): the short §13.4 two-phone test, 13 steps, sent all at once. The developer asked for fewer steps than the 30-step draft, and reported one result ("test passed"). Covered: a new free listing `P4 FREE`; the Profile header and caption texts; Response Rate as a real value, and its page; the gear opening Settings; Notifications with only "New messages", "Reservation updates" and "Notification sound". Also covered, the bell and badges: the Profile bell opening Alerts, its dot gone after "Mark shown as read" and back after new alerts; no Refresh button in Messages. Live, without touching the phone: with Messages open, B's new chat appearing at the top and an older chat moving up, both with an unread count of 1, and the notification sound playing. The chat badge counted the unread messages and went back down once the chats were opened. No sound for a message in the open chat. Both Response Rate numbers were 1 higher after B started a chat and A replied. "Notification sound" turned off stayed off after signing out and in again; the next message then made no sound, but the badge still went up live. In the background: no sound. After reopening from Recents, the badge and the inbox included the message sent meanwhile: the note of a reservation B made through the review page, with the "Reserved" receipt. A also had the "New reservation" alert. Not covered: see §13.6. |
| 2026-09-27 | live project (SQL Editor): `20261002_phase5_reviews_reports.sql`, then `supabase/tests/phase5_reviews_reports_check.sql` in the same run | Developer | Passed | Phase 5 part 1 migration applied; the read-only check returned 18 of 18 PASS (§14.6). |
| 2026-09-27 | live project (SQL Editor); script `supabase/tests/phase5_live_safety_check.sql` | Developer | Passed | 27 passed, 0 failed, 0 skipped. Reservation checked: `P3 FREE` (claimed), with the two test accounts as recipient and donor and the newest other account as the outsider. The sanity checks confirmed the simulated sign-ins, a review and its change (still one row), and a listing report. Refused as expected: reviews by the outsider and by the donor, ratings 0 and 6, a 501-character comment, a RESERVED or CANCELLED reservation, no login and another project's token; reading or writing `reviews` and `reports` directly; a second report of the same listing, reporting your own listing or yourself, a chat you are not in, a wrong or unknown reason, "Other" without details, 1001 characters of details and an 11th report in 24 hours. After the listing was marked `REMOVED`, the outsider got no donor details or reviews and could not report it, while the recipient still got them. `sync_my_email_verified` set only the caller's own value from the token (true, then false) and changed nothing else; a direct UPDATE of `users` was refused; a new profile inserted as verified was stored unverified; `get_listing_donor_names` still worked. The closing error rolled everything back. |
| 2026-09-28 | `3bd038c` (branch `claude/phase5-reviews-reports-v6ahqn`); migration applied | Developer | Passed | Phase 5 part 1 (§14): the §14.4 two-phone test, 15 steps, sent in two parts and reported as passed (steps 1–9 at 00:10 +08:00, then steps 10–15). Covered: A's grey "Email not verified" chip and the real Verification page (not confirmed yet, the verification email sent, then confirmed after its link, with the chip turning green); B's Google account showing "Email verified"; no demo verification or rating texts left; `P5 FREE` posted and reserved twice through the review page; no Rate button on a Reserved record; "Report this listing" with its validation texts, then "You already reported this."; the alerts and Mark claimed; A reporting B from the chat ("Didn't show up"); B rating A from the "Reservation claimed" alert (4 stars, then changed to 5), with "★ 4.0 (1)" and then "★ 5.0 (1)" on the card, the details and the Donor row after the feed reloads, and the Donor page's badge, rating and review; B reporting A from the chat, then a refused Donor-page report; A's Profile rating, panel and Ratings & Reviews page; A's own card after signing out and in, with no Report buttons on A's own listing; the reports query showing the three reports. During the test A's account was deleted in the Firebase console and re-created, and its profile was re-linked by hand (§14.6). Not covered: the "Left out" list in §14.4. |
| 2026-09-28 | live project (SQL Editor): `20261003_phase5_maps.sql`, then `supabase/tests/phase5_maps_check.sql` in the same run | Developer | Passed | Phase 5 part 2 migration applied; the read-only check returned 11 of 11 PASS (§15.6). |
| 2026-09-28 | live project (SQL Editor); script `supabase/tests/phase5_maps_live_safety_check.sql` | Developer | Passed | 22 passed, 0 failed, 0 skipped. The donor of the newest listing and the newest other account (the two test accounts) were the donor and the outsider. The sanity checks confirmed the simulated sign-ins, a listing posted through RLS with a pin sent with 7 decimals and stored as 9.647812, 123.854013, a listing without a pin, moving a pin together with a name change in one call, and adding a pin to a listing without one. Refused as expected: on insert, a half pin, latitude 91 and longitude 181 (the check constraint); on edit, one coordinate, 90.5, −180.5, NaN and a blank name sent with a new pin, each leaving the pin unchanged; the outsider, an expired listing, a `REMOVED` listing, a missing listing, no login and another project's token; a direct UPDATE by the donor. An edit with both pin arguments NULL and an edit through the old `update_my_food_post` both kept the pin. The outsider saw the pin while the listing was available and nothing once it had expired, while the donor still did. The closing error rolled everything back. |
| 2026-09-28 | `47ed894` (branch `claude/phase5-maps`); migration applied | Developer | Passed | Phase 5 part 2 (§15): the §15.4 two-phone test, 12 steps, sent in two parts (steps 1–7, then 8–12) and reported as passed. Covered: the pin picker on Post (the note, the map starting at Tagbilaran with the attribution, "Place the pickup pin on the map.", placing the pin by tapping, and "Use my location" with Android's prompt and Precise); B's Nearby with the prompt, "Food within 5 km" and the filter help; distances on the cards, Nearest (listings without a pin last) and Name; the details' distance line; the Pickup location page's map, attribution link, Directions (Google Maps), new pickup instructions and "Ask for exact location"; a listing without a pin (no distance or map, "Search area in Maps"); Reserve through the review page and "Directions" in My Reservations; A's alerts and chat reply; moving a pin on Edit, and the "no pickup pin yet" text on an older listing; B seeing the moved pin after a reload; location access removed on B (no distances, the Nearby and Nearest line, the map and Directions still working); location turned off on A (the "Use my location" and Nearby texts, then back on); Mark claimed, with no Directions on the Claims record. Not covered: the "Left out" list in §15.4. |
| 2026-09-28 | live project (SQL Editor): `20261004_profile_bio.sql`, then `supabase/tests/profile_bio_check.sql` in the same run | Developer | Passed | Profile bio migration (Mika's PR #10) applied; the read-only check returned 6 of 6 PASS (§16.3). |
| 2026-09-28 | `b2b04ca` (branch `mika/navigation-activity-settings`); migration applied | Developer | Passed | Mika's Profile redesign and settings (§16): a 6-step two-phone test, reported as passed. Covered: A's new Profile layout; Edit Profile saving a new name and a bio; the Settings hub; Food preferences turning Home's row into "For your preferences" and back; the new "About distance" note; Alerts in the bottom bar on Activity; Nearby distances; B seeing A's new name on the cards and the Donor row; B's new Profile, Log out and signing in again. Not covered: the list under "Not tested" in §16.3. |
| 2026-09-28 | `49ac319` (`master`) | Developer | Merged | Mika's Profile redesign and settings (§16) merged into `master` through PR #10 on 2026-09-28 (merge commit `49ac319`). |

- **Build:** the test phone's app code was deployed on 2026-09-25 at 10:37 (+08:00), after the last source change (10:04), so it matches the working tree above. That same code was committed on 2026-09-25, with no source changes after 10:04. The build on any second device was not checked.
- **Phase 4 retest build:** `c7323b8`, built at 21:21 (+08:00) with no local source changes and installed from Android Studio. A's phone showed the new "Loading your alert settings…" text, so it ran this build; which build the second phone ran was not checked.
- **Phase 1 demo fix test build:** installed from Android Studio on 2026-09-26 from `claude/phase1-demo-fixes` at `8908548` plus the uncommitted "starts unchecked" change, which was committed afterwards on branch `claude/phase1-demo-fixes-results`. Which build each phone ran was not checked.
- **Phase 2 demo fix test build:** `assembleDebug` of `claude/phase2-demo-fixes` at `4b6aac6` on the developer's PC (2026-09-26, BUILD SUCCESSFUL in 1m 34s, only the three old deprecation warnings), installed on both phones after the migration. Both phones showed screens that exist only on this branch (A the 4-photo Create Listing, B the gallery and Saved Listings), so both ran a build of it; the exact build on each phone was not checked.
- **Phase 3 demo fix test build:** `assembleDebug` of `claude/phase3-demo-fixes` at `24fe9f2` on the developer's PC (2026-09-26), installed from Android Studio on both phones right after the migration. Both phones showed screens that exist only on this branch (the two-button details page and the review page and receipt; the new delete dialogs), so both ran a build of it; the exact build on each phone was not checked.
- **Phase 4 demo fix test build:** `assembleDebug` of `claude/phase4-demo-fixes` at `3288d02` on the developer's PC (2026-09-27), installed from Android Studio on both phones. A's phone showed screens that exist only on this branch: the gear opening Settings, Notifications with three switches, and the real Response Rate page. So it ran a build of it; which build B's phone ran was not checked.
- **Phase 5 part 1 test build:** `assembleDebug` of `claude/phase5-reviews-reports-v6ahqn` at `3bd038c` on the developer's PC (2026-09-27), installed from Android Studio on both phones. Both phones showed screens that exist only on this branch (A the Email Verification page, B the Rate page), so both ran a build of it; the exact build on each phone was not checked.
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
| Food repository | `app/src/main/java/com/example/salusalo/data/FoodRepository.kt` | Feed (newest first, with donor names since §11), own posts, create with up to 4 photos, `updateMyFoodPost()`, delete, `withSignedImageUrl()`, `signedExtraPhotoUrls()`; since §17 `withSignedImageUrls()` and `signedUrls()` (one signing request per list, per-photo fallback) |
| Reservation repository | `app/src/main/java/com/example/salusalo/data/ReservationRepository.kt` | RPC calls, `getMyReservations()`, `getReservationsForMyFoodPosts()` |
| Reservation ViewModel | `app/src/main/java/com/example/salusalo/viewmodel/ReservationViewModel.kt` | `myReservations`, `donorReservations`, `message`, `reserve` / `cancel` / `claim` / `refresh` |
| Food ViewModel | `app/src/main/java/com/example/salusalo/viewmodel/FoodViewModel.kt` | `availablePosts`, `myPosts`, `createPost`, `updatePost`, `deletePost`; since §11 writes wait for the running request instead of being dropped |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/UserRepository.kt` | Find-or-create profile; `ProfileException`; since §10 `uploadProfilePhoto`, `profilePhotoUrl`, `changePassword`, `supabaseErrorText`; since §16 `updateMyProfile` (`updateMyName` was removed in §17) |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/GoogleSignIn.kt`, `data/ProfilePhoto.kt` | Google ID token through Credential Manager; profile photo shrinking (§10) |
| Auth / profile | `app/src/main/java/com/example/salusalo/model/UserProfile.kt` | `UserProfile`, `UserRole`, `NewUserProfile` |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/SupabaseClientProvider.kt` | Supabase client using the Firebase ID token |
| Auth / profile | `app/src/main/java/com/example/salusalo/ProfileScreen.kt`, `ProfileSections.kt` | Name, photo, member since and password change are real (§10, passed on 2026-09-26), as are the notification preferences (§8) and saved listings (§11, passed on 2026-09-26); since §13 (passed on two phones on 2026-09-27) the bell opens Alerts, the gear opens Settings and the response rate is real; the rating, reviews, verification and report are still demo. **Since §14 (passed on two phones on 2026-09-28):** the rating, reviews and Email verified badge are real, and Report a concern explains where the Report buttons are |
| Storage paths | `app/src/main/java/com/example/salusalo/data/StorageRepository.kt` | `foodImagePath()`, `profileImagePath()` (`<uid>/profile-<millis>.jpg` since §10) |
| Create listing | `app/src/main/java/com/example/salusalo/PostScreen.kt` | Create Listing form, "My posts", `EditListingDialog` (§11), `PostPhoto` |
| Messaging (Phase 4) | `ChatScreen.kt`, `data/MessageRepository.kt`, `data/RealtimeInserts.kt`, `model/Conversation.kt`, `viewmodel/MessageViewModel.kt` | Chat dialog and Messages inbox; RPC calls; Realtime insert stream. Since §13 (passed on two phones on 2026-09-27): the inbox is live for the whole session (`MessageViewModel.start`), the chat badge is the sum of unread counts, and `getMyResponseRate()` / `ResponseRate` |
| Alerts (Phase 4) | `AlertScreen.kt`, `data/NotificationRepository.kt`, `model/AppNotification.kt`, `viewmodel/NotificationViewModel.kt` | Real alerts, unread counts, preferences; since §13 `newAlerts` (alerts that arrive live) |
| Alert sound (§13) | `AlertSound.kt` | `AlertSoundPlayer` (the phone's default notification sound) and the phone-level `notification_sound` setting in `salusalo_session` |
| Demo screens | `app/src/main/java/com/example/salusalo/FoodActions.kt` | Preview-only pickup and checkout dialogs; its demo chat is now only a fallback |
| Listing photos (§17) | `app/src/main/java/com/example/salusalo/ListingPhotos.kt` | `ListingPhoto` (a signed photo with the plain and "Image unavailable" tiles) and `FullScreenPhotos`. The unused template theme `ui/theme/` was removed in §17; `MainActivity` builds its own `MaterialTheme` |
| Reviews and reports (§14) | `ReviewsAndReports.kt`, `model/Review.kt`, `data/ReviewRepository.kt`, `data/ReportRepository.kt` | The Rate page, the Report page and review rows; ratings and reviews through names-only functions; `submit_report` (passed on two phones on 2026-09-28) |
| Database / Storage SQL | `supabase/migrations/` (11 files), `supabase/README.md`, `supabase/tests/` | Applied manually in the Supabase SQL Editor; `supabase/tests/` holds read-only checks, live safety checks and, since §14, `phase5_reports_query.sql` |
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

Phase 5 (Maps, Reviews, Reports) and Phase 6 (Admin and Final Release) follow. Phase 5 part 1 (reviews, reports, Email verified) passed on two phones on 2026-09-28 and was merged into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`) (§14). Phase 5 part 2 (Maps) passed on two phones on 2026-09-28 and was merged into `master` through PR #11 on 2026-09-28 (merge commit `255b0f8`) (§15). Mika's Profile redesign and settings (PR #10) passed a short test on two phones on 2026-09-28 and was merged into `master` through PR #10 on 2026-09-28 (merge commit `49ac319`) (§16). Phase 6 has not started. The fixes, clean-up and optimization after the second demo audit (§17) are in code on branch `claude/cleanup-after-audit`; they are not built or tested yet.

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
  - sample Purchases, Claims and reviews (sample Alerts were replaced by real alerts in Phase 4). **The sample Purchases and Claims were replaced (§9); passed on two phones on 2026-09-26 (§9.6).** Sample reviews remain. **Replaced by real reviews in §14 (passed on two phones on 2026-09-28).**
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
- **`notification_preferences`**: one row per user (`message_alerts`, `reservation_alerts`, `recommendations`, `pickup_reminders`). A missing row means everything is on. Only `message_alerts` and `reservation_alerts` affect alerts today. **Since §13** the app no longer shows `recommendations` and `pickup_reminders` (checked on a phone on 2026-09-27). A save writes back their stored values (as coded; not checked in the database), and the columns stay.
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
  - Home header: new chat icon (badge = unread message alerts; **since §13, passed on two phones on 2026-09-27:** the sum of the threads' unread counts) → Messages inbox (`MessagesScreen`); bell badge = all unread alerts.
  - `ChatScreen`: full-screen conversation over any screen, live updates, marks the thread read.
  - Food details: "Message Donor", the Allergens and Storage rows, and the details dialog's "Ask for exact location"/"Message provider" links open the real chat (prefilled where the demo chat was). On your own listing the button reads "Your listing".
  - Activity: "Message donor"/"Message recipient" in reservation details; the bell dot only shows with unread alerts.
  - Alerts: real alerts, filters All/Messages/Reservations, Today/Earlier, mark read, tap to open the chat or the Activity tab, settings dialog saved to Supabase. The sample alerts and the "Personalized for you" card were removed.
  - Profile → Preferences → Notifications: "New messages", "Reservation updates", "Food recommendations" and "Pickup reminders" are saved to Supabase; "Notification sound" stays on the device. **Changed in §13 (passed on two phones on 2026-09-27):** the page opens from Profile → Notifications and has only "New messages" and "Reservation updates" (saved to Supabase) and "Notification sound" (saved on the phone, and now plays a sound).

### 8.4 Known limits

- No push notifications (the app must be open for live updates; alerts are still stored and shown later).
- No blocking or reporting of users (Phase 5), and no editing or deleting of messages. **Since §14 (passed on two phones on 2026-09-28):** people can be reported from a chat; there is still no blocking.
- No rate limit on messages beyond the 2000-character limit.
- Deleting a listing deletes its conversations, messages and related alerts.
- The chat icon badge counts unread message **alerts**, so it stays empty when message alerts are turned off; the inbox still shows unread counts. **Fixed in §13:** the badge sums the threads' unread counts (passed on two phones on 2026-09-27). With message alerts turned off it was not tested.
- The Profile screen's bell still opens settings and always shows its dot (unchanged demo UI). **Fixed in §13 (passed on two phones on 2026-09-27).**

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
- **Known issue, fixed in §12 (the Reserve review page and receipt; passed on two phones on 2026-09-27): the "Reserved" dialog never appears.** After a successful Reserve, `FoodDetailsScreen` sets its "Reserved" notice ("Reservation created. Pay ₱… at pickup." or "…Please coordinate pickup with the donor.") and at once calls `onReserved()`. `HomeScreen`'s `onReserved` reloads the feed and sets `selectedFood = null`, which closes the details dialog and the notice inside it before it is drawn. Present since `d4f65fe`, so not caused by §9 or §10. It affects §9.4 steps 8, 9 and 19 and §10.4 step 17. The reservation itself is created correctly; the amount still shows in My Reservations and in the line under the buttons. The developer chose on 2026-09-26 to fix it with the Phase 3 demo fixes, together with Buy Now / Claim Now. Proposed fix: keep the details open until the dialog's OK, then close them; the feed can still reload at once.
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
- **Blank Saved Listings card.** A saved listing the user can no longer see (expired, and not reserved by them) is hidden by the `food_posts` SELECT policy, so its card shows only "No longer available" and "Remove", without its name or photo. Seen in the §11.4 test (step 21). **Fixed in §12 (`get_my_saved_listing_names`); passed on two phones on 2026-09-27.**
- **Dates follow the phone's language.** The details times (`listingTimeFormat` and `listingClockFormat` in `MainActivity.kt`), the time buttons of Create and Edit Listing (`postTime()` in `PostScreen.kt`), Activity, Alerts and Chat format dates with `Locale.getDefault()`, while "Member since" uses English; on a phone set to another language the month names and AM/PM change. Found in review; not tested. **Changed in §12 (every date uses `Locale.ENGLISH`); built, but not checked on a phone set to another language.**
- **Long error text on Post Listing; no error on Delete.** `FoodViewModel.errorText()` shows the whole exception message on Post Listing, which for a Supabase error includes the request URL (headers masked); Edit uses the short `supabaseErrorText()`. _Correction (2026-09-26):_ Delete errors were never shown at all; they went to the feed's error state, which no dialog displays and the next load clears. Found in review; not tested. Both are addressed in §12: the short Post Listing error passed on a phone on 2026-09-27 (flight mode), and the Delete result dialog passed for "Listing deleted." and "Listing removed…"; a Delete error was not triggered.

### 11.6 Results (2026-09-26)

- **Build:** `assembleDebug` of `4b6aac6` on the developer's PC: BUILD SUCCESSFUL in 1m 34s, only the three old deprecation warnings. Installed on both phones after the migration.
- **Migration:** the developer ran `20260929_phase2_listing_details.sql` in the SQL Editor before installing, and the test used every new column, both new functions and `saved_listings`. After the test, a read-only check query on the live project returned true for all 8 of its checks: the 6 new columns, both functions and `saved_listings` exist; `authenticated` has no UPDATE privilege on `food_posts` and no UPDATE policy is left on it, while INSERT is still granted; `authenticated` can execute both functions and `anon` cannot.
- **Two-phone test:** §11.4 steps 1–25 passed, given in parts in the order §11.4 now shows. The first draft had `EXPIRY TEST` expire 15 minutes after posting, with its edit in A's part 2; it was changed so that the edit and B's bookmark happen before it expires.
- **Regression round:** §11.4 steps 26–39 passed. What it did not cover is listed after step 39.
- **Expected, not new:** the "Reserved" dialog never appeared after Reserve, and deleting `GALLERY TEST 2` also deleted B's two reservations on it (both §9.6). A reservation kept its price after the listing's price changed, and a new one used the new price (steps 18 and 22).
- **New small issue:** the blank Saved Listings card (§11.5).
- **Not checked on the live project:** the other negative checks of §11.3 (the photo-path rules, the saved-listing refusals, who can see donor names, the refusals of `update_my_food_post`) ran only on the local PostgreSQL copy. The missing UPDATE privilege and policy were confirmed live (above), but no direct UPDATE was attempted there.


## 12. Phase 3 demo fixes: reservations, Activity, Profile

**Status (2026-09-27): passed on two phones, and merged into `master` through PR #7 on 2026-09-27 (merge commit `c184d9b`).** Written on branch `claude/phase3-demo-fixes` (from `master` at `aa3e823`). Built with AGP at `24fe9f2`. Migration `20260930_phase3_demo_fixes.sql` applied to the live project on 2026-09-26; its read-only check passed 13 of 13 and the live safety check 14 of 14. The §12.4 two-phone test passed on 2026-09-27 (§12.6). The SQL was also tested on a local PostgreSQL 16 copy (§12.3). These are fixes to finished Phase 3 features, not part of Phase 5.

### 12.1 What changed

- **Reserve review page and receipt** (`FoodActions.kt` `ReservationReviewDialog`, `MainActivity.kt` `FoodDetailsScreen`).
  - Buy Now, Claim Now and the fake checkout and reserve pages are gone. The details page keeps Message and Reserve; on your own listing it shows "Your listing / You cannot reserve it". Near Reserve it shows the amount: "You'll pay ₱X at pickup for N <unit>." / "The app takes no payments.", or "Free — nothing to pay." / "Please be respectful and pick up on time."
  - Reserve opens the full-screen "Review reservation" page: cover, name, "From <donor>", Reserving as, quantity and unit, pickup location, pickup window, handover ("Not set — ask the donor"), expiry, the price (per unit, total, "Paid at pickup. The app takes no payments.") or "Free", and an optional message to the donor. "Confirm reservation" shows "Reserving…" and is disabled while it runs.
  - On success the page becomes the "Reserved" receipt ("You reserved N <unit> of <name>.", the amount, location, pickup window, whether the message was sent, "You'll find this reservation in Activity → My Reservations.", and Message donor/seller and Done). Done and Back close the receipt and the details and reload the feed. This fixes the missing "Reserved" dialog of §9.6.
  - On failure the page stays open with the server's short error text, or "Your reservation could not be made. Check your internet connection and try again."
  - When the donor name is unavailable, no sentence uses it: "the donor" (or "the seller" for a priced listing) is used instead and the "From" line is left out.
  - The receipt's button reads "Message seller" for a paid reservation and "Message donor" otherwise.
- **Pickup times and the note go through the chat.** The demo pickup picker (`PickupOptions`) and the demo chat in `FoodActions.kt` are removed. The Pickup Window row opens the chat with "Hi! When can I pick up <name>?". The review page's note is sent after the reservation succeeds (`open_reservation_conversation`, then `send_message`); if sending fails, the reservation stands and the receipt says so.
- **Delete through `remove_my_food_post`.** Clients can no longer delete `food_posts` rows directly. A listing nobody ever reserved is deleted with its photos; otherwise it becomes `REMOVED`: hidden from Home, My Listings, saving and reserving; its open reservations are cancelled with `cancel_reason = 'LISTING_REMOVED'`, each recipient gets one "Listing removed" alert (it follows the "Reservation updates" switch and opens that reservation in My Reservations); past reservations, photos and chats are kept, and its saved-listing rows are deleted. Both delete dialogs explain this, and a "Delete listing" dialog shows the result.
- **Activity.** The sample records, the location picker, the "Demonstration record" and closing lines are removed; the footer says "Share surplus food by creating a listing." with Post Food. A record without a photo shows a plain tile. Reservations on a removed listing say "Listing: Removed by the donor" / "Removed by you" with "Cancelled because the donor removed this listing.", "The donor has since removed this listing." or "You removed this listing." Cancel and Mark claimed are disabled while a request runs, and a failed load shows "Your reservations could not be loaded. Check your internet connection and try again."
- **`ReservationViewModel`** runs requests behind a `Mutex`, queues a refresh instead of dropping it, and exposes `saving`, `reserveResult` and `loadFailed`.
- **Profile.** Successful Claims (own reservations marked claimed) and Successful Donations (claimed reservations on own listings) are real, and together equal Activity's Completed. Their pages list the records newest first ("3 packs • ₱135.30", "Picked up Sep 26, 2026 • 3:00 PM", "Recipient: <name>"), with loading, error and empty states. A record page shows "View listing" only while the listing is available, and opens it by ID. The demo totals (38/24), "Demo total…", "DEMO-104", the by-title food lookup and `SectionFoodPhoto` are removed. A caption says "Verified badge, rating and response rate are demo values".
- **Phase 2 leftovers (§11.5).** Post Listing and Delete errors show the short text ("Your listing could not be published. Check your internet connection and try again." / "Your listing could not be deleted. …"). All dates use `Locale.ENGLISH`. A saved listing the user can no longer see shows its name and photo from `get_my_saved_listing_names()`.

**Left as they are:** the Profile bell, the Food recommendations and Pickup reminders switches, sound, compact preview, Response Rate, Ratings & Reviews, Report a concern, the Identity Verified badge, the Home filter help and "Help reduce food waste" card, and a donor cancelling a single reservation.

### 12.2 Migration `20260930_phase3_demo_fixes.sql`

Run it by hand in the SQL Editor after `20260929_phase2_listing_details.sql` (see `supabase/README.md`). It is safe to run again. It adds `REMOVED` to the `food_posts.status` check, `reservations.cancel_reason`, and `RESERVATION_REMOVED` to the `notifications.type` check; drops the `food_posts` DELETE policy and revokes DELETE; refuses removed listings in `create_reservation`, `update_my_food_post` and the `saved_listings` insert policy; makes `notify_reservation_change` skip listing-removal cancellations; and adds `remove_my_food_post(uuid)` and `get_my_saved_listing_names()` (SECURITY DEFINER, `authenticated` only).

Afterwards, run `supabase/tests/phase3_demo_fixes_check.sql` (read-only, 13 checks, expect all PASS) and `supabase/tests/phase3_live_safety_check.sql` (always ends with the error "SAFETY CHECK FINISHED", which rolls everything back; expect every check PASS or SKIP).

### 12.3 Local results (PostgreSQL 16, not the live project)

- All migrations in order, then the new one twice more: no errors, exactly one check constraint each on `food_posts.status` and `notifications.type`, and `authenticated` has only SELECT and INSERT on `food_posts`.
- Functional tests: outsider, `anon` and foreign-token calls refused; a reserved listing with two open reservations by one recipient and one claimed by another → `REMOVED`, 2 cancelled, quantity unchanged, one alert linked to the newest reservation, no alert for the donor or the claimed recipient, saved rows deleted, chats kept; removing again → `REMOVED`, 0; reserving, saving and editing a removed listing refused; a never-reserved listing → `DELETED`; single-reservation wording; no alert with the preference off; a recipient's own cancel still alerts the donor; bad `cancel_reason` values rejected; saved names are the caller's own only.
- Concurrency (two sessions, 2 s hold): cancel/claim/create racing a remove in either order all end consistently, and the log shows no deadlock.
- `phase4_live_safety_check.sql`: 21/21 PASS. `phase3_demo_fixes_check.sql`: 13/13 PASS. `phase3_live_safety_check.sql`: 14/14 PASS and fully rolled back; the SKIP path was checked too.

### 12.4 Manual test (A → B → A; phone 1 = A, phone 2 = B)

The steps in the order they were run on 2026-09-27 (§12.6). **Preconditions:** the migration and both checks in §12.2 have been run, and this branch is installed on both phones right after the migration. A (phone 1) and B (phone 2) are signed in. Each step is tagged [New] or [Regression].

**A, part 1 (phone 1)**
1. [New] Home → one of A's own listings: two buttons, "Your listing / Questions go to Messages" and "Your listing / You cannot reserve it". There is no Buy Now or Claim Now.
2. [Regression] Post `P3 PAID`:
   - "I want to sell", 1 photo, quantity `5`, unit `packs`, price `45.10`;
   - expiration a day or more ahead, and any pickup location;
   - pickup from 3:00 PM to 5:00 PM before that, and handover "Collect from provider".

   Expect "Listing published".
3. [Regression] Post `P3 FREE`: "I want to donate", 1 photo, quantity `3`, unit `pcs`, no pickup times and no handover.
4. [New, setup] Post `P3 EXPIRY`: "I want to donate", 1 photo, quantity `1`, expiration 15 minutes from now.
5. [New] Activity:
   - There is no "Quezon City, Metro Manila" row under "My Activity".
   - The "Thanks for making a difference!" card opens "Share surplus food by creating a listing." with Close and Post Food.
   - A My Listings record has no "This is your live Supabase listing." line.
6. [New] Note Activity's Completed (A0), and Profile's Successful Claims (A1) and Successful Donations (A2). A1 + A2 = A0. Under the stats: "Verified badge, rating and response rate are demo values".

**B, part 1 (phone 2)**

7. [New] Before it expires, bookmark `P3 EXPIRY`. Note B's Completed (B0) and Successful Claims (B1).
8. [New] Open `P3 PAID`:
   - two buttons, "Message Seller" and a green "Reserve";
   - under them, "You'll pay ₱45.10 at pickup for 1 packs." and "The app takes no payments.";
   - at quantity 3, "You'll pay ₱135.30 at pickup for 3 packs."
9. [New] The Pickup Window row opens the chat with A, with "Hi! When can I pick up P3 PAID?" ready to send. Close it without sending.
10. [New] Pickup Address: the card at the top shows P3 PAID's photo. Before, it showed the demo produce picture.
11. [New] Reserve (quantity 3). "Review reservation" shows:
    - the photo, "From <A>" and "Reserving as <B>";
    - Quantity "3 packs", the pickup location, and Pickup window, for example "Sep 27, 3:00 PM – 5:00 PM";
    - Handover "Collect from provider", and Expires;
    - Price per unit "₱45.10", Total "₱135.30", and "Paid at pickup. The app takes no payments."

    Type `I'll come at 5.` and tap **Confirm reservation**; it briefly reads "Reserving…".
12. [New] The "Reserved" receipt shows:
    - "You reserved 3 packs of P3 PAID." and "Pay ₱135.30 at pickup.";
    - the location and window;
    - "Your message was sent to <A>." and "You'll find this reservation in Activity → My Reservations."

    **Message seller** opens the chat showing "I'll come at 5.". **Done** closes the details, and Home shows 2 packs.
13. [New] Open `P3 FREE`. It shows "Free — nothing to pay." and "Please be respectful and pick up on time."
    - Reserve 1. The review page shows Pickup window and Handover as "Not set — ask the donor", and Price "Free".
    - Confirm with no message. The receipt shows "You reserved 1 pcs of P3 FREE." and "Free — nothing to pay.", with no message line.
14. [Regression] Reserve 1 more of `P3 PAID`. Then My Reservations → that reservation → Cancel. Expect "Reservation cancelled. The quantity is available again."
15. [New] Reserve 1 of `P3 PAID` twice more. B now holds 3 + 1 + 1 open, and P3 PAID leaves Home because it is fully reserved.

**Stale quantity (B on both phones)**

16. [New] Sign in as B on phone 1 too.
    - On both phones: open `P3 FREE` ("Up to 2 pcs"), set quantity 2, tap Reserve, and stay on the review page.
    - Phone 1: Confirm. The receipt appears.
    - Phone 2: Confirm. The page stays open with "Not enough food available. Only 0 remains.", and there is no receipt.
    - Sign phone 1 back in as A.

**A, part 2 (phone 1)**

17. [Regression] Alerts shows:
    - "New reservation" for P3 PAID and for P3 FREE;
    - one "Reservation cancelled": "<B> cancelled their reservation of P3 PAID. The quantity is available again.";
    - "New message from <B>": "P3 PAID: I'll come at 5."
18. [Regression] My Donations → P3 PAID "3 packs • ₱135.30". The details show "Recipient: <B>", "Reservation for your listing.", "Price: ₱45.10 per unit" and "Collect ₱135.30 at pickup.", with no closing "live" line. Mark claimed → "Reservation marked as claimed."
19. [Regression] P3 FREE "1 pcs • Free" → Mark claimed → "Reservation marked as claimed."
20. [New] Completed = A0 + 2, Successful Donations = A2 + 2, and Successful Claims = A1.
    - The Successful donations page shows "Reservations on your listings that you marked as claimed.", then P3 FREE ("1 pcs • Free", "Picked up …", "Recipient: <B>") and P3 PAID ("3 packs • ₱135.30" …).
    - The P3 PAID record shows its photo, "Claimed" and "Pickup location: …". It has no "View listing", because the listing is fully reserved.
21. [Regression] `OLD TEST` → Edit listing → change the description → "Listing updated."
22. [New] Post `P3 NEVER` (donation, 1 photo, quantity 1). My Listings → Delete. The dialog reads "If nobody has ever reserved this listing, it is deleted with its photos. If it has reservations, …". Delete → "Listing deleted."
23. [New] Delete `P3 PAID` → "Listing removed. 2 open reservations were cancelled, and their recipients were told. Its past reservations and chats are kept." It is gone from My Listings and from Post → My posts.
24. [New] Delete `P3 FREE` → "Listing removed. 1 open reservation was cancelled, and its recipient was told. Its past reservations and chats are kept."
25. [New] My Donations:
    - P3 PAID's two "1 packs" reservations are Cancelled, and their details end with "Listing: Removed by you" and "You removed this listing.";
    - the claimed ones are still Claimed;
    - Completed and Successful Donations are unchanged (A0 + 2 and A2 + 2);
    - there is no new "Reservation cancelled" alert.

**B, part 2 (phone 2)**

26. [New] Two "Listing removed" alerts, with a ✕ icon:
    - "<A> removed P3 PAID, so your 2 reservations were cancelled."
    - "<A> removed P3 FREE, so your reservation was cancelled."

    [Regression] There are also two "Reservation claimed" alerts.
27. [New] The P3 PAID alert opens My Reservations with a "1 packs • ₱45.10" reservation: "Status: Cancelled", "Listing: Removed by the donor" and "Cancelled because the donor removed this listing.", with no Cancel button.
28. [New] Purchases: P3 PAID "3 packs • ₱135.30", with its photo and "The donor has since removed this listing.". Claims: P3 FREE "1 pcs • Free", the same.
29. [New] Successful Claims = B1 + 2 (P3 FREE and P3 PAID, with no "Recipient" line). Completed = B0 + 2.
30. [New] Purchases → P3 PAID → Message donor: the chat with "I'll come at 5." opens. Send `Thanks anyway!`; it reaches A, whose chat badge goes up on phone 1.
31. [New] Home shows neither P3 PAID nor P3 FREE. Saved Listings: P3 EXPIRY (expired) shows its name and photo, "No longer available" and Remove. Before, this was a blank card. Remove it.
32. [New] With flight mode on:
    - Post Listing → "Your listing could not be published. Check your internet connection and try again."
    - On a listing still on Home, Reserve → Confirm → "Your reservation could not be made. Check your internet connection and try again."

    Turn flight mode off.

**Not covered by this test.** Use §4, §9.4, §10.4 and §11.4 when those areas change. The gaps are: email sign-up, Google sign-in, Keep me signed in, the profile photo, the password change, the photo gallery, the Delete, Cancel and Mark claimed error texts, dates on a phone set to another language, and the password-reset email.

### 12.5 Known limits

- A user who already had a chat about a listing keeps it after the listing is removed and can still open it; nobody else can start one.
- A removed listing cannot be restored.
- Nothing updates live apart from chat and alerts: other phones see a removal after their next reload.
- Deleting photos of a never-reserved listing is best effort.
- **Older app builds break against this migration:** they can't delete listings (no DELETE privilege), and their `Json` fails on the new `REMOVED` status and `RESERVATION_REMOVED` alert type. Install the new build on every phone after running the migration.
- "Listing: Removed by you / by the donor" is shown in a record's details dialog, not on its card in the list.

### 12.6 Results (2026-09-26 to 2026-09-27)

- **Build:** `assembleDebug` of `24fe9f2` on the developer's PC (2026-09-26): BUILD SUCCESSFUL in 3m 23s, with only the three old deprecation warnings. Installed on both phones right after the migration.
- **Migration and live checks (2026-09-26):** `20260930_phase3_demo_fixes.sql` was applied together with `phase3_demo_fixes_check.sql` in the same run: 13 of 13 PASS. `phase3_live_safety_check.sql`: 14 passed, 0 failed, 0 skipped, all rolled back ([results log](#results-log)).
- **Two-phone test:** §12.4 steps 1–32 passed. Steps 1–15 and steps 16–32 were reported in two sessions, both on 2026-09-27.
- **Merged:** into `master` through PR #7 on 2026-09-27 (merge commit `c184d9b`).
- **Observed:** "Listing: Removed by you / by the donor" appears in the record's details dialog, not on its card, as coded (§12.5).
- **Not checked:** the list at the end of §12.4. The concurrency results in §12.3 come from the local PostgreSQL copy only.

---

## 13. Phase 4 demo fixes: messages, alerts, Profile

**Status (2026-09-27): passed on two phones, and merged into `master` through PR #8 on 2026-09-27 (merge commit `39b57b5`).** Written on branch `claude/phase4-demo-fixes` (from `master` at `c184d9b`, the PR #7 merge) and built with AGP at `3288d02`. The migration `20261001_phase4_demo_fixes.sql` was applied to the live project on 2026-09-27, and its read-only check passed 9 of 9. A short two-phone test (§13.4, 13 steps) passed the same day (§13.6). The SQL was also tested on a local PostgreSQL 16 copy (§13.3). These are fixes to finished Phase 4 features, not part of Phase 5.

### 13.1 What changed

- **Profile bell and gear** (`ProfileScreen.kt`). The bell opens Alerts, and its orange dot shows only while there are unread alerts, like the Home and Activity bells. The gear opens the Settings page (content description "Settings"), which was reachable before only through the bell. The "Account Settings" menu row still opens Account details. The header line reads "Demo verification & rating".
- **Notification preferences** (`ProfileSections.kt`, `ProfileScreen.kt`).
  - The "Food recommendations" and "Pickup reminders" switches are removed; no alert ever used them. `NotificationPreferences` keeps both fields (`@EncodeDefault`), and Save writes back the values loaded from the account, so the columns and older builds are unaffected. No database change.
  - "App preferences" → "Compact preview" (a page of sample foods) and the "Preferences" hub are removed. The Profile menu row and the Settings link are now "Notifications / Message and reservation alerts, sound" and open "Notification Preferences" directly.
  - The page shows "New messages", "Reservation updates" and "Notification sound", with the note: "New messages and Reservation updates are saved to your account and decide which alerts you get on the Alerts page. Notification sound is saved on this phone: it plays the phone's notification sound when a new alert arrives while the app is open, and stays quiet in silent and vibrate mode. Phone notifications while the app is closed are not available yet."
- **Notification sound** (new `AlertSound.kt`, `NotificationViewModel.newAlerts`, `HomeScreen`).
  - When an alert arrives live through Realtime (never the alerts loaded when Home starts), `HomeScreen` plays the phone's default notification sound (`RingtoneManager.TYPE_NOTIFICATION`, notification audio usage) if all of these hold: the app is on screen (resumed); "Notification sound" is on; the alert is not a message of the chat that is open; the ringer is on normal (silent and vibrate stay quiet). A new sound stops the previous one, and leaving the screen stops it.
  - The switch is stored on the phone in `salusalo_session` → `notification_sound` (default on), next to "Keep me signed in"; sign-out does not clear it. The old `salusalo_profile` → `notificationSound` value is ignored.
  - No Android notification, no permission and no new library.
- **Response rate** (migration `20261001_phase4_demo_fixes.sql`, `MessageRepository.getMyResponseRate()`, `ResponseRate` in `model/Conversation.kt`, `ProfileScreen.kt`, `ProfileSections.kt`).
  - Over the last 30 days: **received** = the caller's chats whose first message the other participant sent in that period; **replied** = those in which the caller sent a message after that first message; the rate is replied ÷ received × 100, rounded. With nothing received there is no rate: the stat shows "—".
  - The stat loads when Profile opens and again when its page opens. The page shows the percentage or "—", "Response rate", "Last 30 days", then one of: "Loading your response rate…"; "Your response rate could not be loaded. Check your internet connection and try again." with "Try again"; "No one has started a chat with you in the last 30 days, so there is no rate yet."; or a card "Your last 30 days" with "Chats started by others: N", "Chats you replied to: R", "Chats without a reply: N−R" and "R ÷ N × 100 = P%" ("≈" when not exact). "How it works" reads: "Counts chats that someone else started with you in the last 30 days. A chat counts as replied once you send a message after their first message. Chats you started are not counted. A note sent with a reservation starts a chat too. Only you can see your response rate."
  - The demo "92%", "Demo reporting period", the example summary (50 / 46 / 4) and "Your actual messages are not being tracked." are removed. The caption under the stats reads "Verified badge and rating are demo values".
- **Live Messages inbox and chat badge** (`MessageViewModel.start()`, `MessageRepository.allMessageInserts()`, `realtimeInserts()`, `HomeScreen`, `ChatScreen.kt`).
  - The inbox loads when Home starts and then follows new messages in all of the caller's threads through one Realtime channel on `messages` with no filter (`realtimeInserts()`'s filter is now optional). Realtime applies the `messages` SELECT policy, so only the caller's own threads arrive.
  - A message in a thread already listed updates its last message, time, "mine" flag and order in memory; the unread count goes up only for someone else's message outside the open chat. No cover image is re-signed. A thread not listed yet (for example a new chat) is fetched once with `get_my_conversations(p_conversation_id)`. A reload keeps any thread that Realtime has already moved past the loaded copy.
  - The Home chat badge is the sum of the threads' unread counts, instead of the unread MESSAGE alerts, so it also counts while "New messages" alerts are off. The open chat is marked read, so it never counts.
  - When the app returns from the background (lifecycle ON_START after a stop, not the first start), the inbox reloads once, because Realtime misses messages while the app is stopped. The initial load and this reload show no error dialog; opening Messages still reloads the list and shows errors.
  - The Messages page's Refresh button is removed. The Realtime token refresh stays in `NotificationViewModel` only; it covers the whole client.
- **Docs.** The Phase 3 test dates are corrected to 2026-09-27 (both sessions were on that day), and Phase 3's merge through PR #7 (`c184d9b`) is recorded.

**Left as they are:** phone notifications while the app is closed (push; a later phase), Ratings & Reviews and Report a concern (Phase 5), the Identity Verified badge and the verification pages (Phase 6), the Home page, message alerts, the Home and Activity bells and the Alerts page.

### 13.2 Migration `20261001_phase4_demo_fixes.sql`

Run it by hand in the SQL Editor after `20260930_phase3_demo_fixes.sql` (see `supabase/README.md`). It is safe to run again. It adds one function, `get_my_response_rate()`, and changes no table, column, policy, table grant, trigger or existing function.

- **SECURITY INVOKER, STABLE, `search_path = public`, no arguments.** It checks `is_salusalo_firebase_jwt()` first ("Not authenticated"), then takes the caller from `current_salusalo_profile_id()` ("Profile not found" when null). It reads only rows the caller may already select, so it needs no extra rights; it also filters explicitly on the caller being the chat's recipient or donor, and the `conversations` and `messages` RLS still applies on top.
- The first message of a chat is the earliest by `created_at`, ties broken by `id`; "after" uses the same order.
- EXECUTE is revoked from `public` and `anon` and granted to `authenticated`.
- **Order:** it can run before or after the new build is installed. Older builds never call it. Until it runs, the new build shows "—" for Response Rate and "Your response rate could not be loaded…" on its page.

Afterwards, run `supabase/tests/phase4_demo_fixes_check.sql` (read-only, 9 checks, expect all PASS): the function's result type; INVOKER, STABLE and `search_path=public`; EXECUTE for `authenticated` only; the helpers executable by `authenticated`; no INSERT, UPDATE or DELETE for clients on `conversations`, `messages` and `notifications`; SELECT-only policies on them; `notify_user` not executable by clients; `messages` and `notifications` in `supabase_realtime`; no UPDATE on `users` and no UPDATE or DELETE on `food_posts` for clients.

### 13.3 Local results (PostgreSQL 16 and kotlinc, not the live project or a phone)

- **Migrations:** all in order, with `20261001_phase4_demo_fixes.sql` applied twice at the end: no errors.
- **Counts on sample data.** Caller A got received 5, replied 4. Counted and replied: a chat B started that A answered; a chat where B wrote twice before A replied (counted once); a chat whose first two messages share one `created_at`, where B's has the lower `id`; a chat made through the real RPCs (`start_listing_conversation`, `send_message`). Counted, not replied: a chat C started that A never answered. Not counted: a chat A started; a chat whose first message is 31 days old; a same-`created_at` tie where A's message has the lower `id`. B got 2 / 1, C 1 / 1, and a profile with no chats 0 / 0; each caller got only their own counts. With RLS bypassed (table owner), A still got 5 / 4, so the explicit participant filter works on its own.
- **Refusals:** `anon` → "permission denied for function get_my_response_rate"; a token from another Firebase project → "Not authenticated"; no token → "Not authenticated"; a valid token without a profile → "Profile not found"; calling it with an argument → no such function; a direct insert into `messages` as `authenticated` → permission denied.
- **Existing checks on the local copy:** `phase4_live_safety_check.sql` 21 passed, 0 failed, 0 skipped (after seeding a reservation and alert settings); `phase3_live_safety_check.sql` 14/14; `phase3_demo_fixes_check.sql` 13/13; the new `phase4_demo_fixes_check.sql` 9/9 PASS. In a rolled-back negative run (EXECUTE granted to `anon` and PUBLIC, SECURITY DEFINER, INSERT on `messages` granted, an UPDATE policy on `notifications`), checks 2, 3, 5 and 6 reported FAIL, as they should.
- **Kotlin:** a kotlinc 2.2.10 type check of all sources after each commit: 0 errors, and only the three old deprecation warnings. It uses desktop Compose jars and hand-written stubs of the Android classes (including `AudioManager`, `RingtoneManager`, `Ringtone`, `AudioAttributes` and the lifecycle owner classes), because Google Maven is blocked here. **It is not an AGP build**, and nothing ran on a phone: the sound, the ringer-mode check, the lifecycle events, the unfiltered Realtime channel and the badge are untested. (The AGP build and the phone test came afterwards; see §13.6. The ringer-mode check is still untested on a phone.)

### 13.4 Manual test (A → B → A; phone 1 = A, phone 2 = B)

The steps as run on 2026-09-27 (§13.6). The first draft of this test had 30 steps. At the developer's request, this short version of 13 steps was run instead. The checks it left out are listed after the steps, and they are not tested.

**Preconditions:**
- The migration and `phase4_demo_fixes_check.sql` (9 of 9 PASS) have been run, and this branch is installed on both phones.
- A (phone 1) and B (phone 2) are signed in, and already have at least one older chat with each other.
- Phone 1's ringer is on normal, with the notification volume up.

Each step is tagged [New] or [Regression].

**A**

1. [Regression] Post a new listing named `P4 FREE`: a donation, quantity 3, expiring a few days later, with one photo. It shows on Home.
2. [New] Profile:
   - "Demo verification & rating" is near the top, and "Verified badge and rating are demo values" is under the stats.
   - Response Rate shows a percentage or "—", never "92%". Its page: write down "Chats started by others" and "Chats you replied to" (both 0 if it shows "No one has started a chat with you in the last 30 days, so there is no rate yet.").
   - The gear (top right) opens "Settings". Its "Notifications" opens "Notification Preferences" with only "New messages", "Reservation updates" and "Notification sound", all on. There is no "Food recommendations", "Pickup reminders" or "Compact preview".
3. [New] The Profile bell opens Alerts.
   - Tap "Mark shown as read": back on Profile, the bell has no orange dot.
   - On Home, write down the number on the chat icon (none = 0).
   - Tap the chat icon: "Messages" has no Refresh button. Stay on this page.

**B**

4. [New] In Messages, open an older chat with A (not the top one) and send "Hello again". Then Home → `P4 FREE` → "Message Donor" → send "Is P4 still available?". Stay in this chat.

**A (without touching the phone)**

5. [New] Without anyone touching phone 1:
   - The notification sound plays.
   - The `P4 FREE` chat (B's name, "Your listing: P4 FREE") appears at the top with "Is P4 still available?" and an unread count of 1.
   - The older chat is right below it, with "Hello again" and 1.
   - On Home, the chat badge is 2 more than the number written down, and on Profile the bell has the orange dot.
6. [New] Open the `P4 FREE` chat and reply "Yes, it is." Stay in it.

**B**

7. [Regression] "Yes, it is." appears by itself. Reply "On my way".

**A**

8. [New] Three checks:
   - "On my way" appears in the open chat with no sound.
   - Go back, open the older chat too, then go to Home: the chat badge is back to the number written down.
   - Profile → Response Rate: both numbers are 1 more than in step 2.
9. [New] Profile → "Notifications" → turn "Notification sound" off → "Save preferences" ("Preferences saved to your account.").
   - Log out, then sign in again as A.
   - Profile → Notifications: "Notification sound" is still off.
   - Stay on Home.

**B**

10. [New] In the `P4 FREE` chat, send "Test 1".

**A**

11. [New] No sound, but the chat badge goes up by 1 by itself. Turn "Notification sound" back on and save. Press the phone's Home button, so Salu-Salo goes to the background without being swiped away.

**B**

12. [Regression] Home → `P4 FREE` → "Reserve" → "Review reservation":
    - quantity 1;
    - "Pickup at 5 PM?" in "Message to <A> (optional)";
    - "Confirm reservation" → the "Reserved" receipt.

**A**

13. [New] No sound played while Salu-Salo was in the background. Reopened from Recents:
    - the chat badge is 2 more than the number written down;
    - Messages shows the `P4 FREE` chat at the top with "Pickup at 5 PM?";
    - Alerts has "New reservation" ("<B> reserved 1 <unit> of P4 FREE.").

**Left out of the short test (not tested):**
- Sound settings:
  - silent and vibrate mode (the ringer-mode check), and Do Not Disturb;
  - the sound for a reservation alert (the one in step 13 arrived while the app was in the background);
  - "New messages" turned off: no alert and no sound, while the chat badge still counts.
- Settings pages:
  - turning "Reservation updates" off and on again (the §8.7 regression; Save itself was used in steps 9 and 11);
  - the other Settings pages opened from the gear (only "Notifications" was opened).
- Response rate:
  - B's own rate (a chat B started does not count for B; tested only on the local copy, §13.3);
  - the Response Rate error text (it needs the phone offline while the page loads).
- A Realtime reconnect while the app stays open.
- The regressions in the draft's last step:
  - removing a reserved listing and its "Listing removed" alert;
  - Mark claimed, with Purchases or Claims and the Activity totals;
  - email sign-up, editing a listing, and saving a listing.
- Also not run: Google sign-in, the profile photo, the password change, and the password-reset email (never tested).

### 13.5 Known limits

- No sound while the app is in the background or closed, and no phone notifications. Push notifications are a later phase.
- No sound for a message while "New messages" is off, because no alert is created. The chat badge still counts it.
- Every new alert plays the sound, message and reservation alerts alike.
- The sound switch belongs to the phone, not the account: every account signed in on that phone shares it.
- Do Not Disturb was not checked. The app checks only the ringer mode, not Do Not Disturb. Depending on the phone and its Do Not Disturb settings, the sound may still play while the app is open. The push-notification phase should add a Do Not Disturb check (for example `NotificationManager.currentInterruptionFilter`) and test it.
- A message missed during a Realtime reconnect while the app stays open appears the next time Messages opens, a chat is closed, or the app returns from the background.
- The response rate counts a reply however late it came, and the period is fixed at 30 days. A note sent with a reservation starts a chat, so it counts. Chats of a never-reserved listing that is deleted disappear with it (existing cascade) and drop out of the rate.
- Older builds still show "Food recommendations" and "Pickup reminders". Their stored values stay, and the new build writes them back unchanged.

### 13.6 Results

- **Build:** `gradlew.bat :app:assembleDebug --offline` at `3288d02`: BUILD SUCCESSFUL in 43s on 2026-09-27, with only old deprecation warnings (`ArrowBack`, `Logout`). The developer installed it from Android Studio on both phones.
- **Migration:** `20261001_phase4_demo_fixes.sql` applied to the live project on 2026-09-27 in the SQL Editor, with `supabase/tests/phase4_demo_fixes_check.sql` in the same run: 9 of 9 PASS ([results log](#results-log)).
- **Two-phone test:** the short §13.4 test, 13 steps, passed on 2026-09-27, reported as one result ("test passed").
- **Not tested:** the checks listed under "Left out of the short test" in §13.4, including silent and vibrate mode, Do Not Disturb, the sound for a reservation alert, and "New messages" turned off. The developer chose to test them in the later push-notification phase, which reworks the same alert and sound code.
- **Merged:** into `master` through PR #8 on 2026-09-27 (merge commit `39b57b5`).

---

## 14. Phase 5 part 1: reviews, reports, Email verified

**Status (2026-09-28): passed on two phones, and merged into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`).** Written on branch `claude/phase5-reviews-reports-v6ahqn` (from `master` at `39b57b5`, the PR #8 merge) and built with AGP at `3bd038c`. The migration `20261002_phase5_reviews_reports.sql` was applied to the live project on 2026-09-27; its read-only check passed 18 of 18 and `phase5_live_safety_check.sql` 27 of 27. The two-phone test (§14.4, 15 steps) passed on 2026-09-28 (§14.6). The SQL was also tested on a local PostgreSQL 16 copy (§14.3). The developer split Phase 5 into part 1 (this: ratings and reviews, reports, and an "Email verified" badge, which moved here from Phase 6) and part 2 (Maps, not started).

### 14.1 What changed

- **Ratings and reviews** (new `ReviewsAndReports.kt`, `model/Review.kt`, `data/ReviewRepository.kt`; `ActivityScreen.kt`, `FoodActions.kt`, `MainActivity.kt`, `ProfileScreen.kt`, `ProfileSections.kt`, `viewmodel/ReservationViewModel.kt`).
  - After a reservation is **CLAIMED**, its recipient can rate the listing's donor or seller: 1–5 stars and an optional comment of up to 500 characters. There is one review per reservation; sending again changes it, and there is no delete. Donors don't review recipients. A claimed reservation of a `REMOVED` listing can still be rated, and its review counts.
  - **Where to rate:** Activity → Purchases or Claims → the record's details → "Rate <name>" ("Rate the donor"/"Rate the seller" without a name), or "Change your review" once rated. The "Reservation claimed" alert already opens that record. The page shows the listing, "3 packs • ₱135.30", five stars ("Tap a star to rate." / "4 of 5 stars"), "Comment (optional)" with a counter, and "Your name, stars and comment are shown to other users."; its button is "Send review", or "Save review" when changing, with "Sending…" while it runs. Results: "Thanks! Your review was sent." or "Your review was updated.", with Done; errors: "Choose from 1 to 5 stars.", the server's short text, or "Your review could not be sent. Check your internet connection and try again." A sent or changed review reloads the Home feed, so the new rating shows when the reviewer goes back to Home.
  - **Sending** runs in `ReservationViewModel` behind the same Mutex and `saving` flag as reserve, cancel and claim: a second tap while one runs is ignored, one that has started is never dropped (Back waits), and the result goes only to the Rate page (`reviewResult`). The account's own reviews load with the reservation lists (`get_my_given_reviews`); if that load fails, nothing is shown and the last ones are kept.
  - **Where ratings show:** "★ 4.7 (12)" next to the donor's name on the Home cards (not on the "Newest listings" cards, which show no donor name), in the details header, and on the Donor/Seller row ("<name> • ★ 4.7 (12)"); nothing when there are no reviews. The Donor/Seller page loads the donor's details and latest 20 reviews fresh when it opens: "Email verified" (when true), "★ 4.7 • 12 reviews" or "No reviews yet", and under "Reviews" each reviewer's name, stars, comment and date ("Sep 27, 2026"), with "Loading reviews…", "Reviews could not be loaded. Check your internet connection and try again." and "Showing the latest 20 reviews.".
  - **Own Profile:** the Overall Rating stat ("4.7" or "—"); the Ratings & Reviews panel (the average, "Overall Rating", rounded stars, "(12 reviews)"/"(1 review)" or "No reviews yet", and the three latest reviews, or "Donors and sellers are rated after each pickup; people who only receive food have no rating."); the reviews page ("★ 4.7" or "—", "12 reviews"/"1 review"/"No reviews yet", "Reviews from people who picked up your food.", the latest 50 reviews, loading and error texts with "Try again"). The sample reviews, `ProfileReview`, "56 reviews • demo summary" and "Example review" are gone.
  - Donor names, ratings and the badge for lists of listings come from one call, `get_listing_donor_profiles`, which the new build uses everywhere instead of `get_listing_donor_names` (Home, details, Saved Listings, and the recipient's own reservations for "Rate <name>").
- **Reports** (`ReviewsAndReports.kt` `ReportPage`, `data/ReportRepository.kt`, `model/Review.kt`).
  - Three places: "Report this listing" at the bottom of a listing's details (not your own); "Report <name>" at the bottom of the Donor/Seller page (not on your own listing); and a flag at the top right of a chat ("Report <name>"; this is how a donor reports a recipient, for example one who didn't show up).
  - Reasons for a listing: "Unsafe or spoiled food", "Wrong or misleading details", "Spam or fake listing", "Other". For a person: "Rude or abusive", "Didn't show up", "Scam or fraud", "Spam", "Other". "Other" needs details; details are optional otherwise, up to 1000 characters.
  - The page: "Report listing" / "Report <name>", "What's wrong with <listing>?" / "Why are you reporting <name>?", the reasons, "Details (optional)" or "Details (required)" with a counter, "Reports go to the Salu-Salo team. The other person is not told.", "Send report" ("Sending…"). Client checks: "Choose a reason.", "Describe the problem in a few words.". Result: "Report sent. The Salu-Salo team will review it." with Done, or the server's short text, or "Your report could not be sent. Check your internet connection and try again.". The reported person is not told.
  - Settings → Trust & safety → "Report a concern" is no longer a preview: it explains where the three Report buttons are and says "Reports go to the Salu-Salo team. The person you report is not told. You can send up to 10 reports a day." There is no report without a target.
  - The developer reads reports with `supabase/tests/phase5_reports_query.sql` until the Phase 6 admin screen.
- **Email verified** (`UserRepository`, `MainActivity.kt`, `ProfileScreen.kt`, `ProfileSections.kt`, `model/UserProfile.kt`).
  - "Identity Verified" becomes a badge that shows only once Firebase has confirmed the account's email: Google accounts automatically; email/password accounts after they open the link in a verification email the app sends (`sendEmailVerification()`).
  - The server stores it in `users.email_verified`, set only by `sync_my_email_verified()` from the token. The app calls it in the background after the profile loads: it reloads the Firebase user, refreshes the ID token when Firebase says the email is confirmed but the cached token doesn't, and calls the function. It never blocks and never signs anyone out; on any failure the stored value stays and nothing is shown.
  - Own Profile: a chip under the name, green "Email verified" or grey "Email not verified", both opening Verification. The Verification panel reads "Your email address is confirmed." or "Confirm your email address to show the Email verified badge.", with a row "Email verified"/"Email not verified", "Confirms the email address, not your identity" and "Verified"/"Not verified". "Demo verification & rating" and "Verified badge and rating are demo values" are gone. The footer reads "Rate the people you get food from, and report anything that doesn't feel right."
  - Settings → "Verification / Confirm your email address" → "Email Verification": "Email verified" or "Email not verified", the email address, and "The badge only means that this email address was confirmed. It is not an identity check." When not verified: "We'll send a link to <email>. Open it, then come back and tap “I've confirmed my email”.", "Send verification email" ("Sending…") and "I've confirmed my email" ("Checking…", which reloads the user, refreshes the token and syncs). Results: "Verification email sent to <email>. If you don't see it, check your spam folder.", "Too many attempts. Please wait a few minutes and try again.", "The verification email could not be sent. Check your internet connection and try again.", "Your email is confirmed. Your profile now shows the Email verified badge.", "Your email is not confirmed yet. Open the link in the email we sent, then try again.", "Your email could not be checked. Check your internet connection and try again."
  - For other people the badge shows only on the Donor/Seller page (not on the details screen).
  - The demo "verificationSteps" and "verificationStatus" pages, the demo request and `demoVerificationRequested` are removed. Trust & safety now has "Coordinate clearly", "What the Email verified badge means", "Ratings and reviews", "Email verification / Confirm your email address" and "Report a concern / Where to find the Report buttons"; "Understand verification" and "How verification works" are gone.
- **Docs:** the Phase 4 demo fixes' merge through PR #8 (`39b57b5`) is recorded here, in `CLAUDE.md` and in `README.md`.

**Left as they are:** Maps (Phase 5 part 2: coordinates, a map picker, distance, Nearby, "Search area in Maps", "View on map"); phone notifications and the checks left for that phase (§13.4, §13.5); Phase 6 (ID verification, the admin screen); the Home page apart from the ratings; no alert for a new review.

### 14.2 Migration `20261002_phase5_reviews_reports.sql`

Run it by hand in the SQL Editor after `20261001_phase4_demo_fixes.sql`, **before** installing this build: the new build loads donor names through `get_listing_donor_profiles`, so without the migration it shows "Donor name unavailable" and its review, report and verification calls fail. **Older builds are unaffected:** supabase-kt 3.5.0 ignores unknown keys, so `UserProfile` still decodes with the new column (checked locally, §14.3); they never call the new functions; and `get_listing_donor_names` is unchanged. It is safe to run again. Details are in `supabase/README.md`.

- **Every new function the app calls** is SECURITY DEFINER with `search_path = public`, checks `is_salusalo_firebase_jwt()` first ("Not authenticated"), takes the caller only from `current_salusalo_profile_id()` ("Profile not found"), and is executable by `authenticated` only. The helper `caller_can_view_food_post` and the trigger function `users_start_unverified` are executable by no client. The trigger function has no token check: it takes no input, returns nothing but the row and only sets its `email_verified` to false; the insert policy already checks the token.
- **`users.email_verified`** (boolean, default false). A BEFORE INSERT trigger always stores false; the users insert policy is unchanged, and clients still have no UPDATE on `users`. **`sync_my_email_verified()`** sets it to true only when the token's `email_verified` is JSON `true` and its `email` equals the profile's email (trimmed, case-insensitive), changes nothing else (`updated_at` only when the value changes), and returns the stored value.
- **`caller_can_view_food_post(uuid)`** (internal) is exactly the `food_posts` SELECT policy, like `get_listing_donor_names`. If that policy changes, change both.
- **`reviews`** (one per reservation; rating 1–5; comment 1–500 characters or null; reviewer ≠ reviewee): RLS on, no privileges at all for clients. **`submit_review`**, **`get_my_given_reviews`**, **`get_listing_donor_profiles`** (for listings the caller may see: name, `email_verified`, average to one decimal and count), **`get_listing_donor_reviews`** (at most 50), **`get_my_rating_summary`**, **`get_my_received_reviews`** (at most 100). The read functions return only the reviewer's current name, the stars, the comment and the dates.
- **`reports`** (reporter, reported person, `target_type` LISTING/USER, `source` LISTING/DONOR_PAGE/CHAT, `food_post_id` and `conversation_id` set to NULL on delete, the `listing_name` snapshot, `reason`, `details`, `status` only `OPEN`, `created_at`): RLS on, no privileges at all for clients. A check constraint allows each reason only for its target and requires details for `OTHER`. **`submit_report(p_target, p_target_id, p_reason, p_details)`**: `LISTING` or `DONOR` with a listing the caller may see that is not their own, or `CHAT` with one of the caller's own chats; the reported person (the listing's donor or the chat's other participant) is found on the server and is never the caller. One open report per reporter and listing, and per reporter and person: a pre-check plus partial unique indexes, and a `unique_violation` from a race gives the same "You already reported this.". At most 10 reports per reporter in any 24 hours, counted after taking a per-reporter advisory lock.
- **Server error texts** (the app shows them as they are, adding a full stop where missing): "Not authenticated", "Profile not found"; `submit_review`: "Choose from 1 to 5 stars.", "Your comment must be 500 characters or fewer.", "You can rate only your own pickups.", "You can rate only after the pickup is marked as claimed.", "You cannot review yourself."; `submit_report`: "You can send up to 10 reports a day. Please try again later.", "Unknown report type.", "Choose a reason from the list.", "Describe the problem in a few words.", "Details must be 1000 characters or fewer.", "This listing is no longer available.", "You cannot report your own listing.", "You cannot report yourself.", "You can report only people in your own chats.", "You already reported this."
- **Nothing else changes:** no Phase 3 or Phase 4 RPC, trigger or policy, and no RLS is widened.

Afterwards run, in the same way as the earlier checks:
- `supabase/tests/phase5_reviews_reports_check.sql` (read-only, 18 checks, expect all PASS);
- `supabase/tests/phase5_live_safety_check.sql` (always ends with "SAFETY CHECK FINISHED", which rolls everything back; expect every check PASS or SKIP; it needs at least one claimed reservation, and a chat between that reservation's recipient and donor for check 16).

### 14.3 Local results (PostgreSQL 16 and kotlinc, not the live project or a phone)

- **Migrations:** all eleven in order on a local PostgreSQL 16 with stand-ins for Supabase's roles, `auth.jwt()`, Storage and the Realtime publication, then the new file twice more: no errors.
- **The helper matches RLS.** Callers: the donor A, a recipient R who reserved, an outsider O. Listings of A: available; expired; fully reserved by R; `REMOVED` after R's reservation was cancelled; available with an open reservation by R; expired with an open reservation by R; `REMOVED` with a claimed reservation by R. For all 21 pairs the helper's answer equalled whether that caller could SELECT the row under RLS, and also what `get_listing_donor_names` and `get_listing_donor_profiles` returned:

  | Listing | A (donor) | R (reserved) | O (outsider) |
  |---|---|---|---|
  | available | yes | yes | yes |
  | expired | yes | no | no |
  | fully reserved by R | yes | yes | no |
  | `REMOVED` (R's reservation cancelled) | yes | yes | no |
  | available, reserved by R | yes | yes | yes |
  | expired, reserved by R | yes | yes | no |
  | `REMOVED` (R claimed one) | yes | yes | no |

- **Reviews:** the recipient of a claimed reservation rated it ("created" true; the comment was trimmed); a second send changed the same row ("created" false; a blank comment became NULL) and one row remained; a claimed reservation of a `REMOVED` listing could be rated. Refused: the outsider, the donor and another recipient ("You can rate only your own pickups."), a RESERVED and a CANCELLED reservation ("…marked as claimed."), a missing reservation, ratings 0, 6 and null, a 501-character comment (500 accepted, also with surrounding spaces), a token from another project and no token ("Not authenticated"), a valid token without a profile ("Profile not found"), `anon` (permission denied on every new function), and direct SELECT, INSERT, UPDATE and DELETE on `reviews`. Averages: 5, 4, 4, 4 → 4.3 (4); after one change to 5 → 4.5; after another → 4.8. The outsider got summaries only for visible listings, a donor with no reviews got null/0, `p_limit` was kept between 1 and 50, and the recipient still got the donor's reviews after the listing was removed.
- **Reports:** a listing report, a chat report by the recipient and a chat report by the donor were saved with the right `target_type`, `source`, reported person and `listing_name`; a claimed `REMOVED` listing could be reported by its recipient. Refused: a second report of the same listing, and a Donor-page report of a person already reported from a chat ("You already reported this."); your own listing; yourself (DONOR on your own listing); a chat that isn't yours and a missing chat; a listing you can't see (fully reserved, expired, missing); an unknown or null reason, `NO_SHOW` for a listing and `UNSAFE_FOOD` for a person ("Choose a reason from the list."); an unknown target; `OTHER` without details; 1001 characters of details (1000 accepted); the 11th report within 24 hours (accepted again once the earlier ones were more than 24 hours old); direct SELECT, INSERT and DELETE on `reports`, also by `anon`. **Race:** two sessions sent the same person report at the same moment: one "OK", one "You already reported this.", one row. With the pre-check removed (in a rolled-back copy of the function), a duplicate still returned "You already reported this." through the unique index and the `unique_violation` handler. Deleting a never-reserved listing that had a listing report and a chat report still worked; both reports stayed, with `food_post_id` and `conversation_id` NULL and `listing_name` kept.
- **Email verified:** a verified token whose email differed only in case and surrounding spaces set true, and no other column changed; a second sync with the same value left `updated_at` unchanged; a false claim, a missing claim, the string `"true"`, another email and a missing email claim all stored false; the outsider's sync with a token carrying the recipient's email stored false for the outsider and left the recipient's row alone; a token from another project and a token without a profile were refused; a direct UPDATE of `users` was refused; a new profile inserted with `email_verified = true` was stored false.
- **Checks on the local copy:** `phase5_reviews_reports_check.sql` 18 of 18 PASS; `phase5_live_safety_check.sql` 27 passed, 0 failed, 0 skipped, with the data unchanged afterwards; `phase4_live_safety_check.sql` 21/21; `phase3_live_safety_check.sql` 14/14; `phase3_demo_fixes_check.sql` 13/13; `phase4_demo_fixes_check.sql` 9/9. **Negative runs** (rolled back): with SELECT granted on `reviews`, a policy on `reports`, the helper executable by `authenticated`, `sync_my_email_verified` as SECURITY INVOKER, `submit_review` executable by `anon`, another `search_path`, the insert trigger dropped, the reason-per-target constraint and a unique index dropped, and UPDATE granted on `users`, checks 2, 3, 5, 6, 7, 8, 9, 15 and 18 of the read-only check reported FAIL. With the recipient check removed from `submit_review`, the email comparison removed from `sync_my_email_verified`, the daily limit raised, SELECT granted on `reports` and the trigger dropped, checks 5, 6, 12, 19, 23 and 25 of the live safety check reported FAIL.
- **Kotlin:** a kotlinc 2.2.10 type check (with the Compose and serialization plugins) of every source after each Kotlin commit: 0 errors, and only the three old deprecation warnings (`FormatListBulleted`, `ArrowBack`, `Logout`). It used the supabase-kt 3.5.0 JVM jars and **Compose Multiplatform 1.7.3** desktop jars (1.9 needs `androidx` artifacts that are only on Google's Maven repository, which is blocked here), plus hand-written stubs of the Android, AndroidX Activity/Lifecycle, Credential Manager and Firebase APIs. A small JVM check: a `users` row with and without `email_verified` decodes (also into a model without the field, as older builds have), `get_listing_donor_profiles`, `get_my_rating_summary`, `submit_review` and `get_listing_donor_reviews` rows decode, the scalar `true` of `sync_my_email_verified` decodes, `ratingLabel` gives "★ 4.0 (1)", "★ 4.8 (3)" and nothing without reviews, stars round to whole stars (4.3 → ★★★★☆, 4.5 → ★★★★★), and a blank comment is sent as JSON null. **It is not an AGP build**, and nothing ran on a phone. (The AGP build and the phone test came afterwards; see §14.6.)

### 14.4 Manual test (A → B → A; phone 1 = A, phone 2 = B)

**Preconditions:**
- The migration has been run, then `phase5_reviews_reports_check.sql` (18 of 18 PASS) and `phase5_live_safety_check.sql` (every check PASS or SKIP).
- This branch is built with AGP and installed on both phones **after** the migration.
- A (phone 1) is an **email/password** account whose inbox the tester can open, with its email not confirmed yet and no reviews yet. A newly created email/password account needs the role-claim script (outside this repo) before it can sign in, like any sign-up.
- B (phone 2) is a **Google** account that already has a Salu-Salo profile and the role claim (for example G from §10.4). Do not sign in with Google using A's email address (§10.5).
- A and B have not reported each other before (reports can't be withdrawn until Phase 6).

Each step is tagged [New] or [Regression]. Where Home must show new data, the step names the action that reloads it.

**A (phone 1)**

1. [New] Profile (bottom bar):
   - under the name, a grey chip "Email not verified"; no "Identity Verified", no "Demo verification & rating", and no "Verified badge and rating are demo values" under the stats;
   - Overall Rating "—"; Ratings & Reviews shows "—", "Overall Rating", "No reviews yet", and under "Recent reviews" "Donors and sellers are rated after each pickup; people who only receive food have no rating.";
   - Verification Status: "Confirm your email address to show the Email verified badge.", and the row "Email not verified" / "Confirms the email address, not your identity" / "Not verified";
   - the footer: "Rate the people you get food from, and report anything that doesn't feel right."
2. [New] Tap the chip: "Email Verification" shows "Email not verified", A's email and "The badge only means that this email address was confirmed. It is not an identity check."
   - "I've confirmed my email" → "Your email is not confirmed yet. Open the link in the email we sent, then try again."
   - "Send verification email" → "Verification email sent to <A's email>. If you don't see it, check your spam folder." Don't open the email yet.
   - Back, then the gear (Settings): "Verification / Confirm your email address". Trust & safety: "Coordinate clearly", "What the Email verified badge means", "Ratings and reviews", "Email verification / Confirm your email address" and "Report a concern / Where to find the Report buttons"; no "How verification works". Report a concern: the three "Report …" lines and "Reports go to the Salu-Salo team. The person you report is not told. You can send up to 10 reports a day.", with no text box and no "Preview report".
3. [Regression] Post `P5 FREE`: "I want to donate", 1 photo, quantity 3, unit pcs, expiring 2 days later → "Listing published". Post's Back button (this reloads Home): its card shows A's name with no "★".

**B (phone 2)**

4. [New] Profile: a green chip "Email verified". Tap it: "Email verified", B's Gmail address and the sentence, with no "Send verification email" and no "I've confirmed my email".
5. [New, Regression] Open Post and tap its Back button (this reloads Home), then open `P5 FREE`:
   - the donor chip and the "Donor" row show A's name with no "★"; at the bottom, "Report this listing";
   - the Donor row opens "Donor": A's name, "Food donor", no "Email verified", "No reviews yet", "Message donor", and at the bottom "Report <A>". Back.
   - [Regression] Reserve 1 → "Review reservation" → message `P5 test` → "Confirm reservation" → the "Reserved" receipt with "Your message was sent to <A>." → Done. Open `P5 FREE` again → Reserve 1 → Confirm with no message → Done.
6. [New] Activity → My Reservations → one of the two `P5 FREE` reservations: "Status: Reserved", with Cancel and "Message donor" and **no** "Rate" button. Close.
   - Home → `P5 FREE` → "Report this listing": "Report listing", "What's wrong with P5 FREE?" and the four listing reasons. "Send report" with no reason → "Choose a reason."; choose "Other" and send → "Describe the problem in a few words."; choose "Wrong or misleading details", details `Test report` → "Send report" → "Report sent. The Salu-Salo team will review it." → Done.
   - "Report this listing" again → "Spam or fake listing" → "Send report" → "You already reported this." Back.

**A (phone 1)**

7. [Regression] Alerts: two "New reservation" ("<B> reserved 1 pcs of P5 FREE.") and "New message from <B>" ("P5 FREE: P5 test"). Activity → My Donations → one of the `P5 FREE` reservations → "Mark claimed" → "Reservation marked as claimed." Leave the other one Reserved.
8. [New] In A's inbox, open the Firebase verification email and tap its link (the browser page says the email was verified). Back in Salu-Salo: Profile → the chip → "I've confirmed my email" → "Your email is confirmed. Your profile now shows the Email verified badge.", and the page shows "Email verified" with no buttons. Back: the chip is green "Email verified"; Verification Status reads "Your email address is confirmed." with "Email verified" and "Verified".
9. [New] Home → chat icon → the `P5 FREE` chat with B → the flag at the top right → "Report <B>", "Why are you reporting <B>?" with "Rude or abusive", "Didn't show up", "Scam or fraud", "Spam" and "Other" → "Didn't show up" → "Send report" → "Report sent. The Salu-Salo team will review it." → Done.

**B (phone 2)**

10. [New, Regression] Alerts → "Reservation claimed" → Activity opens on Claims with the `P5 FREE` record: "Status: Claimed" and a "Rate <A>" button.
    - Tap it: "Rate <A>", "P5 FREE", "1 pcs • Free", "Tap a star to rate.", "Comment (optional)", and "Your name, stars and comment are shown to other users."
    - "Send review" with no stars → "Choose from 1 to 5 stars."
    - Tap the 4th star ("4 of 5 stars"), comment `Friendly and on time` → "Send review" → "Thanks! Your review was sent." → Done.
    - Open the record again: the button reads "Change your review".
11. [New] Home (bottom bar; the feed was reloaded when the review was sent): the `P5 FREE` card shows "<A>  ★ 4.0 (1)". Open it: the donor chip shows "★ 4.0 (1)" after A's name, and the Donor row reads "<A> • ★ 4.0 (1)". The Donor row opens "Donor": "Email verified", "★ 4.0 • 1 review", and under "Reviews": <B>, "★★★★☆", "Friendly and on time" and today's date.
12. [New] Activity → Claims → `P5 FREE` → "Change your review": the page is titled "Change your review", with 4 stars and the comment filled in. Tap the 5th star, comment `Great, thank you!` → "Save review" → "Your review was updated." → Done. Home (bottom bar; the feed was reloaded again): the card shows "★ 5.0 (1)", still one review.
13. [New] Chat icon → the `P5 FREE` chat with A → flag → "Report <A>" → "Scam or fraud" → "Send report" → "Report sent. The Salu-Salo team will review it." → Done. Then Home → `P5 FREE` → Donor row → "Report <A>" → "Rude or abusive" → "Send report" → "You already reported this."

**A (phone 1)**

14. [New] Profile (bottom bar; the rating loads each time Profile opens): Overall Rating "5.0"; Ratings & Reviews "5.0", "★★★★★", "(1 review)", and under "Recent reviews" <B>, "★★★★★", "Great, thank you!" and today's date. "View all" → "Ratings & Reviews": "★ 5.0", "1 review", "Reviews from people who picked up your food.", and B's review.
    - Profile → Log out, then sign in as A again (Home loads fresh): A's own `P5 FREE` card shows "★ 5.0 (1)". Open it: no "Report this listing"; its Donor row opens "Donor" with "Email verified" and no Report button.

**Developer (Supabase SQL Editor)**

15. [New] Run `supabase/tests/phase5_reports_query.sql`. The three newest rows, all `OPEN`, newest first:
    - <B> → <A>, `USER`, `CHAT`, `SCAM`, listing `P5 FREE`;
    - <A> → <B>, `USER`, `CHAT`, `NO_SHOW`, listing `P5 FREE`;
    - <B> → <A>, `LISTING`, `LISTING`, `MISLEADING`, details `Test report`, listing `P5 FREE`.

    There is no row for B's refused second listing report or the refused Donor-page report.

**Left out of the short test (not tested unless run separately):**
- Reviews: a priced record in Purchases ("Rate the seller" when the name is unknown); rating a claimed reservation of a `REMOVED` listing; the 500-character limit; more than 3 reviews on Profile or 20 on the Donor page; the review error texts (offline).
- Reports: the daily limit, reporting a listing you can't see, and a chat that isn't yours (local and live SQL checks only); the 1000-character limit; the report error text (offline).
- Email verified: a second phone signed in to A with an older token; "Too many attempts. …"; an unverified Google account; Google sign-in with an unverified email account's address (§10.5); the badge on other screens (it shows only on the Donor/Seller page).
- The regressions not in the steps: Cancel, removing a listing, editing a listing, Saved Listings (which now also load donor ratings), notification settings and sound, the response rate, the profile photo and name, the password change, email sign-up and the password-reset email.

### 14.5 Known limits

- No alert for a new review; the donor sees it the next time Profile opens.
- Nothing updates live: other phones see a new rating after their feed reloads (sign-in, Post → Back, a reservation, or a review sent from that phone).
- One review per pickup: someone who picked up several times from the same donor can rate each pickup, so frequent recipients weigh more in the average.
- No deleting a review, no reply from the donor and no moderation of review text; donors don't review recipients.
- Reviews show the reviewer's current name; a rename changes old reviews.
- Reports: only `OPEN`; reporters can't see or withdraw them; nobody is told; one open report per listing and per person per reporter until Phase 6 can close them; at most 10 per 24 hours. The developer reads them only in the SQL Editor.
- The badge means only that the email was confirmed. For other people it shows only on the Donor/Seller page. It is refreshed when the profile loads and after "I've confirmed my email"; another phone with an older token picks up the change at its next start (the app refreshes the token when Firebase already says the email is confirmed).
- Signing in with Google using the email of an unverified email/password account makes that account Google-only and verified (§10.5).
- The verification email is Firebase's default template and may land in spam.
- Older builds still show the demo ratings and the demo "Identity Verified" badge; they are otherwise unaffected.
- `caller_can_view_food_post` and `get_listing_donor_names` both repeat the `food_posts` SELECT rule; if that policy changes, change both.
- Deleting an account in the Firebase console leaves its Supabase profile, whose email is unique. Re-creating the account with the same email then cannot sign in ("We could not load your profile…") until the profile is re-linked to the new Firebase UID in the SQL Editor; this happened during the §14.4 test (§14.6). There is no in-app account deletion.

### 14.6 Results

- **Build:** `gradlew.bat :app:assembleDebug --offline` at `3bd038c`: BUILD SUCCESSFUL in 57s on 2026-09-27, with only the three old deprecation warnings. The developer installed it from Android Studio on both phones.
- **Migration:** `20261002_phase5_reviews_reports.sql` applied to the live project on 2026-09-27 in the SQL Editor, with `supabase/tests/phase5_reviews_reports_check.sql` in the same run: 18 of 18 PASS ([results log](#results-log)).
- **Live safety check:** `supabase/tests/phase5_live_safety_check.sql` on 2026-09-27: 27 passed, 0 failed, 0 skipped, rolled back by its closing error. Reservation checked: `P3 FREE` (claimed), with the two test accounts as recipient and donor and the newest other account as the outsider.
- **Two-phone test:** §14.4 steps 1–15 passed, sent in two parts. Steps 1–9 were reported at 00:10 (+08:00) on 2026-09-28, after being run late on 2026-09-27 and around midnight; steps 10–15 were reported shortly after, on 2026-09-28. A was an email/password account and B a Google account.
- **During the test:** after step 3 the developer deleted A's account in the Firebase console and re-created it with the same email. Sign-in then failed with "We could not load your profile…", because the Supabase profile (whose email is unique) was still linked to the deleted account's UID. After a read-only check of the profile that owned `P5 FREE`, the developer re-linked it to the new UID in the SQL Editor (one row changed) and ran the role script. A then signed in with `P5 FREE` unchanged (quantity 3), sent the verification email again, and the test went on (§14.5).
- **Not tested:** the list under "Left out of the short test" in §14.4.
- **Merged** into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`).

---

## 15. Phase 5 part 2: Maps

**Status (2026-09-28): built with AGP at `47ed894`, migration applied to the live project (read-only check 11 of 11, live safety check 22 of 22), and the 12-step two-phone test (§15.4) passed on 2026-09-28; merged into `master` through PR #11 on 2026-09-28 (merge commit `255b0f8`).** Written on branch `claude/phase5-maps` (from `master` at `711cc5e`, the PR #9 merge). Before that, the SQL was tested on a local PostgreSQL 16 copy, and the Kotlin passed a kotlinc type check and a JVM unit test (§15.3). Results: §15.6.

### 15.1 What changed

- **Pickup pin on Post and Edit** (new `PickupMap.kt`; `PostScreen.kt`, `model/FoodPost.kt`, `data/FoodRepository.kt`).
  - Under "Pickup location / exact meeting point" (and, on Post, "Enter an address or landmark recipients can recognize.") there is "Pickup pin", the note "Everyone who can see this listing can see this pin. Put it where you'll hand over the food. A public spot like a gate, store or plaza is fine; it doesn't have to be your home.", a 220 dp map, a status line and "Use my location". The address text stays as it was.
  - **Tapping the map** places or moves the pin; a tap while zoomed out (below zoom 15) also zooms in to 16. Pinch zooms, and a drag on the map pans it instead of scrolling the page.
  - **"Use my location"** ("Finding your location…" while it runs) asks for location access if needed and places the pin at the phone's position, from a fresh fix (or one at most 2 minutes old if none comes within 20 seconds). If the phone shares only its approximate location, or the fix is less precise than 100 m, the map centres there at zoom 14 and no pin is placed.
  - **Where the map starts:** the listing's pin at zoom 16 (Edit); else the phone's last known position (at most a day old) at zoom 16, only if location access is already allowed (no prompt); else Tagbilaran City (9.6500 N, 123.8540 E) at zoom 12.
  - **Status line:** Post: "Tap the map to place the pin, or use your location."; Edit of an older listing: "This listing has no pickup pin yet. Tap the map to add one. Without a pin, others see no map or distance."; Edit with a pin: "Tap the map to move the pin."; after placing: "Pin placed. Tap the map again to move it."
  - **"Use my location" messages:** "Location access is off for Salu-Salo. Tap the map to place the pin, or allow location in Settings → Apps → Salu-Salo → Permissions → Location."; "Your phone's location is turned off. Turn it on, or tap the map to place the pin."; "Your location could not be found. Try again, or tap the map to place the pin."; "Your phone shared only its approximate location, so the pin wasn't placed. Zoom in and tap the exact spot."
  - **Rules:** a new listing needs a pin ("Place the pickup pin on the map.", checked after the pickup location). On Edit an older listing can get a pin, and a pin can be moved but not removed; an older listing can still be saved without one. The pin is stored with 6 decimals.
- **Pickup location page** ("View on map" or the Pickup Address row; `FoodActions.kt`).
  - With a pin: under the address, "About 1.2 km from you in a straight line; the route may be longer." (when the phone's position is known); a 260 dp map centred on the pin with a marker (zoom and pan allowed); "Directions" instead of "Search area in Maps"; and under "Pickup instructions": "The pin shows where the provider hands over the food. If you can't find it, ask them in the chat." "Ask for exact location" stays.
  - **Directions** opens `https://www.google.com/maps/dir/?api=1&destination=<lat>,<lng>` (6 decimals, always with a dot), which the Google Maps app handles; if nothing opens it, a `geo:<lat>,<lng>?q=<lat>,<lng>` link; else "No maps app is available on this device."
  - Without a pin the page is unchanged: "Search area in Maps" and "Ask the provider for the exact meeting point and collection instructions."
- **Distance, Nearby, Nearest** (`MainActivity.kt`, `model/Geo.kt`).
  - Straight-line (haversine) distance from the phone to the pin: "350 m" under 1 km (steps of 10 m, at least 10 m), "1.2 km" under 10 km, "18 km" above.
  - Home cards: "Available  •  1.2 km away  •  <address>". The details' Pickup Address row: the address, then "About 1.2 km away (straight line)". Nothing without a pin or a phone position. The "Newest listings" cards and Saved Listings show no distance.
  - **Nearby** chip (after All): listings with a pin within 5 km; title "Food within 5 km"; empty: "No listings with a map pin within 5 km.". The filter help starts with "Nearby: listings with a map pin within 5 km of you".
  - **Nearest** in the sort menu (Newest, Nearest, Name): nearest first, listings without a pin last (newest first); until the phone's position is known the order stays Newest.
  - **One line under the chips** while Nearby or Nearest is chosen: "Finding your location…"; "Allow location to use Nearby and Nearest: Settings → Apps → Salu-Salo → Permissions → Location."; "Your phone's location is turned off. Turn it on to use Nearby and Nearest."; "Your location could not be found. Tap Nearby or Nearest again to try again."; "Distances use your phone's approximate location."
  - **The phone's position** is kept only in memory on Home. It is refreshed when Home opens and when the app returns to the foreground (only if access is already allowed; a position at most 10 minutes old is reused, else a fresh fix within 20 seconds), and each time Nearby or Nearest is chosen (asking for access if needed). It is gone after sign-out or when the app closes.
- **Directions for a reservation** (`ActivityScreen.kt`): Activity → My Reservations → a reservation of yours with status Reserved → "Directions" (same link and fallbacks) when its listing has a pin. "Message donor" and Cancel stay.
- **Location permission** (`AndroidManifest.xml`, `data/PhoneLocation.kt`): `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`, foreground only, asked for together (Android 12+ then offers Precise or Approximate) and only when "Use my location" is tapped or Nearby or Nearest is chosen. Precise makes the pin and short distances meaningful; Approximate still gives distances, with the note. Refused: everything else works, tapping the map still places the pin, no distances are shown, and Nearby and Nearest show the one-line message. No location library: the platform LocationManager through `androidx.core`'s `LocationManagerCompat` (fused provider on Android 12+, then network, then GPS).
- **The phone's location** is used on the phone for distances, and is never sent to Supabase or stored. The one exception is "Use my location" on Post and Edit, which puts the pin at the phone's position; the donor then saves that pin with the listing, and the note says who sees it.
- **Map library and tiles:** osmdroid 6.1.20 (`org.osmdroid:osmdroid-android`, Maven Central; no API key) with OpenStreetMap's tiles from `https://tile.openstreetmap.org/`. The tile source is one constant, `MAP_TILES` in `PickupMap.kt` (URL, name, attribution), so another provider can replace it later without touching the screens. Following the tile usage policy: the User-Agent `SaluSalo/<versionName> (Android; com.example.salusalo)`; "© OpenStreetMap contributors" on every map (tapping it opens openstreetmap.org/copyright); tiles cached in the app's own cache folder (at most 50 MB, trimmed to 40 MB), following the servers' cache headers; at most 2 downloads at a time; no bulk, preventive or offline downloading; no storage permission. Without a connection the map is blank and the rest of the page works.
- **Docs:** the Phase 5 part 1 merge through PR #9 (`711cc5e`) is recorded here, in `CLAUDE.md` and in `README.md`.

**Left as they are:** a map of all listings; a Home location, a Profile location and the Activity location picker; phone notifications; Phase 6; the pickup "now" option and the "(optional)" labels on Post; "View insights"; the four summary boxes on the details screen; the Home leaf icon; a full-screen photo view; the chat box and the keyboard.

### 15.2 Migration `20261003_phase5_maps.sql`

Run it by hand in the SQL Editor after `20261002_phase5_reviews_reports.sql`, **before** installing this build: the new build sends the pin with every new listing and saves edits through `update_my_food_post_with_pin`, so without the migration its Post Listing and Save changes fail. It is safe to run again. **Older builds are unaffected:** supabase-kt ignores the two new columns, their new listings get no pin, and they still edit through `update_my_food_post`, which is unchanged and never touches the pin.

- **`food_posts.pickup_latitude numeric(8,6)` and `pickup_longitude numeric(9,6)`**, nullable; existing listings get NULL. The check `food_posts_pickup_pin_check`: both set or both NULL, latitude −90 to 90, longitude −180 to 180 (NaN fails too).
- **Who sees the pin:** exactly who may see the listing under the existing `food_posts` SELECT policy. No policy or grant changes; clients still have SELECT and INSERT only on `food_posts`, and the insert policy is unchanged.
- **`update_my_food_post_with_pin(...)`**: the 12 arguments of `update_my_food_post` plus `p_pickup_latitude` and `p_pickup_longitude`; SECURITY DEFINER, `search_path = public`, executable by `authenticated` only. It takes the caller from the token, locks the listing, and refuses someone else's, a `REMOVED` and an expired listing with the same texts as `update_my_food_post`; then "Send both pickup pin coordinates, or neither." and "The pickup pin is not a valid map position."; then it calls `update_my_food_post` for every other field and its checks, and stores the pin rounded to 6 decimals. One transaction: when anything is refused, nothing changes. Both pin arguments NULL keep the stored pin (or its absence).
- **Nothing else changes:** no Phase 3, 4 or 5 function, trigger or policy, and no RLS is widened. The server does not require a pin, so older builds can still post.

Afterwards run, in the same way as the earlier checks:
- `supabase/tests/phase5_maps_check.sql` (read-only, 11 checks, expect all PASS);
- `supabase/tests/phase5_maps_live_safety_check.sql` (always ends with "SAFETY CHECK FINISHED", which rolls everything back; 22 checks, expect all PASS; with a single account the outsider checks report SKIP).

### 15.3 Local results (PostgreSQL 16, kotlinc and the JVM; not the live project or a phone)

- **Migrations:** all eleven earlier files in order on a local PostgreSQL 16 with stand-ins for Supabase's roles, `auth.jwt()`, Storage and the Realtime publication, a local seed (accounts, listings, reservations, reviews and chats made through the real functions), then the new file twice: no errors.
- **`phase5_maps_check.sql`:** 11 of 11 PASS. **`phase5_maps_live_safety_check.sql`:** 22 passed, 0 failed, 0 skipped, and afterwards the listings were unchanged (no test listing, no pin). Its checks: the donor's and the outsider's sign-ins; a listing posted with a pin through RLS stored 9.647812, 123.854013 (sent with 7 decimals); a listing without a pin; moving a pin and changing the name in one call; adding a pin to a listing without one; refused on insert: latitude only, longitude only, latitude 91, longitude 181; refused on edit with nothing changed: one coordinate, 90.5, −180.5, NaN, and a blank name sent with a new pin ("Enter the food name."); both pin arguments NULL and an edit through the old `update_my_food_post` both keep the pin; refused: the outsider ("You can edit only your own listing."), an expired listing, a `REMOVED` listing, a listing that doesn't exist, no login (`anon`: permission denied) and another Firebase project's token ("Not authenticated"); a direct UPDATE by the donor ("permission denied for table food_posts"); the outsider sees the pin while the listing is available and nothing once it has expired, while the donor still does.
- **Earlier checks on the same copy after the migration:** `phase3_demo_fixes_check.sql` 13/13, `phase3_live_safety_check.sql` 14/14, `phase4_demo_fixes_check.sql` 9/9, `phase4_live_safety_check.sql` 21/21, `phase5_reviews_reports_check.sql` 18/18, `phase5_live_safety_check.sql` 27/27 (its check 16 needs a chat between the claimed reservation's recipient and donor, which the local seed first lacked: 26 passed and 1 skipped, the same before and after this migration).
- **Negative runs** (each rolled back): dropping the pin constraint and storing a half pin made read-only checks 3 and 11 FAIL and live checks 7 and 8 FAIL; EXECUTE for `anon` → check 4 and live check 18 FAIL; a column UPDATE grant on the pin → check 7 and live check 20 FAIL; an UPDATE policy with the grant → checks 7 and 8 and live checks 20 and 21 FAIL; a SELECT policy of `true` → check 10 and live check 22 FAIL; `update_my_food_post` clearing the pin → check 6 and live checks 12, 13, 21 and 22 FAIL; the new function clearing the pin on NULL → live checks 12, 13, 21 and 22 FAIL; both functions without the owner check → live checks 14 and 21 FAIL; the new function without its pin checks → live checks 9 and 10 FAIL (the constraint still refused the values); both functions without the expiry and `REMOVED` checks → live checks 15 and 16 FAIL; the new function as SECURITY INVOKER → check 4 and 10 live checks FAIL. A first version of the live check reported SKIP instead of FAIL when a pin had been wrongly cleared; it was fixed before the commit.
- **Kotlin:** a kotlinc 2.2.10 type check (with the Compose and serialization plugins) of every app source: 0 errors, and only the three old deprecation warnings. This time it used Robolectric's `android-all` 16 jar (`16-robolectric-13921718`, from Maven Central) for the Android classes instead of hand-written stand-ins, the real osmdroid 6.1.20 classes, the supabase-kt 3.5.0 JVM jars and Compose Multiplatform 1.7.3 desktop jars. AndroidX and Firebase still had stand-ins (Google's Maven repository is not reachable from the session): `LocationManagerCompat` (both `getCurrentLocation` overloads, copied from the androidx source), `ContextCompat`, `Consumer`, `AndroidView`, the activity and lifecycle Compose functions, `BuildConfig`.
- **`GeoTest`** (`app/src/test/.../GeoTest.kt`, JUnit 4.13.2, run directly on the JVM, not through Gradle): 11 of 11 passed: distances (1° of latitude ≈ 111.195 km), the Nearby boundary, the labels ("990 m", "1.0 km", "9.9 km", "10 km"), a dot with a German default locale, rounding to 6 decimals, valid positions, and the pin fields' JSON with and without a pin (a row without the new columns decodes too).
- **Not done in that session:** the AGP build, lint, `testDebugUnitTest` through Gradle, the migration on the live project, and anything on a phone. All but lint were done afterwards (§15.6).

### 15.4 Manual test (A → B → A; phone 1 = A, phone 2 = B)

**Preconditions:**
- The migration has been run, then `phase5_maps_check.sql` (11 of 11 PASS) and `phase5_maps_live_safety_check.sql` (22 passed, or SKIPs only for the outsider checks).
- This branch is built with AGP and installed on both phones **after** the migration.
- Both phones have location turned on, the Google Maps app installed, and Salu-Salo's location access **not allowed yet** (a fresh install, or Settings → Apps → Salu-Salo → Permissions → Location → "Don't allow").
- A and B are together (within a few hundred metres of each other).
- A already has an older listing without a pin, available and unexpired (for example `P5 FREE` from §14.4, or post one with the old build before installing this one); below it is called `OLD`.

Each check is tagged [New] or [Regression]. Where a phone must show new data, the step names the action that reloads it.

**A (phone 1)**

1. [New, Regression] Home → Post. Fill in `MAP TAP`: "I want to donate", 1 photo, quantity 2, unit pcs, a pickup location text, expiring tomorrow, pickup window from later today.
   - [New] Under "Enter an address or landmark recipients can recognize.": "Pickup pin", the note "Everyone who can see this listing can see this pin. Put it where you'll hand over the food. A public spot like a gate, store or plaza is fine; it doesn't have to be your home.", a map of Tagbilaran with "© OpenStreetMap contributors" in its bottom-right corner, "Tap the map to place the pin, or use your location." and "Use my location". No location prompt appears.
   - [New] "Post Listing" → "Place the pickup pin on the map."
   - [New] Pan and zoom the map to a spot **more than 5 km** from both phones (another town) and tap it: the marker appears there, the map zooms in if it was zoomed out, and the line reads "Pin placed. Tap the map again to move it." Dragging on the map moves the map, not the page.
   - [Regression] "Post Listing" → "Listing published".
2. [New, Regression] Post's Back button, then Post again. Fill in `MAP HERE` the same way (quantity 2).
   - [New] "Use my location" → Android asks for location access: choose **"Precise"** and "While using the app". The button reads "Finding your location…", then the marker appears where the phone is, the map centres there, and the line reads "Pin placed. Tap the map again to move it."
   - [New] If "Approximate" was chosen by mistake: no pin, the map centres near the phone and "Your phone shared only its approximate location, so the pin wasn't placed. Zoom in and tap the exact spot." Then turn on Settings → Apps → Salu-Salo → Permissions → Location → "Use precise location", go back, and tap "Use my location" again.
   - [Regression] "Post Listing" → "Listing published". Post's Back button.

**B (phone 2)**

3. [New] Home, signed in as B. Tap Post, then its Back button (this reloads Home).
   - No location prompt on Home, and no card shows a distance yet.
   - Tap "Nearby" → Android asks for location access → "Precise" and "While using the app" ("Approximate" also works for B; the line under the chips then reads "Distances use your phone's approximate location."). While it looks: "Finding your location…".
   - The title reads "Food within 5 km"; `MAP HERE` is listed with "Available  •  <distance> away  •  <address>" (for example "10 m away"); `MAP TAP` and `OLD` are not listed.
   - The filter button next to the search box → the first line is "Nearby: listings with a map pin within 5 km of you". OK.
4. [New, Regression] Tap "All".
   - `MAP TAP`'s card shows a distance of more than 5 km (for example "7.4 km away"), `MAP HERE` shows its short distance, and `OLD`'s card shows no distance ("Available  •  <address>").
   - "Sort: Newest" → the menu lists "Newest", "Nearest", "Name" → "Nearest": `MAP HERE` first, then `MAP TAP`, then the other pinned listings by distance, and `OLD` after every pinned listing.
   - [Regression] "Name" sorts by name. Set it back to "Newest".
5. [New, Regression] Open `MAP TAP`.
   - The Pickup Address row: the address, then "About 7.4 km away (straight line)" (the same distance as the card).
   - "View on map" → "Pickup location": under the address "About 7.4 km from you in a straight line; the route may be longer."; a map with the marker at A's pin and "© OpenStreetMap contributors" visible; pinch zooms, and a drag pans the map without scrolling the page. Tapping "© OpenStreetMap contributors" opens openstreetmap.org/copyright; come back.
   - "Directions" opens Google Maps with a route to the pin. Come back to Salu-Salo.
   - Under "Pickup instructions": "The pin shows where the provider hands over the food. If you can't find it, ask them in the chat." [Regression] "Ask for exact location" opens the chat with A with "Hi! Where exactly can I pick up MAP TAP?" filled in; close it without sending.
6. [Regression] Back, open `OLD`: the Pickup Address row has no distance line. "View on map" → no map, "Search area in Maps" (opens Google Maps with a search for the address) and "Ask the provider for the exact meeting point and collection instructions." Come back.
7. [Regression, New] Open `MAP HERE` → Reserve 1 → "Review reservation" → message `Map test` → "Confirm reservation" → the "Reserved" receipt with "Your message was sent to <A>." → Done.
   - [New] Activity → My Reservations → `MAP HERE`: "Status: Reserved", "Message donor", "Directions" and Cancel. "Directions" opens Google Maps with a route to `MAP HERE`'s pin. Come back and Close.

**A (phone 1)**

8. [Regression, New] Alerts: "New reservation" ("<B> reserved 1 pcs of MAP HERE.") and "New message from <B>" ("MAP HERE: Map test"). Open the chat and reply `OK`.
   - [New] Activity → My Listings → `MAP TAP` → "Edit listing": the map starts at the pin, zoomed in, with "Tap the map to move the pin." Tap a spot **1–3 km** from the phones → "Pin placed. Tap the map again to move it." → "Save changes" → "Listing updated." [Regression]
   - [New] Activity → My Listings → `OLD` → "Edit listing": "This listing has no pickup pin yet. Tap the map to add one. Without a pin, others see no map or distance." Cancel without tapping the map (`OLD` keeps no pin).

**B (phone 2)**

9. [New, Regression] Tap Post, then its Back button (this reloads Home), then "Nearby": `MAP TAP` is now listed with its new distance (1–3 km), together with `MAP HERE`. Open `MAP TAP` → "View on map": the marker is at the new spot. [Regression] The chat icon → the `MAP HERE` chat shows A's `OK`.
10. [New] Android Settings → Apps → Salu-Salo → Permissions → Location → "Don't allow". Android closes Salu-Salo when a permission is removed: reopen it, and sign in again if asked.
    - Home: no distances on any card, and no prompt.
    - Tap "Nearby" (if Android asks again, choose "Don't allow"): "Allow location to use Nearby and Nearest: Settings → Apps → Salu-Salo → Permissions → Location." and no cards.
    - "All", then "Sort: Newest" → "Nearest" ("Don't allow" again if asked): the same line, and the order stays newest first.
    - Open `MAP TAP` → "View on map": the map, the marker and "Directions" still work; there is no distance line.

**A (phone 1)**

11. [New] Turn off location in the phone's quick settings.
    - Home → Post → "Use my location" → "Your phone's location is turned off. Turn it on, or tap the map to place the pin." Tap the map → "Pin placed. Tap the map again to move it." Post's Back button without posting.
    - Home → "Nearby" → "Your phone's location is turned off. Turn it on to use Nearby and Nearest."
    - Turn location back on and tap "Nearby" again: the line disappears and the listings within 5 km show their distances.
12. [Regression, New] Activity → My Donations → B's `MAP HERE` reservation → "Mark claimed" → "Reservation marked as claimed." On B (phone 2): the "Reservation claimed" alert; Activity → Claims shows `MAP HERE`, and its record has no "Directions" [New]; My Reservations no longer lists it.

**Left out of the short test (not tested unless run separately):**
- Android 11 and older (no Precise/Approximate choice); a phone without Google Maps (the `geo:` fallback) or without any maps app or browser ("No maps app is available on this device.").
- "Your location could not be found. …" (needs a phone that finds no position within 20 seconds); a fix less precise than 100 m with Precise allowed.
- The map offline (blank map, the rest of the page works), the 50 MB tile cache, and Android clearing the cache.
- Rotating the phone while placing a pin; TalkBack ("Map for the pickup pin. Tap to place the pin.", "Map of the pickup pin for <listing>"); dark mode; a small screen.
- An older build after the migration (it posts without a pin, and its edits keep the pin) and the function's refusals on a phone: covered only by the SQL checks.
- Adding a pin to an older listing on Edit (step 8 only opens Edit); moving a pin by "Use my location" on Edit.
- Home returning from the background with a new position; sign-out clearing the position.
- The regressions not in the steps: Cancel, removing a listing, editing other fields, Saved Listings, reviews and reports, notification settings and sound, the response rate, the profile, the password change, email sign-up and the password-reset email.

### 15.5 Known limits

- **Tiles:** the free `tile.openstreetmap.org` servers are fine for testing and a small user base. Before a large public release, consider a tile provider meant for apps; such providers usually have a free tier and a key, which would stay out of the repository like the Supabase keys (change `MAP_TILES` in `PickupMap.kt`). The OpenStreetMap Foundation may block traffic that degrades its service without notice.
- Distances are straight lines; the route may be longer. The cards say "1.2 km away" without "about" for space.
- The phone's position is refreshed only when Home opens, when the app returns, and when Nearby or Nearest is chosen; it doesn't follow the phone while Home stays open.
- Only listings with a pin have a map, a distance or a place in Nearby; older listings get one only when their donor edits them. A pin can be moved but not removed.
- Everyone who can see the listing sees the exact pin, by design; the note on Post and Edit says so.
- "Use my location" places the pin only from a precise fix (100 m or better); otherwise the donor taps the map.
- Nothing is live: others see a new or moved pin only after their Home reloads (sign-in, Post → Back, a reservation).
- Without a connection the map is blank. Tiles are cached in the app's cache folder, which Android may clear.
- Directions need Google Maps, another maps app or a browser. Android stops showing the permission prompt after the user refuses twice; then only Settings can allow it.
- osmdroid 6.1.20 (August 2024) is the latest release; the project is maintained slowly.
- The migration must run before the new build is installed: without it, Post Listing and Save changes fail with the server's short error.

### 15.6 Results

- **Build:** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` at `47ed894`: BUILD SUCCESSFUL in 3m 43s on 2026-09-28 (online once, to download osmdroid 6.1.20), with only the three old deprecation warnings; `GeoTest` 11 of 11 passed through Gradle. The developer installed it from Android Studio on both phones.
- **osmdroid checks (javap on the 6.1.20 classes):** without `Configuration.load()` osmdroid has no "normalized" User-Agent, so its tile downloader falls back to the app's own value (`SaluSalo/<versionName> (Android; com.example.salusalo)`), which the tile policy accepts. osmdroid adds no permission to the merged manifest, only optional `uses-feature` entries.
- **Migration:** `20261003_phase5_maps.sql` applied to the live project on 2026-09-28 in the SQL Editor, with `supabase/tests/phase5_maps_check.sql` in the same run: 11 of 11 PASS ([results log](#results-log)).
- **Live safety check:** `supabase/tests/phase5_maps_live_safety_check.sql` on 2026-09-28: 22 passed, 0 failed, 0 skipped, rolled back by its closing error. The donor was the donor of the newest listing and the outsider the newest other account (the two test accounts).
- **Two-phone test:** §15.4 steps 1–12 passed on 2026-09-28, sent in two parts (steps 1–7, then 8–12).
- **Not tested:** the list under "Left out of the short test" in §15.4. Lint was not run.
- **Merged** into `master` through PR #11 on 2026-09-28 (merge commit `255b0f8`).

---

## 16. Profile redesign and settings (Mika's PR #10)

**Status (2026-09-28): the 6-step two-phone test passed and the migration is applied (check 6 of 6); merged into `master` through PR #10 on 2026-09-28 (merge commit `49ac319`).** Written by Mika S on branch `mika/navigation-activity-settings` (commit `7888309`, from `master` at `711cc5e`). Before the merge, Claude merged `master` (Maps) into her branch and fixed its one conflict (`308a499`), then changed one sentence and the migration (`b2b04ca`).

### 16.1 What changed

- **Profile** (`ProfileScreen.kt`):
  - a larger photo with the camera button, and the name with a pencil that opens Edit Profile;
  - the bio under the name, or "Add a little about yourself using the pencil." when it is empty;
  - Claims, Donations and Rating; Verification Status; Ratings & Reviews; Saved Listings;
  - "Response: …%" and "Member since …" at the bottom, and a Log out button.
  - The bell is gone: Alerts is always in the bottom bar.
- **Edit Profile** (`ProfileSettingsUi.kt`): the display name and "About me" (at most 160 characters), saved with `update_my_profile_details` ("Profile saved to your account."). The photo can also be changed there.
- **Settings** (the gear): Notifications, Food preferences, Location & distance, Account & security, Help & food safety and About Salu-Salo, with the "How Salu-Salo works" and "Reservations & pickups" help pages. The old account-name editor is gone.
- **Food preferences and pickup area** (`DiscoveryPreferences.kt`):
  - food categories, Both/Donations/For sale, and a text that must appear in the pickup location;
  - saved per account, on this phone only;
  - while any is set, Home's "Newest listings" row becomes "For your preferences"; the full feed below is unchanged.
- **Bottom bar:** the second button is always Alerts (on Activity it used to be Browse).
- **Changed by Claude before the merge:**
  - the "About distance" note on Location & distance now points to the map distances and Nearby; it used to say that distances were not available yet;
  - the migration was renamed from `20261002_profile_bio.sql` to `20261004_profile_bio.sql`, so it sorts after the Maps file, and it got `users_bio_length_check`.

### 16.2 Migration `20261004_profile_bio.sql`

- `users.bio text` (nullable) and the check `users_bio_length_check` (at most 160 characters), which also covers a profile's first insert.
- SECURITY DEFINER `update_my_profile_details(p_name, p_bio)`:
  - takes the caller from the token and changes only their own row;
  - the name must have 1–120 characters and the bio at most 160 (blank is stored as NULL);
  - executable by `authenticated` only;
  - never changes `role`, `email` or `firebase_uid`.
- `update_my_profile_name` is unchanged, so older builds keep working.
- Only the owner sees the bio, because `users` rows can be read only by their owner.
- It must run before the new build is installed; without it, Save changes on Edit Profile fails.
- Read-only check: `supabase/tests/profile_bio_check.sql` (6 checks).

### 16.3 Results

- **Build:** `gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline` at `b2b04ca`: BUILD SUCCESSFUL in 36s on 2026-09-28, with only icon deprecation warnings; `GeoTest` 11 of 11. The developer installed it from Android Studio on both phones.
- **Migration:** applied to the live project on 2026-09-28, with `supabase/tests/profile_bio_check.sql` in the same run: 6 of 6 PASS.
- **Two-phone test (6 steps, 2026-09-28): passed.** Covered:
  - A: the new Profile layout;
  - A: Edit Profile, saving a new name and a bio ("Profile saved to your account.");
  - A: the Settings hub, and Food preferences turning Home's row into "For your preferences" and back;
  - A: the new "About distance" note;
  - A: Alerts in the bottom bar on Activity, and Nearby distances;
  - B: A's new name on the cards and the Donor row;
  - B: the new Profile, Log out and signing in again.
- **Not tested:**
  - the photo change on Edit Profile;
  - the pickup-area filter itself;
  - the 160-character limit;
  - Account & security, Help & food safety and About;
  - email verification from the new layout;
  - a Google account's password page.

### 16.4 Known limits

- Food preferences and the pickup area are saved on the phone, per account; they don't follow the account to another phone.
- The pickup-area filter matches the typed text in pickup locations, not a distance; Nearby does that.
- Only the owner sees the bio; other people's pages don't show it.

---

## 17. Fixes, clean-up and optimization after the second demo audit

**Status (2026-09-28): in code on branch `claude/cleanup-after-audit` (from `master` at `49ac319`, the PR #10 merge); not built, not tested.** No SQL, no database change and no new library. Only local checks were run (§17.3): no AGP build, no lint and nothing on a phone. The two-phone test is §17.4.

Commits: `2404989` (the PR #10 merge in the docs), `9281f61` (photo tile, unused images), `5a1b90d` (signing and reservations), `2f4d424` (Post), `2a05a22` (Home and details), `8dbac5e` (keyboard), `e10894d` (welcome pages), `0bfca3f` (clean-up and icons), `5034262` (WebP), then this document.

### 17.1 The audit

| # | Item | Outcome |
|---|---|---|
| 1 | Stock photo `food_produce.png` shown for listings | Replaced by a neutral tile (`ListingPhoto`): plain for no photo, "Image unavailable" when a stored photo can't be shown. The `photo: Int` fields and parameters are gone. |
| 2 | "Recommendation insights" claimed matching that doesn't exist | Now "Check your listing", a full page of form checks (§17.2). |
| 3 | "My posts" showed the expiry in UTC | Shown in the phone's time zone. |
| 4 | Filter button looked like a filter; Expiring Soon line vague | An info icon, "About the filters"; "Expiring Soon: listings that expire within 3 hours". |
| 5 | Details "Read more" added a generic paragraph | The description shows 4 lines; Read more only when it is longer; the paragraph is removed. |
| 6 | Home "Learn more" showed one sentence | Opens "How Salu-Salo works". |
| 7 | Welcome pages 2–3: placeholder descriptions and TODOs | Real descriptions; TODOs removed. |
| 8 | Mika: a quick way to start pickup now | "Start now" on Post and Edit, with 10 minutes of slack on Post. |
| 9 | Mika: allergens required | Required on Post and Edit. |
| 10 | Mika: Home leaf icon | The profile photo or initial; opens Profile. |
| 11 | Mika: full-screen photos | Tap a details photo to see all photos full screen (no pinch zoom). |
| 12 | Mika: summary boxes misaligned | Four equal columns with aligned captions. |
| 13 | Mika: chat box behind the keyboard | Fixed, and the same fix on the other full-screen pages with text boxes (§17.2). |
| 14 | Unused images | The five `food_*.png` files (13.8 MB) deleted. |
| 15 | Dead code | Removed (§17.2). |
| 16 | Deprecated icons | `Icons.AutoMirrored.Outlined.*` in the 7 places. |
| 17 | One signing request per photo | One request per list, with a per-photo fallback. |
| 18 | Reservations loaded every visible listing | Only the reserved listings, by ID. |
| 19 | Large PNG illustrations | WebP: 892 KB → 78 KB and 535 KB → 49 KB. |
| 20 | Welcome pages on every start | Only until this phone has seen them. |
| 21 | No way to reload Home | Pull to refresh. |

### 17.2 What changed

- **Photos** (new `ListingPhotos.kt`): `ListingPhoto(imageUrl, hasPhoto, …)` is used by the Home cards, the details pager, the Pickup location and Donor pages, the Review reservation page, the chat header, Saved Listings and Activity records. No stored photo: a plain pale tile. A stored photo that can't be signed or loaded: "Image unavailable" (an icon with that description on tiles narrower than 60 dp). Activity records now carry `hasPhoto`, so they show "Image unavailable" too. Saved Listings' card for a listing you can no longer see still shows a plain tile.
- **Signing** (`data/FoodRepository.kt`): `List<FoodPost>.withSignedImageUrls(bucket)` and `BucketApi.signedUrls(paths)` sign a whole list in one `createSignedUrls` request (1-hour URLs), matched by path. supabase-kt 3.5.0 can't read the answer when any path can't be signed (Storage returns `"signedURL": null`), so then each photo is signed on its own as before. The exception is never shown or logged, because it can contain signed URLs. Used by the feed, My Listings, photos 2–4, both reservation lists, Saved Listings (visible covers in one batch, covers of listings you can no longer see in another) and the Messages inbox. Single items (create, edit, one conversation, the profile photo) keep one request each.
- **Reservations** (`data/ReservationRepository.kt`): both lists load only their listings, with `id=in.(…)` in chunks of 100; RLS decides exactly as before.
- **Post** (`PostScreen.kt`, `model/FoodPost.kt`):
  - "Check your listing" panel ("See the price, pickup window and missing details before you post.", button "Check listing") opens a full page "Check your listing": "Based on the details you've entered so far. Nothing is posted until you tap Post Listing.", then Price, Quantity, Pickup window, Expiration, Location and Details to review, and "These are form checks and calculations, not AI predictions or a food-safety assessment." The category section and the matching claims are gone.
  - "Start now" beside "Pickup starts" (Post and Edit) sets the current minute. Post accepts a start up to 10 minutes in the past through one pure rule, `isPickupStartAllowed()`, which the check page also uses. Errors: "Choose when pickup starts, or tap Start now." and "The pickup start is in the past. Tap Start now, or choose a later time."
  - Allergens: label "Allergens", error `Enter the allergens, or write "None".` on Post and Edit (so an older listing without allergens must get them to be saved), hint `List allergens such as wheat, milk, eggs or nuts, or write "None" if there are none.` Storage notes stay optional; the details page still shows "Not provided — ask the donor" when allergens are blank.
  - "My posts": "Expires: Sep 29, 2026 • 6:00 PM" in the phone's time zone, or "Expires: —".
- **Home and details** (`MainActivity.kt`):
  - The info icon ("About the filters") opens a dialog titled "About the filters": "Nearby: listings with a map pin within 5 km of you", "Donation: free food", "For Sale: paid food", "Expiring Soon: listings that expire within 3 hours", then "Tap a category below to filter the listings."
  - "Learn more" opens "How Salu-Salo works" (the Settings help text).
  - The header circle shows the profile photo (its signed URL keyed on the stored path, so a new photo shows as soon as it is saved on Profile), else the name's initial, else a person icon; tapping it opens Profile ("Your profile" for TalkBack).
  - Pull down on Home or "All food listings" reloads the feed and, only if location access is already allowed, the phone's position. It never asks for access. The spinner also shows while Home first loads.
  - Details: the description shows 4 lines, with "Read more" / "Read less" only when it is longer; the four summary columns are equal, as tall as the tallest, with the value at the top (up to 2 lines) and the caption at the bottom; tapping a photo opens a black full-screen view (whole photos, swipe, "1 / 2", "Close photos"), which returns to the photo you were viewing.
- **Keyboard** (`FoodActions.kt`: `EdgeToEdgeDialog`, `DarkSystemBarIcons()`): full-screen dialogs used the default `decorFitsSystemWindows`, so their window never got the keyboard's size and `imePadding()` did nothing. The four full-screen pages with text boxes now draw edge to edge, with dark status-bar and navigation-bar icons, the header clear of the status bar and the bottom clear of the navigation bar:
  - the chat (`ChatScreen.kt`), which also keeps the newest message in view while the keyboard opens;
  - `ActionPage` (the Reserve note on Review reservation, Report, Rate);
  - Edit Listing (`EditListingDialog`);
  - the Profile pages dialog (`ProfileSections.kt`: Edit Profile, Change Password, the pickup area).
- **Welcome** (`MainActivity.kt`): the app opens on the welcome pages only until this phone has seen them (`welcome_seen` in `salusalo_session`), set by Get Started, the new "Already have an account? Sign in" link under the button, or any account reaching Home; a saved "Keep me signed in" choice (an older build was used) counts as seen. Otherwise it opens on Sign In, where Back closes the app; Back on Create Account and Forgot password goes to Sign In. The three images are described as "People sharing a box of vegetables and bread".
- **Removed:** the five `food_*.png` images; `clearLocalAccountData()`; the Activity statuses Pending, Confirmed and Saved locally; `ProfileToggle` and `ProfileStat`; `ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`) and `res/values/colors.xml` (`themes.xml` keeps `Theme.SaluSalo`); `UserProfile.phoneNumber`; `UserRepository.updateMyName()` (the SQL function stays for older builds); `FoodViewModel.clearError()`; an unused local on Review reservation; Home's "Salu-Salo" message dialog and the unreachable bottom-bar branch.
- **Icons:** ArrowBack, Logout, FormatListBulleted, HelpOutline and MenuBook use `Icons.AutoMirrored.Outlined`.
- **Illustrations:** `welcome_reference` and `signin_reference` are lossy WebP at quality 90 (both PNGs were fully opaque); a side-by-side check at 1:1 and 3× zoom showed no visible difference.

### 17.3 Local checks (not a build or a phone)

- **Kotlin:** the kotlinc 2.2.10 type check of every app source (as in §15.3): 0 errors, and no deprecation warnings left. Compose was checked against Compose Multiplatform 1.7.3 desktop jars (material3 1.3), not the project's Compose BOM 2026.02.01 (material3 1.4.0): Google's Maven repository is not reachable from the session. New stand-ins for Android-only APIs: `DialogProperties(usePlatformDefaultWidth, decorFitsSystemWindows)`, `DialogWindowProvider`, `LocalView`, `WindowCompat.getInsetsController` and `WindowInsetsControllerCompat` (`core-ktx` is already a dependency).
- **JVM tests** (JUnit 4.13.2 run directly, not through Gradle): 26 of 26 passed: `PhotoSigningTest` 8 (one request per list, matching by path, a refused path losing only its own picture, the per-photo fallback, cancellation, nothing to sign, each listing's own URL, and a decode test showing that supabase-kt 3.5.0 can't read a batch answer with a null URL), the new `PickupStartTest` 6 (the 10-minute rule), `GeoTest` 11, `ExampleUnitTest` 1.
- **Other:** a search that no removed name is still referenced; a secret scan of the diff.
- **Not done:** the AGP build, lint, `testDebugUnitTest` through Gradle, and anything on a phone.

### 17.4 Manual test (A → B → A; phone 1 = A, phone 2 = B)

**Preconditions:**
- This branch built with AGP (`assembleDebug testDebugUnitTest`) and installed on both phones. There is no migration.
- A has an older listing, available and unexpired, whose details page shows "Not provided — ask the donor" under Allergens (for example `OLD` from §15.4); below it is called `OLD`.
- A has a second photo ready to set as the profile photo.

Each check is tagged [New] or [Regression]. Home reloads by pulling the list down.

**A (phone 1)**

1. [New] Home, signed in as A.
   - The circle at the top left shows A's profile photo (or A's initial). Tap it → Profile. Change the photo with the camera button, then tap Home in the bottom bar: the circle shows the new photo.
   - The info icon next to the search box → "About the filters" with the five lines of §17.2 → OK.
   - "Learn more" → "How Salu-Salo works" with "Share food", "Find and reserve" and "Coordinate and collect" → Back.
2. [New, Regression] Post. Fill in `AUDIT`: "I want to donate", **2 photos**, a description of about 400 characters (8 lines or more), quantity 3, unit pcs, a pickup location, the pin, expiring tomorrow at 6:00 PM, pickup ends later today, **Pickup starts and Allergens left empty**.
   - The panel reads "Check your listing" and "See the price, pickup window and missing details before you post." "Check listing" → the page "Check your listing": "Based on the details you've entered so far. Nothing is posted until you tap Post Listing.", Price "Free donation. Recipients will not pay for the food.", Quantity "Available: 3 pcs.", Pickup window "Select both pickup start and end times.", Details to review "• 2 photo(s) selected." and `• Allergens are missing. Write "None" if there are none.` → Back.
   - "Post Listing" → "Choose when pickup starts, or tap Start now."
   - Set Pickup starts to today, more than 10 minutes ago → "Post Listing" → "The pickup start is in the past. Tap Start now, or choose a later time." "Check listing" shows the same sentence under Pickup window → Back.
   - "Start now" → the button shows the current time. "Post Listing" → `Enter the allergens, or write "None".` The hint under the fields reads `List allergens such as wheat, milk, eggs or nuts, or write "None" if there are none.`
   - [Regression] Allergens `Wheat` → "Post Listing" → "Listing published".
   - "My posts" → `AUDIT`: "Expires: <tomorrow's date> • 6:00 PM" (the phone's time, not UTC). Post's Back button.
3. [New, Regression] Activity → My Listings: `AUDIT` and `OLD` show their photos. `OLD` → "Edit listing": the field is labelled "Allergens" and is empty, and "Start now" is beside "Pickup starts".
   - "Save changes" → `Enter the allergens, or write "None".`
   - Tap the Allergens field: the keyboard opens, the field scrolls into view above it, and the "Edit Listing" header stays below the status bar. Type `None` → "Save changes" → "Listing updated."

**B (phone 2)**

4. [New] Settings → Apps → Salu-Salo → Storage → Clear storage (this signs B out), then open Salu-Salo.
   - The first welcome page, with "Already have an account? Sign in" under "Next". Tap it → Sign In. Press Back: the app closes.
   - Open it again: Sign In at once, with no welcome pages. [Regression] Sign in as B.
5. [New, Regression] Home: the circle shows B's photo or initial. Pull the list down: a spinner, then `AUDIT` appears with its photo.
   - Open `AUDIT`: the description shows 4 lines ending in "…" and "Read more" → the whole text and "Read less", with no extra paragraph.
   - The four summary columns (quantity, Free, expiry, status) have equal widths, and "Available", "Donation", "Expires" and "Status" sit on one line.
   - Tap the photo: black full screen, the photo whole, "1 / 2". Swipe → "2 / 2". "Close photos" → the details page shows the second photo.
   - [Regression] Bookmark `AUDIT`.
6. [New, Regression] Reserve 1 → "Review reservation" → tap the message box: it stays above the keyboard. Type `Audit test` → "Confirm reservation" → the "Reserved" receipt with "Your message was sent to <A>." → Done.
   - The chat icon → Messages: the `AUDIT` thread shows the listing's photo. Open it and tap the message box: the box and `Audit test` stay above the keyboard, and the header stays below the status bar. Send `Hello`.
7. [New, Regression] Activity → My Reservations → the `AUDIT` record shows its photo. Profile → Saved Listings → `AUDIT` shows its photo.

**A (phone 1)**

8. [Regression, New] Alerts: "New reservation" ("<B> reserved 1 pcs of AUDIT.") and "New message from <B>". Activity → My Donations → the `AUDIT` record shows its photo → "Mark claimed" → "Reservation marked as claimed."
9. [Regression, New] Home → system Back: A is signed out and Sign In shows (no welcome pages). Back again: the app closes. Open it and sign in as A: Home.

**Left out of the short test (not tested unless run separately):**
- "Image unavailable" (needs a photo missing from Storage or one that can't be signed), the per-photo fallback that follows, and a plain tile for a listing without a photo (new listings always have one).
- A start between 1 and 10 minutes in the past; "Start now" on Edit; the Price, Expiration and short-window lines of the check page.
- A description of 4 lines or fewer (no Read more); rotating the phone in the full-screen view; TalkBack ("Your profile", "About the filters", "Close photos", "Image unavailable").
- The keyboard on Report, Rate, Edit Profile, Change Password and the pickup area; three-button navigation; the dark bar icons on each page.
- Pull to refresh with location allowed (distances refresh); "All food listings".
- Welcome pages on a phone that used an older build (`keep_signed_in` saved) and after restoring a backup; Back on Create Account and Forgot password.
- More than 100 reservations (a second chunk); right-to-left languages (the mirrored icons).
- The regressions not in the steps: email sign-up, Google sign-in, Cancel, removing a listing, editing other fields, reviews and reports, notification settings and sound, the password change, the maps pages and the password-reset email.

### 17.5 Known limits

- Allergens are required only in this build. The server and older builds still accept blank allergens, and the details page shows "Not provided — ask the donor" for them.
- The 10-minute slack for the pickup start is checked on the phone only; the server doesn't check the start at all.
- A list whose photos include one that can't be signed costs one request per photo, as before this change (supabase-kt 3.5.0 can't read the batch answer). If a later supabase-kt reads it, the fallback can go.
- The welcome "seen" flag is per phone. It survives sign-out and is restored with the app's Android backup; clearing the app's storage shows the welcome pages again.
- Pull to refresh reloads only the feed and the phone's position, not alerts, messages or saved listings.
- The full-screen photo view has no pinch zoom.
- Phones that ran older builds keep the unused `salusalo_profile` and `salusalo_local_posts` files; nothing reads them.
- The keyboard fix depends on edge-to-edge dialogs, which were only type-checked here.
