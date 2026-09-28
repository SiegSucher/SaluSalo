package com.example.salusalo

import com.example.salusalo.viewmodel.SavedListingViewModel
import com.example.salusalo.model.FoodPost
import com.example.salusalo.viewmodel.NotificationViewModel
import com.example.salusalo.viewmodel.ReservationViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.salusalo.model.ReservationStatus
import com.example.salusalo.data.MessageRepository
import com.example.salusalo.data.ReviewRepository
import com.example.salusalo.model.RatingSummary
import com.example.salusalo.model.ReviewItem
import com.example.salusalo.model.formatRatingAverage
import com.example.salusalo.data.UserRepository
import com.example.salusalo.data.encodeProfilePhoto
import com.example.salusalo.data.supabaseErrorText
import com.example.salusalo.model.ResponseRate
import com.example.salusalo.model.UserProfile
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileScreen(
    onAlerts: () -> Unit,
    onLogout: () -> Unit,
    notificationViewModel: NotificationViewModel,
    reservationViewModel: ReservationViewModel,
    remoteProfile: UserProfile? = null,
    onProfileUpdated: (UserProfile) -> Unit = {},
    savedListingViewModel: SavedListingViewModel,
    onOpenListing: (FoodPost) -> Unit
) {
    val context = LocalContext.current

    // Successful Claims and Donations are this account's claimed reservations,
    // as recipient and as donor; together they equal Activity's Completed.
    val myReservations by reservationViewModel.myReservations.collectAsStateWithLifecycle()
    val donorReservations by reservationViewModel.donorReservations.collectAsStateWithLifecycle()
    val reservationsLoaded by reservationViewModel.loaded.collectAsStateWithLifecycle()
    val reservationsFailed by reservationViewModel.loadFailed.collectAsStateWithLifecycle()
    val successfulClaims = myReservations.filter { it.reservation.status == ReservationStatus.CLAIMED }
    val successfulDonations = donorReservations.filter { it.reservation.status == ReservationStatus.CLAIMED }
    LaunchedEffect(Unit) { reservationViewModel.refresh() }

    // Name, photo and member-since date come only from the Supabase profile.
    val userRepository = remember { UserRepository() }
    val scope = rememberCoroutineScope()
    val displayName = remoteProfile?.name.orEmpty()
    val memberSince = formatMemberSince(remoteProfile?.createdAt, "MMM yyyy")
    val photoPath = remoteProfile?.profileImageUrl
    // Each upload has a new path, so keying on the path fetches a fresh URL.
    val photoUrl by produceState<String?>(null, photoPath) {
        value = userRepository.profilePhotoUrl(photoPath)
    }
    var uploadingPhoto by remember { mutableStateOf(false) }

    var page by rememberSaveable { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf("") }

    // This account's own response rate (get_my_response_rate), loaded when
    // Profile opens and again when the Response Rate page opens. A reload keeps
    // the last numbers until it finishes; a failed load clears them.
    val messageRepository = remember { MessageRepository() }
    var responseRate by remember { mutableStateOf<ResponseRate?>(null) }
    var responseRateLoading by remember { mutableStateOf(false) }
    var responseRateFailed by remember { mutableStateOf(false) }
    fun loadResponseRate() {
        if (responseRateLoading) return
        responseRateLoading = true
        responseRateFailed = false
        scope.launch {
            try {
                responseRate = messageRepository.getMyResponseRate()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                responseRate = null
                responseRateFailed = true
            } finally {
                responseRateLoading = false
            }
        }
    }
    LaunchedEffect(remoteProfile?.id) { loadResponseRate() }

    // This account's own rating and the reviews about it (names only), loaded
    // when Profile opens and again when the reviews page opens. A reload keeps
    // the last values until it finishes; a failed load clears them.
    val reviewRepository = remember { ReviewRepository() }
    var ratingSummary by remember { mutableStateOf<RatingSummary?>(null) }
    var receivedReviews by remember { mutableStateOf<List<ReviewItem>?>(null) }
    var reviewsLoading by remember { mutableStateOf(false) }
    var reviewsFailed by remember { mutableStateOf(false) }
    fun loadReviews() {
        if (reviewsLoading) return
        reviewsLoading = true
        reviewsFailed = false
        scope.launch {
            try {
                ratingSummary = reviewRepository.getMyRatingSummary()
                receivedReviews = reviewRepository.getMyReceivedReviews()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ratingSummary = null
                receivedReviews = null
                reviewsFailed = true
            } finally {
                reviewsLoading = false
            }
        }
    }
    LaunchedEffect(remoteProfile?.id) { loadReviews() }
    val emailVerified = remoteProfile?.emailVerified == true

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            uploadingPhoto = true
            scope.launch {
                val jpeg = runCatching { encodeProfilePhoto(context, uri) }.getOrNull()
                if (jpeg == null) {
                    notice = "This photo could not be read. Please choose another photo."
                } else {
                    runCatching { userRepository.uploadProfilePhoto(jpeg) }
                        .onSuccess { profile ->
                            onProfileUpdated(profile)
                            notice = "Profile photo updated."
                        }
                        .onFailure { error ->
                            notice = supabaseErrorText(
                                error,
                                "Your photo could not be uploaded. Check your " +
                                        "internet connection and try again."
                            )
                        }
                }
                uploadingPhoto = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaluColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile identity first; settings and account controls further down.
        Box(Modifier.fillMaxWidth()) {
            IconButton(
                onClick = { page = "settings" },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp)
            ) {
                Icon(Icons.Outlined.Settings, "Settings", tint = SaluColors.Ink)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(112.dp)) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(104.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(3.dp)
                    ) {
                        Avatar(
                            photoUrl = photoUrl,
                            name = displayName,
                            size = 98.dp,
                            contentDescription = "Profile photo"
                        )
                        if (uploadingPhoto) {
                            CircularProgressIndicator(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(30.dp),
                                color = SaluColors.GreenInk
                            )
                        }
                    }
                    FilledIconButton(
                        onClick = { photoPicker.launch(arrayOf("image/*")) },
                        enabled = !uploadingPhoto && remoteProfile != null,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = SaluColors.Yellow,
                            contentColor = SaluColors.Ink
                        )
                    ) { Icon(Icons.Outlined.PhotoCamera, "Change profile photo", Modifier.size(20.dp)) }
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .semantics { heading() },
                        style = SaluText.ScreenTitle,
                        color = SaluColors.Ink,
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { page = "edit_profile" }, enabled = remoteProfile != null) {
                        Icon(Icons.Outlined.Edit, "Edit profile", tint = SaluColors.GreenInk, modifier = Modifier.size(21.dp))
                    }
                }

                VerificationChip(verified = emailVerified, onClick = { page = "verification" })

                Spacer(Modifier.height(10.dp))

                Text(
                    remoteProfile?.bio?.takeIf { it.isNotBlank() } ?: "Add a little about yourself using the pencil.",
                    style = SaluText.Body,
                    color = if (remoteProfile?.bio.isNullOrBlank()) SaluColors.InkMuted else SaluColors.Ink,
                    textAlign = TextAlign.Center
                )

                TextButton(onClick = { page = "membership" }) {
                    Text("Member since $memberSince", style = SaluText.Meta, color = SaluColors.InkMuted)
                }
            }
        }

        // Rating, response rate and completed pickups at a glance.
        SaluCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(vertical = 6.dp)
            ) {
                ProfileStat(
                    value = ratingSummary?.average?.let(::formatRatingAverage) ?: "—",
                    label = "Rating",
                    icon = Icons.Outlined.StarOutline,
                    loading = ratingSummary == null && reviewsLoading,
                    modifier = Modifier.weight(1f)
                ) { page = "reviews" }
                StatDivider()
                ProfileStat(
                    value = responseRate?.percent?.let { "$it%" } ?: "—",
                    label = "Response",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    loading = responseRate == null && responseRateLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    page = "response"
                    loadResponseRate()
                }
                StatDivider()
                ProfileStat(
                    value = if (reservationsLoaded) successfulClaims.size.toString() else "—",
                    label = "Claims",
                    icon = Icons.Outlined.Redeem,
                    loading = !reservationsLoaded && !reservationsFailed,
                    modifier = Modifier.weight(1f)
                ) { page = "claims" }
                StatDivider()
                ProfileStat(
                    value = if (reservationsLoaded) successfulDonations.size.toString() else "—",
                    label = "Donations",
                    icon = Icons.Outlined.VolunteerActivism,
                    loading = !reservationsLoaded && !reservationsFailed,
                    modifier = Modifier.weight(1f)
                ) { page = "donations" }
            }
        }

        SaluCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ratings & Reviews",
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                        style = SaluText.SectionTitle,
                        color = SaluColors.Ink
                    )
                    TextButton(onClick = { page = "reviews" }) {
                        Text("View all", style = MaterialTheme.typography.labelLarge, color = SaluColors.GreenInk)
                    }
                }
                val recent = receivedReviews
                when {
                    recent == null && reviewsFailed -> {
                        InlineNotice(
                            text = "Your reviews could not be loaded.",
                            icon = Icons.Outlined.ErrorOutline,
                            tone = NoticeTone.Error
                        )
                        TextButton(onClick = { loadReviews() }) {
                            Text("Try again", color = SaluColors.GreenInk)
                        }
                    }
                    recent == null -> SkeletonGroup(description = "Loading your reviews") {
                        repeat(2) { ListRowSkeleton(thumbnail = 36.dp, round = true) }
                    }
                    recent.isEmpty() -> Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconBubble(Icons.Outlined.StarOutline, size = 44.dp, iconSize = 24.dp)
                        Spacer(Modifier.height(8.dp))
                        Text("No reviews yet", style = SaluText.FoodTitle, color = SaluColors.Ink)
                        Text(
                            "Reviews from people who picked up your food appear here.",
                            style = SaluText.Supporting,
                            color = SaluColors.InkMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> recent.take(2).forEachIndexed { index, review ->
                        if (index > 0) HorizontalDivider(color = SaluColors.Line)
                        ReviewRow(review)
                    }
                }
            }
        }

        GroupLabel("Your account")
        SaluCard(Modifier.fillMaxWidth()) {
            Column {
                ProfileMenuRow(Icons.Outlined.Edit, "Edit profile", "Name, photo and bio") { page = "edit_profile" }
                MenuDivider()
                ProfileMenuRow(Icons.Outlined.BookmarkBorder, "Saved Listings", "Food you bookmarked") { page = "saved" }
                MenuDivider()
                ProfileMenuRow(
                    Icons.Outlined.VerifiedUser,
                    "Email verification",
                    if (emailVerified) "Confirmed. It confirms the email address, not your identity."
                    else "Confirm your email address to show the Email verified badge."
                ) { page = "verification" }
            }
        }

        GroupLabel("Settings")
        SaluCard(Modifier.fillMaxWidth()) {
            Column {
                ProfileMenuRow(Icons.Outlined.Notifications, "Notifications", "Which updates appear in Alerts") { page = "notifications" }
                MenuDivider()
                ProfileMenuRow(Icons.Outlined.Restaurant, "Food preferences", "Choose food for your Home row") { page = "food_preferences" }
                MenuDivider()
                ProfileMenuRow(Icons.Outlined.LocationOn, "Location & distance", "Your preferred pickup area") { page = "location" }
                MenuDivider()
                ProfileMenuRow(Icons.Outlined.Shield, "Trust & Safety", "Pickup safety, badges and reports") { page = "safety" }
                MenuDivider()
                ProfileMenuRow(Icons.Outlined.Settings, "All settings", "Account, help and about") { page = "settings" }
            }
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = SaluRadius.Control,
            border = BorderStroke(1.dp, SaluColors.PinkInk.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = SaluColors.PinkInk)
            Spacer(Modifier.width(8.dp))
            Text("Log out", style = MaterialTheme.typography.labelLarge, color = SaluColors.PinkInk)
        }
    }

    if (page.isNotBlank()) {
        ProfileSections(
            initialPage = page,
            profile = remoteProfile,
            onProfileUpdated = onProfileUpdated,
            onClose = { page = "" },
            successfulClaims = successfulClaims,
            successfulDonations = successfulDonations,
            recordsLoaded = reservationsLoaded,
            recordsFailed = reservationsFailed,
            onReloadRecords = { reservationViewModel.refresh() },
            responseRate = responseRate,
            responseRateFailed = responseRateFailed,
            onReloadResponseRate = { loadResponseRate() },
            ratingSummary = ratingSummary,
            receivedReviews = receivedReviews,
            reviewsFailed = reviewsFailed,
            onReloadReviews = { loadReviews() },
            notificationViewModel = notificationViewModel,
            savedListingViewModel = savedListingViewModel,
            onOpenListing = { post ->
                page = ""
                onOpenListing(post)
            }
        )
    }

    if (notice.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { notice = "" },
            title = { Text("Profile information") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = { notice = "" }) {
                    Text("Close")
                }
            }
        )
    }
}

