package com.example.salusalo

import android.os.Bundle
import coil3.compose.AsyncImage
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.ListingHandover
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.viewmodel.SavedListingViewModel
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.reservationTotal
import com.example.salusalo.viewmodel.FoodViewModel
import com.example.salusalo.viewmodel.ReservationViewModel
import com.example.salusalo.viewmodel.MessageViewModel
import com.example.salusalo.viewmodel.NotificationViewModel
import com.example.salusalo.model.NotificationType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalTime
import com.example.salusalo.data.UserRepository
import com.example.salusalo.data.clearGoogleCredentialState
import com.example.salusalo.data.requestGoogleIdToken
import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
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
import kotlinx.coroutines.delay
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

        // "Keep me signed in" unchecked: end the session when the app starts
        // fresh (for example after being swiped away), but not on rotation or
        // when Android restores the app, which pass a saved state.
        if (savedInstanceState == null) {
            val auth = FirebaseAuth.getInstance()
            if (!keepSignedIn(this) && auth.currentUser != null) {
                auth.signOut()
                clearLocalAccountData(this)
            }
        }

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

// Device-only "Keep me signed in" choice. It survives sign-out, so the next
// fresh start can act on the choice made at the last sign-in. When it was never
// saved (a session from an older build), the session is kept.
private fun sessionPreferences(context: Context) =
    context.getSharedPreferences("salusalo_session", 0)

private fun keepSignedIn(context: Context): Boolean =
    sessionPreferences(context).getBoolean("keep_signed_in", true)

private fun setKeepSignedIn(context: Context, keep: Boolean) {
    sessionPreferences(context).edit().putBoolean("keep_signed_in", keep).apply()
}

