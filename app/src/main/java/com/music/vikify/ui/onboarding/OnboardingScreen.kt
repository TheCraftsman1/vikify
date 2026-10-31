package com.music.vikify.ui.onboarding

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.vikify.R
import com.music.vikify.auth.GoogleAuthHelper
import com.music.vikify.data.firebase.FirebaseSyncManager
import com.music.vikify.sharedui.resources.Res
import com.music.vikify.sharedui.resources.ic_google_g
import com.music.vikify.sharedui.resources.ic_logo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private const val TAG = "OnboardingScreen"
private const val NAME_LIMIT = 40

/**
 * Redesigned Onboarding Screen matching the reference mockup:
 * - Solid black minimalist aesthetic
 * - Top-left brand: Vikify wave logo mark + bold "Vikify" title
 * - Angled 3D glossy wave hero graphic emerging from the top right
 * - Bold headline: "What should we call you?"
 * - Pill text field: "Enter your name"
 * - Primary solid white pill button: "Continue"
 * - 1-Tap Google Sign-In with native device account discovery
 */
@Composable
fun OnboardingScreen(
    signedIn: Boolean,
    initialName: String,
    onNameChosen: (String) -> Unit,
    onSignIn: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var isSigningInGoogle by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? Activity

    // Native Google Account Picker launcher (discovers existing Google accounts on device)
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val parsed = GoogleAuthHelper.parseSignInResult(result.data)
        parsed.onSuccess { account ->
            val idToken = account.idToken
            val email = account.email
            val displayName = account.displayName

            if (idToken != null) {
                coroutineScope.launch {
                    isSigningInGoogle = true
                    try {
                        FirebaseSyncManager.signInWithGoogleCredential(
                            idToken = idToken,
                            email = email,
                            displayName = displayName
                        )
                        val chosenName = displayName?.takeIf { it.isNotBlank() } ?: name.trim()
                        if (chosenName.isNotBlank()) {
                            onNameChosen(chosenName)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Firebase credential sync error: ${e.message}")
                    } finally {
                        isSigningInGoogle = false
                        onFinish()
                    }
                }
            } else {
                Log.w(TAG, "No ID token received from Google Sign-In")
            }
        }.onFailure { e ->
            Log.w(TAG, "Google Account picker cancelled or failed: ${e.message}")
            isSigningInGoogle = false
        }
    }

    val submitContinue = {
        val chosen = name.trim().ifBlank { "Listener" }
        focusManager.clearFocus()
        onNameChosen(chosen)
        onFinish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            }
    ) {
        // --- 1. Top-Right Angled 3D Wave Graphic ---
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

        // --- 2. Main Content Column ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header: [Icon] Vikify
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

            // Lower Section: Title + Input Field + Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
                    .padding(bottom = 36.dp)
            ) {
                // Headline: "What should we call you?"
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

                // Input Field: "Enter your name" inside a pill outline
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
                        onValueChange = { name = it.take(NAME_LIMIT) },
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
                        keyboardActions = KeyboardActions(onDone = { submitContinue() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Primary Button: Solid White "Continue" Pill
                PillAction(
                    label = "Continue",
                    primary = true,
                    onClick = submitContinue
                )

                Spacer(Modifier.height(14.dp))

                // Secondary Button: Google Sign-In with device account picker
                PillAction(
                    label = if (isSigningInGoogle) "Connecting..." else "Continue with Google",
                    primary = false,
                    enabled = !isSigningInGoogle,
                    leading = {
                        if (isSigningInGoogle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Image(
                                painter = painterResource(Res.drawable.ic_google_g),
                                contentDescription = "Google",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    onClick = {
                        if (activity != null) {
                            val intent = GoogleAuthHelper.getSignInIntent(activity)
                            googleSignInLauncher.launch(intent)
                        }
                    }
                )
            }
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
            .height(58.dp)
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
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (primary) Color.Black else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
