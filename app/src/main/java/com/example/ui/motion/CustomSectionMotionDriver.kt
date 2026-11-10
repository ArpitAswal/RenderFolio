package com.example.ui.motion

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * Manages the "Credential Scale Reveal" for dynamic custom sections:
 * - Expands outward symmetrically from geometric center (pivotX = 50%, pivotY = 50%).
 * - Initial state: scaleX/Y = 0.90f, alpha = 0f, translationY = 28dp.
 * - Viewport threshold: last 20% of section view when scrolling downside.
 * - Reading zone: locked at scale = 1.0f, alpha = 1.0f, translationY = 0dp.
 * - Exit past top when scrolling downside: gently recedes over 400ms.
 */
class CustomSectionMotionDriver(
    private val sectionContainer: View,
    private val scrollView: View? = null
) {
    companion object {
        const val ENTRANCE_START_DELAY_MS: Long = 350L
        const val ENTRANCE_DURATION_MS: Long = 600L
        const val EXIT_DURATION_MS: Long = 400L
        const val VIEWPORT_THRESHOLD_RATIO: Float = 0.20f
        const val INITIAL_SCALE: Float = 0.90f
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
        sectionContainer.scaleX = INITIAL_SCALE
        sectionContainer.scaleY = INITIAL_SCALE
        sectionContainer.translationY = initialLiftPx
        sectionContainer.alpha = 0f
    }

    fun playInitialEntrance(onComplete: (() -> Unit)? = null) {
        if (hasPlayedInitialEntrance) return

        if (sectionContainer.width == 0 || sectionContainer.height == 0) {
            sectionContainer.post {
                playInitialEntrance(onComplete)
            }
            return
        }

        hasPlayedInitialEntrance = true

        val scrollLoc = IntArray(2)
        scrollView?.getLocationOnScreen(scrollLoc)
        val viewLoc = IntArray(2)
        sectionContainer.getLocationOnScreen(viewLoc)

        val relativeTop = viewLoc[1] - scrollLoc[1]
        val viewportHeight = if (scrollView != null && scrollView.height > 0) {
            scrollView.height
        } else {
            sectionContainer.resources.displayMetrics.heightPixels
        }

        if (relativeTop < viewportHeight) {
            currentState = MotionState.ENTERED_VISIBLE
            isInitialAnimationRunning = true

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
                    sectionContainer.scaleX = 1.0f
                    sectionContainer.scaleY = 1.0f
                    sectionContainer.translationY = 0f
                    sectionContainer.alpha = 1.0f
                    onComplete?.invoke()
                }
                .start()
        } else {
            currentState = MotionState.OFF_BOTTOM
            sectionContainer.scaleX = INITIAL_SCALE
            sectionContainer.scaleY = INITIAL_SCALE
            sectionContainer.translationY = initialLiftPx
            sectionContainer.alpha = 0f
            onComplete?.invoke()
        }
    }

    fun onScrollChanged(scrollViewRef: View) {
        onScrollChangedInternal(scrollViewRef, scrollViewRef.height)
    }

    fun onScrollChanged(viewportHeight: Int) {
        onScrollChangedInternal(scrollView, viewportHeight)
    }

    private fun onScrollChangedInternal(targetScrollView: View?, viewportHeightParam: Int) {
        if (isInitialAnimationRunning) {
            sectionContainer.animate().cancel()
            isInitialAnimationRunning = false
            sectionContainer.scaleX = 1.0f
            sectionContainer.scaleY = 1.0f
            sectionContainer.translationY = 0f
            sectionContainer.alpha = 1.0f
            currentState = MotionState.ENTERED_VISIBLE
        }

        hasPlayedInitialEntrance = true

        val viewHeight = sectionContainer.height
        if (viewHeight <= 0) return

        val scrollLoc = IntArray(2)
        val activeScrollView = targetScrollView ?: scrollView
        activeScrollView?.getLocationOnScreen(scrollLoc)

        val viewLoc = IntArray(2)
        sectionContainer.getLocationOnScreen(viewLoc)

        val relativeTop = viewLoc[1] - scrollLoc[1]
        val relativeBottom = relativeTop + viewHeight

        val viewportHeight = if (activeScrollView != null && activeScrollView.height > 0) {
            activeScrollView.height
        } else if (viewportHeightParam > 0) {
            viewportHeightParam
        } else {
            sectionContainer.resources.displayMetrics.heightPixels
        }

        val density = sectionContainer.resources.displayMetrics.density

        // Hide threshold at the top: when scrolling downside, as soon as the last 20% of section view
        // remains visible before exiting the top of the viewport, animate to hide.
        val topHideThreshold = minOf(viewHeight * VIEWPORT_THRESHOLD_RATIO, viewportHeight * VIEWPORT_THRESHOLD_RATIO).coerceAtLeast(40f)

        // 1. Exited past top of viewport (bottom has passed above the last 20% threshold)
        if (relativeBottom <= topHideThreshold) {
            if (currentState == MotionState.ENTERED_VISIBLE) {
                currentState = MotionState.OFF_TOP
                animateExit()
            }
            return
        }

        // 2. Exited below bottom of viewport (top has fallen below viewport bottom)
        if (relativeTop >= viewportHeight) {
            if (currentState != MotionState.OFF_BOTTOM) {
                currentState = MotionState.OFF_BOTTOM
                sectionContainer.animate().cancel()
                sectionContainer.scaleX = INITIAL_SCALE
                sectionContainer.scaleY = INITIAL_SCALE
                sectionContainer.translationY = initialLiftPx
                sectionContainer.alpha = 0f
            }
            return
        }

        // 3. In Reading Zone / Entering viewport:
        val entranceTriggerBottom = viewportHeight - (60f * density)
        if (relativeTop <= entranceTriggerBottom) {
            if (currentState != MotionState.ENTERED_VISIBLE) {
                currentState = MotionState.ENTERED_VISIBLE
                animateEntrance()
            }
        }
    }

    private fun animateEntrance() {
        sectionContainer.animate().cancel()
        sectionContainer.pivotX = sectionContainer.width / 2f
        sectionContainer.pivotY = sectionContainer.height / 2f
        if (sectionContainer.alpha == 0f) {
            sectionContainer.scaleX = INITIAL_SCALE
            sectionContainer.scaleY = INITIAL_SCALE
            sectionContainer.translationY = initialLiftPx
        }
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
            .translationY(-20f * sectionContainer.resources.displayMetrics.density)
            .alpha(0f)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .start()
    }
}
