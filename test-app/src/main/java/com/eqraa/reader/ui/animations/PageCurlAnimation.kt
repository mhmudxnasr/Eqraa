/*
 * Page Curl Animation
 * Realistic book page-turning animation with 3D transformations
 */

package com.eqraa.reader.ui.animations

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * Page curl direction
 */
enum class CurlDirection {
    LEFT_TO_RIGHT,  // Next page
    RIGHT_TO_LEFT   // Previous page
}

/**
 * Page curl animation state
 */
data class PageCurlState(
    val curlProgress: Float = 0f,        // 0f to 1f
    val curlPosition: Offset = Offset.Zero,
    val direction: CurlDirection = CurlDirection.LEFT_TO_RIGHT,
    val isAnimating: Boolean = false
)

/**
 * Page curl transition with realistic 3D effect
 * 
 * Creates a book-like page-turning animation with:
 * - 3D curved page effect
 * - Shadow gradients for depth
 * - Touch-interactive drag
 * - Smooth animations
 */
@Composable
fun PageCurlTransition(
    currentPage: @Composable () -> Unit,
    nextPage: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onPageTurnComplete: (CurlDirection) -> Unit = {},
    enabled: Boolean = true
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var curlState by remember { mutableStateOf(PageCurlState()) }
    
    // Animated curl progress
    val animatedProgress = animateFloatAsState(
        targetValue = if (curlState.isAnimating) 1f else curlState.curlProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = {
            if (curlState.isAnimating && it >= 0.9f) {
                onPageTurnComplete(curlState.direction)
                curlState = PageCurlState()
            }
        }
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val direction = if (offset.x < size.width / 2) {
                                    CurlDirection.RIGHT_TO_LEFT
                                } else {
                                    CurlDirection.LEFT_TO_RIGHT
                                }
                                curlState = curlState.copy(
                                    direction = direction,
                                    curlPosition = offset
                                )
                            },
                            onDragEnd = {
                                if (curlState.curlProgress > 0.3f) {
                                    // Complete the turn
                                    curlState = curlState.copy(isAnimating = true)
                                } else {
                                    // Snap back
                                    curlState = PageCurlState()
                                }
                            },
                            onDragCancel = {
                                curlState = PageCurlState()
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val progress = when (curlState.direction) {
                                    CurlDirection.LEFT_TO_RIGHT -> {
                                        min(1f, abs(dragAmount) / size.width.toFloat())
                                    }
                                    CurlDirection.RIGHT_TO_LEFT -> {
                                        min(1f, abs(dragAmount) / size.width.toFloat())
                                    }
                                }
                                curlState = curlState.copy(
                                    curlProgress = min(1f, curlState.curlProgress + progress),
                                    curlPosition = curlState.curlPosition.copy(
                                        x = curlState.curlPosition.x + dragAmount
                                    )
                                )
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // Current page (underneath)
        Box(modifier = Modifier.fillMaxSize()) {
            currentPage()
        }
        
        // Curling page effect
        if (animatedProgress.value > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawPageCurl(
                    progress = animatedProgress.value,
                    direction = curlState.direction,
                    size = this.size
                )
            }
            
            // Next page (revealed)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = min(1f, animatedProgress.value * 2f)
                    }
            ) {
                nextPage()
            }
        }
    }
}

/**
 * Draw page curl effect on canvas
 */
private fun DrawScope.drawPageCurl(
    progress: Float,
    direction: CurlDirection,
    size: Size
) {
    val curlX = when (direction) {
        CurlDirection.LEFT_TO_RIGHT -> size.width * progress
        CurlDirection.RIGHT_TO_LEFT -> size.width * (1f - progress)
    }
    
    // Create curl shadow gradient
    val shadowGradient = Brush.horizontalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.3f * (1f - progress)),
            Color.Black.copy(alpha = 0.1f * (1f - progress)),
            Color.Transparent
        ),
        startX = curlX,
        endX = curlX + (size.width * 0.1f)
    )
    
    // Draw shadow
    drawRect(
        brush = shadowGradient,
        topLeft = Offset(curlX, 0f),
        size = Size(size.width - curlX, size.height)
    )
    
    // Draw curl highlight (simulated 3D paper edge)
    val highlightPath = Path().apply {
        moveTo(curlX, 0f)
        cubicTo(
            curlX + (size.width * 0.05f), size.height * 0.25f,
            curlX + (size.width * 0.05f), size.height * 0.75f,
            curlX, size.height
        )
    }
    
    drawPath(
        path = highlightPath,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.4f * (1f - progress)),
                Color.White.copy(alpha = 0.2f * (1f - progress)),
                Color.White.copy(alpha = 0.4f * (1f - progress))
            )
        ),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
    )
}

/**
 * Simple page slide transition (alternative to curl)
 * For better performance on lower-end devices
 */
@Composable
fun PageSlideTransition(
    currentPage: @Composable () -> Unit,
    nextPage: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    animating: Boolean = false,
    direction: CurlDirection = CurlDirection.LEFT_TO_RIGHT
) {
    val offsetX = animateFloatAsState(
        targetValue = if (animating) {
            when (direction) {
                CurlDirection.LEFT_TO_RIGHT -> -1f
                CurlDirection.RIGHT_TO_LEFT -> 1f
            }
        } else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        )
    )
    
    Box(modifier = modifier.fillMaxSize()) {
        // Current page
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value * size.width
                }
        ) {
            currentPage()
        }
        
        // Next page
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (offsetX.value + if (direction == CurlDirection.LEFT_TO_RIGHT) 1f else -1f) * size.width
                    alpha = if (animating) 1f else 0f
                }
        ) {
            nextPage()
        }
    }
}
