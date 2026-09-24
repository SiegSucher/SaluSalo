package com.example.salusalo

import android.os.Bundle
import coil3.compose.AsyncImage
import com.example.salusalo.model.FoodPost
import com.example.salusalo.viewmodel.FoodViewModel
import com.example.salusalo.viewmodel.ReservationViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import com.example.salusalo.data.UserRepository
import com.example.salusalo.data.profileErrorMessage
import com.example.salusalo.model.UserProfile
import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val Green = Color(0xFF47782E)
private val DarkGreen = Color(0xFF263C29)
private val Cream = Color(0xFFFCFBF6)
private val PaleGreen = Color(0xFFF3F6E8)
private val IconGreen = Color(0xFFE4EDC9)
private val Gray = Color(0xFF666873)
private val Line = Color(0xFFE3E4E7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Green,
                    onPrimary = Color.White,
                    background = Cream,
                    onBackground = DarkGreen,
                    surface = Color.White,
                    onSurface = DarkGreen
                )
            ) {
                SaluSaloApp()
            }
        }
    }
}

@Composable
private fun SaluSaloApp() {
    var screen by rememberSaveable { mutableStateOf("welcome") }
    var message by rememberSaveable { mutableStateOf("") }
    var isAuthenticating by remember { mutableStateOf(false) }
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val profileRepository = remember { UserRepository() }
    val appScope = rememberCoroutineScope()
    var currentProfile by remember { mutableStateOf<UserProfile?>(null) }

    fun logOut() {
        firebaseAuth.signOut()
        currentProfile = null

        // Remove local, account-specific placeholder data before another user
        // can sign in on this device.
        context.getSharedPreferences("salusalo_profile", 0).edit().clear().apply()
        context.getSharedPreferences("salusalo_local_posts", 0).edit().clear().apply()
        screen = "signin"
    }
    fun openAuthenticatedHome(
        displayNameOverride: String? = null,
        refreshFirebaseToken: Boolean = false
    ) {
        isAuthenticating = true
        appScope.launch {
            runCatching {
                // A newly created Firebase user may receive its required
                // Supabase role claim asynchronously from a Cloud Function.
                if (refreshFirebaseToken) {
                    firebaseAuth.currentUser?.getIdToken(true)?.await()
                }
                profileRepository.loadOrCreateCurrentProfile(displayNameOverride)
            }.onSuccess { profile ->
                currentProfile = profile
                screen = "home"
            }.onFailure { error ->
                message = profileErrorMessage(error)
            }
            isAuthenticating = false
        }
    }

    LaunchedEffect(firebaseAuth.currentUser?.uid) {
        if (firebaseAuth.currentUser != null && currentProfile == null) {
            openAuthenticatedHome()
        }
    }

    BackHandler(enabled = screen != "welcome") {
        if (screen == "home") {
            logOut()
        } else {
            screen = when (screen) {
                "signup", "forgot" -> "signin"
                else -> "welcome"
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Cream
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
        ) {
            AnimatedContent(
                targetState = screen,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Cream),
                transitionSpec = {
                    if (
                        initialState == "welcome" &&
                        targetState == "signup"
                    ) {
                        // Get Started: welcome page swipes away and the
                        // sign-up page slides in from the right.
                        (slideInHorizontally(
                            animationSpec = tween(400)
                        ) { fullWidth -> fullWidth }) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(400)
                                ) { fullWidth -> -fullWidth })
                    } else {
                        // Every other screen change stays instant.
                        EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "screenTransition"
            ) { currentScreen ->
                when (currentScreen) {
                    "welcome" -> WelcomeScreen(
                        onStart = { screen = "signup" },
                        onSignIn = { screen = "signin" }
                    )

                    "home" -> HomeScreen(
                        onLogout = { logOut() },
                        currentProfile = currentProfile
                    )

                    else -> SignInScreen(
                        mode = currentScreen,
                        onNavigate = { screen = it },
                        isAuthenticating = isAuthenticating,
                        onContinue = { email, password, confirmPassword, name ->
                            val cleanEmail = email.trim()

                            when {
                                !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches() -> {
                                    message = "Enter a valid email address."
                                }

                                currentScreen == "signup" && name.trim().isEmpty() -> {
                                    message = "Enter your full name."
                                }

                                currentScreen != "forgot" && password.length < 6 -> {
                                    message = "Your password must be at least 6 characters."
                                }

                                currentScreen == "signup" && password != confirmPassword -> {
                                    message = "Your passwords do not match."
                                }

                                else -> {
                                    isAuthenticating = true

                                    when (currentScreen) {
                                        "signup" -> firebaseAuth
                                            .createUserWithEmailAndPassword(
                                                cleanEmail,
                                                password
                                            )
                                            .addOnCompleteListener { task ->
                                                isAuthenticating = false
                                                if (task.isSuccessful) {
                                                    task.result.user?.updateProfile(
                                                        userProfileChangeRequest {
                                                            displayName = name.trim()
                                                        }
                                                    )
                                                    openAuthenticatedHome(name.trim(), refreshFirebaseToken = true)
                                                } else {
                                                    message = authErrorMessage(
                                                        task.exception
                                                    )
                                                }
                                            }

                                        "forgot" -> firebaseAuth
                                            .sendPasswordResetEmail(cleanEmail)
                                            .addOnCompleteListener { task ->
                                                isAuthenticating = false
                                                if (task.isSuccessful) {
                                                    message =
                                                        "A password-reset link was sent to $cleanEmail."
                                                    screen = "signin"
                                                } else {
                                                    message = authErrorMessage(
                                                        task.exception
                                                    )
                                                }
                                            }

                                        else -> firebaseAuth
                                            .signInWithEmailAndPassword(
                                                cleanEmail,
                                                password
                                            )
                                            .addOnCompleteListener { task ->
                                                isAuthenticating = false
                                                if (task.isSuccessful) {
                                                    openAuthenticatedHome()
                                                } else {
                                                    message = authErrorMessage(
                                                        task.exception
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                        },
                        onSocial = {
                            message =
                                "$it sign-in selected. This prototype " +
                                        "does not connect to an account yet."
                        }
                    )
                }
            }
        }
    }

    if (message.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { message = "" },
            title = { Text("Salu-Salo") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { message = "" }) {
                    Text("OK")
                }
            }
        )
    }
}

private fun authErrorMessage(exception: Exception?): String = when (exception) {
    is FirebaseAuthUserCollisionException ->
        "An account already exists for this email address."
    is FirebaseAuthInvalidUserException ->
        "No account was found for this email address."
    is FirebaseAuthInvalidCredentialsException ->
        "The email address or password is incorrect."
    else -> "Authentication failed. Please try again."
}

/* WELCOME SCREEN */

// One entry = one welcome screen. Edit the text, icons, and image here.
private data class WelcomePage(
    val image: Int,
    val imageDescription: String,
    val title: String,
    val body: String,
    val leftTitle: String,
    val leftDescription: String,
    val leftIcon: ImageVector,
    val rightTitle: String,
    val rightDescription: String,
    val rightIcon: ImageVector
)

private val welcomePages = listOf(
    // WELCOME SCREEN 1
    WelcomePage(
        image = R.drawable.welcome_reference,
        imageDescription = "People sharing a box of food",
        title = "Rescue surplus food,\nreduce waste",
        body = "Donate, sell, reserve, buy, or claim food " +
                "before it expires. Together, we can build " +
                "a more sustainable community.",
        leftTitle = "Find nearby\nfood fast",
        leftDescription = "Discover surplus food\nnear you in real time.",
        leftIcon = Icons.Outlined.LocationOn,
        rightTitle = "Support your\ncommunity",
        rightDescription = "Help reduce waste\nand feed more lives.",
        rightIcon = Icons.Outlined.Groups
    ),

    // WELCOME SCREEN 2  (TODO: replace image and text)
    WelcomePage(
        image = R.drawable.welcome_reference,
        imageDescription = "Welcome screen 2 image",
        title = "Share what you have,\nhelp who needs it",
        body = "Post extra food in seconds and let neighbors " +
                "donate, buy, or claim it before it goes to waste.",
        leftTitle = "Post food\nin seconds",
        leftDescription = "Add a photo, quantity,\nand pickup time.",
        leftIcon = Icons.Outlined.CardGiftcard,
        rightTitle = "Donate\nor sell",
        rightDescription = "You choose whether\nit is free or priced.",
        rightIcon = Icons.Outlined.LocalOffer
    ),

    // WELCOME SCREEN 3  (TODO: replace image and text)
    WelcomePage(
        image = R.drawable.welcome_reference,
        imageDescription = "Welcome screen 3 image",
        title = "Reserve, pick up,\nand enjoy",
        body = "Hold food for pickup, message the provider, " +
                "and collect it safely from people you can trust.",
        leftTitle = "Reserve\nahead",
        leftDescription = "Hold food until\nyou arrive to pick up.",
        leftIcon = Icons.Outlined.Schedule,
        rightTitle = "Safe and\nverified",
        rightDescription = "Profiles are checked\nto keep sharing safe.",
        rightIcon = Icons.Outlined.VerifiedUser
    )
)

@Composable
private fun WelcomeScreen(
    onStart: () -> Unit,
    onSignIn: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { welcomePages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == welcomePages.lastIndex

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val imageHeight = maxHeight * 0.36f

        Column(Modifier.fillMaxSize()) {
            // Swipeable welcome pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { index ->
                val page = welcomePages[index]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Image(
                        painter = painterResource(page.image),
                        contentDescription = page.imageDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(imageHeight)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    topStart = 32.dp,
                                    topEnd = 32.dp
                                )
                            )
                            .background(Cream)
                            .padding(horizontal = 26.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = page.title,
                            color = DarkGreen,
                            fontSize = 25.sp,
                            lineHeight = 31.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = page.body,
                            color = Gray,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FeatureCard(
                                title = page.leftTitle,
                                description = page.leftDescription,
                                icon = page.leftIcon,
                                modifier = Modifier.weight(1f)
                            )

                            FeatureCard(
                                title = page.rightTitle,
                                description = page.rightDescription,
                                icon = page.rightIcon,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Fixed bottom area: page dots + button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Cream)
                    .padding(horizontal = 26.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        9.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    repeat(welcomePages.size) { index ->
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) Green
                                    else Color(0xFFDDDEDA)
                                )
                        )
                    }
                }

                PrimaryButton(
                    title = if (lastPage) "Get Started" else "Next",
                    onClick = {
                        if (lastPage) {
                            onStart()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1
                                )
                            }
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PaleGreen)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(IconGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                title,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            description,
            color = Gray,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

/* SIGN IN, CREATE ACCOUNT, AND RESET PASSWORD */

@Composable
private fun SignInScreen(
    mode: String,
    onNavigate: (String) -> Unit,
    isAuthenticating: Boolean,
    onContinue: (
        email: String,
        password: String,
        confirmPassword: String,
        name: String
    ) -> Unit,
    onSocial: (String) -> Unit
) {
    val creatingAccount = mode == "signup"
    val resettingPassword = mode == "forgot"

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }

    // Passwords are retained only while this screen remains open.
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var keepSignedIn by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Fit shows the entire header image rather than cutting it off.
        Image(
            painter = painterResource(R.drawable.signin_reference),
            contentDescription =
                "Good food. Less waste. Stronger community.",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.05f)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 14.dp
                )
            ) {
                if (resettingPassword) {
                    Text(
                        "Reset password",
                        modifier = Modifier.padding(8.dp),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Green
                    )

                    Text(
                        "Enter the email address linked to your account.",
                        modifier = Modifier.padding(8.dp),
                        fontSize = 13.sp,
                        color = Gray
                    )
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        FormTab(
                            title = "Sign In",
                            selected = !creatingAccount,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate("signin") }
                        )

                        FormTab(
                            title = "Create Account",
                            selected = creatingAccount,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate("signup") }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (creatingAccount) {
                    InputField(
                        value = name,
                        onChange = { name = it },
                        hint = "Full name",
                        icon = Icons.Outlined.Person
                    )

                    Spacer(Modifier.height(10.dp))
                }

                InputField(
                    value = email,
                    onChange = { email = it },
                    hint = "Email address",
                    icon = Icons.Outlined.Email,
                    emailField = true
                )

                if (!resettingPassword) {
                    Spacer(Modifier.height(10.dp))

                    InputField(
                        value = password,
                        onChange = { password = it },
                        hint = "Password",
                        icon = Icons.Outlined.Lock,
                        passwordField = true
                    )

                    if (creatingAccount) {
                        Spacer(Modifier.height(10.dp))

                        InputField(
                            value = confirmPassword,
                            onChange = { confirmPassword = it },
                            hint = "Confirm password",
                            icon = Icons.Outlined.Lock,
                            passwordField = true
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        keepSignedIn = !keepSignedIn
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = keepSignedIn,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(20.dp),
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Green
                                    )
                                )

                                Spacer(Modifier.width(6.dp))

                                Text(
                                    "Keep me signed in",
                                    color = Gray,
                                    fontSize = 11.sp
                                )
                            }

                            TextButton(
                                onClick = { onNavigate("forgot") },
                                contentPadding = PaddingValues(
                                    horizontal = 2.dp
                                )
                            ) {
                                Text(
                                    "Forgot password?",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (creatingAccount || resettingPassword) {
                    Spacer(Modifier.height(16.dp))
                }

                PrimaryButton(
                    title = when {
                        resettingPassword -> "Send reset link"
                        creatingAccount -> "Create Account"
                        else -> "Continue"
                    },
                    enabled = !isAuthenticating,
                    onClick = {
                        onContinue(email, password, confirmPassword, name)
                    }
                )

                if (isAuthenticating) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.CenterHorizontally),
                        color = Green,
                        strokeWidth = 2.dp
                    )
                }

                if (resettingPassword) {
                    TextButton(
                        onClick = { onNavigate("signin") },
                        modifier = Modifier.align(
                            Alignment.CenterHorizontally
                        )
                    ) {
                        Text("Back to Sign In")
                    }
                } else {
                    Spacer(Modifier.height(18.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Line
                        )

                        Text(
                            "or continue with",
                            modifier = Modifier.padding(horizontal = 10.dp),
                            fontSize = 12.sp,
                            color = Gray
                        )

                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Line
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            12.dp,
                            Alignment.CenterHorizontally
                        )
                    ) {
                        ProviderButton("Google") {
                            onSocial("Google")
                        }
                        ProviderButton("Apple") {
                            onSocial("Apple")
                        }
                        ProviderButton("Facebook") {
                            onSocial("Facebook")
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    CommunityCard()
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = Green,
                modifier = Modifier.size(20.dp)
            )

            Text(
                "Your safety matters",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            "We verify profiles and monitor activity to keep\n" +
                    "food sharing safe and trustworthy.",
            modifier = Modifier.padding(horizontal = 20.dp),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = Gray
        )

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun FormTab(
    title: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            modifier = Modifier.padding(vertical = 12.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Green else Gray
        )

        HorizontalDivider(
            color = if (selected) Green else Line,
            thickness = if (selected) 2.dp else 1.dp
        )
    }
}

@Composable
private fun InputField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    icon: ImageVector,
    passwordField: Boolean = false,
    emailField: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(11.dp),
        textStyle = LocalTextStyle.current.copy(
            fontSize = 14.sp,
            color = DarkGreen
        ),
        placeholder = {
            Text(
                hint,
                fontSize = 13.sp,
                color = Gray
            )
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = Gray
            )
        },
        trailingIcon = {
            if (passwordField) {
                IconButton(
                    onClick = {
                        passwordVisible = !passwordVisible
                    }
                ) {
                    Icon(
                        imageVector = if (passwordVisible) {
                            Icons.Outlined.Visibility
                        } else {
                            Icons.Outlined.VisibilityOff
                        },
                        contentDescription = if (passwordVisible) {
                            "Hide password"
                        } else {
                            "Show password"
                        },
                        modifier = Modifier.size(21.dp),
                        tint = Gray
                    )
                }
            }
        },
        visualTransformation = if (
            passwordField && !passwordVisible
        ) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                passwordField -> KeyboardType.Password
                emailField -> KeyboardType.Email
                else -> KeyboardType.Text
            }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Green,
            unfocusedBorderColor = Line,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            cursorColor = Green
        )
    )
}

