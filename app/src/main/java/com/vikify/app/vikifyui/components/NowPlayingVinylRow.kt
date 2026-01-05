package com.vikify.app.vikifyui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.ImageLoader
import coil3.BitmapImage
import com.vikify.app.models.MediaMetadata
import com.vikify.app.vikifyui.theme.VikifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * PREMIUM NOW PLAYING HERO CARD
 * 
 * Features:
 * - Dynamic glow from artwork dominant color (Palette)
 * - High-contrast visible vinyl with glowing grooves
 * - Clean text with shadows for readability
 * - Spinning vinyl animation on play
 */
@Composable
fun NowPlayingVinylRow(
    song: MediaMetadata,
    isPlaying: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // ═══════════════════════════════════════════════════════════════
    // DYNAMIC COLOR EXTRACTION FROM ARTWORK
    // ═══════════════════════════════════════════════════════════════
    var dominantColor by remember { mutableStateOf(Color(0xFF6366F1)) } // Default indigo
    var vibrantColor by remember { mutableStateOf(Color(0xFFEC4899)) }  // Default pink
    
    LaunchedEffect(song.thumbnailUrl) {
        if (song.thumbnailUrl != null) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(song.thumbnailUrl)
                        .allowHardware(false)
                        .build()
                    val result = ImageLoader(context).execute(request)
                    val bitmap = (result.image as? BitmapImage)?.bitmap
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        // Get vibrant colors for the glow
                        palette.vibrantSwatch?.rgb?.let { vibrantColor = Color(it) }
                        palette.dominantSwatch?.rgb?.let { dominantColor = Color(it) }
                        // Fallback to muted if no vibrant
                        if (palette.vibrantSwatch == null) {
                            palette.mutedSwatch?.rgb?.let { vibrantColor = Color(it) }
                        }
                    }
                } catch (e: Exception) { /* Keep defaults */ }
            }
        }
    }
    
    // Smooth color transitions
    val animatedVibrant by animateColorAsState(
        targetValue = vibrantColor,
        animationSpec = tween(800),
        label = "vibrantColor"
    )
    val animatedDominant by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(800),
        label = "dominantColor"
    )
    
    // ═══════════════════════════════════════════════════════════════
    // VINYL ROTATION ANIMATION
    // ═══════════════════════════════════════════════════════════════
    val rotationAnim = remember { Animatable(0f) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                rotationAnim.animateTo(
                    targetValue = rotationAnim.value + 360f,
                    animationSpec = tween(3000, easing = LinearEasing)
                )
            }
        } else {
            rotationAnim.stop()
        }
    }
    
    // ═══════════════════════════════════════════════════════════════
    // MAIN LAYOUT
    // ═══════════════════════════════════════════════════════════════
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .shadow(
                elevation = 32.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = animatedVibrant.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        animatedDominant.copy(alpha = 0.9f),  // Deep color start
                        animatedDominant.copy(alpha = 0.3f),  // Mid fade
                        Color(0xFF060606)                     // Deep dark finish
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 1000f)
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.25f),
                        animatedVibrant.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        // Advanced Atmosphere: Multi-layered light leaks
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(60.dp)
                .drawBehind {
                    drawCircle(
                        color = animatedVibrant.copy(alpha = 0.15f),
                        radius = size.minDimension,
                        center = Offset(0f, 0f)
                    )
                    drawCircle(
                        color = animatedDominant.copy(alpha = 0.1f),
                        radius = size.minDimension * 0.8f,
                        center = Offset(size.width, size.height)
                    )
                }
        )
        
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ───────────────────────────────────────────────────────
            // LEFT SIDE: Text Info (Clean, readable)
            // ───────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .padding(end = 12.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // "NOW PLAYING" label - Ultra clean
                Text(
                    text = "NOW PLAYING",
                    style = TextStyle(
                        color = animatedVibrant.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.5.sp,
                    ),
                    modifier = Modifier.graphicsLayer {
                        alpha = 0.8f
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Song Title - Immersive bold
                Text(
                    text = song.title,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 28.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.4f),
                            offset = Offset(0f, 4f),
                            blurRadius = 8f
                        )
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                // Artist
                if (song.artists.isNotEmpty()) {
                    Text(
                        text = song.artists.joinToString(", ") { it.name }.uppercase(),
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // ───────────────────────────────────────────────────────
            // RIGHT SIDE: Album Art + Vinyl
            // ───────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                // << THE PREMIUM VINYL (Behind, spins) >>
                PremiumVinyl(
                    imageUrl = song.thumbnailUrl,
                    vinylSize = 110.dp,
                    rotation = rotationAnim.value,
                    glowColor = animatedVibrant,
                    modifier = Modifier.offset(x = 45.dp)
                )
                
                // << THE ALBUM ART (Front) >>
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .graphicsLayer {
                            // Subtle 3D tilt
                            rotationY = -5f
                            cameraDistance = 12f * density
                        }
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(12.dp),
                            spotColor = animatedDominant.copy(alpha = 0.6f)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                ) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
