package com.example.salusalo

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.salusalo.data.NotificationRepository
import com.example.salusalo.model.NotificationType
import com.example.salusalo.model.notificationTypeOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

// Phone notifications for alerts (§35). The send-push Edge Function sends every
// new alert as an FCM data message; PushService shows it as a notification, and
// a tap opens MainActivity, which hands the tap to Home.

/** The alert a pushed message (and its notification's tap) carries. */
data class PushTap(
    val alertId: String,
    val type: NotificationType,
    val conversationId: String?,
    val reservationId: String?,
    /** The Firebase UID of the account the alert is for. */
    val uid: String?
)

// The FCM data keys (send-push) a tap carries; the tap's Intent uses the same names.
private val TAP_KEYS = listOf("alert_id", "type", "conversation_id", "reservation_id", "uid")

/** The alert in a pushed message's data; null without an alert ID. */
internal fun pushTapFrom(data: Map<String, String>): PushTap? {
    val alertId = data["alert_id"]?.takeIf { it.isNotEmpty() } ?: return null
    return PushTap(
        alertId = alertId,
        type = notificationTypeOf(data["type"]),
        conversationId = data["conversation_id"]?.takeIf { it.isNotEmpty() },
        reservationId = data["reservation_id"]?.takeIf { it.isNotEmpty() },
        uid = data["uid"]?.takeIf { it.isNotEmpty() }
    )
}

/**
 * Whether the Intent that started MainActivity may carry a new tap: not when
 * Android restores the app, and not when it is reopened from Recents, which
 * relaunches the task's first Intent (an old notification's) after the app was
 * closed with Back (Android 7–11) or the phone restarted.
 */
