package com.music.vikify.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.music.vikify.R
import com.music.vikify.data.model.Song
import com.music.vikify.data.model.durationMillis
import com.music.vikify.data.lyrics.EmbeddedLyrics
import com.music.vikify.data.lyrics.LyricsRepository
import com.music.vikify.data.settings.AppSettings
import com.music.vikify.ui.haptics.Haptic
import com.music.vikify.ui.haptics.rememberHaptics
import com.music.vikify.ui.replay.drawLogo
import com.music.vikify.ui.replay.loadBitmap
import com.music.vikify.ui.theme.rememberArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ShareTab(val title: String) {
    SONG("Song"),
    LYRICS("Lyrics"),
    MUSIC_VIDEO("Music video")
}

/**
 * Spotify-inspired rich card share sheet with 120Hz smooth transitions,
 * zero battery waste (native GPU composition on screen, on-demand background export),
 * dynamic album palette colors, and in-app Vikify deep links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VikifyShareSheet(
    song: Song,
    onDismiss: () -> Unit,
    lyricsSnippet: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentTab by remember(song.videoId) { mutableStateOf(ShareTab.SONG) }
    var lyricLines by remember(song.videoId) { mutableStateOf(lyricShareLines(lyricsSnippet)) }
    var lyricsLoading by remember(song.videoId) { mutableStateOf(false) }
    var lyricStart by remember(song.videoId) { mutableIntStateOf(0) }
    val selectedLyrics = remember(lyricLines, lyricStart) { lyricShareExcerpt(lyricLines, lyricStart) }
    LaunchedEffect(song.videoId, lyricsSnippet, currentTab) {
        if (!lyricsSnippet.isNullOrBlank()) lyricLines = lyricShareLines(lyricsSnippet)
        if (currentTab != ShareTab.LYRICS || lyricLines.isNotEmpty()) return@LaunchedEffect
        lyricsLoading = true
        try {
            val embedded = song.localUri?.let { EmbeddedLyrics.forUri(context, it) }
            val found = embedded ?: LyricsRepository.lyrics(
                song.videoId, song.title, song.artist, song.durationMillis(), song.albumName,
                AppSettings.lyricsSources.value, AppSettings.lyricsSourceOrder.value,
            )?.lines
            lyricLines = found.orEmpty().map { it.text.trim() }.filter(String::isNotBlank)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            lyricLines = emptyList()
        } finally {
            lyricsLoading = false
        }
    }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var isSharing by remember { mutableStateOf(false) }
    val palette = rememberArtworkPalette(song.thumbnailUrl)
    // Palette color swatches for card personalization
    val colorSwatches = remember(palette) {
        listOf(
            palette.wash,
            palette.background,
            palette.elevated,
            palette.accent.copy(alpha = 0.5f),
            Color(0xFF161618),
            Color(0xFF0F172A)
        )
    }
    val activeColor = colorSwatches.getOrElse(selectedColorIndex) { Color(0xFF1E1E22) }
    val animatedBgColor by animateColorAsState(
        targetValue = activeColor,
        animationSpec = tween(durationMillis = 250),
        label = "share_card_bg"
    )
    val shareText = remember(song, currentTab, selectedLyrics) {
        buildString {
            if (currentTab == ShareTab.LYRICS && selectedLyrics.isNotBlank()) append("$selectedLyrics\n\n")
            append("🎵 ${song.title} • ${song.artist}\n")
            append("▶ Listen on Vikify: ${com.music.vikify.data.sharing.VikifyLinks.ORIGIN}/track/${song.videoId}\n")
            append("▶ YouTube Music: https://music.youtube.com/watch?v=${song.videoId}")
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121214),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(36.dp))

                // Drag pill
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Central Card Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(390.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                animatedBgColor.copy(alpha = 0.95f),
                                animatedBgColor.copy(alpha = 0.7f),
                                Color(0xFF101012)
                            )
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                when (currentTab) {
                    ShareTab.SONG -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))
                            // Square Album Art
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = song.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(190.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .shadow(12.dp, RoundedCornerShape(14.dp))
                            )
                            // Metadata
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = song.title,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = song.artist,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val album = song.albumName
                                if (!album.isNullOrBlank()) {
                                    Text(
                                        text = album,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // Vikify Brand Pill
                            VikifyBrandPill()
                        }
                    }
                    ShareTab.LYRICS -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top track header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = song.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // Bold Lyric Lines (Spotify Style)
                            val displayLyrics = selectedLyrics.ifBlank {
                                if (lyricsLoading) "Finding this song’s lyrics…" else "No lyrics found for this song. You can still share its song card."
                            }
                            Text(
                                text = displayLyrics,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                lineHeight = 30.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            // Vikify Brand Pill
                            VikifyBrandPill()
                        }
                    }
                    ShareTab.MUSIC_VIDEO -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            // 16:9 Video Preview Card
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                            ) {
                                AsyncImage(
                                    model = song.thumbnailUrl,
                                    contentDescription = "Video preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Video indicator
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Music video • ${song.durationText ?: "HD"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = song.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Vikify Brand Pill
                            VikifyBrandPill()
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Palette Swatches Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                colorSwatches.forEachIndexed { index, color ->
                    val isSelected = index == selectedColorIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                            .clickable {
                                haptics.play(Haptic.Select)
                                selectedColorIndex = index
                            }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Segmented Tab Selector [Song] [Lyrics] [Music video]
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF222226))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShareTab.entries.forEach { tab ->
                    val active = currentTab == tab
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (active) Color.White else Color.Transparent)
                            .clickable {
                                haptics.play(Haptic.Select)
                                currentTab = tab
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) Color.Black else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))

            if (currentTab == ShareTab.LYRICS && lyricLines.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { lyricStart = (lyricStart - 1).coerceAtLeast(0) }, enabled = lyricStart > 0) {
                        Text("Previous lines", color = Color.White.copy(alpha = if (lyricStart > 0) 1f else .4f))
                    }
                    Text("${lyricStart + 1} / ${lyricLines.size}", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                    TextButton(onClick = { lyricStart++ }, enabled = lyricStart + 4 < lyricLines.size) {
                        Text("Next lines", color = Color.White.copy(alpha = if (lyricStart + 4 < lyricLines.size) 1f else .4f))
                    }
                }
            }

            // Action Row: Copy Link, WhatsApp, Stories, Text message, More
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {

                // 1. Copy Link
                ShareActionButton(
                    icon = Icons.Rounded.Link,
                    label = "Copy link",
                    bgColor = Color(0xFF2C2C30),
                    tint = Color.White,
                    enabled = !isSharing && (currentTab != ShareTab.LYRICS || selectedLyrics.isNotBlank())
                ) {
                    haptics.play(Haptic.Tap)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Vikify Share", shareText))
                    Toast.makeText(context, "Copied Vikify share link to clipboard", Toast.LENGTH_SHORT).show()
                }

                // 2. WhatsApp
                ShareActionButton(
                    icon = Icons.Rounded.Share,
                    label = "WhatsApp",
                    bgColor = Color(0xFF25D366),
                    tint = Color.White,
                    enabled = !isSharing && (currentTab != ShareTab.LYRICS || selectedLyrics.isNotBlank())
                ) {
                    haptics.play(Haptic.Tap)
                    scope.launch {
                        isSharing = true
                        val cardUri = generateCardUri(context, song, activeColor, currentTab, selectedLyrics)
                        isSharing = false
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            setPackage("com.whatsapp")
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            if (cardUri != null) {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, cardUri)
                                clipData = ClipData.newRawUri("Vikify card", cardUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } else {
                                type = "text/plain"
                            }
                        }
                        runCatching { context.startActivity(intent) }.onFailure {
                            // Fallback if WhatsApp is not installed
                            val fallback = Intent(Intent.ACTION_SEND).apply {
                                type = if (cardUri != null) "image/png" else "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                if (cardUri != null) {
                                    putExtra(Intent.EXTRA_STREAM, cardUri)
                                clipData = ClipData.newRawUri("Vikify card", cardUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            }
                            context.startActivity(Intent.createChooser(fallback, song.title))
                        }
                    }
                }

                // 3. Instagram Stories
                ShareActionButton(
                    icon = Icons.Rounded.Share,
                    label = "Stories",
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))
                    ),
                    tint = Color.White,
                    enabled = !isSharing && (currentTab != ShareTab.LYRICS || selectedLyrics.isNotBlank())
                ) {
                    haptics.play(Haptic.Tap)
                    scope.launch {
                        isSharing = true
                        val cardUri = generateCardUri(context, song, activeColor, currentTab, selectedLyrics)
                        isSharing = false
                        shareToInstagramStories(context, cardUri, activeColor, shareText, song.title)
                    }
                }

                // 4. Text Message (SMS)
                ShareActionButton(
                    icon = Icons.Rounded.Message,
                    label = "Text message",
                    bgColor = Color(0xFF2C2C30),
                    tint = Color.White,
                    enabled = !isSharing && (currentTab != ShareTab.LYRICS || selectedLyrics.isNotBlank())
                ) {
                    haptics.play(Haptic.Tap)
                    val smsIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    runCatching { context.startActivity(Intent.createChooser(smsIntent, "Send via")) }
                }

                // 5. System Share / More
                ShareActionButton(
                    icon = Icons.Rounded.Share,
                    label = "More",
                    bgColor = Color(0xFF2C2C30),
                    tint = Color.White,
                    enabled = !isSharing && (currentTab != ShareTab.LYRICS || selectedLyrics.isNotBlank())
                ) {
                    haptics.play(Haptic.Tap)
                    scope.launch {
                        isSharing = true
                        val cardUri = generateCardUri(context, song, activeColor, currentTab, selectedLyrics)
                        isSharing = false
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = if (cardUri != null) "image/png" else "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            if (cardUri != null) {
                                putExtra(Intent.EXTRA_STREAM, cardUri)
                                clipData = ClipData.newRawUri("Vikify card", cardUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        context.startActivity(Intent.createChooser(sendIntent, song.title))
                    }
                }
            }
            if (isSharing) {
                Spacer(modifier = Modifier.height(12.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VikifyBrandPill() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = "Vikify",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Vikify",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ShareActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    bgColor: Color = Color.Transparent,
    brush: Brush? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .then(
                    if (brush != null) Modifier.background(brush)
                    else Modifier.background(bgColor)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Offloaded background worker: Renders a crisp 1080x1440 image of the share card
 * and caches it in cacheDir/shared for zero-lag sharing.
 */

