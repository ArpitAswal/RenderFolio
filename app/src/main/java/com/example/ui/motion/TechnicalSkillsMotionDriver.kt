package com.example.ui.motion

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * Manages the alternating lateral entrance and exit motion lifecycle for Section 5 (Technical Skills):
 * - Enters from beyond the LEFT edge of the screen (-screenWidth -> 0) over 1100ms.
 * - In reading zone (between top 20% and bottom 80%): locked at translationX = 0dp, alpha = 1.0f.
 * - When scrolled past: animates back beyond the LEFT edge (0 -> -screenWidth) over 400ms.
 * - Viewport threshold ratio: exactly 20%.
 */
class TechnicalSkillsMotionDriver(
    private val sectionContainer: View
) {
    companion object {
        const val ENTRANCE_START_DELAY_MS: Long = 350L
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 550L
        const val EXIT_TRANSLATION_DURATION_MS: Long = 350L
        const val VIEWPORT_THRESHOLD_RATIO: Float = 0.20f
    }

    enum class MotionState {
        OFF_BOTTOM,
        ENTERED_VISIBLE,
        OFF_TOP
    }

    private var currentState = MotionState.OFF_BOTTOM
    private var hasPlayedInitialEntrance = false
    private var isInitialAnimationRunning = false

    private val offScreenOffsetPx: Float
        get() {
            val screenWidth = sectionContainer.resources.displayMetrics.widthPixels.toFloat()
            val viewWidth = sectionContainer.width.toFloat()
            return (if (viewWidth > 0f) viewWidth + 100f else screenWidth + 200f).coerceAtLeast(screenWidth)
        }

    init {
        // Initial resting state: completely off the LEFT edge of the screen
        val screenWidth = sectionContainer.resources.displayMetrics.widthPixels.toFloat()
        sectionContainer.translationX = -screenWidth
        sectionContainer.alpha = 0f
        sectionContainer.scaleX = 1.0f
        sectionContainer.scaleY = 1.0f
    }

    fun playInitialEntrance(onComplete: (() -> Unit)? = null) {
        if (hasPlayedInitialEntrance) return
        hasPlayedInitialEntrance = true
        isInitialAnimationRunning = true

        sectionContainer.translationX = -offScreenOffsetPx
        sectionContainer.alpha = 0f

        sectionContainer.animate()
            .translationX(0f)
            .alpha(1f)
            .setStartDelay(ENTRANCE_START_DELAY_MS)
            .setDuration(ENTRANCE_TRANSLATION_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(2.0f))
            .withEndAction {
                isInitialAnimationRunning = false
                currentState = MotionState.ENTERED_VISIBLE
                sectionContainer.translationX = 0f
                sectionContainer.alpha = 1f
                onComplete?.invoke()
            }
            .start()
    }

    fun onScrollChanged(viewportHeight: Int) {
        if (isInitialAnimationRunning || viewportHeight <= 0) return

        val location = IntArray(2)
        sectionContainer.getLocationOnScreen(location)
        val viewTopOnScreen = location[1]
        val viewBottomOnScreen = viewTopOnScreen + sectionContainer.height

        val topThreshold = (viewportHeight * VIEWPORT_THRESHOLD_RATIO).toInt()
        val bottomThreshold = (viewportHeight * (1f - VIEWPORT_THRESHOLD_RATIO)).toInt()

        val isReadingZone = viewTopOnScreen < bottomThreshold && viewBottomOnScreen > topThreshold
        if (isReadingZone) {
            if (currentState != MotionState.ENTERED_VISIBLE) {
                currentState = MotionState.ENTERED_VISIBLE
                animateEntrance()
            }
            return
        }

        if (viewTopOnScreen >= bottomThreshold && currentState != MotionState.OFF_BOTTOM) {
            currentState = MotionState.OFF_BOTTOM
            animateExit(-offScreenOffsetPx)
            return
        }

        if (viewBottomOnScreen <= topThreshold && currentState != MotionState.OFF_TOP) {
            currentState = MotionState.OFF_TOP
            animateExit(-offScreenOffsetPx)
            return
        }
    }

    private fun animateEntrance() {
        sectionContainer.animate().cancel()
        sectionContainer.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(ENTRANCE_TRANSLATION_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(2.0f))
            .start()
    }

    private fun animateExit(targetOffset: Float) {
        sectionContainer.animate()
            .translationX(targetOffset)
            .alpha(0f)
            .setDuration(EXIT_TRANSLATION_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .start()
    }
}
