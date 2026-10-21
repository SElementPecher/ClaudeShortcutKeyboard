package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.pageBackground

/**
 * The whole keyboard: one fixed-height area that shows the grid, a question, or the check screen,
 * plus the ghost tile that follows the finger while dragging. Everything is laid out in code
 * from the current [Look].
 *
 * Quiet chrome: icon buttons have no box, page and question progress are little dots, and
 * colour is kept for the tiles and the one main button on each screen.
 */
internal class KeyboardView(context: Context, private val compact: Boolean, val look: Look) : FrameLayout(context) {

    private val theme = KeyboardTheme(context, look, compact)
    private val palette = theme.palette
    private val content = FrameLayout(context)
    private val ghost = TileView(context, theme).apply { visibility = GONE }
    private val rootPosition = IntArray(2)
    private var gridScreen: GridScreen? = null
    private var shownKey: String? = null

    var gridDragCallbacks: TileGrid.DragCallbacks? = null
        set(value) {
            field = value
            gridScreen?.grid?.dragCallbacks = value
        }

    // With big text the bars get a little taller too, so titles are never cut.
    private val barExtra = theme.dp(minOf(KeyboardMetrics.panelHeightDp(0, theme.combinedScale) / 2, MAX_BAR_EXTRA_DP))
    private val topBarHeight = px(R.dimen.kb_top_bar_height) + barExtra
    private val stripHeight = px(if (compact) R.dimen.kb_strip_height_compact else R.dimen.kb_strip_height)
    private val iconSize = px(R.dimen.kb_icon_button)
    private val gap = theme.dp(TileGrid.TILE_GAP_DP)
    private val padding = px(R.dimen.kb_padding)

