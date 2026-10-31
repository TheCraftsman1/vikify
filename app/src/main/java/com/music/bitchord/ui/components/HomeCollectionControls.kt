package com.music.bitchord.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.mood.MoodGenreRepository
import com.music.bitchord.data.personalization.HomeCollectionPreferences

@Composable
fun HomeCollectionControls(
    collectionId: String?, preferences: HomeCollectionPreferences, saving: Boolean,
    onPin: (String, Boolean) -> Unit, onHide: (String, Boolean) -> Unit,
) {
    val collections = MoodGenreRepository.allMoods.filter { collectionId == null || it.id == collectionId }
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Text(collections.singleOrNull()?.title ?: stringResource(R.string.home_manage_collections),
            style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_collection_controls_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(collections, key = { it.id }) { collection ->
                val pinned = collection.id in preferences.pinned
                val hidden = collection.id in preferences.hidden
                val pinLabel = stringResource(if (pinned) R.string.home_collection_unpin_named else R.string.home_collection_pin_named, collection.title)
                val visibilityLabel = stringResource(if (hidden) R.string.home_collection_show_named else R.string.home_collection_hide_named, collection.title)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(collection.title, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(if (hidden) R.string.home_collection_hidden else if (pinned)
                            R.string.home_collection_pinned else R.string.home_collection_visible),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(enabled = !saving, onClick = { onPin(collection.id, !pinned) },
                        modifier = Modifier.semantics { contentDescription = pinLabel }) {
                        Text(stringResource(if (pinned) R.string.home_collection_unpin else R.string.home_collection_pin))
                    }
                    TextButton(enabled = !saving, onClick = { onHide(collection.id, !hidden) },
                        modifier = Modifier.semantics { contentDescription = visibilityLabel }) {
                        Text(stringResource(if (hidden) R.string.home_collection_show else R.string.home_collection_hide))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
