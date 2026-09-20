package com.example.salusalo

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AGreen = Color(0xFF39792B)
private val ADark = Color(0xFF1C2B22)
private val AGray = Color(0xFF606875)
private val ACream = Color(0xFFFCFBF6)
private val APale = Color(0xFFF2F6E6)
private val ALine = Color(0xFFDCE7C6)
private val AOrange = Color(0xFFEF7900)
private val ARed = Color(0xFFF04A27)

private data class ActivityRecord(
    val id: String,
    val tab: String,
    val title: String,
    val quantity: String,
    val distance: String,
    val posted: String,
    val status: String,
    val timeLabel: String,
    val timeValue: String,
    val photo: Int? = null,
    val uri: String? = null,
    val details: String = "",
    val sample: Boolean = true
)

@Composable
fun ActivityScreen(
    onPost: () -> Unit,
    onAlerts: () -> Unit
) {
    val context = LocalContext.current

    var tab by rememberSaveable { mutableStateOf("My Listings") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var activeOnly by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable {
        mutableStateOf("Quezon City, Metro Manila")
    }
    var locationMenu by remember { mutableStateOf(false) }

    val sampleRecords = remember {
        listOf(
            ActivityRecord(
                id = "listing1",
                tab = "My Listings",
                title = "Pastries Assortment",
                quantity = "12 pcs",
                distance = "1.1 km away",
                posted = "Posted May 17, 2025 • 9:15 AM",
                status = "Pending",
                timeLabel = "Expires in",
                timeValue = "5 hrs 40 mins",
                photo = R.drawable.food_pastries,
                details = "Example listing awaiting confirmation."
            ),
            ActivityRecord(
                id = "listing2",
                tab = "My Listings",
                title = "Bread Assortment",
                quantity = "15 pcs",
                distance = "0.9 km away",
                posted = "Posted May 16, 2025 • 4:30 PM",
                status = "Confirmed",
                timeLabel = "Pick up by",
                timeValue = "Today, 7:00 PM",
                photo = R.drawable.food_bread,
                details = "Example confirmed pickup for a bread donation."
            ),
            ActivityRecord(
                id = "listing3",
                tab = "My Listings",
                title = "Mixed Vegetables",
                quantity = "5 kg",
                distance = "2.5 km away",
                posted = "Posted May 15, 2025 • 10:20 AM",
                status = "Completed",
                timeLabel = "Completed on",
                timeValue = "May 15, 2025",
                photo = R.drawable.food_vegetables,
                details = "Example completed vegetable distribution."
            ),
            ActivityRecord(
                id = "listing4",
                tab = "My Listings",
                title = "Packed Rice Meals",
                quantity = "10 meals",
                distance = "0.8 km away",
                posted = "Posted May 13, 2025 • 11:50 AM",
                status = "Expired",
                timeLabel = "Expired on",
                timeValue = "May 14, 2025",
                photo = R.drawable.food_meals,
                details = "Example listing that passed its expiration."
            ),
            ActivityRecord(
                id = "listing5",
                tab = "My Listings",
                title = "Pastries Assortment",
                quantity = "8 pcs",
                distance = "1.1 km away",
                posted = "Posted May 10, 2025 • 8:00 AM",
                status = "Cancelled",
                timeLabel = "Cancelled on",
                timeValue = "May 10, 2025",
                photo = R.drawable.food_pastries,
                details = "Example listing cancelled by its provider."
            ),
            // The third active example appears through See all
            // and the Active Listings arrow.
            ActivityRecord(
                id = "listing6",
                tab = "My Listings",
                title = "Fresh Produce Box",
                quantity = "3 boxes",
                distance = "0.6 km away",
                posted = "Posted May 17, 2025 • 8:30 AM",
                status = "Confirmed",
                timeLabel = "Pick up by",
                timeValue = "Today, 6:00 PM",
                photo = R.drawable.food_produce
            ),
            ActivityRecord(
                "reservation1", "Reservations", "Packed Rice Meals",
                "2 meals", "0.8 km away",
                "Reserved May 17, 2025 • 9:30 AM",
                "Confirmed", "Pick up by", "Today, 7:00 PM",
                R.drawable.food_meals,
                details = "Provider: Community Kitchen\nExample reservation #RK78A2"
            ),
            ActivityRecord(
                "reservation2", "Reservations", "Bread Assortment",
                "5 pcs", "0.9 km away",
                "Reserved May 17, 2025 • 10:00 AM",
                "Pending", "Requested for", "Today, 5:00 PM",
                R.drawable.food_bread,
                details = "Provider: Local Bakery"
            ),
            ActivityRecord(
                "reservation3", "Reservations", "Fresh Produce Box",
                "1 box", "0.6 km away",
                "Reserved May 16, 2025 • 3:00 PM",
                "Cancelled", "Cancelled on", "May 16, 2025",
                R.drawable.food_produce
            ),
            ActivityRecord(
                "purchase1", "Purchases", "Pastries Assortment",
                "2 packs • ₱300", "1.1 km away",
                "Ordered May 17, 2025 • 9:00 AM",
                "Confirmed", "Pick up by", "Today, 5:30 PM",
                R.drawable.food_pastries,
                details = "Provider: Neighborhood Bakery\nExample payment: pay at pickup"
            ),
            ActivityRecord(
                "purchase2", "Purchases", "Rice Meal Packs",
                "3 meals • ₱180", "0.4 km away",
                "Ordered May 16, 2025 • 11:00 AM",
                "Completed", "Completed on", "May 16, 2025",
                R.drawable.food_meals
            ),
            ActivityRecord(
                "purchase3", "Purchases", "Farm Produce Bundle",
                "1 bundle • ₱85", "3.5 km away",
                "Ordered May 17, 2025 • 8:00 AM",
                "Pending", "Requested for", "Today, 4:00 PM",
                R.drawable.food_produce
            ),
            ActivityRecord(
                "claim1", "Claims", "Bread Assortment",
                "4 pcs • Free", "0.9 km away",
                "Claimed May 17, 2025 • 10:00 AM",
                "Confirmed", "Pick up by", "Today, 7:00 PM",
                R.drawable.food_bread,
                details = "Example pickup code: DEMO-B104"
            ),
            ActivityRecord(
                "claim2", "Claims", "Extra Vegetable Packs",
                "2 packs • Free", "3.2 km away",
                "Claimed May 16, 2025 • 9:00 AM",
                "Completed", "Completed on", "May 16, 2025",
                R.drawable.food_vegetables
            ),
            ActivityRecord(
                "claim3", "Claims", "Surplus Pastry Packs",
                "1 pack • Free", "1.1 km away",
                "Claimed May 15, 2025 • 2:00 PM",
                "Completed", "Completed on", "May 15, 2025",
                R.drawable.food_pastries
            )
        )
    }

    val localRecords = remember {
        val prefs = context.getSharedPreferences("salusalo_local_posts", 0)
        val array = JSONArray(prefs.getString("listings", "[]") ?: "[]")
        val now = System.currentTimeMillis()

        (0 until array.length()).map { index ->
            val post = array.getJSONObject(index)
            val expired = post.optLong("expiry") <= now

            ActivityRecord(
                id = "local-${post.getString("id")}",
                tab = "My Listings",
                title = post.optString("name"),
                quantity = "${post.optInt("quantity")} ${post.optString("unit")}",
                distance = post.optString("location"),
                posted = "Saved ${activityDate(post.optLong("createdAt"))}",
                status = if (expired) "Expired" else "Saved locally",
                timeLabel = if (expired) "Expired on" else "Expires on",
                timeValue = activityDate(post.optLong("expiry")),
                uri = post.optJSONArray("photos")?.optString(0),
                sample = false,
                details =
                    "Category: ${post.optString("category")}\n" +
                            "Price: ${if (post.optBoolean("donation")) "Free" else "₱${post.optString("price")} per unit"}\n" +
                            "Description: ${post.optString("description").ifBlank { "Not provided" }}\n" +
                            "Pickup: ${post.optString("location")}\n" +
                            "Starts: ${activityDate(post.optLong("pickupStart"))}\n" +
                            "Ends: ${activityDate(post.optLong("pickupEnd"))}\n" +
                            "Distribution: ${post.optString("distribution")}\n" +
                            "Partial claims: ${post.optBoolean("partial")}\n" +
                            "Maximum: ${post.optString("maximum").ifBlank { "No additional limit" }}\n" +
                            "Allergens: ${post.optString("allergens").ifBlank { "Not provided" }}\n" +
                            "Storage: ${post.optString("storage").ifBlank { "Not provided" }}"
            )
        }.reversed()
    }

    val allRecords = sampleRecords + localRecords

    val filtered = if (activeOnly) {
        sampleRecords.filter {
            it.tab == "My Listings" &&
                    it.status in listOf("Pending", "Confirmed")
        }
    } else {
        allRecords.filter { it.tab == tab }
    }

    val displayed = if (expanded || activeOnly) filtered else filtered.take(5)
    val selected = allRecords.firstOrNull { it.id == selectedId }

    BackHandler(enabled = expanded || activeOnly) {
        expanded = false
        activeOnly = false
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ACream
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 20.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LeafBadge(46.dp)
                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            "My Activity",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            color = ADark
                        )
                        Text(
                            "Track your listings, reservations, purchases & claims",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = AGray
                        )
                    }

                    IconButton(onClick = onAlerts) {
                        Box {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = "Open alerts",
                                tint = ADark,
                                modifier = Modifier.size(28.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(AOrange)
                            )
                        }
                    }
                }
            }

            item {
                Box {
                    Row(
                        modifier = Modifier
                            .clickable { locationMenu = true }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = AGreen,
                            modifier = Modifier.size(21.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            location,
                            modifier = Modifier.weight(1f, fill = false),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ADark
                        )
                        Spacer(Modifier.width(7.dp))
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = "Choose display location",
                            tint = ADark,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = locationMenu,
                        onDismissRequest = { locationMenu = false }
                    ) {
                        listOf(
                            "Quezon City, Metro Manila",
                            "Del Rosarrio, Balilihan",
                            "Hanopol Este, Balilihan"
                        ).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    location = option
                                    locationMenu = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = APale,
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, ALine),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 10.dp,
                            vertical = 16.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LeafBadge(35.dp)
                        Spacer(Modifier.width(9.dp))

                        ActivityMetric(
                            "Completed", "48", "transactions",
                            Modifier.weight(1f)
                        ) {
                            notice = "48 completed transactions is a demonstration " +
                                    "total matching the reference design."
                        }

                        MetricDivider()

                        ActivityMetric(
                            "Items Rescued", "128", "items",
                            Modifier.weight(1f)
                        ) {
                            notice = "128 items rescued is a demonstration total. " +
                                    "It is not calculated from your local listings."
                        }

                        MetricDivider()

                        Row(
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable {
                                    tab = "My Listings"
                                    activeOnly = true
                                    expanded = true
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActivityMetric(
                                "Active Listings", "3", "listings",
                                Modifier.weight(1f)
                            ) {
                                tab = "My Listings"
                                activeOnly = true
                                expanded = true
                            }

                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = "View active listings",
                                tint = AGreen,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(7.dp))
            }

            item {
                val tabs = listOf(
                    "My Listings" to Icons.Outlined.FormatListBulleted,
                    "Reservations" to Icons.Outlined.BookmarkBorder,
                    "Purchases" to Icons.Outlined.ShoppingBag,
                    "Claims" to Icons.Outlined.Spa
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    tabs.forEach { (label, icon) ->
                        val chosen = tab == label

                        Surface(
                            modifier = Modifier.clickable {
                                tab = label
                                activeOnly = false
                                expanded = false
                            },
                            shape = RoundedCornerShape(15.dp),
                            color = Color.Transparent,
                            border = if (chosen) null else BorderStroke(1.dp, ALine)
                        ) {
                            Row(
                                modifier = Modifier
                                    .background(
                                        if (chosen) {
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFF367025),
                                                    Color(0xFF4B842E)
                                                )
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                listOf(Color.White, Color.White)
                                            )
                                        }
                                    )
                                    .padding(horizontal = 11.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (chosen) Color.White else AGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    color = if (chosen) Color.White else AGreen
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (activeOnly) "Active Listings" else tab,
                        modifier = Modifier.weight(1f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ADark
                    )

                    TextButton(
                        onClick = {
                            if (activeOnly) {
                                activeOnly = false
                                expanded = true
                            } else {
                                expanded = !expanded
                            }
                        }
                    ) {
                        Text(
                            when {
                                activeOnly -> "All listings"
                                expanded -> "Show less"
                                else -> "See all"
                            },
                            color = AGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            items(displayed, key = { it.id }) { record ->
                ReferenceActivityCard(record) {
                    selectedId = record.id
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            notice = "Share surplus food by creating a listing. " +
                                    "The activity examples and historical dates on " +
                                    "this screen reproduce your reference design."
                        },
                    color = APale,
                    border = BorderStroke(1.dp, ALine),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Spa,
                            contentDescription = null,
                            tint = AGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(10.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                "Thanks for making a difference!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ADark
                            )
                            Text(
                                "Every item you share helps reduce food waste " +
                                        "and support your community.",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = AGreen
                            )
                        }

                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = "Learn more",
                            tint = AGreen,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }
    }

    selected?.let { record ->
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = { Text(record.title) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RecordPhoto(record, Modifier.size(100.dp))
                    Text(record.quantity)
                    Text("Status: ${record.status}")
                    Text(record.distance)
                    Text(record.posted)
                    Text("${record.timeLabel}: ${record.timeValue}")

                    if (record.details.isNotBlank()) {
                        Text(record.details, fontSize = 12.sp)
                    }

                    Text(
                        if (record.sample) {
                            "Demonstration record. Dates, distances, and statuses " +
                                    "are sample values; no actual transaction exists."
                        } else {
                            "Your locally saved listing. It has not been published online."
                        },
                        fontSize = 11.sp,
                        color = AGray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedId = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (notice.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { notice = "" },
            title = { Text("My Activity") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = { notice = "" }) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        notice = ""
                        onPost()
                    }
                ) {
                    Text("Post Food")
                }
            }
        )
    }
}

@Composable
private fun ReferenceActivityCard(
    record: ActivityRecord,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFF0EFE9)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecordPhoto(
                record,
                Modifier
                    .width(66.dp)
                    .height(82.dp)
            )

            Spacer(Modifier.width(9.dp))

            Column(
                modifier = Modifier.weight(1.55f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    record.title,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ADark
                )

                Text(record.quantity, fontSize = 10.sp, color = AGray)

                SmallActivityLine(
                    Icons.Outlined.LocationOn,
                    record.distance
                )
                SmallActivityLine(
                    Icons.Outlined.CalendarMonth,
                    record.posted
                )
            }

            Spacer(Modifier.width(7.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActivityStatus(record.status)

                Text(
                    record.timeLabel,
                    fontSize = 9.sp,
                    color = AGray
                )
                Text(
                    record.timeValue,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = if (record.status == "Pending") ARed else AGray
                )
            }

            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "View ${record.title}",
                tint = AGray,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun ActivityStatus(status: String) {
    val color = when (status) {
        "Pending" -> AOrange
        "Confirmed", "Saved locally" -> AGreen
        "Expired", "Cancelled" -> ARed
        else -> AGray
    }

    val icon = when (status) {
        "Pending", "Expired" -> Icons.Outlined.Schedule
        "Cancelled" -> Icons.Outlined.Cancel
        else -> Icons.Outlined.CheckCircle
    }

    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                status,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                color = color
            )
        }
    }
}

@Composable
private fun SmallActivityLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = AGray,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = AGray
        )
    }
}

@Composable
private fun RecordPhoto(
    record: ActivityRecord,
    modifier: Modifier
) {
    Box(modifier.clip(RoundedCornerShape(10.dp))) {
        val uri = record.uri
        val photo = record.photo

        if (!uri.isNullOrBlank()) {
            PostPhoto(uri)
        } else if (photo != null) {
            Image(
                painter = painterResource(photo),
                contentDescription = record.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun LeafBadge(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFF4F7E7),
                        Color(0xFFDFEBC4)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.Spa,
            contentDescription = null,
            tint = AGreen,
            modifier = Modifier.fillMaxSize(0.68f)
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .height(42.dp)
            .width(1.dp)
            .background(Color(0xFFCDDBAF))
    )
}

@Composable
private fun ActivityMetric(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(modifier.clickable(onClick = onClick)) {
        Text(
            title,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = ADark
        )
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = ADark
        )
        Text(
            caption,
            fontSize = 9.sp,
            color = AGreen
        )
    }
}

private fun activityDate(value: Long): String {
    if (value <= 0L) return "Not provided"

    return SimpleDateFormat(
        "MMM d, yyyy • h:mm a",
        Locale.getDefault()
    ).format(Date(value))
}