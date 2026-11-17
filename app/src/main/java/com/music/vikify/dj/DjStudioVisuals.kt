package com.music.vikify.dj

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Deep-navy studio palette: periwinkle type, cyan-to-violet accents, soft blue halo. */
internal object DjStyle {
    val background = Color(0xFF031134)
    val surface = Color(0xFF0B1A42)
    val raised = Color(0xFF15244F)
    val lavender = Color(0xFFA9BCF6) // Primary accent; name kept for existing call sites.
    val glow = Color(0xFF3B52F2)
    val text = Color(0xFFEFF4FF)
    val muted = Color(0xFF8794BE)
    val userBubble = Color(0xFF26407F)
    val brand = Color(0xFFC4D7F3)
    val cyan = Color(0xFF78CCF2)
    val sky = Color(0xFFA8E4F8)
    val violet = Color(0xFF8B7FE2)
    val periwinkle = Color(0xFF8C9FF0)
    val glassBorder = Color(0xFF36467A)
    val field = Color(0xFF061031)
    val ink = Color(0xFF1E2568)
    val error = Color(0xFFF3AFC2)

    val backdrop = Brush.verticalGradient(listOf(Color(0xFF05163C), background, Color(0xFF020C2A)))
    val accent = Brush.linearGradient(listOf(sky, violet))
    val userBubbleFill = Brush.linearGradient(listOf(Color(0xFF2C5DB8), Color(0xFF5548BE)))
}

/** Frosted navy panel shared by the composer, chat bubbles, song rows and the crafting checklist. */
internal fun Modifier.djGlass(shape: Shape, opacity: Float = .74f): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color(0xFF26335E).copy(alpha = opacity), Color(0xFF16214A).copy(alpha = opacity))))
    .border(1.dp, Brush.verticalGradient(listOf(Color(0xFF53638F).copy(alpha = .8f), DjStyle.glassBorder.copy(alpha = .45f))), shape)

/** "DJ AI" pill with the overlapping Beta badge. Pass [onClick] to make the pill a button. */
@Composable
internal fun DjBetaTitle(large: Boolean = false, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(modifier.padding(top = if (large) 9.dp else 7.dp)) {
        Box(
            Modifier.clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF1B3263), Color(0xFF112452))))
                .border(1.dp, Brush.verticalGradient(listOf(Color(0xFF46619B), Color(0xFF203563))), CircleShape)
                .then(if (onClick != null) Modifier.clickable(onClickLabel = "Open DJ options", role = Role.Button, onClick = onClick) else Modifier)
                .padding(start = if (large) 16.dp else 13.dp, end = if (large) 46.dp else 38.dp, top = 5.dp, bottom = 5.dp),
        ) {
            Text("DJ AI", style = TextStyle(
                brush = Brush.horizontalGradient(listOf(DjStyle.cyan, Color(0xFFA9C4F7))),
                fontSize = if (large) 24.sp else 19.sp, fontWeight = FontWeight.Bold, letterSpacing = .2.sp,
            ))
        }
        Text(
            "Beta",
            Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = if (large) (-9).dp else (-7).dp)
                .clip(CircleShape).background(Color(0xFF34548E))
                .border(.8.dp, Color(0xFF6283BE).copy(alpha = .7f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 1.dp),
            color = Color(0xFFDCEBFC), fontSize = if (large) 11.sp else 10.sp, fontWeight = FontWeight.Medium,
        )
    }
}

/** Legacy atmosphere band (no longer on the landing), recoloured for the navy studio. */
@Composable
internal fun DjWaveField(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        for (i in 0..3) {
            val shift = i * .12f
            val path = Path().apply {
                moveTo(-w * .10f, h * (.58f - shift))
                cubicTo(w * .16f, h * (.03f + shift), w * .29f, h * (1.04f - shift), w * .54f, h * .48f)
                cubicTo(w * .77f, -h * .12f, w * .86f, h * (.94f - shift), w * 1.1f, h * (.23f + shift))
                lineTo(w * 1.1f, h * (.55f + shift))
                cubicTo(w * .85f, h * (.31f + shift), w * .76f, h * .50f, w * .52f, h * .70f)
                cubicTo(w * .29f, h * (1.18f - shift), w * .16f, h * (.16f + shift), -w * .1f, h * (.86f - shift))
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(DjStyle.sky.copy(alpha = .02f + i * .02f),
                DjStyle.glow.copy(alpha = .12f), Color.Transparent)))
            drawPath(path, DjStyle.cyan.copy(alpha = .05f), style = Stroke(.65.dp.toPx()))
        }
    }
}

