package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.look.TileColors
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.ui.editor.AddFlow
import io.github.selementpecher.shortcutkeyboard.ui.editor.BlankKind
import io.github.selementpecher.shortcutkeyboard.ui.editor.ChainPanel
import io.github.selementpecher.shortcutkeyboard.ui.editor.ChipPreview
import io.github.selementpecher.shortcutkeyboard.ui.editor.ChipStyle
import io.github.selementpecher.shortcutkeyboard.ui.editor.ClockBlank
import io.github.selementpecher.shortcutkeyboard.ui.editor.EditorText
import io.github.selementpecher.shortcutkeyboard.ui.editor.MessageDraft
import io.github.selementpecher.shortcutkeyboard.ui.editor.MessageEditText
import io.github.selementpecher.shortcutkeyboard.ui.editor.Segment
import io.github.selementpecher.shortcutkeyboard.ui.editor.shownText
import java.time.LocalDateTime
import java.util.Locale

/**
 * Makes or changes a shortcut in three small steps: name and picture, message, check and save.
 *
 * The message is written in a box where the parts that change sit inside the words as coloured chips,
 * put there with "Add something that changes" (a chain of big choices, see [ChainPanel]). Nothing on screen
 * shows curly brackets: [MessageDraft] turns the chips into the stored template.
 *
 * Start with [EXTRA_ID] to change a saved shortcut (it opens on the check step); without it, or if
 * it no longer exists, a new shortcut is made.
 */
class EditShortcutActivity : Activity(), ChainPanel.Listener {

    private enum class Step { NAME, MESSAGE, CHECK }

    /** The chain of choices while it is open. */
    private class Chain(
        val flow: AddFlow,
        /** The message as it was before the chain opened. */
        val original: MessageDraft,
        /** The message without the words the chip replaces. */
        val base: MessageDraft,
        val offset: Int,
        val selStart: Int,
        val selEnd: Int,
        /** The number of the chip being changed, or the one being made. */
        val chipIndex: Int,
        val adding: Boolean,
    )

    private lateinit var ui: Ui
    private lateinit var screen: UiScreen
    private lateinit var panel: ChainPanel

    private var step = Step.NAME
    private var stored: Shortcut? = null
    private var baseline: Shortcut? = null
    private lateinit var shortcutId: String
    private var parentId: String? = null
    private var position = 0

    private var name = ""
    private var icon = ""
    private var color = -1
    private var draft = MessageDraft(emptyList())
    private var nameError = false
    private var messageError = false

    private var dialog: UiPanel? = null

    private var messageEdit: MessageEditText? = null
    private var pinned: LinearLayout? = null
    private var syncing = false
    private var caretStart = -1
    private var caretEnd = -1
    private var chain: Chain? = null

