package com.example.salusalo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.ListingHandover
import com.example.salusalo.model.MyReview
import com.example.salusalo.model.PIN_MAP_ZOOM
import com.example.salusalo.model.ReservationDisplay
import com.example.salusalo.model.ReservationStatus
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.hasPhoto
import com.example.salusalo.model.isExpired
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.model.pickupPin
import com.example.salusalo.model.reservationTotal
import com.example.salusalo.viewmodel.DeleteResult
import com.example.salusalo.viewmodel.FoodViewModel
import com.example.salusalo.viewmodel.MessageViewModel
import com.example.salusalo.viewmodel.ReservationAction
import com.example.salusalo.viewmodel.ReservationViewModel
import com.example.salusalo.viewmodel.ReviewResult
import com.example.salusalo.viewmodel.text
import java.time.Instant

/*
 * ACTIVITY: three tabs, each with its own filters.
 * - My Listings: this account's listings (Active or Closed), with their
 *   reservations counted and managed per listing.
 * - My Reservations: food this account reserved and hasn't picked up yet.
 * - History: finished reservations from both sides, picked up or cancelled.
 * Every record comes from Supabase; nothing here is invented.
 */

private const val TAB_LISTINGS = "My Listings"
private const val TAB_RESERVATIONS = "My Reservations"
private const val TAB_HISTORY = "History"
private val ActivityTabs = listOf(TAB_LISTINGS, TAB_RESERVATIONS, TAB_HISTORY)

private val ListingFilters = listOf("Active", "Closed")
private val ReservationFilters = listOf("All", "Free food", "Purchases")
private val HistoryFilters = listOf("All", "Completed", "Cancelled")
private val ManageFilters = listOf("Active", "Completed", "Cancelled")

/**
 * The tab for a key from an alert or a saved state. Before this layout, the
 * reservations on your listings, purchases and claims had tabs of their own.
 */
private fun activityTabFor(key: String): String = when (key) {
    TAB_RESERVATIONS -> TAB_RESERVATIONS
    TAB_HISTORY, "Purchases", "Claims" -> TAB_HISTORY
    else -> TAB_LISTINGS
}

/** The Manage reservations filter a reservation is listed under. */
private fun manageFilterFor(status: ReservationStatus): String = when (status) {
    ReservationStatus.RESERVED -> "Active"
    ReservationStatus.CLAIMED -> "Completed"
    ReservationStatus.CANCELLED -> "Cancelled"
}

/** A reservation in Activity: one this account made ([mine]) or one on this account's listing. */
private class ActivityReservation(val entry: ReservationDisplay, val mine: Boolean) {
    val reservation get() = entry.reservation
    val post get() = entry.foodPost
    val id get() = entry.reservation.id
    val key get() = (if (mine) "mine-" else "donor-") + entry.reservation.id
    val status get() = entry.reservation.status

    /** Priced when the reservation's unit_price, locked in by the database, is above 0. */
    val priced get() = entry.reservation.unitPrice > 0
    val total: String get() = formatPeso(reservationTotal(reservation.unitPrice, reservation.quantity))
    val amount: String get() = "${reservation.quantity} ${post.unit}"

    /** The other person: the donor for [mine], else the recipient (names only); null when unknown. */
    val otherName: String?
        get() = (if (mine) post.donorName else entry.recipientName)?.takeIf { it.isNotBlank() }

    /** When it was picked up or cancelled (its last change), else when it was made. */
    val lastChange: Instant? get() = parseTimestamp(reservation.updatedAt) ?: parseTimestamp(reservation.createdAt)
}

/** The review page that is open: which reservation, with what it showed when opened. */
private data class RatingTarget(
    val reservationId: String,
    val title: String,
    val listingName: String,
    val summaryLine: String,
    val existing: MyReview?
)

/** "Rate <name>", or "Rate the donor/seller" when the name is unknown. */
private fun rateTitle(item: ActivityReservation): String =
    "Rate " + (item.otherName ?: if (item.priced) "the seller" else "the donor")

