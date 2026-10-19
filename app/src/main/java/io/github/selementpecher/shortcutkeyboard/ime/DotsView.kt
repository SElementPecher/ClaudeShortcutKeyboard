package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Little dots for "which page" or "which question": the current one is a longer pill in the
 * accent colour, the others are quiet. With [cumulative] the dots already passed are also in the accent colour.
 */
internal class DotsView(context: Context, private val theme: KeyboardTheme) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val dot = theme.dp(DOT_DP).toFloat()
    private val gap = theme.dp(GAP_DP).toFloat()
    private val pill = theme.dp(PILL_DP).toFloat()
    private var count = 0
    private var current = 0
    private var cumulative = false

    fun set(count: Int, current: Int, cumulative: Boolean = false) {
        this.count = count
        this.current = current.coerceIn(0, (count - 1).coerceAtLeast(0))
        this.cumulative = cumulative
        requestLayout()
        invalidate()
    }

    private fun totalWidth(): Float = if (count <= 0) 0f else (count - 1) * (dot + gap) + pill

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wanted = Math.ceil(totalWidth().toDouble()).toInt() + paddingLeft + paddingRight
        val h = theme.dp(PILL_HEIGHT_DP)
        setMeasuredDimension(resolveSize(wanted, widthMeasureSpec), resolveSize(h, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        if (count <= 0) return
        val palette = theme.palette
        val quiet = (palette.inkSoft and 0x00FFFFFF) or 0x59000000
        var x = (width - totalWidth()) / 2f
        val cy = height / 2f
        val half = dot / 2f
        for (i in 0 until count) {
            val isCurrent = i == current
            val w = if (isCurrent) pill else dot
            paint.color = if (isCurrent || (cumulative && i < current)) palette.accent else quiet
            rect.set(x, cy - half, x + w, cy + half)
            canvas.drawRoundRect(rect, half, half, paint)
            x += w + gap
        }
    }

    companion object {
        private const val DOT_DP = 7
        private const val PILL_DP = 18
        private const val GAP_DP = 6
        private const val PILL_HEIGHT_DP = 12
    }
}
