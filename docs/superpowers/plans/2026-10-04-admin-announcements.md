# Admin Announcements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admins send in-app announcements (title, message, audience) from the web dashboard to accounts in the Salu-Salo app, and see what was sent.

**Architecture:** The app learns a new alert type `ANNOUNCEMENT` and skips any type it doesn't know. A new migration adds the type, an `announcements` table and four admin-only SECURITY DEFINER functions. `admin_send_announcement` inserts one alert per account in the audience, in one transaction with its history row and audit row. The dashboard's Notifications page calls those functions through `SaluData`.

**Tech Stack:** Kotlin + Compose + kotlinx.serialization (app), PostgreSQL 16 / Supabase (SQL), static JS with supabase-js 2 (dashboard), Node `vm` checks.

**Spec:** `docs/superpowers/specs/2026-10-04-admin-announcements-design.md`

## Global Constraints

- Title 1–80 characters and message 1–500 characters, both after trimming; audience `ALL` / `DONORS` / `RECIPIENTS` (labels Everyone / Donors / Recipients).
- Server texts, exact:
  - 'Add a title.'
  - 'The title can be at most 80 characters.'
  - 'Write the message.'
  - 'The message can be at most 500 characters.'
  - 'Choose who gets it.'
  - 'No one is in this audience yet.'
  - 'You can send up to 3 announcements every 10 minutes. Please wait a few minutes.'
- Rate limit: 3 announcements per admin per 10 minutes, under `pg_advisory_xact_lock(hashtextextended('salusalo-announce:' || admin id, 0))`.
- Every new client function: `revoke all … from public`, `revoke all … from anon`, `grant execute … to authenticated`. Internal helpers: revoked from all three. Clients get no privileges on `announcements`.
- Announcements ignore the alert switches and never go through `notify_user()`.
- App texts:
  - label "Announcement";
  - chip "Announcements";
  - dialog footer "From Salu-Salo · <time>";
  - Alerts header "Messages, reservation updates and announcements".
- Dashboard texts:
  - confirm title "Send to N people?";
  - confirm text "It goes to every account in this audience right away. You can’t unsend it. Phones need the latest app version.";
  - toast "Announcement sent to N people.";
  - audit label "Sent announcement".
- No commits or pushes unless the developer asks (CLAUDE.md). Never print `local.properties` or `admin/js/config.js` values.
- The developer applies the migration by hand, **only after every phone has the new app build**. Never apply it to the live project from here.

## Review Focus

1. A padded title ("  Hi  ") is stored trimmed, and a whitespace-only message is refused with "Write the message." (not stored blank). Pinned by safety checks 3 and 7 (Task 2).
2. A 500-character message reaches the phone whole, not cut to 300 like `notify_user()` would. Pinned by safety check 3 (Task 2).
3. An account with both alert switches off still gets the announcement. Pinned by safety check 3 (Task 2).
4. A list with an alert of a type this build doesn't know still loads and drops only that alert. Pinned by `NotificationTypeTest` test 3 (Task 1).
5. A rate-limit or empty-audience refusal shows the server's own text in the dashboard, not "Unable to send…". Pinned by the `data.test.js` check that `DataError.code` is kept (Task 3).

---

### Task 1: App update: Announcement alerts, unknown types skipped

**Files:**
- Modify: `app/src/main/java/com/example/salusalo/model/AppNotification.kt`
- Modify: `app/src/main/java/com/example/salusalo/data/NotificationRepository.kt`
- Modify: `app/src/main/java/com/example/salusalo/AlertScreen.kt`
- Test: `app/src/test/java/com/example/salusalo/NotificationTypeTest.kt` (create)

**Interfaces:**
- Produces:
  - `NotificationType.ANNOUNCEMENT` and `NotificationType.UNKNOWN`;
  - `object NotificationTypeSerializer : KSerializer<NotificationType>`;
  - `val AppNotification.isKnown: Boolean`.

Build commands (PowerShell, repo root): `$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"` first.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/example/salusalo/NotificationTypeTest.kt`:

```kotlin
package com.example.salusalo

import com.example.salusalo.model.AppNotification
import com.example.salusalo.model.NotificationType
import com.example.salusalo.model.isKnown
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Alerts decode leniently: a type added on the server later never breaks the Alerts list (§27). */
class NotificationTypeTest {
    private fun alert(id: String, type: String) =
        """{"id":"$id","user_id":"u1","type":"$type","title":"T","body":"B","created_at":"2026-10-04T08:00:00Z"}"""

    @Test
    fun announcementDecodes() {
        val decoded = Json.decodeFromString(AppNotification.serializer(), alert("a1", "ANNOUNCEMENT"))
        assertEquals(NotificationType.ANNOUNCEMENT, decoded.type)
    }

    @Test
    fun typeThisBuildDoesNotKnowDecodesAsUnknown() {
        val decoded = Json.decodeFromString(AppNotification.serializer(), alert("a1", "SOMETHING_NEW"))
        assertEquals(NotificationType.UNKNOWN, decoded.type)
    }

