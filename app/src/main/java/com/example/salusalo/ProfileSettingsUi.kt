package com.example.salusalo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.salusalo.data.UserRepository
import com.example.salusalo.data.encodeProfilePhoto
import com.example.salusalo.data.supabaseErrorText
import com.example.salusalo.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    SaluCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** A group of settings rows on one card, divided by hairlines. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    SaluCard(modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, supporting: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(icon, size = 36.dp, iconSize = 19.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = SaluColors.DarkGreen)
            if (supporting != null) {
                Text(supporting, style = SaluText.Supporting, color = SaluColors.Gray)
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = SaluColors.Gray)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = SaluColors.Line, modifier = Modifier.padding(start = 66.dp))
}

@Composable
private fun SettingsCaption(text: String) {
    Text(text, style = SaluText.Supporting, color = SaluColors.Gray)
}

@Composable
internal fun SettingsSaveButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    SaluPrimaryButton(text = label, onClick = onClick, enabled = enabled)
}

@Composable
internal fun SettingsHub(navigate: (String) -> Unit) {
    SettingsCaption("Make Salu-Salo work for you.")
    GroupLabel("Preferences")
    SettingsGroup {
        SettingsRow(Icons.Outlined.Notifications, "Notifications", "Alerts and the notification sound") { navigate("notifications") }
        RowDivider()
        SettingsRow(Icons.Outlined.Restaurant, "Food preferences", "Food for your Home row") { navigate("food_preferences") }
        RowDivider()
        SettingsRow(Icons.Outlined.LocationOn, "Location & distance", "Your preferred pickup area") { navigate("location") }
    }
    GroupLabel("Account & support")
    SettingsGroup {
        SettingsRow(Icons.Outlined.Security, "Account & security", "Email and password") { navigate("account") }
        RowDivider()
        SettingsRow(Icons.Outlined.Shield, "Trust & Safety", "Pickup safety, badges and reports") { navigate("safety") }
        RowDivider()
        SettingsRow(Icons.AutoMirrored.Outlined.HelpOutline, "Help & food safety") { navigate("help") }
        RowDivider()
        SettingsRow(Icons.Outlined.Info, "About Salu-Salo") { navigate("about") }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "Every extra is an invitation.",
        style = SaluText.Supporting,
        color = SaluColors.Gray,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun ProfileEditor(profile: UserProfile?, onUpdated: (UserProfile) -> Unit) {
    val context = LocalContext.current
    val repository = remember { UserRepository() }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var bio by rememberSaveable(profile?.id) { mutableStateOf(profile?.bio.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var feedback by rememberSaveable { mutableStateOf("") }
    val photoUrl by produceState<String?>(null, profile?.profileImageUrl) {
        value = repository.profilePhotoUrl(profile?.profileImageUrl)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !uploading) {
            uploading = true
            scope.launch {
                try {
                    val photo = encodeProfilePhoto(context, uri)
                        ?: error("This photo could not be read.")
                    onUpdated(repository.uploadProfilePhoto(photo))
                    feedback = "Profile photo saved."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    feedback = supabaseErrorText(error, "Your photo could not be saved. Try another image.")
                } finally { uploading = false }
            }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(photoUrl = photoUrl, name = name, size = 108.dp, contentDescription = "Profile photo")
            if (uploading) CircularProgressIndicator(Modifier.size(32.dp), color = SaluColors.Green)
        }
        TextButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !saving && !uploading && profile != null) {
            Icon(Icons.Outlined.PhotoCamera, null, tint = SaluColors.Green)
            Spacer(Modifier.width(6.dp))
            Text("Change photo", style = MaterialTheme.typography.labelLarge, color = SaluColors.Green)
        }
        SettingsCaption("Photo changes save immediately.")
    }
    OutlinedTextField(
        name, { if (it.length <= 120) name = it }, label = { Text("Display name") },
        singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), shape = SaluRadius.Control,
        colors = saluFieldColors()
    )
    OutlinedTextField(
        bio, { if (it.length <= 160) bio = it }, label = { Text("About me") },
        minLines = 4, enabled = !saving, modifier = Modifier.fillMaxWidth(), shape = SaluRadius.Control,
        supportingText = { Text("${bio.length}/160") },
        colors = saluFieldColors()
    )
    SettingsCaption("Your bio appears on your Profile. Your display name is also used on listings and reviews.")
    SaluPrimaryButton(
        text = if (saving) "Saving…" else "Save changes",
        enabled = !uploading && profile != null,
        loading = saving,
        onClick = {
            if (name.isBlank()) feedback = "Enter your display name." else {
                saving = true
                feedback = ""
                scope.launch {
                    try {
                        val updated = repository.updateMyProfile(name, bio)
                        name = updated.name
                        bio = updated.bio.orEmpty()
                        onUpdated(updated)
                        feedback = "Profile saved to your account."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        feedback = supabaseErrorText(error, "Your profile could not be saved. Please try again.")
                    } finally { saving = false }
                }
            }
        }
    )
    if (feedback.isNotBlank()) InlineNotice(text = feedback, icon = Icons.Outlined.Info)
}

@Composable
internal fun FoodPreferenceSettings(profileId: String?) {
    val context = LocalContext.current
    val stored = rememberDiscoveryPreferences(profileId ?: "signed-out")
    // A list, so ticks not saved yet survive rotation like the type below.
    var selected by rememberSaveable { mutableStateOf(stored.categories.toList()) }
    var type by rememberSaveable { mutableStateOf(stored.listingType) }
    var feedback by rememberSaveable { mutableStateOf("") }
    SettingsCaption("Choose food for the ‘For your preferences’ row on Home.")
    SettingsCard {
        Text("Food categories", style = SaluText.FoodTitle, color = SaluColors.DarkGreen, modifier = Modifier.semantics { heading() })
        Column {
            listOf("Bread & pastries", "Vegetables", "Fruit", "Cooked meals", "Packaged food", "Other").forEach { label ->
                val checked = label in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(SaluRadius.Small)
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { now -> selected = if (now) selected + label else selected - label }
                        )
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = SaluColors.Green, uncheckedColor = SaluColors.Gray)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = SaluText.Body, color = SaluColors.DarkGreen)
                }
            }
        }
        SettingsCaption("Leave all categories unchecked to include every category.")
    }
    SettingsCard {
        Text("Listing preference", style = SaluText.FoodTitle, color = SaluColors.DarkGreen, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            listOf("Both", "Donations", "For sale").forEach { label ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(SaluRadius.Small)
                        .selectable(selected = type == label, role = Role.RadioButton, onClick = { type = label })
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(type == label, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = SaluColors.Green))
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = SaluText.Body, color = SaluColors.DarkGreen)
                }
            }
        }
    }
    SettingsCaption("Saved for your account on this phone. The full Home feed still shows all available food.")
    SettingsSaveButton("Save preferences", profileId != null) {
        profileId?.let { saveFoodPreferences(context, it, selected.toSet(), type); feedback = "Preferences saved. Check Home for matching listings." }
    }
    if (feedback.isNotBlank()) InlineNotice(text = feedback, icon = Icons.Outlined.CheckCircle, tone = NoticeTone.Success)
}

