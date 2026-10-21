package io.github.selementpecher.shortcutkeyboard.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * What the hand-drawn pen marks share (the loop and the arrow): a round pen in one colour, and [progress] (0 to 1),
 * how much of the mark is drawn so far.
 */
internal abstract class PenStroke(colour: Int, strokePx: Float) : Drawable() {

    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour
        style = Paint.Style.STROKE
        strokeWidth = strokePx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shown = Path()
    private val measure = PathMeasure()

    var progress: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    /** Draws the first [part] (0 to 1) of [path]; once drawn in full, simply the path. */
    protected fun stroke(canvas: Canvas, path: Path, part: Float) {
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
        invalidateSelf()
    }

    @Deprecated("Part of the Drawable contract")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
