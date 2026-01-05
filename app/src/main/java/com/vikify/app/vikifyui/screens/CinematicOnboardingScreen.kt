
/*
 * Copyright (C) 2026 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.vikify.app.vikifyui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.vikify.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// --- THEME COLORS ---
private val NeonCyan = Color(0xFF00F0FF)
private val DeepPurple = Color(0xFF6200EA) // Electric Purple
private val PureBlack = Color(0xFF000000)

// --- STATE MACHINE ---
enum class OnboardingState {
    PULSE,    // 1. Hold the Orb to Start
    ENGINE,   // 2. 3D Card Carousel
    LAUNCH,   // 3. Auth Selection
    COMPLETE  // 4. Zoom & Exit
}

@Composable
fun CinematicOnboardingScreen(
    onGoogleLogin: () -> Unit,
    onGuestLogin: () -> Unit
) {
    var currentState by remember { mutableStateOf(OnboardingState.PULSE) }
    val view = LocalView.current
    
    // Zoom Transition State (For the "Launch" phase)
    val zoomAnim = remember { Animatable(1f) }
    // Scope for animations triggered by events
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            // Apply infinite zoom effect at the very end
            .graphicsLayer {
                scaleX = zoomAnim.value
                scaleY = zoomAnim.value
                alpha = if (zoomAnim.value > 10f) 0f else 1f
            }
    ) {
        // 1. LIVING BACKGROUND (Nebula)
        LivingBackground()

        // 2. CONTENT SWITCHER
        AnimatedContent(
            targetState = currentState,
            transitionSpec = { 
                fadeIn(tween(800)) togetherWith fadeOut(tween(500)) 
            },
            label = "content"
        ) { state ->
            when (state) {
                OnboardingState.PULSE -> PulseStage(onComplete = { currentState = OnboardingState.ENGINE })
                OnboardingState.ENGINE -> EngineStage(onComplete = { currentState = OnboardingState.LAUNCH })
                OnboardingState.LAUNCH -> LaunchStage(
                    onGoogleClick = {
                        // Trigger zoom, then callback
                        scope.launch {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            zoomAnim.animateTo(
                                targetValue = 50f,
                                animationSpec = tween(1200, easing = CubicBezierEasing(0.95f, 0.05f, 0.795f, 0.035f)) // ExpoIn-like
                            )
                            onGoogleLogin()
                            currentState = OnboardingState.COMPLETE
                        }
                    },
                    onGuestClick = {
                        scope.launch {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            zoomAnim.animateTo(
                                targetValue = 50f,
                                animationSpec = tween(1200, easing = CubicBezierEasing(0.95f, 0.05f, 0.795f, 0.035f))
                            )
                            onGuestLogin()
                            currentState = OnboardingState.COMPLETE
                        }
                    }
                )
                OnboardingState.COMPLETE -> {
                    // Empty state (zoom animation plays on container)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// STAGE 1: THE PULSE (Hold to Start)
// ----------------------------------------------------------------------------
@Composable
fun PulseStage(onComplete: () -> Unit) {
    var holdProgress by remember { mutableFloatStateOf(0f) }
    val view = LocalView.current
    
    // Breathing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "breath"
    )

    // Completion Logic
    LaunchedEffect(holdProgress) {
        if (holdProgress >= 1f) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            onComplete()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Subtle hint
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "VIKIFY",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 28.sp,
                letterSpacing = 8.sp,
                modifier = Modifier.alpha(0.9f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "HOLD TO IGNITE",
                color = NeonCyan.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp
            )
        }

        // The Orb
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(breathScale + (holdProgress * 0.4f)) // Expands as you hold
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val startTime = System.currentTimeMillis()
                            try {
                                // While pressed, increase progress
                                while (true) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    holdProgress = (elapsed / 1200f).coerceAtMost(1f) // 1.2s to fill
                                    if (holdProgress >= 1f) return@detectTapGestures
                                    // Haptic tick
                                    if ((holdProgress * 100).toInt() % 10 == 0) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    delay(16) // ~60fps frame time
                                }
                            } finally {
                                // If released early, decay back quickly
                                if (holdProgress < 1f) {
                                    while (holdProgress > 0) {
                                        holdProgress -= 0.08f
                                        delay(16)
                                    }
                                    holdProgress = 0f
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Inner Core Glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            DeepPurple.copy(alpha = 0.6f + (holdProgress * 0.4f)), 
                            NeonCyan.copy(alpha = 0.2f), 
                            Color.Transparent
                        ),
                        radius = size.width / 2
                    )
                )
                // Progress Ring
                if (holdProgress > 0) {
                    drawCircle(
                        color = NeonCyan.copy(alpha = 0.9f),
                        radius = (size.width / 2) * holdProgress,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx())
                    )
                }
            }
            
            // Logo Image
             Icon(
                painter = painterResource(id = R.drawable.vikify_logo_nobackground),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(140.dp).alpha(0.9f + (holdProgress * 0.1f))
            )
        }
    }
}

// ----------------------------------------------------------------------------
// STAGE 2: THE ENGINE (3D Cards)
// ----------------------------------------------------------------------------
@Composable
fun EngineStage(onComplete: () -> Unit) {
    val items = listOf(
        Triple("Import Spotify", "Your entire library.\nInstantly synced.", Icons.Rounded.CloudDownload),
        Triple("High Fidelity", "Lossless audio engine.\nHear every detail.", Icons.Rounded.GraphicEq),
        Triple("Offline Core", "No internet needed.\nMusic that travels.", Icons.Rounded.WifiOff)
    )
    
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text("SYSTEM CAPABILITIES", color = NeonCyan, letterSpacing = 2.sp, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(0.8f))
        
        Spacer(Modifier.height(40.dp))
        
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 48.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.height(380.dp)
        ) { page ->
            // 3D Rotation Logic
            val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            ).absoluteValue
            
            val rotation = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction) * -10f
            
            EngineCard(
                item = items[page],
                modifier = Modifier
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = lerp(0.85f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                        alpha = lerp(0.5f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
            )
        }
        
        Spacer(Modifier.height(60.dp))
        
        // Navigation / Continue
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicators
            Row {
                repeat(items.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) NeonCyan else Color.White.copy(alpha = 0.2f)
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(if (pagerState.currentPage == iteration) 10.dp else 6.dp)
                    )
                }
            }
            
            IconButton(
                onClick = {
                     if (pagerState.currentPage < items.size - 1) {
                         // Ideally animate scroll, but direct callback for flow speed
                     } 
                     onComplete()
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(0.1f), CircleShape)
            ) {
                Icon(Icons.Rounded.ArrowForward, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun EngineCard(item: Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(0.08f))
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(32.dp))
            .padding(32.dp)
    ) {
        // Shine gradient
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(0.1f), Color.Transparent),
                    center = Offset(0f, 0f),
                    radius = size.width
                )
            )
        }
    
        Column(Modifier.align(Alignment.CenterStart)) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                 Icon(item.third, null, tint = NeonCyan, modifier = Modifier.size(32.dp))
            }
            
            Spacer(Modifier.height(32.dp))
            Text(item.first, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 34.sp)
            Spacer(Modifier.height(16.dp))
            Text(item.second, fontSize = 16.sp, color = Color.White.copy(alpha = 0.6f), lineHeight = 24.sp)
        }
    }
}

// ----------------------------------------------------------------------------
// STAGE 3: THE LAUNCH (Auth Selection)
// ----------------------------------------------------------------------------
@Composable
fun LaunchStage(
    onGoogleClick: () -> Unit,
    onGuestClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.4f))
        
        Text("INITIALIZE", fontSize = 12.sp, color = NeonCyan, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("Connect to\nthe Core.", fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color.White, lineHeight = 52.sp, textAlign = TextAlign.Center)
        
        Spacer(Modifier.weight(0.6f))
        
        // Primary: Google
        Button(
            onClick = onGoogleClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
             Icon(
                painter = painterResource(id = R.drawable.vikify_logo), 
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text("CONTINUE WITH GOOGLE", color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Secondary: Guest
        OutlinedButton(
            onClick = onGuestClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.2f)),
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("CONTINUE AS GUEST", fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
        
         Spacer(Modifier.height(64.dp))
    }
}

// ----------------------------------------------------------------------------
// LIVING BACKGROUND (Animated Mesh Gradient Simulation)
// ----------------------------------------------------------------------------
@Composable
fun LivingBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val shift by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        
        // Deep backing
        drawRect(Color.Black)
        
        // Moving Orbs
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(DeepPurple.copy(alpha = 0.4f), Color.Transparent),
                center = Offset(w * 0.2f, h * 0.3f + (shift * 0.1f)),
                radius = w * 0.8f
            )
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonCyan.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(w * 0.8f, h * 0.7f - (shift * 0.2f)),
                radius = w * 0.6f
            )
        )
    }
}
