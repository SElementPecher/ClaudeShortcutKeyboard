package io.github.selementpecher.shortcutkeyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R

/**
 * One key on the keyboard.
 *
 * Most keys act as soon as they are pressed (quick, and fast typing keeps its order). Delete
 * repeats while held. Space acts when let go, and a long press on it opens the keyboard picker.
 * Enter and the globe key act when let go, so sliding off cancels them.
 *
 * The whole cell, gaps included, takes touches; the gap is only drawn (see [KeyBackground]).
 */
@SuppressLint("ViewConstructor")
internal class KeyView(
    context: Context,
    val key: Key,
    private val listener: Listener,
    gap: Int,
) : TextView(context) {

    interface Listener {
        fun onKey(key: Key, timeMillis: Long)
        fun onSpaceLongPress()
    }

    /** An icon drawn in the middle of the key instead of text. Tinted with the text colour. */
    var icon: Drawable? = null
        set(value) {
            field = value?.mutate()?.apply { setTint(currentTextColor) }
            invalidate()
        }

    private val iconSize = context.px(R.dimen.kb_icon_size)
    private val startFraction = key.padStart / key.weight
    private val endFraction = key.padEnd / key.weight

    private var longPressDone = false

    private val repeater = object : Runnable {
        override fun run() {
            listener.onKey(key, SystemClock.uptimeMillis())
            postDelayed(this, REPEAT_INTERVAL_MILLIS)
        }
    }

    private val longPress = Runnable {
        longPressDone = true
        keyFeedback()
        listener.onSpaceLongPress()
    }

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        isSoundEffectsEnabled = false
        isClickable = true
        isLongClickable = key.kind == KeyKind.SPACE
        val base = when (key.kind) {
            KeyKind.CHARACTER, KeyKind.SPACE -> R.drawable.kb_key_bg
            KeyKind.ENTER -> R.drawable.kb_key_accent_bg
            else -> R.drawable.kb_key_special_bg
        }
        context.getDrawable(base)?.let { background = KeyBackground(it, gap / 2, startFraction, endFraction) }
        setTextColor(context.getColor(if (key.kind == KeyKind.ENTER) R.color.kb_on_accent else R.color.kb_key_text))
        if (key.kind == KeyKind.CHARACTER) {
            setTextSizeRes(R.dimen.kb_key_text_size)
        } else {
            setTextSizeRes(R.dimen.kb_key_label_text_size)
            // Long labels such as "Search" shrink to fit instead of being cut off.
            setAutoSizeTextTypeUniformWithConfiguration(
                resources.getDimensionPixelSize(R.dimen.kb_key_label_min_text_size),
                resources.getDimensionPixelSize(R.dimen.kb_key_label_text_size),
                1,
                TypedValue.COMPLEX_UNIT_PX,
            )
        }
    }

    // Touches are handled here; performClick is only used by screen readers (see below).
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                keyFeedback()
                when (key.kind) {
                    KeyKind.DELETE -> {
                        listener.onKey(key, event.eventTime)
                        postDelayed(repeater, REPEAT_START_MILLIS)
                    }
                    KeyKind.SPACE -> {
                        longPressDone = false
                        postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                    KeyKind.ENTER, KeyKind.NEXT_KEYBOARD -> Unit
                    else -> listener.onKey(key, event.eventTime)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (actsOnRelease && isPressed && !isInside(event)) isPressed = false
            }
            MotionEvent.ACTION_UP -> {
                val stillPressed = isPressed
                stop()
                when (key.kind) {
                    KeyKind.SPACE -> if (!longPressDone) listener.onKey(key, event.eventTime)
                    KeyKind.ENTER, KeyKind.NEXT_KEYBOARD -> if (stillPressed && isInside(event)) listener.onKey(key, event.eventTime)
                    else -> Unit
                }
            }
            MotionEvent.ACTION_CANCEL -> stop()
        }
        return true
    }

    /** Screen readers "click" keys instead of touching them. */
    override fun performClick(): Boolean {
        super.performClick()
        listener.onKey(key, SystemClock.uptimeMillis())
        return true
    }

    override fun performLongClick(): Boolean {
        if (key.kind != KeyKind.SPACE) return super.performLongClick()
        listener.onSpaceLongPress()
        return true
    }

    /** Stops any repeat or long press and lets go of the key. */
    fun stop() {
        removeCallbacks(repeater)
        removeCallbacks(longPress)
        isPressed = false
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        // Centre the label and icon on the visible key, not the whole touch area.
        val shift = (startFraction - endFraction) * width / 2f
        canvas.save()
        canvas.translate(shift, 0f)
        super.onDraw(canvas)
        icon?.let {
            val left = (width - iconSize) / 2
            val top = (height - iconSize) / 2
            it.setBounds(left, top, left + iconSize, top + iconSize)
            it.draw(canvas)
        }
        canvas.restore()
    }

    private val actsOnRelease: Boolean get() = key.kind == KeyKind.ENTER || key.kind == KeyKind.NEXT_KEYBOARD

    private fun isInside(event: MotionEvent): Boolean =
        event.x >= 0 && event.y >= 0 && event.x < width && event.y < height

    /** Draws [base] inset by the gap (and any padding), so keys look apart but have no dead space between them. */
    private class KeyBackground(
        base: Drawable,
        private val inset: Int,
        private val startFraction: Float,
        private val endFraction: Float,
    ) : DrawableWrapper(base) {
        override fun onBoundsChange(bounds: Rect) {
            val width = bounds.width()
            drawable?.setBounds(
                bounds.left + inset + (width * startFraction).toInt(),
                bounds.top + inset,
                bounds.right - inset - (width * endFraction).toInt(),
                bounds.bottom - inset,
            )
        }

        override fun getPadding(padding: Rect): Boolean {
            padding.set(0, 0, 0, 0)
            return false
        }
    }

    companion object {
        private const val REPEAT_START_MILLIS = 400L
        private const val REPEAT_INTERVAL_MILLIS = 50L
    }
}
