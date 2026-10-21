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
 * Shows [count] example messages one after another: each slides in from the right, bounces a little as it lands,
 * waits, then slides out to the left. With the phone's animations off, it just shows the first one.
 */
internal class MessageCarousel(context: Context, private val count: Int, private val build: (Int) -> View) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private val goOut = Runnable { leave() }
    private var index = 0
    private var running = false

    init {
        clipChildren = false
        show(animate = false)
    }

    private fun show(animate: Boolean) {
        removeAllViews()
        val view = build(index)
        addView(view, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (!animate || !Motion.enabled(context)) return
        val from = resources.displayMetrics.widthPixels.toFloat()
        view.translationX = from
        view.alpha = 0f
        view.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(ENTER_MS)
            .setInterpolator(OvershootInterpolator(OVERSHOOT))
            .withEndAction { if (running) handler.postDelayed(goOut, HOLD_MS) }
            .start()
    }

    private fun leave() {
        val view = getChildAt(0) ?: return
        view.animate()
            .translationX(-resources.displayMetrics.widthPixels.toFloat())
            .alpha(0f)
            .setDuration(EXIT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (!running) return@withEndAction
                index = (index + 1) % count
                show(animate = true)
            }
            .start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        if (Motion.enabled(context)) handler.postDelayed(goOut, HOLD_MS)
    }

    override fun onDetachedFromWindow() {
        running = false
        handler.removeCallbacks(goOut)
        getChildAt(0)?.animate()?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val ENTER_MS = 650L
        const val EXIT_MS = 280L
        const val HOLD_MS = 2200L
        const val OVERSHOOT = 2.4f
    }
}
