package com.example.salusalo

import com.example.salusalo.viewmodel.NotificationViewModel
import android.content.Intent
import com.example.salusalo.model.UserProfile
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

private data class ProfileReview(
    val initials: String,
    val name: String,
    val time: String,
    val message: String
)

@Composable
fun ProfileScreen(
    onAlerts: () -> Unit,
    onLogout: () -> Unit,
    onOpenFood: (String) -> Unit,
    notificationViewModel: NotificationViewModel,
    remoteProfile: UserProfile? = null
) {
    val context = LocalContext.current

    val preferences = remember {
        context.getSharedPreferences("salusalo_profile", 0)
    }

    var displayName by rememberSaveable {
        mutableStateOf(preferences.getString("name", "Mika") ?: "Mika")
    }
    var location by rememberSaveable {
        mutableStateOf(
            preferences.getString("location", "Del Rosarrio, Balilihan")
                ?: "Del Rosarrio, Balilihan"
        )
    }
    var photo by rememberSaveable {
        mutableStateOf(preferences.getString("photo", "") ?: "")
    }

    LaunchedEffect(remoteProfile) {
        remoteProfile?.let { profile ->
            displayName = profile.name
            if (!profile.profileImageUrl.isNullOrBlank()) {
                photo = profile.profileImageUrl
            }
        }
    }

    var page by rememberSaveable { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf("") }

    var editName by rememberSaveable { mutableStateOf("") }
    var editLocation by rememberSaveable { mutableStateOf("") }
    var accountError by rememberSaveable { mutableStateOf("") }

    var recommendations by rememberSaveable {
        mutableStateOf(preferences.getBoolean("recommendations", true))
    }
    var pickupReminders by rememberSaveable {
        mutableStateOf(preferences.getBoolean("pickupReminders", true))
    }
    var showLocation by rememberSaveable {
        mutableStateOf(preferences.getBoolean("showLocation", true))
    }

    val reviews = remember {
        listOf(
            ProfileReview(
                "JR", "Juan R.", "2 days ago",
                "Very responsive and easy to coordinate with."
            ),
            ProfileReview(
                "MC", "Maria C.", "1 week ago",
                "Smooth transaction. Thank you!"
            ),
            ProfileReview(
                "LP", "Luis P.", "2 weeks ago",
                "Great donor! Items were as described."
            ),
            ProfileReview(
                "AS", "Anna S.", "3 weeks ago",
                "Pickup instructions were clear and easy to follow."
            ),
            ProfileReview(
                "RD", "Rico D.", "1 month ago",
                "Friendly and helpful. Thank you for sharing!"
            )
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                photo = uri.toString()
                preferences.edit().putString("photo", photo).apply()
            } catch (_: SecurityException) {
                notice = "This photo could not be saved. Please choose another photo."
            }
        }
    }

    fun openAccount() {
        editName = displayName
        editLocation = location
        accountError = ""
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
                    if (photo.isNotBlank()) {
                        PostPhoto(photo)
                    } else {
                        Text(
                            displayName.take(1).uppercase(),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = PGreen
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clickable {
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

                if (showLocation) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = PGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            location,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = PGray
                        )
                    }
                }

                Surface(
                    modifier = Modifier.clickable { page = "verification" },
                    shape = RoundedCornerShape(20.dp),
                    color = PPale
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = PGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Identity Verified",
                            color = PGreen,
                            fontSize = 10.sp
                        )
                    }
                }

                Text(
                    "Demo verification & statistics",
                    fontSize = 9.sp,
                    color = PGray
                )
            }

            Column {
                IconButton(
                    onClick = { page = "settings" },
                    modifier = Modifier.size(36.dp)
                ) {
                    Box {
                        Icon(
                            Icons.Outlined.Notifications,
                            contentDescription = "Open alerts",
                            tint = PDark
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF6900))
                        )
                    }
                }

                IconButton(
                    onClick = { openAccount() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Account settings",
                        tint = PDark
                    )
                }
            }
        }

        // Statistics.
        ProfilePanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileStat(
                    Icons.Outlined.Spa, "38", "Successful\nClaims"
                ) {
                    page = "claims"
                }
                ProfileStat(
                    Icons.Outlined.CardGiftcard, "24", "Successful\nDonations"
                ) {
                    page = "donations"
                }
                ProfileStat(
                    Icons.Outlined.Star, "4.8", "Overall\nRating",
                    iconColor = PGold
                ) {
                    page = "reviews"
                }
                ProfileStat(
                    Icons.Outlined.Schedule, "92%", "Response\nRate"
                ) {
                    page = "response"
                }
                ProfileStat(
                    Icons.Outlined.CalendarMonth, "Mar 2023", "Member\nSince"
                ) {
                    page = "membership"
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
                        "Example verified profile. Verification helps build " +
                                "trust in the community.",
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
                    Text("Identity Verified", fontSize = 11.sp)
                    Text(
                        "Verified on Mar 15, 2023 • Demo",
                        fontSize = 9.sp,
                        color = PGray
                    )
                }

                Surface(
                    color = Color(0xFFE7F0DA),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        "Verified",
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        ),
                        fontSize = 10.sp,
                        color = PGreen
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
                        Icon(
                            Icons.Outlined.Star,
                            contentDescription = null,
                            tint = PGold,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            "4.8",
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Bold,
                            color = PDark
                        )
                        Text("Overall Rating", fontSize = 10.sp)
                        Text("★★★★★", color = PGold, fontSize = 13.sp)
                        Text("(56 reviews)", fontSize = 10.sp)
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

                    reviews.take(3).forEach { review ->
                        ProfileReviewRow(review)
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
                "Your saved items, donors, and organizations"
            ) {
                page = "saved"
            }

            HorizontalDivider(color = PLine)

            ProfileMenuRow(
                Icons.Outlined.Tune,
                "Preferences",
                "Notification, privacy, and app preferences"
            ) {
                page = "preferences"
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
                        "Verified users help build a stronger, safer community " +
                                "for everyone.",
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
            currentName = displayName,
            currentLocation = location,
            onAccountSaved = { updatedName, updatedLocation ->
                displayName = updatedName
                location = updatedLocation
            },
            onPreferencesSaved = { updatedRecommendations, updatedReminders, updatedLocationVisibility ->
                recommendations = updatedRecommendations
                pickupReminders = updatedReminders
                showLocation = updatedLocationVisibility
            },
            onOpenFood = { title ->
                page = ""
                onOpenFood(title)
            },
            onClose = { page = "" },
            notificationViewModel = notificationViewModel
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

@Composable
private fun ProfileReviewRow(review: ProfileReview) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(CircleShape)
                .background(PPale),
            contentAlignment = Alignment.Center
        ) {
            Text(
                review.initials,
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
                review.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text("★★★★★", color = PGold, fontSize = 11.sp)
            Text(
                review.message,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = PGray
            )
            Text(
                review.time,
                fontSize = 9.sp,
                color = PGray
            )
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