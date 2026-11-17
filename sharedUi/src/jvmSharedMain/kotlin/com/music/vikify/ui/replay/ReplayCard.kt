package com.music.vikify.ui.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.music.vikify.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource
import coil3.compose.AsyncImage
import java.util.Locale

/** A headline Replay stat on the same black metal stock as the rest of the wallet. */
@Composable
fun ReplayCreditCard(
    label: String,
    value: String,
    detail: String?,
    /** Whose card it is. Empty falls back to the localized listener label. */
    holder: String,
    /** `MM/YY`, or null when there is nothing to date it from. */
    memberSince: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showChip: Boolean = false,
) {
    val cardTitle = if (showChip) stringResource(Res.string.your_listening_experience) else label
    Box(
        modifier = modifier
            .aspectRatio(CARD_RATIO)
            .shadow(16.dp, CardShape, clip = false)
            .clip(CardShape)
            .replayMetalFinish()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Vikify",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.W800,
                            letterSpacing = 1.sp,
                            brush = PolishedInk,
                        ),
                    )
                    Text(
                        text = cardTitle.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 1.sp,
                        color = Color.White.copy(alpha = 0.67f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                AsyncImage(
                    model = Res.getUri("files/replay/vikify_white.png"),
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showChip) {
                    ReplayMetalChip()
                    Spacer(Modifier.width(12.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = if (showChip) Alignment.End else Alignment.Start,
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 27.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.W800,
                            brush = PolishedInk,
                            shadow = EmbossShadow,
                        ),
                        textAlign = if (showChip) TextAlign.End else TextAlign.Start,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showChip) {
                        Text(
                            text = label.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.W700,
                            letterSpacing = 1.3.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }

            // Weighted either side rather than pinned to the foot, so the figure
            // sits where a card's number sits — across the middle — instead of
            // leaving the whole upper half of a full-width card empty.
            Spacer(Modifier.weight(0.85f))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Embossed(
                        text = holder.ifBlank { stringResource(Res.string.default_replay_holder) }
                            .uppercase(Locale.getDefault()),
                        size = 13.sp,
                    )
                    if (detail != null) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (memberSince != null) {
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(Res.string.member_since).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 7.sp,
                            lineHeight = 8.sp,
                            letterSpacing = 0.8.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            textAlign = TextAlign.End,
                        )
                        Embossed(text = memberSince, size = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * A line pressed into the card rather than printed on it.
 *
 * Monospaced and widely tracked because that is what a card embosser produces —
 * fixed-pitch dies on a fixed-pitch wheel — and it is more of the recognition
 * than the shadow is. The shadow supplies the rest: dark, offset down, under a
 * fill that is brightest at the top edge, which is a raised surface lit from
 * above.
 */
@Composable
private fun Embossed(text: String, size: TextUnit) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.W600,
            fontSize = size,
            letterSpacing = 1.6.sp,
            brush = PolishedInk,
            shadow = EmbossShadow,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A rank badge. Number one is the accent; the rest are quiet, because a chart
 * where every position shouts has no first place.
 */
@Composable
fun RankBadge(rank: Int, accent: Color, modifier: Modifier = Modifier) {
    Text(
        text = rank.toString(),
        style = if (rank == 1) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.titleMedium
        },
        fontWeight = FontWeight.W800,
        color = if (rank == 1) accent else Color.White.copy(alpha = 0.45f),
        modifier = modifier.width(28.dp),
    )
}

/**
 * A stand-in cover for a row that has no artwork — a genre, which is a word
 * rather than a release. Its initial on a colour derived from the word itself,
 * so the same genre is the same colour every time the page is opened.
 */
@Composable
fun InitialTile(text: String, size: Dp, shape: Shape) {
    val hue = (text.hashCode().toFloat() % 360f + 360f) % 360f
    val color = Color.hsl(hue, 0.55f, 0.45f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.take(1).uppercase(Locale.ROOT),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
    }
}

/** The fill on every raised line: bright along the top edge, cooler below. */
private val PolishedInk = Brush.verticalGradient(
    listOf(Color(0xFFFFFFFF), Color(0xFFF3F4F8), Color(0xFFC9CCD6)),
)

/** What makes the fill above read as raised rather than merely pale. */
private val EmbossShadow = Shadow(
    color = Color(0x99000000),
    offset = Offset(0f, 2.5f),
    blurRadius = 4f,
)

private val CardShape = RoundedCornerShape(20.dp)

/** 85.6mm × 54mm, which is what makes the shape read as a card. */
private const val CARD_RATIO = 1.586f