// Remove local, account-specific data before another user can sign in on this device.
private fun clearLocalAccountData(context: Context) {
    context.getSharedPreferences("salusalo_profile", 0).edit().clear().apply()
    context.getSharedPreferences("salusalo_local_posts", 0).edit().clear().apply()
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
        clearLocalAccountData(context)
        // Also forget the Google account chosen in Credential Manager (best effort).
        appScope.launch { clearGoogleCredentialState(context) }
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

    fun signInWithGoogle(keep: Boolean) {
        isAuthenticating = true
        appScope.launch {
            try {
                val idToken = requestGoogleIdToken(context)
                firebaseAuth
                    .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                    .await()
                setKeepSignedIn(context, keep)
                isAuthenticating = false
                // Like sign-up: a new Google user's role claim may arrive later,
                // and the profile is created with the Google name and email.
                openAuthenticatedHome(refreshFirebaseToken = true)
            } catch (error: GetCredentialCancellationException) {
                // The user closed the account picker: nothing to report.
                isAuthenticating = false
            } catch (error: NoCredentialException) {
                isAuthenticating = false
                message = "No Google account was found on this phone. Add one in " +
                        "your phone's Settings, then try again." + errorDetails(error)
            } catch (error: FirebaseAuthException) {
                isAuthenticating = false
                message = authErrorMessage(error)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                isAuthenticating = false
                message = "Google sign-in failed. Please try again." + errorDetails(error)
            }
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
                        currentProfile = currentProfile,
                        onProfileUpdated = { currentProfile = it }
                    )

                    else -> SignInScreen(
                        mode = currentScreen,
                        onNavigate = { screen = it },
                        isAuthenticating = isAuthenticating,
                        onContinue = { email, password, confirmPassword, name, keep ->
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
                                                    // A new account stays signed in.
                                                    setKeepSignedIn(context, true)
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
                                                    setKeepSignedIn(context, keep)
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
                        onGoogle = { keep -> signInWithGoogle(keep) }
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

// A second line with the exception's own text, so setup problems (for example
// a missing SHA-1) can be read on a test phone without logcat.
private fun errorDetails(error: Exception): String =
    error.message?.takeIf { it.isNotBlank() }?.let { "\nDetails: $it" }.orEmpty()

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
        leftTitle = "Find surplus\nfood",
        leftDescription = "Browse food shared\nin your community.",
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
        leftDescription = "Add a photo, quantity,\nand pickup location.",
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
                "and collect it at the agreed place.",
        leftTitle = "Reserve\nahead",
        leftDescription = "Hold food until\nyou arrive to pick up.",
        leftIcon = Icons.Outlined.Schedule,
        rightTitle = "Message\nbefore pickup",
        rightDescription = "Chat with the donor\nto arrange pickup.",
        rightIcon = Icons.Outlined.ChatBubbleOutline
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
        name: String,
        keepSignedIn: Boolean
    ) -> Unit,
    onGoogle: (keepSignedIn: Boolean) -> Unit
) {
    val creatingAccount = mode == "signup"
    val resettingPassword = mode == "forgot"

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }

    // Passwords are retained only while this screen remains open.
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    // Starts unchecked: staying signed in is the user's choice at each sign-in.
    var keepSignedIn by rememberSaveable { mutableStateOf(false) }

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
                        onContinue(email, password, confirmPassword, name, keepSignedIn)
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

                    // A new account made on the Create Account tab stays signed in.
                    GoogleButton(enabled = !isAuthenticating) {
                        onGoogle(if (creatingAccount) true else keepSignedIn)
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
            "Agree on pickup details in the chat and check\n" +
                    "the food before you take it.",
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
private fun GoogleButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        GoogleMark()
        Spacer(Modifier.width(10.dp))
        Text(
            "Continue with Google",
            color = DarkGreen,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
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

/* HOMEPAGE */

@Composable
private fun HomeScreen(
    onLogout: () -> Unit,
    currentProfile: UserProfile?,
    onProfileUpdated: (UserProfile) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var page by rememberSaveable { mutableStateOf("home") }
    var message by remember { mutableStateOf("") }
    var selectedFood by remember { mutableStateOf<HomeFood?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("Newest") }
    val sessionKey = currentProfile?.id ?: "signed-out"
    val foodViewModel: FoodViewModel = viewModel(key = "food-$sessionKey")
    val reservationViewModel: ReservationViewModel = viewModel(key = "reservation-$sessionKey")
    val messageViewModel: MessageViewModel = viewModel(key = "messages-$sessionKey")
    val notificationViewModel: NotificationViewModel = viewModel(key = "notifications-$sessionKey")
    val savedListingViewModel: SavedListingViewModel = viewModel(key = "saved-$sessionKey")
    val savedIds by savedListingViewModel.savedIds.collectAsStateWithLifecycle()
    val savedMessage by savedListingViewModel.message.collectAsStateWithLifecycle()
    val activeChat by messageViewModel.active.collectAsStateWithLifecycle()
    val chatOpening by messageViewModel.opening.collectAsStateWithLifecycle()
    val messageError by messageViewModel.message.collectAsStateWithLifecycle()
    val alerts by notificationViewModel.notifications.collectAsStateWithLifecycle()
    val unreadAlerts by notificationViewModel.unreadCount.collectAsStateWithLifecycle()
    val unreadMessageAlerts = alerts.count { it.readAt == null && it.type == NotificationType.MESSAGE }
    // Tab Activity opens on when an alert sends the user there.
    var activityTab by rememberSaveable { mutableStateOf("My Listings") }
    // Reservation a "Reservation claimed" alert asked Activity to open.
    var activityFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val remotePosts by foodViewModel.availablePosts.collectAsStateWithLifecycle()
    val feedLoading by foodViewModel.loading.collectAsStateWithLifecycle()
    val feedError by foodViewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(currentProfile?.id) { foodViewModel.loadAvailablePosts() }
    // Alerts load once per session and then arrive live.
    LaunchedEffect(currentProfile?.id) { currentProfile?.id?.let { notificationViewModel.start(it) } }
    // Bookmarks load once per session, so the icons show the right state at once.
    LaunchedEffect(currentProfile?.id) { currentProfile?.id?.let { savedListingViewModel.start(it) } }
    // Opening a chat marks its message alerts read on the server; mirror that
    // for alerts that arrive while the chat is open.
    LaunchedEffect(activeChat?.id, alerts) {
        activeChat?.id?.let { id ->
            if (alerts.any { it.conversationId == id && it.readAt == null }) {
                notificationViewModel.markConversationReadLocally(id)
            }
        }
    }
    LaunchedEffect(page) {
        if (page != "activity") {
            activityTab = "My Listings"
            activityFocus = null
        }
    }

    // Greeting follows the signed-in profile and the device's local time;
    // the hour is re-read every minute so it changes while Home stays open.
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hour = LocalTime.now().hour
        }
    }
    val timeGreeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val firstName = currentProfile?.name?.trim()?.substringBefore(" ").orEmpty()
    val greeting = if (firstName.isBlank()) "$timeGreeting 👋" else "$timeGreeting, $firstName 👋"
    DisposableEffect(currentProfile?.id) {
        onDispose {
            foodViewModel.clearSession()
            reservationViewModel.clearSession()
            messageViewModel.clearSession()
            notificationViewModel.clearSession()
            savedListingViewModel.clearSession()
        }
    }

    // The feed arrives newest first (created_at).
    val foods = remotePosts.map { it.toHomeFood() }

    val visibleFoods = foods.filter { food ->
        val matchesSearch =
            food.title.contains(search, ignoreCase = true) ||
                    food.donorName.contains(search, ignoreCase = true)

        val matchesCategory = when (category) {
            "Donation" -> food.price == "Free"
            "For Sale" -> food.price != "Free"
            "Expiring Soon" -> food.urgent
            else -> true
        }

        matchesSearch && matchesCategory
    }.let { results ->
        if (sort == "Newest") {
            results.sortedByDescending { parseTimestamp(it.createdAt) ?: Instant.EPOCH }
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
                    notificationViewModel = notificationViewModel,
                    onOpenConversation = { id -> messageViewModel.openConversation(id) },
                    onOpenActivity = { tab ->
                        activityTab = tab
                        page = "activity"
                    },
                    onOpenClaimedReservation = { reservationId ->
                        // Claims until the reservation loads; Activity then switches
                        // to Purchases if it was priced.
                        activityTab = "Claims"
                        activityFocus = reservationId
                        page = "activity"
                    }
                )
            } else if (page == "messages") {
                MessagesScreen(
                    messageViewModel = messageViewModel,
                    onBack = { page = "home" }
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
                    reservationViewModel = reservationViewModel,
                    messageViewModel = messageViewModel,
                    unreadAlerts = unreadAlerts,
                    initialTab = activityTab,
                    focusReservationId = activityFocus,
                    onFocusHandled = { activityFocus = null }
                )
            } else if (page == "profile") {
                ProfileScreen(
                    onAlerts = { page = "alerts" },
                    onLogout = onLogout,
                    notificationViewModel = notificationViewModel,
                    remoteProfile = currentProfile,
                    onProfileUpdated = onProfileUpdated,
                    onOpenFood = { title ->
                        selectedFood = foods.firstOrNull {
                            it.title == title
                        }
                    },
                    savedListingViewModel = savedListingViewModel,
                    // Saved Listings opens a listing by its ID, from the row it just loaded.
                    onOpenListing = { post -> selectedFood = post.toHomeFood() }
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
                                    greeting,
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

                            IconButton(onClick = { page = "messages" }) {
                                BadgedBox(
                                    badge = {
                                        if (unreadMessageAlerts > 0) {
                                            Badge { Text(if (unreadMessageAlerts > 99) "99+" else "$unreadMessageAlerts") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = "Open messages",
                                        tint = DarkGreen
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    page = "alerts"
                                }
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadAlerts > 0) {
                                            Badge { Text(if (unreadAlerts > 99) "99+" else "$unreadAlerts") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.Notifications,
                                        contentDescription = "Open alerts",
                                        tint = DarkGreen
                                    )
                                }
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
                                "All food listings",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                                    "Search food or donors",
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
                                "Newest listings",
                                modifier = Modifier.weight(1f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            TextButton(
                                onClick = {
                                    page = "all"
                                    category = "All"
                                    search = ""
                                    sort = "Newest"
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

                            foods.take(2).forEach { food ->
                                RecommendationCard(
                                    title = food.title,
                                    photo = food.photo,
                                    imageUrl = food.imageUrl,
                                    price = food.price,
                                    pickupLocation = food.pickupLocation,
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
                                when (category) {
                                    "Donation" -> "Food for donation"
                                    "For Sale" -> "Food for sale"
                                    "Expiring Soon" -> "Expiring soon"
                                    else -> "Available food"
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
                                listOf("Newest", "Name").forEach { option ->
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
                        key(food.id) {
                            AvailableFoodCard(
                                food = food,
                                // Your own listings can't be bookmarked.
                                canSave = food.donorId != currentProfile?.id,
                                saved = food.id in savedIds,
                                onToggleSave = { savedListingViewModel.toggle(food.id) },
                                onClick = { selectedFood = food }
                            )
                        }

                        Spacer(Modifier.height(10.dp))
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
                saved = food.id in savedIds,
                onToggleSave = { savedListingViewModel.toggle(food.id) },
                loadExtraPhotos = { foodViewModel.extraPhotoUrls(food.post) },
                reservationViewModel = reservationViewModel,
                onReserved = {
                    foodViewModel.loadAvailablePosts()
                    selectedFood = null
                },
                onMessage = { draft -> messageViewModel.openForListing(food.id, draft) },
                onBack = { selectedFood = null }
            )
        }
    }

    // The open conversation (Phase 4), drawn over any other screen or dialog.
    if (activeChat != null || chatOpening) {
        ChatScreen(
            messageViewModel = messageViewModel,
            myProfileId = currentProfile?.id,
            onClose = { messageViewModel.close() }
        )
    }

    savedMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { savedListingViewModel.clearMessage() },
            title = { Text("Saved Listings") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { savedListingViewModel.clearMessage() }) { Text("OK") } }
        )
    }

    messageError?.let { error ->
        AlertDialog(
            onDismissRequest = { messageViewModel.clearMessage() },
            title = { Text("Messages") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { messageViewModel.clearMessage() }) { Text("OK") } }
        )
    }
}

private fun FoodPost.toHomeFood(): HomeFood {
    val expiration = parseTimestamp(expirationDate)
    val urgent = expiration?.isBefore(Instant.now().plusSeconds(3 * 60 * 60)) == true
    return HomeFood(
        id = id,
        donorId = donorId,
        title = name,
        quantity = "$quantity $unit",
        price = if (price > 0) formatPeso(price) else "Free",
        unitPrice = price,
        status = if (urgent) "Expires soon" else "Available",
        pickupLocation = pickupLocation,
        donorName = donorName?.takeIf { it.isNotBlank() } ?: "Donor name unavailable",
        photo = R.drawable.food_produce,
        imageUrl = displayImageUrl,
        urgent = urgent,
        description = description?.takeIf { it.isNotBlank() },
        createdAt = createdAt,
        post = this
    )
}
private data class HomeFood(
    val id: String,
    val donorId: String,
    val title: String,
    val quantity: String,
    val price: String,
    val status: String,
    val pickupLocation: String,
    // Listing price per unit (0 = free). The amount owed comes from the reservation.
    val unitPrice: Double = 0.0,
    val donorName: String,
    val photo: Int,
    val imageUrl: String? = null,
    val urgent: Boolean,
    val description: String? = null,
    val createdAt: String? = null,
    // The listing itself, for the details that the cards don't show.
    val post: FoodPost
)

@Composable
private fun RecommendationCard(
    title: String,
    photo: Int,
    imageUrl: String?,
    price: String,
    pickupLocation: String,
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
                var imageFailed by remember(imageUrl) { mutableStateOf(false) }
                if (imageUrl != null && !imageFailed) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(94.dp),
                        onError = { imageFailed = true }
                    )
                } else if (imageUrl == null) {
                    Image(
                        painter = painterResource(photo),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(94.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(94.dp)
                            .background(Color(0xFFF2F6E6)),
                        contentAlignment = Alignment.Center
                    ) { Text("Image unavailable", fontSize = 10.sp, color = Gray) }
                }
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
                        "  •  $pickupLocation",
                        color = Gray,
                        fontSize = 11.sp,
                        maxLines = 1
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
    canSave: Boolean,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onClick: () -> Unit
) {
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

                    if (canSave) IconButton(
                        onClick = onToggleSave,
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
                    "${food.status}  •  ${food.pickupLocation}",
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
                        Icons.Outlined.Person,
                        contentDescription = null,
                        tint = Green,
                        modifier = Modifier.size(13.dp)
                    )

                    Spacer(Modifier.width(3.dp))

                    Text(
                        food.donorName,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 5.dp)
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
    saved: Boolean,
    onToggleSave: () -> Unit,
    loadExtraPhotos: suspend () -> List<String?>,
    reservationViewModel: ReservationViewModel,
    onReserved: () -> Unit,
    onMessage: (String) -> Unit,
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

    // Opens the real conversation with this listing's donor (Phase 4).
    fun messageDonor(draft: String) {
        if (isOwnFood) {
            noticeTitle = "Your listing"
            notice = "You cannot message yourself. Questions from others about this listing appear in Messages."
        } else {
            foodAction = ""
            onMessage(draft)
        }
    }

    if (foodAction.isNotEmpty()) {
        FoodActionsDialog(
            action = foodAction,
            foodTitle = food.title,
            provider = food.donorName,
            photo = food.photo,
            pickupLocation = food.pickupLocation,
            imageUrl = food.imageUrl,
            price = food.price,
            quantity = quantity,
            unit = unit,
            status = food.status,
            pickupChoice = pickupChoice,
            onPickupSelected = { pickupChoice = it },
            onClose = { foodAction = "" },
            onMessage = { draft -> messageDonor(draft) }
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
    val description = food.description ?: "No description provided."

    // Listings made before these fields existed, or left blank, have none.
    // Blank allergens mean "not provided", never "allergen-free".
    val notProvided = "Not provided — ask the donor"
    val notSet = "Not set — ask the donor"
    val post = food.post
    val allergens = post.allergens?.takeIf { it.isNotBlank() } ?: notProvided
    val storageNote = post.storageNotes?.takeIf { it.isNotBlank() } ?: notProvided
    val pickupWindow = listingPickupWindow(post.pickupStart, post.pickupEnd) ?: notSet
    val handover = ListingHandover.fromCode(post.handover)?.label ?: notSet
    val expires = parseTimestamp(post.expirationDate)?.let { listingTime(it) } ?: "—"

    // The cover is already signed; photos 2–4 get their own signed URLs here.
    val extraPhotos by produceState<List<String?>?>(null, post.id) {
        value = if (post.extraImagePaths.isEmpty()) emptyList() else loadExtraPhotos()
    }
    val photoCount = 1 + post.extraImagePaths.size
    val pagerState = rememberPagerState(pageCount = { photoCount })

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
                // Swipe between the photos; each has its own fallback.
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { index ->
                    if (index == 0) {
                        var imageFailed by remember(food.id, food.imageUrl) { mutableStateOf(false) }
                        if (food.imageUrl != null && !imageFailed) {
                            AsyncImage(
                                model = food.imageUrl,
                                contentDescription = food.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onError = { imageFailed = true }
                            )
                        } else if (food.imageUrl == null) {
                            Image(
                                painter = painterResource(food.photo),
                                contentDescription = food.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color(0xFFF2F6E6)),
                                contentAlignment = Alignment.Center
                            ) { Text("Image unavailable", fontSize = 12.sp, color = Gray) }
                        }
                    } else {
                        val loaded = extraPhotos
                        val url = loaded?.getOrNull(index - 1)
                        var imageFailed by remember(food.id, url) { mutableStateOf(false) }
                        if (url != null && !imageFailed) {
                            AsyncImage(
                                model = url,
                                contentDescription = "${food.title}, photo ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onError = { imageFailed = true }
                            )
                        } else {
                            // Still signing (loaded == null), or the photo can't be shown.
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color(0xFFF2F6E6)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (loaded != null) Text("Image unavailable", fontSize = 12.sp, color = Gray)
                            }
                        }
                    }
                }

                if (photoCount > 1) {
                    Text(
                        "${pagerState.currentPage + 1} / $photoCount",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 30.dp, end = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }

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

                // Your own listing can't be bookmarked.
                if (!isOwnFood) FilledIconButton(
                    onClick = onToggleSave,
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
                                food.donorName,
                                fontSize = 11.sp,
                                maxLines = 2
                            )
                        }
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
                        value = expires,
                        caption = "Expires",
                        modifier = Modifier.weight(1f)
                    )

                    FoodSummaryItem(
                        value = food.status,
                        caption = "Status",
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
                            onClick = { messageDonor("Hi! What allergens does ${food.title} contain?") }
                        )

                        HorizontalDivider(color = Line)

                        FoodInfoRow(
                            icon = Icons.Outlined.Schedule,
                            title = "Pickup Window",
                            value = pickupWindow,
                            onClick = { foodAction = "pickup" }
                        )

                        HorizontalDivider(color = Line)

                        FoodInfoRow(
                            icon = Icons.Outlined.Handshake,
                            title = "Handover",
                            value = handover,
                            onClick = { messageDonor("Hi! How will the handover for ${food.title} work?") }
                        )

                        HorizontalDivider(color = Line)

                        FoodInfoRow(
                            icon = Icons.Outlined.LocationOn,
                            title = "Pickup Address",
                            value = food.pickupLocation,
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
                            value = storageNote,
                            onClick = { messageDonor("Hi! How should I store ${food.title} after pickup?") }
                        )

                        HorizontalDivider(color = Line)

                        FoodInfoRow(
                            icon = Icons.Outlined.Person,
                            title = if (donation) "Donor" else "Seller",
                            value = food.donorName,
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
                        title = if (isOwnFood) "Your listing" else if (donation) "Message Donor" else "Message Seller",
                        subtitle = if (isOwnFood) "Questions go to Messages" else "Ask a question",
                        background = Color.White,
                        foreground = Green,
                        modifier = Modifier.weight(1f),
                        onClick = { messageDonor("") }
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
                        "Pay ${formatPeso(reservationTotal(food.unitPrice, quantity))} " +
                                "at pickup. The app takes no payments."
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

private val listingTimeFormat = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", java.util.Locale.getDefault())
private val listingClockFormat = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.getDefault())

/** A listing time in the phone's time zone, e.g. "Sep 27, 6:00 PM". */
private fun listingTime(instant: Instant): String =
    listingTimeFormat.format(instant.atZone(java.time.ZoneId.systemDefault()))

/** "Sep 27, 3:00 PM – 5:00 PM", or with both dates when it spans days; null if not set. */
private fun listingPickupWindow(start: String?, end: String?): String? {
    val from = parseTimestamp(start) ?: return null
    val to = parseTimestamp(end) ?: return null
    val zone = java.time.ZoneId.systemDefault()
    val sameDay = from.atZone(zone).toLocalDate() == to.atZone(zone).toLocalDate()
    return listingTime(from) + " – " +
        if (sameDay) listingClockFormat.format(to.atZone(zone)) else listingTime(to)
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








