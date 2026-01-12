package com.eqraa.reader.utils

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Utility for compressing and decompressing CFI strings.
 * Uses standard ZLIB (Deflate) compression and Base64 encoding.
 */
object CFICompressor {

    /**
     * Compress a string using Deflate algorithm and encode as Base64.
     */
    fun compress(input: String): String {
        if (input.isEmpty()) return ""
        
        val data = input.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        
        deflater.end()
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Decode a Base64 string and decompress using Inflate algorithm.
     */
    fun decompress(compressed: String): String {
        if (compressed.isEmpty()) return ""
        
        return try {
            val data = Base64.decode(compressed, Base64.NO_WRAP)
            val inflater = Inflater()
            inflater.setInput(data)
            
            val outputStream = ByteArrayOutputStream(data.size)
            val buffer = ByteArray(1024)
            
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                outputStream.write(buffer, 0, count)
            }
            
            inflater.end()
            outputStream.toString(Charsets.UTF_8.name())
        } catch (e: Exception) {
            // Return original if decompression fails (legacy non-compressed data support)
            compressed
        }
    }
}
