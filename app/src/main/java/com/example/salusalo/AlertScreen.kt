package com.example.salusalo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AlertsGreen = Color(0xFF397B24)
private val AlertsCream = Color(0xFFFAF9F3)
private val AlertsPale = Color(0xFFEDF4DF)
private val AlertsDark = Color(0xFF17281B)
private val AlertsGray = Color(0xFF646878)

private data class SampleAlert(
    val id: String,
    val title: String,
    val provider: String,
    val category: String,
    val detail: String,
    val time: String,
    val priority: String,
    val photo: Int?,
    val foodTitle: String?,
    val earlier: Boolean = false
)

@Composable
fun AlertsScreen(
    onOpenFood: (String) -> Unit
) {
    var filter by rememberSaveable {
        mutableStateOf("All")
    }

    var showSettings by rememberSaveable {
        mutableStateOf(false)
    }

    var showLearnMore by rememberSaveable {
        mutableStateOf(false)
    }

    var recommendationsEnabled by rememberSaveable {
        mutableStateOf(true)
    }

    var reservationsEnabled by rememberSaveable {
        mutableStateOf(true)
    }

    var updatesEnabled by rememberSaveable {
        mutableStateOf(true)
    }

    var readIds by rememberSaveable {
        mutableStateOf(arrayListOf<String>())
    }

    var selectedId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val alerts = remember {
        listOf(
            SampleAlert(
                id = "bread",
                title = "Nearby bakery posted 30 breads",
                provider = "Local Bakery • 1.2 km away",
                category = "Recommended",
                detail = "Surplus bread available for pickup",
                time = "5m ago",
                priority = "High",
                photo = R.drawable.food_bread,
                foodTitle = "Bread Assortment"
            ),
            SampleAlert(
                id = "rice",
                title = "Reserved rice meals ready for pickup",
                provider = "Community Kitchen • 0.8 km",
                category = "Reservations",
                detail = "Sample order #RK78A2 • Pickup within 1 hr",
                time = "15m ago",
                priority = "Medium",
                photo = R.drawable.food_meals,
                foodTitle = "Packed Rice Meals"
            ),
            SampleAlert(
                id = "produce",
                title = "Expiring soon: Fresh produce",
                provider = "Community Pantry • 0.6 km",
                category = "Recommended",
                detail = "Expires in 2 hrs • 3 boxes available",
                time = "25m ago",
                priority = "High",
                photo = R.drawable.food_produce,
                foodTitle = "Fresh Produce Box"
            ),
            SampleAlert(
                id = "approved",
                title = "Your listing was approved by admin",
                provider = "Mixed Vegetables • Sample listing #MV120",
                category = "Updates",
                detail = "Example of a listing approval notification",
                time = "1h ago",
                priority = "Update",
                photo = null,
                foodTitle = null
            ),
            SampleAlert(
                id = "saved",
                title = "Bread Assortment saved",
                provider = "Local Bakery",
                category = "Updates",
                detail = "Example of a saved food reminder",
                time = "3h ago",
                priority = "Update",
                photo = R.drawable.food_bread,
                foodTitle = "Bread Assortment",
                earlier = true
            ),
            SampleAlert(
                id = "pastry",
                title = "Pastry packs you might like",
                provider = "Neighborhood Bakery • 1.1 km",
                category = "Recommended",
                detail = "8 packs available • ₱150",
                time = "Yesterday",
                priority = "Medium",
                photo = R.drawable.food_pastries,
                foodTitle = "Pastries Assortment",
                earlier = true
            )
        )
    }

    val visibleAlerts = alerts.filter { alert ->
        val matchesFilter =
            filter == "All" || alert.category == filter

        val categoryEnabled = when (alert.category) {
            "Recommended" -> recommendationsEnabled
            "Reservations" -> reservationsEnabled
            else -> updatesEnabled
        }

        matchesFilter && categoryEnabled
    }

    val selectedAlert = alerts.firstOrNull {
        it.id == selectedId
    }

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
                        "Alerts & Recommendations",
                        fontSize = 19.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertsDark
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Stay updated on what matters to you",
                        fontSize = 11.sp,
                        color = AlertsGray
                    )
                }

                IconButton(
                    onClick = { showSettings = true }
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
                    "Recommended",
                    "Reservations",
                    "Updates"
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
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AlertsPale,
                border = BorderStroke(
                    1.dp,
                    AlertsGreen.copy(alpha = 0.12f)
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Personalized for you",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AlertsDark
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        "Discover food and keep track of pickup updates.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = AlertsGray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Sample alerts",
                            modifier = Modifier.weight(1f),
                            fontSize = 10.sp,
                            color = AlertsGreen
                        )

                        TextButton(
                            onClick = { showLearnMore = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "Learn more",
                                color = AlertsGreen,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${visibleAlerts.count { it.id !in readIds }} unread",
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    color = AlertsGray
                )

                TextButton(
                    enabled = visibleAlerts.any { it.id !in readIds },
                    onClick = {
                        readIds = ArrayList(
                            (readIds + visibleAlerts.map { it.id }).distinct()
                        )
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
                            "Try another filter or enable categories in settings.",
                            fontSize = 13.sp,
                            color = AlertsGray
                        )
                    }
                }
            }
        }

        listOf(false, true).forEach { earlier ->
            val group = visibleAlerts.filter {
                it.earlier == earlier
            }

            if (group.isNotEmpty()) {
                item {
                    Text(
                        if (earlier) "Earlier" else "Today",
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
                        unread = alert.id !in readIds,
                        onClick = {
                            if (alert.id !in readIds) {
                                readIds = ArrayList(readIds).apply {
                                    add(alert.id)
                                }
                            }

                            val foodTitle = alert.foodTitle

                            if (foodTitle != null) {
                                onOpenFood(foodTitle)
                            } else {
                                selectedId = alert.id
                            }
                        }
                    )
                }
            }
        }
    }

    if (showLearnMore) {
        AlertDialog(
            onDismissRequest = { showLearnMore = false },
            title = { Text("About recommendations") },
            text = {
                Text(
                    "These are sample alerts for testing the design.\n\n" +
                            "Later, recommendations can use food preferences, " +
                            "location, and activity. Reservation and listing " +
                            "updates will come from actual transactions."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showLearnMore = false }
                ) {
                    Text("Got it")
                }
            }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Alert settings") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 380.dp)
                ) {
                    Text(
                        "Choose which sample categories appear here. " +
                                "These switches do not control phone notifications.",
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    AlertSettingRow(
                        title = "Recommendations",
                        checked = recommendationsEnabled,
                        onCheckedChange = {
                            recommendationsEnabled = it
                        }
                    )

                    AlertSettingRow(
                        title = "Reservations",
                        checked = reservationsEnabled,
                        onCheckedChange = {
                            reservationsEnabled = it
                        }
                    )

                    AlertSettingRow(
                        title = "Updates",
                        checked = updatesEnabled,
                        onCheckedChange = {
                            updatesEnabled = it
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSettings = false }
                ) {
                    Text("Done")
                }
            }
        )
    }

    selectedAlert?.let { alert ->
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text(alert.title) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(alert.provider)
                    Text(alert.detail)

                    Text(
                        when (alert.id) {
                            "rice" ->
                                "Demo reservation only. No actual order " +
                                        "has been placed."

                            "approved" ->
                                "This is an example approval alert. " +
                                        "No listing has been submitted or approved."

                            "saved" ->
                                "This sample reminder is not connected " +
                                        "to your bookmark buttons yet."

                            else ->
                                "Sample notification. Open the listing " +
                                        "to view its food details."
                        },
                        fontSize = 12.sp,
                        color = AlertsGray
                    )
                }
            },
            confirmButton = {
                val title = alert.foodTitle

                if (title != null) {
                    TextButton(
                        onClick = {
                            selectedId = null
                            onOpenFood(title)
                        }
                    ) {
                        Text("View food")
                    }
                } else {
                    TextButton(
                        onClick = { selectedId = null }
                    ) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (alert.foodTitle != null) {
                    TextButton(
                        onClick = { selectedId = null }
                    ) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

@Composable
private fun AlertListingCard(
    alert: SampleAlert,
    unread: Boolean,
    onClick: () -> Unit
) {
    val priorityColor = when (alert.priority) {
        "High" -> Color(0xFFE95524)
        "Medium" -> Color(0xFFB76C09)
        else -> AlertsGreen
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 2.dp
    ) {
        Row {
            val photo = alert.photo

            if (photo != null) {
                Image(
                    painter = painterResource(photo),
                    contentDescription = alert.foodTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(82.dp)
                        .height(158.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(82.dp)
                        .height(158.dp)
                        .background(AlertsPale),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "✓",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertsGreen
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        alert.time,
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
                    alert.provider,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = AlertsGray
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        alert.category,
                        fontSize = 9.sp,
                        color = AlertsGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(AlertsPale)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )

                    Text(
                        alert.priority,
                        fontSize = 9.sp,
                        color = priorityColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(priorityColor.copy(alpha = 0.09f))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                Text(
                    alert.detail,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = AlertsGray
                )
            }
        }
    }
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