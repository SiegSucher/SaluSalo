package com.example.salusalo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Verified
import com.example.salusalo.data.ReviewRepository
import com.example.salusalo.model.DONOR_REVIEWS_LIMIT
import com.example.salusalo.model.GeoPin
import com.example.salusalo.model.PIN_MAP_ZOOM
import com.example.salusalo.model.distanceLabel
import com.example.salusalo.model.ListingDonorProfile
import com.example.salusalo.model.ReportTarget
import com.example.salusalo.model.ReviewItem
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.formatRatingAverage
import com.example.salusalo.model.reservationTotal
import com.example.salusalo.model.DEFAULT_HOLD_MINUTES
import com.example.salusalo.model.HOLD_MINUTES
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.model.pickupDeadlineEstimate
import com.example.salusalo.model.reviewCountText
import com.example.salusalo.viewmodel.ReserveResult
import kotlinx.coroutines.CancellationException

/**
 * The Pickup location and Donor/Seller pages of a listing. With a pickup pin,
 * the Pickup location page shows it on a map with "Directions"; without one,
 * a text search in a maps app. Questions go to the
 * real chat through [onMessage] (with a prefilled draft). The Donor/Seller page
 * also shows the donor's "Email verified" badge, rating and latest reviews
 * (names only, loaded fresh when it opens) and "Report <name>".
 */
