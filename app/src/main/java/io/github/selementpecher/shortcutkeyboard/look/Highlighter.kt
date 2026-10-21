package io.github.selementpecher.shortcutkeyboard.look

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.abs

/**
 * The playful parts of the Exercise book Look: highlighter-pen shapes (slightly uneven rounded ends), index cards with a
 * highlighter swipe under the name, and sticky notes. They only show when the chosen Look has [Look.ruled] set.
 */
internal object Highlighter {

    /** True while a Look with [Look.ruled] is chosen. Kept up to date by [LookStore]. */
    @Volatile
    var on: Boolean = false

    /**
     * A rounded shape for [rect] added to [into]. With the Look off it is the usual shape (the same radius at every end).
     * With it on, the corners are a little different from each other, like a pen stroke.
     */
    fun addShape(into: Path, rect: RectF, radius: Float, roundStart: Boolean = true, roundEnd: Boolean = true, uneven: Boolean = on) {
        val h = rect.height()
        if (!uneven) {
            val l = if (roundStart) radius else 0f
            val r = if (roundEnd) radius else 0f
            into.addRoundRect(rect, floatArrayOf(l, l, r, r, r, r, l, l), Path.Direction.CW)
            return
        }
        val cap = h / 2f
        fun k(f: Float) = minOf(cap, h * f)
        val s = if (roundStart) 1f else 0f
        val e = if (roundEnd) 1f else 0f
        into.addRoundRect(
            rect,
            floatArrayOf(
                k(.18f) * s, k(.46f) * s, // top left (across, down)
                k(.42f) * e, k(.22f) * e, // top right
                k(.22f) * e, k(.48f) * e, // bottom right
                k(.46f) * s, k(.26f) * s, // bottom left
            ),
            Path.Direction.CW,
        )
    }

    /** A small, steady tilt in degrees (about -2 to 2) for the sticky note that [key] names. */
    fun stickyAngle(key: String): Float {
        val spread = (key.hashCode().toLong() * 2654435761L) ushr 8
        val steps = Math.floorMod(spread, 9L).toInt() // 0..8
        val angle = (steps - 4) * 0.5f // -2.0 .. 2.0
        return if (abs(angle) < 0.5f) 1.0f else angle
    }
}

/**
 * An index card: a plain card with a highlighter swipe in the tile's colour under the name.
 * [swipeLeftPx] is where the swipe starts (after the picture), [swipeHeightPx] how thick it is.
 */
internal class IndexCardDrawable(
    private val fill: Int,
    private val swipe: Int,
    private val radiusPx: Float,
    private val swipeLeftPx: Float,
    private val swipeHeightPx: Float,
    private val insetPx: Float,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        rect.set(b)
        paint.color = fill
        canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
        val left = b.left + swipeLeftPx
        val right = b.right - insetPx - (b.width() - swipeLeftPx - insetPx) * 0.2f
        val bottom = b.bottom - insetPx
        if (right > left + swipeHeightPx) {
            rect.set(left, bottom - swipeHeightPx, right, bottom)
            path.reset()
            // Index cards only exist in the Exercise book, so the swipe is always hand-drawn (whatever Look the app has).
            Highlighter.addShape(path, rect, swipeHeightPx / 2f, uneven = true)
            paint.color = swipe
            canvas.drawPath(path, paint)
        }
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Part of the Drawable contract")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