@Composable
private fun PrimaryButton(
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Green,
            contentColor = Color.White
        )
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CommunityCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(PaleGreen)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(IconGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Groups,
                contentDescription = null,
                modifier = Modifier.size(23.dp),
                tint = Green
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                "One account for all your good deeds",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(4.dp))

            Text(
                "Donate, sell, buy, and claim food —\n" +
                        "all from one simple account.",
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = Gray
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SmallCommunityIcon(Icons.Outlined.CardGiftcard)
                SmallCommunityIcon(Icons.Outlined.LocalOffer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SmallCommunityIcon(Icons.Outlined.ShoppingCart)
                SmallCommunityIcon(Icons.Outlined.ShoppingBag)
            }
        }
    }
}

@Composable
private fun SmallCommunityIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(23.dp)
            .clip(CircleShape)
            .background(IconGreen),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Green,
            modifier = Modifier.size(15.dp)
        )
    }
}

/* SOCIAL BUTTONS — NO CROPPED IMAGE FRAGMENTS */

@Composable
private fun ProviderButton(
    provider: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(width = 55.dp, height = 44.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Line),
        contentPadding = PaddingValues(10.dp)
    ) {
        when (provider) {
            "Google" -> GoogleMark()
            "Apple" -> AppleMark()
            else -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1877F2)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "f",
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 25.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleMark() {
    Canvas(Modifier.size(24.dp)) {
        val thickness = size.width * 0.19f
        val inset = thickness / 2f
        val arcSize = Size(
            size.width - thickness,
            size.height - thickness
        )
        val topLeft = Offset(inset, inset)

        drawArc(
            Color(0xFF4285F4),
            -35f, 80f, false,
            topLeft, arcSize,
            style = Stroke(thickness)
        )
        drawArc(
            Color(0xFF34A853),
            45f, 95f, false,
            topLeft, arcSize,
            style = Stroke(thickness)
        )
        drawArc(
            Color(0xFFFBBC05),
            140f, 60f, false,
            topLeft, arcSize,
            style = Stroke(thickness)
        )
        drawArc(
            Color(0xFFEA4335),
            200f, 100f, false,
            topLeft, arcSize,
            style = Stroke(thickness)
        )
        drawLine(
            Color(0xFF4285F4),
            Offset(size.width * 0.53f, size.height * 0.50f),
            Offset(size.width * 0.94f, size.height * 0.50f),
            strokeWidth = thickness
        )
    }
}

@Composable
private fun AppleMark() {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height

        val body = Path().apply {
            moveTo(w * 0.50f, h * 0.27f)
            cubicTo(
                w * 0.17f, h * 0.05f,
                -w * 0.02f, h * 0.43f,
                w * 0.22f, h * 0.85f
            )
            cubicTo(
                w * 0.35f, h * 1.04f,
                w * 0.40f, h * 0.85f,
                w * 0.51f, h * 0.91f
            )
            cubicTo(
                w * 0.70f, h * 1.05f,
                w * 0.85f, h * 0.78f,
                w * 0.90f, h * 0.57f
            )
            cubicTo(
                w * 0.96f, h * 0.26f,
                w * 0.72f, h * 0.12f,
                w * 0.50f, h * 0.27f
            )
            close()
        }

        drawPath(body, Color.Black)

        val leaf = Path().apply {
            moveTo(w * 0.49f, h * 0.21f)
            quadraticTo(
                w * 0.48f, h * 0.02f,
                w * 0.72f, 0f
            )
            quadraticTo(
                w * 0.72f, h * 0.18f,
                w * 0.49f, h * 0.21f
            )
            close()
        }

        drawPath(leaf, Color.Black)

        drawCircle(
            Color.White,
            radius = w * 0.15f,
            center = Offset(w * 0.92f, h * 0.40f)
        )
    }
}

