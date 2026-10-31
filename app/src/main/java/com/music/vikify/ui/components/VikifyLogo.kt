package com.music.vikify.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.music.vikify.R

/**
 * Renders the 3D Vikify waveform logo dynamically based on phone system Light / Dark mode.
 * - In Dark Mode: Displays the glossy White 3D wave logo
 * - In Light Mode: Displays the sleek Black 3D wave logo
 */
@Composable
fun VikifyLogo(
    modifier: Modifier = Modifier,
    isDark: Boolean = isSystemInDarkTheme(),
    contentDescription: String? = "Vikify Logo",
    contentScale: ContentScale = ContentScale.Fit,
) {
    val resId = if (isDark) R.drawable.logo_wave_dark else R.drawable.logo_wave_light
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
