/*
 * Copyright 2024 Eqraa. All rights reserved.
 */

package com.eqraa.reader.focus

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.eqraa.reader.utils.enterFocusMode
import com.eqraa.reader.utils.showSystemUi
import timber.log.Timber

/**
 * Controller for Focus Mode features:
 * - Immersive Sticky UI (swipe to reveal bars)
 * - Blue Light Filter overlay
 * - Keeps screen on while reading
 */
class FocusModeController(
    private val activity: Activity
) {
    private var isInFocusMode = false
    private var blueLightFilterView: View? = null
    private var blueLightIntensity: Float = 0.3f // 0.0 - 1.0

    /**
     * Toggle Focus Mode on/off.
     */
    fun toggle() {
        if (isInFocusMode) {
            exit()
        } else {
            enter()
        }
    }

    /**
     * Enter Focus Mode: Immersive sticky, keep screen on.
     */
    fun enter() {
        activity.enterFocusMode()
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        isInFocusMode = true
        Timber.d("FocusMode: Entered")
    }

    /**
     * Exit Focus Mode: Show system UI, allow screen off.
     */
    fun exit() {
        activity.showSystemUi()
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBlueLightFilter()
        isInFocusMode = false
        Timber.d("FocusMode: Exited")
    }

    fun isActive(): Boolean = isInFocusMode

    // ========================================
    // Blue Light Filter
    // ========================================

    /**
     * Shows the blue light filter overlay.
     * @param intensity 0.0 (off) to 1.0 (maximum warmth)
     */
    fun showBlueLightFilter(intensity: Float = blueLightIntensity) {
        blueLightIntensity = intensity.coerceIn(0f, 1f)

        if (blueLightFilterView == null) {
            blueLightFilterView = View(activity).apply {
                setBackgroundColor(Color.argb((blueLightIntensity * 100).toInt(), 255, 180, 50))
                isClickable = false
                isFocusable = false
            }

            val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            (activity.window.decorView as ViewGroup).addView(blueLightFilterView, params)
            Timber.d("BlueLightFilter: Shown with intensity $blueLightIntensity")
        } else {
            blueLightFilterView?.setBackgroundColor(
                Color.argb((blueLightIntensity * 100).toInt(), 255, 180, 50)
            )
        }
    }

    /**
     * Hides the blue light filter overlay.
     */
    fun hideBlueLightFilter() {
        blueLightFilterView?.let {
            (activity.window.decorView as ViewGroup).removeView(it)
            blueLightFilterView = null
            Timber.d("BlueLightFilter: Hidden")
        }
    }

    /**
     * Toggles the blue light filter.
     */
    fun toggleBlueLightFilter() {
        if (blueLightFilterView == null) {
            showBlueLightFilter()
        } else {
            hideBlueLightFilter()
        }
    }

    /**
     * Updates the intensity of the blue light filter.
     */
    fun setBlueLightIntensity(intensity: Float) {
        blueLightIntensity = intensity.coerceIn(0f, 1f)
        if (blueLightFilterView != null) {
            showBlueLightFilter(blueLightIntensity)
        }
    }
}
