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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.vikify.app.vikifyui.theme.*

/**
 * Premium 3-Mode Theme Switch
 * 
 * ☀️ Sunlight = Light/White
 * 🌙 Moon = Dark/Black  
 * ✨ Cool = Mesh/Gradient
 */
@Composable
fun ThemeSwitch(
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val currentMode = VikifyThemeState.currentMode
    val colors = VikifyTheme.colors
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceCard.copy(alpha = 0.5f))
            .border(1.dp, colors.border.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Sunlight (Light)
        ThemeModeButton(
            icon = Icons.Filled.LightMode,
            isSelected = currentMode == ThemeMode.SUNLIGHT,
            selectedColor = Color(0xFFFFA726), // Warm orange
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                VikifyThemeState.setMode(ThemeMode.SUNLIGHT)
            }
        )
        
        // Moon (Dark)
        ThemeModeButton(
            icon = Icons.Filled.DarkMode,
            isSelected = currentMode == ThemeMode.MOON,
            selectedColor = Color(0xFF7C4DFF), // Purple
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                VikifyThemeState.setMode(ThemeMode.MOON)
            }
        )
        
        // Cool (Mesh/Gradient)
        ThemeModeButton(
            icon = Icons.Rounded.AutoAwesome,
            isSelected = currentMode == ThemeMode.COOL,
            selectedColor = Color(0xFF00BCD4), // Cyan
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                VikifyThemeState.setMode(ThemeMode.COOL)
            }
        )
    }
}

@Composable
private fun ThemeModeButton(
    icon: ImageVector,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else Color.Transparent,
        animationSpec = tween(200),
        label = "bg"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else VikifyTheme.colors.textSecondary,
        animationSpec = tween(200),
        label = "icon"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(36.dp)
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
 * Theme Mode Selector for Settings
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
                .background(colors.surfaceCard)
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == currentMode
                
                val (icon, label, selectedColor) = when (mode) {
                    ThemeMode.SUNLIGHT -> Triple(Icons.Filled.LightMode, "Sunlight", Color(0xFFFFA726))
                    ThemeMode.MOON -> Triple(Icons.Filled.DarkMode, "Moon", Color(0xFF7C4DFF))
                    ThemeMode.COOL -> Triple(Icons.Rounded.AutoAwesome, "Cool", Color(0xFF00BCD4))
                }
                
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) selectedColor else Color.Transparent,
                    label = "modeBg"
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            VikifyThemeState.setMode(mode)
                        }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color.White else colors.textSecondary
                    )
                }
            }
        }
    }
}
