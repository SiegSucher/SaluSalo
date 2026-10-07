package com.example.salusalo

import android.os.Bundle
import android.os.SystemClock
import com.example.salusalo.model.Allergen
import com.example.salusalo.model.FoodPost
import com.example.salusalo.model.FoodPostStatus
import com.example.salusalo.model.ListingRecommender
import com.example.salusalo.model.allergenNames
import com.example.salusalo.model.allergiesIn
import com.example.salusalo.model.parseAllergens
import com.example.salusalo.model.nextReserveBlockerChange
import com.example.salusalo.model.reserveBlocker
import com.example.salusalo.model.reservableNow
import com.example.salusalo.model.ListingHandover
import com.example.salusalo.model.ReportTarget
import com.example.salusalo.model.donorRatingLabel
import com.example.salusalo.model.hasPhoto
import com.example.salusalo.model.NEARBY_RADIUS_METERS
import com.example.salusalo.model.distanceLabel
import com.example.salusalo.model.distanceMeters
import com.example.salusalo.model.pickupPin
import com.example.salusalo.data.PhoneLocation
import com.example.salusalo.data.PhoneLocationResult
import com.example.salusalo.model.parseTimestamp
import com.example.salusalo.viewmodel.SavedListingViewModel
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.salusalo.model.formatPeso
import com.example.salusalo.model.reservationTotal
import com.example.salusalo.viewmodel.FoodViewModel
import com.example.salusalo.viewmodel.PostResult
import com.example.salusalo.viewmodel.ReservationViewModel
import com.example.salusalo.viewmodel.ReserveResult
import com.example.salusalo.viewmodel.MessageViewModel
import com.example.salusalo.viewmodel.NotificationViewModel
import com.example.salusalo.model.NotificationType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalTime
import com.example.salusalo.data.UserRepository
import com.example.salusalo.data.clearGoogleCredentialState
import com.example.salusalo.data.requestGoogleIdToken
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.GoogleAuthProvider
import com.example.salusalo.data.profileErrorMessage
import com.example.salusalo.data.ProfileException
import com.example.salusalo.data.supabaseErrorText
import androidx.activity.compose.LocalActivity
import kotlinx.serialization.json.Json
import com.example.salusalo.model.UserProfile
import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    // A tapped phone notification waiting for Home (§35); Home consumes it once.
    private var pushTap by mutableStateOf<PushTap?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only on a fresh start: Android restoring the app hands back the old intent.
        if (savedInstanceState == null) pushTap = pushTapFrom(intent)


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
            SaluTheme {
                SaluSaloApp(pushTap = pushTap, onPushTapHandled = { pushTap = null })
            }
        }
    }

    // A notification tapped while the app is open (single top: this activity is reused).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pushTapFrom(intent)?.let { pushTap = it }
    }
}

// The signed-in profile as JSON for rememberSaveable (UserProfile is @Serializable).
private val ProfileSaver = Saver<UserProfile?, String>(
    save = { profile -> profile?.let { Json.encodeToString(UserProfile.serializer(), it) } },
    restore = { saved -> runCatching { Json.decodeFromString(UserProfile.serializer(), saved) }.getOrNull() }
)

// This phone's own settings; sign-out doesn't clear them. An account stays
// signed in until Log out (§35; "Keep me signed in" is gone).
private fun sessionPreferences(context: Context) =
    context.getSharedPreferences("salusalo_session", 0)

// The welcome pages show only until this phone has seen them: Get Started,
// "Sign in" on them, or any account reaching Home. A "Keep me signed in" choice
// saved by a build before §35 means someone signed in, so they count as seen too.
private fun welcomeSeen(context: Context): Boolean =
    sessionPreferences(context).run { getBoolean("welcome_seen", false) || contains("keep_signed_in") }

private fun markWelcomeSeen(context: Context) {
    sessionPreferences(context).edit().putBoolean("welcome_seen", true).apply()
}

@Composable
private fun SaluSaloApp(pushTap: PushTap?, onPushTapHandled: () -> Unit) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(if (welcomeSeen(context)) "signin" else "welcome") }
    var message by rememberSaveable { mutableStateOf("") }
    var isAuthenticating by remember { mutableStateOf(false) }
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    val profileRepository = remember { UserRepository() }
    val appScope = rememberCoroutineScope()
    // Saved with the screen, so rotation, dark mode, font size and Android
    // recreating the app keep the user on Home instead of repeating the setup
    // (which, offline, would end on Sign In).
    var currentProfile by rememberSaveable(stateSaver = ProfileSaver) { mutableStateOf<UserProfile?>(null) }
    // The name typed on Create Account, kept so "Try again" creates the profile with it.
    var signUpName by rememberSaveable { mutableStateOf<String?>(null) }
    // True while the message is an account setup failure that "Try again" can repeat.
    var canRetrySetup by rememberSaveable { mutableStateOf(false) }
    // True while the message offers to restore an earlier profile with this email.
    var canRelink by rememberSaveable { mutableStateOf(false) }

    fun logOut() {
        // Home is left before Firebase signs out, so the listener below doesn't
        // take this for a session that ended by itself.
        currentProfile = null
        signUpName = null
        screen = "signin"
        firebaseAuth.signOut()
        // Also forget the Google account chosen in Credential Manager (best effort).
        appScope.launch { clearGoogleCredentialState(context) }
    }

    // Firebase's signed-in user as Compose state. A sign-in that finishes after a
    // rotation reports to the old, discarded screen; this lets the new one notice
    // it and set the account up (the LaunchedEffect below).
    var authUid by remember { mutableStateOf(firebaseAuth.currentUser?.uid) }
    // The Firebase session ended while the app was open: the account was
    // disabled or deleted, or its sign-in revoked (SupabaseClientProvider signs
    // out then). Back to Sign In, saying why. A network failure never ends it.
    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            // Signed out (Log out, or the session ended): this phone's push token
            // is deleted, so the old account's alerts stop popping up here; the
            // next sign-in registers a new one (§35).
            if (auth.currentUser == null && authUid != null) {
                // Deprecated in Firebase 25.1 in favour of an opt-in registration API
                // (off unless enabled in the manifest); still the way to drop the token.
                @Suppress("DEPRECATION")
                FirebaseMessaging.getInstance().deleteToken()
            }
            authUid = auth.currentUser?.uid
            if (auth.currentUser == null && screen == "home") {
                currentProfile = null
                signUpName = null
                screen = "signin"
                message = "Your session has ended. Please sign in again."
            }
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose { firebaseAuth.removeAuthStateListener(listener) }
    }
    // True while an account setup runs (after a sign-in, or Try again), and from a
    // start with a saved session until its setup has finished:
    // the branded loading screen then shows instead of a form.
    var setupPending by remember { mutableStateOf(firebaseAuth.currentUser != null) }
    // Opening the app shows the loading screen for at least STARTUP_INTRO_MS, also
    // when no one is signed in. Saved, so rotation and Android restoring the app
    // don't show it again.
    var introDone by rememberSaveable { mutableStateOf(false) }
    // When the app opened, so a rotation during the intro waits only for the rest of it.
    val introStartedAt = rememberSaveable { SystemClock.elapsedRealtime() }
    LaunchedEffect(Unit) {
        if (!introDone) {
            delay((STARTUP_INTRO_MS - (SystemClock.elapsedRealtime() - introStartedAt)).coerceAtLeast(0L))
            introDone = true
        }
    }
    var setupJob by remember { mutableStateOf<Job?>(null) }
    fun openAuthenticatedHome(displayNameOverride: String? = null) {
        isAuthenticating = true
        setupPending = true
        // A sign-in's own callback and the LaunchedEffect below can both ask;
        // one setup at a time.
        if (setupJob?.isActive == true) return
        setupJob = appScope.launch {
            runCatching {
                // Before the first Supabase call: a new account's token gets the
                // role claim Supabase requires (the set-firebase-role Edge Function).
                profileRepository.ensureSupabaseRole()
                profileRepository.loadOrCreateCurrentProfile(displayNameOverride)
            }.onSuccess { profile ->
                // An overlapping attempt may have failed first: its dialog is out of date.
                if (canRetrySetup) {
                    message = ""
                    canRetrySetup = false
                    canRelink = false
                }
                signUpName = null
                currentProfile = profile
                markWelcomeSeen(context)
                screen = "home"
                // Copy Firebase's email_verified into the profile in the background.
                // It never blocks or signs anyone out; on any failure (offline, a
                // reload or RPC error) the stored value stays and nothing is shown.
                appScope.launch {
                    runCatching { profileRepository.syncEmailVerified(forceRefresh = false) }
                        .onSuccess { verified ->
                            currentProfile = currentProfile
                                ?.takeIf { it.id == profile.id }
                                ?.copy(emailVerified = verified)
                                ?: currentProfile
                        }
                }
            }.onFailure { error ->
                // Nothing to report when an overlapping attempt already reached Home.
                if (currentProfile == null) {
                    // The user stays signed in to Firebase, so Sign In offers "Try again",
                    // or "Restore profile" when an earlier profile has this email.
                    screen = "signin"
                    canRetrySetup = true
                    canRelink = error == ProfileException.EmailInUse
                    message = profileErrorMessage(error)
                }
            }
            isAuthenticating = false
            setupPending = false
        }
    }

    // The user asked to restore the earlier profile with this account's email.
    // The server moves it only when Firebase says the email is verified.
    fun restoreEarlierProfile() {
        isAuthenticating = true
        appScope.launch {
            try {
                if (profileRepository.relinkProfile() != null) {
                    isAuthenticating = false
                    // Finds the restored profile and opens Home as after any sign-in.
                    openAuthenticatedHome()
                    return@launch
                }
                val sent = runCatching { profileRepository.sendVerificationEmail() }.isSuccess
                message = if (sent) {
                    "First confirm that this email address is yours: we sent a link to " +
                        "${firebaseAuth.currentUser?.email}. Open it, then come back and tap Restore profile."
                } else {
                    "First confirm that this email address is yours. The confirmation email could not be " +
                        "sent; check your internet connection and try again."
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                message = supabaseErrorText(
                    error,
                    "Your profile could not be restored. Check your internet connection and try again."
                )
            }
            canRetrySetup = true
            canRelink = true
            isAuthenticating = false
        }
    }

    fun signInWithGoogle() {
        isAuthenticating = true
        // Cleared first, so a setup started after a rotation uses no typed name.
        signUpName = null
        appScope.launch {
            try {
                val idToken = requestGoogleIdToken(context)
                firebaseAuth
                    .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                    .await()
                isAuthenticating = false
                // A new Google user's profile is created with the Google name and email.
                openAuthenticatedHome()
            } catch (error: GetCredentialCancellationException) {
                // The user closed the account picker: nothing to report. Offline,
                // Google also ends it this way after an account is chosen.
                isAuthenticating = false
                if (!hasInternet(context)) message = NO_CONNECTION_TEXT
            } catch (error: NoCredentialException) {
                isAuthenticating = false
                message = "No Google account was found on this phone. Add one in " +
                        "your phone's Settings, then try again."
            } catch (error: FirebaseAuthException) {
                isAuthenticating = false
                message = authErrorMessage(error)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                isAuthenticating = false
                logGoogleSignInError(error)
                message = if (error is FirebaseNetworkException) {
                    authErrorMessage(error)
                } else {
                    "Google sign-in failed. Please try again."
                }
            }
        }
    }

    // A saved session at start, or a sign-in that finished after a rotation.
    LaunchedEffect(authUid) {
        if (firebaseAuth.currentUser != null && currentProfile == null) {
            openAuthenticatedHome(signUpName)
        }
    }

    // Back on Create Account and Forgot password goes to Sign In. On Home, Sign In
    // and the welcome pages it closes the app; Home never signs out (Profile's Log out does).
    BackHandler(enabled = screen == "signup" || screen == "forgot") {
        screen = "signin"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SaluColors.Cream
    ) {
        // While a signed-in account is set up (a saved session at start, or right
        // after a sign-in), the loading screen shows instead of a form. The flag
        // comes first because Firebase's user is not Compose state: only the flag
        // can re-run this check once a sign-in has finished. (A new UID restarts the
        // LaunchedEffect above; openAuthenticatedHome runs one setup at a time.)
        // When the app opens, it shows first in any case (introDone).
        val showStartup = !introDone || (setupPending && screen != "home" && currentProfile == null &&
                !canRetrySetup && firebaseAuth.currentUser != null)
        val shownScreen = if (showStartup) "loading" else screen

        // Under Home's white bottom bar, the system navigation area is white too.
        Box(
            Modifier
                .fillMaxSize()
                .wrapContentHeight(Alignment.Bottom)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .background(if (shownScreen == "home") Color.White else SaluColors.Cream)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
        ) {
            AnimatedContent(
                targetState = shownScreen,
                modifier = Modifier
                    .fillMaxSize()
                    .background(SaluColors.Cream),
                transitionSpec = {
                    when {
                        initialState == "welcome" && targetState == "signup" ->
                            // Get Started: the welcome page moves away and the
                            // sign-up page slides in from the right.
                            (slideInHorizontally(
                                animationSpec = tween(SaluMotion.LONG + 60, easing = SaluMotion.Emphasized)
                            ) { fullWidth -> fullWidth }) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = tween(SaluMotion.LONG + 60, easing = SaluMotion.Emphasized)
                                    ) { fullWidth -> -fullWidth / 3 } + fadeOut(tween(SaluMotion.MEDIUM)))

                        // Into and out of the loading screen and Home: a calm fade.
                        targetState == "home" || targetState == "loading" ||
                                initialState == "home" || initialState == "loading" ->
                            (fadeIn(tween(SaluMotion.LONG)) +
                                    scaleIn(initialScale = 0.98f, animationSpec = tween(SaluMotion.LONG))) togetherWith
                                    fadeOut(tween(SaluMotion.SHORT))

                        // Sign In, Create Account and Reset password switch in place.
                        else -> EnterTransition.None togetherWith ExitTransition.None
                    }
                },
                label = "screenTransition"
            ) { currentScreen ->
                when (currentScreen) {
                    "loading" -> StartupScreen()

                    "welcome" -> WelcomeScreen(
                        onStart = {
                            markWelcomeSeen(context)
                            screen = "signup"
                        },
                        onSignIn = {
                            markWelcomeSeen(context)
                            screen = "signin"
                        }
                    )

                    // Only with a profile: while Home fades out after Log out (or a
                    // session that ended), it must not be drawn again for no one,
                    // which would start a second set of screens and send requests
                    // without a signed-in account.
                    "home" -> currentProfile?.let { profile ->
                        HomeScreen(
                            onLogout = { logOut() },
                            currentProfile = profile,
                            onProfileUpdated = { currentProfile = it },
                            pushTap = pushTap,
                            onPushTapHandled = onPushTapHandled
                        )
                    }

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

                                    // Saved before Firebase answers: if the screen is rebuilt
                                    // (rotation) meanwhile, the new one sets the account up
                                    // with it (the LaunchedEffect on authUid).
                                    if (currentScreen != "forgot") {
                                        signUpName = if (currentScreen == "signup") name.trim() else null
                                    }

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
                                                    openAuthenticatedHome(name.trim())
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
                        onGoogle = { signInWithGoogle() }
                    )
                }
            }
        }
    }

    if (message.isNotEmpty()) {
        // After a failed account setup the user is still signed in to Firebase,
        // so "Try again" repeats the setup without signing in again.
        val retry = canRetrySetup && firebaseAuth.currentUser != null
        val relink = retry && canRelink
        val close = {
            message = ""
            canRetrySetup = false
            canRelink = false
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(if (relink) "Restore your profile?" else "Salu-Salo") },
            text = { Text(message) },
            confirmButton = {
                if (relink) {
                    TextButton(onClick = {
                        close()
                        restoreEarlierProfile()
                    }) {
                        Text("Restore profile")
                    }
                } else if (retry) {
                    TextButton(onClick = {
                        close()
                        openAuthenticatedHome(signUpName)
                    }) {
                        Text("Try again")
                    }
                } else {
                    TextButton(onClick = close) {
                        Text("OK")
                    }
                }
            },
            dismissButton = if (retry) {
                {
                    TextButton(onClick = close) {
                        Text("Close")
                    }
                }
            } else {
                null
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
    is FirebaseNetworkException -> NO_CONNECTION_TEXT
    is FirebaseTooManyRequestsException ->
        "Too many attempts. Please wait a moment and try again."
    else -> "Authentication failed. Please try again."
}

private const val NO_CONNECTION_TEXT =
    "Could not reach the server. Check your internet connection and try again."

// False in airplane mode or on a network without working internet.
private fun hasInternet(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
    return connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}

// The exception's own text goes to logcat only (setup problems such as a
// missing SHA-1 show there), never on screen: it can be technical.
private fun logGoogleSignInError(error: Exception) {
    android.util.Log.w("SaluSalo", "Google sign-in failed: ${error.javaClass.simpleName}: ${error.message}")
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
        imageDescription = "People sharing a box of vegetables and bread",
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

    // WELCOME SCREEN 2
    WelcomePage(
        image = R.drawable.welcome_reference,
        imageDescription = "People sharing a box of vegetables and bread",
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

    // WELCOME SCREEN 3
    WelcomePage(
        image = R.drawable.welcome_reference,
        imageDescription = "People sharing a box of vegetables and bread",
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

/** How long the loading screen shows at least when the app opens. */
private const val STARTUP_INTRO_MS = 1200L

/**
 * The loading screen when the app opens and while a signed-in account is set up:
 * the Salu-Salo logo and name on the app's cream, with a thin green progress bar.
 * The logo settles in once, then breathes gently while the app loads.
 */
@Composable
private fun StartupScreen() {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(SaluMotion.LONG + 180, easing = SaluMotion.Emphasized))
    }
    val transition = rememberInfiniteTransition(label = "startup")
    val breathe by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "startupBreathe"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaluColors.Cream)
            .padding(horizontal = 32.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SaluLogo(
            size = 104.dp,
            modifier = Modifier.graphicsLayer {
                val grow = (0.92f + 0.08f * entrance.value) * breathe
                scaleX = grow
                scaleY = grow
                alpha = entrance.value
            }
        )
        Spacer(Modifier.height(20.dp))
        BrandWordmark(style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Good food. Less waste. Stronger community.",
            style = SaluText.Supporting,
            color = SaluColors.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .width(140.dp)
                .clip(SaluRadius.Pill),
            color = SaluColors.Green,
            trackColor = SaluColors.IconGreen
        )
        Spacer(Modifier.height(12.dp))
        Text("Getting things ready…", style = SaluText.Meta, color = SaluColors.Gray)
    }
}

@Composable
private fun WelcomeScreen(
    onStart: () -> Unit,
    onSignIn: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { welcomePages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == welcomePages.lastIndex

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(SaluColors.Cream)
    ) {
        val imageHeight = maxHeight * 0.34f

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SaluLogo(36.dp)
                Spacer(Modifier.width(10.dp))
                BrandWordmark(style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }

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
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(Modifier.height(16.dp))

                    // The illustration sits on its own light card, so its edges look intended.
                    Image(
                        painter = painterResource(page.image),
                        contentDescription = page.imageDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(imageHeight)
                            .clip(RoundedCornerShape(28.dp))
                            .background(IllustrationBackground)
                    )

                    Spacer(Modifier.height(24.dp))

                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = SaluColors.DarkGreen,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = page.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SaluColors.Gray
                    )

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FeatureCard(
                            title = page.leftTitle,
                            description = page.leftDescription,
                            icon = page.leftIcon,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        FeatureCard(
                            title = page.rightTitle,
                            description = page.rightDescription,
                            icon = page.rightIcon,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }

            // Fixed bottom area: page dots + button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                PageDots(
                    count = welcomePages.size,
                    current = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp)
                )

                SaluPrimaryButton(
                    text = if (lastPage) "Get started" else "Next",
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

                TextButton(
                    onClick = onSignIn,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        "Already have an account? Sign in",
                        style = MaterialTheme.typography.labelLarge,
                        color = SaluColors.Green
                    )
                }
            }
        }
    }
}

