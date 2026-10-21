package io.github.selementpecher.shortcutkeyboard.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * Shows [count] example messages one after another, as if on a ring: each swings round from the right, bounces a
 * little as it reaches the front, waits, then swings away to the left. With the phone's animations off, it just shows the first one.
 */
internal class MessageCarousel(context: Context, private val count: Int, private val build: (Int) -> View) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val goOut = Runnable { leave() }
    private var index = 0
    private var running = false
    private var current: android.animation.ValueAnimator? = null

    init {
        clipChildren = false
        show(animate = false)
    }

    /** Puts [view] on the ring at [degrees] from the front: round the side, turning a little, fading out at the back. */
    private fun place(view: View, degrees: Float) {
        val radius = resources.displayMetrics.widthPixels * RING
        val rad = Math.toRadians(degrees.toDouble())
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.translationX = (radius * Math.sin(rad)).toFloat()
        view.translationY = (radius * (1 - Math.cos(rad))).toFloat() * SAG
        view.rotation = degrees * TURN
        view.alpha = Math.cos(rad).toFloat().coerceIn(0f, 1f)
    }

    private fun swing(view: View, from: Float, to: Float, ms: Long, interpolator: android.animation.TimeInterpolator, then: () -> Unit) {
        place(view, from)
        android.animation.ValueAnimator.ofFloat(from, to).apply {
            duration = ms
            this.interpolator = interpolator
            addUpdateListener { place(view, it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    place(view, to)
                    then()
                }
            })
            current = this
            start()
        }
    }

    private fun show(animate: Boolean) {
        removeAllViews()
        val view = build(index)
        addView(view, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (!animate || !Motion.enabled(context)) return
        // Comes round the ring from the right, overshoots the front a little (the bounce), and settles.
        swing(view, START_DEGREES, 0f, ENTER_MS, OvershootInterpolator(OVERSHOOT)) {
            view.rotation = 0f
            if (running) handler.postDelayed(goOut, HOLD_MS)
        }
    }

    private fun leave() {
        val view = getChildAt(0) ?: return
        swing(view, 0f, -START_DEGREES, EXIT_MS, AccelerateInterpolator()) {
            if (running) {
                index = (index + 1) % count
                show(animate = true)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        if (Motion.enabled(context)) handler.postDelayed(goOut, HOLD_MS)
    }

    override fun onDetachedFromWindow() {
        running = false
        handler.removeCallbacks(goOut)
        current?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val ENTER_MS = 650L
        const val EXIT_MS = 280L
        const val HOLD_MS = 2200L
        const val OVERSHOOT = 1.6f
        const val START_DEGREES = 80f
        const val RING = 0.6f
        const val SAG = 0.5f
        const val TURN = 0.3f
    }
}