/**
 * Helper to share to Instagram Stories with proper URI permission grants and backdrop colors.
 */

private fun shareToInstagramStories(
    context: Context,
    cardUri: Uri?,
    activeColor: Color,
    shareText: String,
    songTitle: String
) {
    if (cardUri == null) {
        Toast.makeText(context, "Couldn’t create the card. Try sharing the song link.", Toast.LENGTH_SHORT).show()
        return
    }
    val hexColor = String.format("#%06X", 0xFFFFFF and activeColor.toArgb())
    val storyIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
        setPackage("com.instagram.android")
        type = "image/png"
        clipData = ClipData.newRawUri("Vikify card", cardUri)
        putExtra("interactive_asset_uri", cardUri)
        putExtra("top_background_color", hexColor)
        putExtra("bottom_background_color", "#101012")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Required for custom URI extras like "interactive_asset_uri" on Android 11+
    runCatching {
        context.grantUriPermission("com.instagram.android", cardUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(storyIntent)
    }.onFailure {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_STREAM, cardUri)
            clipData = ClipData.newRawUri("Vikify card", cardUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(fallback, songTitle))
    }
}

/**
 * Offloaded background worker: Renders a crisp 1080x1440 image matching the Compose UI
 * with vertical gradient, center-cropped rounded artwork, and StaticLayout text wrapping.
 */

