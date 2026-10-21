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
 * A quick pen arrow, the way you would point at a word by hand: it starts at the bottom left of its bounds, sweeps
 * right and curves up to its tip at the top right, then the two strokes of the head. [progress] (0 to 1) is how much
 * of it is drawn so far: the line first, then the head.
 */
internal class ScribbleArrow(colour: Int, strokePx: Float, private val headPx: Float) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val line = Path()
    private val shown = Path()
    private val measure = PathMeasure()
    private var tipX = 0f
    private var tipY = 0f
    private var backAngle = 0.0

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
        tipX = l + w
        tipY = t
        // Along the bottom, then a curve up into the tip.
        val c2x = l + w * 0.93f
        val c2y = t + h * 0.5f
        line.reset()
        line.moveTo(l, t + h)
        line.cubicTo(l + w * 0.5f, t + h * 1.02f, c2x, c2y, tipX, tipY)
        // The head points back along the line's last direction.
        backAngle = atan2((c2y - tipY).toDouble(), (c2x - tipX).toDouble())
    }

    override fun draw(canvas: Canvas) {
        if (progress <= 0f) return
        measure.setPath(line, false)
        shown.reset()
        val lineDone = (progress / LINE_SHARE).coerceAtMost(1f)
        measure.getSegment(0f, measure.length * lineDone, shown, true)
        canvas.drawPath(shown, paint)
        if (progress <= LINE_SHARE) return
        val head = headPx * ((progress - LINE_SHARE) / (1f - LINE_SHARE))
        for (side in listOf(-1, 1)) {
            val a = backAngle + side * HEAD_SPREAD
            canvas.drawLine(tipX, tipY, tipX + head * cos(a).toFloat(), tipY + head * sin(a).toFloat(), paint)
        }
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
        /** How much of [progress] is the line; the rest draws the head. */
        const val LINE_SHARE = 0.75f
        const val HEAD_SPREAD = 0.55 // radians either side of the line, about 30°
    }
}
