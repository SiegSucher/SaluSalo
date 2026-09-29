package com.example.salusalo

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import coil3.compose.LocalPlatformContext
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val SectionGreen = SaluColors.Green
private val SectionCream = SaluColors.Cream
private val SectionPale = SaluColors.PaleGreen
private val SectionDark = SaluColors.DarkGreen

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
        properties = EdgeToEdgeDialog
    ) {
        DarkSystemBarIcons()
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
                SaluTopBar(title = title, onBack = { back() })

                // Each destination starts at the top of its content.
                key(route) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                    recordsFailed -> InlineNotice(
                                        text = "Your records could not be loaded. Check your internet connection and try again.",
                                        icon = Icons.Outlined.ErrorOutline,
                                        tone = NoticeTone.Error
                                    )
                                    !recordsLoaded -> SkeletonGroup(description = "Loading your records") {
                                        repeat(3) { ListRowSkeleton() }
                                    }
                                    records.isEmpty() -> EmptyState(
                                        icon = if (claims) Icons.Outlined.Redeem else Icons.Outlined.VolunteerActivism,
                                        title = if (claims) "No successful claims yet" else "No successful donations yet",
                                        message = if (claims) {
                                            "They appear here after a donor marks your reservation as claimed."
                                        } else {
                                            "They appear here after you mark a reservation on your listing as claimed."
                                        }
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
                                    EmptyState(
                                        icon = Icons.Outlined.SearchOff,
                                        title = "This record is no longer available.",
                                        message = "Go back to see your other records."
                                    )
                                } else {
                                    val post = entry.foodPost
                                    SavedListingPhoto(post, large = true)

                                    SectionCard {
                                        StatusBadge(SaluStatus.Claimed)
                                        Text(post.name, style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
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
                                        InlineNotice(
                                            text = "Your response rate could not be loaded. " +
                                                    "Check your internet connection and try again.",
                                            icon = Icons.Outlined.ErrorOutline,
                                            tone = NoticeTone.Error
                                        )
                                        SectionButton("Try again") { onReloadResponseRate() }
                                    }

                                    rate == null -> SkeletonGroup(description = "Loading your response rate") {
                                        SkeletonBlock(Modifier.fillMaxWidth().height(96.dp), SaluRadius.Card)
                                    }

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
                                    style = SaluText.Supporting,
                                    color = SaluColors.Gray
                                )

                                when {
                                    savedRows.isEmpty() && savedLoading ->
                                        SkeletonGroup(description = "Loading your saved listings") {
                                            repeat(3) { ListRowSkeleton(thumbnail = 72.dp) }
                                        }

                                    savedRows.isEmpty() && savedLoadFailed ->
                                        InlineNotice(
                                            text = "Your saved listings could not be loaded. " +
                                                    "Check your internet connection and try again.",
                                            icon = Icons.Outlined.ErrorOutline,
                                            tone = NoticeTone.Error
                                        )

                                    savedRows.isEmpty() ->
                                        EmptyState(
                                            icon = Icons.Outlined.BookmarkBorder,
                                            title = "You have no saved listings yet",
                                            message = "Tap the bookmark on a listing to save it here.",
                                                                                    )
                                }

                                savedRows.forEach { row ->
                                    val post = row.foodPost
                                    // Gone, fully reserved or expired: it can only be removed.
                                    val available = post != null &&
                                            post.status == FoodPostStatus.AVAILABLE &&
                                            !post.isExpired()
                                    SectionCard {
                                        Row(verticalAlignment = Alignment.Top) {
                                            if (post != null) {
                                                SavedListingPhoto(post)
                                            } else {
                                                // A listing this account can no longer see: its
                                                // name and cover from get_my_saved_listing_names().
                                                SavedFallbackPhoto(row.fallbackImageUrl, row.fallbackName)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    post?.name ?: row.fallbackName ?: "Saved listing",
                                                    style = SaluText.FoodTitle,
                                                    color = SaluColors.DarkGreen
                                                )
                                                if (post != null) {
                                                    Text(
                                                        post.donorName?.takeIf { it.isNotBlank() }
                                                            ?: "Donor name unavailable",
                                                        style = SaluText.Supporting,
                                                        color = SaluColors.Gray
                                                    )
                                                    Text(
                                                        "${post.quantity} ${post.unit} • " +
                                                                if (post.price > 0) formatPeso(post.price) else "Free",
                                                        style = SaluText.Meta,
                                                        color = SaluColors.DarkGreen
                                                    )
                                                }
                                                if (!available) {
                                                    Text(
                                                        "No longer available",
                                                        style = SaluText.Status,
                                                        color = SaluColors.Error
                                                    )
                                                }
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (available) {
                                                SaluPrimaryButton(
                                                    text = "View listing",
                                                    onClick = { onOpenListing(post!!) },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            } else {
                                                Spacer(Modifier.weight(1f))
                                            }
                                            TextButton(
                                                onClick = { savedListingViewModel.remove(row.foodPostId) }
                                            ) {
                                                Text("Remove", color = SaluColors.Error)
                                            }
                                        }
                                    }
                                }
                            }

                            "notifications" -> {
                                SectionCard {
                                    if (serverPreferences == null) {
                                        Text("Alert preferences have not loaded yet.", style = SaluText.Body, color = SaluColors.Gray)
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
                                SaluSecondaryButton(
                                    text = "Open device notification settings",
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
                                    }
                                )
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
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = SaluRadius.Control,
                                            colors = saluFieldColors()
                                        )
                                        OutlinedTextField(
                                            value = newPassword,
                                            onValueChange = { newPassword = it },
                                            label = { Text("New password") },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = SaluRadius.Control,
                                            colors = saluFieldColors()
                                        )
                                        OutlinedTextField(
                                            value = confirmPassword,
                                            onValueChange = { confirmPassword = it },
                                            label = { Text("Confirm new password") },
                                            singleLine = true,
                                            visualTransformation = PasswordVisualTransformation(),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = SaluRadius.Control,
                                            colors = saluFieldColors()
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
                                                "them from the reservation in Activity \u2192 History. " +
                                                "Your name, stars and comment are shown to " +
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
                                        InlineNotice(
                                            text = "Your reviews could not be loaded. " +
                                                    "Check your internet connection and try again.",
                                            icon = Icons.Outlined.ErrorOutline,
                                            tone = NoticeTone.Error
                                        )
                                        SectionButton("Try again") { onReloadReviews() }
                                    }

                                    reviews == null -> SkeletonGroup(description = "Loading your reviews") {
                                        repeat(3) { ListRowSkeleton(thumbnail = 36.dp, round = true) }
                                    }

                                    reviews.isEmpty() -> EmptyState(
                                        icon = Icons.Outlined.StarOutline,
                                        title = "No reviews yet",
                                        message = NO_RATING_LINE
                                    )

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
                            InlineNotice(text = feedback, icon = Icons.Outlined.Info)
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
    SaluCard(
        modifier = Modifier.fillMaxWidth(),
        color = if (pale) SectionPale else Color.White,
        border = !pale
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
    SaluCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = SaluText.Supporting, color = SaluColors.Gray)
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = SaluColors.Gray
            )
        }
    }
}

