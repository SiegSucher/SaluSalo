package com.example.salusalo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager

// "Notification sound" is a setting of this phone, not of the account: it is kept
// in salusalo_session (next to "Keep me signed in"), which sign-out does not clear.
private fun soundPreferences(context: Context) =
    context.getSharedPreferences("salusalo_session", 0)

internal fun notificationSoundOn(context: Context): Boolean =
    soundPreferences(context).getBoolean("notification_sound", true)

internal fun setNotificationSoundOn(context: Context, on: Boolean) {
    soundPreferences(context).edit().putBoolean("notification_sound", on).apply()
}

/**
 * Plays the phone's default notification sound for a new in-app alert. There is
 * no Android notification and no permission, so it only plays while the app is
 * open. It stays quiet in silent and vibrate mode.
 */
class AlertSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var current: Ringtone? = null

    fun play() {
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        stop()
        // A missing or "None" notification sound plays nothing.
        current = runCatching {
            RingtoneManager.getRingtone(appContext, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }.getOrNull()
    }

    fun stop() {
        runCatching { current?.stop() }
        current = null
    }
}
