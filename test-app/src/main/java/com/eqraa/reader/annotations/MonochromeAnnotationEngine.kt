/*
 * Monochrome Annotation Engine
 * Elegant highlighting with tags and sync support
 */

package com.eqraa.reader.annotations

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.readium.r2.shared.publication.Locator
import java.time.Instant
import java.util.UUID

/**
 * Annotation style (Monochrome)
 */
enum class AnnotationStyle {
    UNDERLINE,    // Subtle single-line underline
    HIGHLIGHT;    // Transparent gray background overlay
    
    fun toDisplayName(): String = when (this) {
        UNDERLINE -> "Underline"
        HIGHLIGHT -> "Highlight"
    }
}

/**
 * Highlight tag for categorization
 */
data class HighlightTag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String,           // Emoji or icon identifier
    val colorShade: Color       // Monochrome shade for visual distinction
)

/**
 * Default tag presets
 */
object DefaultHighlightTags {
    val Important = HighlightTag(
        id = "important",
        name = "Important",
        icon = "⭐",
        colorShade = Color(0xFF1C1C1C)
    )
    
    val Insight = HighlightTag(
        id = "insight",
        name = "Insight",
        icon = "💡",
        colorShade = Color(0xFF404040)
    )
    
    val Quote = HighlightTag(
        id = "quote",
        name = "Quote",
        icon = "💬",
        colorShade = Color(0xFF6B6B6B)
    )
    
    val Question = HighlightTag(
        id = "question",
        name = "Question",
        icon = "❓",
        colorShade = Color(0xFF505050)
    )
    
    val Remember = HighlightTag(
        id = "remember",
        name = "Remember",
        icon = "📌",
        colorShade = Color(0xFF2C2C2C)
    )
    
    fun all() = listOf(Important, Insight, Quote, Question, Remember)
}

/**
 * Annotation with tag support
 */
data class MonochromeAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val bookId: Long,
    val locator: Locator,
    val selectedText: String,
    val style: AnnotationStyle,
    val tags: Set<String> = emptySet(),  // Tag IDs
    val note: String = "",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

/**
 * Markdown export formatter
 */
class MarkdownExporter {
    fun exportAnnotations(
        bookTitle: String,
        annotations: List<MonochromeAnnotation>,
        tags: Map<String, HighlightTag>
    ): String = buildString {
        appendLine("# Highlights from \"$bookTitle\"")
        appendLine()
        
        // Group by chapter
        val byChapter = annotations.groupBy { 
            it.locator.title ?: "Unknown Chapter" 
        }
        
        byChapter.forEach { (chapter, chapterAnnotations) ->
            appendLine("## $chapter")
            appendLine()
            
            chapterAnnotations.forEach { annotation ->
                // Quote
                appendLine("> ${annotation.selectedText}")
                
                // Style
                appendLine("- **Style**: ${annotation.style.toDisplayName()}")
                
                // Tags
                if (annotation.tags.isNotEmpty()) {
                    val tagNames = annotation.tags.mapNotNull { tags[it]?.let { tag -> "${tag.icon} ${tag.name}" } }
                    appendLine("- **Tags**: ${tagNames.joinToString(", ")}")
                }
                
                // Note
                if (annotation.note.isNotEmpty()) {
                    appendLine("- **Note**: ${annotation.note}")
                }
                
                // Location
                val location = annotation.locator.locations
                appendLine("- **Location**: Chapter ${location.position ?: "?"}, Page ${location.totalProgression ?: "?"}")
                
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }
}

/**
 * Monochrome Annotation Manager
 */
interface MonochromeAnnotationManager {
    fun getAnnotations(bookId: Long): Flow<List<MonochromeAnnotation>>
    fun getAnnotationsByTag(bookId: Long, tagId: String): Flow<List<MonochromeAnnotation>>
    suspend fun addAnnotation(annotation: MonochromeAnnotation)
    suspend fun updateAnnotation(id: String, style: AnnotationStyle? = null, tags: Set<String>? = null, note: String? = null)
    suspend fun deleteAnnotation(id: String)
    suspend fun exportToMarkdown(bookId: Long, bookTitle: String): String
    
    fun getTags(): Flow<List<HighlightTag>>
    suspend fun addTag(tag: HighlightTag)
    suspend fun deleteTag(tagId: String)
}
