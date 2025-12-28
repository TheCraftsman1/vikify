package com.vikify.app.vikifyui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Vikify Typography - "4K" High-Fidelity Text
 * 
 * THE SECRET SAUCE:
 * - includeFontPadding = false (removes Android's extra top padding)
 * - LineHeightStyle.Alignment.Center (perfect vertical alignment like iOS)
 * - Tight letter spacing for large headlines (-0.5sp)
 * 
 * This is why Spotify/Apple Music text looks "crisp" and Android apps look "loose"
 */

// The iOS-style platform text config applied to ALL styles
private val PremiumTextStyle = PlatformTextStyle(includeFontPadding = false)
private val PremiumLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

val VikifyTypography = Typography(
    // ═══════════════════════════════════════════════════════════════
    // DISPLAY - Large heroes, splash screens
    // ═══════════════════════════════════════════════════════════════
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,  // Tight for premium feel
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    
    // ═══════════════════════════════════════════════════════════════
    // HEADLINES - Section headers, important text
    // ═══════════════════════════════════════════════════════════════
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    
    // ═══════════════════════════════════════════════════════════════
    // TITLES - Card titles, list item titles
    // ═══════════════════════════════════════════════════════════════
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    
    // ═══════════════════════════════════════════════════════════════
    // BODY - Content text
    // ═══════════════════════════════════════════════════════════════
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    
    // ═══════════════════════════════════════════════════════════════
    // LABELS - Buttons, chips, metadata
    // ═══════════════════════════════════════════════════════════════
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        platformStyle = PremiumTextStyle,
        lineHeightStyle = PremiumLineHeightStyle
    )
)