@Composable
fun ActivityScreen(
    onPost: () -> Unit,
    onAlerts: () -> Unit,
    foodViewModel: FoodViewModel,
    reservationViewModel: ReservationViewModel,
    messageViewModel: MessageViewModel,
    unreadAlerts: Int = 0,
    initialTab: String = "My Listings",
    // A reservation to open, from an alert: one this account made (claimed, or
    // cancelled because its listing was removed) or one on this account's listing.
    focusReservationId: String? = null,
    onFocusHandled: () -> Unit = {}
) {
    val myPosts by foodViewModel.myPosts.collectAsStateWithLifecycle()
    val myReservations by reservationViewModel.myReservations.collectAsStateWithLifecycle()
    val donorReservations by reservationViewModel.donorReservations.collectAsStateWithLifecycle()
    val foodSaving by foodViewModel.saving.collectAsStateWithLifecycle()
    val foodLoading by foodViewModel.loading.collectAsStateWithLifecycle()
    val myPostsLoaded by foodViewModel.myPostsLoaded.collectAsStateWithLifecycle()
    val myPostsError by foodViewModel.myPostsError.collectAsStateWithLifecycle()
    val reservationSaving by reservationViewModel.saving.collectAsStateWithLifecycle()
    val reservationsBusy by reservationViewModel.loading.collectAsStateWithLifecycle()
    val reservationsLoadFailed by reservationViewModel.loadFailed.collectAsStateWithLifecycle()
    val reservationsLoaded by reservationViewModel.loaded.collectAsStateWithLifecycle()
    // This account's own reviews, and the page to rate a claimed pickup's donor.
    val myReviews by reservationViewModel.myReviews.collectAsStateWithLifecycle()
    val reviewResult by reservationViewModel.reviewResult.collectAsStateWithLifecycle()
    val reservationAction by reservationViewModel.actionResult.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        foodViewModel.loadMyPosts()
        reservationViewModel.refresh()
    }

    // A new or changed review changes the donor's rating on Home, so reload the feed.
    LaunchedEffect(reviewResult) {
        if (reviewResult is ReviewResult.Saved) foodViewModel.loadAvailablePosts()
    }

    // Show reservation results (cancel, claim, release) on this screen, then
    // clear them so they do not reappear later in the food details dialog.
    var reservationNotice by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(reservationAction) {
        reservationAction?.let { result ->
            reservationNotice = result.text()
            reservationViewModel.clearActionResult()
            // A cancel or release returns quantity to the listing, so refresh the Home feed.
            if (result == ReservationAction.Cancelled || result == ReservationAction.Released) {
                foodViewModel.loadAvailablePosts()
            }
        }
    }

    var tab by rememberSaveable(initialTab) { mutableStateOf(activityTabFor(initialTab)) }
    var listingFilter by rememberSaveable { mutableStateOf(ListingFilters.first()) }
    var reservationFilter by rememberSaveable { mutableStateOf(ReservationFilters.first()) }
    var historyFilter by rememberSaveable { mutableStateOf(HistoryFilters.first()) }
    var manageFilter by rememberSaveable { mutableStateOf(ManageFilters.first()) }
    // Pages over the list, by the listing or reservation they show.
    var managingPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var openReservationKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pickupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClaim by remember { mutableStateOf<ActivityReservation?>(null) }
    var confirmCancel by remember { mutableStateOf<ActivityReservation?>(null) }
    var confirmRelease by remember { mutableStateOf<ActivityReservation?>(null) }
    var pendingDelete by remember { mutableStateOf<FoodPost?>(null) }
    var editing by remember { mutableStateOf<FoodPost?>(null) }
    var editNotice by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf<RatingTarget?>(null) }
    var notice by rememberSaveable { mutableStateOf("") }

    val now = remember(myPosts, myReservations, donorReservations) { Instant.now() }
    // Only reservations this account made, and only reservations on its own listings.
    val made = myReservations.map { ActivityReservation(it, mine = true) }
    val received = donorReservations.map { ActivityReservation(it, mine = false) }
    val everyReservation = made + received
    val onListing = received.groupBy { it.reservation.foodPostId }

    // Active: can still be reserved, or has reservations to hand over. Nothing on
    // the server marks a listing EXPIRED; it is worked out from expiration_date.
    // Until the reservations have loaded, a fully reserved listing counts as
    // active: it may still have food to hand over.
    fun isActive(post: FoodPost): Boolean =
        onListing[post.id].orEmpty().any { it.status == ReservationStatus.RESERVED } ||
            (!reservationsLoaded && post.status == FoodPostStatus.RESERVED) ||
            (post.status == FoodPostStatus.AVAILABLE && !post.isExpired(now))

    // Removed listings are left out by the repository.
    val listings = myPosts.sortedByDescending { parseTimestamp(it.createdAt) ?: Instant.EPOCH }
    val shownListings = listings.filter { isActive(it) == (listingFilter == "Active") }

    // Open reservations only; once picked up or cancelled they move to History.
    val openMade = made.filter { it.status == ReservationStatus.RESERVED }
    val shownReservations = when (reservationFilter) {
        "Free food" -> openMade.filter { !it.priced }
        "Purchases" -> openMade.filter { it.priced }
        else -> openMade
    }

    val history = everyReservation
        .filter { it.status != ReservationStatus.RESERVED }
        .sortedByDescending { it.lastChange ?: Instant.EPOCH }
    val shownHistory = when (historyFilter) {
        "Completed" -> history.filter { it.status == ReservationStatus.CLAIMED }
        "Cancelled" -> history.filter { it.status == ReservationStatus.CANCELLED }
        else -> history
    }

    // Totals from this account's own claimed reservations, as recipient and as donor.
    val claimed = everyReservation.filter { it.status == ReservationStatus.CLAIMED }
    val completedCount = claimed.size
    val itemsRescued = claimed.sumOf { it.reservation.quantity }

    // Skeletons until the list shown has loaded once; a failed load says so instead.
    val showSkeleton = if (tab == TAB_LISTINGS) {
        !myPostsLoaded && myPostsError == null
    } else {
        !reservationsLoaded && !reservationsLoadFailed
    }

    // An alert opens its reservation once this screen's reload has finished, so
    // the page shows the current status: one this account made opens its details
    // (in History once picked up or cancelled); one on this account's listing
    // opens that listing's Manage reservations.
    LaunchedEffect(focusReservationId, myReservations, donorReservations, myPosts, reservationsBusy, foodLoading) {
        val id = focusReservationId ?: return@LaunchedEffect
        if (reservationViewModel.loading.value) return@LaunchedEffect
        val mine = made.firstOrNull { it.id == id }
        val theirs = received.firstOrNull { it.id == id }
        when {
            mine != null -> {
                tab = if (mine.status == ReservationStatus.RESERVED) TAB_RESERVATIONS else TAB_HISTORY
                reservationFilter = ReservationFilters.first()
                historyFilter = HistoryFilters.first()
                openReservationKey = mine.key
            }
            theirs != null -> {
                if (foodViewModel.loading.value) return@LaunchedEffect
                val post = myPosts.firstOrNull { it.id == theirs.reservation.foodPostId }
                if (post != null) {
                    tab = TAB_LISTINGS
                    listingFilter = if (isActive(post)) "Active" else "Closed"
                    manageFilter = manageFilterFor(theirs.status)
                    managingPostId = post.id
                } else {
                    // A removed listing: its reservations are only in History.
                    tab = TAB_HISTORY
                    historyFilter = HistoryFilters.first()
                    openReservationKey = theirs.key
                }
            }
            else -> return@LaunchedEffect
        }
        onFocusHandled()
    }

    // A deleted or removed listing closes its pages.
    LaunchedEffect(myPosts) {
        if (managingPostId != null && myPosts.none { it.id == managingPostId }) managingPostId = null
        if (viewingPostId != null && myPosts.none { it.id == viewingPostId }) viewingPostId = null
    }

    fun selectTab(key: String) {
        tab = key
        // Reservations can change on another device while this screen is open,
        // so reload them when a tab is tapped (at most every 10 seconds, so
        // switching tabs doesn't repeat the requests); they also give My
        // Listings its counts.
        reservationViewModel.refreshIfStale()
    }

    fun openManage(post: FoodPost) {
        manageFilter = ManageFilters.first()
        managingPostId = post.id
    }

    fun startRating(item: ActivityReservation) {
        val existing = myReviews[item.id]
        val title = if (existing != null) "Change your review" else rateTitle(item)
        reservationViewModel.clearReviewResult()
        rating = RatingTarget(
            reservationId = item.id,
            title = title,
            listingName = item.post.name,
            summaryLine = "${item.amount} • ${if (item.priced) item.total else "Free"}",
            existing = existing
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SaluColors.Cream
    ) {
        LazyColumn(
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item(key = "header") {
                ActivityHeader(unreadAlerts = unreadAlerts, onAlerts = onAlerts)
            }

            item(key = "tabs") {
                ActivityTabRow(
                    selected = tab,
                    onSelect = { selectTab(it) },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
                )
            }

            item(key = "section-$tab") {
                when (tab) {
                    TAB_LISTINGS -> ActivitySection("Food you posted", role = "Donor / seller", spokenRole = "As donor or seller")
                    TAB_RESERVATIONS -> ActivitySection("Food you reserved", role = "Recipient / buyer", spokenRole = "As recipient or buyer")
                    else -> ActivitySection("Your past transactions")
                }
            }

            // Totals only from loaded reservations, never zeros from a failed load.
            if (tab == TAB_HISTORY && reservationsLoaded) {
                item(key = "totals") {
                    HistoryTotals(
                        completed = completedCount,
                        items = itemsRescued,
                        onExplain = { notice = it },
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    )
                }
            }

            item(key = "filters-$tab") {
                val modifier = Modifier.padding(top = 12.dp)
                when (tab) {
                    TAB_LISTINGS -> ActivityFilterRow(ListingFilters, listingFilter, { listingFilter = it }, modifier)
                    TAB_RESERVATIONS -> ActivityFilterRow(ReservationFilters, reservationFilter, { reservationFilter = it }, modifier)
                    else -> ActivityFilterRow(HistoryFilters, historyFilter, { historyFilter = it }, modifier)
                }
            }

            // Over lists loaded before, or My Listings (its reservation counts are
            // missing); a reservation tab that never loaded says so below instead.
            if (reservationsLoadFailed && (reservationsLoaded || tab == TAB_LISTINGS)) {
                item(key = "load-failed") {
                    InlineNotice(
                        text = ReservationViewModel.LOAD_FAILED,
                        icon = Icons.Outlined.CloudOff,
                        tone = NoticeTone.Error,
                        actionLabel = "Try again",
                        onAction = { reservationViewModel.refresh() },
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    )
                }
            }

            val cardModifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)
            when {
                showSkeleton -> item(key = "skeleton-$tab") {
                    ActivitySkeleton(cardModifier)
                }

                // Reservations or History after a failed first load: not "none".
                tab != TAB_LISTINGS && !reservationsLoaded -> item(key = "reservations-failed-$tab") {
                    EmptyState(
                        icon = Icons.Outlined.CloudOff,
                        title = "Could not load your reservations",
                        message = "Check your internet connection, then try again.",
                        actionLabel = "Try again",
                        onAction = { reservationViewModel.refresh() },
                        tint = SaluColors.Error,
                        iconBackground = SaluColors.ErrorSoft
                    )
                }

                tab == TAB_LISTINGS -> {
                    // A failed reload keeps the listings already shown.
                    if (myPostsError != null && myPostsLoaded) {
                        item(key = "listings-error") {
                            InlineNotice(
                                text = "Couldn't refresh your listings. These may be out of date.",
                                icon = Icons.Outlined.CloudOff,
                                tone = NoticeTone.Error,
                                actionLabel = "Try again",
                                onAction = { foodViewModel.loadMyPosts() },
                                modifier = cardModifier
                            )
                        }
                    }
                    if (!myPostsLoaded) {
                        // Only reached after a failed first load (the skeleton shows before).
                        item(key = "listings-failed") {
                            EmptyState(
                                icon = Icons.Outlined.CloudOff,
                                title = "Could not load your listings",
                                message = "Check your internet connection, then try again.",
                                actionLabel = "Try again",
                                onAction = { foodViewModel.loadMyPosts() },
                                tint = SaluColors.Error,
                                iconBackground = SaluColors.ErrorSoft
                            )
                        }
                    } else if (shownListings.isEmpty()) {
                        item(key = "empty-listings-$listingFilter") {
                            if (listingFilter == "Active") {
                                EmptyState(
                                    icon = Icons.Outlined.Inventory2,
                                    title = "No active listings",
                                    message = "Listings that can still be reserved, or that have reservations to hand over, appear here."
                                )
                            } else {
                                EmptyState(
                                    icon = Icons.Outlined.Inventory2,
                                    title = "No closed listings",
                                    message = "Listings that have expired, or have nothing left to reserve or hand over, appear here."
                                )
                            }
                        }
                    }
                    items(shownListings, key = { "listing-${it.id}" }) { post ->
                        val reservationsHere = onListing[post.id].orEmpty()
                        ActivityListingCard(
                            post = post,
                            reservations = reservationsHere,
                            reservationsKnown = reservationsLoaded,
                            now = now,
                            onManage = { openManage(post) },
                            onView = { viewingPostId = post.id },
                            onEdit = if (post.isExpired(now)) null else ({ editing = post }),
                            onDelete = { pendingDelete = post },
                            modifier = Modifier
                                .animateItem()
                                .then(cardModifier)
                        )
                    }
                    item(key = "create-listing") {
                        SaluSecondaryButton(
                            text = "Create listing",
                            icon = Icons.Outlined.Add,
                            onClick = onPost,
                            modifier = Modifier
                                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                tab == TAB_RESERVATIONS -> {
                    if (shownReservations.isEmpty()) {
                        item(key = "empty-reservations-$reservationFilter") {
                            when (reservationFilter) {
                                "Free food" -> EmptyState(
                                    icon = Icons.Outlined.Redeem,
                                    title = "No free food reserved",
                                    message = "Free food you reserve appears here until you pick it up."
                                )
                                "Purchases" -> EmptyState(
                                    icon = Icons.Outlined.ShoppingBag,
                                    title = "No purchases to pick up",
                                    message = "Food you buy appears here until you pick it up. You pay at pickup."
                                )
                                else -> EmptyState(
                                    icon = Icons.Outlined.BookmarkBorder,
                                    title = "No reservations",
                                    message = "Reserve food from Home and it appears here until you pick it up. Past pickups and cancellations are in History."
                                )
                            }
                        }
                    }
                    items(shownReservations, key = { it.key }) { item ->
                        ActivityReservationCard(
                            item = item,
                            now = now,
                            onView = { openReservationKey = item.key },
                            onMessage = { messageViewModel.openForReservation(item.id) },
                            onCancel = { confirmCancel = item },
                            modifier = Modifier
                                .animateItem()
                                .then(cardModifier)
                        )
                    }
                    if (shownReservations.isNotEmpty()) {
                        item(key = "reservations-hint") {
                            HintLine(
                                "Open a reservation for pickup details or to message the provider.",
                                Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
                            )
                        }
                    }
                }

                else -> {
                    if (shownHistory.isEmpty()) {
                        item(key = "empty-history-$historyFilter") {
                            when (historyFilter) {
                                "Completed" -> EmptyState(
                                    icon = Icons.Outlined.TaskAlt,
                                    title = "Nothing completed yet",
                                    message = "A reservation is completed when the provider marks it as picked up."
                                )
                                "Cancelled" -> EmptyState(
                                    icon = Icons.Outlined.EventBusy,
                                    title = "No cancellations",
                                    message = "Cancelled reservations, yours and those on your listings, appear here."
                                )
                                else -> EmptyState(
                                    icon = Icons.Outlined.History,
                                    title = "No past transactions yet",
                                    message = "Pickups and cancellations appear here, for food you shared and food you reserved."
                                )
                            }
                        }
                    }
                    items(shownHistory, key = { it.key }) { item ->
                        ActivityHistoryRow(
                            item = item,
                            now = now,
                            onClick = { openReservationKey = item.key },
                            modifier = Modifier
                                .animateItem()
                                .then(cardModifier)
                        )
                    }
                }
            }
        }
    }

    // The pages find their listing or reservation in the current lists, so they
    // show fresh data after a reload.
    myPosts.firstOrNull { it.id == viewingPostId }?.let { post ->
        val reservationsHere = onListing[post.id].orEmpty()
        ListingDetailsPage(
            post = post,
            reservations = reservationsHere,
            now = now,
            onManage = if (reservationsHere.isEmpty()) null else ({ openManage(post) }),
            onEdit = if (post.isExpired(now)) null else ({ editing = post }),
            onDelete = { pendingDelete = post },
            onBack = { viewingPostId = null }
        )
    }

    myPosts.firstOrNull { it.id == managingPostId }?.let { post ->
        ManageReservationsPage(
            post = post,
            reservations = onListing[post.id].orEmpty(),
            now = now,
            filter = manageFilter,
            onFilter = { manageFilter = it },
            saving = reservationSaving,
            onMessage = { messageViewModel.openForReservation(it.id) },
            onMarkPickedUp = { confirmClaim = it },
            onRelease = { confirmRelease = it },
            onBack = { managingPostId = null }
        )
    }

    everyReservation.firstOrNull { it.key == openReservationKey }?.let { item ->
        ReservationDetailsPage(
            item = item,
            now = now,
            review = myReviews[item.id],
            saving = reservationSaving,
            onPickup = { pickupKey = item.key },
            onMessage = { messageViewModel.openForReservation(item.id) },
            onCancel = { confirmCancel = item },
            onRate = { startRating(item) },
            onMarkPickedUp = { confirmClaim = item },
            onRelease = { confirmRelease = item },
            onBack = { openReservationKey = null }
        )
    }

    everyReservation.firstOrNull { it.key == pickupKey }?.let { item ->
        PickupDetailsPage(
            item = item,
            now = now,
            onMessage = { messageViewModel.openForReservation(item.id) },
            onBack = { pickupKey = null }
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

    confirmClaim?.let { item ->
        val name = item.otherName ?: "the recipient"
        AlertDialog(
            onDismissRequest = { confirmClaim = null },
            title = { Text("Mark as picked up?") },
            text = {
                Text("Do this only after you've handed ${item.amount} of ${item.post.name} to $name. It can't be undone.")
            },
            confirmButton = {
                TextButton(enabled = !reservationSaving, onClick = {
                    if (reservationViewModel.claim(item.id)) confirmClaim = null
                }) { Text("Mark as picked up") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClaim = null }) { Text("Not yet") }
            }
        )
    }

    confirmRelease?.let { item ->
        val name = item.otherName ?: "The recipient"
        AlertDialog(
            onDismissRequest = { confirmRelease = null },
            title = { Text("Release this reservation?") },
            text = {
                Text(
                    "Do this only if $name won't pick up ${item.amount} of ${item.post.name}. " +
                        "$name will be told, and the amount can be reserved by others again."
                )
            },
            confirmButton = {
                TextButton(enabled = !reservationSaving, onClick = {
                    if (reservationViewModel.release(item.id)) {
                        confirmRelease = null
                        openReservationKey = null
                    }
                }) { Text("Release reservation", color = SaluColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRelease = null }) { Text("Keep it") }
            }
        )
    }

    confirmCancel?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text("Cancel this reservation?") },
            text = {
                Text("Your ${item.amount} of ${item.post.name} will be available for others to reserve again.")
            },
            confirmButton = {
                TextButton(enabled = !reservationSaving, onClick = {
                    if (reservationViewModel.cancel(item.id)) {
                        confirmCancel = null
                        openReservationKey = null
                        pickupKey = null
                    }
                }) { Text("Cancel reservation", color = SaluColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = null }) { Text("Keep it") }
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
                    viewingPostId = null
                    managingPostId = null
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
            title = { Text("Activity") },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = { notice = "" }) { Text("OK") } }
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

/* LIST PIECES */

@Composable
private fun ActivityHeader(unreadAlerts: Int, onAlerts: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Activity",
                style = SaluText.ScreenTitle,
                color = SaluColors.DarkGreen,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Your food-sharing journey",
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

/** The three tabs as one segmented control; the chosen one is filled green. */
@Composable
private fun ActivityTabRow(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SaluRadius.Pill)
            .background(Color.White)
            .border(1.dp, SaluColors.Line, SaluRadius.Pill)
            .padding(4.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActivityTabs.forEach { label ->
            val isSelected = label == selected
            val container by animateColorAsState(
                if (isSelected) SaluColors.Green else Color.Transparent,
                tween(SaluMotion.MEDIUM),
                label = "activityTabContainer"
            )
            val content by animateColorAsState(
                if (isSelected) Color.White else SaluColors.DarkGreen,
                tween(SaluMotion.MEDIUM),
                label = "activityTabContent"
            )
            Box(
                modifier = Modifier
                    // Wider for a longer name, so "My Reservations" fits on one line.
                    .weight(label.length + 4f)
                    .heightIn(min = 48.dp)
                    .clip(SaluRadius.Pill)
                    .background(container)
                    .selectable(selected = isSelected, onClick = { onSelect(label) }, role = Role.Tab)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = SaluText.Meta.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = content,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

/** A tab's heading, with the part you play in it on the right ("Donor / seller"). */
@Composable
private fun ActivitySection(title: String, role: String? = null, spokenRole: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = SaluText.SectionTitle,
            color = SaluColors.DarkGreen,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (role != null) {
            Row(
                modifier = Modifier.clearAndSetSemantics { contentDescription = spokenRole ?: role },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.PersonOutline,
                    contentDescription = null,
                    tint = SaluColors.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(role, style = SaluText.Meta, color = SaluColors.Gray)
            }
        }
    }
}

@Composable
private fun ActivityFilterRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (String) -> String = { it }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            SaluFilterChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) }
            )
        }
    }
}

