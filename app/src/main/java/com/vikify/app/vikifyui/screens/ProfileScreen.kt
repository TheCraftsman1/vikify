package com.vikify.app.vikifyui.screens

import com.vikify.app.R

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.theme.*
import com.vikify.app.vikifyui.components.VikifyGlassCard
import androidx.hilt.navigation.compose.hiltViewModel


/**
 * Profile Screen
 * 
 * Clean Profile with:
 * - Spotify connection
 * - Quick stats (Liked Songs, Playlists)
 * - Settings section
 */

@Composable
fun ProfileScreen(
    onSpotifyLogin: () -> Unit = {},
    spotifyUser: com.vikify.app.spotify.SpotifyUser? = null,
    spotifyPlaylists: List<com.vikify.app.spotify.SpotifyPlaylist> = emptyList(),
    isSpotifyLoggedIn: Boolean = false,
    onSpotifyLogout: () -> Unit = {},
    likedSongsCount: Int = 0,
    onLikedSongsClick: () -> Unit = {},
    onThemeToggle: () -> Unit = {},
    isDarkTheme: Boolean = true,
    userName: String? = null,
    userEmail: String? = null,
    onTimeCapsuleClick: () -> Unit = {},
    onSignIn: () -> Unit = {},
    isGuest: Boolean = false,
    onNameChange: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: com.vikify.app.viewmodels.ProfileViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val isDark = VikifyTheme.isDark
    
    // Edit Profile Dialog State
    var showEditDialog by remember { mutableStateOf(false) }
    var editedName by remember(userName) { mutableStateOf(userName ?: "") }
    
    // Collect Real State
    val gapless by viewModel.gaplessEnabled.collectAsState()
    val normalize by viewModel.normalizeEnabled.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()
    val animatedBackground by viewModel.animatedBackground.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    
    // Refresh stats on load
    LaunchedEffect(Unit) {
        viewModel.refreshStats()
        viewModel.calculateCacheSize()
    }
    
    // Edit Profile Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Display Name", style = MaterialTheme.typography.labelMedium, color = LocalVikifyColors.current.textSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LocalVikifyColors.current.accent,
                            unfocusedBorderColor = LocalVikifyColors.current.border
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onNameChange(editedName)
                        showEditDialog = false
                    }
                ) {
                    Text("Save", color = LocalVikifyColors.current.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = LocalVikifyColors.current.textSecondary)
                }
            },
            containerColor = LocalVikifyColors.current.surface,
            titleContentColor = LocalVikifyColors.current.textPrimary,
            textContentColor = LocalVikifyColors.current.textPrimary
        )
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        if (isDark) {
            LivingBackground(modifier = Modifier.matchParentSize()) { }
        } else {
            EtherealBackground(modifier = Modifier.matchParentSize()) { }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            // 1. HERO IDENTITY (Google/Guest User)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.vikify.app.R.drawable.vikify_logo),
                        contentDescription = null,
                        modifier = Modifier.size(50.dp)
                    )
                }
                ProfileHeader(
                    name = userName ?: "Vikify User",
                    email = userEmail,
                    joinedText = "Member since 2025",
                    isGuest = isGuest,
                    onEditClick = if (isGuest) onSignIn else { { showEditDialog = true } }
                )
            }
            
            // 2. STATS GRID (Wrapped Style)
            item {
                Spacer(modifier = Modifier.height(Spacing.LG))
                StatsGrid(stats = stats)
            }
            
            // 2.5 TIME CAPSULE PORTAL
            item {
                Spacer(modifier = Modifier.height(Spacing.MD))
                SonicDnaPortal(onClick = onTimeCapsuleClick)
            }
            
            // 3. TOP ARTISTS RAIL
            item {
                Spacer(modifier = Modifier.height(Spacing.XL))
                TopArtistsRail(artists = topArtists)
            }
            
            // 4. CONNECTED SERVICES (Spotify)
            item {
                Spacer(modifier = Modifier.height(Spacing.XL))
                ConnectedServicesSection(
                    isSpotifyLoggedIn = isSpotifyLoggedIn,
                    spotifyUser = spotifyUser,
                    onSpotifyLogin = onSpotifyLogin,
                    onSpotifyLogout = onSpotifyLogout,
                    playlistCount = spotifyPlaylists.size
                )
            }
            
            // 5. SETTINGS
            item {
                Spacer(modifier = Modifier.height(Spacing.XL))
                AccountSettingsSection(
                    onThemeToggle = onThemeToggle,
                    currentTheme = isDarkTheme,
                    animatedBackground = animatedBackground,
                    onAnimatedBackgroundChange = viewModel::setAnimatedBackground,
                    gapless = gapless,
                    normalize = normalize,
                    audioQuality = audioQuality,
                    cacheSize = cacheSize,
                    onGaplessChange = viewModel::setGapless,
                    onNormalizeChange = viewModel::setNormalize,
                    onQualityChange = viewModel::setAudioQuality,
                    onClearCache = viewModel::clearCache
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 1. PROFILE HERO
// ----------------------------------------------------------------------------
@Composable
private fun ProfileHeader(
    name: String, 
    email: String?,
    joinedText: String,
    isGuest: Boolean,
    onEditClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar with Golden Ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp)
        ) {
            // Rotating Gradient Ring
            val infiniteTransition = rememberInfiniteTransition(label = "ring")
            val rotate by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing)
                ),
                label = "rotate"
            )
            
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .rotate(rotate)
                    .border(
                        width = 3.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(VikifyPurple, GlowBlue, VikifyPurple)
                        ),
                        shape = CircleShape
                    )
            )
            
            // Avatar Image/Initials
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(LocalVikifyColors.current.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(2).uppercase(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold, 
                    color = LocalVikifyColors.current.accent
                )
            }
        }
                        
        Spacer(Modifier.height(16.dp))
                        
        Text(
            text = name,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold, 
            color = LocalVikifyColors.current.textPrimary
        )

        if (email != null) {
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalVikifyColors.current.accent
            )
            Spacer(Modifier.height(4.dp))
        }
        
        Text(
            text = joinedText,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalVikifyColors.current.textSecondary
        )
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = onEditClick,
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, LocalVikifyColors.current.border),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (isGuest) "Sign In & Sync" else "Edit Profile",
                color = LocalVikifyColors.current.textPrimary
            )
        }
    }
}

