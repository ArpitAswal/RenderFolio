package com.example.ui.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Manages the dual-phase motion lifecycle for Section 6 (Education & Academics):
 * - Resting state (off-screen): scale = 0.85f, alpha = 0f, translationY = +40dp.
 * - Entrance (on crossing viewport reading zone threshold):
 *   * Fade-in (0f -> 1f over 600ms with DecelerateInterpolator)
 *   * Scale-up (0.85f -> 1.0f over 600ms with OvershootInterpolator)
 *   * Vertical Lift (40dp -> 0dp over 600ms with DecelerateInterpolator)
 * - Viewport Reading Zone: stays locked cleanly at scale = 1.0f, alpha = 1.0f, translationY = 0dp.
 * - Exit past top when scrolling downside (last 20% remaining): gracefully recedes with smooth fade-out over 400ms.
 */
class EducationMotionDriver(
    private val sectionContainer: View,
    private val scrollView: View? = null
) {
    // Secondary constructor to maintain compatibility with 3-argument callers
    constructor(
        titleView: View,
        contentView: View,
        sectionContainer: View
    ) : this(sectionContainer, null)

    companion object {
        const val ENTRANCE_START_DELAY_MS: Long = 350L
        const val ENTRANCE_ALPHA_DURATION_MS: Long = 600L
        const val ENTRANCE_SCALE_DURATION_MS: Long = 600L
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 600L
        const val EXIT_DURATION_MS: Long = 400L

        const val INITIAL_SCALE: Float = 0.85f
        const val INITIAL_TRANSLATION_DP: Float = 40f
        const val VIEWPORT_THRESHOLD_RATIO: Float = 0.20f
    }

    enum class MotionState {
        OFF_BOTTOM,
        ENTERED_VISIBLE,
        OFF_TOP
    }

    private var currentState = MotionState.OFF_BOTTOM
    private var hasPlayedInitialEntrance = false
    private var currentAnimator: AnimatorSet? = null

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
            animateEntrance(withDelay = true, onComplete = onComplete)
        } else {
            currentState = MotionState.OFF_BOTTOM
            val density = sectionContainer.resources.displayMetrics.density
            sectionContainer.alpha = 0f
            sectionContainer.scaleX = INITIAL_SCALE
            sectionContainer.scaleY = INITIAL_SCALE
            sectionContainer.translationY = INITIAL_TRANSLATION_DP * density
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
                animateExitToTop()
            }
            return
        }

        // 2. Exited below bottom of viewport (top has fallen below viewport bottom)
        if (relativeTop >= viewportHeight) {
            if (currentState != MotionState.OFF_BOTTOM) {
                currentState = MotionState.OFF_BOTTOM
                currentAnimator?.cancel()
                sectionContainer.alpha = 0f
                sectionContainer.scaleX = INITIAL_SCALE
                sectionContainer.scaleY = INITIAL_SCALE
                sectionContainer.translationY = INITIAL_TRANSLATION_DP * density
            }
            return
        }

        // 3. In Reading Zone / Entering viewport:
        val entranceTriggerBottom = viewportHeight - (60f * density)
        if (relativeTop <= entranceTriggerBottom) {
            if (currentState != MotionState.ENTERED_VISIBLE) {
                currentState = MotionState.ENTERED_VISIBLE
                animateEntrance(withDelay = false)
            }
        }
    }

    private fun animateEntrance(withDelay: Boolean = false, onComplete: (() -> Unit)? = null) {
        currentAnimator?.cancel()

        val density = sectionContainer.resources.displayMetrics.density
        if (sectionContainer.alpha == 0f) {
            sectionContainer.scaleX = INITIAL_SCALE
            sectionContainer.scaleY = INITIAL_SCALE
            sectionContainer.translationY = INITIAL_TRANSLATION_DP * density
        }

        sectionContainer.pivotX = sectionContainer.width / 2f
        sectionContainer.pivotY = 0f

        val alphaAnim = ObjectAnimator.ofFloat(sectionContainer, View.ALPHA, sectionContainer.alpha, 1f).apply {
            duration = if (withDelay) ENTRANCE_ALPHA_DURATION_MS else 550L
            interpolator = DecelerateInterpolator(1.8f)
        }

        val scaleXAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_X, sectionContainer.scaleX, 1.0f).apply {
            duration = if (withDelay) ENTRANCE_SCALE_DURATION_MS else 550L
            interpolator = OvershootInterpolator(1.05f)
        }

        val scaleYAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_Y, sectionContainer.scaleY, 1.0f).apply {
            duration = if (withDelay) ENTRANCE_SCALE_DURATION_MS else 550L
            interpolator = OvershootInterpolator(1.05f)
        }

        val transYAnim = ObjectAnimator.ofFloat(sectionContainer, View.TRANSLATION_Y, sectionContainer.translationY, 0f).apply {
            duration = if (withDelay) ENTRANCE_TRANSLATION_DURATION_MS else 550L
            interpolator = DecelerateInterpolator(1.8f)
        }

        currentAnimator = AnimatorSet().apply {
            if (withDelay) startDelay = ENTRANCE_START_DELAY_MS
            playTogether(alphaAnim, scaleXAnim, scaleYAnim, transYAnim)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    sectionContainer.alpha = 1f
                    sectionContainer.scaleX = 1f
                    sectionContainer.scaleY = 1f
                    sectionContainer.translationY = 0f
                    onComplete?.invoke()
                }
            })
            start()
        }
    }

    private fun animateExitToTop() {
        currentAnimator?.cancel()

        val density = sectionContainer.resources.displayMetrics.density
        val targetTransY = -30f * density

        val alphaAnim = ObjectAnimator.ofFloat(sectionContainer, View.ALPHA, sectionContainer.alpha, 0f).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val scaleXAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_X, sectionContainer.scaleX, 0.85f).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val scaleYAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_Y, sectionContainer.scaleY, 0.85f).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val transYAnim = ObjectAnimator.ofFloat(sectionContainer, View.TRANSLATION_Y, sectionContainer.translationY, targetTransY).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        currentAnimator = AnimatorSet().apply {
            playTogether(alphaAnim, scaleXAnim, scaleYAnim, transYAnim)
            start()
        }
    }
}
