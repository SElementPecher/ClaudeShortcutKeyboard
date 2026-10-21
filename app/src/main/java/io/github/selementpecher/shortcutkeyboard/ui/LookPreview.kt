package io.github.selementpecher.shortcutkeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.look.Highlighter
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.TileLayout
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf
import kotlin.math.max

/**
 * A small picture of the app's home screen in a [Look]: the same screen for every Look (a "quiKies" header with the
 * gear, then a quiKey, a folder, another quiKey and a New tile), drawn with that Look's own colours, corners, lines,
 * tile layout and paper. The phone's light or dark setting decides which of its two palettes is used.
 * It scales with its width.
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
    private val newLabel = context.getString(R.string.tile_new)
    private val title = context.getString(R.string.app_name)

    /** Everything is drawn in "units": a dp times how much this picture is scaled for its width. */
    private var scale = 1f

    private fun u(v: Float) = v * density * scale

    private fun refresh(widthPx: Int) {
        scale = (widthPx / density / REFERENCE_DP).coerceIn(0.7f, 1.3f)
    }

    private fun textSize() = max(u(11f), 9f * density)

    private fun headerHeight() = u(22f)

    private fun tileHeight() = u(52f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
        refresh(width)
        val height = PAD * 2 * density * scale + headerHeight() + u(8f) + tileHeight() * 2 + u(8f)
        setMeasuredDimension(width, resolveSize(height.toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = u(look.shape.cardRadiusDp.coerceAtMost(18).toFloat())
        paint.style = Paint.Style.FILL
        paint.color = palette.bg
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (look.ruled) drawPaper(canvas, w, h, radius)
        if (look.shape.strokeDp > 0f || look.id == "calm" || look.ruled) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = density
            paint.color = palette.outline
            rect.set(density / 2f, density / 2f, w - density / 2f, h - density / 2f)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        val pad = u(PAD)
        drawHeader(canvas, pad, pad, w - pad)
        val gap = u(8f)
        val tileW = (w - pad * 2 - gap) / 2f
        val tileH = tileHeight()
        val gridTop = pad + headerHeight() + u(8f)
        val tileRadius = u(look.shape.tileRadiusDp * 0.55f)
        // Same four tiles in every Look: a quiKey, a folder, another quiKey, New.
        for (index in 0 until 4) {
            val left = pad + (index % 2) * (tileW + gap)
            val top = gridTop + (index / 2) * (tileH + gap)
            when (index) {
                0 -> drawQuikey(canvas, left, top, tileW, tileH, tileRadius, 0, labels.getOrElse(0) { "" })
                1 -> drawFolder(canvas, left, top, tileW, tileH, tileRadius, labels.getOrElse(2) { "" })
                2 -> drawQuikey(canvas, left, top, tileW, tileH, tileRadius, 1, labels.getOrElse(1) { "" })
                else -> drawNew(canvas, left, top, tileW, tileH, tileRadius)
            }
        }
    }

    /** The title on the left and the gear on the right, as on the real screen. */
    private fun drawHeader(canvas: Canvas, left: Float, top: Float, right: Float) {
        paint.style = Paint.Style.FILL
        paint.typeface = typefaceOf(look.type.headingWeight)
        paint.color = palette.ink
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = max(u(14f), 11f * density)
        canvas.drawText(title, left, top + u(15f), paint)
        val size = u(18f).toInt()
        val gear = Icons.drawable(Icons.Kind.GEAR, palette.ink, size)
        gear.setBounds((right - size).toInt(), (top + u(2f)).toInt(), right.toInt(), (top + u(2f)).toInt() + size)
        gear.draw(canvas)
    }

    private fun tileColors(index: Int) = palette.tiles[index % palette.tiles.size]

    /** The pictures of the three sample tiles, as on the real ones. */
    private fun glyphFor(index: Int, folder: Boolean) = if (folder) GLYPH_FOLDER else if (index == 0) GLYPH_MEDICINE else GLYPH_LATE

    /** A quiKey tile in this Look's style. */
    private fun drawQuikey(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, radius: Float, index: Int, label: String) {
        val colors = tileColors(index)
        if (look.ruled) {
            drawIndexCard(canvas, left, top, w, h, radius, colors.bg, label, folder = false, glyph = glyphFor(index, false))
            return
        }
        rect.set(left, top, left + w, top + h)
        paint.style = Paint.Style.FILL
        paint.color = colors.bg
        canvas.drawRoundRect(rect, radius, radius, paint)
        if (look.shape.strokeDp > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = u(1.2f)
            paint.color = palette.outline
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        drawTileText(canvas, left, top, w, h, colors.fg, label, glyphFor(index, false))
    }

    /** A folder tile: the same, with the little tab on top the real folders have. */
    private fun drawFolder(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, radius: Float, label: String) {
        val colors = palette.folder
        val tab = u(6f)
        if (look.ruled) {
            drawIndexCard(canvas, left, top, w, h, radius, tileColors(2).bg, label, folder = true, glyph = glyphFor(0, true))
            return
        }
        paint.style = Paint.Style.FILL
        paint.color = colors.bg
        rect.set(left, top, left + w * 0.4f, top + tab + radius)
        canvas.drawRoundRect(rect, radius, radius, paint)
        rect.set(left, top + tab, left + w, top + h)
        canvas.drawRoundRect(rect, radius, radius, paint)
        val ring = when {
            look.id == "bold" -> palette.accent
            look.shape.strokeDp > 0f -> palette.outline
            else -> 0
        }
        if (ring != 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (look.id == "bold") u(2f) else u(1.2f)
            paint.color = ring
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        drawTileText(canvas, left, top + tab, w, h - tab, colors.fg, label, glyphFor(0, true))
    }

    /** The dashed New tile. */
    private fun drawNew(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, radius: Float) {
        rect.set(left + u(1f), top + u(1f), left + w - u(1f), top + h - u(1f))
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u(1.6f)
        paint.color = if (look.shape.strokeDp > 0f) palette.outline else palette.inkSoft
        paint.pathEffect = DashPathEffect(floatArrayOf(u(5f), u(4f)), 0f)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        paint.color = palette.inkSoft
        paint.typeface = typefaceOf(look.type.tileWeight)
        paint.textSize = textSize()
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("+ $newLabel", left + w / 2f, top + h / 2f + textSize() / 3f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /**
     * Name and picture inside a tile, fitted like the real tiles: wide Looks have a round picture with the name beside it
     * (two lines if it is long); Squares has a big letter with the name under it. The text shrinks before it is ever cut.
     */
    private fun drawTileText(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, fg: Int, label: String, glyph: String) {
        paint.style = Paint.Style.FILL
        paint.typeface = typefaceOf(look.type.tileWeight)
        if (look.tileLayout == TileLayout.WIDE) {
            val textLeft = drawPicture(canvas, left, top, h, fg, glyph)
            drawFitted(canvas, label, textLeft, top, h, left + w - u(6f) - textLeft, fg, centre = false)
        } else {
            paint.color = fg
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = u(18f)
            canvas.drawText(glyph, left + w / 2f, top + h * 0.5f, paint)
            paint.textAlign = Paint.Align.LEFT
            drawFitted(canvas, label, left + u(4f), top + h * 0.56f, h * 0.4f, w - u(8f), fg, centre = true)
        }
    }

    /** The round picture at the left of a wide tile. Returns where the name starts. */
    private fun drawPicture(canvas: Canvas, left: Float, top: Float, h: Float, fg: Int, glyph: String): Float {
        val circle = u(16f)
        val cx = left + u(8f) + circle / 2f
        val cy = top + h / 2f
        paint.style = Paint.Style.FILL
        paint.color = (fg and 0x00FFFFFF) or (0x26 shl 24)
        canvas.drawCircle(cx, cy, circle / 2f, paint)
        paint.color = fg
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = u(11f)
        canvas.drawText(glyph, cx, cy + u(4f), paint)
        paint.textAlign = Paint.Align.LEFT
        return left + u(8f) + circle + u(6f)
    }

    /**
     * Draws [label] in the area ([x], [areaTop], [maxWidth] wide, [areaHeight] tall): at most two lines, the biggest size
     * (down to a small floor) at which no word is cut. Centred in the area up and down.
     */
    private fun drawFitted(canvas: Canvas, label: String, x: Float, areaTop: Float, areaHeight: Float, maxWidth: Float, colour: Int, centre: Boolean) {
        val tp = TextPaint(paint)
        tp.typeface = typefaceOf(look.type.tileWeight)
        tp.color = colour
        var size = textSize()
        val floor = 7.5f * density
        var layout: StaticLayout
        while (true) {
            tp.textSize = size
            layout = StaticLayout.Builder.obtain(label, 0, label.length, tp, max(1, maxWidth.toInt()))
                .setMaxLines(2)
                .setAlignment(if (centre) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
            val wordsFit = label.split(' ').none { it.isNotEmpty() && tp.measureText(it) > maxWidth }
            val linesFit = layout.lineCount <= 2 && layout.height <= areaHeight
            if ((wordsFit && linesFit) || size <= floor) break
            size -= 0.5f * density
        }
        canvas.save()
        canvas.translate(x, areaTop + (areaHeight - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()
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
        // Start so a rule sits just under the title, instead of running through it.
        var y = u(29f) % gap
        while (y < h) {
            canvas.drawRect(0f, y, w, y + max(1f, density * 0.8f), paint)
            y += gap
        }
        paint.color = (palette.danger and 0x00FFFFFF) or 0x99000000.toInt()
        canvas.drawRect(u(8f), 0f, u(8f) + max(1f, density), h, paint)
        canvas.restore()
    }

    /** A white card with a shadow and a highlighter swipe in [swipe] under the name; a coloured binder tab if a folder. */
    private fun drawIndexCard(canvas: Canvas, left: Float, top: Float, w: Float, h: Float, radius: Float, swipe: Int, label: String, folder: Boolean, glyph: String) {
        val bodyTop = if (folder) top + u(7f) else top
        rect.set(left, bodyTop, left + w, top + h)
        paint.style = Paint.Style.FILL
        paint.color = (palette.ink and 0x00FFFFFF) or 0x30000000
        rect.offset(0f, u(1.6f))
        canvas.drawRoundRect(rect, radius, radius, paint)
        rect.offset(0f, -u(1.6f))
        paint.color = palette.surface
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.typeface = typefaceOf(look.type.tileWeight)
        val textLeft = drawPicture(canvas, left, bodyTop, top + h - bodyTop, palette.ink, glyph)
        if (folder) {
            paint.color = swipe
            rect.set(left + u(6f), top, left + w * 0.5f, bodyTop + u(3f))
            canvas.drawRoundRect(rect, u(3.5f), u(3.5f), paint)
            drawFitted(canvas, label, textLeft, bodyTop, top + h - bodyTop, left + w - u(6f) - textLeft, palette.ink, centre = false)
        } else {
            // The name, and a highlighter swipe under it.
            drawFitted(canvas, label, textLeft, top, h - u(11f), left + w - u(6f) - textLeft, palette.ink, centre = false)
            rect.set(textLeft, top + h - u(10f), left + w - u(10f), top + h - u(4f))
            path.reset()
            Highlighter.addShape(path, rect, u(3f), uneven = true)
            paint.color = swipe
            canvas.drawPath(path, paint)
        }
    }

    private companion object {
        /** The width (in dp) the sizes are drawn for; narrower or wider pictures scale from it. */
        const val REFERENCE_DP = 200f
        const val PAD = 12f
        const val GLYPH_MEDICINE = "💊"
        const val GLYPH_FOLDER = "🧒"
        const val GLYPH_LATE = "⏰"
    }
}
