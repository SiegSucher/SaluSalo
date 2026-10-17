package com.example.salusalo.model

import kotlinx.serialization.Serializable

/** The appeal address on the suspended screen (§36), chosen by the developer. */
const val SUPPORT_EMAIL = "aiiiiii12241@gmail.com"

/** get_my_account_status (§36): the one question a suspended account can still ask. */
@Serializable
data class AccountStatus(val suspended: Boolean = false, val reason: String? = null)

/** What the suspended person sees; the codes are admin_suspend_user's (never rename one). */
fun suspensionReasonLabel(code: String?): String = when (code) {
    "NO_SHOWS" -> "Repeated no-shows at pickup"
    "UNSAFE_FOOD" -> "Unsafe or misleading food"
    "HARASSMENT" -> "Harassment or abuse"
    "SPAM" -> "Spam or fake account"
    else -> "Breaking the community rules"
}

/** What the app does with an answer when it comes back to the screen. */
enum class StatusChange { NONE, SHOW_SUSPENDED, RESUME_SETUP }

/**
 * No answer (offline, or the migration not applied yet) changes nothing: the
 * server still refuses a suspended account everything else.
 */
fun statusChange(onSuspendedScreen: Boolean, status: AccountStatus?): StatusChange = when {
    status == null -> StatusChange.NONE
    status.suspended && !onSuspendedScreen -> StatusChange.SHOW_SUSPENDED
    !status.suspended && onSuspendedScreen -> StatusChange.RESUME_SETUP
    else -> StatusChange.NONE
}
