package io.github.selementpecher.shortcutkeyboard.ui

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.template.Template

/**
 * The shortcut editor's rules: building the shortcut to save, cleaning list options, and
 * checking blank names.
 *
 * Plain Kotlin with no Android types, so it can be unit tested.
 */
object ShortcutDraft {

    /** Why a blank name typed in "Add a blank" can't be used. */
    enum class NameProblem { EMPTY, BUILT_IN, INVALID }

    /** List options: trimmed, empty lines dropped, repeats removed ignoring case (the first one wins). */
    fun cleanOptions(options: List<String>): List<String> {
        val seen = HashSet<String>()
        return options.map { it.trim() }.filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }

    /** Options typed one per line. */
    fun parseOptions(text: String): List<String> = cleanOptions(text.lines())

    fun optionsText(options: List<String>): String = options.joinToString("\n")

    /**
     * The shortcut to save, staying in its folder ([parentId]) and place ([order]). Only the blanks used in [template] are kept, in the order they first
     * appear, named as in the message. [settingsFor] gives the editor's settings for each blank name.
     */
    fun build(
        id: String,
        name: String,
        template: String,
        parentId: String? = null,
        order: Int = 0,
        settingsFor: (String) -> VariableDef,
    ): Shortcut {
        val variables = Template.parse(template).variableNames.map { blank ->
            val settings = settingsFor(blank)
            settings.copy(name = blank, options = cleanOptions(settings.options))
        }
        return Shortcut(
            id = id,
            name = name.trim(),
            template = template,
            variables = variables,
            parentId = parentId,
            order = order,
        )
    }

    /**
     * [shortcut] as the editor would save it without any changes. Comparing this with [build]'s
     * result tells whether there are unsaved changes.
     */
    fun normalise(shortcut: Shortcut): Shortcut {
        val parsed = Template.parse(shortcut.template)
        val resolved = FillSession.resolveVariables(shortcut, parsed).associateBy { it.key }
        return build(shortcut.id, shortcut.name, shortcut.template, shortcut.parentId, shortcut.order) { blank ->
            resolved[VariableDef.keyOf(blank)] ?: VariableDef(blank, Template.guessType(blank, parsed.usesOffset(blank)))
        }
    }

    /** Checks a name typed for a new blank. Returns null when it is fine. */
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

    /** The text for a blank, e.g. `{child}`, or `{time+4h}` with an offset in minutes. */
    fun blankToken(name: String, offsetMinutes: Long = 0): String =
        "{" + name.trim() + Template.describeOffset(offsetMinutes) + "}"

    /**
     * [token] with a space added before or after it where it would otherwise run into the
     * neighbouring word. [before] and [after] are the characters around the insert point.
     */
    fun spaced(token: String, before: Char?, after: Char?): String {
        val spaceBefore = before != null && (before.isLetterOrDigit() || before in ",.;:!?)}")
        val spaceAfter = after != null && (after.isLetterOrDigit() || after == '{')
        return (if (spaceBefore) " " else "") + token + (if (spaceAfter) " " else "")
    }
}
