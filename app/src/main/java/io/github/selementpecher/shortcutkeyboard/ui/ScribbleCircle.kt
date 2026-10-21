package io.github.selementpecher.shortcutkeyboard.ui

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * A quick pen loop drawn round something to point at it, the way you would circle a word by hand: not quite closed,
 * a little wobbly, finishing just outside where it started. [progress] (0 to 1) is how much of it is drawn so far.
 */
internal class ScribbleCircle(colour: Int, strokePx: Float) : PenStroke(colour, strokePx) {

    private val loop = Path()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        loop.reset()
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val rx = bounds.width() / 2f
        val ry = bounds.height() / 2f
        // Starts at the top left, goes round clockwise, and ends low on the left a little outside the start: a gap.
        val steps = 64
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val angle = Math.toRadians(START_DEGREES + SWEEP_DEGREES * t)
            // The wobble and the overshoot at the end go up and down only, so the loop never reaches wider than its
            // bounds (and so never over the words beside it).
            val wobble = 1f + WOBBLE * sin(t * 9.0).toFloat()
            val grow = 1f + GROW * t
            val x = cx + rx * cos(angle).toFloat()
            val y = cy + ry * grow * wobble * sin(angle).toFloat()
            if (i == 0) loop.moveTo(x, y) else loop.lineTo(x, y)
        }
    }

    override fun draw(canvas: Canvas) {
        if (progress <= 0f) return
        stroke(canvas, loop, progress)
    }


    private companion object {
        const val START_DEGREES = 215.0
        const val SWEEP_DEGREES = 315.0
        const val WOBBLE = 0.03f
        const val GROW = 0.08f
    }
}
