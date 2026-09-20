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
    currentName: String,
    currentLocation: String,
    onAccountSaved: (String, String) -> Unit,
    onPreferencesSaved: (Boolean, Boolean, Boolean) -> Unit,
    onOpenFood: (String) -> Unit,
    onClose: () -> Unit
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

    var name by rememberSaveable { mutableStateOf(currentName) }
    var location by rememberSaveable { mutableStateOf(currentLocation) }
    var feedback by rememberSaveable { mutableStateOf("") }

    var recommendations by rememberSaveable {
        mutableStateOf(prefs.getBoolean("recommendations", true))
    }
    var reminders by rememberSaveable {
        mutableStateOf(prefs.getBoolean("pickupReminders", true))
    }
    var showLocation by rememberSaveable {
        mutableStateOf(prefs.getBoolean("showLocation", true))
    }
    var sound by rememberSaveable {
        mutableStateOf(prefs.getBoolean("notificationSound", true))
    }
    var compact by rememberSaveable {
        mutableStateOf(prefs.getBoolean("compactPreview", false))
    }

    var verificationRequested by rememberSaveable {
        mutableStateOf(prefs.getBoolean("demoVerificationRequested", false))
    }

    var otherSession by rememberSaveable { mutableStateOf(true) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

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
            .putBoolean("showLocation", showLocation)
            .putBoolean("notificationSound", sound)
            .putBoolean("compactPreview", compact)
            .apply()

        onPreferencesSaved(recommendations, reminders, showLocation)
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
        "privacy" -> "Privacy"
        "app" -> "App Preferences"
        "account" -> "Account Details"
        "security" -> "Account Security"
        "password" -> "Change Password"
        "sessions" -> "Signed-in Devices"
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
                                    Text("Pickup area: $currentLocation")
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
                                    "Privacy",
                                    "Control the location shown on your profile"
                                ) { navigate("privacy") }

                                SectionLink(
                                    "App preferences",
                                    "Choose a compact or comfortable preview"
                                ) { navigate("app") }
                            }

                            "notifications" -> {
                                SectionCard {
                                    SectionToggle(
                                        "Food recommendations",
                                        recommendations
                                    ) { recommendations = it }

                                    SectionToggle(
                                        "Pickup reminders",
                                        reminders
                                    ) { reminders = it }

                                    SectionToggle(
                                        "Notification sound",
                                        sound
                                    ) { sound = it }
                                }

                                Text(
                                    "These choices are stored locally. Phone " +
                                            "notifications and sounds are not connected yet.",
                                    fontSize = 12.sp
                                )

                                SectionButton("Save preferences") {
                                    savePreferences()
                                }
                            }

                            "privacy" -> {
                                SectionCard {
                                    SectionToggle(
                                        "Show location on my profile",
                                        showLocation
                                    ) { showLocation = it }

                                    Text(
                                        "Turning this off hides the location in " +
                                                "your Profile header after saving."
                                    )
                                }

                                SectionCard(pale = true) {
                                    Text("Profile preview",
                                        fontWeight = FontWeight.Bold)
                                    Text(currentName)
                                    Text(
                                        if (showLocation) currentLocation
                                        else "Location hidden"
                                    )
                                }

                                SectionButton("Save privacy preference") {
                                    savePreferences()
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
                                        placeholder = { Text("e.g., Mika Suarez") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = location,
                                        onValueChange = { location = it },
                                        label = { Text("Location") },
                                        placeholder = {
                                            Text("e.g., Del Rosarrio, Balilihan")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                SectionButton("Save account details") {
                                    if (name.isBlank() || location.isBlank()) {
                                        feedback = "Enter your name and location."
                                    } else {
                                        prefs.edit()
                                            .putString("name", name.trim())
                                            .putString("location", location.trim())
                                            .apply()

                                        onAccountSaved(name.trim(), location.trim())
                                        feedback = "Profile details saved on this device."
                                    }
                                }

                                SectionLink(
                                    "Account security",
                                    "Password and signed-in device previews"
                                ) { navigate("security") }
                            }

                            "security" -> {
                                SectionCard(pale = true) {
                                    Text("Account Security",
                                        fontWeight = FontWeight.Bold)
                                    Text(
                                        "The app is not connected to authentication " +
                                                "yet. The controls below demonstrate " +
                                                "the proposed security screens."
                                    )
                                }

                                SectionLink(
                                    "Change password",
                                    "Preview password validation"
                                ) { navigate("password") }

                                SectionLink(
                                    "Signed-in devices",
                                    "Inspect example sessions"
                                ) { navigate("sessions") }
                            }

                            "password" -> {
                                Text(
                                    "Demo only. Use a made-up password; " +
                                            "nothing is saved or sent.",
                                    fontSize = 12.sp
                                )

                                SectionCard {
                                    OutlinedTextField(
                                        value = newPassword,
                                        onValueChange = { newPassword = it },
                                        label = { Text("New demo password") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = confirmPassword,
                                        onValueChange = { confirmPassword = it },
                                        label = { Text("Confirm demo password") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                SectionButton("Preview password change") {
                                    feedback = when {
                                        newPassword.length < 8 ->
                                            "Use at least 8 characters for this demo."
                                        newPassword != confirmPassword ->
                                            "The passwords do not match."
                                        else -> {
                                            newPassword = ""
                                            confirmPassword = ""
                                            "Preview passed. No account password was changed."
                                        }
                                    }
                                }
                            }

                            "sessions" -> {
                                SectionCard {
                                    Text("Current device",
                                        fontWeight = FontWeight.Bold)
                                    Text("Android phone • Example session")
                                    Text("This is a sample device list.")
                                }

                                if (otherSession) {
                                    SectionCard {
                                        Text("Chrome on Windows",
                                            fontWeight = FontWeight.Bold)
                                        Text("Example additional session")

                                        SectionButton("Remove demo session") {
                                            otherSession = false
                                        }
                                    }
                                } else {
                                    Text("The example additional session was removed.")
                                }
                            }

                            "settings" -> {
                                SectionLink(
                                    "Account details",
                                    "Edit your profile name and location"
                                ) { navigate("account") }

                                SectionLink(
                                    "Security",
                                    "Password and device previews"
                                ) { navigate("security") }

                                SectionLink(
                                    "Preferences",
                                    "Notifications, privacy, and app settings"
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
                                    Text("Member since March 2023",
                                        fontWeight = FontWeight.Bold)
                                    Text("This date is a sample value from the design.")
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
private fun SectionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
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