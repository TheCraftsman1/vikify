package com.vikify.app.vikifyui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vikify.app.vikifyui.theme.NavBackground
import com.vikify.app.vikifyui.theme.NavIconActive
import com.vikify.app.vikifyui.theme.NavIconInactive
import com.vikify.app.vikifyui.theme.Divider

/**
 * Bottom Navigation
 * 
 * Floating glass style, 3 tabs: Home, Search, Library
 * Sits above mini player
 */

enum class NavTab(
    val label: String,
    val iconOutlined: ImageVector,
    val iconFilled: ImageVector
) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Search("Search", Icons.Outlined.Search, Icons.Filled.Search),
    Library("Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic)
}

@Composable
fun BottomNav(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NavBackground)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavTab.values().forEach { tab ->
            NavItem(
                tab = tab,
                isSelected = tab == currentTab,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

@Composable
private fun NavItem(
    tab: NavTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (isSelected) tab.iconFilled else tab.iconOutlined,
            contentDescription = tab.label,
            tint = if (isSelected) NavIconActive else NavIconInactive,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) NavIconActive else NavIconInactive
        )
    }
}

