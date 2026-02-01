package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vikify.app.vikifyui.theme.CyberpunkJamColors
import com.vikify.app.vikifyui.theme.JamModeColors

/**
 * JAM PARTICIPANT AVATAR
 * 
 * Displays a user in the Jam session with:
 * - Circular profile picture
 * - Animated glow ring (pulsing effect)
 * - Host badge (star icon)
 * - Display name label
 * - "Listening" indicator (optional audio wave)
 * 
 * @param user The participant data
 * @param isHost Whether this user is the session host
 * @param isCurrentUser Whether this is "me" (shows at top of screen)
 * @param isListening Whether user is currently receiving audio
 * @param glowColor Color for the animated ring
 * @param colors Full Jam mode color scheme
 */
@Composable
fun JamParticipantAvatar(
    user: JamUser,
    isHost: Boolean = false,
    isCurrentUser: Boolean = false,
    isListening: Boolean = true,
    glowColor: Color = CyberpunkJamColors.userRingGlow,
    colors: JamModeColors = CyberpunkJamColors,
    modifier: Modifier = Modifier
) {
    // ═══════════════════════════════════════════════════════════════════
    // GLOW ANIMATION
    // ═══════════════════════════════════════════════════════════════════
    
    val infiniteTransition = rememberInfiniteTransition(label = "avatarGlow")
    
    // Pulsing glow effect
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    // ═══════════════════════════════════════════════════════════════════
    // RENDER
    // ═══════════════════════════════════════════════════════════════════
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Avatar with glow ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(100.dp)
        ) {
            // LAYER 1: Outer glow (blurred, scaled)
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .graphicsLayer {
                        scaleX = glowScale
                        scaleY = glowScale
                        alpha = glowAlpha
                    }
                    .blur(8.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor,
                                glowColor.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            
            // LAYER 2: Ring border
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .border(
                        width = 3.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                glowColor,
                                glowColor.copy(alpha = 0.5f),
                                glowColor,
                                glowColor.copy(alpha = 0.5f),
                                glowColor
                            )
                        ),
                        shape = CircleShape
                    )
            )
            
            // LAYER 3: Avatar image
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(colors.backgroundSecondary)
            ) {
                if (user.avatarUrl != null) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = "${user.displayName}'s avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Default avatar - first letter of name
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        glowColor.copy(alpha = 0.3f),
                                        colors.backgroundTertiary
                                    )
                                )
                            )
                    ) {
                        Text(
                            text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                            color = colors.textPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // LAYER 4: Host badge (star icon)
            if (isHost) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(24.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(colors.hostBadge)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Host",
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            // LAYER 5: "You" indicator for current user
            if (isCurrentUser) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 8.dp)
                        .background(
                            color = colors.codeBackground,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "YOU",
                        color = glowColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Display name
        Text(
            text = user.displayName,
            color = colors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp)
        )
        
        // Listening indicator (audio wave dots)
        if (isListening) {
            Spacer(modifier = Modifier.height(4.dp))
            ListeningIndicator(
                color = glowColor,
                modifier = Modifier.height(16.dp)
            )
        }
    }
}

/**
 * Animated "listening" indicator (3 bouncing dots)
 */
@Composable
private fun ListeningIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "listening")
    
    // Staggered bounce animations for each dot
    val dot1Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    
    val dot2Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing, delayMillis = 100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    
    val dot3Scale by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing, delayMillis = 200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        listOf(dot1Scale, dot2Scale, dot3Scale).forEach { scale ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer { 
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(
                        color = color.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Data class for Jam session participant
 */
data class JamUser(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val isHost: Boolean = false
)
