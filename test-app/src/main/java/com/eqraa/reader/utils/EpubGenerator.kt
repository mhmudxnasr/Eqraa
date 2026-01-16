package com.eqraa.reader.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.UUID

class EpubGenerator(private val context: Context) {

    fun createEpub(title: String, content: String): File {
        val fileName = "${title.take(20).replace("[^a-zA-Z0-9]".toRegex(), "_")}_${System.currentTimeMillis()}.epub"
        val file = File(context.cacheDir, fileName)

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            // 1. mimetype (Must be first, stored, uncompressed)
            val mimetypeData = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            val mimetypeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeData.size.toLong()
                crc = CRC32().apply { update(mimetypeData) }.value
            }
            zip.putNextEntry(mimetypeEntry)
            zip.write(mimetypeData)
            zip.closeEntry()

            // 2. META-INF/container.xml
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(containerXml.toByteArray())
            zip.closeEntry()

            // 3. OEBPS/content.opf
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(getContentOpf(title).toByteArray())
            zip.closeEntry()

            // 4. OEBPS/index.html
            zip.putNextEntry(ZipEntry("OEBPS/index.html"))
            zip.write(getHtml(title, content).toByteArray())
            zip.closeEntry()
        }
        return file
    }

    private val containerXml = """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
            </rootfiles>
        </container>
    """.trimIndent()

    private fun getContentOpf(title: String): String {
        val uuid = UUID.randomUUID().toString()
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="3.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:language>en</dc:language>
                    <dc:identifier id="BookId">urn:uuid:$uuid</dc:identifier>
                    <meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
                </metadata>
                <manifest>
                    <item id="index" href="index.html" media-type="application/xhtml+xml"/>
                </manifest>
                <spine>
                    <itemref idref="index"/>
                </spine>
            </package>
        """.trimIndent()
    }

    private fun getHtml(title: String, content: String): String {
        // Basic HTML wrapping. Replace newlines with <br> or <p>
        val formattedContent = content.split("\n").joinToString("") { "<p>${it}</p>" }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <title>$title</title>
                <style>
                    body { font-family: sans-serif; line-height: 1.6; padding: 1em; }
                    p { margin-bottom: 1em; }
                </style>
            </head>
            <body>
                <h1>$title</h1>
                $formattedContent
            </body>
            </html>
        """.trimIndent()
    }
}
