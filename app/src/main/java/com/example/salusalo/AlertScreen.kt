package com.example.salusalo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.salusalo.model.AppNotification
import com.example.salusalo.model.NotificationType
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.viewmodel.NotificationViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Filter chip label for each alert type. */
private fun NotificationType.category() = when (this) {
    NotificationType.MESSAGE -> "Messages"
    else -> "Reservations"
}

/**
 * How each kind of alert looks: its icon, colours and a short label, so the list
 * can be scanned. [labelColor] is the label's own colour, readable on the row.
 */
private class AlertStyle(
    val icon: ImageVector,
    val tint: Color,
    val background: Color,
    val label: String,
    val labelColor: Color = tint
)

private fun NotificationType.style(): AlertStyle = when (this) {
    NotificationType.MESSAGE ->
        AlertStyle(Icons.Outlined.ChatBubbleOutline, SaluColors.Green, SaluColors.IconGreen, "Message")
    NotificationType.RESERVATION_CREATED ->
        AlertStyle(Icons.Outlined.BookmarkAdded, SaluColors.Orange, SaluColors.OrangeSoft, "New reservation", SaluColors.DarkGreen)
    NotificationType.RESERVATION_CANCELLED ->
        AlertStyle(Icons.Outlined.EventBusy, SaluColors.Error, SaluColors.ErrorSoft, "Cancelled")
    NotificationType.RESERVATION_CLAIMED ->
        AlertStyle(Icons.Outlined.TaskAlt, Color.White, SaluColors.Green, "Claimed", SaluColors.Green)
    NotificationType.RESERVATION_REMOVED ->
        AlertStyle(Icons.Outlined.RemoveCircleOutline, SaluColors.Gray, SaluColors.Neutral, "Listing removed")
}

/**
 * Real in-app alerts (Phase 4). The database creates them for new messages and
 * reservation changes; they arrive live through NotificationViewModel.
 */
