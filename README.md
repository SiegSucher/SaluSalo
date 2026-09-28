# Salu-Salo

Salu-Salo is an Android app for sharing surplus food within a community to reduce food waste. Donors post surplus food with a photo, quantity, pickup location and expiry date. Recipients browse the available listings and reserve some or all of a listing for pickup. The UI uses Philippine pesos (₱) and Philippine sample locations.

**Status (2026-09-28):** Phases 1–4 of 6 (backend foundation, food listings, reservations, and messaging and notifications) are complete and were signed off after manual testing. Fixes to the demo parts of Phase 1 (Google sign-in, profile name and photo, password change) passed on two phones on 2026-09-26. Listing prices and the Purchases and Claims tabs passed on two phones the same day. Fixes to the demo parts of Phase 2 (real donor names, listing details, up to 4 photos, editing, saved listings; `docs/PROJECT_STATUS.md` §11) passed on two phones the same day, with the migration `20260929_phase2_listing_details.sql`. Fixes to the demo parts of Phase 3 (a Reserve review page and receipt, removing reserved listings, real Activity and Profile records; §12) passed on two phones on 2026-09-27 with the migration `20260930_phase3_demo_fixes.sql`, and were merged into `master` through PR #7 on 2026-09-27 (merge commit `c184d9b`). Fixes to the demo parts of Phase 4 (the Profile bell and Settings, a real notification sound, a real response rate, a live Messages inbox and a chat badge that counts unread messages; §13) passed a short test on two phones on 2026-09-27 with the migration `20261001_phase4_demo_fixes.sql`, and were merged into `master` through PR #8 on 2026-09-27 (merge commit `39b57b5`). Phase 5 part 1 (ratings and reviews, reports, and an "Email verified" badge; §14) passed on two phones on 2026-09-28 with the migration `20261002_phase5_reviews_reports.sql`, and was merged into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`). Phase 5 part 2 (Maps; §15) passed on two phones on 2026-09-28 with the migration `20261003_phase5_maps.sql`, and was merged into `master` through PR #11 on 2026-09-28 (merge commit `255b0f8`). A Profile redesign with a bio, a Settings hub and food preferences (Mika's PR #10; §16) passed a short test on two phones the same day, with the migration `20261004_profile_bio.sql`, and was merged into `master` through PR #10 on 2026-09-28 (merge commit `49ac319`). Fixes, clean-up and optimization after the second demo audit (§17), with no database change, passed a 9-step test on two phones on 2026-09-28, and were merged into `master` through PR #12 on 2026-09-28 (merge commit `12e931c`). Parts of the UI still show demo data. See [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) for the detailed status.

## Features

Working against the live backend:

- **Accounts:** email/password or Google sign-up and sign-in, password-reset email, password change and sign-out through Firebase Authentication, with an optional "Keep me signed in". Each account gets a Supabase profile the first time it signs in; its display name and photo are saved there.
- **Food listings:** post a listing with a name, up to 4 photos, category, quantity, price, pickup location, expiry date, pickup window, handover, allergens and storage notes. Browse the feed of available, unexpired listings, newest first, with each donor's display name. Edit or delete your own listings, and bookmark other people's in Saved Listings.
- **Reservations:** reserve part or all of another user's listing. The recipient can cancel until the donor marks the reservation claimed. The donor sees the recipient's display name, but not their email or phone number. The server updates the remaining quantity atomically.
- **Activity:** the My Listings, My Reservations, My Donations, Purchases and Claims tabs, with totals of completed reservations. Listings can have a price, which is paid at pickup (no online payments).
- **Messages:** message a listing's donor from its details screen or from a reservation; live chat and a Messages inbox that stays live while the app is open, with unread counts and a chat badge that adds them up. Participants see each other's display name only.
- **Alerts:** in-app alerts for new messages and for reservations being made, cancelled or claimed, with unread badges and settings saved to your account. While the app is open, a new alert plays the phone's notification sound (a switch saved on the phone).
- **Response rate:** your own share of chats started by others in the last 30 days that you replied to, shown only to you on Profile.
- **Reviews and reports:** after a claimed pickup, the recipient can rate the donor or seller (1–5 stars and an optional comment); ratings show next to donor names, on the Donor/Seller page and on Profile. Listings, donors and chat partners can be reported. An "Email verified" badge is set only from the Firebase token.

Since PR #11 (Maps): a pickup pin on an OpenStreetMap map when posting or editing a listing (tap the map or "Use my location"), the distance to each pinned listing on Home and in its details, a Nearby filter (5 km) and a Nearest sort, and a map with Directions (Google Maps) on the pickup location page and for open reservations.

Since PR #12 (fixes after the second demo audit): "Start now" for the pickup start, allergens required when posting or editing, a "Check your listing" page, the profile photo on Home, full-screen listing photos, pull to refresh on Home, welcome pages only on the first start, text boxes that stay above the keyboard on full-screen pages, a neutral tile instead of a sample photo when a listing's photo can't be shown, one signing request per list of photos, and Back on Home closing the app instead of signing out.

