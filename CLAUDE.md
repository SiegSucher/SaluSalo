# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Salu-Salo** is an Android app for sharing surplus food within a community to reduce food waste. Donors post surplus food with a photo, quantity, pickup location and expiry. Recipients browse the available listings and reserve part or all of the quantity for pickup. The UI uses ₱ and Philippine sample locations.

- **Kotlin + Jetpack Compose (Material 3).** Single `MainActivity`, single `:app` module, package `com.example.salusalo`.
- **Firebase Authentication.** Email/password sign-up, sign-in, password reset and sign-out.
- **Supabase.** Postgres (via PostgREST and RPCs) plus Storage, through supabase-kt 3.x on Ktor. Images load with Coil 3 `AsyncImage`.
- **Supabase trusts Firebase via Third-Party Auth.** `data/SupabaseClientProvider.kt` sends the current Firebase ID token as the Supabase access token.
  - Every Firebase user needs the custom claim `role: authenticated`. A trusted Admin SDK or Cloud Function sets it; that code is **not in this repo**.
  - SQL checks tokens with `is_salusalo_firebase_jwt()`, which requires issuer and audience = Firebase project `salusalo`.
- **Storage.** The existing private bucket **`food-images`** holds listing photos. The `profile-images` bucket exists but the app does not use it yet.

**Roadmap: six phases.** Phases 1–3 are complete and manually verified (2026-09-25); do not redo them. No phase is in progress.
- Phase 1 — Backend Foundation: complete.
- Phase 2 — Food Listings: complete.
- Phase 3 — Reservations: complete.
- Phase 4 — Messaging and Notifications: **not started** (next). Its scope is known only by this title. Do not begin it until the user asks.
- Phase 5 — Maps, Reviews, Reports: not started.
- Phase 6 — Admin and Final Release: not started.
- Read `docs/PROJECT_STATUS.md` first. It has the detailed status, the test results and the two-account manual test.

## Commands

Run these from the repo root. Toolchain: Gradle wrapper 9.4.1, AGP 9.2.1, Kotlin 2.2.10, JDK 21, compileSdk 37, minSdk 24.

```bash
./gradlew assembleDebug                  # build debug APK (Windows: gradlew.bat assembleDebug)
./gradlew installDebug                   # install on a connected device/emulator
./gradlew testDebugUnitTest              # JVM unit tests
./gradlew testDebugUnitTest --tests "com.example.salusalo.ExampleUnitTest"   # single test class (or Class.method)
./gradlew connectedDebugAndroidTest      # instrumented tests (needs device/emulator)
./gradlew lintDebug                      # Android lint
```

- On the current Windows dev machine, `java` is not on PATH. The build verified on 2026-09-25 used PowerShell:
  `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"; .\gradlew.bat :app:assembleDebug --offline`
- `local.properties` is git-ignored and must define `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`. Both become `BuildConfig` fields, and `SupabaseClientProvider` throws if either is blank. Never print, copy or commit their values.
- `app/google-services.json` (Firebase project `salusalo`) is committed.
- Database changes are SQL files in `supabase/migrations/`. They are applied **manually**, in filename order, in the Supabase SQL Editor (see `supabase/README.md`). There is no Supabase CLI config, and nothing in the repo shows which migrations the live project has.

## Architecture

Paths below are relative to `app/src/main/java/com/example/salusalo/`.

**Navigation uses plain Compose state. There is no Navigation library and no DI.**
- `SaluSaloApp()` in `MainActivity.kt` holds `screen` (`welcome | signin | signup | forgot | home`).
  - It runs Firebase sign-in, sign-up and reset, then `UserRepository.loadOrCreateCurrentProfile()` before entering `home`.
  - System Back on the Home page calls `logOut()`.
