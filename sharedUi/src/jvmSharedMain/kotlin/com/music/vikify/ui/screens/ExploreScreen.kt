package com.music.vikify.ui.screens

import com.music.vikify.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.vikify.data.model.HomeShelf
import com.music.vikify.data.model.MoodGenre
import com.music.vikify.data.model.MoodGenreSection
import com.music.vikify.data.model.ShelfItem
import com.music.vikify.data.model.UiState
import com.music.vikify.ui.components.MessageState
import com.music.vikify.ui.components.PAGE_GUTTER
import com.music.vikify.ui.components.PullToRefresh
import com.music.vikify.ui.components.ShimmerBox
import com.music.vikify.ui.components.feedSkeleton
import com.music.vikify.ui.components.thumbnailBorder
import com.music.vikify.ui.icons.VikifyIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    state: UiState<List<MoodGenreSection>>,
    listState: LazyListState,
    onCategoryClick: (MoodGenre) -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    pullState: PullToRefreshState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** The big "Explore" heading; the desktop's pages carry none. */
    showTitle: Boolean = true,
) {
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = moodColumns(maxWidth, LocalDensity.current.fontScale)
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showTitle) {
                    item(key = "explore-title") { ExploreTitle() }
                }
                when (state) {
                    UiState.Loading -> {
                        item {
                            ShimmerBox(Modifier.padding(horizontal = PAGE_GUTTER, vertical = 10.dp).width(128.dp).height(20.dp))
                        }
                        items(5) { ExploreSkeletonRow(columns) }
                    }
                    is UiState.Error -> item {
                        MessageState(state.message, actionLabel = stringResource(Res.string.retry), onAction = onRetry)
                    }
                    is UiState.Success -> {
                        val seen = mutableSetOf<Pair<String, String?>>()
                        val sections = state.data.mapNotNull { section ->
                            val categories = section.items.filter { seen.add(it.browseId to it.params) }
                            categories.takeIf { it.isNotEmpty() }?.let { section.copy(items = it) }
                        }
                        sections.forEachIndexed { sectionIndex, section ->
                            item(key = "heading:${section.items.first().exploreKey}") {
                                if (sectionIndex > 0) Spacer(Modifier.height(12.dp))
                                SectionHeader(section.title)
                            }
                            val rows = section.items.chunked(columns)
                            items(rows, key = { it.first().exploreKey }) { row ->
                                MoodGenreRow(
                                    row = row,
                                    columns = columns,
                                    onCategoryClick = onCategoryClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val MoodGenre.exploreKey: String
    get() = "category:${browseId.length}:$browseId${params?.let { ":$it" }.orEmpty()}"

@Composable
private fun ExploreTitle() {
    Column(Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp)) {
        Text(
            text = stringResource(Res.string.explore),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.explore_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun MoodGenreRow(
    row: List<MoodGenre>,
    columns: Int,
    onCategoryClick: (MoodGenre) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MOOD_SPACING),
        modifier = Modifier
            .padding(horizontal = PAGE_GUTTER)
            .padding(bottom = MOOD_SPACING),
    ) {
        row.forEach { item ->
            MoodGenreCard(
                item = item,
                onClick = { onCategoryClick(item) },
                modifier = Modifier.weight(1f),
            )
        }
        // Short rows keep their cards the size of a full row's.
        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun MoodGenreCard(
    item: MoodGenre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = remember(item.stripeColor, item.title) { moodColor(item) }
    Box(
        modifier = modifier
            .clip(EXPLORE_CARD_SHAPE)
            .thumbnailBorder(EXPLORE_CARD_SHAPE)
            .background(lerp(accent, Color.Black, .58f))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Spacer(Modifier.fillMaxWidth().aspectRatio(EXPLORE_CARD_ASPECT))
        Icon(
            imageVector = VikifyIcons.MusicNote,
            contentDescription = null,
            tint = accent.copy(alpha = .52f),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(54.dp),
        )
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .28f to Color.Black.copy(alpha = .70f),
                        1f to Color.Black.copy(alpha = .88f),
                    ),
                )
                .padding(start = 12.dp, top = 22.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = .8f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * A solid stripe down the left and the category's cover filling the rest,
 * redrawn as a duotone of the stripe's colour so every card reads as one
 * tinted sleeve rather than a photo pasted onto a swatch.
 *
 * Only the artwork: the caller sizes, clips and makes it clickable, which is
 * how the TV app wraps the same sleeve in its own focus treatment.
 */
@Composable
fun MoodGenreSleeve(
    item: MoodGenre,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    val tone = remember(item.stripeColor, item.title) { moodTone(item) }
    Box(modifier = modifier.background(tone.stripe)) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(1f - MOOD_STRIPE_FRACTION)
                .background(tone.placeholder),
        ) {
            item.thumbnailUrl?.let { artwork ->
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = tone.duotone,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Pale stripes (Energize's cream, Feel good's mint) would swallow
        // white type; a soft shade under the title keeps it legible.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .24f), .62f to Color.Transparent)),
        )
        Text(
            text = item.title,
            style = titleStyle.copy(
                shadow = Shadow(Color.Black.copy(alpha = .35f), offset = Offset(0f, 1f), blurRadius = 6f),
            ),
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

private class MoodTone(val stripe: Color, val placeholder: Color, val duotone: ColorFilter)

private fun moodColor(item: MoodGenre): Color =
    item.stripeColor?.let { Color(it.toInt()) } ?: fallbackMoodColor(item.title)

private fun moodTone(item: MoodGenre): MoodTone {
    val base = moodColor(item)
    // YouTube's palette is already pastel; a touch of black keeps it from
    // glowing against the black page.
    val stripe = lerp(base, Color.Black, .08f)
    val shadow = lerp(base, Color.Black, .68f)
    val highlight = lerp(base, Color.White, .32f)
    return MoodTone(
        stripe = stripe,
        placeholder = lerp(shadow, highlight, .45f),
        duotone = duotone(shadow, highlight),
    )
}

/**
 * Maps each pixel's luminance onto the ramp [shadow] to [highlight]. Offsets
 * are in 0..255, which Compose honours on Skia too (it rescales them).
 */
private fun duotone(shadow: Color, highlight: Color): ColorFilter {
    fun channel(from: Float, to: Float): FloatArray {
        val span = to - from
        return floatArrayOf(span * .299f, span * .587f, span * .114f, 0f, from * 255f)
    }
    return ColorFilter.colorMatrix(
        ColorMatrix(
            channel(shadow.red, highlight.red) +
                channel(shadow.green, highlight.green) +
                channel(shadow.blue, highlight.blue) +
                floatArrayOf(0f, 0f, 0f, 1f, 0f),
        ),
    )
}

/** For a button that came without YouTube's colour: muted, never neon. */
private fun fallbackMoodColor(title: String): Color = when ((title.hashCode() and Int.MAX_VALUE) % 8) {
    0 -> Color(0xFFCC6A55)
    1 -> Color(0xFFC07A92)
    2 -> Color(0xFF9C8AC0)
    3 -> Color(0xFF8090C8)
    4 -> Color(0xFFD0A060)
    5 -> Color(0xFF6A88B0)
    6 -> Color(0xFF7AAED0)
    else -> Color(0xFF86B890)
}

/** Gap between mood cards, in both directions. */
private val MOOD_SPACING = 12.dp

private val EXPLORE_CARD_SHAPE = RoundedCornerShape(18.dp)
private const val EXPLORE_CARD_ASPECT = 1.28f

/** Width over height; a two-column phone row lands at about 100dp tall. */
const val MOOD_CARD_ASPECT = 1.72f

/** How much of the card the solid stripe covers before the artwork starts. */
private const val MOOD_STRIPE_FRACTION = .28f

/** Keep two columns on phones and add columns as the layout gets wider. */
private val MOOD_MIN_CARD_WIDTH = 168.dp

private const val MOOD_MAX_COLUMNS = 6

private fun moodColumns(available: Dp, fontScale: Float): Int {
    val row = available - PAGE_GUTTER * 2
    val largeText = fontScale >= 1.5f
    val minCardWidth = if (largeText) MOOD_MIN_CARD_WIDTH * fontScale else MOOD_MIN_CARD_WIDTH
    val minColumns = if (available >= 344.dp && !largeText) 2 else 1
    return ((row + MOOD_SPACING) / (minCardWidth + MOOD_SPACING)).toInt().coerceIn(minColumns, MOOD_MAX_COLUMNS)
}

@Composable
private fun ExploreSkeletonRow(columns: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MOOD_SPACING),
        modifier = Modifier.padding(start = PAGE_GUTTER, end = PAGE_GUTTER, bottom = MOOD_SPACING),
    ) {
        repeat(columns) {
            ShimmerBox(Modifier.weight(1f).aspectRatio(EXPLORE_CARD_ASPECT), EXPLORE_CARD_SHAPE)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodGenrePlaylistsScreen(
    title: String,
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    onItemClick: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
            )
        }
        when (state) {
            UiState.Loading -> feedSkeleton()
            is UiState.Error -> item {
                MessageState(state.message, actionLabel = stringResource(Res.string.retry), onAction = onRetry)
            }
            is UiState.Success -> items(state.data, key = { it.title }) { shelf ->
                Shelf(shelf = shelf, onItemClick = onItemClick)
            }
        }
    }
}