/** History's totals: completed reservations and the items they rescued. Each explains itself when tapped. */
@Composable
private fun HistoryTotals(
    completed: Int,
    items: Int,
    onExplain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SaluCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(vertical = 4.dp)
        ) {
            ActivityMetric("Completed", completed.toString(), "transactions", Modifier.weight(1f)) {
                onExplain(
                    "$completed completed: your reservations that providers marked as picked up, " +
                        "plus reservations on your listings that you marked as picked up."
                )
            }
            MetricDivider()
            ActivityMetric("Items rescued", items.toString(), "items", Modifier.weight(1f)) {
                onExplain(
                    "$items items rescued: the total quantity of those completed reservations, " +
                        "counting each unit (pack, piece, kg) as one."
                )
            }
        }
    }
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
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(SaluRadius.Small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(title, style = SaluText.Meta, color = SaluColors.Gray, maxLines = 2)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = SaluColors.DarkGreen)
        Text(caption, style = SaluText.Meta, color = SaluColors.Gray)
    }
}

@Composable
private fun ActivitySkeleton(modifier: Modifier = Modifier) {
    SkeletonGroup(description = "Loading your activity", modifier = modifier) {
        repeat(3) {
            SaluCard(Modifier.fillMaxWidth()) {
                ListRowSkeleton(Modifier.padding(horizontal = 12.dp), thumbnail = 72.dp)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** "Donation · Free" or "For sale · ₱20.00 / bunch". */
private fun listingOffer(post: FoodPost): String =
    if (post.price > 0) "For sale · ${formatPeso(post.price)} / ${unitSingular(post.unit)}" else "Donation · Free"

/** "8 pcs available", or that none is left to reserve. */
private fun listingStock(post: FoodPost): String =
    if (post.quantity > 0) "${post.quantity} ${post.unit} available" else "None left to reserve"

/** A badge for a listing that can't simply be reserved (Expired, Reserved, Claimed, Removed); null otherwise. */
private fun listingStatus(post: FoodPost, now: Instant): SaluStatus? =
    listingStatus(post, post.isExpired(now)).takeIf { it != SaluStatus.Available }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityListingCard(
    post: FoodPost,
    reservations: List<ActivityReservation>,
    // False until the reservations have loaded: no count rather than a wrong one.
    reservationsKnown: Boolean,
    now: Instant,
    onManage: () -> Unit,
    onView: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val open = reservations.count { it.status == ReservationStatus.RESERVED }
    SaluCard(modifier = modifier.fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    ActivityPhoto(post, Modifier.size(96.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            post.name,
                            style = SaluText.FoodTitle,
                            color = SaluColors.DarkGreen,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            // Clear of the ⋮ button in the card's corner.
                            modifier = Modifier.padding(top = 2.dp, end = 40.dp)
                        )
                        Text(listingOffer(post), style = SaluText.Supporting, color = SaluColors.Gray)
                        Text(listingStock(post), style = SaluText.Supporting, color = SaluColors.Gray)
                        Spacer(Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (reservationsKnown) ReservationCountChip(open = open, any = reservations.isNotEmpty())
                            listingStatus(post, now)?.let { StatusBadge(it) }
                        }
                    }
                }

                if (reservations.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SaluPrimaryButton(
                            text = "Manage reservations",
                            onClick = onManage,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(
                            onClick = onView,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("View listing", style = MaterialTheme.typography.labelLarge, color = SaluColors.Green)
                        }
                    }
                } else {
                    SaluSecondaryButton(text = "View listing", onClick = onView)
                }
            }

            MoreMenu(
                title = post.name,
                actions = buildList {
                    add(MenuAction("View listing", Icons.Outlined.Visibility, onView))
                    if (onEdit != null) add(MenuAction("Edit listing", Icons.Outlined.Edit, onEdit))
                    add(MenuAction("Delete listing", Icons.Outlined.DeleteOutline, onDelete, destructive = true))
                },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/** "2 active reservations" in green, or a grey "No reservations yet". */
@Composable
private fun ReservationCountChip(open: Int, any: Boolean) {
    val active = open > 0
    val text = when {
        open == 1 -> "1 active reservation"
        active -> "$open active reservations"
        any -> "No active reservations"
        else -> "No reservations yet"
    }
    val content = if (active) SaluColors.Green else SaluColors.Gray
    Row(
        modifier = Modifier
            .clip(SaluRadius.Pill)
            .background(if (active) SaluColors.PaleGreen else SaluColors.Neutral)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (active) Icons.Outlined.People else Icons.Outlined.Info,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            style = SaluText.Meta.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1
        )
    }
}

@Composable
private fun ActivityReservationCard(
    item: ActivityReservation,
    now: Instant,
    onView: () -> Unit,
    onMessage: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val post = item.post
    SaluCard(modifier = modifier.fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    ActivityPhoto(post, Modifier.size(96.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            post.name,
                            style = SaluText.FoodTitle,
                            color = SaluColors.DarkGreen,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            // Clear of the ⋮ button in the card's corner.
                            modifier = Modifier.padding(top = 2.dp, end = 40.dp)
                        )
                        item.otherName?.let {
                            Text(
                                "From $it",
                                style = SaluText.Supporting,
                                color = SaluColors.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            item.amount + " · " + if (item.priced) "${item.total} total" else "Free",
                            style = SaluText.Supporting,
                            color = SaluColors.Gray
                        )
                        Spacer(Modifier.height(4.dp))
                        ReservationStatusBadge(item.status)
                        Spacer(Modifier.height(2.dp))
                        if (item.priced) IconLine(Icons.Outlined.Payments, "Pay at pickup")
                        val window = pickupLine(parseTimestamp(post.pickupStart), parseTimestamp(post.pickupEnd), now)
                        if (window != null) {
                            IconLine(Icons.Outlined.CalendarMonth, window)
                        } else {
                            IconLine(Icons.Outlined.LocationOn, post.pickupLocation)
                        }
                    }
                }
                SaluSecondaryButton(text = "View reservation", onClick = onView)
            }

            MoreMenu(
                title = post.name,
                actions = listOf(
                    MenuAction("View reservation", Icons.Outlined.Visibility, onView),
                    MenuAction("Message provider", Icons.Outlined.ChatBubbleOutline, onMessage),
                    MenuAction("Cancel reservation", Icons.Outlined.EventBusy, onCancel, destructive = true)
                ),
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/** What a finished transaction was, with the other person when known: "Donation to Mika", "Purchase from Lina · ₱60.00". */
private fun historyKind(item: ActivityReservation): String {
    val name = item.otherName
    return when {
        item.mine && item.priced -> (if (name != null) "Purchase from $name" else "Purchase") + " · ${item.total}"
        item.mine -> if (name != null) "Donation from $name" else "Free food"
        item.priced -> (if (name != null) "Sale to $name" else "Sale") + " · ${item.total}"
        else -> if (name != null) "Donation to $name" else "Donation"
    }
}

@Composable
private fun ActivityHistoryRow(
    item: ActivityReservation,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaluCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActivityPhoto(item.post, Modifier.size(72.dp))
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        item.post.name,
                        style = SaluText.FoodTitle,
                        color = SaluColors.DarkGreen,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    ReservationStatusBadge(item.status)
                }
                Text(
                    historyKind(item),
                    style = SaluText.Supporting,
                    color = SaluColors.DarkGreen,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(item.amount, historyDayLabel(item.lastChange, now)).joinToString(" · "),
                    style = SaluText.Meta,
                    color = SaluColors.Gray
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = SaluColors.Gray,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(22.dp)
            )
        }
    }
}

/** A reservation's status: Reserved, Completed (marked picked up) or Cancelled, each with its icon. */
@Composable
private fun ReservationStatusBadge(status: ReservationStatus, modifier: Modifier = Modifier) {
    when (status) {
        ReservationStatus.RESERVED -> StatusBadge(SaluStatus.Reserved, modifier)
        ReservationStatus.CLAIMED -> StatusBadge(SaluStatus.Claimed, modifier, label = "Completed")
        ReservationStatus.CANCELLED -> StatusBadge(SaluStatus.Cancelled, modifier)
    }
}

@Composable
private fun ActivityPhoto(post: FoodPost, modifier: Modifier) {
    ListingPhoto(
        imageUrl = post.displayImageUrl?.takeIf { it.isNotBlank() },
        hasPhoto = post.hasPhoto,
        contentDescription = post.name,
        modifier = modifier.clip(SaluRadius.Small),
        textSize = 9.sp
    )
}

/** A small grey line with its icon, under a card's title. */
@Composable
private fun IconLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = SaluColors.Gray,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = SaluText.Meta,
            color = SaluColors.Gray,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A quiet hint with an info icon, under a list or at the end of a page. */
@Composable
private fun HintLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = SaluColors.Gray,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = SaluText.Supporting, color = SaluColors.Gray)
    }
}

