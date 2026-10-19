package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.VariableDef

/**
 * One box to type a new answer for a blank, e.g. a new child's name.
 *
 * The keyboard has no letter keys, so it sends people here. The new answer is added to the blank's
 * list and remembered as its last value; the keyboard picks it up when it is opened again.
 */
class AddOptionActivity : Activity() {

    private lateinit var field: EditText
    private lateinit var error: TextView
    private var shortcutId: String? = null
    private var blankName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_option)
        EdgeToEdge.apply(this, findViewById(R.id.add_option_root), findViewById(R.id.add_option_top_bar))

        shortcutId = intent?.getStringExtra(EXTRA_SHORTCUT_ID)
        blankName = intent?.getStringExtra(EXTRA_BLANK_NAME).orEmpty()
        field = findViewById(R.id.add_option_field)
        error = findViewById(R.id.add_option_error)

        findViewById<TextView>(R.id.add_option_title).text = getString(R.string.add_option_title, blankName)
        findViewById<View>(R.id.add_option_save).setOnClickListener { save() }
        findViewById<View>(R.id.add_option_cancel).setOnClickListener { finish() }
        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { save(); true } else false
        }
    }

    private fun save() {
        val value = field.text.toString().trim()
        if (value.isEmpty()) {
            error.setText(R.string.add_option_error_empty)
            error.visibility = View.VISIBLE
            return
        }
        val shortcut = shortcutId?.let { ShortcutRepository.find(this, it) }
        if (shortcut != null) {
            val current = FillSession.resolveVariables(shortcut).firstOrNull { it.key == VariableDef.keyOf(blankName) }
                ?: VariableDef(name = blankName)
            val known = current.options.firstOrNull { it.equals(value, ignoreCase = true) }
            val updated = current.copy(
                options = if (known == null) current.options + value else current.options,
                lastValue = known ?: value,
            )
            val others = shortcut.variables.filterNot { it.key == updated.key }
            runCatching { ShortcutRepository.save(this, shortcut.copy(variables = others + updated)) }
        }
        finish()
    }

    companion object {
        const val EXTRA_SHORTCUT_ID = "shortcut_id"
        const val EXTRA_BLANK_NAME = "blank_name"

        /** An intent the keyboard can start from outside an activity. */
        fun intent(context: Context, shortcutId: String, blankName: String): Intent =
            Intent(context, AddOptionActivity::class.java)
                .putExtra(EXTRA_SHORTCUT_ID, shortcutId)
                .putExtra(EXTRA_BLANK_NAME, blankName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
