package com.example.ui.motion

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * HeroSectionMotionDriver
 *
 * Drives whole-section motion on Section 1 (hero_container):
 * 1. Initial Load Entrance Animation:
 *    - Pinned at top edge (pivotX = 50%, pivotY = 0f).
 *    - Starts from alpha = 0f, scale = 0.80f, translationY = 50dp.
 *    - Interpolates to alpha = 1.0f, scale = 1.0f, translationY = 0dp over 1100ms/1200ms with Overshoot spring.
 *
 * 2. Continuous Scroll Physics:
 *    - Upward Parallax Glide: container translates at 18% scroll speed (-scrollY * 0.18f) giving continuous depth.
 *    - Perspective Scale Compression: subtle continuous perspective scaling as it scrolls upward.
 *    - Smooth Top Exit Fade: stays at full 1.0f alpha while on screen, then smoothly fades out as it leaves the top.
 *    - Elastic Pull-Down Scaling: when scrolled past 0 (rubber-band), scales up to 1.08f.
 */
class HeroSectionMotionDriver(
    private val heroContainer: View,
    private val scrollView: View? = null
) {
    companion object {
        const val ENTRANCE_START_DELAY_MS: Long = 350L
        const val ENTRANCE_ALPHA_DURATION_MS: Long = 1100L
        const val ENTRANCE_SCALE_DURATION_MS: Long = 1200L
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 1100L

        const val INITIAL_SCALE: Float = 0.80f
        const val INITIAL_TRANSLATION_DP: Float = 50f
    }

    private var isEntranceAnimated = false
    private var currentScrollY: Int = 0
    private var entranceAnimator: AnimatorSet? = null

    init {
        val density = heroContainer.resources.displayMetrics.density
        heroContainer.alpha = 0f
        heroContainer.scaleX = INITIAL_SCALE
        heroContainer.scaleY = INITIAL_SCALE
        heroContainer.translationY = INITIAL_TRANSLATION_DP * density
    }

    fun playInitialEntrance(onComplete: (() -> Unit)? = null) = startEntranceAnimation(onComplete)

    fun startEntranceAnimation(onComplete: (() -> Unit)? = null) {
        if (isEntranceAnimated) return

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
        heroContainer.pivotY = 0f

        val alphaAnim = ObjectAnimator.ofFloat(heroContainer, View.ALPHA, 0f, 1f).apply {
            duration = ENTRANCE_ALPHA_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
        }

        val scaleXAnim = ObjectAnimator.ofFloat(heroContainer, View.SCALE_X, INITIAL_SCALE, 1.0f).apply {
            duration = ENTRANCE_SCALE_DURATION_MS
            interpolator = OvershootInterpolator(1.05f)
        }

        val scaleYAnim = ObjectAnimator.ofFloat(heroContainer, View.SCALE_Y, INITIAL_SCALE, 1.0f).apply {
            duration = ENTRANCE_SCALE_DURATION_MS
            interpolator = OvershootInterpolator(1.05f)
        }

        val transYAnim = ObjectAnimator.ofFloat(heroContainer, View.TRANSLATION_Y, startTransY, 0f).apply {
            duration = ENTRANCE_TRANSLATION_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
        }

        val animatorSet = AnimatorSet().apply {
            startDelay = ENTRANCE_START_DELAY_MS
            playTogether(alphaAnim, scaleXAnim, scaleYAnim, transYAnim)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
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
        if (!isEntranceAnimated) {
            entranceAnimator?.cancel()
            isEntranceAnimated = true
        }

        val density = heroContainer.resources.displayMetrics.density

        if (scrollY < 0) {
            // Elastic pull-down over-scroll (rubber-band past the top)
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
            // 1. Maintain locked layout alignment so gap to subsequent sections remains constant (32dp)
            heroContainer.translationY = 0f

            // 2. Perspective Scale Compression as it scrolls up
            val heroH = if (heroContainer.height > 0) heroContainer.height.toFloat() else 380f * density
            val progress = (scrollY / heroH).coerceIn(0f, 1f)
            val scale = 1.0f - (progress * 0.05f)
            heroContainer.scaleX = scale
            heroContainer.scaleY = scale
            heroContainer.pivotX = heroContainer.width / 2f
            heroContainer.pivotY = 0f

            // 3. Graceful Top Exit Fade: stays at full 1.0f opacity while visible,
            // only smoothly fading out as its bottom approaches the top edge of the screen
            val fadeStartPx = heroH * 0.40f
            val fadeEndPx = heroH * 1.00f
            if (scrollY <= fadeStartPx) {
                heroContainer.alpha = 1.0f
            } else {
                val fadeProgress = ((scrollY - fadeStartPx) / (fadeEndPx - fadeStartPx)).coerceIn(0f, 1f)
                heroContainer.alpha = 1.0f - fadeProgress
            }
        }
    }
}
