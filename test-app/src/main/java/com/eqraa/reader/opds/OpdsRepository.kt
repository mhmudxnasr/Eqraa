package com.eqraa.reader.opds

import org.readium.r2.opds.OPDS1Parser
import org.readium.r2.opds.OPDS2Parser
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.opds.ParseData
import timber.log.Timber

class OpdsRepository(
    private val httpClient: HttpClient
) {
    suspend fun loadFeed(url: String): Try<ParseData, Exception> {
        val cleanedUrl = if (url.contains("://")) url else "http://$url"
        Timber.d("OPDS: Attempting to load feed from: $cleanedUrl")
        
        val absoluteUrl = AbsoluteUrl(cleanedUrl) 
        if (absoluteUrl == null) {
            Timber.e("OPDS: Invalid URL format: $cleanedUrl")
            return Try.failure(Exception("Invalid URL: $cleanedUrl"))
        }
        
        val request = HttpRequest(absoluteUrl)
        
        return try {
            Timber.d("OPDS: Trying OPDS 1.0 parser...")
            val opds1Result = OPDS1Parser.parseRequest(request, httpClient)
            
            when (opds1Result) {
                is Try.Success -> {
                    Timber.d("OPDS: OPDS 1.0 parse SUCCESS - ${opds1Result.value.feed?.metadata?.title ?: "No title"}")
                    Timber.d("OPDS: Found ${opds1Result.value.feed?.publications?.size ?: 0} publications, ${opds1Result.value.feed?.navigation?.size ?: 0} nav links")
                    opds1Result
                }
                is Try.Failure -> {
                    Timber.w("OPDS: OPDS 1.0 parse failed: ${opds1Result.value.message}")
                    Timber.d("OPDS: Trying OPDS 2.0 parser...")
                    val opds2Result = OPDS2Parser.parseRequest(request, httpClient)
                    when (opds2Result) {
                        is Try.Success -> {
                            Timber.d("OPDS: OPDS 2.0 parse SUCCESS")
                            opds2Result
                        }
                        is Try.Failure -> {
                            Timber.e("OPDS: Both parsers failed. OPDS 2 error: ${opds2Result.value.message}")
                            opds2Result
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "OPDS: Network or parsing exception for $cleanedUrl")
            Try.failure(e)
        }
    }
}