private class MenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val destructive: Boolean = false
)

/** A card's ⋮ menu. */
@Composable
private fun MoreMenu(title: String, actions: List<MenuAction>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "More options for $title", tint = SaluColors.Gray)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                val color = if (action.destructive) SaluColors.Error else SaluColors.DarkGreen
                DropdownMenuItem(
                    text = { Text(action.label, color = color) },
                    leadingIcon = {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            tint = if (action.destructive) SaluColors.Error else SaluColors.Green
                        )
                    },
                    onClick = {
                        open = false
                        action.onClick()
                    }
                )
            }
        }
    }
}

/* PAGES */

/** The food at the top of a reservation page: photo, name, who it's from or for, and the status. */
@Composable
private fun PageFoodCard(post: FoodPost, line: String?, status: ReservationStatus) {
    SaluCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActivityPhoto(post, Modifier.size(96.dp))
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    post.name,
                    style = SaluText.SectionTitle,
                    color = SaluColors.DarkGreen,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                line?.let {
                    Text(it, style = SaluText.Supporting, color = SaluColors.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                ReservationStatusBadge(status)
            }
        }
    }
}

/** Facts as rows of icon, label and value, in one card. */
@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    SaluCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            content = content
        )
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = SaluColors.Green,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = SaluText.Supporting,
            color = SaluColors.Gray,
            modifier = Modifier
                .weight(0.4f)
                .padding(top = 1.dp)
        )
        Text(
            value,
            style = SaluText.Body.copy(fontWeight = FontWeight.Medium),
            color = SaluColors.DarkGreen,
            modifier = Modifier.weight(0.6f)
        )
    }
}

