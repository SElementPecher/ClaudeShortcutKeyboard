package io.github.selementpecher.shortcutkeyboard.look

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable

/**
 * A page of ruled school paper: the page colour and thin blue lines across, [LINE_GAP_DP] apart. The first line is
 * [phasePx] from the top, so a screen can line its rules up with its text. The red margin is a separate drawable
 * ([MarginLine]) drawn over everything, so nothing can cover it.
 */
internal class RuledPaper(context: Context, private val page: Int, private val rule: Int) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint()

    /** How far down the first line is, 0 until [gapPx]. */
    var phasePx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    val gapPx: Float get() = LINE_GAP_DP * density

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawColor(page)
        paint.color = rule
        val thick = max(1f, LINE_DP * density)
        var y = b.top + phasePx
        while (y < b.bottom) {
            canvas.drawRect(b.left.toFloat(), y, b.right.toFloat(), y + thick, paint)
            y += gapPx
        }
    }

    private fun max(a: Float, b: Float) = if (a > b) a else b

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Part of the Drawable contract")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    companion object {
        const val LINE_GAP_DP = 32f
        const val LINE_DP = 1.2f
    }
}

/** The red margin line down the left of the page. Drawn as a foreground, so nothing sits on top of it. */
internal class MarginLine(context: Context, private val colour: Int) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint()

    override fun draw(canvas: Canvas) {
        val b = bounds
        paint.color = colour
        val x = b.left + MARGIN_X_DP * density
        canvas.drawRect(x, b.top.toFloat(), x + MARGIN_DP * density, b.bottom.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Part of the Drawable contract")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /** Close to the left edge, clear of every tile, card and button. */
        const val MARGIN_X_DP = 6f
        const val MARGIN_DP = 1.5f
    }
}

/** The background for a screen or the keyboard in this Look: ruled paper, or a plain colour. */
internal fun Look.pageBackground(context: Context, palette: Palette): Drawable =
    if (ruled) RuledPaper(context, palette.bg, palette.outline) else ColorDrawable(palette.bg)

/** The red margin to draw over a screen in this Look, or null. */
internal fun Look.marginLine(context: Context, palette: Palette): Drawable? =
    if (ruled) MarginLine(context, (palette.danger and 0x00FFFFFF) or 0x99000000.toInt()) else null
