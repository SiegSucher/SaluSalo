package com.example.salusalo

import com.example.salusalo.model.isExpired
import com.example.salusalo.model.hasPhoto
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.GeoPin
import com.example.salusalo.model.pickupPin
import com.example.salusalo.model.ReservationStatus
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.reservationTotal
import com.example.salusalo.viewmodel.DeleteResult
import com.example.salusalo.viewmodel.FoodViewModel
import com.example.salusalo.viewmodel.text
import com.example.salusalo.viewmodel.ReservationViewModel
import com.example.salusalo.viewmodel.MessageViewModel
import com.example.salusalo.viewmodel.ReviewResult
import com.example.salusalo.model.MyReview
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

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
    val uri: String? = null,
    // The listing has a stored photo; with no uri it could not be signed ("Image unavailable").
    val hasPhoto: Boolean = false,
    val details: String = "",
    val ownedPost: FoodPost? = null,
    val reservationId: String? = null,
    val donorCanClaim: Boolean = false,
    // A claimed reservation this account received: its donor can be rated.
    val canRate: Boolean = false,
    // The listing's donor, for "Rate <name>"; null when unknown.
    val donorName: String? = null,
    val priced: Boolean = false,
    // The listing's pickup pin, for "Directions" on a reservation this account made.
    val pickupPin: GeoPin? = null
)

/** The review page that is open: which reservation, with what it showed when opened. */
private data class RatingTarget(
    val reservationId: String,
    val title: String,
    val listingName: String,
    val summaryLine: String,
    val existing: MyReview?
)

/** "Rate <name>", or "Rate the donor/seller" when the name is unknown. */
private fun rateTitle(record: ActivityRecord): String =
    "Rate " + (record.donorName ?: if (record.priced) "the seller" else "the donor")

/** One Activity tab: its name (also the key alerts use), icon and what it holds. */
private class ActivityTab(val label: String, val icon: ImageVector, val explanation: String)

