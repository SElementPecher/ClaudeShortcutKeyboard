package io.github.selementpecher.shortcutkeyboard.look

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The one icon family used by the app and the keyboard: simple rounded line icons drawn in code
 * (no image files), so they match each Look's colours and scale to any size.
 */
object Icons {

    enum class Kind { BACK, FORWARD, CLOSE, GEAR, KEYBOARD, PLUS, CHECK, TRASH, FOLDER, BACKSPACE, GLOBE }

    /** An icon in [color], [sizePx] square. Resize with `setBounds` if needed. */
    fun drawable(kind: Kind, color: Int, sizePx: Int): Drawable = IconDrawable(kind, color, sizePx)
}

private class IconDrawable(private val kind: Icons.Kind, color: Int, private val sizePx: Int) : Drawable() {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()).toFloat()
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        stroke.strokeWidth = s * 0.09f
        when (kind) {
            Icons.Kind.BACK -> chevron(canvas, cx, cy, s, -1f)
            Icons.Kind.FORWARD -> chevron(canvas, cx, cy, s, 1f)
            Icons.Kind.CLOSE -> {
                val d = s * 0.25f
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, stroke)
                canvas.drawLine(cx - d, cy + d, cx + d, cy - d, stroke)
            }
            Icons.Kind.PLUS -> {
                val d = s * 0.29f
                canvas.drawLine(cx - d, cy, cx + d, cy, stroke)
                canvas.drawLine(cx, cy - d, cx, cy + d, stroke)
            }
            Icons.Kind.CHECK -> canvas.drawPath(
                Path().apply {
                    moveTo(cx - s * 0.3f, cy + s * 0.02f)
                    lineTo(cx - s * 0.08f, cy + s * 0.24f)
                    lineTo(cx + s * 0.32f, cy - s * 0.22f)
                },
                stroke,
            )
            Icons.Kind.GEAR -> gear(canvas, cx, cy, s)
            Icons.Kind.KEYBOARD -> keyboard(canvas, cx, cy, s)
            Icons.Kind.GLOBE -> globe(canvas, cx, cy, s)
            Icons.Kind.TRASH -> trash(canvas, cx, cy, s)
            Icons.Kind.FOLDER -> folder(canvas, cx, cy, s)
            Icons.Kind.BACKSPACE -> backspace(canvas, cx, cy, s)
        }
    }

    /** A delete key: a tag pointing left with a cross inside. */
    private fun backspace(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        canvas.drawPath(
            Path().apply {
                moveTo(cx - s * 0.42f, cy)
                lineTo(cx - s * 0.2f, cy - s * 0.25f)
                lineTo(cx + s * 0.4f, cy - s * 0.25f)
                lineTo(cx + s * 0.4f, cy + s * 0.25f)
                lineTo(cx - s * 0.2f, cy + s * 0.25f)
                close()
            },
            stroke,
        )
        val d = s * 0.09f
        val mx = cx + s * 0.1f
        canvas.drawLine(mx - d, cy - d, mx + d, cy + d, stroke)
        canvas.drawLine(mx - d, cy + d, mx + d, cy - d, stroke)
    }

    private fun chevron(canvas: Canvas, cx: Float, cy: Float, s: Float, direction: Float) {
        val tip = cx + direction * s * 0.17f
        val back = cx - direction * s * 0.13f
        canvas.drawPath(
            Path().apply {
                moveTo(back, cy - s * 0.3f)
                lineTo(tip, cy)
                lineTo(back, cy + s * 0.3f)
            },
            stroke,
        )
    }

    /** A cog: eight rounded teeth round a ring, with a hole in the middle. */
    private fun gear(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        stroke.strokeWidth = s * 0.08f
        val inner = s * 0.31f
        val outer = s * 0.42f
        val path = Path()
        for (tooth in 0 until 8) {
            val centre = Math.toRadians(45.0 * tooth - 90.0)
            val points = listOf(-17.0 to inner, -10.0 to outer, 10.0 to outer, 17.0 to inner)
            for ((index, point) in points.withIndex()) {
                val angle = centre + Math.toRadians(point.first)
                val x = cx + point.second * cos(angle).toFloat()
                val y = cy + point.second * sin(angle).toFloat()
                if (tooth == 0 && index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        path.close()
        canvas.drawPath(path, stroke)
        canvas.drawCircle(cx, cy, s * 0.14f, stroke)
    }

    /** A globe: a ring, an upright oval and a middle line, as on the phone's keyboard switch key. */
    private fun globe(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val r = s * 0.36f
        canvas.drawCircle(cx, cy, r, stroke)
        canvas.drawOval(RectF(cx - r * 0.5f, cy - r, cx + r * 0.5f, cy + r), stroke)
        canvas.drawLine(cx - r, cy, cx + r, cy, stroke)
    }

    private fun keyboard(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val body = RectF(cx - s * 0.4f, cy - s * 0.25f, cx + s * 0.4f, cy + s * 0.25f)
        canvas.drawRoundRect(body, s * 0.1f, s * 0.1f, stroke)
        val dot = s * 0.035f
        for (i in -2..2) {
            canvas.drawCircle(cx + i * s * 0.15f, cy - s * 0.1f, dot, fill)
        }
        for (i in -1..1) {
            canvas.drawCircle(cx + i * s * 0.15f, cy + s * 0.0f, dot, fill)
        }
        canvas.drawLine(cx - s * 0.15f, cy + s * 0.12f, cx + s * 0.15f, cy + s * 0.12f, stroke.also { it.strokeWidth = s * 0.07f })
    }

    private fun trash(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        canvas.drawLine(cx - s * 0.3f, cy - s * 0.2f, cx + s * 0.3f, cy - s * 0.2f, stroke)
        canvas.drawPath(
            Path().apply {
                moveTo(cx - s * 0.09f, cy - s * 0.2f)
                lineTo(cx - s * 0.09f, cy - s * 0.31f)
                lineTo(cx + s * 0.09f, cy - s * 0.31f)
                lineTo(cx + s * 0.09f, cy - s * 0.2f)
            },
            stroke,
        )
        canvas.drawPath(
            Path().apply {
                moveTo(cx - s * 0.2f, cy - s * 0.1f)
                lineTo(cx - s * 0.17f, cy + s * 0.32f)
                lineTo(cx + s * 0.17f, cy + s * 0.32f)
                lineTo(cx + s * 0.2f, cy - s * 0.1f)
            },
            stroke,
        )
        canvas.drawLine(cx - s * 0.06f, cy, cx - s * 0.06f, cy + s * 0.2f, stroke)
        canvas.drawLine(cx + s * 0.06f, cy, cx + s * 0.06f, cy + s * 0.2f, stroke)
    }

    private fun folder(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val left = cx - s * 0.36f
        val right = cx + s * 0.36f
        canvas.drawPath(
            Path().apply {
                moveTo(left, cy + s * 0.28f)
                lineTo(left, cy - s * 0.24f)
                lineTo(left + s * 0.24f, cy - s * 0.24f)
                lineTo(left + s * 0.34f, cy - s * 0.14f)
                lineTo(right, cy - s * 0.14f)
                lineTo(right, cy + s * 0.28f)
                close()
            },
            stroke,
        )
    }

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
