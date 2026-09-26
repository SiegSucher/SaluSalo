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
    onPreferencesSaved: (Boolean, Boolean) -> Unit,
    onOpenFood: (String) -> Unit,
    onClose: () -> Unit,
    notificationViewModel: NotificationViewModel
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

    var recommendations by rememberSaveable {
        mutableStateOf(prefs.getBoolean("recommendations", true))
    }
    var reminders by rememberSaveable {
        mutableStateOf(prefs.getBoolean("pickupReminders", true))
    }
    var sound by rememberSaveable {
        mutableStateOf(prefs.getBoolean("notificationSound", true))
    }
    var compact by rememberSaveable {
        mutableStateOf(prefs.getBoolean("compactPreview", false))
    }

    // Alert preferences saved to the account in Supabase (Phase 4). The
    // recommendation and pickup-reminder switches are stored there too, but no
    // alert uses them yet. Notification sound stays on this device.
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
            recommendations = it.recommendations
            reminders = it.pickupReminders
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

    var savedExamples by rememberSaveable {
        mutableStateOf(
            arrayListOf(
                "Bread Assortment",
                "Fresh Produce Box",
                "Pastries Assortment"
            )
        )
    }

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
            .putBoolean("recommendations", recommendations)
            .putBoolean("pickupReminders", reminders)
            .putBoolean("notificationSound", sound)
            .putBoolean("compactPreview", compact)
            .apply()

        onPreferencesSaved(recommendations, reminders)
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
        "preferences" -> "Preferences"
        "notifications" -> "Notification Preferences"
        "app" -> "App Preferences"
        "account" -> "Account Details"
        "security" -> "Account Security"
        "password" -> "Change Password"
        "settings" -> "Settings"
        "safety" -> "Trust & Safety"
        "report" -> "Report a Concern"
        "reviews" -> "Ratings & Reviews"
        "membership" -> "Membership"
        "record", "food" -> itemName
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
                                SectionCard(pale = true) {
                                    Text(
                                        if (page == "claims") "38" else "24",
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SectionGreen
                                    )
                                    Text(
                                        if (page == "claims") {
                                            "Successful claims"
                                        } else {
                                            "Successful donations"
                                        },
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Demo total • three example records below",
                                        fontSize = 12.sp
                                    )
                                }

                                val foods = if (page == "claims") {
                                    listOf(
                                        "Bread Assortment",
                                        "Fresh Produce Box",
                                        "Packed Rice Meals"
                                    )
                                } else {
                                    listOf(
                                        "Pastries Assortment",
                                        "Mixed Vegetables",
                                        "Bread Assortment"
                                    )
                                }

                                foods.forEachIndexed { index, food ->
                                    SectionLink(
                                        title = food,
                                        subtitle = "Completed • Example ${index + 1}"
                                    ) {
                                        navigate("record|$food")
                                    }
                                }
                            }

                            "record" -> {
                                SectionFoodPhoto(itemName)

                                SectionCard {
                                    Text(
                                        "Completed",
                                        color = SectionGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("Food: $itemName")
                                    Text("Account: $currentName")
                                    Text("Example reference: DEMO-104")
                                    Text(
                                        "This is a demonstration history record. " +
                                                "No real transaction was completed.",
                                        fontSize = 12.sp
                                    )
                                }

                                SectionButton("View food listing") {
                                    onOpenFood(itemName)
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
                                    "Example saved listings. These are separate " +
                                            "from the homepage bookmark buttons.",
                                    fontSize = 12.sp
                                )

                                if (savedExamples.isEmpty()) {
                                    SectionCard {
                                        Text("No example bookmarks remaining.")
                                        SectionButton("Restore examples") {
                                            savedExamples = arrayListOf(
                                                "Bread Assortment",
                                                "Fresh Produce Box",
                                                "Pastries Assortment"
                                            )
                                        }
                                    }
                                }

                                savedExamples.forEach { food ->
                                    SectionCard {
                                        SectionFoodPhoto(food)
                                        Text(food, fontWeight = FontWeight.Bold)

                                        SectionButton("View listing") {
                                            navigate("food|$food")
                                        }

                                        TextButton(
                                            onClick = {
                                                savedExamples = ArrayList(
                                                    savedExamples.filter { it != food }
                                                )
                                            }
                                        ) {
                                            Text("Remove example bookmark")
                                        }
                                    }
                                }
                            }

                            "food" -> {
                                SectionFoodPhoto(itemName)
                                SectionCard {
                                    Text(itemName, fontWeight = FontWeight.Bold)
                                    Text("Example saved food listing")
                                    Text(
                                        "Open the listing to see its available " +
                                                "quantity, provider, and pickup details."
                                    )
                                }
                                SectionButton("Open Food Details") {
                                    onOpenFood(itemName)
                                }
                            }

                            "preferences" -> {
                                SectionLink(
                                    "Notifications",
                                    "Recommendations, pickup reminders, and sound"
                                ) { navigate("notifications") }

                                SectionLink(
                                    "App preferences",
                                    "Choose a compact or comfortable preview"
                                ) { navigate("app") }
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

                                        SectionToggle(
                                            "Food recommendations",
                                            recommendations
                                        ) { recommendations = it }

                                        SectionToggle(
                                            "Pickup reminders",
                                            reminders
                                        ) { reminders = it }
                                    }

                                    SectionToggle(
                                        "Notification sound",
                                        sound
                                    ) { sound = it }
                                }

                                Text(
                                    "Alert choices are saved to your account and apply to " +
                                            "the Alerts page. Food recommendations and pickup " +
                                            "reminders are saved but not sent yet. Notification " +
                                            "sound is stored on this device; phone notifications " +
                                            "are not connected yet.",
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
                                                "Other preferences were saved on this device."
                                    } else {
                                        notificationViewModel.savePreferences(
                                            base.copy(
                                                messageAlerts = messageAlerts,
                                                reservationAlerts = reservationAlerts,
                                                recommendations = recommendations,
                                                pickupReminders = reminders
                                            )
                                        ) { feedback = "Preferences saved to your account." }
                                    }
                                }
                            }

                            "app" -> {
                                SectionCard {
                                    SectionToggle(
                                        "Compact preview",
                                        compact
                                    ) { compact = it }

                                    Text(
                                        "Try the spacing below. This setting applies " +
                                                "to this demonstration preview."
                                    )
                                }

                                SectionCard {
                                    listOf(
                                        "Bread Assortment",
                                        "Fresh Produce Box",
                                        "Packed Rice Meals"
                                    ).forEach {
                                        Text(
                                            it,
                                            modifier = Modifier.padding(
                                                vertical = if (compact) 3.dp else 15.dp
                                            )
                                        )
                                        HorizontalDivider()
                                    }
                                }

                                SectionButton("Save app preference") {
                                    savePreferences()
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
                                    "Preferences",
                                    "Notifications and app settings"
                                ) { navigate("preferences") }

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

@Composable
private fun SectionFoodPhoto(title: String) {
    val photo = when (title) {
        "Bread Assortment" -> R.drawable.food_bread
        "Fresh Produce Box" -> R.drawable.food_produce
        "Mixed Vegetables" -> R.drawable.food_vegetables
        "Packed Rice Meals" -> R.drawable.food_meals
        else -> R.drawable.food_pastries
    }

    Surface(shape = RoundedCornerShape(16.dp)) {
        Image(
            painter = painterResource(photo),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        )
    }
}