package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/** Lays children out left to right and wraps onto a new line when a line is full, like words in a sentence. */
internal class FlowLayout(context: Context) : ViewGroup(context) {

    var horizontalSpacing: Int = 0
        set(value) {
            field = value
            requestLayout()
        }

    var verticalSpacing: Int = 0
        set(value) {
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val maxLineWidth = if (widthMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else widthSize - paddingLeft - paddingRight
        val childWidthSpec = if (widthMode == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        } else {
            MeasureSpec.makeMeasureSpec(max(0, maxLineWidth), MeasureSpec.AT_MOST)
        }
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        var lineWidth = 0
        var lineHeight = 0
        var widest = 0
        var height = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            child.measure(childWidthSpec, childHeightSpec)
            val w = child.measuredWidth
            if (lineWidth > 0 && lineWidth + horizontalSpacing + w > maxLineWidth) {
                height += lineHeight + verticalSpacing
                lineWidth = 0
                lineHeight = 0
            }
            lineWidth += (if (lineWidth > 0) horizontalSpacing else 0) + w
            lineHeight = max(lineHeight, child.measuredHeight)
            widest = max(widest, lineWidth)
        }
        height += lineHeight
        setMeasuredDimension(
            resolveSize(widest + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(height + paddingTop + paddingBottom, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxLineWidth = r - l - paddingLeft - paddingRight
        val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
        var x = 0
        var y = paddingTop
        var lineHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val w = child.measuredWidth
            val h = child.measuredHeight
            if (x > 0 && x + horizontalSpacing + w > maxLineWidth) {
                y += lineHeight + verticalSpacing
                x = 0
                lineHeight = 0
            }
            if (x > 0) x += horizontalSpacing
            val left = if (rtl) r - l - paddingRight - x - w else paddingLeft + x
            child.layout(left, y, left + w, y + h)
            x += w
            lineHeight = max(lineHeight, h)
        }
    }
}
