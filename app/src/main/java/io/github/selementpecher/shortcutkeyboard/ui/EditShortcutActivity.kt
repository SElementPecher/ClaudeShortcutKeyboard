package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template

/**
 * Makes a new shortcut or changes one: its name, message and the settings of each
 * blank, with a live preview.
 *
 * Start with [EXTRA_ID] to change a saved shortcut; without it (or if it no longer exists) a new
 * one is made.
 */
class EditShortcutActivity : Activity() {

    private lateinit var nameField: EditText
    private lateinit var messageField: EditText
    private lateinit var nameError: TextView
    private lateinit var messageError: TextView
    private lateinit var blanksContainer: LinearLayout
    private lateinit var noBlanksText: View
    private lateinit var builtInNote: View
    private lateinit var previewText: TextView

    private lateinit var shortcutId: String
    private var isNew = true

    /** The folder this shortcut sits in (null = top level) and its place there. */
    private var parentId: String? = null
    private var position = 0

    /** The shortcut as it was when the screen opened (empty for a new one). */
    private lateinit var original: Shortcut

    /** [original] as the editor would save it untouched; anything else means unsaved changes. */
    private lateinit var unchanged: Shortcut

    /**
     * One card per blank name (by key), including blanks no longer in the message, so their
     * settings come back if the blank is typed again.
     */
    private val cards = HashMap<String, BlankCard>()
    private var shownKeys: List<String> = emptyList()
    private var template: Template = Template.parse("")

