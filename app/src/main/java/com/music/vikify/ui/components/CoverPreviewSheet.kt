package com.music.vikify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.vikify.R
import com.music.vikify.data.covers.CoverCatalog
import com.music.vikify.data.covers.CoverSpec
import com.music.vikify.data.covers.CoverUri
import kotlinx.coroutines.delay

/** The same artwork system as the preview, attached to a real playlist draft. */
@Composable
internal fun PlaylistCoverEditor(title: String, onCoverChange: (String) -> Unit) {
    var template by remember { mutableStateOf(CoverCatalog.templates.first()) }
    var palette by remember { mutableStateOf(CoverCatalog.palettes.first()) }
    val cleanTitle = remember(title) { CoverCatalog.title(title.ifBlank { "Your playlist" }) }
    val spec = remember(cleanTitle, template, palette) {
        CoverSpec("playlist_draft", cleanTitle,
            locale = if (cleanTitle.any { it in '\u0C00'..'\u0C7F' }) "te-IN" else "en-IN",
            templateId = template.id, paletteId = palette.id, variant = 1)
    }
    val uri = remember(spec) { CoverUri.encode(spec, 720) }
    LaunchedEffect(uri) { onCoverChange(uri) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Playlist cover", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AsyncImage(uri, contentDescription = "$cleanTitle cover", contentScale = ContentScale.Fit,
                modifier = Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)))
            Text("Make it yours. Your title appears on the cover as you type.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverCatalog.templates.forEach { option -> FilterChip(selected = template == option,
                onClick = { template = option }, label = { Text(option.name) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverCatalog.palettes.forEach { option -> FilterChip(selected = palette == option,
                onClick = { palette = option }, label = { Text(option.name) }, leadingIcon = {
                    Spacer(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(option.accent)))
                }) }
        }
    }
}

@Composable
fun CoverPreviewSheet() {
    var title by remember { mutableStateOf("Telugu Melodies") }
    var composedTitle by remember { mutableStateOf(title) }
    var locale by remember { mutableStateOf("en-IN") }
    var template by remember { mutableStateOf(CoverCatalog.templates.first()) }
    var palette by remember { mutableStateOf(CoverCatalog.palettes.first()) }
    LaunchedEffect(title) { delay(300); composedTitle = CoverCatalog.title(title) }
    val spec = remember(composedTitle, locale, template, palette) { CoverSpec("language_telugu_melodies",
        composedTitle, locale, template.id, palette.id, variant = 1) }
    Column(Modifier.fillMaxWidth().heightIn(max = 790.dp).verticalScroll(rememberScrollState())
        .navigationBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.cover_preview), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.cover_preview_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(title, { if (it.codePointCount(0, it.length) <= 120) title = it },
            label = { Text(stringResource(R.string.cover_title)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row {
            TextButton(onClick = { locale = "en-IN"; title = "Telugu Melodies" }) { Text("English") }
            TextButton(onClick = { locale = "te-IN"; title = "తెలుగు మెలోడీస్" }) { Text("తెలుగు") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverCatalog.templates.forEach { option -> FilterChip(selected = template == option,
                onClick = { template = option }, label = { Text(option.name) }) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverCatalog.palettes.forEach { option -> FilterChip(selected = palette == option,
                onClick = { palette = option }, label = { Text(option.name) }, leadingIcon = {
                    Spacer(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(option.accent)))
                }) }
        }
        AsyncImage(CoverUri.encode(spec, 720), contentDescription = composedTitle, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)))
        Text(stringResource(R.string.cover_small_preview), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(CoverUri.encode(spec, 192), contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(composedTitle, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.cover_preview_only), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(R.string.cover_reference), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage("file:///android_asset/moods/on_repeat.webp", contentDescription = "On Repeat reference", modifier = Modifier.size(110.dp))
            AsyncImage(CoverUri.encode(spec, 192), contentDescription = composedTitle, modifier = Modifier.size(110.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}
