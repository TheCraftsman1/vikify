package com.music.bitchord.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.personalization.MixCopyDraft

@Composable
fun PersonalMixControls(title: String, following: Boolean, canSave: Boolean,
    requestDraft: () -> MixCopyDraft?, onFollow: (Boolean, (Boolean) -> Unit) -> Unit,
    onSave: (MixCopyDraft, String, (Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<MixCopyDraft?>(null) }
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 8.dp)) {
        Text(stringResource(R.string.mix_follow_copy_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = !busy, onClick = {
                busy = true
                onFollow(!following) { saved ->
                    busy = false
                    if (!saved) Toast.makeText(context, R.string.home_collections_save_failed, Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(if (following) R.string.mix_unfollow else R.string.mix_follow)) }
            TextButton(enabled = canSave && !busy, onClick = { draft = requestDraft(); name = "$title copy".take(120) }) {
                Text(stringResource(R.string.mix_save_copy))
            }
        }
    }
    val selected = draft
    if (selected != null) AlertDialog(
        onDismissRequest = { if (!busy) draft = null },
        title = { Text(stringResource(R.string.mix_save_copy)) },
        text = { Column {
            Text(stringResource(R.string.mix_copy_local_hint))
            OutlinedTextField(value = name, onValueChange = { name = it.take(120) }, enabled = !busy,
                label = { Text(stringResource(R.string.mix_copy_name)) }, singleLine = true)
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !busy, onClick = {
            busy = true
            onSave(selected, name) { saved ->
                busy = false
                if (saved) draft = null
                Toast.makeText(context, if (saved) R.string.mix_copy_saved else R.string.mix_copy_failed, Toast.LENGTH_SHORT).show()
            }
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { draft = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun SavedMixCopyControls(title: String, onRename: (String, (Boolean) -> Unit) -> Unit,
    onDelete: ((Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(title) }
    Column(Modifier.fillMaxWidth().padding(horizontal = PAGE_GUTTER, vertical = 8.dp)) {
        Text(stringResource(R.string.mix_copy_description), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            TextButton(enabled = !busy, onClick = { name = title; renaming = true }) { Text(stringResource(R.string.rename)) }
            TextButton(enabled = !busy, onClick = { deleting = true }) { Text(stringResource(R.string.delete)) }
        }
    }
    if (renaming) AlertDialog(onDismissRequest = { if (!busy) renaming = false },
        title = { Text(stringResource(R.string.rename)) },
        text = { OutlinedTextField(name, { name = it.take(120) }, enabled = !busy,
            label = { Text(stringResource(R.string.mix_copy_name)) }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !busy, onClick = {
            busy = true
            onRename(name) { saved ->
                busy = false
                if (saved) renaming = false else Toast.makeText(context, R.string.mix_copy_failed, Toast.LENGTH_SHORT).show()
            }
        }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } })
    if (deleting) AlertDialog(onDismissRequest = { if (!busy) deleting = false },
        title = { Text(stringResource(R.string.mix_copy_delete_title)) }, text = { Text(stringResource(R.string.mix_copy_delete_hint)) },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            onDelete { saved ->
                busy = false
                if (saved) deleting = false else Toast.makeText(context, R.string.mix_copy_failed, Toast.LENGTH_SHORT).show()
            }
        }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } })
}