- `HomeScreen()` (also in `MainActivity.kt`) holds `page` (`home | recommended | all | alerts | post | activity | profile`) plus the bottom bar.
  - Food details open as a full-screen `Dialog` (`FoodDetailsScreen`).
  - It creates the ViewModels with `viewModel(key = "food-$profileId")` / `"reservation-$profileId"`, so each account gets its own instances. They live in the Activity's `ViewModelStore` and are reused if the same account signs in again; `clearSession()` empties their lists when `HomeScreen` is disposed.

**Layers**
- `data/`: plain repository classes that use the `SupabaseClientProvider.client` singleton.
  - `UserRepository` finds or creates the caller's `users` row, keyed by `FirebaseAuth.currentUser.uid`.
  - `FoodRepository` handles `food_posts` and image upload. `withSignedImageUrl(bucket)`, a top-level function in `FoodRepository.kt`, turns the stored path into a 1-hour signed URL. If signing fails, only that post loses its image; the list still loads.
  - `ReservationRepository` handles reservation RPCs and reads. It signs the images of the listings its reservations reference with the same helper.
  - `StorageRepository` builds storage paths.
- `model/`: `@Serializable` DTOs, with `@SerialName` for snake_case columns.
  - `FoodPost`: its `displayImageUrl` is `@Transient` (a signed URL, never stored).
  - `Reservation`, `ReservationDisplay`, `DonorReservationRow`.
  - `UserProfile`.
- `viewmodel/`: `AndroidViewModel` + `StateFlow`.
  - `FoodViewModel` exposes `availablePosts` and `myPosts`.
  - `ReservationViewModel` exposes `myReservations`, `donorReservations` and `message`.
  - `FoodViewModel`: a feed or My Listings load requested while another request runs is queued and runs right after it (My Listings first). `createPost()` and `deletePost()` are still **ignored** while any request runs.
  - `ReservationViewModel`: every request is **ignored** while another is running. `refresh()`, also used after cancel and claim, loads My Reservations and My Donations independently.
- UI: large files in the root package.
  - `MainActivity.kt`: auth screens, Home feed, `RecommendationCard`, `AvailableFoodCard`, `FoodDetailsScreen` with Reserve.
  - `ActivityScreen.kt`: tabs My Listings, My Reservations, My Donations, Purchases, Claims. It shows reservation results and errors in a "Reservation" dialog, and reloads the reservation lists when their tab is tapped.
  - `PostScreen.kt`: Create Listing.
  - `ProfileScreen.kt` and `ProfileSections.kt`: profile.
  - `AlertScreen.kt`: alerts.
  - `FoodActions.kt`: preview-only dialogs.
  - **Much of the UI is still demo/placeholder data** (sample records, hard-coded names and locations, preview-only flows). Only the feed, listings, reservations and the profile name use Supabase.

**Identity.**
- The Firebase UID is stored in `users.firebase_uid`.
- `food_posts.donor_id` and `reservations.recipient_id` hold the Supabase profile `users.id` (uuid), **not** the Firebase UID.
- Storage paths start with the Firebase UID.

**Supabase objects** (in `supabase/migrations/`)
- Tables `users`, `food_posts` and `reservations`, each with RLS enabled and `anon` revoked.
- Helper functions: `is_salusalo_firebase_jwt()`, `current_salusalo_profile_id()`, `is_reservation_recipient()`.
- SECURITY DEFINER RPCs: `create_reservation`, `cancel_reservation`, `claim_reservation`, `get_donor_reservations`.
- `food-images` object path: `<firebaseUid>/<postId>/image.<ext>`. `food_posts.image_url` stores this **path**, so the UI must use `displayImageUrl`.

## Security and ownership rules (keep them)

- Identity comes only from the token: `FirebaseAuth.currentUser` in Kotlin, and `auth.jwt() ->> 'sub'` / `current_salusalo_profile_id()` in SQL. Never trust a user or profile ID passed in from the UI.
- **Reservations change only through the RPCs.** Clients have SELECT only on `reservations`.
  - The RPCs lock the `food_posts` row and update `quantity`/`status` atomically.
  - Never write `reservations` directly, and never adjust listing quantity or status from the app.
