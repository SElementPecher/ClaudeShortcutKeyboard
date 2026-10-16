package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R

/**
 * The box that shows what is being typed into a blank, with a blinking cursor at the end.
 *
 * It is only a display: the keyboard's own keys type into it (an editable field inside a
 * keyboard would try to take the keyboard for itself).
 */
internal class FillInputView(context: Context) : TextView(context) {

    private var value: String = ""
    private var allSelected = false
    private var hint: String = ""
    private var caretOn = true

    private val accent = context.getColor(R.color.kb_accent)
    private val hintColor = context.getColor(R.color.kb_hint_text)
    private val selectedColor = context.getColor(R.color.kb_chip_selected)

    private val blink = object : Runnable {
        override fun run() {
            caretOn = !caretOn
            refresh()
            postDelayed(this, BLINK_MILLIS)
        }
    }

    init {
        setBackgroundResource(R.drawable.kb_input_box_bg)
        setTextColor(context.getColor(R.color.kb_key_text))
        setTextSizeRes(R.dimen.kb_panel_input_text_size)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        maxLines = 3
        val padding = context.px(R.dimen.kb_panel_padding) * 3 / 2
        setPadding(padding, padding / 2, padding, padding / 2)
        minHeight = context.px(R.dimen.kb_panel_input_height)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    fun show(value: String, allSelected: Boolean, hint: String) {
        this.value = value
        this.allSelected = allSelected
        this.hint = hint
        contentDescription = value.ifEmpty { hint }
        // Keep the cursor solid while typing; blink again after a pause.
        caretOn = true
        restartBlink()
        refresh()
    }

    private fun refresh() {
        // Long text: show the end, where the cursor is.
        val shown = if (value.length > MAX_SHOWN) {
            ELLIPSIS + value.takeLast(MAX_SHOWN - 1).let { if (Character.isLowSurrogate(it[0])) it.drop(1) else it }
        } else {
            value
        }
        val text = SpannableStringBuilder(shown)
        if (allSelected && shown.isNotEmpty()) {
            text.setSpan(BackgroundColorSpan(selectedColor), 0, shown.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val caretStart = text.length
        text.append(CARET)
        text.setSpan(
            ForegroundColorSpan(if (caretOn) accent else Color.TRANSPARENT),
            caretStart,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        if (shown.isEmpty()) {
            val hintStart = text.length
            text.append(hint)
            text.setSpan(ForegroundColorSpan(hintColor), hintStart, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        setText(text, BufferType.SPANNABLE)
    }

    private fun restartBlink() {
        removeCallbacks(blink)
        if (isAttachedToWindow && windowVisibility == View.VISIBLE) postDelayed(blink, BLINK_MILLIS)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartBlink()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blink)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) restartBlink() else removeCallbacks(blink)
    }

    companion object {
        private const val CARET = "|"
        private const val ELLIPSIS = "…"
        private const val MAX_SHOWN = 60
        private const val BLINK_MILLIS = 530L
    }
}
