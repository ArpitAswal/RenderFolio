package com.example.ui.motion

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Manages the dual-phase motion lifecycle for Section 2 (Professional Overview & Summary):
 * 1. Phase 1 (Launch Entrance): Synchronized with Personal Info's smooth speed and timing
 *    (350ms delay to clear window transition, 1100ms fade-in, 1200ms scale-up, 1100ms translation lift).
 * 2. Phase 2 (On-Scroll Reactivity):
 *    - Real-time upward parallax glide (-20% scroll velocity) giving physical depth.
 *    - Dynamic perspective fade & subtle scale compression as section scrolls toward the top edge.
 *    - Rubber-band elastic over-scroll scaling (up to 1.08f) on pull-down past the top.
 */
class SummarySectionMotionDriver(
    private val sectionContainer: View
) {
    companion object {
        // --- DURATION AND TIMING CONSTANTS (SYNCHRONIZED WITH PERSONAL INFO) ---
        const val ENTRANCE_START_DELAY_MS: Long = 350L     // Post-launch delay ensuring activity window transition finishes
        const val ENTRANCE_ALPHA_DURATION_MS: Long = 1100L  // Fade-in duration (0f -> 1f)
        const val ENTRANCE_SCALE_DURATION_MS: Long = 1200L  // Scale-up duration (0.80f -> 1.0f)
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 1100L // Translation duration (50dp -> 0dp)

        const val INITIAL_SCALE: Float = 0.80f
        const val INITIAL_TRANSLATION_DP: Float = 50f
    }

    private var entranceAnimator: AnimatorSet? = null
    private var isEntranceAnimationRunning = false
    private var hasPlayedInitialEntrance = false
    private var currentScrollY: Int = 0

    init {
        // Pre-configure initial state for synchronized entrance
        val density = sectionContainer.resources.displayMetrics.density
        sectionContainer.alpha = 0f
        sectionContainer.scaleX = INITIAL_SCALE
        sectionContainer.scaleY = INITIAL_SCALE
        sectionContainer.translationY = INITIAL_TRANSLATION_DP * density
    }

    /**
     * Phase 1: Executes initial entrance animation at the exact same speed and timing as Personal Info.
     */
    fun playInitialEntrance(onComplete: (() -> Unit)? = null) {
        if (hasPlayedInitialEntrance) return

        // Ensure the container has completed layout so center pivot is precise
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

    /**
     * Phase 2: Coordinates reactive on-scroll motion with the scroll container:
     * - Immediate scroll response: cancels running entrance animator if user scrolls right away.
     * - Downward scrolling: Smooth upward parallax glide (-20% scroll velocity) + perspective fade/scale exit.
     * - Pull-down past top (over-scroll): Elastic rubber-band scaling up to 1.08f.
     */
    fun onScrollChanged(scrollY: Int) {
        currentScrollY = scrollY

        // If user touches and scrolls while entrance animation is running, cancel it so scroll motion is immediate
        if (isEntranceAnimationRunning) {
            entranceAnimator?.cancel()
            isEntranceAnimationRunning = false
        }

        hasPlayedInitialEntrance = true

        val density = sectionContainer.resources.displayMetrics.density

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
            // 1. Upward Parallax Glide: section floats with subtle lag (-20% velocity)
            sectionContainer.translationY = -scrollY * 0.20f

            // 2. Scroll-driven Perspective Fade & Subtle Compression
            // Fades gracefully as it scrolls up into the header exit zone
            val fadeStartPx = 140f * density
            val fadeEndPx = 480f * density

            if (scrollY <= fadeStartPx) {
                sectionContainer.alpha = 1.0f
                sectionContainer.scaleX = 1.0f
                sectionContainer.scaleY = 1.0f
            } else {
                val progress = ((scrollY - fadeStartPx) / (fadeEndPx - fadeStartPx)).coerceIn(0f, 1f)
                // Smooth linear fade
                sectionContainer.alpha = 1.0f - (progress * 0.90f) // down to 0.10f or 0.0f
                // Slight perspective compression
                val scale = 1.0f - (progress * 0.05f)
                sectionContainer.scaleX = scale
                sectionContainer.scaleY = scale
                sectionContainer.pivotX = sectionContainer.width / 2f
                sectionContainer.pivotY = sectionContainer.height * 0.5f
            }
        }
    }
}