@Composable
fun FoodActionsDialog(
    action: String,
    foodPostId: String,
    foodTitle: String,
    provider: String,
    // The donor's real name, or null when it could not be loaded.
    donorName: String?,
    isOwnListing: Boolean,
    // From the listing row until the page's own load finishes.
    donorEmailVerified: Boolean,
    donorRatingAverage: Double?,
    donorReviewCount: Int,
    // True when the listing has a stored cover photo.
    hasPhoto: Boolean,
    // The listing's pickup_location and its signed cover URL (null if none).
    pickupLocation: String,
    // The listing's pickup pin (null for older listings) and the straight-line
    // distance to it from the phone (null when unknown).
    pickupPin: GeoPin?,
    distanceMeters: Double?,
    imageUrl: String?,
    price: String,
    status: String,
    onClose: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val address = pickupLocation
    val donation = price.equals("Free", ignoreCase = true)

    var mapError by remember {
        mutableStateOf("")
    }

    val heading = when (action) {
        "location" -> "Pickup location"
        "provider" -> if (donation) "Donor" else "Seller"
        else -> "Food details"
    }

    ActionPage(heading = heading, onBack = onClose) {
        if (action != "provider") {
            ActionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ListingPhoto(
                        imageUrl = imageUrl,
                        hasPhoto = hasPhoto,
                        contentDescription = foodTitle,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(SaluRadius.Small)
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            foodTitle,
                            style = SaluText.FoodTitle,
                            color = SaluColors.DarkGreen
                        )

                        Text(
                            provider,
                            style = SaluText.Supporting,
                            color = SaluColors.Gray
                        )

                        Text(
                            price,
                            style = SaluText.Meta.copy(fontWeight = FontWeight.Bold),
                            color = if (donation) SaluColors.Green else SaluColors.DarkGreen
                        )
                    }
                }
            }
        }

        when (action) {
            "location" -> {
                ActionCard {
                    Row(verticalAlignment = Alignment.Top) {
                        IconBubble(Icons.Outlined.LocationOn, size = 40.dp, iconSize = 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Pickup area",
                                style = SaluText.SectionTitle,
                                color = SaluColors.DarkGreen,
                                modifier = Modifier.semantics { heading() }
                            )

                            Text(address, style = SaluText.Body, color = SaluColors.DarkGreen)

                            if (pickupPin != null && distanceMeters != null) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.NearMe,
                                        contentDescription = null,
                                        tint = SaluColors.Green,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "About ${distanceLabel(distanceMeters)} from you in a straight line; " +
                                                "the route may be longer.",
                                        style = SaluText.Meta,
                                        color = SaluColors.Green
                                    )
                                }
                            }
                        }
                    }
                }

                if (pickupPin != null) {
                    PickupMap(
                        pin = pickupPin,
                        start = pickupPin,
                        startZoom = PIN_MAP_ZOOM,
                        description = "Map of the pickup pin for $foodTitle",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(SaluRadius.Card)
                    )

                    ActionButton(
                        text = "Directions",
                        icon = Icons.Outlined.Directions,
                        onClick = {
                            if (!openDirections(context, pickupPin)) {
                                mapError = "No maps app is available on this device."
                            }
                        }
                    )
                } else {
                    ActionButton(
                        text = "Search area in Maps",
                        icon = Icons.Outlined.Map,
                        onClick = {
                            // A text search for the address as entered; this
                            // listing has no map pin.
                            val query = if (address.contains("Philippines", ignoreCase = true)) {
                                address
                            } else {
                                "$address, Philippines"
                            }
                            val uri = Uri.parse("geo:0,0?q=" + Uri.encode(query))

                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, uri)
                                )
                            } catch (_: ActivityNotFoundException) {
                                mapError =
                                    "No maps app is available on this device."
                            }
                        }
                    )
                }

                if (mapError.isNotBlank()) {
                    InlineNotice(
                        text = mapError,
                        icon = Icons.Outlined.ErrorOutline,
                        tone = NoticeTone.Error
                    )
                }

                ActionCard {
                    Text(
                        "Pickup instructions",
                        style = SaluText.FoodTitle,
                        color = SaluColors.DarkGreen
                    )

                    Text(
                        if (pickupPin != null) {
                            "The pin shows where the provider hands over the food. " +
                                    "If you can't find it, ask them in the chat."
                        } else {
                            "Ask the provider for the exact meeting " +
                                    "point and collection instructions."
                        },
                        style = SaluText.Body,
                        color = SaluColors.Gray
                    )

                    SaluSecondaryButton(
                        text = "Ask for exact location",
                        icon = Icons.Outlined.ChatBubbleOutline,
                        onClick = { onMessage("Hi! Where exactly can I pick up $foodTitle?") }
                    )
                }
            }

            "provider" -> {
                // The donor's badge, rating and latest reviews, loaded fresh when
                // the page opens. Until then the listing row's values are shown.
                val reviewRepository = remember { ReviewRepository() }
                var donor by remember(foodPostId) { mutableStateOf<ListingDonorProfile?>(null) }
                var reviews by remember(foodPostId) { mutableStateOf<List<ReviewItem>?>(null) }
                var reviewsFailed by remember(foodPostId) { mutableStateOf(false) }
                LaunchedEffect(foodPostId) {
                    try {
                        donor = reviewRepository.getListingDonorProfile(foodPostId)
                        reviews = reviewRepository.getListingDonorReviews(foodPostId)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        reviewsFailed = true
                    }
                }
                val verified = donor?.emailVerified ?: donorEmailVerified
                val average = if (donor != null) donor?.ratingAverage else donorRatingAverage
                val count = donor?.ratingCount ?: donorReviewCount
                var reporting by rememberSaveable(foodPostId) { mutableStateOf(false) }

                ActionCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Avatar(photoUrl = null, name = provider, size = 72.dp)

                        Spacer(Modifier.height(4.dp))

                        Text(
                            provider,
                            style = MaterialTheme.typography.titleLarge,
                            color = SaluColors.DarkGreen,
                            modifier = Modifier.semantics { heading() }
                        )

                        Text(
                            if (donation) "Food donor" else "Food seller",
                            style = SaluText.Supporting,
                            color = SaluColors.Gray
                        )

                        if (verified) {
                            Row(
                                modifier = Modifier
                                    .clip(SaluRadius.Pill)
                                    .background(SaluColors.PaleGreen)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Verified,
                                    contentDescription = null,
                                    tint = SaluColors.Green,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Email verified", color = SaluColors.Green, style = SaluText.Status)
                            }
                        }

                        Text(
                            if (average != null && count > 0) {
                                "★ ${formatRatingAverage(average)} • ${reviewCountText(count)}"
                            } else {
                                "No reviews yet"
                            },
                            color = if (count > 0) RatingAmber else SaluColors.Gray,
                            style = SaluText.FoodTitle
                        )
                    }

                    ActionButton(
                        text = if (donation) "Message donor" else "Message seller",
                        icon = Icons.Outlined.ChatBubbleOutline,
                        onClick = { onMessage("") }
                    )
                }

                val loaded = reviews
                if (count > 0 || reviewsFailed || !loaded.isNullOrEmpty()) {
                    Text(
                        "Reviews",
                        style = SaluText.SectionTitle,
                        color = SaluColors.DarkGreen,
                        modifier = Modifier.semantics { heading() }
                    )

                    ActionCard {
                        when {
                            loaded == null && reviewsFailed -> InlineNotice(
                                text = "Reviews could not be loaded. Check your internet connection and try again.",
                                icon = Icons.Outlined.ErrorOutline,
                                tone = NoticeTone.Error
                            )
                            loaded == null -> SkeletonGroup(description = "Loading reviews") {
                                repeat(2) { ListRowSkeleton(thumbnail = 32.dp, round = true) }
                            }
                            else -> {
                                loaded.forEachIndexed { index, review ->
                                    if (index > 0) HorizontalDivider(color = SaluColors.Line)
                                    ReviewRow(review)
                                }
                                if (count > loaded.size && loaded.size >= DONOR_REVIEWS_LIMIT) {
                                    Text(
                                        "Showing the latest ${loaded.size} reviews.",
                                        style = SaluText.Meta,
                                        color = SaluColors.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                if (reporting) {
                    val person = donorName ?: if (donation) "this donor" else "this seller"
                    ReportPage(
                        target = ReportTarget.DONOR,
                        targetId = foodPostId,
                        heading = "Report $person",
                        prompt = "Why are you reporting $person?",
                        onClose = { reporting = false }
                    )
                }

                Text(
                    "Listing from this provider",
                    style = SaluText.SectionTitle,
                    color = SaluColors.DarkGreen,
                    modifier = Modifier.semantics { heading() }
                )

                ActionCard {
                    ListingPhoto(
                        imageUrl = imageUrl,
                        hasPhoto = hasPhoto,
                        contentDescription = foodTitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(SaluRadius.Photo)
                    )

                    Text(
                        foodTitle,
                        style = SaluText.FoodTitle,
                        color = SaluColors.DarkGreen
                    )

                    Text("$price • $status", style = SaluText.Supporting, color = SaluColors.Gray)

                    SaluSecondaryButton(
                        text = "Return to food details",
                        onClick = onClose
                    )
                }

                // Nobody reports themself.
                if (!isOwnListing) {
                    TextButton(
                        onClick = { reporting = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Flag,
                            contentDescription = null,
                            tint = SaluColors.Error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Report " + (donorName ?: if (donation) "this donor" else "this seller"),
                            style = MaterialTheme.typography.labelLarge,
                            color = SaluColors.Error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full-screen "Review reservation" page. Confirm sends create_reservation
 * (through the caller's [onConfirm]); on success the page turns into the
 * "Reserved" receipt. Amounts on the receipt come from the reservation the
 * server returned, never from the listing shown here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReservationReviewDialog(
    foodPostId: String,
    foodTitle: String,
    // The donor's name, or null when it could not be loaded.
    donorName: String?,
    // True when the listing has a stored cover photo.
    hasPhoto: Boolean,
    imageUrl: String?,
    // The signed-in profile's name, or null while unknown.
    profileName: String?,
    quantity: Int,
    unit: String,
    // Listing price per unit (0 = free).
    unitPrice: Double,
    pickupLocation: String,
    pickupWindow: String,
    handover: String,
    expires: String,
    // The listing's times, for the pickup deadline estimate (null when not set).
    pickupStart: java.time.Instant?,
    pickupEnd: java.time.Instant?,
    expiration: java.time.Instant?,
    saving: Boolean,
    result: ReserveResult?,
    onConfirm: (note: String, holdMinutes: Int) -> Unit,
    onMessageDonor: (reservationId: String) -> Unit,
    // Back or Done on the receipt: closes the receipt and the details page.
    onFinished: () -> Unit,
    // Back on the review page: returns to the details page.
    onBack: () -> Unit
) {
    val priced = unitPrice > 0
    val donorRole = if (priced) "seller" else "donor"
    var note by rememberSaveable(foodPostId) { mutableStateOf("") }
    var holdMinutes by rememberSaveable(foodPostId) { mutableIntStateOf(DEFAULT_HOLD_MINUTES) }

    val reserved = (result as? ReserveResult.Reserved)
        ?.takeIf { it.reservation.foodPostId == foodPostId }
    val failure = (result as? ReserveResult.Failed)?.message

    ActionPage(
        heading = if (reserved != null) "Reserved" else "Review reservation",
        onBack = if (reserved != null) onFinished else onBack
    ) {
        ActionCard {
            ListingPhoto(
                imageUrl = imageUrl,
                hasPhoto = hasPhoto,
                contentDescription = foodTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(SaluRadius.Photo)
            )

            Text(
                foodTitle,
                style = MaterialTheme.typography.titleLarge,
                color = SaluColors.DarkGreen
            )

            donorName?.takeIf { it.isNotBlank() }?.let {
                Text("From $it", style = SaluText.Supporting, color = SaluColors.Gray)
            }
        }

        if (reserved != null) {
            val reservation = reserved.reservation
            val paid = reservation.unitPrice > 0
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
                        "You reserved ${reservation.quantity} $unit of $foodTitle.",
                        style = SaluText.FoodTitle,
                        color = SaluColors.DarkGreen,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    if (paid) {
                        "Pay ${formatPeso(reservationTotal(reservation.unitPrice, reservation.quantity))} at pickup."
                    } else {
                        "Free — nothing to pay."
                    },
                    style = SaluText.Price,
                    color = if (paid) SaluColors.DarkGreen else SaluColors.Green
                )

                HorizontalDivider(color = SaluColors.Line)

                ActionLine("Pickup location", pickupLocation)
                ActionLine("Pickup window", pickupWindow)
                parseTimestamp(reservation.pickupDeadline)?.let { deadline ->
                    ActionLine("Pick up by", pickUpByTime(deadline, java.time.Instant.now()))
                    Text(
                        "If it isn't marked as picked up by then, the reservation expires and the food goes back on the listing.",
                        style = SaluText.Meta,
                        color = SaluColors.Gray
                    )
                }

                when (reserved.noteSent) {
                    true -> Text(
                        if (donorName.isNullOrBlank()) {
                            "Your message was sent to the $donorRole."
                        } else {
                            "Your message was sent to $donorName."
                        },
                        style = SaluText.Supporting,
                        color = SaluColors.Gray
                    )
                    false -> InlineNotice(
                        text = "Your message could not be sent. You can send it from the chat.",
                        icon = Icons.Outlined.ErrorOutline,
                        tone = NoticeTone.Error
                    )
                    null -> Unit
                }

                Text(
                    "You'll find this reservation in Activity → My Reservations.",
                    style = SaluText.Supporting,
                    color = SaluColors.Gray
                )
            }

            ActionButton(
                text = if (paid) "Message seller" else "Message donor",
                icon = Icons.Outlined.ChatBubbleOutline,
                onClick = { onMessageDonor(reservation.id) }
            )

            SaluSecondaryButton(
                text = "Done",
                onClick = onFinished
            )
        } else {
            ActionCard {
                ActionLine("Reserving as", profileName?.takeIf { it.isNotBlank() } ?: "—")
                ActionLine("Quantity", "$quantity $unit")
                ActionLine("Pickup location", pickupLocation)
                ActionLine("Pickup window", pickupWindow)
                ActionLine("Handover", handover)
                ActionLine("Expires", expires)

                HorizontalDivider(color = SaluColors.Line)

                if (priced) {
                    ActionLine("Price per unit", formatPeso(unitPrice))
                    ActionLine("Total", formatPeso(reservationTotal(unitPrice, quantity)), emphasis = true)
                    Text(
                        "Paid at pickup. The app takes no payments.",
                        style = SaluText.Meta,
                        color = SaluColors.Gray
                    )
                } else {
                    ActionLine("Price", "Free", emphasis = true)
                }
            }

            ActionCard {
                Text("Pick up within", style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HOLD_MINUTES.forEach { minutes ->
                        FilterChip(
                            selected = holdMinutes == minutes,
                            onClick = { holdMinutes = minutes },
                            enabled = !saving,
                            label = {
                                Text(
                                    when {
                                        minutes < 60 -> "$minutes min"
                                        minutes == 60 -> "1 hour"
                                        else -> "${minutes / 60} hours"
                                    }
                                )
                            }
                        )
                    }
                }
                if (expiration != null) {
                    val now = java.time.Instant.now()
                    val estimate = pickupDeadlineEstimate(now, pickupStart, pickupEnd, expiration, holdMinutes)
                    Text(
                        holdEstimateText(estimate, pickupStart, pickupEnd, expiration, now),
                        style = SaluText.Supporting,
                        color = SaluColors.DarkGreen
                    )
                }
                Text(
                    "If it isn't marked as picked up by then, the reservation expires and the food goes back on the listing.",
                    style = SaluText.Meta,
                    color = SaluColors.Gray
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 2000) note = it },
                label = {
                    Text(
                        if (donorName.isNullOrBlank()) {
                            "Message to the $donorRole (optional)"
                        } else {
                            "Message to $donorName (optional)"
                        }
                    )
                },
                supportingText = {
                    Text("Sent in your chat with the $donorRole after the reservation is made.")
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                shape = SaluRadius.Control,
                minLines = 2,
                maxLines = 5,
                colors = saluFieldColors()
            )

            if (failure != null) {
                InlineNotice(
                    text = failure,
                    icon = Icons.Outlined.ErrorOutline,
                    tone = NoticeTone.Error
                )
            }

            ActionButton(
                text = if (saving) "Reserving…" else "Confirm reservation",
                enabled = !saving,
                loading = saving,
                onClick = { onConfirm(note, holdMinutes) }
            )
        }
    }
}

/**
 * For full-screen dialog pages with text boxes. The dialog draws edge to edge,
 * so the page receives the keyboard's size and imePadding() (or
 * safeDrawingPadding()) keeps the focused text box above it. Each page pads
 * its header for the status bar and its bottom for the navigation bar, and
 * calls [DarkSystemBarIcons].
 */
internal val EdgeToEdgeDialog = DialogProperties(
    usePlatformDefaultWidth = false,
    decorFitsSystemWindows = false
)

/** Dark status-bar and navigation-bar icons over a light edge-to-edge dialog page. */
@Composable
internal fun DarkSystemBarIcons() {
    val view = LocalView.current
    LaunchedEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}

/**
 * Full-screen page with a Back arrow, a title and an optional line under it,
 * shared by the pages above, ReviewsAndReports.kt and Activity's pages.
 */
@Composable
internal fun ActionPage(
    heading: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onBack,
        properties = EdgeToEdgeDialog
    ) {
        DarkSystemBarIcons()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SaluColors.Cream
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
            ) {
                SaluTopBar(title = heading, onBack = onBack, subtitle = subtitle)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
internal fun ActionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    SaluCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun ActionLine(label: String, value: String, emphasis: Boolean = false) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = SaluText.Supporting,
            color = SaluColors.Gray,
            modifier = Modifier.weight(0.42f)
        )

        Text(
            value,
            style = if (emphasis) SaluText.Price else SaluText.Body.copy(fontWeight = FontWeight.Medium),
            color = SaluColors.DarkGreen,
            modifier = Modifier.weight(0.58f)
        )
    }
}

/** The page's main action: the shared green button. */
@Composable
internal fun ActionButton(
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    SaluPrimaryButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        loading = loading,
        icon = icon
    )
}