/** "Email verified" in green, or "Email not verified" with a way to fix it. */
@Composable
private fun VerificationChip(verified: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 2.dp)
            .clip(SaluRadius.Pill)
            .background(if (verified) SaluColors.GreenSoft else SaluColors.Cream)
            .clickable(role = Role.Button, onClickLabel = "Open email verification", onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (verified) Icons.Outlined.Verified else Icons.Outlined.MarkEmailUnread,
            contentDescription = null,
            tint = if (verified) SaluColors.GreenInk else SaluColors.InkMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (verified) "Email verified" else "Email not verified · Verify",
            style = SaluText.Meta.copy(fontSize = 13.sp),
            color = if (verified) SaluColors.GreenInk else SaluColors.Ink
        )
    }
}

@Composable
private fun ProfileStat(
    value: String,
    label: String,
    icon: ImageVector,
    loading: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(SaluRadius.Small)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = SaluColors.AmberInk, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        if (loading) {
            SkeletonGroup(description = "Loading $label") {
                SkeletonBlock(
                    Modifier
                        .padding(vertical = 3.dp)
                        .size(width = 36.dp, height = 22.dp)
                )
            }
        } else {
            Text(value, style = MaterialTheme.typography.titleLarge, color = SaluColors.Ink, maxLines = 1)
        }
        Text(label, style = SaluText.Meta, color = SaluColors.InkMuted, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .fillMaxHeight()
            .padding(vertical = 14.dp)
            .width(1.dp)
            .background(SaluColors.Line)
    )
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(color = SaluColors.Line, modifier = Modifier.padding(start = 66.dp))
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(icon, tint = SaluColors.Ink, background = SaluColors.Cream, size = 36.dp, iconSize = 19.dp)

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = SaluColors.Ink
            )
            Text(
                subtitle,
                style = SaluText.Supporting,
                color = SaluColors.InkMuted
            )
        }

        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = SaluColors.InkMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** users.created_at in the phone's time zone, e.g. "Sep 2026"; "—" if unknown. */
internal fun formatMemberSince(createdAt: String?, pattern: String): String =
    createdAt?.let {
        runCatching {
            OffsetDateTime.parse(it)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
        }.getOrNull()
    } ?: "—"
