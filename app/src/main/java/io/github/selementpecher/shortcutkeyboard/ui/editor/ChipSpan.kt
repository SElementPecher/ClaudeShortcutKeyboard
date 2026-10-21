package io.github.selementpecher.shortcutkeyboard.ui.editor

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ReplacementSpan
import io.github.selementpecher.shortcutkeyboard.look.Highlighter
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf

/** How one chip looks: its picture, its words and its colours. */
internal data class ChipStyle(
    val glyph: String,
    val label: String,
    val background: Int,
    val foreground: Int,
    /** A ring around the chip, for the one being made or changed right now. */
    val ring: Int? = null,
    /** A dashed outline instead of a fill, for parts the phone fills in by itself. */
    val dashed: Boolean = false,
)

/** What every chip in one text box needs to know about its surroundings. */
internal class ChipMetrics(val density: Float) {
    /** The widest a chip may be (the line), so a long question is shortened instead of running off the screen. */
    var maxWidthPx: Int = Int.MAX_VALUE
}

/**
 * A changing part drawn inside the message: a rounded pill in the part's colour with its picture and question,
 * as one character (U+FFFC) of the text. It never breaks across lines and its words are never clipped
 * (a very long question ends in "…" only when it is wider than the whole line).
 *
 * [segment] says which part of the draft it stands for; the text box reads the chips back in order.
 */
internal class ChipSpan(val segment: Segment, style: ChipStyle, private val metrics: ChipMetrics) : ReplacementSpan() {

    var style: ChipStyle = style

    /** 0 to 1 while the chip grows in. */
    var pop: Float = 1f

    /** Where the chip was last drawn, in the text's own coordinates (for finding a tap). */
    val bounds = RectF()

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }
    private val chipPath = android.graphics.Path()

    private fun dp(value: Float) = value * metrics.density

    private val padH get() = dp(12f)
    private val padV get() = dp(5f)

    private fun prepare(paint: Paint) {
        textPaint.set(paint)
        textPaint.typeface = typefaceOf(600)
        textPaint.color = style.foreground
        textPaint.letterSpacing = 0.02f
    }

    private fun content(): CharSequence {
        val full = if (style.glyph.isEmpty()) style.label else style.glyph + "  " + style.label
        val room = (metrics.maxWidthPx - 2 * padH).coerceAtLeast(dp(60f))
        return TextUtils.ellipsize(full, textPaint, room, TextUtils.TruncateAt.END)
    }

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        prepare(paint)
        if (fm != null) {
            val m = paint.fontMetricsInt
            fm.ascent = m.ascent - padV.toInt()
            fm.descent = m.descent + padV.toInt()
            fm.top = fm.ascent
            fm.bottom = fm.descent
        }
        val shown = content()
        return (textPaint.measureText(shown, 0, shown.length) + 2 * padH + 0.5f).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        prepare(paint)
        val shown = content()
        val width = textPaint.measureText(shown, 0, shown.length) + 2 * padH
        val m = paint.fontMetrics
        val gap = dp(2f)
        val rect = RectF(x, y + m.ascent - padV + gap, x + width, y + m.descent + padV - gap)
        bounds.set(rect)
        val radius = rect.height() / 2f

        canvas.save()
        if (pop < 1f) {
            val scale = 0.82f + 0.18f * pop
            canvas.scale(scale, scale, rect.centerX(), rect.centerY())
        }
        val alpha = if (pop < 1f) (255 * (0.4f + 0.6f * pop)).toInt().coerceIn(0, 255) else 255
        if (style.dashed) {
            linePaint.color = style.foreground
            linePaint.strokeWidth = dp(1.5f)
            linePaint.pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
            linePaint.alpha = alpha
            val inset = dp(1f)
            canvas.drawRoundRect(RectF(rect).apply { inset(inset, inset) }, radius, radius, linePaint)
        } else {
            fillPaint.color = style.background
            fillPaint.alpha = alpha
            chipPath.reset()
            Highlighter.addShape(chipPath, rect, radius)
            canvas.drawPath(chipPath, fillPaint)
        }
        style.ring?.let { ring ->
            linePaint.color = ring
            linePaint.strokeWidth = dp(2.5f)
            linePaint.pathEffect = null
            linePaint.alpha = alpha
            val inset = dp(1.25f)
            canvas.drawRoundRect(RectF(rect).apply { inset(inset, inset) }, radius, radius, linePaint)
        }
        textPaint.alpha = alpha
        canvas.drawText(shown, 0, shown.length, x + padH, y.toFloat(), textPaint)
        canvas.restore()
    }
}
