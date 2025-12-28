
package com.vikify.app.vikifyui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.R
import kotlinx.coroutines.delay

/**
 * Premium Onboarding Screen for Vikify
 * 
 * Flow:
 * 1. Intro + Auth (Google / Guest)
 * 2. Setup Library (Import Spotify / Start Fresh) - Only shown after auth
 */

// Vikify brand colors
private val VikifyPurple = Color(0xFF8B5CF6)
private val VikifyBlue = Color(0xFF3B82F6)
private val VikifyPink = Color(0xFFEC4899)
private val VikifyDark = Color(0xFF0F0F1A)
private val SpottedGreen = Color(0xFF1DB954)

enum class OnboardingStep {
    AUTH,
    USERNAME,
    SETUP
}

@Composable
fun OnboardingScreen(
    onGoogleLogin: () -> Unit,
    onGuestLogin: () -> Unit,
    onUsernameSet: (String) -> Unit,
    onSetupComplete: (importSpotify: Boolean) -> Unit,
    onSkip: () -> Unit, // Legacy skip, maps to guest login usually
    modifier: Modifier = Modifier,
    isAuthenticated: Boolean = false
) {
    // If authenticated, jump to username step if not set, else setup? 
    // For now, simplify: if authenticated, assume we need to check username or go to setup.
    // User requested explicit flow: Auth -> Username -> Spotify.
    
    var currentStep by remember { mutableStateOf(OnboardingStep.AUTH) }
    
    // Logic: If already authenticated externally (e.g. Google return), move to Username step
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated && currentStep == OnboardingStep.AUTH) {
            currentStep = OnboardingStep.USERNAME
        }
    }
    
    var showContent by remember { mutableStateOf(false) }
    
    // Animate in content
    LaunchedEffect(Unit) {
        delay(300)
        showContent = true
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VikifyDark)
    ) {
        // Animated gradient background
        AnimatedGradientBackground()
        
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))
            
            // Logo and branding - Always visible but animates slightly
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(800)) + scaleIn(tween(800), initialScale = 0.8f)
            ) {
                VikifyLogo(compact = currentStep == OnboardingStep.SETUP)
            }
            
            Spacer(Modifier.weight(1f))
            
            // Page content with transition
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(600)) + slideInHorizontally { width -> width } togetherWith
                    fadeOut(animationSpec = tween(600)) + slideOutHorizontally { width -> -width }
                },
                label = "onboarding_step"
            ) { step ->
                when (step) {
                    OnboardingStep.AUTH -> AuthStep(
                        onGoogleLogin = onGoogleLogin,
                        onGuestLogin = {
                            // Guest login moves to Username step immediately
                            currentStep = OnboardingStep.USERNAME
                            onGuestLogin() 
                        }
                    )
                    OnboardingStep.USERNAME -> UsernameInputStep(
                        onContinue = { username ->
                            onUsernameSet(username)
                            currentStep = OnboardingStep.SETUP
                        }
                    )
                    OnboardingStep.SETUP -> SetupStep(
                        onImportSpotify = { onSetupComplete(true) },
                        onStartFresh = { onSetupComplete(false) }
                    )
                }
            }
            
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AuthStep(
    onGoogleLogin: () -> Unit,
    onGuestLogin: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Text(
            text = "Welcome to Vikify",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "The ultimate music experience.\nAd-free. Unlimited. Yours.",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        
        Spacer(Modifier.height(48.dp))
        
        // 1. Google Login (Primary)
        Button(
            onClick = onGoogleLogin,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground), // Fallback to launcher if no google icon
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Continue with Google",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // 2. Guest Login (Secondary)
        OutlinedButton(
            onClick = onGuestLogin,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White 
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Continue as Guest",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "By continuing, you agree to our Terms & Privacy Policy.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SetupStep(
    onImportSpotify: () -> Unit,
    onStartFresh: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Text(
            text = "Set up your Library",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "How would you like to start?",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(40.dp))
        
        // Option 1: Spotify Import
        Button(
            onClick = onImportSpotify,
            colors = ButtonDefaults.buttonColors(
                containerColor = SpottedGreen
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Import from Spotify",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Playlists & Liked Songs",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Option 2: Fresh Start
        OutlinedButton(
            onClick = onStartFresh,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White 
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Start Fresh",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AnimatedGradientBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1"
    )
    
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset2"
    )
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(100.dp)
            .alpha(0.6f)
    ) {
        // Purple blob
        drawCircle(
            color = VikifyPurple.copy(alpha = 0.4f),
            radius = size.width * 0.5f,
            center = Offset(
                x = size.width * (0.2f + offset1 * 0.3f),
                y = size.height * (0.15f + offset1 * 0.2f)
            ),
            style = Fill
        )
        
        // Blue blob
        drawCircle(
            color = VikifyBlue.copy(alpha = 0.3f),
            radius = size.width * 0.4f,
            center = Offset(
                x = size.width * (0.8f - offset2 * 0.3f),
                y = size.height * (0.6f + offset2 * 0.15f)
            ),
            style = Fill
        )
        
        // Pink blob
        drawCircle(
            color = VikifyPink.copy(alpha = 0.25f),
            radius = size.width * 0.35f,
            center = Offset(
                x = size.width * (0.3f + offset2 * 0.4f),
                y = size.height * (0.85f - offset1 * 0.1f)
            ),
            style = Fill
        )
    }
}

@Composable
private fun UsernameInputStep(
    onContinue: (String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 32.dp)
    ) {
        Text(
            text = "Pick a Username",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "This is how you'll appear on Vikify.",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(40.dp))
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = VikifyPurple,
                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                focusedLabelColor = VikifyPurple,
                unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                cursorColor = VikifyPurple
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = { onContinue(username.ifBlank { "Guest" }) },
            enabled = username.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = VikifyPurple
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Continue",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun VikifyLogo(compact: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val logoSize = if (compact) 100.dp else 140.dp
    val fontSize = if (compact) 32.sp else 42.sp
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo container with glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.scale(scale)
        ) {
            // Glow effect
            Box(
                modifier = Modifier
                    .size(logoSize * 1.3f)
                    .blur(40.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                VikifyPurple.copy(alpha = 0.5f),
                                VikifyBlue.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
            
            // Logo - using the Vikify logo PNG
            Icon(
                painter = painterResource(id = R.drawable.vikify_logo),
                contentDescription = "Vikify",
                tint = Color.Unspecified,
                modifier = Modifier.size(logoSize)
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Brand name
        Text(
            text = "Vikify",
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = (-1).sp
        )
        
        if (!compact) {
            Text(
                text = "Music, Reimagined",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f),
                letterSpacing = 2.sp
            )
        }
    }
}