@Composable
internal fun LocationPreferenceSettings(profileId: String?) {
    val context = LocalContext.current
    val stored = rememberDiscoveryPreferences(profileId ?: "signed-out")
    var area by rememberSaveable { mutableStateOf(stored.area) }
    var feedback by rememberSaveable { mutableStateOf("") }
    SettingsCaption("Find food by its pickup area.")
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(Icons.Outlined.LocationOn, size = 36.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            Text("Preferred pickup area", style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
        }
        OutlinedTextField(
            area, { if (it.length <= 120) area = it }, singleLine = true,
            label = { Text("Town or barangay") }, placeholder = { Text("e.g. Balilihan") },
            modifier = Modifier.fillMaxWidth(), shape = SaluRadius.Control, colors = saluFieldColors()
        )
        SettingsCaption("Matches this text in listing pickup locations. Leave blank to include all areas.")
    }
    SaluCard(Modifier.fillMaxWidth(), color = SaluColors.PaleGreen, border = false) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About distance", style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
            Text(
                "Listings with a map pin show how far away they are, and Home's Nearby chip lists those within 5 km. This pickup area filter matches the written pickup location instead.",
                style = SaluText.Body,
                color = SaluColors.DarkGreen
            )
        }
    }
    SettingsCaption("Used with your food preferences on Home and saved for this account on this phone.")
    SettingsSaveButton("Save preferences", profileId != null) {
        profileId?.let { saveAreaPreference(context, it, area); feedback = "Pickup area saved." }
    }
    if (feedback.isNotBlank()) InlineNotice(text = feedback, icon = Icons.Outlined.CheckCircle, tone = NoticeTone.Success)
}

