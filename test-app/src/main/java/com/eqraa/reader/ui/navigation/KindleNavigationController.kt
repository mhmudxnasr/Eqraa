/*
 * Kindle-Style Navigation Controller
 * Complete Amazon Kindle navigation implementation
 */

package com.eqraa.reader.ui.navigation

import android.view.KeyEvent
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.absoluteValue


/**
 * Navigation actions
 */
sealed class NavigationAction {
    object NextPage : NavigationAction()
    object PreviousPage : NavigationAction()
    object ToggleUI : NavigationAction()
    object ShowTOC : NavigationAction()
    object ShowProgress : NavigationAction()
}

/**
 * Tap zone configuration (Kindle-style)
 */
data class TapZoneConfig(
    val leftZonePercent: Float = 0.30f,      // Left 30% = Previous
    val rightZonePercent: Float = 0.30f,     // Right 30% = Next
    val centerZonePercent: Float = 0.40f     // Center 40% = Toggle UI
)

/**
 * Kindle-style navigation controller
 * 
 * Implements all Kindle navigation methods:
 * - Tap zones (left/right/center)
 * - Swipe gestures
 * - Volume buttons
 * - Progress slider
 */
class KindleNavigationController {
    private val _navigationEvents = MutableSharedFlow<NavigationAction>(extraBufferCapacity = 10)
    val navigationEvents: SharedFlow<NavigationAction> = _navigationEvents
    
    private val tapZoneConfig = TapZoneConfig()
    
    /**
     * Handle tap gesture with zone detection
     */
    fun handleTap(offset: Offset, size: IntSize) {
        val tapX = offset.x
        val width = size.width
        
        val action = when {
            tapX < width * tapZoneConfig.leftZonePercent -> NavigationAction.PreviousPage
            tapX > width * (1f - tapZoneConfig.rightZonePercent) -> NavigationAction.NextPage
            else -> NavigationAction.ToggleUI
        }
        
        _navigationEvents.tryEmit(action)
    }
    
    /**
     * Handle swipe gesture
     */
    fun handleSwipe(velocity: Offset) {
        val action = when {
            velocity.x > 500f -> NavigationAction.PreviousPage  // Swipe right
            velocity.x < -500f -> NavigationAction.NextPage     // Swipe left
            velocity.y > 500f -> NavigationAction.ShowProgress  // Swipe down
            velocity.y < -500f -> NavigationAction.ShowTOC      // Swipe up
            else -> return
        }
        
        _navigationEvents.tryEmit(action)
    }
    
    /**
     * Handle volume button press
     */
    fun handleVolumeButton(keyCode: Int): Boolean {
        val action = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> NavigationAction.PreviousPage
            KeyEvent.KEYCODE_VOLUME_DOWN -> NavigationAction.NextPage
            else -> return false
        }
        
        return _navigationEvents.tryEmit(action)
    }
}

/**
 * Kindle navigation gesture detector modifier
 */
@Composable
fun Modifier.kindleNavigation(
    controller: KindleNavigationController,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this
    
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    return this
        .onSizeChanged { size = it }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset ->
                    controller.handleTap(offset, size)
                }
            )
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    // Optionally handle drag end
                }
            ) { change, dragAmount ->
                if (dragAmount.x.absoluteValue > 50f || dragAmount.y.absoluteValue > 50f) {
                    controller.handleSwipe(Offset(dragAmount.x, dragAmount.y))
                    change.consume()
                }
            }
        }
}

/**
 * Visual tap zone indicators (for debugging/development)
 */
@Composable
fun TapZoneIndicators(
    modifier: Modifier = Modifier,
    config: TapZoneConfig = TapZoneConfig(),
    visible: Boolean = false
) {
    if (!visible) return
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        
        // Left zone
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { (width * config.leftZonePercent).toDp() })
        )
        
        // Right zone
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { (width * config.rightZonePercent).toDp() })
        )
    }
}