internal fun isFreshTap(hasSavedState: Boolean, intentFlags: Int): Boolean =
    !hasSavedState && (intentFlags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

/** The alert of a tapped notification that opened MainActivity, or null for any other start. */
internal fun pushTapFrom(intent: Intent?): PushTap? =
    intent?.let { pushTapFrom(TAP_KEYS.mapNotNull { key -> it.getStringExtra(key)?.let { value -> key to value } }.toMap()) }

/**
 * Whether a pushed alert becomes a phone notification: only for the account
 * signed in on this phone, and never for a message of the chat that is open on
 * screen (the user is reading it).
 */
internal fun shouldShowPush(tap: PushTap, signedInUid: String?, appResumed: Boolean, openChatId: String?): Boolean =
    signedInUid != null && tap.uid == signedInUid &&
        !(appResumed && tap.type == NotificationType.MESSAGE &&
            tap.conversationId != null && tap.conversationId == openChatId)

/** What PushService needs to know about the screen; HomeScreen keeps it current. */
internal object AlertPresence {
    /** True while Home is on screen (resumed). */
    @Volatile var resumed = false
    /** The chat open on Home, if any. */
    @Volatile var openChatId: String? = null
}

internal const val ALERTS_CHANNEL = "alerts"

/** Whether this phone shows Salu-Salo's notifications: allowed for the app, and the Alerts channel not turned off. */
internal fun phoneNotificationsOn(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    return manager.areNotificationsEnabled() &&
        manager.getNotificationChannelCompat(ALERTS_CHANNEL)?.importance != NotificationManagerCompat.IMPORTANCE_NONE
}

/** Removes a chat's notification once the chat is open. */
internal fun cancelChatNotification(context: Context, conversationId: String) {
    NotificationManagerCompat.from(context).cancel(conversationId, 0)
}

/**
 * Shows a pushed alert as a notification that pops up (channel importance high),
 * silent when this phone's "Notification sound" is off. A chat's newest message
 * replaces its older one; other alerts each get their own. Tapping it opens the
 * app on the alert's own place (MainActivity → Home).
 */
internal fun showAlertNotification(context: Context, tap: PushTap, data: Map<String, String>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) return
    val manager = NotificationManagerCompat.from(context)
    // Cheap, and nothing happens once it exists.
    manager.createNotificationChannel(
        NotificationChannelCompat.Builder(ALERTS_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Alerts")
            .setDescription("Messages, reservation updates and announcements")
            .build()
    )
    val open = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    TAP_KEYS.forEach { key -> data[key]?.let { open.putExtra(key, it) } }
    val pending = PendingIntent.getActivity(
        context, tap.alertId.hashCode(), open,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val body = data["body"].orEmpty()
    val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL)
        .setSmallIcon(R.mipmap.ic_launcher_monochrome)
        .setContentTitle(data["title"].orEmpty())
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        // Android 7 has no channels: these make it pop up and sound there.
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setSilent(!notificationSoundOn(context))
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()
    val tag = if (tap.type == NotificationType.MESSAGE) tap.conversationId ?: tap.alertId else tap.alertId
    try {
        manager.notify(tag, 0, notification)
    } catch (_: SecurityException) {
        // Notifications were turned off in the meantime.
    }
}

// When this phone last received a Salu-Salo push (salusalo_session, like the
// sound switch). The app's own alert sound stays quiet only while pushes
// arrive, so a push side that isn't set up or fails never silences the app.
private const val PUSH_RECENT_MILLIS = 14L * 24 * 60 * 60 * 1000

/** Whether pushes reach this phone: one arrived in the last 14 days. */
internal fun pushesArriving(lastPushAtMillis: Long, nowMillis: Long): Boolean =
    lastPushAtMillis > 0 && nowMillis - lastPushAtMillis < PUSH_RECENT_MILLIS

internal fun lastPushAt(context: Context): Long =
    context.getSharedPreferences("salusalo_session", 0).getLong("last_push_at", 0L)

private fun markPushArrived(context: Context) {
    context.getSharedPreferences("salusalo_session", 0).edit { putLong("last_push_at", System.currentTimeMillis()) }
}

/**
 * Firebase signed out (Log out, or the session ended): this phone's push token
 * is deleted, so the old account's alerts stop popping up here (the next
 * sign-in registers a new one), and its notifications leave the shade, so the
 * next person using the phone can't read them.
 */
internal fun forgetPushOnSignOut(context: Context) {
    // Deprecated in Firebase 25.1 in favour of an opt-in registration API
    // (off unless enabled in the manifest); still the way to drop the token.
    @Suppress("DEPRECATION")
    FirebaseMessaging.getInstance().deleteToken()
    NotificationManagerCompat.from(context).cancelAll()
}

// The notification permission is asked once per phone; kept in salusalo_session
// like "Notification sound", which sign-out doesn't clear.
internal fun pushPermissionAsked(context: Context): Boolean =
    context.getSharedPreferences("salusalo_session", 0).getBoolean("push_permission_asked", false)

internal fun markPushPermissionAsked(context: Context) {
    context.getSharedPreferences("salusalo_session", 0).edit { putBoolean("push_permission_asked", true) }
}

/**
 * Receives Salu-Salo's pushed alerts and shows them (see [shouldShowPush]), and
 * saves a new FCM token for the account signed in on this phone.
 */
class PushService : FirebaseMessagingService() {
    // Firebase 25.1 deprecates onNewToken but still calls it, unless its newer
    // registration is turned on, which calls onRegistered instead; both save it.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) = saveToken(token)

    override fun onRegistered(token: String) = saveToken(token)

    private fun saveToken(token: String) {
        if (FirebaseAuth.getInstance().currentUser == null) return
        // FCM calls this on a background thread. A failure stays quiet: Home
        // registers the token again on its next start.
        runBlocking {
            withTimeoutOrNull(10_000L) { runCatching { NotificationRepository().registerPushToken(token) } }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val tap = pushTapFrom(message.data) ?: return
        markPushArrived(this)
        val signedIn = FirebaseAuth.getInstance().currentUser?.uid
        if (!shouldShowPush(tap, signedIn, AlertPresence.resumed, AlertPresence.openChatId)) return
        showAlertNotification(this, tap, message.data)
    }
}
