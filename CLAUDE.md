# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Salu-Salo** is an Android app for sharing surplus food within a community to reduce food waste. Donors post surplus food with a photo, quantity, pickup location and expiry. Recipients browse the available listings and reserve part or all of the quantity for pickup. The UI uses ₱ and Philippine sample locations.

- **Kotlin + Jetpack Compose (Material 3).** Single `MainActivity`, single `:app` module, package `com.example.salusalo`.
- **Firebase Authentication.** Email/password sign-up, sign-in, password reset, password change and sign-out, plus Google sign-in through Credential Manager (`data/GoogleSignIn.kt`; server client ID `R.string.default_web_client_id` from `google-services.json`, never hard-coded). "Keep me signed in" (unchecked by default) is a device-only setting checked on a fresh start (`MainActivity.onCreate`).
- **Supabase.** Postgres (via PostgREST and RPCs), Storage and Realtime, through supabase-kt 3.5 on Ktor with the OkHttp engine (Realtime needs WebSockets, which the Android engine lacks). Images load with Coil 3 `AsyncImage`.
- **Supabase trusts Firebase via Third-Party Auth.** `data/SupabaseClientProvider.kt` sends the current Firebase ID token as the Supabase access token.
  - Every Firebase user needs the custom claim `role: authenticated`. A trusted Admin SDK or Cloud Function sets it; that code is **not in this repo**.
  - SQL checks tokens with `is_salusalo_firebase_jwt()`, which requires issuer and audience = Firebase project `salusalo`.
- **Storage.** The existing private bucket **`food-images`** holds listing photos. The private **`profile-images`** bucket holds profile photos, visible only to their owner.

**Roadmap: six phases.** Phases 1–4 are complete and manually verified (2026-09-25); do not redo them.
- Phase 1 — Backend Foundation: complete.
- Phase 2 — Food Listings: complete.
- Phase 3 — Reservations: complete.
- Phase 4 — Messaging and Notifications: complete (signed off 2026-09-25). Chat per listing, in-app alerts, Realtime, preferences in Supabase. Built, migration applied, and tested on two phones, including the two preference fixes and live alerts after more than 1 hour; the live safety check passed. The password-reset email was not tested (`PROJECT_STATUS.md` §8).
- Activity Purchases, Claims and listing prices (between Phases 4 and 5, not part of Phase 5): **merged into `master` through PR #3; built, migration `20260927_activity_prices.sql` applied, and the two-phone test passed (2026-09-26), except the "Reserved" dialog after Reserve, a Phase 3 bug left for the Phase 3 demo fixes** (`PROJECT_STATUS.md` §9.6).
- Phase 1 demo fixes (Google sign-in, Keep me signed in, profile name and photo in Supabase, password change; not part of Phase 5): **complete: merged into `master` through PR #3, migration `20260928_profile_updates.sql` applied, and passed on two phones (2026-09-26)** (`PROJECT_STATUS.md` §10).
- Phase 2 demo fixes (real donor names, no ratings or distances, listing details, up to 4 photos, Edit listing, Saved Listings, expiry; not part of Phase 5): **merged into `master` through PR #5 on 2026-09-26 (`aa3e823`), migration `20260929_phase2_listing_details.sql` applied, and the two-phone test and a regression round passed (2026-09-26)** (`PROJECT_STATUS.md` §11).
- Phase 3 demo fixes (Reserve review page and receipt, pickup questions through the chat, removing reserved listings, real Activity and Profile records; not part of Phase 5): **in code on branch `claude/phase3-demo-fixes`; not built, migration `20260930_phase3_demo_fixes.sql` not applied, not tested** (`PROJECT_STATUS.md` §12).
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
- `supabase/tests/` holds checks to paste into the SQL Editor, **not migrations**. `phase4_live_safety_check.sql` tests the Phase 4 refusals on the live project and always ends with an error on purpose, which rolls back everything it did.

## Architecture

Paths below are relative to `app/src/main/java/com/example/salusalo/`.

