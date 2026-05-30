package com.vikify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vikify.app.ui.viewmodels.AudioFxViewModel
import com.vikify.app.ui.theme.VikifyTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onBackClick: () -> Unit,
    viewModel: AudioFxViewModel = hiltViewModel()
) {
    val bands by viewModel.equalizerBands.collectAsState()
    val bassStrength by viewModel.bassStrength.collectAsState()
    val virtualizerStrength by viewModel.virtualizerStrength.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Engine") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VikifyTheme.colors.surfaceBackground,
                    titleContentColor = VikifyTheme.colors.textPrimary,
                    navigationIconContentColor = VikifyTheme.colors.textPrimary
                )
            )
        },
        containerColor = VikifyTheme.colors.surfaceBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Bass & Virtualizer
            Card(
                colors = CardDefaults.cardColors(containerColor = VikifyTheme.colors.surfaceElevated),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Effects",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VikifyTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    // Bass
                    Text("Bass Boost: ${(bassStrength / 10.0).roundToInt()}%", color = VikifyTheme.colors.textSecondary)
                    Slider(
                        value = bassStrength.toFloat(),
                        onValueChange = { viewModel.setBassStrength(it.toInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = VikifyTheme.colors.brandSecondary,
                            activeTrackColor = VikifyTheme.colors.brandSecondary
                        )
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    // Virtualizer
                    Text("Virtualizer: ${(virtualizerStrength / 10.0).roundToInt()}%", color = VikifyTheme.colors.textSecondary)
                    Slider(
                        value = virtualizerStrength.toFloat(),
                        onValueChange = { viewModel.setVirtualizerStrength(it.toInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = VikifyTheme.colors.brandPrimary,
                            activeTrackColor = VikifyTheme.colors.brandPrimary
                        )
                    )
                }
            }
            
            // Equalizer Bands
            Card(
                colors = CardDefaults.cardColors(containerColor = VikifyTheme.colors.surfaceElevated),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Equalizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VikifyTheme.colors.textPrimary
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bands.forEach { band ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Vertical Slider
                                VerticalSlider(
                                    value = band.currentLevel.toFloat(),
                                    min = band.minLevel.toFloat(),
                                    max = band.maxLevel.toFloat(),
                                    onValueChange = { viewModel.setBandLevel(band.index, it.toInt().toShort()) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 8.dp)
                                )
                                
                                Text(
                                    text = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000}k" else "${band.centerFreqHz}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VikifyTheme.colors.textTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalSlider(
    value: Float,
    min: Float,
    max: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Rotated Slider
        // -90 degrees makes it vertical growing upwards
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            colors = SliderDefaults.colors(
                thumbColor = VikifyTheme.colors.brandSecondary,
                activeTrackColor = VikifyTheme.colors.brandSecondary,
                inactiveTrackColor = VikifyTheme.colors.surface
            ),
            modifier = Modifier
                .graphicsLayer {
                    rotationZ = 270f
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
                .layout { measurable, constraints ->
                    // Swap width and height constraints
                    val placeable = measurable.measure(
                        Constraints(
                            minWidth = constraints.minHeight,
                            maxWidth = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(
                            -placeable.width / 2 + placeable.height / 2,
                            -placeable.height / 2 + placeable.width / 2
                        )
                    }
                }
                .fillMaxWidth() // Fill parent height (due to rotation)
        )
    }
}