/** Mascot on a soft blue studio halo. Static at rest; animates only while speaking, thinking or crafting. */
@Composable
internal fun DjOrb(
    size: Dp, emotion: DjEmotion, songId: String?, reduceMotion: Boolean,
    modifier: Modifier = Modifier, crafting: Boolean = false, onClick: (() -> Unit)? = null,
) {
    val voiceLevel = if (emotion == DjEmotion.SPEAKING && !reduceMotion) DjPlayback.voiceLevel.collectAsStateWithLifecycle() else null
    val transition = if (!reduceMotion && (crafting || emotion == DjEmotion.THINKING)) rememberInfiniteTransition(label = "dj halo") else null
    val spin = if (transition != null && crafting)
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(22_000, easing = LinearEasing)), label = "orb turn") else null
    val breath = if (transition != null)
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "halo breath") else null
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val d = this.size.minDimension
            if (d <= 0f) return@Canvas
            val c = center
            val voice = voiceLevel?.value ?: 0f
            val lift = 1f + voice * .45f + (breath?.value ?: 0f) * .2f
            val tint = if (emotion == DjEmotion.LISTENING) DjStyle.cyan else DjStyle.glow
            val reach = d / 2 * if (crafting) .8f else 1f
            // The halo peeks out just past the mascot silhouette, like the studio mock.
            drawCircle(Brush.radialGradient(
                0f to tint.copy(alpha = (.62f * lift).coerceAtMost(1f)),
                .56f to tint.copy(alpha = (.46f * lift).coerceAtMost(1f)),
                .78f to tint.copy(alpha = (.28f * lift).coerceAtMost(1f)),
                .91f to tint.copy(alpha = (.09f * lift).coerceAtMost(1f)),
                1f to Color.Transparent,
                center = c, radius = reach,
            ), reach, c)
            if (emotion == DjEmotion.SPEAKING) drawCircle(DjStyle.sky.copy(alpha = (.16f + voice * .4f).coerceAtMost(1f)),
                reach * (.9f + voice * .05f), c, style = Stroke((1.2f + voice * 1.6f).dp.toPx()))
            if (crafting) rotate(spin?.value ?: 0f, c) {
                repeat(5) { index ->
                    rotate(index * 34f, c) {
                        drawOval(Brush.radialGradient(listOf(Color.Transparent, DjStyle.glow.copy(alpha = .05f),
                            DjStyle.cyan.copy(alpha = .11f), Color.Transparent), c, d * .49f),
                            Offset(d * .11f, d * .04f), Size(d * .78f, d * .92f))
                        drawOval(Brush.linearGradient(listOf(DjStyle.sky.copy(alpha = .03f), DjStyle.cyan.copy(alpha = .32f), Color.Transparent)),
                            Offset(d * .11f, d * .04f), Size(d * .78f, d * .92f), style = Stroke(.8.dp.toPx()))
                    }
                }
            }
        }
        VikifyMascot(size = size * if (crafting) .52f else .84f, emotion = emotion, newSong = songId,
            reduceMotion = reduceMotion, onClick = onClick)
    }
}

/** Rounded-square icon control used by the composer (plus, mic, send) and song rows. */
@Composable
internal fun DjIconTile(
    icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier,
    size: Dp = 38.dp, fill: Brush = SolidColor(DjStyle.raised), tint: Color = DjStyle.periwinkle,
    enabled: Boolean = true, border: Color? = null, iconSize: Dp = 20.dp,
) {
    val shape = RoundedCornerShape(size * .3f)
    Box(
        modifier.size(size).alpha(if (enabled) 1f else .5f).clip(shape).background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, Modifier.size(iconSize), tint = tint) }
}

@Composable
internal fun DjProgressStep(label: String, index: Int, activeStep: Int) {
    val done = index < activeStep
    val active = index == activeStep
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            done -> Box(Modifier.size(26.dp).clip(CircleShape).background(DjStyle.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = DjStyle.ink)
            }
            active -> CircularProgressIndicator(Modifier.size(26.dp), color = DjStyle.cyan,
                trackColor = Color.White.copy(alpha = .10f), strokeWidth = 2.dp)
            else -> Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = .05f))
                .border(1.dp, DjStyle.glassBorder.copy(alpha = .6f), CircleShape), contentAlignment = Alignment.Center) {
                Text((index + 1).toString(), color = DjStyle.muted.copy(alpha = .7f), fontSize = 11.sp)
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = if (done || active) DjStyle.text else DjStyle.muted.copy(alpha = .7f))
    }
}
