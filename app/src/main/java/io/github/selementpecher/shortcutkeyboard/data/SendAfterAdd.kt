package io.github.selementpecher.shortcutkeyboard.data

import android.content.Context
import android.view.inputmethod.EditorInfo

/**
 * The "send after adding" choice. When on, tapping Add also presses the app's own Send button, but only where the
 * text box says it has one (an editor action of Send). A phone keyboard cannot reliably send anywhere else.
 */
object SendAfterAdd {
    private const val PREFS = "keyboard"
    private const val KEY = "send_after_add"

    fun isOn(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(context: Context, on: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    /** True when the text box with these [imeOptions] has a Send action that can be pressed. */
    fun canSend(imeOptions: Int): Boolean =
        (imeOptions and EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_SEND &&
            (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
}
