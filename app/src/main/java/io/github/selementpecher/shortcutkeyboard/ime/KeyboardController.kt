package io.github.selementpecher.shortcutkeyboard.ime

import java.util.Locale

/** What the keyboard service should do after a key press. */
sealed interface KeyOutput {
    /** Type this text (shift already applied). */
    data class Type(val text: String) : KeyOutput
    data object Delete : KeyOutput
    data object Space : KeyOutput
    data object Enter : KeyOutput
    data object NextKeyboard : KeyOutput

    /** Only the keyboard itself changed (page or shift). */
    data object None : KeyOutput
}

/**
 * The key-handling state of the keyboard: which page is showing and the shift key.
 *
 * Plain Kotlin with no Android types. The service turns each [KeyOutput] into an edit of the
 * app's text field, or of the fill-in box while a blank is being typed.
 */
class KeyboardController(private val locale: Locale = Locale.ROOT) {

    var page: KeyboardPage = KeyboardPage.LETTERS
        private set

    val shift = ShiftState()

    /** Back to [page] with shift off, e.g. when a new text field starts. */
    fun reset(page: KeyboardPage) {
        this.page = page
        shift.reset()
    }

    /** What [key] shows right now: letters follow the shift key. */
    fun labelFor(key: Key): String =
        if (key.kind == KeyKind.CHARACTER && page == KeyboardPage.LETTERS && shift.isUpperCase) {
            key.label.uppercase(locale)
        } else {
            key.label
        }

    /** Handles a key press at [timeMillis] (used to spot a double tap on shift). */
    fun onKey(key: Key, timeMillis: Long): KeyOutput = when (key.kind) {
        KeyKind.CHARACTER -> {
            val text = if (page == KeyboardPage.LETTERS && shift.isUpperCase) key.text.uppercase(locale) else key.text
            shift.onCharacterTyped()
            KeyOutput.Type(text)
        }
        KeyKind.SHIFT -> {
            shift.tap(timeMillis)
            KeyOutput.None
        }
        KeyKind.TO_SYMBOLS -> {
            page = KeyboardPage.SYMBOLS
            KeyOutput.None
        }
        KeyKind.TO_MORE_SYMBOLS -> {
            page = KeyboardPage.MORE_SYMBOLS
            KeyOutput.None
        }
        KeyKind.TO_LETTERS -> {
            page = KeyboardPage.LETTERS
            KeyOutput.None
        }
        KeyKind.DELETE -> {
            shift.onEdited()
            KeyOutput.Delete
        }
        KeyKind.SPACE -> {
            shift.onEdited()
            KeyOutput.Space
        }
        KeyKind.ENTER -> {
            shift.onEdited()
            KeyOutput.Enter
        }
        KeyKind.NEXT_KEYBOARD -> KeyOutput.NextKeyboard
    }
}
