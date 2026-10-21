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
internal class ScribbleArrow(
    colour: Int,
    strokePx: Float,
    private val headPx: Float,
    /** For right-to-left reading: the arrow comes from the bottom right instead. */
    private val mirrored: Boolean = false,
) : Drawable() {

    /** Room kept inside the bounds, at the tip's side and above it, so the head and the pen's width stay inside them. */
    val edgePx: Int = Math.ceil((headPx * 0.6f + strokePx).toDouble()).toInt()

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

    /** How much of [progress] draws the head (its share of the whole pen stroke, so the pen keeps one speed). */
    private var headShare = 0.3f

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        // The tip sits [edgePx] in from the top right, so nothing is drawn outside the bounds.
        val l = bounds.left.toFloat()
        val t = bounds.top.toFloat() + edgePx
        val w = bounds.width().toFloat() - edgePx
        val h = bounds.height().toFloat() - edgePx
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
        val headLength = PathMeasure(head, false).length
        val lineLength = PathMeasure(line, false).length
        headShare = if (headLength + lineLength > 0f) headLength / (headLength + lineLength) else 0.3f
    }

    override fun draw(canvas: Canvas) {
        if (progress <= 0f) return
        val saved = canvas.save()
        if (mirrored) canvas.scale(-1f, 1f, bounds.exactCenterX(), 0f)
        stroke(canvas, head, (progress / headShare).coerceAtMost(1f))
        if (progress > headShare) stroke(canvas, line, (progress - headShare) / (1f - headShare))
        canvas.restoreToCount(saved)
    }

    /** Draws the first [part] (0 to 1) of [path]; once drawn in full, simply the path. */
    private fun stroke(canvas: Canvas, path: Path, part: Float) {
        if (part >= 1f) {
            canvas.drawPath(path, paint)
            return
        }
        measure.setPath(path, false)
        shown.reset()
        measure.getSegment(0f, measure.length * part, shown, true)
        canvas.drawPath(shown, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Part of the Drawable contract")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val HEAD_SPREAD = 0.55 // radians either side of the line, about 30°
    }
}
