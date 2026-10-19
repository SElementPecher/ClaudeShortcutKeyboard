package io.github.selementpecher.shortcutkeyboard.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import io.github.selementpecher.shortcutkeyboard.ui.editor.MessageDraft
import io.github.selementpecher.shortcutkeyboard.ui.editor.Segment

/**
 * An example message cut into the words that stay the same and the words that change, so the owner can see
 * which is which. Plain Kotlin, so it can be unit tested.
 */
internal object ExampleParts {

    /** [blank] is −1 for words that never change, the position (0, 1, 2…) of the question for a changing word. */
    data class Part(val text: String, val blank: Int) {
        val changes: Boolean get() = blank != FIXED
    }

    /** A word that is filled in by the phone itself (the time now, today's date); it changes, but nobody is asked. */
    const val BUILT_IN = -2

    const val FIXED = -1

    /** The message as parts, in order. Two blanks never share a part; the same blank keeps the same number. */
    fun of(draft: MessageDraft): List<Part> {
        val order = draft.questions().withIndex().associate { it.value.blankId to it.index }
        val parts = ArrayList<Part>()
        for ((index, segment) in draft.segments.withIndex()) {
            // The same rule as the keyboard: a value is always set off from the word before it.
            if (segment !is Segment.Text && io.github.selementpecher.shortcutkeyboard.template.Template.needsSpaceAfterChar(parts.lastOrNull()?.text?.lastOrNull())) {
                parts += Part(" ", FIXED)
            }
            when (segment) {
                is Segment.Text -> if (segment.text.isNotEmpty()) parts += Part(segment.text, FIXED)
                is Segment.Ref -> parts += Part(segment.shown, order[segment.blankId] ?: 0)
                is Segment.Fixed -> parts += Part(segment.shown, BUILT_IN)
            }
            // The same rule the keyboard uses: a value is always followed by a space before a word.
            if (segment !is Segment.Text) {
                val next = draft.segments.getOrNull(index + 1)
                val needsSpace = when (next) {
                    is Segment.Text -> next.text.firstOrNull()?.let { it.isLetterOrDigit() || it == '(' } == true
                    null -> false
                    else -> true
                }
                if (needsSpace) parts += Part(" ", FIXED)
            }
        }
        return parts
    }

    /** All the words joined: the same as the plain example message. */
    fun plain(parts: List<Part>): String = parts.joinToString("") { it.text }
}

/**
 * Builds the example message as styled text: fixed words in plain ink, each changing word as a soft rounded
 * highlight in the colour of its question's tile (see [RoundedHighlightSpan]).
 */
internal object ExampleText {

    /**
     * @param colors the tile colours to use, by question position (wrapped around when there are more questions).
     * @param neutral the highlight for words the phone fills in by itself.
     */
    fun build(
        parts: List<ExampleParts.Part>,
        colors: List<io.github.selementpecher.shortcutkeyboard.look.TileColors>,
        neutral: io.github.selementpecher.shortcutkeyboard.look.TileColors,
        paddingPx: Float,
        radiusPx: Float,
        verticalPx: Float,
    ): CharSequence {
        val out = SpannableStringBuilder()
        for (part in parts) {
            if (!part.changes) {
                out.append(part.text)
                continue
            }
            val tile = if (part.blank == ExampleParts.BUILT_IN || colors.isEmpty()) neutral else colors[part.blank % colors.size]
            // A short value stays in one piece (no line break inside it); only a long one may wrap.
            val pieces = if (part.text.length <= UNBROKEN_MAX) listOf(part.text.replace(' ', NO_BREAK_SPACE)) else splitAtSpaces(part.text)
            pieces.forEachIndexed { index, piece ->
                val start = out.length
                out.append(piece)
                out.setSpan(
                    RoundedHighlightSpan(
                        background = tile.bg,
                        foreground = tile.fg,
                        paddingPx = paddingPx,
                        radiusPx = radiusPx,
                        verticalPx = verticalPx,
                        roundStart = index == 0,
                        roundEnd = index == pieces.lastIndex,
                    ),
                    start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return out
    }

    private const val UNBROKEN_MAX = 22
    private const val NO_BREAK_SPACE = '\u00A0'

    /** "Sam Jones" becomes "Sam " and "Jones", so a long answer can wrap onto the next line. */
    internal fun splitAtSpaces(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val pieces = ArrayList<String>()
        var from = 0
        for (i in text.indices) {
            if (text[i] == ' ' && i + 1 < text.length) {
                pieces += text.substring(from, i + 1)
                from = i + 1
            }
        }
        pieces += text.substring(from)
        return pieces
    }
}

/**
 * Draws the text it covers on a soft rounded background, like a highlighter pen (the built-in background
 * span has square corners). A changing word is made of one or more of these, so a long one can wrap:
 * only the first piece has a rounded left end and only the last a rounded right end.
 */
internal class RoundedHighlightSpan(
    private val background: Int,
    private val foreground: Int,
    private val paddingPx: Float,
    private val radiusPx: Float,
    private val verticalPx: Float,
    private val roundStart: Boolean,
    private val roundEnd: Boolean,
) : ReplacementSpan() {

    private val rect = RectF()
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun startPad() = if (roundStart) paddingPx else 0f
    private fun endPad() = if (roundEnd) paddingPx else 0f

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        // Make the line tall enough for the highlight's own padding, or its top and bottom are cut off.
        if (fm != null) {
            val m = paint.fontMetricsInt
            val pad = Math.ceil(verticalPx.toDouble()).toInt()
            fm.ascent = m.ascent - pad
            fm.top = fm.ascent
            fm.descent = m.descent + pad
            fm.bottom = fm.descent
        }
        return (paint.measureText(text, start, end) + startPad() + endPad() + 0.5f).toInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val width = paint.measureText(text, start, end)
        val metrics = paint.fontMetrics
        rect.set(x, y + metrics.ascent - verticalPx, x + startPad() + width + endPad(), y + metrics.descent + verticalPx)
        val left = if (roundStart) radiusPx else 0f
        val right = if (roundEnd) radiusPx else 0f
        path.reset()
        path.addRoundRect(rect, floatArrayOf(left, left, right, right, right, right, left, left), Path.Direction.CW)
        fill.color = background
        canvas.drawPath(path, fill)

        val oldColor = paint.color
        paint.color = foreground
        canvas.drawText(text, start, end, x + startPad(), y.toFloat(), paint)
        paint.color = oldColor
    }
}