/** The welcome and sign-in illustrations' own background colour. */
private val IllustrationBackground = Color(0xFFFCFBF4)

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier
) {
    // Pale green with a green icon circle, as the original welcome cards.
    SaluCard(modifier = modifier.heightIn(min = 120.dp), color = SaluColors.PaleGreen, border = false) {
        Column(Modifier.padding(14.dp)) {
            IconBubble(icon, size = 36.dp, iconSize = 20.dp)

            Spacer(Modifier.height(10.dp))

            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp),
                color = SaluColors.DarkGreen
            )

            Spacer(Modifier.height(4.dp))

            Text(
                description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = SaluColors.Gray
            )
        }
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
    onGoogle: () -> Unit
) {
    val creatingAccount = mode == "signup"
    val resettingPassword = mode == "forgot"

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }

    // Passwords are retained only while this screen remains open.
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaluColors.Cream)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SaluLogo(36.dp)
            Spacer(Modifier.width(10.dp))
            BrandWordmark(style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }

        Spacer(Modifier.height(16.dp))

        // Fit shows the entire header image rather than cutting it off.
        Image(
            painter = painterResource(R.drawable.signin_reference),
            contentDescription =
                "Good food. Less waste. Stronger community.",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(IllustrationBackground)
                .aspectRatio(2.05f)
        )

        Spacer(Modifier.height(20.dp))

        SaluCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (resettingPassword) {
                    Text(
                        "Reset password",
                        style = SaluText.ScreenTitle,
                        color = SaluColors.DarkGreen,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "Enter the email address linked to your account.",
                        style = SaluText.Body,
                        color = SaluColors.Gray
                    )
                } else {
                    AuthModeSwitch(
                        creatingAccount = creatingAccount,
                        onSignIn = { onNavigate("signin") },
                        onCreateAccount = { onNavigate("signup") }
                    )
                }

                Spacer(Modifier.height(20.dp))

                if (creatingAccount) {
                    InputField(
                        value = name,
                        onChange = { name = it },
                        hint = "Full name",
                        icon = Icons.Outlined.Person
                    )

                    Spacer(Modifier.height(12.dp))
                }

                InputField(
                    value = email,
                    onChange = { email = it },
                    hint = "Email address",
                    icon = Icons.Outlined.Email,
                    emailField = true,
                    last = resettingPassword
                )

                if (!resettingPassword) {
                    Spacer(Modifier.height(12.dp))

                    InputField(
                        value = password,
                        onChange = { password = it },
                        hint = "Password",
                        icon = Icons.Outlined.Lock,
                        passwordField = true,
                        last = !creatingAccount
                    )

                    if (creatingAccount) {
                        Spacer(Modifier.height(12.dp))

                        InputField(
                            value = confirmPassword,
                            onChange = { confirmPassword = it },
                            hint = "Confirm password",
                            icon = Icons.Outlined.Lock,
                            passwordField = true,
                            last = true
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { onNavigate("forgot") },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    "Forgot password?",
                                    style = SaluText.Supporting.copy(fontWeight = FontWeight.SemiBold),
                                    color = SaluColors.Green
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(if (creatingAccount || resettingPassword) 20.dp else 12.dp))

                SaluPrimaryButton(
                    text = when {
                        resettingPassword -> "Send reset link"
                        creatingAccount -> "Create account"
                        else -> "Continue"
                    },
                    loading = isAuthenticating,
                    onClick = {
                        onContinue(email, password, confirmPassword, name)
                    }
                )

                if (resettingPassword) {
                    TextButton(
                        onClick = { onNavigate("signin") },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Back to sign in", style = MaterialTheme.typography.labelLarge, color = SaluColors.Green)
                    }
                } else {
                    Spacer(Modifier.height(20.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = SaluColors.Line
                        )

                        Text(
                            "or continue with",
                            modifier = Modifier.padding(horizontal = 12.dp),
                            style = SaluText.Meta,
                            color = SaluColors.Gray
                        )

                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = SaluColors.Line
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    GoogleButton(enabled = !isAuthenticating) { onGoogle() }

                    Spacer(Modifier.height(16.dp))

                    CommunityCard()
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = SaluColors.Green,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    "Your safety matters",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                    color = SaluColors.DarkGreen
                )

                Text(
                    "Agree on pickup details in the chat and check the food before you take it.",
                    style = SaluText.Supporting,
                    color = SaluColors.Gray
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Sign In | Create Account, as a two-part switch. */
@Composable
private fun AuthModeSwitch(
    creatingAccount: Boolean,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaluRadius.Control)
            .background(SaluColors.PaleGreen)
            .padding(4.dp)
            .selectableGroup()
    ) {
        AuthModeSegment("Sign in", selected = !creatingAccount, onClick = onSignIn, modifier = Modifier.weight(1f))
        AuthModeSegment("Create account", selected = creatingAccount, onClick = onCreateAccount, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AuthModeSegment(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) SaluColors.Green else SaluColors.Gray,
            textAlign = TextAlign.Center
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
    emailField: Boolean = false,
    last: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = SaluRadius.Control,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(hint) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp)
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
                        tint = SaluColors.Gray
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
            },
            imeAction = if (last) ImeAction.Done else ImeAction.Next
        ),
        colors = saluFieldColors()
    )
}

