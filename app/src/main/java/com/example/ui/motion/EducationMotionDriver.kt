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
 * Synchronized with Section 2 (Summary) dynamics:
 * - Resting state (off-screen): scale = 0.80f, alpha = 0f, translationY = +50dp.
 * - Entrance (on crossing viewport reading zone threshold):
 *   * Fade-in (0f -> 1f over 1100ms with DecelerateInterpolator(1.8f))
 *   * Scale-up (0.80f -> 1.0f over 1200ms with OvershootInterpolator(1.05f))
 *   * Vertical Lift (50dp -> 0dp over 1100ms with DecelerateInterpolator(1.8f))
 * - Viewport Reading Zone: between top 20% and bottom 80% of screen.
 * - Exit past top/bottom: gracefully recedes with smooth fade-out over 400ms.
 */
class EducationMotionDriver(
    private val sectionContainer: View
) {
    // Secondary constructor to maintain compatibility with 3-argument callers
    constructor(
        titleView: View,
        contentView: View,
        sectionContainer: View
    ) : this(sectionContainer)

    companion object {
        // --- TIMING CONSTANTS (SYNCHRONIZED WITH SECTION 2) ---
        const val ENTRANCE_START_DELAY_MS: Long = 350L
        const val ENTRANCE_ALPHA_DURATION_MS: Long = 1100L
        const val ENTRANCE_SCALE_DURATION_MS: Long = 1200L
        const val ENTRANCE_TRANSLATION_DURATION_MS: Long = 1100L
        const val EXIT_DURATION_MS: Long = 400L

        const val INITIAL_SCALE: Float = 0.80f // Synchronized with Section 2 (was 0.92f)
        const val INITIAL_TRANSLATION_DP: Float = 50f // Synchronized with Section 2 (was 28f)
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
        // Prepare initial resting state: scale 0.80, translationY +50dp, alpha 0
        val density = sectionContainer.resources.displayMetrics.density
        sectionContainer.alpha = 0f
        sectionContainer.scaleX = INITIAL_SCALE
        sectionContainer.scaleY = INITIAL_SCALE
        sectionContainer.translationY = INITIAL_TRANSLATION_DP * density
    }

    /**
     * Called on pre-draw or layout completion.
     * If the section is already within the visible viewport on initial load (e.g. large tablet display),
     * executes the launch entrance with 350ms delay.
     * Otherwise, keeps it in resting state until scrolled into view.
     */
    fun playInitialEntrance(onComplete: (() -> Unit)? = null) {
        if (hasPlayedInitialEntrance) return

        if (sectionContainer.width == 0 || sectionContainer.height == 0) {
            sectionContainer.post {
                playInitialEntrance(onComplete)
            }
            return
        }

        hasPlayedInitialEntrance = true

        val location = IntArray(2)
        sectionContainer.getLocationOnScreen(location)
        val viewTopOnScreen = location[1]
        val screenHeight = sectionContainer.resources.displayMetrics.heightPixels
        val bottomThreshold = (screenHeight * (1f - VIEWPORT_THRESHOLD_RATIO)).toInt()

        // Check if section is already visible in viewport on initial launch
        if (viewTopOnScreen in 1 until bottomThreshold) {
            currentState = MotionState.ENTERED_VISIBLE
            animateEntrance(withDelay = true, onComplete = onComplete)
        } else {
            // Off-screen below fold: remains in resting state until scrolled into reading zone
            currentState = MotionState.OFF_BOTTOM
            onComplete?.invoke()
        }
    }

    /**
     * Primary scroll change listener hook:
     * Tracks location relative to viewport reading zone (20% top & bottom thresholds).
     */
    fun onScrollChanged(viewportHeight: Int) {
        val actualViewportHeight = if (viewportHeight > 0) {
            viewportHeight
        } else {
            sectionContainer.resources.displayMetrics.heightPixels
        }

        if (sectionContainer.height <= 0) return

        val location = IntArray(2)
        sectionContainer.getLocationOnScreen(location)
        val viewTopOnScreen = location[1]
        val viewBottomOnScreen = viewTopOnScreen + sectionContainer.height

        val topThreshold = (actualViewportHeight * VIEWPORT_THRESHOLD_RATIO).toInt()
        val bottomThreshold = (actualViewportHeight * (1f - VIEWPORT_THRESHOLD_RATIO)).toInt()

        // 1. Reading Zone: between top 20% and bottom 80% of screen
        val isReadingZone = viewTopOnScreen < bottomThreshold && viewBottomOnScreen > topThreshold
        if (isReadingZone) {
            if (currentState != MotionState.ENTERED_VISIBLE) {
                currentState = MotionState.ENTERED_VISIBLE
                animateEntrance(withDelay = false)
            }
            return
        }

        // 2. Scrolled below bottom threshold (receding back off-screen at bottom)
        if (viewTopOnScreen >= bottomThreshold && currentState != MotionState.OFF_BOTTOM) {
            currentState = MotionState.OFF_BOTTOM
            animateExitToBottom()
            return
        }

        // 3. Scrolled above top threshold (receding past the top of the screen)
        if (viewBottomOnScreen <= topThreshold && currentState != MotionState.OFF_TOP) {
            currentState = MotionState.OFF_TOP
            animateExitToTop()
            return
        }
    }

    private fun animateEntrance(withDelay: Boolean = false, onComplete: (() -> Unit)? = null) {
        currentAnimator?.cancel()

        sectionContainer.pivotX = sectionContainer.width / 2f
        sectionContainer.pivotY = 0f

        val alphaAnim = ObjectAnimator.ofFloat(sectionContainer, View.ALPHA, sectionContainer.alpha, 1.0f).apply {
            duration = ENTRANCE_ALPHA_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
        }

        val scaleXAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_X, sectionContainer.scaleX, 1.0f).apply {
            duration = ENTRANCE_SCALE_DURATION_MS
            interpolator = OvershootInterpolator(1.05f)
        }

        val scaleYAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_Y, sectionContainer.scaleY, 1.0f).apply {
            duration = ENTRANCE_SCALE_DURATION_MS
            interpolator = OvershootInterpolator(1.05f)
        }

        val transYAnim = ObjectAnimator.ofFloat(sectionContainer, View.TRANSLATION_Y, sectionContainer.translationY, 0f).apply {
            duration = ENTRANCE_TRANSLATION_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
        }

        val animatorSet = AnimatorSet().apply {
            if (withDelay) {
                startDelay = ENTRANCE_START_DELAY_MS
            }
            playTogether(alphaAnim, scaleXAnim, scaleYAnim, transYAnim)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (currentState == MotionState.ENTERED_VISIBLE) {
                        sectionContainer.alpha = 1.0f
                        sectionContainer.scaleX = 1.0f
                        sectionContainer.scaleY = 1.0f
                        sectionContainer.translationY = 0f
                    }
                    onComplete?.invoke()
                }
            })
        }
        currentAnimator = animatorSet
        animatorSet.start()
    }

    private fun animateExitToBottom() {
        currentAnimator?.cancel()

        val density = sectionContainer.resources.displayMetrics.density
        val targetTransY = INITIAL_TRANSLATION_DP * density

        sectionContainer.pivotX = sectionContainer.width / 2f
        sectionContainer.pivotY = 0f

        val alphaAnim = ObjectAnimator.ofFloat(sectionContainer, View.ALPHA, sectionContainer.alpha, 0f).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val scaleXAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_X, sectionContainer.scaleX, INITIAL_SCALE).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val scaleYAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_Y, sectionContainer.scaleY, INITIAL_SCALE).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val transYAnim = ObjectAnimator.ofFloat(sectionContainer, View.TRANSLATION_Y, sectionContainer.translationY, targetTransY).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val animatorSet = AnimatorSet().apply {
            playTogether(alphaAnim, scaleXAnim, scaleYAnim, transYAnim)
        }
        currentAnimator = animatorSet
        animatorSet.start()
    }

    private fun animateExitToTop() {
        currentAnimator?.cancel()

        val density = sectionContainer.resources.displayMetrics.density
        val targetTransY = -INITIAL_TRANSLATION_DP * density

        sectionContainer.pivotX = sectionContainer.width / 2f
        sectionContainer.pivotY = sectionContainer.height * 0.5f

        val alphaAnim = ObjectAnimator.ofFloat(sectionContainer, View.ALPHA, sectionContainer.alpha, 0f).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val scaleXAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_X, sectionContainer.scaleX, INITIAL_SCALE).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val scaleYAnim = ObjectAnimator.ofFloat(sectionContainer, View.SCALE_Y, sectionContainer.scaleY, INITIAL_SCALE).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val transYAnim = ObjectAnimator.ofFloat(sectionContainer, View.TRANSLATION_Y, sectionContainer.translationY, targetTransY).apply {
            duration = EXIT_DURATION_MS
            interpolator = AccelerateInterpolator(1.5f)
        }

        val animatorSet = AnimatorSet().apply {
            playTogether(alphaAnim, scaleXAnim, scaleYAnim, transYAnim)
        }
        currentAnimator = animatorSet
        animatorSet.start()
    }
}
