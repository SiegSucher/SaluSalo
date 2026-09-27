package com.example.salusalo

import com.example.salusalo.viewmodel.SavedListingViewModel
import com.example.salusalo.model.FoodPost
import com.example.salusalo.viewmodel.NotificationViewModel
import com.example.salusalo.viewmodel.ReservationViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.salusalo.model.ReservationStatus
import coil3.compose.AsyncImage
import com.example.salusalo.data.MessageRepository
import com.example.salusalo.data.ReviewRepository
import com.example.salusalo.model.RatingSummary
import com.example.salusalo.model.ReviewItem
import com.example.salusalo.model.dateText
import com.example.salusalo.model.formatRatingAverage
import com.example.salusalo.model.reviewCountText
import com.example.salusalo.model.starsText
import com.example.salusalo.data.UserRepository
import com.example.salusalo.data.encodeProfilePhoto
import com.example.salusalo.data.supabaseErrorText
import com.example.salusalo.model.ResponseRate
import com.example.salusalo.model.UserProfile
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PGreen = Color(0xFF347523)
private val PDark = Color(0xFF213027)
private val PGray = Color(0xFF626870)
private val PCream = Color(0xFFFCFBF6)
private val PPale = Color(0xFFF0F5E8)
private val PLine = Color(0xFFE4E9DC)
private val PGold = Color(0xFFFFAD08)

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
    val unreadAlerts by notificationViewModel.unreadCount.collectAsStateWithLifecycle()

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
    var photoFailed by remember(photoUrl) { mutableStateOf(false) }
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

    fun openAccount() {
        page = "account"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PCream)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Profile header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(86.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(PPale),
                    contentAlignment = Alignment.Center
                ) {
                    // Same fallback as the food cards: the initial when there is
                    // no photo or it cannot be loaded.
                    if (photoUrl != null && !photoFailed) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = { photoFailed = true }
                        )
                    } else {
                        Text(
                            displayName.take(1).uppercase(),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = PGreen
                        )
                    }
                    if (uploadingPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = PGreen,
                            strokeWidth = 2.dp
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clickable(enabled = !uploadingPhoto && remoteProfile != null) {
                            photoPicker.launch(arrayOf("image/*"))
                        },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = "Change profile photo",
                            tint = PDark,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName,
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = PDark
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        Icons.Outlined.Eco,
                        contentDescription = null,
                        tint = PGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Green once Firebase has confirmed the email; grey until then.
                // Both open the Verification page.
                Surface(
                    modifier = Modifier.clickable { page = "verification" },
                    shape = RoundedCornerShape(20.dp),
                    color = if (emailVerified) PPale else Color(0xFFEDEEF0)
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (emailVerified) Icons.Outlined.MarkEmailRead else Icons.Outlined.MarkEmailUnread,
                            contentDescription = null,
                            tint = if (emailVerified) PGreen else PGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (emailVerified) "Email verified" else "Email not verified",
                            color = if (emailVerified) PGreen else PGray,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Column {
                IconButton(
                    onClick = onAlerts,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = "Open alerts",
                            tint = PDark
                        )
                        // The dot shows only while there are unread alerts.
                        if (unreadAlerts > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF6900))
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { page = "settings" },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = PDark
                    )
                }
            }
        }

        // Statistics.
        ProfilePanel {
            Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileStat(
                    Icons.Outlined.Spa,
                    if (reservationsLoaded) successfulClaims.size.toString() else "—",
                    "Successful\nClaims"
                ) {
                    page = "claims"
                }
                ProfileStat(
                    Icons.Outlined.CardGiftcard,
                    if (reservationsLoaded) successfulDonations.size.toString() else "—",
                    "Successful\nDonations"
                ) {
                    page = "donations"
                }
                ProfileStat(
                    Icons.Outlined.Star,
                    ratingSummary?.average?.let(::formatRatingAverage) ?: "—",
                    "Overall\nRating",
                    iconColor = PGold
                ) {
                    page = "reviews"
                }
                ProfileStat(
                    Icons.Outlined.Schedule,
                    responseRate?.percent?.let { "$it%" } ?: "—",
                    "Response\nRate"
                ) {
                    page = "response"
                }
                ProfileStat(
                    Icons.Outlined.CalendarMonth, memberSince, "Member\nSince"
                ) {
                    page = "membership"
                }
            }
            }
        }

        // Verification panel.
        ProfilePanel(pale = true) {
            Row(verticalAlignment = Alignment.Top) {
                ProfileShield()
                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Verification Status",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = PDark
                    )
                    Text(
                        if (emailVerified) {
                            "Your email address is confirmed."
                        } else {
                            "Confirm your email address to show the Email verified badge."
                        },
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = PGray
                    )

                    OutlinedButton(
                        onClick = { page = "verification" },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(
                            horizontal = 10.dp,
                            vertical = 4.dp
                        )
                    ) {
                        Text(
                            "Manage Verification",
                            color = PGreen,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = PLine)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { page = "verification" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = PGray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))

                Column(Modifier.weight(1f)) {
                    Text(if (emailVerified) "Email verified" else "Email not verified", fontSize = 11.sp)
                    Text(
                        "Confirms the email address, not your identity",
                        fontSize = 9.sp,
                        color = PGray
                    )
                }

                Surface(
                    color = if (emailVerified) Color(0xFFE7F0DA) else Color(0xFFEDEEF0),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        if (emailVerified) "Verified" else "Not verified",
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        ),
                        fontSize = 10.sp,
                        color = if (emailVerified) PGreen else PGray
                    )
                }

                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Verification details",
                    tint = PGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Ratings and reviews.
        ProfilePanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ratings & Reviews",
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                TextButton(onClick = { page = "reviews" }) {
                    Text("View all", fontSize = 11.sp, color = PGreen)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.width(92.dp),
                    color = PPale,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 24.dp
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        val average = ratingSummary?.average
                        val count = ratingSummary?.count ?: 0
                        Icon(
                            Icons.Outlined.Star,
                            contentDescription = null,
                            tint = PGold,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            average?.let(::formatRatingAverage) ?: "—",
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Bold,
                            color = PDark
                        )
                        Text("Overall Rating", fontSize = 10.sp)
                        if (average != null && count > 0) {
                            Text(starsText(average), color = PGold, fontSize = 13.sp)
                            Text("(${reviewCountText(count)})", fontSize = 10.sp)
                        } else if (ratingSummary != null) {
                            Text("No reviews yet", fontSize = 10.sp)
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Recent reviews",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )

                    val recent = receivedReviews
                    when {
                        recent == null && reviewsFailed ->
                            Text("Your reviews could not be loaded.", fontSize = 10.sp, color = PGray)
                        recent == null ->
                            Text("Loading your reviews…", fontSize = 10.sp, color = PGray)
                        recent.isEmpty() ->
                            Text(NO_RATING_LINE, fontSize = 10.sp, lineHeight = 13.sp, color = PGray)
                        else -> recent.take(3).forEach { review ->
                            ProfileReviewRow(review)
                        }
                    }

                    TextButton(
                        onClick = { page = "reviews" },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            "View all reviews",
                            color = PGreen,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Menu.
        ProfilePanel {
            ProfileMenuRow(
                Icons.Outlined.BookmarkBorder,
                "Saved Listings",
                "Listings you bookmarked"
            ) {
                page = "saved"
            }

            HorizontalDivider(color = PLine)

            ProfileMenuRow(
                Icons.Outlined.Tune,
                "Notifications",
                "Message and reservation alerts, sound"
            ) {
                page = "notifications"
            }

            HorizontalDivider(color = PLine)

            ProfileMenuRow(
                Icons.Outlined.Person,
                "Account Settings",
                "Manage your account details and security"
            ) {
                openAccount()
            }

            HorizontalDivider(color = PLine)

            ProfileMenuRow(
                Icons.Outlined.Logout,
                "Log out",
                "Sign out of your Salu-Salo account"
            ) {
                onLogout()
            }
        }

        // Trust footer.
        ProfilePanel(pale = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileShield()
                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "We value trust and safety",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Rate the people you get food from, and report anything " +
                                "that doesn't feel right.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = PGray
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    page = "safety"
                },
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Learn more", fontSize = 11.sp, color = PGreen)
            }
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

@Composable
private fun ProfilePanel(
    pale: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (pale) Color(0xFFF6F8F0) else Color.White,
        border = BorderStroke(1.dp, PLine),
        shadowElevation = if (pale) 0.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun ProfileShield() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color(0xFFE7EFD9)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.VerifiedUser,
            contentDescription = null,
            tint = PGreen,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun ProfileStat(
    icon: ImageVector,
    value: String,
    caption: String,
    iconColor: Color = PGreen,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(70.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                value,
                fontSize = if (value.length > 5) 10.sp else 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = PDark
            )
        }

        Text(
            caption,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = PGray
        )
    }
}

/** A short review row for the Profile panel: initials, name, stars, comment and date. */
@Composable
private fun ProfileReviewRow(review: ReviewItem) {
    val initials = review.reviewerName.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(PPale),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials,
                fontSize = 10.sp,
                color = PGreen
            )
        }

        Spacer(Modifier.width(7.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                review.reviewerName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(starsText(review.rating), color = PGold, fontSize = 11.sp)
            review.comment?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = PGray
                )
            }
            review.dateText()?.let {
                Text(
                    it,
                    fontSize = 9.sp,
                    color = PGray
                )
            }
        }
    }
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
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = PDark,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = PGray
            )
        }

        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = PDark,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ProfileToggle(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange
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
