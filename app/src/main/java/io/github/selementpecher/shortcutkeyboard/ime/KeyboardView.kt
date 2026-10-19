package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
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
import io.github.selementpecher.shortcutkeyboard.look.Look

/**
 * The whole keyboard: one fixed-height area that shows the grid, a question, or the check screen,
 * plus the ghost tile that follows the finger while dragging. Everything is laid out in code
 * from the current [Look].
 */
internal class KeyboardView(context: Context, private val compact: Boolean, val look: Look) : FrameLayout(context) {

    private val theme = KeyboardTheme(context, look, compact)
    private val palette = theme.palette
    private val content = FrameLayout(context)
    private val ghost = TileView(context, theme).apply { visibility = GONE }
    private val rootPosition = IntArray(2)
    private var gridScreen: GridScreen? = null

    var gridDragCallbacks: TileGrid.DragCallbacks? = null
        set(value) {
            field = value
            gridScreen?.grid?.dragCallbacks = value
        }

    private val topBarHeight = px(R.dimen.kb_top_bar_height)
    private val stripHeight = px(if (compact) R.dimen.kb_strip_height_compact else R.dimen.kb_strip_height)
    private val buttonWidth = px(R.dimen.kb_button_width)
    private val gap = theme.dp(TileGrid.TILE_GAP_DP)
    private val padding = px(R.dimen.kb_padding)

