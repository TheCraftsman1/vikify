package com.vikify.app.vikifyui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.data.MusicLanguage
import com.vikify.app.vikifyui.data.MusicPreferences
import com.vikify.app.vikifyui.theme.VikifyTheme
import kotlinx.coroutines.launch

/**
 * Music Preference Selection Screen
 * 
 * Shows during onboarding to let users pick their preferred music languages.
 * Premium animated chip selection UI.
 */
@Composable
fun MusicPreferenceScreen(
    onComplete: () -> Unit,
    isSettingsMode: Boolean = false,
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = VikifyTheme.colors
    
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
    
    // Animation for header
    val infiniteTransition = rememberInfiniteTransition(label = "header")
    val headerGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headerGlow"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar for Settings Mode
            if (isSettingsMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
            
            // Header Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.accent.copy(alpha = headerGlow),
                                colors.accent.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title
            Text(
                text = if (isSettingsMode) "Music Languages" else "What do you like to listen?",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "Pick your preferred music languages.\nWe'll personalize your home feed.",
                fontSize = 16.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Language Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(MusicLanguage.entries.toList()) { language ->
                    LanguageChip(
                        language = language,
                        isSelected = language in selectedLanguages,
                        onClick = {
                            selectedLanguages = if (language in selectedLanguages) {
                                selectedLanguages - language
                            } else {
                                selectedLanguages + language
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Continue/Save Button
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        // Save preferences
                        val finalLanguages = selectedLanguages.ifEmpty { setOf(MusicLanguage.ENGLISH) }
                        MusicPreferences.setSelectedLanguages(context, finalLanguages)
                        // Mark onboarding complete only if not in settings mode (though harmless to set again)
                        if (!isSettingsMode) {
                            MusicPreferences.setOnboardingCompleted(context, true)
                        }
                        isLoading = false
                        onComplete()
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isSettingsMode) "Save Preferences" else (if (selectedLanguages.isEmpty()) "Skip for now" else "Continue"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Hint
            if (!isSettingsMode) {
                Text(
                    text = "You can change this later in Settings",
                    fontSize = 14.sp,
                    color = colors.textSecondary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LanguageChip(
    language: MusicLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VikifyTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    
    // Selection animation
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "chipScale"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent else colors.border,
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) colors.accent.copy(alpha = 0.15f) else colors.surfaceCard,
        animationSpec = tween(200),
        label = "bgColor"
    )
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Emoji
                Text(
                    text = language.emoji,
                    fontSize = 24.sp
                )
                
                // Name
                Text(
                    text = language.displayName,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) colors.accent else colors.textPrimary
                )
            }
            
            // Checkmark
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
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