**Navigation uses plain Compose state. There is no Navigation library and no DI.**
- `SaluSaloApp()` in `MainActivity.kt` holds `screen` (`welcome | signin | signup | forgot | home`).
  - It runs Firebase sign-in, sign-up and reset, then `UserRepository.loadOrCreateCurrentProfile()` before entering `home`.
  - System Back on the Home page calls `logOut()`.
- `HomeScreen()` (also in `MainActivity.kt`) holds `page` (`home | all | messages | alerts | post | activity | profile`; the `recommended` page was removed in §11) plus the bottom bar.
  - Food details open as a full-screen `Dialog` (`FoodDetailsScreen`).
  - It creates the ViewModels with `viewModel(key = "food-$profileId")` / `"reservation-$profileId"` / `"messages-$profileId"` / `"notifications-$profileId"` / `"saved-$profileId"`, so each account gets its own instances. They live in the Activity's `ViewModelStore` and are reused if the same account signs in again; `clearSession()` empties their lists when `HomeScreen` is disposed.

**Layers**
- `data/`: plain repository classes that use the `SupabaseClientProvider.client` singleton.
  - `UserRepository` finds or creates the caller's `users` row, keyed by `FirebaseAuth.currentUser.uid`. It also changes the name and photo through the profile functions, uploads photos to `profile-images` (a new `<uid>/profile-<millis>.jpg` each time, shrunk by `data/ProfilePhoto.kt`), and changes the password (reauthenticate, then update). `supabaseErrorText()` shows only a `RestException`'s short `error` text, never the full message.
  - `FoodRepository` handles `food_posts` and image upload: the feed (newest first, donor names from `get_listing_donor_names`), create with up to 4 photos (each shrunk to a 1600 px JPEG by `encodeListingPhoto()` in `data/ProfilePhoto.kt`), `updateMyFoodPost()` (the `update_my_food_post` RPC) and delete (removes every photo, best effort).
  - `SavedListingRepository` handles `saved_listings` (bookmarks), with each listing embedded. `withSignedImageUrl(bucket)`, a top-level function in `FoodRepository.kt`, turns the stored path into a 1-hour signed URL. If signing fails, only that post loses its image; the list still loads.
  - `ReservationRepository` handles reservation RPCs and reads. It signs the images of the listings its reservations reference with the same helper.
  - `MessageRepository` (conversation RPCs, messages) and `NotificationRepository` (alerts, preferences). `realtimeInserts()` in `data/RealtimeInserts.kt` streams inserted rows through a Realtime channel.
  - `StorageRepository` builds storage paths.
- `model/`: `@Serializable` DTOs, with `@SerialName` for snake_case columns.
  - `FoodPost`: its `displayImageUrl` is `@Transient` (a signed URL, never stored). `price` is per unit, 0 = free. `formatPeso()` and `reservationTotal()` (same file) format money with `BigDecimal.valueOf`; never use `BigDecimal(double)`.
  - `Reservation`, `ReservationDisplay`, `DonorReservationRow`.
  - `UserProfile`.
  - `Conversation`, `ConversationSummary`, `ChatMessage` (`model/Conversation.kt`, with `parseTimestamp()`); `AppNotification`, `NotificationType`, `NotificationPreferences`.
- `viewmodel/`: `AndroidViewModel` + `StateFlow`.
  - `FoodViewModel` exposes `availablePosts` and `myPosts`.
  - `ReservationViewModel` exposes `myReservations`, `donorReservations` and `message`.
  - `FoodViewModel`: requests run one at a time behind a `Mutex`. A feed or My Listings load requested while another request runs is queued and runs right after it (My Listings first). A write (`createPost()`, `updatePost()`, `deletePost()`) waits for the running request instead of being dropped; a second write before the first has finished is ignored (`saving` is true meanwhile), and create and edit report their own result (`createError`, `editResult`).
  - `SavedListingViewModel`: `savedIds` for the bookmark icons (loaded once per profile when Home starts), `saved` rows for Saved Listings (reloaded when it opens), `toggle()` changes the icon at once and reverts it if the server refuses.
  - `ReservationViewModel`: requests run one at a time behind a `Mutex`; a refresh asked for meanwhile is queued, never dropped. A second write (reserve, cancel, claim) before the first has finished is ignored (`saving`). `reserve()` reports only to the review page (`reserveResult`) and sends the optional note in the reservation's chat. `refresh()` loads My Reservations and My Donations independently (`loadFailed`).
  - `MessageViewModel`: inbox, the open chat (`active`, `messages`, live while open) and `send()`.
  - `NotificationViewModel`: alerts, `unreadCount`, preferences. `start(profileId)` keeps alerts live for the session and re-sends the Realtime token every 5 minutes (Realtime does not refresh the Firebase token itself).
