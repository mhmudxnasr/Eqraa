/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Edit Mode Gesture Handler
 * 
 * Custom modifier that handles gestures based on mode:
 * 
 * **Read Mode (isEditMode = false):**
 * - Standard behavior (single-finger swipe turns pages, long-press selects text)
 * 
 * **Edit Mode (isEditMode = true):**
 * - Stylus: Draws highlights
 * - 1 Finger: Ignored (prevents accidental page turns)
 * - 2 Fingers: Swipe to turn page
 */
fun Modifier.editModeGestures(
    isEditMode: Boolean,
    onStylusDown: (Offset) -> Unit,
    onStylusMove: (Offset) -> Unit,
    onStylusUp: () -> Unit,
    onTwoFingerSwipeLeft: () -> Unit,
    onTwoFingerSwipeRight: () -> Unit
): Modifier = composed {
    if (!isEditMode) {
        // Read Mode: Pass through all gestures (default behavior)
        this
    } else {
        // Edit Mode: Custom gesture handling
        this.pointerInput(Unit) {
            awaitEachGesture {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                val pointerType = firstDown.type
                
                when {
                    // STYLUS: Draw highlights
                    pointerType == PointerType.Stylus || pointerType == PointerType.Eraser -> {
                        onStylusDown(firstDown.position)
                        firstDown.consume()
                        
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            
                            when (event.type) {
                                PointerEventType.Move -> {
                                    onStylusMove(change.position)
                                    change.consume()
                                }
                                PointerEventType.Release -> {
                                    onStylusUp()
                                    break
                                }
                                else -> {}
                            }
                            
                            if (change.pressed.not()) {
                                onStylusUp()
                                break
                            }
                        }
                    }
                    
                    // TOUCH: Check for 2-finger swipe
                    pointerType == PointerType.Touch -> {
                        // Wait to see if this becomes a 2-finger gesture
                        var startX = firstDown.position.x
                        var pointerCount = 1
                        var twoFingerStartX: Float? = null
                        
                        while (true) {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }
                            pointerCount = activePointers.size
                            
                            if (pointerCount >= 2 && twoFingerStartX == null) {
                                // Record the starting X position for 2-finger swipe
                                twoFingerStartX = activePointers.map { it.position.x }.average().toFloat()
                            }
                            
                            if (pointerCount >= 2 && twoFingerStartX != null) {
                                val currentX = activePointers.map { it.position.x }.average().toFloat()
                                val deltaX = currentX - twoFingerStartX
                                
                                // Check for swipe threshold
                                val swipeThreshold = 100f
                                if (abs(deltaX) > swipeThreshold) {
                                    if (deltaX > 0) {
                                        onTwoFingerSwipeRight() // Swipe right = previous page
                                    } else {
                                        onTwoFingerSwipeLeft() // Swipe left = next page
                                    }
                                    // Consume all changes
                                    event.changes.forEach { it.consume() }
                                    break
                                }
                            }
                            
                            // Exit loop when all pointers are released
                            if (event.changes.all { !it.pressed }) {
                                break
                            }
                            
                            // 1-finger touch: IGNORE (consume but do nothing)
                            if (pointerCount == 1) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                    
                    // MOUSE or other: Pass through
                    else -> {}
                }
            }
        }
    }
}