@Composable
private fun CommunityCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaluRadius.Control)
            .background(SaluColors.PaleGreen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBubble(
            Icons.Outlined.Groups,
            size = 40.dp,
            iconSize = 22.dp
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                "One account for all your good deeds",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp),
                color = SaluColors.DarkGreen
            )

            Spacer(Modifier.height(2.dp))

            Text(
                "Donate, sell, buy, and claim food — all from one simple account.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = SaluColors.Gray
            )
        }
    }
}

/* SOCIAL BUTTONS — NO CROPPED IMAGE FRAGMENTS */

@Composable
private fun GoogleButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .pressScale(interaction),
        shape = SaluRadius.Control,
        border = BorderStroke(1.dp, SaluColors.Gray.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = SaluColors.DarkGreen
        ),
        interactionSource = interaction
    ) {
        GoogleMark()
        Spacer(Modifier.width(10.dp))
        Text(
            "Continue with Google",
            style = MaterialTheme.typography.labelLarge
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
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreen(
    onLogout: () -> Unit,
    currentProfile: UserProfile?,
    onProfileUpdated: (UserProfile) -> Unit,
    // A tapped phone notification to open (§35), then onPushTapHandled.
    pushTap: PushTap?,
    onPushTapHandled: () -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("All") }
    var page by rememberSaveable { mutableStateOf("home") }
    var filterHelp by remember { mutableStateOf(false) }
    var howItWorks by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("Newest") }
    val sessionKey = currentProfile?.id ?: "signed-out"
    val foodViewModel: FoodViewModel = viewModel(key = "food-$sessionKey")
    val reservationViewModel: ReservationViewModel = viewModel(key = "reservation-$sessionKey")
    val messageViewModel: MessageViewModel = viewModel(key = "messages-$sessionKey")
    val notificationViewModel: NotificationViewModel = viewModel(key = "notifications-$sessionKey")
    val savedListingViewModel: SavedListingViewModel = viewModel(key = "saved-$sessionKey")
    val savedIds by savedListingViewModel.savedIds.collectAsStateWithLifecycle()
    // The saved rows carry each saved listing's details, for the recommendations.
    val savedRows by savedListingViewModel.saved.collectAsStateWithLifecycle()
    val savedMessage by savedListingViewModel.message.collectAsStateWithLifecycle()
    val activeChat by messageViewModel.active.collectAsStateWithLifecycle()
    val chatOpening by messageViewModel.opening.collectAsStateWithLifecycle()
    val messageError by messageViewModel.message.collectAsStateWithLifecycle()
    val alerts by notificationViewModel.notifications.collectAsStateWithLifecycle()
    val unreadAlerts by notificationViewModel.unreadCount.collectAsStateWithLifecycle()
    // The chat badge counts unread messages across all threads, as the Messages page shows them.
    val conversations by messageViewModel.conversations.collectAsStateWithLifecycle()
    val unreadMessages = conversations.sumOf { it.unreadCount }
    // Tab Activity opens on when an alert sends the user there.
    var activityTab by rememberSaveable { mutableStateOf("My Listings") }
    // Reservation a "Reservation claimed" or "Listing removed" alert asked Activity to open.
    var activityFocus by rememberSaveable { mutableStateOf<String?>(null) }
    val remotePosts by foodViewModel.availablePosts.collectAsStateWithLifecycle()
    val feedLoading by foodViewModel.loading.collectAsStateWithLifecycle()
    val feedLoaded by foodViewModel.feedLoaded.collectAsStateWithLifecycle()
    val feedError by foodViewModel.feedError.collectAsStateWithLifecycle()
    val postResult by foodViewModel.postResult.collectAsStateWithLifecycle()
    // The listing whose details page is open (kept through rotation).
    val openedListing by foodViewModel.openedListing.collectAsStateWithLifecycle()
    val reserveResult by reservationViewModel.reserveResult.collectAsStateWithLifecycle()
    // This account's own reservations, each with its listing, for the recommendations.
    val myReservations by reservationViewModel.myReservations.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The phone's position, only for distances, Nearby and Nearest. It stays in
    // memory on this screen: never saved, sent to Supabase or logged, and gone
    // when Home closes. Refreshed when Home opens and when the app comes back
    // (only if location access is already allowed), and whenever Nearby or
    // Nearest is chosen (asking for access if needed).
    var phonePosition by remember { mutableStateOf<PhoneLocationResult.Found?>(null) }
    var locationProblem by remember { mutableStateOf<PhoneLocationResult?>(null) }
    val locationFinder = rememberLocationFinder()
    fun refreshPhonePosition(askIfNeeded: Boolean) {
        locationFinder.find(PhoneLocation.DISTANCE_MAX_AGE_MILLIS, askIfNeeded = askIfNeeded) { result ->
            when (result) {
                is PhoneLocationResult.Found -> {
                    phonePosition = result
                    locationProblem = null
                }
                PhoneLocationResult.NoPermission, PhoneLocationResult.LocationOff -> {
                    phonePosition = null
                    locationProblem = result
                }
                // Keep a position found earlier; say so only when there is none.
                PhoneLocationResult.NotFound -> if (phonePosition == null) locationProblem = result
            }
        }
    }
    val phonePin = phonePosition?.position
    // Straight-line distance to a listing's pin; null without a pin or a phone position.
    fun distanceTo(post: FoodPost): Double? {
        val here = phonePin ?: return null
        val pin = post.pickupPin ?: return null
        return distanceMeters(here, pin)
    }

    // The feed loads when Home starts and again when Home or All listings is
    // opened or the app comes back, at most every 30 seconds (refreshFeedIfStale).
    LaunchedEffect(currentProfile?.id) { foodViewModel.refreshFeedIfStale() }
    LaunchedEffect(currentProfile?.id) {
        if (PhoneLocation.hasPermission(context)) refreshPhonePosition(askIfNeeded = false)
    }
    // Alerts load once per session and then arrive live.
    LaunchedEffect(currentProfile?.id) { currentProfile?.id?.let { notificationViewModel.start(it) } }
    // The inbox loads once per session and then follows new messages live.
    LaunchedEffect(currentProfile?.id) { currentProfile?.id?.let { messageViewModel.start(it) } }
    // Realtime misses messages and alerts while the app is in the background, so
    // the inbox and alerts reload once when the app comes back (not on the first
    // start), and the feed too unless it loaded in the last 30 seconds.
    var wasStopped by remember { mutableStateOf(false) }
    LifecycleStartEffect(messageViewModel) {
        if (wasStopped) {
            wasStopped = false
            messageViewModel.loadConversations(quiet = true)
            notificationViewModel.refresh(quiet = true)
            foodViewModel.refreshFeedIfStale()
            // The phone may have moved meanwhile; never asks for access here.
            if (PhoneLocation.hasPermission(context)) refreshPhonePosition(askIfNeeded = false)
        }
        onStopOrDispose { wasStopped = true }
    }
    // Bookmarks load once per session, so the icons show the right state at once.
    LaunchedEffect(currentProfile?.id) { currentProfile?.id?.let { savedListingViewModel.start(it) } }
    // The reservations load once per session too (quietly), so the recommendations
    // can learn from them before Activity has been opened.
    LaunchedEffect(currentProfile?.id) { currentProfile?.id?.let { reservationViewModel.start(it) } }
    // A new alert plays the phone's notification sound while the app is on
    // screen, unless it is a message in the chat that is open. The switch is
    // read when the alert arrives. While the phone shows Salu-Salo's
    // notifications, the pop-up plays it instead (§35), so it never sounds twice.
    val alertSound = remember { AlertSoundPlayer(context) }
    var appResumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(alertSound) {
        appResumed = true
        AlertPresence.resumed = true
        onPauseOrDispose {
            appResumed = false
            AlertPresence.resumed = false
            alertSound.stop()
        }
    }
    val openChatId by rememberUpdatedState(activeChat?.id)
    LaunchedEffect(notificationViewModel) {
        notificationViewModel.newAlerts.collect { alert ->
            val inOpenChat = alert.type == NotificationType.MESSAGE &&
                    alert.conversationId != null && alert.conversationId == openChatId
            if (appResumed && !inOpenChat && notificationSoundOn(context) && !phoneNotificationsOn(context)) {
                alertSound.play()
            }
        }
    }
    // The chat open on screen: its pushed messages don't pop up, and opening it
    // clears its notification (§35).
    DisposableEffect(activeChat?.id) {
        val id = activeChat?.id
        AlertPresence.openChatId = id
        if (id != null) cancelChatNotification(context, id)
        onDispose { AlertPresence.openChatId = null }
    }
    // Android 13+: asked once whether Salu-Salo may show notifications (§35).
    // Turned off later, Profile → Notifications offers the phone's settings.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !pushPermissionAsked(context)) {
            markPushPermissionAsked(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    // Opening a chat marks its message alerts read on the server; mirror that
    // for alerts that arrive while the chat is open.
    LaunchedEffect(activeChat?.id, alerts) {
        activeChat?.id?.let { id ->
            if (alerts.any { it.conversationId == id && it.readAt == null }) {
                notificationViewModel.markConversationReadLocally(id)
            }
        }
    }
    // The announcement the Alerts page should open (tapped there or on its notification).
    var announcementToOpen by rememberSaveable { mutableStateOf<String?>(null) }
    // Where an alert leads, for a tap on the Alerts page and on its phone notification alike.
    fun openAlert(type: NotificationType, alertId: String, conversationId: String?, reservationId: String?) {
        when (type) {
            NotificationType.MESSAGE -> conversationId?.let { messageViewModel.openConversation(it) }
            // History; Activity then opens the reservation (with Rate for a claimed one).
            NotificationType.RESERVATION_CLAIMED, NotificationType.RESERVATION_REMOVED -> {
                activityTab = "History"
                activityFocus = reservationId
                page = "activity"
            }
            NotificationType.ANNOUNCEMENT -> {
                announcementToOpen = alertId
                page = "alerts"
            }
            NotificationType.UNKNOWN -> Unit
            // A new or cancelled reservation on one of this account's listings:
            // My Listings; Activity then opens that listing's Manage reservations.
            else -> {
                activityTab = "My Listings"
                activityFocus = reservationId
                page = "activity"
            }
        }
    }
    // A tapped phone notification (§35), handled once Home is open.
    LaunchedEffect(pushTap) {
        val tap = pushTap ?: return@LaunchedEffect
        onPushTapHandled()
        // For another account (this phone signed in to someone else since): the app just opens.
        if (tap.uid != FirebaseAuth.getInstance().currentUser?.uid) return@LaunchedEffect
        notificationViewModel.markRead(listOf(tap.alertId))
        openAlert(tap.type, tap.alertId, tap.conversationId, tap.reservationId)
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
    // The header's profile circle. Keyed on the stored path, as on Profile:
    // each upload has a new path, so a changed photo shows here at once.
    val userRepository = remember { UserRepository() }
    val profilePhotoPath = currentProfile?.profileImageUrl
    val profilePhotoUrl by produceState<String?>(null, profilePhotoPath) {
        value = userRepository.profilePhotoUrl(profilePhotoPath)
    }
    val greeting = if (firstName.isBlank()) "$timeGreeting 👋" else "$timeGreeting, $firstName 👋"
    // Kept through rotation and other configuration changes (the ViewModels
    // survive them), cleared when the account leaves Home.
    val activity = LocalActivity.current
    DisposableEffect(currentProfile?.id) {
        onDispose {
            if (activity?.isChangingConfigurations == true) return@onDispose
            foodViewModel.clearSession()
            reservationViewModel.clearSession()
            messageViewModel.clearSession()
            notificationViewModel.clearSession()
            savedListingViewModel.clearSession()
        }
    }

    // The feed arrives newest first (created_at). The lists below are rebuilt only
    // when what they are made from changes, not on every recomposition (each
    // alert, message or badge change); every value they read is a key.
    val foods = remember(remotePosts, phonePin) { remotePosts.map { it.toHomeFood(distanceTo(it)) } }
    val discoveryPreferences = rememberDiscoveryPreferences(sessionKey)
    // The row at the top of Home never shows a listing with one of the user's
    // allergies (My allergies), not even after Show.
    val preferredFoods = remember(remotePosts, phonePin, discoveryPreferences) {
        remotePosts.filter { discoveryPreferences.matches(it) && discoveryPreferences.myAllergiesIn(it).isEmpty() }
            .map { it.toHomeFood(distanceTo(it)) }
    }
    // "Recommended for you": a small model trained on this phone from the account's
    // own reservations and saves (ListingRecommender) picks among the listings that
    // match the saved preferences and have none of the user's allergies. Empty until
    // it has something to learn from, or when nothing matches what it learned; the
    // row then stays as above.
    val ownProfileId = currentProfile?.id
    val recommendations = remember(remotePosts, myReservations, savedIds, savedRows, discoveryPreferences, ownProfileId) {
        ListingRecommender.train(
            feed = remotePosts,
            reserved = myReservations.map { it.foodPost },
            savedIds = savedIds,
            saved = savedRows.mapNotNull { it.foodPost },
            ownProfileId = ownProfileId
        )?.recommend(
            remotePosts.filter { discoveryPreferences.matches(it) && discoveryPreferences.myAllergiesIn(it).isEmpty() },
            limit = TOP_ROW_CARDS
        ).orEmpty()
    }
    val recommendedFoods = remember(recommendations, phonePin) {
        recommendations.map { it.post.toHomeFood(distanceTo(it.post)) }
    }

    val matchingFoods = remember(foods, search, category, sort, phonePin) { foods.filter { food ->
        val matchesSearch =
            food.title.contains(search, ignoreCase = true) ||
                    food.donorName.contains(search, ignoreCase = true)

        val matchesCategory = when (category) {
            // Listings with a pin within 5 km; none until the phone's position is known.
            "Nearby" -> food.distanceMeters?.let { it <= NEARBY_RADIUS_METERS } == true
            "Donation" -> food.free
            "For Sale" -> !food.free
            "Expiring Soon" -> food.urgent
            else -> true
        }

        matchesSearch && matchesCategory
    }.let { results ->
        val newestFirst = compareByDescending<HomeFood> { parseTimestamp(it.createdAt) ?: Instant.EPOCH }
        when {
            sort == "Name" -> results.sortedBy { it.title }
            // Nearest first; listings without a pin last, newest first. Until the
            // phone's position is known, the order stays Newest.
            sort == "Nearest" && phonePin != null -> results.sortedWith(
                compareBy<HomeFood> { it.distanceMeters == null }
                    .thenBy { it.distanceMeters ?: 0.0 }
                    .then(newestFirst)
            )
            else -> results.sortedWith(newestFirst)
        }
    } }
    // Listings whose donor picked one of the user's allergies (My allergies), by ID:
    // left out of the feed until Show is tapped, then marked. Show lasts until Hide
    // or until the app is closed.
    var showAllergyFoods by rememberSaveable { mutableStateOf(false) }
    val allergyMatches = remember(matchingFoods, discoveryPreferences, ownProfileId) {
        matchingFoods.filter { it.donorId != ownProfileId }
            .associate { it.id to discoveryPreferences.myAllergiesIn(it.post) }
            .filterValues { it.isNotEmpty() }
    }
    val visibleFoods = remember(matchingFoods, allergyMatches, showAllergyFoods) {
        if (showAllergyFoods) matchingFoods else matchingFoods.filter { it.id !in allergyMatches }
    }
    // One line under the chips while Nearby or Nearest is chosen.
    val locationLine = if (category != "Nearby" && sort != "Nearest") null else when {
        locationFinder.finding && phonePin == null -> "Finding your location…"
        locationProblem == PhoneLocationResult.NoPermission ->
            "Allow location to use Nearby and Nearest: Settings → Apps → Salu-Salo → Permissions → Location."
        locationProblem == PhoneLocationResult.LocationOff ->
            "Your phone's location is turned off. Turn it on to use Nearby and Nearest."
        locationProblem == PhoneLocationResult.NotFound ->
            "Your location could not be found. Tap Nearby or Nearest again to try again."
        phonePosition?.approximate == true -> "Distances use your phone's approximate location."
        else -> null
    }

    // Home and All listings keep their scroll positions while another page is
    // open. Post keeps its draft while the user looks at other tabs; a new key
    // gives it a fresh form after "Post another listing".
    val homeListState = rememberLazyListState()
    val allListState = rememberLazyListState()
    val pageStates = rememberSaveableStateHolder()
    var postFormKey by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    // The feed shows skeleton cards, not "no food", until its first load has
    // finished; a failed first load shows the error instead.
    val showFeedSkeleton = !feedLoaded && feedError == null
    LaunchedEffect(page) {
        if (page == "home" || page == "all") foodViewModel.refreshFeedIfStale()
    }
    // While Home or All listings is on screen and the app is in front, the feed
    // also reloads every minute, so other phones' new listings and changes appear
    // without a pull (nothing on the server pushes listing changes).
    LaunchedEffect(page, appResumed) {
        if ((page == "home" || page == "all") && appResumed) {
            while (true) {
                delay(60_000)
                foodViewModel.refreshFeedIfStale()
            }
        }
    }
    // A refused reservation (a new price, less left, pickup over) reloads the
    // feed, so the open details and review page show the listing as it is now.
    LaunchedEffect(reserveResult) {
        if (reserveResult is ReserveResult.Failed) foodViewModel.loadAvailablePosts()
    }
    // A cancel or release gave quantity back to a listing: reload the feed, even
    // if the user left Activity before the result arrived.
    LaunchedEffect(reservationViewModel) {
        reservationViewModel.quantityReturned.collect { foodViewModel.loadAvailablePosts() }
    }
    // The pull-to-refresh spinner shows only for a pull, not for automatic reloads.
    var feedPulled by remember { mutableStateOf(false) }
    LaunchedEffect(feedLoading) { if (!feedLoading) feedPulled = false }

    fun resetFilters() {
        search = ""
        category = "All"
    }

    // Leaving Post by Back discards its draft, as before; switching tabs keeps it.
    // Home's filters are cleared, so a listing just posted is not hidden by them.
    fun leavePost() {
        pageStates.removeState("post-$postFormKey")
        foodViewModel.loadAvailablePosts()
        resetFilters()
        page = "home"
    }

    // Activity opens on My Listings unless an alert chose a tab.
    fun openActivity() {
        activityTab = "My Listings"
        activityFocus = null
        page = "activity"
    }

    // The bottom bar.
    fun openTab(tab: String) {
        when (tab) {
            "home" -> {
                // Home again while on Home: back to the top.
                if (page == "home") scope.launch { homeListState.animateScrollToItem(0) }
                page = "home"
                resetFilters()
            }
            "activity" -> openActivity()
            else -> page = tab
        }
    }

    // A swipe moves to the neighbouring main tab and keeps Home's filters.
    fun swipeTo(direction: Int) {
        val next = MainTabs.getOrNull(MainTabs.indexOf(page) + direction) ?: return
        if (next == "activity") openActivity() else page = next
    }

    // Back returns to Home from any other page; Back on Post discards its draft.
    BackHandler(enabled = page != "home") {
        if (page == "post") leavePost() else page = "home"
        resetFilters()
    }

    // The app shrinks above the keyboard, so the bar would ride on it: hidden while typing.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val keyboardOpen by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaluColors.Cream)
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .swipeBetweenTabs(enabled = page in MainTabs) { direction -> swipeTo(direction) }
        ) {
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { pageTransition() },
                label = "homePage"
            ) { shown ->
                when (shown) {
                    "alerts" -> AlertsScreen(
                        notificationViewModel = notificationViewModel,
                        onOpenAlert = { alert -> openAlert(alert.type, alert.id, alert.conversationId, alert.reservationId) },
                        openAnnouncement = announcementToOpen,
                        onAnnouncementOpened = { announcementToOpen = null }
                    )

                    "messages" -> MessagesScreen(
                        messageViewModel = messageViewModel,
                        onBack = { page = "home" }
                    )

                    "post" -> pageStates.SaveableStateProvider("post-$postFormKey") {
                        PostScreen(
                            onBack = { leavePost() },
                            foodViewModel = foodViewModel,
                            onPostAnother = {
                                pageStates.removeState("post-$postFormKey")
                                postFormKey++
                            }
                        )
                    }

                    "activity" -> ActivityScreen(
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

                    "profile" -> ProfileScreen(
                        onAlerts = { page = "alerts" },
                        onLogout = onLogout,
                        notificationViewModel = notificationViewModel,
                        remoteProfile = currentProfile,
                        onProfileUpdated = onProfileUpdated,
                        reservationViewModel = reservationViewModel,
                        savedListingViewModel = savedListingViewModel,
                        // Saved Listings and the Successful claims/donations records open
                        // a listing by its ID: the feed's copy if it has one, else the row's.
                        onOpenListing = { post ->
                            foodViewModel.openListing(remotePosts.firstOrNull { it.id == post.id } ?: post)
                        }
                    )

                    else -> {
                        // "home" or "all": each has its own scroll position.
                        val onHome = shown == "home"
                        // Pull down to reload the listings and, if location access is
                        // already allowed, the phone's position (never asks for access).
                        PullToRefreshBox(
                            isRefreshing = feedPulled && feedLoading,
                            onRefresh = {
                                feedPulled = true
                                foodViewModel.loadAvailablePosts()
                                if (PhoneLocation.hasPermission(context)) refreshPhonePosition(askIfNeeded = false)
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                state = if (onHome) homeListState else allListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
                            ) {
                                item(key = "top") {
                                    if (onHome) {
                                        HomeHeader(
                                            greeting = greeting,
                                            photoUrl = profilePhotoUrl,
                                            name = currentProfile?.name.orEmpty(),
                                            unreadMessages = unreadMessages,
                                            unreadAlerts = unreadAlerts,
                                            onProfile = { page = "profile" },
                                            onMessages = { page = "messages" },
                                            onAlerts = { page = "alerts" }
                                        )
                                    } else {
                                        AllListingsHeader(
                                            onBack = {
                                                page = "home"
                                                resetFilters()
                                            }
                                        )
                                    }
                                }

                                item(key = "search") {
                                    FeedSearchAndFilters(
                                        search = search,
                                        onSearch = { search = it },
                                        category = category,
                                        onCategory = { value ->
                                            category = value
                                            if (value == "Nearby") refreshPhonePosition(askIfNeeded = true)
                                        },
                                        onFilterHelp = { filterHelp = true },
                                        locationLine = locationLine,
                                        // "Finding your location…" and the approximate-location note are not problems.
                                        locationProblem = locationProblem != null && !(locationFinder.finding && phonePin == null)
                                    )
                                }

                                // Hide the general recommendations while filtering,
                                // so the selected category's results are clear.
                                if (onHome && category == "All" && search.isBlank()) {
                                    item(key = "picked") {
                                        val now = Instant.now()
                                        val learned = recommendations.isNotEmpty()
                                        PickedForYouSection(
                                            learned = learned,
                                            preferencesActive = discoveryPreferences.isActive,
                                            foods = if (learned) recommendedFoods else preferredFoods.take(TOP_ROW_CARDS),
                                            reasonFor = { food ->
                                                recommendations.firstOrNull { it.post.id == food.id }
                                                    ?.let { recommendationLabel(it.reason) }
                                                    ?: discoveryPreferences.reasonFor(food.post)
                                                    ?: postedLabel(parseTimestamp(food.createdAt), now)
                                                    ?: "New listing"
                                            },
                                            loading = showFeedSkeleton,
                                            onOpen = { food -> foodViewModel.openListing(food.post) },
                                            onBrowseAll = {
                                                page = "all"
                                                category = "All"
                                                search = ""
                                                sort = "Newest"
                                            },
                                            onHowItWorks = { howItWorks = true },
                                            onPhotoError = foodViewModel::refreshExpiredPhotoUrls
                                        )
                                    }
                                }

                                item(key = "feed-header") {
                                    FeedHeader(
                                        title = when (category) {
                                            "Nearby" -> "Food within 5 km"
                                            "Donation" -> "Free food"
                                            "For Sale" -> "Food for sale"
                                            "Expiring Soon" -> "Expiring soon"
                                            else -> "Available food"
                                        },
                                        subtitle = if (showFeedSkeleton) "Loading listings…" else listingCount(visibleFoods.size),
                                        sort = sort,
                                        sortMenu = sortMenu,
                                        onSortMenu = { sortMenu = it },
                                        onSort = { option ->
                                            sort = option
                                            sortMenu = false
                                            if (option == "Nearest") refreshPhonePosition(askIfNeeded = true)
                                        }
                                    )
                                }

                                // My allergies: how many listings are left out, with Show or
                                // Hide. When every listing is left out, the empty state says it.
                                if (!showFeedSkeleton && allergyMatches.isNotEmpty() &&
                                    (showAllergyFoods || visibleFoods.isNotEmpty())
                                ) {
                                    item(key = "allergy-notice") {
                                        val found = Allergen.entries.filter { allergen ->
                                            allergyMatches.values.any { allergen in it }
                                        }
                                        InlineNotice(
                                            text = if (showAllergyFoods) {
                                                "Showing ${listingCount(allergyMatches.size)} with your allergies."
                                            } else {
                                                "${listingCount(allergyMatches.size)} hidden because of your " +
                                                        "allergies (${allergenNames(found)})."
                                            },
                                            icon = Icons.Outlined.WarningAmber,
                                            tone = NoticeTone.Warning,
                                            actionLabel = if (showAllergyFoods) "Hide" else "Show",
                                            onAction = { showAllergyFoods = !showAllergyFoods },
                                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                                        )
                                    }
                                }

                                // A failed reload keeps the listings already shown.
                                if (feedError != null && remotePosts.isNotEmpty()) {
                                    item(key = "feed-error") {
                                        InlineNotice(
                                            text = "Couldn't refresh the listings. These may be out of date.",
                                            icon = Icons.Outlined.CloudOff,
                                            tone = NoticeTone.Error,
                                            actionLabel = "Try again",
                                            onAction = { foodViewModel.loadAvailablePosts() },
                                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                                        )
                                    }
                                }

                                when {
                                    showFeedSkeleton -> item(key = "skeleton") {
                                        SkeletonGroup(
                                            description = "Loading food listings",
                                            modifier = Modifier.padding(horizontal = 20.dp)
                                        ) {
                                            repeat(3) {
                                                FoodCardSkeleton()
                                                Spacer(Modifier.height(12.dp))
                                            }
                                        }
                                    }

                                    visibleFoods.isEmpty() -> item(key = "empty") {
                                        FeedEmptyState(
                                            // Only when there is nothing to show; filters otherwise.
                                            feedError = feedError.takeIf { remotePosts.isEmpty() },
                                            search = search,
                                            category = category,
                                            hasPosition = phonePin != null,
                                            hiddenForAllergies = if (showAllergyFoods) 0 else allergyMatches.size,
                                            onRetry = { foodViewModel.loadAvailablePosts() },
                                            onClearSearch = { search = "" },
                                            onShowAll = { category = "All" },
                                            onShowAllergyFoods = { showAllergyFoods = true },
                                            onPost = { page = "post" }
                                        )
                                    }

                                    // All matching listings are shown; scroll to see more.
                                    else -> items(visibleFoods, key = { it.id }) { food ->
                                        AvailableFoodCard(
                                            food = food,
                                            allergyWarning = allergyMatches[food.id],
                                            // Your own listings can't be bookmarked.
                                            canSave = food.donorId != currentProfile?.id,
                                            saved = food.id in savedIds,
                                            onToggleSave = { savedListingViewModel.toggle(food.id) },
                                            onClick = { foodViewModel.openListing(food.post) },
                                            onPhotoError = foodViewModel::refreshExpiredPhotoUrls,
                                            modifier = Modifier
                                                .animateItem()
                                                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!keyboardOpen) {
            MainNavigationBar(
                selected = tabOf(page),
                unreadAlerts = unreadAlerts,
                onSelect = { tab -> openTab(tab) }
            )
        }
    }

    if (filterHelp) {
        AlertDialog(
            onDismissRequest = { filterHelp = false },
            icon = { Icon(Icons.Outlined.Tune, contentDescription = null, tint = SaluColors.Green) },
            title = { Text("About the filters") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterHelpRow(Icons.Outlined.NearMe, "Nearby", "Listings with a map pin within 5 km of you")
                    FilterHelpRow(Icons.Outlined.VolunteerActivism, "Free", "Donations: nothing to pay")
                    FilterHelpRow(Icons.Outlined.Sell, "For sale", "Food with a price, paid at pickup")
                    FilterHelpRow(Icons.Outlined.HourglassBottom, "Expiring soon", "Listings that expire within 3 hours")
                    Text(
                        "Tap a filter to show only those listings. Sort puts the nearest or newest first.",
                        style = SaluText.Supporting,
                        color = SaluColors.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { filterHelp = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (howItWorks) {
        ActionPage(heading = "How Salu-Salo works", onBack = { howItWorks = false }) {
            HowItWorksSettings(pickups = false)
        }
    }

    openedListing?.let { opened ->
        // The feed's latest copy while it has one: fresh photo URLs, price,
        // quantity and status after a reload.
        val latest = remotePosts.firstOrNull { it.id == opened.post.id }
        val food = (latest ?: opened.post).toHomeFood(null)
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { foodViewModel.closeListing() },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            FoodDetailsScreen(
                // The distance follows the phone's latest position.
                food = food.copy(distanceMeters = distanceTo(food.post)),
                // Listed when the page opened but not by the feed now: the old
                // copy would still offer Reserve.
                noLongerListed = opened.listed && latest == null,
                isOwnFood = currentProfile?.id != null && food.donorId == currentProfile.id,
                allergies = discoveryPreferences.allergies,
                saved = food.id in savedIds,
                onToggleSave = { savedListingViewModel.toggle(food.id) },
                loadExtraPhotos = { foodViewModel.extraPhotoUrls(food.post) },
                profileName = currentProfile?.name,
                reservationViewModel = reservationViewModel,
                onReserved = {
                    foodViewModel.loadAvailablePosts()
                    foodViewModel.closeListing()
                },
                onMessage = { draft -> messageViewModel.openForListing(food.id, draft) },
                onMessageReservation = { reservationId -> messageViewModel.openForReservation(reservationId) },
                onPhotoError = foodViewModel::refreshExpiredPhotoUrls,
                onBack = { foodViewModel.closeListing() }
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

    // A listing finished posting while Post was not open; the form shows its own result.
    if (page != "post") postResult?.let { result ->
        AlertDialog(
            onDismissRequest = { foodViewModel.clearPostResult() },
            title = { Text(if (result is PostResult.Published) "Listing published" else "Listing not published") },
            text = {
                Text(
                    when (result) {
                        is PostResult.Published -> "“${result.post.name}” is now in the community feed."
                        is PostResult.Failed -> result.message
                    }
                )
            },
            confirmButton = { TextButton(onClick = { foodViewModel.clearPostResult() }) { Text("OK") } }
        )
    }
}

/* MAIN NAVIGATION */

/** The main tabs, in bottom-bar and swipe order, with Post in the middle as in the original bar. */
private val MainTabs = listOf("home", "alerts", "post", "activity", "profile")

/** The tab a page belongs to: All listings and Messages open from Home. */
private fun tabOf(page: String): String = if (page == "all" || page == "messages") "home" else page

/** Left-to-right order of pages, so a transition slides the way the tabs lie. */
private fun pageOrder(page: String): Float = when (page) {
    "all", "messages" -> 0.5f
    else -> MainTabs.indexOf(page).coerceAtLeast(0).toFloat()
}

/** Tab changes slide a tenth of the width with a fade: movement, not a slideshow. */
private fun AnimatedContentTransitionScope<String>.pageTransition(): ContentTransform {
    val direction = if (pageOrder(targetState) >= pageOrder(initialState)) 1 else -1
    val slide = tween<IntOffset>(SaluMotion.LONG, easing = SaluMotion.Emphasized)
    return (slideInHorizontally(slide) { width -> direction * width / 10 } +
            fadeIn(tween(SaluMotion.MEDIUM, delayMillis = 60)))
        .togetherWith(
            slideOutHorizontally(slide) { width -> -direction * width / 10 } +
                    fadeOut(tween(SaluMotion.SHORT))
        )
        .using(SizeTransform(clip = false))
}

/**
 * Swipes between the main tabs: a long enough or quick enough sideways drag
 * moves one tab over. Anything that scrolls sideways itself (chip rows, photo
 * pagers, the map) keeps its own drags, because it takes them first.
 */
@Composable
private fun Modifier.swipeBetweenTabs(enabled: Boolean, onSwipe: (direction: Int) -> Unit): Modifier {
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    if (!enabled) return this
    return this.pointerInput(Unit) {
        val distance = 72.dp.toPx()
        val flingDistance = 28.dp.toPx()
        val flingVelocity = 700.dp.toPx()
        val tracker = VelocityTracker()
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = {
                dragged = 0f
                tracker.resetTracking()
            },
            onDragCancel = { dragged = 0f },
            onDragEnd = {
                val velocity = tracker.calculateVelocity().x
                val direction = when {
                    dragged <= -distance || (dragged <= -flingDistance && velocity <= -flingVelocity) -> 1
                    dragged >= distance || (dragged >= flingDistance && velocity >= flingVelocity) -> -1
                    else -> 0
                }
                if (direction != 0) currentOnSwipe(direction)
            },
            onHorizontalDrag = { change, amount ->
                change.consume()
                dragged += amount
                tracker.addPosition(change.uptimeMillis, change.position)
            }
        )
    }
}

private class NavTab(val page: String, val label: String, val selectedIcon: ImageVector, val icon: ImageVector)

private val NavTabs = listOf(
    NavTab("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab("alerts", "Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    NavTab("post", "Post", Icons.Outlined.Add, Icons.Outlined.Add),
    NavTab("activity", "Activity", Icons.Rounded.Timeline, Icons.Outlined.Timeline),
    NavTab("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
private fun MainNavigationBar(
    selected: String,
    unreadAlerts: Int,
    onSelect: (String) -> Unit
) {
    // The original bar: white, rounded on top and lifted by a soft shadow.
    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTabs.forEach { tab ->
                    val isSelected = tab.page == selected
                    val isPost = tab.page == "post"
                    val badge = if (tab.page == "alerts") unreadAlerts else 0
                    val interaction = remember { MutableInteractionSource() }
                    val indicatorAlpha by animateFloatAsState(
                        if (isSelected) 1f else 0f,
                        tween(SaluMotion.MEDIUM),
                        label = "navIndicatorAlpha"
                    )
                    val indicatorWidth by animateDpAsState(
                        if (isSelected) 60.dp else 36.dp,
                        tween(SaluMotion.MEDIUM, easing = SaluMotion.Emphasized),
                        label = "navIndicatorWidth"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(tab.page) },
                                role = Role.Tab,
                                interactionSource = interaction,
                                indication = ripple(bounded = false, radius = 36.dp)
                            )
                            .semantics {
                                contentDescription = if (badge > 0) "${tab.label}, $badge unread" else tab.label
                            }
                            .padding(top = 10.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .width(64.dp)
                                .clearAndSetSemantics { },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPost) {
                                // Post keeps the original green button with a white plus.
                                Box(
                                    Modifier
                                        .pressScale(interaction, pressedScale = 0.9f)
                                        .size(width = 40.dp, height = 30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(SaluColors.Green),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(tab.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                            } else {
                                Box(
                                    Modifier
                                        .size(width = indicatorWidth, height = 32.dp)
                                        .graphicsLayer { alpha = indicatorAlpha }
                                        .clip(SaluRadius.Pill)
                                        .background(SaluColors.IconGreen)
                                )
                                Box {
                                    Icon(
                                        if (isSelected) tab.selectedIcon else tab.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) SaluColors.Green else SaluColors.Gray,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .pressScale(interaction, pressedScale = 0.88f)
                                    )
                                    CountBadge(
                                        badge,
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 9.dp, y = (-6).dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tab.label,
                            modifier = Modifier.clearAndSetSemantics { },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = if (isSelected || isPost) SaluColors.Green else SaluColors.Gray,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/* HOME FEED PIECES */

@Composable
private fun HomeHeader(
    greeting: String,
    photoUrl: String?,
    name: String,
    unreadMessages: Int,
    unreadAlerts: Int,
    onProfile: () -> Unit,
    onMessages: () -> Unit,
    onAlerts: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The profile photo, else the name's initial; opens Profile.
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Open your profile", onClick = onProfile)
        ) {
            Avatar(photoUrl = photoUrl, name = name, size = 48.dp, contentDescription = "Your profile")
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                greeting,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = SaluColors.DarkGreen,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                "Make a difference today 🌿",
                style = SaluText.Supporting,
                color = SaluColors.Gray
            )
        }

        HeaderIconButton(
            icon = Icons.Outlined.ChatBubbleOutline,
            description = "Open messages",
            count = unreadMessages,
            onClick = onMessages
        )
        HeaderIconButton(
            icon = Icons.Outlined.Notifications,
            description = "Open alerts",
            count = unreadAlerts,
            onClick = onAlerts
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    description: String,
    count: Int,
    onClick: () -> Unit
) {
    // Not an IconButton: that clips its content to a 40 dp circle, which cut the
    // badge off along the curve. The same 48 dp target and round ripple, unclipped.
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp),
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = if (count > 0) "$description, $count unread" else description
            },
        contentAlignment = Alignment.Center
    ) {
        Box {
            Icon(icon, contentDescription = null, tint = SaluColors.DarkGreen, modifier = Modifier.size(24.dp))
            CountBadge(
                count,
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = (-7).dp)
            )
        }
    }
}

@Composable
private fun AllListingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to Home", tint = SaluColors.DarkGreen)
        }
        Text(
            "All food listings",
            style = SaluText.ScreenTitle,
            color = SaluColors.DarkGreen,
            modifier = Modifier.semantics { heading() }
        )
    }
}

/** A filter chip: the value the feed filters on, and the words people see. */
private class FeedFilter(val value: String, val label: String, val icon: ImageVector?)

private val FeedFilters = listOf(
    FeedFilter("All", "All", null),
    FeedFilter("Nearby", "Nearby", Icons.Outlined.NearMe),
    FeedFilter("Donation", "Free", Icons.Outlined.VolunteerActivism),
    FeedFilter("For Sale", "For sale", Icons.Outlined.Sell),
    FeedFilter("Expiring Soon", "Expiring soon", Icons.Outlined.HourglassBottom)
)

@Composable
private fun FeedSearchAndFilters(
    search: String,
    onSearch: (String) -> Unit,
    category: String,
    onCategory: (String) -> Unit,
    onFilterHelp: () -> Unit,
    locationLine: String?,
    locationProblem: Boolean
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search food or donors", style = SaluText.Body) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { onSearch("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = SaluColors.Gray)
                        }
                    }
                },
                singleLine = true,
                shape = SaluRadius.Control,
                textStyle = SaluText.Body,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = saluFieldColors()
            )

            IconButton(onClick = onFilterHelp) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "About the filters",
                    tint = SaluColors.Gray
                )
            }
        }

        LazyRow(
            modifier = Modifier.padding(top = 10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(FeedFilters, key = { it.value }) { filter ->
                SaluFilterChip(
                    label = filter.label,
                    selected = category == filter.value,
                    onClick = { onCategory(filter.value) },
                    icon = filter.icon
                )
            }
        }

        // One line under the chips while Nearby or Nearest is chosen.
        AnimatedVisibility(visible = locationLine != null) {
            InlineNotice(
                text = locationLine.orEmpty(),
                icon = if (locationProblem) Icons.Outlined.LocationOff else Icons.Outlined.MyLocation,
                tone = if (locationProblem) NoticeTone.Warning else NoticeTone.Info,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
            )
        }
    }
}

/** How many cards the row at the top of Home shows at most (it scrolls sideways). */
private const val TOP_ROW_CARDS = 4

/**
 * "Recommended for you" when the on-device model ([ListingRecommender]) has
 * picks ([learned]), else "Picked for you" when Food preferences or a pickup
 * area are set, else the newest listings; each card says why it is here.
 * Without picks the selection is as before: listings that match the saved
 * preferences, newest first.
 */
@Composable
private fun PickedForYouSection(
    learned: Boolean,
    preferencesActive: Boolean,
    foods: List<HomeFood>,
    reasonFor: (HomeFood) -> String,
    loading: Boolean,
    onOpen: (HomeFood) -> Unit,
    onBrowseAll: () -> Unit,
    onHowItWorks: () -> Unit,
    onPhotoError: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, top = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SaluColors.PaleGreen)
            .padding(vertical = 16.dp)
    ) {
        SectionHeader(
            title = when {
                learned -> "Recommended for you"
                preferencesActive -> "Picked for you"
                else -> "Just posted"
            },
            subtitle = when {
                learned -> "Learned from what you reserve and save"
                preferencesActive -> "Matches your food preferences"
                else -> "The newest food in your community"
            },
            icon = Icons.Outlined.AutoAwesome,
            iconTint = SaluColors.Sparkle,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            action = {
                TextButton(onClick = onBrowseAll) {
                    Text("Browse all", style = MaterialTheme.typography.labelLarge, color = SaluColors.Green)
                }
            }
        )

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                loading -> items(2) {
                    SkeletonGroup(description = "Loading listings") {
                        RecommendationSkeleton()
                    }
                }

                foods.isEmpty() -> item(key = "no-match") {
                    SaluCard(Modifier.width(220.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            IconBubble(Icons.Outlined.SearchOff, size = 36.dp, iconSize = 20.dp)
                            Spacer(Modifier.height(10.dp))
                            Text("No matching food yet", style = SaluText.FoodTitle, color = SaluColors.DarkGreen)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "You can still browse all listings below.",
                                style = SaluText.Supporting,
                                color = SaluColors.Gray
                            )
                        }
                    }
                }

                else -> items(foods, key = { it.id }) { food ->
                    RecommendationCard(
                        food = food,
                        reason = reasonFor(food),
                        onClick = { onOpen(food) },
                        onPhotoError = onPhotoError
                    )
                }
            }

            item(key = "how-it-works") {
                SaluCard(Modifier.width(196.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        IconBubble(Icons.Outlined.Eco, tint = SaluColors.Green, background = SaluColors.IconGreen, size = 36.dp, iconSize = 20.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Help reduce food waste in your community.",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp),
                            color = SaluColors.DarkGreen
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = onHowItWorks,
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            Text("Learn more", style = MaterialTheme.typography.labelLarge, color = SaluColors.Green)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SaluColors.Green, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedHeader(
    title: String,
    subtitle: String,
    sort: String,
    sortMenu: Boolean,
    onSortMenu: (Boolean) -> Unit,
    onSort: (String) -> Unit
) {
    SectionHeader(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 12.dp),
        action = {
            Box {
                TextButton(
                    onClick = { onSortMenu(true) },
                    modifier = Modifier.semantics { contentDescription = "Sort listings, now $sort" }
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = null,
                        tint = SaluColors.DarkGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(sort, style = MaterialTheme.typography.labelLarge, color = SaluColors.DarkGreen)
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = SaluColors.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = sortMenu,
                    onDismissRequest = { onSortMenu(false) }
                ) {
                    listOf("Newest", "Nearest", "Name").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { onSort(option) },
                            trailingIcon = {
                                if (option == sort) {
                                    Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = SaluColors.Green)
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}

/** What to say when the feed has nothing to show, and what the user can do. */
@Composable
private fun FeedEmptyState(
    feedError: String?,
    search: String,
    category: String,
    // False while Nearby has no phone position; the note under the chips says why.
    hasPosition: Boolean,
    // Listings left out because of My allergies (0 after Show).
    hiddenForAllergies: Int,
    onRetry: () -> Unit,
    onClearSearch: () -> Unit,
    onShowAll: () -> Unit,
    onShowAllergyFoods: () -> Unit,
    onPost: () -> Unit
) {
    val modifier = Modifier.padding(horizontal = 20.dp)
    when {
        feedError != null -> EmptyState(
            icon = Icons.Outlined.CloudOff,
            title = "Could not load listings",
            message = "Check your internet connection, then try again or pull down to refresh.",
            actionLabel = "Try again",
            onAction = onRetry,
            tint = SaluColors.Error,
            iconBackground = SaluColors.ErrorSoft,
            modifier = modifier
        )

        hiddenForAllergies > 0 -> EmptyState(
            icon = Icons.Outlined.NoFood,
            title = "No food without your allergies right now",
            message = if (hiddenForAllergies == 1) "1 listing with your allergies is hidden."
            else "$hiddenForAllergies listings with your allergies are hidden.",
            actionLabel = "Show them",
            onAction = onShowAllergyFoods,
            modifier = modifier
        )

        search.isNotBlank() -> EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = "No food matches “${search.trim()}”",
            message = "Try another word, or clear the search to see every listing.",
            actionLabel = "Clear search",
            onAction = onClearSearch,
            modifier = modifier
        )

        category == "Nearby" -> EmptyState(
            icon = Icons.Outlined.NearMe,
            title = if (hasPosition) "No nearby listings" else "Nearby needs your location",
            message = if (hasPosition) {
                "No listings with a map pin within 5 km."
            } else {
                "Nearby shows listings with a map pin within 5 km of your phone."
            },
            actionLabel = "Show all food",
            onAction = onShowAll,
            modifier = modifier
        )

        category != "All" -> EmptyState(
            icon = Icons.Outlined.FilterAltOff,
            title = "Nothing here right now",
            message = when (category) {
                "Donation" -> "No free food is listed at the moment."
                "For Sale" -> "No food is for sale at the moment."
                else -> "No listings expire within the next 3 hours."
            },
            actionLabel = "Show all food",
            onAction = onShowAll,
            modifier = modifier
        )

        else -> EmptyState(
            icon = Icons.Outlined.RiceBowl,
            title = "No available food listings yet",
            message = "When people in your community share surplus food, it appears here. Have extra food? Share it.",
            actionLabel = "Post food",
            onAction = onPost,
            modifier = modifier
        )
    }
}

@Composable
private fun FilterHelpRow(icon: ImageVector, label: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        IconBubble(icon, size = 32.dp, iconSize = 18.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp), color = SaluColors.DarkGreen)
            Text(text, style = SaluText.Supporting, color = SaluColors.Gray)
        }
    }
}

/** "1 listing", "12 listings". */
private fun listingCount(count: Int): String = if (count == 1) "1 listing" else "$count listings"

private fun FoodPost.toHomeFood(distanceMeters: Double?): HomeFood {
    val now = Instant.now()
    val expiration = parseTimestamp(expirationDate)
    val urgent = expiration?.isBefore(now.plusSeconds(3 * 60 * 60)) == true
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
        donorRating = donorRatingLabel,
        imageUrl = displayImageUrl,
        urgent = urgent,
        description = description?.takeIf { it.isNotBlank() },
        createdAt = createdAt,
        distanceMeters = distanceMeters,
        expiresText = expiryLabel(expiration, now),
        unitLabel = unitSingular(unit),
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
    // "★ 4.7 (12)", or null when the donor has no reviews.
    val donorRating: String? = null,
    val imageUrl: String? = null,
    val urgent: Boolean,
    val description: String? = null,
    val createdAt: String? = null,
    // Straight line from the phone to the pickup pin; null without either.
    val distanceMeters: Double? = null,
    // "Expires in 2 h" or "Expires Sep 30"; null when the time is unknown.
    val expiresText: String? = null,
    // The unit one price is for, e.g. "pack" for packs.
    val unitLabel: String = "",
    // The listing itself, for the details that the cards don't show.
    val post: FoodPost
) {
    // From the listing's price, not the display text.
    val free: Boolean get() = post.price <= 0.0
}

/** A card in the row at the top of Home: photo with its price, the name, and why it is here. */
@Composable
private fun RecommendationCard(
    food: HomeFood,
    reason: String,
    onClick: () -> Unit,
    onPhotoError: () -> Unit
) {
    SaluCard(
        modifier = Modifier.width(212.dp),
        onClick = onClick
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(118.dp)
            ) {
                ListingPhoto(
                    imageUrl = food.imageUrl,
                    hasPhoto = food.post.hasPhoto,
                    contentDescription = food.title,
                    modifier = Modifier.fillMaxSize(),
                    onLoadError = onPhotoError
                )

                PhotoPriceLabel(
                    food = food,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )

                if (food.urgent) {
                    StatusBadge(
                        SaluStatus.ExpiresSoon,
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    food.title,
                    style = SaluText.FoodTitle,
                    color = SaluColors.DarkGreen,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = SaluColors.Green,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        reason,
                        style = SaluText.Meta,
                        color = SaluColors.Green,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    listOfNotNull(
                        food.quantity,
                        food.distanceMeters?.let { "${distanceLabel(it)} away" } ?: food.pickupLocation
                    ).joinToString(" · "),
                    style = SaluText.Meta,
                    color = SaluColors.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The price on a white label over a photo: "Free" in green, else the price per unit. */
@Composable
private fun PhotoPriceLabel(food: HomeFood, modifier: Modifier = Modifier) {
    Text(
        if (food.free) "Free" else "${food.price} / ${food.unitLabel}",
        style = SaluText.Meta.copy(fontWeight = FontWeight.Bold),
        color = if (food.free) SaluColors.Green else SaluColors.DarkGreen,
        maxLines = 1,
        modifier = modifier
            .clip(SaluRadius.Small)
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** Skeleton of a card in the row at the top of Home. */
@Composable
private fun RecommendationSkeleton() {
    SaluCard(Modifier.width(212.dp)) {
        Column {
            SkeletonBlock(
                Modifier
                    .fillMaxWidth()
                    .height(118.dp),
                RoundedCornerShape(0.dp)
            )
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(16.dp)
                )
                SkeletonBlock(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(12.dp)
                )
                SkeletonBlock(
                    Modifier
                        .fillMaxWidth(0.45f)
                        .height(12.dp)
                )
            }
        }
    }
}

/**
 * A Home listing: the photo and name first, then who shares it, the price and
 * amount, and when and where to pick it up.
 */
@Composable
private fun AvailableFoodCard(
    food: HomeFood,
    // The user's allergies this listing contains (shown after Home's Show).
    allergyWarning: List<Allergen>?,
    canSave: Boolean,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onClick: () -> Unit,
    onPhotoError: () -> Unit,
    modifier: Modifier = Modifier
) {
    SaluCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .size(104.dp)
                    .clip(SaluRadius.Photo)
            ) {
                ListingPhoto(
                    imageUrl = food.imageUrl,
                    hasPhoto = food.post.hasPhoto,
                    contentDescription = food.title,
                    modifier = Modifier.fillMaxSize(),
                    onLoadError = onPhotoError
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        food.title,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                        style = SaluText.FoodTitle,
                        color = SaluColors.DarkGreen,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (canSave) {
                        IconButton(
                            onClick = onToggleSave,
                            modifier = Modifier.offset(x = 10.dp, y = (-10).dp)
                        ) {
                            SaveToggleIcon(saved = saved, title = food.title)
                        }
                    }
                }

                Text(
                    buildAnnotatedString {
                        append(food.donorName)
                        food.donorRating?.let {
                            append("  ")
                            withStyle(SpanStyle(color = SaluColors.RatingAmber, fontWeight = FontWeight.SemiBold)) { append(it) }
                        }
                    },
                    style = SaluText.Supporting,
                    color = SaluColors.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!allergyWarning.isNullOrEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    MetaItem(
                        icon = Icons.Outlined.WarningAmber,
                        text = "Contains ${allergenNames(allergyWarning)}",
                        color = SaluColors.Error
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    PriceTag(price = food.price, free = food.free, unit = food.unitLabel.takeIf { !food.free })
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${food.quantity} available",
                        style = SaluText.Meta,
                        color = SaluColors.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    food.expiresText?.let { expires ->
                        MetaItem(
                            icon = if (food.urgent) Icons.Outlined.HourglassBottom else Icons.Outlined.Schedule,
                            text = expires,
                            color = if (food.urgent) SaluColors.DarkGreen else SaluColors.Gray,
                            iconTint = if (food.urgent) SaluColors.Urgent else SaluColors.Gray
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    MetaItem(
                        icon = Icons.Outlined.LocationOn,
                        text = food.distanceMeters?.let { "${distanceLabel(it)} away" } ?: food.pickupLocation,
                        color = SaluColors.Gray,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

/** A small icon and a short text on one line. */
@Composable
private fun MetaItem(
    icon: ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    iconTint: Color = color
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = SaluText.Meta,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The bookmark icon; it pops briefly when the saved state changes. */
@Composable
private fun SaveToggleIcon(saved: Boolean, title: String, tint: Color = SaluColors.Gray) {
    val scale = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) {
        if (saved != shown) {
            shown = saved
            scale.animateTo(1.25f, tween(110))
            scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
        }
    }
    Icon(
        imageVector = if (saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
        contentDescription = if (saved) "Remove saved $title" else "Save $title",
        tint = if (saved) SaluColors.Green else tint,
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
    )
}

/* FOOD DETAILS SCREEN */

@Composable
private fun FoodDetailsScreen(
    food: HomeFood,
    // The feed listed it when the page opened and no longer does.
    noLongerListed: Boolean,
    isOwnFood: Boolean,
    // My allergies (saved on the phone).
    allergies: Set<Allergen>,
    saved: Boolean,
    onToggleSave: () -> Unit,
    loadExtraPhotos: suspend () -> List<String?>,
    profileName: String?,
    reservationViewModel: ReservationViewModel,
    // Closes the details page and reloads the feed after a reservation.
    onReserved: () -> Unit,
    onMessage: (String) -> Unit,
    onMessageReservation: (String) -> Unit,
    onPhotoError: () -> Unit,
    onBack: () -> Unit
) {
    // From the listing itself, not parsed back out of the display texts.
    val available = food.post.quantity
    val unit = food.post.unit
    val donation = food.free

    val limit = food.post.maxPerRecipient
    // How much of this listing the person already holds or picked up; read once
    // when the page opens and only for a listing with a limit (null while unknown).
    val held by produceState<Int?>(null, food.id, limit) {
        value = if (limit == null || isOwnFood) 0 else reservationViewModel.heldQuantity(food.id)
    }
    // A failed read counts as 0; the server still refuses over the limit.
    val reservable = reservableNow(limit, available, held ?: 0)
    var quantity by rememberSaveable(food.id) {
        mutableStateOf(1)
    }
    // A reload (or the count above) may leave less than the amount chosen.
    LaunchedEffect(reservable) { if (quantity > reservable) quantity = reservable.coerceAtLeast(1) }
    // Re-checked when the expiry or the end of pickup passes while the page is
    // open, and when the app comes back (a delay doesn't count while the phone sleeps).
    var clockTick by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(food.post.expirationDate, food.post.pickupEnd, clockTick) {
        val next = food.post.nextReserveBlockerChange(clockTick) ?: return@LaunchedEffect
        delay(java.time.Duration.between(Instant.now(), next).toMillis().coerceAtLeast(0L) + 1L)
        clockTick = Instant.now()
    }
    LifecycleResumeEffect(Unit) {
        clockTick = Instant.now()
        onPauseOrDispose { }
    }
    // Why a reservation would be refused now (a saved or older copy may be
    // removed, expired, past its pickup time or fully reserved); null if it wouldn't.
    val blocker = if (noLongerListed) {
        "This listing is no longer available to reserve."
    } else {
        food.post.reserveBlocker(maxOf(clockTick, Instant.now()))
    }
    var expanded by rememberSaveable(food.id) {
        mutableStateOf(false)
    }
    // True when the description is longer than its first 4 lines.
    var descriptionOverflows by remember(food.id) { mutableStateOf(false) }
    // The photo the full-screen view opened at; null while it's closed.
    var viewerPage by rememberSaveable(food.id) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf("") }
    var noticeTitle by remember { mutableStateOf("") }
    var foodAction by rememberSaveable(food.id) {
        mutableStateOf("")
    }
    // The price and amount the Review reservation page shows, fixed when it
    // opens: a feed reload meanwhile must not change what Confirm sends (the
    // server refuses a price that has changed since). Null while it is closed.
    var reviewPrice by rememberSaveable(food.id) { mutableStateOf<Double?>(null) }
    var reviewQuantity by rememberSaveable(food.id) { mutableIntStateOf(1) }
    val reserveSaving by reservationViewModel.saving.collectAsStateWithLifecycle()
    val reserveResult by reservationViewModel.reserveResult.collectAsStateWithLifecycle()

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

    var reporting by rememberSaveable(food.id) {
        mutableStateOf(false)
    }

    if (foodAction.isNotEmpty()) {
        FoodActionsDialog(
            action = foodAction,
            foodPostId = food.id,
            foodTitle = food.title,
            provider = food.donorName,
            donorName = food.post.donorName?.takeIf { it.isNotBlank() },
            isOwnListing = isOwnFood,
            donorEmailVerified = food.post.donorEmailVerified,
            donorRatingAverage = food.post.donorRatingAverage,
            donorReviewCount = food.post.donorReviewCount,
            hasPhoto = food.post.hasPhoto,
            pickupLocation = food.pickupLocation,
            pickupPin = food.post.pickupPin,
            distanceMeters = food.distanceMeters,
            imageUrl = food.imageUrl,
            price = food.price,
            status = food.status,
            onClose = { foodAction = "" },
            onMessage = { draft -> messageDonor(draft) }
        )
    }

    if (reporting && !isOwnFood) {
        ReportPage(
            target = ReportTarget.LISTING,
            targetId = food.id,
            heading = "Report listing",
            prompt = "What's wrong with ${food.title}?",
            onClose = { reporting = false }
        )
    }

    val description = food.description ?: "No description provided."

    // Listings made before these fields existed, or left blank, have none.
    // Blank allergens mean "not provided", never "allergen-free".
    val notProvided = "Not provided — ask the donor"
    val notSet = "Not set — ask the donor"
    val post = food.post
    val allergens = post.allergens?.takeIf { it.isNotBlank() } ?: notProvided
    // My allergies: the ones the donor picked, and whether the rest could be
    // checked at all (typed text and "Other" can't). Never shown as safe.
    val myAllergens = if (isOwnFood) emptyList() else allergiesIn(post.allergens, allergies)
    val pickedAllergens = parseAllergens(post.allergens)
    val allergensUnchecked = !isOwnFood && allergies.isNotEmpty() &&
            (pickedAllergens == null || pickedAllergens.other != null)
    val storageNote = post.storageNotes?.takeIf { it.isNotBlank() } ?: notProvided
    val pickupWindow = listingPickupWindow(post.pickupStart, post.pickupEnd) ?: notSet
    val handover = ListingHandover.fromCode(post.handover)?.label ?: notSet
    val expires = parseTimestamp(post.expirationDate)?.let { listingTime(it) } ?: "—"

    val frozenPrice = reviewPrice
    if (frozenPrice != null && !isOwnFood) {
        ReservationReviewDialog(
            foodPostId = food.id,
            foodTitle = food.title,
            donorName = post.donorName?.takeIf { it.isNotBlank() },
            hasPhoto = post.hasPhoto,
            imageUrl = food.imageUrl,
            profileName = profileName,
            quantity = reviewQuantity,
            unit = unit,
            unitPrice = frozenPrice,
            pickupLocation = food.pickupLocation,
            pickupWindow = pickupWindow,
            handover = handover,
            expires = expires,
            pickupStart = parseTimestamp(post.pickupStart),
            pickupEnd = parseTimestamp(post.pickupEnd),
            expiration = parseTimestamp(post.expirationDate),
            saving = reserveSaving,
            result = reserveResult,
            // The price shown and the chosen hold; the server refuses if the price has changed.
            onConfirm = { note, holdMinutes ->
                reservationViewModel.reserve(food.id, reviewQuantity, frozenPrice, note, holdMinutes)
            },
            onMessageDonor = { reservationId -> onMessageReservation(reservationId) },
            onFinished = {
                reviewPrice = null
                reservationViewModel.clearReserveResult()
                onReserved()
            },
            onBack = {
                // Leaving while the request runs keeps its result for the receipt.
                if (!reserveSaving) {
                    reviewPrice = null
                    reservationViewModel.clearReserveResult()
                }
            }
        )
    }

    // The cover is already signed; photos 2–4 get their own signed URLs here.
    val extraPhotos by produceState<List<String?>?>(null, post.id) {
        value = if (post.extraImagePaths.isEmpty()) emptyList() else loadExtraPhotos()
    }
    val photoCount = 1 + post.extraImagePaths.size
    val pagerState = rememberPagerState(pageCount = { photoCount })
    // Only a listing with a stored photo opens the full-screen view.
    fun photoModifier(index: Int) = Modifier
        .fillMaxSize()
        .clickable(enabled = post.hasPhoto, onClickLabel = "View full screen") { viewerPage = index }

    viewerPage?.let { start ->
        val loaded = extraPhotos
        FullScreenPhotos(
            title = food.title,
            imageUrls = listOf(food.imageUrl) + List(post.extraImagePaths.size) { loaded?.getOrNull(it) },
            hasPhotos = listOf(post.hasPhoto) + List(post.extraImagePaths.size) { loaded != null },
            startPage = start.coerceIn(0, photoCount - 1),
            onClose = { page ->
                viewerPage = null
                // The header shows the photo that was being viewed.
                scope.launch { pagerState.scrollToPage(page) }
            }
        )
    }

    val role = if (donation) "donor" else "seller"
    val payLine = if (donation) {
        "Free — nothing to pay. Please be respectful and pick up on time."
    } else {
        "You'll pay ${formatPeso(reservationTotal(food.unitPrice, quantity))} " +
                "at pickup for $quantity $unit. The app takes no payments."
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SaluColors.Cream
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Food photographs and floating controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    // Swipe between the photos; each has its own fallback.
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { index ->
                        if (index == 0) {
                            ListingPhoto(
                                imageUrl = food.imageUrl,
                                hasPhoto = food.post.hasPhoto,
                                contentDescription = food.title,
                                modifier = photoModifier(index),
                                textSize = 12.sp,
                                onLoadError = onPhotoError
                            )
                        } else {
                            val loaded = extraPhotos
                            // A plain tile while photos 2–4 are still being signed (loaded == null).
                            ListingPhoto(
                                imageUrl = loaded?.getOrNull(index - 1),
                                hasPhoto = loaded != null,
                                contentDescription = "${food.title}, photo ${index + 1}",
                                modifier = photoModifier(index),
                                textSize = 12.sp
                            )
                        }
                    }

                    // A soft shade behind the buttons, so they read on any photo.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent)
                                )
                            )
                    )

                    if (photoCount > 1) {
                        PageDots(
                            count = photoCount,
                            current = pagerState.currentPage,
                            onDark = true,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .clip(SaluRadius.Pill)
                                .background(Color.Black.copy(alpha = 0.35f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = onBack,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = SaluColors.DarkGreen
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back to food listings"
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // Your own listing can't be bookmarked.
                        if (!isOwnFood) FilledIconButton(
                            onClick = onToggleSave,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = SaluColors.DarkGreen
                            )
                        ) {
                            SaveToggleIcon(saved = saved, title = food.title, tint = SaluColors.DarkGreen)
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
                ) {
                    if (myAllergens.isNotEmpty()) {
                        InlineNotice(
                            text = "Contains ${allergenNames(myAllergens)}, which " +
                                    (if (myAllergens.size == 1) "is" else "are") + " on your allergy list.",
                            icon = Icons.Outlined.WarningAmber,
                            tone = NoticeTone.Error
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (noLongerListed) {
                            StatusBadge(SaluStatus.Removed, label = "Unavailable")
                        } else StatusBadge(
                            when {
                                food.post.status == FoodPostStatus.REMOVED -> SaluStatus.Removed
                                blocker == null -> if (food.urgent) SaluStatus.ExpiresSoon else SaluStatus.Available
                                food.post.status == FoodPostStatus.AVAILABLE && food.post.quantity > 0 -> SaluStatus.Expired
                                else -> SaluStatus.Reserved
                            }
                        )
                        Text(
                            post.category,
                            style = SaluText.Status,
                            color = SaluColors.Gray,
                            modifier = Modifier
                                .clip(SaluRadius.Pill)
                                .background(SaluColors.PaleGreen)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        food.title,
                        style = SaluText.ScreenTitle,
                        color = SaluColors.DarkGreen,
                        modifier = Modifier.semantics { heading() }
                    )

                    Spacer(Modifier.height(16.dp))

                    // Who shares it; opens the Donor/Seller page with the reviews.
                    SaluCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { foodAction = "provider" }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Avatar(photoUrl = null, name = food.donorName, size = 44.dp)

                            Spacer(Modifier.width(12.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    food.donorName,
                                    style = SaluText.FoodTitle,
                                    color = SaluColors.DarkGreen,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    buildAnnotatedString {
                                        append(if (donation) "Donor" else "Seller")
                                        append(" · ")
                                        val rating = food.donorRating
                                        if (rating != null) {
                                            withStyle(SpanStyle(color = SaluColors.RatingAmber, fontWeight = FontWeight.SemiBold)) {
                                                append(rating)
                                            }
                                        } else {
                                            append("No reviews yet")
                                        }
                                    },
                                    style = SaluText.Supporting,
                                    color = SaluColors.Gray
                                )
                                if (post.donorEmailVerified) {
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Outlined.Verified,
                                            contentDescription = null,
                                            tint = SaluColors.Green,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Email verified", style = SaluText.Meta, color = SaluColors.Green)
                                    }
                                }
                            }

                            if (!isOwnFood) {
                                Spacer(Modifier.width(8.dp))
                                SaluSecondaryButton(
                                    text = "Message",
                                    onClick = { messageDonor("") },
                                    modifier = Modifier,
                                    icon = Icons.Outlined.ChatBubbleOutline
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = SaluColors.Gray
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Three facts at a glance: how much, what it costs, until when.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(SaluRadius.Card)
                            .background(SaluColors.PaleGreen)
                            .padding(vertical = 14.dp)
                    ) {
                        KeyFact(
                            icon = Icons.Outlined.Inventory2,
                            value = food.quantity,
                            caption = "Available",
                            modifier = Modifier.weight(1f)
                        )
                        KeyFactDivider()
                        KeyFact(
                            icon = if (donation) Icons.Outlined.VolunteerActivism else Icons.Outlined.Sell,
                            value = food.price,
                            caption = if (donation) "Donation" else "Per ${food.unitLabel}",
                            modifier = Modifier.weight(1f),
                            valueColor = if (donation) SaluColors.Green else SaluColors.DarkGreen
                        )
                        KeyFactDivider()
                        KeyFact(
                            icon = if (food.urgent) Icons.Outlined.HourglassBottom else Icons.Outlined.Schedule,
                            value = expires,
                            caption = "Expires",
                            modifier = Modifier.weight(1f),
                            iconTint = if (food.urgent) SaluColors.Urgent else SaluColors.Green
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    DetailSection("About this food") {
                        Text(
                            description,
                            modifier = Modifier.smoothSize(),
                            style = SaluText.Body,
                            color = if (food.description == null) SaluColors.Gray else SaluColors.DarkGreen,
                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { layout -> if (!expanded) descriptionOverflows = layout.hasVisualOverflow }
                        )

                        // Only a description longer than 4 lines has Read more.
                        if (expanded || descriptionOverflows) TextButton(
                            onClick = { expanded = !expanded },
                            contentPadding = PaddingValues(horizontal = 0.dp)
                        ) {
                            Text(
                                if (expanded) "Read less" else "Read more",
                                style = MaterialTheme.typography.labelLarge,
                                color = SaluColors.Green
                            )

                            Icon(
                                imageVector = if (expanded) {
                                    Icons.Outlined.ExpandLess
                                } else {
                                    Icons.Outlined.ExpandMore
                                },
                                contentDescription = null,
                                tint = SaluColors.Green,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    DetailSection("Pickup") {
                        DetailRow(
                            icon = Icons.Outlined.Schedule,
                            label = "Pickup window",
                            value = pickupWindow,
                            missing = pickupWindow == notSet,
                            actionLabel = "Ask the $role when to pick up",
                            onClick = { messageDonor("Hi! When can I pick up ${food.title}?") }
                        )

                        DetailRow(
                            icon = Icons.Outlined.Handshake,
                            label = "Handover",
                            value = handover,
                            missing = handover == notSet,
                            actionLabel = "Ask the $role about the handover",
                            onClick = { messageDonor("Hi! How will the handover for ${food.title} work?") }
                        )

                        DetailRow(
                            icon = Icons.Outlined.LocationOn,
                            label = "Pickup location",
                            value = food.pickupLocation + (food.distanceMeters?.let {
                                "\nAbout ${distanceLabel(it)} away (straight line)"
                            } ?: ""),
                            trailing = Icons.Outlined.Map,
                            actionLabel = "View the pickup location",
                            onClick = { foodAction = "location" }
                        )

                        SaluSecondaryButton(
                            text = "View on map",
                            onClick = { foodAction = "location" },
                            icon = Icons.Outlined.Map,
                            modifier = Modifier
                                .padding(start = 48.dp, top = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    DetailSection("Food safety") {
                        DetailRow(
                            icon = Icons.Outlined.WarningAmber,
                            label = "Allergens",
                            value = allergens,
                            missing = allergens == notProvided,
                            actionLabel = "Ask the $role about allergens",
                            onClick = { messageDonor("Hi! What allergens does ${food.title} contain?") }
                        )

                        if (allergensUnchecked) {
                            InlineNotice(
                                text = if (pickedAllergens == null) {
                                    "These allergens can't be checked against your allergies. " +
                                            "Ask the $role if you're unsure."
                                } else {
                                    "Allergens under \"Other\" aren't checked against your allergies. " +
                                            "Read them, and ask the $role if you're unsure."
                                },
                                icon = Icons.Outlined.WarningAmber,
                                tone = NoticeTone.Warning
                            )
                        }

                        DetailRow(
                            icon = Icons.Outlined.AcUnit,
                            label = "Storage",
                            value = storageNote,
                            missing = storageNote == notProvided,
                            actionLabel = "Ask the $role about storage",
                            onClick = { messageDonor("Hi! How should I store ${food.title} after pickup?") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        if (isOwnFood) {
                            "Questions from others about this listing appear in Messages."
                        } else {
                            "Tap a detail to ask the $role about it in the chat."
                        },
                        style = SaluText.Meta,
                        color = SaluColors.Gray
                    )

                    if (!isOwnFood) {
                        Spacer(Modifier.height(12.dp))

                        TextButton(
                            onClick = { reporting = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                Icons.Outlined.Flag,
                                contentDescription = null,
                                tint = SaluColors.Error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Report this listing",
                                style = MaterialTheme.typography.labelLarge,
                                color = SaluColors.Error
                            )
                        }
                    }
                }
            }

            // Main action, always in reach: how many, what it costs, Reserve.
            Surface(color = Color.White) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(color = SaluColors.Line)
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        if (isOwnFood) {
                            InlineNotice(
                                text = "This is your listing, so you cannot reserve it. " +
                                        "Questions from others appear in Messages.",
                                icon = Icons.Outlined.Info
                            )
                        } else if (blocker != null) {
                            InlineNotice(text = blocker, icon = Icons.Outlined.Info)
                        } else if (limit != null && held != null && reservable == 0) {
                            InlineNotice(
                                text = "You've reserved the most this listing allows per person ($limit).",
                                icon = Icons.Outlined.Info
                            )
                        } else {
                            if (limit != null) {
                                Text("Limit: $limit per person", style = SaluText.Meta, color = SaluColors.Gray)
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(payLine, style = SaluText.Supporting, color = SaluColors.DarkGreen)

                            Spacer(Modifier.height(10.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QuantityStepper(
                                    quantity = quantity,
                                    available = reservable.coerceAtLeast(1),
                                    unit = unit,
                                    onDecrease = { quantity-- },
                                    onIncrease = { quantity++ }
                                )

                                Spacer(Modifier.width(12.dp))

                                SaluPrimaryButton(
                                    text = "Reserve",
                                    onClick = {
                                        reservationViewModel.clearReserveResult()
                                        reviewQuantity = quantity
                                        reviewPrice = food.post.price
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
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

private val listingTimeFormat = java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", java.util.Locale.ENGLISH)
private val listingClockFormat = java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)

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

/** One of the three facts under a listing's title: an icon, the value (up to 2 lines) and a caption. */
@Composable
private fun KeyFact(
    icon: ImageVector,
    value: String,
    caption: String,
    modifier: Modifier,
    valueColor: Color = SaluColors.DarkGreen,
    iconTint: Color = SaluColors.Green
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = valueColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Text(
            caption,
            modifier = Modifier.padding(top = 4.dp),
            style = SaluText.Meta,
            color = SaluColors.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun KeyFactDivider() {
    Box(
        Modifier
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .width(1.dp)
            .background(SaluColors.Line)
    )
}

/** A titled group of details, separated by space rather than boxes. */
@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = SaluText.SectionTitle,
            color = SaluColors.DarkGreen,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/**
 * A detail with its icon. Tapping it does [actionLabel] (asks the donor in the
 * chat, or opens the map); [missing] values are shown in muted text.
 */
@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    actionLabel: String,
    onClick: () -> Unit,
    missing: Boolean = false,
    trailing: ImageVector = Icons.Outlined.ChatBubbleOutline
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SaluRadius.Small)
            .clickable(onClickLabel = actionLabel, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        IconBubble(
            icon,
            size = 36.dp,
            iconSize = 19.dp
        )

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(label, style = SaluText.Meta, color = SaluColors.Gray)
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = SaluText.Body,
                color = if (missing) SaluColors.Gray else SaluColors.DarkGreen
            )
        }

        Icon(
            trailing,
            contentDescription = null,
            tint = SaluColors.Gray,
            modifier = Modifier
                .padding(start = 8.dp, top = 8.dp)
                .size(18.dp)
        )
    }
}

/** − quantity + for the reservation; the server still checks the amount. */
@Composable
private fun QuantityStepper(
    quantity: Int,
    available: Int,
    unit: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(SaluRadius.Control)
            .background(SaluColors.PaleGreen)
            .semantics(mergeDescendants = false) {
                stateDescription = "$quantity of $available $unit"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDecrease,
            enabled = quantity > 1
        ) {
            Icon(
                Icons.Outlined.Remove,
                contentDescription = "Decrease quantity"
            )
        }

        Text(
            quantity.toString(),
            modifier = Modifier.widthIn(min = 28.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = SaluColors.DarkGreen
        )

        IconButton(
            onClick = onIncrease,
            enabled = quantity < available
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Increase quantity"
            )
        }
    }
}
