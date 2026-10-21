package io.github.selementpecher.shortcutkeyboard.look

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * A page of ruled school paper: the page colour, thin blue lines across and a red margin line down the left.
 * Used behind screens and the keyboard by Looks with [Look.ruled] set.
 */
internal class RuledPaper(context: Context, private val page: Int, private val rule: Int, private val margin: Int) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint()

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawColor(page)
        paint.color = rule
        val gap = LINE_GAP_DP * density
        val thick = LINE_DP * density
        var y = b.top + gap
        while (y < b.bottom) {
            canvas.drawRect(b.left.toFloat(), y, b.right.toFloat(), y + thick, paint)
            y += gap
        }
        paint.color = margin
        val x = b.left + MARGIN_X_DP * density
        canvas.drawRect(x, b.top.toFloat(), x + MARGIN_DP * density, b.bottom.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) {}

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Part of the Drawable contract")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    private companion object {
        const val LINE_GAP_DP = 36f
        const val LINE_DP = 1.2f
        const val MARGIN_X_DP = 12f
        const val MARGIN_DP = 1.5f
    }
}

/** The background for a screen or the keyboard in this Look: ruled paper, or a plain colour. */
internal fun Look.pageBackground(context: Context, palette: Palette): Drawable =
    if (ruled) RuledPaper(context, palette.bg, palette.outline, (palette.danger and 0x00FFFFFF) or 0x99000000.toInt())
    else android.graphics.drawable.ColorDrawable(palette.bg)