    /** Card state saved before a rotation, used as each card is made again. */
    private var restoredCards: Bundle? = null
    private var filling = false
    private var dialog: Dialog? = null
    private var backCallback: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_shortcut)
        EdgeToEdge.apply(this, findViewById(R.id.edit_root), findViewById(R.id.edit_top_bar))

        nameField = findViewById(R.id.edit_name)
        messageField = findViewById(R.id.edit_message)
        nameError = findViewById(R.id.edit_name_error)
        messageError = findViewById(R.id.edit_message_error)
        blanksContainer = findViewById(R.id.edit_blanks)
        noBlanksText = findViewById(R.id.edit_no_blanks)
        builtInNote = findViewById(R.id.edit_built_in_note)
        previewText = findViewById(R.id.edit_preview)

        val requestedId = intent?.getStringExtra(EXTRA_ID)
        val stored = requestedId?.let { ShortcutRepository.find(this, it) }
        isNew = stored == null
        shortcutId = stored?.id ?: savedInstanceState?.getString(STATE_ID) ?: ShortcutRepository.newId()
        val library = ShortcutRepository.library(this)
        parentId = if (stored != null) stored.parentId else FolderLogic.existingFolder(library, intent?.getStringExtra(EXTRA_PARENT_ID))
        position = stored?.order ?: 0
        original = stored ?: Shortcut(id = shortcutId, name = "", template = "", parentId = parentId)
        unchanged = ShortcutDraft.normalise(original)

        setActionBar(findViewById<Toolbar>(R.id.edit_toolbar))
        actionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.app_ic_back)
            setHomeActionContentDescription(R.string.action_back)
            setTitle(if (isNew) R.string.edit_title_new else R.string.edit_title)
        }

        findViewById<TextView>(R.id.edit_folder).apply {
            val line = FolderLogic.folderLine(library, parentId, getString(R.string.shortcut_no_name))
            if (line != null) {
                text = getString(R.string.edit_in_folder, line)
                visibility = View.VISIBLE
            }
        }

        findViewById<View>(R.id.edit_toolbar_save).setOnClickListener { save() }
        findViewById<View>(R.id.edit_save_button).setOnClickListener { save() }
        findViewById<View>(R.id.edit_add_blank).setOnClickListener { showAddBlank() }
        findViewById<View>(R.id.edit_add_time_later).setOnClickListener { showAddTimeLater() }
        findViewById<View>(R.id.edit_help).setOnClickListener { showDialog(HelpDialog.create(this)) }

        nameField.onTextChanged { nameError.visibility = View.GONE }
        messageField.onTextChanged {
            if (filling) return@onTextChanged
            messageError.visibility = View.GONE
            onMessageChanged()
        }

        if (savedInstanceState != null) {
            restoreDraft(savedInstanceState)
        } else {
            fillFields(original.name, original.template)
            if (isNew) {
                // A new shortcut starts with the keyboard open on "Name".
                nameField.requestFocus()
                val mode = window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE.inv()
                window.setSoftInputMode(mode or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerBackCallback()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ID, shortcutId)
        outState.putString(STATE_NAME, nameField.text.toString())
        outState.putString(STATE_MESSAGE, messageField.text.toString())
        outState.putInt(STATE_SELECTION_START, messageField.selectionStart)
        outState.putInt(STATE_SELECTION_END, messageField.selectionEnd)
        outState.putBundle(STATE_CARDS, Bundle().apply { cards.forEach { (key, card) -> putBundle(key, card.saveState()) } })
    }

    override fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) unregisterBackCallback()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_edit_shortcut, menu)
        menu.findItem(R.id.menu_delete)?.isVisible = !isNew
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            leave()
            true
        }
        R.id.menu_delete -> {
            confirmDelete()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        leave()
    }

    // Back

    /** Back and the top-left arrow: ask first if there are unsaved changes. */
    private fun leave() {
        if (currentDraft() == unchanged) {
            finish()
            return
        }
        showDialog(
            AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_title)
                .setMessage(R.string.unsaved_message)
                .setPositiveButton(R.string.unsaved_save) { _, _ -> save() }
                .setNegativeButton(R.string.unsaved_discard) { _, _ -> finish() }
                .setNeutralButton(R.string.dialog_cancel, null)
                .create(),
        )
    }

    /**
     * Only used when the app opts in to the newer back handling (android:enableOnBackInvokedCallback);
     * otherwise Android ignores this and calls onBackPressed().
     */
    private fun registerBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = OnBackInvokedCallback { leave() }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        backCallback = callback
    }

    private fun unregisterBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        (backCallback as? OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        backCallback = null
    }

    // Filling the screen

    private fun fillFields(name: String, message: String) {
        filling = true
        nameField.setText(name)
        messageField.setText(message)
        filling = false
        onMessageChanged()
    }

    private fun restoreDraft(state: Bundle) {
        restoredCards = state.getBundle(STATE_CARDS)
        fillFields(
            state.getString(STATE_NAME).orEmpty(),
            state.getString(STATE_MESSAGE).orEmpty(),
        )
        val length = messageField.length()
        val start = state.getInt(STATE_SELECTION_START, length).coerceIn(0, length)
        val end = state.getInt(STATE_SELECTION_END, start).coerceIn(0, length)
        messageField.setSelection(start, end)
        // Keep the cards of blanks taken out of the message too, for another rotation.
        restoredCards?.let { saved ->
            for (key in saved.keySet()) {
                if (cards.containsKey(key)) continue
                val cardState = saved.getBundle(key) ?: continue
                BlankCard.fromState(this, blanksContainer, cardState, ::onBlankChanged)?.let { cards[key] = it }
            }
        }
        restoredCards = null
    }

    // Blanks

    /** Rebuilds the blank cards when the set of blanks changes, and updates the preview. */
    private fun onMessageChanged() {
        template = Template.parse(messageField.text.toString())
        val names = template.variableNames
        val keys = names.map { VariableDef.keyOf(it) }
        for (name in names) {
            val card = cardFor(name)
            card.rename(name)
            card.setUsesOffset(template.usesOffset(name))
        }
        if (keys != shownKeys) {
            blanksContainer.removeAllViews()
            for (key in keys) cards[key]?.let { blanksContainer.addView(it.view) }
            shownKeys = keys
        }
        noBlanksText.visibility = if (keys.isEmpty()) View.VISIBLE else View.GONE
        val usesBuiltIn = template.parts.any { it is Template.Part.Ref && Template.isBuiltIn(it.name) }
        builtInNote.visibility = if (usesBuiltIn) View.VISIBLE else View.GONE
        updatePreview()
    }

    private fun onBlankChanged() = updatePreview()

    /** The card for [name], made from saved settings or a guess the first time it is needed. */
    private fun cardFor(name: String): BlankCard {
        val key = VariableDef.keyOf(name)
        cards[key]?.let { return it }
        val card = restoredCards?.getBundle(key)?.let { BlankCard.fromState(this, blanksContainer, it, ::onBlankChanged) }
            ?: original.variable(name)?.let { BlankCard(this, blanksContainer, it, typeChosen = true, onChanged = ::onBlankChanged) }
            ?: BlankCard(
                this,
                blanksContainer,
                VariableDef(name.trim(), Template.guessType(name, template.usesOffset(name))),
                typeChosen = false,
                onChanged = ::onBlankChanged,
            )
        cards[key] = card
        return card
    }

    private fun shownCards(): List<BlankCard> = shownKeys.mapNotNull { cards[it] }

    private fun updatePreview() {
        if (messageField.text.isNullOrBlank()) {
            previewText.setText(R.string.edit_preview_empty)
            previewText.setTextColor(getColor(R.color.text_secondary))
            return
        }
        previewText.text = ShortcutPreview.render(template, shownCards().map { it.toVariableDef() })
        previewText.setTextColor(getColor(R.color.text_primary))
    }

    private fun showAddBlank() {
        val dialog = AddBlankDialogs.addBlank(
            this,
            typeOf = { name -> cards[VariableDef.keyOf(name)]?.takeIf { it.view.parent != null }?.type },
        ) { name, type ->
            cardFor(name).setType(type, chosen = true)
            insertIntoMessage(ShortcutDraft.blankToken(name))
        }
        showDialog(dialog)
    }

    private fun showAddTimeLater() {
        val timeNames = shownCards().filter { it.type == VariableType.TIME }.map { it.name }
        showDialog(AddBlankDialogs.addTimeLater(this, timeNames) { token -> insertIntoMessage(token) })
    }

    /** Puts [token] at the cursor in the message, or at the end if the message isn't being typed in. */
    private fun insertIntoMessage(token: String) {
        val text = messageField.text
        val length = text.length
        val atCursor = messageField.hasFocus() && messageField.selectionStart >= 0
        val start = if (atCursor) minOf(messageField.selectionStart, messageField.selectionEnd).coerceIn(0, length) else length
        val end = if (atCursor) maxOf(messageField.selectionStart, messageField.selectionEnd).coerceIn(0, length) else length
        val insert = ShortcutDraft.spaced(token, text.getOrNull(start - 1), text.getOrNull(end))
        text.replace(start, end, insert)
        messageField.requestFocus()
        messageField.setSelection((start + insert.length).coerceAtMost(messageField.length()))
    }

    // Saving

    private fun currentDraft(): Shortcut = ShortcutDraft.build(
        shortcutId,
        nameField.text.toString(),
        messageField.text.toString(),
        parentId,
        position,
    ) { name -> cardFor(name).toVariableDef() }

    private fun save() {
        val draft = currentDraft()
        var firstProblem: View? = null
        if (draft.name.isEmpty()) {
            showError(nameError, R.string.error_name_required)
            firstProblem = nameField
        }
        if (draft.template.isBlank()) {
            showError(messageError, R.string.error_message_required)
            firstProblem = firstProblem ?: messageField
        }
        if (firstProblem != null) {
            firstProblem.requestFocus()
            return
        }

        val all = ShortcutRepository.all(this)
        // Keep anything the keyboard learned while this screen was open.
        val latest = all.firstOrNull { it.id == shortcutId }
        val toSave = if (latest == null) {
            draft
        } else {
            // It may have been moved (e.g. in the keyboard) while this screen was open.
            draft.copy(
                parentId = latest.parentId,
                order = latest.order,
                variables = draft.variables.map { variable ->
                    latest.variable(variable.name)?.lastValue?.let { variable.copy(lastValue = it) } ?: variable
                },
            )
        }
        try {
            ShortcutRepository.save(this, toSave)
        } catch (e: Exception) {
            Log.w(TAG, "Could not save shortcut", e)
            Toast.makeText(this, R.string.edit_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.edit_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showError(view: TextView, message: Int) {
        view.setText(message)
        view.visibility = View.VISIBLE
    }

    private fun confirmDelete() {
        val name = nameField.text.toString().trim().ifEmpty { original.name.trim() }
        val title = if (name.isEmpty()) getString(R.string.delete_title_no_name) else getString(R.string.delete_title, name)
        showDialog(
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(R.string.delete_message)
                .setPositiveButton(R.string.delete_confirm) { _, _ -> delete() }
                .setNegativeButton(R.string.dialog_cancel, null)
                .create(),
        )
    }

    private fun delete() {
        try {
            ShortcutRepository.delete(this, shortcutId)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete shortcut", e)
            Toast.makeText(this, R.string.edit_save_failed, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.edit_deleted, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showDialog(newDialog: Dialog) {
        dialog?.dismiss()
        dialog = newDialog
        showReadable(newDialog)
    }

    companion object {
        /** The id of the shortcut to change. Leave it out to make a new one. */
        const val EXTRA_ID = "id"

        /** The folder a new shortcut is made in (a folder id). Leave it out for the top level. */
        const val EXTRA_PARENT_ID = "parent_id"

        private const val TAG = "EditShortcutActivity"
        private const val STATE_ID = "id"
        private const val STATE_NAME = "name"
        private const val STATE_MESSAGE = "message"
        private const val STATE_SELECTION_START = "selectionStart"
        private const val STATE_SELECTION_END = "selectionEnd"
        private const val STATE_CARDS = "blanks"
    }
}
