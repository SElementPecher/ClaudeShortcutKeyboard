package io.github.selementpecher.shortcutkeyboard.ui.editor

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

/**
 * Words laid out left to right and wrapped onto new lines, each line centred vertically.
 * A child can ask for a line break before it ([Params.lineBreakBefore]) or to sit right up
 * against the one before it ([Params.tight]), like a comma after a word.
 */
internal class WordFlow(context: Context, private val gapX: Int, private val gapY: Int) : ViewGroup(context) {

    class Params(width: Int, height: Int) : ViewGroup.LayoutParams(width, height) {
        var tight = false
        var lineBreakBefore = false
    }

    override fun generateDefaultLayoutParams(): LayoutParams = Params(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(attrs: android.util.AttributeSet?): LayoutParams = Params(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = Params(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is Params

    private class Place(val view: View, val x: Int, val line: Int)

    /** Works out where each child goes. Returns the places and the height of every line. */
    private fun arrange(maxWidth: Int, places: MutableList<Place>, lineHeights: MutableList<Int>): Int {
        var x = 0
        var line = 0
        var height = 0
        var widest = 0
        var first = true
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            val params = child.layoutParams as Params
            val w = child.measuredWidth
            val gap = if (first || params.tight || x == 0) 0 else gapX
            val wraps = !first && (params.lineBreakBefore || (x > 0 && x + gap + w > maxWidth))
            if (wraps) {
                lineHeights += height
                line++
                x = 0
                height = 0
            }
            val at = if (x == 0) 0 else x + gap
            places += Place(child, at, line)
            x = at + w
            widest = max(widest, x)
            height = max(height, child.measuredHeight)
            first = false
        }
        lineHeights += height
        return widest
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            measureChild(
                child,
                MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
        }
        val lines = ArrayList<Int>()
        arrange(maxWidth, ArrayList(), lines)
        val height = lines.sum() + gapY * (lines.size - 1).coerceAtLeast(0) + paddingTop + paddingBottom
        setMeasuredDimension(resolveSize(maxWidth + paddingLeft + paddingRight, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val places = ArrayList<Place>()
        val lines = ArrayList<Int>()
        arrange(r - l - paddingLeft - paddingRight, places, lines)
        val tops = IntArray(lines.size)
        var y = paddingTop
        for (i in lines.indices) {
            tops[i] = y
            y += lines[i] + gapY
        }
        for (place in places) {
            val view = place.view
            val top = tops[place.line] + (lines[place.line] - view.measuredHeight) / 2
            val left = paddingLeft + place.x
            view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        }
    }
}
