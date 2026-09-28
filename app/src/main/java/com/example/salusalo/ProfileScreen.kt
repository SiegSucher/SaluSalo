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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PCream)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Profile identity. Account controls live only under the settings gear.
        Box(Modifier.fillMaxWidth()) {
            IconButton(onClick = { page = "settings" }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Outlined.Settings, "Settings", tint = PDark)
            }
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(106.dp)) {
                    Box(Modifier.size(100.dp).clip(CircleShape).background(PPale), contentAlignment = Alignment.Center) {
                        if (photoUrl != null && !photoFailed) {
                            AsyncImage(model = photoUrl, contentDescription = "Profile photo",
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                onError = { photoFailed = true })
                        } else {
                            Text(displayName.take(1).uppercase(), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = PGreen)
                        }
                        if (uploadingPhoto) CircularProgressIndicator(Modifier.size(30.dp), color = PGreen)
                    }
                    FilledIconButton(
                        onClick = { photoPicker.launch(arrayOf("image/*")) },
                        enabled = !uploadingPhoto && remoteProfile != null,
                        modifier = Modifier.align(Alignment.BottomEnd).size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = PDark)
                    ) { Icon(Icons.Outlined.PhotoCamera, "Change profile photo", Modifier.size(20.dp)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayName, modifier = Modifier.weight(1f, fill = false), fontSize = 23.sp, fontWeight = FontWeight.Bold, color = PDark)
                    IconButton(onClick = { page = "edit_profile" }, enabled = remoteProfile != null) {
                        Icon(Icons.Outlined.Edit, "Edit profile", tint = PGreen, modifier = Modifier.size(21.dp))
                    }
                }
                Text(remoteProfile?.bio?.takeIf { it.isNotBlank() } ?: "Add a little about yourself using the pencil.",
                    color = PGray, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        ProfilePanel {
            Row(Modifier.fillMaxWidth()) {
                listOf(
                    Triple(if (reservationsLoaded) successfulClaims.size.toString() else "—", "Claims", "claims"),
                    Triple(if (reservationsLoaded) successfulDonations.size.toString() else "—", "Donations", "donations"),
                    Triple(ratingSummary?.average?.let(::formatRatingAverage) ?: "—", "Rating", "reviews")
                ).forEach { (value, label, destination) ->
                    Column(Modifier.weight(1f).clickable { page = destination }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PDark)
                        Text(label, fontSize = 12.sp, color = PGray)
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

        ProfilePanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.StarOutline, null, tint = PGreen)
                Spacer(Modifier.width(8.dp))
                Text("Ratings & Reviews", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { page = "reviews" }) { Text("View all", color = PGreen) }
            }
            val recent = receivedReviews
            when {
                recent == null && reviewsFailed -> {
                    Text("Your reviews could not be loaded.", color = PGray)
                    TextButton(onClick = { loadReviews() }) { Text("Try again") }
                }
                recent == null -> Text("Loading your reviews…", color = PGray)
                recent.isEmpty() -> Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.StarOutline, null, tint = PGray, modifier = Modifier.size(32.dp))
                    Text("No reviews yet", fontWeight = FontWeight.Medium)
                    Text("Reviews from people who picked up your food appear here.", fontSize = 12.sp, color = PGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                else -> recent.take(2).forEach { ProfileReviewRow(it) }
            }
        }
        ProfilePanel {
            ProfileMenuRow(Icons.Outlined.BookmarkBorder, "Saved Listings", "Food you bookmarked") { page = "saved" }
        }
        // Retain the existing response and membership destinations in a compact footer.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { page = "response"; loadResponseRate() }) {
                Text("Response: ${responseRate?.percent?.let { "$it%" } ?: "—"}", color = PGray, fontSize = 11.sp)
            }
            TextButton(onClick = { page = "membership" }) {
                Text("Member since $memberSince", color = PGray, fontSize = 11.sp)
            }
        }
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)) {
            Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("Log out", color = MaterialTheme.colorScheme.error)
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

/** users.created_at in the phone's time zone, e.g. "Sep 2026"; "—" if unknown. */
internal fun formatMemberSince(createdAt: String?, pattern: String): String =
    createdAt?.let {
        runCatching {
            OffsetDateTime.parse(it)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
        }.getOrNull()
    } ?: "—"