    init {
        // The panel colour also fills the padding under the tiles, so the safety gap just looks like space.
        setBackgroundColor(palette.bg)
        clipChildren = false
        content.clipChildren = false
        val height = px(if (compact) R.dimen.kb_panel_height_compact else R.dimen.kb_panel_height)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, height))
        addView(ghost, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        setBottomPadding(0, 0, 0)
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
        top.addView(buttonTile("◀", getString(R.string.kb_desc_back), model.onBack), LinearLayout.LayoutParams(buttonWidth, MATCH))
        top.addView(
            label(model.shortcutName, 17f, heading = true, gravity = Gravity.CENTER),
            LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(gap, 0, gap, 0) },
        )
        top.addView(buttonTile("✕", getString(R.string.kb_desc_close), model.onCancel), LinearLayout.LayoutParams(buttonWidth, MATCH))
        column.addView(top, LinearLayout.LayoutParams(MATCH, topBarHeight))

        val card = ScrollView(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = theme.cardRadius
                setColor(palette.surface)
                if (theme.strokePx > 0) setStroke(theme.strokePx, palette.outline)
            }
            if (theme.elevationPx > 0f) elevation = theme.elevationPx
            isFillViewport = true
            clipToOutline = true
        }
        val message = label(model.text, look.type.bodySp + if (compact) 1f else 4f, heading = false, gravity = Gravity.CENTER_VERTICAL or Gravity.START).apply {
            setTextColor(palette.ink)
            typeface = theme.nameTypeface
            maxLines = Int.MAX_VALUE
            ellipsize = null
            setLineSpacing(0f, 1.1f)
            setPadding(padding * 2, padding, padding * 2, padding)
        }
        card.addView(message, ViewGroup.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.contentDescription = model.text
        column.addView(card, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap + gap / 2) })

        val add = TileView(context, theme)
        add.bind(
            TileSpec(
                getString(R.string.kb_add), TileLook(TileLook.Kind.OK),
                onClick = model.onAdd, textSp = look.type.headingSp + 3f,
            ),
        )
        column.addView(add, LinearLayout.LayoutParams(MATCH, px(if (compact) R.dimen.kb_check_tile_height_compact else R.dimen.kb_check_tile_height)))
        content.addView(column, LayoutParams(MATCH, MATCH))
    }

    // ---- Drag ghost ----

    fun showGhost(spec: TileSpec, width: Int, height: Int, rawX: Float, rawY: Float) {
        ghost.layoutParams = LayoutParams(width, height)
        ghost.bind(spec)
        ghost.alpha = 0.92f
        ghost.scaleX = 1.06f
        ghost.scaleY = 1.06f
        ghost.elevation = theme.dp(10).toFloat()
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
        val grid = TileGrid(context, theme)
        private val backTile = TileView(context, theme)
        private val title = label("", look.type.headingSp, heading = true, gravity = Gravity.CENTER_VERTICAL or Gravity.START)
        private val pageText = label("", look.type.smallSp, heading = false, gravity = Gravity.CENTER).apply { setTextColor(palette.inkSoft) }
        private val abc = TileView(context, theme)
        private val previous = TileView(context, theme)
        private val next = TileView(context, theme)
        private val settings = TileView(context, theme)
        private val emptyTitle = label(getString(R.string.kb_empty_title), look.type.headingSp, heading = true, gravity = Gravity.CENTER)
        private val emptyHint = label(getString(R.string.kb_empty_hint), look.type.bodySp, heading = false, gravity = Gravity.CENTER)
            .apply { setTextColor(palette.inkSoft); maxLines = 2 }
        private val emptyNew = TileView(context, theme)
        private val emptyBox = LinearLayout(context)
        private val backPosition = IntArray(2)

        init {
            val top = row()
            top.addView(backTile, LinearLayout.LayoutParams(buttonWidth, MATCH).apply { marginEnd = gap })
            top.addView(title, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginStart = theme.dp(4) })
            top.addView(pageText, LinearLayout.LayoutParams(WRAP, MATCH).apply { marginStart = gap })
            top.addView(settings, LinearLayout.LayoutParams(buttonWidth, MATCH).apply { marginStart = gap })
            title.ellipsize = TextUtils.TruncateAt.START
            title.isSingleLine = true
            root.addView(top, LinearLayout.LayoutParams(MATCH, topBarHeight))

            val (columns, rows) = KeyboardMetrics.gridShape(look, compact)
            grid.setLayoutShape(columns, rows)
            grid.isOverBack = { x, y -> isOver(backTile, x, y) }

            emptyBox.orientation = LinearLayout.VERTICAL
            emptyBox.gravity = Gravity.CENTER
            emptyBox.addView(emptyTitle, LinearLayout.LayoutParams(MATCH, WRAP))
            emptyBox.addView(emptyHint, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = theme.dp(2) })
            emptyBox.addView(emptyNew, LinearLayout.LayoutParams(MATCH, px(R.dimen.kb_check_tile_height)).apply { topMargin = theme.dp(12) })

            val body = FrameLayout(context).apply { clipChildren = false }
            body.addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))
            body.addView(emptyBox, FrameLayout.LayoutParams(MATCH, MATCH))
            root.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap / 2) })

            val strip = row()
            strip.addView(abc, LinearLayout.LayoutParams(0, MATCH, 3f))
            strip.addView(previous, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginStart = gap })
            strip.addView(next, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginStart = gap })
            root.addView(strip, LinearLayout.LayoutParams(MATCH, stripHeight))
        }

        fun bind(model: GridModel) {
            backTile.visibility = if (model.inFolder) VISIBLE else GONE
            backTile.bind(
                TileSpec("◀", TileLook(TileLook.Kind.SPECIAL, highlighted = model.backHighlighted),
                    description = getString(R.string.kb_desc_up), onClick = model.onBack),
            )
            title.text = if (model.inFolder) model.pathText else getString(R.string.kb_title)
            title.contentDescription = title.text
            val paged = model.pageCount > 1
            pageText.visibility = if (paged) VISIBLE else GONE
            pageText.text = getString(R.string.kb_page_indicator, model.page + 1, model.pageCount)

            grid.visibility = if (model.isEmpty) INVISIBLE else VISIBLE
            emptyBox.visibility = if (model.isEmpty) VISIBLE else GONE
            grid.bind(if (model.isEmpty) emptyList() else model.tiles)
            if (model.isEmpty) {
                emptyNew.bind(
                    TileSpec(getString(R.string.kb_new_big), TileLook(TileLook.Kind.NEW),
                        description = getString(R.string.kb_desc_new_shortcut), onClick = model.onSettings),
                )
            }

            abc.bind(
                TileSpec(getString(R.string.kb_abc), TileLook(TileLook.Kind.SPECIAL), getString(R.string.kb_desc_abc), onClick = model.onAbc),
            )
            previous.visibility = if (paged) VISIBLE else GONE
            next.visibility = if (paged) VISIBLE else GONE
            previous.bind(
                TileSpec("◀", TileLook(TileLook.Kind.SPECIAL), getString(R.string.kb_desc_prev_page), enabled = model.page > 0, onClick = model.onPrevious),
            )
            next.bind(
                TileSpec("▶", TileLook(TileLook.Kind.SPECIAL), getString(R.string.kb_desc_next_page), enabled = model.page < model.pageCount - 1, onClick = model.onNext),
            )
            // The settings button lives at the right end of the top bar, well away from the system's switcher icons.
            settings.bind(
                TileSpec("⚙", TileLook(TileLook.Kind.SPECIAL), getString(R.string.kb_desc_open_app), textSp = 20f, onClick = model.onSettings),
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
        top.addView(buttonTile("◀", getString(R.string.kb_desc_back), model.onBack), LinearLayout.LayoutParams(buttonWidth, MATCH))
        val middle = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val dots = label(dotsText(model.step, model.steps), look.type.smallSp, heading = false, gravity = Gravity.CENTER)
        if (compactTitle != null) {
            middle.addView(label(compactTitle, look.type.bodySp, heading = true, gravity = Gravity.CENTER), LinearLayout.LayoutParams(MATCH, 0, 1f))
        } else {
            middle.addView(label(model.shortcutName, look.type.bodySp, heading = true, gravity = Gravity.CENTER), LinearLayout.LayoutParams(MATCH, WRAP))
        }
        middle.addView(dots, LinearLayout.LayoutParams(MATCH, WRAP))
        middle.contentDescription = getString(R.string.kb_desc_step, model.step + 1, model.steps)
        top.addView(middle, LinearLayout.LayoutParams(0, MATCH, 1f).apply { setMargins(gap, 0, gap, 0) })
        top.addView(buttonTile("✕", getString(R.string.kb_desc_close), model.onCancel), LinearLayout.LayoutParams(buttonWidth, MATCH))
        return top
    }

    /** Dots for the steps so far in the accent colour, the rest quiet. */
    private fun dotsText(step: Int, steps: Int): CharSequence {
        if (steps > MAX_DOTS) return "${step + 1} / $steps"
        val text = SpannableStringBuilder()
        for (i in 0 until steps) {
            if (i > 0) text.append(' ')
            val start = text.length
            text.append(if (i <= step) "●" else "○")
            val colour = if (i <= step) palette.ink else palette.inkSoft
            text.setSpan(ForegroundColorSpan(colour), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return text
    }

    private fun questionRow(question: String, value: String?): View {
        val row = row()
        row.addView(
            label(question, look.type.headingSp + 2f, heading = true, gravity = Gravity.CENTER_VERTICAL or Gravity.START).apply { setPadding(theme.dp(4), 0, 0, 0) },
            LinearLayout.LayoutParams(0, MATCH, 1f),
        )
        if (value != null) {
            val shown = label(value, look.type.headingSp + 2f, heading = true, gravity = Gravity.CENTER_VERTICAL or Gravity.END)
            shown.contentDescription = value
            row.addView(shown, LinearLayout.LayoutParams(WRAP, MATCH).apply { marginEnd = theme.dp(4) })
        }
        return row
    }

    private fun addTilesBody(column: LinearLayout, body: StepBody.Tiles) {
        if (body.hint != null) {
            column.addView(
                label(body.hint, look.type.bodySp, heading = false, gravity = Gravity.CENTER_VERTICAL).apply { setTextColor(palette.inkSoft); setPadding(theme.dp(4), 0, 0, 0) },
                LinearLayout.LayoutParams(MATCH, WRAP),
            )
        }
        val grid = TileGrid(context, theme)
        val (columns, rows) = KeyboardMetrics.gridShape(look, compact)
        grid.setLayoutShape(columns, rows)
        grid.bind(body.tiles)
        column.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap / 2) })
        if (body.pageCount > 1) {
            val strip = row()
            strip.addView(
                buttonTile("◀", getString(R.string.kb_desc_prev_page), body.onPrevious, enabled = body.page > 0),
                LinearLayout.LayoutParams(0, MATCH, 1f),
            )
            strip.addView(
                label(getString(R.string.kb_page_indicator, body.page + 1, body.pageCount), look.type.smallSp, heading = false, gravity = Gravity.CENTER)
                    .apply { setTextColor(palette.inkSoft) },
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
        val pad = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        for (r in 0 until 3) {
            val line = row().apply { clipChildren = false }
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
                    textSp = PAD_SP,
                    onClick = if (key == "⌫") body.onBackspace else ({ body.onKey(key[0]) }),
                )
                line.addView(TileView(context, theme).also { it.bind(spec) }, LinearLayout.LayoutParams(0, MATCH, 1f).apply { if (c > 0) marginStart = gap })
            }
            pad.addView(line, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { if (r > 0) topMargin = gap })
        }
        val ok = TileView(context, theme).also {
            it.bind(TileSpec(getString(R.string.kb_ok), TileLook(TileLook.Kind.OK), onClick = body.onOk, textSp = PAD_SP))
        }
        val holder = if (compact) {
            // Short screen: OK sits beside the pad so every tile stays big.
            row().also {
                it.clipChildren = false
                it.addView(pad, LinearLayout.LayoutParams(0, MATCH, 4f))
                it.addView(ok, LinearLayout.LayoutParams(0, MATCH, 1.3f).apply { marginStart = gap })
            }
        } else {
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                addView(pad, LinearLayout.LayoutParams(MATCH, 0, 3f))
                addView(ok, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = gap })
            }
        }
        column.addView(holder, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap / 2) })
    }

    private fun addAdjustBody(column: LinearLayout, body: StepBody.Adjust) {
        val valueArea = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val big = label(body.value, if (compact) 34f else 48f, heading = true, gravity = Gravity.CENTER)
        valueArea.addView(big, LinearLayout.LayoutParams(MATCH, WRAP))
        if (body.later != null) {
            valueArea.addView(
                label(body.later, look.type.bodySp, heading = false, gravity = Gravity.CENTER).apply { setTextColor(palette.inkSoft) },
                LinearLayout.LayoutParams(MATCH, WRAP),
            )
        }
        column.addView(valueArea, LinearLayout.LayoutParams(MATCH, 0, 1.1f))

        val buttons = row().apply { clipChildren = false }
        body.buttons.forEachIndexed { i, spec ->
            buttons.addView(TileView(context, theme).also { it.bind(spec) }, LinearLayout.LayoutParams(0, MATCH, 1f).apply { if (i > 0) marginStart = gap })
        }
        column.addView(buttons, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap / 2) })
        column.addView(TileView(context, theme).also { it.bind(body.ok) }, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 2, 0, gap / 2) })
    }

    // ---- Small helpers ----

    private fun column() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        setPadding(padding, padding / 2, padding, padding / 2)
    }

    private fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun buttonTile(text: String, description: String, onClick: () -> Unit, enabled: Boolean = true): TileView =
        TileView(context, theme).also {
            it.bind(TileSpec(text, TileLook(TileLook.Kind.SPECIAL), description, enabled = enabled, onClick = onClick))
        }

    private fun label(text: CharSequence, sizeSp: Float, heading: Boolean, gravity: Int): TextView = TextView(context).apply {
        this.text = text
        this.gravity = gravity
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(palette.ink)
        typeface = if (heading) theme.headingTypeface else theme.bodyTypeface
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun px(res: Int): Int = resources.getDimensionPixelSize(res)

    private fun getString(res: Int, vararg args: Any): String = context.getString(res, *args)

    private fun setBottomPadding(left: Int, right: Int, navigationBottom: Int) {
        val bottom = KeyboardMetrics.bottomPaddingPx(navigationBottom, resources.displayMetrics.density, Build.VERSION.SDK_INT)
        setPadding(left, 0, right, bottom)
    }

    /**
     * Keeps the tiles clear of the navigation bar, plus a safety gap so the system's own keyboard
     * switcher icons (drawn in that area on Android 13 and up) never touch a tile.
     */
    private fun applyInsets(view: View, insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val nav = insets.getInsets(WindowInsets.Type.navigationBars())
            setBottomPadding(nav.left, nav.right, nav.bottom)
        } else {
            @Suppress("DEPRECATION")
            setBottomPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        return insets
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val MAX_DOTS = 10
        private const val PAD_SP = 26f
    }
}
