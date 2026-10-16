package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Spinner
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template

/** The "+ Add a blank" and "+ Add a time later" dialogs of the shortcut editor. */
internal object AddBlankDialogs {

    private const val MAX_HOURS = 23
    private const val MINUTE_STEP = 5
    private const val DEFAULT_HOURS = 4
    private const val PICKER_TEXT_SP = 24f

    /**
     * Asks for a blank's name and how it is filled in.
     *
     * @param typeOf the type of a blank already in the message, so picking its name shows its type.
     * @param onAdd called with the trimmed name and the chosen type when "Add" is tapped.
     */
    fun addBlank(
        activity: Activity,
        typeOf: (String) -> VariableType?,
        onAdd: (name: String, type: VariableType) -> Unit,
    ): AlertDialog {
        val view = View.inflate(activity, R.layout.dialog_add_blank, null)
        val nameField = view.findViewById<EditText>(R.id.add_blank_name)
        val error = view.findViewById<TextView>(R.id.add_blank_error)
        val typeSpinner = view.findViewById<Spinner>(R.id.add_blank_type)
        val typeHint = view.findViewById<TextView>(R.id.add_blank_type_hint)

        var type = VariableType.CHOICE
        var typePicked = false
        typeSpinner.adapter = BlankTypes.adapter(activity)
        typeSpinner.setSelection(BlankTypes.position(type), false)
        typeHint.setText(BlankTypes.hint(type))
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val picked = BlankTypes.at(position)
                if (picked == type) return
                type = picked
                typePicked = true
                typeHint.setText(BlankTypes.hint(type))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        // Until the user picks a type, suggest one from the name (e.g. "time" -> Time).
        nameField.onTextChanged { text ->
            error.visibility = View.GONE
            val name = text?.toString().orEmpty().trim()
            if (typePicked || name.isEmpty()) return@onTextChanged
            val suggested = typeOf(name) ?: Template.guessType(name)
            if (suggested != type) {
                type = suggested
                typeSpinner.setSelection(BlankTypes.position(suggested))
                typeHint.setText(BlankTypes.hint(suggested))
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.add_blank_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_add, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        val tryAdd = {
            val name = nameField.text.toString().trim()
            val problem = ShortcutDraft.checkBlankName(name)
            if (problem == null) {
                onAdd(name, type)
                dialog.dismiss()
            } else {
                error.setText(
                    when (problem) {
                        ShortcutDraft.NameProblem.EMPTY -> R.string.error_blank_name_required
                        ShortcutDraft.NameProblem.BUILT_IN -> R.string.error_blank_name_built_in
                        ShortcutDraft.NameProblem.INVALID -> R.string.error_blank_name_invalid
                    },
                )
                error.visibility = View.VISIBLE
                nameField.requestFocus()
            }
        }
        nameField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                tryAdd()
                true
            } else {
                false
            }
        }
        // Set after show() so a problem keeps the dialog open.
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { tryAdd() }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        nameField.requestFocus()
        return dialog
    }

    /**
     * Asks which time and how much later, then gives back a blank like `{time+4h}`.
     *
     * @param timeNames the Time blanks in the message, or empty to offer a new "time" blank.
     */
    fun addTimeLater(activity: Activity, timeNames: List<String>, onAdd: (token: String) -> Unit): AlertDialog {
        val names = timeNames.ifEmpty { listOf(DEFAULT_TIME_NAME) }
        val view = View.inflate(activity, R.layout.dialog_add_time_later, null)
        val whichGroup = view.findViewById<LinearLayout>(R.id.time_later_which_group)
        val whichSpinner = view.findViewById<Spinner>(R.id.time_later_which)
        val hours = view.findViewById<NumberPicker>(R.id.time_later_hours)
        val minutes = view.findViewById<NumberPicker>(R.id.time_later_minutes)
        val result = view.findViewById<TextView>(R.id.time_later_result)
        val error = view.findViewById<TextView>(R.id.time_later_error)

        whichSpinner.adapter = ArrayAdapter(activity, R.layout.item_spinner, names.map { "{$it}" }).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        whichGroup.visibility = if (names.size > 1) View.VISIBLE else View.GONE

        hours.minValue = 0
        hours.maxValue = MAX_HOURS
        hours.value = DEFAULT_HOURS
        minutes.minValue = 0
        minutes.maxValue = 60 / MINUTE_STEP - 1
        minutes.displayedValues = Array(60 / MINUTE_STEP) { (it * MINUTE_STEP).toString() }
        minutes.value = 0
        for (picker in listOf(hours, minutes)) {
            picker.wrapSelectorWheel = false
            // Scroll or tap the arrows; don't pop up a keyboard for these.
            picker.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                picker.textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    PICKER_TEXT_SP,
                    activity.resources.displayMetrics,
                )
            }
        }

        fun offsetMinutes(): Long = hours.value * 60L + minutes.value * MINUTE_STEP.toLong()
        fun token(): String {
            val name = names.getOrElse(whichSpinner.selectedItemPosition) { names.first() }
            return ShortcutDraft.blankToken(name, offsetMinutes())
        }
        fun showResult() {
            result.text = activity.getString(R.string.time_later_result, token())
            error.visibility = View.GONE
        }
        hours.setOnValueChangedListener { _, _, _ -> showResult() }
        minutes.setOnValueChangedListener { _, _, _ -> showResult() }
        whichSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = showResult()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        showResult()

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.time_later_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_add, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                if (offsetMinutes() == 0L) {
                    error.visibility = View.VISIBLE
                } else {
                    onAdd(token())
                    dialog.dismiss()
                }
            }
        }
        return dialog
    }

    /** The blank offered when the message has no Time blank yet. */
    const val DEFAULT_TIME_NAME = "time"
}