// ----------------------------------------------------------------------------
// 2. STATS GRID (The Wrapped Feel)
// ----------------------------------------------------------------------------
@Composable
private fun StatsGrid(stats: com.vikify.app.viewmodels.ProfileStats) {
    Column(modifier = Modifier.padding(horizontal = Spacing.MD)) {
        Text(
            text = "Your 2025",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LocalVikifyColors.current.textPrimary
        )
        Spacer(Modifier.height(Spacing.MD))
        
        // 2x2 Grid using Column/Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.SM)
                        ) {
            // Minutes Listened
            StatCard(
                icon = Icons.Rounded.Schedule,
                value = "${stats.minutesListened}m",
                label = "Listened",
                color = GlowBlue,
                modifier = Modifier.weight(1f)
            )
            
            // Like Count
            StatCard(
                icon = Icons.Rounded.Favorite,
                value = "${stats.likedSongsCount}",
                label = "Likes",
                color = VikifyPurple,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(Modifier.height(Spacing.SM))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.SM)
        ) {
            // Top Genre
            StatCard(
                icon = Icons.Rounded.GraphicEq,
                value = stats.topGenre,
                label = "Top Genre",
                color = Color(0xFF10B981), // Green
                modifier = Modifier.weight(1f)
            )
            
            // Energy Score
            StatCard(
                icon = Icons.Rounded.Bolt,
                value = "92%",
                label = "Energy",
                color = Color(0xFFF59E0B), // Amber
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier
) {
    VikifyGlassCard(
        modifier = modifier.height(110.dp)
    ) {
        Column(
                        modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LocalVikifyColors.current.textPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = LocalVikifyColors.current.textSecondary
            )
        }
    }
}

// ----------------------------------------------------------------------------
// 3. TOP ARTISTS RAIL
// ----------------------------------------------------------------------------
// ----------------------------------------------------------------------------
// 3. TOP ARTISTS RAIL
// ----------------------------------------------------------------------------
@Composable
private fun TopArtistsRail(artists: List<String>) {
    Column {
        Text(
            text = "Top Artists",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LocalVikifyColors.current.textPrimary,
            modifier = Modifier.padding(horizontal = Spacing.MD)
        )
        
        Spacer(Modifier.height(Spacing.MD))
        
        if (artists.isEmpty()) {
            Text(
                text = "Keep listening to discover your favorites",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalVikifyColors.current.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.MD)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.MD),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(artists) { artistName ->
                    ArtistCircle(name = artistName)
                }
            }
        }
    }
}


