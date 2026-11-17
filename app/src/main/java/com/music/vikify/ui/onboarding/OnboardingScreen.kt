package com.music.vikify.ui.onboarding

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource as androidPainterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.vikify.R
import com.music.vikify.auth.GoogleAuthHelper
import com.music.vikify.data.firebase.FirebaseSyncManager
import com.music.vikify.data.settings.AppSettings
import com.music.vikify.sharedui.resources.Res
import com.music.vikify.sharedui.resources.ic_google_g
import com.music.vikify.sharedui.resources.ic_logo
import com.music.vikify.sharedui.resources.spotify_logo
import com.music.vikify.ui.icons.VikifyIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private const val NAME_LIMIT = 40
private const val PAGE_NAME = 0
private const val PAGE_CONNECT = 1

/**
 * Onboarding Flow:
 * Page 1: "What should we call you?" - minimalist aesthetic with angled 3D wave hero graphic.
 * Page 2: Optional account connections, with an always-visible way into the app.
 */
@Composable
fun OnboardingScreen(
    signedIn: Boolean,
    initialName: String,
    onNameChosen: (String) -> Unit,
    onSignIn: () -> Unit,
    onSpotifySignIn: () -> Unit = {},
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_NAME) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var isSigningInGoogle by remember { mutableStateOf(false) }
    var googleAuthError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity
    val spotifyToken by AppSettings.spotifySpdcToken.collectAsState()
    val firebaseUserEmail by FirebaseSyncManager.userEmail.collectAsState()
    val isAnonymousFirebase by FirebaseSyncManager.isAnonymous.collectAsState()
    val hasGoogleAccount = !isAnonymousFirebase && !firebaseUserEmail.isNullOrBlank()

    BackHandler(enabled = page == PAGE_CONNECT) {
        page = PAGE_NAME
    }

    // Google Account Picker launcher (discovers existing Google accounts on the phone)
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val parsed = GoogleAuthHelper.parseSignInResult(result.data)
        parsed.onSuccess { account ->
            coroutineScope.launch {
                try {
                    val signResult = FirebaseSyncManager.signInWithGoogleCredential(
                        idToken = account.idToken,
                        email = account.email,
                        displayName = account.displayName,
                    )
                    signResult.onSuccess {
                        val chosenName = account.displayName?.takeIf { it.isNotBlank() } ?: name.trim()
                        if (chosenName.isNotBlank()) {
                            onNameChosen(chosenName)
                        }
                        Toast.makeText(context, "Google connected for cloud backup", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        googleAuthError = "Google was selected, but cloud sign-in couldn't finish. Please try again."
                    }
                } finally {
                    isSigningInGoogle = false
                }
            }
        }.onFailure { e ->
            isSigningInGoogle = false
            googleAuthError = GoogleAuthHelper.userMessage(e)
        }
    }

    if (googleAuthError != null) {
        AlertDialog(
            onDismissRequest = { googleAuthError = null },
            title = { Text("Couldn't connect Google", color = Color.White) },
            text = { Text(googleAuthError.orEmpty(), color = Color.White.copy(alpha = .85f)) },
            confirmButton = {
                TextButton(onClick = { googleAuthError = null }) {
                    Text("OK", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            }
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState > initialState
                val slide = tween<IntOffset>(420)
                (slideInHorizontally(slide) { if (forward) it / 2 else -it / 2 } + fadeIn(tween(320, 80)))
                    .togetherWith(
                        slideOutHorizontally(slide) { if (forward) -it / 2 else it / 2 } + fadeOut(tween(200))
                    )
            },
            label = "onboardingPages",
            modifier = Modifier.fillMaxSize()
        ) { currentPage ->
            when (currentPage) {
                PAGE_NAME -> {
                    NameStepPage(
                        name = name,
                        onNameChange = { name = it.take(NAME_LIMIT) },
                        onContinue = {
                            val chosen = name.trim().ifBlank { "Listener" }
                            focusManager.clearFocus()
                            onNameChosen(chosen)
                            page = PAGE_CONNECT
                        }
                    )
                }
                PAGE_CONNECT -> {
                    ConnectStepPage(
                        name = name.trim().ifBlank { "Listener" },
                        isSigningInGoogle = isSigningInGoogle,
                        hasGoogleAccount = hasGoogleAccount,
                        googleEmail = firebaseUserEmail,
                        signedInYouTube = signedIn,
                        spotifyConnected = spotifyToken.isNotBlank(),
                        onBack = { page = PAGE_NAME },
                        onGoogleSignIn = {
                            if (!isSigningInGoogle && activity != null) {
                                isSigningInGoogle = true
                                googleAuthError = null
                                try {
                                    googleSignInLauncher.launch(GoogleAuthHelper.getSignInIntent(activity))
                                } catch (_: Exception) {
                                    isSigningInGoogle = false
                                    googleAuthError = "Google sign-in couldn't open. Please try again."
                                }
                            }
                        },
                        onSpotifySignIn = onSpotifySignIn,
                        onYouTubeSignIn = onSignIn,
                        onStartListening = onFinish
                    )
                }
            }
        }
    }
}

