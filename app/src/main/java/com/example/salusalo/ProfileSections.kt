package com.example.salusalo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
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
import com.example.salusalo.model.ResponseRate
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.model.reservationTotal
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.isExpired
import com.example.salusalo.model.hasPhoto
import androidx.compose.foundation.background
import coil3.compose.AsyncImage
import com.example.salusalo.data.PasswordChangeException
import com.example.salusalo.data.VerificationException
import com.example.salusalo.model.RatingSummary
import com.example.salusalo.model.ReviewItem
import com.example.salusalo.model.formatRatingAverage
import com.example.salusalo.model.reviewCountText
import com.example.salusalo.data.UserRepository
import com.example.salusalo.data.supabaseErrorText
import com.example.salusalo.model.UserProfile
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    responseRate: ResponseRate?,
    responseRateFailed: Boolean,
    onReloadResponseRate: () -> Unit,
    ratingSummary: RatingSummary?,
    receivedReviews: List<ReviewItem>?,
    reviewsFailed: Boolean,
    onReloadReviews: () -> Unit,
    notificationViewModel: NotificationViewModel,
    savedListingViewModel: SavedListingViewModel,
    onOpenListing: (FoodPost) -> Unit
) {
    val context = LocalContext.current

    var history by rememberSaveable {
        mutableStateOf(arrayListOf(initialPage))
    }
    val route = history.last()
    val page = route.substringBefore("|")
    val itemName = route.substringAfter("|", "")

    val userRepository = remember { UserRepository() }
    val scope = rememberCoroutineScope()
    val currentName = profile?.name.orEmpty()
    var feedback by rememberSaveable { mutableStateOf("") }

    var sound by rememberSaveable {
        mutableStateOf(notificationSoundOn(context))
    }

    // Alert preferences saved to the account in Supabase (Phase 4). The
    // recommendations and pickup_reminders columns stay but have no switch; a
    // save writes back the values it loaded. Notification sound stays on this phone.
    val serverPreferences by notificationViewModel.preferences.collectAsStateWithLifecycle()
    val preferenceError by notificationViewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(preferenceError, page) {
        if (page == "notifications" && preferenceError != null) {
            feedback = preferenceError.orEmpty()
            notificationViewModel.clearMessage()
        }
    }
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

    // Email verification (Firebase): sending the email and checking the link.
    var sendingEmail by remember { mutableStateOf(false) }
    var checkingEmail by remember { mutableStateOf(false) }

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
        setNotificationSoundOn(context, sound)
        feedback = "Preferences saved on this device."
    }

    val title = when (page) {
        "claims" -> "Successful Claims"
        "donations" -> "Successful Donations"
        "response" -> "Response Rate"
        "verification" -> "Email Verification"
        "saved" -> "Saved Listings"
        "notifications" -> "Notifications"
        "account" -> "Account & security"
        "security" -> "Account Security"
        "password" -> "Change Password"
        "edit_profile" -> "Edit Profile"
        "food_preferences" -> "Food preferences"
        "location" -> "Location & distance"
        "help" -> "Help & food safety"
        "about" -> "About Salu-Salo"
        "how_it_works" -> "How Salu-Salo works"
        "pickup_help" -> "Reservations & pickups"
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
                        IconButton(onClick = { back() }) {
                            Icon(Icons.Outlined.ArrowBack, "Back", tint = SectionGreen)
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
                                // Only this account's own numbers (get_my_response_rate).
                                LaunchedEffect(page) { onReloadResponseRate() }
                                val rate = responseRate
                                val days = rate?.periodDays ?: 30
                                SectionCard(pale = true) {
                                    Text(
                                        rate?.percent?.let { "$it%" } ?: "—",
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SectionGreen
                                    )
                                    Text(
                                        "Response rate",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("Last $days days")
                                }

                                when {
                                    rate == null && responseRateFailed -> {
                                        Text(
                                            "Your response rate could not be loaded. " +
                                                    "Check your internet connection and try again.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        SectionButton("Try again") { onReloadResponseRate() }
                                    }

                                    rate == null ->
                                        Text("Loading your response rate…", fontSize = 13.sp)

                                    rate.received == 0 ->
                                        SectionCard {
                                            Text(
                                                "No one has started a chat with you in the " +
                                                        "last $days days, so there is no rate yet."
                                            )
                                        }

                                    else -> SectionCard {
                                        Text("Your last $days days",
                                            fontWeight = FontWeight.Bold)
                                        Text("Chats started by others: ${rate.received}")
                                        Text("Chats you replied to: ${rate.replied}")
                                        Text("Chats without a reply: ${rate.received - rate.replied}")
                                        HorizontalDivider()
                                        val exact = rate.replied * 100 % rate.received == 0
                                        Text(
                                            "${rate.replied} ÷ ${rate.received} × 100 " +
                                                    "${if (exact) "=" else "≈"} ${rate.percent}%"
                                        )
                                    }
                                }

                                SectionCard {
                                    Text("How it works",
                                        fontWeight = FontWeight.Bold)
                                    Text(
                                        "Counts chats that someone else started with you in " +
                                                "the last $days days. A chat counts as replied once " +
                                                "you send a message after their first message. Chats " +
                                                "you started are not counted. A note sent with a " +
                                                "reservation starts a chat too. Only you can see " +
                                                "your response rate."
                                    )
                                }

                                SectionButton("Notification preferences") {
                                    navigate("notifications")
                                }
                            }

                            "verification" -> {
                                // The stored value, set only from the Firebase token.
                                val verified = profile?.emailVerified == true
                                val email = profile?.email.orEmpty()
                                SectionCard(pale = true) {
                                    Text(
                                        if (verified) "Email verified" else "Email not verified",
                                        fontSize = 23.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (verified) SectionGreen else SectionDark
                                    )
                                    Text(email)
                                    Text(
                                        "The badge only means that this email address was " +
                                                "confirmed. It is not an identity check.",
                                        fontSize = 12.sp
                                    )
                                }

                                if (!verified) {
                                    Text(
                                        "We'll send a link to $email. Open it, then come back " +
                                                "and tap \u201cI've confirmed my email\u201d.",
                                        fontSize = 12.sp
                                    )

                                    SectionButton(
                                        if (sendingEmail) "Sending…" else "Send verification email",
                                        enabled = !sendingEmail && !checkingEmail && profile != null
                                    ) {
                                        sendingEmail = true
                                        feedback = ""
                                        scope.launch {
                                            runCatching { userRepository.sendVerificationEmail() }
                                                .onSuccess {
                                                    feedback = "Verification email sent to $email. " +
                                                            "If you don't see it, check your spam folder."
                                                }
                                                .onFailure { error ->
                                                    feedback = (error as? VerificationException)?.message
                                                        ?: "The verification email could not be sent. " +
                                                                "Check your internet connection and try again."
                                                }
                                            sendingEmail = false
                                        }
                                    }

                                    SectionButton(
                                        if (checkingEmail) "Checking…" else "I've confirmed my email",
                                        enabled = !sendingEmail && !checkingEmail && profile != null
                                    ) {
                                        checkingEmail = true
                                        feedback = ""
                                        scope.launch {
                                            // Reloads the Firebase user and refreshes the token, so
                                            // the server reads the new email_verified claim.
                                            runCatching { userRepository.syncEmailVerified(forceRefresh = true) }
                                                .onSuccess { confirmed ->
                                                    profile?.let { onProfileUpdated(it.copy(emailVerified = confirmed)) }
                                                    feedback = if (confirmed) {
                                                        "Your email is confirmed. Your profile now shows " +
                                                                "the Email verified badge."
                                                    } else {
                                                        "Your email is not confirmed yet. Open the link " +
                                                                "in the email we sent, then try again."
                                                    }
                                                }
                                                .onFailure {
                                                    feedback = "Your email could not be checked. Check " +
                                                            "your internet connection and try again."
                                                }
                                            checkingEmail = false
                                        }
                                    }
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
                                        Text("Alert preferences have not loaded yet.", fontSize = 13.sp)
                                        TextButton(onClick = { notificationViewModel.loadPreferences() }) { Text("Retry") }
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
                                    "Choose which updates appear in Alerts. Sound plays while " +
                                            "Salu-Salo is open. Notifications while the app is " +
                                            "closed are not available yet.",
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
                                OutlinedButton(
                                    onClick = {
                                        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${context.packageName}"))
                                        val target = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        } else fallback
                                        try { context.startActivity(target) }
                                        catch (_: Exception) {
                                            try { context.startActivity(fallback) }
                                            catch (_: Exception) { feedback = "Open your phone Settings, then Apps, then Salu-Salo." }
                                        }
                                    }, modifier = Modifier.fillMaxWidth()
                                ) { Text("Open device notification settings") }
                            }

                            "edit_profile" -> ProfileEditor(profile, onProfileUpdated)
                            "account" -> AccountSecuritySettings(profile, ::navigate)
                            "food_preferences" -> FoodPreferenceSettings(profile?.id)
                            "location" -> LocationPreferenceSettings(profile?.id)
                            "help" -> HelpSettings(::navigate)
                            "about" -> AboutSettings(::navigate)
                            "how_it_works" -> HowItWorksSettings(pickups = false)
                            "pickup_help" -> HowItWorksSettings(pickups = true)

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

                            "settings" -> SettingsHub(::navigate)

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
                                    Text("What the Email verified badge means",
                                        fontWeight = FontWeight.Bold)
                                    Text(
                                        "It only shows that the person confirmed their email " +
                                                "address. It is not an identity check, and it does " +
                                                "not guarantee the food's quality or anyone's conduct."
                                    )
                                }

                                SectionCard {
                                    Text("Ratings and reviews",
                                        fontWeight = FontWeight.Bold)
                                    Text(
                                        "After a donor marks your pickup as claimed, you can rate " +
                                                "them from the reservation in Activity \u2192 Purchases " +
                                                "or Claims. Your name, stars and comment are shown to " +
                                                "other users."
                                    )
                                }

                                SectionLink(
                                    "Email verification",
                                    "Confirm your email address"
                                ) { navigate("verification") }

                                SectionLink(
                                    "Report a concern",
                                    "Where to find the Report buttons"
                                ) { navigate("report") }
                            }

                            "report" -> {
                                // Reports are sent from the listing, the Donor/Seller page
                                // or the chat; there is no report without a target.
                                SectionCard {
                                    Text(
                                        "Report a listing: open it and tap \u201cReport this " +
                                                "listing\u201d at the bottom of its details."
                                    )
                                    Text(
                                        "Report a donor or seller: open one of their listings, " +
                                                "tap the Donor or Seller row, then tap \u201cReport " +
                                                "<name>\u201d."
                                    )
                                    Text(
                                        "Report someone you chat with: open the chat and tap " +
                                                "the flag at the top right."
                                    )
                                }

                                Text(
                                    "Reports go to the Salu-Salo team. The person you report " +
                                            "is not told. You can send up to 10 reports a day.",
                                    fontSize = 12.sp
                                )
                            }

                            "reviews" -> {
                                // Reviews about this account (names only), newest first.
                                LaunchedEffect(page) { onReloadReviews() }
                                val summary = ratingSummary
                                val average = summary?.average
                                SectionCard(pale = true) {
                                    Text(
                                        average?.let { "\u2605 ${formatRatingAverage(it)}" } ?: "—",
                                        fontSize = 35.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SectionGreen
                                    )
                                    if (summary != null) {
                                        Text(
                                            if (summary.count > 0) reviewCountText(summary.count)
                                            else "No reviews yet"
                                        )
                                    }
                                    Text(
                                        "Reviews from people who picked up your food.",
                                        fontSize = 12.sp
                                    )
                                }

                                val reviews = receivedReviews
                                when {
                                    reviews == null && reviewsFailed -> {
                                        Text(
                                            "Your reviews could not be loaded. " +
                                                    "Check your internet connection and try again.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        SectionButton("Try again") { onReloadReviews() }
                                    }

                                    reviews == null ->
                                        Text("Loading your reviews…", fontSize = 13.sp)

                                    reviews.isEmpty() ->
                                        SectionCard { Text(NO_RATING_LINE) }

                                    else -> {
                                        reviews.forEach { review ->
                                            SectionCard { ReviewRow(review) }
                                        }
                                        if ((summary?.count ?: 0) > reviews.size) {
                                            Text(
                                                "Showing your latest ${reviews.size} reviews.",
                                                fontSize = 12.sp
                                            )
                                        }
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
                                    navigate("edit_profile")
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
    Surface(shape = RoundedCornerShape(16.dp)) {
        ListingPhoto(
            imageUrl = post.displayImageUrl,
            hasPhoto = post.hasPhoto,
            contentDescription = post.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp),
            textSize = 12.sp
        )
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
