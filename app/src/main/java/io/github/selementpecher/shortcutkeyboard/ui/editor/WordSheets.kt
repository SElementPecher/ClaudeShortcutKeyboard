package io.github.selementpecher.shortcutkeyboard.ui.editor

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.ui.Ui
import io.github.selementpecher.shortcutkeyboard.ui.UiPanel
import kotlin.math.max

/**
 * The bottom sheets for the step "Tap the words that change": one for a plain word (take more
 * words, choose what kind of word it is, write the question and answers) and one for a word that
 * already changes (edit it, or turn it back into plain words).
 *
 * [draft] gives the current draft; [onChange] receives the new one when the owner finishes.
 */
internal class WordSheets(
    private val activity: Activity,
    private val ui: Ui,
    private val draft: () -> MessageDraft,
    private val onChange: (MessageDraft) -> Unit,
) {
    private val palette = ui.palette
    private var open: UiPanel? = null

    /** Closes the sheet if one is showing (when the screen goes away). */
    fun dismiss() {
        open?.dismiss()
        open = null
    }

    /** A plain word was tapped. */
    fun openWord(selection: Selection) {
        dismiss()
        val panel = ui.sheet(activity)
        open = panel
        choose(panel, selection, selection)
        panel.show()
    }

    /** A word that changes (or a built-in) was tapped; [segment] is its place in the message. */
    fun openChip(segment: Int) {
        val current = draft()
        dismiss()
        val panel = ui.sheet(activity)
        open = panel
        when (val s = current.segments.getOrNull(segment)) {
            is Segment.Fixed -> info(panel, fixedText(s))
            is Segment.Ref -> {
                val spec = current.blank(s.blankId)
                when {
                    spec == null -> return
                    s.offsetMinutes != 0L -> offsetEditor(panel, segment, s, spec)
                    else -> editor(panel, spec)
                }
            }
            else -> return
        }
        panel.show()
    }

    /**
     * Puts [buttons] in a strip under the scrolling part of the sheet, so the main button is always
     * in reach however long the form is. An empty list removes the strip.
     */
    private fun setFooter(panel: UiPanel, buttons: List<View>) {
        val scroller = panel.body.parent as? ViewGroup ?: return
        val surface = scroller.parent as? LinearLayout ?: return
        surface.findViewWithTag<View>(FOOTER_TAG)?.let { surface.removeView(it) }
        if (buttons.isEmpty()) return
        val footer = ui.column().apply { tag = FOOTER_TAG }
        buttons.forEachIndexed { i, b -> with(ui) { footer.add(b, top = if (i == 0) 12 else 4) } }
        surface.addView(footer, ui.lp())
    }

    // ---- first look: the word, how much of it, and what kind ----

    private fun choose(panel: UiPanel, selection: Selection, original: Selection) {
        val current = draft()
        val body = panel.body
        body.removeAllViews()
        setFooter(panel, emptyList())
        with(ui) {
            // The words chosen so far, big.
            val phrase = text(current.textOf(selection), look.type.titleSp, look.type.headingWeight, palette.ink).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(18), dp(16), dp(18))
                background = shape(withAlpha(palette.accent, 0x26), look.shape.cardRadiusDp.coerceAtMost(20).toFloat(), palette.accent, 2f)
            }
            body.add(phrase)

            val before = current.extend(selection, before = true)
            val after = current.extend(selection, before = false)
            val more = row()
            more.addView(
                secondaryButton(activity.getString(R.string.editor_sheet_before)) { before?.let { choose(panel, it, original) } }
                    .also { it.isEnabled = before != null; it.alpha = if (before != null) 1f else 0.4f },
                lp(width = 0, weight = 1f, end = 6),
            )
            more.addView(
                secondaryButton(activity.getString(R.string.editor_sheet_after)) { after?.let { choose(panel, it, original) } }
                    .also { it.isEnabled = after != null; it.alpha = if (after != null) 1f else 0.4f },
                lp(width = 0, weight = 1f, start = 6),
            )
            body.add(more, top = 12)
            if (selection != original) {
                val one = current.textOf(original)
                body.add(
                    textButton(activity.getString(R.string.editor_sheet_just_one, one)) { choose(panel, original, original) },
                    top = 4,
                )
            }

            body.add(label(activity.getString(R.string.editor_kind_title)), top = 20)

            // A time that can be counted from a time already in the message comes first.
            val relations = current.timeRelations(selection)
            for (relation in relations) {
                val text = when {
                    relation.afterMinutes == 0L -> activity.getString(R.string.editor_time_same, relation.question)
                    else -> activity.getString(R.string.editor_time_after, EditorText.duration(activity, relation.afterMinutes), relation.question)
                }
                body.add(
                    primaryButton("🕒  $text") {
                        panel.dismiss()
                        onChange(draft().reuseTimeLater(selection, relation.blankId, relation.afterMinutes))
                    },
                    top = 10,
                )
                if (relation.beforeMinutes != 0L && -relation.beforeMinutes < relation.afterMinutes) {
                    body.add(
                        textButton(activity.getString(R.string.editor_time_choose_before, EditorText.duration(activity, relation.beforeMinutes), relation.question)) {
                            panel.dismiss()
                            onChange(draft().reuseTimeLater(selection, relation.blankId, relation.beforeMinutes))
                        },
                        top = 2,
                    )
                }
            }

            val tiles = BlankKind.entries.map { kind ->
                kindTile(kind) { details(panel, selection, kind) }
            }
            body.add(grid(2, tiles, gapDp = 12), top = 10)
        }
    }

    private fun kindTile(kind: BlankKind, onClick: () -> Unit): View = with(ui) {
        column().apply {
            val r = look.shape.tileRadiusDp.coerceAtMost(20).toFloat()
            val bg = if (look.shape.strokeDp > 0f) shape(palette.surface, r, palette.outline, look.shape.strokeDp) else shape(palette.surfaceAlt, r)
            background = rippled(bg, r)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            minimumHeight = dp(104)
            isClickable = true
            isFocusable = true
            contentDescription = activity.getString(EditorText.kindName(kind)) + ". " + activity.getString(EditorText.kindHint(kind))
            setOnClickListener { onClick() }
            addView(text(kind.glyph, 28f, 400, palette.ink))
            add(text(activity.getString(EditorText.kindName(kind)), look.type.bodySp, max(600, look.type.headingWeight), palette.ink), top = 6)
            add(small(activity.getString(EditorText.kindHint(kind))), top = 2)
        }
    }

    // ---- second look: the question and the answers ----

    /** The question box, its suggestions and (for a list) the answer rows. */
    private inner class Form(
        private val kind: BlankKind,
        question: String,
        /** The word that was tapped: always the first answer, shown but not editable. Null when editing. */
        private val firstAnswer: String?,
        answers: List<String>,
    ) {
        val view: LinearLayout = ui.column()
        private val questionField: EditText = ui.field(activity.getString(R.string.editor_question_hint)).apply { setText(question) }
        private val suggestions = ui.flow(8)
        private val rows = ui.column()
        private val answerFields = ArrayList<EditText>()

        val question: String get() = questionField.text.toString()

        /** The answers typed after the first one (or all of them when editing). */
        val answers: List<String> get() = answerFields.map { it.text.toString() }

        init {
            with(ui) {
                view.add(label(activity.getString(R.string.editor_question_title)))
                view.add(suggestions, top = 8)
                view.add(questionField, top = 10)
                questionField.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) = showSuggestions()
                })
                showSuggestions()
                if (kind == BlankKind.LIST) {
                    view.add(label(activity.getString(R.string.editor_answers_title)), top = 20)
                    view.add(small(activity.getString(R.string.editor_answers_help)), top = 2)
                    if (firstAnswer != null) rows.addView(firstRow(firstAnswer), lp(bottom = 8))
                    view.add(rows, top = 10)
                    answers.forEach { addRow(it) }
                    view.add(secondaryButton(activity.getString(R.string.editor_answer_add)) { addRow("").requestFocus() }, top = 4)
                } else {
                    EditorText.kindInfo(kind)?.let { view.add(small(activity.getString(it)), top = 14) }
                }
            }
        }

        private fun showSuggestions() {
            suggestions.removeAllViews()
            val typed = question.trim()
            for (q in BlankQuestions.suggestions(kind)) {
                suggestions.addView(ui.chip(q, selected = q.equals(typed, ignoreCase = true)) { questionField.setText(q); questionField.setSelection(q.length) })
            }
        }

        private fun firstRow(word: String): View = with(ui) {
            val r = min16()
            text(activity.getString(R.string.editor_answer_first, word), look.type.bodySp, 600, palette.ink).apply {
                setPadding(dp(16), dp(14), dp(16), dp(14))
                minHeight = dp(Ui.MAIN_ACTION_DP)
                gravity = Gravity.CENTER_VERTICAL
                background = shape(withAlpha(palette.accent, 0x26), r)
            }
        }

        private fun min16() = kotlin.math.min(ui.look.shape.cardRadiusDp, 16).toFloat()

        private fun addRow(value: String): EditText = with(ui) {
            val line = row()
            val field = field(activity.getString(R.string.editor_answer_hint)).apply { setText(value) }
            answerFields += field
            line.addView(field, lp(width = 0, weight = 1f))
            val remove = iconButton(
                Icons.Kind.CLOSE,
                if (value.isBlank()) activity.getString(R.string.editor_answer_remove_empty) else activity.getString(R.string.editor_answer_remove, value),
            ) {
                answerFields.remove(field)
                rows.removeView(line)
            }
            line.addView(remove)
            rows.addView(line, lp(bottom = 8))
            field
        }
    }

    private fun details(panel: UiPanel, selection: Selection, kind: BlankKind) {
        val current = draft()
        val word = current.textOf(selection)
        val body = panel.body
        body.removeAllViews()
        // For a number the unit stays outside: "5ml" asks about "5".
        val form = Form(kind, BlankQuestions.default(kind), if (kind == BlankKind.LIST) word else null, emptyList())
        with(ui) {
            body.add(kindHeader(kind, word))
            body.add(form.view, top = 16)
        }
        setFooter(
            panel,
            listOf(
                ui.primaryButton(activity.getString(R.string.editor_make_it)) {
                    panel.dismiss()
                    onChange(draft().makeBlank(selection, kind, form.question, form.answers))
                },
                ui.textButton(activity.getString(R.string.editor_back)) { choose(panel, selection, selection) },
            ),
        )
    }

    private fun kindHeader(kind: BlankKind, word: String): View = with(ui) {
        row().apply {
            addView(text(kind.glyph, 26f, 400, palette.ink), lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, end = 10))
            val names = column()
            names.addView(text(activity.getString(EditorText.kindName(kind)), look.type.headingSp, look.type.headingWeight, palette.ink))
            names.add(small("“$word”"), top = 2)
            addView(names, lp(width = 0, weight = 1f))
        }
    }

    // ---- a word that already changes ----

    private fun editor(panel: UiPanel, spec: BlankSpec) {
        val body = panel.body
        val form = Form(spec.kind, spec.question, null, if (spec.kind == BlankKind.LIST) spec.answers else emptyList())
        with(ui) {
            body.add(kindHeader(spec.kind, spec.example.ifBlank { spec.question }))
            body.add(form.view, top = 16)
        }
        setFooter(
            panel,
            listOf(
                ui.primaryButton(activity.getString(R.string.editor_done)) {
                    panel.dismiss()
                    var next = draft().renameQuestion(spec.id, form.question)
                    if (spec.kind == BlankKind.LIST) next = next.setAnswers(spec.id, form.answers)
                    onChange(next)
                },
                ui.textButton(activity.getString(R.string.editor_plain_again)) {
                    panel.dismiss()
                    onChange(draft().unmakeBlank(spec.id))
                }.also { it.setTextColor(palette.danger) },
            ),
        )
    }

    private fun offsetEditor(panel: UiPanel, segment: Int, ref: Segment.Ref, spec: BlankSpec) {
        val resId = if (ref.offsetMinutes > 0) R.string.editor_offset_info else R.string.editor_offset_info_minus
        val message = activity.getString(resId, spec.question, EditorText.duration(activity, ref.offsetMinutes)) +
            " " + activity.getString(R.string.editor_time_help)
        with(ui) {
            panel.body.add(kindHeader(BlankKind.TIME, ref.shown))
            panel.body.add(body(message), top = 12)
        }
        setFooter(
            panel,
            listOf(
                ui.primaryButton(activity.getString(R.string.editor_ok)) { panel.dismiss() },
                ui.textButton(activity.getString(R.string.editor_plain_again)) {
                    panel.dismiss()
                    onChange(draft().unmakeRef(segment))
                }.also { it.setTextColor(palette.danger) },
            ),
        )
    }

    private fun fixedText(fixed: Segment.Fixed): Int = when (fixed.raw.trim().substringBefore('|').trim().lowercase().takeWhile { it.isLetter() }) {
        "now" -> R.string.editor_fixed_now
        "today" -> R.string.editor_fixed_today
        else -> R.string.editor_fixed_other
    }

    private fun info(panel: UiPanel, resId: Int) {
        with(ui) {
            panel.body.add(body(activity.getString(resId)), top = 4)
        }
        setFooter(panel, listOf(ui.primaryButton(activity.getString(R.string.editor_ok)) { panel.dismiss() }))
    }

    private companion object {
        const val FOOTER_TAG = "sheet-footer"
    }
}