    /** A chip to open for changing as soon as the message step is drawn (tapped on the check step). */
    private var openChipAfterDraw = -1
    private var hintAfterDraw = false
    private var messageErrorLine: TextView? = null

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
        panel = ChainPanel(this, ui, screen, this)
        render()
        savedInstanceState?.let { restoreChain(it) }
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
        // While the chain is open the draft holds a half-made chip: keep the message as it was and the chain beside it.
        val open = chain
        outState.putString(STATE_DRAFT, (open?.original ?: draft).toJson())
        messageEdit?.let {
            caretStart = it.selectionStart
            caretEnd = it.selectionEnd
        }
        outState.putInt(STATE_CARET_START, caretStart)
        outState.putInt(STATE_CARET_END, caretEnd)
        if (open != null) {
            outState.putString(STATE_CHAIN_FLOW, open.flow.toJson())
            outState.putString(STATE_CHAIN_BASE, open.base.toJson())
            outState.putInt(STATE_CHAIN_OFFSET, open.offset)
            outState.putInt(STATE_CHAIN_SEL_START, open.selStart)
            outState.putInt(STATE_CHAIN_SEL_END, open.selEnd)
            outState.putInt(STATE_CHAIN_CHIP, open.chipIndex)
            outState.putBoolean(STATE_CHAIN_ADDING, open.adding)
        }
    }

    private fun restore(state: Bundle) {
        step = Step.entries.firstOrNull { it.name == state.getString(STATE_STEP) } ?: step
        name = state.getString(STATE_NAME, name)
        icon = state.getString(STATE_ICON, icon)
        color = state.getInt(STATE_COLOR, color)
        state.getString(STATE_DRAFT)?.let { json -> MessageDraft.fromJson(json)?.let { draft = it } }
        caretStart = state.getInt(STATE_CARET_START, -1)
        caretEnd = state.getInt(STATE_CARET_END, -1)
    }

    /** Opens the chain again after the phone was turned in the middle of it. */
    private fun restoreChain(state: Bundle) {
        val json = state.getString(STATE_CHAIN_FLOW) ?: return
        if (step != Step.MESSAGE) return
        val flow = AddFlow.fromJson(json) ?: return
        val base = state.getString(STATE_CHAIN_BASE)?.let { MessageDraft.fromJson(it) } ?: return
        val chain = Chain(
            flow = flow,
            original = draft,
            base = base,
            offset = state.getInt(STATE_CHAIN_OFFSET),
            selStart = state.getInt(STATE_CHAIN_SEL_START),
            selEnd = state.getInt(STATE_CHAIN_SEL_END),
            chipIndex = state.getInt(STATE_CHAIN_CHIP),
            adding = state.getBoolean(STATE_CHAIN_ADDING),
        )
        startChain(chain)
    }

    override fun onDestroy() {
        dialog?.dismiss()
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
        messageEdit = null
        render()
    }

    private fun goBack() {
        if (chain != null) {
            panel.back()
            return
        }
        when (step) {
            Step.NAME -> leave()
            Step.MESSAGE -> goTo(if (isNew) Step.NAME else Step.CHECK)
            Step.CHECK -> if (isNew) goTo(Step.MESSAGE) else leave()
        }
    }

    /**
     * Leaves the screen. Changes are kept: a quiKey that is complete (it has a name and a message) is saved on the way
     * out. Only a half-made one, which cannot be saved, asks first.
     */
    private fun leave() {
        if (!hasChanges()) {
            finish()
            return
        }
        if (name.isNotBlank() && draft.plainText().isNotBlank()) {
            save()
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
        val before = baseline ?: return name.isNotBlank() || icon.isNotBlank() || color >= 0 || draft.segments.isNotEmpty()
        return currentShortcut() != before
    }

    // ---- save and delete ----

    /** Saves. From the Name or Message step of an existing quiKey ([thenCheck]) it goes on to the Check screen instead of closing. */
    private fun save(thenCheck: Boolean = false) {
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
        val shortcut = currentShortcut()
        try {
            ShortcutRepository.save(this, shortcut)
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the shortcut", e)
            screen.snack(getString(R.string.editor_save_failed), error = true)
            return
        }
        if (thenCheck) {
            stored = shortcut
            baseline = ShortcutDraft.normalise(shortcut)
            goTo(Step.CHECK)
            screen.snack(getString(R.string.editor_saved))
            return
        }
        // This screen closes at once, so the home screen says it.
        Snackbars.later(getString(R.string.editor_saved))
        finish()
    }

    /** Makes a copy of this quiKey as it is saved, then goes back to the home screen. */
    private fun duplicate() {
        try {
            ShortcutRepository.duplicateMany(this, setOf(shortcutId), getString(R.string.copy_suffix))
        } catch (e: Exception) {
            Log.w(TAG, "Could not copy the shortcut", e)
            screen.snack(getString(R.string.editor_save_failed), error = true)
            return
        }
        Snackbars.later(getString(R.string.editor_copied))
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
        unpinMessage()
        screen.content.removeAllViews()
        screen.bottomBar.removeAllViews()
        screen.showBottomBar(true)
        when (step) {
            Step.NAME -> buildName()
            Step.MESSAGE -> buildMessage()
            Step.CHECK -> buildCheck()
        }
        screen.scroll.scrollTo(0, 0)
    }

    /** A slim progress bar and "Step 2 of 3" under the title, only while making a new shortcut. */
    /** The three steps as a slim bar with their names, at the very top: where you are, and how far there is to go. */
    private fun stepBar(current: Int) {
        with(ui) {
            val names = listOf(R.string.editor_step_name, R.string.editor_step_message, R.string.editor_step_check)
            val box = column().apply {
                contentDescription = getString(R.string.editor_step_of, current, TOTAL_STEPS) + ": " + getString(names[current - 1])
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
            val segments = row()
            val labels = row()
            for (i in 1..TOTAL_STEPS) {
                segments.addView(progressBar(if (i <= current) 1f else 0f, 6), lp(width = 0, weight = 1f, start = if (i == 1) 0 else 6, height = dp(6)))
                labels.addView(
                    text(getString(names[i - 1]), look.type.smallSp, if (i == current) 700 else 500, if (i == current) palette.ink else palette.inkSoft).apply {
                        gravity = Gravity.START
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    lp(width = 0, weight = 1f, start = if (i == 1) 0 else 6),
                )
            }
            box.addView(segments, lp())
            box.add(labels, top = 8)
            screen.content.add(box, bottom = 20)
        }
    }

    /**
     * The buttons at the bottom of the Name and Message steps. A new quiKey has just Next. One that already
     * exists also gets Save above it, so a small change never needs the steps after it.
     */
    private fun addStepButtons(onNext: () -> Unit) {
        with(ui) {
            if (isNew) {
                screen.bottomBar.addView(primaryButton(getString(R.string.editor_next)) { onNext() })
            } else {
                screen.bottomBar.addView(primaryButton(getString(R.string.editor_save)) { save(thenCheck = true) })
                screen.bottomBar.add(secondaryButton(getString(R.string.editor_next)) { onNext() }, top = 10)
            }
        }
    }

    private fun errorLine(textId: Int, visible: Boolean): TextView = with(ui) {
        text(getString(textId), look.type.smallSp, 600, palette.danger).apply {
            visibility = if (visible) View.VISIBLE else View.GONE
            layoutParams = lp(top = 8)
        }
    }

    private fun buildName() {
        val content = screen.content
        with(ui) {
            stepBar(1)
            content.add(title(getString(R.string.editor_name_title)))
            content.add(small(getString(R.string.editor_name_help)), top = 12)

            val line = row()
            val field = field(getString(R.string.editor_name_hint)).apply {
                setText(name)
                imeOptions = EditorInfo.IME_ACTION_NEXT
                setOnEditorActionListener { _, _, _ -> next(); true }
            }
            val error = errorLine(R.string.editor_name_error, nameError)
            field.addTextChangedListener(watcher { name = it; if (nameError) { nameError = false; error.visibility = View.GONE } })
            line.addView(pictureButton({ icon }, { name }) { icon = it }, LinearLayout.LayoutParams(dp(Ui.MAIN_ACTION_DP), dp(Ui.MAIN_ACTION_DP)).apply { marginEnd = dp(14) })
            line.addView(field, lp(width = 0, weight = 1f))
            content.add(line, top = 24)
            content.add(error)
            content.add(ColorPicker.collapsible(ui, color) { color = it }, top = 16)

            addStepButtons { next() }
            if (isNew && name.isEmpty()) showKeyboardFor(field)
        }
    }

    // ---- step 2: the message ----

    private fun buildMessage() {
        val content = screen.content
        with(ui) {
            stepBar(2)
            content.add(title(getString(R.string.editor_message_title)))
            content.add(small(getString(R.string.editor_message_help)), top = 12)

            val edit = messageEdit ?: createMessageEdit()
            (edit.parent as? ViewGroup)?.removeView(edit)
            edit.minLines = 4
            edit.maxLines = 12
            content.add(edit, top = 16)
            val error = errorLine(R.string.editor_message_error, messageError)
            messageErrorLine = error
            content.add(error)
            content.add(
                secondaryButton(getString(R.string.editor_add), Icons.Kind.PLUS) { openAdd() }.apply {
                    contentDescription = getString(R.string.editor_add)
                    // One line at any text size: the words shrink a little before they would wrap.
                    maxLines = 1
                    setAutoSizeTextTypeUniformWithConfiguration(14, look.type.bodySp.toInt().coerceAtLeast(14), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                },
                top = 14,
            )
            if (draft.chips().isNotEmpty()) content.add(small(getString(R.string.editor_message_tap)), top = 12)

            addStepButtons {
                if (draft.plainText().isBlank()) {
                    messageError = true
                    error.visibility = View.VISIBLE
                } else {
                    goTo(Step.CHECK)
                }
            }
            val caret = if (caretStart in 0..draft.length) caretStart else draft.length
            renderMessage(caret, if (caretEnd in 0..draft.length) caretEnd else caret)
            if (openChipAfterDraw >= 0) {
                val index = openChipAfterDraw
                openChipAfterDraw = -1
                edit.post { if (!isFinishing && !isDestroyed) openEdit(index) }
            } else if (hintAfterDraw) {
                hintAfterDraw = false
                edit.post { if (!isFinishing && !isDestroyed) screen.snack(getString(R.string.editor_change_answers_hint)) }
            } else if (draft.segments.isEmpty() && messageEdit === edit && chain == null && isNew) {
                showKeyboardFor(edit)
            }
        }
    }

    private fun createMessageEdit(): MessageEditText {
        val edit = MessageEditText(this, ui)
        edit.hint = getString(R.string.editor_message_hint)
        edit.contentDescription = getString(R.string.editor_message_desc)
        edit.onChipTap = { index -> openEdit(index) }
        edit.addTextChangedListener(watcher {
            if (syncing) return@watcher
            draft = draft.withEditableText(edit.editableText(), edit.chipSegments())
            edit.restyle(::styleFor)
            if (messageError && draft.plainText().isNotBlank()) {
                messageError = false
                messageErrorLine?.visibility = View.GONE
            }
        })
        messageEdit = edit
        return edit
    }

    /** Puts [draft] into the message box with the caret at [start] to [end]. */
    private fun renderMessage(start: Int, end: Int = start) {
        val edit = messageEdit ?: return
        syncing = true
        try {
            edit.render(draft, start, end, ::styleFor)
        } finally {
            syncing = false
        }
    }

    /** The colour and words of chip number [index]. */
    private fun styleFor(index: Int, segment: Segment): ChipStyle {
        val palette = ui.palette
        val open = chain
        val pending = if (open != null) open.chipIndex.takeIf { it >= 0 } ?: pendingChipIndex(open) else -1
        val ring = if (index == pending) palette.ink else null
        if (segment is Segment.Fixed) {
            val glyph = if (segment.shown.contains(':')) "🕒" else "📅"
            return ChipStyle(glyph, segment.shown, palette.surface, palette.ink, ring, dashed = true)
        }
        val ref = segment as Segment.Ref
        val spec = draft.blank(ref.blankId)
        val order = draft.questions().indexOfFirst { it.blankId == ref.blankId }.coerceAtLeast(0)
        val colours: TileColors = palette.tiles[order % palette.tiles.size]
        if (spec == null) return ChipStyle("", ref.shown, colours.bg, colours.fg, ring)
        val provisional = open != null && index == pending && (open.flow.preview() as? ChipPreview.Blank)?.named == false
        val label = when {
            provisional -> spec.question.ifBlank { getString(EditorText.kindName(spec.kind)) }
            ref.offsetMinutes != 0L -> EditorText.referenceLabel(this, spec.question, ref.offsetMinutes)
            spec.fixed -> ref.shown
            else -> spec.question
        }
        return ChipStyle(spec.kind.glyph, label, colours.bg, colours.fg, ring)
    }

    /** Which chip the chain is making: the one at its offset. */
    private fun pendingChipIndex(open: Chain): Int =
        (0 until open.base.chips().size).count { open.base.chipOffset(it) < open.offset }

    // ---- the chain of choices ----

    /** "Add something that changes" was pressed. */
    private fun openAdd() {
        val edit = messageEdit ?: return
        val size = draft.length
        val a = edit.selectionStart.let { if (it < 0) size else it.coerceIn(0, size) }
        val b = edit.selectionEnd.let { if (it < 0) size else it.coerceIn(0, size) }
        val start = minOf(a, b)
        val end = maxOf(a, b)
        val selected = draft.placeholderText().substring(start, end).replace(MessageDraft.PLACEHOLDER.toString(), "")
        val base = if (start < end) draft.deleteRange(start, end) else draft
        val flow = AddFlow.start(
            taken = base.blanks.map { it.question }.toSet(),
            clockBlanks = clockBlanks(base),
            selected = selected,
            now = LocalDateTime.now(),
            locale = Locale.getDefault(),
        )
        startChain(Chain(flow, draft, base, start, start, end, chipIndex = -1, adding = true))
    }

    /** A chip was tapped: change it. */
    private fun openEdit(index: Int) {
        chain?.let { open ->
            // Tapping another pill while one is open: drop the unfinished one and open the tapped pill.
            val current = if (open.adding) pendingChipIndex(open) else open.chipIndex
            if (index == current) return
            val target = if (open.adding && index > current) index - 1 else index
            draft = open.original
            openChipAfterDraw = target
            closeChain(open.selStart, open.selEnd)
            return
        }
        val segment = draft.chips().getOrNull(index) ?: return
        val offset = draft.chipOffset(index)
        val ref = segment as? Segment.Ref
        if (ref == null) {
            showFixedInfo(index, segment as Segment.Fixed)
            return
        }
        val spec = draft.blank(ref.blankId) ?: return
        val now = LocalDateTime.now()
        val locale = Locale.getDefault()
        val flow = if (ref.offsetMinutes != 0L) {
            AddFlow.editReference(spec.id, ref.offsetMinutes, index, clockBlanks(draft), now, locale)
        } else {
            AddFlow.edit(spec, index, draft.blanks.filter { it.id != spec.id }.map { it.question }.toSet(), clockBlanks(draft), now, locale)
        }
        startChain(Chain(flow, draft, draft, offset, offset + 1, offset + 1, index, adding = false))
    }

    private fun clockBlanks(from: MessageDraft): List<ClockBlank> =
        from.blanks.filter { it.kind == BlankKind.TIME }.map { ClockBlank(it.id, it.question) }

    private fun startChain(open: Chain) {
        val edit = messageEdit ?: return
        chain = open
        edit.lock(true)
        hideKeyboard()
        screen.content.removeAllViews()
        screen.bottomBar.removeAllViews()
        pinMessage(edit)
        screen.content.addView(panel.view, ui.lp())
        panel.show(open.flow)
        applyChain()
        screen.scroll.scrollTo(0, 0)
    }

    /** Shows the message with the half-made chip in it. */
    private fun applyChain() {
        val open = chain ?: return
        draft = open.flow.applyTo(open.base, open.offset)
        val caret = if (open.adding) open.offset + (if (draft.length > open.base.length) 1 else 0) else open.offset + 1
        renderMessage(caret)
        messageEdit?.let { edit -> edit.post { edit.bringIntoView(caret) } }
    }

    private fun MessageEditText.bringIntoView(offset: Int) {
        if (android.os.Build.VERSION.SDK_INT >= 29) bringPointIntoView(offset.coerceIn(0, length()))
    }

    override fun onChanged() = applyChain()

    override fun onFinished() {
        val open = chain ?: return
        draft = open.flow.applyTo(open.base, open.offset)
        val index = if (open.adding) pendingChipIndex(open) else open.chipIndex
        val caret = open.offset + 1
        closeChain(caret, caret, popChip = index)
    }

    override fun onCancelled() {
        val open = chain ?: return
        draft = open.original
        closeChain(open.selStart, open.selEnd)
    }

    override fun onMakePlain() {
        val open = chain ?: return
        draft = open.original.makePlain(open.chipIndex)
        val caret = open.offset + open.original.chips()[open.chipIndex].shownText.length
        closeChain(caret, caret)
    }

    override fun onRemove() {
        val open = chain ?: return
        draft = open.original.removeChip(open.chipIndex)
        closeChain(open.offset, open.offset)
    }

    /** Closes the chain and goes back to the message, with the caret at [start] to [end]. */
    private fun closeChain(start: Int, end: Int, popChip: Int = -1) {
        val edit = messageEdit
        chain = null
        panel.clear()
        edit?.lock(false)
        caretStart = start
        caretEnd = end
        unpinMessage()
        render()
        if (popChip >= 0) messageEdit?.popChip(popChip)
    }

    /** A chip the phone fills in by itself: explains it and offers to make it plain words or remove it. */
    private fun showFixedInfo(index: Int, fixed: Segment.Fixed) {
        dialog?.dismiss()
        val key = fixed.raw.trim().substringBefore('|').trim().lowercase().takeWhile { it.isLetter() }
        val text = when (key) {
            "now" -> R.string.editor_fixed_now
            "today" -> R.string.editor_fixed_today
            else -> R.string.editor_fixed_other
        }
        dialog = ui.ask(
            this,
            getString(R.string.editor_fixed_title),
            getString(text),
            listOf(
                DialogButton(getString(R.string.editor_ok)),
                DialogButton(getString(R.string.chain_make_plain), ButtonStyle.SECONDARY) {
                    val at = draft.chipOffset(index)
                    draft = draft.makePlain(index)
                    caretStart = at + fixed.shownText.length
                    caretEnd = caretStart
                    render()
                    true
                },
                DialogButton(getString(R.string.chain_remove), ButtonStyle.DANGER) {
                    val at = draft.chipOffset(index)
                    draft = draft.removeChip(index)
                    caretStart = at
                    caretEnd = at
                    render()
                    true
                },
            ),
        ).show()
    }

    /** While the chain is open the message stays on screen above it, outside the scrolling part. */
    private fun pinMessage(edit: MessageEditText) {
        unpinMessage()
        (edit.parent as? ViewGroup)?.removeView(edit)
        edit.minLines = 2
        edit.maxLines = 5
        val holder = ui.column().apply {
            setPadding(ui.dp(TileSpec.SCREEN_MARGIN_DP), ui.dp(4), ui.dp(TileSpec.SCREEN_MARGIN_DP), ui.dp(12))
            addView(edit, ui.lp())
        }
        screen.root.addView(holder, 1, ui.lp())
        pinned = holder
    }

    private fun unpinMessage() {
        val holder = pinned ?: return
        holder.removeAllViews()
        (holder.parent as? ViewGroup)?.removeView(holder)
        pinned = null
    }

    // ---- step 3: check and save ----

    /** Try the new quiKey: tap the box and pick it from the keyboard, so it is typed here. */
    private fun tryCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.settings_try_title)))
        card.add(small(getString(R.string.try_tip)), top = 4)
        card.add(field(getString(R.string.try_hint)), top = 14)
        card
    }

    private fun buildCheck() {
        val content = screen.content
        with(ui) {
            stepBar(3)
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
            nameCard.addView(pictureCircle(52, 26f), LinearLayout.LayoutParams(dp(Ui.MAIN_ACTION_DP), dp(Ui.MAIN_ACTION_DP)).apply { marginEnd = dp(14) })
            val names = column()
            names.addView(heading(name.ifBlank { getString(R.string.editor_title_new) }).apply { maxLines = 2 })
            names.add(small(getString(R.string.editor_name_row_hint)), top = 2)
            nameCard.addView(names, lp(width = 0, weight = 1f))
            content.add(nameCard, top = if (isNew) 20 else 0)

            content.add(exampleCard(), top = 20)

            val asks = draft.questions()
            if (asks.isEmpty()) {
                content.add(body(getString(R.string.editor_check_nothing)), top = 16)
            } else {
                val card = card(8)
                asks.forEachIndexed { index, q ->
                    val tile = palette.tiles[index % palette.tiles.size]
                    val asked = q.fixedAmount == null
                    val title = if (asked) q.question.replaceFirstChar { it.uppercase() } + "?" else q.question.replaceFirstChar { it.uppercase() }
                    val extra = EditorText.answersLine(this@EditShortcutActivity, q)
                    val line = row().apply {
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                        minimumHeight = dp(56)
                        background = rippled(shape(android.graphics.Color.TRANSPARENT, 14f), 14f)
                        isClickable = true
                        isFocusable = true
                        contentDescription = getString(R.string.editor_check_row_desc, listOfNotNull(title, extra).joinToString(", "))
                        setOnClickListener { editQuestion(q.blankId) }
                    }
                    line.addView(
                        text(q.kind.glyph, 18f, 400, tile.fg).apply {
                            gravity = Gravity.CENTER
                            background = shape(tile.bg, 999f)
                        },
                        lp(width = dp(40), height = dp(40), end = 12),
                    )
                    val words = column()
                    words.addView(text(title, look.type.bodySp, 600, palette.ink).apply { maxLines = 2 })
                    if (extra != null) words.add(small(extra), top = 1)
                    line.addView(words, lp(width = 0, weight = 1f))
                    line.addView(
                        ImageView(context).apply {
                            setImageDrawable(icon(Icons.Kind.FORWARD, 20, palette.inkSoft))
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        },
                        lp(width = dp(20), height = dp(20), start = 8),
                    )
                    card.addView(line)
                }
                content.add(card, top = 16)
            }

            val problem = errorLine(R.string.editor_message_error, draft.plainText().isBlank())
            content.add(problem)

            content.add(tryCard(), top = 20)
            content.add(secondaryButton(getString(R.string.editor_change_words)) { goTo(Step.MESSAGE) }, top = 20)
            if (!isNew) {
                content.add(secondaryButton(getString(R.string.editor_duplicate)) { duplicate() }, top = 12)
                content.add(
                    dangerButton(getString(R.string.editor_delete), Icons.Kind.TRASH) { confirmDelete() },
                    top = 12,
                )
            }
            screen.bottomBar.addView(primaryButton(getString(R.string.editor_save)) { save() })
        }
    }

    /** Goes to the message and opens the first use of question [blankId] for changing. */
    private fun editQuestion(blankId: Int) {
        val index = draft.chips().indexOfFirst { it is Segment.Ref && it.blankId == blankId && it.offsetMinutes == 0L }
            .let { if (it >= 0) it else draft.chips().indexOfFirst { c -> c is Segment.Ref && c.blankId == blankId } }
        openChipAfterDraw = index
        goTo(Step.MESSAGE)
    }

    private fun editAnswers(blankIds: List<Int>) {
        if (blankIds.size == 1) editQuestion(blankIds.first()) else {
            hintAfterDraw = true
            goTo(Step.MESSAGE)
        }
    }

    /** The example message, with the changing parts shown as soft highlights in their question's colour. */
    private fun exampleCard(): View = with(ui) {
        val card = card(20)
        val parts = ExampleParts.of(draft)
        val shown: CharSequence = if (parts.isEmpty()) "…" else ExampleText.build(
            parts = parts,
            colors = palette.tiles,
            neutral = TileColors(palette.surfaceAlt, palette.ink),
            paddingPx = dpf(6f),
            radiusPx = dpf(8f),
            verticalPx = dpf(2f),
        )
        card.addView(text(shown, look.type.bodySp + 1, 500, palette.ink, 1.45f))
        card
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
                goTo(Step.MESSAGE)
            }
            Step.MESSAGE -> goTo(Step.CHECK)
            Step.CHECK -> save()
        }
    }

    /** A round picture button: shows the chosen picture or a plus, and opens the picture grid. */
    private fun pictureButton(getIcon: () -> String, getName: () -> String, setIcon: (String) -> Unit): View = with(ui) {
        lateinit var circle: TextView
        fun refresh() {
            val chosen = getIcon()
            circle.text = if (chosen.isBlank()) "+" else glyphFor(chosen, getName())
        }
        circle = pictureCircle(Ui.MAIN_ACTION_DP, 26f).also { it.contentDescription = getString(R.string.editor_picture_desc) }
        circle.setOnClickListener {
            IconPicker.show(this@EditShortcutActivity, ui, getIcon()) { picked -> setIcon(picked); refresh() }
        }
        refresh()
        circle
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
        var newColor = color
        var newName = name
        with(ui) {
            val panel = dialog(this@EditShortcutActivity, getString(R.string.editor_edit_name_title))
            val field = field(getString(R.string.editor_name_hint)).apply { setText(newName) }
            val error = errorLine(R.string.editor_name_error, false)
            field.addTextChangedListener(watcher { newName = it })
            val line = row()
            line.addView(pictureButton({ newIcon }, { newName }) { newIcon = it }, LinearLayout.LayoutParams(dp(Ui.MAIN_ACTION_DP), dp(Ui.MAIN_ACTION_DP)).apply { marginEnd = dp(14) })
            line.addView(field, lp(width = 0, weight = 1f))
            panel.body.add(line, top = 8)
            panel.body.add(error)
            panel.body.add(ColorPicker.collapsible(ui, color) { newColor = it }, top = 12)
            panel.buttons(
                listOf(
                    DialogButton(getString(R.string.editor_done)) {
                        if (newName.isBlank()) {
                            error.visibility = View.VISIBLE
                            false
                        } else {
                            name = newName.trim()
                            icon = newIcon
                            color = newColor
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

    private fun hideKeyboard() {
        val token = window.decorView.windowToken ?: return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(token, 0)
    }

    companion object {
        /** The id of the shortcut to change. Leave it out to make a new one. */
        const val EXTRA_ID = "id"

        /** The folder a new shortcut is made in (a folder id). Leave it out for the top level. */
        const val EXTRA_PARENT_ID = "parent_id"

        private const val TAG = "EditShortcutActivity"
        private const val TOTAL_STEPS = 3
        private const val STATE_ID = "id"
        private const val STATE_STEP = "step"
        private const val STATE_NAME = "name"
        private const val STATE_ICON = "icon"
        private const val STATE_COLOR = "color"
        private const val STATE_DRAFT = "draft"
        private const val STATE_CARET_START = "caretStart"
        private const val STATE_CARET_END = "caretEnd"
        private const val STATE_CHAIN_FLOW = "chainFlow"
        private const val STATE_CHAIN_BASE = "chainBase"
        private const val STATE_CHAIN_OFFSET = "chainOffset"
        private const val STATE_CHAIN_SEL_START = "chainSelStart"
        private const val STATE_CHAIN_SEL_END = "chainSelEnd"
        private const val STATE_CHAIN_CHIP = "chainChip"
        private const val STATE_CHAIN_ADDING = "chainAdding"
    }
}
