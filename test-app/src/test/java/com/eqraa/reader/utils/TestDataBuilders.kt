/*
 * Copyright 2024 Eqraa. All rights reserved.
 * Test Infrastructure - Test Data Builders
 */
package com.eqraa.reader.utils

import com.eqraa.reader.data.model.Book
import com.eqraa.reader.data.model.Bookmark
import com.eqraa.reader.data.model.Highlight
import com.eqraa.reader.data.model.ReadingPosition
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/**
 * Test data builders for creating test fixtures.
 * Uses Builder pattern for flexible test data creation.
 */
object TestDataBuilders {

    /**
     * Creates a test Book with sensible defaults.
     * All parameters can be overridden.
     */
    fun createTestBook(
        id: Long? = 1L,
        creation: Long = System.currentTimeMillis(),
        title: String = "Test Book Title",
        author: String? = "Test Author",
        href: String = "/storage/books/test-book.epub",
        identifier: String = "urn:isbn:1234567890",
        mediaType: MediaType = MediaType.EPUB,
        progression: String? = "{}",
        cover: String = "/storage/covers/test-cover.jpg",
        updatedAt: Long = System.currentTimeMillis()
    ) = Book(
        id = id,
        creation = creation,
        title = title,
        author = author,
        href = href,
        identifier = identifier,
        mediaType = mediaType,
        progression = progression,
        cover = cover,
        updatedAt = updatedAt
    )

    /**
     * Creates a test Bookmark with sensible defaults.
     */
    fun createTestBookmark(
        id: Long? = 1L,
        creation: Long = System.currentTimeMillis(),
        bookId: Long = 1L,
        resourceIndex: Long = 0L,
        resourceHref: String = "chapter1.xhtml",
        resourceType: String = "application/xhtml+xml",
        resourceTitle: String = "Chapter 1",
        location: String = """{"position":1,"totalProgression":0.1}""",
        locatorText: String = "{}"
    ) = Bookmark(
        id = id,
        creation = creation,
        bookId = bookId,
        resourceIndex = resourceIndex,
        resourceHref = resourceHref,
        resourceType = resourceType,
        resourceTitle = resourceTitle,
        location = location,
        locatorText = locatorText
    )

    /**
     * Creates a test Highlight with sensible defaults.
     */
    fun createTestHighlight(
        bookId: Long = 1L,
        style: Highlight.Style = Highlight.Style.HIGHLIGHT,
        tint: Int = 0xFFFFFF00.toInt(), // Yellow
        locator: Locator = createTestLocator(),
        annotation: String = ""
    ) = Highlight(
        bookId = bookId,
        style = style,
        tint = tint,
        locator = locator,
        annotation = annotation
    )

    /**
     * Creates a test Locator with sensible defaults.
     */
    fun createTestLocator(
        href: String = "chapter1.xhtml",
        mediaType: String = "application/xhtml+xml",
        title: String = "Chapter 1",
        position: Int = 1,
        totalProgression: Double = 0.1,
        text: String = "Selected text content"
    ): Locator {
        val url = Url(href) ?: Url("chapter1.xhtml")!!
        return Locator(
            href = url,
            mediaType = MediaType(mediaType)!!,
            title = title,
            locations = Locator.Locations(
                position = position,
                totalProgression = totalProgression
            ),
            text = Locator.Text(
                highlight = text
            )
        )
    }

    /**
     * Creates a test ReadingPosition for sync testing.
     */
    fun createTestReadingPosition(
        bookId: String = "urn:isbn:1234567890",
        cfi: String = """{"position":1,"totalProgression":0.1}""",
        percentage: Float = 0.1f,
        timestamp: Long = System.currentTimeMillis(),
        deviceId: String? = "test-device-id",
        pageNumber: Int? = null,
        totalPages: Int? = null
    ) = ReadingPosition(
        bookId = bookId,
        cfi = cfi,
        percentage = percentage,
        timestamp = timestamp,
        deviceId = deviceId,
        pageNumber = pageNumber,
        totalPages = totalPages
    )

    /**
     * Creates a list of test books for library testing.
     */
    fun createTestBookList(count: Int = 5): List<Book> {
        return (1..count).map { index ->
            createTestBook(
                id = index.toLong(),
                title = "Test Book $index",
                author = "Author $index",
                identifier = "urn:isbn:${1234567890 + index}"
            )
        }
    }

    /**
     * Creates test highlights for a book.
     */
    fun createTestHighlightList(
        bookId: Long = 1L,
        count: Int = 3
    ): List<Highlight> {
        val colors = listOf(0xFFFFFF00.toInt(), 0xFF00FF00.toInt(), 0xFFFF0000.toInt())
        return (1..count).map { index ->
            createTestHighlight(
                bookId = bookId,
                tint = colors[index % colors.size],
                annotation = if (index % 2 == 0) "Note for highlight $index" else ""
            )
        }
    }
}
