package com.example.salusalo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.ReservationDisplay
import com.example.salusalo.model.ReservationStatus
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.reservationTotal
import com.example.salusalo.viewmodel.FoodViewModel
import com.example.salusalo.viewmodel.MessageViewModel
import com.example.salusalo.viewmodel.ReservationViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AGreen = Color(0xFF39792B)
private val ADark = Color(0xFF1C2B22)
private val AGray = Color(0xFF606875)
private val ACream = Color(0xFFFCFBF6)
private val APale = Color(0xFFF2F6E6)
private val ALine = Color(0xFFDCE7C6)

private data class ActivityTransaction(val entry: ReservationDisplay, val provider: Boolean)

// Keep the existing call signature: MainActivity owns the original bottom bar.
@Composable
fun ActivityScreen(
    onPost: () -> Unit,
    onAlerts: () -> Unit,
    foodViewModel: FoodViewModel,
    reservationViewModel: ReservationViewModel,
    messageViewModel: MessageViewModel,
    unreadAlerts: Int = 0,
    initialTab: String = "My Listings",
    focusReservationId: String? = null,
    onFocusHandled: () -> Unit = {}
) {
    val posts by foodViewModel.myPosts.collectAsStateWithLifecycle()
    val requests by reservationViewModel.myReservations.collectAsStateWithLifecycle()
    val incoming by reservationViewModel.donorReservations.collectAsStateWithLifecycle()
    val foodLoading by foodViewModel.loading.collectAsStateWithLifecycle()
    val reservationLoading by reservationViewModel.loading.collectAsStateWithLifecycle()
    val foodError by foodViewModel.error.collectAsStateWithLifecycle()
    val message by reservationViewModel.message.collectAsStateWithLifecycle()
    val busy = foodLoading || reservationLoading
    var notice by rememberSaveable { mutableStateOf("") }

    // Existing alert links use the old names; map them without editing navigation.
    var tab by rememberSaveable(initialTab) {
        mutableStateOf(when (initialTab) {
            "My Reservations", "Reservations", "My Requests" -> "My Requests"
            "History", "Purchases", "Claims" -> "History"
            else -> "My Listings"
        })
    }
    var listingFilter by rememberSaveable { mutableStateOf("Active") }
    var historyFilter by rememberSaveable { mutableStateOf("All") }
    var selectedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedProvider by rememberSaveable { mutableStateOf(false) }
    var confirmAction by rememberSaveable { mutableStateOf<String?>(null) }

    fun refresh() {
        foodViewModel.loadMyPosts()
        reservationViewModel.refresh()
    }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(message) {
        message?.let {
            notice = it
            reservationViewModel.clearMessage()
            if (it.startsWith("Reservation cancelled") || it.startsWith("Reservation marked")) {
                foodViewModel.loadMyPosts()
                foodViewModel.loadAvailablePosts()
            }
        }
    }

    val transactions = (incoming.map { ActivityTransaction(it, true) } +
            requests.map { ActivityTransaction(it, false) })
        .distinctBy { it.entry.reservation.id }
        .sortedByDescending { it.entry.reservation.updatedAt ?: it.entry.reservation.createdAt.orEmpty() }
    val selectedPost = posts.firstOrNull { it.id == selectedPostId }
    val selectedTransaction = transactions.firstOrNull {
        it.entry.reservation.id == selectedTransactionId && it.provider == selectedProvider
    }
    fun openTransaction(value: ActivityTransaction) {
        selectedTransactionId = value.entry.reservation.id
        selectedProvider = value.provider
    }
    // Preserve the alert's exact reservation destination after the lists load.
    LaunchedEffect(focusReservationId, transactions) {
        val id = focusReservationId ?: return@LaunchedEffect
        val transaction = transactions.firstOrNull {
            it.entry.reservation.id == id && it.entry.reservation.status == ReservationStatus.CLAIMED
        } ?: return@LaunchedEffect
        tab = "History"
        historyFilter = "All"
        selectedPostId = null
        openTransaction(transaction)
        onFocusHandled()
    }
    val completed = transactions.filter { it.entry.reservation.status == ReservationStatus.CLAIMED }
    val activeRequests = transactions.filter { !it.provider && it.entry.reservation.status == ReservationStatus.RESERVED }
    val history = transactions.filter {
        it.entry.reservation.status != ReservationStatus.RESERVED && when (historyFilter) {
            "Completed" -> it.entry.reservation.status == ReservationStatus.CLAIMED
            "Cancelled" -> it.entry.reservation.status == ReservationStatus.CANCELLED
            else -> true
        }
    }
    val visiblePosts = posts.sortedByDescending { it.createdAt.orEmpty() }.filter { post ->
        // A fully reserved listing still needs to be managed until pickup.
        val active = post.status == FoodPostStatus.AVAILABLE || post.status == FoodPostStatus.RESERVED ||
                incoming.any { it.reservation.foodPostId == post.id && it.reservation.status == ReservationStatus.RESERVED }
        if (listingFilter == "Active") active else !active
    }

    BackHandler(enabled = selectedPostId != null) { selectedPostId = null }
    Surface(Modifier.fillMaxSize(), color = ACream) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Activity", color = ADark, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Your food-sharing journey", color = AGray, fontSize = 14.sp)
                    }
                    IconButton(onClick = { refresh() }, enabled = !busy) {
                        Icon(Icons.Outlined.Refresh, "Refresh activity", tint = AGreen)
                    }
                    IconButton(onClick = onAlerts) {
                        Box {
                            Icon(Icons.Outlined.Notifications, "Open alerts", tint = ADark)
                            if (unreadAlerts > 0) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(AGreen))
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("My Listings", "My Requests", "History").forEach { label ->
                        Surface(
                            onClick = { tab = label; selectedPostId = null; refresh() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = if (tab == label) AGreen else Color.White,
                            border = BorderStroke(1.dp, ALine)
                        ) {
                            Box(Modifier.heightIn(min = 48.dp).padding(6.dp), contentAlignment = Alignment.Center) {
                                Text(label, color = if (tab == label) Color.White else ADark,
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${completed.size} completed", color = AGray, fontSize = 12.sp)
                    Text("${completed.sumOf { it.entry.reservation.quantity }} items redistributed", color = AGray, fontSize = 12.sp)
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = AGreen) }
            foodError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item {
                Text(when (tab) {
                    "My Listings" -> "Food you’re sharing"
                    "My Requests" -> "Food you’ve requested"
                    else -> "Your past transactions"
                }, color = ADark, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                if (tab == "My Listings") ActivityFilters(listOf("Active", "Closed"), listingFilter) { listingFilter = it }
                if (tab == "History") ActivityFilters(listOf("All", "Completed", "Cancelled"), historyFilter) { historyFilter = it }
            }
            when (tab) {
                "My Listings" -> {
                    if (visiblePosts.isEmpty() && !busy) item { ActivityEmpty("No ${listingFilter.lowercase()} listings", "Food you post will appear here.") }
                    items(visiblePosts, key = { it.id }) { post ->
                        val count = incoming.count { it.reservation.foodPostId == post.id && it.reservation.status == ReservationStatus.RESERVED }
                        ActivityCard {
                            ActivityFoodHeader(post, if (post.price > 0) "For sale • ${formatPeso(post.price)} per unit" else "Donation • Free")
                            Text("${post.quantity} ${post.unit} remaining", color = AGray)
                            ActivityBadge(if (count > 0) "$count awaiting pickup" else post.status.name.lowercase().replaceFirstChar { it.uppercase() })
                            Text("Expires: ${activityTime(post.expirationDate)}", fontSize = 12.sp, color = AGray)
                            ActivityButton(if (count > 0) "Manage requests" else "View listing") { selectedPostId = post.id }
                        }
                    }
                    item { ActivityButton("+  Create listing", onClick = onPost) }
                }
                "My Requests" -> {
                    if (activeRequests.isEmpty() && !busy) item { ActivityEmpty("No active requests", "Reserve food from Home to see your pickup details here.") }
                    items(activeRequests, key = { it.entry.reservation.id }) { transaction ->
                        ActivityTransactionCard(transaction, "View pickup details") { openTransaction(transaction) }
                    }
                }
                else -> {
                    if (history.isEmpty() && !busy) item { ActivityEmpty("No transactions to show", "Completed and cancelled requests appear here for both providers and recipients.") }
                    items(history, key = { it.entry.reservation.id }) { transaction ->
                        ActivityTransactionCard(transaction, "View transaction") { openTransaction(transaction) }
                    }
                }
            }
        }
    }

    // Opening a listing also exposes requests on fully reserved or closed posts.
    selectedPost?.let { post ->
        val related = transactions.filter { it.provider && it.entry.reservation.foodPostId == post.id }
        AlertDialog(
            onDismissRequest = { selectedPostId = null },
            title = { Text(post.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${if (post.price > 0) "For sale" else "Donation"} • ${post.quantity} ${post.unit} remaining")
                    if (post.price > 0) Text("Listing price: ${formatPeso(post.price)} per unit • Pay at pickup")
                    Text("Pickup: ${post.pickupLocation}")
                    Text("Expires: ${activityTime(post.expirationDate)}")
                    post.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                    Text("Requests for this listing", fontWeight = FontWeight.Bold)
                    if (related.isEmpty()) Text(if (reservationLoading) "Loading requests…" else "No requests loaded. Use Refresh to check again.", color = AGray)
                    related.forEach { transaction ->
                        OutlinedButton(onClick = { openTransaction(transaction) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(transaction.entry.recipientName ?: "Recipient")
                                Text("${transaction.entry.reservation.quantity} ${post.unit} • ${activityStatus(transaction.entry.reservation.status)}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedPostId = null }) { Text("Close") } },
            dismissButton = {
                // Preserve history: do not expose deletion after any transaction exists.
                if (related.isEmpty()) TextButton(onClick = { confirmAction = "delete" }, enabled = !busy) {
                    Text("Delete listing", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
    selectedTransaction?.let { transaction ->
        val entry = transaction.entry
        val reservation = entry.reservation
        AlertDialog(
            onDismissRequest = { selectedTransactionId = null },
            title = { Text(entry.foodPost.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your role: " + if (transaction.provider) {
                        if (reservation.unitPrice > 0) "Seller" else "Donation provider"
                    } else {
                        if (reservation.unitPrice > 0) "Buyer" else "Donation recipient"
                    })
                    ActivityBadge(activityStatus(reservation.status))
                    Text("Quantity: ${reservation.quantity} ${entry.foodPost.unit}")
                    if (reservation.unitPrice > 0) {
                        Text("Price per unit: ${formatPeso(reservation.unitPrice)}")
                        Text("Total: ${formatPeso(reservationTotal(reservation.unitPrice, reservation.quantity))}")
                        Text(when (reservation.status) {
                            ReservationStatus.RESERVED -> if (transaction.provider) "Collect payment at pickup." else "Pay at pickup."
                            ReservationStatus.CLAIMED -> "Payment method: pay at pickup."
                            ReservationStatus.CANCELLED -> "Cancelled request."
                        })
                    } else Text("Free donation")
                    Text("Pickup: ${entry.foodPost.pickupLocation}")
                    Text("Food expires: ${activityTime(entry.foodPost.expirationDate)}")
                    if (transaction.provider) Text("Recipient: ${entry.recipientName ?: "Unknown"}")
                    reservation.createdAt?.let { Text("Requested: ${activityTime(it)}") }
                    if (reservation.status != ReservationStatus.RESERVED) reservation.updatedAt?.let { Text("Updated: ${activityTime(it)}") }
                    Text("Reference: ${reservation.id}", fontSize = 12.sp, color = AGray)
                    OutlinedButton(onClick = {
                        messageViewModel.openForReservation(reservation.id)
                        selectedTransactionId = null
                        selectedPostId = null
                    }) { Text(if (transaction.provider) "Message recipient" else "Message provider") }
                }
            },
            confirmButton = {
                if (reservation.status == ReservationStatus.RESERVED) TextButton(
                    enabled = !busy,
                    onClick = { confirmAction = if (transaction.provider) "claim" else "cancel" }
                ) { Text(if (transaction.provider) "Mark as collected" else "Cancel request") }
            },
            dismissButton = { TextButton(onClick = { selectedTransactionId = null }) { Text("Close") } }
        )
    }
    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(when (action) { "claim" -> "Confirm food collected?"; "cancel" -> "Cancel this request?"; else -> "Delete listing?" }) },
            text = { Text(when (action) {
                "claim" -> "Confirm only after you have handed the food to the recipient. " +
                        (if ((selectedTransaction?.entry?.reservation?.unitPrice ?: 0.0) > 0) "Also confirm that payment was received at pickup. " else "") +
                        "This moves the transaction to History."
                "cancel" -> "Your reserved quantity will become available to others."
                else -> "This permanently removes the listing and its uploaded image."
            }) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    when (action) {
                        "claim" -> selectedTransaction?.let { reservationViewModel.claim(it.entry.reservation.id) }
                        "cancel" -> selectedTransaction?.let { reservationViewModel.cancel(it.entry.reservation.id) }
                        "delete" -> selectedPost?.let { foodViewModel.deletePost(it) }
                    }
                    confirmAction = null
                    selectedTransactionId = null
                    selectedPostId = null
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Go back") } }
        )
    }
    if (notice.isNotBlank()) AlertDialog(
        onDismissRequest = { notice = "" }, title = { Text("Activity update") },
        text = { Text(notice) }, confirmButton = { TextButton(onClick = { notice = "" }) { Text("OK") } }
    )
}

@Composable
private fun ActivityFilters(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { label ->
            FilterChip(selected = label == selected, onClick = { onSelect(label) }, label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = APale, selectedLabelColor = AGreen))
        }
    }
}

@Composable
private fun ActivityCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ALine), shadowElevation = 1.dp) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun ActivityFoodHeader(post: FoodPost, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).background(APale), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = AGreen)
            post.displayImageUrl?.let { url ->
                AsyncImage(model = url, contentDescription = post.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(post.name, color = ADark, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AGray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ActivityTransactionCard(transaction: ActivityTransaction, action: String, onClick: () -> Unit) {
    val entry = transaction.entry
    ActivityCard {
        val priced = entry.reservation.unitPrice > 0
        val role = if (transaction.provider) {
            if (priced) "Sale • Provider" else "Donation • Provider"
        } else {
            if (priced) "Purchase • Recipient" else "Donation • Recipient"
        }
        ActivityFoodHeader(entry.foodPost, "$role • ${entry.reservation.quantity} ${entry.foodPost.unit}")
        ActivityBadge(activityStatus(entry.reservation.status))
        Text(if (priced) {
            val total = formatPeso(reservationTotal(entry.reservation.unitPrice, entry.reservation.quantity))
            if (entry.reservation.status == ReservationStatus.RESERVED) "$total • Pay at pickup" else "$total • Reservation total"
        } else "Free", color = AGreen, fontSize = 13.sp)
        if (transaction.provider) Text("Recipient: ${entry.recipientName ?: "Unknown"}", color = AGray, fontSize = 13.sp)
        Text(entry.foodPost.pickupLocation, color = AGray, fontSize = 13.sp)
        (entry.reservation.updatedAt ?: entry.reservation.createdAt)?.let {
            Text("Updated: ${activityTime(it)}", color = AGray, fontSize = 12.sp)
        }
        ActivityButton(action, onClick)
    }
}

@Composable
private fun ActivityBadge(label: String) {
    val color = when (label) {
        "Cancelled", "Expired" -> Color(0xFFB93828)
        "Reserved • arrange pickup" -> Color(0xFF986000)
        else -> AGreen
    }
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(20.dp)) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = color, fontSize = 12.sp)
    }
}

@Composable
private fun ActivityButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, AGreen),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AGreen)) { Text(label) }
}

@Composable
private fun ActivityEmpty(title: String, description: String) {
    ActivityCard {
        Text(title, color = ADark, fontWeight = FontWeight.SemiBold)
        Text(description, color = AGray)
    }
}

private fun activityStatus(status: ReservationStatus): String = when (status) {
    ReservationStatus.RESERVED -> "Reserved • arrange pickup"
    ReservationStatus.CLAIMED -> "Completed"
    ReservationStatus.CANCELLED -> "Cancelled"
}

private fun activityTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault(value)
