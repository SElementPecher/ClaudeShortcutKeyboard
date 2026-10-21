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
 * The changing word of an example message, turning like a drum: each of [count] words rolls up into place from
 * below, bounces a little as it lands, waits, then rolls away over the top and the next one comes round.
 * With the phone's animations off, it just shows the first one.
 */
internal class WordCarousel(context: Context, private val count: Int, private val build: (Int) -> View) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val goOut = Runnable { leave() }
    private var index = 0
    private var running = false
    private var current: ValueAnimator? = null

    init {
        clipChildren = false
        show(animate = false)
    }

    /** Puts [view] on the drum at [degrees] from the front: below for more than 0, above for less, fading at the back. */
    private fun place(view: View, degrees: Float) {
        val rad = Math.toRadians(degrees.toDouble())
        val radius = view.height * RING
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
        view.cameraDistance = CAMERA * resources.displayMetrics.density
        view.translationY = (radius * Math.sin(rad)).toFloat()
        view.rotationX = -degrees
        view.alpha = Math.cos(rad).toFloat().coerceIn(0f, 1f)
    }

    private fun turn(view: View, from: Float, to: Float, ms: Long, interpolator: TimeInterpolator, then: () -> Unit) {
        place(view, from)
        current = ValueAnimator.ofFloat(from, to).apply {
            duration = ms
            this.interpolator = interpolator
            addUpdateListener { place(view, it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    place(view, to)
                    then()
                }
            })
            start()
        }
    }

    private fun show(animate: Boolean) {
        removeAllViews()
        val view = build(index)
        addView(view, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (!animate || !Motion.enabled(context)) return
        view.alpha = 0f
        // Wait for its size, then roll it up from below with a small bounce at the front.
        view.post {
            if (!running) return@post
            turn(view, START_DEGREES, 0f, ENTER_MS, OvershootInterpolator(OVERSHOOT)) {
                if (running) handler.postDelayed(goOut, HOLD_MS)
            }
        }
    }

    private fun leave() {
        val view = getChildAt(0) ?: return
        turn(view, 0f, -START_DEGREES, EXIT_MS, AccelerateInterpolator()) {
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
        const val ENTER_MS = 600L
        const val EXIT_MS = 260L
        const val HOLD_MS = 1800L
        const val OVERSHOOT = 2f
        const val START_DEGREES = 80f
        const val RING = 1.1f
        const val CAMERA = 6000f
    }
}
