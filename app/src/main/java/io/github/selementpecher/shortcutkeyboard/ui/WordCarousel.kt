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
 * The changing word of an example message as the cylinder of a revolver: all [count] words sit round a circle, the
 * one in use at the left (in the sentence) and the others at the top, right and bottom. Every second the cylinder
 * turns one place, with a small bounce: the word in use rolls away downwards and the one at the top drops in.
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
     * Puts every word on the circle, [progress] (0 to 1) of the way through a turn. The circle's centre is to the right
     * of the word in use, so that word sits at its left; the next word waits at the top.
     */
    private fun placeAll(progress: Float) {
        if (!animated) return
        val density = resources.displayMetrics.density
        // A little taller than wide, so the cylinder fits beside the sentence on a phone.
        val across = ACROSS_DP * density
        val down = DOWN_DP * density
        words.forEachIndexed { i, view ->
            val place = Math.floorMod(i - index, count)
            // 180° is the left (in the sentence); each later word is a quarter turn further round: top, right, bottom.
            val degrees = 180.0 + 90.0 * (place - progress)
            val rad = Math.toRadians(degrees)
            // Each word is centred on its point of the circle; the word in use stays exactly where the sentence has it.
            val activeHalf = words[index].width / 2f
            view.pivotX = view.width / 2f
            view.pivotY = view.height / 2f
            view.translationX = (activeHalf + across + across * Math.cos(rad)).toFloat() - view.width / 2f
            view.translationY = (down * Math.sin(rad)).toFloat()
            // The word in use is full size and solid; the waiting ones are smaller and lighter.
            val inUse = 1f - Math.min(1.0, (1 + Math.cos(rad)) * 2).toFloat() // 1 at the left .. 0 a quarter turn away
            view.alpha = WAITING_ALPHA + (1f - WAITING_ALPHA) * inUse
            val scale = WAITING_SCALE + (1f - WAITING_SCALE) * inUse
            view.scaleX = scale
            view.scaleY = scale
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
        const val ACROSS_DP = 40f
        const val DOWN_DP = 52f
        const val WAITING_SCALE = 0.7f
        const val WAITING_ALPHA = 0.5f
    }
}