- UI: large files in the root package.
  - `MainActivity.kt`: auth screens, Home feed, `RecommendationCard`, `AvailableFoodCard`, `FoodDetailsScreen` with Reserve (it opens `ReservationReviewDialog`).
  - `ActivityScreen.kt`: tabs My Listings, My Reservations (open and cancelled), My Donations, Purchases (claimed, priced), Claims (claimed, free), and totals computed from claimed reservations. It shows reservation results and errors in a "Reservation" dialog, and reloads the reservation lists when their tab is tapped.
  - `PostScreen.kt`: Create Listing.
  - `ProfileScreen.kt` and `ProfileSections.kt`: profile.
  - `AlertScreen.kt`: real alerts (Phase 4).
  - `ChatScreen.kt`: `ChatScreen` (conversation dialog) and `MessagesScreen` (inbox, page `messages`).
  - `FoodActions.kt`: `FoodActionsDialog` (the pickup location and provider pages; questions go to the real chat through the required `onMessage`) and `ReservationReviewDialog` (the "Review reservation" page, which becomes the "Reserved" receipt).
  - **Much of the UI is still demo/placeholder data** (sample records, hard-coded names and locations, preview-only flows). The feed, listings (with donor names, details and photos), saved listings, reservations (with the review page and receipt), Activity, messages, alerts, notification preferences, the profile name and photo, and the Profile claims and donations use Supabase.

**Identity.**
- The Firebase UID is stored in `users.firebase_uid`.
- `food_posts.donor_id` and `reservations.recipient_id` hold the Supabase profile `users.id` (uuid), **not** the Firebase UID.
- Storage paths start with the Firebase UID.

**Supabase objects** (in `supabase/migrations/`)
- Tables `users`, `food_posts` and `reservations`, each with RLS enabled and `anon` revoked.
- Helper functions: `is_salusalo_firebase_jwt()`, `current_salusalo_profile_id()`, `is_reservation_recipient()`.
- Profile functions (`20260928_profile_updates.sql`): `update_my_profile_name(p_name)` and `set_my_profile_image(p_path)`, SECURITY DEFINER, caller's own row from the token only.
- SECURITY DEFINER RPCs: `create_reservation`, `cancel_reservation`, `claim_reservation`, `get_donor_reservations` (returns `unit_price` since `20260927_activity_prices.sql`; do not re-run the Phase 3 files after it).
- Prices: `food_posts.price` and `reservations.unit_price`. `unit_price` is set only by the `reservations_set_unit_price` trigger from the listing's price; never send or adjust it from the app. Pay at pickup only, no online payments.
- Phase 4: tables `conversations`, `messages`, `notifications`, `notification_preferences`; RPCs `start_listing_conversation`, `open_reservation_conversation`, `send_message`, `mark_conversation_read`, `mark_notifications_read`, `get_my_conversations`; internal `notify_user()` and the `reservations_notify` trigger; `messages` and `notifications` in the `supabase_realtime` publication.
- `food-images` object paths: `<firebaseUid>/<postId>/image.<ext>` (the cover; new listings use `image.jpg`) and, since §11, `photo-2.jpg`–`photo-4.jpg`. `food_posts.image_url` stores the cover's **path** and `extra_image_paths` the others, so the UI must use `displayImageUrl` (and `FoodViewModel.extraPhotoUrls()` for the gallery).
- Phase 3 demo fixes (`20260930_phase3_demo_fixes.sql`, §12): `food_posts.status` may be `REMOVED`; `reservations.cancel_reason` (`LISTING_REMOVED` or null); alert type `RESERVATION_REMOVED`; SECURITY DEFINER `remove_my_food_post(uuid)` (returns `outcome` `DELETED`/`REMOVED` and `cancelled_reservations`) and `get_my_saved_listing_names()`. Clients have no DELETE on `food_posts`.
- Phase 2 demo fixes (`20260929_phase2_listing_details.sql`, §11): `food_posts` columns `pickup_start`, `pickup_end`, `handover` (`MEET_AT_PICKUP_LOCATION` / `COLLECT_FROM_PROVIDER`), `allergens`, `storage_notes`, `extra_image_paths` (≤ 3); SECURITY DEFINER `update_my_food_post(...)` and `get_listing_donor_names(uuid[])`; table `saved_listings` (user, post, `created_at`). Do not re-run the Phase 2 migration after it (it would restore the UPDATE grant and policy).