There are no push notifications yet. Most settings are saved only on the device; the profile name and photo and the notification preferences come from Supabase.

## Tech stack

| Area | Technology |
|---|---|
| App | Kotlin and Jetpack Compose (Material 3); one activity (`MainActivity`), one module (`:app`) |
| Sign-in | Firebase Authentication (email/password, and Google through Credential Manager) |
| Backend | Supabase Postgres (PostgREST and RPCs), Storage and Realtime, through supabase-kt 3.5 on Ktor (OkHttp engine) |
| Images | Coil 3 |
| Maps | osmdroid 6.1.20 with OpenStreetMap tiles (no API key); the phone's location through the platform LocationManager (`androidx.core`), no location library |
| Build | Gradle 9.4.1 wrapper, Android Gradle Plugin 9.2.1, Kotlin 2.2.10, JDK 21; compileSdk 37, targetSdk 36, minSdk 24 |

Supabase trusts Firebase through Third-Party Auth: the app sends the signed-in user's Firebase ID token as its Supabase access token, and row-level security (RLS) checks it. Navigation is plain Compose state, with no navigation library and no dependency injection. [`CLAUDE.md`](CLAUDE.md) describes the architecture in detail.

## Getting started

### Prerequisites

- A recent Android Studio. The project uses Android Gradle Plugin 9.2.1 and compiles against API 37.
- JDK 21 for command-line builds. The Gradle daemon is pinned to Java 21 in `gradle/gradle-daemon-jvm.properties`.
- The Supabase project URL and publishable key.

### 1. Add the Supabase keys

Add these lines to `local.properties` in the repo root. Android Studio creates the file; create it yourself if it's missing.

```properties
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_PUBLISHABLE_KEY=<publishable-key>
```

`local.properties` is git-ignored. Gradle copies both values into `BuildConfig`. If either is blank, the app fails with an error the first time it connects to Supabase. Never commit these values.

### 2. Build and run

Open the project in Android Studio and run the `app` configuration, or use the Gradle wrapper from the repo root:

```bash
./gradlew assembleDebug   # build the debug APK into app/build/outputs/apk/debug/
./gradlew installDebug    # install it on a connected device or emulator
```

