package io.github.selementpecher.shortcutkeyboard.ui

import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.view.inputmethod.InputMethodManager
import io.github.selementpecher.shortcutkeyboard.ime.ShortcutKeyboardService

/** Whether our keyboard is turned on and picked, for the setup steps on the main screen. */
internal object KeyboardSetup {
    private const val TAG = "KeyboardSetup"

    fun component(context: Context): ComponentName = ComponentName(context, ShortcutKeyboardService::class.java)

    /** Step 1: turned on in the phone's keyboard settings. */
    fun isEnabled(context: Context): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        val ours = component(context)
        return try {
            imm.enabledInputMethodList.orEmpty().any { it.component == ours }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not read the enabled keyboards", e)
            false
        }
    }

}
