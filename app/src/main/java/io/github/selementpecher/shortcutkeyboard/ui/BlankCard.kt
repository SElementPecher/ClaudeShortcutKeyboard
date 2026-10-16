package io.github.selementpecher.shortcutkeyboard.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template

/**
 * The settings card for one blank in the shortcut editor: how it is filled in and, for
 * "Pick from a list", its options.
 *
 * The editor keeps one card per blank name and reuses it while the message changes, so typing
 * in a card is never interrupted. The card saves its own state ([saveState]) because several
 * cards share the same view ids.
 */
internal class BlankCard(
    context: Context,
    parent: ViewGroup,
    settings: VariableDef,
    /** True when the type came from saved settings or the user, so we never guess over it. */
    private var typeChosen: Boolean,
    private val onChanged: () -> Unit,
    optionsText: String = ShortcutDraft.optionsText(settings.options),
) {
    val view: View = LayoutInflater.from(context).inflate(R.layout.item_blank, parent, false)

    private val label: TextView = view.findViewById(R.id.blank_label)
    private val typeSpinner: Spinner = view.findViewById(R.id.blank_type)
    private val typeHint: TextView = view.findViewById(R.id.blank_type_hint)
    private val offsetWarning: TextView = view.findViewById(R.id.blank_offset_warning)
    private val optionsGroup: View = view.findViewById(R.id.blank_options_group)
    private val optionsField: EditText = view.findViewById(R.id.blank_options)
    private val rememberBox: CheckBox = view.findViewById(R.id.blank_remember)

    /** The blank's name as written in the message (first use). */
    var name: String = settings.name.trim()
        private set

    var type: VariableType = settings.type
        private set

    private val lastValue: String? = settings.lastValue
    private var usesOffset = false

    init {
        // Several cards use the same ids; the editor saves their state through saveState().
        view.isSaveFromParentEnabled = false

        typeSpinner.adapter = BlankTypes.adapter(context)
        typeSpinner.setSelection(BlankTypes.position(type), false)
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val picked = BlankTypes.at(position)
                // Also called once after layout and after setType(); only a real change counts.
                if (picked == type) return
                typeChosen = true
                type = picked
                showType()
                onChanged()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        optionsField.setText(optionsText)
        optionsField.onTextChanged { onChanged() }
        rememberBox.isChecked = settings.remember
        rememberBox.setOnCheckedChangeListener { _, _ -> onChanged() }

        showName()
        showType()
    }

    /** The same blank written differently in the message, e.g. "Child" instead of "child". */
    fun rename(newName: String) {
        val trimmed = newName.trim()
        if (trimmed == name) return
        name = trimmed
        showName()
    }

    /** Sets the type from code (not the user picking it). */
    fun setType(newType: VariableType, chosen: Boolean) {
        if (chosen) typeChosen = true
        if (newType == type) return
        type = newType
        typeSpinner.setSelection(BlankTypes.position(newType))
        showType()
    }

    /**
     * Whether the message adds time to this blank, like `{time+4h}`. That only works for Time and
     * Date, so a guessed type is switched to one of those, and a chosen one shows a warning.
     */
    fun setUsesOffset(uses: Boolean) {
        usesOffset = uses
        if (uses && !typeChosen && !type.allowsOffset()) {
            setType(Template.guessType(name, usesOffset = true), chosen = false)
        }
        showWarning()
    }

    /** The blank's settings as they are now, with the options cleaned up. */
    fun toVariableDef(): VariableDef = VariableDef(
        name = name,
        type = type,
        options = ShortcutDraft.parseOptions(optionsField.text.toString()),
        remember = rememberBox.isChecked,
        lastValue = lastValue,
    )

    fun saveState(): Bundle = Bundle().apply {
        putString(STATE_NAME, name)
        putString(STATE_TYPE, type.id)
        putString(STATE_OPTIONS, optionsField.text.toString())
        putBoolean(STATE_REMEMBER, rememberBox.isChecked)
        putString(STATE_LAST_VALUE, lastValue)
        putBoolean(STATE_TYPE_CHOSEN, typeChosen)
    }

    private fun showName() {
        label.text = view.context.getString(R.string.blank_label, name)
    }

    private fun showType() {
        typeHint.setText(BlankTypes.hint(type))
        optionsGroup.visibility = if (type == VariableType.CHOICE) View.VISIBLE else View.GONE
        showWarning()
    }

    private fun showWarning() {
        offsetWarning.visibility = if (usesOffset && !type.allowsOffset()) View.VISIBLE else View.GONE
    }

    private fun VariableType.allowsOffset() = this == VariableType.TIME || this == VariableType.DATE

    companion object {
        private const val STATE_NAME = "name"
        private const val STATE_TYPE = "type"
        private const val STATE_OPTIONS = "options"
        private const val STATE_REMEMBER = "remember"
        private const val STATE_LAST_VALUE = "lastValue"
        private const val STATE_TYPE_CHOSEN = "typeChosen"

        /** Rebuilds a card from [saveState], or returns null if [state] is not usable. */
        fun fromState(context: Context, parent: ViewGroup, state: Bundle, onChanged: () -> Unit): BlankCard? {
            val name = state.getString(STATE_NAME)?.takeIf { it.isNotBlank() } ?: return null
            val settings = VariableDef(
                name = name,
                type = VariableType.fromId(state.getString(STATE_TYPE)),
                remember = state.getBoolean(STATE_REMEMBER, true),
                lastValue = state.getString(STATE_LAST_VALUE),
            )
            return BlankCard(
                context,
                parent,
                settings,
                typeChosen = state.getBoolean(STATE_TYPE_CHOSEN, true),
                onChanged = onChanged,
                optionsText = state.getString(STATE_OPTIONS).orEmpty(),
            )
        }
    }
}
