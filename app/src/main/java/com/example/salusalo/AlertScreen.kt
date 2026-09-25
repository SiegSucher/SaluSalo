package com.example.salusalo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

private val AlertsGreen = Color(0xFF397B24)
private val AlertsCream = Color(0xFFFAF9F3)
private val AlertsPale = Color(0xFFEDF4DF)
private val AlertsDark = Color(0xFF17281B)
private val AlertsGray = Color(0xFF646878)

/** Filter chip label for each alert type. */
private fun NotificationType.category() = when (this) {
    NotificationType.MESSAGE -> "Messages"
    else -> "Reservations"
}

/**
 * Real in-app alerts (Phase 4). The database creates them for new messages and
 * reservation changes; they arrive live through NotificationViewModel.
 */
@Composable
fun AlertsScreen(
    notificationViewModel: NotificationViewModel,
    onOpenConversation: (String) -> Unit,
    onOpenActivity: (String) -> Unit
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
    val today = LocalDate.now(ZoneId.systemDefault())
    fun isToday(alert: AppNotification) =
        parseTimestamp(alert.createdAt)?.atZone(ZoneId.systemDefault())?.toLocalDate() == today

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AlertsCream),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(AlertsPale),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = AlertsDark,
                        modifier = Modifier.size(27.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Alerts",
                        fontSize = 19.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertsDark
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Messages and updates on your reservations",
                        fontSize = 11.sp,
                        color = AlertsGray
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
                        tint = AlertsDark
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "All",
                    "Messages",
                    "Reservations"
                ).forEach { label ->
                    FilterChip(
                        selected = filter == label,
                        onClick = { filter = label },
                        label = {
                            Text(label, fontSize = 12.sp)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White,
                            labelColor = AlertsGreen,
                            selectedContainerColor = AlertsGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${visibleAlerts.count { it.readAt == null }} unread",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = AlertsGray
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
                    Text(
                        "Mark shown as read",
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (visibleAlerts.isEmpty()) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            "No alerts to show",
                            fontWeight = FontWeight.Bold,
                            color = AlertsDark
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "You will be alerted here when someone messages you " +
                                    "or a reservation on your listings changes.",
                            fontSize = 13.sp,
                            color = AlertsGray
                        )
                    }
                }
            }
        }

        listOf(true, false).forEach { todayGroup ->
            val group = visibleAlerts.filter { isToday(it) == todayGroup }

            if (group.isNotEmpty()) {
                item {
                    Text(
                        if (todayGroup) "Today" else "Earlier",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertsDark
                    )
                }

                items(
                    items = group,
                    key = { it.id }
                ) { alert ->
                    AlertListingCard(
                        alert = alert,
                        onClick = {
                            notificationViewModel.markRead(listOf(alert.id))
                            when (alert.type) {
                                NotificationType.MESSAGE ->
                                    alert.conversationId?.let(onOpenConversation)
                                NotificationType.RESERVATION_CLAIMED ->
                                    onOpenActivity("My Reservations")
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
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(12.dp))

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

@Composable
private fun AlertListingCard(
    alert: AppNotification,
    onClick: () -> Unit
) {
    val unread = alert.readAt == null
    val symbol = when (alert.type) {
        NotificationType.MESSAGE -> "💬"
        NotificationType.RESERVATION_CREATED -> "🧺"
        NotificationType.RESERVATION_CANCELLED -> "↩"
        NotificationType.RESERVATION_CLAIMED -> "✓"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 2.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .padding(11.dp)
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AlertsPale),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    symbol,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AlertsGreen
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 11.dp, bottom = 11.dp, end = 11.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatAlertTime(alert.createdAt),
                        modifier = Modifier.weight(1f),
                        fontSize = 10.sp,
                        color = AlertsGray
                    )

                    if (unread) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(AlertsGreen)
                        )
                    }
                }

                Text(
                    alert.title,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (unread) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Medium
                    },
                    color = AlertsDark
                )

                Text(
                    alert.body,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = AlertsGray,
                    maxLines = 3
                )

                Text(
                    alert.type.category(),
                    fontSize = 9.sp,
                    color = AlertsGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(AlertsPale)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** "3:05 PM" today, otherwise "Sep 24, 3:05 PM". */
private fun formatAlertTime(value: String): String {
    val instant = parseTimestamp(value) ?: return ""
    val zone = ZoneId.systemDefault()
    val pattern = if (instant.atZone(zone).toLocalDate() == LocalDate.now(zone)) "h:mm a" else "MMM d, h:mm a"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).withZone(zone).format(instant)
}

@Composable
private fun AlertSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}