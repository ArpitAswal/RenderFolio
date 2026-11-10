package com.example.ui.motion

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * Manages the vice-versa lateral entrance and exit motion lifecycle for Section 4 (Featured Projects):
 * - Enters completely from beyond the RIGHT edge of the screen (+offScreenOffsetPx -> 0dp) with smooth fade-in.
 * - In reading zone: locked cleanly at translationX = 0dp, alpha = 1.0f.
 * - When scrolled past (last 20% remaining when scrolling downside), animates back beyond the RIGHT edge with fade-out.
 * - Duration: responsive entrance (600ms), exit (400ms).
 */
class ProjectsMotionDriver(
    private val sectionContainer: View,
    private val scrollView: View? = null
) {
    companion object {
        const val ENTRANCE_START_DELAY_MS: Long = 350L
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 600L
        const val EXIT_TRANSLATION_DURATION_MS: Long = 400L

        // Viewport threshold ratio (last 20% of section view)
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
        val screenWidth = sectionContainer.resources.displayMetrics.widthPixels.toFloat()
        sectionContainer.translationX = screenWidth
        sectionContainer.alpha = 0f
        sectionContainer.scaleX = 1.0f
        sectionContainer.scaleY = 1.0f
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

            val startX = offScreenOffsetPx
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
        } else {
            currentState = MotionState.OFF_BOTTOM
            sectionContainer.translationX = offScreenOffsetPx
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
            sectionContainer.translationX = 0f
            sectionContainer.alpha = 1f
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
                sectionContainer.translationX = offScreenOffsetPx
                sectionContainer.alpha = 0f
            }
            return
        }

        // 3. In Reading Zone / Entering viewport:
        // Trigger entrance as soon as the section top enters into the viewport area
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
        if (sectionContainer.translationX == 0f && sectionContainer.alpha == 0f) {
            sectionContainer.translationX = offScreenOffsetPx
        }
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
            .translationX(offScreenOffsetPx)
            .alpha(0f)
            .setStartDelay(0L)
            .setDuration(EXIT_TRANSLATION_DURATION_MS)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .start()
    }
}
