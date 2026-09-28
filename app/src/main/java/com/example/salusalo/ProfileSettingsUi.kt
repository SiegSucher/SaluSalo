package com.example.salusalo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.salusalo.data.UserRepository
import com.example.salusalo.data.encodeProfilePhoto
import com.example.salusalo.data.supabaseErrorText
import com.example.salusalo.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val SettingsGreen = Color(0xFF39792B)
private val SettingsPale = Color(0xFFF0F5E8)
private val SettingsGray = Color(0xFF626870)

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), color = Color.White,
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFE4E9DC))
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = SettingsGreen)
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), fontSize = 15.sp)
        Icon(Icons.Outlined.ChevronRight, null, tint = SettingsGray)
    }
}

@Composable
private fun SettingsCaption(text: String) { Text(text, fontSize = 13.sp, color = SettingsGray) }

@Composable
internal fun SettingsSaveButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SettingsGreen)
    ) { Text(label) }
}

@Composable
internal fun SettingsHub(navigate: (String) -> Unit) {
    SettingsCaption("Make Salu-Salo work for you.")
    Text("PREFERENCES", fontSize = 12.sp, color = SettingsGray, fontWeight = FontWeight.SemiBold)
    SettingsCard {
        SettingsRow(Icons.Outlined.Notifications, "Notifications") { navigate("notifications") }
        HorizontalDivider()
        SettingsRow(Icons.Outlined.Restaurant, "Food preferences") { navigate("food_preferences") }
        HorizontalDivider()
        SettingsRow(Icons.Outlined.LocationOn, "Location & distance") { navigate("location") }
    }
    Text("ACCOUNT & SUPPORT", fontSize = 12.sp, color = SettingsGray, fontWeight = FontWeight.SemiBold)
    SettingsCard {
        SettingsRow(Icons.Outlined.Security, "Account & security") { navigate("account") }
        HorizontalDivider()
        SettingsRow(Icons.AutoMirrored.Outlined.HelpOutline, "Help & food safety") { navigate("help") }
        HorizontalDivider()
        SettingsRow(Icons.Outlined.Info, "About Salu-Salo") { navigate("about") }
    }
    Spacer(Modifier.height(12.dp))
    SettingsCaption("Every extra is an invitation.")
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
    var failedPhoto by remember(photoUrl) { mutableStateOf(false) }
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
        Box(Modifier.size(108.dp).clip(CircleShape).background(SettingsPale), contentAlignment = Alignment.Center) {
            if (photoUrl != null && !failedPhoto) {
                AsyncImage(photoUrl, "Profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    onError = { failedPhoto = true })
            } else {
                Text(name.take(1).uppercase(), fontSize = 40.sp, color = SettingsGreen, fontWeight = FontWeight.Bold)
            }
            if (uploading) CircularProgressIndicator(Modifier.size(32.dp), color = SettingsGreen)
        }
        TextButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !saving && !uploading && profile != null) {
            Icon(Icons.Outlined.PhotoCamera, null)
            Spacer(Modifier.width(6.dp))
            Text("Change photo")
        }
        SettingsCaption("Photo changes save immediately.")
    }
    OutlinedTextField(
        name, { if (it.length <= 120) name = it }, label = { Text("Display name") },
        singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        bio, { if (it.length <= 160) bio = it }, label = { Text("About me") },
        minLines = 4, enabled = !saving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        supportingText = { Text("${bio.length}/160") }
    )
    SettingsCaption("Your bio appears on your Profile. Your display name is also used on listings and reviews.")
    SettingsSaveButton(if (saving) "Saving…" else "Save changes", !saving && !uploading && profile != null) {
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
    if (feedback.isNotBlank()) SettingsCard { Text(feedback) }
}

@Composable
internal fun FoodPreferenceSettings(profileId: String?) {
    val context = LocalContext.current
    val stored = rememberDiscoveryPreferences(profileId ?: "signed-out")
    var selected by remember { mutableStateOf(stored.categories) }
    var type by rememberSaveable { mutableStateOf(stored.listingType) }
    var feedback by rememberSaveable { mutableStateOf("") }
    SettingsCaption("Choose food for the ‘For your preferences’ row on Home.")
    SettingsCard {
        Text("Food categories", fontWeight = FontWeight.Bold)
        listOf("Bread & pastries", "Vegetables", "Fruit", "Cooked meals", "Packaged food", "Other").forEach { label ->
            Row(
                Modifier.fillMaxWidth().clickable { selected = if (label in selected) selected - label else selected + label },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(label in selected, { checked -> selected = if (checked) selected + label else selected - label },
                    colors = CheckboxDefaults.colors(checkedColor = SettingsGreen))
                Text(label)
            }
        }
        SettingsCaption("Leave all categories unchecked to include every category.")
    }
    SettingsCard {
        Text("Listing preference", fontWeight = FontWeight.Bold)
        listOf("Both", "Donations", "For sale").forEach { label ->
            Row(Modifier.fillMaxWidth().clickable { type = label }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(type == label, { type = label }, colors = RadioButtonDefaults.colors(selectedColor = SettingsGreen))
                Text(label)
            }
        }
    }
    SettingsCaption("Saved for your account on this phone. The full Home feed still shows all available food.")
    SettingsSaveButton("Save preferences", profileId != null) {
        profileId?.let { saveFoodPreferences(context, it, selected, type); feedback = "Preferences saved. Check Home for matching listings." }
    }
    if (feedback.isNotBlank()) Text(feedback, color = SettingsGreen)
}

