package com.example.ui.motion

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * HeroSectionMotionDriver
 *
 * Drives whole-section unified motion on Section 1 (hero_container):
 *
 * 1. Initial Load Entrance Animation:
 *    - Prominent, clearly perceptible fade-and-scale-up animation on initial screen view.
 *    - Starts from alpha = 0f, scale = 0.82f, translationY = 60dp.
 *    - Starts after a 120ms initial launch pause to ensure window transition finishes completely,
 *      making the animation visibly noticeable to the user.
 *    - Interpolates smoothly to alpha = 1.0f and scale = 1.0f over defined durations with an Overshoot spring.
 *
 * 2. Continuous Scroll Physics:
 *    - Upward Parallax: container translates at 40% scroll speed.
 *    - Upward Fade: linear fade from 1.0 to 0.0 over 400px of scroll.
 *    - Elastic Pull-Down Scaling: when scrolled past 0 (rubber-band), scales smoothly up to 1.06f.
 */
class HeroSectionMotionDriver(
    private val heroContainer: View,
    private val maxFadeDistancePx: Float = 400f
) {
    companion object {
        // --- DURATION AND TIMING CONSTANTS FOR INITIAL ANIMATION ---
        // Defined here for easy adjustment and fine-tuning:
        const val ENTRANCE_START_DELAY_MS: Long = 350L     // Post-launch delay ensuring activity window transition finishes
        const val ENTRANCE_ALPHA_DURATION_MS: Long = 1100L  // Fade-in duration (0f -> 1f)
        const val ENTRANCE_SCALE_DURATION_MS: Long = 1200L  // Scale-up duration (0.78f -> 1.0f)
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 1100L // Translation duration (60dp -> 0dp)

        const val INITIAL_SCALE: Float = 0.78f
        const val INITIAL_TRANSLATION_DP: Float = 60f
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
     * Executes the initial fade-and-scale-up entrance animation.
     */
    fun startEntranceAnimation(onComplete: (() -> Unit)? = null) {
        if (isEntranceAnimated) return

        // Ensure the container has completed measurement so pivot is exactly at center
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
        heroContainer.pivotY = heroContainer.height * 0.35f

        val alphaAnim = ObjectAnimator.ofFloat(heroContainer, View.ALPHA, 0f, 1f).apply {
            duration = ENTRANCE_ALPHA_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
        }

        val scaleXAnim = ObjectAnimator.ofFloat(heroContainer, View.SCALE_X, INITIAL_SCALE, 1.0f).apply {
            duration = ENTRANCE_SCALE_DURATION_MS
            interpolator = OvershootInterpolator(1.1f)
        }

        val scaleYAnim = ObjectAnimator.ofFloat(heroContainer, View.SCALE_Y, INITIAL_SCALE, 1.0f).apply {
            duration = ENTRANCE_SCALE_DURATION_MS
            interpolator = OvershootInterpolator(1.1f)
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
     * Drives scroll-based parallax, fade, and elastic pull-down scaling.
     */
    fun onScrollChanged(scrollY: Int) {
        currentScrollY = scrollY

        // Only apply scroll physics once the initial entrance animation has completed or started
        if (!isEntranceAnimated) return

        // 1. Upward Parallax: container translates at 40% scroll speed
        heroContainer.translationY = -scrollY * 0.40f

        // 2. Upward Fade: linear fade from 1.0 to 0.0 over 400px of scroll
        val alphaProgress = (1f - (scrollY / maxFadeDistancePx)).coerceIn(0f, 1f)
        heroContainer.alpha = alphaProgress

        // 3. Elastic Pull-Down Scaling: when scrolled past 0 (rubber-band)
        if (scrollY < 0) {
            val pullDistance = kotlin.math.abs(scrollY)
            val scale = (1.0f + (pullDistance * 0.0006f)).coerceAtMost(1.06f)
            heroContainer.scaleX = scale
            heroContainer.scaleY = scale
            heroContainer.pivotX = heroContainer.width / 2f
            heroContainer.pivotY = 0f
        } else {
            heroContainer.scaleX = 1.0f
            heroContainer.scaleY = 1.0f
        }
    }
}
