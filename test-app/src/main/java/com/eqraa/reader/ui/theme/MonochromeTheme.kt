/*
 * Premium Monochrome Glassy Theme System
 * Implements minimalist black & white design with glassmorphism effects
 */

package com.eqraa.reader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Monochrome theme with glassmorphism support
 * Provides a premium, minimalist reading experience
 */
data class MonochromeTheme(
    // Base colors
    val background: Color,
    val surface: Color,
    val surfaceAlpha: Float,      // For frosted glass effect
    
    // Text colors
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    
    // Accent & highlights
    val accent: Color,
    val accentSecondary: Color,
    
    // Glassmorphism properties
    val glassBlur: Dp,
    val glassSurface: Color,
    val glassStroke: Color,
    val glassStrokeWidth: Dp,
    
    // Shadows & elevation
    val shadowColor: Color,
    val elevation: Dp,
    
    // Interactive states
    val ripple: Color,
    val divider: Color
) {
    companion object {
        /**
         * Pure White theme - Clean, bright, minimal
         */
        val PureWhite = MonochromeTheme(
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFAFAFA),
            surfaceAlpha = 0.85f,
            
            text = Color(0xFF000000),
            textSecondary = Color(0xFF6B6B6B),
            textTertiary = Color(0xFF9E9E9E),
            
            accent = Color(0xFF2C2C2C),
            accentSecondary = Color(0xFF757575),
            
            // Glassmorphism
            glassBlur = 16.dp,
            glassSurface = Color(0xF0FAFAFA),
            glassStroke = Color(0x1A000000),
            glassStrokeWidth = 0.5.dp,
            
            shadowColor = Color(0x14000000),
            elevation = 4.dp,
            
            ripple = Color(0x12000000),
            divider = Color(0x1F000000)
        )
        
        /**
         * Deep Black theme - Premium OLED-optimized dark mode
         */
        val DeepBlack = MonochromeTheme(
            background = Color(0xFF000000),
            surface = Color(0xFF0A0A0A),
            surfaceAlpha = 0.85f,
            
            text = Color(0xFFFFFFFF),
            textSecondary = Color(0xFFB8B8B8),
            textTertiary = Color(0xFF757575),
            
            accent = Color(0xFFEAEAEA),
            accentSecondary = Color(0xFF999999),
            
            // Glassmorphism
            glassBlur = 16.dp,
            glassSurface = Color(0xF00A0A0A),
            glassStroke = Color(0x1AFFFFFF),
            glassStrokeWidth = 0.5.dp,
            
            shadowColor = Color(0x28000000),
            elevation = 4.dp,
            
            ripple = Color(0x12FFFFFF),
            divider = Color(0x1FFFFFFF)
        )
        
        /**
         * Classic Paper theme - Warm, book-like experience
         */
        val ClassicPaper = MonochromeTheme(
            background = Color(0xFFF5F1E8),
            surface = Color(0xFFEDE9E0),
            surfaceAlpha = 0.85f,
            
            text = Color(0xFF2C2C2C),
            textSecondary = Color(0xFF666660),
            textTertiary = Color(0xFF999990),
            
            accent = Color(0xFF404038),
            accentSecondary = Color(0xFF737370),
            
            // Glassmorphism
            glassBlur = 16.dp,
            glassSurface = Color(0xF0EDE9E0),
            glassStroke = Color(0x1A000000),
            glassStrokeWidth = 0.5.dp,
            
            shadowColor = Color(0x14000000),
            elevation = 4.dp,
            
            ripple = Color(0x12000000),
            divider = Color(0x1F000000)
        )
        
        /**
         * Charcoal Gray theme - Sophisticated mid-tone
         */
        val Charcoal = MonochromeTheme(
            background = Color(0xFF1C1C1C),
            surface = Color(0xFF242424),
            surfaceAlpha = 0.85f,
            
            text = Color(0xFFE8E8E8),
            textSecondary = Color(0xFFA8A8A8),
            textTertiary = Color(0xFF707070),
            
            accent = Color(0xFFD8D8D8),
            accentSecondary = Color(0xFF909090),
            
            // Glassmorphism
            glassBlur = 16.dp,
            glassSurface = Color(0xF0242424),
            glassStroke = Color(0x1AFFFFFF),
            glassStrokeWidth = 0.5.dp,
            
            shadowColor = Color(0x24000000),
            elevation = 4.dp,
            
            ripple = Color(0x12FFFFFF),
            divider = Color(0x1FFFFFFF)
        )
    }
}

/**
 * Theme preferences enum
 */
enum class MonochromeThemePreset {
    PURE_WHITE,
    DEEP_BLACK,
    CLASSIC_PAPER,
    CHARCOAL;
    
    fun toTheme(): MonochromeTheme = when (this) {
        PURE_WHITE -> MonochromeTheme.PureWhite
        DEEP_BLACK -> MonochromeTheme.DeepBlack
        CLASSIC_PAPER -> MonochromeTheme.ClassicPaper
        CHARCOAL -> MonochromeTheme.Charcoal
    }
}
