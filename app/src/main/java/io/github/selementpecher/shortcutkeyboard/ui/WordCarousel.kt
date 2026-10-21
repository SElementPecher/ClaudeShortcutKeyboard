package io.github.selementpecher.shortcutkeyboard.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * The changing word of an example message, travelling round a wheel: each of [count] words comes up from below
 * along a round path, bounces a little as it lands, waits a second, then carries on round and away over the top
 * while the next one comes up. The words stay in sight the whole way round.
 * With the phone's animations off, it just shows the first one.
 */
internal class WordCarousel(context: Context, private val count: Int, private val build: (Int) -> View) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val goOut = Runnable { leave() }
    private var index = 0
    private var running = false
    private val animators = ArrayList<ValueAnimator>()

    init {
        clipChildren = false
        show(animate = false)
    }

    /**
     * Puts [view] on the wheel at [degrees] from its place: below for more than 0, above for less. The path bows out
     * to the right, away from the words before it, so a travelling word never covers them.
     */
    private fun place(view: View, degrees: Float) {
        val rad = Math.toRadians(degrees.toDouble())
        val radius = RADIUS_DP * resources.displayMetrics.density
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.translationX = (radius * (1 - Math.cos(rad)) * BOW).toFloat()
        view.translationY = (radius * Math.sin(rad)).toFloat()
        view.rotation = degrees * TILT
        // Always readable on the way: a little lighter away from its place, never gone.
        view.alpha = (MIN_ALPHA + (1 - MIN_ALPHA) * Math.cos(rad)).toFloat().coerceIn(MIN_ALPHA, 1f)
    }

    private fun turn(view: View, from: Float, to: Float, ms: Long, interpolator: TimeInterpolator, then: () -> Unit) {
        place(view, from)
        val animator = ValueAnimator.ofFloat(from, to).apply {
            duration = ms
            this.interpolator = interpolator
            addUpdateListener { place(view, it.animatedValue as Float) }
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                animators.remove(animator)
                if (cancelled) return
                place(view, to)
                then()
            }
        })
        animators += animator
        animator.start()
    }

    /** Adds the word at [index]. When [animate], it comes up round the wheel while the one before is still leaving. */
    private fun show(animate: Boolean) {
        if (!animate) removeAllViews()
        val view = build(index)
        addView(view, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (!animate || !Motion.enabled(context)) return
        view.alpha = 0f
        // Wait for its size, then bring it round with a small bounce as it lands.
        view.post {
            if (!running) return@post
            turn(view, START_DEGREES, 0f, ENTER_MS, OvershootInterpolator(OVERSHOOT)) {
                if (running) handler.postDelayed(goOut, HOLD_MS)
            }
        }
    }

    private fun leave() {
        val old = getChildAt(childCount - 1) ?: return
        turn(old, 0f, -START_DEGREES, EXIT_MS, AccelerateInterpolator()) { removeView(old) }
        index = (index + 1) % count
        show(animate = true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        if (Motion.enabled(context)) handler.postDelayed(goOut, HOLD_MS)
    }

    override fun onDetachedFromWindow() {
        running = false
        handler.removeCallbacks(goOut)
        animators.toList().forEach { it.cancel() }
        animators.clear()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val ENTER_MS = 750L
        const val EXIT_MS = 500L
        const val HOLD_MS = 1000L
        const val OVERSHOOT = 1.6f
        const val START_DEGREES = 90f
        const val RADIUS_DP = 56f
        const val BOW = 1.1f
        const val TILT = 0.35f
        const val MIN_ALPHA = 0.55f
    }
}
