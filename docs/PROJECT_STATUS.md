# Salu-Salo — Project Status

_Last updated: 2026-09-25 (Phase 4 signed off, §8; Purchases, Claims and listing prices in code, not yet built or tested, §9). Based on the code at commit `d4f65fe` on `master`, plus the image changes described in §3.2 and §3.3 and the load-failure fixes in §3.4, which were committed together with this document on 2026-09-25, and the known-issue fixes in §3.5 (commit `85c9263`). Manual test results reported on 2026-09-25 are in the [results log](#results-log)._

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
| Activity: Purchases, Claims, listing prices (between Phases 4 and 5; not part of Phase 5) | Stored listing prices, the price locked into each reservation, real Purchases and Claims tabs, real Activity totals (§9) | **Implemented in code on 2026-09-25. Not built with AGP, migration not applied, not tested on a device.** The SQL was tested on a local Postgres 16; the Kotlin was type-checked (§9.5). |
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
  - The Google, Apple and Facebook buttons only show a message.
  - "Keep me signed in" has no effect.
  - Profile name/location edits, the profile photo and settings are saved only to local SharedPreferences. Reviews are sample data.
  - Only the displayed profile **name** comes from Supabase.
  - `StorageRepository.profileImagePath()` is unused.

### Phase 2: what exists (summary)

- **Create listing.** `FoodRepository.createFoodPost()` uploads the image to `food-images/<firebaseUid>/<postId>/image.<ext>` with `upsert = false`, then inserts the `food_posts` row with `image_url = <storage path>`. If the insert fails, the uploaded image is deleted.
- **Feed.** `getAvailableFoodPosts()` returns posts with `status = AVAILABLE` and `expiration_date > now`.
  - Each post gets a 1-hour signed URL in `displayImageUrl`.
  - The Home list (`AvailableFoodCard`) shows it with `AsyncImage`.
- **Own posts.** `getUserFoodPosts()` feeds PostScreen → "My posts" and Activity → My Listings, both with Delete. Delete removes the row, then the image.
- **Not part of the implemented scope:**
  - **No edit UI.** `FoodRepository.updateFoodPost()` exists but is unused.
  - **Only the first selected photo is uploaded**, even though up to four can be picked.
  - **Form fields that are collected but not saved:** price ("I want to sell"; saved since §9, not yet tested), pickup start/end, distribution mode, partial claims and maximum per recipient, allergens, storage notes.
  - **Hard-coded display values:** every listing shows "Free", provider "Food donor", rating "New", and uses a fixed distance of 0 for the "Nearby" filter.

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
  - `FoodDetailsScreen` calls `onReserved()`, which reloads the feed and closes the dialog (L1960–1963, L2279–2287).
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

- **Build:** the test phone's app code was deployed on 2026-09-25 at 10:37 (+08:00), after the last source change (10:04), so it matches the working tree above. That same code was committed on 2026-09-25, with no source changes after 10:04. The build on any second device was not checked.
- **Phase 4 retest build:** `c7323b8`, built at 21:21 (+08:00) with no local source changes and installed from Android Studio. A's phone showed the new "Loading your alert settings…" text, so it ran this build; which build the second phone ran was not checked.
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
| Food post model | `app/src/main/java/com/example/salusalo/model/FoodPost.kt` | `FoodPost` (with `@Transient displayImageUrl`), `NewFoodPost`, `FoodPostDraft`, `FoodPostStatus` |
| Reservation model | `app/src/main/java/com/example/salusalo/model/Reservation.kt` | `Reservation`, `ReservationStatus`, `ReservationDisplay`, `DonorReservationRow` |
| Food repository | `app/src/main/java/com/example/salusalo/data/FoodRepository.kt` | Feed, own posts, create/delete, `withSignedImageUrl()` |
| Reservation repository | `app/src/main/java/com/example/salusalo/data/ReservationRepository.kt` | RPC calls, `getMyReservations()`, `getReservationsForMyFoodPosts()` |
| Reservation ViewModel | `app/src/main/java/com/example/salusalo/viewmodel/ReservationViewModel.kt` | `myReservations`, `donorReservations`, `message`, `reserve` / `cancel` / `claim` / `refresh` |
| Food ViewModel | `app/src/main/java/com/example/salusalo/viewmodel/FoodViewModel.kt` | `availablePosts`, `myPosts`, `createPost`, `deletePost` |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/UserRepository.kt` | Find-or-create profile; `ProfileException` |
| Auth / profile | `app/src/main/java/com/example/salusalo/model/UserProfile.kt` | `UserProfile`, `UserRole`, `NewUserProfile` |
| Auth / profile | `app/src/main/java/com/example/salusalo/data/SupabaseClientProvider.kt` | Supabase client using the Firebase ID token |
| Auth / profile | `app/src/main/java/com/example/salusalo/ProfileScreen.kt`, `ProfileSections.kt` | Name and notification preferences from Supabase; everything else local or demo |
| Storage paths | `app/src/main/java/com/example/salusalo/data/StorageRepository.kt` | `foodImagePath()`, unused `profileImagePath()` |
| Create listing | `app/src/main/java/com/example/salusalo/PostScreen.kt` | Create Listing form, "My posts", `PostPhoto` |
| Messaging (Phase 4) | `ChatScreen.kt`, `data/MessageRepository.kt`, `data/RealtimeInserts.kt`, `model/Conversation.kt`, `viewmodel/MessageViewModel.kt` | Chat dialog and Messages inbox; RPC calls; Realtime insert stream |
| Alerts (Phase 4) | `AlertScreen.kt`, `data/NotificationRepository.kt`, `model/AppNotification.kt`, `viewmodel/NotificationViewModel.kt` | Real alerts, unread counts, preferences |
| Demo screens | `app/src/main/java/com/example/salusalo/FoodActions.kt` | Preview-only pickup and checkout dialogs; its demo chat is now only a fallback |
| Theme | `app/src/main/java/com/example/salusalo/ui/theme/` | `SaluSaloTheme` is **unused**; `MainActivity` builds its own `MaterialTheme` |
| Database / Storage SQL | `supabase/migrations/` (5 files), `supabase/README.md` | Applied manually in the Supabase SQL Editor |
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

- **Food details screen.** Since §3.5 (verified on 2026-09-25) the header shows the uploaded image, and the description and "Pickup Address" come from the post. Still generic: allergens (not saved), Storage Note, Pickup Window (shows the listing status), and the map address in `FoodActions.kt`. The "Distance" box shows the pickup location, because `toHomeFood()` puts `pickupLocation` in `distance`.
- **Dropped requests.** Since §3.4, feed and My Listings loads requested during another `FoodViewModel` request are queued, not dropped. Still ignored while any request is running: `FoodViewModel.createPost()`/`deletePost()`, and every `ReservationViewModel` request (for example a tab-tap reload during a running refresh).
- **No realtime updates outside Phase 4.** Since §3.5 (verified on 2026-09-25) the Home feed reloads after a cancel. Claim does not change the feed. Apart from Phase 4's open chat and alerts (§8), nothing updates in real time across devices.
- **Placeholder or hard-coded UI values:**
  - the greeting "Good evening, Mika". **Changed on 2026-09-25 (§3.5, commit `851e8cb`): it now uses the signed-in profile's first name and the time of day. Built; the evening greeting was seen in the Phase 4 test on 2026-09-25.**
  - the location "Del Rosarrio, Balilihan";
  - "Browse all 10 food listings";
  - Activity metrics 48 / 128. **Replaced in code on 2026-09-25 by real totals (§9); not yet built or tested.**
  - sample Purchases, Claims and reviews (sample Alerts were replaced by real alerts in Phase 4). **The sample Purchases and Claims were replaced in code on 2026-09-25 (§9); not yet built or tested.** Sample reviews remain.
  - The "Nearby" filter relies on a fixed distance. The "For Sale" filter matched nothing, because every live listing was "Free"; since §9 it matches listings with a price (not yet tested).
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

**Status (2026-09-25): implemented in code on branch `claude/activity-purchases-claims` (from `master` at `5eaee72`). Not built with AGP, the migration has not been applied to the live project, and nothing has been tested on a device.** This is not Phase 5: no reviews, ratings, maps, reports or online payments. Payment is always in person at pickup.

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

Preconditions: run `20260927_activity_prices.sql` in the SQL Editor (check: `select table_name, column_name from information_schema.columns where column_name in ('price','unit_price') and table_schema = 'public';` returns `food_posts.price` and `reservations.unit_price`). Build this branch and install it on both phones.

**A, part 1**
1. [Regression] Sign in as A. Home shows "Good <morning/afternoon/evening>, <A's first name> 👋".
2. [New] Post → For Sale, name `PRICE TEST`, a photo, any category, quantity `5`, unit `packs`, price `45.10`, future dates → Post Listing. Back on Home, its card shows "₱45.10".
3. [New] Post → Donation, name `FREE TEST`, a photo, quantity `4`, unit `pcs` → Post Listing. Its card shows "Free".
4. [New] Post → For Sale, price `100000` → Post Listing shows "Enter a price of at most ₱99,999.99." Leave without posting.
5. [New] Home chips: "For Sale" lists `PRICE TEST` and not `FREE TEST`; "Donation" lists `FREE TEST` and not `PRICE TEST`.
6. [New] Activity: write down Completed and Items Rescued (A0, A1). Tap Completed: "<A0> completed: your reservations that donors marked claimed, plus claimed reservations on your listings." The Purchases and Claims tabs show no sample records.

**B, part 1**
7. [New] Sign in as B. Activity: write down Completed and Items Rescued (B0, B1).
8. [New] Home → `PRICE TEST`: the price box shows "₱45.10" / "For sale". Set quantity 3: the line under Reserve reads "Pay ₱135.30 at pickup. The app takes no payments." Reserve → dialog "Reserved": "Reservation created. Pay ₱135.30 at pickup."
9. [Regression] Home → `FREE TEST`: "This is a free donation. Please be respectful and pick up on time." Quantity 2 → Reserve → "Reservation created. Please coordinate pickup with the donor."
10. [New] Activity → My Reservations: "`PRICE TEST` 3 packs • ₱135.30", status Reserved; details "Your reservation.", "Price: ₱45.10 per unit", "Pay ₱135.30 at pickup.", closing "This is your live reservation.", buttons Cancel and "Message donor". `FREE TEST`: "2 pcs • Free". Purchases and Claims are empty.

**A, part 2**
11. [New] Activity → My Donations: `PRICE TEST` "3 packs • ₱135.30"; details "Recipient: <B's name>", "Reservation for your listing.", "Price: ₱45.10 per unit", "Collect ₱135.30 at pickup.". `FREE TEST` "2 pcs • Free" with no price lines. [Regression] Alerts has two "New reservation" alerts.
12. [New] Price lock: in the SQL Editor run `update public.food_posts set price = 99 where name = 'PRICE TEST' returning name, price;` → 1 row, `99.00`. Tap My Donations again: still "₱135.30".
13. [Regression] Open `PRICE TEST` in My Donations → "Mark claimed" → "Reservation" dialog "Reservation marked as claimed.". Same for `FREE TEST`.
14. [New] Completed = A0 + 2, Items Rescued = A1 + 5.

**B, part 2**
15. [Regression] Alerts shows two "Reservation claimed" alerts ("The donor marked your reservation of PRICE TEST as claimed." and the same for FREE TEST).
16. [New] Tap the `PRICE TEST` alert: Activity opens on **Purchases** with its details open: "3 packs • ₱135.30", "Status: Claimed", "Picked up: <time>", "Price: ₱45.10 per unit", "Total: ₱135.30, paid at pickup.", "This is your live purchase, paid at pickup." Only Close and "Message donor" (no Cancel). The total is not ₱297.00, so the reserved price was kept.
17. [New] Back to Alerts, tap the `FREE TEST` alert: Activity opens on **Claims** with "2 pcs • Free", "Free food you picked up.", "This is free food you picked up."
18. [New] My Reservations no longer lists these two. Completed = B0 + 2, Items Rescued = B1 + 5.
19. [New] Home (open Post, then Back, to reload): `PRICE TEST` shows "₱99.00". Reserve 1 → "Reservation created. Pay ₱99.00 at pickup." [Regression] Activity → My Reservations → it → Cancel → "Reservation cancelled. The quantity is available again."; it stays in My Reservations as Cancelled, "1 packs • ₱99.00".
20. [Regression] Purchases → `PRICE TEST` → "Message donor" → send "Thanks!". On A's phone, the chat badge goes up without refreshing; A opens the thread and replies; B sees the reply live.

**A, part 3**
21. [Regression] Alerts has "Reservation cancelled" for `PRICE TEST`; tapping it opens My Donations.
22. [Regression] Delete `PRICE TEST` and `FREE TEST` in Activity → My Listings; they leave Home and My Listings. Sign out, sign in again.

### 9.5 How this was checked (2026-09-25)

- **SQL:** all migrations run in order on a local PostgreSQL 16 with stand-ins for Supabase's roles and `auth.jwt()`, then `20260927_activity_prices.sql` run twice more without errors. A two-account test confirmed: the price check rejects −1 and 100000; a donor's own insert saves a price; `create_reservation` returns `unit_price` 45.10 for a priced listing and 0.00 for a free one; clients still cannot insert reservations; after the donor changed the price to 99 the earlier reservation kept 45.10 and a new one got 99.00; a directly supplied `unit_price` is overwritten; `get_donor_reservations()` returns names and `unit_price`; claim and cancel still work and still create the Phase 4 alerts; clients cannot execute the trigger function; `anon` cannot execute `get_donor_reservations()`. Running `20260924_phase3_donor_recipient_names.sql` afterwards fails with "cannot change return type of existing function", as documented.
- **Kotlin:** all app sources compiled with kotlinc 2.2.10 and the Compose and serialization plugins against the supabase-kt 3.5.0 JVM jars and Compose desktop jars, with small stand-ins for Android and Firebase APIs: no errors; the only warning is the deprecated `FormatListBulleted` that was already there. A small JVM check: `formatPeso(45.1)` = ₱45.10, 0.1 + 0.2 = ₱0.30, `reservationTotal(45.1, 3)` = ₱135.30, `NewFoodPost` encodes `"price":0.0`, and `unit_price: 45.10` decodes. This is not an Android build.
- **Not done:** AGP build, live migration, any device test (§9.4).
