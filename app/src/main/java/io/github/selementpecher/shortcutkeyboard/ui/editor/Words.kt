package io.github.selementpecher.shortcutkeyboard.ui.editor

/**
 * Finds the words in a piece of text, so they can be tapped one by one.
 *
 * A word is letters and digits, plus the little marks that belong inside or at the edge of one:
 * `5ml`, `20:20`, `3.5`, `1,000`, `O'Brien`, `co-op`, `£5`, `50%`, `-5`. Spaces and other
 * punctuation (commas, full stops at the end, brackets, emoji) are not part of a word.
 *
 * Plain Kotlin with no Android types, so it can be unit tested.
 */
object Words {

    /** A word's place in the text: [start] up to, not including, [end]. */
    data class Span(val start: Int, val end: Int) {
        val length: Int get() = end - start
    }

    /** Every word in [text], in order. */
    fun spans(text: String): List<Span> {
        val out = ArrayList<Span>()
        var start = -1
        for (i in text.indices) {
            if (isWordChar(text, i)) {
                if (start < 0) start = i
            } else if (start >= 0) {
                out += Span(start, i)
                start = -1
            }
        }
        if (start >= 0) out += Span(start, text.length)
        return out
    }

    /** True when the character at [i] is the sort of character [spans] keeps inside a word. */
    private fun isWordChar(text: String, i: Int): Boolean {
        val c = text[i]
        if (c.isLetterOrDigit()) return true
        val prev = text.getOrNull(i - 1)
        val next = text.getOrNull(i + 1)
        val afterWord = prev != null && prev.isLetterOrDigit()
        val beforeWord = next != null && next.isLetterOrDigit()
        val inside = afterWord && beforeWord
        return when (c) {
            // Inside a word: 20:20, 3.5, O'Brien, co-op, and/or, R&B.
            '\'', '’', '.', ':', '/', '&' -> inside
            ',' -> inside && prev!!.isDigit() && next!!.isDigit()
            // At the start of a word: £5, #12, @sam.
            '£', '$', '€', '#', '@' -> beforeWord && !afterWord
            // Inside a word (co-op) or a sign in front of a number (-5).
            '-' -> inside || (beforeWord && next!!.isDigit() && !afterWord)
            '+' -> beforeWord && next!!.isDigit() && !afterWord
            // At the end of a word: 50%.
            '%' -> afterWord
            else -> false
        }
    }
}