## Security and ownership rules (keep them)

- Identity comes only from the token: `FirebaseAuth.currentUser` in Kotlin, and `auth.jwt() ->> 'sub'` / `current_salusalo_profile_id()` in SQL. Never trust a user or profile ID passed in from the UI.
- **Listings change only by insert, `update_my_food_post` and `remove_my_food_post`** (plus the reservation RPCs for quantity and status). Clients have no DELETE on `food_posts` either; never add a DELETE grant or policy. `remove_my_food_post` deletes a never-reserved listing, and otherwise marks it `REMOVED`, cancels its open reservations and alerts their recipients. Clients have no UPDATE on `food_posts`; never add an UPDATE grant or policy. The function takes the caller from the token, locks the row, refuses other people's and expired listings, and never changes quantity, unit, status, donor, photos or `created_at`. New insert fields must be sent explicitly (no defaults in `NewFoodPost`).
- Listing photos must be in the uploader's own `<firebaseUid>/<postId>/` folder (checked by the `food_posts` insert policy too).
- **`saved_listings`:** own rows only (select, insert, delete; no UPDATE; nothing for `anon`); insert only for a listing the caller can see that is not their own.
- Donor names come only from `get_listing_donor_names()` (names only, same visibility as the `food_posts` SELECT policy). If that policy changes, change the function too.
- **Reservations change only through the RPCs.** Clients have SELECT only on `reservations`.
  - The RPCs lock the `food_posts` row and update `quantity`/`status` atomically.
  - Never write `reservations` directly, and never adjust listing quantity or status from the app.
- The server enforces these rules; the UI mirrors them but the server stays authoritative:
  - No one can reserve their own post.
  - Only the recipient can cancel, and only while the reservation is `RESERVED`.
  - Only the post's donor can mark a reservation claimed, and only while it is `RESERVED`.
- Donors see recipient **names only**, via `get_donor_reservations()`. Conversation participants see each other's **name only**, via `get_my_conversations()`. Do not widen `users` RLS.
- **Messages and alerts change only through the Phase 4 RPCs and triggers.** Clients have SELECT only on `conversations`, `messages` and `notifications`; never insert them directly. `notify_user()` must stay non-executable for clients.
- Client-created profiles are always `RECIPIENT` (enforced by RLS). The client never sets `role`.
- **`users` rows change only through `update_my_profile_name` and `set_my_profile_image`.** Clients have SELECT and INSERT only; never add an UPDATE grant or policy on `users`, and never let these functions change `role`, `email` or `firebase_uid`.
- `profile-images` stays private and owner-only; a photo path must be inside the owner's Firebase-UID folder.
- Keep `food-images` private and display images through signed URLs. Upload only under the uploader's own Firebase-UID folder.
- Never add a Supabase service-role key or Firebase Admin credentials to the app, docs or commits.
- A schema or RLS change needs a **new** migration file plus a note in `supabase/README.md`. Tell the user it must be run manually.

## Development rules

