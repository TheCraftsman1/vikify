package com.vikify.app.vikifyui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.vikify.app.vikifyui.theme.*

/**
 * Premium Theme Switch with Animation
 * 
 * Features:
 * - Smooth icon transition (Sun spins into Moon)
 * - Color morphing animation
 * - Scale/bounce effect on tap
 * - Haptic feedback
 */
@Composable
fun ThemeSwitch(
    modifier: Modifier = Modifier,
    onThemeChanged: ((Boolean) -> Unit)? = null
) {
    val view = LocalView.current
    val isDark = VikifyTheme.isDark
    
    // Rotation animation - Sun spins to Moon
    val rotation by animateFloatAsState(
        targetValue = if (isDark) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rotation"
    )
    
    // Scale animation - bounce on tap
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    // Background color animation
    val backgroundColor by animateColorAsState(
        targetValue = if (isDark) DarkColors.Surface else LightColors.Surface,
        animationSpec = tween(300),
        label = "bgColor"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isDark) DarkColors.Accent else LightColors.Accent,
        animationSpec = tween(300),
        label = "iconColor"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isDark) DarkColors.Border else LightColors.Border,
        animationSpec = tween(300),
        label = "borderColor"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .size(44.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable {
                isPressed = true
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                VikifyThemeState.toggleTheme()
                onThemeChanged?.invoke(!isDark)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isDark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
            contentDescription = if (isDark) "Dark Mode" else "Light Mode",
            tint = iconColor,
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation)
        )
    }
    
    // Reset pressed state after animation
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

/**
 * Premium Pill Theme Toggle
 * 
 * iOS-style segmented toggle with Sun/Moon icons
 */
@Composable
fun ThemePillToggle(
    modifier: Modifier = Modifier
) {
    val isDark = VikifyTheme.isDark
    val view = LocalView.current
    
    // Slider position animation
    val sliderOffset by animateIntAsState(
        targetValue = if (isDark) 1 else 0,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "sliderOffset"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isDark) DarkColors.SurfaceVariant else LightColors.SurfaceVariant,
        animationSpec = tween(300),
        label = "bgColor"
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .padding(4.dp)
    ) {
        Row {
            // Light Mode Button
            ThemePillButton(
                icon = Icons.Filled.LightMode,
                isSelected = !isDark,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    VikifyThemeState.setMode(ThemeMode.LIGHT)
                }
            )
            
            Spacer(Modifier.width(4.dp))
            
            // Dark Mode Button
            ThemePillButton(
                icon = Icons.Filled.DarkMode,
                isSelected = isDark,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    VikifyThemeState.setMode(ThemeMode.DARK)
                }
            )
        }
    }
}

@Composable
private fun ThemePillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (LocalIsDarkTheme.current) DarkColors.Accent else LightColors.Accent
        } else Color.Transparent,
        animationSpec = tween(200),
        label = "pillBg"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else {
            if (LocalIsDarkTheme.current) DarkColors.TextSecondary else LightColors.TextSecondary
        },
        animationSpec = tween(200),
        label = "pillIcon"
    )
    
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Theme Mode Row for Settings
 * 
 * Shows current theme mode with options to change
 */
@Composable
fun ThemeModeSelector(
    modifier: Modifier = Modifier
) {
    val currentMode = VikifyThemeState.currentMode
    val view = LocalView.current
    val colors = VikifyTheme.colors
    
    Column(modifier = modifier) {
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary
        )
        
        Spacer(Modifier.height(12.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == currentMode
                
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) colors.accent else Color.Transparent,
                    label = "modeBg"
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            VikifyThemeState.setMode(mode)
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (mode) {
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                            ThemeMode.SYSTEM -> "Auto"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) Color.White else colors.textSecondary
                    )
                }
            }
        }
    }
}