/** A red text button for a page's destructive action, under its other buttons. */
@Composable
private fun DestructiveTextButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) SaluColors.Error else SaluColors.Faint)
    }
}

/**
 * A donor's page for one listing's reservations: who reserved how much, with
 * Message, and Mark as picked up while a reservation is open.
 */
@Composable
private fun ManageReservationsPage(
    post: FoodPost,
    reservations: List<ActivityReservation>,
    now: Instant,
    filter: String,
    onFilter: (String) -> Unit,
    saving: Boolean,
    onMessage: (ActivityReservation) -> Unit,
    onMarkPickedUp: (ActivityReservation) -> Unit,
    onRelease: (ActivityReservation) -> Unit,
    onBack: () -> Unit
) {
    val byFilter = reservations.groupBy { manageFilterFor(it.status) }
    val open = byFilter["Active"].orEmpty()
    val shown = byFilter[filter].orEmpty().sortedByDescending { it.lastChange ?: Instant.EPOCH }

    ActionPage(
        heading = "Manage reservations",
        subtitle = "People reserving your food.",
        onBack = onBack
    ) {
        SaluCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActivityPhoto(post, Modifier.size(88.dp))
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        post.name,
                        style = SaluText.SectionTitle,
                        color = SaluColors.DarkGreen,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(listingOffer(post), style = SaluText.Supporting, color = SaluColors.Gray)
                    Text(
                        "${post.quantity} ${post.unit} available · ${open.sumOf { it.reservation.quantity }} reserved",
                        style = SaluText.Supporting,
                        color = SaluColors.Gray
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ManageFilters.forEach { option ->
                val count = byFilter[option].orEmpty().size
                SaluFilterChip(
                    label = if (count > 0) "$option ($count)" else option,
                    selected = option == filter,
                    onClick = { onFilter(option) }
                )
            }
        }

        if (shown.isEmpty()) {
            when (filter) {
                "Completed" -> EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = "No completed pickups yet",
                    message = "Reservations you mark as picked up appear here."
                )
                "Cancelled" -> EmptyState(
                    icon = Icons.Outlined.EventBusy,
                    title = "No cancellations",
                    message = "Reservations the recipient cancelled appear here."
                )
                else -> EmptyState(
                    icon = Icons.Outlined.People,
                    title = "No active reservations",
                    message = "When someone reserves this food, they appear here until you mark it as picked up."
                )
            }
        } else {
            shown.forEach { item ->
                key(item.key) {
                    ManagedReservationCard(
                        item = item,
                        now = now,
                        saving = saving,
                        onMessage = { onMessage(item) },
                        onMarkPickedUp = { onMarkPickedUp(item) },
                        onRelease = { onRelease(item) }
                    )
                }
            }
        }

        if (open.isNotEmpty()) {
            HintLine("Confirm only after handing over the food.")
        }
    }
}

