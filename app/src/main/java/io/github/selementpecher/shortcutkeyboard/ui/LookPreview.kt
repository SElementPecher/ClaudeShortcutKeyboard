package io.github.selementpecher.shortcutkeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import io.github.selementpecher.shortcutkeyboard.look.Highlighter
import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.TileLayout
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf
import kotlin.math.max
import kotlin.math.min

/**
 * A small picture of what a [Look] looks like, drawn with that Look's own colours, corners and tile layout
 * (the phone's light or dark setting decides which of its two palettes is used). It scales with its width, and in a
 * small size shows two tiles instead of three. The Exercise book shows ruled paper, index cards and a highlighted sentence.
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
    private val path = Path()

    /** Everything is drawn in "units": a dp times how much this picture is scaled for its width. */
    private var scale = 1f
    private var compact = false

    private fun u(v: Float) = v * density * scale

    private fun refresh(widthPx: Int) {
        val widthDp = widthPx / density
        compact = widthDp < COMPACT_BELOW_DP
        scale = (widthDp / (if (compact) 190f else 300f)).coerceIn(0.7f, 1.3f)
    }

    private val cols get() = if (compact) 2 else 3

    /** How tall a tile is for a picture [w] wide: wide tiles are fixed; square ones follow the width. */
    private fun tileHeight(w: Float): Float {
        if (look.tileLayout == TileLayout.WIDE) return u(60f)
        val tileW = (w - u(12f) * 2 - u(8f) * (cols - 1)) / cols
        return tileW * 0.9f
    }

    /** The second row: a button, or for the ruled Look a sentence with highlighter marks. */
    private fun lowerHeight() = if (look.ruled) u(30f) else u(20f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        refresh(width)
        val height = u(12f) + tileHeight(width.toFloat()) + u(12f) + lowerHeight() + u(12f)
        setMeasuredDimension(width, resolveSize(max(height, u(112f)).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // The "screen" the tiles sit on.
        val screenRadius = u(look.shape.cardRadiusDp.coerceAtMost(18).toFloat())
        paint.style = Paint.Style.FILL
        paint.color = palette.bg
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, screenRadius, screenRadius, paint)
        if (look.ruled) drawPaper(canvas, w, h, screenRadius)
        if (look.shape.strokeDp > 0f || look.id == "calm" || look.ruled) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density
            paint.color = palette.outline
            rect.set(density / 2f, density / 2f, w - density / 2f, h - density / 2f)
            canvas.drawRoundRect(rect, screenRadius, screenRadius, paint)
        }
        val pad = u(12f)
        val gap = u(8f)
        val count = cols
        val tileW = (w - pad * 2 - gap * (count - 1)) / count
        val tileH = tileHeight(w)
        val top = pad
        val radius = u(look.shape.tileRadiusDp * 0.55f)
        for (i in 0 until count) {
            val left = pad + i * (tileW + gap)
            val isFolder = i == count - 1
            val colors = if (isFolder) palette.folder else palette.tiles[i % palette.tiles.size]
            if (look.ruled) {
                drawIndexCard(canvas, left, top, tileW, tileH, radius, i, isFolder, labels.getOrElse(i) { "" })
                continue
            }
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
                paint.strokeWidth = if (look.id == "bold") u(2f) else u(1.2f)
                paint.color = ring
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            drawTile(canvas, left, top, tileW, tileH, colors.fg, labels.getOrElse(i) { "" }, isFolder)
        }
        val lowTop = top + tileH + u(12f)
        if (look.ruled) drawSentence(canvas, pad, lowTop, w - pad, h - pad) else drawButton(canvas, w, h, pad, lowTop)
    }

    /** A button, to show the accent and button shape. */
    private fun drawButton(canvas: Canvas, w: Float, h: Float, pad: Float, lowTop: Float) {
        val btnH = (h - pad - lowTop).coerceAtLeast(u(14f)).coerceAtMost(u(24f))
        rect.set(pad, h - pad - btnH, w - pad, h - pad)
        paint.style = Paint.Style.FILL
        paint.color = palette.accent
        val br = if (look.shape.buttonRadiusDp >= 100) btnH / 2f else u(look.shape.buttonRadiusDp * 0.6f)
        canvas.drawRoundRect(rect, br, br, paint)
        paint.color = palette.onAccent
        rect.set(w / 2f - u(22f), rect.centerY() - u(2f), w / 2f + u(22f), rect.centerY() + u(2f))
        canvas.drawRoundRect(rect, u(2f), u(2f), paint)
    }

    private fun drawTile(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, fg: Int, label: String, folder: Boolean) {
        paint.style = Paint.Style.FILL
        paint.typeface = typefaceOf(look.type.tileWeight)
        paint.color = fg
        paint.textSize = textSize()
        val name = shortName(label, w)
        val initial = (label.firstOrNull() ?: '?').uppercaseChar().toString()
        if (look.tileLayout == TileLayout.WIDE) {
            val circle = u(20f)
            val cx = left + u(9f) + circle / 2f
            val cy = top + u(8f) + circle / 2f
            paint.color = (fg and 0x00FFFFFF) or (0x26 shl 24)
            canvas.drawCircle(cx, cy, circle / 2f, paint)
            paint.color = fg
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = textSize()
            canvas.drawText(if (folder) "▤" else initial, cx, cy + u(4f), paint)
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(name, left + u(9f), top + h - u(8f), paint)
        } else {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = u(22f)
            canvas.drawText(if (folder) "▤" else initial, left + w / 2f, top + h * 0.5f, paint)
            paint.textSize = textSize()
            canvas.drawText(name, left + w / 2f, top + h - u(9f), paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }

    private fun textSize() = max(u(11f), 9f * density)

    /** The label, shortened with a dot dot dot until it fits [w]. */
    private fun shortName(label: String, w: Float): String {
        paint.textSize = textSize()
        var name = label
        while (name.length > 2 && paint.measureText(name) > w - u(14f)) name = name.dropLast(1).trimEnd()
        return if (name.length < label.length) "$name…" else name
    }

    // ---- the Exercise book ----

    /** Blue rules across and a red margin down the left, inside the rounded page. */
    private fun drawPaper(canvas: Canvas, w: Float, h: Float, radius: Float) {
        canvas.save()
        path.reset()
        rect.set(0f, 0f, w, h)
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(path)
        paint.style = Paint.Style.FILL
        paint.color = palette.outline
        val gap = u(14f)
        var y = gap
        while (y < h) {
            canvas.drawRect(0f, y, w, y + max(1f, density * 0.8f), paint)
            y += gap
        }
        paint.color = (palette.danger and 0x00FFFFFF) or 0x99000000.toInt()
        canvas.drawRect(u(8f), 0f, u(8f) + max(1f, density), h, paint)
        canvas.restore()
    }

    /** A white card with a highlighter swipe in the tile's colour (a binder tab on the folder). */
    private fun drawIndexCard(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, radius: Float, index: Int, folder: Boolean, label: String) {
        val colors = palette.tiles[index % palette.tiles.size]
        val bodyTop = if (folder) top + u(8f) else top
        rect.set(left, bodyTop, left + w, top + h)
        paint.style = Paint.Style.FILL
        // A soft shadow so the white card stands off the page.
        paint.color = (palette.ink and 0x00FFFFFF) or 0x30000000
        rect.offset(0f, u(1.6f))
        canvas.drawRoundRect(rect, radius, radius, paint)
        rect.offset(0f, -u(1.6f))
        paint.color = palette.surface
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (folder) {
            paint.color = colors.bg
            rect.set(left + u(6f), top, left + w * 0.5f, bodyTop + u(3f))
            canvas.drawRoundRect(rect, u(3.5f), u(3.5f), paint)
        } else {
            // The swipe under the name.
            rect.set(left + u(9f), top + h - u(13f), left + w * 0.7f, top + h - u(5f))
            path.reset()
            Highlighter.addShape(path, rect, u(3f), uneven = true)
            paint.color = colors.bg
            canvas.drawPath(path, paint)
        }
        paint.typeface = typefaceOf(look.type.tileWeight)
        paint.textSize = textSize()
        paint.color = palette.ink
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(shortName(label, w), left + u(9f), bodyTop + u(20f), paint)
    }

    /** "Sam, Calpol" with the two names on highlighter marks, like the changing parts in a message. */
    private fun drawSentence(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        paint.typeface = typefaceOf(600)
        paint.textSize = textSize()
        val baseline = bottom - u(8f)
        var x = left + u(4f)
        val marks = listOf("Sam" to palette.tiles[1].bg, "Calpol" to palette.tiles[2].bg)
        val joiner = " and "
        for ((index, mark) in marks.withIndex()) {
            val text = mark.first
            val width = paint.measureText(text)
            if (x + width + u(8f) > right) break
            rect.set(x - u(3f), baseline - textSize() - u(1f), x + width + u(3f), baseline + u(4f))
            path.reset()
            Highlighter.addShape(path, rect, u(4f), uneven = true)
            paint.style = Paint.Style.FILL
            paint.color = mark.second
            canvas.drawPath(path, paint)
            paint.color = palette.ink
            canvas.drawText(text, x, baseline, paint)
            x += width + u(6f)
            if (index == 0) {
                canvas.drawText(joiner, x, baseline, paint)
                x += paint.measureText(joiner)
            }
        }
    }

    private companion object {
        /** Narrower than this (in dp), the picture shows two tiles and scales to a smaller reference. */
        const val COMPACT_BELOW_DP = 230f
    }
}
