package io.github.selementpecher.shortcutkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** What the enter key does in the current text field. */
enum class EnterAction(
    /** The editor action to send, for the action kinds. */
    val editorAction: Int,
) {
    /** Types a line break (multi-line fields). */
    NEW_LINE(EditorInfo.IME_ACTION_NONE),

    /** Sends a plain enter key press (single-line fields with no action, terminals). */
    ENTER_KEY(EditorInfo.IME_ACTION_UNSPECIFIED),
    GO(EditorInfo.IME_ACTION_GO),
    SEARCH(EditorInfo.IME_ACTION_SEARCH),
    SEND(EditorInfo.IME_ACTION_SEND),
    NEXT(EditorInfo.IME_ACTION_NEXT),
    DONE(EditorInfo.IME_ACTION_DONE),
    PREVIOUS(EditorInfo.IME_ACTION_PREVIOUS);

    val isEditorAction: Boolean get() = this != NEW_LINE && this != ENTER_KEY
}

/**
 * Reads a text field's [EditorInfo] flags. Only uses the (compile-time) constants, so it runs in
 * plain unit tests.
 */
object EditorRules {

    fun enterAction(imeOptions: Int, inputType: Int): EnterAction {
        if (isMultiLine(inputType)) return EnterAction.NEW_LINE
        if ((imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return EnterAction.ENTER_KEY
        return when (imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterAction.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterAction.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterAction.SEND
            EditorInfo.IME_ACTION_NEXT -> EnterAction.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterAction.DONE
            EditorInfo.IME_ACTION_PREVIOUS -> EnterAction.PREVIOUS
            else -> EnterAction.ENTER_KEY
        }
    }

    fun isMultiLine(inputType: Int): Boolean =
        (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT &&
            (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

    /** Number, phone and date fields start on the numbers page. */
    fun startPage(inputType: Int): KeyboardPage = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> KeyboardPage.SYMBOLS
        else -> KeyboardPage.LETTERS
    }

    /** Password fields: trigger words are not looked for there. */
    fun isPassword(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }
}
