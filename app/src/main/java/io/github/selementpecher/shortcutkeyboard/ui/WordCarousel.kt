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
 * The changing word of an example message as the cylinder of a revolver: all [count] words are spokes round one
 * axis, the one in use at the left (in the sentence) and the others at the top, right and bottom. Every second the
 * cylinder turns a quarter, with a small bounce: the word in use swings down and the one at the top comes into place.
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
     * Turns the cylinder [progress] (0 to 1) of the way to the next word. The words are spokes of one wheel: each
     * points out from the axis and turns with it, so the one at the top stands on end and the one at the right is
     * upside down. The axis is just to the right of the word in use, which sits in the sentence reading normally.
     */
    private fun placeAll(progress: Float) {
        if (!animated) return
        val hub = HUB_DP * resources.displayMetrics.density
        val axisX = words[index].width + hub
        words.forEachIndexed { i, view ->
            val place = Math.floorMod(i - index, count)
            // 180° is the left (in the sentence); each later word is a quarter turn further round: top, right, bottom.
            val degrees = 180.0 + 90.0 * (place - progress)
            val rad = Math.toRadians(degrees)
            // The word in use is full size and solid; the waiting ones are smaller and lighter.
            val inUse = 1f - Math.min(1.0, (1 + Math.cos(rad)) * 2).toFloat() // 1 at the left .. 0 a quarter turn away
            val scale = WAITING_SCALE + (1f - WAITING_SCALE) * inUse
            // Its inner end stays just clear of the axis.
            val reach = hub + view.width * scale / 2f
            view.pivotX = view.width / 2f
            view.pivotY = view.height / 2f
            view.scaleX = scale
            view.scaleY = scale
            view.translationX = (axisX + reach * Math.cos(rad)).toFloat() - view.width / 2f
            view.translationY = (reach * Math.sin(rad)).toFloat()
            view.rotation = (degrees - 180.0).toFloat()
            view.alpha = WAITING_ALPHA + (1f - WAITING_ALPHA) * inUse
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
        const val HUB_DP = 6f
        const val WAITING_SCALE = 0.55f
        const val WAITING_ALPHA = 0.5f
    }
}