    init {
        // The panel colour also fills the padding under the tiles, so the safety gap just looks like space.
        background = look.pageBackground(context, palette)
        clipChildren = false
        content.clipChildren = false
        val base = px(if (compact) R.dimen.kb_panel_height_compact else R.dimen.kb_panel_height)
        val height = base + theme.dp(KeyboardMetrics.panelHeightDp(0, theme.combinedScale))
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
            content.addView(screen.root, LayoutParams(MATCH, MATCH))
        }
        screen.bind(model)
        reveal("grid:${model.pathText}:${model.page}:${model.isEmpty}")
    }

    fun showStep(model: StepModel) {
        content.removeAllViews()
        val column = column()
        val numberBody = model.body as? StepBody.Number
        val numberValue = numberBody?.relative?.result ?: numberBody?.value
        val questionText = if (numberValue != null && compact) "${model.question}  $numberValue" else model.question

        column.addView(stepTopBar(model, if (compact) questionText else null), LinearLayout.LayoutParams(MATCH, topBarHeight))
        if (model.preview.isNotEmpty()) column.addView(previewTile(model), LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = gap / 2 })
        if (!compact) {
            val typed = numberBody?.relative?.typed
            val rowHeight = px(R.dimen.kb_question_height) + if (typed != null) theme.dp(14) else 0
            column.addView(questionRow(model.question, numberValue, typed), LinearLayout.LayoutParams(MATCH, rowHeight))
        }
        when (val body = model.body) {
            is StepBody.Tiles -> addTilesBody(column, body)
            is StepBody.Number -> addNumberBody(column, body)
            is StepBody.Adjust -> addAdjustBody(column, body)
        }
        content.addView(column, LayoutParams(MATCH, MATCH))
        val page = (model.body as? StepBody.Tiles)?.page ?: 0
        reveal("step:${model.step}:${model.body::class.simpleName}:$page")
    }

    /** The whole message with the defaults filled in; pressing it types it straight away. */
    private fun previewTile(model: StepModel): View {
        val plain = model.preview.joinToString("") { it.text }
        val message = TextView(context).apply {
            text = ResultSpans.build(model.preview, palette.tiles, theme.dp(6).toFloat(), theme.dp(3).toFloat(), theme.dp(1).toFloat())
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(look.type.bodySp))
            setTextColor(palette.ink)
            typeface = theme.bodyTypeface
            letterSpacing = KeyboardTheme.LETTER_SPACING
            maxLines = if (compact) 2 else 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(theme.dp(16), theme.dp(10), theme.dp(8), theme.dp(10))
        }
        val check = ImageView(context).apply {
            setImageDrawable(Icons.drawable(Icons.Kind.CHECK, palette.onAccent, theme.dp(22)))
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette.accent) }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = theme.cardRadius
                setColor(palette.surface)
                if (theme.strokePx > 0) setStroke(theme.strokePx, palette.outline)
            }
            if (theme.elevationPx > 0f) elevation = theme.elevationPx
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.kb_desc_add_now, plain)
            setOnClickListener { model.onAddNow() }
            addView(message, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(check, LinearLayout.LayoutParams(theme.dp(36), theme.dp(36)).apply { marginEnd = theme.dp(10) })
        }
        return row
    }

    fun showCheck(model: CheckModel) {
        content.removeAllViews()
        val column = column()
        val bar = row()
        bar.addView(iconButton(Icons.Kind.BACK, R.string.kb_desc_back, model.onBack), iconParams(start = -padding))
        bar.addView(
            label(model.shortcutName, SMALL_TITLE_SP, heading = true, gravity = Gravity.CENTER).apply { setTextColor(palette.inkSoft) },
            LinearLayout.LayoutParams(0, MATCH, 1f),
        )
        bar.addView(iconButton(Icons.Kind.CLOSE, R.string.kb_desc_close, model.onCancel), iconParams(end = -padding))
        column.addView(bar, LinearLayout.LayoutParams(MATCH, topBarHeight))

        // The message sits in a card as tall as the message itself; what changes is highlighted.
        val plain = model.pieces.joinToString("") { it.text }
        val message = TextView(context).apply {
            text = ResultSpans.build(model.pieces, palette.tiles, theme.dp(7).toFloat(), theme.dp(4).toFloat(), theme.dp(2).toFloat())
            gravity = Gravity.START
            setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(look.type.bodySp + if (compact) 1f else 4f))
            setTextColor(palette.ink)
            typeface = theme.bodyTypeface
            letterSpacing = KeyboardTheme.LETTER_SPACING
            setLineSpacing(0f, READING_LINE_SPACING)
            setPadding(theme.dp(20), theme.dp(if (compact) 12 else 18), theme.dp(20), theme.dp(if (compact) 12 else 18))
            contentDescription = plain
        }
        val card = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = theme.cardRadius
                setColor(palette.surface)
                if (theme.strokePx > 0) setStroke(theme.strokePx, palette.outline)
            }
            if (theme.elevationPx > 0f) elevation = theme.elevationPx
            addView(message, LayoutParams(MATCH, WRAP))
        }
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            setPadding(0, theme.dp(4), 0, theme.dp(8))
            addView(card, ViewGroup.LayoutParams(MATCH, WRAP))
        }
        val holder = FrameLayout(context).apply { clipChildren = false }
        holder.addView(scroll, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER_VERTICAL))
        column.addView(holder, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val add = TileView(context, theme)
        add.bind(
            TileSpec(
                getString(R.string.kb_add), TileLook(TileLook.Kind.OK, pill = true),
                icon = Icons.Kind.CHECK, onClick = model.onAdd, textSp = look.type.headingSp + 3f,
            ),
        )
        column.addView(add, LinearLayout.LayoutParams(MATCH, px(if (compact) R.dimen.kb_check_tile_height_compact else R.dimen.kb_check_tile_height)).apply { topMargin = gap / 2 })
        content.addView(column, LayoutParams(MATCH, MATCH))
        reveal("check")
    }

    /** A short fade-and-rise when a different screen appears (skipped when the phone has animations off). */
    private fun reveal(key: String) {
        if (key == shownKey) return
        shownKey = key
        content.animate().cancel()
        if (!theme.animations) {
            content.alpha = 1f
            content.translationY = 0f
            return
        }
        content.alpha = 0f
        content.translationY = theme.dp(6).toFloat()
        content.animate().alpha(1f).translationY(0f).setDuration(Motion.POP_MS).setInterpolator(DecelerateInterpolator()).start()
    }

    // ---- Drag ghost ----

    /** The tile "lifts": a little bigger, nearly solid and with a deep shadow under it. */
    fun showGhost(spec: TileSpec, width: Int, height: Int, rawX: Float, rawY: Float) {
        ghost.layoutParams = LayoutParams(width, height)
        ghost.bind(spec)
        ghost.alpha = 0.95f
        ghost.scaleX = 1.07f
        ghost.scaleY = 1.07f
        ghost.elevation = theme.dp(LIFT_DP).toFloat()
        ghost.translationZ = theme.dp(LIFT_DP / 2).toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ghost.outlineSpotShadowColor = SHADOW_COLOUR
            ghost.outlineAmbientShadowColor = SHADOW_COLOUR
        }
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
        private val back = iconButton(Icons.Kind.BACK, R.string.kb_desc_up) { onBack() }
        private val title = label("", look.type.headingSp, heading = true, gravity = Gravity.CENTER_VERTICAL or Gravity.START)
        private val settings = iconButton(Icons.Kind.GEAR, R.string.kb_desc_open_app) { onSettings() }
        private val strip = row()
        private val dots = DotsView(context, theme)
        private val previous = iconButton(Icons.Kind.BACK, R.string.kb_desc_prev_page) { onPrevious() }
        private val next = iconButton(Icons.Kind.FORWARD, R.string.kb_desc_next_page) { onNext() }
        private val emptyEmoji = label(getString(R.string.kb_empty_emoji), EMPTY_EMOJI_SP, heading = false, gravity = Gravity.CENTER)
        private val emptyTitle = label(getString(R.string.kb_empty_title), look.type.headingSp, heading = true, gravity = Gravity.CENTER)
        private val emptyHint = label(getString(R.string.kb_empty_hint), look.type.bodySp, heading = false, gravity = Gravity.CENTER)
            .apply { setTextColor(palette.inkSoft); maxLines = 2 }
        private val emptyNew = TileView(context, theme)
        private val emptyBox = LinearLayout(context)
        private val backPosition = IntArray(2)
        private var model: GridModel? = null

        private fun onBack() = model?.onBack?.invoke()
        private fun onSettings() = model?.onSettings?.invoke()
        private fun onPrevious() = model?.onPrevious?.invoke()
        private fun onNext() = model?.onNext?.invoke()

        init {
            val top = row()
            top.addView(back, iconParams(start = -padding))
            top.addView(title, LinearLayout.LayoutParams(0, MATCH, 1f))
            top.addView(settings, iconParams(end = -padding))
            title.ellipsize = TextUtils.TruncateAt.START
            title.isSingleLine = true
            // A little room so the first letter's edge is never clipped.
            title.setPadding(theme.dp(2), 0, theme.dp(2), 0)
            root.addView(top, LinearLayout.LayoutParams(MATCH, topBarHeight))

            val (columns, rows) = KeyboardMetrics.gridShape(look, compact)
            grid.setLayoutShape(columns, rows)
            grid.isOverBack = { x, y -> isOver(back, x, y) }

            emptyBox.orientation = LinearLayout.VERTICAL
            emptyBox.gravity = Gravity.CENTER
            if (!compact) emptyBox.addView(emptyEmoji, LinearLayout.LayoutParams(MATCH, WRAP))
            emptyBox.addView(emptyTitle, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = theme.dp(2) })
            emptyBox.addView(emptyHint, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = theme.dp(2) })
            emptyBox.addView(emptyNew, LinearLayout.LayoutParams(MATCH, px(R.dimen.kb_check_tile_height)).apply { topMargin = theme.dp(14) })

            val body = FrameLayout(context).apply { clipChildren = false }
            body.addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))
            body.addView(emptyBox, FrameLayout.LayoutParams(MATCH, MATCH))
            root.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 4, 0, gap / 4) })

            // The phone's own keyboard switcher does the job of an ABC key, so the strip only pages.
            strip.addView(dots, LinearLayout.LayoutParams(0, MATCH, 1f))
            strip.addView(previous, iconParams())
            strip.addView(next, iconParams(end = -theme.dp(8)))
            root.addView(strip, LinearLayout.LayoutParams(MATCH, stripHeight))
        }

        fun bind(model: GridModel) {
            this.model = model
            back.visibility = if (model.inFolder) VISIBLE else GONE
            back.setHighlighted(model.backHighlighted)
            (title.layoutParams as LinearLayout.LayoutParams).marginStart = if (model.inFolder) -theme.dp(4) else 0
            title.text = if (model.inFolder) model.pathText else getString(R.string.kb_title)
            // The breadcrumb keeps its end when it is long; the plain title never loses its first letter.
            title.ellipsize = if (model.inFolder) TextUtils.TruncateAt.START else TextUtils.TruncateAt.END
            title.contentDescription = title.text
            val paged = model.pageCount > 1

            grid.visibility = if (model.isEmpty) INVISIBLE else VISIBLE
            emptyBox.visibility = if (model.isEmpty) VISIBLE else GONE
            grid.bind(if (model.isEmpty) emptyList() else model.tiles)
            if (model.isEmpty) {
                emptyNew.bind(
                    TileSpec(
                        getString(R.string.kb_new_big), TileLook(TileLook.Kind.OK, pill = true),
                        description = getString(R.string.kb_desc_new_shortcut), icon = Icons.Kind.PLUS,
                        textSp = look.type.headingSp, onClick = model.onSettings,
                    ),
                )
            }

            strip.visibility = if (paged) VISIBLE else GONE
            dots.visibility = VISIBLE
            dots.set(model.pageCount, model.page)
            dots.contentDescription = getString(R.string.kb_desc_page, model.page + 1, model.pageCount)
            previous.visibility = if (paged) VISIBLE else GONE
            next.visibility = if (paged) VISIBLE else GONE
            previous.setActive(model.page > 0)
            next.setActive(model.page < model.pageCount - 1)
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
        top.addView(iconButton(Icons.Kind.BACK, R.string.kb_desc_back, model.onBack), iconParams(start = -padding))
        val middle = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        if (compactTitle != null) {
            middle.addView(label(compactTitle, look.type.bodySp, heading = true, gravity = Gravity.CENTER), LinearLayout.LayoutParams(MATCH, WRAP))
        } else {
            middle.addView(
                label(model.shortcutName, SMALL_TITLE_SP, heading = true, gravity = Gravity.CENTER).apply { setTextColor(palette.inkSoft) },
                LinearLayout.LayoutParams(MATCH, WRAP),
            )
        }
        if (model.steps <= MAX_DOTS) {
            val dots = DotsView(context, theme).also { it.set(model.steps, model.step, cumulative = true) }
            middle.addView(dots, LinearLayout.LayoutParams(MATCH, WRAP))
        } else {
            middle.addView(
                label("${model.step + 1} / ${model.steps}", look.type.smallSp, heading = false, gravity = Gravity.CENTER).apply { setTextColor(palette.inkSoft) },
                LinearLayout.LayoutParams(MATCH, WRAP),
            )
        }
        middle.contentDescription = getString(R.string.kb_desc_step, model.step + 1, model.steps)
        top.addView(middle, LinearLayout.LayoutParams(0, MATCH, 1f))
        top.addView(iconButton(Icons.Kind.CLOSE, R.string.kb_desc_close, model.onCancel), iconParams(end = -padding))
        return top
    }

    /** The question, and on the number screens the value as typed (for "how long": as it will print, with [typed] under it). */
    private fun questionRow(question: String, value: String?, typed: String? = null): View {
        val row = row()
        row.addView(
            label(question, look.type.headingSp + 2f, heading = true, gravity = Gravity.CENTER_VERTICAL or Gravity.START),
            LinearLayout.LayoutParams(0, MATCH, 1f),
        )
        if (value != null) {
            val shown = label(value, look.type.headingSp + 2f, heading = true, gravity = Gravity.CENTER_VERTICAL or Gravity.END)
            shown.contentDescription = value
            if (typed == null) {
                row.addView(shown, LinearLayout.LayoutParams(WRAP, MATCH))
            } else {
                val stack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL or Gravity.END }
                stack.addView(shown, LinearLayout.LayoutParams(WRAP, WRAP))
                stack.addView(
                    label(typed, look.type.smallSp, heading = false, gravity = Gravity.END).apply { setTextColor(palette.inkSoft) },
                    LinearLayout.LayoutParams(WRAP, WRAP),
                )
                row.addView(stack, LinearLayout.LayoutParams(WRAP, MATCH))
            }
        }
        return row
    }

    private fun addTilesBody(column: LinearLayout, body: StepBody.Tiles) {
        if (body.hint != null) {
            column.addView(
                label(body.hint, look.type.bodySp, heading = false, gravity = Gravity.CENTER_VERTICAL).apply { setTextColor(palette.inkSoft) },
                LinearLayout.LayoutParams(MATCH, WRAP),
            )
        }
        val grid = TileGrid(context, theme)
        val (columns, rows) = KeyboardMetrics.gridShape(look, compact)
        grid.setLayoutShape(columns, rows)
        grid.bind(body.tiles)
        column.addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 4, 0, gap / 4) })
        if (body.pageCount > 1) {
            val strip = row()
            strip.addView(
                iconButton(Icons.Kind.BACK, R.string.kb_desc_prev_page, body.onPrevious).also { it.setActive(body.page > 0) },
                iconParams(start = -padding),
            )
            val dots = DotsView(context, theme).also {
                it.set(body.pageCount, body.page)
                it.contentDescription = getString(R.string.kb_desc_page, body.page + 1, body.pageCount)
            }
            strip.addView(dots, LinearLayout.LayoutParams(0, MATCH, 1f))
            strip.addView(
                iconButton(Icons.Kind.FORWARD, R.string.kb_desc_next_page, body.onNext).also { it.setActive(body.page < body.pageCount - 1) },
                iconParams(end = -padding),
            )
            column.addView(strip, LinearLayout.LayoutParams(MATCH, stripHeight))
        }
    }

    private fun addNumberBody(column: LinearLayout, body: StepBody.Number) {
        body.relative?.let { return addAmountBody(column, body, it) }
        val keys = listOf("1", "2", "3", "⌫", "4", "5", "6", ".", "7", "8", "9", "0")
        val pad = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        for (r in 0 until 3) {
            val line = row().apply { clipChildren = false }
            for (c in 0 until 4) {
                val key = keys[r * 4 + c]
                val spec = TileSpec(
                    if (key == "⌫") "" else key,
                    TileLook(TileLook.Kind.SPECIAL),
                    icon = if (key == "⌫") Icons.Kind.BACKSPACE else null,
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
            it.bind(TileSpec(getString(R.string.kb_ok), TileLook(TileLook.Kind.OK, pill = !compact), icon = Icons.Kind.CHECK, onClick = body.onOk, textSp = PAD_SP))
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
        column.addView(holder, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 4, 0, gap / 4) })
    }

    /**
     * The "Other" screen of a "how long" question. Digits only: three columns of digits, and a fourth with
     * delete, 0 and OK. The Mins / Hours / Days tiles go in a row above (portrait) or a column beside (short screens).
     */
    private fun addAmountBody(column: LinearLayout, body: StepBody.Number, relative: RelativeEntry) {
        fun key(label: String, desc: String = label, onClick: () -> Unit) = TileView(context, theme).also {
            val delete = label == "⌫"
            it.bind(
                TileSpec(
                    if (delete) "" else label, TileLook(TileLook.Kind.SPECIAL), description = desc,
                    icon = if (delete) Icons.Kind.BACKSPACE else null, textSp = PAD_SP, onClick = onClick,
                ),
            )
        }
        val ok = TileView(context, theme).also {
            it.bind(
                TileSpec(
                    getString(R.string.kb_ok), TileLook(TileLook.Kind.OK), icon = Icons.Kind.CHECK, enabled = relative.okEnabled,
                    textSp = OK_SP, onClick = body.onOk,
                ),
            )
        }
        val cells = listOf(
            key("1") { body.onKey('1') }, key("2") { body.onKey('2') }, key("3") { body.onKey('3') },
            key("⌫", getString(R.string.kb_desc_delete_digit), body.onBackspace),
            key("4") { body.onKey('4') }, key("5") { body.onKey('5') }, key("6") { body.onKey('6') },
            key("0") { body.onKey('0') },
            key("7") { body.onKey('7') }, key("8") { body.onKey('8') }, key("9") { body.onKey('9') },
            ok,
        )
        val pad = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        for (r in 0 until 3) {
            val line = row().apply { clipChildren = false }
            for (c in 0 until 4) {
                // The OK tile is a little wider than the keys, so it reads as the way forward.
                val weight = if (c == 3) 1.5f else 1f
                line.addView(cells[r * 4 + c], LinearLayout.LayoutParams(0, MATCH, weight).apply { if (c > 0) marginStart = gap })
            }
            pad.addView(line, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { if (r > 0) topMargin = gap })
        }
        val unitViews = relative.units.map { spec -> TileView(context, theme).also { it.bind(spec) } }
        val holder = LinearLayout(context).apply { clipChildren = false }
        if (compact) {
            holder.orientation = LinearLayout.HORIZONTAL
            holder.addView(pad, LinearLayout.LayoutParams(0, MATCH, 4f))
            val units = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
            unitViews.forEachIndexed { i, v -> units.addView(v, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { if (i > 0) topMargin = gap }) }
            holder.addView(units, LinearLayout.LayoutParams(0, MATCH, 1.4f).apply { marginStart = gap })
        } else {
            holder.orientation = LinearLayout.VERTICAL
            val units = row().apply { clipChildren = false }
            unitViews.forEachIndexed { i, v -> units.addView(v, LinearLayout.LayoutParams(0, MATCH, 1f).apply { if (i > 0) marginStart = gap }) }
            holder.addView(units, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { bottomMargin = gap })
            holder.addView(pad, LinearLayout.LayoutParams(MATCH, 0, 3.3f))
        }
        column.addView(holder, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 4, 0, gap / 4) })
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
        column.addView(buttons, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 4, 0, gap / 2) })
        column.addView(TileView(context, theme).also { it.bind(body.ok) }, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { setMargins(0, gap / 4, 0, gap / 4) })
    }

    // ---- Small helpers ----

    private fun column() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        setPadding(padding, theme.dp(4), padding, theme.dp(4))
    }

    private fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
    }

    private fun iconButton(kind: Icons.Kind, description: Int, onClick: () -> Unit) =
        IconButton(context, theme, kind, getString(description), onClick)

    /** A 48dp square button slot. Negative margins pull the icon out towards the panel edge, where the eye expects it. */
    private fun iconParams(start: Int = 0, end: Int = 0) = LinearLayout.LayoutParams(iconSize, iconSize).apply {
        marginStart = start
        marginEnd = end
    }

    /** [sizeSp] is the Look's size; the owner's text size is applied here. */
    private fun label(text: CharSequence, sizeSp: Float, heading: Boolean, gravity: Int): TextView = TextView(context).apply {
        this.text = text
        this.gravity = gravity
        setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(sizeSp))
        setTextColor(palette.ink)
        typeface = if (heading) theme.headingTypeface else theme.bodyTypeface
        letterSpacing = KeyboardTheme.LETTER_SPACING
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
        private const val PAD_SP = 22f
        private const val OK_SP = 20f
        private const val SMALL_TITLE_SP = 15f
        private const val EMPTY_EMOJI_SP = 40f
        private const val READING_LINE_SPACING = 1.3f
        private const val MAX_BAR_EXTRA_DP = 12
        private const val LIFT_DP = 18
        private const val SHADOW_COLOUR = 0xFF000000.toInt()
    }
}
