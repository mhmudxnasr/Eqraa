/*
 * Copyright 2024 Eqraa. All rights reserved.
 */

package com.eqraa.reader.obsidian

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import timber.log.Timber

/**
 * Exports reading data to Obsidian-compatible Markdown files.
 */
class ObsidianExporter(
    private val context: Context
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timestampFormat = SimpleDateFormat("HH:mm", Locale.US)

    /**
     * Appends a reading session to the Daily Note (YYYY-MM-DD.md).
     * Creates the file if it doesn't exist.
     *
     * @param vaultUri URI of the Obsidian vault folder (from SAF picker)
     * @param bookTitle Title of the book that was read
     * @param durationMinutes How many minutes were spent reading
     * @param highlights List of new highlights from this session (optional)
     */
    suspend fun appendToDailyNote(
        vaultUri: Uri,
        bookTitle: String,
        durationMinutes: Int,
        highlights: List<String> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val today = dateFormat.format(Date())
            val time = timestampFormat.format(Date())
            val fileName = "$today.md"

            val vaultFolder = DocumentFile.fromTreeUri(context, vaultUri)
                ?: return@withContext false

            // Find or create the daily note
            var dailyNote = vaultFolder.findFile(fileName)
            if (dailyNote == null || !dailyNote.exists()) {
                dailyNote = vaultFolder.createFile("text/markdown", fileName)
            }

            if (dailyNote == null) {
                Timber.e("Obsidian: Could not create daily note")
                return@withContext false
            }

            // Build the content to append
            val content = buildString {
                appendLine()
                appendLine("## 📚 Eqraa Reading Log")
                appendLine("- $time - Read **$bookTitle** for $durationMinutes minutes")

                if (highlights.isNotEmpty()) {
                    appendLine("  - Highlights:")
                    highlights.forEach { highlight ->
                        appendLine("    - > \"$highlight\"")
                    }
                }
            }

            // Append to existing content
            context.contentResolver.openOutputStream(dailyNote.uri, "wa")?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(content)
                }
            }

            Timber.d("Obsidian: Appended to $fileName")
            true
        } catch (e: Exception) {
            Timber.e(e, "Obsidian: Failed to append to daily note")
            false
        }
    }

    /**
     * Exports a single highlight as an atomic note.
     * Format: {BookTitle} - {Timestamp}.md
     *
     * @param vaultUri URI of the vault/highlights subfolder
     * @param bookTitle Title of the book
     * @param bookAuthor Author of the book
     * @param highlightText The highlighted text
     * @param chapterTitle Chapter where the highlight was made (optional)
     * @param userNote User's note on the highlight (optional)
     */
    suspend fun exportAtomicNote(
        vaultUri: Uri,
        bookTitle: String,
        bookAuthor: String,
        highlightText: String,
        chapterTitle: String? = null,
        userNote: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9\\s]"), "").take(30)
            val fileName = "$safeTitle - $timestamp.md"

            val folder = DocumentFile.fromTreeUri(context, vaultUri)
                ?: return@withContext false

            val noteFile = folder.createFile("text/markdown", fileName)
                ?: return@withContext false

            val content = buildString {
                appendLine("---")
                appendLine("book: \"$bookTitle\"")
                appendLine("author: \"$bookAuthor\"")
                if (chapterTitle != null) appendLine("chapter: \"$chapterTitle\"")
                appendLine("created: ${dateFormat.format(Date())}")
                appendLine("tags: [highlight, reading]")
                appendLine("---")
                appendLine()
                appendLine("> $highlightText")
                appendLine()
                if (userNote != null) {
                    appendLine("**My Note:** $userNote")
                }
            }

            context.contentResolver.openOutputStream(noteFile.uri)?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    writer.write(content)
                }
            }

            Timber.d("Obsidian: Created atomic note $fileName")
            true
        } catch (e: Exception) {
            Timber.e(e, "Obsidian: Failed to export atomic note")
            false
        }
    }
}