private suspend fun generateCardUri(
    context: Context,
    song: Song,
    cardColor: Color,
    tab: ShareTab,
    lyrics: String?,
): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        val width = 1080
        val height = 1440
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Outer sheet background
        canvas.drawColor(android.graphics.Color.parseColor("#121214"))

        // Card container rect with vertical gradient matching Compose preview
        val cardRect = RectF(60f, 60f, width - 60f, height - 60f)
        val topColor = cardColor.copy(alpha = 0.95f).toArgb()
        val midColor = cardColor.copy(alpha = 0.70f).toArgb()
        val bottomColor = android.graphics.Color.parseColor("#101012")
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, cardRect.top, 0f, cardRect.bottom,
                intArrayOf(topColor, midColor, bottomColor),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(cardRect, 56f, 56f, bgPaint)

        // Subtle 1px inner border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = android.graphics.Color.argb(22, 255, 255, 255)
        }
        canvas.drawRoundRect(cardRect, 56f, 56f, borderPaint)
        val cover = song.thumbnailUrl?.let { runCatching { loadBitmap(context, it) }.getOrNull() }
        if (tab == ShareTab.SONG || tab == ShareTab.MUSIC_VIDEO) {
            if (cover != null) {
                val artRect = if (tab == ShareTab.SONG) {
                    val size = width - 320f // 760x760 square
                    RectF(160f, 150f, 160f + size, 150f + size)
                } else {
                    val w = width - 240f
                    val h = w * 9f / 16f
                    RectF(120f, 240f, 120f + w, 240f + h)
                }
                drawCenterCroppedRoundedBitmap(canvas, cover, artRect, cornerRadius = 38f)
            }

            // Title (auto-wrapping up to 2 lines)
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 54f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawMultilineText(
                canvas = canvas,
                text = song.title,
                paint = titlePaint,
                width = (width - 240),
                x = 120f,
                y = height - 340f,
                maxLines = 2
            )

            // Artist
            val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#CCCCCC")
                textSize = 38f
                typeface = android.graphics.Typeface.DEFAULT
            }
            drawMultilineText(
                canvas = canvas,
                text = song.artist,
                paint = artistPaint,
                width = (width - 240),
                x = 120f,
                y = height - 210f,
                maxLines = 1
            )
        } else {

            // Lyrics Card with StaticLayout wrapping
            val quote = lyrics?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 60f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawMultilineText(
                canvas = canvas,
                text = quote,
                paint = lyricPaint,
                width = (width - 240),
                x = 120f,
                y = 260f,
                maxLines = 6,
                lineSpacingMultiplier = 1.25f
            )
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#CCCCCC")
                textSize = 36f
            }
            drawMultilineText(
                canvas = canvas,
                text = "${song.title} • ${song.artist}",
                paint = subPaint,
                width = (width - 240),
                x = 120f,
                y = height - 230f,
                maxLines = 1
            )
        }

        // Vikify brand mark
        drawLogo(canvas, context, width - 140f, height - 120f)
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 36f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("Vikify", 120f, height - 120f, brandPaint)

        // Clean up older cached cards and write cache-busted filename
        val folder = File(context.cacheDir, "shared").apply {
            mkdirs()
            listFiles()?.filter { it.name.startsWith("vikify_card_") &&
                System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        }
        val stateHash = "${tab.name}_${java.util.UUID.randomUUID()}"
        val file = File(folder, "vikify_card_$stateHash.png")
        FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}

