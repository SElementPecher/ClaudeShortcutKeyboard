package io.github.selementpecher.shortcutkeyboard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.selementpecher.shortcutkeyboard.R
import kotlin.math.hypot

/**
 * The picture of the phone's keyboard. A dark veil spreads out from the top left corner over the keys, towards the
 * globe key, and stops there: the globe stays clear, with a ring round it. Then the veil fades and the sweep starts
 * again. With the phone's animations turned off, the veil simply sits over the keys with the globe clear.
 */
internal class GlobePicture(context: Context) : FrameLayout(context) {

    private val image = ImageView(context).apply {
        setImageResource(R.drawable.onboard_keyboard)
        scaleType = ImageView.ScaleType.FIT_XY
    }
    private val veil = object : View(context) {
        override fun onDraw(canvas: Canvas) = drawVeil(canvas)
    }.apply {
        isClickable = false
        setWillNotDraw(false)
    }
    private val ring = View(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke((RING_DP * resources.displayMetrics.density).toInt(), RING_COLOUR)
        }
        isClickable = false
    }
    private val veilPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    /** How far the veil has spread from the corner: 0 at the corner, 1 once it has reached the globe. */
    private var spread = 1f

    /** How strong the veil is, 0 (gone) to 1 (full). */
    private var strength = 1f

    private var sweep: ValueAnimator? = null

    init {
        addView(image, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(veil, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(ring)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = width * IMAGE_HEIGHT / IMAGE_WIDTH
        setMeasuredDimension(width, height)
        val exact = { size: Int -> MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY) }
        image.measure(exact(width), exact(height))
        veil.measure(exact(width), exact(height))
        val ringSize = (width * RING_FRACTION).toInt()
        ring.measure(exact(ringSize), exact(ringSize))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        image.layout(0, 0, measuredWidth, measuredHeight)
        veil.layout(0, 0, measuredWidth, measuredHeight)
        val size = ring.measuredWidth
        val cx = (measuredWidth * GLOBE_X).toInt()
        val cy = (measuredHeight * GLOBE_Y).toInt()
        ring.layout(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!Motion.enabled(context)) {
            spread = 1f
            strength = 1f
            veil.invalidate()
            return
        }
        sweep = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SWEEP_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                // The veil spreads over the first part of the cycle, rests on the globe, then fades away.
                spread = (t / SPREAD_END).coerceIn(0f, 1f)
                strength = if (t < FADE_START) 1f else 1f - (t - FADE_START) / (1f - FADE_START)
                veil.invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        sweep?.cancel()
        sweep = null
        super.onDetachedFromWindow()
    }

    /** Draws the veil: dark, from the corner out to a soft edge, with a clear hole round the globe. */
    private fun drawVeil(canvas: Canvas) {
        val w = veil.width.toFloat()
        val h = veil.height.toFloat()
        if (w <= 0f || strength <= 0f) return
        val gx = w * GLOBE_X
        val gy = h * GLOBE_Y
        val reach = hypot(gx, gy) * REACH_OVERSHOOT
        val radius = (reach * spread).coerceAtLeast(1f)
        val alpha = (VEIL_ALPHA * strength * 255).toInt()
        val colour = alpha shl 24
        veilPaint.shader = RadialGradient(0f, 0f, radius, intArrayOf(colour, colour, 0), floatArrayOf(0f, EDGE_START, 1f), Shader.TileMode.CLAMP)
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawRect(0f, 0f, w, h, veilPaint)
        canvas.drawCircle(gx, gy, ring.measuredWidth / 2f, clearPaint)
        canvas.restoreToCount(layer)
    }

    private companion object {
        /** The screenshot's size in pixels. */
        const val IMAGE_WIDTH = 1080
        const val IMAGE_HEIGHT = 891

        /** Where the globe key is, as a fraction of the picture's width and height. */
        const val GLOBE_X = 920f / IMAGE_WIDTH
        const val GLOBE_Y = 830f / IMAGE_HEIGHT

        /** The ring's width as a fraction of the picture's width, and its line width in dp. */
        const val RING_FRACTION = 0.1f
        const val RING_DP = 4
        const val RING_COLOUR = 0xFF34D3B4.toInt()

        /** The veil's darkness, and where its soft edge starts (as a fraction of its radius). */
        const val VEIL_ALPHA = 0.72f
        const val EDGE_START = 0.88f

        /** One full cycle: the spread takes the first part, the veil rests, then it fades. */
        const val SWEEP_MS = 3200L
        const val SPREAD_END = 0.6f
        const val FADE_START = 0.85f

        /** The radius reaches a little past the globe, so the veil is clear there and not just edge-soft. */
        const val REACH_OVERSHOOT = 1.05f
    }
}
