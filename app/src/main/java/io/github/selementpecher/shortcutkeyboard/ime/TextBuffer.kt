package io.github.selementpecher.shortcutkeyboard.ime

/**
 * The text typed into the fill-in box while a blank is being filled in.
 *
 * Typing always happens at the end. A value that was already there starts "all selected", so the
 * first key press replaces it and delete clears it, like a highlighted field in a form.
 */
class TextBuffer(
    initial: String = "",
    selectAll: Boolean = initial.isNotEmpty(),
    private val maxLength: Int = MAX_LENGTH,
) {
    private val builder = StringBuilder(clip(initial, maxLength))

    var isAllSelected: Boolean = selectAll && builder.isNotEmpty()
        private set

    /** True once the user has typed or deleted anything. */
    var isEdited: Boolean = false
        private set

    val text: String get() = builder.toString()
    val isEmpty: Boolean get() = builder.isEmpty()

    fun insert(value: String) {
        if (value.isEmpty()) return
        if (isAllSelected) {
            builder.setLength(0)
            isAllSelected = false
        }
        builder.append(clip(value, maxLength - builder.length))
        isEdited = true
    }

    /** Deletes the last character (or everything when all selected). Returns false if already empty. */
    fun deleteBackward(): Boolean {
        if (builder.isEmpty()) {
            isAllSelected = false
            return false
        }
        if (isAllSelected) {
            builder.setLength(0)
            isAllSelected = false
        } else {
            builder.delete(Character.offsetByCodePoints(builder, builder.length, -1), builder.length)
        }
        isEdited = true
        return true
    }

    /** True when the next letter starts a new sentence, e.g. after "Hello. ". */
    val isAfterSentenceEnd: Boolean
        get() {
            if (builder.isEmpty() || !builder.last().isWhitespace()) return false
            val trimmed = builder.trimEnd()
            return trimmed.isNotEmpty() && trimmed.last() in SENTENCE_ENDS
        }

    companion object {
        const val MAX_LENGTH = 500
        private const val SENTENCE_ENDS = ".!?"

        /** The start of [value], at most [max] chars, without splitting a surrogate pair. */
        private fun clip(value: String, max: Int): String {
            if (max <= 0) return ""
            if (value.length <= max) return value
            val end = if (Character.isHighSurrogate(value[max - 1])) max - 1 else max
            return value.substring(0, end)
        }
    }
}
