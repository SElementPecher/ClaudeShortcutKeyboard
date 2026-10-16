package io.github.selementpecher.shortcutkeyboard.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.model.VariableType

/** Friendly names and hints for each kind of blank, and the drop-down that picks one. */
internal object BlankTypes {

    /** The order shown in the drop-down. */
    val ORDER: List<VariableType> = listOf(
        VariableType.CHOICE,
        VariableType.TEXT,
        VariableType.NUMBER,
        VariableType.TIME,
        VariableType.DATE,
    )

    /** String resource for the drop-down label. */
    fun label(type: VariableType): Int = when (type) {
        VariableType.CHOICE -> R.string.blank_type_choice
        VariableType.TEXT -> R.string.blank_type_text
        VariableType.NUMBER -> R.string.blank_type_number
        VariableType.TIME -> R.string.blank_type_time
        VariableType.DATE -> R.string.blank_type_date
    }

    /** String resource for the short line under the drop-down. */
    fun hint(type: VariableType): Int = when (type) {
        VariableType.CHOICE -> R.string.blank_hint_choice
        VariableType.TEXT -> R.string.blank_hint_text
        VariableType.NUMBER -> R.string.blank_hint_number
        VariableType.TIME -> R.string.blank_hint_time
        VariableType.DATE -> R.string.blank_hint_date
    }

    fun position(type: VariableType): Int = ORDER.indexOf(type).coerceAtLeast(0)

    fun at(position: Int): VariableType = ORDER.getOrElse(position) { VariableType.CHOICE }

    fun adapter(context: Context): ArrayAdapter<String> =
        ArrayAdapter(context, R.layout.item_spinner, ORDER.map { context.getString(label(it)) }).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
}

/** Runs [action] after the text changes. Returns the watcher so it can be removed later. */
internal fun TextView.onTextChanged(action: (Editable?) -> Unit): TextWatcher {
    val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = action(s)
    }
    addTextChangedListener(watcher)
    return watcher
}
