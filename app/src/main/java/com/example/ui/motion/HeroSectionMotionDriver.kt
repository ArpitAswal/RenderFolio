package com.example.ui.motion

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * HeroSectionMotionDriver
 *
 * Drives whole-section motion on Section 1 (hero_container), synchronized with Section 2 (Summary) style:
 *
 * 1. Initial Load Entrance Animation:
 *    - Pinned at top edge (pivotX = 50%, pivotY = 0f).
 *    - Starts from alpha = 0f, scale = 0.80f, translationY = 50dp.
 *    - Starts after 350ms delay to clear window transitions.
 *    - Interpolates to alpha = 1.0f, scale = 1.0f, translationY = 0dp over 1100ms/1200ms.
 *
 * 2. Continuous Scroll Physics:
 *    - Upward Parallax Glide: container translates at 20% scroll speed (-scrollY * 0.20f).
 *    - Perspective Fade & Compression: between 140dp and 480dp, gracefully dims and compresses scale.
 *    - Elastic Pull-Down Scaling: when scrolled past 0 (rubber-band), scales up to 1.08f.
 *    - Viewport 20% Threshold: fully fades to 0 when bottom passes top 20% threshold.
 */
class HeroSectionMotionDriver(
    private val heroContainer: View,
    private val maxFadeDistancePx: Float = 400f
) {
    companion object {
        // --- DURATION AND TIMING CONSTANTS (SYNCHRONIZED WITH SECTION 2) ---
        const val ENTRANCE_START_DELAY_MS: Long = 350L     // Post-launch delay ensuring activity window transition finishes
        const val ENTRANCE_ALPHA_DURATION_MS: Long = 1100L  // Fade-in duration (0f -> 1f)
        const val ENTRANCE_SCALE_DURATION_MS: Long = 1200L  // Scale-up duration (0.80f -> 1.0f)
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 1100L // Translation duration (50dp -> 0dp)

        const val INITIAL_SCALE: Float = 0.80f // Synchronized with Section 2
        const val INITIAL_TRANSLATION_DP: Float = 50f // Synchronized with Section 2
        const val VIEWPORT_THRESHOLD_RATIO: Float = 0.20f // Viewport threshold
    }

    private var isEntranceAnimated = false
    private var currentScrollY: Int = 0

    init {
        // Pre-configure initial state for smooth entrance
        val density = heroContainer.resources.displayMetrics.density
        heroContainer.alpha = 0f
        heroContainer.scaleX = INITIAL_SCALE
        heroContainer.scaleY = INITIAL_SCALE
        heroContainer.translationY = INITIAL_TRANSLATION_DP * density
    }

    /**
     * Executes the initial fade-and-scale-up entrance animation matching Section 2.
     */
    fun startEntranceAnimation(onComplete: (() -> Unit)? = null) {
        if (isEntranceAnimated) return

        // Ensure the container has completed measurement so pivot is precise
        if (heroContainer.width == 0 || heroContainer.height == 0) {
            heroContainer.post {
                startEntranceAnimation(onComplete)
            }
            return
        }

        isEntranceAnimated = true

        val density = heroContainer.resources.displayMetrics.density
        val startTransY = INITIAL_TRANSLATION_DP * density

        heroContainer.pivotX = heroContainer.width / 2f
        heroContainer.pivotY = 0f // Pinned at top edge, matching Section 2

        val alphaAnim = ObjectAnimator.ofFloat(heroContainer, View.ALPHA, 0f, 1f).apply {
            duration = ENTRANCE_ALPHA_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
        }

        val scaleXAnim = ObjectAnimator.ofFloat(heroContainer, View.SCALE_X, INITIAL_SCALE, 1.0f).apply {
            duration = ENTRANCE_SCALE_DURATION_MS
            interpolator = OvershootInterpolator(1.05f) // Matching Section 2
        }

        val scaleYAnim = ObjectAnimator.ofFloat(heroContainer, View.SCALE_Y, INITIAL_SCALE, 1.0f).apply {
            duration = ENTRANCE_SCALE_DURATION_MS
            interpolator = OvershootInterpolator(1.05f) // Matching Section 2
        }

        val transYAnim = ObjectAnimator.ofFloat(heroContainer, View.TRANSLATION_Y, startTransY, 0f).apply {
            duration = ENTRANCE_TRANSLATION_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
        }

        AnimatorSet().apply {
            startDelay = ENTRANCE_START_DELAY_MS
            playTogether(alphaAnim, scaleXAnim, scaleYAnim, transYAnim)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Re-apply any scroll position offset that might have occurred during launch
                    onScrollChanged(currentScrollY)
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    /**
     * Drives scroll-based parallax, fade, elastic pull-down scaling, and 20% viewport threshold.
     */
    fun onScrollChanged(scrollY: Int, viewportHeight: Int = 0) {
        currentScrollY = scrollY

        // Only apply scroll physics once the initial entrance animation has completed or started
        if (!isEntranceAnimated) return

        val density = heroContainer.resources.displayMetrics.density
        val actualViewportHeight = if (viewportHeight > 0) {
            viewportHeight
        } else {
            heroContainer.resources.displayMetrics.heightPixels
        }

        if (scrollY < 0) {
            // Elastic pull-down over-scroll (rubber-band past the top) - matching Section 2
            val pullDistance = kotlin.math.abs(scrollY).toFloat()
            val scale = (1.0f + (pullDistance * 0.0008f)).coerceAtMost(1.08f)
            heroContainer.scaleX = scale
            heroContainer.scaleY = scale
            heroContainer.pivotX = heroContainer.width / 2f
            heroContainer.pivotY = 0f
            heroContainer.translationY = 0f
            heroContainer.alpha = 1.0f
        } else {
            // DOWNWARD SCROLLING (scrollY >= 0)
            // 1. Upward Parallax Glide: section floats with subtle lag (-20% velocity) - matching Section 2
            heroContainer.translationY = -scrollY * 0.20f

            // 2. Scroll-driven Perspective Fade & Subtle Compression - matching Section 2
            val fadeStartPx = 140f * density
            val fadeEndPx = 480f * density

            if (scrollY <= fadeStartPx) {
                heroContainer.alpha = 1.0f
                heroContainer.scaleX = 1.0f
                heroContainer.scaleY = 1.0f
            } else {
                val progress = ((scrollY - fadeStartPx) / (fadeEndPx - fadeStartPx)).coerceIn(0f, 1f)
                heroContainer.alpha = 1.0f - (progress * 0.90f)
                val scale = 1.0f - (progress * 0.05f)
                heroContainer.scaleX = scale
                heroContainer.scaleY = scale
                heroContainer.pivotX = heroContainer.width / 2f
                heroContainer.pivotY = heroContainer.height * 0.5f
            }

            // 3. Viewport 20% Threshold Guard: if bottom of view has crossed above top 20% of screen
            val topThreshold = actualViewportHeight * VIEWPORT_THRESHOLD_RATIO
            val locationOnScreen = IntArray(2)
            heroContainer.getLocationOnScreen(locationOnScreen)
            val viewBottom = locationOnScreen[1] + heroContainer.height
            if (viewBottom <= topThreshold) {
                heroContainer.alpha = 0f
            }
        }
    }
}
