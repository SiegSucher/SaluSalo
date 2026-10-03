# Admin dashboard: announcements (Phase 6, third slice)

Approved by the developer on 2026-10-04. Branch `claude/admin-announcements` (from `master` at `c240f48`).

## Goal

An admin writes an announcement on the dashboard's Notifications page, picks who gets it, previews it and sends it now. Every account in that audience gets it as an in-app alert in the Salu-Salo app (live when the app is open). The page lists what was sent, to whom, to how many people, by which admin and when.

## Decisions

- **A new alert type, `ANNOUNCEMENT`.** The app's `NotificationType` is a strict enum today: one unknown type makes the whole Alerts list fail to load ("Could not load alerts") and breaks the live stream. So the app update comes first, and it also skips any unknown type from now on.
- **Send now + history.** Title and message, audience Everyone / Donors / Recipients, preview, confirm, send. No scheduling, drafts or unsend.
- **Always delivered.** Announcements ignore the "New messages" and "Reservation updates" switches; there is no new switch.
- **In-app only.** The app has no push notifications; an announcement shows when the app is open or the next time Alerts loads.

## 1. App update (Android)

- `model/AppNotification.kt`: `NotificationType` gains `ANNOUNCEMENT` and `UNKNOWN`. A small serializer turns any type name the app doesn't know into `UNKNOWN` instead of failing.
- `NotificationRepository`: `getNotifications()` and `inserts()` drop `UNKNOWN` alerts, so they never show, count as unread or play the sound.
- `AlertScreen.kt`:
  - Style: the megaphone icon (`Icons.Outlined.Campaign`), label "Announcement".
  - Category "Announcements", with its own filter chip next to Messages and Reservations.
  - Tapping an announcement marks it read and opens a dialog with its title, its full text (rows cut off at 3 lines), "From Salu-Salo · <time>" and OK. The open dialog survives rotation.
  - The header line becomes "Messages, reservation updates and announcements".
- The live alert sound plays for announcements like any other alert (no change to `MainActivity`).
- Unit test `NotificationTypeTest`: `ANNOUNCEMENT` decodes; an unknown name decodes to `UNKNOWN`; a list mixing both decodes.

## 2. Database (one migration, `supabase/migrations/20261014_admin_announcements.sql`, run by hand)

- `notifications` type check: also `ANNOUNCEMENT` (dropped and re-added by column, as in `20260930`).
- Table `announcements`: `id`, `admin_id` (SET NULL on delete), `admin_name` (snapshot), `title` (1–80 characters), `message` (1–500), `audience` (`ALL`/`DONORS`/`RECIPIENTS`), `reach`, `created_at`. RLS on; no privileges for any client.
- `admin_audit_log` checks: action also `ANNOUNCEMENT_SENT`, target type also `ANNOUNCEMENT`.
- Audiences follow the Users page's filters (`admin_user_matches`), so the numbers agree:
  - Everyone: every account, admins included.
  - Donors: has posted at least one listing.
  - Recipients: has never posted and isn't an admin.
- New admin-only functions, each starting with `admin_require()`; EXECUTE for `authenticated` only:
  - `admin_audience_sizes()`: `{ALL, DONORS, RECIPIENTS}` counts for the form.
  - `admin_send_announcement(p_title, p_message, p_audience)`:
    - Trims the title and message and checks them ("Add a title.", "Write the message.", the length limits, "Choose who gets it.").
    - Rate limit: at most 3 announcements per admin per 10 minutes, counted under a per-admin advisory lock (`salusalo-announce:<admin id>`).
    - Inserts one `ANNOUNCEMENT` alert per account in the audience in a single statement. It inserts them itself rather than through `notify_user()`, which would apply the switches and cut the text to 300 characters.
    - Refuses an empty audience ("No one is in this audience yet.").
    - Saves the `announcements` row with the reach, writes the audit row ("To 12 people (everyone)."), and returns `{id, reach}`, all in one transaction.
  - `admin_announcements(p_page, p_page_size)`: the history, newest first, paged like the other lists.
  - `admin_announcement(p_id)`: one announcement, for the details panel and the Audit log's link.
- Check `supabase/tests/admin_announcements_live_safety_check.sql`: fails before the migration, passes after it, and always ends with an error that undoes everything.

## 3. Dashboard (`admin/`)

- `data.js`: `audienceSizes()`, `sendAnnouncement({title, message, audience})`, `announcements(q)`, `announcement(id)`; `text.audience`; audit action `ANNOUNCEMENT_SENT` → "Sent announcement".
- `pages/notifications.js`, rebuilt from the sample version (`e24c8d7`) minus the status filter, scheduling, drafts and the admin alerts tab:
  - The history list (ListView, paged), with title, message, audience, reached, by and date.
  - "New announcement" opens the form:
    - title and message with counters (80 / 500);
    - the audience as three choices with live counts ("Everyone · 12 people");
    - a preview drawn like the app's alert row.
  - Send asks "Send to 12 people?". The confirm text says "It goes to every account in this audience right away. You can’t unsend it. Phones need the latest app version." The result says "Announcement sent to 12 people."
  - A row opens the details panel: the full message, the preview, audience, reached, sent, by.
- `audit.js`: an icon for "Sent announcement", and target type `ANNOUNCEMENT` links to `#/notifications/<id>`.
- `data.test.js`: send and history checks.

## 4. Rollout order

1. Build the app; install it on **every** phone that uses the live project (yours and Mika's).
2. Run the migration and the safety check.
3. Deploy the dashboard.
4. Send a test announcement and check both phones.

A phone still on an older build shows "Could not load alerts" once it has an announcement, until it is updated.

## 5. Testing

- JVM unit tests (`NotificationTypeTest` plus the existing 68), `assembleDebug`, `lintDebug`.
- Local PostgreSQL 16 dry run: the migration twice, the new safety check (fail before, pass after), and the earlier admin checks still pass.
- Node checks in `admin/tests/`.
- The developer's manual test on two phones and the dashboard: an ordered checklist.

## 6. Known limits

- No push while the app is closed; no scheduling, drafts, unsend or per-person opt-out.
- The reach counts accounts, not phones, at the moment of sending.
- Older builds can't show announcements (see the rollout order).
