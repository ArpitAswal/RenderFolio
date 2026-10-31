package com.example.ui.motion

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * Manages the "Credential Scale Reveal" for dynamic custom sections:
 * - Expands outward symmetrically from geometric center (pivotX = 50%, pivotY = 50%).
 * - Initial state: scaleX/Y = 0.92f, alpha = 0f, translationY = 28dp.
 * - Viewport threshold: 20%.
 * - Reading zone: locked at scale = 1.0f, alpha = 1.0f.
 * - Exit past top/bottom: gently recedes over 350ms.
 */
class CustomSectionMotionDriver(
    private val sectionContainer: View
) {
    companion object {
        const val ENTRANCE_START_DELAY_MS: Long = 350L
        const val ENTRANCE_DURATION_MS: Long = 1100L // Synchronized with Sections 3..6 (1100ms)
        const val EXIT_DURATION_MS: Long = 400L // Synchronized with Sections 3..6 (400ms)
        const val VIEWPORT_THRESHOLD_RATIO: Float = 0.20f
        const val INITIAL_SCALE: Float = 0.92f
        const val INITIAL_LIFT_DP: Float = 28f
    }

    enum class MotionState {
        OFF_BOTTOM,
        ENTERED_VISIBLE,
        OFF_TOP
    }

    private var currentState = MotionState.OFF_BOTTOM
    private var hasPlayedInitialEntrance = false
    private var isInitialAnimationRunning = false

    private val initialLiftPx: Float
        get() = INITIAL_LIFT_DP * sectionContainer.resources.displayMetrics.density

    init {
        // Prepare initial resting state: scale 0.92, translationY +28dp, alpha 0
        sectionContainer.scaleX = INITIAL_SCALE
        sectionContainer.scaleY = INITIAL_SCALE
        sectionContainer.translationY = initialLiftPx
        sectionContainer.alpha = 0f
    }

    fun playInitialEntrance(onComplete: (() -> Unit)? = null) {
        if (hasPlayedInitialEntrance) return
        hasPlayedInitialEntrance = true
        isInitialAnimationRunning = true

        // Ensure pivot is dead center
        sectionContainer.pivotX = sectionContainer.width / 2f
        sectionContainer.pivotY = sectionContainer.height / 2f

        sectionContainer.scaleX = INITIAL_SCALE
        sectionContainer.scaleY = INITIAL_SCALE
        sectionContainer.translationY = initialLiftPx
        sectionContainer.alpha = 0f

        sectionContainer.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .translationY(0f)
            .alpha(1.0f)
            .setStartDelay(ENTRANCE_START_DELAY_MS)
            .setDuration(ENTRANCE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(2.0f))
            .withEndAction {
                isInitialAnimationRunning = false
                currentState = MotionState.ENTERED_VISIBLE
                sectionContainer.scaleX = 1.0f
                sectionContainer.scaleY = 1.0f
                sectionContainer.translationY = 0f
                sectionContainer.alpha = 1.0f
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

        // 1. Reading Zone: locked at full scale and opacity
        val isReadingZone = viewTopOnScreen < bottomThreshold && viewBottomOnScreen > topThreshold
        if (isReadingZone) {
            if (currentState != MotionState.ENTERED_VISIBLE) {
                currentState = MotionState.ENTERED_VISIBLE
                animateEntrance()
            }
            return
        }

        // 2. Scrolled below bottom threshold -> recede
        if (viewTopOnScreen >= bottomThreshold && currentState != MotionState.OFF_BOTTOM) {
            currentState = MotionState.OFF_BOTTOM
            animateExit()
            return
        }

        // 3. Scrolled above top threshold -> recede
        if (viewBottomOnScreen <= topThreshold && currentState != MotionState.OFF_TOP) {
            currentState = MotionState.OFF_TOP
            animateExit()
            return
        }
    }

    private fun animateEntrance() {
        sectionContainer.animate().cancel()
        sectionContainer.pivotX = sectionContainer.width / 2f
        sectionContainer.pivotY = sectionContainer.height / 2f
        sectionContainer.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .translationY(0f)
            .alpha(1.0f)
            .setDuration(ENTRANCE_DURATION_MS)
            .setInterpolator(DecelerateInterpolator(2.0f))
            .start()
    }

    private fun animateExit() {
        sectionContainer.animate().cancel()
        sectionContainer.pivotX = sectionContainer.width / 2f
        sectionContainer.pivotY = sectionContainer.height / 2f
        sectionContainer.animate()
            .scaleX(INITIAL_SCALE)
            .scaleY(INITIAL_SCALE)
            .translationY(initialLiftPx)
            .alpha(0f)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .start()
    }
}
