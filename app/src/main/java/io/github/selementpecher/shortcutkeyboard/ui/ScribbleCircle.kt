package io.github.selementpecher.shortcutkeyboard.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/**
 * A quick pen loop drawn round something to point at it, the way you would circle a word by hand: not quite closed,
 * a little wobbly, finishing just outside where it started. [progress] (0 to 1) is how much of it is drawn so far.
 */
internal class ScribbleCircle(colour: Int, strokePx: Float) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val loop = Path()
    private val shown = Path()
    private val measure = PathMeasure()

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        loop.reset()
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val rx = bounds.width() / 2f
        val ry = bounds.height() / 2f
        // Starts at the top left, goes round clockwise, and ends low on the left a little outside the start: a gap.
        val steps = 64
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val angle = Math.toRadians(START_DEGREES + SWEEP_DEGREES * t)
            val wobble = 1f + WOBBLE * sin(t * 9.0).toFloat()
            val grow = 1f + GROW * t
            val x = cx + rx * grow * wobble * cos(angle).toFloat()
            val y = cy + ry * grow * wobble * sin(angle).toFloat()
            if (i == 0) loop.moveTo(x, y) else loop.lineTo(x, y)
        }
    }

    override fun draw(canvas: Canvas) {
        if (progress <= 0f) return
        measure.setPath(loop, false)
        shown.reset()
        measure.getSegment(0f, measure.length * progress, shown, true)
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
        const val START_DEGREES = 215.0
        const val SWEEP_DEGREES = 315.0
        const val WOBBLE = 0.03f
        const val GROW = 0.08f
    }
}
