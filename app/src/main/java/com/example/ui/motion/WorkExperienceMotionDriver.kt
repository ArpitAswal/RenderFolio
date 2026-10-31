package com.example.ui.motion

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * Manages the lateral entrance and exit motion lifecycle for Section 3 (Work Experience):
 * - Enters completely from beyond the left edge of the screen to its resting position with a smooth fade-in.
 * - When scrolled past (reaches its end / exits), animates back beyond the left edge with a fade-out.
 * - In reading zone (between top 20% and bottom 80% of screen): locked cleanly at translationX = 0dp, alpha = 1.0f.
 * - Duration: 1100ms (synchronized with Section 1 and Section 2).
 * - Initial launch delay: 350ms (synchronized with activity window transition).
 */
class WorkExperienceMotionDriver(
    private val sectionContainer: View
) {
    companion object {
        const val ENTRANCE_START_DELAY_MS: Long =
            350L     // Post-launch delay ensuring activity window transition finishes
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 550L // Harmonized responsive entrance (550ms)
        const val EXIT_TRANSLATION_DURATION_MS: Long = 350L // Snappy slide-out and fade-out duration (350ms)

        // Viewport threshold ratio (20% margin on both top and bottom)
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
        // Initial resting state: completely off the left edge of the screen and fully transparent
        val screenWidth = sectionContainer.resources.displayMetrics.widthPixels.toFloat()
        sectionContainer.translationX = -screenWidth
        sectionContainer.alpha = 0f
        sectionContainer.scaleX = 1.0f
        sectionContainer.scaleY = 1.0f
    }

    /**
     * Executes initial entrance on screen view:
     * Glides completely from beyond the left edge of the screen into place (translationX: -offset -> 0),
     * while smoothly fading in (alpha: 0 -> 1) over 1100ms.
     */
    fun playInitialEntrance(onComplete: (() -> Unit)? = null) {
        if (hasPlayedInitialEntrance) return

        if (sectionContainer.width == 0 || sectionContainer.height == 0) {
            sectionContainer.post {
                playInitialEntrance(onComplete)
            }
            return
        }

        hasPlayedInitialEntrance = true
        isInitialAnimationRunning = true
        currentState = MotionState.ENTERED_VISIBLE

        val startX = -offScreenOffsetPx
        sectionContainer.translationX = startX
        sectionContainer.alpha = 0f

        sectionContainer.animate()
            .translationX(0f)
            .alpha(1f)
            .setStartDelay(ENTRANCE_START_DELAY_MS)
            .setDuration(ENTRANCE_TRANSLATION_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(2.0f))
            .withEndAction {
                isInitialAnimationRunning = false
                sectionContainer.translationX = 0f
                sectionContainer.alpha = 1f
                onComplete?.invoke()
            }
            .start()
    }

    /**
     * Viewport Threshold State Tracker on scroll:
     * - Immediate responsiveness: Cancels initial animation if user begins scrolling right away.
     * - In reading zone: Slides in from beyond the left edge with fade-in.
     * - Exited top/bottom: Slides out beyond the left edge with fade-out.
     */
    fun onScrollChanged(viewportHeight: Int) {
        if (isInitialAnimationRunning) {
            sectionContainer.animate().cancel()
            isInitialAnimationRunning = false
            sectionContainer.translationX = 0f
            sectionContainer.alpha = 1f
            currentState = MotionState.ENTERED_VISIBLE
        }

        hasPlayedInitialEntrance = true

        val actualViewportHeight = if (viewportHeight > 0) {
            viewportHeight
        } else {
            sectionContainer.resources.displayMetrics.heightPixels
        }

        val locationOnScreen = IntArray(2)
        sectionContainer.getLocationOnScreen(locationOnScreen)
        val viewTop = locationOnScreen[1]
        val viewHeight = sectionContainer.height
        val viewBottom = viewTop + viewHeight

        if (viewHeight <= 0) return

        val topThreshold = actualViewportHeight * VIEWPORT_THRESHOLD_RATIO
        val bottomThreshold = actualViewportHeight * (1.0f - VIEWPORT_THRESHOLD_RATIO)

        // Reading / Active Viewport Zone:
        // Section is in view (top has entered into bottom 80% and bottom has not scrolled past top 20%)
        val inReadingZone = (viewTop < bottomThreshold) && (viewBottom > topThreshold)

        if (inReadingZone) {
            if (currentState != MotionState.ENTERED_VISIBLE) {
                currentState = MotionState.ENTERED_VISIBLE
                animateEntrance()
            }
            return
        }

        // Exited past the top of the viewport (bottom has passed above top 20% threshold)
        if (viewBottom <= topThreshold && currentState == MotionState.ENTERED_VISIBLE) {
            currentState = MotionState.OFF_TOP
            animateExit()
            return
        }

        // Exited below the bottom of the viewport (top has fallen below bottom 80% threshold)
        if (viewTop >= bottomThreshold && currentState == MotionState.ENTERED_VISIBLE) {
            currentState = MotionState.OFF_BOTTOM
            animateExit()
            return
        }
    }

    private fun animateEntrance() {
        sectionContainer.animate().cancel()
        sectionContainer.animate()
            .translationX(0f)
            .alpha(1f)
            .setStartDelay(0L)
            .setDuration(ENTRANCE_TRANSLATION_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(2.0f))
            .start()
    }

    private fun animateExit() {
        sectionContainer.animate().cancel()
        sectionContainer.animate()
            .translationX(-offScreenOffsetPx)
            .alpha(0f)
            .setStartDelay(0L)
            .setDuration(EXIT_TRANSLATION_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .start()
    }
}
