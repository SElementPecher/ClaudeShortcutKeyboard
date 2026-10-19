package io.github.selementpecher.shortcutkeyboard.ime

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import io.github.selementpecher.shortcutkeyboard.fill.ResultPiece
import io.github.selementpecher.shortcutkeyboard.look.TileColors

/**
 * Draws a stretch of text on a soft rounded background in a tile's colours. A value of several
 * words is made of several of these (one per word), joined up: only the first has rounded
 * left corners and only the last has rounded right corners, so it can wrap over lines.
 */
internal class PillSpan(
    private val colors: TileColors,
    private val radius: Float,
    private val padX: Float,
    private val padY: Float,
    private val roundStart: Boolean,
    private val roundEnd: Boolean,
) : ReplacementSpan() {
    private val path = Path()
    private val rect = RectF()

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        // Make the line tall enough for the pill's own padding, or its top and bottom are cut off.
        if (fm != null) {
            val m = paint.fontMetricsInt
            val pad = Math.ceil(padY.toDouble()).toInt()
            fm.ascent = m.ascent - pad
            fm.top = fm.ascent
            fm.descent = m.descent + pad
            fm.bottom = fm.descent
        }
        return Math.ceil(paint.measureText(text, start, end) + startPad() + endPad().toDouble()).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val width = paint.measureText(text, start, end)
        val metrics = paint.fontMetrics
        rect.set(x, y + metrics.ascent - padY, x + width + startPad() + endPad(), y + metrics.descent + padY)
        val left = if (roundStart) radius else 0f
        val right = if (roundEnd) radius else 0f
        path.reset()
        path.addRoundRect(rect, floatArrayOf(left, left, right, right, right, right, left, left), Path.Direction.CW)
        val oldColor = paint.color
        paint.color = colors.bg
        canvas.drawPath(path, paint)
        paint.color = colors.fg
        canvas.drawText(text, start, end, x + startPad(), y.toFloat(), paint)
        paint.color = oldColor
    }

    private fun startPad() = if (roundStart) padX else 0f
    private fun endPad() = if (roundEnd) padX else 0f
}

/** Builds the message text for the check screen. */
internal object ResultSpans {
    private const val UNBROKEN_MAX = 22

    /** The pieces as one text, every filled-in value on a rounded highlight in the tile colour of its blank. */
    fun build(pieces: List<ResultPiece>, tiles: List<TileColors>, radius: Float, padX: Float, padY: Float): CharSequence {
        val text = SpannableStringBuilder()
        for (piece in pieces) {
            if (!piece.isFilled || tiles.isEmpty()) {
                text.append(piece.text)
                continue
            }
            val colors = tiles[Math.floorMod(piece.blank, tiles.size)]
            // A short value stays in one piece (no line break inside it); only a long one may wrap.
            val chunks = if (piece.text.length <= UNBROKEN_MAX) listOf(piece.text.replace(' ', '\u00A0')) else chunksOf(piece.text)
            chunks.forEachIndexed { i, chunk ->
                val start = text.length
                text.append(chunk)
                text.setSpan(
                    PillSpan(colors, radius, padX, padY, roundStart = i == 0, roundEnd = i == chunks.lastIndex),
                    start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return text
    }

    /** "Sam Smith" as "Sam " and "Smith", so a long value can wrap onto the next line. */
    fun chunksOf(value: String): List<String> {
        val chunks = ArrayList<String>()
        val current = StringBuilder()
        for (c in value) {
            current.append(c)
            if (c == ' ' || c == '\n') {
                chunks += current.toString()
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }
}