On Windows, use `gradlew.bat`. If `java` is not on your PATH, set `JAVA_HOME` to a JDK 21 first. For example, in PowerShell, using a JDK that Gradle has downloaded:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\<jdk-21-folder>"
.\gradlew.bat assembleDebug
```

## Backend setup (new Supabase project only)

Skip this section if you use the project's existing Supabase backend.

1. **Database.** In the Supabase SQL Editor, run the files in `supabase/migrations/` in this order:
   1. `20260921_phase1_foundation.sql`: the `users` table and the private `food-images` and `profile-images` Storage buckets.
   2. `20260923_phase2_food_posts.sql`: the `food_posts` table and the listing-image policies.
   3. `20260924_phase3_reservations.sql`: the `reservations` table and the reservation RPCs.
   4. `20260924_phase3_donor_recipient_names.sql`: not needed on a new project, because step 3 already defines the same function. Running it is harmless, but only after step 3, even though its name sorts first.
   5. `20260926_phase4_messaging_notifications.sql`: conversations, messages, alerts and notification preferences, their RPCs and triggers, and the Realtime publication for `messages` and `notifications`.
   6. `20260927_activity_prices.sql`: listing prices and the price locked into each reservation. Don't run the Phase 3 files again after it.
   7. `20260928_profile_updates.sql`: the functions that save your own profile name and photo.
   8. `20260929_phase2_listing_details.sql`: the listing details and extra photos, editing through `update_my_food_post`, donor names and saved listings. Don't run `20260923_phase2_food_posts.sql` again after it.
   9. `20260930_phase3_demo_fixes.sql`: removing reserved listings through `remove_my_food_post` (clients have no DELETE on listings), the `REMOVED` status and saved-listing names.
   10. `20261001_phase4_demo_fixes.sql`: `get_my_response_rate()`, the caller's own response rate.
   11. `20261002_phase5_reviews_reports.sql` (Phase 5 part 1, branch `claude/phase5-reviews-reports-v6ahqn`): reviews, reports and `users.email_verified`, with their SECURITY DEFINER functions. Clients can't read or write the two new tables directly.
2. **Third-Party Auth.** In Supabase, open **Authentication → Third-Party Auth** and add a Firebase integration for the project ID `salusalo`.
3. **Custom claim.** Every Firebase user needs the custom claim `role: authenticated`. A trusted Admin SDK or Cloud Function must add it; that code is not in this repository. Existing users must sign in again or refresh their ID token after the claim is added.

Both the app and the SQL are tied to the Firebase project `salusalo`: `app/google-services.json` is committed, and `is_salusalo_firebase_jwt()` accepts only tokens issued for that project. There is no Supabase CLI setup, so migrations are always applied by hand, and a schema or RLS change needs a new migration file. [`supabase/README.md`](supabase/README.md) has more detail.

## Testing

The automated tests are the Android Studio templates `ExampleUnitTest` and `ExampleInstrumentedTest`, and the JVM tests `GeoTest` (distances and pins), `PhotoSigningTest` (signing a list of photos) and `PickupStartTest` (the pickup-start rule).

```bash
./gradlew testDebugUnitTest           # JVM unit tests
./gradlew connectedDebugAndroidTest   # instrumented tests (needs a device or emulator)
./gradlew lintDebug                   # Android lint
```

Features are tested by hand. The two-account reservation test (Account A / Account B) and the results log are in [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) §4.

## Project structure

```text
app/src/main/java/com/example/salusalo/
├── MainActivity.kt      Auth screens, navigation state, Home feed, food details and Reserve
├── ActivityScreen.kt    Activity tabs: My Listings, My Reservations, My Donations, Purchases, Claims
├── PostScreen.kt        Create Listing and Edit Listing
├── ProfileScreen.kt     Profile (with ProfileSections.kt)
├── AlertScreen.kt       Alerts
├── ChatScreen.kt        Chat and the Messages inbox
├── FoodActions.kt       Pickup location and Donor/Seller pages, Reserve review page and receipt
├── ListingPhotos.kt     Listing photo with its fallback tiles, and the full-screen photo view
├── ReviewsAndReports.kt Rate and Report pages (Phase 5 part 1)
├── data/                Repositories and the Supabase client
├── model/               Serializable data classes for the Supabase tables
└── viewmodel/           Food, Saved listing, Reservation, Message and Notification ViewModels
supabase/
├── migrations/          SQL migrations, applied by hand
├── tests/               Read-only checks, live safety checks and the reports query (SQL Editor)
└── README.md            Supabase and Firebase setup
docs/PROJECT_STATUS.md   Detailed status, manual test plan and results
CLAUDE.md                Architecture and working rules for Claude Code
```

## Roadmap

| Phase | Scope | Status |
|---|---|---|
| 1 | Backend Foundation | Complete |
| 2 | Food Listings | Complete |
| 3 | Reservations | Complete |
| 4 | Messaging and Notifications | Complete |
| 5 | Maps, Reviews, Reports | Part 1 (reviews, reports, Email verified) passed on two phones (2026-09-28) and was merged through PR #9; part 2 (Maps) passed on two phones (2026-09-28) and was merged through PR #11 |
| 6 | Admin and Final Release | Not started |

Known issues, and checks that still have no test record, are listed in [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md).

## Security

- Never commit `local.properties`, and never add a Supabase service-role key or Firebase Admin credentials to the app, the docs or a commit.
- Listing photos are stored in the private `food-images` bucket, in a folder named after the uploader's Firebase UID. The app shows them through signed URLs that expire after one hour.
- The app can only read the `conversations`, `messages` and `notifications` tables; messages are sent through a server function, and alerts are created only by the database.
- The app can only read the `reservations` table. Reserving, cancelling and claiming go through server functions (RPCs); reserving and cancelling change the listing's quantity in the same transaction.
- The server enforces the ownership rules: no one can reserve their own listing, only the recipient can cancel, and only the listing's donor can mark a reservation claimed.
- Phase 5 part 1 (passed on two phones; merged into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`)): reviews and reports are written only through server functions that take the caller from the token; the app can't read the `reviews` or `reports` tables at all, and other people's reviews come back with names only. `users.email_verified` is set only from the Firebase token.
- Profile bio (Mika's PR #10, §16): it changes only through the `update_my_profile_details` server function, which takes the caller from the token, and only its owner can read it.
- Phase 5 part 2 (merged through PR #11 on 2026-09-28): a listing's pickup pin is visible exactly to the people who can see the listing, and changes only through the insert and the `update_my_food_post_with_pin` server function, which takes the caller from the token; the app still has no UPDATE or DELETE on `food_posts`. The phone's location is used on the phone for distances, and is never sent to Supabase or stored. The one exception is "Use my location" on Post and Edit, which puts the pin at the phone's position; the donor then saves that pin with the listing, and the note says who sees it. Location access is foreground only and asked for only when it's needed.
- Maps use OpenStreetMap's free tile servers, following their usage policy: a User-Agent that names the app, "© OpenStreetMap contributors" on every map, tiles cached in the app's own cache folder, and no bulk or offline downloading. There is no map API key. These servers suit testing and a small user base; before a large public release, consider a tile provider meant for apps, and keep its key out of the repo like the Supabase keys.

## Documentation

- [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md): detailed status, the manual test plan and results, and known issues.
- [`supabase/README.md`](supabase/README.md): Supabase and Firebase setup.
- [`CLAUDE.md`](CLAUDE.md): architecture, security rules and development rules. It is written for Claude Code but is a useful reference for anyone working on the code.
