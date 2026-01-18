/*
 * Performance Monitor
 * Real-time performance tracking and optimization suggestions
 */

package com.eqraa.reader.performance

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.system.measureTimeMillis

/**
 * Performance metrics
 */
@Immutable
data class PerformanceMetrics(
    val fps: Int = 60,
    val frameTimeMs: Float = 16.67f,
    val pageLoadTimeMs: Long = 0,
    val memoryUsageMb: Float = 0f,
    val cacheHitRate: Float = 0f,
    val droppedFrames: Int = 0
)

/**
 * Performance status
 */
enum class PerformanceStatus {
    EXCELLENT,  // 60 FPS, <50ms page loads
    GOOD,       // 45-60 FPS, <100ms page loads
    FAIR,       // 30-45 FPS, <200ms page loads
    POOR        // <30 FPS, >200ms page loads
}

/**
 * Performance monitor for the reader
 * 
 * Tracks:
 * - Frame rate and jank
 * - Page load latency
 * - Memory usage
 * - Cache performance
 */
class PerformanceMonitor {
    private val _metrics = MutableStateFlow(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()
    
    private val frameTimestamps = ArrayDeque<Long>(60)
    private var lastFrameTime = 0L
    private var droppedFrameCount = 0
    
    /**
     * Track frame timing
     */
    fun trackFrame() {
        val currentTime = System.nanoTime()
        
        if (lastFrameTime > 0) {
            val frameTime = (currentTime - lastFrameTime) / 1_000_000f // ms
            
            frameTimestamps.add(currentTime)
            if (frameTimestamps.size > 60) {
                frameTimestamps.removeFirst()
            }
            
            // Detect dropped frames (>16.67ms = 60 FPS)
            if (frameTime > 16.67f * 1.5f) { // 50% tolerance
                droppedFrameCount++
            }
            
            updateMetrics()
        }
        
        lastFrameTime = currentTime
    }
    
    /**
     * Measure page load time
     */
    suspend fun <T> measurePageLoad(block: suspend () -> T): T {
        var result: T
        val time = measureTimeMillis {
            result = block()
        }
        
        _metrics.value = _metrics.value.copy(pageLoadTimeMs = time)
        
        if (time > 50) {
            Timber.w("PerformanceMonitor: Slow page load: ${time}ms")
        } else {
            Timber.d("PerformanceMonitor: Page loaded in ${time}ms")
        }
        
        return result
    }
    
    /**
     * Update cache hit rate
     */
    fun updateCacheHitRate(hitRate: Float) {
        _metrics.value = _metrics.value.copy(cacheHitRate = hitRate)
    }
    
    /**
     * Update memory usage
     */
    fun updateMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        _metrics.value = _metrics.value.copy(memoryUsageMb = usedMemory.toFloat())
    }
    
    /**
     * Calculate current FPS
     */
    private fun calculateFps(): Int {
        if (frameTimestamps.size < 2) return 60
        
        val timeSpan = (frameTimestamps.last() - frameTimestamps.first()) / 1_000_000_000.0 // seconds
        val frameCount = frameTimestamps.size
        
        return (frameCount / timeSpan).toInt().coerceIn(0, 60)
    }
    
    /**
     * Calculate average frame time
     */
    private fun calculateFrameTime(): Float {
        if (frameTimestamps.size < 2) return 16.67f
        
        val timeSpan = (frameTimestamps.last() - frameTimestamps.first()) / 1_000_000f // ms
        val frameCount = frameTimestamps.size - 1
        
        return if (frameCount > 0) timeSpan / frameCount else 16.67f
    }
    
    /**
     * Update metrics
     */
    private fun updateMetrics() {
        _metrics.value = _metrics.value.copy(
            fps = calculateFps(),
            frameTimeMs = calculateFrameTime(),
            droppedFrames = droppedFrameCount
        )
    }
    
    /**
     * Get performance status
     */
    fun getStatus(): PerformanceStatus {
        val current = _metrics.value
        
        return when {
            current.fps >= 55 && current.pageLoadTimeMs < 50 -> PerformanceStatus.EXCELLENT
            current.fps >= 45 && current.pageLoadTimeMs < 100 -> PerformanceStatus.GOOD
            current.fps >= 30 && current.pageLoadTimeMs < 200 -> PerformanceStatus.FAIR
            else -> PerformanceStatus.POOR
        }
    }
    
    /**
     * Get optimization suggestions
     */
    fun getOptimizationSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()
        val current = _metrics.value
        
        if (current.fps < 45) {
            suggestions.add("Low FPS detected. Consider reducing animation complexity.")
        }
        
        if (current.pageLoadTimeMs > 100) {
            suggestions.add("Slow page loads. Enable aggressive caching and prefetching.")
        }
        
        if (current.cacheHitRate < 0.7f) {
            suggestions.add("Low cache hit rate. Increase cache size or prefetch window.")
        }
        
        if (current.memoryUsageMb > 200) {
            suggestions.add("High memory usage. Consider reducing cache size.")
        }
        
        if (current.droppedFrames > 10) {
            suggestions.add("${current.droppedFrames} dropped frames detected. Optimize rendering.")
        }
        
        return suggestions
    }
    
    /**
     * Reset metrics
     */
    fun reset() {
        frameTimestamps.clear()
        droppedFrameCount = 0
        lastFrameTime = 0
        _metrics.value = PerformanceMetrics()
    }
}

/**
 * Development-only performance overlay
 */
@Composable
fun PerformanceOverlay(
    monitor: PerformanceMonitor,
    visible: Boolean = false
) {
    if (!visible || Build.TYPE == "user") return // Only in debug builds
    
    val metrics by monitor.metrics.collectAsState()
    
    // Simple overlay showing FPS and page load time
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Column(
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Text(
                text = "FPS: ${metrics.fps}",
                color = when {
                    metrics.fps >= 55 -> androidx.compose.ui.graphics.Color.Green
                    metrics.fps >= 30 -> androidx.compose.ui.graphics.Color.Yellow
                    else -> androidx.compose.ui.graphics.Color.Red
                },
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp
            )
            
            Text(
                text = "Load: ${metrics.pageLoadTimeMs}ms",
                color = when {
                    metrics.pageLoadTimeMs < 50 -> androidx.compose.ui.graphics.Color.Green
                    metrics.pageLoadTimeMs < 100 -> androidx.compose.ui.graphics.Color.Yellow
                    else -> androidx.compose.ui.graphics.Color.Red
                },
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp
            )
            
            Text(
                text = "Mem: ${"%.1f".format(metrics.memoryUsageMb)}MB",
                color = androidx.compose.ui.graphics.Color.White,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

