/*
 * Premium Reading Fonts Collection
 * Curated selection of the 5 most beloved fonts for comfortable reading
 */

package com.eqraa.reader.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.eqraa.reader.R

/**
 * The most loved reading fonts
 */
sealed class ReadingFont(
    val displayName: String,
    val fontFamily: FontFamily
) {
    /**
     * Georgia - Classic serif, timeless elegance
     * Perfect for long reading sessions
     */
    object Georgia : ReadingFont(
        displayName = "Georgia",
        fontFamily = FontFamily.Serif
    )
    
    /**
     * Literata - Google's premium book font used in Play Books
     * Optimized for body text and extended reading
     */
    object Literata : ReadingFont(
        displayName = "Literata",
        fontFamily = FontFamily(
            Font(R.font.literata_regular, FontWeight.Normal)
        )
    )
    
    /**
     * Source Serif Pro - Adobe's beautiful open-source reading font
     * Excellent readability with elegant design
     */
    object SourceSerifPro : ReadingFont(
        displayName = "Source Serif",
        fontFamily = FontFamily(
            Font(R.font.source_serif_regular, FontWeight.Normal)
        )
    )

    /**
     * Classic Serif - System serif font
     * Universal compatibility
     */
    object ClassicSerif : ReadingFont(
        displayName = "Classic",
        fontFamily = FontFamily.Serif
    )

    /**
     * Monospace - For code or technical content
     */
    object Monospace : ReadingFont(
        displayName = "Monospace",
        fontFamily = FontFamily.Monospace
    )
    
    companion object {
        /**
         * Get all available reading fonts
         */
        fun all(): List<ReadingFont> = listOf(
            Georgia,
            Literata,
            SourceSerifPro,
            ClassicSerif,
            Monospace
        )
        
        /**
         * Get font by name
         */
        fun fromName(name: String): ReadingFont = when (name) {
            "Georgia" -> Georgia
            "Literata" -> Literata
            "Source Serif" -> SourceSerifPro
            "Classic" -> ClassicSerif
            "Monospace" -> Monospace
            else -> Georgia // Default fallback
        }
    }
}

/**
 * Font weight preferences for reading
 */
enum class ReadingFontWeight {
    LIGHT,
    REGULAR,
    MEDIUM,
    SEMIBOLD;
    
    fun toFontWeight(): FontWeight = when (this) {
        LIGHT -> FontWeight.Light
        REGULAR -> FontWeight.Normal
        MEDIUM -> FontWeight.Medium
        SEMIBOLD -> FontWeight.SemiBold
    }
}

