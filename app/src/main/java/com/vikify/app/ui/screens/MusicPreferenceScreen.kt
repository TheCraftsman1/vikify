package com.vikify.app.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.ui.models.MusicLanguage
import com.vikify.app.ui.models.MusicPreferences
import com.vikify.app.ui.theme.VikifyTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Music Preference Selection Screen - Premium Edition
 * 
 * Features:
 * - Glassmorphism design
 * - Animated floating orbs background
 * - Premium language cards with haptic feedback
 * - Smooth staggered animations
 */
@Composable
fun MusicPreferenceScreen(
    onComplete: () -> Unit,
    isSettingsMode: Boolean = false,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val colors = VikifyTheme.colors
    val gridState = rememberLazyGridState()
    
    // Load saved preferences
    val savedLanguages by MusicPreferences.getSelectedLanguages(context)
        .collectAsState(initial = emptySet())

    var selectedLanguages by remember { mutableStateOf<Set<MusicLanguage>>(emptySet()) }
    
    // Initialize state when saved preferences are loaded
    LaunchedEffect(savedLanguages) {
        if (selectedLanguages.isEmpty() && savedLanguages.isNotEmpty()) {
            selectedLanguages = savedLanguages
        }
    }
    
    var isLoading by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }
    
    // Staggered entrance animation
    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }
    
    // Floating orb animations
    val infiniteTransition = rememberInfiniteTransition(label = "orbs")
    val orbOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1"
    )
    val orbOffset2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Floating orbs background
        FloatingOrbsBackground(
            colors = colors,
            orbOffset1 = orbOffset1,
            orbOffset2 = orbOffset2
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar for Settings Mode
            if (isSettingsMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceCard.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // Animated Header
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { -it / 2 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Premium Icon with glow
                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Glow ring
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .blur(20.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            colors.accent.copy(alpha = 0.4f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )
                        
                        // Icon container
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            colors.accent,
                                            colors.accent.copy(alpha = 0.7f)
                                        )
                                    )
                                )
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.3f),
                                            Color.White.copy(alpha = 0.1f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Title
                    Text(
                        text = if (isSettingsMode) "Music Languages" else "Choose Your Vibe",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Subtitle
                    Text(
                        text = "Select languages to personalize\nyour music discovery",
                        fontSize = 15.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    
                    // Selected count badge
                    AnimatedVisibility(
                        visible = selectedLanguages.isNotEmpty(),
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.accent.copy(alpha = 0.15f))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${selectedLanguages.size} selected",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Language Grid with staggered animation
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(600, delayMillis = 200))
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(MusicLanguage.entries.toList()) { language ->
                        PremiumLanguageCard(
                            language = language,
                            isSelected = language in selectedLanguages,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                selectedLanguages = if (language in selectedLanguages) {
                                    selectedLanguages - language
                                } else {
                                    selectedLanguages + language
                                }
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Premium Continue Button
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(600, delayMillis = 400)) + slideInVertically(tween(600, delayMillis = 400)) { it / 2 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            scope.launch {
                                isLoading = true
                                val finalLanguages = selectedLanguages.ifEmpty { setOf(MusicLanguage.ENGLISH) }
                                MusicPreferences.setSelectedLanguages(context, finalLanguages)
                                if (!isSettingsMode) {
                                    MusicPreferences.setOnboardingCompleted(context, true)
                                }
                                delay(200)
                                isLoading = false
                                onComplete()
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = when {
                                    isSettingsMode -> "Save Preferences"
                                    selectedLanguages.isEmpty() -> "Continue with English"
                                    else -> "Let's Go! 🎵"
                                },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (!isSettingsMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "You can change this anytime in settings",
                            fontSize = 13.sp,
                            color = colors.textSecondary.copy(alpha = 0.6f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FLOATING ORBS BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FloatingOrbsBackground(
    colors: com.vikify.app.ui.theme.VikifyColors,
    orbOffset1: Float,
    orbOffset2: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Large accent orb - top right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.accent.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(
                    size.width * (0.8f + orbOffset1 * 0.1f),
                    size.height * 0.15f
                ),
                radius = size.width * 0.6f
            )
        )
        
        // Medium orb - bottom left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.accent.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(
                    size.width * (0.2f - orbOffset2 * 0.1f),
                    size.height * 0.7f
                ),
                radius = size.width * 0.5f
            )
        )
        
        // Small accent orb - center
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    colors.accent.copy(alpha = 0.03f),
                    Color.Transparent
                ),
                center = Offset(
                    size.width * 0.5f,
                    size.height * (0.4f + orbOffset1 * 0.1f)
                ),
                radius = size.width * 0.3f
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PREMIUM LANGUAGE CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PremiumLanguageCard(
    language: MusicLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VikifyTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Animations
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isSelected -> 1.02f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "cardScale"
    )
    
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = tween(200),
        label = "borderWidth"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else colors.border.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent.copy(alpha = 0.12f) else colors.surfaceCard.copy(alpha = 0.8f),
        animationSpec = tween(200),
        label = "bgColor"
    )
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.3f else 0f,
        animationSpec = tween(300),
        label = "glowAlpha"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .scale(scale)
    ) {
        // Glow effect when selected
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = glowAlpha }
                    .blur(12.dp)
                    .background(colors.accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            )
        }
        
        // Card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(backgroundColor)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(18.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Emoji container
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) colors.accent.copy(alpha = 0.2f)
                                else colors.surface
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = language.emoji,
                            fontSize = 22.sp
                        )
                    }
                    
                    // Name
                    Text(
                        text = language.displayName,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) colors.accent else colors.textPrimary
                    )
                }
                
                // Checkmark
                AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn(spring(stiffness = Spring.StiffnessHigh)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
