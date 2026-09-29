package com.example.salusalo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostDraft
import com.example.salusalo.model.FoodPostEdit
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.GeoPin
import com.example.salusalo.model.pickupPin
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.viewmodel.EditResult
import com.example.salusalo.model.ListingHandover
import com.example.salusalo.model.isExpired
import com.example.salusalo.model.isPickupStartAllowed
import com.example.salusalo.viewmodel.FoodViewModel
import com.example.salusalo.viewmodel.PostResult
import java.math.BigDecimal
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Keeps the pickup pin across rotation: [latitude, longitude], or nothing. */
private val PinSaver = listSaver<GeoPin?, Double>(
    save = { pin -> if (pin == null) emptyList() else listOf(pin.latitude, pin.longitude) },
    restore = { values -> if (values.size == 2) GeoPin(values[0], values[1]) else null }
)

private val FoodCategories = listOf(
    "Bread & pastries",
    "Vegetables",
    "Fruit",
    "Cooked meals",
    "Packaged food",
    "Other"
)

private val FoodUnits = listOf("pcs", "packs", "meals", "boxes", "kg")

@Composable
fun PostScreen(
    onBack: () -> Unit,
    foodViewModel: FoodViewModel,
    // After a listing is published: start again with an empty form. Null hides the button.
    onPostAnother: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val myPosts by foodViewModel.myPosts.collectAsStateWithLifecycle()
    val postResult by foodViewModel.postResult.collectAsStateWithLifecycle()
    val postingDraftId by foodViewModel.postingDraftId.collectAsStateWithLifecycle()
    val publishedDraftIds by foodViewModel.publishedDraftIds.collectAsStateWithLifecycle()
    val backendSaving by foodViewModel.saving.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { foodViewModel.loadMyPosts() }

    var donation by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var pickupPin by rememberSaveable(stateSaver = PinSaver) { mutableStateOf<GeoPin?>(null) }
    var distribution by rememberSaveable {
        mutableStateOf(ListingHandover.MEET_AT_PICKUP_LOCATION.label)
    }
    var allergens by rememberSaveable { mutableStateOf("") }
    var storage by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    var expiry by rememberSaveable { mutableStateOf(0L) }
    var pickupStart by rememberSaveable { mutableStateOf(0L) }
    var pickupEnd by rememberSaveable { mutableStateOf(0L) }

    var photos by rememberSaveable {
        mutableStateOf(arrayListOf<String>())
    }

    var error by rememberSaveable { mutableStateOf("") }
    var info by rememberSaveable { mutableStateOf("") }
    var checking by rememberSaveable { mutableStateOf(false) }
    var savedId by rememberSaveable { mutableStateOf("") }
    // This form's listing ID, kept across rotation and retries: posting again
    // after a failure can't publish it twice, and results of other forms are
    // never shown here.
    val draftId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    val posting = postingDraftId == draftId
    var showLocal by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<com.example.salusalo.model.FoodPost?>(null) }
    var editing by remember { mutableStateOf<com.example.salusalo.model.FoodPost?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { selected ->
        val accepted = arrayListOf<String>()
        var failed = false

        selected.take((4 - photos.size).coerceAtLeast(0)).forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                accepted.add(uri.toString())
            } catch (_: SecurityException) {
                failed = true
            }
        }

        photos = ArrayList((photos + accepted).distinct())

        if (failed) {
            error = "One photo could not be kept. Please choose it from another folder."
        }
    }

    fun chooseDateTime(current: Long, onSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        if (current > 0L) calendar.timeInMillis = current

        DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(year, month, day)

                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        onSelected(calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun validate(): String {
        val count = quantity.toIntOrNull()
        val amount = price.toBigDecimalOrNull()
        val now = System.currentTimeMillis()

        return when {
            photos.isEmpty() -> "Add at least one food photo."
            name.isBlank() -> "Enter the food name."
            textLength(name) > 160 -> "The food name must be 160 characters or fewer."
            category.isBlank() -> "Select a category."
            count == null || count <= 0 -> "Enter a whole quantity greater than zero."
            // The database refuses more (food_posts_check_values).
            count > 10_000 -> "Enter a quantity of at most 10,000."
            unit.isBlank() -> "Select a unit."
            !donation && (
                    amount == null ||
                            amount.signum() <= 0 ||
                            amount.scale() > 2
                    ) -> "Enter a price greater than zero, with up to two decimal places."
            // Matches the food_posts price check in the database.
            !donation && amount != null && amount > BigDecimal("99999.99") -> "Enter a price of at most ₱99,999.99."
            location.isBlank() -> "Enter the pickup location."
            textLength(location) > 300 -> "The pickup location must be 300 characters or fewer."
            pickupPin == null -> "Place the pickup pin on the map."
            expiry <= now -> "Choose a future expiration date and time."
            pickupStart == 0L -> "Choose when pickup starts, or tap Start now."
            !isPickupStartAllowed(pickupStart, now) -> PICKUP_START_PAST
            pickupEnd <= pickupStart -> "Pickup must end after it starts."
            pickupEnd > expiry -> "Pickup must end on or before expiration."
            allergens.isBlank() -> ALLERGENS_MISSING
            textLength(allergens) > 300 -> "Allergens must be 300 characters or fewer."
            textLength(storage) > 300 -> "Storage notes must be 300 characters or fewer."
            textLength(description) > 2000 -> "The description must be 2,000 characters or fewer."
            else -> ""
        }
    }

    // Published, also when Home showed the result while this form was not open.
    LaunchedEffect(publishedDraftIds) {
        if (draftId in publishedDraftIds) savedId = draftId
    }
    LaunchedEffect(postResult) {
        val result = postResult
        if (result != null && result.draftId == draftId) {
            if (result is PostResult.Failed) error = result.message
            foodViewModel.clearPostResult()
        }
    }
    val postScroll = rememberScrollState()
    // A new error scrolls up to the button area so it is seen.
    LaunchedEffect(error) {
        if (error.isNotBlank()) postScroll.animateScrollTo(postScroll.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaluColors.Cream)
    ) {
        // Post is a main tab: the bottom bar and Back lead away from it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
        ) {
            Text(
                "Create listing",
                style = SaluText.ScreenTitle,
                color = SaluColors.DarkGreen,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Post surplus food to help others",
                style = SaluText.Supporting,
                color = SaluColors.Gray
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(postScroll)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (savedId.isNotBlank()) {
                SaluCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconBubble(
                            Icons.Outlined.CheckCircle,
                            tint = SaluColors.Green,
                            background = SaluColors.IconGreen,
                            size = 52.dp,
                            iconSize = 28.dp
                        )
                        Text(
                            "Listing published",
                            style = MaterialTheme.typography.titleLarge,
                            color = SaluColors.Green,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(name, style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
                        Text(
                            "$quantity $unit • ${if (donation) "Free" else "₱$price per unit"}",
                            style = SaluText.Body,
                            color = SaluColors.DarkGreen
                        )
                        Text("Pickup: $location", style = SaluText.Body, color = SaluColors.DarkGreen)
                        Text("Photos: ${photos.size}", style = SaluText.Supporting, color = SaluColors.Gray)
                        Text("Reference: ${savedId.take(8)}", style = SaluText.Supporting, color = SaluColors.Gray)
                        Text(
                            "Your listing is now published to the community feed.",
                            style = SaluText.Supporting,
                            color = SaluColors.Gray
                        )
                        Spacer(Modifier.height(4.dp))
                        SaluPrimaryButton(text = "Back to home", onClick = onBack)
                        if (onPostAnother != null) {
                            SaluSecondaryButton(text = "Post another listing", onClick = onPostAnother)
                        }
                    }
                }
            } else {
                ListingTypeSwitch(donation = donation, onChange = { donation = it })

                Text(
                    "Fields marked * are required.",
                    style = SaluText.Meta,
                    color = SaluColors.Gray
                )

                FormSection(
                    title = "Food",
                    subtitle = "What you're sharing, and how much",
                    icon = Icons.Outlined.RiceBowl
                ) {
                    PhotoPicker(
                        photos = photos,
                        onAdd = { photoPicker.launch(arrayOf("image/*")) },
                        onRemove = { uri -> photos = ArrayList(photos.filter { it != uri }) }
                    )

                    PostField(
                        label = "Food name",
                        value = name,
                        onChange = { name = it },
                        example = "e.g., Assorted bread loaves",
                        required = true
                    )

                    PostChoice(
                        label = "Category",
                        selected = category,
                        options = FoodCategories,
                        onSelect = { category = it },
                        required = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PostField(
                            label = "Quantity",
                            value = quantity,
                            onChange = { quantity = it },
                            modifier = Modifier.weight(1f),
                            example = "e.g., 10",
                            keyboard = KeyboardType.Number,
                            required = true
                        )
                        if (!donation) {
                            PostField(
                                label = "Price per unit",
                                value = price,
                                onChange = { price = it },
                                modifier = Modifier.weight(1f),
                                example = "25.00",
                                keyboard = KeyboardType.Decimal,
                                prefix = "₱",
                                required = true
                            )
                        }
                    }

                    PostChoice(
                        label = "Unit",
                        selected = unit,
                        options = FoodUnits,
                        onSelect = { unit = it },
                        required = true
                    )

                    if (donation) {
                        InlineNotice(
                            text = "Free donation: recipients pay nothing.",
                            icon = Icons.Outlined.VolunteerActivism,
                            tone = NoticeTone.Success
                        )
                    }

                    PostField(
                        label = "Description (optional)",
                        value = description,
                        onChange = { description = it },
                        example = "e.g., 10 individually packed bread rolls available today"
                    )
                }

                FormSection(
                    title = "Pickup",
                    subtitle = "Where and when people collect it",
                    icon = Icons.Outlined.LocationOn
                ) {
                    PostField(
                        label = "Pickup location / exact meeting point",
                        value = location,
                        onChange = { location = it },
                        example = "e.g., Main entrance of the barangay hall, Del Rosario, Balilihan",
                        hint = "Enter an address or landmark recipients can recognize.",
                        required = true
                    )

                    PickupPinPicker(
                        pin = pickupPin,
                        onPinChange = { pickupPin = it },
                        savedPin = null,
                        editing = false,
                        accent = SaluColors.Green
                    )

                    PostDateButton("Pickup starts", pickupStart, required = true, onNow = { pickupStart = currentMinute() }) {
                        chooseDateTime(pickupStart) { pickupStart = it }
                    }
                    PostDateButton("Pickup ends", pickupEnd, required = true) {
                        chooseDateTime(pickupEnd) { pickupEnd = it }
                    }

                    PostChoice(
                        label = "Handover",
                        selected = distribution,
                        options = ListingHandover.entries.map { it.label },
                        onSelect = { distribution = it }
                    )
                }

                FormSection(
                    title = "Food information",
                    subtitle = "Help people eat it safely",
                    icon = Icons.Outlined.WarningAmber
                ) {
                    PostField(
                        label = "Allergens",
                        value = allergens,
                        onChange = { allergens = it },
                        example = "e.g., Contains wheat, milk, and eggs",
                        hint = ALLERGENS_HINT,
                        required = true
                    )
                    PostField(
                        label = "Storage notes (optional)",
                        value = storage,
                        onChange = { storage = it },
                        example = "e.g., Keep refrigerated until pickup"
                    )
                    PostDateButton("Expiration", expiry, required = true) {
                        chooseDateTime(expiry) { expiry = it }
                    }
                }

                SaluCard(
                    modifier = Modifier.fillMaxWidth(),
                    color = SaluColors.PaleGreen,
                    border = false
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(
                                Icons.AutoMirrored.Outlined.FactCheck,
                                background = Color.White,
                                size = 40.dp,
                                iconSize = 22.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Check your listing",
                                    style = SaluText.FoodTitle,
                                    color = SaluColors.DarkGreen
                                )
                                Text(
                                    "See the price, pickup window and missing details before you post.",
                                    style = SaluText.Supporting,
                                    color = SaluColors.Gray
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        SaluSecondaryButton(text = "Check listing", onClick = { checking = true })
                    }
                }

                if (error.isNotBlank()) {
                    InlineNotice(
                        text = error,
                        icon = Icons.Outlined.ErrorOutline,
                        tone = NoticeTone.Error
                    )
                }

                SaluPrimaryButton(
                    text = if (posting || backendSaving) "Posting…" else "Post listing",
                    loading = posting || backendSaving,
                    onClick = {
                        error = validate()
                        val pin = pickupPin

                        if (error.isBlank() && pin != null) {
                            // Ignored while another write runs; the button shows it.
                            foodViewModel.createPost(
                                draftId,
                                FoodPostDraft(
                                    name = name,
                                    description = description,
                                    category = category,
                                    quantity = quantity.toInt(),
                                    unit = unit,
                                    pickupLocation = location,
                                    expirationDate = Instant.ofEpochMilli(expiry).toString(),
                                    // validate() has checked the price; a donation is free.
                                    price = if (donation) 0.0 else price.toBigDecimal().toDouble(),
                                    pickupStart = Instant.ofEpochMilli(pickupStart).toString(),
                                    pickupEnd = Instant.ofEpochMilli(pickupEnd).toString(),
                                    handover = ListingHandover.fromLabel(distribution),
                                    allergens = allergens,
                                    storageNotes = storage,
                                    pickupPin = pin
                                ),
                                photos.map { Uri.parse(it) }
                            )
                        }
                    }
                )

                Text(
                    "Review the food details and pickup arrangements before posting.",
                    style = SaluText.Meta,
                    color = SaluColors.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // This account's own posts, folded away under the form.
            SaluCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showLocal = !showLocal }
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .heightIn(min = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = SaluColors.Green)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "My posts (${myPosts.size})",
                        style = SaluText.FoodTitle,
                        color = SaluColors.DarkGreen,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (showLocal) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (showLocal) "Hide my posts" else "Show my posts",
                        tint = SaluColors.Gray
                    )
                }
            }

            AnimatedVisibility(visible = showLocal) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (myPosts.isEmpty()) {
                        Text(
                            "You have no food posts yet.",
                            style = SaluText.Body,
                            color = SaluColors.Gray
                        )
                    }

                    myPosts.forEach { post ->
                        MyPostCard(
                            post = post,
                            onDetails = { info = post.description ?: "No description provided." },
                            onEdit = { editing = post },
                            onDelete = { pendingDelete = post }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (checking) {
        val count = quantity.toIntOrNull()
        val amount = price.toBigDecimalOrNull()
        val now = System.currentTimeMillis()

        ActionPage(heading = "Check your listing", onBack = { checking = false }) {
            Text(
                "Based on the details you've entered so far. Nothing is posted until you tap Post listing.",
                style = SaluText.Body,
                color = SaluColors.Gray
            )

            CheckSection(
                "Price",
                when {
                    donation -> listOf("Free donation. Recipients will not pay for the food.")
                    amount != null && amount.signum() > 0 && amount.scale() <= 2 -> listOfNotNull(
                        "₱${amount.toPlainString()} per selected unit.",
                        if (count != null && count > 0) {
                            "Price for the full batch: ₱${amount.multiply(count.toBigDecimal()).toPlainString()}."
                        } else null
                    )
                    else -> listOf("Enter a valid price with up to two decimal places.")
                }
            )

            CheckSection(
                "Quantity",
                listOf(
                    if (count == null || count <= 0) "Enter a quantity greater than zero."
                    else "Available: $count ${unit.ifBlank { "units" }}."
                )
            )

            CheckSection(
                "Pickup window",
                when {
                    pickupStart == 0L || pickupEnd == 0L -> listOf("Select both pickup start and end times.")
                    // The same rule as Post Listing's check, so the two never disagree.
                    !isPickupStartAllowed(pickupStart, now) -> listOf(PICKUP_START_PAST)
                    pickupEnd <= pickupStart -> listOf("Pickup must end after it starts.")
                    else -> {
                        val minutes = (pickupEnd - pickupStart) / 60_000
                        listOfNotNull(
                            "Starts: ${postTime(pickupStart)}",
                            "Ends: ${postTime(pickupEnd)}",
                            "Collection window: ${minutes / 60} hr ${minutes % 60} min.",
                            if (minutes < 60) {
                                "This is a short collection window. Make the meeting point clear."
                            } else null
                        )
                    }
                }
            )

            CheckSection(
                "Expiration",
                when {
                    expiry == 0L -> listOf("Select the expiration date and time.")
                    expiry <= now -> listOf("The entered expiration time has already passed.")
                    pickupEnd > expiry -> listOf("Move the pickup end time before expiration.")
                    else -> listOfNotNull(
                        "Entered expiration: ${postTime(expiry)}",
                        if (pickupEnd > 0L && pickupEnd > pickupStart) {
                            "Pickup ends ${(expiry - pickupEnd) / 60_000} minute(s) before the entered expiration."
                        } else null
                    )
                }
            )

            CheckSection(
                "Location",
                listOf(location.ifBlank { "Add the exact pickup address or a recognizable landmark." })
            )

            CheckSection(
                "Details to review",
                listOfNotNull(
                    if (photos.isEmpty()) "• Add at least one food photo." else "• ${photos.size} photo(s) selected.",
                    if (allergens.isBlank()) "• Allergens are missing. Write \"None\" if there are none." else null,
                    if (storage.isBlank()) "• Storage instructions have not been provided." else null
                )
            )

            Text(
                "These are form checks and calculations, not AI predictions or a food-safety assessment.",
                style = SaluText.Meta,
                color = SaluColors.Gray
            )
        }
    }

    pendingDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete listing?") },
            text = { Text(DELETE_LISTING_TEXT) },
            confirmButton = {
                TextButton(enabled = !backendSaving, onClick = {
                    foodViewModel.deletePost(post)
                    pendingDelete = null
                }) { Text("Delete", color = SaluColors.Error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
    DeleteResultDialog(foodViewModel = foodViewModel)
    editing?.let { post ->
        EditListingDialog(
            post = post,
            foodViewModel = foodViewModel,
            onClose = { message ->
                editing = null
                if (message != null) info = message
            }
        )
    }
    if (info.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { info = "" },
            title = { Text("Listing information") },
            text = {
                Text(
                    info,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { info = "" }) {
                    Text("Close")
                }
            }
        )
    }
}

/** One of this account's posts under "My posts": status, details and its actions. */
@Composable
private fun MyPostCard(
    post: FoodPost,
    onDetails: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val expired = post.isExpired()
    SaluCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    post.name,
                    style = SaluText.FoodTitle,
                    color = SaluColors.DarkGreen,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                StatusBadge(listingStatus(post, expired), Modifier.padding(end = 8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${post.quantity} ${post.unit} • ${post.category}",
                style = SaluText.Supporting,
                color = SaluColors.Gray
            )
            Text(post.pickupLocation, style = SaluText.Meta, color = SaluColors.Gray)
            Text(
                "Expires: ${epochMillis(post.expirationDate).takeIf { it != 0L }?.let(::postTime) ?: "—"}",
                style = SaluText.Meta,
                color = SaluColors.Gray
            )
            Row {
                TextButton(onClick = onDetails) { Text("View details", color = SaluColors.Green) }
                if (!expired) {
                    TextButton(onClick = onEdit) { Text("Edit", color = SaluColors.Green) }
                }
                TextButton(onClick = onDelete) { Text("Delete", color = SaluColors.Error) }
            }
        }
    }
}

/** A listing's status as a badge; expiry is worked out from the date, as the server never sets it. */
internal fun listingStatus(post: FoodPost, expired: Boolean = post.isExpired()): SaluStatus = when {
    expired -> SaluStatus.Expired
    post.status == FoodPostStatus.RESERVED -> SaluStatus.Reserved
    post.status == FoodPostStatus.CLAIMED -> SaluStatus.Claimed
    post.status == FoodPostStatus.REMOVED -> SaluStatus.Removed
    post.status == FoodPostStatus.EXPIRED -> SaluStatus.Expired
    else -> SaluStatus.Available
}

/**
 * Edits one of the signed-in user's own, unexpired listings through
 * update_my_food_post(). Quantity, unit and photos can't be changed.
 * [onClose] gets "Listing updated." after a save, or null when cancelled.
 */
@Composable
fun EditListingDialog(
    post: FoodPost,
    foodViewModel: FoodViewModel,
    onClose: (String?) -> Unit
) {
    val context = LocalContext.current
    val editResult by foodViewModel.editResult.collectAsStateWithLifecycle()
    val backendSaving by foodViewModel.saving.collectAsStateWithLifecycle()

    var donation by rememberSaveable(post.id) { mutableStateOf(post.price <= 0.0) }
    var name by rememberSaveable(post.id) { mutableStateOf(post.name) }
    var category by rememberSaveable(post.id) { mutableStateOf(post.category) }
    var price by rememberSaveable(post.id) {
        mutableStateOf(
            if (post.price > 0.0) BigDecimal.valueOf(post.price).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
            else ""
        )
    }
    var description by rememberSaveable(post.id) { mutableStateOf(post.description.orEmpty()) }
    var location by rememberSaveable(post.id) { mutableStateOf(post.pickupLocation) }
    // Older listings have no pin; one can be added or moved here, never removed.
    var pickupPin by rememberSaveable(post.id, stateSaver = PinSaver) { mutableStateOf(post.pickupPin) }
    var expiry by rememberSaveable(post.id) { mutableStateOf(epochMillis(post.expirationDate)) }
    var pickupStart by rememberSaveable(post.id) { mutableStateOf(epochMillis(post.pickupStart)) }
    var pickupEnd by rememberSaveable(post.id) { mutableStateOf(epochMillis(post.pickupEnd)) }
    var handover by rememberSaveable(post.id) {
        mutableStateOf(ListingHandover.fromCode(post.handover)?.label.orEmpty())
    }
    var allergens by rememberSaveable(post.id) { mutableStateOf(post.allergens.orEmpty()) }
    var storage by rememberSaveable(post.id) { mutableStateOf(post.storageNotes.orEmpty()) }
    var error by rememberSaveable(post.id) { mutableStateOf("") }
    var waiting by rememberSaveable(post.id) { mutableStateOf(false) }

    // Every accepted save ends with "Listing updated." or an error.
    LaunchedEffect(editResult) {
        val result = editResult ?: return@LaunchedEffect
        foodViewModel.clearEditResult()
        if (!waiting) return@LaunchedEffect
        waiting = false
        when (result) {
            EditResult.Updated -> onClose("Listing updated.")
            is EditResult.Failed -> error = result.message
        }
    }

    fun chooseDateTime(current: Long, onSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        if (current > 0L) calendar.timeInMillis = current
        DatePickerDialog(
            context,
            { _, year, month, day ->
                calendar.set(year, month, day)
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)
                        onSelected(calendar.timeInMillis)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // The same checks as update_my_food_post(). Unlike Create Listing, the
    // pickup window may already have started, and old listings may have none.
    fun validate(): String {
        val amount = price.toBigDecimalOrNull()
        return when {
            name.isBlank() -> "Enter the food name."
            textLength(name) > 160 -> "The food name must be 160 characters or fewer."
            category.isBlank() -> "Select a category."
            !donation && (amount == null || amount.signum() <= 0 || amount.scale() > 2) ->
                "Enter a price greater than zero, with up to two decimal places."
            !donation && amount != null && amount > BigDecimal("99999.99") -> "Enter a price of at most ₱99,999.99."
            location.isBlank() -> "Enter the pickup location."
            textLength(location) > 300 -> "The pickup location must be 300 characters or fewer."
            expiry <= System.currentTimeMillis() -> "Choose a future expiration date and time."
            (pickupStart == 0L) != (pickupEnd == 0L) -> "Choose both pickup times, or neither."
            pickupStart != 0L && pickupEnd <= pickupStart -> "Pickup must end after it starts."
            pickupEnd != 0L && pickupEnd > expiry -> "Pickup must end on or before expiration."
            // Required here too, so an older listing with none must fill them in.
            allergens.isBlank() -> ALLERGENS_MISSING
            textLength(allergens) > 300 -> "Allergens must be 300 characters or fewer."
            textLength(storage) > 300 -> "Storage notes must be 300 characters or fewer."
            textLength(description) > 2000 -> "The description must be 2,000 characters or fewer."
            else -> ""
        }
    }

    Dialog(
        onDismissRequest = { if (!waiting) onClose(null) },
        properties = EdgeToEdgeDialog
    ) {
        DarkSystemBarIcons()
        // Clear of the status bar, the navigation bar and the keyboard, so the
        // focused text box scrolls into view above the keyboard.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SaluColors.Cream)
                .safeDrawingPadding()
        ) {
            SaluTopBar(
                title = "Edit listing",
                subtitle = post.name,
                onBack = { if (!waiting) onClose(null) },
                backDescription = "Cancel editing"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InlineNotice(
                    text = "Quantity, unit and photos can't be changed.",
                    icon = Icons.Outlined.Info
                )

                ListingTypeSwitch(donation = donation, onChange = { donation = it })

                FormSection(title = "Food", icon = Icons.Outlined.RiceBowl) {
                    PostField(
                        label = "Food name",
                        value = name,
                        onChange = { name = it },
                        example = "e.g., Assorted bread loaves",
                        required = true
                    )
                    PostChoice(
                        label = "Category",
                        selected = category,
                        options = FoodCategories,
                        onSelect = { category = it },
                        required = true
                    )
                    if (!donation) {
                        PostField(
                            label = "Price per unit",
                            value = price,
                            onChange = { price = it },
                            example = "25.00",
                            keyboard = KeyboardType.Decimal,
                            prefix = "₱",
                            required = true
                        )
                    }
                    PostField(
                        label = "Description (optional)",
                        value = description,
                        onChange = { description = it },
                        example = "e.g., 10 individually packed bread rolls available today"
                    )
                }

                FormSection(title = "Pickup", icon = Icons.Outlined.LocationOn) {
                    PostField(
                        label = "Pickup location / exact meeting point",
                        value = location,
                        onChange = { location = it },
                        example = "e.g., Main entrance of the barangay hall, Del Rosario, Balilihan",
                        required = true
                    )
                    PickupPinPicker(
                        pin = pickupPin,
                        onPinChange = { pickupPin = it },
                        savedPin = post.pickupPin,
                        editing = true,
                        accent = SaluColors.Green
                    )
                    PostDateButton("Pickup starts", pickupStart, onNow = { pickupStart = currentMinute() }) {
                        chooseDateTime(pickupStart) { pickupStart = it }
                    }
                    PostDateButton("Pickup ends", pickupEnd) {
                        chooseDateTime(pickupEnd) { pickupEnd = it }
                    }
                    if (pickupStart != 0L || pickupEnd != 0L) {
                        TextButton(onClick = { pickupStart = 0L; pickupEnd = 0L }) {
                            Text("Clear pickup times", color = SaluColors.Green)
                        }
                    }
                    PostChoice(
                        label = "Handover",
                        selected = handover,
                        options = ListingHandover.entries.map { it.label },
                        onSelect = { handover = it }
                    )
                }

                FormSection(title = "Food information", icon = Icons.Outlined.WarningAmber) {
                    PostField(
                        label = "Allergens",
                        value = allergens,
                        onChange = { allergens = it },
                        example = "e.g., Contains wheat, milk, and eggs",
                        hint = ALLERGENS_HINT,
                        required = true
                    )
                    PostField(
                        label = "Storage notes (optional)",
                        value = storage,
                        onChange = { storage = it },
                        example = "e.g., Keep refrigerated until pickup"
                    )
                    PostDateButton("Expiration", expiry, required = true) {
                        chooseDateTime(expiry) { expiry = it }
                    }
                }

                if (error.isNotBlank()) {
                    InlineNotice(
                        text = error,
                        icon = Icons.Outlined.ErrorOutline,
                        tone = NoticeTone.Error
                    )
                }

                SaluPrimaryButton(
                    text = if (waiting) "Saving…" else "Save changes",
                    enabled = !backendSaving,
                    loading = waiting,
                    onClick = {
                        error = validate()
                        if (error.isBlank()) {
                            foodViewModel.clearEditResult()
                            waiting = foodViewModel.updatePost(
                                post.id,
                                FoodPostEdit(
                                    name = name,
                                    description = description,
                                    category = category,
                                    // validate() has checked the price; a donation is free.
                                    price = if (donation) 0.0 else price.toBigDecimal().toDouble(),
                                    pickupLocation = location,
                                    expirationDate = Instant.ofEpochMilli(expiry).toString(),
                                    pickupStart = pickupStart.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it).toString() },
                                    pickupEnd = pickupEnd.takeIf { it != 0L }?.let { Instant.ofEpochMilli(it).toString() },
                                    handover = ListingHandover.fromLabel(handover),
                                    allergens = allergens,
                                    storageNotes = storage,
                                    // Null only for an older listing that still has no pin.
                                    pickupPin = pickupPin
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

private fun epochMillis(timestamp: String?): Long =
    parseTimestamp(timestamp)?.toEpochMilli() ?: 0L

/** Characters as the database counts them (char_length), not UTF-16 units. */
private fun textLength(value: String): Int = value.trim().let { it.codePointCount(0, it.length) }

private const val PICKUP_START_PAST = "The pickup start is in the past. Tap Start now, or choose a later time."
private const val ALLERGENS_MISSING = "Enter the allergens, or write \"None\"."
private const val ALLERGENS_HINT =
    "List allergens such as wheat, milk, eggs or nuts, or write \"None\" if there are none."

/** "Start now": the current minute, with seconds zeroed. */
private fun currentMinute(): Long = System.currentTimeMillis().let { it - it % 60_000L }

private fun postTime(value: Long): String {
    if (value == 0L) return "Select date and time"
    return SimpleDateFormat(
        "MMM d, yyyy • h:mm a",
        Locale.ENGLISH
    ).format(Date(value))
}

/** Donate or sell, as a two-part choice. */
@Composable
private fun ListingTypeSwitch(donation: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaluRadius.Control)
            .background(SaluColors.PaleGreen)
            .padding(4.dp)
            .selectableGroup()
    ) {
        ListingTypeOption(
            icon = Icons.Outlined.VolunteerActivism,
            title = "I want to donate",
            subtitle = "Free for others",
            selected = donation,
            onClick = { onChange(true) },
            modifier = Modifier.weight(1f)
        )
        ListingTypeOption(
            icon = Icons.Outlined.Sell,
            title = "I want to sell",
            subtitle = "Paid at pickup",
            selected = !donation,
            onClick = { onChange(false) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ListingTypeOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape11)
            .background(if (selected) Color.White else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .heightIn(min = 56.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) SaluColors.Green else SaluColors.Gray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                color = if (selected) SaluColors.DarkGreen else SaluColors.Gray
            )
            Text(subtitle, style = SaluText.Meta, color = SaluColors.Gray)
        }
    }
}

private val RoundedCornerShape11 = androidx.compose.foundation.shape.RoundedCornerShape(11.dp)

/** A titled group of form fields on one card. */
@Composable
private fun FormSection(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    SaluCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(icon, size = 36.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        title,
                        style = SaluText.SectionTitle,
                        color = SaluColors.DarkGreen,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (subtitle != null) {
                        Text(subtitle, style = SaluText.Supporting, color = SaluColors.Gray)
                    }
                }
            }
            content()
        }
    }
}

/** Up to four photos: the first is the cover; each can be removed. */
@Composable
private fun PhotoPicker(
    photos: List<String>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Food photos *",
                style = SaluText.Meta,
                color = SaluColors.Gray,
                modifier = Modifier.weight(1f)
            )
            Text("${photos.size} of 4", style = SaluText.Meta, color = SaluColors.Gray)
        }
        Text(
            "Add up to four clear photos of the food. The first one is the cover.",
            style = SaluText.Supporting,
            color = SaluColors.Gray
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            photos.forEachIndexed { index, uri ->
                Box {
                    PostPhoto(uri, cover = index == 0)
                    // White circle so the X reads on any photo; the button itself is 48 dp.
                    IconButton(
                        onClick = { onRemove(uri) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, SaluColors.Line, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Remove photo ${index + 1}",
                                tint = SaluColors.DarkGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (photos.size < 4) {
                Column(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(SaluRadius.Photo)
                        .background(SaluColors.PaleGreen)
                        .border(1.dp, SaluColors.Gray.copy(alpha = 0.45f), SaluRadius.Photo)
                        .clickable(onClickLabel = "Add photos", role = Role.Button, onClick = onAdd),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null, tint = SaluColors.DarkGreen)
                    Spacer(Modifier.height(6.dp))
                    Text("Add photo", style = SaluText.Meta, color = SaluColors.DarkGreen)
                }
            }
        }
    }
}

@Composable
private fun PostDateButton(
    label: String,
    value: Long,
    required: Boolean = false,
    onNow: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val exampleTime = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)

        set(
            Calendar.HOUR_OF_DAY,
            when (label) {
                "Pickup starts" -> 15
                "Pickup ends" -> 17
                else -> 18
            }
        )

        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val shown = if (value == 0L) "e.g., ${postTime(exampleTime)}" else postTime(value)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (required) "$label *" else label,
                style = SaluText.Meta,
                color = SaluColors.Gray,
                modifier = Modifier.weight(1f)
            )
            if (onNow != null) {
                TextButton(onClick = onNow) {
                    Text("Start now", style = MaterialTheme.typography.labelLarge, color = SaluColors.Green)
                }
            }
        }

        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics {
                    contentDescription = if (value == 0L) "$label: not chosen. Choose date and time" else "$label: $shown. Change date and time"
                },
            shape = SaluRadius.Control,
            border = BorderStroke(1.dp, SaluColors.Gray.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = SaluColors.DarkGreen
            ),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = SaluColors.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                shown,
                style = SaluText.Body,
                color = if (value == 0L) SaluColors.Gray else SaluColors.DarkGreen,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** One card of "Check your listing": a heading and its lines. */
@Composable
private fun CheckSection(title: String, lines: List<String>) {
    ActionCard {
        Text(title, style = SaluText.Meta.copy(fontWeight = FontWeight.Bold), color = SaluColors.Green)
        lines.forEach { Text(it, style = SaluText.Body, color = SaluColors.DarkGreen) }
    }
}

@Composable
private fun PostField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    example: String? = null,
    hint: String? = null,
    required: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    prefix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(if (required) "$label *" else label) },
        placeholder = example?.let { { Text(it, style = SaluText.Body) } },
        supportingText = hint?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard
        ),
        singleLine = keyboard == KeyboardType.Number || keyboard == KeyboardType.Decimal,
        textStyle = SaluText.Body.copy(fontSize = 15.sp),
        shape = SaluRadius.Control,
        colors = saluFieldColors()
    )
}

/** A choice from a few options, shown as chips so every option is visible. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostChoice(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    required: Boolean = false
) {
    Column {
        Text(
            if (required) "$label *" else label,
            style = SaluText.Meta,
            color = SaluColors.Gray
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                SaluFilterChip(
                    label = option,
                    selected = selected == option,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
fun PostPhoto(uri: String, cover: Boolean = false) {
    val context = LocalContext.current

    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val parsed = Uri.parse(uri)
                val bounds = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(parsed)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }

                var sample = 1
                while (
                    bounds.outWidth / sample > 512 ||
                    bounds.outHeight / sample > 512
                ) {
                    sample *= 2
                }

                context.contentResolver.openInputStream(parsed)?.use {
                    BitmapFactory.decodeStream(
                        it,
                        null,
                        BitmapFactory.Options().apply {
                            inSampleSize = sample
                        }
                    )?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(SaluRadius.Photo)
            .background(SaluColors.PaleGreen),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = if (cover) "Selected food photo, cover" else "Selected food photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("Photo preview", style = SaluText.Meta, color = SaluColors.Gray)
        }

        if (cover) {
            Text(
                "Cover",
                style = SaluText.Status,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(SaluRadius.Small)
                    .background(SaluColors.Green)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
