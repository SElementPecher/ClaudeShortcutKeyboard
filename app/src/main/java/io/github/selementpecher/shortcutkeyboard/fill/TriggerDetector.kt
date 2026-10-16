package io.github.selementpecher.shortcutkeyboard.fill

import io.github.selementpecher.shortcutkeyboard.model.Shortcut

/** Spots a shortcut's trigger word (e.g. "/meds") just before the cursor. */
object TriggerDetector {

    data class Match(val shortcut: Shortcut, /** Characters to delete before the cursor. */ val length: Int)

    fun find(textBeforeCursor: CharSequence?, shortcuts: List<Shortcut>): Match? {
        if (textBeforeCursor.isNullOrEmpty()) return null
        var start = textBeforeCursor.length
        while (start > 0 && !textBeforeCursor[start - 1].isWhitespace()) start--
        val word = textBeforeCursor.subSequence(start, textBeforeCursor.length).toString()
        if (word.isEmpty()) return null
        val shortcut = shortcuts.firstOrNull { it.trigger.isNotBlank() && it.trigger.trim().equals(word, ignoreCase = true) }
            ?: return null
        return Match(shortcut, word.length)
    }
}
