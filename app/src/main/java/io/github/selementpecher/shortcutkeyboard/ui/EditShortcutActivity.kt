package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.look.tileColorsFor
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf
import io.github.selementpecher.shortcutkeyboard.ui.editor.BlankKind
import io.github.selementpecher.shortcutkeyboard.ui.editor.EditorText
import io.github.selementpecher.shortcutkeyboard.ui.editor.MessageDraft
import io.github.selementpecher.shortcutkeyboard.ui.editor.Piece
import io.github.selementpecher.shortcutkeyboard.ui.editor.WordFlow
import io.github.selementpecher.shortcutkeyboard.ui.editor.WordSheets
import kotlin.math.max

/**
 * Makes or changes a shortcut, one small step at a time:
 * name, message, tap the words that change, check and save.
 *
 * Nothing on screen shows curly brackets: the message is written in plain words and the words that
 * change are tapped. [MessageDraft] turns that into the stored template.
 *
 * Start with [EXTRA_ID] to change a saved shortcut (it opens on the check step); without it, or if
 * it no longer exists, a new shortcut is made.
 */
class EditShortcutActivity : Activity() {

    private enum class Step { NAME, MESSAGE, WORDS, CHECK }

    private lateinit var ui: Ui
    private lateinit var screen: UiScreen
    private lateinit var sheets: WordSheets

    private var step = Step.NAME

    /** Where the back arrow goes from the "tap the words" step. */
    private var wordsBack = Step.MESSAGE
    private var stored: Shortcut? = null
    private var baseline: Shortcut? = null
    private lateinit var shortcutId: String
    private var parentId: String? = null
    private var position = 0

    private var name = ""
    private var icon = ""
    private var color = -1
    private var messageText = ""
    private var draft = MessageDraft(emptyList())

    /** How many changing words the last re-typing could not find again. */
    private var lost = 0
    private var nameError = false
    private var messageError = false

    private var dialog: UiPanel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui.of(this)

        val requestedId = intent?.getStringExtra(EXTRA_ID)
        stored = requestedId?.let { runCatching { ShortcutRepository.find(this, it) }.getOrNull() }
        val existing = stored
        shortcutId = existing?.id ?: savedInstanceState?.getString(STATE_ID) ?: ShortcutRepository.newId()
        parentId = if (existing != null) {
            existing.parentId
        } else {
            val wanted = intent?.getStringExtra(EXTRA_PARENT_ID)
            wanted?.let { id -> runCatching { ShortcutRepository.findFolder(this, id) }.getOrNull()?.id }
        }
        position = existing?.order ?: 0

        if (existing != null) {
            baseline = ShortcutDraft.normalise(existing)
            name = existing.name
            icon = existing.icon
            color = existing.color
            draft = MessageDraft.fromShortcut(existing)
            step = Step.CHECK
        }
        if (savedInstanceState != null) restore(savedInstanceState)

