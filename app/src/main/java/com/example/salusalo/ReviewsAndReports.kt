package com.example.salusalo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salusalo.data.ReportRepository
import com.example.salusalo.data.shortErrorText
import com.example.salusalo.model.MyReview
import com.example.salusalo.model.REPORT_DETAILS_MAX
import com.example.salusalo.model.REVIEW_COMMENT_MAX
import com.example.salusalo.model.ReportReason
import com.example.salusalo.model.ReportTarget
import com.example.salusalo.model.ReviewItem
import com.example.salusalo.model.dateText
import com.example.salusalo.model.starsText
import com.example.salusalo.viewmodel.ReviewResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Dark amber for ratings ("★ 4.7 (12)"), readable on white. */
internal val RatingAmber = Color(0xFFB26A00)
private val StarGold = Color(0xFFFFAD08)
private val ReviewDark = Color(0xFF203E2C)
private val ReviewGray = Color(0xFF626870)

/** Shown where someone has no reviews: only donors and sellers are rated. */
internal const val NO_RATING_LINE =
    "Donors and sellers are rated after each pickup; people who only receive food have no rating."

/**
 * The page to rate a donor or seller after a claimed pickup, or to change that
 * review. [existing] prefills it. Sending goes through [onSend]; the result
 * ([result], from ReservationViewModel.reviewResult) is shown only here.
 */
@Composable
fun RateDonorPage(
    reservationId: String,
    // "Rate <name>" or "Change your review".
    title: String,
    listingName: String,
    // e.g. "3 packs • ₱135.30"
    summaryLine: String,
    existing: MyReview?,
    saving: Boolean,
    result: ReviewResult?,
    onSend: (rating: Int, comment: String) -> Unit,
    onClose: () -> Unit
) {
    var stars by rememberSaveable(reservationId) { mutableIntStateOf(existing?.rating ?: 0) }
    var comment by rememberSaveable(reservationId) { mutableStateOf(existing?.comment.orEmpty()) }
    var error by remember { mutableStateOf("") }
    val saved = (result as? ReviewResult.Saved)?.takeIf { it.review.reservationId == reservationId }
    val failure = (result as? ReviewResult.Failed)?.message

    // A review that is being sent is never dropped; Back waits until it has finished.
    ActionPage(heading = title, onBack = { if (!saving) onClose() }) {
        ActionCard {
            Text(listingName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ReviewDark)
            Text(summaryLine, fontSize = 13.sp, color = ReviewGray)
        }

        if (saved != null) {
            ActionCard {
                Text(
                    if (saved.review.created == false) "Your review was updated." else "Thanks! Your review was sent.",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ReviewDark
                )
                Text(starsText(saved.review.rating), color = StarGold, fontSize = 20.sp)
                saved.review.comment?.let { Text(it, fontSize = 13.sp) }
            }
            ActionButton(text = "Done", onClick = onClose)
        } else {
            ActionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { n ->
                        IconButton(
                            onClick = {
                                stars = n
                                error = ""
                            },
                            enabled = !saving
                        ) {
                            Icon(
                                if (n <= stars) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (n == 1) "1 star" else "$n stars",
                                tint = StarGold,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
                Text(
                    if (stars == 0) "Tap a star to rate." else "$stars of 5 stars",
                    fontSize = 13.sp,
                    color = ReviewGray
                )
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= REVIEW_COMMENT_MAX) comment = it },
                label = { Text("Comment (optional)") },
                supportingText = { Text("${comment.length} / $REVIEW_COMMENT_MAX") },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 3,
                maxLines = 6
            )

            Text(
                "Your name, stars and comment are shown to other users.",
                fontSize = 13.sp,
                color = ReviewDark
            )

            val shownError = error.ifBlank { failure.orEmpty() }
            if (shownError.isNotBlank()) {
                Text(shownError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            ActionButton(
                text = when {
                    saving -> "Sending…"
                    existing != null -> "Save review"
                    else -> "Send review"
                },
                enabled = !saving,
                onClick = {
                    if (stars !in 1..5) {
                        error = "Choose from 1 to 5 stars."
                    } else {
                        error = ""
                        onSend(stars, comment)
                    }
                }
            )
        }
    }
}

/**
 * The page to report a listing, its donor or seller, or the other person in a
 * chat. The reason list follows [target]. Nothing is shown to the reported
 * person.
 */
@Composable
fun ReportPage(
    target: ReportTarget,
    // The listing for LISTING and DONOR, the conversation for CHAT.
    targetId: String,
    // "Report listing" or "Report <name>".
    heading: String,
    // "What's wrong with <listing>?" or "Why are you reporting <name>?"
    prompt: String,
    onClose: () -> Unit
) {
    val repository = remember { ReportRepository() }
    val scope = rememberCoroutineScope()
    val reasons = remember(target) { ReportReason.forTarget(target) }
    var reasonName by rememberSaveable(target, targetId) { mutableStateOf<String?>(null) }
    var details by rememberSaveable(target, targetId) { mutableStateOf("") }
    var sent by rememberSaveable(target, targetId) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val reason = reasons.firstOrNull { it.name == reasonName }

    ActionPage(heading = heading, onBack = { if (!sending) onClose() }) {
        if (sent) {
            ActionCard {
                Text(
                    "Report sent. The Salu-Salo team will review it.",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ReviewDark
                )
            }
            ActionButton(text = "Done", onClick = onClose)
            return@ActionPage
        }

        ActionCard {
            Text(prompt, fontWeight = FontWeight.Bold, color = ReviewDark)
            reasons.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = option == reason,
                            enabled = !sending,
                            role = Role.RadioButton,
                            onClick = {
                                reasonName = option.name
                                error = ""
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = option == reason, onClick = null, enabled = !sending)
                    Text(option.label, modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
                }
            }
        }

        OutlinedTextField(
            value = details,
            onValueChange = { if (it.length <= REPORT_DETAILS_MAX) details = it },
            label = { Text(if (reason?.needsDetails == true) "Details (required)" else "Details (optional)") },
            supportingText = { Text("${details.length} / $REPORT_DETAILS_MAX") },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            minLines = 3,
            maxLines = 8
        )

        Text(
            "Reports go to the Salu-Salo team. The other person is not told.",
            fontSize = 13.sp,
            color = ReviewDark
        )

        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        ActionButton(
            text = if (sending) "Sending…" else "Send report",
            enabled = !sending,
            onClick = {
                when {
                    reason == null -> error = "Choose a reason."
                    reason.needsDetails && details.isBlank() -> error = "Describe the problem in a few words."
                    else -> {
                        sending = true
                        error = ""
                        scope.launch {
                            try {
                                repository.submitReport(target, targetId, reason, details)
                                sent = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = shortErrorText(
                                    failure,
                                    "Your report could not be sent. Check your internet connection and try again."
                                )
                            } finally {
                                sending = false
                            }
                        }
                    }
                }
            }
        )
    }
}

/** One review: the reviewer's name, the stars, the comment and the date. */
@Composable
fun ReviewRow(item: ReviewItem) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(item.reviewerName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ReviewDark)
        Text(
            starsText(item.rating),
            color = StarGold,
            fontSize = 14.sp,
            modifier = Modifier.semantics { contentDescription = "${item.rating} of 5 stars" }
        )
        item.comment?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp) }
        item.dateText()?.let { Text(it, fontSize = 11.sp, color = ReviewGray) }
    }
}