@Composable
internal fun AccountSecuritySettings(profile: UserProfile?, navigate: (String) -> Unit) {
    SettingsCaption("Manage your sign-in details.")
    SettingsCard {
        Text("Email address", style = SaluText.Meta, color = SaluColors.Gray)
        Text(profile?.email.orEmpty(), style = SaluText.Body.copy(fontSize = 15.sp), color = SaluColors.DarkGreen)
        StatusBadge(
            if (profile?.emailVerified == true) SaluStatus.Available else SaluStatus.Expired,
            label = if (profile?.emailVerified == true) "Email verified" else "Email not verified"
        )
        SettingsCaption("Manage email verification from the Email verification row on Profile.")
    }
    SettingsGroup {
        SettingsRow(Icons.Outlined.Lock, "Change password", "Update the password you sign in with") { navigate("password") }
    }
    SettingsCard {
        Text("Keep your account secure", style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
        Text("Use a unique password and never share verification codes.", style = SaluText.Body, color = SaluColors.DarkGreen)
        SettingsCaption("Email changes are not supported in this version. Edit your name and bio using the pencil on Profile.")
    }
}

@Composable
internal fun HelpSettings(navigate: (String) -> Unit) {
    SettingsCaption("Support for sharing responsibly.")
    SettingsGroup {
        SettingsRow(Icons.AutoMirrored.Outlined.MenuBook, "How Salu-Salo works") { navigate("how_it_works") }
        RowDivider()
        SettingsRow(Icons.Outlined.People, "Reservations & pickups") { navigate("pickup_help") }
        RowDivider()
        SettingsRow(Icons.Outlined.Flag, "Report a problem") { navigate("report") }
    }
    Text(
        "Before you collect food",
        style = SaluText.SectionTitle,
        color = SaluColors.DarkGreen,
        modifier = Modifier.semantics { heading() }
    )
    SettingsCard {
        Text("• Check ingredients and allergens with the provider.", style = SaluText.Body, color = SaluColors.DarkGreen)
        Text("• Ask how the food was stored.", style = SaluText.Body, color = SaluColors.DarkGreen)
        Text("• Agree on a pickup place and time.", style = SaluText.Body, color = SaluColors.DarkGreen)
    }
    SaluCard(Modifier.fillMaxWidth(), color = SaluColors.UrgentSoft, border = false) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.WarningAmber, null, tint = SaluColors.Urgent)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Food safety matters", style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
                Text(
                    "Do not consume food if its storage or safety is uncertain. Appearance and smell alone cannot confirm safety.",
                    style = SaluText.Body,
                    color = SaluColors.DarkGreen
                )
            }
        }
    }
    SettingsSaveButton("Read sharing guidelines") { navigate("safety") }
}

@Composable
internal fun AboutSettings(navigate: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SaluLogo(72.dp, contentDescription = "Salu-Salo logo")
        Spacer(Modifier.height(14.dp))
        BrandWordmark(style = MaterialTheme.typography.headlineMedium)
        SettingsCaption("Every extra is an invitation.")
    }
    SettingsCard {
        Text("Good food. Shared locally.", style = SaluText.SectionTitle, color = SaluColors.DarkGreen)
        Text(
            "Connect with your community to share surplus food through donations or affordable listings.",
            style = SaluText.Body,
            color = SaluColors.DarkGreen
        )
        Text("Share surplus food • Find listings • Coordinate pickups", style = SaluText.Supporting, color = SaluColors.Gray)
    }
    SettingsGroup {
        SettingsRow(Icons.AutoMirrored.Outlined.MenuBook, "How it works") { navigate("how_it_works") }
        RowDivider()
        SettingsRow(Icons.Outlined.Security, "Community guidelines") { navigate("safety") }
    }
    Text(
        "Built for a more caring community.",
        style = SaluText.Supporting,
        color = SaluColors.Gray,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun HowItWorksSettings(pickups: Boolean) {
    if (pickups) {
        SettingsCard {
            Text("Your reservations", style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
            Text("Open Activity to view reservations. Use the message action to agree on pickup details with the provider.", style = SaluText.Body, color = SaluColors.DarkGreen)
            Text("If you cannot collect the food, cancel your open reservation in Activity. The quantity becomes available again.", style = SaluText.Body, color = SaluColors.DarkGreen)
            Text("The provider marks a reservation as claimed after pickup. Completed pickups appear in your Activity history, where you can review the provider.", style = SaluText.Body, color = SaluColors.DarkGreen)
            Text("For paid listings, the reservation shows the amount due at pickup.", style = SaluText.Body, color = SaluColors.DarkGreen)
        }
    } else {
        HowItWorksStep(1, Icons.Outlined.AddCircleOutline, "Share food",
            "Use Post to add a photo, food details, quantity, price or donation, expiry, and pickup information.")
        HowItWorksStep(2, Icons.Outlined.BookmarkBorder, "Find and reserve",
            "Browse Home, open a listing, choose your quantity, and confirm your reservation.")
        HowItWorksStep(3, Icons.Outlined.Handshake, "Coordinate and collect",
            "Use Messages to arrange pickup and Activity to track your reservations and listings.")
    }
}

/** One numbered step of "How Salu-Salo works". */
@Composable
private fun HowItWorksStep(number: Int, icon: ImageVector, title: String, text: String) {
    SaluCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(contentAlignment = Alignment.TopEnd) {
                IconBubble(icon, size = 44.dp, iconSize = 22.dp)
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(SaluColors.DarkGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        number.toString(),
                        color = SaluColors.Surface,
                        style = SaluText.Status.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = SaluText.FoodTitle, color = SaluColors.DarkGreen, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(text, style = SaluText.Body, color = SaluColors.Gray)
            }
        }
    }
}
