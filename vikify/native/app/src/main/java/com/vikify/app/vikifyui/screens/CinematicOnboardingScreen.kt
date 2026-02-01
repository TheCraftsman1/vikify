/*
 * Copyright (C) 2026 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Music-First Onboarding Experience
 * Warm, inviting, and focused on the joy of music
 */
package com.vikify.app.vikifyui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.vikify.app.R
import com.vikify.app.vikifyui.data.MusicLanguage
import com.vikify.app.vikifyui.data.MusicPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// ═══════════════════════════════════════════════════════════════════════════════
// MUSIC-FIRST COLOR SYSTEM
// ═══════════════════════════════════════════════════════════════════════════════

private object AppColors {
    // Core palette - matches VikifyTheme COOL mode
    val DeepBlack = Color(0xFF0D1117)      // GitHub-style bluer dark
    val RichBlack = Color(0xFF161B22)      // Card background
    val CardSurface = Color(0xFF1C2128)    // Elevated surface
    val CardSurfaceLight = Color(0xFF21262D)
    
    // Text hierarchy
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8B949E)  // Bluer gray
    val TextMuted = Color(0xFF6E7681)
    
    // Accent colors - cyan/purple ambient glow
    val Cyan = Color(0xFF00D4FF)           // Primary accent
    val Purple = Color(0xFF8B5CF6)         // Secondary accent
    val CyanGlow = Color(0xFF00D4FF).copy(alpha = 0.4f)
    val PurpleGlow = Color(0xFF8B5CF6).copy(alpha = 0.4f)
    
    // Legacy colors (for compatibility)
    val Amber = Color(0xFF00D4FF)          // Remapped to Cyan
    val Coral = Color(0xFF8B5CF6)          // Remapped to Purple
    val Lavender = Color(0xFFA78BFA)
    val Mint = Color(0xFF6EE7B7)
    
    // Brand colors
    val SpotifyGreen = Color(0xFF1DB954)
    val GoogleBlue = Color(0xFF4285F4)
    
    // Ambient gradient for backgrounds
    val warmGradient = listOf(
        DeepBlack,
        Color(0xFF0D1520),  // Slight blue tint
        DeepBlack
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// STATE MANAGEMENT
// ═══════════════════════════════════════════════════════════════════════════════

private enum class OnboardingStep {
    WELCOME,
    FEATURES,
    SIGN_IN,
    NAME_SETUP,         // Added: Set up your name after sign-in
    SPOTIFY_CONNECT,
    LANGUAGE_SETUP,     // Added: Language preferences before full music prefs
    MUSIC_PREFERENCES,
    READY
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN COMPOSABLE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun CinematicOnboardingScreen(
    onGoogleLogin: () -> Unit,
    onGuestLogin: () -> Unit,
    isLoggedIn: Boolean,
    isSpotifyConnected: Boolean = false,
    onSpotifyLogin: () -> Unit,
    onOnboardingComplete: () -> Unit,
    userName: String = "",
    onSaveName: (String) -> Unit = {}
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    
    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var isExiting by remember { mutableStateOf(false) }
    
    // Track if user explicitly clicked login button (prevents auto-advance for pre-logged-in users)
    var userInitiatedLogin by remember { mutableStateOf(false) }
    
    // Final launch sequence
    fun finishOnboarding() {
        scope.launch {
            val hapticType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(hapticType)
            isExiting = true
            delay(500)
            onOnboardingComplete()
        }
    }

    // Handle login success - ONLY advance if user explicitly clicked login button
    // This prevents auto-skipping for users who were already logged in from a previous session
    LaunchedEffect(isLoggedIn, currentStep, userInitiatedLogin) {
        if (isLoggedIn && currentStep == OnboardingStep.SIGN_IN && userInitiatedLogin) {
            delay(600)
            currentStep = OnboardingStep.NAME_SETUP  // Go to name setup after login
        }
    }
    
    // Handle Spotify connect success
    LaunchedEffect(isSpotifyConnected) {
        if (isSpotifyConnected && currentStep == OnboardingStep.SPOTIFY_CONNECT) {
             delay(600)
             currentStep = OnboardingStep.LANGUAGE_SETUP  // Go to language setup
        }
    }
    
    // Navigation helper
    fun goToStep(step: OnboardingStep) {
        scope.launch {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            currentStep = step
        }
    }

    // Exit animation
    val exitAlpha by animateFloatAsState(
        targetValue = if (isExiting) 0f else 1f,
        animationSpec = tween(500),
        label = "exitAlpha"
    )
    
    val exitScale by animateFloatAsState(
        targetValue = if (isExiting) 1.1f else 1f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "exitScale"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DeepBlack)
            .graphicsLayer {
                alpha = exitAlpha
                scaleX = exitScale
                scaleY = exitScale
            }
    ) {
        // Background
        WarmBackground()
        
        // Content
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                fadeIn(tween(400)) + slideInHorizontally(
                    tween(400, easing = FastOutSlowInEasing)
                ) { it / 3 } togetherWith fadeOut(tween(300)) + slideOutHorizontally(
                    tween(300)
                ) { -it / 3 }
            },
            modifier = Modifier.fillMaxSize(),
            label = "stepContent"
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> WelcomeStep(
                    onContinue = { goToStep(OnboardingStep.FEATURES) },
                    onSkip = { goToStep(OnboardingStep.SIGN_IN) }
                )
                OnboardingStep.FEATURES -> FeaturesStep(
                    onContinue = { goToStep(OnboardingStep.SIGN_IN) }
                )
                OnboardingStep.SIGN_IN -> SignInStep(
                    onGoogleClick = {
                        userInitiatedLogin = true
                        onGoogleLogin()
                    },
                    onGuestClick = {
                        userInitiatedLogin = true
                        onGuestLogin()
                        scope.launch {
                            delay(400)
                            goToStep(OnboardingStep.NAME_SETUP)  // Go to name setup after guest login
                        }
                    }
                )
                OnboardingStep.NAME_SETUP -> NameSetupStep(
                    initialName = userName,
                    onSaveName = { name ->
                        onSaveName(name)
                        goToStep(OnboardingStep.SPOTIFY_CONNECT)
                    },
                    onSkip = { goToStep(OnboardingStep.SPOTIFY_CONNECT) }
                )
                OnboardingStep.SPOTIFY_CONNECT -> SpotifyConnectStep(
                    onConnect = onSpotifyLogin,
                    onSkip = { goToStep(OnboardingStep.LANGUAGE_SETUP) }  // Skip to language setup
                )
                OnboardingStep.LANGUAGE_SETUP -> LanguageSetupStep(
                    onContinue = { goToStep(OnboardingStep.MUSIC_PREFERENCES) },
                    onSkip = { goToStep(OnboardingStep.MUSIC_PREFERENCES) }
                )
                OnboardingStep.MUSIC_PREFERENCES -> MusicPreferenceScreen(
                    onComplete = { finishOnboarding() }
                )
                OnboardingStep.READY -> {
                    // Transition state
                }
            }
        }
        
        // Progress dots (except welcome and ready)
        AnimatedVisibility(
            visible = currentStep != OnboardingStep.WELCOME && currentStep != OnboardingStep.READY,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 20.dp)
        ) {
            ProgressDots(
                currentStep = when (currentStep) {
                    OnboardingStep.FEATURES -> 0
                    OnboardingStep.SIGN_IN -> 1
                    OnboardingStep.NAME_SETUP -> 2
                    OnboardingStep.SPOTIFY_CONNECT -> 3
                    OnboardingStep.LANGUAGE_SETUP -> 4
                    OnboardingStep.MUSIC_PREFERENCES -> 5
                    else -> 0
                },
                totalSteps = 6
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun WarmBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_bg")
    
    // Orb A (Cyan): Figure-8 motion - matches LivingPlayerBackground
    val orbAPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_a_phase"
    )
    
    // Orb B (Purple): Circular motion
    val orbBAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_b_angle"
    )
    
    // Orb C (Subtle): Pulsing scale
    val orbCScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_c_scale"
    )
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(80.dp) // Glass-morphism blur
    ) {
        val width = size.width
        val height = size.height
        
        // Base gradient - GitHub-style dark blue
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    AppColors.DeepBlack,
                    Color(0xFF0D1520),
                    AppColors.DeepBlack
                )
            )
        )
        
        // === ORB A (Cyan) - Figure-8 Pattern ===
        val t = Math.toRadians(orbAPhase.toDouble())
        val orbAX = (width * 0.5f) + (kotlin.math.sin(t).toFloat() * width * 0.3f)
        val orbAY = (height * 0.25f) + (kotlin.math.sin(2 * t).toFloat() * height * 0.12f)
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    AppColors.Cyan.copy(alpha = 0.35f),
                    AppColors.Cyan.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(orbAX.toFloat(), orbAY.toFloat()),
                radius = width * 0.45f
            ),
            radius = width * 0.45f,
            center = Offset(orbAX.toFloat(), orbAY.toFloat()),
            blendMode = BlendMode.Screen
        )
        
        // === ORB B (Purple) - Circular Motion at Bottom ===
        val orbBX = (width * 0.5f) + (kotlin.math.cos(Math.toRadians(orbBAngle.toDouble())).toFloat() * width * 0.25f)
        val orbBY = (height * 0.75f) + (kotlin.math.sin(Math.toRadians(orbBAngle.toDouble())).toFloat() * height * 0.1f)
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    AppColors.Purple.copy(alpha = 0.3f),
                    AppColors.Purple.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                center = Offset(orbBX, orbBY),
                radius = width * 0.5f
            ),
            radius = width * 0.5f,
            center = Offset(orbBX, orbBY),
            blendMode = BlendMode.Screen
        )
        
        // === ORB C (Highlight) - Pulsing Center ===
        val orbCRadius = width * 0.2f * orbCScale
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.08f),
                    AppColors.Cyan.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(width * 0.5f, height * 0.4f),
                radius = orbCRadius
            ),
            radius = orbCRadius,
            center = Offset(width * 0.5f, height * 0.4f),
            blendMode = BlendMode.Screen
        )
    }
    
    // Glass Finish Overlay - subtle depth
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.7f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.3f)
                    )
                )
            )
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// PROGRESS INDICATOR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProgressDots(currentStep: Int, totalSteps: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val isPast = index < currentStep
            
            val width by animateDpAsState(
                targetValue = if (isActive) 24.dp else 8.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "dotWidth"
            )
            
            val color by animateColorAsState(
                targetValue = when {
                    isActive -> AppColors.Amber
                    isPast -> AppColors.TextSecondary
                    else -> AppColors.TextMuted.copy(alpha = 0.3f)
                },
                animationSpec = tween(300),
                label = "dotColor"
            )
            
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STEP 1: WELCOME
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun WelcomeStep(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    // Choreographed entrance
    var phase by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) {
        delay(200)
        phase = 1  // Logo
        delay(400)
        phase = 2  // Text
        delay(300)
        phase = 3  // Button
    }
    
    // Auto-advance after 4 second.
    LaunchedEffect(Unit) {
        delay(4500)
        onContinue()
    }
    
    val logoAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(600, easing = EaseOut),
        label = "logoAlpha"
    )
    
    val logoScale by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    
    val textAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(500),
        label = "textAlpha"
    )
    
    val buttonAlpha by animateFloatAsState(
        targetValue = if (phase >= 3) 1f else 0f,
        animationSpec = tween(400),
        label = "buttonAlpha"
    )
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Spacer(Modifier.weight(0.4f))
            
            // Logo
            Box(
                modifier = Modifier
                    .alpha(logoAlpha)
                    .scale(logoScale),
                contentAlignment = Alignment.Center
            ) {
                // Subtle glow behind logo
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .blur(40.dp)
                        .background(
                            AppColors.Amber.copy(alpha = 0.2f),
                            CircleShape
                        )
                )
                
                Icon(
                    painter = painterResource(id = R.drawable.vikify_logo_nobackground),
                    contentDescription = "Vikify",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(100.dp)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Brand name
            Text(
                text = "Vikify",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                letterSpacing = 1.sp,
                modifier = Modifier.alpha(textAlpha)
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Tagline
            Text(
                text = "Your soundtrack, always",
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
                color = AppColors.TextSecondary,
                modifier = Modifier.alpha(textAlpha)
            )
            
            Spacer(Modifier.weight(0.5f))
            
            // Get Started button
            PrimaryButton(
                text = "Get Started",
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(buttonAlpha)
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Skip option
            TextButton(
                onClick = onSkip,
                modifier = Modifier.alpha(buttonAlpha)
            ) {
                Text(
                    text = "Skip intro",
                    color = AppColors.TextMuted,
                    fontSize = 14.sp
                )
            }
            
            Spacer(Modifier.height(48.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STEP 2: FEATURES
// ═══════════════════════════════════════════════════════════════════════════════

private data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val accentColor: Color
)

private val featureItems = listOf(
    FeatureItem(
        icon = Icons.Rounded.Headphones,
        title = "Pick up where\nyou left off",
        description = "Your playlists and favorites, ready and waiting.  Just connect your Spotify.",
        accentColor = AppColors.Cyan  // Primary accent
    ),
    FeatureItem(
        icon = Icons.Rounded.AutoAwesome,
        title = "Sound that\ngives you chills",
        description = "Crystal clear audio that lets you hear every detail the artist intended.",
        accentColor = AppColors.Purple  // Secondary accent
    ),
    FeatureItem(
        icon = Icons.Rounded.Smartphone,
        title = "Music for\neverywhere",
        description = "On a plane, in the subway, off the grid.  Your music never stops.",
        accentColor = AppColors.Cyan  // Alternate
    ),
    FeatureItem(
        icon = Icons.Rounded.Favorite,
        title = "We get\nyour taste",
        description = "Smart recommendations that actually understand what you love.",
        accentColor = AppColors.Lavender  // Purple variant
    )
)

@Composable
private fun FeaturesStep(onContinue: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { featureItems.size })
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Feature pager
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            pageSpacing = 24.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            ).absoluteValue.coerceIn(0f, 1f)
            
            FeatureCard(
                feature = featureItems[page],
                pageOffset = pageOffset
            )
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Page indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(featureItems.size) { index ->
                val isSelected = pagerState.currentPage == index
                
                val size by animateDpAsState(
                    targetValue = if (isSelected) 10.dp else 8.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "indicatorSize"
                )
                
                val color by animateColorAsState(
                    targetValue = if (isSelected) 
                        featureItems[index].accentColor 
                    else 
                        AppColors.TextMuted.copy(alpha = 0.3f),
                    animationSpec = tween(300),
                    label = "indicatorColor"
                )
                
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(color)
                        .clickable {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                )
            }
        }
        
        Spacer(Modifier.height(40.dp))
        
        // Continue button
        PrimaryButton(
            text = if (pagerState.currentPage == featureItems.size - 1) 
                "Let's Go" 
            else 
                "Continue",
            onClick = {
                if (pagerState.currentPage < featureItems.size - 1) {
                    scope.launch {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                } else {
                    onContinue()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Skip
        TextButton(onClick = onContinue) {
            Text(
                text = "Skip",
                color = AppColors.TextMuted,
                fontSize = 14.sp
            )
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FeatureCard(
    feature: FeatureItem,
    pageOffset: Float
) {
    val scale = lerp(0.9f, 1f, 1f - pageOffset)
    val alpha = lerp(0.5f, 1f, 1f - pageOffset)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(28.dp))
            .background(AppColors.CardSurface)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(28.dp)
            )
    ) {
        // Accent glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        feature.accentColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = size.width * 0.6f
                )
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = feature.accentColor,
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(Modifier.height(28.dp))
            
            // Title
            Text(
                text = feature.title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                lineHeight = 40.sp
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Description
            Text(
                text = feature.description,
                fontSize = 16.sp,
                color = AppColors.TextSecondary,
                lineHeight = 24.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STEP 3: SIGN IN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SignInStep(
    onGoogleClick: () -> Unit,
    onGuestClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible && !isLoading) 1f else if (isLoading) 0.5f else 0f,
        animationSpec = tween(500),
        label = "contentAlpha"
    )
    
    val contentOffset by animateDpAsState(
        targetValue = if (visible) 0.dp else 30.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "contentOffset"
    )
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .offset(y = contentOffset)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.35f))
            
            // Waving hand
            Icon(
                imageVector = Icons.Rounded.WavingHand, // Use standard icon
                contentDescription = "Hello",
                tint = AppColors.Cyan,
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Headline
            Text(
                text = "Let's get you\nlistening",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 44.sp
            )
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = "Sign in to save your music and\nsync across devices",
                fontSize = 16.sp,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            
            Spacer(Modifier.weight(0.4f))
            
            // Google Sign In
            SocialButton(
                text = "Continue with Google",
                icon = Icons.Rounded.Email, // Replace with Google icon in production
                backgroundColor = Color.White,
                contentColor = Color.Black,
                onClick = {
                    if (!isLoading) {
                        isLoading = true
                        onGoogleClick()
                    }
                }
            )
            
            Spacer(Modifier.height(12.dp))
            
            // Divider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AppColors.TextMuted.copy(alpha = 0.2f))
                )
                Text(
                    text = "or",
                    color = AppColors.TextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AppColors.TextMuted.copy(alpha = 0.2f))
                )
            }
            
            Spacer(Modifier.height(4.dp))
            
            // Guest button
            SecondaryButton(
                text = "Continue as Guest",
                onClick = {
                    if (!isLoading) {
                        isLoading = true
                        onGuestClick()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(32.dp))
            
            // Terms
            Text(
                text = "By continuing, you agree to our Terms of Service and Privacy Policy",
                fontSize = 12.sp,
                color = AppColors.TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(Modifier.height(48.dp))
        }
        
        // Loading Spinner Overlay
        if (isLoading) {
            CircularProgressIndicator(
                color = AppColors.Cyan,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STEP 4: SPOTIFY CONNECT
// ═══════════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════════
// STEP 3.5: NAME SETUP - Right after sign-in
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NameSetupStep(
    initialName: String,
    onSaveName: (String) -> Unit,
    onSkip: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initialName) }
    val view = LocalView.current
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600),
        label = "alpha"
    )
    
    val contentOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "offset"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp)
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Greeting emoji
        Text(
            text = "👋",
            fontSize = 64.sp,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // Title
        Text(
            text = "Hello there!",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "What should we call you?",
            fontSize = 18.sp,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(40.dp))
        
        // Name input field with glassmorphism style
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.CardSurface.copy(alpha = 0.6f))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            AppColors.Cyan.copy(alpha = 0.3f),
                            AppColors.Purple.copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        if (name.isEmpty()) {
                            Text(
                                text = "Your name",
                                color = AppColors.TextMuted,
                                fontSize = 18.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        
        Spacer(Modifier.height(40.dp))
        
        // Continue button
        PrimaryButton(
            text = "Continue",
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                onSaveName(name.ifEmpty { "Music Lover" })
            },
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Skip option
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSkip() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Skip for now",
                color = AppColors.TextMuted,
                fontSize = 15.sp
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = AppColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SpotifyConnectStep(
    onConnect: () -> Unit,
    onSkip: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "contentAlpha"
    )
    
    // Subtle pulse animation for Spotify icon
    val infiniteTransition = rememberInfiniteTransition(label = "spotifyPulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .alpha(contentAlpha),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.3f))
        
        // Spotify icon with glow
        Box(contentAlignment = Alignment.Center) {
            // Glow
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(iconScale)
                    .blur(30.dp)
                    .background(
                        AppColors.SpotifyGreen.copy(alpha = 0.4f),
                        CircleShape
                    )
            )
            
            // Icon container
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(iconScale)
                    .clip(CircleShape)
                    .background(AppColors.SpotifyGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        
        Spacer(Modifier.height(40.dp))
        
        // Headline
        Text(
            text = "Bring your\nmusic with you",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "Connect Spotify to import your playlists,\nliked songs, and listening history",
            fontSize = 16.sp,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        
        Spacer(Modifier.weight(0.4f))
        
        // Connect Spotify button
        SocialButton(
            text = "Connect Spotify",
            icon = Icons.Rounded.LibraryMusic,
            backgroundColor = AppColors.SpotifyGreen,
            contentColor = Color.Black,
            onClick = onConnect
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Skip
        TextButton(onClick = onSkip) {
            Text(
                text = "Maybe later",
                color = AppColors.TextMuted,
                fontSize = 16.sp
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = AppColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(Modifier.height(48.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STEP 5: LANGUAGE SETUP - Pick your music language preferences
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LanguageSetupStep(
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    
    var visible by remember { mutableStateOf(false) }
    var selectedLanguages by remember { mutableStateOf(setOf<MusicLanguage>()) }
    
    // Available languages with emojis
    val languages = MusicLanguage.values().toList()
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600),
        label = "alpha"
    )
    
    val contentOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "offset"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            
            // Title with music note emoji
            Text(
                text = "🎵",
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Text(
                text = "What languages do you\nlisten to?",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )
            
            Spacer(Modifier.height(6.dp))
            
            Text(
                text = "We'll personalize your feed",
                fontSize = 15.sp,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Language grid with proper sizing
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.weight(1f)
            ) {
                androidx.compose.foundation.lazy.grid.items(
                    items = languages,
                    key = { it.name }
                ) { language ->
                    val isSelected = language in selectedLanguages
                    
                    OnboardingLanguageCard(
                        language = language,
                        isSelected = isSelected,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedLanguages = if (isSelected) {
                                selectedLanguages - language
                            } else {
                                selectedLanguages + language
                            }
                        }
                    )
                }
            }
            
            // Bottom section with buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                // Save and continue button
                PrimaryButton(
                    text = if (selectedLanguages.isEmpty()) "Continue" else "Continue (${selectedLanguages.size} selected)",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        // Save language preferences
                        scope.launch {
                            MusicPreferences.getInstance(context).setMusicLanguages(selectedLanguages.toList())
                        }
                        onContinue()
                    },
                    modifier = Modifier.fillMaxWidth(0.85f)
                )
                
                Spacer(Modifier.height(12.dp))
                
                // Skip option
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSkip() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Skip for now",
                        color = AppColors.TextMuted,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingLanguageCard(
    language: MusicLanguage,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)  // Proper card aspect ratio
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) {
                    Brush.linearGradient(
                        colors = listOf(
                            AppColors.Cyan.copy(alpha = 0.2f),
                            AppColors.Purple.copy(alpha = 0.15f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            AppColors.CardSurface,
                            AppColors.CardSurfaceLight
                        )
                    )
                }
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                brush = if (isSelected) {
                    Brush.linearGradient(
                        colors = listOf(AppColors.Cyan, AppColors.Purple)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.04f)
                        )
                    )
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = language.emoji,
                fontSize = 32.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = language.displayName,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) AppColors.Cyan else AppColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        
        // Selection checkmark
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AppColors.Cyan, AppColors.Purple)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonScale"
    )
    
    Box(
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        AppColors.Cyan,
                        AppColors.Purple.copy(alpha = 0.8f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White  // White text on cyan/purple gradient
        )
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonScale"
    )
    
    Box(
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = AppColors.TextMuted.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary
        )
    }
}

@Composable
private fun SocialButton(
    text: String,
    icon: ImageVector,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonScale"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
