package io.github.selementpecher.shortcutkeyboard.ime

/**
 * The shift key: off, on for the next letter only, or locked on (caps lock).
 *
 * One tap turns it on for one letter, a quick double tap locks it, and a tap while locked turns
 * it off. Automatic capitals (start of a sentence) are applied with [autoCapitalize], but never
 * override a choice the user made since their last edit.
 */
class ShiftState(private val doubleTapMillis: Long = DOUBLE_TAP_MILLIS) {

    enum class Mode { OFF, ONCE, LOCKED }

    var mode: Mode = Mode.OFF
        private set

    val isUpperCase: Boolean get() = mode != Mode.OFF

    private var lastTapAt: Long? = null

    /** True when the user pressed shift since the last edit, so automatic capitals keep out. */
    private var userChoice = false

    /** The shift key was tapped at [timeMillis] (any monotonic clock). */
    fun tap(timeMillis: Long) {
        val last = lastTapAt
        val doubleTap = last != null && timeMillis >= last && timeMillis - last <= doubleTapMillis
        when {
            mode == Mode.LOCKED -> {
                mode = Mode.OFF
                lastTapAt = null
            }
            doubleTap -> {
                mode = Mode.LOCKED
                lastTapAt = null
            }
            else -> {
                mode = if (mode == Mode.OFF) Mode.ONCE else Mode.OFF
                lastTapAt = timeMillis
            }
        }
        userChoice = true
    }

    /** A character was typed: a one-letter shift is used up. */
    fun onCharacterTyped() {
        if (mode == Mode.ONCE) mode = Mode.OFF
        userChoice = false
        lastTapAt = null
    }

    /** Space, enter or delete was pressed. Shift stays as it is, but automatic capitals may apply again. */
    fun onEdited() {
        userChoice = false
        lastTapAt = null
    }

    /** Turns shift on or off for the start of a sentence, unless it is locked or the user just chose. */
    fun autoCapitalize(shouldCapitalize: Boolean) {
        if (mode == Mode.LOCKED || userChoice) return
        mode = if (shouldCapitalize) Mode.ONCE else Mode.OFF
    }

    fun reset() {
        mode = Mode.OFF
        userChoice = false
        lastTapAt = null
    }

    companion object {
        const val DOUBLE_TAP_MILLIS = 350L
    }
}
