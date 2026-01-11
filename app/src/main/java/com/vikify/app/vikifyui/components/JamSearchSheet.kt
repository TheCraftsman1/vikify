package com.vikify.app.vikifyui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
// Removed ambiguous VikifyColors import, using MaterialTheme instead
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Bottom sheet for searching and adding songs to the Jam queue
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamSearchSheet(
    onDismissRequest: () -> Unit,
    onSearch: suspend (String) -> List<Track>,
    onTrackSelected: (Track) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E), // Dark background
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.XL)
                .fillMaxHeight(0.85f)
        ) {
            // Header with Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.MD, vertical = Spacing.SM)
            ) {
                Text(
                    text = "Add to Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = Spacing.MD)
                )
                
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search songs...") },
                    leadingIcon = { 
                        Icon(
                            Icons.Rounded.Search, 
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f)
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (query.isNotEmpty()) {
                            focusManager.clearFocus()
                            isLoading = true
                            scope.launch {
                                results = onSearch(query)
                                isLoading = false
                            }
                        }
                    })
                )
            }
            
            Divider(color = Color.White.copy(alpha = 0.1f))
            
            Box(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (results.isNotEmpty()) {
                    LazyColumn(
                        contentPadding = PaddingValues(Spacing.MD)
                    ) {
                        items(results) { track ->
                            JamSearchItem(
                                track = track,
                                onAdd = {
                                    onTrackSelected(track)
                                    onDismissRequest() // Close after adding
                                }
                            )
                            Spacer(modifier = Modifier.height(Spacing.SM))
                        }
                    }
                } else if (query.isNotEmpty() && !isLoading) {
                    // Empty state (no results)
                     Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.XL),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No results found",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    // Initial state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.XL),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Search for tracks on YouTube to add them to the Jam session.",
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JamSearchItem(
    track: Track,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .clickable { onAdd() }
            .padding(Spacing.SM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork
        AsyncImage(
            model = track.remoteArtworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        )
        
        Spacer(modifier = Modifier.width(Spacing.MD))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Add Button
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Add to Queue",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = Spacing.XS)
                .size(24.dp)
        )
    }
}
