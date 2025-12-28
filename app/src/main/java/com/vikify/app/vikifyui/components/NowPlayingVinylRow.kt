package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vikify.app.models.MediaMetadata
// import com.vikify.app.vikifyui.theme.TextWhite deleted
import com.vikify.app.vikifyui.theme.TextGray
import kotlinx.coroutines.isActive
import androidx.compose.ui.draw.shadow

@Composable
fun NowPlayingVinylRow(
    song: MediaMetadata,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    // Infinite rotation for the vinyl
    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "vinylAngle"
    )
    
    // If paused, we want to stop the rotation. 
    // real-time rotation state that only updates when playing
    var currentRotation by remember { mutableFloatStateOf(0f) }
    
    // We'll use a frame-based approach or just simple state flow if we want it to pause EXACTLY
    // For simplicity, we can use the animated value but "snap" it or use a running animation that pauses.
    // A simpler trick: wrapping the rotation in a graphicLayer that only updates `rotationZ` if playing?
    // Actually, animateFloat with infiniteRepeatable runs always. 
    // Let's use a simpler approach: Just animate a generic value only if Playing.
    
    // Better Approach for Pausing:
    // We use a manual Animatable that loops.
    val rotationAnim = remember { Animatable(0f) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            // Spin forever from current value
            // We loop 0->360 repeatedly, adding to current base
            // But ensure seamlessness. 
            // easier: infinite rotation logic
             while (isActive) {
                rotationAnim.animateTo(
                    targetValue = rotationAnim.value + 360f,
                    animationSpec = tween(4000, easing = LinearEasing)
                )
             }
        } else {
            rotationAnim.stop()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF2E1065), // Deep Purple
                        Color.Black
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT SIDE: Text Info
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .padding(start = 20.dp, end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "NOW PLAYING",
                    color = TextGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (song.artists.isNotEmpty()) {
                    Text(
                        text = song.artists.joinToString(", ") { it.name },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // RIGHT SIDE: Vinyl Animation
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                // The Vinyl Animation Layout (Layered: Vinyl Back, Art Front)
                VinylAnimationLayout(
                    song = song,
                    rotationDegrees = rotationAnim.value,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        // Correcting the Layering: Vinyl needs to be rendered FIRST to be behind.
        // But in the Row above, I put the Vinyl in the Right Box. 
        // To achieve "Vinyl behind Album Art", they should be in the SAME Box, with Vinyl rendered first.
    }
}

@Composable
fun VinylAnimationLayout(
    song: MediaMetadata,
    rotationDegrees: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(100.dp),
        contentAlignment = Alignment.CenterStart // Align left so vinyl sticks out right
    ) {
            // 1. Vinyl Disc (Behind, offset right)
        Box(
            modifier = Modifier
                .size(80.dp) 
                .offset(x = 45.dp) // Push out more (was 30dp)
                .rotate(rotationDegrees)
                .clip(CircleShape)
                .background(Color(0xFF111111)) // Dark vinyl color
        ) {
             // Stylized Vinyl Grooves
             Box(modifier = Modifier.fillMaxSize().border(20.dp, Color.Black.copy(alpha=0.8f), CircleShape))
             
             // Center Label
             AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        
        // 2. Album Cove (Front, Square)
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(4.dp))
                .shadow(8.dp),
            contentScale = ContentScale.Crop
        )
    }
}