    @Test
    fun listWithAnUnknownTypeStillLoadsAndDropsOnlyThatAlert() {
        val json = listOf(alert("a1", "MESSAGE"), alert("a2", "SOMETHING_NEW"), alert("a3", "ANNOUNCEMENT"))
            .joinToString(",", "[", "]")
        val list = Json.decodeFromString(ListSerializer(AppNotification.serializer()), json)
        assertEquals(listOf("a1", "a3"), list.filter { it.isKnown }.map { it.id })
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.example.salusalo.NotificationTypeTest"`
Expected: FAIL. The compile fails with unresolved `ANNOUNCEMENT`, `UNKNOWN` and `isKnown`.

- [ ] **Step 3: The lenient type**

In `model/AppNotification.kt` replace the enum line with the code below, and add the imports `kotlinx.serialization.KSerializer`, `kotlinx.serialization.descriptors.PrimitiveKind`, `kotlinx.serialization.descriptors.PrimitiveSerialDescriptor`, `kotlinx.serialization.encoding.Decoder` and `kotlinx.serialization.encoding.Encoder`.

```kotlin
@Serializable(with = NotificationTypeSerializer::class)
enum class NotificationType {
    MESSAGE, RESERVATION_CREATED, RESERVATION_CANCELLED, RESERVATION_CLAIMED, RESERVATION_REMOVED,
    /** An admin's announcement from the web dashboard (§27). */
    ANNOUNCEMENT,
    /** A type added on the server after this build; never shown (NotificationRepository drops it). */
    UNKNOWN
}

/** Alerts of an unknown type: true for every type this build can show. */
val AppNotification.isKnown: Boolean get() = type != NotificationType.UNKNOWN

/**
 * Decodes a type name this build doesn't know as UNKNOWN instead of failing, so one
 * new alert type can't break the whole Alerts list or the live alerts (§27).
 */
object NotificationTypeSerializer : KSerializer<NotificationType> {
    override val descriptor = PrimitiveSerialDescriptor("com.example.salusalo.model.NotificationType", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: NotificationType) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): NotificationType {
        val name = decoder.decodeString()
        return NotificationType.entries.firstOrNull { it.name == name && it != NotificationType.UNKNOWN }
            ?: NotificationType.UNKNOWN
    }
}
```

- [ ] **Step 4: Run the test and see it pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --offline --tests "com.example.salusalo.NotificationTypeTest"`
Expected: 3 tests PASS. The build may fail first on `AlertScreen.kt`, because `style()` must be exhaustive. In that case do Step 6's `style()` change first, then re-run.

- [ ] **Step 5: The repository drops unknown alerts**

In `data/NotificationRepository.kt`:
- Add the imports `com.example.salusalo.model.isKnown` and `kotlinx.coroutines.flow.filter`.
- Change the class comment's first sentence to: `The database creates them (new messages, reservation changes and, since §27, admins' announcements); the client can only read its own and mark them read. Alerts of a type this build doesn't know are dropped here.`
- Change the two functions:

```kotlin
    suspend fun getNotifications(): List<AppNotification> =
        supabase.from("notifications").select {
            order("created_at", Order.DESCENDING)
            limit(100)
        }.decodeList<AppNotification>().filter { it.isKnown }

    /** New alerts for this profile while the app is open; [onRejoined] after a lost connection. */
    fun inserts(profileId: String, onRejoined: suspend () -> Unit): Flow<AppNotification> =
        realtimeInserts<AppNotification>("notifications", "user_id", profileId, onRejoined).filter { it.isKnown }
```

- [ ] **Step 6: Alerts shows announcements**

In `AlertScreen.kt`:

1. Imports: add `androidx.compose.foundation.rememberScrollState`, `androidx.compose.foundation.verticalScroll` and `androidx.compose.material.icons.outlined.Campaign`.
2. `category()`:

```kotlin
private fun NotificationType.category() = when (this) {
    NotificationType.MESSAGE -> "Messages"
    NotificationType.ANNOUNCEMENT -> "Announcements"
    else -> "Reservations"
}
```

3. `style()`: add after the `RESERVATION_REMOVED` branch:

```kotlin
    NotificationType.ANNOUNCEMENT ->
        AlertStyle(Icons.Outlined.Campaign, Color.White, SaluColors.DarkGreen, "Announcement", SaluColors.DarkGreen)
    // Never shown: NotificationRepository drops alerts of unknown types.
    NotificationType.UNKNOWN ->
        AlertStyle(Icons.Outlined.NotificationsNone, SaluColors.Gray, SaluColors.Neutral, "Alert")
```

4. In `AlertsScreen`, after `var showSettings …`:

```kotlin
    // The announcement whose full text is open; by ID, so it stays open through rotation.
    var openAnnouncementId by rememberSaveable { mutableStateOf<String?>(null) }
```

5. Header text `"Messages and updates on your reservations"` → `"Messages, reservation updates and announcements"`.
6. Filter chips: add `"Announcements" to Icons.Outlined.Campaign` after the Reservations entry.
7. Empty-state title:

```kotlin
                    title = when (filter) {
                        "All" -> "No alerts to show"
                        "Announcements" -> "No announcements"
                        else -> "No ${filter.lowercase(Locale.ENGLISH)} alerts"
                    },
```

8. The row's `onClick` `when`: add before `else ->`:

```kotlin
                                NotificationType.ANNOUNCEMENT ->
                                    openAnnouncementId = alert.id
```

9. After the `if (showSettings) { … }` block:

```kotlin
    alerts.firstOrNull { it.id == openAnnouncementId }?.let { alert ->
        AlertDialog(
            onDismissRequest = { openAnnouncementId = null },
            icon = { Icon(Icons.Outlined.Campaign, contentDescription = null, tint = SaluColors.DarkGreen) },
            title = { Text(alert.title) },
            text = {
                // Rows show 3 lines; the whole message (up to 500 characters) is here.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(alert.body, style = SaluText.Body, color = SaluColors.DarkGreen)
                    Spacer(Modifier.height(12.dp))
                    Text("From Salu-Salo · " + formatAlertTime(alert.createdAt), style = SaluText.Meta, color = SaluColors.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { openAnnouncementId = null }) { Text("OK") }
            }
        )
    }
```

- [ ] **Step 7: Full unit tests, build and lint**

Run: `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline`
Expected: BUILD SUCCESSFUL, 71 tests (68 + 3) passing, lint 0 errors.

---

### Task 2: Database: migration and live safety check

**Files:**
- Create: `supabase/tests/admin_announcements_live_safety_check.sql`
- Create: `supabase/migrations/20261014_admin_announcements.sql`
- Modify (scratchpad, not the repo): `<scratchpad>/pg/run.sh`, to skip a migration with `SKIP=…`

**Interfaces:**
- Consumes (from `20261012`):
  - `admin_require() returns users`
  - `admin_user_matches(users, text, text)` (filters `ALL`/`DONOR`/`RECIPIENT`)
  - `admin_ms(timestamptz)`
  - `admin_paging(int, int, bigint)`
  - `admin_log(users, text, text, uuid, text, text)`
- Produces:
  - `admin_audience_sizes() → {ALL, DONORS, RECIPIENTS}`
  - `admin_send_announcement(p_title text, p_message text, p_audience text) → {id, reach}`
  - `admin_announcements(p_page int, p_page_size int) → {rows, total, page, pageSize, pages}`
  - `admin_announcement(p_id uuid) → row | null`
  - Each row is `{id, at (ms), title, message, audience, reach, admin: {id, name}}`.
  - Audit action `ANNOUNCEMENT_SENT`, target type `ANNOUNCEMENT`.

Local database: `S=<scratchpad>/pg`. Start it if it isn't running: `$S/pgsql/bin/pg_ctl.exe -D $S/data -l $S/pg.log -o "-p 5433" start`.

- [ ] **Step 1: Let run.sh skip a migration**

In `$S/run.sh`, make the first line of the `for f in $FILES; do` loop:

```bash
  if [ -n "${SKIP:-}" ] && [[ "$f" == *"$SKIP"* ]]; then continue; fi
```

- [ ] **Step 2: Write the safety check**

`supabase/tests/admin_announcements_live_safety_check.sql`:

```sql
-- Salu-Salo admin announcements: safety check for the live database (PROJECT_STATUS.md §27).
--
-- Run it in the Supabase dashboard after migrations/20261014_admin_announcements.sql:
-- SQL Editor -> New query -> paste -> Run. Leave the role on "postgres". Run it
-- before anyone sends a real announcement: an admin who sent one in the last 10
-- minutes would reach the limit this check tests sooner.
--
-- It takes the donor of the newest listing, the newest other account (the
-- "recipient") and, if there is one, a third account (the "admin"; otherwise the
-- recipient is also the admin), and sets their roles itself. The admin sends test
-- announcements to each audience; each must reach exactly the accounts in it.
--
-- It ALWAYS ENDS WITH AN ERROR, ON PURPOSE. The error undoes everything the check
-- did (roles, announcements, alerts, audit rows), so nothing is saved and no phone
-- gets a test announcement. The error text is the report; it starts with
-- "SAFETY CHECK FINISHED".

do $$
declare
  v_donor    public.users;
  v_rec      public.users;
  v_admin    public.users;
  c_donor    text;
  c_rec      text;
  c_admin    text;
  c_iss      text := 'https://securetoken.google.com/salusalo';
  n_all      bigint;
  n_donors   bigint;
  n_recips   bigint;
  v_msg      text := rpad('Safety check message for everyone. ', 500, 'x');
  v_id_all   uuid;
  v_sent     integer;
  v_j        jsonb;
  v_one      jsonb;
  v_audit    jsonb;
  n          bigint;
  n2         bigint;
  v_bad      text;
  t_title    text[];
  t_msg      text[];
  t_aud      text[];
  t_want     text[];
  v_label    text;
  v_ok       boolean;
  v_note     text;
  a_label    text[] := '{}';
  a_ok       boolean[] := '{}';
  a_note     text[] := '{}';
  v_report   text := '';
  v_pass     integer := 0;
  v_fail     integer := 0;
  v_skip     integer := 0;
begin
  select u.* into v_donor from public.users u
  join public.food_posts p on p.donor_id = u.id
  order by p.created_at desc limit 1;
  if v_donor.id is null then
    select * into v_donor from public.users order by created_at desc limit 1;
  end if;
  select * into v_rec from public.users
  where id is distinct from v_donor.id order by created_at desc limit 1;
  select * into v_admin from public.users
  where id is distinct from v_donor.id and id is distinct from v_rec.id order by created_at desc limit 1;
  if v_admin.id is null then
    v_admin := v_rec;
  end if;

  update public.users set role = 'RECIPIENT' where id in (v_donor.id, v_rec.id);
  update public.users set role = 'ADMIN' where id = v_admin.id;

  c_donor := json_build_object('sub', v_donor.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_rec   := json_build_object('sub', v_rec.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;
  c_admin := json_build_object('sub', v_admin.firebase_uid, 'role', 'authenticated', 'iss', c_iss, 'aud', 'salusalo')::text;

  -- The audiences, counted here independently of the functions under test.
  select count(*),
         count(*) filter (where exists (select 1 from public.food_posts p where p.donor_id = u.id)),
         count(*) filter (where u.role <> 'ADMIN' and not exists (select 1 from public.food_posts p where p.donor_id = u.id))
    into n_all, n_donors, n_recips
  from public.users u;

  -- 1
  v_label := 'Sanity: the admin reads the audience sizes, and they match the accounts';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_audience_sizes();
    perform set_config('role', 'postgres', true);
    v_ok := (v_j ->> 'ALL')::bigint = n_all and (v_j ->> 'DONORS')::bigint = n_donors
        and (v_j ->> 'RECIPIENTS')::bigint = n_recips;
    v_note := format('sizes %s; expected everyone %s, donors %s, recipients %s', v_j, n_all, n_donors, n_recips);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 2
  v_label := 'An empty audience is refused (everyone who never posted made an admin for a moment)';
  v_ok := false;
  begin
    update public.users u set role = 'ADMIN'
    where not exists (select 1 from public.food_posts p where p.donor_id = u.id);
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    perform public.admin_send_announcement('Safety check: nobody', 'Nobody should get this.', 'RECIPIENTS');
    perform set_config('role', 'postgres', true);
    raise exception 'it sent to an empty audience';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'No one is in this audience yet.';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 3
  v_label := 'Everyone: each account gets exactly one alert with the trimmed title and the whole 500-character text, even with alert switches off';
  v_ok := false;
  begin
    insert into public.notification_preferences (user_id, message_alerts, reservation_alerts)
    values (v_rec.id, false, false)
    on conflict (user_id) do update set message_alerts = false, reservation_alerts = false;
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_send_announcement('  Safety check: everyone  ', v_msg, 'ALL');
    perform set_config('role', 'postgres', true);
    v_id_all := (v_j ->> 'id')::uuid;
    select count(*), count(distinct user_id) into n, n2 from public.notifications
    where type = 'ANNOUNCEMENT' and title = 'Safety check: everyone' and body = v_msg;
    v_ok := (v_j ->> 'reach')::bigint = n_all and n = n_all and n2 = n_all
        and exists (select 1 from public.notifications where user_id = v_rec.id
                    and type = 'ANNOUNCEMENT' and title = 'Safety check: everyone');
    v_note := format('reach %s, alerts %s for %s accounts, expected %s', v_j ->> 'reach', n, n2, n_all);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 4
  v_label := 'Donors: exactly the accounts that have posted get it';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_send_announcement('Safety check: donors', 'For donors.', 'DONORS');
    perform set_config('role', 'postgres', true);
    select count(*) into n from (
      (select user_id from public.notifications where type = 'ANNOUNCEMENT' and title = 'Safety check: donors')
      except (select donor_id from public.food_posts)) x;
    select count(*) into n2 from (
      (select donor_id from public.food_posts)
      except (select user_id from public.notifications where type = 'ANNOUNCEMENT' and title = 'Safety check: donors')) x;
    v_ok := n = 0 and n2 = 0 and (v_j ->> 'reach')::bigint = n_donors;
    v_note := format('reach %s (expected %s), extra %s, missing %s', v_j ->> 'reach', n_donors, n, n2);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 5
  v_label := 'Recipients: exactly the accounts that never posted and are not admins get it';
  v_ok := false;
  begin
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    v_j := public.admin_send_announcement('Safety check: recipients', 'For recipients.', 'RECIPIENTS');
    perform set_config('role', 'postgres', true);
    select count(*) into n from (
      (select user_id from public.notifications where type = 'ANNOUNCEMENT' and title = 'Safety check: recipients')
      except (select u.id from public.users u where u.role <> 'ADMIN'
              and not exists (select 1 from public.food_posts p where p.donor_id = u.id))) x;
    select count(*) into n2 from (
      (select u.id from public.users u where u.role <> 'ADMIN'
       and not exists (select 1 from public.food_posts p where p.donor_id = u.id))
      except (select user_id from public.notifications where type = 'ANNOUNCEMENT' and title = 'Safety check: recipients')) x;
    v_ok := n = 0 and n2 = 0 and (v_j ->> 'reach')::bigint = n_recips;
    v_note := format('reach %s (expected %s), extra %s, missing %s', v_j ->> 'reach', n_recips, n, n2);
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    -- A project where every account has posted has no recipients: then refusing is right.
    v_ok := n_recips = 0 and sqlerrm = 'No one is in this audience yet.';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 6
  v_label := 'Wrong input is refused with the dashboard''s texts';
  v_ok := false; v_bad := '';
  t_title := array[null, repeat('t', 81), 'Safety check', 'Safety check', 'Safety check'];
  t_msg   := array['x', 'x', '   ', repeat('m', 501), 'x'];
  t_aud   := array['ALL', 'ALL', 'ALL', 'ALL', 'ADMINS'];
  t_want  := array['Add a title.', 'The title can be at most 80 characters.', 'Write the message.',
                   'The message can be at most 500 characters.', 'Choose who gets it.'];
  perform set_config('request.jwt.claims', c_admin, true);
  perform set_config('role', 'authenticated', true);
  for i in 1 .. 5 loop
    begin
      perform public.admin_send_announcement(t_title[i], t_msg[i], t_aud[i]);
      v_bad := v_bad || i || ' sent; ';
    exception when others then
      if sqlerrm <> t_want[i] then v_bad := v_bad || i || ': ' || sqlerrm || '; '; end if;
    end;
  end loop;
  perform set_config('role', 'postgres', true);
  v_ok := v_bad = '';
  v_note := case when v_ok then 'all five refused' else v_bad end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 7
  v_label := 'A fourth announcement within 10 minutes is refused';
  v_ok := false;
  begin
    select count(*) into v_sent from public.announcements
    where admin_id = v_admin.id and created_at > now() - interval '10 minutes';
    perform set_config('request.jwt.claims', c_admin, true);
    perform set_config('role', 'authenticated', true);
    while v_sent < 3 loop
      perform public.admin_send_announcement('Safety check: filler', 'Filler.', 'ALL');
      v_sent := v_sent + 1;
    end loop;
    perform public.admin_send_announcement('Safety check: one too many', 'Too many.', 'ALL');
    perform set_config('role', 'postgres', true);
    v_note := 'the fourth was sent';
  exception when others then
    perform set_config('role', 'postgres', true);
    v_note := sqlerrm;
    v_ok := sqlerrm = 'You can send up to 3 announcements every 10 minutes. Please wait a few minutes.';
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 8
  v_label := 'The history, the announcement by ID and the audit log show what was sent';
  v_ok := null; v_note := 'check 3 did not pass';
  if a_ok[3] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_admin, true);
      perform set_config('role', 'authenticated', true);
      v_j := public.admin_announcements(1, 50);
      v_one := public.admin_announcement(v_id_all);
      v_audit := public.admin_audit(null, null, 'ANNOUNCEMENT_SENT', 'ANY', 1, 50);
      n := (select count(*) from jsonb_array_elements(v_j -> 'rows') r where r ->> 'id' = v_id_all::text);
      n2 := (select count(*) from jsonb_array_elements(v_audit -> 'rows') r
             where r ->> 'targetId' = v_id_all::text and r ->> 'targetType' = 'ANNOUNCEMENT'
               and r ->> 'targetName' = 'Safety check: everyone'
               and r ->> 'details' = format('To %s %s (everyone).', n_all, case when n_all = 1 then 'person' else 'people' end));
      v_ok := n = 1 and n2 = 1
          and v_one ->> 'title' = 'Safety check: everyone' and v_one ->> 'message' = v_msg
          and v_one ->> 'audience' = 'ALL' and (v_one ->> 'reach')::bigint = n_all
          and v_one -> 'admin' ->> 'name' = v_admin.name
          and public.admin_announcement(gen_random_uuid()) is null;
      perform set_config('role', 'postgres', true);
      v_note := format('in history %s, audit rows %s, history total %s', n, n2, v_j ->> 'total');
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 9
  v_label := 'The recipient''s app reads its own announcement (and only its own)';
  v_ok := null; v_note := 'check 3 did not pass';
  if a_ok[3] then
    v_ok := false;
    begin
      perform set_config('request.jwt.claims', c_rec, true);
      perform set_config('role', 'authenticated', true);
      select count(*) into n from public.notifications where type = 'ANNOUNCEMENT' and title = 'Safety check: everyone';
      perform set_config('role', 'postgres', true);
      v_ok := n = 1;
      v_note := format('sees %s', n);
    exception when others then
      perform set_config('role', 'postgres', true);
      v_note := sqlerrm;
    end;
  end if;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 10
  v_label := 'A signed-in account that is not an admin is refused by all four functions';
  v_bad := '';
  perform set_config('request.jwt.claims', c_donor, true);
  perform set_config('role', 'authenticated', true);
  begin perform public.admin_audience_sizes(); v_bad := v_bad || 'sizes answered; ';
  exception when others then if sqlerrm <> 'Admin access required' then v_bad := v_bad || 'sizes: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_send_announcement('Safety check: donor', 'x', 'ALL'); v_bad := v_bad || 'send answered; ';
  exception when others then if sqlerrm <> 'Admin access required' then v_bad := v_bad || 'send: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_announcements(1, 10); v_bad := v_bad || 'history answered; ';
  exception when others then if sqlerrm <> 'Admin access required' then v_bad := v_bad || 'history: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_announcement(gen_random_uuid()); v_bad := v_bad || 'one answered; ';
  exception when others then if sqlerrm <> 'Admin access required' then v_bad := v_bad || 'one: ' || sqlerrm || '; '; end if; end;
  perform set_config('role', 'postgres', true);
  v_ok := v_bad = '';
  v_note := case when v_ok then 'all four refused' else v_bad end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 11
  v_label := 'No one signed out (anon) can run any of them';
  v_bad := '';
  perform set_config('request.jwt.claims', '{}', true);
  perform set_config('role', 'anon', true);
  begin perform public.admin_audience_sizes(); v_bad := v_bad || 'sizes answered; ';
  exception when others then if sqlerrm not like 'permission denied for function admin_%' then v_bad := v_bad || 'sizes: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_send_announcement('Safety check: anon', 'x', 'ALL'); v_bad := v_bad || 'send answered; ';
  exception when others then if sqlerrm not like 'permission denied for function admin_%' then v_bad := v_bad || 'send: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_announcements(1, 10); v_bad := v_bad || 'history answered; ';
  exception when others then if sqlerrm not like 'permission denied for function admin_%' then v_bad := v_bad || 'history: ' || sqlerrm || '; '; end if; end;
  begin perform public.admin_announcement(gen_random_uuid()); v_bad := v_bad || 'one answered; ';
  exception when others then if sqlerrm not like 'permission denied for function admin_%' then v_bad := v_bad || 'one: ' || sqlerrm || '; '; end if; end;
  perform set_config('role', 'postgres', true);
  v_ok := v_bad = '';
  v_note := case when v_ok then 'all four refused' else v_bad end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 12
  v_label := 'Clients have no privileges on the announcements table (RLS on)';
  v_ok := false;
  begin
    v_ok := (select relrowsecurity from pg_class where oid = 'public.announcements'::regclass)
        and not has_table_privilege('authenticated', 'public.announcements', 'SELECT, INSERT, UPDATE, DELETE')
        and not has_table_privilege('anon', 'public.announcements', 'SELECT, INSERT, UPDATE, DELETE');
    v_note := case when v_ok then 'as expected' else 'a client has a privilege, or RLS is off' end;
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 13
  v_label := 'Privileges: authenticated may run the four functions, anon may not, and no client may run the JSON helper';
  v_ok := to_regprocedure('public.admin_announcement_json(public.announcements)') is not null
      and not has_function_privilege('authenticated', 'public.admin_announcement_json(public.announcements)', 'execute')
      and not has_function_privilege('anon', 'public.admin_announcement_json(public.announcements)', 'execute')
      and (select count(*) from unnest(array['public.admin_audience_sizes()', 'public.admin_send_announcement(text,text,text)',
                                             'public.admin_announcements(integer,integer)', 'public.admin_announcement(uuid)']) f(sig)
           where to_regprocedure(f.sig) is not null
             and has_function_privilege('authenticated', f.sig, 'execute')
             and not has_function_privilege('anon', f.sig, 'execute')) = 4;
  v_note := case when v_ok then 'as expected' else 'a privilege is wrong or a function is missing' end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- 14
  v_label := 'Alerts still accept every earlier type and ANNOUNCEMENT, and refuse an unknown one';
  v_ok := false;
  begin
    insert into public.notifications (user_id, type, title, body)
    select v_rec.id, t, 'Safety check: type', 'x'
    from unnest(array['MESSAGE', 'RESERVATION_CREATED', 'RESERVATION_CANCELLED', 'RESERVATION_CLAIMED',
                      'RESERVATION_REMOVED', 'ANNOUNCEMENT']) t;
    begin
      insert into public.notifications (user_id, type, title, body) values (v_rec.id, 'SOMETHING_NEW', 'Safety check: type', 'x');
      v_note := 'SOMETHING_NEW was accepted';
    exception when check_violation then
      v_ok := true;
      v_note := 'six accepted, SOMETHING_NEW refused';
    end;
  exception when others then
    v_note := sqlerrm;
  end;
  a_label := array_append(a_label, v_label); a_ok := array_append(a_ok, v_ok); a_note := array_append(a_note, v_note);

  -- Report. Raising an error here undoes everything above.
  for i in 1 .. coalesce(array_length(a_label, 1), 0) loop
    if a_ok[i] is null then
      v_skip := v_skip + 1;
    elsif a_ok[i] then
      v_pass := v_pass + 1;
    else
      v_fail := v_fail + 1;
    end if;
    v_report := v_report || format(E'\n%s %s. %s: %s',
      case when a_ok[i] is null then 'SKIP' when a_ok[i] then 'PASS' else 'FAIL' end,
      i, a_label[i], coalesce(a_note[i], '-'));
  end loop;

  raise exception '%', format(E'SAFETY CHECK FINISHED: %s passed, %s failed, %s skipped.\nThis error is on purpose: it undoes everything the check did, so nothing was saved.\nDonor: %s. Recipient: %s. Admin: %s.%s',
    v_pass, v_fail, v_skip, coalesce(v_donor.name, 'none'), coalesce(v_rec.name, 'none'), coalesce(v_admin.name, 'none'), v_report);
end $$;
```

- [ ] **Step 3: Run it before the migration and see it fail**

Run: `SKIP=20261014 bash $S/run.sh $REPO/supabase/tests/admin_announcements_live_safety_check.sql 2>&1 | tail -20`
Expected: "SAFETY CHECK FINISHED: 0 passed, 12 failed, 2 skipped" or close to it. The functions and the table don't exist, so check 14's ANNOUNCEMENT insert fails and checks 8 and 9 are skipped.

- [ ] **Step 4: Write the migration**

`supabase/migrations/20261014_admin_announcements.sql`:

```sql
-- Salu-Salo admin dashboard: announcements (PROJECT_STATUS.md §27;
-- docs/superpowers/specs/2026-10-04-admin-announcements-design.md).
--
-- Run by hand in the Supabase SQL Editor after 20261013_admin_analytics.sql, and
-- ONLY ONCE EVERY PHONE HAS THE APP BUILD THAT KNOWS ANNOUNCEMENT ALERTS (§27):
-- older builds can't load Alerts once they have one. Then run
-- supabase/tests/admin_announcements_live_safety_check.sql. Safe to run again.
--
-- An admin sends an in-app announcement from the dashboard to Everyone, Donors
-- (has posted a listing) or Recipients (never posted, not an admin): one alert of
-- type ANNOUNCEMENT per account, the history row and the audit row in one
-- transaction. Every function starts with admin_require() (20261012).

-- ---------------------------------------------------------------------------
-- 1. The alert type and the audit log's new action
-- ---------------------------------------------------------------------------

do $$
declare c record;
begin
    for c in
        select con.conname from pg_constraint con
        where con.conrelid = 'public.notifications'::regclass and con.contype = 'c'
          and con.conkey = array[(select attnum from pg_attribute
                                   where attrelid = 'public.notifications'::regclass
                                     and attname = 'type')]::smallint[]
    loop
        execute format('alter table public.notifications drop constraint %I', c.conname);
    end loop;
    alter table public.notifications add constraint notifications_type_check check (type in (
        'MESSAGE', 'RESERVATION_CREATED', 'RESERVATION_CANCELLED', 'RESERVATION_CLAIMED',
        'RESERVATION_REMOVED', 'ANNOUNCEMENT'
    ));

    for c in
        select distinct con.conname from pg_constraint con
        join pg_attribute a on a.attrelid = con.conrelid and a.attnum = any (con.conkey)
        where con.conrelid = 'public.admin_audit_log'::regclass and con.contype = 'c'
          and a.attname in ('action', 'target_type')
    loop
        execute format('alter table public.admin_audit_log drop constraint %I', c.conname);
    end loop;
    alter table public.admin_audit_log add constraint admin_audit_log_action_check check (action in
        ('LISTING_REMOVED', 'REPORTS_DISMISSED', 'LISTING_REVIEWED', 'REPORT_NOTE_ADDED', 'ANNOUNCEMENT_SENT'));
    alter table public.admin_audit_log add constraint admin_audit_log_target_type_check
        check (target_type in ('LISTING', 'REPORT', 'ANNOUNCEMENT'));
end $$;

-- ---------------------------------------------------------------------------
-- 2. What was sent. Only the functions below write or read it.
-- ---------------------------------------------------------------------------

create table if not exists public.announcements (
    id uuid primary key default gen_random_uuid(),
    admin_id uuid references public.users(id) on delete set null,
    admin_name text not null,
    title text not null check (char_length(title) between 1 and 80),
    message text not null check (char_length(message) between 1 and 500),
    audience text not null check (audience in ('ALL', 'DONORS', 'RECIPIENTS')),
    reach integer not null check (reach > 0),
    created_at timestamptz not null default now()
);
create index if not exists announcements_created_idx on public.announcements (created_at desc);
create index if not exists announcements_admin_created_idx on public.announcements (admin_id, created_at);
alter table public.announcements enable row level security;
revoke all on public.announcements from anon;
revoke all on public.announcements from authenticated;

-- ---------------------------------------------------------------------------
-- 3. Functions
-- ---------------------------------------------------------------------------

-- Internal: one announcement as the dashboard shows it.
create or replace function public.admin_announcement_json(a public.announcements)
returns jsonb language sql immutable set search_path = public as $$
    select jsonb_build_object('id', a.id, 'at', public.admin_ms(a.created_at), 'title', a.title,
        'message', a.message, 'audience', a.audience, 'reach', a.reach,
        'admin', jsonb_build_object('id', a.admin_id, 'name', a.admin_name));
$$;

-- The form's counts. The audiences are the Users page's filters (admin_user_matches).
create or replace function public.admin_audience_sizes()
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
begin
    return (select jsonb_build_object(
                'ALL', count(*),
                'DONORS', count(*) filter (where public.admin_user_matches(u, null, 'DONOR')),
                'RECIPIENTS', count(*) filter (where public.admin_user_matches(u, null, 'RECIPIENT')))
            from public.users u);
end; $$;

create or replace function public.admin_send_announcement(p_title text, p_message text, p_audience text)
returns jsonb language plpgsql volatile security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_title text := btrim(coalesce(p_title, ''));
    v_message text := btrim(coalesce(p_message, ''));
    v_filter text := case p_audience when 'ALL' then 'ALL' when 'DONORS' then 'DONOR'
                                     when 'RECIPIENTS' then 'RECIPIENT' end;
    v_id uuid := gen_random_uuid();
    v_reach integer;
begin
    if v_title = '' then raise exception 'Add a title.'; end if;
    if char_length(v_title) > 80 then raise exception 'The title can be at most 80 characters.'; end if;
    if v_message = '' then raise exception 'Write the message.'; end if;
    if char_length(v_message) > 500 then raise exception 'The message can be at most 500 characters.'; end if;
    if v_filter is null then raise exception 'Choose who gets it.'; end if;

    -- At most 3 announcements per admin per 10 minutes (round 2's rule for
    -- anything that alerts other people), counted under a per-admin lock.
    perform pg_advisory_xact_lock(hashtextextended('salusalo-announce:' || v_admin.id::text, 0));
    if (select count(*) from public.announcements a
        where a.admin_id = v_admin.id and a.created_at > now() - interval '10 minutes') >= 3 then
        raise exception 'You can send up to 3 announcements every 10 minutes. Please wait a few minutes.';
    end if;

    -- Straight into notifications, not through notify_user(): announcements
    -- ignore the alert switches, and notify_user() cuts the text to 300 characters.
    insert into public.notifications (user_id, type, title, body)
    select u.id, 'ANNOUNCEMENT', v_title, v_message
    from public.users u
    where public.admin_user_matches(u, null, v_filter);
    get diagnostics v_reach = row_count;
    if v_reach = 0 then raise exception 'No one is in this audience yet.'; end if;

    insert into public.announcements (id, admin_id, admin_name, title, message, audience, reach)
    values (v_id, v_admin.id, v_admin.name, v_title, v_message, p_audience, v_reach);
    perform public.admin_log(v_admin, 'ANNOUNCEMENT_SENT', 'ANNOUNCEMENT', v_id, v_title,
        format('To %s %s (%s).', v_reach, case when v_reach = 1 then 'person' else 'people' end,
               case p_audience when 'ALL' then 'everyone' when 'DONORS' then 'donors' else 'recipients' end));
    return jsonb_build_object('id', v_id, 'reach', v_reach);
end; $$;

create or replace function public.admin_announcements(p_page integer, p_page_size integer)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
    v_total bigint;
    v_pg record;
    v_rows jsonb;
begin
    select count(*) into v_total from public.announcements;
    select * into v_pg from public.admin_paging(p_page, p_page_size, v_total);
    select coalesce(jsonb_agg(public.admin_announcement_json(a) order by a.created_at desc, a.id), '[]'::jsonb)
      into v_rows
    from public.announcements a
    where a.id in (select a2.id from public.announcements a2
                   order by a2.created_at desc, a2.id limit v_pg.size offset v_pg.skip);
    return jsonb_build_object('rows', v_rows, 'total', v_total, 'page', v_pg.page,
                              'pageSize', v_pg.size, 'pages', v_pg.pages);
end; $$;

create or replace function public.admin_announcement(p_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public as $$
declare
    v_admin public.users := public.admin_require();
begin
    return (select public.admin_announcement_json(a) from public.announcements a where a.id = p_id);
end; $$;

-- ---------------------------------------------------------------------------
-- 4. Privileges: the four for signed-in callers (admin_require decides), the
-- helper for no client.
-- ---------------------------------------------------------------------------

revoke all on function public.admin_audience_sizes() from public;
revoke all on function public.admin_audience_sizes() from anon;
grant execute on function public.admin_audience_sizes() to authenticated;
revoke all on function public.admin_send_announcement(text, text, text) from public;
revoke all on function public.admin_send_announcement(text, text, text) from anon;
grant execute on function public.admin_send_announcement(text, text, text) to authenticated;
revoke all on function public.admin_announcements(integer, integer) from public;
revoke all on function public.admin_announcements(integer, integer) from anon;
grant execute on function public.admin_announcements(integer, integer) to authenticated;
revoke all on function public.admin_announcement(uuid) from public;
revoke all on function public.admin_announcement(uuid) from anon;
grant execute on function public.admin_announcement(uuid) to authenticated;
revoke all on function public.admin_announcement_json(public.announcements) from public;
revoke all on function public.admin_announcement_json(public.announcements) from anon;
revoke all on function public.admin_announcement_json(public.announcements) from authenticated;
```

- [ ] **Step 5: Run it after the migration and see it pass, twice**

Run: `bash $S/run.sh $REPO/supabase/migrations/20261014_admin_announcements.sql $REPO/supabase/tests/admin_announcements_live_safety_check.sql 2>&1 | tail -20`
Expected: the re-run of the migration prints no error, then "SAFETY CHECK FINISHED: 14 passed, 0 failed, 0 skipped".

- [ ] **Step 6: Earlier checks still pass**

Run: `bash $S/run.sh $REPO/supabase/tests/admin_dashboard_live_safety_check.sql $REPO/supabase/tests/admin_analytics_live_safety_check.sql $REPO/supabase/tests/bugfix_anon_grants_check.sql $REPO/supabase/tests/admin_dashboard_check.sql 2>&1 | grep -E "FINISHED|passed|ok|f$|t$"`
Expected: 20/20, 10/10, 3/3, and every `ok` row true except "At least one admin account exists". The seed has no admin, as in §25.

---

### Task 3: Dashboard: Notifications page on real data

**Files:**
- Modify: `admin/js/data.js`
- Modify: `admin/js/ui.js` (`confirm` takes `errorText` as a function too)
- Modify: `admin/js/pages/audit.js`
- Rewrite: `admin/js/pages/notifications.js`
- Test: `admin/tests/data.test.js`

**Interfaces:**
- Consumes (Task 2): the four RPCs and their JSON.
- Produces:
  - `SaluData.audienceSizes(): Promise<{ALL, DONORS, RECIPIENTS}>`;
  - `SaluData.sendAnnouncement({title, message, audience}): Promise<{id, reach}>`;
  - `SaluData.announcements(q): Promise<{rows, total, page, pageSize, pages}>`;
  - `SaluData.announcement(id): Promise<row|null>`;
  - `SaluData.text.audience`;
  - `DataError.code` (the server's error code).

- [ ] **Step 1: Write the failing checks**

In `admin/tests/data.test.js`, before `console.log('data: all checks passed');`:

```js
  // Announcements: sending passes the form's fields; the history and one announcement mark the admin's own rows.
  {
    let asked = null;
    const t = load({
      admin_whoami: () => ({ data: { me: { id: 'a1', name: 'Ada', email: 'ada@example.com' }, admins: [] }, error: null }),
      admin_counts: () => ({ data: { openReports: 0 }, error: null }),
      admin_audience_sizes: () => ({ data: { ALL: 3, DONORS: 1, RECIPIENTS: 1 }, error: null }),
      admin_send_announcement: (args) => { asked = args; return { data: { id: 'n1', reach: 1 }, error: null }; },
      admin_announcements: () => ({ data: { rows: [{ id: 'n1', admin: { id: 'a1', name: 'Ada' } }], total: 1, page: 1, pageSize: 10, pages: 1 }, error: null }),
      admin_announcement: () => ({ data: { id: 'n1', admin: { id: 'b2', name: 'Ben' } }, error: null })
    });
    await t.data.start(t.sb, () => {});
    assert.deepStrictEqual(await t.data.audienceSizes(), { ALL: 3, DONORS: 1, RECIPIENTS: 1 });
    assert.deepStrictEqual(await t.data.sendAnnouncement({ title: 'Hi', message: 'Hello', audience: 'DONORS' }), { id: 'n1', reach: 1 });
    assert.deepStrictEqual(JSON.parse(JSON.stringify(asked)), { p_title: 'Hi', p_message: 'Hello', p_audience: 'DONORS' });
    assert.strictEqual((await t.data.announcements({ page: 1, pageSize: 10 })).rows[0].admin.isYou, true);
    assert.strictEqual((await t.data.announcement('n1')).admin.isYou, undefined);
    assert.strictEqual(t.data.text.audience.RECIPIENTS, 'Recipients');
    assert.strictEqual(t.data.text.actions.ANNOUNCEMENT_SENT, 'Sent announcement');
  }

  // A refusal keeps the server's code, so the page can show the server's own text (the rate limit).
  {
    const limit = 'You can send up to 3 announcements every 10 minutes. Please wait a few minutes.';
    const t = load({
      admin_whoami: () => ({ data: { me: { id: 'a1', name: 'Ada', email: 'ada@example.com' }, admins: [] }, error: null }),
      admin_counts: () => ({ data: { openReports: 0 }, error: null }),
      admin_send_announcement: () => ({ data: null, error: { code: 'P0001', message: limit } })
    });
    await t.data.start(t.sb, () => {});
    await assert.rejects(t.data.sendAnnouncement({ title: 'Hi', message: 'Hello', audience: 'ALL' }),
      (e) => e instanceof t.data.DataError && e.code === 'P0001' && e.message === limit);
  }
```

- [ ] **Step 2: Run them and see them fail**

Run: `node admin/tests/data.test.js`
Expected: FAIL with `TypeError: t.data.audienceSizes is not a function`.

- [ ] **Step 3: The data layer**

In `admin/js/data.js`:
- Header comment: after `(supabase/migrations/20261012_admin_dashboard.sql)` add `, 20261013 (Analytics) and 20261014 (announcements)`.
- In `text`, change `actions` and add `audience`:

```js
    actions: { LISTING_REMOVED: 'Removed listing', REPORTS_DISMISSED: 'Dismissed report', LISTING_REVIEWED: 'Reviewed listing', REPORT_NOTE_ADDED: 'Added note', ANNOUNCEMENT_SENT: 'Sent announcement' },
    audience: { ALL: 'Everyone', DONORS: 'Donors', RECIPIENTS: 'Recipients' }
```

- In `rpc()`, replace `throw new DataError(error.message || 'Request failed');` with:

```js
      // Our own short texts; never details or hints. The code tells a refusal (P0001) from a failure.
      throw Object.assign(new DataError(error.message || 'Request failed'), { code: error.code || null });
```

- After `analytics(...)`:

```js
    /** Announcements (20261014): the form's audience sizes, sending one, the history, one by ID. */
    audienceSizes() { return rpc('admin_audience_sizes'); },
    sendAnnouncement({ title, message, audience }) {
      return rpc('admin_send_announcement', { p_title: title, p_message: message, p_audience: audience });
    },
    async announcements(q) {
      const res = await rpc('admin_announcements', paging(q));
      res.rows.forEach((a) => markYou(a.admin));
      return res;
    },
    async announcement(id) {
      const a = await rpc('admin_announcement', { p_id: id });
      if (a) markYou(a.admin);
      return a;
    },
```

- [ ] **Step 4: Run them and see them pass**

Run: `for t in admin/tests/*.test.js; do node $t; done`
Expected: `timeline`, `auth` and `data: all checks passed`.

- [ ] **Step 5: confirm() can show the server's text**

In `admin/js/ui.js`:
- In the `confirm` doc comment, change `errorText })` to `errorText: string | (err) => string })`.
- Replace `errorBox.append(notice({ tone: 'error', text: o.errorText || 'That didn’t work. Please try again.' }));` with:

```js
          const errorText = typeof o.errorText === 'function' ? o.errorText(err) : o.errorText;
          errorBox.append(notice({ tone: 'error', text: errorText || 'That didn’t work. Please try again.' }));
```

- [ ] **Step 6: Audit log**

In `admin/js/pages/audit.js`:
- In `ACTION_ICON`, replace `'Sent notification': 'send',` with `'Sent announcement': 'campaign',`.
- In `targetLink`'s map, add `ANNOUNCEMENT: '#/notifications/',`.
- In the empty state, replace `adding a note, marking a listing reviewed)` with `adding a note, marking a listing reviewed, sending an announcement)`.

- [ ] **Step 7: The Notifications page**

Replace `admin/js/pages/notifications.js` with:

```js
/* Notifications: announcements to people using the app (admin_send_announcement, 20261014) and what was sent. */
window.Pages = window.Pages || {};
window.Pages.notifications = (function () {
  'use strict';
  const UI = window.UI;
  const { h, icon, clear, fmt, btn, dl, section, empty, errorState, skeletonPanel, confirm, toast } = UI;
  const data = window.SaluData;

  const TITLE_MAX = 80;
  const MESSAGE_MAX = 500;
  const AUDIENCES = [['ALL', 'Every account'], ['DONORS', 'Has posted a listing'], ['RECIPIENTS', 'Has never posted']];
  const by = (a) => (a.admin.isYou ? 'You' : a.admin.name);
  const people = (n) => fmt.plural(n, 'person', 'people');

  let s = null;

  function mount(main) {
    s = { panelFor: null };
    s.list = window.ListView({
      what: 'announcements', noun: 'announcement', caption: 'Announcements', pageSize: 10, avatars: false,
      defaults: {},
      fetch: (q) => data.announcements(q),
      open: (a) => window.App.go('#/notifications/' + a.id),
      columns: [
        { label: 'Announcement', className: 'cell-wide', cell: (a) => h('div', { class: 'identity__text', style: { maxWidth: '420px' } },
          h('a', { class: 'row-link', href: '#/notifications/' + a.id }, a.title),
          h('span', { class: 'identity__meta' }, a.message)) },
        { label: 'Audience', cell: (a) => data.text.audience[a.audience] },
        { label: 'Reached', className: 'cell-num', cell: (a) => people(a.reach) },
        { label: 'Sent', className: 'cell-muted', cell: (a) => fmt.moment(a.at) },
        { label: 'By', className: 'cell-muted', hide: 'tablet', cell: by }
      ],
      card: (a) => UI.rowCard({
        visual: h('span', { class: 'icon-bubble', 'aria-hidden': 'true' }, icon('campaign', 18)), title: a.title, href: '#/notifications/' + a.id,
        meta: { sub: a.message, items: [{ icon: 'group', text: data.text.audience[a.audience] + ' · ' + people(a.reach) }, { icon: 'schedule', text: fmt.moment(a.at) }] }
      }),
      emptyState: () => ({
        iconName: 'campaign', title: 'No announcements yet', text: 'Announcements you send appear here, with who got them and when.',
        action: { label: 'New announcement', icon: 'add', href: '#/notifications/new', variant: 'primary' }
      })
    });
    main.append(
      UI.pageHead({ title: 'Notifications', sub: 'Announcements to people using the Salu-Salo app.',
        actions: [btn({ label: 'New announcement', icon: 'add', variant: 'primary', href: '#/notifications/new' })] }),
      s.list.el);
    s.list.load();
  }

  /* Drawn like the app's Alerts row: icon, title, text, the "Announcement" label. */
  function preview(title, message, time) {
    return h('div', { class: 'preview-phone', 'aria-label': 'Preview' },
      h('div', { class: 'preview-phone__bar' }, h('span', null, 'Salu-Salo · Alerts'), h('span', null, time)),
      h('div', { class: 'preview-alert' },
        h('span', { class: 'icon-bubble' }, icon('campaign', 18)),
        h('div', { class: 'preview-alert__text' },
          h('span', { class: 'preview-alert__title' }, title || 'Your title'),
          h('span', { class: 'preview-alert__body' }, message || 'Your message appears here.'),
          h('span', { class: 'preview-alert__time' }, 'Announcement'))));
  }

  /* ---------- Details ---------- */
  async function openAnnouncement(id, keep) {
    s.panelFor = id;
    const onClose = () => window.App.closeTo('#/notifications');
    if (!keep) UI.openPanel({ label: 'Announcement', title: 'Loading…', body: skeletonPanel(), onClose });
    let a;
    try { a = await data.announcement(id); } catch (err) {
      if (s && s.panelFor === id) UI.openPanel({ label: 'Announcement', title: 'Couldn’t load this announcement', body: errorState(() => openAnnouncement(id, true), 'this announcement'), onClose });
      return;
    }
    if (!s || s.panelFor !== id) return;
    if (!a) { UI.openPanel({ label: 'Announcement', title: 'Announcement not found', body: empty({ iconName: 'campaign', title: 'This announcement doesn’t exist', text: 'Check the link and try again.' }), onClose }); return; }
    UI.openPanel({
      label: 'Announcement', title: a.title, sub: data.text.audience[a.audience] + ' · sent ' + fmt.relative(a.at), onClose,
      body: [
        section('Message', h('blockquote', { class: 'quote' }, a.message)),
        section('How it looks in the app', preview(a.title, a.message, fmt.relative(a.at))),
        section('Details', dl([
          ['Audience', data.text.audience[a.audience]],
          ['Reached', people(a.reach)],
          ['Sent', fmt.dateTime(a.at)],
          ['Sent by', by(a)]
        ]))
      ]
    });
  }

  /* ---------- New announcement ---------- */
  async function openComposer() {
    s.panelFor = 'new';
    const onClose = () => window.App.closeTo('#/notifications');
    UI.openPanel({ label: 'New announcement', title: 'Loading…', body: skeletonPanel(), onClose });
    let sizes;
    try { sizes = await data.audienceSizes(); } catch (err) {
      if (s && s.panelFor === 'new') UI.openPanel({ label: 'New announcement', title: 'Couldn’t load the audiences', body: errorState(openComposer, 'the audiences'), onClose });
      return;
    }
    if (!s || s.panelFor !== 'new') return;

    const title = h('input', { class: 'input', id: 'n-title', maxlength: String(TITLE_MAX), autocomplete: 'off', 'aria-describedby': 'n-title-count' });
    const message = h('textarea', { class: 'textarea', id: 'n-message', maxlength: String(MESSAGE_MAX), rows: '5', 'aria-describedby': 'n-message-count' });
    const titleCount = h('span', { class: 'field__count', id: 'n-title-count' });
    const messageCount = h('span', { class: 'field__count', id: 'n-message-count' });
    const titleErr = h('span', { class: 'field__error', hidden: true }, 'Add a title.');
    const messageErr = h('span', { class: 'field__error', hidden: true }, 'Write the message.');
    let audience = 'ALL';
    const audienceChoices = h('div', { class: 'choice-grid', role: 'radiogroup', 'aria-label': 'Who gets it' },
      AUDIENCES.map(([v, meta]) => h('label', { class: 'choice' },
        h('input', { type: 'radio', name: 'n-audience', value: v, checked: v === audience || null }),
        h('span', { class: 'choice__title' }, data.text.audience[v]),
        h('span', { class: 'choice__meta' }, people(sizes[v]) + ' · ' + meta))));
    const previewBox = h('div');
    const sendBtn = btn({ label: 'Send announcement', icon: 'send', variant: 'primary' });

    function sync() {
      titleCount.textContent = title.value.length + ' / ' + TITLE_MAX;
      messageCount.textContent = message.value.length + ' / ' + MESSAGE_MAX;
      clear(previewBox).append(preview(title.value.trim(), message.value.trim(), 'now'));
    }
    function mark(input, err, bad) {
      err.hidden = !bad;
      input.classList.toggle('is-invalid', bad);
      if (bad) input.setAttribute('aria-invalid', 'true'); else input.removeAttribute('aria-invalid');
    }
    title.addEventListener('input', () => { mark(title, titleErr, false); sync(); });
    message.addEventListener('input', () => { mark(message, messageErr, false); sync(); });
    audienceChoices.addEventListener('change', (e) => { audience = e.target.value; });

    sendBtn.addEventListener('click', async () => {
      const t = title.value.trim();
      const m = message.value.trim();
      mark(title, titleErr, !t);
      mark(message, messageErr, !m);
      if (!t) { title.focus(); return; }
      if (!m) { message.focus(); return; }
      let reach = 0;
      const ok = await confirm({
        title: 'Send to ' + people(sizes[audience]) + '?',
        text: 'It goes to every account in this audience right away. You can’t unsend it. Phones need the latest app version.',
        confirmLabel: 'Send', tone: 'primary', iconName: 'send',
        // The server's own text for a refusal (the limit, an empty audience); never a failure's details.
        errorText: (err) => (err && err.code === 'P0001' ? err.message : 'Unable to send the announcement. Please try again.'),
        action: async () => { reach = (await data.sendAnnouncement({ title: t, message: m, audience })).reach; }
      });
      if (!ok) return;
      toast('Announcement sent to ' + people(reach) + '.', { sub: 'Saved in the audit log.' });
      window.App.closeTo('#/notifications');
      if (s) s.list.reload();
    });

    const counted = (err, count) => h('div', { style: { display: 'flex', justifyContent: 'space-between', gap: '8px' } }, err, count);
    UI.openPanel({
      label: 'New announcement', title: 'New announcement', sub: 'An alert in the Salu-Salo app', onClose,
      foot: [h('span', { class: 'spacer' }), sendBtn],
      body: [
        h('div', { class: 'field' }, h('label', { class: 'field__label', for: 'n-title' }, 'Title'), title, counted(titleErr, titleCount)),
        h('div', { class: 'field' }, h('label', { class: 'field__label', for: 'n-message' }, 'Message'), message, counted(messageErr, messageCount)),
        h('div', { class: 'field' }, h('span', { class: 'field__label' }, 'Who gets it'), audienceChoices),
        section('Preview', previewBox, h('p', { class: 'muted', style: { fontSize: 'var(--t-sm)' } },
          'Shown in the app’s Alerts, where long messages are cut after 3 lines; tapping it shows the whole text. It arrives whatever the person’s alert settings are.'))
      ]
    });
    sync();
    setTimeout(() => title.focus(), 260);
  }

  function update(route) {
    if (!s) return;
    if (route.id === 'new') openComposer();
    else if (route.id) openAnnouncement(route.id);
    else if (UI.panelIsOpen()) { s.panelFor = null; UI.closePanel(); }
  }

  return {
    title: 'Notifications',
    mount,
    update,
    unmount() { s = null; }
  };
})();
```

- [ ] **Step 8: Syntax and checks**

Run: `for f in admin/js/*.js admin/js/pages/*.js; do node --check $f || echo BAD $f; done; for t in admin/tests/*.test.js; do node $t; done`
Expected: no `BAD`; all three checks pass.

- [ ] **Step 9: Try it locally against a stand-in**

Serve `npx http-server admin -p 8080 -c-1`. The live project has no migration yet, so the page loads and shows "Couldn't load …" for the list. That's expected and proves only that it mounts without script errors. Open the browser console and check there are no errors besides the refused RPC. If no browser is available, skip this step and say so. The real test is the developer's (Task 4).

---

### Task 4: Docs, final review and the developer's test

**Files:**
- Modify: `docs/PROJECT_STATUS.md`, `CLAUDE.md`, `supabase/README.md`, `admin/README.md`
- Modify: the developer's tracker `C:\Users\pasto\OneDrive\Documents\Salu-Salo demo fix tracker.txt`
- Modify: memory `admin-dashboard-slices.md`

- [ ] **Step 1: Record PR #24 as merged** (left over from Analytics). In `docs/PROJECT_STATUS.md`:
  - The status line of §26: replace `Not yet merged.**` with `Merged into \`master\` through PR #24 on 2026-10-03 (merge commit \`c240f48\`).**`.
  - The top summary (line 3), the Phase 6 table row (line 38) and the §1 Phase 6 bullet (line 127): replace each `Analytics (§26) on branch \`claude/admin-analytics\`…not yet merged` / `Analytics (§26) on branch \`claude/admin-analytics\`.` with `Analytics (§26) applied, deployed, tested on the phone and merged through PR #24 (\`c240f48\`, 2026-10-03). Announcements (§27) on branch \`claude/admin-announcements\`: built, not yet applied or tested.`.
  - Line 38's `ID verification, suspending accounts, announcements and analytics are later slices.` becomes `ID verification and suspending accounts are later slices.`.
  - In `CLAUDE.md`: the Phase 6 roadmap text `…tested on the phone (2026-10-03); not yet merged** (§26). ID verification, suspending accounts and announcements are later slices; announcements need an app update that ignores unknown alert types first.` becomes `…tested on the phone (2026-10-03); merged into \`master\` through PR #24 (merge commit \`c240f48\`)** (§26). **Third slice, announcements: built on branch \`claude/admin-announcements\` (from \`master\` at \`c240f48\`): an app update (the \`ANNOUNCEMENT\` alert, unknown types skipped) and migration \`20261014_admin_announcements.sql\`; not yet applied or tested** (§27). ID verification and suspending accounts are later slices.`. The Supabase-objects entry `(\`20261013_admin_analytics.sql\`, §26; on branch \`claude/admin-analytics\`, applied…)` becomes `(\`20261013_admin_analytics.sql\`, §26; merged through PR #24, applied…)`.

- [ ] **Step 2: Document §27.** Append to `docs/PROJECT_STATUS.md`:

```markdown
## 27. Admin dashboard: announcements (Phase 6, third slice)

### 27.0 Status

**Written on branch `claude/admin-announcements` (from `master` at `c240f48`, the PR #24 merge) on 2026-10-04. Checked locally only: the app build is not installed, the migration not applied and the dashboard not deployed.** Spec: `docs/superpowers/specs/2026-10-04-admin-announcements-design.md`.

### 27.1 What changed

- **App** (must be on every phone before the migration):
  - `NotificationType` gains `ANNOUNCEMENT`.
  - Alerts of a type the build doesn't know decode as `UNKNOWN` (`NotificationTypeSerializer`) and are dropped by `NotificationRepository`, so a type added later never breaks Alerts again. Before this build, one unknown type made the whole list fail ("Could not load alerts").
  - Alerts shows announcements with a megaphone, the label "Announcement" and an "Announcements" filter chip. Tapping one opens its whole text with "From Salu-Salo · <time>", and the dialog stays open through rotation. They play the alert sound like other live alerts.
- **Database** (`20261014_admin_announcements.sql`):
  - The `ANNOUNCEMENT` alert type.
  - Table `announcements` (title 1–80, message 1–500, audience, reach, admin and time; no client privileges).
  - Audit action `ANNOUNCEMENT_SENT` (target type `ANNOUNCEMENT`).
  - Admin-only functions: `admin_audience_sizes()`, `admin_send_announcement(title, message, audience)`, `admin_announcements(page, size)` and `admin_announcement(id)`, plus the internal `admin_announcement_json`.
  - Sending inserts one alert per account in the audience directly, not through `notify_user()`: it ignores the alert switches and keeps the whole text. It refuses an empty audience. It allows at most 3 announcements per admin per 10 minutes, under a per-admin advisory lock. The history row, the alerts and the audit row are written in one transaction.
  - Audiences are the Users page's filters: Everyone (every account, admins included), Donors (has posted), Recipients (never posted, not an admin).
- **Web**:
  - The Notifications page: the history (paged) with each announcement's details, and "New announcement" with title and message counters, the audience with live counts, a preview like the app's row, and "Send to N people?". A refusal shows the server's own text.
  - The Audit log's "Sent announcement" links to the announcement.
  - `confirm()` can show an error text built from the error.

### 27.2 Checks run (local only, 2026-10-04)

(Filled in from the actual runs: JVM tests, `assembleDebug`, `lintDebug`, the safety check before and after the migration, the earlier checks, the Node checks.)

### 27.3 Order (by hand)

1. Install the new app build on every phone that uses the live project.
2. Run `supabase/migrations/20261014_admin_announcements.sql`, then `supabase/tests/admin_announcements_live_safety_check.sql` (expect 14 passed).
3. Deploy: `npx firebase-tools deploy --only hosting`.
4. The manual test (27.4).

### 27.4 Manual test (not yet run)

(The checklist given to the developer.)

### 27.5 Known limits

- In-app only: no push while the app is closed; it shows when the app opens or Alerts reloads.
- No scheduling, drafts, unsend or per-person opt-out.
- The reach counts accounts, not phones, at the moment of sending; accounts created later don't get it.
- A phone on a build older than §27 shows "Could not load alerts" once it has an announcement, until it is updated.
```

Replace the two "(Filled in …)" and "(The checklist …)" lines with the real results and the checklist when they exist. Never leave them in a commit.

- [ ] **Step 3: The other docs**
  - `CLAUDE.md`:
    - Supabase objects: a new bullet before "Admin Analytics", `- Admin announcements (\`20261014_admin_announcements.sql\`, §27; on branch \`claude/admin-announcements\`, not yet applied; run only after every phone has the §27 build): alert type \`ANNOUNCEMENT\`; table \`announcements\` (no client privileges); audit action \`ANNOUNCEMENT_SENT\`; admin-only \`admin_audience_sizes\`, \`admin_send_announcement\` (one alert per account in the audience, inserted directly so the switches don't apply and the text isn't cut; at most 3 per admin per 10 minutes; refuses an empty audience), \`admin_announcements\`, \`admin_announcement\`; internal \`admin_announcement_json\`. Check: \`supabase/tests/admin_announcements_live_safety_check.sql\`.`
    - Security: in "Messages and alerts change only through the Phase 4 RPCs and triggers" add `(and, since §27, admins' announcements through \`admin_send_announcement\`)`.
    - In "Admin functions (§25)" replace `and only alert with existing types (the app's \`NotificationType\` is a strict enum)` with `and alert only with existing types; since §27 the app skips types it doesn't know, but builds before §27 fail on them, so a new type still needs every phone updated before the migration`.
    - Architecture: the `model/` bullet's `` `AppNotification`, `NotificationType` `` gains `(lenient since §27: an unknown type decodes as \`UNKNOWN\` and \`NotificationRepository\` drops it)`; `AlertScreen.kt: real alerts (Phase 4).` becomes `AlertScreen.kt: real alerts (Phase 4), with admins' announcements and their full-text dialog since §27.`.
    - Testing: `68 JVM tests (2026-10-03; …)` → `71 JVM tests (2026-10-04; …, \`NotificationTypeTest\` (3, §27))`, keeping the rest of the list.
    - Known issues #21: `strict enum decoding` → `strict enum decoding (lenient since the §27 build)`.
  - `supabase/README.md`: append

```markdown
### 20261014_admin_announcements.sql (admin announcements, PROJECT_STATUS.md §27)

Run after `20261013_admin_analytics.sql`, **and only once every phone has the app build from §27**: older builds can't load Alerts once they have an announcement. Safe to run again.

It adds the `ANNOUNCEMENT` alert type, the `announcements` table (no client privileges), the audit action `ANNOUNCEMENT_SENT`, and the admin-only `admin_audience_sizes`, `admin_send_announcement`, `admin_announcements` and `admin_announcement`.

1. **Apply:** run this file alone ("Success. No rows returned").
2. **Safety check:** run `tests/admin_announcements_live_safety_check.sql` alone, before anyone sends a real announcement. It ends with "SAFETY CHECK FINISHED: 14 passed, 0 failed, 0 skipped." on purpose and undoes everything (test announcements, their alerts, audit rows and the roles it sets); no phone gets them.
```

  - `admin/README.md`: in "What works", add `Notifications (announcements, \`supabase/migrations/20261014_admin_announcements.sql\`; the app build from PROJECT_STATUS.md §27 must be on every phone first)`, and replace `Not connected yet: Notifications (announcements), verification requests, suspending accounts.` with `Not connected yet: verification requests, suspending accounts.`.

- [ ] **Step 4: Final verification**

Run every check again on the final tree:
- `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline` (71 tests, 0 lint errors);
- the Node checks;
- `bash $S/run.sh $REPO/supabase/tests/admin_announcements_live_safety_check.sql` (14/14).

- [ ] **Step 5: Final review**

One fresh reviewer (most capable model) reads the whole branch diff against the spec and this plan. Fix what it confirms; re-run Step 4.

- [ ] **Step 6: Hand over**

- Fill §27.2 with the real results.
- Give the developer the ordered checklist: build and install on both phones → migration → safety check → deploy → the two-phone test. The test: Alerts on the new build before any announcement; send to Everyone, live on both phones with the sound; full text; rotation; the Announcements chip; Donors and Recipients reach; the history and details; the Audit log link; the rate-limit text on the 4th within 10 minutes; regression: a message alert and a reservation alert still open their pages.
- Put the checklist into §27.4, and update the tracker note and the memory file.
- No commit until the developer asks.
