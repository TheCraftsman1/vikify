package com.music.vikify.dj

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.imageResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.vikify.R
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class DjEmotion { HAPPY, LISTENING, THINKING, SPEAKING, SLEEPY }

/**
 * 3D rendered DJ mascot with live, voice-reactive coding components for eyes and mouth.
 * The high-fidelity base image renders the pearl crown and photographic headphones directly,
 * while animated Compose vector paths draw blinking, gaze-tracking eyes and voice equalizer mouth.
 */
@Composable
fun VikifyMascot(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    emotion: DjEmotion = DjEmotion.HAPPY,
    newSong: String? = null,
    reduceMotion: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val mascotBase = ImageBitmap.imageResource(R.drawable.dj_mascot_base)
    val voiceLevel: State<Float>? = if (!reduceMotion && emotion == DjEmotion.SPEAKING) DjPlayback.voiceLevel.collectAsStateWithLifecycle() else null
    val blink = remember { Animatable(1f) }
    val gaze = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    var previousSong by remember { mutableStateOf(newSong) }

    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) while (true) {
            delay(Random.nextLong(2800, 5200))
            blink.animateTo(.08f, tween(80))
            blink.animateTo(1f, tween(130))
            gaze.animateTo(Random.nextFloat() * 2 - 1, tween(380))
        }
    }
    LaunchedEffect(newSong, reduceMotion) {
        if (newSong != null && newSong != previousSong && !reduceMotion) {
            flash.snapTo(0f)
            flash.animateTo(1f, tween(150))
            flash.animateTo(0f, tween(850))
        }
        previousSong = newSong
    }
    val animatedModifier = modifier.size(size).semantics {
        contentDescription = if (onClick == null) "Vikify DJ" else "Talk to your DJ"
    }
    Canvas(if (onClick != null) animatedModifier.clickable(role = Role.Button, onClick = onClick) else animatedModifier) {
        val eye = if (reduceMotion) 1f else blink.value
        val look = if (reduceMotion) 0f else gaze.value
        val side = this.size.minDimension
        if (side <= 0f) return@Canvas
        withTransform({
            translate((this@Canvas.size.width - side) / 2f, (this@Canvas.size.height - side) / 2f)
            scale(side / 1024f, side / 1024f, Offset.Zero)
        }) {
            // 1. Photographic 3D mascot base image with pearl crown and studio headphones
            drawImage(mascotBase, dstSize = IntSize(1024, 1024), filterQuality = FilterQuality.High)

            // 2. Visor drop flash glow
            if (flash.value > 0f) {
                drawOval(
                    Brush.radialGradient(
                        listOf(Color(0xFF64D2FF).copy(alpha = flash.value * 0.28f), Color.Transparent),
                        center = Offset(511.5f, 646f),
                        radius = 180f
                    ),
                    topLeft = Offset(360f, 530f),
                    size = Size(303f, 230f)
                )
            }

            // 3. Live coding components: animated eyes & mouth on the glossy visor
            drawExpressions(emotion, eye, look, flash.value, voiceLevel?.value ?: 0f)
        }
    }
}