@Composable
private fun ManagedReservationCard(
    item: ActivityReservation,
    now: Instant,
    saving: Boolean,
    onMessage: () -> Unit,
    onMarkPickedUp: () -> Unit,
    onRelease: () -> Unit
) {
    // Donors see the recipient's name only, so the avatar is their initial.
    val name = item.otherName ?: "Recipient"
    val moment = when (item.status) {
        ReservationStatus.RESERVED -> momentLabel(parseTimestamp(item.reservation.createdAt), now)?.let { "Reserved $it" }
        ReservationStatus.CLAIMED -> momentLabel(item.lastChange, now)?.let { "Picked up $it" }
        ReservationStatus.CANCELLED -> momentLabel(item.lastChange, now)?.let { "Cancelled $it" }
    }
    SaluCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Avatar(photoUrl = null, name = name, size = 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        name,
                        style = SaluText.FoodTitle,
                        color = SaluColors.DarkGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        item.amount + if (item.priced) " · ${item.total} at pickup" else "",
                        style = SaluText.Supporting,
                        color = SaluColors.Gray
                    )
                    moment?.let { Text(it, style = SaluText.Meta, color = SaluColors.Gray) }
                    Spacer(Modifier.height(2.dp))
                    ReservationStatusBadge(item.status)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SaluSecondaryButton(
                    text = "Message",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    onClick = onMessage,
                    modifier = Modifier.weight(0.85f)
                )
                if (item.status == ReservationStatus.RESERVED) {
                    SaluPrimaryButton(
                        text = "Mark as picked up",
                        onClick = onMarkPickedUp,
                        enabled = !saving,
                        modifier = Modifier.weight(1.15f)
                    )
                }
            }
            // For a recipient who won't come: the food can be reserved again.
            if (item.status == ReservationStatus.RESERVED) {
                DestructiveTextButton(text = "Release reservation", enabled = !saving, onClick = onRelease)
            }
        }
    }
}

