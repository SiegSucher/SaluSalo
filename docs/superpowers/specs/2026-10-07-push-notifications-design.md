# Push notifications (phone pop-ups) and staying signed in

Agreed with the developer on 2026-10-07. Branch `claude/push-notifications` (from `claude/portrait-only` at `387e0bb`, which holds §34 portrait-only, not yet tested on a phone; one PR brings both into `master`).

## Goal

Every in-app alert (a message, a reservation update, an admin announcement) also shows as an Android notification that pops up on the phone, whether Salu-Salo is closed, in the background or open. Tapping it opens the same place as tapping that alert on the Alerts page. Also: an account stays signed in on a phone until the user taps Log out; the "Keep me signed in" checkbox goes.

## Decisions

- **Pop-ups also while the app is open (A),** except a message in the chat that is open on screen.
- **A tap opens the alert's own place:** the chat, the reservation, or the announcement's text (the Alerts page's routing).
- **Push follows the alert rows.** Every row inserted into `notifications` is pushed; nothing else is. The "New messages" and "Reservation updates" switches already stop those rows (`notify_user()`), so they stop the pop-ups too. Announcements reach everyone, as in the app.
- **Sending path:** an AFTER INSERT trigger on `notifications` calls a new Edge Function `send-push` through `pg_net`; the function sends through Firebase Cloud Messaging (FCM HTTP v1). Rejected: a dashboard Database Webhook (not in the repo, its secret in plain text in the webhook settings) and a Cron poll (up to a minute late).
- **Data-only FCM messages, high priority.** The app builds every notification itself, so it can leave out the open chat, keep one sound per alert and honour the "Notification sound" switch.
- **No "Keep me signed in".** Its only effect was signing out on a fresh start, which would also have stopped the pop-ups. Sessions now end only through Log out (or Firebase ending them, as today).
- **The alert's text passes through Google's FCM servers** on its way to the phone (the title and up to 300 characters of text). Firebase is already the app's sign-in provider.

## 1. Database (one migration, `supabase/migrations/20261016_push_notifications.sql`, run by hand)

- `create extension if not exists pg_net with schema extensions;`
- Table `push_tokens`: `token text primary key` (1–4096 characters, FCM's token characters only), `user_id uuid not null` → `users(id) on delete cascade`, `updated_at timestamptz not null default now()`; index on `(user_id, updated_at desc)`. RLS on; **no privileges for any client** (revoke all from `anon` and `authenticated`, no policies).
- `register_my_push_token(p_token text)`, SECURITY DEFINER, EXECUTE for `authenticated` only (revoked from `public` and `anon`):
  - "Not authenticated" without a Salu-Salo Firebase token, "Profile not found" without a profile; the account comes from the token only.
  - Checks the token's length and characters ("Invalid push token.").
  - Upsert by token: a token already saved for another account moves to the caller (one phone, a new account signed in); `updated_at = now()`.
  - Keeps the caller's newest 5 tokens and deletes the rest of the caller's own.
- Internal trigger function `push_new_notification()` (SECURITY DEFINER, executable by no client) and the trigger `notifications_push` (AFTER INSERT, for each row) on `notifications`:
  - Reads the Vault secrets `push_function_url` and `push_webhook_secret` (`vault.decrypted_secrets`), which the developer adds by hand; either one missing → does nothing.
  - Reads the account's tokens updated within 60 days (newest 5); none → does nothing.
  - `net.http_post` to the function URL with the header `x-push-secret` and the body `{uid, tokens, alert: {id, type, title, body, conversation_id, reservation_id, food_post_id}}`, where `uid` is the account's `users.firebase_uid`. `pg_net` sends it only after the alert's transaction commits.
  - Wrapped in `begin … exception when others then raise warning` with the SQLSTATE only: a push problem can never make an alert, a message or a reservation fail.
- Check `supabase/tests/push_notifications_live_safety_check.sql` (the usual pattern: fails before the migration, passes after it, always ends with an error that undoes everything): no client privileges on `push_tokens`; the RPC's grants; refusals without a token or profile; a moved token; the 5-token cap; the trigger present and an alert insert still succeeding with no Vault secrets.

## 2. Edge Function `send-push` (`supabase/functions/send-push/index.ts`)

- Self-contained, no dependencies, deployed from the dashboard editor with **JWT verification off** (the database's call carries no Supabase JWT; the shared secret protects it).
- POST only. The header `x-push-secret` must equal the secret `PUSH_WEBHOOK_SECRET` (constant-time comparison); otherwise 401 and nothing is sent.
- Validates the body: `uid` a string, `tokens` an array of 1–5 strings, `alert.id`, `alert.type`, `alert.title`, `alert.body` strings; 400 otherwise.
- Gets a Google access token for the service account in the secret **`FIREBASE_PUSH_KEY`** (scope `https://www.googleapis.com/auth/firebase.messaging`), cached until 5 minutes before it expires. The service-account code is copied from `set-firebase-role` because dashboard-deployed functions can't share files.
- Sends one FCM v1 message per token to `projects/salusalo/messages:send`: `data` (all strings: `alert_id`, `type`, `title`, `body` cut to 300 characters, `conversation_id`, `reservation_id`, `food_post_id`, `uid`), `android: {priority: "HIGH", ttl: "86400s"}`.
- Replies `{sent, failed}`. Logs only counts and HTTP statuses: never a token, the text, the key, an access token, Google's error body or more than 6 characters of a UID.
- Dead tokens are not deleted here (the function has no database access by design); the 60-day filter and the 5-token cap bound them.
- Deno tests `supabase/functions/tests/send-push-test.ts`: a wrong or missing secret sends nothing; a bad body is refused; one FCM call per token with the expected message; a failing token doesn't stop the others; a missing or invalid key is a setup error without details.

## 3. App (Android)

- **Library:** `com.google.firebase:firebase-messaging` through the existing Firebase BoM (one line in `libs.versions.toml`, one in `app/build.gradle.kts`).
- **Manifest:** `POST_NOTIFICATIONS`; the service `.PushService` (not exported) with the `com.google.firebase.MESSAGING_EVENT` filter.
- **`PushNotifications.kt`** (new, root package) with `PushService`, a `FirebaseMessagingService`:
  - `onNewToken`: when a Firebase user is signed in, `NotificationRepository.registerPushToken(token)` (quiet on failure).
  - `onMessageReceived`: drops the message when no Firebase user is signed in on the phone or its `uid` isn't that user's, and when it is a message of the chat open on screen while the app is resumed. Otherwise posts a notification:
    - channel `alerts` ("Alerts", importance HIGH, so it pops up), created before each post (cheap and idempotent);
    - small icon: the existing monochrome launcher silhouette; title and text from the alert; `BigTextStyle`; auto-cancel;
    - tag: the conversation ID for messages (a chat's newest message replaces its older one), else the alert ID;
    - silent (`setSilent(true)`) when the "Notification sound" switch is off;
    - tap: a `PendingIntent` to `MainActivity` (`FLAG_IMMUTABLE`, single top) carrying the alert's ID, type, conversation ID and reservation ID.
- **Shared, process-wide state** (`AlertPresence`, a small `object` in `PushNotifications.kt`): whether Home is resumed and which chat is open, set by `HomeScreen`.
- **Pure rules for tests:** `pushTapFrom(data)` (reading the alert from the FCM data) and `shouldShowPush(…)` (the drop rules). Unit test `PushRulesTest`.
- **One routing function:** `HomeScreen`'s `openAlert(type, alertId, conversationId, reservationId)` replaces the Alerts page's four callbacks; a tapped alert and a tapped pop-up both call it, so they can't disagree.
- **Registering the token:** when Home starts for a profile, `FirebaseMessaging.getToken()` → `register_my_push_token` (quiet). `NotificationRepository.registerPushToken()`.
- **Sign-out:** whenever the app sees Firebase sign out (Log out, or the session ending), `FirebaseMessaging.deleteToken()` in the background, so the old token stops working; the next sign-in gets a new one. If that fails offline, the `uid` check above still drops the old account's pop-ups.
- **Tap handling:** `MainActivity` reads the tap's extras in `onCreate` and `onNewIntent` into Compose state (consumed once, so rotation doesn't repeat it). When Home is open for a signed-in profile it marks the alert read and routes with `openAlert`: a message opens the chat; a reservation alert opens the same reservation page as on Alerts; an announcement switches to Alerts and opens its dialog (`AlertsScreen` gains an announcement ID to open). Not signed in: the app just opens.
- **Permission (Android 13+):** asked once, the first time Home opens after this update (`push_permission_asked` in `salusalo_session`). Profile → Notifications shows "Phone notifications are off" with **Turn on** (the phone's notification settings for Salu-Salo) while they are off, re-checked when the page is shown again.
- **In-app sound:** unchanged rules (resumed, not the open chat, switch on, normal ringer), and only while the phone doesn't show Salu-Salo's notifications (`phoneNotificationsOn()`) or no push has arrived in the last 14 days; otherwise the pop-up plays the sound, so an alert doesn't sound twice (changed after the final review, so a push side that isn't working never silences the app). Opening a chat also clears its pop-up from the shade.
- **"Keep me signed in" removed:** the checkbox on Sign In and Create Account, the sign-out in `MainActivity.onCreate`, `keepSignedIn()`/`setKeepSignedIn()` and the parameters that carried the choice. `welcomeSeen()` still treats a saved `keep_signed_in` as "seen" (installs from older builds); the stored value is otherwise ignored.

## 4. Developer's one-time setup (exact steps go in `supabase/README.md`)

1. Google Cloud console, project `salusalo`: check that "Firebase Cloud Messaging API" (V1) is enabled.
2. A custom IAM role with only `cloudmessaging.messages.create`; a new service account with only that role; a JSON key stored as the Supabase Edge Function secret `FIREBASE_PUSH_KEY`; then delete the downloaded key file. (The `set-firebase-role` service account keeps its two Auth permissions and nothing else.)
3. Make a long random secret. Store it as the Edge Function secret `PUSH_WEBHOOK_SECRET` and in Vault as `push_webhook_secret`. Store the function's URL (`https://<project-ref>.supabase.co/functions/v1/send-push`) in Vault as `push_function_url`.
4. Deploy `send-push` from the dashboard editor with JWT verification off.
5. Run the migration, then the live safety check.

Order doesn't matter: before the migration the app's token call fails quietly; before the Vault secrets the trigger sends nothing. Older builds are unaffected (they never register a token).

## 5. Testing

- `assembleDebug`, `testDebugUnitTest` (95 + `PushRulesTest`), `lintDebug` 0 errors.
- Deno tests for `send-push` (and the existing 21 still pass).
- SQL: a local dry run where possible, then the live safety check after the developer runs the migration.
- Phone test (the developer runs it; two phones or two accounts), short, A → B → A, including the three §34 portrait steps:
  - the permission prompt once; Sign In without "Keep me signed in"; swiping the app away and reopening keeps the account signed in;
  - with the app swiped away, closed, in the background and open: a message, a reservation and an announcement pop up; no pop-up for a message in the open chat; one sound only while open;
  - tapping each opens the chat, the reservation and the announcement;
  - "Notification sound" off → silent pop-ups; "New messages" off → no message pop-up;
  - Log out → no pop-ups for that account on that phone; signing in as the other account → its pop-ups only;
  - Xiaomi: if nothing arrives after swiping the app away, turn on Autostart for Salu-Salo; if it shows only in the shade, turn on Floating notifications.

## Known limits

- Xiaomi, Redmi and POCO phones (and some other brands) may block pop-ups for an app swiped away from Recents until Autostart is allowed; no app can get around it.
- The alert text passes through Google's FCM.
- Dead tokens stay in `push_tokens` until the 60-day filter and the 5-token cap ignore them.
- An announcement makes one `pg_net` call per account (fine for the current number of users).
- Someone who somehow learned another phone's FCM token could register it to their own account; tokens stay on the phone and are never shown or sent anywhere else by the app.
- A tap after the account signed out, or on a phone now signed in to another account, just opens the app.
