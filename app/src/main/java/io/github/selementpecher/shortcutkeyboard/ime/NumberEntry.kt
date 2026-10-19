package io.github.selementpecher.shortcutkeyboard.ime

/** The number being typed on the pad: digits and one ".", at most [MAX_LENGTH] characters. */
class NumberEntry(start: String? = null) {
    var text: String = start?.takeIf { it.length <= MAX_LENGTH && VALID.matches(it) }.orEmpty()
        private set

    fun press(key: Char) {
        when {
            key == '.' -> {
                if ('.' in text || text.length >= MAX_LENGTH) return
                text = if (text.isEmpty()) "0." else "$text."
            }
            key in '0'..'9' -> {
                if (text.length >= MAX_LENGTH) return
                // No leading zeros: "0" then "5" is "5".
                text = if (text == "0") key.toString() else text + key
            }
        }
    }

    fun backspace() {
        text = text.dropLast(1)
    }

    companion object {
        const val MAX_LENGTH = 8
        private val VALID = Regex("""\d*\.?\d*""")
    }
}