@Composable
internal fun LocationPreferenceSettings(profileId: String?) {
    val context = LocalContext.current
    val stored = rememberDiscoveryPreferences(profileId ?: "signed-out")
    var area by rememberSaveable { mutableStateOf(stored.area) }
    var feedback by rememberSaveable { mutableStateOf("") }
    SettingsCaption("Find food by its pickup area.")
    SettingsCard {
        Icon(Icons.Outlined.LocationOn, null, tint = SettingsGreen)
        Text("Preferred pickup area", fontWeight = FontWeight.Bold)
        OutlinedTextField(area, { if (it.length <= 120) area = it }, singleLine = true,
            label = { Text("Town or barangay") }, placeholder = { Text("e.g. Balilihan") }, modifier = Modifier.fillMaxWidth())
        SettingsCaption("Matches this text in listing pickup locations. Leave blank to include all areas.")
    }
    Surface(color = SettingsPale, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About distance", fontWeight = FontWeight.Bold)
            Text("Listings with a map pin show how far away they are, and Home's Nearby chip lists those within 5 km. This pickup area filter matches the written pickup location instead.", fontSize = 13.sp)
        }
    }
    SettingsCaption("Used with your food preferences on Home and saved for this account on this phone.")
    SettingsSaveButton("Save preferences", profileId != null) {
        profileId?.let { saveAreaPreference(context, it, area); feedback = "Pickup area saved." }
    }
    if (feedback.isNotBlank()) Text(feedback, color = SettingsGreen)
}

@Composable
internal fun AccountSecuritySettings(profile: UserProfile?, navigate: (String) -> Unit) {
    SettingsCaption("Manage your sign-in details.")
    SettingsCard {
        Text("Email address", fontWeight = FontWeight.Bold)
        Text(profile?.email.orEmpty())
        Text(if (profile?.emailVerified == true) "Email verified" else "Email not verified", color = SettingsGray)
        SettingsCaption("Manage email verification from the Verification Status card on Profile.")
    }
    SettingsCard {
        SettingsRow(Icons.Outlined.Lock, "Change password") { navigate("password") }
    }
    SettingsCard {
        Text("Keep your account secure", fontWeight = FontWeight.Bold)
        Text("Use a unique password and never share verification codes.")
        SettingsCaption("Email changes are not supported in this version. Edit your name and bio using the pencil on Profile.")
    }
}

@Composable
internal fun HelpSettings(navigate: (String) -> Unit) {
    SettingsCaption("Support for sharing responsibly.")
    SettingsCard {
        SettingsRow(Icons.AutoMirrored.Outlined.MenuBook, "How Salu-Salo works") { navigate("how_it_works") }
        HorizontalDivider()
        SettingsRow(Icons.Outlined.People, "Reservations & pickups") { navigate("pickup_help") }
        HorizontalDivider()
        SettingsRow(Icons.Outlined.Flag, "Report a problem") { navigate("report") }
    }
    Text("Before you collect food", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    SettingsCard {
        Text("• Check ingredients and allergens with the provider.")
        Text("• Ask how the food was stored.")
        Text("• Agree on a pickup place and time.")
    }
    SettingsCard {
        Text("Food safety matters", fontWeight = FontWeight.Bold)
        Text("Do not consume food if its storage or safety is uncertain. Appearance and smell alone cannot confirm safety.")
    }
    SettingsSaveButton("Read sharing guidelines") { navigate("safety") }
}

@Composable
internal fun AboutSettings(navigate: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.VolunteerActivism, null, Modifier.size(64.dp), tint = SettingsGreen)
        Spacer(Modifier.height(12.dp))
        Text("Salu-Salo", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        SettingsCaption("Every extra is an invitation.")
    }
    SettingsCard {
        Text("Good food. Shared locally.", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Connect with your community to share surplus food through donations or affordable listings.")
        Text("Share surplus food • Find listings • Coordinate pickups")
    }
    SettingsCard {
        SettingsRow(Icons.AutoMirrored.Outlined.MenuBook, "How it works") { navigate("how_it_works") }
        SettingsRow(Icons.Outlined.Security, "Community guidelines") { navigate("safety") }
    }
    SettingsCaption("Built for a more caring community.")
}

@Composable
internal fun HowItWorksSettings(pickups: Boolean) {
    SettingsCard {
        if (pickups) {
            Text("Your reservations", fontWeight = FontWeight.Bold)
            Text("Open Activity to view reservations. Use the message action to agree on pickup details with the provider.")
            Text("If you cannot collect the food, cancel your open reservation in Activity. The quantity becomes available again.")
            Text("The provider marks a reservation as claimed after pickup. Completed pickups appear in your Activity history, where you can review the provider.")
            Text("For paid listings, the reservation shows the amount due at pickup.")
        } else {
            Text("Share food", fontWeight = FontWeight.Bold)
            Text("Use Post to add a photo, food details, quantity, price or donation, expiry, and pickup information.")
            Text("Find and reserve", fontWeight = FontWeight.Bold)
            Text("Browse Home, open a listing, choose your quantity, and confirm your reservation.")
            Text("Coordinate and collect", fontWeight = FontWeight.Bold)
            Text("Use Messages to arrange pickup and Activity to track your reservations and listings.")
        }
    }
}