/* HOMEPAGE */

@Composable
private fun HomeScreen(
    onLogout: () -> Unit,
    currentProfile: UserProfile?
) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var page by rememberSaveable { mutableStateOf("home") }
    var message by remember { mutableStateOf("") }
    var selectedFood by remember { mutableStateOf<HomeFood?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("Nearest") }
    val sessionKey = currentProfile?.id ?: "signed-out"
    val foodViewModel: FoodViewModel = viewModel(key = "food-$sessionKey")
    val reservationViewModel: ReservationViewModel = viewModel(key = "reservation-$sessionKey")
    val remotePosts by foodViewModel.availablePosts.collectAsStateWithLifecycle()
    val feedLoading by foodViewModel.loading.collectAsStateWithLifecycle()
    val feedError by foodViewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(currentProfile?.id) { foodViewModel.loadAvailablePosts() }
    DisposableEffect(currentProfile?.id) {
        onDispose {
            foodViewModel.clearSession()
            reservationViewModel.clearSession()
        }
    }

    val foods = remotePosts.map { it.toHomeFood() }

    // A fixed sample recommendation order; no recommendation algorithm yet.
    val recommendedFoods = foods

    val source = if (page == "recommended") {
        recommendedFoods
    } else {
        foods
    }

    val visibleFoods = source.filter { food ->
        val matchesSearch =
            food.title.contains(search, ignoreCase = true) ||
                    food.provider.contains(search, ignoreCase = true)

        val matchesCategory = when (category) {
            "Donation" -> food.price == "Free"
            "For Sale" -> food.price != "Free"
            "Nearby" -> food.distanceValue <= 1.5
            "Expiring Soon" -> food.urgent
            else -> true
        }

        matchesSearch && matchesCategory
    }.let { results ->
        if (sort == "Nearest") {
            results.sortedBy { it.distanceValue }
        } else {
            results.sortedBy { it.title }
        }
    }

    // Return from See all to the homepage with the phone's Back button.
    BackHandler(enabled = page != "home") {
        page = "home"
        search = ""
        category = "All"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cream)
    ) {
        Box(Modifier.weight(1f)) {
            // Separate scroll position when opening another listing screen.
            if (page == "alerts") {
                AlertsScreen(
                    onOpenFood = { title ->
                        selectedFood = foods.firstOrNull {
                            it.title == title
                        }
                    }
                )
            } else if (page == "post") {
                PostScreen(
                    onBack = {
                        foodViewModel.loadAvailablePosts()
                        page = "home"
                    },
                    foodViewModel = foodViewModel
                )
            } else if (page == "activity") {
                ActivityScreen(
                    onPost = { page = "post" },
                    onAlerts = { page = "alerts" },
                    foodViewModel = foodViewModel,
                    reservationViewModel = reservationViewModel
                )
            } else if (page == "profile") {
                ProfileScreen(
                    onAlerts = { page = "alerts" },
                    onLogout = onLogout,
                    remoteProfile = currentProfile,
                    onOpenFood = { title ->
                        selectedFood = foods.firstOrNull {
                            it.title == title
                        }
                    }
                )
            } else key(page) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 100.dp
                        )
                ) {
                    if (page == "home") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(IconGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Eco,
                                    contentDescription = null,
                                    tint = Green,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Good evening, Mika 👋",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkGreen
                                )

                                Text(
                                    "Make a difference today 🌿",
                                    fontSize = 12.sp,
                                    color = Gray
                                )
                            }

                            IconButton(
                                onClick = {
                                    page = "alerts"
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = "Open alerts",
                                    tint = DarkGreen
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    page = "home"
                                    search = ""
                                    category = "All"
                                }
                            ) {
                                Text("Back")
                            }

                            Text(
                                if (page == "recommended") {
                                    "All recommendations"
                                } else {
                                    "All food listings"
                                },
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .clickable {
                                message = "Current sample location:\n" +
                                        "Del Rosarrio, Balilihan"
                            }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = Green,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(Modifier.width(5.dp))

                        Text(
                            "Del Rosarrio, Balilihan",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Search food or providers",
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(13.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 13.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = Green,
                                unfocusedBorderColor = Line
                            )
                        )

                        FilledIconButton(
                            onClick = {
                                message =
                                    "Donation: free food\n" +
                                            "For Sale: paid food\n" +
                                            "Nearby: within 1.5 km\n" +
                                            "Expiring Soon: urgent pickup or expiry\n\n" +
                                            "Tap a category below to filter the listings."
                            },
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = IconGreen,
                                contentColor = DarkGreen
                            )
                        ) {
                            Icon(
                                Icons.Outlined.Tune,
                                contentDescription = "Filter information"
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.horizontalScroll(
                            rememberScrollState()
                        ),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        listOf(
                            "All",
                            "Donation",
                            "For Sale",
                            "Nearby",
                            "Expiring Soon"
                        ).forEach { label ->
                            FilterChip(
                                selected = category == label,
                                onClick = { category = label },
                                label = {
                                    Text(label, fontSize = 12.sp)
                                },
                                shape = RoundedCornerShape(22.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Cream,
                                    labelColor = Green,
                                    selectedContainerColor = Green,
                                    selectedLabelColor = Color.White
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    Green.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }

                    // Hide the general recommendations while filtering,
                    // so the selected category's results are clear.
                    if (
                        page == "home" &&
                        category == "All" &&
                        search.isBlank()
                    ) {
                        Spacer(Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "✦",
                                color = Color(0xFFFFA125),
                                fontSize = 23.sp
                            )

                            Spacer(Modifier.width(6.dp))

                            Text(
                                "Recommended for you",
                                modifier = Modifier.weight(1f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            TextButton(
                                onClick = {
                                    page = "recommended"
                                    category = "All"
                                    search = ""
                                }
                            ) {
                                Text("See all", fontSize = 12.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.horizontalScroll(
                                rememberScrollState()
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .width(134.dp)
                                    .height(180.dp),
                                shape = RoundedCornerShape(15.dp),
                                color = PaleGreen
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Icon(
                                        Icons.Outlined.Eco,
                                        contentDescription = null,
                                        tint = Green,
                                        modifier = Modifier.size(30.dp)
                                    )

                                    Text(
                                        "Help reduce food waste in your community.",
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp
                                    )

                                    Button(
                                        onClick = {
                                            message = "Share surplus food and " +
                                                    "discover available food in " +
                                                    "your community."
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(8.dp)
                                    ) {
                                        Text("Learn more", fontSize = 12.sp)
                                    }
                                }
                            }

                            recommendedFoods.take(2).forEach { food ->
                                RecommendationCard(
                                    title = food.title,
                                    photo = food.photo,
                                    badge = if (food.distanceValue <= 1.0) {
                                        "Nearby"
                                    } else {
                                        "Popular"
                                    },
                                    price = food.price,
                                    distance = food.distance,
                                    status = food.status,
                                    urgent = food.urgent,
                                    onClick = { selectedFood = food }
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        HorizontalDivider(color = Line)
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when {
                                    page == "recommended" -> "Recommended food"
                                    category == "Donation" -> "Food for donation"
                                    category == "For Sale" -> "Food for sale"
                                    category == "Nearby" -> "Within 1.5 km"
                                    category == "Expiring Soon" -> "Expiring soon"
                                    else -> "Available near you"
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "${visibleFoods.size} listings",
                                fontSize = 11.sp,
                                color = Gray
                            )
                        }

                        Box {
                            TextButton(
                                onClick = { sortMenu = true },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Sort: $sort", fontSize = 11.sp)
                                Icon(
                                    Icons.Outlined.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = sortMenu,
                                onDismissRequest = { sortMenu = false }
                            ) {
                                listOf("Nearest", "Name").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            sort = option
                                            sortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (visibleFoods.isEmpty()) {
                        Text(
                            if (feedLoading) "Loading listings…" else if (feedError != null) "Could not load listings. Try again." else "No available food listings yet.",
                            modifier = Modifier.padding(vertical = 20.dp),
                            fontSize = 13.sp,
                            color = Gray
                        )

                        TextButton(
                            onClick = {
                                search = ""
                                category = "All"
                            }
                        ) {
                            Text("Clear filters")
                        }
                    }

                    // All matching listings are shown; scroll to see more.
                    visibleFoods.forEach { food ->
                        key(food.title) {
                            AvailableFoodCard(
                                food = food,
                                onClick = { selectedFood = food }
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                    }

                    if (page == "recommended") {
                        PrimaryButton(
                            title = "Browse all 10 food listings",
                            onClick = {
                                page = "all"
                                category = "All"
                                search = ""
                            }
                        )
                    }
                }
            }

            if (
                page != "alerts" &&
                page != "post" &&
                page != "activity" &&
                page != "profile"
            ) FloatingActionButton(
                onClick = {
                    page = "post"
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = CircleShape,
                containerColor = Green,
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(27.dp)
                    )

                    Text("Post Food", fontSize = 10.sp)
                }
            }
        }

        Surface(
            color = Color.White,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "Home" to Icons.Outlined.Home,
                    (
                            if (page == "activity") {
                                "Browse" to Icons.Outlined.Search
                            } else {
                                "Alerts" to Icons.Outlined.Notifications
                            }
                            ),
                    "Post" to Icons.Outlined.Add,
                    "Activity" to Icons.Outlined.Timeline,
                    "Profile" to Icons.Outlined.Person
                ).forEach { (label, icon) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                when (label) {
                                    "Home" -> {
                                        page = "home"
                                        category = "All"
                                        search = ""
                                    }

                                    "Alerts" -> {
                                        page = "alerts"
                                    }

                                    "Post" -> {
                                        page = "post"
                                    }

                                    "Activity" -> {
                                        page = "activity"
                                    }

                                    "Browse" -> {
                                        page = "all"
                                        category = "All"
                                        search = ""
                                    }

                                    "Profile" -> {
                                        page = "profile"
                                    }

                                    else -> {
                                        message = "$label will open here. " +
                                                "This screen will be added next."
                                    }
                                }
                            },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (label == "Post") {
                            Box(
                                modifier = Modifier
                                    .size(width = 36.dp, height = 28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Green),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        } else {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (
                                    (label == "Alerts" && page == "alerts") ||
                                    (label == "Activity" && page == "activity") ||
                                    (label == "Profile" && page == "profile") ||
                                    (
                                            label == "Home" &&
                                                    page !in listOf("alerts", "post", "activity", "profile")
                                            )
                                ) Green else Gray,
                                modifier = Modifier.size(25.dp)
                            )
                        }

                        Spacer(Modifier.height(3.dp))

                        Text(
                            label,
                            fontSize = 10.sp,
                            color = if (
                                label == "Post" ||
                                (label == "Alerts" && page == "alerts") ||
                                (label == "Activity" && page == "activity") ||
                                (label == "Profile" && page == "profile") ||
                                (
                                        label == "Home" &&
                                                page !in listOf("alerts", "post", "activity", "profile")
                                        )
                            ) {
                                Green
                            } else {
                                Gray
                            }
                        )
                    }
                }
            }
        }
    }

    if (message.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { message = "" },
            title = { Text("Salu-Salo") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { message = "" }) {
                    Text("OK")
                }
            }
        )

    }

    selectedFood?.let { food ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { selectedFood = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            FoodDetailsScreen(
                food = food,
                isOwnFood = currentProfile?.id != null && food.donorId == currentProfile.id,
                reservationViewModel = reservationViewModel,
                onReserved = {
                    foodViewModel.loadAvailablePosts()
                    selectedFood = null
                },
                onBack = { selectedFood = null }
            )
        }
    }
}

private fun FoodPost.toHomeFood(): HomeFood {
    val expiration = runCatching { Instant.parse(expirationDate) }.getOrNull()
    val urgent = expiration?.isBefore(Instant.now().plusSeconds(3 * 60 * 60)) == true
    return HomeFood(
        id = id,
        donorId = donorId,
        title = name,
        quantity = "$quantity $unit",
        price = "Free",
        status = if (urgent) "Expires soon" else "Available",
        distance = pickupLocation,
        distanceValue = 0.0,
        provider = "Food donor",
        rating = "New",
        photo = R.drawable.food_produce,
        imageUrl = displayImageUrl,
        urgent = urgent
    )
}
private data class HomeFood(
    val id: String,
    val donorId: String,
    val title: String,
    val quantity: String,
    val price: String,
    val status: String,
    val distance: String,
    val distanceValue: Double,
    val provider: String,
    val rating: String,
    val photo: Int,
    val imageUrl: String? = null,
    val urgent: Boolean
)

@Composable
private fun RecommendationCard(
    title: String,
    photo: Int,
    badge: String,
    price: String,
    distance: String,
    status: String,
    urgent: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(142.dp)
            .height(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column {
            Box {
                Image(
                    painter = painterResource(photo),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(94.dp)
                )

                Text(
                    badge,
                    modifier = Modifier
                        .padding(7.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(DarkGreen)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    color = Color.White,
                    fontSize = 10.sp
                )
            }

            Column(Modifier.padding(8.dp)) {
                Text(
                    title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )

                Spacer(Modifier.height(4.dp))

                Row {
                    Text(
                        price,
                        color = if (urgent) Color(0xFFE85B13) else Green,
                        fontSize = 12.sp
                    )

                    Text(
                        "  •  $distance",
                        color = Gray,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    status,
                    color = if (urgent) Color(0xFFE85B13) else Green,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun AvailableFoodCard(
    food: HomeFood,
    onClick: () -> Unit
) {
    var saved by rememberSaveable(food.title) {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.heightIn(min = 116.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var imageFailed by remember(food.id, food.imageUrl) { mutableStateOf(false) }
            if (food.imageUrl != null && !imageFailed) {
                AsyncImage(
                    model = food.imageUrl,
                    contentDescription = food.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(92.dp).height(116.dp).clip(RoundedCornerShape(14.dp)),
                    onError = { imageFailed = true }
                )
            } else if (food.imageUrl == null) {
                Image(
                    painter = painterResource(food.photo),
                    contentDescription = food.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(92.dp).height(116.dp).clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    modifier = Modifier.width(92.dp).height(116.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFF2F6E6)),
                    contentAlignment = Alignment.Center
                ) { Text("Image unavailable", fontSize = 10.sp, color = Gray) }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 3.dp, top = 7.dp, bottom = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        food.title,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = { saved = !saved },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (saved) {
                                Icons.Outlined.Bookmark
                            } else {
                                Icons.Outlined.BookmarkBorder
                            },
                            contentDescription = if (saved) {
                                "Remove saved ${food.title}"
                            } else {
                                "Save ${food.title}"
                            },
                            tint = if (saved) Green else DarkGreen,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(food.quantity, fontSize = 11.sp, color = Gray)
                    Text("  •  ", fontSize = 11.sp, color = Gray)

                    Text(
                        food.price,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaleGreen)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Green,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(5.dp))

                Text(
                    "${food.status}  •  ${food.distance}",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = if (food.urgent) {
                        Color(0xFFE85B13)
                    } else {
                        Green
                    }
                )

                Spacer(Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = Green,
                        modifier = Modifier.size(13.dp)
                    )

                    Spacer(Modifier.width(3.dp))

                    Text(
                        food.provider,
                        fontSize = 9.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        "★ ${food.rating}",
                        fontSize = 9.sp,
                        color = Color(0xFF9A7517),
                        modifier = Modifier.padding(end = 5.dp)
                    )
                }
            }
        }
    }
}

/* FOOD DETAILS SCREEN */

@Composable
private fun FoodDetailsScreen(
    food: HomeFood,
    isOwnFood: Boolean,
    reservationViewModel: ReservationViewModel,
    onReserved: () -> Unit,
    onBack: () -> Unit
) {
    val available = food.quantity
        .substringBefore(" ")
        .toIntOrNull()
        ?: 1

    val unit = food.quantity.substringAfter(" ", "items")
    val donation = food.price == "Free"

    var quantity by rememberSaveable(food.title) {
        mutableStateOf(1)
    }
    var saved by rememberSaveable(food.title) {
        mutableStateOf(false)
    }
    var expanded by rememberSaveable(food.title) {
        mutableStateOf(false)
    }
    var notice by remember { mutableStateOf("") }
    var noticeTitle by remember { mutableStateOf("") }
    var foodAction by rememberSaveable(food.title) {
        mutableStateOf("")
    }

    var pickupChoice by rememberSaveable(food.title) {
        mutableStateOf("")
    }

    if (foodAction.isNotEmpty()) {
        FoodActionsDialog(
            action = foodAction,
            foodTitle = food.title,
            provider = food.provider,
            rating = food.rating,
            photo = food.photo,
            price = food.price,
            quantity = quantity,
            unit = unit,
            status = food.status,
            pickupChoice = pickupChoice,
            onPickupSelected = { pickupChoice = it },
            onClose = { foodAction = "" }
        )
    }

    val reservationMessage by reservationViewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(reservationMessage) {
        reservationMessage?.let { result ->
            noticeTitle = if (result.startsWith("Reservation created")) "Reserved" else "Reservation"
            notice = result
            reservationViewModel.clearMessage()
            if (result.startsWith("Reservation created")) onReserved()
        }
    }
    val description = when (food.photo) {
        R.drawable.food_bread ->
            "Assorted bread including baguettes, whole wheat, " +
                    "and multigrain loaves."

        R.drawable.food_vegetables ->
            "A selection of mixed vegetables available for pickup " +
                    "from a community provider."

        R.drawable.food_meals ->
            "Packed rice meals available for collection during " +
                    "the listed pickup window."

        R.drawable.food_produce ->
            "A mixed produce box with vegetables for sharing " +
                    "or preparing meals at home."

        else ->
            "An assortment of pastries available for pickup " +
                    "from a local provider."
    }

    val allergens = if (food.photo == R.drawable.food_bread) {
        "Wheat, gluten, sesame"
    } else {
        "Not provided — ask the provider"
    }

    val orange = Color(0xFFE85B13)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Cream
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Food photograph and floating controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                Image(
                    painter = painterResource(food.photo),
                    contentDescription = food.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                FilledIconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .size(42.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = DarkGreen
                    )
                ) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back to food listings"
                    )
                }

                FilledIconButton(
                    onClick = { saved = !saved },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(42.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Green
                    )
                ) {
                    Icon(
                        imageVector = if (saved) {
                            Icons.Outlined.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = if (saved) {
                            "Remove bookmark"
                        } else {
                            "Save food"
                        }
                    )
                }
            }

            // Rounded details panel
            Column(
                modifier = Modifier
                    .offset(y = (-20).dp)
                    .fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = 26.dp,
                            topEnd = 26.dp
                        )
                    )
                    .background(Cream)
                    .padding(18.dp)
            ) {
                Text(
                    food.title,
                    fontSize = 25.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkGreen
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        shadowElevation = 1.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 8.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Storefront,
                                contentDescription = null,
                                tint = Green,
                                modifier = Modifier.size(17.dp)
                            )

                            Spacer(Modifier.width(5.dp))

                            Text(
                                food.provider,
                                fontSize = 11.sp,
                                maxLines = 2
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            "⭐ ${food.rating}",
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 8.dp
                            ),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Four summary columns
                Row(Modifier.fillMaxWidth()) {
                    FoodSummaryItem(
                        value = food.quantity,
                        caption = "Available",
                        modifier = Modifier.weight(1f)
                    )

                    FoodSummaryItem(
                        value = food.price,
                        caption = if (donation) "Donation" else "For sale",
                        modifier = Modifier.weight(1f)
                    )

                    FoodSummaryItem(
                        value = food.distance,
                        caption = "Distance",
                        modifier = Modifier.weight(1f)
                    )

                    FoodSummaryItem(
                        value = food.status,
                        caption = "Pickup",
                        modifier = Modifier.weight(1.25f),
                        valueColor = if (food.urgent) orange else Green
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    description,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = DarkGreen
                )

                if (expanded) {
                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Coordinate your pickup with the provider. " +
                                "Confirm the food contents, condition, and " +
                                "collection instructions before requesting it.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Gray
                    )
                }

                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        if (expanded) "Read less" else "Read more",
                        fontSize = 12.sp
                    )

                    Icon(
                        imageVector = if (expanded) {
                            Icons.Outlined.ExpandLess
                        } else {
                            Icons.Outlined.ExpandMore
                        },
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Information card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Column {
                        FoodInfoRow(
                            icon = Icons.Outlined.Eco,
                            title = "Allergens",
                            value = allergens,
                            onClick = { foodAction = "allergens" }
                        )

                        HorizontalDivider(color = Line)

                        FoodInfoRow(
                            icon = Icons.Outlined.Schedule,
                            title = "Pickup Window",
                            value = food.status,
                            onClick = { foodAction = "pickup" }
                        )

                        HorizontalDivider(color = Line)

                        FoodInfoRow(
                            icon = Icons.Outlined.LocationOn,
                            title = "Pickup Address",
                            value = "Del Rosarrio, Balilihan",
                            onClick = { foodAction = "location" }
                        )

                        OutlinedButton(
                            onClick = { foodAction = "location" },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 12.dp, bottom = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(
                                horizontal = 10.dp,
                                vertical = 0.dp
                            )
                        ) {
                            Icon(
                                Icons.Outlined.Map,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )

                            Spacer(Modifier.width(5.dp))
                            Text("View on map", fontSize = 11.sp)
                        }

                        HorizontalDivider(color = Line)

                        FoodInfoRow(
                            icon = Icons.Outlined.AcUnit,
                            title = "Storage Note",
                            value = "Ask the provider for storage instructions.",
                            onClick = { foodAction = "storage" }
                        )

                        HorizontalDivider(color = Line)

                        FoodInfoRow(
                            icon = Icons.Outlined.Person,
                            title = if (donation) "Donor" else "Seller",
                            value = food.provider,
                            onClick = { foodAction = "provider" }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Quantity selector
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Select quantity",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    "Up to ${food.quantity}",
                                    fontSize = 11.sp,
                                    color = Gray
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Green),
                                color = PaleGreen
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { quantity-- },
                                        enabled = quantity > 1
                                    ) {
                                        Icon(
                                            Icons.Outlined.Remove,
                                            contentDescription = "Decrease quantity"
                                        )
                                    }

                                    Text(
                                        quantity.toString(),
                                        modifier = Modifier.widthIn(min = 24.dp),
                                        textAlign = TextAlign.Center,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    IconButton(
                                        onClick = { quantity++ },
                                        enabled = quantity < available
                                    ) {
                                        Icon(
                                            Icons.Outlined.Add,
                                            contentDescription = "Increase quantity"
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(7.dp))

                        Text(
                            "You’re requesting $quantity $unit",
                            modifier = Modifier.align(Alignment.End),
                            fontSize = 11.sp,
                            color = Gray
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Main actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    FoodActionButton(
                        title = if (donation) "Message Donor" else "Message Seller",
                        subtitle = "Ask a question",
                        background = Color.White,
                        foreground = Green,
                        modifier = Modifier.weight(1f),
                        onClick = { foodAction = "message" }
                    )

                    FoodActionButton(
                        title = if (isOwnFood) "Your listing" else "Reserve",
                        subtitle = if (isOwnFood) "You cannot reserve it" else "Hold for pickup",
                        background = PaleGreen,
                        foreground = Green,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isOwnFood) {
                                noticeTitle = "Your listing"
                                notice = "You cannot reserve your own food post."
                            } else {
                                reservationViewModel.reserve(food.id, quantity)
                            }
                        }
                    )

                    FoodActionButton(
                        title = if (donation) "Claim Now" else "Buy Now",
                        subtitle = "Confirm & pickup",
                        background = Green,
                        foreground = Color.White,
                        modifier = Modifier.weight(1f),
                        onClick = { foodAction = "checkout" }
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    if (donation) {
                        "This is a free donation. Please be respectful " +
                                "and pick up on time."
                    } else {
                        "Please confirm the price and pickup arrangements " +
                                "with the seller."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    color = Gray
                )
            }
        }
    }

    if (notice.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { notice = "" },
            title = { Text(noticeTitle) },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = { notice = "" }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun FoodSummaryItem(
    value: String,
    caption: String,
    modifier: Modifier,
    valueColor: Color = DarkGreen
) {
    Column(
        modifier = modifier.padding(end = 6.dp)
    ) {
        Text(
            value,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )

        Spacer(Modifier.height(5.dp))

        Text(
            caption,
            fontSize = 10.sp,
            color = Gray
        )
    }
}

@Composable
private fun FoodInfoRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Green,
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            title,
            modifier = Modifier.width(83.dp),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            value,
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = Gray
        )

        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = Gray,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun FoodActionButton(
    title: String,
    subtitle: String,
    background: Color,
    foreground: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 70.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = background,
        border = BorderStroke(1.dp, PaleGreen)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 5.dp,
                vertical = 12.dp
            ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                title,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = foreground
            )

            Spacer(Modifier.height(4.dp))

            Text(
                subtitle,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                color = foreground
            )
        }
    }
}