private fun drawCenterCroppedRoundedBitmap(
    canvas: Canvas,
    source: Bitmap,
    dstRect: RectF,
    cornerRadius: Float
) {
    val saveCount = canvas.save()
    val path = Path().apply { addRoundRect(dstRect, cornerRadius, cornerRadius, Path.Direction.CW) }
    canvas.clipPath(path)
    val scale: Float
    var dx = 0f
    var dy = 0f
    if (source.width * dstRect.height() > dstRect.width() * source.height) {
        scale = dstRect.height() / source.height.toFloat()
        dx = (dstRect.width() - source.width * scale) * 0.5f
    } else {
        scale = dstRect.width() / source.width.toFloat()
        dy = (dstRect.height() - source.height * scale) * 0.5f
    }
    val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate(dstRect.left + dx, dstRect.top + dy)
    }
    canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    canvas.restoreToCount(saveCount)
}

private fun drawMultilineText(
    canvas: Canvas,
    text: String,
    paint: TextPaint,
    width: Int,
    x: Float,
    y: Float,
    maxLines: Int,
    lineSpacingMultiplier: Float = 1.1f
) {
    val staticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, lineSpacingMultiplier)
        .setIncludePad(false)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
    canvas.save()
    canvas.translate(x, y)
    staticLayout.draw(canvas)
    canvas.restore()
}
