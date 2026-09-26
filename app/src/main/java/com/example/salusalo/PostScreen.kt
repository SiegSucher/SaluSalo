package com.example.salusalo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostDraft
import com.example.salusalo.model.FoodPostEdit
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.viewmodel.EditResult
import com.example.salusalo.model.ListingHandover
import com.example.salusalo.model.isExpired
import com.example.salusalo.viewmodel.FoodViewModel
import java.math.BigDecimal
import java.time.Instant
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val PostGreen = Color(0xFF3F792B)
private val PostCream = Color(0xFFFAF8F1)
private val PostPale = Color(0xFFEDF3E1)
private val PostDark = Color(0xFF213420)

@Composable
fun PostScreen(onBack: () -> Unit, foodViewModel: FoodViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myPosts by foodViewModel.myPosts.collectAsStateWithLifecycle()
    val createdPost by foodViewModel.createdPost.collectAsStateWithLifecycle()
    val createError by foodViewModel.createError.collectAsStateWithLifecycle()
    val backendSaving by foodViewModel.saving.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { foodViewModel.loadMyPosts() }

    var donation by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
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
    var savedId by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
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
            expiry <= now -> "Choose a future expiration date and time."
            pickupStart <= now -> "Choose a future pickup start time."
            pickupEnd <= pickupStart -> "Pickup must end after it starts."
            pickupEnd > expiry -> "Pickup must end on or before expiration."
            textLength(allergens) > 300 -> "Allergens must be 300 characters or fewer."
            textLength(storage) > 300 -> "Storage notes must be 300 characters or fewer."
            else -> ""
        }
    }

    LaunchedEffect(createdPost) {
        createdPost?.let { post ->
            savedId = post.id
            saving = false
            foodViewModel.clearCreatedPost()
        }
    }
    LaunchedEffect(createError) {
        createError?.let { message ->
            if (saving) {
                saving = false
                error = message
            }
            foodViewModel.clearCreateError()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PostCream)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = !saving,
                onClick = onBack
            ) {
                Text("Back", color = PostGreen)
            }

            Column(Modifier.weight(1f)) {
                Text(
                    "Create Listing",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = PostDark
                )
                Text(
                    "Post surplus food to help others",
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (savedId.isNotBlank()) {
                PostPanel {
                    Text(
                        "Listing published",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PostGreen
                    )
                    Text(name, fontWeight = FontWeight.Bold)
                    Text("$quantity $unit • ${if (donation) "Free" else "₱$price per unit"}")
                    Text("Pickup: $location")
                    Text("Photos: ${photos.size}")
                    Text("Reference: ${savedId.take(8)}")
                    Text(
                        "Your listing is now published to the community feed.",
                        fontSize = 12.sp
                    )
                    PostButton("Back to home", onClick = onBack)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = donation,
                        onClick = { donation = true },
                        label = { Text("I want to donate") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !donation,
                        onClick = { donation = false },
                        label = { Text("I want to sell") },
                        modifier = Modifier.weight(1f)
                    )
                }

                PostPanel {
                    Text(
                        "Food photos • Required",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Add up to four clear photos of the food.",
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(
                            rememberScrollState()
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        photos.forEach { uri ->
                            Column {
                                PostPhoto(uri)
                                TextButton(
                                    onClick = {
                                        photos = ArrayList(photos.filter { it != uri })
                                    }
                                ) {
                                    Text("Remove", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        enabled = photos.size < 4,
                        onClick = {
                            photoPicker.launch(arrayOf("image/*"))
                        }
                    ) {
                        Text("＋ Add photos")
                    }
                }

                PostPanel {
                    PostField("Food name", name, { name = it })
                    PostChoice(
                        "Category",
                        category,
                        listOf(
                            "Bread & pastries",
                            "Vegetables",
                            "Fruit",
                            "Cooked meals",
                            "Packaged food",
                            "Other"
                        ),
                        { category = it }
                    )

                    PostField(
                        label = if (donation) "Price — free donation" else "Price per unit (₱)",
                        value = if (donation) "0.00" else price,
                        onChange = { price = it },
                        keyboard = KeyboardType.Decimal,
                        enabled = !donation
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PostField(
                            "Quantity",
                            quantity,
                            { quantity = it },
                            modifier = Modifier.weight(1f),
                            keyboard = KeyboardType.Number
                        )
                        Box(Modifier.weight(1f)) {
                            PostChoice(
                                "Unit",
                                unit,
                                listOf("pcs", "packs", "meals", "boxes", "kg"),
                                { unit = it }
                            )
                        }
                    }

                    PostField(
                        "Description (optional)",
                        description,
                        { description = it }
                    )

                    PostDateButton("Expiration", expiry) {
                        chooseDateTime(expiry) { expiry = it }
                    }

                    PostField(
                        "Pickup location / exact meeting point",
                        location,
                        { location = it }
                    )

                    Text(
                        "Enter an address or landmark recipients can recognize.",
                        fontSize = 11.sp
                    )

                    PostDateButton("Pickup starts", pickupStart) {
                        chooseDateTime(pickupStart) { pickupStart = it }
                    }
                    PostDateButton("Pickup ends", pickupEnd) {
                        chooseDateTime(pickupEnd) { pickupEnd = it }
                    }

                    PostChoice(
                        "Handover",
                        distribution,
                        ListingHandover.entries.map { it.label },
                        { distribution = it }
                    )
                }

                PostPanel {
                    PostField(
                        "Allergens (optional)",
                        allergens,
                        { allergens = it }
                    )
                    PostField(
                        "Storage notes (optional)",
                        storage,
                        { storage = it }
                    )
                    Text(
                        "Blank allergen information means not provided, " +
                                "not allergen-free.",
                        fontSize = 11.sp
                    )
                }

                PostPanel(pale = true) {
                    Text(
                        "Recommendation insights",
                        fontWeight = FontWeight.Bold,
                        color = PostGreen
                    )
                    Text(
                        "Food category and pickup information can help " +
                                "match listings with recipient preferences.",
                        fontSize = 12.sp
                    )
                    TextButton(
                        onClick = {
                            val count = quantity.toIntOrNull()
                            val amount = price.toBigDecimalOrNull()
                            val now = System.currentTimeMillis()

                            info = buildString {
                                appendLine("INSIGHTS FOR ${name.ifBlank { "YOUR LISTING" }}")
                                appendLine()
                                appendLine("Based on the details you entered.")
                                appendLine()

                                appendLine("FOOD CATEGORY")
                                if (category.isBlank()) {
                                    appendLine("Select a category to describe the food.")
                                } else {
                                    appendLine(
                                        "$category: this category could help match " +
                                                "the listing with recipients who prefer it."
                                    )
                                }

                                appendLine()
                                appendLine("PRICE")

                                if (donation) {
                                    appendLine("Free donation. Recipients will not pay for the food.")
                                } else if (
                                    amount != null &&
                                    amount.signum() > 0 &&
                                    amount.scale() <= 2
                                ) {
                                    appendLine("₱${amount.toPlainString()} per selected unit.")

                                    if (count != null && count > 0) {
                                        val batchTotal = amount.multiply(count.toBigDecimal())
                                        appendLine(
                                            "Price for the full batch: ₱${batchTotal.toPlainString()}."
                                        )
                                    }
                                } else {
                                    appendLine("Enter a valid price with up to two decimal places.")
                                }

                                appendLine()
                                appendLine("QUANTITY")

                                if (count == null || count <= 0) {
                                    appendLine("Enter a quantity greater than zero.")
                                } else {
                                    val unitLabel = unit.ifBlank { "units" }

                                    appendLine("Available: $count $unitLabel.")
                                }

                                appendLine()
                                appendLine("PICKUP WINDOW")

                                when {
                                    pickupStart == 0L || pickupEnd == 0L ->
                                        appendLine("Select both pickup start and end times.")

                                    pickupStart <= now ->
                                        appendLine("The pickup start time must be in the future.")

                                    pickupEnd <= pickupStart ->
                                        appendLine("Pickup must end after it starts.")

                                    else -> {
                                        val minutes = (pickupEnd - pickupStart) / 60_000
                                        val hours = minutes / 60
                                        val remainingMinutes = minutes % 60

                                        appendLine("Starts: ${postTime(pickupStart)}")
                                        appendLine("Ends: ${postTime(pickupEnd)}")
                                        appendLine(
                                            "Collection window: $hours hr $remainingMinutes min."
                                        )

                                        if (minutes < 60) {
                                            appendLine(
                                                "This is a short collection window. " +
                                                        "Make the meeting point clear."
                                            )
                                        }
                                    }
                                }

                                appendLine()
                                appendLine("EXPIRATION")

                                when {
                                    expiry == 0L ->
                                        appendLine("Select the expiration date and time.")

                                    expiry <= now ->
                                        appendLine("The entered expiration time has already passed.")

                                    pickupEnd > expiry ->
                                        appendLine("Move the pickup end time before expiration.")

                                    else -> {
                                        appendLine("Entered expiration: ${postTime(expiry)}")

                                        if (pickupEnd > 0L && pickupEnd > pickupStart) {
                                            val buffer = (expiry - pickupEnd) / 60_000
                                            appendLine(
                                                "Pickup ends $buffer minute(s) before " +
                                                        "the entered expiration."
                                            )
                                        }
                                    }
                                }

                                appendLine()
                                appendLine("LOCATION")
                                appendLine(
                                    location.ifBlank {
                                        "Add the exact pickup address or a recognizable landmark."
                                    }
                                )

                                appendLine()
                                appendLine("DETAILS TO REVIEW")

                                appendLine(
                                    if (photos.isEmpty()) {
                                        "• Add at least one food photo."
                                    } else {
                                        "• ${photos.size} photo(s) selected."
                                    }
                                )

                                if (allergens.isBlank()) {
                                    appendLine("• Allergen information has not been provided.")
                                }

                                if (storage.isBlank()) {
                                    appendLine("• Storage instructions have not been provided.")
                                }

                                appendLine()
                                appendLine(
                                    "These are form checks and calculations, not AI predictions " +
                                            "or a food-safety assessment."
                                )
                            }
                        }
                    ) {
                        Text("View insights", color = PostGreen)
                    }
                }

                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                PostButton(
                    text = if (saving || backendSaving) "Posting…" else "Post Listing",
                    enabled = !saving && !backendSaving,
                    onClick = {
                        error = validate()

                        if (error.isBlank()) {
                            // Only an accepted request is waited for, so the
                            // button can never stay on "Posting…".
                            saving = foodViewModel.createPost(
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
                                    storageNotes = storage
                                ),
                                photos.map { Uri.parse(it) }
                            )
                        }
                    }
                )

                Text(
                    "Review the food details and pickup arrangements before posting.",
                    fontSize = 11.sp
                )
            }

            TextButton(onClick = { showLocal = !showLocal }) {
                Text("My posts (${myPosts.size})", color = PostGreen)
            }

            if (showLocal) {
                if (myPosts.isEmpty()) {
                    Text("You have no food posts yet.")
                }

                myPosts.forEach { post ->
                    PostPanel {
                        Text(post.name, fontWeight = FontWeight.Bold, color = PostDark)
                        Text("${post.quantity} ${post.unit} • ${post.category}")
                        Text(post.pickupLocation, fontSize = 12.sp)
                        Text("Expires: ${post.expirationDate.replace("T", " ").take(16)}", fontSize = 12.sp)
                        Text("Status: ${if (post.isExpired()) "Expired" else post.status.name}", fontSize = 12.sp, color = PostGreen)
                        TextButton(onClick = {
                            info = post.description ?: "No description provided."
                        }) { Text("View details") }
                        if (!post.isExpired()) {
                            TextButton(onClick = { editing = post }) { Text("Edit", color = PostGreen) }
                        }
                        TextButton(onClick = { pendingDelete = post }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    pendingDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete listing?") },
            text = { Text("This permanently removes this listing and its photos.") },
            confirmButton = {
                TextButton(enabled = !backendSaving, onClick = {
                    foodViewModel.deletePost(post)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
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
            textLength(allergens) > 300 -> "Allergens must be 300 characters or fewer."
            textLength(storage) > 300 -> "Storage notes must be 300 characters or fewer."
            else -> ""
        }
    }

    Dialog(
        onDismissRequest = { if (!waiting) onClose(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PostCream)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(enabled = !waiting, onClick = { onClose(null) }) {
                    Text("Cancel", color = PostGreen)
                }
                Text(
                    "Edit Listing",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = PostDark
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Quantity, unit and photos can't be changed.",
                    fontSize = 12.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = donation,
                        onClick = { donation = true },
                        label = { Text("I want to donate") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !donation,
                        onClick = { donation = false },
                        label = { Text("I want to sell") },
                        modifier = Modifier.weight(1f)
                    )
                }

                PostPanel {
                    PostField("Food name", name, { name = it })
                    PostChoice(
                        "Category",
                        category,
                        listOf(
                            "Bread & pastries",
                            "Vegetables",
                            "Fruit",
                            "Cooked meals",
                            "Packaged food",
                            "Other"
                        ),
                        { category = it }
                    )
                    PostField(
                        label = if (donation) "Price — free donation" else "Price per unit (₱)",
                        value = if (donation) "0.00" else price,
                        onChange = { price = it },
                        keyboard = KeyboardType.Decimal,
                        enabled = !donation
                    )
                    PostField("Description (optional)", description, { description = it })
                    PostDateButton("Expiration", expiry) {
                        chooseDateTime(expiry) { expiry = it }
                    }
                    PostField(
                        "Pickup location / exact meeting point",
                        location,
                        { location = it }
                    )
                    PostDateButton("Pickup starts", pickupStart) {
                        chooseDateTime(pickupStart) { pickupStart = it }
                    }
                    PostDateButton("Pickup ends", pickupEnd) {
                        chooseDateTime(pickupEnd) { pickupEnd = it }
                    }
                    if (pickupStart != 0L || pickupEnd != 0L) {
                        TextButton(onClick = { pickupStart = 0L; pickupEnd = 0L }) {
                            Text("Clear pickup times", color = PostGreen)
                        }
                    }
                    PostChoice(
                        "Handover",
                        handover,
                        ListingHandover.entries.map { it.label },
                        { handover = it }
                    )
                }

                PostPanel {
                    PostField("Allergens (optional)", allergens, { allergens = it })
                    PostField("Storage notes (optional)", storage, { storage = it })
                    Text(
                        "Blank allergen information means not provided, " +
                                "not allergen-free.",
                        fontSize = 11.sp
                    )
                }

                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                PostButton(
                    text = if (waiting) "Saving…" else "Save changes",
                    enabled = !waiting && !backendSaving,
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
                                    storageNotes = storage
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

private fun postTime(value: Long): String {
    if (value == 0L) return "Select date and time"
    return SimpleDateFormat(
        "MMM d, yyyy • h:mm a",
        Locale.getDefault()
    ).format(Date(value))
}

@Composable
private fun PostDateButton(
    label: String,
    value: Long,
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

    Column {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )

        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                if (value == 0L) {
                    "e.g., ${postTime(exampleTime)}"
                } else {
                    postTime(value)
                },
                fontSize = 12.sp
            )
        }

        Text(
            "Tap to choose your actual date and time.",
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PostPanel(
    pale: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (pale) PostPale else Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = if (pale) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun PostField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true
) {
    val example = when {
        label == "Food name" ->
            "e.g., Assorted bread loaves"

        label.contains("free donation") ->
            "e.g., 0.00 — free donation"

        label.startsWith("Price") ->
            "e.g., 25.00 per selected unit"

        label == "Quantity" ->
            "e.g., 10"

        label.startsWith("Description") ->
            "e.g., 10 individually packed bread rolls available today"

        label.startsWith("Pickup location") ->
            "e.g., Main entrance of the barangay hall, Del Rosarrio, Balilihan"

        label.startsWith("Allergens") ->
            "e.g., Contains wheat, milk, and eggs"

        label.startsWith("Storage") ->
            "e.g., Keep refrigerated until pickup"

        else ->
            "e.g., Enter the requested details"
    }

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = {
            Text(label, fontSize = 12.sp)
        },
        placeholder = {
            Text(example, fontSize = 12.sp)
        },
        supportingText = {
            if (value.isNotBlank()) {
                Text(example, fontSize = 10.sp)
            }
        },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PostGreen,
            focusedLabelColor = PostGreen
        )
    )
}

@Composable
private fun PostChoice(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    val example = when (label) {
        "Category" -> "e.g., Bread & pastries"
        "Unit" -> "e.g., packs"
        "Handover" -> "e.g., Collect from provider"
        else -> "Select an option"
    }

    Column {
        Text(label, fontSize = 12.sp)

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (selected.isBlank()) {
                        "$example ▾"
                    } else {
                        "$selected ▾"
                    },
                    fontSize = 12.sp
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }

        if (selected.isNotBlank()) {
            Text(
                example,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun PostButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PostGreen
        )
    ) {
        Text(text)
    }
}

@Composable
fun PostPhoto(uri: String) {
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
            .clip(RoundedCornerShape(12.dp))
            .background(PostPale),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Selected food photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("Photo preview", fontSize = 10.sp)
        }
    }
}