- **Inspect before modifying.** Read the relevant Kotlin and SQL, and check every claim (including those in docs and handoff notes) against the code.
- **Make minimal, targeted changes** in the surrounding style. Do not reorganize the large UI files, remove demo UI or add libraries unless the task requires it.
- **Reuse existing patterns.**
  - Signed URLs: `withSignedImageUrl(bucket)` in `data/FoodRepository.kt`.
  - Image with fallback: `AvailableFoodCard` and `RecommendationCard` (`MainActivity.kt`), and `RecordPhoto` (`ActivityScreen.kt`).
- **Stay inside the phase the user asks for.** Do not redo Phases 1–4, and do not start Phase 5 or later until the user asks.
- **Keep `docs/PROJECT_STATUS.md` current** whenever work changes status. Never mark anything fixed or tested unless it actually was.

## Testing expectations

- There are no meaningful automated tests yet; only the template `ExampleUnitTest` and `ExampleInstrumentedTest` exist.
- Every change must at least pass `assembleDebug`.
- Changes to auth, listings, reservations or images need a manual run on a device or emulator.
  - Reservation work needs the Account A/B test in `docs/PROJECT_STATUS.md` §4.
  - Also run a regression of the completed phases: sign-up, sign-in, sign-out, create/delete a listing with an image, the feed, the A/B reservation test, and the Phase 4 chat and alerts test (`PROJECT_STATUS.md` §4).
- Report exactly what was built, what was manually tested, and what was not.

## Known current work / issues

Details, test results and line references are in `docs/PROJECT_STATUS.md`.

1. **Phase 3 is complete** (signed off 2026-09-25). Manually verified:
   - the A/B reservation test, including the reservation images;
   - cancel and claim, with their Activity dialog;
   - the Recommended for You image;
   - the missing-image fallback on the feed;
   - the Phase 1/2 regression.
   - Checks with no test record are listed under the results log in `docs/PROJECT_STATUS.md` §4 (for example tab reload and the over-quantity error). Do not treat them as verified.
2. **Committed.** The work since `d4f65fe` (the Recommended and reservation image changes and the load-failure fixes, `PROJECT_STATUS.md` §3.2–§3.4), `docs/` and this file were committed on 2026-09-25. The §3.5 fixes (commit `85c9263`) came from pull request #1, merged into `master` on 2026-09-25 (`85e2bf4`). Commit only when the user asks.
3. **Known issues fixed on 2026-09-25 and verified on a device** (`PROJECT_STATUS.md` §3.5):
   - Reservation details dialogs no longer end with the listing text "This is your live Supabase listing."
   - The `FoodDetailsScreen` header shows the uploaded image, and the screen shows the post's description and pickup location.
   - The Home feed reloads after a cancel. Apart from Phase 4's open chat and alerts, nothing updates in real time across devices (still open).
   - The Home greeting uses the signed-in profile's first name and the time of day instead of "Good evening, Mika". This came after pull request #1 (commit `851e8cb`). The Phase 4 test on 2026-09-25 showed "Good evening, <first name> 👋"; the morning and afternoon greetings were not checked.
4. **Images missing from Storage.** On 2026-09-25 two listings referenced image files that no longer existed in `food-images`; how they were lost is unknown. The app now loads such listings without an image (`PROJECT_STATUS.md` §3.4). By the §3.5 test later that day they were no longer on Home; why was not checked.
5. **Phase 4 (Messaging and Notifications) is complete**, signed off by the developer on 2026-09-25 (`PROJECT_STATUS.md` §8). Status on 2026-09-25:
   - Built: `assembleDebug` passed at `266325b` and at `c7323b8`. The migration was applied to the live project, and `messages` and `notifications` are in the `supabase_realtime` publication.
   - The one-phone A/B Phase 4 test and the regression passed at `266325b`, except that a notification switch turned back on was not saved.
   - At `c7323b8`, on two phones: the retest of the two bugs below passed, and so did live message alerts, live chat, a live reservation alert, and live alerts after more than 1 hour on Home. A new account signed up.
   - The live safety check `supabase/tests/phase4_live_safety_check.sql` (negative tests in the SQL Editor) passed 21 of 21.
   - Not tested: the password-reset email; Phase 4 did not change its code.
   - **Two bugs, fixed (`5c1e89c`) and retested** (`PROJECT_STATUS.md` §8.7):
     1. A notification preference switched back on was never saved. supabase-kt's `Json` leaves out values equal to their default, so `true` was missing from the upsert. The fix puts `@EncodeDefault` on the four booleans in `NotificationPreferences`.
     2. The switches showed on before the saved settings loaded. They now show "Loading your alert settings…" and Save stays disabled until then.
   - The unused `FoodRepository.updateFoodPost()`, which had the same problem with dropped defaults, was removed in §11. Do not turn on `encodeDefaults` globally; that would change the inserts of other models.
