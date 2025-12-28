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
    modifier: Modifier = Modifier
) {
    val isDark = VikifyTheme.isDark
    
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
                ProfileHeader(
                    name = userName ?: "Vikify User",
                    joinedText = "Member since 2025"
                )
            }
            
            // 2. STATS GRID (Wrapped Style)
            item {
                Spacer(modifier = Modifier.height(Spacing.LG))
                StatsGrid(likedSongsCount = likedSongsCount)
            }
            
            // 3. TOP ARTISTS RAIL
            item {
                Spacer(modifier = Modifier.height(Spacing.XL))
                TopArtistsRail()
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
                    currentTheme = isDarkTheme
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------
// 1. PROFILE HERO
// ----------------------------------------------------------------------------
@Composable
private fun ProfileHeader(name: String, joinedText: String) {
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
        
        Text(
            text = joinedText,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalVikifyColors.current.textSecondary
        )
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = { /* Edit Profile */ },
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, LocalVikifyColors.current.border),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text("Edit Profile", color = LocalVikifyColors.current.textPrimary)
        }
    }
}

// ----------------------------------------------------------------------------
// 2. STATS GRID (The Wrapped Feel)
// ----------------------------------------------------------------------------
@Composable
private fun StatsGrid(likedSongsCount: Int) {
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
                value = "1,240m",
                label = "Listened",
                color = GlowBlue,
                modifier = Modifier.weight(1f)
            )
            
            // Like Count
            StatCard(
                icon = Icons.Rounded.Favorite,
                value = "$likedSongsCount",
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
                value = "Pop",
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
        modifier = modifier.height(100.dp)
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
@Composable
private fun TopArtistsRail() {
    Column {
        PaddingValues(horizontal = Spacing.MD).let { padding ->
            Text(
                text = "Top Artists",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = LocalVikifyColors.current.textPrimary,
                modifier = Modifier.padding(padding)
            )
        }
        
        Spacer(Modifier.height(Spacing.MD))
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.MD),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(5) { index ->
                ArtistCircle(name = "Artist ${index + 1}")
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
    currentTheme: Boolean // true = dark
) {
    // Local state for mock settings (would be in ViewModel/Prefs later)
    var gaplessEnabled by remember { mutableStateOf(true) }
    var normalizeEnabled by remember { mutableStateOf(true) }
    
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
        
        Spacer(modifier = Modifier.height(Spacing.LG))
        
        // Playback
        SettingsSectionHeader("PLAYBACK")
        
        SettingsRow(
            icon = Icons.Outlined.Speed,
            title = "Gapless Playback",
            subtitle = "Seamless track transitions",
            onClick = { gaplessEnabled = !gaplessEnabled },
            trailingContent = {
                Switch(
                    checked = gaplessEnabled,
                    onCheckedChange = { gaplessEnabled = it },
                    colors = switchColors()
                )
            }
        )
        
        SettingsRow(
            icon = Icons.Outlined.GraphicEq,
            title = "Normalize Volume",
            subtitle = "Consistent loudness",
            onClick = { normalizeEnabled = !normalizeEnabled },
            trailingContent = {
                Switch(
                    checked = normalizeEnabled,
                    onCheckedChange = { normalizeEnabled = it },
                    colors = switchColors()
                )
            }
        )
        
        Spacer(modifier = Modifier.height(Spacing.LG))
        
        // Data & Privacy
        SettingsSectionHeader("DATA & PRIVACY")
        
        SettingsRow(
            icon = Icons.Outlined.Storage,
            title = "Clear Cache",
            subtitle = "458 MB", // Mock data
            onClick = { /* TODO: Implement clear cache */ }
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