- The server enforces these rules; the UI mirrors them but the server stays authoritative:
  - No one can reserve their own post.
  - Only the recipient can cancel, and only while the reservation is `RESERVED`.
  - Only the post's donor can mark a reservation claimed, and only while it is `RESERVED`.
- Donors see recipient **names only**, via `get_donor_reservations()`. Do not widen `users` RLS.
- Client-created profiles are always `RECIPIENT` (enforced by RLS). The client never sets `role`.
- Keep `food-images` private and display images through signed URLs. Upload only under the uploader's own Firebase-UID folder.
- Never add a Supabase service-role key or Firebase Admin credentials to the app, docs or commits.
- A schema or RLS change needs a **new** migration file plus a note in `supabase/README.md`. Tell the user it must be run manually.

## Development rules

- **Inspect before modifying.** Read the relevant Kotlin and SQL, and check every claim (including those in docs and handoff notes) against the code.
- **Make minimal, targeted changes** in the surrounding style. Do not reorganize the large UI files, remove demo UI or add libraries unless the task requires it.
- **Reuse existing patterns.**
  - Signed URLs: `withSignedImageUrl(bucket)` in `data/FoodRepository.kt`.
  - Image with fallback: `AvailableFoodCard` and `RecommendationCard` (`MainActivity.kt`), and `RecordPhoto` (`ActivityScreen.kt`).
- **Stay inside the phase the user asks for.** Do not redo Phases 1–3, and do not start Phase 4 or later until the user asks.
- **Keep `docs/PROJECT_STATUS.md` current** whenever work changes status. Never mark anything fixed or tested unless it actually was.

## Testing expectations

- There are no meaningful automated tests yet; only the template `ExampleUnitTest` and `ExampleInstrumentedTest` exist.
- Every change must at least pass `assembleDebug`.
- Changes to auth, listings, reservations or images need a manual run on a device or emulator.
  - Reservation work needs the Account A/B test in `docs/PROJECT_STATUS.md` §4.
  - Also run a regression of the completed phases: sign-up, sign-in, sign-out, create/delete a listing with an image, the feed, and the A/B reservation test.
- Report exactly what was built, what was manually tested, and what was not.

## Known current work / issues

Details, test results and line references are in `docs/PROJECT_STATUS.md`.

1. **Phase 3 is complete** (signed off 2026-09-25). Manually verified:
   - the A/B reservation test, including the reservation images;
   - cancel and claim, with their Activity dialog;
   - the Recommended for You image;
   - the missing-image fallback on the feed;
   - the Phase 1/2 regression.
   - Checks with no test record are listed under the results log in `docs/PROJECT_STATUS.md` §4 (for example tab reload, the over-quantity error and reserving your own listing). Do not treat them as verified.
2. **Committed.** The work since `d4f65fe` (the Recommended and reservation image changes and the load-failure fixes, `PROJECT_STATUS.md` §3.2–§3.4), `docs/` and this file were committed on 2026-09-25. Commit only when the user asks.
3. **Known issues fixed in code on 2026-09-25, not yet built or tested** (`PROJECT_STATUS.md` §3.5):
   - Reservation details dialogs no longer end with the listing text "This is your live Supabase listing."
   - The `FoodDetailsScreen` header shows the uploaded image, and the screen shows the post's description and pickup location.
   - The Home feed reloads after a cancel. Nothing updates in real time across devices (still open).
4. **Images missing from Storage.** On 2026-09-25 two listings referenced image files that no longer existed in `food-images`; how they were lost is unknown. The app now loads such listings without an image (`PROJECT_STATUS.md` §3.4).
5. **Phase 4 (Messaging and Notifications) has not started.** This area has demo-only UI with no backend:
   - sample alerts (`AlertScreen.kt`);
   - the "Message Donor / Seller" demo chat (`FoodActions.kt`);
   - notification preferences saved only on the device (`ProfileSections.kt`).
