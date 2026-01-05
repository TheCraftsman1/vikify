package com.vikify.app.vikifyui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vikify.app.db.entities.Playlist
import com.vikify.app.vikifyui.theme.LocalVikifyColors

@Composable
fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onDismissRequest: () -> Unit,
    onPlaylistSelected: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showCreateInput by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = LocalVikifyColors.current.surfaceCard,
        title = {
            Text(
                if (showCreateInput) "New Playlist" else "Add to Playlist",
                fontWeight = FontWeight.Bold,
                color = LocalVikifyColors.current.textPrimary
            )
        },
        text = {
            if (showCreateInput) {
                Column {
                    Text(
                        "Playlist Name",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalVikifyColors.current.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        singleLine = true,
                        placeholder = { Text("My Playlist") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LocalVikifyColors.current.accent,
                            unfocusedBorderColor = LocalVikifyColors.current.border,
                            focusedTextColor = LocalVikifyColors.current.textPrimary,
                            unfocusedTextColor = LocalVikifyColors.current.textPrimary
                        )
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp) // Limit height
                ) {
                    // "New Playlist" Item
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCreateInput = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LocalVikifyColors.current.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = LocalVikifyColors.current.accent
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "New Playlist",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalVikifyColors.current.textPrimary
                            )
                        }
                    }

                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaylistSelected(playlist.id) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                             Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(LocalVikifyColors.current.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.PlaylistPlay,
                                    contentDescription = null,
                                    tint = LocalVikifyColors.current.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = LocalVikifyColors.current.textPrimary
                                )
                                Text(
                                    "${playlist.songCount} songs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalVikifyColors.current.textSecondary
                                )
                            }
                        }
                    }
                    
                    if (playlists.isEmpty()) {
                        item {
                           Text(
                                "No playlists found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalVikifyColors.current.textSecondary,
                                modifier = Modifier.padding(top = 16.dp)
                           )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateInput) {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreatePlaylist(newPlaylistName)
                            showCreateInput = false
                            newPlaylistName = ""
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Create", color = LocalVikifyColors.current.accent)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (showCreateInput) {
                    showCreateInput = false
                } else {
                    onDismissRequest()
                }
            }) {
                Text("Cancel", color = LocalVikifyColors.current.textSecondary)
            }
        }
    )
}