/**
 * Step 1: "What should we call you?"
 * Matches user reference mockup with pure black background, top-left brand, and angled 3D wave.
 */
@Composable
private fun NameStepPage(
    name: String,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Angled 3D Wave Graphic in the top-right
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(440.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Image(
                painter = androidPainterResource(R.drawable.onboarding_hero_wave),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(300.dp)
                    .height(300.dp)
                    .offset(x = 24.dp, y = 60.dp)
            )
        }

        // Main layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: [Icon] Vikify
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_logo),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Vikify",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(Modifier.weight(1f))

            // Bottom Section: Title + Input Field + Continue Button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = "What should\nwe call you?",
                    style = TextStyle(
                        fontSize = 38.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.2).sp,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // Input Field inside pill container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            shape = CircleShape
                        )
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (name.isEmpty()) {
                        Text(
                            text = "Enter your name",
                            style = TextStyle(
                                fontSize = 18.sp,
                                color = Color.White.copy(alpha = 0.45f),
                                letterSpacing = (-0.2).sp
                            )
                        )
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = onNameChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            letterSpacing = (-0.2).sp
                        ),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onContinue() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Primary Button: Solid White "Continue"
                PillAction(
                    label = "Continue",
                    primary = true,
                    onClick = onContinue
                )
            }
        }
    }
}

/**
 * Keep the destination separate from account actions: signing in must never
 * replace the way into the app with another sign-in button.
 */
@Composable
private fun ConnectStepPage(
    name: String,
    isSigningInGoogle: Boolean,
    hasGoogleAccount: Boolean,
    googleEmail: String?,
    signedInYouTube: Boolean,
    spotifyConnected: Boolean,
    onBack: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSpotifySignIn: () -> Unit,
    onYouTubeSignIn: () -> Unit,
    onStartListening: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(scrollState).padding(bottom = 24.dp),
        ) {
            // Header with Back Button and Brand
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = VikifyIcons.ChevronRight,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { scaleX = -1f }
                    )
                }
                Spacer(Modifier.width(14.dp))
                Icon(
                    painter = painterResource(Res.drawable.ic_logo),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Vikify",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(28.dp))

            // Headline
            Text(
                text = if (hasGoogleAccount) "You're ready\nto listen" else "Your music\nstarts here",
                style = TextStyle(
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.0).sp,
                    color = Color.White
                )
            )

            Spacer(Modifier.height(14.dp))

            // Explanation text
            Text(
                text = if (hasGoogleAccount) {
                    "Hi $name! You're signed in. Start listening now, or connect a music account to bring in your library."
                } else {
                    "Hi $name! Start listening now, or connect your accounts for cloud backup and your music library."
                },
                style = TextStyle(
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            )

            Spacer(Modifier.height(20.dp))

            // Cloud identity and music sessions have separate, stable actions.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cloud backup · Optional",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                )
                PillAction(
                    label = when {
                        isSigningInGoogle -> "Connecting to Google…"
                        hasGoogleAccount -> "Google connected ✓"
                        else -> "Sign in with Google"
                    },
                    primary = false,
                    enabled = !isSigningInGoogle && !hasGoogleAccount,
                    leading = {
                        if (isSigningInGoogle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Image(
                                painter = painterResource(Res.drawable.ic_google_g),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    onClick = onGoogleSignIn,
                )
                Text(
                    text = if (hasGoogleAccount) googleEmail.orEmpty() else "Save and sync your Vikify playlists.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Your music library · Optional",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Use a different account for each service if you like.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            PillAction(
                label = if (signedInYouTube) "YouTube Music · Session saved" else "Connect YouTube Music",
                primary = false,
                enabled = !isSigningInGoogle,
                onClick = onYouTubeSignIn,
            )
            Spacer(Modifier.height(12.dp))
            PillAction(
                label = if (spotifyConnected) "Spotify · Session saved" else "Connect Spotify",
                primary = false,
                enabled = !isSigningInGoogle,
                leading = {
                    Image(
                        painter = painterResource(Res.drawable.spotify_logo),
                        contentDescription = "Spotify",
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = onSpotifySignIn,
            )
        }

        // Pinned outside the scroll area, including on short screens and large text.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PillAction(
                label = "Start listening",
                primary = true,
                enabled = !isSigningInGoogle,
                onClick = onStartListening,
            )
            Text(
                text = "You can connect accounts later in Settings.",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun PillAction(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 900f),
        label = "pillScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        label = "pillAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.10f))
            .then(
                if (!primary) {
                    Modifier.border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (primary) Color.Black else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
