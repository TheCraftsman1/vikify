package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import coil3.compose.AsyncImage
import kotlin.math.sin
import kotlin.random.Random

// Vikify brand colors
private val VIKIFY_ACCENT = Color(0xFF7C4DFF)
private val VIKIFY_ACCENT_GLOW = Color(0xFFB388FF)

// Turntable colors
private val WALNUT_DARK = Color(0xFF2D1E1B)
private val WALNUT_MID = Color(0xFF3E2723)
private val WALNUT_LIGHT = Color(0xFF4E342E)
private val CHROME_LIGHT = Color(0xFFEEEEEE)
private val CHROME_MID = Color(0xFFCCCCCC)
private val CHROME_DARK = Color(0xFF888888)
private val VINYL_BLACK = Color(0xFF0A0A0A)
private val VINTAGE_PAPER = Color(0xFFDDCCA5)
private val VINTAGE_RED = Color(0xFFAA0000)
private val CARTRIDGE_ORANGE = Color(0xFFFF6F00)

/**
 * JAM MODE TURNTABLE
 *
 * A premium, realistic turntable component featuring:
 * - Authentic walnut wood chassis with grain texture & parallax lighting
 * - Brushed aluminum platter with chrome rim & dynamic reflections
 * - Spinning vinyl with album art label, dust texture, and deep grooves
 * - Animated S-shaped tonearm with lift physics & shadow dept
 * - Retro VU meter with glass refraction and needle shadow
 * - Jam Mode connection glow effect
 *
 * @param artworkUrl URL for the center label artwork
 * @param isPlaying Controls vinyl spin and tonearm position
 * @param isConnected Enables Jam Mode glow effect
 * @param modifier Standard Compose modifier
 */
