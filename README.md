# Salu-Salo

Salu-Salo is an Android app for sharing surplus food within a community to reduce food waste. Donors post surplus food with a photo, quantity, pickup location and expiry date. Recipients browse the available listings and reserve some or all of a listing for pickup. The UI uses Philippine pesos (₱) and Philippine sample locations.

**Status (2026-09-30):** Phases 1–4 of 6 (backend foundation, food listings, reservations, and messaging and notifications) are complete and signed off. Both parts of Phase 5 (ratings, reviews, reports and an "Email verified" badge; maps) passed on two phones and are merged into `master`, as are the demo fixes to Phases 1–4, a Profile redesign, the clean-ups after the second demo audit, automatic Supabase roles for new accounts (PR #13), a UI/UX refresh (PRs #14 and #15) and a web admin dashboard with sample data (`admin/`, PR #15). A bug fix pass after a full bug audit (security of reservations and photos, Home refresh and error states, safe posting, price and pickup-time checks, donor release, rotation and session handling) is in PR #17: its database migrations are applied to the live project and it passed a manual test on one phone on 2026-09-30; it is not merged yet. A second round of fixes for the rest of that audit (fairer ratings, friendly error texts, photo caching and decoding, Storage and rate limits, backup rules, release configuration, database privileges) is on branch `claude/bugfix-pass-2`: built and unit-tested, its database migrations applied to the live project on 2026-09-30 (their checks passed), and it passed a manual test on one phone the same day; it is not committed yet. Phase 6 (admin and final release) has not started. Parts of the UI still show demo data. See [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) for the detailed status, the current state and the known issues.

## Features

Working against the live backend:

- **Accounts:** email/password or Google sign-up and sign-in, password-reset email, password change and sign-out through Firebase Authentication, with an optional "Keep me signed in". Each account gets a Supabase profile the first time it signs in; its display name and photo are saved there.
- **Food listings:** post a listing with a name, up to 4 photos, category, quantity, price, pickup location, expiry date, pickup window, handover, allergens and storage notes. Browse the feed of available, unexpired listings whose pickup time hasn't ended, newest first, with each donor's display name. The feed reloads when you pull down, and (in PR #17) when you come back to Home or to the app. Edit or delete your own listings, and bookmark other people's in Saved Listings.
- **Reservations:** reserve part or all of another user's listing, after a review page with the total and a receipt. The recipient can cancel until the donor marks the reservation picked up. The donor sees the recipient's display name, but not their email or phone number. The server updates the remaining quantity atomically. In PR #17: the donor can release a reservation that won't be picked up (the recipient is told), a reservation is refused if the price changed since you saw it or the pickup time has ended, and the details page says why a listing can't be reserved.
- **Activity:** three tabs. My Listings (active and closed listings, with Manage reservations), My Reservations (open reservations: all, free food or purchases) and History (picked-up and cancelled reservations from both sides, with totals). Listings can have a price, which is paid at pickup (no online payments).
- **Messages:** message a listing's donor from its details screen or from a reservation; live chat and a Messages inbox that stays live while the app is open, with unread counts and a chat badge that adds them up. Participants see each other's display name only.
- **Alerts:** in-app alerts for new messages and for reservations being made, cancelled or claimed, with unread badges and settings saved to your account. While the app is open, a new alert plays the phone's notification sound (a switch saved on the phone).
- **Response rate:** your own share of chats started by others in the last 30 days that you replied to, shown only to you on Profile.
- **Reviews and reports:** after a claimed pickup, the recipient can rate the donor or seller (1–5 stars and an optional comment); ratings show next to donor names, on the Donor/Seller page and on Profile. Listings, donors and chat partners can be reported. An "Email verified" badge is set only from the Firebase token.

Since PR #11 (Maps): a pickup pin on an OpenStreetMap map when posting or editing a listing (tap the map or "Use my location"), the distance to each pinned listing on Home and in its details, a Nearby filter (5 km) and a Nearest sort, and a map with Directions (Google Maps) on the pickup location page and for open reservations.

Since PR #12 (fixes after the second demo audit): "Start now" for the pickup start, allergens required when posting or editing, a "Check your listing" page, the profile photo on Home, full-screen listing photos, pull to refresh on Home, welcome pages only on the first start, text boxes that stay above the keyboard on full-screen pages, a neutral tile instead of a sample photo when a listing's photo can't be shown, one signing request per list of photos, and Back on Home closing the app instead of signing out.

Since PR #13: a new account gets the Supabase role it needs by itself, through the `set-firebase-role` Edge Function, instead of the developer running a script after each sign-up. If that setup fails, Sign In offers "Try again".

Since PRs #14 and #15 (UI/UX refresh): one design in Salu-Salo's original colours and logo, a loading screen, the bottom bar with Post in the middle, transitions and swipes between tabs, skeletons and empty states.

In PR #17 (bug fix pass, not merged yet), besides the reservation changes above: failed loads keep what is on screen and offer Try again instead of showing "nothing here", expired photo links are renewed, posting again after a failed or unclear post never creates a duplicate, rotation and dark mode keep you on Home, a disabled or deleted account returns to Sign In with a message, alerts reload when you come back to the app, and an account that was deleted and created again with the same email can restore its earlier profile after confirming that email.

On branch `claude/bugfix-pass-2` (round 2, migrations applied, passed a one-phone test on 2026-09-30):
- A donor's rating counts each reviewer's latest review once.
- Chat, alert and Google sign-in errors show short, friendly texts.
- Photos are cached by their storage path, so refreshing doesn't download them again, and large camera photos are shrunk without loading them fully into memory.
- The photo buckets accept only images up to 5 MB.
- Messages (20 a minute) and reservations (10 per 10 minutes) are rate-limited.
- A quick bookmark tap is no longer undone by the first load.
- Android backups no longer carry the Firebase sign-in to another phone.
- Release signing can be configured without committing keys.
- No database function can be called without signing in.

There are no push notifications yet. Most settings are saved only on the device; the profile name and photo and the notification preferences come from Supabase.

## Tech stack

| Area | Technology |
|---|---|
| App | Kotlin and Jetpack Compose (Material 3); one activity (`MainActivity`), one module (`:app`) |
| Sign-in | Firebase Authentication (email/password, and Google through Credential Manager) |
| Backend | Supabase Postgres (PostgREST and RPCs), Storage, Realtime and one Edge Function (TypeScript on Deno, no dependencies), through supabase-kt 3.5 on Ktor (OkHttp engine) |
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

### Release build

`./gradlew assembleRelease` builds an unsigned release APK unless a signing key is configured. To sign it, create a keystore outside the repository (for example `keytool -genkeypair -v -keystore salusalo-release.jks -alias salusalo -keyalg RSA -keysize 2048 -validity 10000`) and a `keystore.properties` file in the repo root:

```properties
storeFile=../salusalo-release.jks
storePassword=<store password>
keyAlias=salusalo
keyPassword=<key password>
```

`keystore.properties`, `*.jks` and `*.keystore` are git-ignored: never commit the keystore or its passwords, and keep a safe backup (a lost upload key has to be reset through Play Console). `storeFile` is relative to the repo root.

The release build is not Play-ready yet (`docs/PROJECT_STATUS.md` §22.1 #31):
- R8 shrinking is off until keep rules exist and a release build has been tested.
- The package name `com.example.salusalo` is refused by Google Play; changing it means registering a new Android app in Firebase (new `google-services.json` and SHA-1 fingerprints for Google sign-in).
- Google Play requires in-app account deletion and a web link for it, a privacy policy and the Data safety form.
- Google sign-in works only in builds signed with a SHA-1 registered in Firebase.

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
   11. `20261002_phase5_reviews_reports.sql` (Phase 5 part 1): reviews, reports and `users.email_verified`, with their SECURITY DEFINER functions. Clients can't read or write the two new tables directly.
   12. `20261003_phase5_maps.sql` (Phase 5 part 2): the pickup pin and `update_my_food_post_with_pin`.
   13. `20261004_profile_bio.sql`: the profile bio and `update_my_profile_details`.
   14. `20261005_bugfix_security_integrity.sql` (bug fix pass): the profile check in cancel and claim, the stricter listing-photo read rule and the checks on new listings.
   15. `20261006_bugfix_reservations.sql`: `create_reservation_at_price`, `release_reservation` and the pickup-end check. Run it before installing a build that uses them.
   16. `20261007_bugfix_profile_relink.sql`: `relink_my_profile`, restoring a re-created account's profile.
   17. `20261008_bugfix_one_rating_per_reviewer.sql` (round 2): ratings count each reviewer's latest review.
   18. `20261009_bugfix_storage_and_rate_limits.sql`: bucket type and size limits, message and reservation rate limits.
   19. `20261010_bugfix_revoke_anon_execute.sql`: no function can be run without signing in.

   After each of the last six, run its safety check from `supabase/tests/` (`supabase/README.md`).
2. **Third-Party Auth.** In Supabase, open **Authentication → Third-Party Auth** and add a Firebase integration for the project ID `salusalo`.
3. **Custom claim.** Every Firebase user needs the custom claim `role: authenticated`. Set up the Edge Function `set-firebase-role` as [`supabase/README.md`](supabase/README.md) describes (a Google Cloud service account with a two-permission custom role, its JSON key as an Edge Function secret, deployed with JWT verification off); the app then calls it once for an account whose token lacks the claim (since PR #13). Before that, a trusted Admin SDK script, not in this repository, added it by hand; it stays as a backup.

Both the app and the SQL are tied to the Firebase project `salusalo`: `app/google-services.json` is committed, and `is_salusalo_firebase_jwt()` accepts only tokens issued for that project. There is no Supabase CLI setup, so migrations are always applied by hand, and a schema or RLS change needs a new migration file. [`supabase/README.md`](supabase/README.md) has more detail.

## Testing

The automated tests are the Android Studio templates `ExampleUnitTest` and `ExampleInstrumentedTest`, and the JVM tests `GeoTest` (distances and pins), `PhotoSigningTest` (signing a list of photos), `PickupStartTest` (the pickup-start rule), `SupabaseRoleTest` (when the app calls the role function), `UiTextTest` (expiry and "posted" labels), in PR #17 `PublishOnceTest` (posting never twice, photos kept when unsure) and `ReserveBlockerTest` (why a listing can't be reserved), and in round 2 `PhotoSampleTest` (how much a camera photo is shrunk while decoding). On 2026-09-30 `testDebugUnitTest` passed all 54 tests; `lintDebug` found 0 errors on 2026-09-29. The Edge Function has Deno tests: `deno test supabase/functions/tests/set-firebase-role-test.ts` (needs [Deno](https://deno.com); not part of the Android build).

```bash
./gradlew testDebugUnitTest           # JVM unit tests
./gradlew connectedDebugAndroidTest   # instrumented tests (needs a device or emulator)
./gradlew lintDebug                   # Android lint
```

Features are tested by hand. The two-account reservation test (Account A / Account B) and the results log are in [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md) §4. The SQL files in `supabase/tests/` check the database's rules on the live project; they always end with an error on purpose, which undoes everything they did.

## Project structure

```text
app/src/main/java/com/example/salusalo/
├── MainActivity.kt      Auth screens, navigation state, Home feed, food details and Reserve
├── ActivityScreen.kt    Activity tabs: My Listings, My Reservations, History
├── PostScreen.kt        Create Listing and Edit Listing
├── ProfileScreen.kt     Profile (with ProfileSections.kt)
├── AlertScreen.kt       Alerts
├── ChatScreen.kt        Chat and the Messages inbox
├── FoodActions.kt       Pickup location and Donor/Seller pages, Reserve review page and receipt
├── ListingPhotos.kt     Listing photo with its fallback tiles, and the full-screen photo view
├── SaluTheme.kt         Colours, type and shared components (UiText.kt: expiry and posted labels)
├── ReviewsAndReports.kt Rate and Report pages (Phase 5 part 1)
├── data/                Repositories and the Supabase client
├── model/               Serializable data classes for the Supabase tables
└── viewmodel/           Food, Saved listing, Reservation, Message and Notification ViewModels
supabase/
├── functions/           Edge Function set-firebase-role and its Deno tests, deployed by hand
├── migrations/          SQL migrations, applied by hand
├── tests/               Read-only checks, live safety checks and the reports query (SQL Editor)
└── README.md            Supabase and Firebase setup
admin/                   Web admin dashboard with sample data (not connected to the backend)
docs/PROJECT_STATUS.md   Detailed status, current state, known issues, manual test plan and results
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
| 6 | Admin and Final Release | Not started (a web dashboard with sample data is in `admin/`) |

Known issues, and checks that still have no test record, are listed in [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md).

## Security

- Never commit `local.properties`, and never add a Supabase service-role key or Firebase Admin credentials to the app, the docs or a commit.
- Listing photos are stored in the private `food-images` bucket, in a folder named after the uploader's Firebase UID. The app shows them through signed URLs that expire after one hour. With the bug fix pass's migrations (applied on 2026-09-30), a photo can be read only by its uploader and by people who may see, have saved, or chat about its listing.
- The app can only read the `conversations`, `messages` and `notifications` tables; messages are sent through a server function, and alerts are created only by the database.
- The app can only read the `reservations` table. Reserving, cancelling and claiming go through server functions (RPCs); reserving and cancelling change the listing's quantity in the same transaction.
- The server enforces the ownership rules: no one can reserve their own listing, only the recipient can cancel, and only the listing's donor can mark a reservation claimed or (since the bug fix pass) release it. Every reservation function refuses an account that has no Salu-Salo profile. The price recorded for a reservation always comes from the listing, never from the app.
- Phase 5 part 1 (passed on two phones; merged into `master` through PR #9 on 2026-09-28 (merge commit `711cc5e`)): reviews and reports are written only through server functions that take the caller from the token; the app can't read the `reviews` or `reports` tables at all, and other people's reviews come back with names only. `users.email_verified` is set only from the Firebase token.
- Profile bio (Mika's PR #10, §16): it changes only through the `update_my_profile_details` server function, which takes the caller from the token, and only its owner can read it.
- Phase 5 part 2 (merged through PR #11 on 2026-09-28): a listing's pickup pin is visible exactly to the people who can see the listing, and changes only through the insert and the `update_my_food_post_with_pin` server function, which takes the caller from the token; the app still has no UPDATE or DELETE on `food_posts`. The phone's location is used on the phone for distances, and is never sent to Supabase or stored. The one exception is "Use my location" on Post and Edit, which puts the pin at the phone's position; the donor then saves that pin with the listing, and the note says who sees it. Location access is foreground only and asked for only when it's needed.
- New accounts' Supabase role (since PR #13): the `set-firebase-role` Edge Function verifies the caller's Firebase ID token itself and only ever sets `role: "authenticated"` on the caller's own account. Its Google service-account key has only two permissions (read and update accounts) and is kept only as a Supabase Edge Function secret, never in the app, the repo, the docs or a log. `.gitignore` ignores downloaded key files (`salusalo-*.json`, `*service-account*.json`) as a safety net.
- Restoring an earlier profile (bug fix pass): only after the user confirms, and only when Firebase's own signed token says the account's email is verified; only the link to the Firebase account changes.
- Round 2 (`claude/bugfix-pass-2`, migrations applied 2026-09-30):
  - no database function can be run without a signed-in Firebase token;
  - messages and reservations are rate-limited per person on the server;
  - the photo buckets accept only images up to 5 MB;
  - Android backups and phone transfers leave out Firebase's saved sign-in;
  - error messages never show server URLs, headers or technical details.
- Maps use OpenStreetMap's free tile servers, following their usage policy: a User-Agent that names the app, "© OpenStreetMap contributors" on every map, tiles cached in the app's own cache folder, and no bulk or offline downloading. There is no map API key. These servers suit testing and a small user base; before a large public release, consider a tile provider meant for apps, and keep its key out of the repo like the Supabase keys.

## Documentation

- [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md): detailed status, the manual test plan and results, and known issues.
- [`supabase/README.md`](supabase/README.md): Supabase and Firebase setup.
- [`CLAUDE.md`](CLAUDE.md): architecture, security rules and development rules. It is written for Claude Code but is a useful reference for anyone working on the code.
