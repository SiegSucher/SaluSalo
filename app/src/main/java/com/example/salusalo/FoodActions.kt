package com.example.salusalo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.material.icons.Icons
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
import com.example.salusalo.model.reviewCountText
import com.example.salusalo.viewmodel.ReserveResult
import kotlinx.coroutines.CancellationException

private val ActionGreen = Color(0xFF28754A)
private val ActionCream = Color(0xFFF8F7EF)
private val ActionPale = Color(0xFFE7F1E5)
private val ActionDark = Color(0xFF203E2C)

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
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            foodTitle,
                            fontWeight = FontWeight.Bold,
                            color = ActionDark
                        )

                        Text(
                            provider,
                            fontSize = 12.sp,
                            color = ActionGreen
                        )

                        Text(
                            price,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        when (action) {
            "location" -> {
                ActionCard {
                    Text(
                        "Pickup area",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ActionDark
                    )

                    Text(address)

                    if (pickupPin != null && distanceMeters != null) {
                        Text(
                            "About ${distanceLabel(distanceMeters)} from you in a straight line; " +
                                    "the route may be longer.",
                            fontSize = 12.sp,
                            color = ActionGreen
                        )
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
                            .clip(RoundedCornerShape(18.dp))
                    )

                    ActionButton(
                        text = "Directions",
                        onClick = {
                            if (!openDirections(context, pickupPin)) {
                                mapError = "No maps app is available on this device."
                            }
                        }
                    )
                } else {
                    ActionButton(
                        text = "Search area in Maps",
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
                    Text(
                        mapError,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                ActionCard {
                    Text(
                        "Pickup instructions",
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        if (pickupPin != null) {
                            "The pin shows where the provider hands over the food. " +
                                    "If you can't find it, ask them in the chat."
                        } else {
                            "Ask the provider for the exact meeting " +
                                    "point and collection instructions."
                        },
                        fontSize = 13.sp
                    )

                    ActionButton(
                        text = "Ask for exact location",
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
                    Text(
                        provider.take(1).uppercase(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(ActionPale)
                            .padding(20.dp),
                        color = ActionGreen,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        provider,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = ActionDark
                    )

                    Text(
                        if (donation) "Food donor" else "Food seller",
                        color = ActionGreen
                    )

                    if (verified) {
                        Surface(shape = RoundedCornerShape(20.dp), color = ActionPale) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Verified,
                                    contentDescription = null,
                                    tint = ActionGreen,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Email verified", color = ActionGreen, fontSize = 12.sp)
                            }
                        }
                    }

                    Text(
                        if (average != null && count > 0) {
                            "★ ${formatRatingAverage(average)} • ${reviewCountText(count)}"
                        } else {
                            "No reviews yet"
                        },
                        color = if (count > 0) RatingAmber else ActionDark,
                        fontWeight = FontWeight.SemiBold
                    )

                    ActionButton(
                        text = if (donation) "Message donor" else "Message seller",
                        onClick = { onMessage("") }
                    )
                }

                val loaded = reviews
                if (count > 0 || reviewsFailed || !loaded.isNullOrEmpty()) {
                    Text(
                        "Reviews",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    ActionCard {
                        when {
                            loaded == null && reviewsFailed -> Text(
                                "Reviews could not be loaded. Check your internet connection and try again.",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                            loaded == null -> Text("Loading reviews…", fontSize = 13.sp)
                            else -> {
                                loaded.forEachIndexed { index, review ->
                                    if (index > 0) HorizontalDivider()
                                    ReviewRow(review)
                                }
                                if (count > loaded.size && loaded.size >= DONOR_REVIEWS_LIMIT) {
                                    Text(
                                        "Showing the latest ${loaded.size} reviews.",
                                        fontSize = 12.sp,
                                        color = ActionDark
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                ActionCard {
                    ListingPhoto(
                        imageUrl = imageUrl,
                        hasPhoto = hasPhoto,
                        contentDescription = foodTitle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )

                    Text(
                        foodTitle,
                        fontWeight = FontWeight.Bold
                    )

                    Text("$price • $status")

                    ActionButton(
                        text = "Return to food details",
                        onClick = onClose
                    )
                }

                // Nobody reports themself.
                if (!isOwnListing) {
                    OutlinedButton(
                        onClick = { reporting = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            "Report " + (donorName ?: if (donation) "this donor" else "this seller"),
                            color = MaterialTheme.colorScheme.error
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
    saving: Boolean,
    result: ReserveResult?,
    onConfirm: (note: String) -> Unit,
    onMessageDonor: (reservationId: String) -> Unit,
    // Back or Done on the receipt: closes the receipt and the details page.
    onFinished: () -> Unit,
    // Back on the review page: returns to the details page.
    onBack: () -> Unit
) {
    val priced = unitPrice > 0
    val donorRole = if (priced) "seller" else "donor"
    // Never put the "unavailable" placeholder into a sentence.
    val donorLabel = donorName?.takeIf { it.isNotBlank() } ?: "the $donorRole"
    var note by rememberSaveable(foodPostId) { mutableStateOf("") }

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
                    .clip(RoundedCornerShape(12.dp))
            )

            Text(
                foodTitle,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = ActionDark
            )

            donorName?.takeIf { it.isNotBlank() }?.let {
                Text("From $it", fontSize = 13.sp, color = ActionGreen)
            }
        }

        if (reserved != null) {
            val reservation = reserved.reservation
            val paid = reservation.unitPrice > 0
            ActionCard {
                Text(
                    "You reserved ${reservation.quantity} $unit of $foodTitle.",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ActionDark
                )

                Text(
                    if (paid) {
                        "Pay ${formatPeso(reservationTotal(reservation.unitPrice, reservation.quantity))} at pickup."
                    } else {
                        "Free — nothing to pay."
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ActionGreen
                )

                ActionLine("Pickup location", pickupLocation)
                ActionLine("Pickup window", pickupWindow)

                when (reserved.noteSent) {
                    true -> Text(
                        if (donorName.isNullOrBlank()) {
                            "Your message was sent to the $donorRole."
                        } else {
                            "Your message was sent to $donorName."
                        },
                        fontSize = 13.sp
                    )
                    false -> Text(
                        "Your message could not be sent. You can send it from the chat.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                    null -> Unit
                }

                Text(
                    "You'll find this reservation in Activity → My Reservations.",
                    fontSize = 13.sp
                )
            }

            ActionButton(
                text = if (paid) "Message seller" else "Message donor",
                onClick = { onMessageDonor(reservation.id) }
            )

            OutlinedButton(
                onClick = onFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Done", color = ActionGreen)
            }
        } else {
            ActionCard {
                ActionLine("Reserving as", profileName?.takeIf { it.isNotBlank() } ?: "—")
                ActionLine("Quantity", "$quantity $unit")
                ActionLine("Pickup location", pickupLocation)
                ActionLine("Pickup window", pickupWindow)
                ActionLine("Handover", handover)
                ActionLine("Expires", expires)

                HorizontalDivider()

                if (priced) {
                    ActionLine("Price per unit", formatPeso(unitPrice))
                    ActionLine("Total", formatPeso(reservationTotal(unitPrice, quantity)))
                    Text(
                        "Paid at pickup. The app takes no payments.",
                        fontSize = 12.sp,
                        color = ActionDark
                    )
                } else {
                    ActionLine("Price", "Free")
                }
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
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
                maxLines = 5
            )

            if (failure != null) {
                Text(
                    failure,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
            }

            ActionButton(
                text = if (saving) "Reserving…" else "Confirm reservation",
                enabled = !saving,
                onClick = { onConfirm(note) }
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

/** Full-screen page with a Back button and a title, shared by the pages above and ReviewsAndReports.kt. */
@Composable
internal fun ActionPage(
    heading: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onBack,
        properties = EdgeToEdgeDialog
    ) {
        DarkSystemBarIcons()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ActionCream
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
            ) {
                Surface(
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onBack) {
                            Text("Back", color = ActionGreen)
                        }

                        Column(Modifier.weight(1f)) {
                            Text(
                                heading,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = ActionDark
                            )

                            Text(
                                "Salu-Salo",
                                fontSize = 11.sp,
                                color = ActionGreen
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun ActionLine(label: String, value: String) {
    Column {
        Text(
            label,
            fontSize = 11.sp,
            color = ActionGreen
        )

        Text(
            value,
            fontSize = 14.sp,
            color = ActionDark,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun ActionButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ActionGreen
        )
    ) {
        Text(text)
    }
}
