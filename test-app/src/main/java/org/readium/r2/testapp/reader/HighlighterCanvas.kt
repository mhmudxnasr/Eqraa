/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Data class representing a single highlighter stroke path
 */
data class HighlightStroke(
    val path: Path,
    val color: Color = Color.Cyan.copy(alpha = 0.4f),
    val strokeWidth: Float = 30f,
    val blendMode: BlendMode = BlendMode.Darken
)

/**
 * Highlighter Canvas Composable
 * 
 * A canvas layer that accepts stylus input and draws translucent yellow 
 * highlighting strokes. Uses BlendMode.Darken to keep text readable.
 */
@Composable
fun HighlighterCanvas(
    strokes: List<HighlightStroke>,
    currentPath: Path?,
    currentColor: Color = Color.Yellow.copy(alpha = 0.4f),
    currentStrokeWidth: Float = 30f,
    currentBlendMode: BlendMode = BlendMode.Darken,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw completed strokes
        strokes.forEach { stroke ->
            drawPath(
                path = stroke.path,
                color = stroke.color,
                style = Stroke(
                    width = stroke.strokeWidth,
                    cap = StrokeCap.Square,
                    join = StrokeJoin.Round
                ),
                blendMode = stroke.blendMode
            )
        }
        
        // Draw current in-progress stroke
        currentPath?.let { path ->
            drawPath(
                path = path,
                color = currentColor,
                style = Stroke(
                    width = currentStrokeWidth,
                    cap = StrokeCap.Square,
                    join = StrokeJoin.Round
                ),
                blendMode = currentBlendMode
            )
        }
    }
}

/**
 * Stylus drawing style options
 */
enum class StylusStyle {
    HIGHLIGHTER,  // Translucent, thick, BlendMode.Darken
    PEN,          // Solid thin line
    MARKER        // Thick solid line
}

/**
 * State holder for highlighter strokes with style and color support
 */
class HighlighterState {
    var strokes by mutableStateOf<List<HighlightStroke>>(emptyList())
        private set
    
    var currentPath by mutableStateOf<Path?>(null)
        private set
    
    // Current style settings
    var currentStyle by mutableStateOf(StylusStyle.HIGHLIGHTER)
    var currentColor by mutableStateOf(Color.Yellow.copy(alpha = 0.4f))
    
    private var currentPathBuilder: Path? = null
    
    fun setStyle(style: StylusStyle) {
        currentStyle = style
        // Update color alpha based on style
        currentColor = when (style) {
            StylusStyle.HIGHLIGHTER -> currentColor.copy(alpha = 0.4f)
            StylusStyle.PEN -> currentColor.copy(alpha = 1f)
            StylusStyle.MARKER -> currentColor.copy(alpha = 0.8f)
        }
    }
    
    fun setColor(color: Color) {
        currentColor = when (currentStyle) {
            StylusStyle.HIGHLIGHTER -> color.copy(alpha = 0.4f)
            StylusStyle.PEN -> color.copy(alpha = 1f)
            StylusStyle.MARKER -> color.copy(alpha = 0.8f)
        }
    }
    
    fun getStrokeWidth(): Float = when (currentStyle) {
        StylusStyle.HIGHLIGHTER -> 30f
        StylusStyle.PEN -> 4f
        StylusStyle.MARKER -> 15f
    }
    
    fun getBlendMode(): BlendMode = when (currentStyle) {
        StylusStyle.HIGHLIGHTER -> BlendMode.Darken
        StylusStyle.PEN -> BlendMode.SrcOver
        StylusStyle.MARKER -> BlendMode.SrcOver
    }
    
    fun startStroke(position: Offset) {
        currentPathBuilder = Path().apply {
            moveTo(position.x, position.y)
        }
        currentPath = currentPathBuilder
    }
    
    fun continueStroke(position: Offset) {
        currentPathBuilder?.lineTo(position.x, position.y)
        // Force recomposition by creating a new path reference
        currentPath = Path().apply { addPath(currentPathBuilder!!) }
    }
    
    fun endStroke() {
        currentPathBuilder?.let { path ->
            strokes = strokes + HighlightStroke(
                path = path,
                color = currentColor,
                strokeWidth = getStrokeWidth(),
                blendMode = getBlendMode()
            )
        }
        currentPathBuilder = null
        currentPath = null
    }
    
    fun clearStrokes() {
        strokes = emptyList()
        currentPathBuilder = null
        currentPath = null
    }
    
    fun undoLastStroke() {
        if (strokes.isNotEmpty()) {
            strokes = strokes.dropLast(1)
        }
    }
}

@Composable
fun rememberHighlighterState(): HighlighterState {
    return remember { HighlighterState() }
}
