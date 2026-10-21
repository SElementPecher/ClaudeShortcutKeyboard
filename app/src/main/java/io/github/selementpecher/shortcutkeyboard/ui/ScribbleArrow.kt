package io.github.selementpecher.shortcutkeyboard.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A quick pen arrow, the way you would point at a word by hand: the tip is at the top right of its bounds and the line
 * curves down and away to the bottom left. It is drawn the way a hand would: the head first, in one stroke from left to
 * right through the tip, then the line from the tip down. [progress] (0 to 1) is how much of it is drawn so far.
 */
internal class ScribbleArrow(colour: Int, strokePx: Float, private val headPx: Float) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val head = Path()
    private val line = Path()
    private val shown = Path()
    private val measure = PathMeasure()

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        val l = bounds.left.toFloat()
        val t = bounds.top.toFloat()
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val tipX = l + w
        val tipY = t
        // From the tip, a curve down and round to the bottom left.
        val c1x = l + w * 0.93f
        val c1y = t + h * 0.5f
        line.reset()
        line.moveTo(tipX, tipY)
        line.cubicTo(c1x, c1y, l + w * 0.5f, t + h * 1.02f, l, t + h)
        // The head's two strokes point back along the line, made into one stroke from the left barb to the right one.
        val back = atan2((c1y - tipY).toDouble(), (c1x - tipX).toDouble())
        val barbs = listOf(-1, 1).map { side ->
            val a = back + side * HEAD_SPREAD
            floatArrayOf(tipX + headPx * cos(a).toFloat(), tipY + headPx * sin(a).toFloat())
        }.sortedBy { it[0] }
        head.reset()
        head.moveTo(barbs[0][0], barbs[0][1])
        head.lineTo(tipX, tipY)
        head.lineTo(barbs[1][0], barbs[1][1])
    }

    override fun draw(canvas: Canvas) {
        if (progress <= 0f) return
        stroke(canvas, head, (progress / HEAD_SHARE).coerceAtMost(1f))
        if (progress > HEAD_SHARE) stroke(canvas, line, (progress - HEAD_SHARE) / (1f - HEAD_SHARE))
    }

    /** Draws the first [part] (0 to 1) of [path]. */
    private fun stroke(canvas: Canvas, path: Path, part: Float) {
        measure.setPath(path, false)
        shown.reset()
        measure.getSegment(0f, measure.length * part, shown, true)
        canvas.drawPath(shown, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Part of the Drawable contract")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        /** How much of [progress] draws the head; the rest draws the line. */
        const val HEAD_SHARE = 0.3f
        const val HEAD_SPREAD = 0.55 // radians either side of the line, about 30°
    }
}
