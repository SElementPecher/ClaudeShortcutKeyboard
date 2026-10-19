package io.github.selementpecher.shortcutkeyboard.fill

/**
 * Cuts a filled-in value into pieces that each get their own complete rounded pill, so a long value never
 * shows as one highlight with a ragged break in the middle. A short value is one piece. Plain Kotlin.
 */
object PillChunks {
    const val MAX_LENGTH = 22

    /**
     * "the school gate by the doors" becomes "the school gate" and "by the doors". Words are never split, and a
     * piece is as long as it can be up to [max] letters (one long word may go over on its own).
     */
    fun of(value: String, max: Int = MAX_LENGTH): List<String> {
        if (value.length <= max) return if (value.isEmpty()) emptyList() else listOf(value)
        val chunks = ArrayList<String>()
        val current = StringBuilder()
        // Aim for pieces of about the same length rather than a long one and a stub.
        val count = (value.length + max - 1) / max
        val target = value.length / count
        for (word in value.split(' ')) {
            if (word.isEmpty()) continue
            val full = current.length + 1 + word.length > max
            val balanced = current.length >= target && chunks.size < count - 1
            if (current.isNotEmpty() && (full || balanced)) {
                chunks += current.toString()
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }
}
