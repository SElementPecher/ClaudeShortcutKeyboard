package io.github.selementpecher.shortcutkeyboard.look

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.sin

/**
 * The app icon drawn small: three lines of wavy "words", with one pill in the middle line where the words change.
 * Colours come from the Look, so it suits every one of them.
 */
class LogoMark(private val words: Int, private val pill: Int, private val pillInk: Int) : Drawable() {

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = words }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pill }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = pillInk }
    private val path = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = minOf(b.width(), b.height()).toFloat()
        val left = b.left + (b.width() - s) / 2f
        val top = b.top + (b.height() - s) / 2f
        line.strokeWidth = s * 0.075f
        dash.strokeWidth = s * 0.08f
        fun wave(x1: Float, x2: Float, y: Float) {
            path.reset()
            val amp = s * 0.035f
            val step = s * 0.01f
            var x = x1
            path.moveTo(left + x, top + y)
            while (x <= x2) {
                path.lineTo(left + x, top + y - amp * sin((x - x1) / (s * 0.12f) * 2 * PI).toFloat())
                x += step
            }
            canvas.drawPath(path, line)
        }
        wave(s * 0.08f, s * 0.92f, s * 0.22f)
        wave(s * 0.08f, s * 0.28f, s * 0.5f)
        wave(s * 0.72f, s * 0.92f, s * 0.5f)
        wave(s * 0.08f, s * 0.6f, s * 0.78f)
        rect.set(left + s * 0.31f, top + s * 0.37f, left + s * 0.69f, top + s * 0.63f)
        canvas.drawRoundRect(rect, s * 0.13f, s * 0.13f, fill)
        canvas.drawLine(left + s * 0.4f, top + s * 0.5f, left + s * 0.6f, top + s * 0.5f, dash)
    }

    override fun setAlpha(alpha: Int) { line.alpha = alpha; fill.alpha = alpha; dash.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Part of the Drawable contract")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