@Composable
fun JamModeTurntable(
    artworkUrl: String?,
    isPlaying: Boolean,
    isConnected: Boolean = false,
    modifier: Modifier = Modifier
) {
    // ═══════════════════════════════════════════════════════════════════
    // STATE & ANIMATIONS
    // ═══════════════════════════════════════════════════════════════════

    val infiniteTransition = rememberInfiniteTransition(label = "turntable")

    // ─────────────────────────────────────────────────────────────
    // PHYSICS ENGINE
    // ─────────────────────────────────────────────────────────────

    // Current rotation angle (0-360)
    var actualRotation by remember { mutableFloatStateOf(0f) }

    // Inertia simulation (0f = Stopped, 1f = Full Speed)
    val vinylSpeed by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isPlaying) 2000 else 2500, // Heavy platter inertia (authentic feel)
            easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f) // Analog-style startup curve
        ),
        label = "vinylSpeed"
    )

    // Frame-loop for smooth rotation integration - strict battery optimization
    LaunchedEffect(Unit) {
        var lastFrameTime = withFrameNanos { it }
        while (isActive) {
            // Only request frames if the turntable is actually moving
            if (vinylSpeed > 0.001f) {
                withFrameNanos { now ->
                    val dtMillis = (now - lastFrameTime) / 1_000_000f
                    lastFrameTime = now

                    val rotationDelta = 0.2f * vinylSpeed * dtMillis
                    actualRotation = (actualRotation + rotationDelta) % 360f
                }
            } else {
                // Suspend execution until speed increases (stops the frame loop completely)
                snapshotFlow { vinylSpeed }.first { it > 0.001f }
                lastFrameTime = withFrameNanos { it }
            }
        }
    }

    // Tonearm with realistic spring physics & lift
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 24f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tonearm"
    )
    
    // Tonearm Lift (Scale) for 3D effect
    val tonearmElevation by animateFloatAsState(
        targetValue = if (isPlaying) 1.05f else 1f, // Slight scale up when moving (lifted)
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tonearmElevation"
    )

    // Connection pulse for Jam Mode
    val connectionPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "connectionPulse"
    )

    // Smooth VU meter needle
    val vuNeedleBase by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vuNeedle"
    )

    // Secondary wobble for organic feel
    val vuNeedleWobble by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "vuWobble"
    )

    // Parallax Shift (Subtle breathing movement to simulate viewing angle change)
    val parallaxShift by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "parallax"
    )

    // LED indicator pulse
    val ledBrightness by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "led"
    )

    // Pre-calculate wood grain pattern
    val woodGrainLines = remember {
        List(60) {
            WoodGrainLine(
                yOffset = (it * 0.02f) + (Random.nextFloat() * 0.01f),
                thickness = 0.5f + Random.nextFloat() * 2f,
                lengthFactor = 0.85f + Random.nextFloat() * 0.3f,
                curve = Random.nextFloat() * 15f - 7.5f,
                alpha = 0.04f + Random.nextFloat() * 0.06f // Softer grain
            )
        }
    }
    
    // Pre-calculate dust particles for vinyl
    val dustParticles = remember {
        List(35) {
            Offset(Random.nextFloat(), Random.nextFloat())
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CACHED RESOURCES (Avoid object creation in draw loop)
    // ─────────────────────────────────────────────────────────────
    
    val connectionGlowBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                VIKIFY_ACCENT_GLOW.copy(alpha = 0.4f),
                VIKIFY_ACCENT.copy(alpha = 0.2f),
                Color.Transparent
            )
        )
    }

    val woodBaseBrush = remember {
        Brush.verticalGradient(
            colors = listOf(WALNUT_LIGHT, WALNUT_MID, WALNUT_DARK)
        )
    }
    
    val varnishBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
                Color.White.copy(alpha = 0.03f)
            ),
            start = Offset(0f, 0f), 
            end = Offset(1000f, 500f) // Approximate, will be scaled
        )
    }

    val chromeBorderBrush = remember {
        Brush.linearGradient(
            colors = listOf(CHROME_LIGHT, CHROME_DARK, CHROME_LIGHT, CHROME_DARK)
        )
    }

    val platterShadowBrush = remember {
        Brush.radialGradient(
            0.9f to Color.Black.copy(alpha = 0.6f),
            1.0f to Color.Transparent
        )
    }
    
    val chromeRimBrush = remember {
        Brush.sweepGradient(
            colors = listOf(
                CHROME_DARK, CHROME_LIGHT, CHROME_DARK,
                CHROME_LIGHT, CHROME_DARK
            )
        )
    }
    
    val reflectionBrush = remember {
        Brush.sweepGradient(
            0f to Color.Transparent,
            0.4f to Color.White.copy(alpha = 0.12f),
            0.6f to Color.Transparent,
            0.9f to Color.White.copy(alpha = 0.05f),
            1f to Color.Transparent
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // RENDER
    // ═══════════════════════════════════════════════════════════════════

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // ─────────────────────────────────────────────────────────────
        // LAYER 0:  Jam Mode Connection Glow
        // ─────────────────────────────────────────────────────────────
        if (isConnected) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = connectionPulse }
            ) {
                drawRoundRect(
                    brush = connectionGlowBrush,
                    cornerRadius = CornerRadius(12.dp.toPx())
                )
            }
        }

        // ─────────────────────────────────────────────────────────────
        // LAYER 1: Walnut Wood Chassis (with Parallax Varnish)
        // ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(10.dp),
                    spotColor = Color.Black.copy(alpha = 0.9f)
                )
                .clip(RoundedCornerShape(10.dp))
                .background(WALNUT_MID)
        ) {
            // Wood grain texture
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Base gradient
                drawRect(brush = woodBaseBrush)

                // Stable wood grain lines (shifted by parallax)
                rotate(degrees = 3f) {
                    woodGrainLines.forEach { line ->
                        val y = size.height * line.yOffset
                        drawLine(
                            color = Color.Black.copy(alpha = line.alpha),
                            start = Offset(-20f + parallaxShift, y),
                            end = Offset(size.width * line.lengthFactor + parallaxShift, y + line.curve),
                            strokeWidth = line.thickness,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Subtle varnish sheen (High gloss topcoat effect using BlendMode)
                drawRect(
                    brush = varnishBrush,
                    blendMode = BlendMode.Screen,
                    topLeft = Offset(parallaxShift * 5, 0f) // Apply parallax translation here
                )
            }

            // Chrome trim edge
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 3.dp,
                        brush = chromeBorderBrush,
                        shape = RoundedCornerShape(10.dp)
                    )
            )
        }

        // ─────────────────────────────────────────────────────────────
        // NEW LAYER: Platter Recess (Ambient Occlusion/Shadow Well)
        // ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(256.dp) // Slightly larger than platter
                .align(Alignment.Center)
                .offset(y = (-12).dp)
                .background(platterShadowBrush)
        )

        // ─────────────────────────────────────────────────────────────
        // LAYER 2: Brushed Aluminum Platter
        // ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.Center)
                .offset(y = (-12).dp)
                .shadow(4.dp, CircleShape) // Reduced shadow as we have the recess now
                .clip(CircleShape)
                .background(CHROME_MID)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2

                // Concentric brushed metal rings
                for (i in 1..25) {
                    val ringRadius = radius * (i / 25f)
                    drawCircle(
                        color = if (i % 2 == 0)
                            Color.White.copy(alpha = 0.15f)
                        else
                            Color.Black.copy(alpha = 0.08f),
                        radius = ringRadius,
                        style = Stroke(width = 3f)
                    )
                }

                // Dynamic Room Light Reflection (Rotates independently)
                rotate(actualRotation * 0.15f) { 
                    drawCircle(
                        brush = reflectionBrush,
                        radius = radius
                    )
                }

                // Chrome outer rim with sweep gradient
                drawCircle(
                    brush = chromeRimBrush,
                    radius = radius - 2.dp.toPx(),
                    style = Stroke(width = 5.dp.toPx())
                )
            }
        }

        // ─────────────────────────────────────────────────────────────
        // LAYER 3: Vinyl Record
        // ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(230.dp)
                .align(Alignment.Center)
                .offset(y = (-12).dp)
                .graphicsLayer { rotationZ = actualRotation }
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(VINYL_BLACK)
        ) {
            // Vinyl grooves and sheen
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2

                // Simplified grooves (calmer)
                for (i in 0..20) {
                    val grooveRadius = radius * (0.36f + (i / 20f) * 0.62f)
                    val alpha = if (i % 2 == 0) 0.04f else 0.02f
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = grooveRadius,
                        center = center,
                        style = Stroke(width = 0.8f)
                    )
                }
                
                // Dust & Static Texture
                dustParticles.forEach { point ->
                     drawCircle(
                        color = Color.White.copy(alpha = 0.15f),
                        radius = 0.8f,
                        center = Offset(point.x * size.width, point.y * size.height)
                     )
                }

                // Light reflection arc (Static relative to record surface for gloss)
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                            Color.Transparent
                        )
                    ),
                    startAngle = -60f,
                    sweepAngle = 120f,
                    useCenter = true,
                    size = Size(radius * 1.8f, radius * 1.8f),
                    topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.9f)
                )
            }

            // Center Label
            Box(
                modifier = Modifier
                    .size(85.dp)
                    .align(Alignment.Center)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(VINTAGE_PAPER)
            ) {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = "Album artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Aged vignette overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        WALNUT_MID.copy(alpha = 0.4f)
                                    )
                                )
                            )
                    )
                } else {
                    // Vintage generic label
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = VINTAGE_RED,
                                radius = size.minDimension / 2 * 0.85f,
                                style = Stroke(width = 3.dp.toPx())
                            )
                            drawCircle(
                                color = VINTAGE_RED,
                                radius = size.minDimension / 2 * 0.6f,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "VIKIFY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1A1A1A),
                                letterSpacing = 2.sp
                            )
                            Text(
                                "HI-FI",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF444444)
                            )
                        }
                    }
                }

                // Center spindle
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.Center)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(CHROME_LIGHT, CHROME_DARK)
                            )
                        )
                )
            }
        }

        // ─────────────────────────────────────────────────────────────
        // LAYER 4: S-Shaped Tonearm (with 3D Lift Physics)
        // ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-20).dp, y = 20.dp)
                .size(150.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.88f, 0.12f)
                    rotationZ = tonearmAngle
                    scaleX = tonearmElevation
                    scaleY = tonearmElevation
                    // Logic: As the arm "lifts", the shadow should offset further/blur more
                    shadowElevation = if (isPlaying) 16f else 4f 
                    // Manual shadow offset compensation would go here if not using elevation
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pivotX = size.width * 0.88f
                val pivotY = size.height * 0.12f
                val endX = size.width * 0.18f
                val endY = size.height * 0.38f

                // S-curve path
                val armPath = Path().apply {
                    moveTo(pivotX, pivotY)
                    cubicTo(
                        pivotX - 25f, pivotY + 60f,
                        endX + 60f, endY - 40f,
                        endX, endY
                    )
                }
                
                // Dynamic Shadow (Based on Lift) - Drawn manually for control
                // As it lifts (scale > 1), shadow moves down/right
                val shadowOffset = if (tonearmElevation > 1.01f) 12f else 5f
                translate(left = shadowOffset, top = shadowOffset) {
                    drawPath(
                        path = armPath,
                        color = Color.Black.copy(alpha = if (tonearmElevation > 1.01f) 0.3f else 0.5f), // Softer when higher
                        style = Stroke(width = 14f, cap = StrokeCap.Round)
                    )
                }

                // Main tube (silver)
                drawPath(
                    path = armPath,
                    color = Color(0xFFD0D0D0),
                    style = Stroke(width = 9f, cap = StrokeCap.Round)
                )

                // Highlight
                drawPath(
                    path = armPath,
                    color = Color.White.copy(alpha = 0.5f),
                    style = Stroke(width = 2f, cap = StrokeCap.Round)
                )

                // Headshell
                drawRoundRect(
                    color = Color(0xFFB8B8B8),
                    topLeft = Offset(endX - 18f, endY - 8f),
                    size = Size(36f, 22f),
                    cornerRadius = CornerRadius(3f)
                )

                // Cartridge
                drawRect(
                    color = CARTRIDGE_ORANGE,
                    topLeft = Offset(endX - 8f, endY + 12f),
                    size = Size(16f, 8f)
                )

                // Stylus
                drawLine(
                    color = Color(0xFF666666),
                    start = Offset(endX, endY + 20f),
                    end = Offset(endX, endY + 26f),
                    strokeWidth = 2f
                )

                // Pivot base
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(CHROME_LIGHT, Color(0xFF333333)),
                        center = Offset(pivotX, pivotY),
                        radius = 28f
                    ),
                    radius = 28f,
                    center = Offset(pivotX, pivotY)
                )

                // Pivot highlight
                drawCircle(
                    color = Color.White.copy(alpha = 0.3f),
                    radius = 8f,
                    center = Offset(pivotX - 6f, pivotY - 6f)
                )
            }
        }

        // Controls removed for calm Jam Mode atmosphere
    }
}

/**
 * Data class for stable wood grain rendering
 */
private data class WoodGrainLine(
    val yOffset: Float,
    val thickness: Float,
    val lengthFactor: Float,
    val curve: Float,
    val alpha: Float
)

/**
 * RPM speed selector button
 */
@Composable
private fun SpeedButton(
    rpm: String,
    isSelected: Boolean
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(
                if (isSelected) Color(0xFF444444) else Color(0xFF2A2A2A)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) VIKIFY_ACCENT else Color(0xFF555555),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = rpm,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else Color(0xFF888888)
        )
    }
}