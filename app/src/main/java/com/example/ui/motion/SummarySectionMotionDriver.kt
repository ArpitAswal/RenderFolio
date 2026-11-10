package com.example.ui.motion

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Manages the continuous motion lifecycle for Section 2 (Professional Overview & Summary):
 * 1. Initial Launch Entrance: Synchronized with Personal Info (350ms delay, fade-in, scale-up, translation lift).
 * 2. Continuous On-Scroll Physics:
 *    - Real-time upward parallax glide (-12% velocity) for physical depth.
 *    - Subtle perspective scale compression as section scrolls toward the top edge.
 *    - Full reading opacity (alpha = 1.0f) while on screen for crisp, high-contrast legibility.
 *    - 20% Viewport Exit: smooth fade-out as the last 20% of the section reaches the top of the viewport.
 *    - Rubber-band elastic over-scroll scaling (up to 1.08f) on pull-down past the top.
 */
class SummarySectionMotionDriver(
    private val sectionContainer: View,
    private val scrollView: View? = null
) {
    companion object {
        const val ENTRANCE_START_DELAY_MS: Long = 350L
        const val ENTRANCE_ALPHA_DURATION_MS: Long = 1100L
        const val ENTRANCE_SCALE_DURATION_MS: Long = 1200L
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 1100L

        const val INITIAL_SCALE: Float = 0.80f
        const val INITIAL_TRANSLATION_DP: Float = 50f
        const val VIEWPORT_THRESHOLD_RATIO: Float = 0.20f
    }

    private var entranceAnimator: AnimatorSet? = null
    private var isEntranceAnimationRunning = false
    private var hasPlayedInitialEntrance = false
    private var currentScrollY: Int = 0

    init {
        val density = sectionContainer.resources.displayMetrics.density
        sectionContainer.alpha = 0f
        sectionContainer.scaleX = INITIAL_SCALE
        sectionContainer.scaleY = INITIAL_SCALE
        sectionContainer.translationY = INITIAL_TRANSLATION_DP * density
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
        isEntranceAnimationRunning = true

        val density = sectionContainer.resources.displayMetrics.density
        val startTransY = INITIAL_TRANSLATION_DP * density

        sectionContainer.pivotX = sectionContainer.width / 2f
        sectionContainer.pivotY = 0f

        val alphaAnim = ObjectAnimator.ofFloat(sectionContainer, View.ALPHA, 0f, 1f).apply {
            duration = ENTRANCE_ALPHA_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
        }

        val scaleXAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_X, INITIAL_SCALE, 1.0f).apply {
            duration = ENTRANCE_SCALE_DURATION_MS
            interpolator = OvershootInterpolator(1.05f)
        }

        val scaleYAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_Y, INITIAL_SCALE, 1.0f).apply {
            duration = ENTRANCE_SCALE_DURATION_MS
            interpolator = OvershootInterpolator(1.05f)
        }

        val transYAnim = ObjectAnimator.ofFloat(sectionContainer, View.TRANSLATION_Y, startTransY, 0f).apply {
            duration = ENTRANCE_TRANSLATION_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
        }

        val animatorSet = AnimatorSet().apply {
            startDelay = ENTRANCE_START_DELAY_MS
            playTogether(alphaAnim, scaleXAnim, scaleYAnim, transYAnim)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isEntranceAnimationRunning = false
                    onScrollChanged(currentScrollY)
                    onComplete?.invoke()
                }
            })
        }
        entranceAnimator = animatorSet
        animatorSet.start()
    }

    fun onScrollChanged(scrollY: Int, viewportHeight: Int = 0) {
        currentScrollY = scrollY

        // If user touches and scrolls while entrance animation is running, cancel and snap to scroll physics
        if (isEntranceAnimationRunning) {
            entranceAnimator?.cancel()
            isEntranceAnimationRunning = false
        }

        hasPlayedInitialEntrance = true

        val density = sectionContainer.resources.displayMetrics.density
        val actualViewportHeight = if (viewportHeight > 0) {
            viewportHeight
        } else if (scrollView != null && scrollView.height > 0) {
            scrollView.height
        } else {
            sectionContainer.resources.displayMetrics.heightPixels
        }

        if (scrollY < 0) {
            // Elastic pull-down over-scroll (rubber-band past the top)
            val pullDistance = kotlin.math.abs(scrollY).toFloat()
            val scale = (1.0f + (pullDistance * 0.0008f)).coerceAtMost(1.08f)
            sectionContainer.scaleX = scale
            sectionContainer.scaleY = scale
            sectionContainer.pivotX = sectionContainer.width / 2f
            sectionContainer.pivotY = 0f
            sectionContainer.translationY = 0f
            sectionContainer.alpha = 1.0f
        } else {
            // DOWNWARD SCROLLING (scrollY >= 0)
            // 1. Maintain locked layout alignment so gap to Experience section remains exactly 32dp
            sectionContainer.translationY = 0f

            val scrollLoc = IntArray(2)
            scrollView?.getLocationOnScreen(scrollLoc)
            val viewLoc = IntArray(2)
            sectionContainer.getLocationOnScreen(viewLoc)

            val relativeTop = viewLoc[1] - scrollLoc[1]
            val viewHeight = sectionContainer.height.toFloat().coerceAtLeast(100f)
            val relativeBottom = relativeTop + viewHeight

            // 2. Early, Graceful Top Exit Fade (Synchronized with Profile Info):
            // - In Reading Zone: alpha = 1.0f (crisp, 100% legibility).
            // - Early Exit Fade: As the section scrolls past the top, once the upper portion
            //   begins crossing above the viewport top (relativeBottom <= fadeThreshold),
            //   it immediately begins a smooth, early fade and subtle scale compression.
            //   The entire remaining content (Email/cards/body lines) visibly animates and
            //   fades smoothly from 1.0f down to 0.0f across the upper screen,
            //   exactly matching Profile Info's early, visible exit animation!
            val fadeThreshold = viewHeight * 0.92f
            if (relativeBottom >= fadeThreshold) {
                sectionContainer.alpha = 1.0f
                sectionContainer.scaleX = 1.0f
                sectionContainer.scaleY = 1.0f
            } else if (relativeBottom > 0f) {
                val progress = (relativeBottom / fadeThreshold).coerceIn(0f, 1f)
                sectionContainer.alpha = progress
                val scale = 0.95f + (progress * 0.05f)
                sectionContainer.scaleX = scale
                sectionContainer.scaleY = scale
                sectionContainer.pivotX = sectionContainer.width / 2f
                sectionContainer.pivotY = 0f
            } else {
                sectionContainer.alpha = 0f
            }
        }
    }
}
