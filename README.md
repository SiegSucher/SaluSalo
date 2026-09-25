# Salu-Salo

Salu-Salo is an Android app for sharing surplus food within a community to reduce food waste. Donors post surplus food with a photo, quantity, pickup location and expiry date. Recipients browse the available listings and reserve some or all of a listing for pickup. The UI uses Philippine pesos (₱) and Philippine sample locations.

**Status (2026-09-25):** Phases 1–3 of 6 (backend foundation, food listings and reservations) are complete and were signed off after manual testing. Phase 4, Messaging and Notifications, is in progress: it is built, its migration is applied, and it has been partly tested on devices. Fixes for two notification-preference bugs are not yet retested. Parts of the UI still show demo data. See [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) for the detailed status.

## Features

Working against the live backend:

- **Accounts:** email/password sign-up, sign-in, password-reset email and sign-out through Firebase Authentication. Each account gets a Supabase profile the first time it signs in.
- **Food listings:** post a listing with a name, photo, category, quantity, pickup location and expiry date. Browse the feed of available, unexpired listings, and view or delete your own.
- **Reservations:** reserve part or all of another user's listing. The recipient can cancel until the donor marks the reservation claimed. The donor sees the recipient's display name, but not their email or phone number. The server updates the remaining quantity atomically.
- **Activity:** the My Listings, My Reservations and My Donations tabs.
- **Messages (Phase 4, partly tested):** message a listing's donor from its details screen or from a reservation; live chat and a Messages inbox with unread counts. Participants see each other's display name only.
- **Alerts (Phase 4, partly tested):** in-app alerts for new messages and for reservations being made, cancelled or claimed, with unread badges and settings saved to your account.

Listing prices and real Purchases and Claims tabs (pay at pickup, no online payments) are in code on branch `claude/activity-purchases-claims` but not yet built or tested (`docs/PROJECT_STATUS.md` §9). Demo or preview-only, with no backend yet: the Claim Now / Buy Now dialogs, reviews, and the Google, Apple and Facebook sign-in buttons. There are no push notifications yet. Profile edits and most settings are saved only on the device; the profile name and notification preferences come from Supabase.

## Tech stack

| Area | Technology |
|---|---|
| App | Kotlin and Jetpack Compose (Material 3); one activity (`MainActivity`), one module (`:app`) |
| Sign-in | Firebase Authentication (email/password) |
| Backend | Supabase Postgres (PostgREST and RPCs), Storage and Realtime, through supabase-kt 3.5 on Ktor (OkHttp engine) |
| Images | Coil 3 |
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
2. **Third-Party Auth.** In Supabase, open **Authentication → Third-Party Auth** and add a Firebase integration for the project ID `salusalo`.
3. **Custom claim.** Every Firebase user needs the custom claim `role: authenticated`. A trusted Admin SDK or Cloud Function must add it; that code is not in this repository. Existing users must sign in again or refresh their ID token after the claim is added.

Both the app and the SQL are tied to the Firebase project `salusalo`: `app/google-services.json` is committed, and `is_salusalo_firebase_jwt()` accepts only tokens issued for that project. There is no Supabase CLI setup, so migrations are always applied by hand, and a schema or RLS change needs a new migration file. [`supabase/README.md`](supabase/README.md) has more detail.

## Testing

The only automated tests are the Android Studio templates `ExampleUnitTest` and `ExampleInstrumentedTest`.

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
├── PostScreen.kt        Create Listing
├── ProfileScreen.kt     Profile (with ProfileSections.kt)
├── AlertScreen.kt       Alerts
├── ChatScreen.kt        Chat and the Messages inbox
├── FoodActions.kt       Preview-only pickup and checkout dialogs
├── data/                Repositories and the Supabase client
├── model/               Serializable data classes for the Supabase tables
├── viewmodel/           Food, Reservation, Message and Notification ViewModels
└── ui/theme/            Template theme (not currently used)
supabase/
├── migrations/          SQL migrations, applied by hand
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
| 4 | Messaging and Notifications | In progress |
| 5 | Maps, Reviews, Reports | Not started |
| 6 | Admin and Final Release | Not started |

Known issues, and checks that still have no test record, are listed in [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md).

## Security

- Never commit `local.properties`, and never add a Supabase service-role key or Firebase Admin credentials to the app, the docs or a commit.
- Listing photos are stored in the private `food-images` bucket, in a folder named after the uploader's Firebase UID. The app shows them through signed URLs that expire after one hour.
- The app can only read the `conversations`, `messages` and `notifications` tables; messages are sent through a server function, and alerts are created only by the database.
- The app can only read the `reservations` table. Reserving, cancelling and claiming go through server functions (RPCs); reserving and cancelling change the listing's quantity in the same transaction.
- The server enforces the ownership rules: no one can reserve their own listing, only the recipient can cancel, and only the listing's donor can mark a reservation claimed.

## Documentation

- [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md): detailed status, the manual test plan and results, and known issues.
- [`supabase/README.md`](supabase/README.md): Supabase and Firebase setup.
- [`CLAUDE.md`](CLAUDE.md): architecture, security rules and development rules. It is written for Claude Code but is a useful reference for anyone working on the code.