@Composable
private fun ArtistCircle(name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(LocalVikifyColors.current.divider),
            contentAlignment = Alignment.Center
                    ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = LocalVikifyColors.current.textSecondary
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalVikifyColors.current.textPrimary
        )
                    }
                }

// ----------------------------------------------------------------------------
// 4. CONNECTED SERVICES
// ----------------------------------------------------------------------------
@Composable
private fun ConnectedServicesSection(
    isSpotifyLoggedIn: Boolean,
    spotifyUser: com.vikify.app.spotify.SpotifyUser?,
    onSpotifyLogin: () -> Unit,
    onSpotifyLogout: () -> Unit,
    playlistCount: Int
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.MD)) {
        Text(
            text = "Connected Services",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LocalVikifyColors.current.textPrimary
        )
        Spacer(Modifier.height(Spacing.MD))
        
        VikifyGlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Spotify Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF1DB954), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Spotify",
                        tint = Color.White
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSpotifyLoggedIn) spotifyUser?.displayName ?: "Spotify Connected" else "Spotify",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalVikifyColors.current.textPrimary
                    )
                    Text(
                        text = if (isSpotifyLoggedIn) "$playlistCount playlists synced" else "Connect to import playlists",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalVikifyColors.current.textSecondary
                    )
                }
                
                if (isSpotifyLoggedIn) {
                    IconButton(onClick = onSpotifyLogout) {
                        Icon(Icons.Outlined.Logout, null, tint = LocalVikifyColors.current.error)
                    }
                } else {
                    Button(
                        onClick = onSpotifyLogin,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}


@Composable

private fun AccountSettingsSection(
    onThemeToggle: () -> Unit,
    currentTheme: Boolean,
    animatedBackground: Boolean,
    onAnimatedBackgroundChange: (Boolean) -> Unit,
    gapless: Boolean,
    normalize: Boolean,
    audioQuality: String,
    cacheSize: String,
    onGaplessChange: (Boolean) -> Unit,
    onNormalizeChange: (Boolean) -> Unit,
    onQualityChange: (String) -> Unit,
    onClearCache: () -> Unit
) {
    // Quality Selection Dialog
    var showQualityDialog by remember { mutableStateOf(false) }
    
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Audio Quality", color = LocalVikifyColors.current.textPrimary) },
            text = {
                Column {
                    listOf("Low", "High", "Super").forEach { quality ->
                         Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                                     onQualityChange(quality)
                                     showQualityDialog = false 
                        }
                                 .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                             RadioButton(
                                 selected = audioQuality == quality,
                                 onClick = null, // Handled by row
                                 colors = RadioButtonDefaults.colors(
                                     selectedColor = LocalVikifyColors.current.accent,
                                     unselectedColor = LocalVikifyColors.current.textSecondary
                                 )
                             )
                             Spacer(modifier = Modifier.width(8.dp))
                             Text(
                                 text = quality,
                                 style = MaterialTheme.typography.bodyLarge,
                                 color = LocalVikifyColors.current.textPrimary
                             )
                         }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Cancel", color = LocalVikifyColors.current.accent)
                }
            },
            containerColor = LocalVikifyColors.current.surfaceElevated,
            textContentColor = LocalVikifyColors.current.textSecondary
        )
    }

    Column(modifier = Modifier.padding(horizontal = Spacing.MD)) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LocalVikifyColors.current.textPrimary
        )
        
        Spacer(modifier = Modifier.height(Spacing.MD))
        
        // Appearance
        SettingsSectionHeader("APPEARANCE")
        SettingsRow(
            icon = if (currentTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
            title = "Dark Mode",
            subtitle = null,
            onClick = onThemeToggle,
            trailingContent = {
                Switch(
                    checked = currentTheme,
                    onCheckedChange = { onThemeToggle() },
                    colors = switchColors()
                )
            }
        )
        
        SettingsRow(
            icon = Icons.Outlined.Animation,
            title = "Animated Background",
            subtitle = "Floating color orbs",
            onClick = { onAnimatedBackgroundChange(!animatedBackground) },
            trailingContent = {
                Switch(
                    checked = animatedBackground,
                    onCheckedChange = onAnimatedBackgroundChange,
                    colors = switchColors()
                )
            }
        )
        
        Spacer(modifier = Modifier.height(Spacing.LG))
        
        // Playback
        SettingsSectionHeader("PLAYBACK")
                
        SettingsRow(
            icon = Icons.Outlined.Speed,
            title = "Gapless Playback",
            subtitle = "Seamless track transitions",
            onClick = { onGaplessChange(!gapless) },
            trailingContent = {
                Switch(
                    checked = gapless,
                    onCheckedChange = { onGaplessChange(it) },
                    colors = switchColors()
                )
            }
                )
                
        SettingsRow(
            icon = Icons.Outlined.GraphicEq,
            title = "Normalize Volume",
            subtitle = "Consistent loudness",
            onClick = { onNormalizeChange(!normalize) },
            trailingContent = {
                Switch(
                    checked = normalize,
                    onCheckedChange = { onNormalizeChange(it) },
                    colors = switchColors()
                )
            }
                )
        
        SettingsRow(
            icon = Icons.Outlined.HighQuality,
            title = "Audio Quality",
            subtitle = audioQuality,
            onClick = { showQualityDialog = true }
        )
        
        Spacer(modifier = Modifier.height(Spacing.LG))
        
        // Data & Privacy
        SettingsSectionHeader("DATA & PRIVACY")
        
        SettingsRow(
            icon = Icons.Outlined.Storage,
            title = "Clear Cache",
            subtitle = cacheSize, 
            onClick = onClearCache
        )
        
        // About
        SettingsRow(
            icon = Icons.Outlined.Info,
            title = "About Vikify",
            subtitle = "Version 1.0.0",
            onClick = { }
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalVikifyColors.current.textSecondary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = Spacing.SM, start = Spacing.SM)
    )
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = LocalVikifyColors.current.accent,
    checkedTrackColor = LocalVikifyColors.current.surfaceElevated,
    uncheckedThumbColor = LocalVikifyColors.current.textSecondary,
    uncheckedTrackColor = LocalVikifyColors.current.surface
)

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
            .background(LocalVikifyColors.current.surface.copy(alpha = 0.5f)) // Glassy feel
            .clickable(onClick = onClick)
            .padding(Spacing.MD),
        horizontalArrangement = Arrangement.spacedBy(Spacing.MD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = LocalVikifyColors.current.textSecondary,
            modifier = Modifier.size(24.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = LocalVikifyColors.current.textPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalVikifyColors.current.textSecondary
                )
            }
        }
        
        if (trailingContent != null) {
            trailingContent()
        } else {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Open",
                tint = LocalVikifyColors.current.divider,
                modifier = Modifier.size(20.dp)
            )
    }
}
    
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SonicDnaPortal(onClick: () -> Unit) {
    VikifyGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = Spacing.MD)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black, Color(0xFF1E1E2E))
                        )
                    )
            )
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Sonic DNA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Enter the Time Capsule",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome, // Or a better galaxy icon
                    contentDescription = null,
                    tint = Color(0xFFFFA500),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}


