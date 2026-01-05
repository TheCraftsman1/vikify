package com.vikify.app.vikifyui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vikify.app.vikifyui.data.TimeCapsuleViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TimeCapsuleScreen(
    viewModel: TimeCapsuleViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var timeWarp by remember { mutableFloatStateOf(1f) } // 0f = Past, 1f = Now

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Universe Background
        StarfieldBackground(speed = timeWarp)

        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else {
            // Solar System
            SolarSystem(
                state = state,
                timeWarp = timeWarp,
                modifier = Modifier.fillMaxSize()
            )

            // Overlay Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black)
                        )
                    )
                    .padding(24.dp)
            ) {
                Text(
                    text = "Sonic DNA",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "${state.totalMinutes} Minutes Listened",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
                Text(
                    text = "Top Artist: ${state.topArtist}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Time Warp", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Slider(
                    value = timeWarp,
                    onValueChange = { timeWarp = it },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
fun StarfieldBackground(speed: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "stars")
    
    // Three layer rotations for parallax effect
    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(80000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationSlow"
    )
    val rotation2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(50000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationMedium"
    )
    val rotation3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationFast"
    )
    
    // Shooting star animation
    val shootingStarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shootingStar"
    )

    // Generate stable star positions
    val stars1 = remember { List(80) { Pair(Math.random().toFloat(), Math.random().toFloat()) } }
    val stars2 = remember { List(60) { Pair(Math.random().toFloat(), Math.random().toFloat()) } }
    val stars3 = remember { List(40) { Pair(Math.random().toFloat(), Math.random().toFloat()) } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        // NEBULA BACKGROUND (Purple/Blue blobs)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF6B21A8).copy(alpha = 0.3f), Color.Transparent),
                center = Offset(size.width * 0.3f, size.height * 0.2f),
                radius = size.width * 0.5f
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1E40AF).copy(alpha = 0.2f), Color.Transparent),
                center = Offset(size.width * 0.7f, size.height * 0.8f),
                radius = size.width * 0.6f
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF7C3AED).copy(alpha = 0.15f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.width * 0.7f
            )
        )
        
        // LAYER 1: Distant stars (slowest, smallest)
        stars1.forEach { (xRatio, yRatio) ->
            val angle = Math.toRadians((rotation1 * speed).toDouble())
            val dist = (xRatio * size.width * 0.4f)
            val x = centerX + (dist * cos(angle + yRatio * Math.PI * 2)).toFloat()
            val y = centerY + (dist * sin(angle + yRatio * Math.PI * 2)).toFloat()
            val brightness = 0.3f + (xRatio * 0.4f)
            drawCircle(Color.White.copy(alpha = brightness), radius = 1f, center = Offset(x, y))
        }
        
        // LAYER 2: Mid-distance stars
        stars2.forEach { (xRatio, yRatio) ->
            val angle = Math.toRadians((rotation2 * speed).toDouble())
            val dist = (xRatio * size.width * 0.5f)
            val x = centerX + (dist * cos(angle + yRatio * Math.PI * 2)).toFloat()
            val y = centerY + (dist * sin(angle + yRatio * Math.PI * 2)).toFloat()
            val brightness = 0.5f + (xRatio * 0.3f)
            drawCircle(Color.White.copy(alpha = brightness), radius = 1.5f, center = Offset(x, y))
        }
        
        // LAYER 3: Close stars (fastest, brightest)
        stars3.forEach { (xRatio, yRatio) ->
            val angle = Math.toRadians((rotation3 * speed).toDouble())
            val dist = (xRatio * size.width * 0.6f)
            val x = centerX + (dist * cos(angle + yRatio * Math.PI * 2)).toFloat()
            val y = centerY + (dist * sin(angle + yRatio * Math.PI * 2)).toFloat()
            val brightness = 0.7f + (xRatio * 0.3f)
            drawCircle(Color.White.copy(alpha = brightness), radius = 2.5f, center = Offset(x, y))
        }
        
        // SHOOTING STAR
        if (shootingStarProgress < 0.3f) {
            val progress = shootingStarProgress / 0.3f
            val startX = size.width * 0.8f
            val startY = size.height * 0.1f
            val endX = size.width * 0.2f
            val endY = size.height * 0.4f
            
            val currentX = startX + (endX - startX) * progress
            val currentY = startY + (endY - startY) * progress
            
            // Draw shooting star trail
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    start = Offset(currentX, currentY),
                    end = Offset(currentX + 50f, currentY - 20f)
                ),
                start = Offset(currentX, currentY),
                end = Offset(currentX + 80f, currentY - 30f),
                strokeWidth = 2f
            )
            drawCircle(Color.White, radius = 3f, center = Offset(currentX, currentY))
        }
    }
}

@Composable
fun SolarSystem(state: com.vikify.app.vikifyui.data.TimeCapsuleState, timeWarp: Float, modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbits")
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitBase"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // SUN (Top Artist)
        Box(
            modifier = Modifier
                .size(120.dp * timeWarp.coerceAtLeast(0.5f))
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFA500), Color(0xFFFF4500))))
                .padding(4.dp)
                .clip(CircleShape)
        ) {
            if (state.topArtistImageUrl != null) {
                AsyncImage(
                    model = state.topArtistImageUrl,
                    contentDescription = "Top Artist",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.topArtist.take(1),
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium
                    )
                }
            }
        }

        // PLANETS (Genres)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            state.genres.forEachIndexed { index, genre ->
                val orbitRadius = 200f + (index * 150f) * timeWarp
                val angle = (baseRotation * genre.orbitSpeed) + (index * 120f)
                val rad = Math.toRadians(angle.toDouble())
                
                // Draw Orbit
                drawCircle(
                    color = Color.White.copy(alpha = 0.2f),
                    radius = orbitRadius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2f)
                )

                // Draw Planet with GLOW
                val planetX = centerX + (orbitRadius * cos(rad)).toFloat()
                val planetY = centerY + (orbitRadius * sin(rad)).toFloat()
                val planetSize = (20f + (genre.minutes / 10f)).coerceIn(10f, 60f)
                val planetColor = Color(android.graphics.Color.parseColor(genre.colorHex))

                // Glow effect (larger, semi-transparent)
                drawCircle(
                    color = planetColor.copy(alpha = 0.4f),
                    radius = planetSize * 1.8f,
                    center = Offset(planetX, planetY)
                )
                drawCircle(
                    color = planetColor.copy(alpha = 0.6f),
                    radius = planetSize * 1.3f,
                    center = Offset(planetX, planetY)
                )
                // Core planet
                drawCircle(
                    color = planetColor,
                    radius = planetSize,
                    center = Offset(planetX, planetY)
                )
            }
        }
    }
}