private fun DrawScope.drawExpressions(
    emotion: DjEmotion,
    blink: Float,
    gaze: Float,
    flash: Float,
    voiceLevel: Float
) {
    val shiftX = gaze * 9f
    val baseColor = if (flash > 0f) Color(0xFFD6F5FF) else Color.White
    val eyeColor = baseColor.copy(alpha = (1f + flash * 0.2f).coerceAtMost(1f))

    when (emotion) {
        DjEmotion.LISTENING -> {
            // Attentive wide-eyed listening state
            val ovalHeight = 38f * blink.coerceAtLeast(0.1f)
            listOf(438f + shiftX, 584f + shiftX).forEach { cx ->
                drawOval(
                    color = eyeColor,
                    topLeft = Offset(cx - 14f, 617f - ovalHeight / 2f),
                    size = Size(28f, ovalHeight)
                )
            }
        }
        DjEmotion.SLEEPY -> {
            // Relaxed downward drooping curves
            val leftEye = Path().apply {
                moveTo(410f + shiftX, 618f)
                cubicTo(424f + shiftX, 638f, 452f + shiftX, 638f, 466f + shiftX, 618f)
            }
            val rightEye = Path().apply {
                moveTo(556f + shiftX, 618f)
                cubicTo(570f + shiftX, 638f, 598f + shiftX, 638f, 612f + shiftX, 618f)
            }
            drawPath(leftEye, eyeColor, style = Stroke(10f, cap = StrokeCap.Round))
            drawPath(rightEye, eyeColor, style = Stroke(10f, cap = StrokeCap.Round))
        }
        DjEmotion.THINKING -> {
            // Curious pondering expression: left eye arch, right eye tilted
            val leftEye = Path().apply {
                moveTo(410f + shiftX, 635f)
                cubicTo(422f + shiftX, 635f - 35f * blink, 454f + shiftX, 635f - 35f * blink, 466f + shiftX, 635f)
            }
            val rightEye = Path().apply {
                moveTo(556f + shiftX, 630f)
                cubicTo(568f + shiftX, 620f - 25f * blink, 600f + shiftX, 630f - 35f * blink, 612f + shiftX, 640f)
            }
            drawPath(leftEye, eyeColor, style = Stroke(11f, cap = StrokeCap.Round))
            drawPath(rightEye, eyeColor, style = Stroke(11f, cap = StrokeCap.Round))
        }
        else -> { // HAPPY, SPEAKING
            // Cheerful upward curved happy eyes with smooth blink morphing
            val leftEye = Path().apply {
                moveTo(410f + shiftX, 635f)
                cubicTo(422f + shiftX, 635f - 35f * blink, 454f + shiftX, 635f - 35f * blink, 466f + shiftX, 635f)
            }
            val rightEye = Path().apply {
                moveTo(556f + shiftX, 635f)
                cubicTo(568f + shiftX, 635f - 35f * blink, 600f + shiftX, 635f - 35f * blink, 612f + shiftX, 635f)
            }
            drawPath(leftEye, eyeColor, style = Stroke(11f, cap = StrokeCap.Round))
            drawPath(rightEye, eyeColor, style = Stroke(11f, cap = StrokeCap.Round))
        }
    }

    // Mouth component
    val mouthX = 511.5f + shiftX * 0.25f
    if (emotion == DjEmotion.SPEAKING) {
        // Voice-reactive animated equalizer waveform bars
        for (i in -2..2) {
            val barX = mouthX + i * 11f
            val h = (8f + 32f * voiceLevel * (1f - kotlin.math.abs(i) * 0.18f)).coerceAtLeast(6f)
            drawLine(
                color = eyeColor,
                start = Offset(barX, 680f - h / 2f),
                end = Offset(barX, 680f + h / 2f),
                strokeWidth = 7f,
                cap = StrokeCap.Round
            )
        }
    } else if (emotion == DjEmotion.SLEEPY) {
        val mouth = Path().apply {
            moveTo(494f + shiftX * 0.25f, 678f)
            cubicTo(506f + shiftX * 0.25f, 685f, 518f + shiftX * 0.25f, 685f, 528f + shiftX * 0.25f, 678f)
        }
        drawPath(mouth, eyeColor, style = Stroke(8f, cap = StrokeCap.Round))
    } else { // HAPPY, LISTENING, THINKING
        val mouth = Path().apply {
            moveTo(490f + shiftX * 0.25f, 674f)
            cubicTo(500f + shiftX * 0.25f, 694f, 523f + shiftX * 0.25f, 694f, 533f + shiftX * 0.25f, 674f)
        }
        drawPath(mouth, eyeColor, style = Stroke(8.5f, cap = StrokeCap.Round))
    }
}
