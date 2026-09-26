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
import java.util.Locale
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.reservationTotal
import coil3.compose.AsyncImage

private val ActionGreen = Color(0xFF28754A)
private val ActionCream = Color(0xFFF8F7EF)
private val ActionPale = Color(0xFFE7F1E5)
private val ActionDark = Color(0xFF203E2C)

@Composable
fun FoodActionsDialog(
    action: String,
    foodTitle: String,
    provider: String,
    rating: String = "",
    photo: Int,
    price: String,
    quantity: Int,
    unit: String,
    status: String,
    pickupChoice: String,
    onPickupSelected: (String) -> Unit,
    onClose: () -> Unit,
    // Opens the real Phase 4 chat with a prefilled message. When null, the
    // links below fall back to the local demo chat.
    onMessage: ((String) -> Unit)? = null,
    imageUrl: String? = null,
    pickupLocation: String? = null,
    claimSubmitting: Boolean = false,
    claimReservationId: String? = null,
    claimQuantity: Int? = null,
    claimUnitPrice: Double = 0.0,
    claimError: String? = null,
    expirationDate: String? = null,
    claimDeadline: String? = null,
    claimSavedStatus: String? = null,
    lockedPickupMinutes: Int? = null,
    onConfirmClaim: ((Int) -> Unit)? = null,
    onViewRequests: (() -> Unit)? = null
) {
    var selectedPickupMinutes by rememberSaveable(action) { mutableStateOf(30) }
    var reviewNow by remember { mutableStateOf(java.time.Instant.now()) }
    LaunchedEffect(action) {
        while (true) {
            reviewNow = java.time.Instant.now()
            kotlinx.coroutines.delay(1000)
        }
    }
    val foodExpiry = com.example.salusalo.model.parseTimestamp(expirationDate)
    val duration = lockedPickupMinutes ?: selectedPickupMinutes
    val eligibleDurations = listOf(30, 60, 120).filter { minutes ->
        foodExpiry?.let { !reviewNow.plusSeconds(minutes * 60L).isAfter(it) } == true
    }
    val context = LocalContext.current
    val address = pickupLocation?.takeIf { it.isNotBlank() } ?: "Ask the provider for the pickup location"
    val donation = price.equals("Free", ignoreCase = true)
    val claimSuccessTitle = if (claimSavedStatus != "RESERVED") "Request already recorded"
        else if (claimUnitPrice > 0) "Reservation confirmed" else "Claim confirmed"

    var page by rememberSaveable(action) {
        mutableStateOf(
            when (action) {
                "allergens", "storage", "message" -> "chat"
                else -> action
            }
        )
    }

    var draft by rememberSaveable(action) {
        mutableStateOf(
            when (action) {
                "allergens" ->
                    "Hi! What allergens does $foodTitle contain?"

                "storage" ->
                    "Hi! How should I store $foodTitle after pickup?"

                else -> ""
            }
        )
    }

    var messages by rememberSaveable {
        mutableStateOf(arrayListOf<String>())
    }

    var pickup by rememberSaveable {
        mutableStateOf(pickupChoice)
    }

    var note by rememberSaveable {
        mutableStateOf("")
    }

    var confirmed by rememberSaveable {
        mutableStateOf(false)
    }

    var mapError by remember {
        mutableStateOf("")
    }

    // Demo assumption: the displayed price is for one selectable unit.
    val unitPrice = price
        .removePrefix("₱")
        .replace(",", "")
        .trim()
        .toBigDecimalOrNull()

    val total = unitPrice?.multiply(quantity.toBigDecimal())

    val formattedTotal = if (donation) {
        "Free"
    } else {
        total?.let {
            String.format(Locale.US, "₱%,.2f", it)
        } ?: "Confirm with seller"
    }

    val heading = when (page) {
        "chat" -> provider
        "pickup" -> "Choose pickup time"
        "location" -> "Pickup location"
        "provider" -> if (donation) "Donor" else "Seller"
        "reserve" -> "Review reservation"
        "checkout" -> if (donation && claimReservationId != null) claimSuccessTitle else if (donation) "Review claim" else "Review purchase"
        else -> "Food details"
    }

    Dialog(
        onDismissRequest = { if (!claimSubmitting) onClose() },
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
                        TextButton(onClick = onClose, enabled = !claimSubmitting) {
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (page != "provider") {
                        ActionCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ActionFoodPhoto(
                                    photo = photo,
                                    imageUrl = imageUrl,
                                    contentDescription = foodTitle,
                                    contentScale = ContentScale.Crop,
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

                    when (page) {
                        "chat" -> {
                            Text(
                                "Demo conversation • messages are not delivered",
                                fontSize = 12.sp,
                                color = ActionGreen
                            )

                            if (messages.isEmpty()) {
                                ActionCard {
                                    Text(
                                        "Ask about this listing",
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        "Check ingredients, storage, or pickup " +
                                                "arrangements with $provider.",
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            messages.forEach { message ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        modifier = Modifier.widthIn(max = 280.dp),
                                        color = ActionPale,
                                        shape = RoundedCornerShape(
                                            topStart = 18.dp,
                                            topEnd = 18.dp,
                                            bottomStart = 18.dp,
                                            bottomEnd = 4.dp
                                        )
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text(
                                                message,
                                                color = ActionDark
                                            )

                                            Spacer(Modifier.height(5.dp))

                                            Text(
                                                "Local demo",
                                                fontSize = 10.sp,
                                                color = ActionGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        "pickup" -> {
                            ActionCard {
                                ActionLine("Listing deadline", status)

                                Text(
                                    "Choose a preferred arrival time. These " +
                                            "sample options are not confirmed " +
                                            "provider availability.",
                                    fontSize = 13.sp
                                )
                            }

                            PickupOptions(
                                selected = pickup,
                                onSelect = {
                                    pickup = it
                                    confirmed = false
                                }
                            )

                            ActionButton(
                                text = "Save preferred pickup time",
                                enabled = pickup.isNotBlank(),
                                onClick = {
                                    onPickupSelected(pickup)
                                    confirmed = true
                                }
                            )

                            if (confirmed) {
                                ActionCard {
                                    Text(
                                        "Preference saved",
                                        fontWeight = FontWeight.Bold,
                                        color = ActionGreen
                                    )

                                    Text(
                                        "$pickup. This does not reserve food.",
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

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
                                    onClick = {
                                        val question = "Hi! Where exactly can I pick up $foodTitle?"
                                        if (onMessage != null) {
                                            onMessage(question)
                                        } else {
                                            draft = question
                                            page = "chat"
                                        }
                                    }
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
                                    onClick = {
                                        if (onMessage != null) {
                                            onMessage("")
                                        } else {
                                            draft = ""
                                            page = "chat"
                                        }
                                    }
                                )
                            }

                            Text(
                                "Listing from this provider",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )

                            ActionCard {
                                ActionFoodPhoto(
                                    photo = photo,
                                    imageUrl = imageUrl,
                                    contentDescription = foodTitle,
                                    contentScale = ContentScale.Crop,
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

                        "reserve", "checkout" -> {
                            if (donation && onConfirmClaim != null) {
                                if (claimReservationId != null) {
                                    ActionCard {
                                        Text(claimSuccessTitle, fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold, color = ActionGreen)
                                        Text(if (claimSavedStatus == "RESERVED") "Your food is reserved until the pickup deadline."
                                            else "This request has already been processed. Open Activity to see its latest status.")
                                        ActionLine("Food", foodTitle)
                                        ActionLine("Quantity", "${claimQuantity ?: quantity} $unit")
                                        ActionLine("Status", when (claimSavedStatus) {
                                            "RESERVED" -> "Awaiting pickup"
                                            "CLAIMED" -> "Collected"
                                            else -> "Hold ended"
                                        })
                                        claimDeadline?.let { ActionLine("Pick up by", pickupTimeText(it)) }
                                        if (claimUnitPrice > 0) {
                                            ActionLine("Amount due", formatPeso(reservationTotal(claimUnitPrice, claimQuantity ?: quantity)))
                                            Text("The saved reservation has a price. Pay at pickup. You can review or cancel it in My Requests.")
                                        } else {
                                            ActionLine("Cost", "Free")
                                        }
                                        ActionLine("Pickup location", address)
                                        Text("Contact the provider for pickup details. The provider confirms collection at handover before the deadline.")
                                        ActionButton(text = "View in Activity", onClick = { onViewRequests?.invoke() })
                                    }
                                } else {
                                    ActionCard {
                                        ActionLine("Food", foodTitle)
                                        ActionLine("Quantity", "$quantity $unit")
                                        ActionLine("Pickup location", address)
                                        ActionLine("Deadline", status)
                                        ActionLine("Cost", "Free")
                                        Text("Choose how soon you can collect. Your hold starts when the server accepts your claim.")
                                        Text("Pickup within", fontWeight = FontWeight.Bold)
                                        listOf(30, 60, 120).forEach { minutes ->
                                            val label = when (minutes) { 30 -> "30 minutes"; 60 -> "1 hour"; else -> "2 hours" }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                RadioButton(selected = duration == minutes,
                                                    enabled = !claimSubmitting && lockedPickupMinutes == null && minutes in eligibleDurations,
                                                    onClick = { selectedPickupMinutes = minutes })
                                                Text(label + if (minutes !in eligibleDurations) " • past food expiry" else "")
                                            }
                                        }
                                        if (duration in eligibleDurations) {
                                            ActionLine("Estimated pickup deadline", pickupTimeText(reviewNow.plusSeconds(duration * 60L).toString()))
                                        } else {
                                            Text("No valid selection. Choose a shorter duration or another listing.", color = Color(0xFFB3261E))
                                        }
                                        Text("The exact deadline appears after confirmation. Missed pickups release the hold; food that has expired stays unavailable.", fontSize = 12.sp)
                                    }
                                    claimError?.let { error ->
                                        ActionCard {
                                            Text(error, color = Color(0xFFB3261E))
                                            TextButton(onClick = { onViewRequests?.invoke() }) {
                                                Text("Check My Requests")
                                            }
                                        }
                                    }
                                    Button(
                                        onClick = { onConfirmClaim(duration) },
                                        enabled = !claimSubmitting && quantity > 0 && (duration in eligibleDurations || lockedPickupMinutes != null),
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                                    ) {
                                        Text(if (claimSubmitting) "Confirming…" else "Confirm Claim")
                                    }
                                }
                            } else if (confirmed) {
                                ActionCard {
                                    Text(
                                        "Preview complete",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ActionGreen
                                    )

                                    Text(
                                        when {
                                            page == "reserve" ->
                                                "You reviewed a reservation."

                                            donation ->
                                                "You reviewed a donation claim."

                                            else ->
                                                "You reviewed a purchase."
                                        }
                                    )

                                    ActionLine("Food", foodTitle)
                                    ActionLine("Quantity", "$quantity $unit")
                                    ActionLine("Preferred pickup", pickup)

                                    Text(
                                        "No request was sent, no stock was " +
                                                "held, and no payment was taken.",
                                        fontSize = 13.sp
                                    )

                                    ActionButton(
                                        text = "Back to food details",
                                        onClick = onClose
                                    )
                                }
                            } else {
                                ActionCard {
                                    ActionLine("Requesting as", "Mika")
                                    ActionLine("Quantity", "$quantity $unit")
                                    ActionLine("Pickup area", address)
                                    ActionLine("Deadline", status)

                                    if (!donation) {
                                        ActionLine("Price per unit", price)

                                        Text(
                                            "Demo pricing assumes this price " +
                                                    "applies to one selectable unit.",
                                            fontSize = 11.sp
                                        )
                                    }

                                    HorizontalDivider()

                                    ActionLine(
                                        if (donation) "Cost" else "Estimated total",
                                        formattedTotal
                                    )

                                    if (!donation) {
                                        ActionLine(
                                            "Payment",
                                            "Pay at pickup (demo)"
                                        )
                                    }
                                }

                                Text(
                                    "Preferred pickup",
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "Sample preferences only. The provider " +
                                            "must confirm a time within the deadline.",
                                    fontSize = 12.sp
                                )

                                PickupOptions(
                                    selected = pickup,
                                    onSelect = {
                                        pickup = it
                                        onPickupSelected(it)
                                    }
                                )

                                OutlinedTextField(
                                    value = note,
                                    onValueChange = { note = it },
                                    label = { Text("Note to provider (optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    minLines = 2
                                )

                                ActionButton(
                                    text = when {
                                        page == "reserve" ->
                                            "Preview reservation"

                                        donation ->
                                            "Preview claim"

                                        else ->
                                            "Preview purchase"
                                    },
                                    enabled = pickup.isNotBlank() &&
                                            (donation || unitPrice != null),
                                    onClick = { confirmed = true }
                                )
                            }
                        }
                    }
                }

                if (page == "chat") {
                    Surface(
                        color = Color.White,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type your question…") },
                                shape = RoundedCornerShape(18.dp),
                                maxLines = 4
                            )

                            Button(
                                enabled = draft.isNotBlank(),
                                onClick = {
                                    messages = ArrayList(messages).apply {
                                        add(draft.trim())
                                    }
                                    draft = ""
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ActionGreen
                                )
                            ) {
                                Text("Send\nDemo", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
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

@Composable
private fun PickupOptions(
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            "Within 15 minutes",
            "Within 30 minutes",
            "Agree on a time in chat"
        ).forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        option,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ActionPale,
                    selectedLabelColor = ActionGreen
                )
            )
        }
    }
}

@Composable
private fun ActionFoodPhoto(
    photo: Int,
    imageUrl: String?,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    if (imageUrl.isNullOrBlank()) {
        Image(
            painter = painterResource(photo),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        var loading by remember(imageUrl) { mutableStateOf(true) }
        var failed by remember(imageUrl) { mutableStateOf(false) }

        Box(
            modifier = modifier.background(ActionPale),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onLoading = {
                    loading = true
                    failed = false
                },
                onSuccess = {
                    loading = false
                    failed = false
                },
                onError = {
                    loading = false
                    failed = true
                }
            )

            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = ActionGreen,
                    strokeWidth = 2.dp
                )
            }

            if (failed) {
                Text(
                    text = "Photo unavailable",
                    modifier = Modifier.padding(4.dp),
                    color = ActionDark,
                    fontSize = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private fun pickupTimeText(value: String): String {
    val instant = com.example.salusalo.model.parseTimestamp(value) ?: return value
    return java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
        .withZone(java.time.ZoneId.systemDefault()).format(instant)
}
