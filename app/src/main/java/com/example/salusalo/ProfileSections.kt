package com.example.salusalo

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.salusalo.viewmodel.NotificationViewModel
import com.example.salusalo.viewmodel.SavedListingViewModel
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.ReservationDisplay
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.model.reservationTotal
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.isExpired
import androidx.compose.foundation.background
import coil3.compose.AsyncImage
import com.example.salusalo.data.PasswordChangeException
import com.example.salusalo.data.UserRepository
import com.example.salusalo.data.supabaseErrorText
import com.example.salusalo.model.UserProfile
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val SectionGreen = Color(0xFF347523)
private val SectionCream = Color(0xFFFCFBF6)
private val SectionPale = Color(0xFFEEF4E4)
private val SectionDark = Color(0xFF213027)

@Composable
fun ProfileSections(
    initialPage: String,
    profile: UserProfile?,
    onProfileUpdated: (UserProfile) -> Unit,
    onClose: () -> Unit,
    successfulClaims: List<ReservationDisplay>,
    successfulDonations: List<ReservationDisplay>,
    recordsLoaded: Boolean,
    recordsFailed: Boolean,
    onReloadRecords: () -> Unit,
    notificationViewModel: NotificationViewModel,
    savedListingViewModel: SavedListingViewModel,
    onOpenListing: (FoodPost) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("salusalo_profile", 0)
    }

    var history by rememberSaveable {
        mutableStateOf(arrayListOf(initialPage))
    }
    val route = history.last()
    val page = route.substringBefore("|")
    val itemName = route.substringAfter("|", "")

    val userRepository = remember { UserRepository() }
    val scope = rememberCoroutineScope()
    val currentName = profile?.name.orEmpty()
    var name by rememberSaveable { mutableStateOf(currentName) }
    var savingName by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf("") }

    var sound by rememberSaveable {
        mutableStateOf(prefs.getBoolean("notificationSound", true))
    }

    // Alert preferences saved to the account in Supabase (Phase 4). The
    // recommendations and pickup_reminders columns stay but have no switch; a
    // save writes back the values it loaded. Notification sound stays on this phone.
    val serverPreferences by notificationViewModel.preferences.collectAsStateWithLifecycle()
    var messageAlerts by rememberSaveable { mutableStateOf(true) }
    var reservationAlerts by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(page) {
        if (page == "notifications") notificationViewModel.loadPreferences()
    }
    LaunchedEffect(serverPreferences) {
        serverPreferences?.let {
            messageAlerts = it.messageAlerts
            reservationAlerts = it.reservationAlerts
        }
    }

    var verificationRequested by rememberSaveable {
        mutableStateOf(prefs.getBoolean("demoVerificationRequested", false))
    }

    // Passwords are kept only while this page is open.
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var changingPassword by remember { mutableStateOf(false) }

    val savedRows by savedListingViewModel.saved.collectAsStateWithLifecycle()
    val savedLoading by savedListingViewModel.loading.collectAsStateWithLifecycle()
    val savedLoadFailed by savedListingViewModel.loadFailed.collectAsStateWithLifecycle()
    // Saved Listings reloads each time it opens; bookmarks don't update live.
    LaunchedEffect(page) { if (page == "saved") savedListingViewModel.load() }

    fun navigate(destination: String) {
        feedback = ""
        history = ArrayList(history).apply { add(destination) }
    }

    fun back() {
        feedback = ""
        if (history.size > 1) {
            history = ArrayList(history.dropLast(1))
        } else {
            onClose()
        }
    }

    fun savePreferences() {
        prefs.edit()
            .putBoolean("notificationSound", sound)
            .apply()

        feedback = "Preferences saved on this device."
    }

    val title = when (page) {
        "claims" -> "Successful Claims"
        "donations" -> "Successful Donations"
        "response" -> "Response Rate"
        "verification" -> "Manage Verification"
        "verificationSteps" -> "Verification Process"
        "verificationStatus" -> "Request Status"
        "saved" -> "Saved Listings"
        "notifications" -> "Notification Preferences"
        "account" -> "Account Details"
        "security" -> "Account Security"
        "password" -> "Change Password"
        "settings" -> "Settings"
        "safety" -> "Trust & Safety"
        "report" -> "Report a Concern"
        "reviews" -> "Ratings & Reviews"
        "membership" -> "Membership"
        "record" -> (successfulClaims + successfulDonations)
            .firstOrNull { it.reservation.id == itemName }?.foodPost?.name ?: "Record"
        else -> "Profile"
    }

    Dialog(
        onDismissRequest = { back() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SectionCream
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
                            .padding(8.dp)
                    ) {
                        TextButton(onClick = { back() }) {
                            Text("Back", color = SectionGreen)
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                title,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = SectionDark
                            )
                            Text(
                                "Salu-Salo",
                                fontSize = 11.sp,
                                color = SectionGreen
                            )
                        }
                    }
                }

                // Each destination starts at the top of its content.
                key(route) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (page) {
                            "claims", "donations" -> {
                                val claims = page == "claims"
                                val records = (if (claims) successfulClaims else successfulDonations)
                                    .sortedByDescending { it.reservation.updatedAt ?: it.reservation.createdAt.orEmpty() }
                                LaunchedEffect(page) { onReloadRecords() }
                                SectionCard(pale = true) {
                                    Text(
                                        if (recordsLoaded) records.size.toString() else "—",
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SectionGreen
                                    )
                                    Text(
                                        if (claims) "Successful claims" else "Successful donations",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (claims) {
                                            "Food you picked up that donors marked as claimed, free and paid."
                                        } else {
                                            "Reservations on your listings that you marked as claimed."
                                        },
                                        fontSize = 12.sp
                                    )
                                }

                                when {
                                    recordsFailed -> Text(
                                        "Your records could not be loaded. Check your internet connection and try again.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    !recordsLoaded -> Text("Loading your records…", fontSize = 13.sp)
                                    records.isEmpty() -> Text(
                                        if (claims) {
                                            "No successful claims yet. They appear here after a donor marks your reservation as claimed."
                                        } else {
                                            "No successful donations yet. They appear here after you mark a reservation on your listing as claimed."
                                        },
                                        fontSize = 13.sp
                                    )
                                }

                                records.forEach { entry ->
                                    SectionLink(
                                        title = entry.foodPost.name,
                                        subtitle = successRecordLines(entry, showRecipient = !claims)
                                    ) {
                                        navigate("record|${entry.reservation.id}")
                                    }
                                }
                            }

                            "record" -> {
                                val donation = successfulDonations.firstOrNull { it.reservation.id == itemName }
                                val entry = donation ?: successfulClaims.firstOrNull { it.reservation.id == itemName }
                                if (entry == null) {
                                    Text("This record is no longer available.", fontSize = 13.sp)
                                } else {
                                    val post = entry.foodPost
                                    SavedListingPhoto(post)

                                    SectionCard {
                                        Text(
                                            "Claimed",
                                            color = SectionGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(post.name, fontWeight = FontWeight.SemiBold)
                                        Text(successRecordLines(entry, showRecipient = donation != null))
                                        Text("Pickup location: ${post.pickupLocation}", fontSize = 12.sp)
                                    }

                                    // Only a listing that can still be reserved opens.
                                    if (post.status == FoodPostStatus.AVAILABLE && !post.isExpired()) {
                                        SectionButton("View listing") {
                                            onOpenListing(post)
                                        }
                                    }
                                }
                            }

                            "response" -> {
                                SectionCard(pale = true) {
                                    Text(
                                        "92%",
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SectionGreen
                                    )
                                    Text(
                                        "Response rate",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("Demo reporting period: last 30 days")
                                }

                                SectionCard {
                                    Text("Example response summary",
                                        fontWeight = FontWeight.Bold)
                                    Text("Conversations received: 50")
                                    Text("Conversations replied to: 46")
                                    Text("Conversations without a reply: 4")
                                    HorizontalDivider()
                                    Text("46 ÷ 50 × 100 = 92%")
                                }

                                SectionCard {
                                    Text("How it works",
                                        fontWeight = FontWeight.Bold)
                                    Text(
                                        "This example measures the share of " +
                                                "received conversations that got a reply. " +
                                                "Your actual messages are not being tracked."
                                    )
                                }

                                SectionButton("Notification preferences") {
                                    navigate("notifications")
                                }
                            }

                            "verification" -> {
                                SectionCard(pale = true) {
                                    Text(
                                        "Identity Verified",
                                        fontSize = 23.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SectionGreen
                                    )
                                    Text("Demo badge • Mar 15, 2023")
                                    Text(
                                        "This example badge reproduces the profile " +
                                                "design. No identity review has taken place.",
                                        fontSize = 12.sp
                                    )
                                }

                                SectionLink(
                                    "How verification works",
                                    "See the proposed review process"
                                ) {
                                    navigate("verificationSteps")
                                }

                                SectionLink(
                                    "Verification request",
                                    if (verificationRequested) {
                                        "Demo request pending"
                                    } else {
                                        "Try the request preview"
                                    }
                                ) {
                                    navigate("verificationStatus")
                                }
                            }

                            "verificationSteps" -> {
                                SectionCard {
                                    Text("1. Submit account information",
                                        fontWeight = FontWeight.Bold)
                                    Text("A future verification flow would collect " +
                                            "the information required for review.")

                                    Text("2. Review",
                                        fontWeight = FontWeight.Bold)
                                    Text("An authorized reviewer would check the request.")

                                    Text("3. Receive a result",
                                        fontWeight = FontWeight.Bold)
                                    Text("The account would show its actual review status.")
                                }

                                Text(
                                    "This prototype uses a demo request and does " +
                                            "not collect identity documents.",
                                    fontSize = 12.sp
                                )

                                SectionButton("Try demo request") {
                                    navigate("verificationStatus")
                                }
                            }

                            "verificationStatus" -> {
                                SectionCard {
                                    Text(
                                        if (verificationRequested) {
                                            "Demo request pending"
                                        } else {
                                            "No demo request yet"
                                        },
                                        fontSize = 21.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "No documents will be sent. This only " +
                                                "changes the local demonstration status."
                                    )
                                }

                                SectionButton(
                                    if (verificationRequested) {
                                        "Reset demo request"
                                    } else {
                                        "Submit demo request"
                                    }
                                ) {
                                    verificationRequested = !verificationRequested
                                    prefs.edit()
                                        .putBoolean(
                                            "demoVerificationRequested",
                                            verificationRequested
                                        )
                                        .apply()
                                }
                            }

                            "saved" -> {
                                Text(
                                    "Listings you bookmarked, newest first.",
                                    fontSize = 12.sp
                                )

                                when {
                                    savedRows.isEmpty() && savedLoading ->
                                        Text("Loading your saved listings…", fontSize = 13.sp)

                                    savedRows.isEmpty() && savedLoadFailed ->
                                        Text(
                                            "Your saved listings could not be loaded. " +
                                                    "Check your internet connection and try again.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )

                                    savedRows.isEmpty() ->
                                        SectionCard {
                                            Text(
                                                "You have no saved listings yet. Tap the " +
                                                        "bookmark on a listing to save it here."
                                            )
                                        }
                                }

                                savedRows.forEach { row ->
                                    val post = row.foodPost
                                    // Gone, fully reserved or expired: it can only be removed.
                                    val available = post != null &&
                                            post.status == FoodPostStatus.AVAILABLE &&
                                            !post.isExpired()
                                    SectionCard {
                                        if (post != null) {
                                            SavedListingPhoto(post)
                                            Text(post.name, fontWeight = FontWeight.Bold)
                                            Text(
                                                post.donorName?.takeIf { it.isNotBlank() }
                                                    ?: "Donor name unavailable",
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                "${post.quantity} ${post.unit} • " +
                                                        if (post.price > 0) formatPeso(post.price) else "Free",
                                                fontSize = 12.sp
                                            )
                                        } else {
                                            // A listing this account can no longer see: its
                                            // name and cover from get_my_saved_listing_names().
                                            SavedFallbackPhoto(row.fallbackImageUrl, row.fallbackName)
                                            Text(
                                                row.fallbackName ?: "Saved listing",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (available) {
                                            SectionButton("View listing") {
                                                onOpenListing(post!!)
                                            }
                                        } else {
                                            Text(
                                                "No longer available",
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        TextButton(
                                            onClick = { savedListingViewModel.remove(row.foodPostId) }
                                        ) {
                                            Text("Remove")
                                        }
                                    }
                                }
                            }

                            "notifications" -> {
                                SectionCard {
                                    if (serverPreferences == null) {
                                        Text("Loading your alert settings…", fontSize = 13.sp)
                                    } else {
                                        SectionToggle(
                                            "New messages",
                                            messageAlerts
                                        ) { messageAlerts = it }

                                        SectionToggle(
                                            "Reservation updates",
                                            reservationAlerts
                                        ) { reservationAlerts = it }
                                    }

                                    SectionToggle(
                                        "Notification sound",
                                        sound
                                    ) { sound = it }
                                }

                                Text(
                                    "New messages and Reservation updates are saved to your " +
                                            "account and decide which alerts you get on the Alerts " +
                                            "page. Notification sound is saved on this phone: it " +
                                            "plays the phone's notification sound when a new alert " +
                                            "arrives while the app is open, and stays quiet in " +
                                            "silent and vibrate mode. Phone notifications while " +
                                            "the app is closed are not available yet.",
                                    fontSize = 12.sp
                                )

                                SectionButton(
                                    "Save preferences",
                                    enabled = serverPreferences != null
                                ) {
                                    savePreferences()
                                    val base = serverPreferences
                                    if (base == null) {
                                        feedback = "Could not load your account's alert settings. " +
                                                "Notification sound was saved on this phone."
                                    } else {
                                        notificationViewModel.savePreferences(
                                            // recommendations and pickupReminders keep
                                            // the values loaded from the account.
                                            base.copy(
                                                messageAlerts = messageAlerts,
                                                reservationAlerts = reservationAlerts
                                            )
                                        ) { feedback = "Preferences saved to your account." }
                                    }
                                }
                            }

                            "account" -> {
                                SectionCard {
                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = { Text("Display name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    // The sign-in email comes from the account and cannot be edited here.
                                    OutlinedTextField(
                                        value = profile?.email.orEmpty(),
                                        onValueChange = {},
                                        label = { Text("Email (used to sign in)") },
                                        singleLine = true,
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                SectionButton(
                                    "Save account details",
                                    enabled = !savingName && profile != null
                                ) {
                                    val trimmed = name.trim()
                                    when {
                                        trimmed.isEmpty() -> feedback = "Enter your name."
                                        trimmed.length > 120 ->
                                            feedback = "Your name must be 120 characters or fewer."
                                        else -> {
                                            savingName = true
                                            feedback = ""
                                            scope.launch {
                                                runCatching { userRepository.updateMyName(trimmed) }
                                                    .onSuccess { updated ->
                                                        name = updated.name
                                                        onProfileUpdated(updated)
                                                        feedback = "Saved to your account."
                                                    }
                                                    .onFailure { error ->
                                                        feedback = supabaseErrorText(
                                                            error,
                                                            "Your name could not be saved. Check " +
                                                                    "your internet connection and try again."
                                                        )
                                                    }
                                                savingName = false
                                            }
                                        }
                                    }
                                }

                                SectionLink(
                                    "Account security",
                                    "Change your password"
                                ) { navigate("security") }
                            }

                            "security" -> {
                                SectionLink(
                                    "Change password",
                                    "Update the password you sign in with"
                                ) { navigate("password") }
                            }

                            "password" -> {
                                if (!userRepository.hasPassword()) {
                                    SectionCard(pale = true) {
                                        Text(
                                            "This account signs in with Google, " +
                                                    "so it has no password to change."
                                        )
                                    }
                                } else {
                                    SectionCard {
                                        OutlinedTextField(
                                            value = currentPassword,
                                            onValueChange = { currentPassword = it },
                                            label = { Text("Current password") },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = newPassword,
                                            onValueChange = { newPassword = it },
                                            label = { Text("New password") },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = confirmPassword,
                                            onValueChange = { confirmPassword = it },
                                            label = { Text("Confirm new password") },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    SectionButton(
                                        "Change password",
                                        enabled = !changingPassword
                                    ) {
                                        // Same rules and texts as sign-up.
                                        when {
                                            currentPassword.isEmpty() ->
                                                feedback = "Enter your current password."
                                            newPassword.length < 6 ->
                                                feedback = "Your password must be at least 6 characters."
                                            newPassword != confirmPassword ->
                                                feedback = "Your passwords do not match."
                                            else -> {
                                                changingPassword = true
                                                feedback = ""
                                                scope.launch {
                                                    runCatching {
                                                        userRepository.changePassword(
                                                            currentPassword,
                                                            newPassword
                                                        )
                                                    }.onSuccess {
                                                        currentPassword = ""
                                                        newPassword = ""
                                                        confirmPassword = ""
                                                        feedback = "Your password was changed."
                                                    }.onFailure { error ->
                                                        feedback = (error as? PasswordChangeException)?.message
                                                            ?: "Your password could not be changed. Check " +
                                                            "your internet connection and try again."
                                                    }
                                                    changingPassword = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "settings" -> {
                                SectionLink(
                                    "Account details",
                                    "Edit your display name"
                                ) { navigate("account") }

                                SectionLink(
                                    "Security",
                                    "Change your password"
                                ) { navigate("security") }

                                SectionLink(
                                    "Notifications",
                                    "Message and reservation alerts, sound"
                                ) { navigate("notifications") }

                                SectionLink(
                                    "Verification",
                                    "Review the example verification process"
                                ) { navigate("verification") }

                                SectionLink(
                                    "Trust & safety",
                                    "Community information and reporting"
                                ) { navigate("safety") }
                            }

                            "safety" -> {
                                SectionCard {
                                    Text("Coordinate clearly",
                                        fontWeight = FontWeight.Bold)
                                    Text(
                                        "Agree on the pickup location, quantity, " +
                                                "and collection time."
                                    )
                                }

                                SectionCard {
                                    Text("Understand verification",
                                        fontWeight = FontWeight.Bold)
                                    Text(
                                        "A verification badge does not guarantee " +
                                                "food quality or another person's conduct."
                                    )
                                }

                                SectionLink(
                                    "How verification works",
                                    "Learn about the proposed review process"
                                ) { navigate("verificationSteps") }

                                SectionLink(
                                    "Report a concern",
                                    "Preview the reporting form"
                                ) { navigate("report") }
                            }

                            "report" -> {
                                var reason by rememberSaveable {
                                    mutableStateOf("")
                                }

                                SectionCard {
                                    OutlinedTextField(
                                        value = reason,
                                        onValueChange = { reason = it },
                                        label = { Text("Describe the concern") },
                                        placeholder = {
                                            Text("e.g., The pickup details were incorrect")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 4
                                    )
                                }

                                SectionButton("Preview report") {
                                    feedback = if (reason.isBlank()) {
                                        "Describe the concern first."
                                    } else {
                                        "Report preview ready. Nothing was submitted."
                                    }
                                }
                            }

                            "reviews" -> {
                                SectionCard(pale = true) {
                                    Text(
                                        "★ 4.8",
                                        fontSize = 35.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SectionGreen
                                    )
                                    Text("56 reviews • demo summary")
                                }

                                listOf(
                                    "Juan R." to "Very responsive and easy to coordinate with.",
                                    "Maria C." to "Smooth transaction. Thank you!",
                                    "Luis P." to "Great donor! Items were as described.",
                                    "Anna S." to "Pickup instructions were clear.",
                                    "Rico D." to "Friendly and helpful."
                                ).forEach { (reviewer, comment) ->
                                    SectionCard {
                                        Text(reviewer, fontWeight = FontWeight.Bold)
                                        Text("★★★★★", color = Color(0xFFFFAD08))
                                        Text(comment)
                                        Text("Example review", fontSize = 10.sp)
                                    }
                                }
                            }

                            "membership" -> {
                                SectionCard {
                                    Text(
                                        "Member since ${formatMemberSince(profile?.createdAt, "MMMM yyyy")}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("Account name: $currentName")
                                }

                                SectionButton("Edit account details") {
                                    navigate("account")
                                }
                            }
                        }

                        if (feedback.isNotBlank()) {
                            SectionCard(pale = true) {
                                Text(feedback)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    pale: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (pale) SectionPale else Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SectionLink(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, fontSize = 12.sp)
            }
            Text("›", fontSize = 25.sp, color = SectionGreen)
        }
    }
}

@Composable
private fun SectionButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SectionGreen
        )
    ) {
        Text(text)
    }
}

@Composable
private fun SectionToggle(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            title,
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** The cover of a saved listing that no longer loads, or a plain tile. */
@Composable
private fun SavedFallbackPhoto(imageUrl: String?, name: String?) {
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val modifier = Modifier
        .fillMaxWidth()
        .height(170.dp)
    Surface(shape = RoundedCornerShape(16.dp)) {
        if (imageUrl != null && !imageFailed) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = modifier,
                onError = { imageFailed = true }
            )
        } else {
            Box(modifier.background(SectionPale))
        }
    }
}

/** A saved listing's cover, with the same fallbacks as the Home cards. */
@Composable
private fun SavedListingPhoto(post: FoodPost) {
    val imageUrl = post.displayImageUrl
    var imageFailed by remember(post.id, imageUrl) { mutableStateOf(false) }
    val modifier = Modifier
        .fillMaxWidth()
        .height(170.dp)
    Surface(shape = RoundedCornerShape(16.dp)) {
        if (imageUrl != null && !imageFailed) {
            AsyncImage(
                model = imageUrl,
                contentDescription = post.name,
                contentScale = ContentScale.Crop,
                modifier = modifier,
                onError = { imageFailed = true }
            )
        } else if (post.imageUrl.isNullOrBlank()) {
            Image(
                painter = painterResource(R.drawable.food_produce),
                contentDescription = post.name,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        } else {
            Box(
                modifier = modifier.background(SectionPale),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { Text("Image unavailable", fontSize = 12.sp) }
        }
    }
}

/**
 * "3 packs • ₱135.30" (or "• Free"), "Picked up Sep 26, 2026 • 3:00 PM" and,
 * for a donation, "Recipient: <name>".
 */
private fun successRecordLines(entry: ReservationDisplay, showRecipient: Boolean): String {
    val reservation = entry.reservation
    val amount = if (reservation.unitPrice > 0) {
        formatPeso(reservationTotal(reservation.unitPrice, reservation.quantity))
    } else {
        "Free"
    }
    val pickedUp = parseTimestamp(reservation.updatedAt)?.let {
        java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a", java.util.Locale.ENGLISH)
            .withZone(java.time.ZoneId.systemDefault())
            .format(it)
    }
    return buildString {
        append("${reservation.quantity} ${entry.foodPost.unit} • $amount")
        pickedUp?.let { append("\nPicked up $it") }
        if (showRecipient) append("\nRecipient: ${entry.recipientName ?: "Unknown"}")
    }
}
