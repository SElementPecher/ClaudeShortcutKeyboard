package io.github.selementpecher.shortcutkeyboard.ui

import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.template.Template
import io.github.selementpecher.shortcutkeyboard.ui.editor.MessageDraft

/**
 * The shortcut editor's small rules: cleaning lists of answers, checking the names of changing
 * words, and telling whether anything has changed. The message itself lives in [MessageDraft].
 *
 * Plain Kotlin with no Android types, so it can be unit tested.
 */
object ShortcutDraft {

    /** Why a question typed for a changing word can't be used as it is. */
    enum class NameProblem { EMPTY, BUILT_IN, INVALID }

    /** Answers: trimmed, empty ones dropped, repeats removed ignoring case (the first one wins). */
    fun cleanOptions(options: List<String>): List<String> {
        val seen = HashSet<String>()
        return options.map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }

    /**
     * [shortcut] as the editor would save it if nothing were changed. Comparing this with what
     * the editor builds tells whether there are unsaved changes.
     */
    fun normalise(shortcut: Shortcut): Shortcut =
        MessageDraft.fromShortcut(shortcut)
            .toShortcut(shortcut.id, shortcut.name, shortcut.parentId, shortcut.order, shortcut.icon)

    /** Checks a name for a changing word. Returns null when it is fine. */
    fun checkBlankName(name: String): NameProblem? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return NameProblem.EMPTY
        if (Template.isBuiltIn(trimmed)) return NameProblem.BUILT_IN
        if (trimmed.any { it == '{' || it == '}' || it == '|' || it == '\n' }) return NameProblem.INVALID
        // It must read back as exactly this blank, not as a blank with an offset like "time+4h".
        val ref = Template.parse("{$trimmed}").parts.singleOrNull() as? Template.Part.Ref
            ?: return NameProblem.INVALID
        if (ref.name != trimmed || ref.hasOffset || ref.format != null) return NameProblem.INVALID
        return null
    }
}
