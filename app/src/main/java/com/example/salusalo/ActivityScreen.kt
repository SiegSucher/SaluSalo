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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.ReservationStatus
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.reservationTotal
import com.example.salusalo.viewmodel.FoodViewModel
import com.example.salusalo.viewmodel.ReservationViewModel
import com.example.salusalo.viewmodel.MessageViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val sample: Boolean = true,
    val ownedPost: FoodPost? = null,
    val reservationId: String? = null,
    val donorCanClaim: Boolean = false
)

@Composable
fun ActivityScreen(
    onPost: () -> Unit,
    onAlerts: () -> Unit,
    foodViewModel: FoodViewModel,
    reservationViewModel: ReservationViewModel,
    messageViewModel: MessageViewModel,
    unreadAlerts: Int = 0,
    initialTab: String = "My Listings",
    // A reservation to open, from a "Reservation claimed" alert.
    focusReservationId: String? = null,
    onFocusHandled: () -> Unit = {}
) {
    val myPosts by foodViewModel.myPosts.collectAsStateWithLifecycle()
    val myReservations by reservationViewModel.myReservations.collectAsStateWithLifecycle()
    val donorReservations by reservationViewModel.donorReservations.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<FoodPost?>(null) }

    LaunchedEffect(Unit) {
        foodViewModel.loadMyPosts()
        reservationViewModel.refresh()
    }

    // Show reservation results (cancel, claim, or a failed load) on this screen,
    // then clear them so they do not reappear later in the food details dialog.
    val reservationMessage by reservationViewModel.message.collectAsStateWithLifecycle()
    var reservationNotice by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(reservationMessage) {
        reservationMessage?.let {
            reservationNotice = it
            reservationViewModel.clearMessage()
            // A cancel returns quantity to the listing, so refresh the Home feed.
            if (it.startsWith("Reservation cancelled")) foodViewModel.loadAvailablePosts()
        }
    }

    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
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
            )
        )
    }

    // Food posts are the real Phase 2 records; the reservation tabs below are
    // live too. Only the samples filtered out of allRecords remain.
    val myListingRecords = myPosts
        .sortedByDescending { it.createdAt.orEmpty() }
        .map { post ->
            ActivityRecord(
                id = post.id,
                tab = "My Listings",
                title = post.name,
                quantity = "${post.quantity} ${post.unit}",
                distance = post.pickupLocation,
                posted = post.createdAt?.let { "Posted ${formatActivityTimestamp(it)}" }
                    ?: "Posted recently",
                status = post.status.name.lowercase()
                    .replaceFirstChar { it.uppercase() },
                timeLabel = "Expires",
                timeValue = formatActivityTimestamp(post.expirationDate),
                uri = post.displayImageUrl,
                details = buildString {
                    append("Category: ${post.category}")
                    post.description?.takeIf { it.isNotBlank() }?.let {
                        append("\nDescription: $it")
                    }
                },
                sample = false,
                ownedPost = post
            )
        }

    // Recipient-facing records: only reservations created by this signed-in user.
    // Open and cancelled ones stay in My Reservations; once the donor marks one
    // claimed it moves to Purchases (priced) or Claims (free). Prices come from
    // the reservation's unit_price, locked in by the database when it was made.
    val myReservationRecords = myReservations.map { entry ->
        val reservation = entry.reservation
        val post = entry.foodPost
        val claimed = reservation.status == ReservationStatus.CLAIMED
        val priced = reservation.unitPrice > 0
        val total = formatPeso(reservationTotal(reservation.unitPrice, reservation.quantity))
        ActivityRecord(
            id = "my-reservation-${reservation.id}",
            tab = when {
                !claimed -> "My Reservations"
                priced -> "Purchases"
                else -> "Claims"
            },
            title = post.name,
            quantity = "${reservation.quantity} ${post.unit} • ${if (priced) total else "Free"}",
            distance = post.pickupLocation,
            posted = reservation.createdAt?.let { "Reserved ${formatActivityTimestamp(it)}" } ?: "Reserved recently",
            status = reservation.status.name.lowercase().replaceFirstChar { it.uppercase() },
            timeLabel = if (claimed) "Picked up" else "Pickup",
            timeValue = if (claimed) {
                reservation.updatedAt?.let(::formatActivityTimestamp) ?: "Recently"
            } else {
                post.pickupLocation
            },
            uri = post.displayImageUrl,
            details = when {
                claimed && priced ->
                    "Price: ${formatPeso(reservation.unitPrice)} per unit\nTotal: $total, paid at pickup."
                claimed -> "Free food you picked up."
                priced ->
                    "Your reservation.\nPrice: ${formatPeso(reservation.unitPrice)} per unit\nPay $total at pickup."
                else -> "Your reservation."
            },
            sample = false, reservationId = reservation.id, donorCanClaim = false
        )
    }

    // Donor-facing records: only reservations made on this user's food posts.
    val donorReservationRecords = donorReservations.map { entry ->
        val reservation = entry.reservation
        val post = entry.foodPost
        val priced = reservation.unitPrice > 0
        val total = formatPeso(reservationTotal(reservation.unitPrice, reservation.quantity))
        ActivityRecord(
            id = "donor-reservation-${reservation.id}", tab = "My Donations",
            title = post.name,
            quantity = "${reservation.quantity} ${post.unit} • ${if (priced) total else "Free"}",
            distance = post.pickupLocation,
            posted = reservation.createdAt?.let { "Reserved ${formatActivityTimestamp(it)}" } ?: "Reserved recently",
            status = reservation.status.name.lowercase().replaceFirstChar { it.uppercase() },
            timeLabel = "Pickup", timeValue = post.pickupLocation,
            uri = post.displayImageUrl,
            details = "Recipient: ${entry.recipientName ?: "Unknown"}\nReservation for your listing." +
                if (priced) "\nPrice: ${formatPeso(reservation.unitPrice)} per unit\nCollect $total at pickup." else "",
            sample = false, reservationId = reservation.id, donorCanClaim = true
        )
    }

    // Keep live listings and recipient/donor reservation destinations separate.
    val allRecords = sampleRecords.filter {
        it.tab != "My Listings" && it.tab != "Reservations"
    } + myListingRecords + myReservationRecords + donorReservationRecords
    val filtered = if (activeOnly) {
        myListingRecords.filter { it.status == "Available" }
    } else {
        allRecords.filter { it.tab == tab }
    }

    val displayed = if (expanded || activeOnly) filtered else filtered.take(5)
    val selected = allRecords.firstOrNull { it.id == selectedId }

    // Totals from this account's own claimed reservations, as recipient and as donor.
    val claimedReservations = (myReservations + donorReservations)
        .map { it.reservation }
        .filter { it.status == ReservationStatus.CLAIMED }
    val completedCount = claimedReservations.size
    val itemsRescued = claimedReservations.sumOf { it.quantity }

    // A "Reservation claimed" alert opens that reservation in Purchases or
    // Claims, once the reloaded list shows it as claimed.
    LaunchedEffect(focusReservationId, myReservations) {
        val id = focusReservationId ?: return@LaunchedEffect
        val entry = myReservations.firstOrNull {
            it.reservation.id == id && it.reservation.status == ReservationStatus.CLAIMED
        } ?: return@LaunchedEffect
        tab = if (entry.reservation.unitPrice > 0) "Purchases" else "Claims"
        activeOnly = false
        selectedId = "my-reservation-$id"
        onFocusHandled()
    }

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
                            if (unreadAlerts > 0) {
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
                            "Completed", completedCount.toString(), "transactions",
                            Modifier.weight(1f)
                        ) {
                            notice = "$completedCount completed: your reservations that donors " +
                                    "marked claimed, plus claimed reservations on your listings."
                        }

                        MetricDivider()

                        ActivityMetric(
                            "Items Rescued", itemsRescued.toString(), "items",
                            Modifier.weight(1f)
                        ) {
                            notice = "$itemsRescued items rescued: the total quantity of those " +
                                    "completed reservations, counting each unit (pack, piece, kg) as one."
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
                                "Active Listings", myListingRecords.count { it.status == "Available" }.toString(), "listings",
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
                    "My Reservations" to Icons.Outlined.BookmarkBorder,
                    "My Donations" to Icons.Outlined.VolunteerActivism,
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
                                // Reservations can change on another device while this
                                // screen is open, so reload them when their tab is tapped.
                                if (label != "My Listings") {
                                    reservationViewModel.refresh()
                                }
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
                    if (record.details.isNotBlank()) Text(record.details, fontSize = 12.sp)
                    Text(
                        when {
                            record.sample ->
                                "Demonstration record. Dates, distances, and statuses are sample values."
                            record.ownedPost != null -> "This is your live Supabase listing."
                            record.donorCanClaim -> "This is a live reservation on your listing."
                            record.tab == "Purchases" -> "This is your live purchase, paid at pickup."
                            record.tab == "Claims" -> "This is free food you picked up."
                            else -> "This is your live reservation."
                        },
                        fontSize = 11.sp,
                        color = AGray
                    )
                    record.reservationId?.let { reservationId ->
                        OutlinedButton(onClick = {
                            messageViewModel.openForReservation(reservationId)
                            selectedId = null
                        }) {
                            Text(if (record.donorCanClaim) "Message recipient" else "Message donor")
                        }
                    }
                }
            },
            confirmButton = {
                when {
                    record.ownedPost != null -> TextButton(onClick = { pendingDelete = record.ownedPost }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    record.reservationId != null && record.status == "Reserved" && record.donorCanClaim ->
                        TextButton(onClick = { reservationViewModel.claim(record.reservationId); selectedId = null }) { Text("Mark claimed") }
                    record.reservationId != null && record.status == "Reserved" ->
                        TextButton(onClick = { reservationViewModel.cancel(record.reservationId); selectedId = null }) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedId = null }) { Text("Close") }
            }
        )
    }

    pendingDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete listing?") },
            text = { Text("This permanently removes this listing and its uploaded image.") },
            confirmButton = {
                TextButton(onClick = {
                    foodViewModel.deletePost(post)
                    pendingDelete = null
                    selectedId = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
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
    if (reservationNotice.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { reservationNotice = "" },
            title = { Text("Reservation") },
            text = { Text(reservationNotice) },
            confirmButton = {
                TextButton(onClick = { reservationNotice = "" }) {
                    Text("OK")
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

                // Donor records keep the recipient's display name visible without
                // loading or exposing any additional profile fields.
                if (record.donorCanClaim && record.details.startsWith("Recipient:")) {
                    Text(
                        record.details.substringBefore('\n'),
                        fontSize = 10.sp,
                        color = AGreen,
                        maxLines = 1
                    )
                }

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
            var failed by remember(uri) { mutableStateOf(false) }
            if (!failed) {
                AsyncImage(
                    model = uri,
                    contentDescription = record.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { failed = true }
                )
            }
            if (failed) {
                Box(
                    modifier = Modifier.fillMaxSize().background(APale),
                    contentAlignment = Alignment.Center
                ) { Text("Image unavailable", fontSize = 9.sp, color = AGray) }
            }
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

private fun formatActivityTimestamp(value: String): String =
    runCatching { java.time.Instant.parse(value) }
        .map { java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.getDefault()).withZone(java.time.ZoneId.systemDefault()).format(it) }
        .getOrElse { value }
