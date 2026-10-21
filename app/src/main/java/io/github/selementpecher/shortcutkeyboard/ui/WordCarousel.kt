package io.github.selementpecher.shortcutkeyboard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout

/**
 * The changing word of an example message on an arc: the word in use sits in the sentence, the next one waits just
 * above it and the last one just below, a little to the right, like the edge of a wheel. Every second the wheel
 * turns one step, with a small bounce: the word in use rolls down and away and the one above rolls into the sentence.
 * With the phone's animations off, only the first word is shown.
 */
internal class WordCarousel(context: Context, private val count: Int, build: (Int) -> View) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val turnNext = Runnable { turn() }
    private val words: List<View> = List(count) { build(it) }
    private var index = 0
    private var running = false
    private var turning: ValueAnimator? = null
    private val animated = Motion.enabled(context)

    init {
        clipChildren = false
        words.forEach { addView(it, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        if (!animated) words.drop(1).forEach { it.visibility = View.GONE }
        placeAll(0f)
    }

    /** As wide as the word in use, so the words after it follow it; the others stand outside. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val active = words[index]
        setMeasuredDimension(active.measuredWidth, measuredHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        placeAll(0f)
    }

    /**
     * Turns the wheel [progress] (0 to 1) of the way to the next word. The words ride on the left edge of a big wheel
     * whose centre is to the right of the sentence, so the arc bows out to the left through the word in use.
     */
    private fun placeAll(progress: Float) {
        if (!animated) return
        val radius = RADIUS_DP * resources.displayMetrics.density
        val activeHalf = words[index].width / 2f
        words.forEachIndexed { i, view ->
            val place = Math.floorMod(i - index, count)
            // Steps from the sentence: the next word is one step up (-1), the last one a step down (+1), any others
            // further up and out of sight. Every word moves one step down per turn.
            val steps = when (place) {
                0 -> 0
                count - 1 -> 1
                else -> -place
            } + progress
            val rad = Math.toRadians(STEP_DEGREES * steps.toDouble())
            view.pivotX = view.width / 2f
            view.pivotY = view.height / 2f
            // Each word is centred on its point of the arc; the word in use stays exactly where the sentence has it.
            view.translationX = activeHalf + (radius * (1 - Math.cos(rad))).toFloat() - view.width / 2f
            view.translationY = (radius * Math.sin(rad)).toFloat()
            view.rotation = (STEP_DEGREES * steps * TILT).toFloat()
            // Solid in the sentence, lighter one step away, gone two steps away.
            val away = Math.abs(steps)
            view.alpha = (if (away <= 1f) 1f - (1f - WAITING_ALPHA) * away else WAITING_ALPHA * (2f - away)).coerceIn(0f, 1f)
        }
    }

    private fun turn() {
        if (!running) return
        turning = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TURN_MS
            interpolator = OvershootInterpolator(OVERSHOOT)
            addUpdateListener { placeAll(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (cancelled) return
                    index = (index + 1) % count
                    placeAll(0f)
                    requestLayout()
                    handler.postDelayed(turnNext, HOLD_MS)
                }
            })
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        if (animated) handler.postDelayed(turnNext, HOLD_MS)
    }

    override fun onDetachedFromWindow() {
        running = false
        handler.removeCallbacks(turnNext)
        turning?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val TURN_MS = 650L
        const val HOLD_MS = 1000L
        const val OVERSHOOT = 1.6f
        const val RADIUS_DP = 80f
        const val STEP_DEGREES = 36.0
        const val TILT = 0.3
        const val WAITING_ALPHA = 0.5f
    }
}
