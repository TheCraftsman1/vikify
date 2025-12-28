package com.vikify.app.vikifyui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.theme.*

/**
 * Sleep Timer Duration Options
 */
enum class SleepTimerDuration(val label: String, val minutes: Long) {
    MINUTES_15("15 minutes", 15),
    MINUTES_30("30 minutes", 30),
    MINUTES_45("45 minutes", 45),
    HOUR_1("1 hour", 60),
    END_OF_TRACK("End of current track", -1), // Special case
    OFF("Off", 0)
}

/**
 * Sleep Timer State
 */
data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0,
    val duration: SleepTimerDuration = SleepTimerDuration.OFF,
    val endTime: Long = 0
) {
    val remainingFormatted: String
        get() {
            if (!isActive || remainingMs <= 0) return ""
            val totalSeconds = remainingMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return if (minutes > 0) {
                "${minutes}m ${seconds}s"
            } else {
                "${seconds}s"
            }
        }
}

/**
 * Sleep Timer Dialog
 * 
 * Shows options to set a sleep timer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerDialog(
    currentState: SleepTimerState,
    onSelectDuration: (SleepTimerDuration) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF8F8F8),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.LightGray)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = VikifyTheme.colors.textPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Sleep Timer",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = VikifyTheme.colors.textPrimary
                    )
                    if (currentState.isActive) {
                        Text(
                            "Stops in ${currentState.remainingFormatted}",
                            fontSize = 14.sp,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
            
            Divider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = Color.LightGray.copy(alpha = 0.5f)
            )
            
            // Timer Options
            SleepTimerDuration.entries.forEach { duration ->
                if (duration != SleepTimerDuration.OFF || currentState.isActive) {
                    val isSelected = currentState.duration == duration && currentState.isActive
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectDuration(duration)
                                onDismiss()
                            }
                            .background(if (isSelected) Color(0xFFE8F5E9) else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = duration.label,
                            fontSize = 16.sp,
                            color = if (duration == SleepTimerDuration.OFF) Color(0xFFE57373) else VikifyTheme.colors.textPrimary,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Sleep Timer Indicator
 * 
 * Small badge shown in player when timer is active
 */
@Composable
fun SleepTimerIndicator(
    state: SleepTimerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.isActive) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Bedtime,
                contentDescription = "Sleep Timer",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = state.remainingFormatted,
                fontSize = 12.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
