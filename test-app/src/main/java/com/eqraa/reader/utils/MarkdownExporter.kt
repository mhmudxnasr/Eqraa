package com.eqraa.reader.utils

import com.eqraa.reader.data.model.Book
import com.eqraa.reader.data.model.Highlight
import java.text.SimpleDateFormat
import java.util.*

object MarkdownExporter {

    fun generateMarkdown(book: Book, highlights: List<Highlight>): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // Frontmatter / Header
        sb.append("# ${book.title}\n")
        sb.append("**Author:** ${book.author ?: "Unknown"}\n")
        sb.append("**Exported:** ${dateFormat.format(Date())}\n\n")
        sb.append("---\n\n")

        if (highlights.isEmpty()) {
            sb.append("*No highlights found for this book.*\n")
            return sb.toString()
        }

        // Highlights
        highlights.sortedBy { it.totalProgression }.forEach { highlight ->
            // Chapter / Location if available (using title or progress)
            val location = if (!highlight.title.isNullOrBlank()) {
                "**${highlight.title}**"
            } else {
                "**Location:** ${(highlight.totalProgression * 100).toInt()}%"
            }
            
            sb.append("$location\n")
            
            // The Highlighted Text (Quote)
            // Note: Highlight model stores text in `locator.text`. 
            // We need to access the text content.
            // Highlight.text is a Locator.Text object.
            val quote = highlight.text.highlight ?: ""
            if (quote.isNotBlank()) {
                sb.append("> $quote\n")
            }
            
            // User Note / Annotation
            if (highlight.annotation.isNotBlank()) {
                sb.append("\n**Note:** ${highlight.annotation}\n")
            }
            
            sb.append("\n---\n\n")
        }

        return sb.toString()
    }
}