@Composable
private fun SectionButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    SaluPrimaryButton(text = text, onClick = onClick, enabled = enabled)
}

@Composable
private fun SectionToggle(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = SaluText.Body.copy(fontSize = 15.sp),
            color = SaluColors.DarkGreen
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** The cover of a saved listing that no longer loads, or a plain tile. */
@Composable
private fun SavedFallbackPhoto(imageUrl: String?, name: String?) {
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val modifier = Modifier
        .size(72.dp)
        .clip(SaluRadius.Small)
    if (imageUrl != null && !imageFailed) {
        val context = LocalPlatformContext.current
        AsyncImage(
            model = remember(imageUrl, context) { signedImageRequest(context, imageUrl) },
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
            onError = { imageFailed = true }
        )
    } else {
        Box(modifier.background(SaluColors.PaleGreen))
    }
}

/** A listing's cover, with the same fallbacks as the Home cards: a thumbnail, or [large] across the page. */
@Composable
private fun SavedListingPhoto(post: FoodPost, large: Boolean = false) {
    ListingPhoto(
        imageUrl = post.displayImageUrl,
        hasPhoto = post.hasPhoto,
        contentDescription = post.name,
        modifier = if (large) {
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(SaluRadius.Card)
        } else {
            Modifier
                .size(72.dp)
                .clip(SaluRadius.Small)
        },
        textSize = if (large) 12.sp else 10.sp
    )
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
