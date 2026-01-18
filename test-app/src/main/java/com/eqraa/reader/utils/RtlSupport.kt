/*
 * RTL Support Utilities
 * Enhanced right-to-left language support for Arabic and Hebrew
 */

package com.eqraa.reader.utils

import androidx.compose.ui.unit.LayoutDirection
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.ReadingProgression
import java.util.Locale

/**
 * RTL language detection and utilities
 */
object RtlSupport {
    
    /**
     * RTL language codes
     */
    private val RTL_LANGUAGES = setOf(
        "ar",  // Arabic
        "he",  // Hebrew
        "fa",  // Persian (Farsi)
        "ur",  // Urdu
        "yi"   // Yiddish
    )
    
    /**
     * Detect if content is RTL based on metadata
     */
    fun isRtl(metadata: Metadata): Boolean {
        // Check reading progression
        when (metadata.readingProgression) {
            ReadingProgression.RTL -> return true
            ReadingProgression.LTR -> return false
            else -> {} // Continue with language detection
        }
        
        // Check language
        metadata.languages.firstOrNull()?.let { lang ->
            val languageCode = lang.take(2).lowercase()
            if (RTL_LANGUAGES.contains(languageCode)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Detect if text content is RTL
     */
    fun isRtlText(text: String): Boolean {
        if (text.isEmpty()) return false
        
        // Check first few non-whitespace characters
        val firstChars = text.filter { !it.isWhitespace() }.take(10)
        var rtlCount = 0
        
        for (char in firstChars) {
            if (isRtlCharacter(char)) {
                rtlCount++
            }
        }
        
        // If more than 50% are RTL characters, consider it RTL
        return rtlCount > firstChars.length / 2
    }
    
    /**
     * Check if character is RTL
     */
    private fun isRtlCharacter(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.ARABIC ||
               block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
               block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B ||
               block == Character.UnicodeBlock.HEBREW ||
               block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
    }
    
    /**
     * Get layout direction for compose
     */
    fun getLayoutDirection(isRtl: Boolean): LayoutDirection {
        return if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    }
    
    /**
     * Mirror navigation direction for RTL
     */
    fun mirrorNavigation(direction: NavigationDirection, isRtl: Boolean): NavigationDirection {
        if (!isRtl) return direction
        
        return when (direction) {
            NavigationDirection.FORWARD -> NavigationDirection.BACKWARD
            NavigationDirection.BACKWARD -> NavigationDirection.FORWARD
        }
    }
    
    /**
     * Get swipe direction considering RTL
     */
    fun getSwipeDirection(deltaX: Float, isRtl: Boolean): NavigationDirection? {
        val threshold = 50f
        
        return when {
            deltaX > threshold -> {
                if (isRtl) NavigationDirection.FORWARD else NavigationDirection.BACKWARD
            }
            deltaX < -threshold -> {
                if (isRtl) NavigationDirection.BACKWARD else NavigationDirection.FORWARD
            }
            else -> null
        }
    }
    
    /**
     * Format BiDi text for display
     * Ensures proper display of mixed LTR/RTL content
     */
    fun formatBidiText(text: String): String {
        // Use Unicode BiDi control characters
        return if (isRtlText(text)) {
            "\u202B$text\u202C" // RLE (Right-to-Left Embedding) + PDF
        } else {
            text
        }
    }
}

/**
 * Navigation direction
 */
enum class NavigationDirection {
    FORWARD,
    BACKWARD
}