private val ActivityTabs = listOf(
    ActivityTab("My Listings", Icons.AutoMirrored.Outlined.FormatListBulleted, "Food you've posted, newest first."),
    ActivityTab("My Reservations", Icons.Outlined.BookmarkBorder, "Food you've reserved and not picked up yet, and cancelled reservations."),
    ActivityTab("My Donations", Icons.Outlined.VolunteerActivism, "Reservations other people made on your listings."),
    ActivityTab("Purchases", Icons.Outlined.ShoppingBag, "Paid food you've picked up."),
    ActivityTab("Claims", Icons.Outlined.Redeem, "Free food you've picked up.")
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
    // A reservation to open, from a "Reservation claimed" or "Listing removed" alert.
    focusReservationId: String? = null,
    onFocusHandled: () -> Unit = {}
) {
    val myPosts by foodViewModel.myPosts.collectAsStateWithLifecycle()
    val myReservations by reservationViewModel.myReservations.collectAsStateWithLifecycle()
    val donorReservations by reservationViewModel.donorReservations.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<FoodPost?>(null) }
    var editing by remember { mutableStateOf<FoodPost?>(null) }
    var editNotice by remember { mutableStateOf("") }
    val foodSaving by foodViewModel.saving.collectAsStateWithLifecycle()
    val foodLoading by foodViewModel.loading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        foodViewModel.loadMyPosts()
        reservationViewModel.refresh()
    }

    // My Listings shows skeleton rows, not "no listings", until its first load here has finished.
    var listingsStarted by remember { mutableStateOf(false) }
    var listingsSettled by remember { mutableStateOf(false) }
    LaunchedEffect(foodLoading) {
        if (foodLoading) listingsStarted = true else if (listingsStarted) listingsSettled = true
    }

    // Show reservation results (cancel, claim, or a failed load) on this screen,
    // then clear them so they do not reappear later in the food details dialog.
    val reservationMessage by reservationViewModel.message.collectAsStateWithLifecycle()
    val reservationSaving by reservationViewModel.saving.collectAsStateWithLifecycle()
    // This account's own reviews, and the page to rate a claimed pickup's donor.
    val myReviews by reservationViewModel.myReviews.collectAsStateWithLifecycle()
    val reviewResult by reservationViewModel.reviewResult.collectAsStateWithLifecycle()
    var rating by remember { mutableStateOf<RatingTarget?>(null) }
    // A new or changed review changes the donor's rating on Home, so reload the feed.
    LaunchedEffect(reviewResult) {
        if (reviewResult is ReviewResult.Saved) foodViewModel.loadAvailablePosts()
    }
    val reservationsLoadFailed by reservationViewModel.loadFailed.collectAsStateWithLifecycle()
    val reservationsLoaded by reservationViewModel.loaded.collectAsStateWithLifecycle()
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
    val context = LocalContext.current
    // "No maps app…" under Directions, for the record that is open.
    var directionsError by remember(selectedId) { mutableStateOf("") }
    // Every record comes from Supabase: this account's listings (removed ones
    // are left out by the repository) and its reservations as recipient and donor.
    val myListingRecords = myPosts
        .sortedByDescending { it.createdAt.orEmpty() }
        .map { post ->
            // Nothing on the server marks a listing EXPIRED; it is worked out
            // from expiration_date, so Active Listings leaves it out.
            val expired = post.isExpired()
            ActivityRecord(
                id = post.id,
                tab = "My Listings",
                title = post.name,
                quantity = "${post.quantity} ${post.unit}",
                distance = post.pickupLocation,
                posted = post.createdAt?.let { "Posted ${formatActivityTimestamp(it)}" }
                    ?: "Posted recently",
                status = if (expired) "Expired" else post.status.name.lowercase()
                    .replaceFirstChar { it.uppercase() },
                timeLabel = if (expired) "Expired on" else "Expires",
                timeValue = formatActivityTimestamp(post.expirationDate),
                uri = post.displayImageUrl,
                hasPhoto = post.hasPhoto,
                details = buildString {
                    append("Category: ${post.category}")
                    post.description?.takeIf { it.isNotBlank() }?.let {
                        append("\nDescription: $it")
                    }
                },
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
            hasPhoto = post.hasPhoto,
            pickupPin = post.pickupPin,
            details = when {
                claimed && priced ->
                    "Price: ${formatPeso(reservation.unitPrice)} per unit\nTotal: $total, paid at pickup."
                claimed -> "Free food you picked up."
                priced ->
                    "Your reservation.\nPrice: ${formatPeso(reservation.unitPrice)} per unit\nPay $total at pickup."
                else -> "Your reservation."
            } + when {
                reservation.cancelledByListingRemoval ->
                    "\nListing: Removed by the donor\nCancelled because the donor removed this listing."
                post.status == FoodPostStatus.REMOVED ->
                    "\nListing: Removed by the donor\nThe donor has since removed this listing."
                else -> ""
            },
            reservationId = reservation.id, donorCanClaim = false,
            canRate = claimed,
            donorName = post.donorName?.takeIf { it.isNotBlank() },
            priced = priced
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
            hasPhoto = post.hasPhoto,
            details = "Recipient: ${entry.recipientName ?: "Unknown"}\nReservation for your listing." +
                (if (priced) "\nPrice: ${formatPeso(reservation.unitPrice)} per unit\nCollect $total at pickup." else "") +
                if (post.status == FoodPostStatus.REMOVED) "\nListing: Removed by you\nYou removed this listing." else "",
            reservationId = reservation.id, donorCanClaim = true
        )
    }

    // Keep live listings and recipient/donor reservation destinations separate.
    val allRecords = myListingRecords + myReservationRecords + donorReservationRecords
    val filtered = if (activeOnly) {
        myListingRecords.filter { it.status == "Available" }
    } else {
        allRecords.filter { it.tab == tab }
    }

    val displayed = if (expanded || activeOnly) filtered else filtered.take(5)
    val selected = allRecords.firstOrNull { it.id == selectedId }
    // The records one tab shows ("Active" for Active Listings), so a tab that is
    // fading out keeps its own list rather than the one fading in.
    fun recordsFor(key: String): List<ActivityRecord> =
        if (key == "Active") {
            myListingRecords.filter { it.status == "Available" }
        } else {
            allRecords.filter { it.tab == key }.let { if (expanded) it else it.take(5) }
        }

    // Totals from this account's own claimed reservations, as recipient and as donor.
    val claimedReservations = (myReservations + donorReservations)
        .map { it.reservation }
        .filter { it.status == ReservationStatus.CLAIMED }
    val completedCount = claimedReservations.size
    val itemsRescued = claimedReservations.sumOf { it.quantity }
    val activeListingCount = myListingRecords.count { it.status == "Available" }

    // Skeleton rows until the list shown has loaded once; a failed load says so instead.
    val showSkeleton = if (tab == "My Listings" || activeOnly) {
        myPosts.isEmpty() && !listingsSettled
    } else {
        !reservationsLoaded && !reservationsLoadFailed
    }

    // A "Reservation claimed" alert opens that reservation in Purchases or
    // Claims, once the reloaded list shows it as claimed. A "Listing removed"
    // alert opens the cancelled reservation in My Reservations.
    LaunchedEffect(focusReservationId, myReservations) {
        val id = focusReservationId ?: return@LaunchedEffect
        val entry = myReservations.firstOrNull {
            it.reservation.id == id && (
                it.reservation.status == ReservationStatus.CLAIMED ||
                    it.reservation.cancelledByListingRemoval
                )
        } ?: return@LaunchedEffect
        tab = when {
            entry.reservation.status != ReservationStatus.CLAIMED -> "My Reservations"
            entry.reservation.unitPrice > 0 -> "Purchases"
            else -> "Claims"
        }
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
        color = SaluColors.Cream
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "My Activity",
                            style = SaluText.ScreenTitle,
                            color = SaluColors.DarkGreen,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            "Track your listings, reservations, purchases & claims",
                            style = SaluText.Supporting,
                            color = SaluColors.Gray
                        )
                    }

                    IconButton(
                        onClick = onAlerts,
                        modifier = Modifier.semantics {
                            contentDescription = if (unreadAlerts > 0) "Open alerts, $unreadAlerts unread" else "Open alerts"
                        }
                    ) {
                        Box {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = SaluColors.DarkGreen,
                                modifier = Modifier.size(26.dp)
                            )
                            if (unreadAlerts > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(SaluColors.Orange)
                                )
                            }
                        }
                    }
                }
            }

            item(key = "summary") {
                SaluCard(
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 6.dp)
                    ) {
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

                        ActivityMetric(
                            "Active Listings", activeListingCount.toString(), "listings",
                            Modifier.weight(1f),
                            showChevron = true
                        ) {
                            tab = "My Listings"
                            activeOnly = true
                            expanded = true
                        }
                    }
                }
            }

            item(key = "tabs") {
                LazyRow(
                    modifier = Modifier.padding(top = 20.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ActivityTabs, key = { it.label }) { activityTab ->
                        val count = when (activityTab.label) {
                            "My Listings" -> myListingRecords.size
                            else -> (myReservationRecords + donorReservationRecords).count { it.tab == activityTab.label }
                        }
                        ActivityTabChip(
                            tab = activityTab,
                            count = count,
                            selected = !activeOnly && tab == activityTab.label,
                            onClick = {
                                tab = activityTab.label
                                activeOnly = false
                                expanded = false
                                // Reservations can change on another device while this
                                // screen is open, so reload them when their tab is tapped.
                                if (activityTab.label != "My Listings") {
                                    reservationViewModel.refresh()
                                }
                            }
                        )
                    }
                }
            }

            item(key = "tab-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (activeOnly) "Active Listings" else tab,
                            style = SaluText.SectionTitle,
                            color = SaluColors.DarkGreen,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            if (activeOnly) {
                                "Your listings that can still be reserved."
                            } else {
                                ActivityTabs.firstOrNull { it.label == tab }?.explanation.orEmpty()
                            },
                            style = SaluText.Supporting,
                            color = SaluColors.Gray
                        )
                    }

                    if (activeOnly || filtered.size > 5) TextButton(
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
                                else -> "See all (${filtered.size})"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = SaluColors.Green
                        )
                    }
                }
            }

            if (reservationsLoadFailed && tab != "My Listings" && !activeOnly) {
                item(key = "load-failed") {
                    InlineNotice(
                        text = ReservationViewModel.LOAD_FAILED,
                        icon = Icons.Outlined.CloudOff,
                        tone = NoticeTone.Error,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
                    )
                }
            }

            item(key = "records") {
                // A short cross-fade when the tab changes; the list itself is not reloaded by it.
                AnimatedContent(
                    targetState = Triple(if (activeOnly) "Active" else tab, showSkeleton, displayed.isEmpty()),
                    transitionSpec = { fadeIn(tween(SaluMotion.MEDIUM)) togetherWith fadeOut(tween(SaluMotion.SHORT)) },
                    label = "activityTab",
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) { (shownTab, skeleton, empty) ->
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when {
                            skeleton -> SkeletonGroup(description = "Loading your activity") {
                                repeat(3) {
                                    SaluCard(Modifier.fillMaxWidth()) {
                                        ListRowSkeleton(Modifier.padding(horizontal = 12.dp), thumbnail = 72.dp)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                }
                            }

                            empty -> ActivityEmptyState(shownTab, onPost)

                            else -> recordsFor(shownTab).forEach { record ->
                                key(record.id) {
                                    ActivityRecordCard(record) {
                                        selectedId = record.id
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "thanks") {
                SaluCard(
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp)
                        .fillMaxWidth(),
                    color = SaluColors.PaleGreen,
                    border = false,
                    onClick = {
                        notice = "Share surplus food by creating a listing."
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBubble(
                            Icons.Outlined.Spa,
                            tint = SaluColors.Green,
                            background = Color.White,
                            size = 40.dp,
                            iconSize = 22.dp
                        )
                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                "Thanks for making a difference!",
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                                color = SaluColors.DarkGreen
                            )
                            Text(
                                "Every item you share helps reduce food waste " +
                                        "and support your community.",
                                style = SaluText.Supporting,
                                color = SaluColors.Green
                            )
                        }

                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = "Learn more",
                            tint = SaluColors.Green,
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RecordPhoto(
                        record,
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RecordStatus(record.status)
                        Spacer(Modifier.width(8.dp))
                        RecordRole(record)
                    }
                    RecordLine("Amount", record.quantity)
                    RecordLine("Pickup location", record.distance)
                    RecordLine(record.timeLabel, record.timeValue)
                    Text(record.posted, style = SaluText.Meta, color = SaluColors.Gray)
                    if (record.details.isNotBlank()) {
                        Text(record.details, style = SaluText.Supporting, color = SaluColors.DarkGreen)
                    }
                    record.ownedPost?.takeIf { !it.isExpired() }?.let { post ->
                        SaluSecondaryButton(
                            text = "Edit listing",
                            icon = Icons.Outlined.Edit,
                            onClick = {
                                editing = post
                                selectedId = null
                            }
                        )
                    }
                    record.reservationId?.let { reservationId ->
                        SaluSecondaryButton(
                            text = if (record.donorCanClaim) "Message recipient" else "Message donor",
                            icon = Icons.Outlined.ChatBubbleOutline,
                            onClick = {
                                messageViewModel.openForReservation(reservationId)
                                selectedId = null
                            }
                        )
                    }
                    // Directions to the pickup pin while this account's reservation is open;
                    // the same link and fallbacks as the Pickup location page.
                    record.pickupPin?.takeIf {
                        record.reservationId != null && !record.donorCanClaim && record.status == "Reserved"
                    }?.let { pin ->
                        SaluSecondaryButton(
                            text = "Directions",
                            icon = Icons.Outlined.Directions,
                            onClick = {
                                directionsError = if (openDirections(context, pin)) "" else "No maps app is available on this device."
                            }
                        )
                        if (directionsError.isNotBlank()) {
                            Text(directionsError, color = MaterialTheme.colorScheme.error, style = SaluText.Supporting)
                        }
                    }
                    // Only a claimed pickup this account received can be rated.
                    record.reservationId?.takeIf { record.canRate }?.let { reservationId ->
                        val existing = myReviews[reservationId]
                        SaluSecondaryButton(
                            text = if (existing != null) "Change your review" else rateTitle(record),
                            icon = Icons.Outlined.StarOutline,
                            onClick = {
                                reservationViewModel.clearReviewResult()
                                rating = RatingTarget(
                                    reservationId = reservationId,
                                    title = if (existing != null) "Change your review" else rateTitle(record),
                                    listingName = record.title,
                                    summaryLine = record.quantity,
                                    existing = existing
                                )
                                selectedId = null
                            }
                        )
                    }
                }
            },
            confirmButton = {
                when {
                    record.ownedPost != null -> TextButton(onClick = { pendingDelete = record.ownedPost }) { Text("Delete", color = SaluColors.Error) }
                    record.reservationId != null && record.status == "Reserved" && record.donorCanClaim ->
                        TextButton(enabled = !reservationSaving, onClick = {
                            if (reservationViewModel.claim(record.reservationId)) selectedId = null
                        }) { Text("Mark claimed") }
                    record.reservationId != null && record.status == "Reserved" ->
                        TextButton(enabled = !reservationSaving, onClick = {
                            if (reservationViewModel.cancel(record.reservationId)) selectedId = null
                        }) { Text("Cancel", color = SaluColors.Error) }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedId = null }) { Text("Close") }
            }
        )
    }

    rating?.let { target ->
        RateDonorPage(
            reservationId = target.reservationId,
            title = target.title,
            listingName = target.listingName,
            summaryLine = target.summaryLine,
            existing = target.existing,
            saving = reservationSaving,
            result = reviewResult,
            onSend = { stars, comment ->
                reservationViewModel.submitReview(target.reservationId, stars, comment)
            },
            onClose = {
                rating = null
                reservationViewModel.clearReviewResult()
            }
        )
    }

    pendingDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete listing?") },
            text = { Text(DELETE_LISTING_TEXT) },
            confirmButton = {
                TextButton(enabled = !foodSaving, onClick = {
                    foodViewModel.deletePost(post)
                    pendingDelete = null
                    selectedId = null
                }) { Text("Delete", color = SaluColors.Error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
    DeleteResultDialog(
        foodViewModel = foodViewModel,
        // A removal cancels open reservations, so reload the reservation lists too.
        onRemoved = { reservationViewModel.refresh() }
    )
    editing?.let { post ->
        EditListingDialog(
            post = post,
            foodViewModel = foodViewModel,
            onClose = { result ->
                editing = null
                if (result != null) editNotice = result
            }
        )
    }
    if (editNotice.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { editNotice = "" },
            title = { Text("Edit Listing") },
            text = { Text(editNotice) },
            confirmButton = { TextButton(onClick = { editNotice = "" }) { Text("OK") } }
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

/** A tab as a pill with its icon, name and how many records it holds. */
@Composable
private fun ActivityTabChip(
    tab: ActivityTab,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(SaluRadius.Pill)
            .background(if (selected) SaluColors.Green else SaluColors.Cream)
            .border(1.dp, if (selected) Color.Transparent else SaluColors.Green.copy(alpha = 0.3f), SaluRadius.Pill)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .semantics { contentDescription = "${tab.label}, $count" }
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = if (selected) Color.White else SaluColors.Green,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            tab.label,
            style = SaluText.Meta.copy(fontSize = 13.sp),
            color = if (selected) Color.White else SaluColors.Green
        )
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            style = SaluText.Status,
            color = if (selected) SaluColors.Green else SaluColors.DarkGreen,
            modifier = Modifier
                .clip(SaluRadius.Pill)
                .background(if (selected) Color.White else SaluColors.IconGreen)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/** What each tab says when it has nothing yet. */
@Composable
private fun ActivityEmptyState(tab: String, onPost: () -> Unit) {
    when (tab) {
        "Active" -> EmptyState(
            icon = Icons.Outlined.Inventory2,
            title = "No active listings",
            message = "Listings that can still be reserved appear here.",
            actionLabel = "Post food",
            onAction = onPost
        )
        "My Reservations" -> EmptyState(
            icon = Icons.Outlined.BookmarkBorder,
            title = "No reservations",
            message = "Reserve food from Home and it appears here until you pick it up."
        )
        "My Donations" -> EmptyState(
            icon = Icons.Outlined.VolunteerActivism,
            title = "No reservations on your listings yet",
            message = "When someone reserves your food, you'll see it here.",
            tint = SaluColors.Green,
            iconBackground = SaluColors.PaleGreen
        )
        "Purchases" -> EmptyState(
            icon = Icons.Outlined.ShoppingBag,
            title = "No purchases yet",
            message = "Paid food appears here after the seller marks your pickup as claimed."
        )
        "Claims" -> EmptyState(
            icon = Icons.Outlined.Redeem,
            title = "No claims yet",
            message = "Free food appears here after the donor marks your pickup as claimed.",
            tint = SaluColors.Green,
            iconBackground = SaluColors.PaleGreen
        )
        else -> EmptyState(
            icon = Icons.Outlined.Inventory2,
            title = "No listings yet",
            message = "Share surplus food and it appears here, with its reservations.",
            actionLabel = "Post food",
            onAction = onPost
        )
    }
}

@Composable
private fun ActivityRecordCard(
    record: ActivityRecord,
    onClick: () -> Unit
) {
    SaluCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            RecordPhoto(
                record,
                Modifier.size(72.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        record.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        style = SaluText.FoodTitle,
                        color = SaluColors.DarkGreen,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    RecordStatus(record.status)
                }

                RecordRole(record)

                Text(
                    record.quantity,
                    style = SaluText.Supporting,
                    color = SaluColors.DarkGreen
                )

                SmallActivityLine(
                    Icons.Outlined.LocationOn,
                    record.distance
                )
                SmallActivityLine(
                    Icons.Outlined.CalendarMonth,
                    // A reservation's pickup value is its location, already shown above.
                    if (record.timeValue != record.distance) "${record.timeLabel} ${record.timeValue}" else record.posted
                )
            }
        }
    }
}

/** The record's status as a badge with its icon; other words as plain text. */
@Composable
private fun RecordStatus(status: String) {
    val known = SaluStatus.fromLabel(status)
    if (known != null) {
        StatusBadge(known)
    } else {
        Text(status, style = SaluText.Status, color = SaluColors.Gray)
    }
}

/** Who did what: "Your listing", "You reserved", "Reserved by Ana", "You bought", "You claimed". */
@Composable
private fun RecordRole(record: ActivityRecord) {
    val (icon, text, color) = when (record.tab) {
        "My Listings" -> Triple(Icons.Outlined.Storefront, "Your listing", SaluColors.Green)
        "My Reservations" -> Triple(Icons.Outlined.BookmarkBorder, "You reserved", SaluColors.Green)
        // Donor records keep the recipient's display name visible without
        // loading or exposing any additional profile fields.
        "My Donations" -> Triple(
            Icons.Outlined.VolunteerActivism,
            "Reserved by " + record.details.substringBefore('\n').removePrefix("Recipient: "),
            SaluColors.Green
        )
        "Purchases" -> Triple(Icons.Outlined.ShoppingBag, "You bought", SaluColors.DarkGreen)
        else -> Triple(Icons.Outlined.Redeem, "You claimed", SaluColors.Green)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            style = SaluText.Meta.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecordLine(label: String, value: String) {
    Column {
        Text(label, style = SaluText.Meta, color = SaluColors.Gray)
        Text(value, style = SaluText.Body, color = SaluColors.DarkGreen)
    }
}

@Composable
private fun SmallActivityLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = SaluColors.Gray,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = SaluText.Meta,
            color = SaluColors.Gray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecordPhoto(
    record: ActivityRecord,
    modifier: Modifier
) {
    ListingPhoto(
        imageUrl = record.uri?.takeIf { it.isNotBlank() },
        hasPhoto = record.hasPhoto,
        contentDescription = record.title,
        modifier = modifier.clip(SaluRadius.Small),
        textSize = 9.sp
    )
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 10.dp)
            .width(1.dp)
            .background(SaluColors.Line)
    )
}

@Composable
private fun ActivityMetric(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier,
    showChevron: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(SaluRadius.Small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            title,
            style = SaluText.Meta,
            color = SaluColors.Gray,
            maxLines = 2
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = SaluColors.DarkGreen
            )
            if (showChevron) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "View active listings",
                    tint = SaluColors.Green,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            caption,
            style = SaluText.Meta,
            color = SaluColors.Gray
        )
    }
}

private fun formatActivityTimestamp(value: String): String =
    runCatching { java.time.Instant.parse(value) }
        .map { java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.ENGLISH).withZone(java.time.ZoneId.systemDefault()).format(it) }
        .getOrElse { value }

const val DELETE_LISTING_TEXT =
    "If nobody has ever reserved this listing, it is deleted with its photos. If it has reservations, it is removed from Home and your listings instead: open reservations are cancelled and their recipients are told, and past reservations, photos and chats are kept."

/** Shows the result of Delete listing, then clears it. */
@Composable
fun DeleteResultDialog(
    foodViewModel: FoodViewModel,
    onRemoved: () -> Unit = {}
) {
    val result by foodViewModel.deleteResult.collectAsStateWithLifecycle()
    LaunchedEffect(result) {
        if (result is DeleteResult.Removed) onRemoved()
    }
    result?.let {
        AlertDialog(
            onDismissRequest = { foodViewModel.clearDeleteResult() },
            title = { Text("Delete listing") },
            text = { Text(it.text()) },
            confirmButton = {
                TextButton(onClick = { foodViewModel.clearDeleteResult() }) { Text("OK") }
            }
        )
    }
}