/** What a reservation page says about where things stand. */
private fun reservationNote(item: ActivityReservation): String {
    val removedLater = item.post.status == FoodPostStatus.REMOVED
    return when (item.status) {
        ReservationStatus.RESERVED -> if (item.mine) {
            "Your reservation is recorded. Message the provider to coordinate pickup." +
                if (item.priced) " Pay ${item.total} at pickup." else ""
        } else {
            "Hand over the food, then mark it as picked up." +
                if (item.priced) " Collect ${item.total} at pickup." else ""
        }
        ReservationStatus.CLAIMED -> if (item.mine) {
            "The provider marked this as picked up." +
                if (removedLater) " The donor has since removed this listing." else ""
        } else {
            "You marked this as picked up." +
                if (removedLater) " You have since removed this listing." else ""
        }
        ReservationStatus.CANCELLED -> when {
            item.mine && item.reservation.cancelledByListingRemoval ->
                "Cancelled because the donor removed this listing."
            item.mine && item.reservation.releasedByDonor ->
                "The provider released this reservation, so the food is no longer held for you."
            item.reservation.releasedByDonor -> "You released this reservation."
            item.mine -> "You cancelled this reservation." +
                if (removedLater) " The donor has since removed this listing." else ""
            removedLater -> "This reservation was cancelled. You have since removed this listing."
            else -> (item.otherName ?: "The recipient") + " cancelled this reservation."
        }
    }
}

/**
 * One reservation, for the person who made it or the listing's donor: its
 * facts and what can be done with it (pickup details, message, cancel, rate,
 * mark as picked up).
 */
@Composable
private fun ReservationDetailsPage(
    item: ActivityReservation,
    now: Instant,
    review: MyReview?,
    saving: Boolean,
    onPickup: () -> Unit,
    onMessage: () -> Unit,
    onCancel: () -> Unit,
    onRate: () -> Unit,
    onMarkPickedUp: () -> Unit,
    onRelease: () -> Unit,
    onBack: () -> Unit
) {
    val post = item.post
    val open = item.status == ReservationStatus.RESERVED
    ActionPage(
        heading = "Reservation details",
        subtitle = if (item.mine) "Food you reserved." else "A reservation on your listing.",
        onBack = onBack
    ) {
        PageFoodCard(
            post = post,
            line = item.otherName?.let { if (item.mine) "From $it" else "Reserved by $it" },
            status = item.status
        )

        DetailCard {
            DetailRow(Icons.Outlined.Inventory2, "Quantity", item.amount)
            DetailRow(Icons.Outlined.Sell, if (item.priced) "Total" else "Cost", if (item.priced) item.total else "Free")
            when (item.status) {
                ReservationStatus.RESERVED -> DetailRow(
                    Icons.Outlined.CalendarMonth,
                    "Pickup",
                    pickupWindowLabel(parseTimestamp(post.pickupStart), parseTimestamp(post.pickupEnd), now)
                        ?: "Not set — ask in the chat"
                )
                ReservationStatus.CLAIMED -> DetailRow(
                    Icons.Outlined.TaskAlt,
                    "Picked up",
                    momentLabel(item.lastChange, now) ?: "—"
                )
                ReservationStatus.CANCELLED -> DetailRow(
                    Icons.Outlined.EventBusy,
                    "Cancelled",
                    momentLabel(item.lastChange, now) ?: "—"
                )
            }
            DetailRow(
                Icons.Outlined.Schedule,
                "Reserved",
                momentLabel(parseTimestamp(item.reservation.createdAt), now) ?: "—"
            )
        }

        InlineNotice(text = reservationNote(item), icon = Icons.Outlined.Info)

        val messageLabel = if (item.mine) "Message provider" else "Message recipient"
        when {
            item.mine && open -> {
                ActionButton(text = "View pickup details", icon = Icons.Outlined.LocationOn, onClick = onPickup)
                SaluSecondaryButton(text = messageLabel, icon = Icons.Outlined.ChatBubbleOutline, onClick = onMessage)
                DestructiveTextButton(text = "Cancel reservation", enabled = !saving, onClick = onCancel)
            }
            // Only a picked-up reservation this account made can be rated.
            item.mine && item.status == ReservationStatus.CLAIMED -> {
                ActionButton(
                    text = if (review != null) "Change your review" else rateTitle(item),
                    icon = Icons.Outlined.StarOutline,
                    onClick = onRate
                )
                SaluSecondaryButton(text = messageLabel, icon = Icons.Outlined.ChatBubbleOutline, onClick = onMessage)
            }
            !item.mine && open -> {
                ActionButton(text = "Mark as picked up", enabled = !saving, onClick = onMarkPickedUp)
                SaluSecondaryButton(text = messageLabel, icon = Icons.Outlined.ChatBubbleOutline, onClick = onMessage)
                DestructiveTextButton(text = "Release reservation", enabled = !saving, onClick = onRelease)
            }
            else -> SaluSecondaryButton(text = messageLabel, icon = Icons.Outlined.ChatBubbleOutline, onClick = onMessage)
        }
    }
}

