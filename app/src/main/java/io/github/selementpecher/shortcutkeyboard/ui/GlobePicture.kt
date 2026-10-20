package io.github.selementpecher.shortcutkeyboard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.selementpecher.shortcutkeyboard.R

/**
 * The picture of the phone's keyboard, with a ring that fades in and out over the globe key to draw the eye to it.
 * The ring stays still when the phone has animations turned off.
 */
internal class GlobePicture(context: Context) : FrameLayout(context) {

    private val image = ImageView(context).apply {
        setImageResource(R.drawable.onboard_keyboard)
        scaleType = ImageView.ScaleType.FIT_XY
    }
    private val ring = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((4 * resources.displayMetrics.density).toInt(), RING_COLOUR)
        }
        isClickable = false
    }
    private var pulse: ValueAnimator? = null

    init {
        addView(image, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(ring)
        ring.alpha = 0f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = width * IMAGE_HEIGHT / IMAGE_WIDTH
        setMeasuredDimension(width, height)
        image.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        val ringSize = (width * RING_FRACTION).toInt()
        ring.measure(MeasureSpec.makeMeasureSpec(ringSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ringSize, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        image.layout(0, 0, measuredWidth, measuredHeight)
        val size = ring.measuredWidth
        val cx = (measuredWidth * GLOBE_X).toInt()
        val cy = (measuredHeight * GLOBE_Y).toInt()
        ring.layout(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!Motion.enabled(context)) {
            ring.alpha = 1f
            return
        }
        pulse = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = PULSE_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val v = it.animatedValue as Float
                ring.alpha = v
                ring.scaleX = 0.9f + 0.15f * v
                ring.scaleY = 0.9f + 0.15f * v
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        pulse = null
        super.onDetachedFromWindow()
    }

    private companion object {
        /** The screenshot's size in pixels. */
        const val IMAGE_WIDTH = 1080
        const val IMAGE_HEIGHT = 891

        /** Where the globe key is, as a fraction of the picture's width and height. */
        const val GLOBE_X = 920f / IMAGE_WIDTH
        const val GLOBE_Y = 830f / IMAGE_HEIGHT

        /** The ring's width as a fraction of the picture's width. */
        const val RING_FRACTION = 0.1f

        const val PULSE_MS = 1600L
        const val RING_COLOUR = 0xFF34D3B4.toInt()
    }
}
