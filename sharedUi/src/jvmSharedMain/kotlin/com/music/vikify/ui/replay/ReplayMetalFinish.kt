package com.music.vikify.ui.replay

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Brushed black metal with shallow record grooves and a polished edge. */
internal fun Modifier.replayMetalFinish(): Modifier = drawWithCache {
    val width = size.width
    val height = size.height
    val diagonal = Offset(width, height)
    val plate = Brush.linearGradient(
        0f to Color(0xFF242628),
        .32f to Color(0xFF111213),
        .62f to Color(0xFF0D0F10),
        1f to Color(0xFF070807),
        start = Offset.Zero,
        end = Offset(width * .78f, height * .52f),
    )
    val reflectedLight = Brush.linearGradient(
        .14f to Color.Transparent,
        .40f to Color(0x0E929AA7),
        .55f to Color.Transparent,
        start = Offset(0f, height),
        end = Offset(width * .8f, height * .08f),
    )
    val grooveLight = Brush.verticalGradient(
        .18f to Color.Transparent,
        .48f to Color(0x2498A2B1),
        .72f to Color(0x0C98A2B1),
        .94f to Color.Transparent,
        endY = height,
    )
    val grooveShadow = Brush.verticalGradient(
        .16f to Color.Transparent,
        .50f to Color(0x77000000),
        .75f to Color(0x36000000),
        .96f to Color.Transparent,
        endY = height,
    )
    val rim = Brush.linearGradient(
        0f to Color(0xFFA4ABB2),
        .16f to Color(0xFF4E5258),
        .37f to Color(0xFF666A70),
        .58f to Color(0xFF282B30),
        .82f to Color(0xFF3A3E44),
        1f to Color(0xFF969DA7),
        start = Offset.Zero,
        end = diagonal,
    )
    val edgeWidth = 1.15.dp.toPx()
    val edgeInset = edgeWidth / 2f
    val corner = 20.dp.toPx()
    val innerInset = 2.dp.toPx()
    val grooveCentre = Offset(width * .17f, height * .71f)
    val grooveStroke = Stroke(width = 1.15.dp.toPx())
    val shadowStroke = Stroke(width = 2.25.dp.toPx())

    onDrawBehind {
        drawRect(plate)
        drawRect(reflectedLight)
        for (ring in 0 until 13) {
            val radiusX = width * (.13f + ring * .047f)
            val radiusY = radiusX * 1.26f
            val bounds = Size(radiusX * 2f, radiusY * 2f)
            val topLeft = grooveCentre - Offset(radiusX, radiusY)
            drawOval(grooveShadow, topLeft = topLeft, size = bounds, style = shadowStroke)
            drawOval(
                grooveLight,
                topLeft = topLeft + Offset(edgeWidth, 0f),
                size = bounds,
                style = grooveStroke,
            )
        }
        drawRoundRect(
            brush = rim,
            topLeft = Offset(edgeInset, edgeInset),
            size = Size(width - 2f * edgeInset, height - 2f * edgeInset),
            cornerRadius = CornerRadius(corner - edgeInset),
            style = Stroke(width = edgeWidth),
        )
        drawRoundRect(
            color = Color(0x28ADB3BC),
            topLeft = Offset(innerInset, innerInset),
            size = Size(width - 2f * innerInset, height - 2f * innerInset),
            cornerRadius = CornerRadius(corner - innerInset),
            style = Stroke(width = .55.dp.toPx()),
        )
    }
}

/** Gold contact plate used on the listening overview. */
@Composable
internal fun ReplayMetalChip(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .size(width = 32.dp, height = 22.dp)
            .clip(RoundedCornerShape(4.dp))
            .drawWithCache {
                val width = size.width
                val height = size.height
                val gold = Brush.linearGradient(
                    0f to Color(0xFF9A7542),
                    .36f to Color(0xFFD8BD84),
                    .53f to Color(0xFFF3DEAA),
                    .70f to Color(0xFFC1A068),
                    1f to Color(0xFF917040),
                    start = Offset.Zero,
                    end = Offset(width, height),
                )
                val contacts = Path().apply {
                    moveTo(width * .27f, 0f)
                    cubicTo(width * .41f, height * .22f, width * .22f, height * .38f, width * .31f, height * .5f)
                    cubicTo(width * .39f, height * .64f, width * .26f, height * .77f, width * .34f, height)
                    moveTo(width * .73f, 0f)
                    cubicTo(width * .59f, height * .22f, width * .78f, height * .38f, width * .69f, height * .5f)
                    cubicTo(width * .61f, height * .64f, width * .74f, height * .77f, width * .66f, height)
                    moveTo(0f, height * .34f)
                    lineTo(width * .30f, height * .34f)
                    moveTo(0f, height * .67f)
                    lineTo(width * .31f, height * .67f)
                    moveTo(width * .70f, height * .34f)
                    lineTo(width, height * .34f)
                    moveTo(width * .69f, height * .67f)
                    lineTo(width, height * .67f)
                    moveTo(width * .42f, height * .19f)
                    lineTo(width * .58f, height * .19f)
                    moveTo(width * .42f, height * .81f)
                    lineTo(width * .58f, height * .81f)
                }
                val edge = .65.dp.toPx()
                onDrawBehind {
                    drawRect(gold)
                    drawPath(contacts, Color(0xAA614A2B), style = Stroke(width = .55.dp.toPx()))
                    drawRoundRect(
                        color = Color(0xFFC9AA6D),
                        topLeft = Offset(edge / 2f, edge / 2f),
                        size = Size(width - edge, height - edge),
                        cornerRadius = CornerRadius(4.dp.toPx() - edge / 2f),
                        style = Stroke(width = edge),
                    )
                }
            },
    )
}