/**
 * Where and when to collect a reservation this account made: the window, the
 * place, the handover, the pin on a map with directions, and what to pay.
 */
@Composable
private fun PickupDetailsPage(
    item: ActivityReservation,
    now: Instant,
    onMessage: () -> Unit,
    onBack: () -> Unit
) {
    val post = item.post
    val context = LocalContext.current
    val pin = post.pickupPin
    var directionsError by remember { mutableStateOf("") }

    ActionPage(heading = "Pickup details", onBack = onBack) {
        PageFoodCard(post = post, line = item.otherName?.let { "From $it" }, status = item.status)

        DetailCard {
            DetailRow(Icons.Outlined.Inventory2, "Quantity", item.amount)
            DetailRow(Icons.Outlined.Sell, "Total", if (item.priced) item.total else "Free")
            DetailRow(Icons.Outlined.Payments, "Payment", if (item.priced) "Pay at pickup" else "Nothing to pay")
        }

        SaluCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = SaluColors.Green,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Pickup",
                            style = SaluText.FoodTitle,
                            color = SaluColors.DarkGreen,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            pickupWindowLabel(parseTimestamp(post.pickupStart), parseTimestamp(post.pickupEnd), now)
                                ?: "Pickup window not set — ask in the chat",
                            style = SaluText.Body,
                            color = SaluColors.DarkGreen
                        )
                        Text(post.pickupLocation, style = SaluText.Body, color = SaluColors.DarkGreen)
                        ListingHandover.fromCode(post.handover)?.let {
                            Text(it.label, style = SaluText.Supporting, color = SaluColors.Gray)
                        }
                    }
                }

                if (pin != null) {
                    PickupMap(
                        pin = pin,
                        start = pin,
                        startZoom = PIN_MAP_ZOOM,
                        description = "Map of the pickup pin for ${post.name}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(SaluRadius.Photo)
                    )
                } else {
                    Text(
                        "This listing has no map pin. Ask the provider for directions in the chat.",
                        style = SaluText.Supporting,
                        color = SaluColors.Gray
                    )
                }
            }
        }

        if (pin != null) {
            ActionButton(
                text = "Open directions",
                icon = Icons.Outlined.Directions,
                onClick = {
                    directionsError = if (openDirections(context, pin)) "" else "No maps app is available on this device."
                }
            )
            if (directionsError.isNotBlank()) {
                Text(directionsError, color = MaterialTheme.colorScheme.error, style = SaluText.Supporting)
            }
        }
        SaluSecondaryButton(text = "Message provider", icon = Icons.Outlined.ChatBubbleOutline, onClick = onMessage)
        HintLine("The provider marks this as picked up after handover.")
    }
}

/** A donor's own listing: what it offers, its reservations, pickup and details, with Edit and Delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListingDetailsPage(
    post: FoodPost,
    reservations: List<ActivityReservation>,
    now: Instant,
    onManage: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val open = reservations.filter { it.status == ReservationStatus.RESERVED }
    val expired = post.isExpired(now)

    ActionPage(heading = "Your listing", onBack = onBack) {
        SaluCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityPhoto(
                    post,
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
                Text(post.name, style = MaterialTheme.typography.titleLarge, color = SaluColors.DarkGreen)
                Text(listingOffer(post), style = SaluText.Supporting, color = SaluColors.Gray)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusBadge(listingStatus(post, now) ?: SaluStatus.Available)
                    ReservationCountChip(open = open.size, any = reservations.isNotEmpty())
                }
            }
        }

        DetailCard {
            DetailRow(Icons.Outlined.Inventory2, "Available", "${post.quantity} ${post.unit}")
            DetailRow(
                Icons.Outlined.People,
                "Reserved",
                "${open.sumOf { it.reservation.quantity }} ${post.unit}" +
                    if (open.isNotEmpty()) " (${open.size} open)" else ""
            )
            DetailRow(
                Icons.Outlined.Sell,
                "Price",
                if (post.price > 0) "${formatPeso(post.price)} / ${unitSingular(post.unit)}" else "Free"
            )
            DetailRow(
                Icons.Outlined.HourglassBottom,
                if (expired) "Expired" else "Expires",
                momentLabel(parseTimestamp(post.expirationDate), now) ?: "—"
            )
            DetailRow(Icons.Outlined.Schedule, "Posted", momentLabel(parseTimestamp(post.createdAt), now) ?: "—")
        }

        DetailCard {
            DetailRow(
                Icons.Outlined.CalendarMonth,
                "Pickup",
                pickupWindowLabel(parseTimestamp(post.pickupStart), parseTimestamp(post.pickupEnd), now) ?: "Not set"
            )
            DetailRow(Icons.Outlined.LocationOn, "Location", post.pickupLocation)
            DetailRow(Icons.Outlined.Handshake, "Handover", ListingHandover.fromCode(post.handover)?.label ?: "Not set")
        }

        SaluCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "About this food",
                    style = SaluText.FoodTitle,
                    color = SaluColors.DarkGreen,
                    modifier = Modifier.semantics { heading() }
                )
                Text("Category: ${post.category}", style = SaluText.Body, color = SaluColors.DarkGreen)
                post.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = SaluText.Body, color = SaluColors.DarkGreen)
                }
                post.allergens?.takeIf { it.isNotBlank() }?.let {
                    Text("Allergens: $it", style = SaluText.Supporting, color = SaluColors.Gray)
                }
                post.storageNotes?.takeIf { it.isNotBlank() }?.let {
                    Text("Storage: $it", style = SaluText.Supporting, color = SaluColors.Gray)
                }
            }
        }

        onManage?.let { ActionButton(text = "Manage reservations", icon = Icons.Outlined.People, onClick = it) }
        onEdit?.let { SaluSecondaryButton(text = "Edit listing", icon = Icons.Outlined.Edit, onClick = it) }
        DestructiveTextButton(text = "Delete listing", onClick = onDelete)
    }
}

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
