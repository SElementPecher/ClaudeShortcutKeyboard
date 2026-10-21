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
            .toShortcut(shortcut.id, shortcut.name, shortcut.parentId, shortcut.order, shortcut.icon, shortcut.color)

    /**
     * [edited] (what the editor is about to save) with the changes made elsewhere while the editor was open kept:
     * answers the keyboard added, the value it learned, and a move to another folder or place. [original] is the quiKey
     * as the editor opened it; [latest] is how it is saved now.
     */
    fun keepOutsideChanges(edited: Shortcut, original: Shortcut?, latest: Shortcut?): Shortcut {
        if (original == null || latest == null || latest == original) return edited
        val variables = edited.variables.map { mine ->
            val before = original.variables.firstOrNull { it.key == mine.key }
            val now = latest.variables.firstOrNull { it.key == mine.key }
            if (before == null || now == null || now.type != mine.type) {
                mine
            } else {
                // Answers added outside the editor (and not already here) are kept, after the editor's own.
                val added = now.options.filter { o ->
                    before.options.none { it.equals(o, ignoreCase = true) } && mine.options.none { it.equals(o, ignoreCase = true) }
                }
                mine.copy(
                    options = mine.options + added,
                    lastValue = if (mine.lastValue == before.lastValue) now.lastValue else mine.lastValue,
                )
            }
        }
        val moved = latest.parentId != original.parentId || latest.order != original.order
        return edited.copy(
            variables = variables,
            parentId = if (moved) latest.parentId else edited.parentId,
            order = if (moved) latest.order else edited.order,
        )
    }

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