        screen = ui.screen(
            title = getString(if (existing == null) R.string.editor_title_new else R.string.editor_title_change),
            onBack = { goBack() },
            withBottomBar = true,
        )
        screen.install(this)
        sheets = WordSheets(this, ui, { draft }, { changed -> draft = changed; lost = 0; render() })
        render()
    }

    override fun onResume() {
        super.onResume()
        if (ui.isStale()) recreate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ID, shortcutId)
        outState.putString(STATE_STEP, step.name)
        outState.putString(STATE_NAME, name)
        outState.putString(STATE_ICON, icon)
        outState.putInt(STATE_COLOR, color)
        outState.putString(STATE_MESSAGE, messageText)
        outState.putString(STATE_DRAFT, draft.toJson())
        outState.putInt(STATE_LOST, lost)
        outState.putString(STATE_WORDS_BACK, wordsBack.name)
    }

    private fun restore(state: Bundle) {
        step = Step.entries.firstOrNull { it.name == state.getString(STATE_STEP) } ?: step
        name = state.getString(STATE_NAME, name)
        icon = state.getString(STATE_ICON, icon)
        color = state.getInt(STATE_COLOR, color)
        messageText = state.getString(STATE_MESSAGE, messageText)
        state.getString(STATE_DRAFT)?.let { json -> MessageDraft.fromJson(json)?.let { draft = it } }
        lost = state.getInt(STATE_LOST)
        wordsBack = Step.entries.firstOrNull { it.name == state.getString(STATE_WORDS_BACK) } ?: wordsBack
    }

    override fun onDestroy() {
        dialog?.dismiss()
        sheets.dismiss()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goBack()
    }

    // ---- moving between steps ----

    private val isNew: Boolean get() = stored == null

    private fun goTo(next: Step) {
        step = next
        render()
    }

    private fun goBack() {
        when (step) {
            Step.NAME -> leave()
            Step.MESSAGE -> if (isNew) goTo(Step.NAME) else { applyTypedMessage(); goTo(Step.CHECK) }
            Step.WORDS -> {
                if (wordsBack == Step.MESSAGE) messageText = draft.plainText()
                goTo(wordsBack)
            }
            Step.CHECK -> if (isNew) goTo(Step.WORDS) else leave()
        }
    }

    /** Leaves the screen, asking first when there is something that would be lost. */
    private fun leave() {
        if (!hasChanges()) {
            finish()
            return
        }
        dialog?.dismiss()
        dialog = ui.ask(
            this,
            getString(R.string.editor_unsaved_title),
            null,
            listOf(
                DialogButton(getString(R.string.editor_unsaved_save)) { save(); true },
                DialogButton(getString(R.string.editor_unsaved_discard), ButtonStyle.DANGER) { finish(); true },
                DialogButton(getString(R.string.editor_unsaved_keep), ButtonStyle.TEXT),
            ),
        ).show()
    }

    private fun currentShortcut(): Shortcut = draft.toShortcut(shortcutId, name, parentId, position, icon, color)

    private fun hasChanges(): Boolean {
        val typedElsewhere = step == Step.MESSAGE && messageText != draft.plainText()
        val before = baseline ?: return name.isNotBlank() || icon.isNotBlank() || color >= 0 || messageText.isNotBlank() ||
            draft.segments.isNotEmpty()
        return typedElsewhere || currentShortcut() != before
    }

    /** Takes what is typed in the message box into the draft, keeping the changing words it can still find. */
    private fun applyTypedMessage() {
        if (step != Step.MESSAGE) return
        val text = messageText.trim()
        val result = draft.withNewText(text)
        draft = result.draft
        lost = result.lost
        messageText = draft.plainText()
    }

    // ---- save and delete ----

    private fun save() {
        applyTypedMessage()
        if (name.isBlank()) {
            nameError = true
            goTo(Step.NAME)
            return
        }
        if (draft.plainText().isBlank()) {
            messageError = true
            goTo(Step.MESSAGE)
            return
        }
        try {
            ShortcutRepository.save(this, currentShortcut())
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the shortcut", e)
            Toast.makeText(this, R.string.editor_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.editor_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        dialog?.dismiss()
        dialog = ui.confirm(
            this,
            getString(R.string.editor_delete_title),
            getString(R.string.editor_delete_message, name.ifBlank { getString(R.string.editor_title_new) }),
            getString(R.string.editor_delete_confirm),
            getString(R.string.editor_cancel),
            danger = true,
        ) {
            try {
                ShortcutRepository.delete(this, shortcutId)
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete the shortcut", e)
            }
            finish()
        }.show()
    }

    // ---- drawing the steps ----

    private fun render() {
        screen.content.removeAllViews()
        screen.bottomBar.removeAllViews()
        when (step) {
            Step.NAME -> buildName()
            Step.MESSAGE -> buildMessage()
            Step.WORDS -> buildWords()
            Step.CHECK -> buildCheck()
        }
        screen.scroll.scrollTo(0, 0)
    }

    /** "Step 2 of 4", only while making a new shortcut. */
    private fun stepLabel(number: Int) {
        if (!isNew) return
        with(ui) { screen.content.add(label(getString(R.string.editor_step_of, number, 4)), bottom = 6) }
    }

    private fun errorLine(textId: Int, visible: Boolean): TextView = with(ui) {
        text(getString(textId), look.type.smallSp, 600, palette.danger).apply {
            visibility = if (visible) View.VISIBLE else View.GONE
            layoutParams = lp(top = 8)
        }
    }

    private fun buildName() {
        val content = screen.content
        stepLabel(1)
        with(ui) {
            content.add(title(getString(R.string.editor_name_title)))
            content.add(small(getString(R.string.editor_name_help)), top = 8)

            val line = row()
            val field = field(getString(R.string.editor_name_hint)).apply {
                setText(name)
                imeOptions = EditorInfo.IME_ACTION_NEXT
                setOnEditorActionListener { _, _, _ -> next(); true }
            }
            val error = errorLine(R.string.editor_name_error, nameError)
            field.addTextChangedListener(watcher { name = it; if (nameError) { nameError = false; error.visibility = View.GONE } })
            line.addView(pictureButton({ icon }, { name }) { icon = it }, lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, end = 14))
            line.addView(field, lp(width = 0, weight = 1f))
            content.add(line, top = 24)
            content.add(error)
            content.add(label(getString(R.string.color_label)), top = 24)
            content.add(ColorPicker.view(ui, color) { color = it }, top = 8)

            screen.bottomBar.addView(primaryButton(getString(R.string.editor_next)) { next() })
            if (isNew && name.isEmpty()) showKeyboardFor(field)
        }
    }

    private fun buildMessage() {
        val content = screen.content
        stepLabel(2)
        with(ui) {
            content.add(title(getString(R.string.editor_message_title)))
            content.add(small(getString(R.string.editor_message_help)), top = 8)
            content.add(
                text(getString(R.string.editor_message_example), look.type.bodySp, 600, palette.ink).apply {
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = shape(android.graphics.Color.TRANSPARENT, 14f, palette.outline, 1.5f, dashed = true)
                },
                top = 8,
            )
            val field = field(getString(R.string.editor_message_hint), multiline = true).apply { setText(messageText) }
            val error = errorLine(R.string.editor_message_error, messageError)
            field.addTextChangedListener(watcher { messageText = it; if (messageError) { messageError = false; error.visibility = View.GONE } })
            content.add(field, top = 20)
            content.add(error)
            screen.bottomBar.addView(primaryButton(getString(R.string.editor_next)) { next() })
            if (messageText.isEmpty()) showKeyboardFor(field)
        }
    }

    private fun buildWords() {
        val content = screen.content
        stepLabel(3)
        with(ui) {
            content.add(title(getString(R.string.editor_words_title)))
            content.add(body(getString(R.string.editor_words_hint)), top = 10)
            content.add(textButton(getString(R.string.cheat_open_link)) {
                startActivity(android.content.Intent(this@EditShortcutActivity, CheatSheetActivity::class.java))
            }, top = 4)
            if (lost > 0) {
                content.add(
                    text(resources.getQuantityString(R.plurals.editor_words_lost, lost, lost), look.type.bodySp, 600, palette.ink).apply {
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        background = shape(withAlpha(palette.accent, 0x26), 14f, palette.accent, 1.5f)
                    },
                    top = 14,
                )
            }
            val card = card(16)
            card.addView(wordFlow())
            content.add(card, top = 16)
            content.add(small(getString(R.string.editor_words_none)), top = 14)
            screen.bottomBar.addView(primaryButton(getString(R.string.editor_next)) { next() })
        }
    }

    /** The message as words to tap: plain words, punctuation, and the words that already change. */
    private fun wordFlow(): View {
        val flow = WordFlow(this, ui.dp(6), ui.dp(8)).apply { layoutParams = ui.lp() }
        val colourIndex = draft.questions().withIndex().associate { it.value.blankId to it.index }
        var tightNext = false
        var breakNext = false
        fun add(view: View, tight: Boolean = false) {
            val params = WordFlow.Params(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.tight = tight || tightNext
            params.lineBreakBefore = breakNext
            tightNext = false
            breakNext = false
            flow.addView(view, params)
        }
        for (piece in draft.pieces()) {
            when (piece) {
                is Piece.Word -> add(wordView(piece))
                is Piece.Chip -> add(chipView(piece, colourIndex))
                is Piece.Gap -> {
                    val shown = piece.text.replace('\n', ' ').trim()
                    val breaks = piece.text.contains('\n')
                    if (shown.isNotEmpty()) {
                        // Punctuation hugs the word before it; an opening bracket hugs the word after it.
                        add(ui.text(shown, ui.look.type.bodySp + 4, 700, ui.palette.inkSoft), tight = !piece.text.first().isWhitespace())
                        tightNext = !piece.text.last().isWhitespace()
                    }
                    if (breaks) {
                        breakNext = true
                        tightNext = false
                    }
                }
            }
        }
        return flow
    }

    private fun pill(): Float = ui.look.shape.buttonRadiusDp.toFloat()

    private fun wordView(piece: Piece.Word): View = with(ui) {
        val r = pill()
        TextView(context).apply {
            text = piece.text
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, look.type.bodySp + 1)
            typeface = typefaceOf(500)
            setTextColor(palette.ink)
            minHeight = dp(48)
            minWidth = dp(48)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = rippled(shape(palette.surfaceAlt, r, palette.outline, max(1f, look.shape.strokeDp)), r)
            isClickable = true
            isFocusable = true
            includeFontPadding = false
            contentDescription = getString(R.string.editor_word_desc, piece.text)
            setOnClickListener { sheets.openWord(piece.selection) }
        }
    }

    private fun chipView(piece: Piece.Chip, colourIndex: Map<Int, Int>): View = with(ui) {
        val r = pill()
        val spec = draft.blank(piece.blankId)
        val label: String
        val colours: io.github.selementpecher.shortcutkeyboard.look.TileColors
        val bg: android.graphics.drawable.Drawable
        if (spec == null) {
            // `now` or `today` from an older shortcut: shown as it will be, not asked about.
            label = piece.shown
            colours = io.github.selementpecher.shortcutkeyboard.look.TileColors(palette.surface, palette.ink)
            bg = rippled(shape(colours.bg, r, palette.inkSoft, 1.5f, dashed = true), r)
        } else {
            label = EditorText.chipLabel(context, spec.question, piece.offsetMinutes)
            colours = palette.tiles[(colourIndex[spec.id] ?: 0) % palette.tiles.size]
            bg = rippled(shape(colours.bg, r), r)
        }
        val glyph = spec?.kind?.glyph ?: if (piece.shown.contains(':')) "🕒" else "📅"
        TextView(context).apply {
            text = "$glyph  $label"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, look.type.bodySp + 1)
            typeface = typefaceOf(max(600, look.type.tileWeight))
            setTextColor(colours.fg)
            minHeight = dp(48)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = bg
            isClickable = true
            isFocusable = true
            includeFontPadding = false
            contentDescription = getString(R.string.editor_chip_desc, label)
            setOnClickListener { sheets.openChip(piece.segment) }
        }
    }

    private fun buildCheck() {
        val content = screen.content
        stepLabel(4)
        with(ui) {
            if (isNew) content.add(title(getString(R.string.editor_check_title)))

            // Name and picture: tap to change.
            val nameCard = card(14).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                contentDescription = name + ". " + getString(R.string.editor_name_row_hint)
                setOnClickListener { editNameAndPicture() }
            }
            nameCard.addView(pictureCircle(52, 26f), lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, end = 14))
            val names = column()
            names.addView(heading(name.ifBlank { getString(R.string.editor_title_new) }).apply { maxLines = 2 })
            names.add(small(getString(R.string.editor_name_row_hint)), top = 2)
            nameCard.addView(names, lp(width = 0, weight = 1f))
            content.add(nameCard, top = if (isNew) 16 else 0)

            content.add(label(getString(R.string.editor_check_asks)), top = 24)
            val asks = draft.questions()
            if (asks.isEmpty()) {
                content.add(body(getString(R.string.editor_check_nothing)), top = 8)
            } else {
                val card = card(8)
                asks.forEachIndexed { index, q ->
                    val line = row().apply { setPadding(dp(8), dp(8), dp(8), dp(8)); minimumHeight = dp(48) }
                    val dot = palette.tiles[index % palette.tiles.size]
                    line.addView(
                        text(q.kind.glyph, 18f, 400, dot.fg).apply {
                            gravity = Gravity.CENTER
                            background = shape(dot.bg, 999f)
                        },
                        lp(width = dp(40), height = dp(40), end = 12),
                    )
                    line.addView(text(q.question.replaceFirstChar { it.uppercase() } + "?", look.type.bodySp, 600, palette.ink), lp(width = 0, weight = 1f))
                    if (q.answerCount > 0) {
                        line.addView(
                            small(resources.getQuantityString(R.plurals.editor_check_answers, q.answerCount, q.answerCount)),
                            lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, start = 8),
                        )
                    }
                    card.addView(line)
                }
                content.add(card, top = 8)
            }

            content.add(label(getString(R.string.editor_check_types)), top = 24)
            val example = card(18)
            example.addView(text(draft.exampleMessage().ifBlank { "…" }, look.type.bodySp + 1, 500, palette.ink).apply { setTextIsSelectable(true) })
            content.add(example, top = 8)

            val problem = errorLine(R.string.editor_message_error, draft.plainText().isBlank())
            content.add(problem)

            val quiet = row()
            quiet.addView(
                textButton(getString(R.string.editor_change_words)) { messageText = draft.plainText(); goTo(Step.MESSAGE) },
                lp(width = 0, weight = 1f, end = 4),
            )
            quiet.addView(
                textButton(getString(R.string.editor_change_answers)) { wordsBack = Step.CHECK; goTo(Step.WORDS) },
                lp(width = 0, weight = 1f, start = 4),
            )
            content.add(quiet, top = 12)
            if (!isNew) {
                content.add(
                    textButton(getString(R.string.editor_delete)) { confirmDelete() }.also { it.setTextColor(palette.danger) },
                    top = 12,
                )
            }
            screen.bottomBar.addView(primaryButton(getString(R.string.editor_save)) { save() })
        }
    }

    // ---- small pieces ----

    private fun next() {
        when (step) {
            Step.NAME -> {
                name = name.trim()
                if (name.isEmpty()) {
                    nameError = true
                    render()
                    return
                }
                if (messageText.isBlank()) messageText = draft.plainText()
                goTo(Step.MESSAGE)
            }
            Step.MESSAGE -> {
                if (messageText.isBlank()) {
                    messageError = true
                    render()
                    return
                }
                applyTypedMessage()
                wordsBack = Step.MESSAGE
                goTo(Step.WORDS)
            }
            Step.WORDS -> goTo(Step.CHECK)
            Step.CHECK -> save()
        }
    }

    /** A round picture button: shows the chosen picture or a plus, and opens the picture grid. */
    private fun pictureButton(getIcon: () -> String, getName: () -> String, setIcon: (String) -> Unit): View = with(ui) {
        val column = column().apply { gravity = Gravity.CENTER_HORIZONTAL }
        lateinit var circle: TextView
        fun refresh() {
            val chosen = getIcon()
            circle.text = if (chosen.isBlank()) "＋" else glyphFor(chosen, getName())
        }
        circle = pictureCircle(64, 30f).also { it.contentDescription = getString(R.string.editor_picture_desc) }
        circle.setOnClickListener {
            IconPicker.show(this@EditShortcutActivity, ui, getIcon()) { picked -> setIcon(picked); refresh() }
        }
        refresh()
        column.addView(circle, LinearLayout.LayoutParams(dp(64), dp(64)))
        column.add(small(getString(R.string.editor_picture)), top = 4)
        column
    }

    private fun pictureCircle(sizeDp: Int, sizeSp: Float): TextView = with(ui) {
        text(glyphFor(icon, name), sizeSp, 400, palette.ink).apply {
            gravity = Gravity.CENTER
            background = rippled(shape(withAlpha(palette.accent, 0x30), 999f, palette.accent, 1.5f), 999f)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
    }

    /** A small dialog to change the name and the picture from the check step. */
    private fun editNameAndPicture() {
        dialog?.dismiss()
        var newIcon = icon
        var newName = name
        with(ui) {
            val panel = dialog(this@EditShortcutActivity, getString(R.string.editor_edit_name_title))
            val field = field(getString(R.string.editor_name_hint)).apply { setText(newName) }
            val error = errorLine(R.string.editor_name_error, false)
            field.addTextChangedListener(watcher { newName = it })
            val line = row()
            line.addView(pictureButton({ newIcon }, { newName }) { newIcon = it }, lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, end = 14))
            line.addView(field, lp(width = 0, weight = 1f))
            panel.body.add(line, top = 8)
            panel.body.add(error)
            panel.buttons(
                listOf(
                    DialogButton(getString(R.string.editor_done)) {
                        if (newName.isBlank()) {
                            error.visibility = View.VISIBLE
                            false
                        } else {
                            name = newName.trim()
                            icon = newIcon
                            render()
                            true
                        }
                    },
                    DialogButton(getString(R.string.editor_cancel), ButtonStyle.TEXT),
                ),
            )
            dialog = panel.show()
        }
    }

    private fun watcher(onText: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onText(s?.toString().orEmpty())
    }

    private fun showKeyboardFor(field: EditText) {
        field.post {
            if (isFinishing || isDestroyed) return@post
            if (field.requestFocus()) {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(field, 0)
            }
        }
    }

    companion object {
        /** The id of the shortcut to change. Leave it out to make a new one. */
        const val EXTRA_ID = "id"

        /** The folder a new shortcut is made in (a folder id). Leave it out for the top level. */
        const val EXTRA_PARENT_ID = "parent_id"

        private const val TAG = "EditShortcutActivity"
        private const val STATE_ID = "id"
        private const val STATE_STEP = "step"
        private const val STATE_NAME = "name"
        private const val STATE_ICON = "icon"
        private const val STATE_COLOR = "color"
        private const val STATE_MESSAGE = "message"
        private const val STATE_DRAFT = "draft"
        private const val STATE_LOST = "lost"
        private const val STATE_WORDS_BACK = "wordsBack"
    }
}
