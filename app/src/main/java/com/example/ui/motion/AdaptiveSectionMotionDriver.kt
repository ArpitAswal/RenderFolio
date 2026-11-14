package com.example.ui.motion

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

enum class SlideDirection {
    FROM_LEFT,
    FROM_RIGHT,
    VERTICAL_LIFT
}

/**
 * Universal adaptive motion driver for resume sections.
 *
 * Implements position-based motion choreography:
 * 1. Single-Column (Mobile & Tablet Portrait): Alternates direction based on visual index:
 *    - 1st section after summary: FROM_LEFT (100ms delay)
 *    - 2nd section: FROM_RIGHT (200ms delay)
 *    - 3rd section: FROM_LEFT (300ms delay)
 *    - 4th section: FROM_RIGHT (400ms delay)
 *    - Always maintains strict Left -> Right -> Left -> Right visual rhythm.
 * 2. Dual-Pane (Tablet Landscape):
 *    - Left Pane sections enter from LEFT.
 *    - Right Pane sections enter from RIGHT.
 *    - Displacement is strictly bounded so sections never cross the center gutter.
 * 3. Continuous scroll-driven physics:
 *    - 20% viewport exit threshold when scrolling past top.
 *    - Reading zone locking at alpha = 1.0f, translationX = 0f, translationY = 0f.
 */
