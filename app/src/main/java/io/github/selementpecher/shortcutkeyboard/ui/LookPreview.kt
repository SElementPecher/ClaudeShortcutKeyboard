package io.github.selementpecher.shortcutkeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.TileLayout
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf

/**
 * A small picture of what a [Look] looks like, drawn with that Look's own colours, corners and tile layout
 * (the phone's light or dark setting decides which of its two palettes is used).
 */
internal class LookPreview(
    context: Context,
    private val look: Look,
    private val labels: List<String>,
) : View(context) {

    private val palette = look.palette(context)
    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun dp(v: Float) = v * density

    /** How tall a tile is for a picture [w] wide: wide tiles are fixed; square ones follow the width. */
    private fun tileHeight(w: Float): Float {
        if (look.tileLayout == TileLayout.WIDE) return dp(60f)
        val tileW = (w - dp(12f) * 2 - dp(8f) * 2) / 3
        return tileW * 0.9f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        // Room for the tiles, a gap and a button, so the button never covers the tiles.
        val height = dp(12f) + tileHeight(width.toFloat()) + dp(12f) + dp(20f) + dp(12f)
        setMeasuredDimension(width, resolveSize(maxOf(height, dp(112f)).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // The "screen" the tiles sit on.
        val screenRadius = dp(look.shape.cardRadiusDp.coerceAtMost(18).toFloat())
        paint.style = Paint.Style.FILL
        paint.color = palette.bg
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, screenRadius, screenRadius, paint)
        if (look.shape.strokeDp > 0f || look.id == "calm") {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1f)
            paint.color = palette.outline
            rect.set(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f))
            canvas.drawRoundRect(rect, screenRadius, screenRadius, paint)
        }
        val pad = dp(12f)
        val gap = dp(8f)
        val cols = 3
        val tileW = (w - pad * 2 - gap * (cols - 1)) / cols
        val tileH = tileHeight(w)
        val top = pad
        val radius = dp(look.shape.tileRadiusDp * 0.55f)
        for (i in 0 until cols) {
            val left = pad + i * (tileW + gap)
            val isFolder = i == cols - 1
            val colors = if (isFolder) palette.folder else palette.tiles[i % palette.tiles.size]
            rect.set(left, top, left + tileW, top + tileH)
            paint.style = Paint.Style.FILL
            paint.color = colors.bg
            canvas.drawRoundRect(rect, radius, radius, paint)
            val ring = when {
                isFolder && look.id == "bold" -> palette.accent
                look.shape.strokeDp > 0f -> palette.outline
                else -> 0
            }
            if (ring != 0) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (look.id == "bold") dp(2f) else dp(1.2f)
                paint.color = ring
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            drawTile(canvas, left, top, tileW, tileH, colors.fg, labels.getOrElse(i) { "" }, isFolder)
        }
        // A button, to show the accent and button shape.
        val btnTop = top + tileH + dp(12f)
        val btnH = (h - pad - btnTop).coerceAtLeast(dp(14f)).coerceAtMost(dp(24f))
        rect.set(pad, h - pad - btnH, w - pad, h - pad)
        paint.style = Paint.Style.FILL
        paint.color = palette.accent
        val br = if (look.shape.buttonRadiusDp >= 100) btnH / 2f else dp(look.shape.buttonRadiusDp * 0.6f)
        canvas.drawRoundRect(rect, br, br, paint)
        paint.color = palette.onAccent
        rect.set(w / 2f - dp(22f), rect.centerY() - dp(2f), w / 2f + dp(22f), rect.centerY() + dp(2f))
        canvas.drawRoundRect(rect, dp(2f), dp(2f), paint)
    }

    private fun drawTile(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, fg: Int, label: String, folder: Boolean) {
        paint.style = Paint.Style.FILL
        paint.typeface = typefaceOf(look.type.tileWeight)
        paint.color = fg
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        val name = if (label.length > 9) label.take(8) + "…" else label
        val initial = (label.firstOrNull() ?: '?').uppercaseChar().toString()
        if (look.tileLayout == TileLayout.WIDE) {
            val circle = dp(20f)
            val cx = left + dp(9f) + circle / 2f
            val cy = top + dp(8f) + circle / 2f
            paint.color = (fg and 0x00FFFFFF) or (0x26 shl 24)
            canvas.drawCircle(cx, cy, circle / 2f, paint)
            paint.color = fg
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(11f)
            canvas.drawText(if (folder) "▤" else initial, cx, cy + dp(4f), paint)
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
            canvas.drawText(name, left + dp(9f), top + h - dp(8f), paint)
        } else {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = dp(22f)
            canvas.drawText(if (folder) "▤" else initial, left + w / 2f, top + h * 0.5f, paint)
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
            canvas.drawText(name, left + w / 2f, top + h - dp(9f), paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }
}
