package io.github.selementpecher.shortcutkeyboard.ui.editor

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.selementpecher.shortcutkeyboard.ui.Motion

/**
 * Moving the lines of a list: press a line's handle and drag. The line follows the finger and the others slide out
 * of its way; when the finger lifts, [onMoved] is told where the line came from and where it went (only if it moved).
 * The views are put back as they were: the caller draws the new order.
 */
internal class LineReorder(
    private val rows: LinearLayout,
    private val liftPx: Float,
    private val onMoved: (from: Int, to: Int) -> Unit,
) {
    private var dragged: View? = null
    private var from = -1
    private var to = -1
    private var startY = 0f

    /** Lets [row] be dragged by [handle]. */
    @SuppressLint("ClickableViewAccessibility")
    fun attach(handle: View, row: View) {
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // The page must not scroll while a line is being moved.
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    start(row, event.rawY)
                }
                MotionEvent.ACTION_MOVE -> move(event.rawY)
                MotionEvent.ACTION_UP -> finish(drop = true)
                MotionEvent.ACTION_CANCEL -> finish(drop = false)
            }
            true
        }
    }

    private fun start(row: View, y: Float) {
        from = rows.indexOfChild(row)
        if (from < 0) return
        to = from
        startY = y
        dragged = row
        row.translationZ = liftPx
        row.scaleX = LIFT_SCALE
        row.scaleY = LIFT_SCALE
        row.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    /** From the top of one line to the top of the next. */
    private fun stride(row: View): Int {
        val margins = row.layoutParams as? ViewGroup.MarginLayoutParams
        return row.height + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)
    }

    private fun move(y: Float) {
        val row = dragged ?: return
        val step = stride(row)
        if (step <= 0) return
        // The line stays between the first and the last place.
        val dy = (y - startY).coerceIn((rows.getChildAt(0).top - row.top).toFloat(), (rows.getChildAt(rows.childCount - 1).top - row.top).toFloat())
        row.translationY = dy
        val target = (from + Math.round(dy / step)).coerceIn(0, rows.childCount - 1)
        if (target == to) return
        to = target
        row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        val animate = Motion.enabled(row.context)
        for (i in 0 until rows.childCount) {
            val other = rows.getChildAt(i)
            if (other === row) continue
            val shift = when (i) {
                in from + 1..to -> -step
                in to until from -> step
                else -> 0
            }.toFloat()
            if (animate) other.animate().translationY(shift).setDuration(SLIDE_MS).start() else other.translationY = shift
        }
    }

    private fun finish(drop: Boolean) {
        val row = dragged ?: return
        dragged = null
        for (i in 0 until rows.childCount) {
            rows.getChildAt(i).apply {
                animate().cancel()
                translationY = 0f
            }
        }
        row.translationZ = 0f
        row.scaleX = 1f
        row.scaleY = 1f
        if (drop && to != from) onMoved(from, to)
    }

    private companion object {
        const val SLIDE_MS = 120L
        const val LIFT_SCALE = 1.02f
    }
}
