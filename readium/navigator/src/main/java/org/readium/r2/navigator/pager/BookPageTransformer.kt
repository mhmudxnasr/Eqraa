/*
 * Copyright 2024 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.navigator.pager

import android.view.View
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

/**
 * A ViewPager PageTransformer that simulates a book page flip effect.
 */
public class BookPageTransformer : ViewPager.PageTransformer {
    override fun transformPage(view: View, position: Float) {
        val width = view.width.toFloat()
        
        when {
            position < -1 -> { // [-Infinity,-1)
                // This page is way off-screen to the left.
                view.alpha = 0f
            }
            position <= 0 -> { // [-1,0]
                // Left page stays flat
                view.alpha = 1f
                view.translationX = 0f
                view.rotationY = 0f
                view.scaleX = 1f
                view.scaleY = 1f
            }
            position <= 1 -> { // (0,1]
                // Right page flips/stacks
                view.alpha = 1f
                
                // Counteract the default slide transition
                view.translationX = -width * position
                
                // Tilt the page slightly like it's being lifted
                view.pivotX = 0f
                view.pivotY = view.height / 2f
                view.rotationY = -15f * position
                
                // Subtle scaling
                val scaleFactor = 0.95f + (0.05f * (1 - position))
                view.scaleX = scaleFactor
                view.scaleY = scaleFactor
                
                // Dim the page as it moves away
                // view.findViewById<View>(R.id.shadow_overlay)?.alpha = position // If we had a shadow view
            }
            else -> { // (1,+Infinity]
                // This page is way off-screen to the right.
                view.alpha = 0f
            }
        }
    }
}