6. **Activity Purchases, Claims and listing prices** (`PROJECT_STATUS.md` §9), written on 2026-09-25 on branch `claude/activity-purchases-claims` and merged into `master` through PR #3 on 2026-09-26, together with the Phase 1 demo fixes, before its own device test. Built with AGP, and the migration was applied to the live project on 2026-09-26. The §9.4 two-phone test then passed on 2026-09-26, except one older bug: **the "Reserved" dialog never appears after Reserve**, because `onReserved()` closes the details dialog that holds it (since `d4f65fe`). The developer left it for the Phase 3 demo fixes, together with Buy Now / Claim Now (§9.6). My Reservations no longer lists claimed reservations; they are in Purchases or Claims. Deleting a listing deletes its reservations (cascade), so a buyer's Purchases and Claims history goes with it.
7. **Phase 1 demo fixes are complete** (`PROJECT_STATUS.md` §10): merged into `master` through PR #3 on 2026-09-26, migration applied, and the Phase 1 checks passed on two phones the same day (reported as one result; the regression list was not reported). Built after pinning `googleid` 1.2.0 (1.2.1 is built with Kotlin 2.4 metadata, which Kotlin 2.2.10 cannot read). "Keep me signed in" starts unchecked (changed after PR #3; the tested build had it). `app/google-services.json` (Google enabled, the debug SHA-1 of the developer's PC, Android and web OAuth clients) is committed; Google sign-in works only in builds signed with a registered SHA-1. Google sign-in with the email of an unverified email/password account makes that account Google-only (§10.5).
8. **Phase 2 demo fixes** (`PROJECT_STATUS.md` §11), written on 2026-09-26 on branch `claude/phase2-demo-fixes` from `master` at `8cc18e3` (PR #5). **Built with AGP at `4b6aac6`, migration `20260929_phase2_listing_details.sql` applied to the live project, and the §11.4 two-phone test (steps 1–25) and its regression round (steps 26–39) passed on 2026-09-26.** On the live project, a read-only check confirmed the new objects, that clients have no UPDATE privilege or policy on `food_posts`, and that `anon` cannot call the new functions; the other §11.3 negative checks ran only on a local PostgreSQL copy. Known limits: recipients are not told about edits, photos can't be edited, saved listings don't update live, old listings have no pickup window or handover until edited, nothing sets `EXPIRED` on the server, the Maps button is a text search (Phase 5). Small follow-ups (§11.5): a saved listing the user can no longer see shows as a card without name or photo; listing dates follow the phone's language; Post Listing and Delete errors show the long exception text.
9. **Phase 3 demo fixes** (`PROJECT_STATUS.md` §12), written on 2026-09-26 on branch `claude/phase3-demo-fixes` from `master` at `aa3e823`. **Not built with AGP (only type-checked), migration `20260930_phase3_demo_fixes.sql` not applied, not tested on a device.** The SQL passed functional, concurrency and safety checks on a local PostgreSQL copy only. It fixes the missing "Reserved" dialog (§9.6) with a review page and receipt, removes Buy Now / Claim Now and the demo pickup and chat pages, replaces direct deletes with `remove_my_food_post` (reserved listings are kept as `REMOVED`, so Purchases and Claims history stays), makes the Activity and Profile claims/donations records real, and fixes the §11.5 follow-ups. Known limits: a user with an existing chat about a removed listing keeps it; no restore; no live updates; photo deletion is best effort; **older app builds break after the migration** (no DELETE, unknown `REMOVED` and `RESERVATION_REMOVED` values).
