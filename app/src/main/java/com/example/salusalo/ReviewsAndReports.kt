package com.example.salusalo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.graphics.graphicsLayer
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

/** Ratings ("★ 4.7 (12)") in the original amber, and the stars in the original gold. */
internal val RatingAmber = SaluColors.RatingAmber
private val StarGold = SaluColors.StarGold

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
            Text(listingName, style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
            Text(summaryLine, style = SaluText.Supporting, color = SaluColors.Gray)
        }

        if (saved != null) {
            ActionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(
                        Icons.Outlined.CheckCircle,
                        tint = SaluColors.Green,
                        background = SaluColors.IconGreen,
                        size = 44.dp,
                        iconSize = 24.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (saved.review.created == false) "Your review was updated." else "Thanks! Your review was sent.",
                        style = SaluText.FoodTitle,
                        color = SaluColors.DarkGreen
                    )
                }
                Text(
                    starsText(saved.review.rating),
                    color = StarGold,
                    fontSize = 22.sp,
                    modifier = Modifier.semantics { contentDescription = "${saved.review.rating} of 5 stars" }
                )
                saved.review.comment?.let { Text(it, style = SaluText.Body, color = SaluColors.DarkGreen) }
            }
            ActionButton(text = "Done", onClick = onClose)
        } else {
            ActionCard {
                Text("How was the pickup?", style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
                Row(
                    modifier = Modifier.selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { n ->
                        val filled = n <= stars
                        val scale by animateFloatAsState(
                            if (filled) 1f else 0.9f,
                            spring(dampingRatio = 0.6f),
                            label = "star$n"
                        )
                        IconButton(
                            onClick = {
                                stars = n
                                error = ""
                            },
                            enabled = !saving
                        ) {
                            Icon(
                                if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (n == 1) "1 star" else "$n stars",
                                tint = if (filled) StarGold else SaluColors.Gray,
                                modifier = Modifier
                                    .size(36.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                            )
                        }
                    }
                }
                Text(
                    if (stars == 0) "Tap a star to rate." else "$stars of 5 stars",
                    style = SaluText.Supporting,
                    color = SaluColors.Gray
                )
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= REVIEW_COMMENT_MAX) comment = it },
                label = { Text("Comment (optional)") },
                supportingText = { Text("${comment.length} / $REVIEW_COMMENT_MAX") },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                shape = SaluRadius.Control,
                minLines = 3,
                maxLines = 6,
                colors = saluFieldColors()
            )

            InlineNotice(
                text = "Your name, stars and comment are shown to other users.",
                icon = Icons.Outlined.Info
            )

            val shownError = error.ifBlank { failure.orEmpty() }
            if (shownError.isNotBlank()) {
                InlineNotice(text = shownError, icon = Icons.Outlined.ErrorOutline, tone = NoticeTone.Error)
            }

            ActionButton(
                text = when {
                    saving -> "Sending…"
                    existing != null -> "Save review"
                    else -> "Send review"
                },
                enabled = !saving,
                loading = saving,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(
                        Icons.Outlined.CheckCircle,
                        tint = SaluColors.Green,
                        background = SaluColors.IconGreen,
                        size = 44.dp,
                        iconSize = 24.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Report sent. The Salu-Salo team will review it.",
                        style = SaluText.FoodTitle,
                        color = SaluColors.DarkGreen
                    )
                }
            }
            ActionButton(text = "Done", onClick = onClose)
            return@ActionPage
        }

        ActionCard {
            Text(prompt, style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
            Column(Modifier.selectableGroup()) {
                reasons.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
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
                        Text(
                            option.label,
                            modifier = Modifier.padding(start = 8.dp),
                            style = SaluText.Body,
                            color = SaluColors.DarkGreen
                        )
                    }
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
            shape = SaluRadius.Control,
            minLines = 3,
            maxLines = 8,
            colors = saluFieldColors()
        )

        InlineNotice(
            text = "Reports go to the Salu-Salo team. The other person is not told.",
            icon = Icons.Outlined.Info
        )

        if (error.isNotBlank()) {
            InlineNotice(text = error, icon = Icons.Outlined.ErrorOutline, tone = NoticeTone.Error)
        }

        ActionButton(
            text = if (sending) "Sending…" else "Send report",
            enabled = !sending,
            loading = sending,
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

/** One review: the reviewer's initials and name, the stars, the comment and the date. */
@Composable
fun ReviewRow(item: ReviewItem) {
    Row(verticalAlignment = Alignment.Top) {
        Avatar(photoUrl = null, name = item.reviewerName, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.reviewerName,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                    color = SaluColors.DarkGreen,
                    modifier = Modifier.weight(1f)
                )
                item.dateText()?.let { Text(it, style = SaluText.Meta, color = SaluColors.Gray) }
            }
            Text(
                starsText(item.rating),
                color = StarGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { contentDescription = "${item.rating} of 5 stars" }
            )
            item.comment?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = SaluText.Body, color = SaluColors.DarkGreen)
            }
        }
    }
}
