package io.github.selementpecher.shortcutkeyboard.ui.editor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.StateListDrawable
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.text.SpannableStringBuilder
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.EditText
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf
import io.github.selementpecher.shortcutkeyboard.ui.Motion
import io.github.selementpecher.shortcutkeyboard.ui.Ui
import kotlin.math.abs
import kotlin.math.max

/**
 * The message box. It holds the owner's words with every changing part as a chip ([ChipSpan]) sitting on one
 * placeholder character ([MessageDraft.PLACEHOLDER]): the text is the single source of truth. Backspace on a
 * chip removes it like a letter; the placeholder can't be typed or pasted; tapping a chip reports it.
 *
 * While [locked] (the chain of choices is open) touches are swallowed, so the message can only be read.
 */
internal class MessageEditText(context: Context, private val ui: Ui) : EditText(context) {

    /** Called with the number of the tapped chip (counting chips from the start of the message). */
    var onChipTap: ((Int) -> Unit)? = null

    /** True while the owner can only look at the message (the chain is open). */
    var locked: Boolean = false

    private val metrics = ChipMetrics(resources.displayMetrics.density)
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.look.type.bodySp + 1)
        typeface = typefaceOf(400)
        setTextColor(ui.palette.ink)
        setHintTextColor(ui.palette.inkSoft)
        letterSpacing = Ui.READING_LETTER_SPACING
        setLineSpacing(0f, 1.35f)
        val r = minOf(ui.look.shape.cardRadiusDp, 16).toFloat()
        val rest = if (ui.look.shape.strokeDp > 0f) ui.shape(ui.palette.surface, r, ui.palette.outline, ui.look.shape.strokeDp)
        else ui.shape(ui.palette.surfaceAlt, r)
        val focus = ui.shape(
            if (ui.look.shape.strokeDp > 0f) ui.palette.surface else ui.palette.surfaceAlt,
            r, ui.palette.accent, max(2f, ui.look.shape.strokeDp),
        )
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focus)
            addState(intArrayOf(), rest)
        }
        setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 5
        maxLines = 12
        isVerticalScrollBarEnabled = false
        filters = arrayOf(StripPlaceholder)
        highlightColor = ui.withAlpha(ui.palette.accent, 0x44)
    }

    /** Keeps the placeholder character out of anything typed or pasted. */
    private object StripPlaceholder : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
            for (i in start until end) {
                if (source[i] == MessageDraft.PLACEHOLDER) {
                    return source.subSequence(start, end).toString().replace(MessageDraft.PLACEHOLDER.toString(), "")
                }
            }
            return null
        }
    }

    /**
     * Shows [draft]: its words, and a chip for each changing part, styled by [styleOf] (given the chip's number and part).
     * The caret goes to [selStart] to [selEnd].
     */
    fun render(draft: MessageDraft, selStart: Int, selEnd: Int = selStart, styleOf: (Int, Segment) -> ChipStyle) {
        val out = SpannableStringBuilder()
        var index = 0
        for (segment in draft.segments) {
            if (segment is Segment.Text) {
                out.append(segment.text)
            } else {
                val at = out.length
                out.append(MessageDraft.PLACEHOLDER)
                out.setSpan(ChipSpan(segment, styleOf(index++, segment), metrics), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        // setText runs the input filters too, and ours would strip the placeholders the chips sit on.
        val kept = filters
        filters = emptyArray()
        setText(out)
        filters = kept
        val a = selStart.coerceIn(0, length())
        val b = selEnd.coerceIn(0, length())
        setSelection(minOf(a, b), maxOf(a, b))
    }

    private fun spans(): List<ChipSpan> {
        val text = text ?: return emptyList()
        return text.getSpans(0, text.length, ChipSpan::class.java).sortedBy { text.getSpanStart(it) }
    }

    /** The parts the chips stand for, in order: what the draft needs to follow the typing. */
    fun chipSegments(): List<Segment> = spans().map { it.segment }

    /** The text as the draft sees it (one placeholder per chip). */
    fun editableText(): String = text?.toString().orEmpty()

    /** Gives the chips new colours and words (after a change that moves colours around) without touching the text. */
    fun restyle(styleOf: (Int, Segment) -> ChipStyle) {
        spans().forEachIndexed { i, span -> span.style = styleOf(i, span.segment) }
        invalidate()
    }

    /** Grows chip number [index] in. Skipped when animations are off. */
    fun popChip(index: Int) {
        val span = spans().getOrNull(index) ?: return
        if (!Motion.enabled(context)) return
        span.pop = 0f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.POP_MS
            addUpdateListener {
                span.pop = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        metrics.maxWidthPx = (w - totalPaddingLeft - totalPaddingRight).coerceAtLeast(ui.dp(80))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (locked) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) <= slop && abs(event.y - downY) <= slop) {
                    val tapped = chipAt(event.x, event.y)
                    if (tapped >= 0) {
                        onChipTap?.invoke(tapped)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /** The number of the chip under a touch, or -1. */
    private fun chipAt(touchX: Float, touchY: Float): Int {
        val x = touchX - totalPaddingLeft + scrollX
        val y = touchY - extendedPaddingTop + scrollY
        val slack = ui.dpf(4f)
        spans().forEachIndexed { i, span ->
            val b = span.bounds
            if (x >= b.left - slack && x <= b.right + slack && y >= b.top - slack && y <= b.bottom + slack) return i
        }
        return -1
    }

    override fun onCheckIsTextEditor(): Boolean = !locked && super.onCheckIsTextEditor()

    /** Drops the caret and the keyboard link while the chain is open. */
    fun lock(on: Boolean) {
        locked = on
        isCursorVisible = !on
        if (on) clearFocus()
    }
}