@Composable
fun AlertsScreen(
    notificationViewModel: NotificationViewModel,
    onOpenConversation: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
    onOpenClaimedReservation: (String?) -> Unit,
    onOpenRemovedReservation: (String?) -> Unit
) {
    val alerts by notificationViewModel.notifications.collectAsStateWithLifecycle()
    val preferences by notificationViewModel.preferences.collectAsStateWithLifecycle()
    val error by notificationViewModel.message.collectAsStateWithLifecycle()

    var filter by rememberSaveable {
        mutableStateOf("All")
    }

    var showSettings by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) { notificationViewModel.refresh() }

    val visibleAlerts = alerts.filter { alert ->
        filter == "All" || alert.type.category() == filter
    }
    val unreadShown = visibleAlerts.count { it.readAt == null }
    val today = LocalDate.now(ZoneId.systemDefault())
    fun isToday(alert: AppNotification) =
        parseTimestamp(alert.createdAt)?.atZone(ZoneId.systemDefault())?.toLocalDate() == today

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SaluColors.Cream),
        contentPadding = PaddingValues(
            top = 16.dp,
            bottom = 24.dp
        )
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Alerts",
                        style = SaluText.ScreenTitle,
                        color = SaluColors.DarkGreen,
                        modifier = Modifier.semantics { heading() }
                    )

                    Text(
                        "Messages and updates on your reservations",
                        style = SaluText.Supporting,
                        color = SaluColors.Gray
                    )
                }

                IconButton(
                    onClick = {
                        notificationViewModel.loadPreferences()
                        showSettings = true
                    }
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Alert settings",
                        tint = SaluColors.DarkGreen
                    )
                }
            }
        }

        item(key = "filters") {
            LazyRow(
                modifier = Modifier.padding(top = 16.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    listOf(
                        "All" to null,
                        "Messages" to Icons.Outlined.ChatBubbleOutline,
                        "Reservations" to Icons.Outlined.BookmarkBorder
                    ),
                    key = { it.first }
                ) { (label, icon) ->
                    SaluFilterChip(
                        label = label,
                        selected = filter == label,
                        onClick = { filter = label },
                        icon = icon
                    )
                }
            }
        }

        item(key = "unread") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (unreadShown == 0) "All caught up" else "$unreadShown unread",
                    modifier = Modifier.weight(1f),
                    style = SaluText.Meta,
                    color = if (unreadShown == 0) SaluColors.Gray else SaluColors.Green
                )

                TextButton(
                    enabled = visibleAlerts.any { it.readAt == null },
                    onClick = {
                        if (filter == "All") {
                            notificationViewModel.markAllRead()
                        } else {
                            notificationViewModel.markRead(visibleAlerts.map { it.id })
                        }
                    }
                ) {
                    Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mark shown as read", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (visibleAlerts.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.NotificationsNone,
                    title = if (filter == "All") "No alerts to show" else "No ${filter.lowercase(Locale.ENGLISH)} alerts",
                    message = "You will be alerted here when someone messages you " +
                            "or a reservation on your listings changes."
                )
            }
        }

        listOf(true, false).forEach { todayGroup ->
            val group = visibleAlerts.filter { isToday(it) == todayGroup }

            if (group.isNotEmpty()) {
                item(key = if (todayGroup) "group-today" else "group-earlier") {
                    GroupLabel(
                        if (todayGroup) "Today" else "Earlier",
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                itemsIndexed(
                    items = group,
                    key = { _, alert -> alert.id }
                ) { index, alert ->
                    AlertRow(
                        alert = alert,
                        first = index == 0,
                        last = index == group.lastIndex,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 20.dp),
                        onClick = {
                            notificationViewModel.markRead(listOf(alert.id))
                            when (alert.type) {
                                NotificationType.MESSAGE ->
                                    alert.conversationId?.let(onOpenConversation)
                                NotificationType.RESERVATION_CLAIMED ->
                                    onOpenClaimedReservation(alert.reservationId)
                                NotificationType.RESERVATION_REMOVED ->
                                    onOpenRemovedReservation(alert.reservationId)
                                else ->
                                    onOpenActivity("My Donations")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showSettings) {
        var messageAlerts by remember(preferences) { mutableStateOf(preferences?.messageAlerts ?: true) }
        var reservationAlerts by remember(preferences) { mutableStateOf(preferences?.reservationAlerts ?: true) }
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Alert settings") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    Text(
                        "Choose which events create alerts for your account. " +
                                "These settings are saved to your account. " +
                                "Messages still arrive in Messages when message alerts are off.",
                        style = SaluText.Supporting,
                        color = SaluColors.Gray
                    )

                    Spacer(Modifier.height(12.dp))

                    if (preferences == null) {
                        Text("Loading your alert settings…", style = SaluText.Body)
                    } else {
                        AlertSettingRow(
                            title = "New messages",
                            checked = messageAlerts,
                            onCheckedChange = { messageAlerts = it }
                        )

                        AlertSettingRow(
                            title = "Reservation updates",
                            checked = reservationAlerts,
                            onCheckedChange = { reservationAlerts = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = preferences != null,
                    onClick = {
                        preferences?.let {
                            notificationViewModel.savePreferences(
                                it.copy(messageAlerts = messageAlerts, reservationAlerts = reservationAlerts)
                            ) { showSettings = false }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettings = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    error?.let { text ->
        AlertDialog(
            onDismissRequest = { notificationViewModel.clearMessage() },
            title = { Text("Alerts") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { notificationViewModel.clearMessage() }) { Text("OK") }
            }
        )
    }
}

/**
 * One alert in a grouped list: its kind as an icon and label, the title, the
 * text and the time. Unread alerts have a tinted row, a bold title and a dot,
 * and say "Unread" to screen readers.
 */
@Composable
private fun AlertRow(
    alert: AppNotification,
    first: Boolean,
    last: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val unread = alert.readAt == null
    val style = alert.type.style()
    val shape = RoundedCornerShape(
        topStart = if (first) 18.dp else 0.dp,
        topEnd = if (first) 18.dp else 0.dp,
        bottomStart = if (last) 18.dp else 0.dp,
        bottomEnd = if (last) 18.dp else 0.dp
    )

    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (unread) SaluColors.PaleGreen else Color.White)
                .clickable(onClick = onClick)
                .semantics { stateDescription = if (unread) "Unread" else "Read" }
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconBubble(style.icon, tint = style.tint, background = style.background, size = 42.dp, iconSize = 22.dp)

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        alert.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = SaluColors.DarkGreen
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        formatAlertTime(alert.createdAt),
                        style = SaluText.Meta,
                        color = if (unread) SaluColors.Green else SaluColors.Gray
                    )

                    if (unread) {
                        Box(
                            Modifier
                                .padding(start = 6.dp, top = 5.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SaluColors.Orange)
                        )
                    }
                }

                Text(
                    alert.body,
                    style = SaluText.Supporting,
                    color = SaluColors.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    style.label,
                    style = SaluText.Status,
                    color = style.labelColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (!last) HorizontalDivider(color = SaluColors.Line)
    }
}

/** "3:05 PM" today, otherwise "Sep 24, 3:05 PM". */
private fun formatAlertTime(value: String): String {
    val instant = parseTimestamp(value) ?: return ""
    val zone = ZoneId.systemDefault()
    val pattern = if (instant.atZone(zone).toLocalDate() == LocalDate.now(zone)) "h:mm a" else "MMM d, h:mm a"
    return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withZone(zone).format(instant)
}

@Composable
private fun AlertSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = SaluText.Body,
            color = SaluColors.DarkGreen
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
