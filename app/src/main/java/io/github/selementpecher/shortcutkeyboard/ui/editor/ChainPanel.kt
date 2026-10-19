package io.github.selementpecher.shortcutkeyboard.ui.editor

import android.app.Activity
import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.look.TileLayout
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf
import io.github.selementpecher.shortcutkeyboard.ime.QuickLabels
import io.github.selementpecher.shortcutkeyboard.model.RelativeShow
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.QuickChoices
import io.github.selementpecher.shortcutkeyboard.template.RelativeUnit
import io.github.selementpecher.shortcutkeyboard.ui.Motion
import io.github.selementpecher.shortcutkeyboard.ui.Ui
import io.github.selementpecher.shortcutkeyboard.ui.UiScreen
import kotlin.math.max

/**
 * The chain of big choices drawn under the message: one level at a time, a quiet breadcrumb, ◀ and ✕, big tiles
 * (or a field where typing is needed) and one main button in the screen's bottom bar when a choice needs
 * confirming. It draws whatever [AddFlow] says and tells [Listener] what happened; it keeps no state of its own.
 */
internal class ChainPanel(
    private val activity: Activity,
    private val ui: Ui,
    private val screen: UiScreen,
    private val listener: Listener,
) {
    interface Listener {
        /** Something was chosen or typed: the half-made chip in the message should follow. */
        fun onChanged()

        /** The last choice was made. */
        fun onFinished()

        /** ✕, or ◀ on the first level. */
        fun onCancelled()

        /** (Changing a chip) turn it back into plain words. */
        fun onMakePlain()

        /** (Changing a chip) take it out. */
        fun onRemove()
    }

    /** The panel's views: put them under the message. */
    val view: LinearLayout = ui.column()

    private var flow: AddFlow? = null
    private var primary: TextView? = null
    private var addAnother: TextView? = null
    private var typed: EditText? = null

    private fun str(id: Int) = activity.getString(id)

    /** Draws the level the flow is on. Call again after the flow moved. */
    fun show(flow: AddFlow) {
        this.flow = flow
        render()
    }

    fun clear() {
        flow = null
        view.removeAllViews()
        screen.bottomBar.removeAllViews()
        primary = null
        addAnother = null
        typed = null
    }

    private fun render() {
        val flow = flow ?: return
        view.removeAllViews()
        screen.bottomBar.removeAllViews()
        primary = null
        addAnother = null
        typed = null
        view.addView(header(flow))
        when (flow.step) {
            FlowStep.KIND -> kindLevel()
            FlowStep.TIME_KIND -> timeKindLevel(flow)
            FlowStep.BASE -> baseLevel(flow)
            FlowStep.UNIT -> unitLevel(flow)
            FlowStep.AMOUNTS -> amountsLevel(flow)
            FlowStep.AFTER_NUMBER -> afterLevel(flow)
            FlowStep.SHOW -> showLevel(flow)
            FlowStep.QUESTION, FlowStep.LIST_QUESTION -> questionLevel(flow)
            FlowStep.LIST_ANSWERS -> answersLevel(flow)
        }
        // Make it plain words / Remove it: on the short screens they sit at the bottom, just above Done, so the empty
        // space is not in the middle; on the long amounts screen they stay in the page so the bottom has room.
        val extras = flow.isEditing && !flow.canGoBack
        val quick = flow.kind() == BlankKind.TIME || flow.kind() == BlankKind.DATE
        val pinned = extras && !quick && (flow.step == FlowStep.QUESTION || flow.step == FlowStep.LIST_ANSWERS)
        if (extras) editExtras(pinned)
        val button = primaryLabel(flow)
        if (button != null) {
            val b = ui.primaryButton(button) { advance() }
            primary = b
            screen.bottomBar.addView(b)
            screen.showBottomBar(true)
            refreshControls()
        } else {
            screen.showBottomBar(pinned)
        }
        val field = typed
        if (field != null) showKeyboardFor(field) else hideKeyboard()
    }

    // ---- header: back, title, cancel, breadcrumb ----

    private fun header(flow: AddFlow): View = with(ui) {
        val box = column()
        // A progress bar, only when making or changing this part takes more than one screen.
        flow.progress()?.let { (at, total) ->
            val bar = row()
            for (i in 1..total) {
                bar.addView(progressBar(if (i <= at) 1f else 0f, 6), lp(width = 0, weight = 1f, start = if (i == 1) 0 else 6, height = dp(6)))
            }
            bar.contentDescription = activity.getString(R.string.editor_step_of, at, total)
            box.add(bar, bottom = 12)
        }
        val line = row()
        if (flow.canGoBack) {
            line.addView(iconButton(Icons.Kind.BACK, str(R.string.chain_back)) { goBack() }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = -dp(12) })
        }
        line.addView(
            heading(titleFor(flow)).apply {
                layoutParams = lp(width = 0, weight = 1f, start = if (flow.canGoBack) 2 else 0, end = 4)
            },
        )
        // Changing an existing part has Done (and the back arrow), so no close button; making a new one keeps it.
        if (!flow.isEditing) {
            line.addView(iconButton(Icons.Kind.CLOSE, str(R.string.chain_cancel)) { listener.onCancelled() }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = -dp(12) })
        }
        box.addView(line, lp())
        val crumbs = flow.crumbs().joinToString(str(R.string.chain_crumb_separator)) { crumbText(it) }
        if (crumbs.isNotEmpty()) {
            box.add(
                small(crumbs).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
                top = 2,
            )
        }
        box.layoutParams = lp(bottom = 14)
        box
    }

    private fun crumbText(crumb: Crumb): String = when (crumb) {
        is Crumb.Kind -> str(EditorText.kindName(crumb.kind))
        is Crumb.Time -> str(EditorText.timeChoiceName(crumb.choice))
        is Crumb.Unit -> str(EditorText.unitName(crumb.unit))
    }

    private fun titleFor(flow: AddFlow): String = when (flow.step) {
        FlowStep.KIND -> str(R.string.chain_title_kind)
        FlowStep.TIME_KIND -> str(R.string.chain_title_time)
        FlowStep.BASE -> str(R.string.chain_title_base)
        FlowStep.UNIT -> str(if (flow.timeChoice == TimeChoice.AFTER_OTHER) R.string.chain_title_unit_short else R.string.chain_title_unit)
        FlowStep.AMOUNTS, FlowStep.AFTER_NUMBER -> str(R.string.chain_title_amount)
        FlowStep.SHOW -> str(R.string.chain_title_show)
        FlowStep.QUESTION, FlowStep.LIST_QUESTION -> str(if (flow.kind() == BlankKind.TIME || flow.kind() == BlankKind.DATE) R.string.chain_title_quick else R.string.chain_title_question)
        FlowStep.LIST_ANSWERS -> str(if (flow.isEditing) R.string.chain_title_values else R.string.chain_title_answers)
    }

    private fun primaryLabel(flow: AddFlow): String? = when (flow.step) {
        // Amounts always go on to "what to show", so it is "Next" even when changing an existing one.
        FlowStep.AMOUNTS -> str(R.string.chain_next)
        FlowStep.LIST_QUESTION -> str(if (flow.isEditing) R.string.chain_done else R.string.chain_next)
        FlowStep.LIST_ANSWERS, FlowStep.AFTER_NUMBER, FlowStep.QUESTION -> str(if (flow.isEditing) R.string.chain_done else R.string.chain_add)
        FlowStep.SHOW -> if (flow.isEditing) str(R.string.chain_done) else null
        else -> null
    }

    // ---- moving ----

    /** One level back (the system back button); closes the chain from the first level. */
    fun back() = goBack()

    private fun goBack() {
        val flow = flow ?: return
        if (!flow.back()) {
            listener.onCancelled()
            return
        }
        listener.onChanged()
        render()
    }

    private fun advance() {
        val flow = flow ?: return
        if (!flow.canAdvance) return
        flow.advance()
        moved()
    }

    /** After a choice: finish, or draw the next level. */
    private fun moved() {
        val flow = flow ?: return
        listener.onChanged()
        if (flow.isDone) listener.onFinished() else render()
    }

    private fun refreshControls() {
        val flow = flow ?: return
        primary?.let {
            it.isEnabled = flow.canAdvance
            it.alpha = if (flow.canAdvance) 1f else 0.4f
        }
        addAnother?.let {
            val ok = flow.pendingAmount != null
            it.isEnabled = ok
            it.alpha = if (ok) 1f else 0.4f
        }
    }

    // ---- the levels ----

    private fun kindLevel() = with(ui) {
        val flow = flow!!
        val hints = mapOf(
            BlankKind.LIST to R.string.chain_kind_list_hint,
            BlankKind.TIME to R.string.chain_kind_time_hint,
            BlankKind.DATE to R.string.chain_kind_date_hint,
            BlankKind.NUMBER to R.string.chain_kind_number_hint,
        )
        val tiles = BlankKind.topLevel.mapIndexed { i, kind ->
            tile(i, kind.glyph, str(EditorText.kindName(kind)), str(hints.getValue(kind))) {
                flow.chooseKind(kind)
                moved()
            }
        }
        view.addView(grid(2, tiles, gapDp = 12), lp())
    }

    private fun timeKindLevel(flow: AddFlow) = with(ui) {
        val choices = buildList {
            add(Triple(TimeChoice.CLOCK, "🕒", R.string.chain_time_clock_hint))
            add(Triple(TimeChoice.FROM_NOW, "⏳", R.string.chain_time_from_now_hint))
            add(Triple(TimeChoice.AGO, "⌛", R.string.chain_time_ago_hint))
            if (flow.clockBlanks.isNotEmpty()) add(Triple(TimeChoice.AFTER_OTHER, "🔗", R.string.chain_time_after_other_hint))
        }
        val tiles = choices.mapIndexed { i, (choice, glyph, hint) ->
            tile(i + 1, glyph, str(EditorText.timeChoiceName(choice)), str(hint)) {
                flow.chooseTime(choice)
                moved()
            }
        }
        view.addView(grid(2, tiles, gapDp = 12), lp())
    }

    private fun baseLevel(flow: AddFlow) = with(ui) {
        val tiles = flow.clockBlanks.mapIndexed { i, blank ->
            tile(i, "🕒", blank.question, null, wide = true) {
                flow.chooseBase(blank.id)
                moved()
            }
        }
        view.addView(stack(tiles), lp())
    }

    private fun unitLevel(flow: AddFlow) = with(ui) {
        val units = flow.availableUnits()
        val tiles = units.mapIndexed { i, unit ->
            tile(i + 1, null, str(EditorText.unitName(unit)), null, centered = true, minHeightDp = 96) {
                flow.chooseUnit(unit)
                moved()
            }
        }
        view.addView(grid(units.size, tiles, gapDp = 12), lp())
    }

    private fun amountsLevel(flow: AddFlow) = with(ui) {
        val field = numberField(flow)
        view.addView(field, lp())
        view.add(unitChoice(flow, flow.availableUnits()), top = 12)
        val add = secondaryButton(str(R.string.chain_amount_add), Icons.Kind.PLUS) {
            if (flow.addAnotherAmount()) {
                listener.onChanged()
                render()
            }
        }
        addAnother = add
        view.add(add, top = 12)
        if (flow.amounts.isNotEmpty()) {
            view.add(label(str(R.string.chain_amount_so_far)), top = 16)
            val chips = ui.flow(8)
            flow.amounts.forEachIndexed { index, amount ->
                val text = amount.text()
                chips.addView(
                    chip(text) {
                        flow.removeAmount(index)
                        listener.onChanged()
                        render()
                    }.apply {
                        setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon(Icons.Kind.CLOSE, 18, palette.inkSoft), null)
                        compoundDrawablePadding = dp(8)
                        contentDescription = activity.getString(R.string.chain_amount_remove, text)
                    },
                )
            }
            view.add(chips, top = 8)
        }
        view.add(small(str(R.string.chain_amount_help)), top = 16)
        typed = field
    }

    private fun afterLevel(flow: AddFlow) = with(ui) {
        val field = numberField(flow)
        view.addView(field, lp())
        view.add(unitChoice(flow, flow.availableUnits()), top = 12)
        val holder = column()
        fun directions() {
            holder.removeAllViews()
            val line = row()
            listOf(true to R.string.chain_after, false to R.string.chain_before).forEachIndexed { i, (after, label) ->
                line.addView(
                    chip(str(label), selected = flow.after == after) {
                        flow.setAfter(after)
                        listener.onChanged()
                        directions()
                    }.apply { gravity = Gravity.CENTER },
                    lp(width = 0, weight = 1f, start = if (i == 0) 0 else 8),
                )
            }
            holder.addView(line, lp())
        }
        directions()
        flow.base?.let { view.add(label(activity.getString(R.string.chain_after_help, it.question)), top = 20) }
        view.add(holder, top = 8)
        typed = field
    }

    /** The big number box, filled in from the flow and kept to 1 to 3 digits. */
    private fun numberField(flow: AddFlow): EditText = with(ui) {
        field(str(R.string.chain_amount_hint)).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(30f))
            typeface = typefaceOf(700)
            gravity = Gravity.CENTER
            minHeight = dp(72)
            filters = arrayOf(InputFilter.LengthFilter(AddFlow.MAX_DIGITS))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(flow.digits)
            setSelection(text.length)
            contentDescription = str(R.string.chain_title_amount)
            addTextChangedListener(afterText { s ->
                flow.typeNumber(s)
                if (flow.digits != s) {
                    setText(flow.digits)
                    setSelection(flow.digits.length)
                }
                refreshControls()
                listener.onChanged()
            })
            setOnEditorActionListener { _, _, _ ->
                if (flow.canAdvance) advance()
                true
            }
        }
    }

    /** Mins · Hours · (Days): equal chips, the chosen one in the accent colour. */
    private fun unitChoice(flow: AddFlow, units: List<RelativeUnit>): View = with(ui) {
        val holder = column()
        fun draw() {
            holder.removeAllViews()
            val line = row()
            units.forEachIndexed { i, unit ->
                line.addView(
                    chip(str(EditorText.unitName(unit)), selected = flow.unit == unit) {
                        flow.setUnit(unit)
                        listener.onChanged()
                        draw()
                        refreshControls()
                    }.apply { gravity = Gravity.CENTER },
                    lp(width = 0, weight = 1f, start = if (i == 0) 0 else 8),
                )
            }
            holder.addView(line, lp())
        }
        draw()
        holder
    }

    private fun showLevel(flow: AddFlow) = with(ui) {
        val examples = flow.showExamples()
        val choices = listOf(
            Triple(RelativeShow.AMOUNT, R.string.chain_show_amount, 0),
            Triple(RelativeShow.CLOCK, R.string.chain_show_clock, 1),
            Triple(RelativeShow.BOTH, R.string.chain_show_both, 2),
        )
        val tiles = choices.map { (show, label, colour) ->
            // Changing an existing one: the choice it has now is marked, and Done keeps it.
            val hint = str(label) + if (flow.isEditing && flow.show == show) str(R.string.chain_show_current) else ""
            tile(colour + 1, null, examples.getValue(show), hint, wide = true, bigTitle = true) {
                flow.chooseShow(show)
                moved()
            }
        }
        view.addView(stack(tiles), lp())
    }

    /** A pill with no list of values to type (a number, a time, a date). Time and date have quick choices to pick. */
    private fun questionLevel(flow: AddFlow) = with(ui) {
        when (flow.kind()) {
            BlankKind.TIME, BlankKind.DATE -> quickLevel(flow)
            else -> view.add(small(str(R.string.chain_number_note)))
        }
        typed = null
    }

    /**
     * The ready-made answers the keyboard offers for a time or date: one to six, the first is the default. Chosen ones
     * show first (tap to take one out); the rest can be tapped to add. With an odd number the first is twice as wide,
     * as it is on the keyboard.
     */
    private fun quickLevel(flow: AddFlow) = with(ui) {
        val isDate = flow.kind() == BlankKind.DATE
        val chosen = flow.quickChoices()
        val now = java.time.LocalTime.now().let { String.format("%02d:%02d", it.hour, it.minute) }
        fun name(value: Long): String =
            (if (isDate) QuickLabels.date(activity, value) else QuickLabels.time(activity, value, now)).replace('\n', ' ')
        val presets = if (isDate) QuickChoices.presets(VariableType.DATE) else QuickChoices.presets(VariableType.TIME)

        view.add(label(activity.getString(R.string.chain_quick_chosen, chosen.size, QuickChoices.MAX)))
        val grid = column()
        val odd = QuickChoices.firstIsWide(chosen.size)
        var rowView: LinearLayout? = null
        chosen.forEachIndexed { i, value ->
            val pill = chip(name(value), selected = i == 0) {
                if (chosen.size > 1) {
                    flow.toggleQuick(value)
                    render()
                } else {
                    screen.snack(str(R.string.chain_quick_min))
                }
            }.apply {
                contentDescription = name(value) + ", " + str(R.string.chain_quick_tap_remove)
            }
            val wide = odd && i == 0
            if (wide || rowView == null || rowView!!.childCount == 2) {
                rowView = row().also { grid.add(it, top = if (i == 0) 0 else 8) }
            }
            rowView!!.addView(pill, LinearLayout.LayoutParams(0, dp(52), if (wide) 2f else 1f).apply {
                if (rowView!!.childCount > 0) marginStart = dp(8)
            })
            if (wide) rowView = null
        }
        view.add(grid, top = 8)
        view.add(small(str(R.string.chain_quick_first)), top = 8)

        val others = presets.filter { it !in chosen }
        if (others.isNotEmpty()) {
            view.add(label(str(R.string.chain_quick_add)), top = 20)
            val chips = flow(8)
            val full = chosen.size >= QuickChoices.MAX
            for (value in others) {
                chips.addView(chip(name(value)) {
                    flow.toggleQuick(value)
                    render()
                }.apply {
                    alpha = if (full) 0.4f else 1f
                    isEnabled = !full
                })
            }
            view.add(chips, top = 8)
            if (full) view.add(small(str(R.string.chain_quick_max)), top = 8)
        }
        view.add(small(str(R.string.chain_quick_other)), top = 12)
    }

    private fun answersLevel(flow: AddFlow) = with(ui) {
        // The list's name: optional, no suggestions. Left empty, it is called "List".
        view.add(label(str(R.string.chain_name_label)), bottom = 6)
        view.add(
            field(str(R.string.chain_name_hint)).apply {
                setText(flow.question.takeUnless { it == BlankQuestions.default(BlankKind.LIST) }.orEmpty())
                imeOptions = EditorInfo.IME_ACTION_NEXT
                addTextChangedListener(afterText { s ->
                    flow.setQuestion(s)
                    listener.onChanged()
                })
            },
            bottom = 16,
        )
        view.add(small(str(R.string.chain_answers_help)), bottom = 10)
        val rows = column()
        val answerFields = ArrayList<EditText>()
        var firstField: EditText? = null
        flow.answers.forEachIndexed { index, answer ->
            val line = row()
            val f = field(str(R.string.chain_answer_hint)).apply {
                setText(answer)
                setSelection(text.length)
                imeOptions = EditorInfo.IME_ACTION_NEXT
                addTextChangedListener(afterText { s ->
                    flow.setAnswer(index, s)
                    refreshControls()
                    listener.onChanged()
                })
            }
            if (firstField == null) firstField = f
            answerFields += f
            line.addView(f, lp(width = 0, weight = 1f))
            if (flow.answers.size > 1) {
                line.addView(
                    iconButton(Icons.Kind.CLOSE, if (answer.isBlank()) str(R.string.chain_answer_remove_empty) else activity.getString(R.string.chain_answer_remove, answer)) {
                        flow.removeAnswerRow(index)
                        listener.onChanged()
                        render()
                    },
                )
            }
            rows.addView(line, lp(bottom = 8))
        }
        view.addView(rows, lp())
        view.add(
            secondaryButton(str(R.string.chain_answer_add), Icons.Kind.PLUS) {
                flow.addAnswerRow()
                render()
            },
            top = 4,
        )
        // Start in the first empty row (or the first row).
        typed = flow.answers.indexOfFirst { it.isBlank() }.let { i -> answerFields.getOrNull(max(i, 0)) } ?: firstField
    }

    private fun editExtras(pinned: Boolean) = with(ui) {
        val plain = textButton(str(R.string.chain_make_plain)) { listener.onMakePlain() }
        val remove = dangerButton(str(R.string.chain_remove)) { listener.onRemove() }
        if (pinned) {
            screen.bottomBar.addView(plain)
            screen.bottomBar.addView(remove)
        } else {
            view.add(plain, top = 12)
            view.add(remove)
        }
    }

    // ---- big tiles ----

    /**
     * A big tile in the colour of position [colour] (the same pastel or vivid colours as the shortcut tiles).
     * Wide Looks put the picture at the left; the square Look centres everything. [centered] forces centring and
     * [wide] makes a full-width row.
     */
    private fun tile(
        colour: Int,
        glyph: String?,
        title: String,
        hint: String?,
        centered: Boolean = false,
        wide: Boolean = false,
        bigTitle: Boolean = false,
        minHeightDp: Int = 0,
        onClick: () -> Unit,
    ): View = with(ui) {
        val colours = palette.tiles[colour % palette.tiles.size]
        val radius = look.shape.tileRadiusDp.coerceAtMost(24).toFloat()
        val base = shape(colours.bg, radius, palette.outline, if (look.shape.strokeDp > 0f) look.shape.strokeDp else 0f)
        val square = look.tileLayout == TileLayout.SQUARE
        val center = centered || (square && !wide)
        val box = column().apply {
            background = rippled(softShadow(base, radius), radius)
            isClickable = true
            isFocusable = true
            contentDescription = if (hint != null) "$title. $hint" else title
            setOnClickListener { onClick() }
            Motion.pressable(this)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            minimumHeight = dp(if (minHeightDp > 0) minHeightDp else if (wide) 72 else 112)
            gravity = if (center) Gravity.CENTER else Gravity.CENTER_VERTICAL or Gravity.START
            layoutParams = lp()
        }
        if (glyph != null) {
            val circle = TextView(context).apply {
                text = glyph
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(22f))
                includeFontPadding = false
                background = shape(withAlpha(colours.fg, 0x22), 999f)
            }
            box.addView(circle, LinearLayout.LayoutParams(dp(44), dp(44)).apply { if (center) gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(10) })
        }
        val titleView = text(
            title,
            if (bigTitle) look.type.headingSp + 2 else look.type.tileSp,
            max(600, look.type.tileWeight),
            colours.fg,
            1.15f,
        ).apply { if (center) gravity = Gravity.CENTER }
        box.addView(titleView)
        if (hint != null) {
            box.addView(
                text(hint, look.type.smallSp, 500, colours.fg, 1.15f).apply {
                    alpha = 0.85f
                    if (center) gravity = Gravity.CENTER
                    layoutParams = lp(top = 2)
                },
            )
        }
        box
    }

    /** Full-width tiles one under the other. */
    private fun stack(tiles: List<View>): LinearLayout = with(ui) {
        val box = column().apply { clipChildren = false }
        tiles.forEachIndexed { i, t -> box.add(t, top = if (i == 0) 0 else 12) }
        box
    }

    // ---- the keyboard ----

    private fun showKeyboardFor(field: EditText) {
        field.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            if (field.requestFocus()) {
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(field, 0)
            }
        }
    }

    private fun hideKeyboard() {
        val token = view.windowToken ?: activity.currentFocus?.windowToken ?: return
        (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(token, 0)
    }

    private fun afterText(onText: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onText(s?.toString().orEmpty())
    }

    private fun fieldsIn(root: View): List<EditText> {
        val out = ArrayList<EditText>()
        fun walk(v: View) {
            if (v is EditText) out += v
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return out
    }
}
