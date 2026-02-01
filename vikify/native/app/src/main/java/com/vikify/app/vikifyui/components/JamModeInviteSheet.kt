package com.vikify.app.vikifyui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.theme.*

/**
 * JAM MODE INVITE SHEET
 * 
 * Bottom sheet for creating or joining a Jam session.
 * 
 * Two options:
 * 1. "Start a Jam" - Create new session as host
 * 2. "Join a Jam" - Enter 6-digit code to join
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamModeInviteSheet(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onCreateSession: () -> Unit,
    onJoinSession: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VikifyTheme.colors
    
    // ═══════════════════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════════════════
    
    var showJoinInput by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    
    // Focus the input when switching to join mode
    LaunchedEffect(showJoinInput) {
        if (showJoinInput) {
            focusRequester.requestFocus()
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // RENDER
    // ═══════════════════════════════════════════════════════════════════
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceSheet,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.textTertiary) },
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = colors.brandPrimary,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Jam Mode",
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Listen together with a friend in real-time",
                color = colors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Error message
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.error.copy(alpha = 0.1f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = colors.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // ─────────────────────────────────────────────────────────
            // OPTION 1: Start a Jam
            // ─────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = !showJoinInput,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                JamOptionButton(
                    icon = Icons.Rounded.PlayCircle,
                    title = "Start a Jam",
                    subtitle = "Create a session and invite a friend",
                    accentColor = colors.brandPrimary,
                    isLoading = isLoading,
                    onClick = onCreateSession
                )
            }
            
            if (!showJoinInput) {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // ─────────────────────────────────────────────────────────
            // OPTION 2: Join a Jam
            // ─────────────────────────────────────────────────────────
            AnimatedContent(
                targetState = showJoinInput,
                transitionSpec = {
                    fadeIn() + expandVertically() togetherWith fadeOut() + shrinkVertically()
                },
                label = "joinInput"
            ) { isJoining ->
                if (isJoining) {
                    // Show code input
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Enter 6-digit code",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Code input field
                        BasicTextField(
                            value = joinCode,
                            onValueChange = { newValue ->
                                // Only allow digits, max 6
                                if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                                    joinCode = newValue
                                }
                            },
                            textStyle = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 8.sp,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (joinCode.length == 6) {
                                        onJoinSession(joinCode)
                                    }
                                }
                            ),
                            cursorBrush = SolidColor(colors.brandPrimary),
                            decorationBox = { innerTextField ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.surfaceCard)
                                        .border(
                                            width = 2.dp,
                                            color = if (joinCode.length == 6) colors.brandPrimary else colors.border,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(vertical = 20.dp)
                                ) {
                                    if (joinCode.isEmpty()) {
                                        Text(
                                            text = "______",
                                            color = colors.textTertiary,
                                            fontSize = 32.sp,
                                            letterSpacing = 8.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Join button
                        Button(
                            onClick = { onJoinSession(joinCode) },
                            enabled = joinCode.length == 6 && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.brandSecondary,
                                disabledContainerColor = colors.textTertiary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Join Session",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Back button
                        TextButton(onClick = { showJoinInput = false }) {
                            Text(
                                text = "Back",
                                color = colors.textSecondary
                            )
                        }
                    }
                } else {
                    // Show "Join a Jam" button
                    JamOptionButton(
                        icon = Icons.Rounded.GroupAdd,
                        title = "Join a Jam",
                        subtitle = "Enter a code to join a friend's session",
                        accentColor = colors.brandSecondary,
                        onClick = { showJoinInput = true }
                    )
                }
            }
        }
    }
}

/**
 * Option button for Jam Mode actions
 */
@Composable
private fun JamOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = VikifyTheme.colors
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.15f),
                        accentColor.copy(alpha = 0.05f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.2f))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }
            
            // Arrow
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary
            )
        }
    }
}
