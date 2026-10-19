package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R

/**
 * The whole keyboard: one fixed-height area that shows the grid, a question, or the check screen,
 * plus the ghost tile that follows the finger while dragging. Everything is laid out in code.
 */
internal class KeyboardView(context: Context, private val compact: Boolean) : FrameLayout(context) {

    private val content = FrameLayout(context)
    private val ghost = TileView(context).apply { visibility = GONE; alpha = 0.85f; scaleX = 1.08f; scaleY = 1.08f }
    private val rootPosition = IntArray(2)
    private var gridScreen: GridScreen? = null

    var gridDragCallbacks: TileGrid.DragCallbacks? = null
        set(value) {
            field = value
            gridScreen?.grid?.dragCallbacks = value
        }

    private val topBarHeight = px(if (compact) R.dimen.kb_top_bar_height_compact else R.dimen.kb_top_bar_height)
    private val stripHeight = px(if (compact) R.dimen.kb_strip_height_compact else R.dimen.kb_strip_height)
    private val gap = px(R.dimen.kb_tile_gap)
    private val padding = px(R.dimen.kb_padding)
    private val tileTextRes = if (compact) R.dimen.kb_tile_text_size_compact else R.dimen.kb_tile_text_size

    init {
        setBackgroundColor(context.getColor(R.color.kb_background))
        val height = px(if (compact) R.dimen.kb_panel_height_compact else R.dimen.kb_panel_height)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, height))
        addView(ghost, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        setOnApplyWindowInsetsListener { view, insets -> applyInsets(view, insets) }
    }

    // ---- Screens ----

    fun showGrid(model: GridModel) {
        val screen = gridScreen ?: GridScreen().also {
            gridScreen = it
            it.grid.dragCallbacks = gridDragCallbacks
        }
        if (screen.root.parent !== content) {
            content.removeAllViews()
            content.addView(screen.root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        screen.bind(model)
    }

    fun showStep(model: StepModel) {
        content.removeAllViews()
        val column = column()
        val numberValue = (model.body as? StepBody.Number)?.value
        val questionText = if (numberValue != null && compact) "${model.question}  $numberValue" else model.question

        column.addView(stepTopBar(model, if (compact) questionText else null), LinearLayout.LayoutParams(MATCH, topBarHeight))
        if (!compact) column.addView(questionRow(model.question, numberValue), LinearLayout.LayoutParams(MATCH, px(R.dimen.kb_question_height)))
        when (val body = model.body) {
            is StepBody.Tiles -> addTilesBody(column, body)
            is StepBody.Number -> addNumberBody(column, body)
            is StepBody.Adjust -> addAdjustBody(column, body)
        }
        content.addView(column, LayoutParams(MATCH, MATCH))
    }

    fun showCheck(model: CheckModel) {
        content.removeAllViews()
        val column = column()
        val top = row()
        top.addView(buttonTile("◀", getString(R.string.kb_desc_back), model.onBack), LinearLayout.LayoutParams(px(R.dimen.kb_back_width), MATCH))
        top.addView(
            label(model.shortcutName, R.dimen.kb_bar_text_size, bold = true, gravity = Gravity.CENTER),
            LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(gap, 0, gap, 0) },
        )
        top.addView(buttonTile("✕", getString(R.string.kb_desc_close), model.onCancel), LinearLayout.LayoutParams(px(R.dimen.kb_back_width), MATCH))
        column.addView(top, LinearLayout.LayoutParams(MATCH, topBarHeight))

        val card = ScrollView(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = resources.getDimension(R.dimen.kb_tile_radius)
                setColor(context.getColor(R.color.kb_tile_folder))
            }
            isFillViewport = true
        }
        val message = label(model.text, R.dimen.kb_check_text_size, bold = true, gravity = Gravity.CENTER_VERTICAL or Gravity.START).apply {
            setTextColor(context.getColor(R.color.kb_tile_folder_text))
            maxLines = Int.MAX_VALUE
            ellipsize = null
            setPadding(padding * 2, padding, padding * 2, padding)
        }
        card.addView(message, ViewGroup.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.contentDescription = model.text
        column.addView(card, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap, 0, gap) })

        val add = TileView(context)
        add.bind(
            TileSpec(getString(R.string.kb_add), TileLook(TileLook.Kind.OK), onClick = model.onAdd, textSizeRes = R.dimen.kb_big_text_size_compact),
            tileTextRes,
        )
        column.addView(add, LinearLayout.LayoutParams(MATCH, px(if (compact) R.dimen.kb_check_tile_height_compact else R.dimen.kb_check_tile_height)))
        content.addView(column, LayoutParams(MATCH, MATCH))
    }

    // ---- Drag ghost ----

    fun showGhost(spec: TileSpec, width: Int, height: Int, rawX: Float, rawY: Float) {
        ghost.layoutParams = LayoutParams(width, height)
        ghost.bind(spec, tileTextRes)
        ghost.visibility = VISIBLE
        moveGhost(rawX, rawY)
    }

    fun moveGhost(rawX: Float, rawY: Float) {
        getLocationOnScreen(rootPosition)
        val w = ghost.layoutParams.width
        val h = ghost.layoutParams.height
        ghost.translationX = rawX - rootPosition[0] - w / 2f
        ghost.translationY = rawY - rootPosition[1] - h / 2f
    }

    fun hideGhost() {
        ghost.visibility = GONE
    }

    /** The grid's tile in [slot], for sizing the ghost. Null when the grid is not on screen. */
    fun gridTile(slot: Int): TileView? = gridScreen?.grid?.tileAt(slot)

    // ---- Grid screen ----

    private inner class GridScreen {
        val root = column()
        val grid = TileGrid(context)
        private val backTile = TileView(context)
        private val title = label("", R.dimen.kb_question_text_size, bold = true, gravity = Gravity.CENTER_VERTICAL or Gravity.START)
        private val pageText = label("", R.dimen.kb_bar_text_size, bold = true, gravity = Gravity.CENTER)
        private val abc = TileView(context)
        private val previous = TileView(context)
        private val next = TileView(context)
        private val settings = TileView(context)
        private val backPosition = IntArray(2)

        init {
            val top = row()
            top.addView(backTile, LinearLayout.LayoutParams(px(R.dimen.kb_back_width), MATCH).apply { marginEnd = gap })
            top.addView(title, LinearLayout.LayoutParams(0, MATCH, 1f))
            top.addView(pageText, LinearLayout.LayoutParams(WRAP, MATCH).apply { marginStart = gap })
            title.ellipsize = TextUtils.TruncateAt.START
            title.isSingleLine = true
            root.addView(top, LinearLayout.LayoutParams(MATCH, topBarHeight))

            grid.setLayoutShape(if (compact) 3 else 2, if (compact) 2 else 3, tileTextRes)
            grid.isOverBack = { x, y -> isOver(backTile, x, y) }
            root.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap / 2) })

            val strip = row()
            strip.addView(abc, LinearLayout.LayoutParams(0, MATCH, 3f))
            strip.addView(previous, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginStart = gap })
            strip.addView(next, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginStart = gap })
            strip.addView(settings, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginStart = gap })
            root.addView(strip, LinearLayout.LayoutParams(MATCH, stripHeight))
        }

        fun bind(model: GridModel) {
            backTile.visibility = if (model.inFolder) VISIBLE else GONE
            backTile.bind(
                TileSpec("◀", TileLook(TileLook.Kind.SPECIAL, highlighted = model.backHighlighted),
                    description = getString(R.string.kb_desc_up), onClick = model.onBack),
                tileTextRes,
            )
            title.text = if (model.inFolder) model.pathText else getString(R.string.kb_title)
            title.contentDescription = title.text
            val paged = model.pageCount > 1
            pageText.visibility = if (paged) VISIBLE else GONE
            pageText.text = getString(R.string.kb_page_indicator, model.page + 1, model.pageCount)
            grid.bind(model.tiles)

            abc.bind(
                TileSpec(getString(R.string.kb_abc), TileLook(TileLook.Kind.SPECIAL), getString(R.string.kb_desc_abc), onClick = model.onAbc),
                tileTextRes,
            )
            previous.visibility = if (paged) VISIBLE else GONE
            next.visibility = if (paged) VISIBLE else GONE
            previous.bind(
                TileSpec("◀", TileLook(TileLook.Kind.SPECIAL), getString(R.string.kb_desc_prev_page), enabled = model.page > 0, onClick = model.onPrevious),
                tileTextRes,
            )
            next.bind(
                TileSpec("▶", TileLook(TileLook.Kind.SPECIAL), getString(R.string.kb_desc_next_page), enabled = model.page < model.pageCount - 1, onClick = model.onNext),
                tileTextRes,
            )
            settings.bind(
                TileSpec("⚙", TileLook(TileLook.Kind.SPECIAL), getString(R.string.kb_desc_open_app), onClick = model.onSettings),
                tileTextRes,
            )
        }

        private fun isOver(view: View, rawX: Float, rawY: Float): Boolean {
            if (view.visibility != VISIBLE) return false
            view.getLocationOnScreen(backPosition)
            return rawX >= backPosition[0] && rawX <= backPosition[0] + view.width &&
                rawY >= backPosition[1] && rawY <= backPosition[1] + view.height
        }
    }

    // ---- Question screens ----

    private fun stepTopBar(model: StepModel, compactTitle: String?): View {
        val top = row()
        top.addView(buttonTile("◀", getString(R.string.kb_desc_back), model.onBack), LinearLayout.LayoutParams(px(R.dimen.kb_back_width), MATCH))
        val middle = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val dots = dotsText(model.step, model.steps)
        if (compactTitle != null) {
            middle.addView(label(compactTitle, R.dimen.kb_tile_text_size_compact, bold = true, gravity = Gravity.CENTER), LinearLayout.LayoutParams(MATCH, 0, 1f))
            middle.addView(label(dots, R.dimen.kb_small_tile_text_size, bold = false, gravity = Gravity.CENTER), LinearLayout.LayoutParams(MATCH, WRAP))
        } else {
            middle.addView(label(model.shortcutName, R.dimen.kb_bar_text_size, bold = true, gravity = Gravity.CENTER), LinearLayout.LayoutParams(MATCH, WRAP))
            middle.addView(label(dots, R.dimen.kb_small_tile_text_size, bold = false, gravity = Gravity.CENTER), LinearLayout.LayoutParams(MATCH, WRAP))
        }
        middle.contentDescription = getString(R.string.kb_desc_step, model.step + 1, model.steps)
        top.addView(middle, LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(gap, 0, gap, 0) })
        top.addView(buttonTile("✕", getString(R.string.kb_desc_close), model.onCancel), LinearLayout.LayoutParams(px(R.dimen.kb_back_width), MATCH))
        return top
    }

    private fun dotsText(step: Int, steps: Int): String =
        if (steps > MAX_DOTS) "${step + 1} / $steps" else (0 until steps).joinToString(" ") { if (it <= step) "●" else "○" }

    private fun questionRow(question: String, value: String?): View {
        val row = row()
        row.addView(label(question, R.dimen.kb_question_text_size, bold = true, gravity = Gravity.CENTER_VERTICAL or Gravity.START), LinearLayout.LayoutParams(0, MATCH, 1f))
        if (value != null) {
            val shown = label(value, R.dimen.kb_question_text_size, bold = true, gravity = Gravity.CENTER_VERTICAL or Gravity.END)
            shown.contentDescription = value
            row.addView(shown, LinearLayout.LayoutParams(WRAP, MATCH))
        }
        return row
    }

    private fun addTilesBody(column: LinearLayout, body: StepBody.Tiles) {
        if (body.hint != null) {
            column.addView(label(body.hint, R.dimen.kb_bar_text_size, bold = false, gravity = Gravity.CENTER_VERTICAL), LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val grid = TileGrid(context)
        grid.setLayoutShape(if (compact) 3 else 2, if (compact) 2 else 3, tileTextRes)
        grid.bind(body.tiles)
        column.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap / 2) })
        if (body.pageCount > 1) {
            val strip = row()
            strip.addView(
                buttonTile("◀", getString(R.string.kb_desc_prev_page), body.onPrevious, enabled = body.page > 0),
                LinearLayout.LayoutParams(0, MATCH, 1f),
            )
            strip.addView(
                label(getString(R.string.kb_page_indicator, body.page + 1, body.pageCount), R.dimen.kb_bar_text_size, bold = true, gravity = Gravity.CENTER),
                LinearLayout.LayoutParams(0, MATCH, 1f),
            )
            strip.addView(
                buttonTile("▶", getString(R.string.kb_desc_next_page), body.onNext, enabled = body.page < body.pageCount - 1),
                LinearLayout.LayoutParams(0, MATCH, 1f),
            )
            column.addView(strip, LinearLayout.LayoutParams(MATCH, stripHeight))
        }
    }

    private fun addNumberBody(column: LinearLayout, body: StepBody.Number) {
        val keys = listOf("1", "2", "3", "⌫", "4", "5", "6", ".", "7", "8", "9", "0")
        val pad = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 3) {
            val line = row()
            for (c in 0 until 4) {
                val key = keys[r * 4 + c]
                val spec = TileSpec(
                    key,
                    TileLook(TileLook.Kind.SPECIAL),
                    description = when (key) {
                        "⌫" -> getString(R.string.kb_desc_delete_digit)
                        "." -> getString(R.string.kb_desc_point)
                        else -> key
                    },
                    textSizeRes = R.dimen.kb_pad_text_size,
                    onClick = if (key == "⌫") body.onBackspace else ({ body.onKey(key[0]) }),
                )
                line.addView(TileView(context).also { it.bind(spec, tileTextRes) }, LinearLayout.LayoutParams(0, MATCH, 1f).apply { if (c > 0) marginStart = gap })
            }
            pad.addView(line, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { if (r > 0) topMargin = gap })
        }
        val ok = TileView(context).also {
            it.bind(TileSpec(getString(R.string.kb_ok), TileLook(TileLook.Kind.OK), onClick = body.onOk, textSizeRes = R.dimen.kb_pad_text_size), tileTextRes)
        }
        val holder = if (compact) {
            // Short screen: OK sits beside the pad so every tile stays big.
            row().also {
                it.addView(pad, LinearLayout.LayoutParams(0, MATCH, 4f))
                it.addView(ok, LinearLayout.LayoutParams(0, MATCH, 1.3f).apply { marginStart = gap })
            }
        } else {
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(pad, LinearLayout.LayoutParams(MATCH, 0, 3f))
                addView(ok, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = gap })
            }
        }
        column.addView(holder, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap / 2) })
    }

    private fun addAdjustBody(column: LinearLayout, body: StepBody.Adjust) {
        val valueArea = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val big = label(
            body.value,
            if (compact) R.dimen.kb_big_text_size_compact else R.dimen.kb_big_text_size,
            bold = true,
            gravity = Gravity.CENTER,
        )
        valueArea.addView(big, LinearLayout.LayoutParams(MATCH, WRAP))
        if (body.later != null) {
            valueArea.addView(label(body.later, R.dimen.kb_bar_text_size, bold = false, gravity = Gravity.CENTER), LinearLayout.LayoutParams(MATCH, WRAP))
        }
        column.addView(valueArea, LinearLayout.LayoutParams(MATCH, 0, 1.1f))

        val buttons = row()
        body.buttons.forEachIndexed { i, spec ->
            buttons.addView(TileView(context).also { it.bind(spec, tileTextRes) }, LinearLayout.LayoutParams(0, MATCH, 1f).apply { if (i > 0) marginStart = gap })
        }
        column.addView(buttons, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap / 2) })
        column.addView(TileView(context).also { it.bind(body.ok, tileTextRes) }, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap / 2) })
    }

    // ---- Small helpers ----

    private fun column() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(padding, padding / 2, padding, padding / 2)
    }

    private fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun buttonTile(text: String, description: String, onClick: () -> Unit, enabled: Boolean = true): TileView =
        TileView(context).also {
            it.bind(TileSpec(text, TileLook(TileLook.Kind.SPECIAL), description, enabled = enabled, onClick = onClick), tileTextRes)
        }

    private fun label(text: String, sizeRes: Int, bold: Boolean, gravity: Int): TextView = TextView(context).apply {
        this.text = text
        this.gravity = gravity
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(sizeRes))
        setTextColor(context.getColor(R.color.kb_key_text))
        setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun px(res: Int): Int = resources.getDimensionPixelSize(res)

    private fun getString(res: Int, vararg args: Any): String = context.getString(res, *args)

    /** Keeps the tiles clear of the navigation bar when the keyboard is drawn behind it. */
    private fun applyInsets(view: View, insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val nav = insets.getInsets(WindowInsets.Type.navigationBars())
            view.setPadding(nav.left, 0, nav.right, nav.bottom)
        } else {
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, 0, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        return insets
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val MAX_DOTS = 10
    }
}
