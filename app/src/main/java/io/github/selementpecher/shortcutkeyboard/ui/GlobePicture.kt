package io.github.selementpecher.shortcutkeyboard.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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
import kotlin.math.max

/**
 * The picture of the phone's keyboard. A dark veil closes in on the globe key from the edges, in round rings that
 * match the globe's shape, and stops once the globe is clear, with a ring round it. It plays once. With the phone's
 * animations turned off, the veil is already in its final place.
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

    /** How far the veil has closed in: 0 at the edges (nothing covered yet), 1 once it reaches the globe. */
    private var progress = 1f
    private var sweep: ValueAnimator? = null

    init {
        addView(image, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(veil, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(ring)
        // Rounded corners, like the cards around it.
        val radius = 20f * resources.displayMetrics.density
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        clipToOutline = true
    }

    private val clip = android.graphics.Path()

    override fun dispatchDraw(canvas: Canvas) {
        clip.reset()
        clip.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 20f * resources.displayMetrics.density, 20f * resources.displayMetrics.density, android.graphics.Path.Direction.CW)
        val saved = canvas.save()
        canvas.clipPath(clip)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saved)
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
            progress = 1f
            veil.invalidate()
            return
        }
        progress = 0f
        sweep = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SWEEP_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
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

    /**
     * Draws the veil outside a circle round the globe. The circle starts past the corners (no veil) and shrinks to the
     * ring round the globe, so the dark edge closes in round the globe's own shape.
     */
    private fun drawVeil(canvas: Canvas) {
        val w = veil.width.toFloat()
        val h = veil.height.toFloat()
        if (w <= 0f) return
        val gx = w * GLOBE_X
        val gy = h * GLOBE_Y
        val farthest = hypot(max(gx, w - gx), max(gy, h - gy))
        val ringRadius = ring.measuredWidth / 2f
        val radius = farthest + (ringRadius - farthest) * progress
        // The edge of the veil is a soft ring, EDGE_SOFT of the way across, so it fades rather than cuts.
        val edge = (radius / farthest).coerceIn(0f, 1f)
        val soft = (edge - EDGE_SOFT).coerceIn(0f, edge)
        val veilColour = (VEIL_ALPHA * 255).toInt() shl 24
        val clear = 0
        veilPaint.shader = RadialGradient(
            gx, gy, farthest,
            intArrayOf(clear, clear, veilColour, veilColour),
            floatArrayOf(0f, soft, max(edge, soft + 0.0001f).coerceAtMost(1f), 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, veilPaint)
    }

    private companion object {
        /** The screenshot's size in pixels. */
        const val IMAGE_WIDTH = 864
        const val IMAGE_HEIGHT = 700

        /** Where the globe key is, as a fraction of the picture's width and height. */
        const val GLOBE_X = 736f / IMAGE_WIDTH
        const val GLOBE_Y = 650f / IMAGE_HEIGHT

        /** The ring's width as a fraction of the picture's width, and its line width in dp. */
        const val RING_FRACTION = 0.1f
        const val RING_DP = 4
        const val RING_COLOUR = 0xFF34D3B4.toInt()

        /** How dark the veil is, and how wide its soft edge is (as a fraction of the way across). */
        const val VEIL_ALPHA = 0.72f
        const val EDGE_SOFT = 0.08f

        /** One slow pass, once. */
        const val SWEEP_MS = 6000L
    }
}
