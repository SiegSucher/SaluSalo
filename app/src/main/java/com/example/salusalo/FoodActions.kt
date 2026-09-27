package com.example.salusalo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.reservationTotal
import com.example.salusalo.viewmodel.ReserveResult

private val ActionGreen = Color(0xFF28754A)
private val ActionCream = Color(0xFFF8F7EF)
private val ActionPale = Color(0xFFE7F1E5)
private val ActionDark = Color(0xFF203E2C)

/**
 * The Pickup location and Donor/Seller pages of a listing. Questions go to the
 * real chat through [onMessage] (with a prefilled draft).
 */
@Composable
fun FoodActionsDialog(
    action: String,
    foodTitle: String,
    provider: String,
    photo: Int,
    // The listing's pickup_location and its signed cover URL (null if none).
    pickupLocation: String,
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
                    ListingCover(
                        imageUrl = imageUrl,
                        photo = photo,
                        description = foodTitle,
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
                }

                ActionButton(
                    text = "Search area in Maps",
                    onClick = {
                        // A text search for the address as entered;
                        // listings have no map pin.
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
                        "Ask the provider for the exact meeting " +
                                "point and collection instructions.",
                        fontSize = 13.sp
                    )

                    ActionButton(
                        text = "Ask for exact location",
                        onClick = { onMessage("Hi! Where exactly can I pick up $foodTitle?") }
                    )
                }
            }

            "provider" -> {
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

                    ActionButton(
                        text = if (donation) "Message donor" else "Message seller",
                        onClick = { onMessage("") }
                    )
                }

                Text(
                    "Listing from this provider",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                ActionCard {
                    ListingCover(
                        imageUrl = imageUrl,
                        photo = photo,
                        description = foodTitle,
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
    photo: Int,
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
            ListingCover(
                imageUrl = imageUrl,
                photo = photo,
                description = foodTitle,
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

/** Full-screen page with a Back button and a title, shared by the pages above. */
@Composable
private fun ActionPage(
    heading: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
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

/**
 * The listing's signed cover with the same fallbacks as AvailableFoodCard: the
 * sample drawable when the listing has no photo, "Image unavailable" when it
 * can't be loaded.
 */
@Composable
private fun ListingCover(
    imageUrl: String?,
    photo: Int,
    description: String,
    modifier: Modifier
) {
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    if (imageUrl != null && !imageFailed) {
        AsyncImage(
            model = imageUrl,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            onError = { imageFailed = true }
        )
    } else if (imageUrl == null) {
        Image(
            painter = painterResource(photo),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(ActionPale),
            contentAlignment = Alignment.Center
        ) { Text("Image unavailable", fontSize = 12.sp) }
    }
}

@Composable
private fun ActionCard(
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
private fun ActionButton(
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
