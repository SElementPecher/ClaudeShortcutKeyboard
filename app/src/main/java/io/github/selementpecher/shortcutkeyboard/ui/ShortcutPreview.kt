package io.github.selementpecher.shortcutkeyboard.ui

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.Template
import java.time.LocalDateTime
import java.util.Locale

/**
 * Shows what a message will look like, using sample answers: the last used or first list
 * option, the time now and today's date. Blanks with no sample answer show as `[name]`.
 *
 * Plain Kotlin with no Android types, so it can be unit tested.
 */
object ShortcutPreview {

    fun render(
        shortcut: Shortcut,
        now: LocalDateTime = LocalDateTime.now(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val template = Template.parse(shortcut.template)
        return render(template, FillSession.resolveVariables(shortcut, template), now, locale)
    }

    fun render(
        template: Template,
        variables: List<VariableDef>,
        now: LocalDateTime = LocalDateTime.now(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val values = HashMap<String, String>()
        val types = HashMap<String, VariableType>()
        for (variable in variables) {
            types[variable.key] = variable.type
            sampleValue(variable, now)?.let { values[variable.key] = it }
        }
        return template.render(values, types, now, locale) { "[${it.name}]" }
    }

    private fun sampleValue(variable: VariableDef, now: LocalDateTime): String? = when (variable.type) {
        VariableType.CHOICE ->
            variable.lastValue?.takeIf { it.isNotBlank() } ?: variable.options.firstOrNull { it.isNotBlank() }?.trim()
        VariableType.TEXT, VariableType.NUMBER -> null
        VariableType.TIME -> Template.formatTimeValue(now.toLocalTime())
        VariableType.DATE -> now.toLocalDate().toString()
    }
}