class AdaptiveSectionMotionDriver(
    val sectionContainer: View,
    private val scrollView: View? = null,
    val direction: SlideDirection = SlideDirection.FROM_LEFT,
    val startDelayMs: Long = 100L,
    private val boundToPane: Boolean = false
) {
    companion object {
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 550L
        const val EXIT_TRANSLATION_DURATION_MS: Long = 380L
        const val VIEWPORT_THRESHOLD_RATIO: Float = 0.20f
        const val VERTICAL_LIFT_DP: Float = 36f
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
            return if (boundToPane) {
                // In dual-pane mode, bound translation to pane width so it never crosses the center gutter
                if (viewWidth > 0f) viewWidth + 48f else (screenWidth * 0.45f)
            } else {
                // In single-column mode, full off-screen travel
                (if (viewWidth > 0f) viewWidth + 80f else screenWidth + 120f).coerceAtLeast(screenWidth)
            }
        }

    private val verticalLiftPx: Float
        get() = VERTICAL_LIFT_DP * sectionContainer.resources.displayMetrics.density

    init {
        val screenWidth = sectionContainer.resources.displayMetrics.widthPixels.toFloat()
        val initialOffset = if (boundToPane) (screenWidth * 0.45f) else screenWidth

        when (direction) {
            SlideDirection.FROM_LEFT -> {
                sectionContainer.translationX = -initialOffset
                sectionContainer.translationY = 0f
                sectionContainer.alpha = 0f
            }
            SlideDirection.FROM_RIGHT -> {
                sectionContainer.translationX = initialOffset
                sectionContainer.translationY = 0f
                sectionContainer.alpha = 0f
            }
            SlideDirection.VERTICAL_LIFT -> {
                sectionContainer.translationX = 0f
                sectionContainer.translationY = verticalLiftPx
                sectionContainer.scaleX = 0.94f
                sectionContainer.scaleY = 0.94f
                sectionContainer.alpha = 0f
            }
        }
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

            when (direction) {
                SlideDirection.FROM_LEFT -> {
                    sectionContainer.translationX = -offScreenOffsetPx
                    sectionContainer.alpha = 0f
                    sectionContainer.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setStartDelay(startDelayMs)
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
                SlideDirection.FROM_RIGHT -> {
                    sectionContainer.translationX = offScreenOffsetPx
                    sectionContainer.alpha = 0f
                    sectionContainer.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setStartDelay(startDelayMs)
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
                SlideDirection.VERTICAL_LIFT -> {
                    sectionContainer.translationY = verticalLiftPx
                    sectionContainer.scaleX = 0.94f
                    sectionContainer.scaleY = 0.94f
                    sectionContainer.alpha = 0f
                    sectionContainer.animate()
                        .translationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setStartDelay(startDelayMs)
                        .setDuration(ENTRANCE_TRANSLATION_DURATION_MS)
                        .setInterpolator(DecelerateInterpolator(2.0f))
                        .withEndAction {
                            isInitialAnimationRunning = false
                            sectionContainer.translationY = 0f
                            sectionContainer.scaleX = 1f
                            sectionContainer.scaleY = 1f
                            sectionContainer.alpha = 1f
                            onComplete?.invoke()
                        }
                        .start()
                }
            }
        } else {
            currentState = MotionState.OFF_BOTTOM
            when (direction) {
                SlideDirection.FROM_LEFT -> sectionContainer.translationX = -offScreenOffsetPx
                SlideDirection.FROM_RIGHT -> sectionContainer.translationX = offScreenOffsetPx
                SlideDirection.VERTICAL_LIFT -> {
                    sectionContainer.translationY = verticalLiftPx
                    sectionContainer.scaleX = 0.94f
                    sectionContainer.scaleY = 0.94f
                }
            }
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
            sectionContainer.translationY = 0f
            sectionContainer.scaleX = 1f
            sectionContainer.scaleY = 1f
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
        val topHideThreshold = minOf(viewHeight * VIEWPORT_THRESHOLD_RATIO, viewportHeight * VIEWPORT_THRESHOLD_RATIO).coerceAtLeast(40f)

        // 1. Exited past top of viewport
        if (relativeBottom <= topHideThreshold) {
            if (currentState == MotionState.ENTERED_VISIBLE) {
                currentState = MotionState.OFF_TOP
                animateExit()
            }
            return
        }

        // 2. Exited below bottom of viewport
        if (relativeTop >= viewportHeight) {
            if (currentState != MotionState.OFF_BOTTOM) {
                currentState = MotionState.OFF_BOTTOM
                sectionContainer.animate().cancel()
                when (direction) {
                    SlideDirection.FROM_LEFT -> sectionContainer.translationX = -offScreenOffsetPx
                    SlideDirection.FROM_RIGHT -> sectionContainer.translationX = offScreenOffsetPx
                    SlideDirection.VERTICAL_LIFT -> {
                        sectionContainer.translationY = verticalLiftPx
                        sectionContainer.scaleX = 0.94f
                        sectionContainer.scaleY = 0.94f
                    }
                }
                sectionContainer.alpha = 0f
            }
            return
        }

        // 3. In Reading Zone / Entering viewport
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
        when (direction) {
            SlideDirection.FROM_LEFT -> {
                if (sectionContainer.translationX == 0f && sectionContainer.alpha == 0f) {
                    sectionContainer.translationX = -offScreenOffsetPx
                }
                sectionContainer.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setStartDelay(0L)
                    .setDuration(ENTRANCE_TRANSLATION_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator(2.0f))
                    .start()
            }
            SlideDirection.FROM_RIGHT -> {
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
            SlideDirection.VERTICAL_LIFT -> {
                sectionContainer.animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setStartDelay(0L)
                    .setDuration(ENTRANCE_TRANSLATION_DURATION_MS)
                    .setInterpolator(DecelerateInterpolator(2.0f))
                    .start()
            }
        }
    }

    private fun animateExit() {
        sectionContainer.animate().cancel()
        when (direction) {
            SlideDirection.FROM_LEFT -> {
                sectionContainer.animate()
                    .translationX(-offScreenOffsetPx)
                    .alpha(0f)
                    .setStartDelay(0L)
                    .setDuration(EXIT_TRANSLATION_DURATION_MS)
                    .setInterpolator(AccelerateInterpolator(1.5f))
                    .start()
            }
            SlideDirection.FROM_RIGHT -> {
                sectionContainer.animate()
                    .translationX(offScreenOffsetPx)
                    .alpha(0f)
                    .setStartDelay(0L)
                    .setDuration(EXIT_TRANSLATION_DURATION_MS)
                    .setInterpolator(AccelerateInterpolator(1.5f))
                    .start()
            }
            SlideDirection.VERTICAL_LIFT -> {
                sectionContainer.animate()
                    .translationY(-verticalLiftPx * 0.7f)
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .alpha(0f)
                    .setStartDelay(0L)
                    .setDuration(EXIT_TRANSLATION_DURATION_MS)
                    .setInterpolator(AccelerateInterpolator(1.5f))
                    .start()
            }
        }
    }
}

