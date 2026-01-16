/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package com.eqraa.reader.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Reading preferences manager using SharedPreferences
 */
class ReadingPreferences(context: Context, private val bookId: String? = null) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("reading_prefs", Context.MODE_PRIVATE)
    
    companion object {
        // Keys
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_FONT_WEIGHT = "font_weight"
        private const val KEY_MARGIN_SIZE = "margin_size"
        private const val KEY_LINE_HEIGHT = "line_height"
        private const val KEY_TEXT_ALIGN = "text_align"
        private const val KEY_THEME = "theme"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_VOLUME_PAGE_TURN = "volume_page_turn"
        private const val KEY_AI_ENABLED = "ai_enabled"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_GROQ_API_KEY = "groq_api_key"
        private const val KEY_OLLAMA_URL = "ollama_url"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_CEREBRAS_API_KEY = "cerebras_api_key"
        private const val KEY_IS_EDIT_MODE = "is_edit_mode"
        private const val KEY_AUTO_BOOKMARK = "auto_bookmark"
        
        // Defaults
        const val DEFAULT_FONT_FAMILY = "Newsreader"
        const val DEFAULT_FONT_SIZE = 18
        const val DEFAULT_FONT_WEIGHT = 400
        const val DEFAULT_MARGIN_SIZE = 1 // 0=small, 1=medium, 2=large
        const val DEFAULT_LINE_HEIGHT = 1 // 0=compact, 1=normal, 2=relaxed
        const val DEFAULT_TEXT_ALIGN = 0 // 0=left, 1=justify
        const val DEFAULT_THEME = 0 // 0=paper, 1=sepia, 2=dark
    }

    private fun getKey(baseKey: String): String {
        return if (bookId != null) "book_${bookId}_$baseKey" else baseKey
    }

    private fun getString(key: String, default: String): String {
        return if (bookId != null && prefs.contains("book_${bookId}_$key")) {
            prefs.getString("book_${bookId}_$key", default) ?: default
        } else {
            prefs.getString(key, default) ?: default
        }
    }

    private fun getInt(key: String, default: Int): Int {
        return if (bookId != null && prefs.contains("book_${bookId}_$key")) {
            prefs.getInt("book_${bookId}_$key", default)
        } else {
            prefs.getInt(key, default)
        }
    }

    private fun getBoolean(key: String, default: Boolean): Boolean {
        return if (bookId != null && prefs.contains("book_${bookId}_$key")) {
            prefs.getBoolean("book_${bookId}_$key", default)
        } else {
            prefs.getBoolean(key, default)
        }
    }

    private fun putString(key: String, value: String) {
        prefs.edit().putString(getKey(key), value).apply()
    }

    private fun putInt(key: String, value: Int) {
        prefs.edit().putInt(getKey(key), value).apply()
    }

    private fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(getKey(key), value).apply()
    }
    
    // Font Family
    var fontFamily: String
        get() = getString(KEY_FONT_FAMILY, DEFAULT_FONT_FAMILY)
        set(value) = putString(KEY_FONT_FAMILY, value)
    
    // Font Size (12-32pt)
    var fontSize: Int
        get() = getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        set(value) = putInt(KEY_FONT_SIZE, value.coerceIn(12, 32))
    
    // Font Weight (100-900)
    var fontWeight: Int
        get() = getInt(KEY_FONT_WEIGHT, DEFAULT_FONT_WEIGHT)
        set(value) = putInt(KEY_FONT_WEIGHT, value.coerceIn(100, 900))
    
    // Margin Size (0=small, 1=medium, 2=large)
    var marginSize: Int
        get() = getInt(KEY_MARGIN_SIZE, DEFAULT_MARGIN_SIZE)
        set(value) = putInt(KEY_MARGIN_SIZE, value.coerceIn(0, 2))
    
    // Line Height (0=compact, 1=normal, 2=relaxed)
    var lineHeight: Int
        get() = getInt(KEY_LINE_HEIGHT, DEFAULT_LINE_HEIGHT)
        set(value) = putInt(KEY_LINE_HEIGHT, value.coerceIn(0, 2))
    
    // Text Alignment (0=left, 1=justify)
    var textAlign: Int
        get() = getInt(KEY_TEXT_ALIGN, DEFAULT_TEXT_ALIGN)
        set(value) = putInt(KEY_TEXT_ALIGN, value.coerceIn(0, 1))
    
    // Theme (0=paper, 1=sepia, 2=dark)
    var theme: Int
        get() = getInt(KEY_THEME, DEFAULT_THEME)
        set(value) = putInt(KEY_THEME, value.coerceIn(0, 2))
    
    
    // Keep Screen On
    var keepScreenOn: Boolean
        get() = getBoolean(KEY_KEEP_SCREEN_ON, false)
        set(value) = putBoolean(KEY_KEEP_SCREEN_ON, value)
    
    // Volume Page Turn
    var volumePageTurn: Boolean
        get() = getBoolean(KEY_VOLUME_PAGE_TURN, false)
        set(value) = putBoolean(KEY_VOLUME_PAGE_TURN, value)
    
    // AI Enabled
    var aiEnabled: Boolean
        get() = getBoolean(KEY_AI_ENABLED, true)
        set(value) = putBoolean(KEY_AI_ENABLED, value)
    
    // AI Provider (0: Groq, 1: OpenRouter, 2: Ollama, 3: Gemini, 4: Cerebras)
    var aiProvider: Int
        get() = getInt(KEY_AI_PROVIDER, 0)
        set(value) = putInt(KEY_AI_PROVIDER, value)
    
    // Groq API Key
    var groqApiKey: String
        get() = getString(KEY_GROQ_API_KEY, "")
        set(value) = putString(KEY_GROQ_API_KEY, value)
    
    // Ollama URL
    var ollamaUrl: String
        get() = getString(KEY_OLLAMA_URL, "http://10.0.2.2:11434")
        set(value) = putString(KEY_OLLAMA_URL, value)
    
    // Gemini API Key
    var geminiApiKey: String
        get() = getString(KEY_GEMINI_API_KEY, "")
        set(value) = putString(KEY_GEMINI_API_KEY, value)

    // Cerebras API Key
    var cerebrasApiKey: String
        get() = getString(KEY_CEREBRAS_API_KEY, "")
        set(value) = putString(KEY_CEREBRAS_API_KEY, value)
    
    // Edit Mode (false = Read Mode, true = Edit Mode with stylus highlighter)
    var isEditMode: Boolean
        get() = getBoolean(KEY_IS_EDIT_MODE, false)
        set(value) = putBoolean(KEY_IS_EDIT_MODE, value)

    // Auto Bookmark on Close
    var autoBookmark: Boolean
        get() = getBoolean(KEY_AUTO_BOOKMARK, true)
        set(value) = putBoolean(KEY_AUTO_BOOKMARK, value)
    
    // Reset all to defaults
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
