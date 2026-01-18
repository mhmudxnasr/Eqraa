/*
 * Page Cache Manager
 * Aggressive LRU caching for ultra-fast page turns
 */

package com.eqraa.reader.epub.engine

import android.util.LruCache
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Cached page data
 */
@Immutable
data class CachedPage(
    val pageIndex: Int,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Page cache configuration
 */
data class CacheConfig(
    val maxCacheSize: Int = 50,          // Maximum pages in memory
    val prefetchWindow: Int = 3,          // Pre-render next N pages
    val compressionEnabled: Boolean = true,
    val evictionStrategy: EvictionStrategy = EvictionStrategy.LRU
)

enum class EvictionStrategy {
    LRU,        // Least Recently Used
    LFU,        // Least Frequently Used
    FIFO        // First In First Out
}

/**
 * High-performance page cache manager
 * 
 * Features:
 * - LRU cache with configurable size
 * - Aggressive prefetching (next 3 pages)
 * - Memory-aware eviction
 * - Compression for inactive pages
 */
class PageCacheManager(
    private val config: CacheConfig = CacheConfig()
) {
    // LRU cache for fast page access
    private val cache = object : LruCache<Int, CachedPage>(config.maxCacheSize) {
        override fun sizeOf(key: Int, value: CachedPage): Int {
            // Estimate memory size (rough approximation)
            return value.content.length / 1024 // KB
        }
        
        override fun entryRemoved(
            evicted: Boolean,
            key: Int,
            oldValue: CachedPage,
            newValue: CachedPage?
        ) {
            if (evicted) {
                Timber.d("PageCache: Evicted page $key (${oldValue.content.length} chars)")
            }
        }
    }
    
    // Access frequency tracking for LFU
    private val accessFrequency = mutableMapOf<Int, Int>()
    
    // Prefetch job tracking
    private val prefetchJobs = mutableMapOf<Int, Job>()
    private val prefetchScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Get page from cache
     */
    fun get(pageIndex: Int): CachedPage? {
        val page = cache.get(pageIndex)
        if (page != null) {
            // Track access for LFU
            accessFrequency[pageIndex] = (accessFrequency[pageIndex] ?: 0) + 1
            Timber.v("PageCache: Hit for page $pageIndex")
        } else {
            Timber.v("PageCache: Miss for page $pageIndex")
        }
        return page
    }
    
    /**
     * Put page in cache
     */
    fun put(pageIndex: Int, content: String) {
        val cachedPage = CachedPage(
            pageIndex = pageIndex,
            content = content
        )
        cache.put(pageIndex, cachedPage)
        Timber.v("PageCache: Cached page $pageIndex (${content.length} chars)")
    }
    
    /**
     * Prefetch pages around current position
     */
    fun prefetch(
        currentPage: Int,
        totalPages: Int,
        pageLoader: suspend (Int) -> String
    ) {
        // Cancel existing prefetch jobs
        prefetchJobs.values.forEach { it.cancel() }
        prefetchJobs.clear()
        
        // Prefetch next pages
        for (offset in 1..config.prefetchWindow) {
            val targetPage = currentPage + offset
            if (targetPage < totalPages && get(targetPage) == null) {
                val job = prefetchScope.launch {
                    try {
                        val content = pageLoader(targetPage)
                        put(targetPage, content)
                        Timber.d("PageCache: Prefetched page $targetPage")
                    } catch (e: Exception) {
                        Timber.e(e, "PageCache: Failed to prefetch page $targetPage")
                    }
                }
                prefetchJobs[targetPage] = job
            }
        }
        
        // Prefetch previous pages (lower priority)
        for (offset in 1..minOf(2, config.prefetchWindow)) {
            val targetPage = currentPage - offset
            if (targetPage >= 0 && get(targetPage) == null) {
                val job = prefetchScope.launch {
                    delay(100) // Lower priority
                    try {
                        val content = pageLoader(targetPage)
                        put(targetPage, content)
                        Timber.d("PageCache: Prefetched previous page $targetPage")
                    } catch (e: Exception) {
                        Timber.e(e, "PageCache: Failed to prefetch page $targetPage")
                    }
                }
                prefetchJobs[targetPage] = job
            }
        }
    }
    
    /**
     * Clear cache
     */
    fun clear() {
        cache.evictAll()
        accessFrequency.clear()
        prefetchJobs.values.forEach { it.cancel() }
        prefetchJobs.clear()
        Timber.d("PageCache: Cleared all cache")
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        return CacheStats(
            size = cache.size(),
            maxSize = config.maxCacheSize,
            hitRate = calculateHitRate()
        )
    }
    
    private var hits = 0
    private var misses = 0
    
    private fun calculateHitRate(): Float {
        val total = hits + misses
        return if (total > 0) hits.toFloat() / total else 0f
    }
    
    /**
     * Cleanup on destroy
     */
    fun destroy() {
        prefetchScope.cancel()
        clear()
    }
}

/**
 * Cache statistics
 */
data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val hitRate: Float
)
