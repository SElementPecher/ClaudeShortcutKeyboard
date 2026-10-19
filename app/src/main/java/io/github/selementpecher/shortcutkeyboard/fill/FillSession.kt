package io.github.selementpecher.shortcutkeyboard.fill

import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import io.github.selementpecher.shortcutkeyboard.template.RelativeAmount
import io.github.selementpecher.shortcutkeyboard.template.Template
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * Walks through the blanks of one shortcut, one at a time, and builds the final text.
 *
 * Plain Kotlin with no Android types, so the keyboard UI stays thin and this stays testable.
 */
class FillSession(
    val shortcut: Shortcut,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
    private val locale: Locale = Locale.getDefault(),
) {
    val template: Template = Template.parse(shortcut.template)

    /** Every blank in the message, including fixed ones that are never asked. */
    private val allVariables: List<VariableDef> = resolveVariables(shortcut, template)

    /** The blanks to ask about, in the order they first appear in the message. */
    val variables: List<VariableDef> = allVariables.filterNot { isFixed(it) }

    private val types: Map<String, VariableType> = allVariables.associate { it.key to it.type }
    private val values = LinkedHashMap<String, String>()

    var index: Int = 0
        private set

    init {
        val start = now()
        for (variable in allVariables) {
            initialValue(variable, start)?.let { values[variable.key] = it }
        }
    }

    val current: VariableDef? get() = variables.getOrNull(index)
    val isEmpty: Boolean get() = variables.isEmpty()
    val isFirst: Boolean get() = index <= 0
    val isLast: Boolean get() = index >= variables.size - 1

    /** Gives every list that has no value yet its first answer, so the message can be typed straight away. */
    fun useDefaults() {
        for (variable in allVariables) {
            if (variable.type != VariableType.CHOICE || values.containsKey(variable.key)) continue
            variable.options.firstOrNull { it.isNotBlank() }?.let { values[variable.key] = it }
        }
    }

    fun valueOf(variable: VariableDef): String? = values[variable.key]

    fun setValue(variable: VariableDef, value: String) {
        values[variable.key] = value
    }

    /** Empties a blank, so the preview shows it as [name] again. */
    fun clearValue(variable: VariableDef) {
        values.remove(variable.key)
    }

    /** Moves to the next blank. Returns false when there is none. */
    fun next(): Boolean {
        if (isLast) return false
        index++
        return true
    }

    /** Moves to the previous blank. Returns false when already on the first. */
    fun back(): Boolean {
        if (isFirst) return false
        index--
        return true
    }

    fun goTo(position: Int) {
        index = position.coerceIn(0, (variables.size - 1).coerceAtLeast(0))
    }

    /** Nudges a TIME blank by [minutes]. Wraps around midnight. */
    fun adjustTime(variable: VariableDef, minutes: Long) {
        val time = Template.parseTime(values[variable.key].orEmpty()) ?: now().toLocalTime()
        values[variable.key] = Template.formatTimeValue(time.plusMinutes(minutes))
    }

    fun setTimeToNow(variable: VariableDef) {
        values[variable.key] = Template.formatTimeValue(now().toLocalTime())
    }

    /** Nudges a DATE blank by [days]. */
    fun adjustDate(variable: VariableDef, days: Long) {
        val date = Template.parseDate(values[variable.key].orEmpty()) ?: now().toLocalDate()
        values[variable.key] = date.plusDays(days).toString()
    }

    fun setDate(variable: VariableDef, date: LocalDate) {
        values[variable.key] = date.toString()
    }

    /** The message so far, with unfilled blanks shown as [name]. */
    fun preview(): String = now().let { t -> template.render(renderValues(t), types, t, locale) { "[${it.name}]" } }

    /** The finished message. Unfilled blanks become empty. */
    fun result(): String = now().let { t -> template.render(renderValues(t), types, t, locale) { "" } }

    /**
     * Every value as it will be printed, keyed by [VariableDef.key]: includes fixed "how long" blanks (which are not
     * steps) and turns amounts into what the message shows ("10 mins", "20:35" or "10 mins (20:35)").
     */
    fun printedValues(): Map<String, String> = renderValues(now())

    /** The values as printed: "how long" blanks become "in 5 mins" / "5 mins ago" (with the clock if switched on). */
    private fun renderValues(at: LocalDateTime): Map<String, String> {
        if (allVariables.none { it.type.isRelative }) return values
        val printed = LinkedHashMap(values)
        for (variable in allVariables) {
            if (!variable.type.isRelative) continue
            val value = values[variable.key] ?: continue
            printed[variable.key] = relativePhrase(variable, value, at)
        }
        return printed
    }

    private fun relativePhrase(variable: VariableDef, value: String, at: LocalDateTime): String {
        val amount = RelativeAmount.parse(value) ?: return value
        return amount.phrase(variable.type == VariableType.TIME_AHEAD, variable.show, at, locale)
    }

    /** How a value is shown on screen, e.g. a DATE "2026-10-16" as "16 Oct 2026". */
    fun displayValue(variable: VariableDef): String {
        val value = values[variable.key].orEmpty()
        return when (variable.type) {
            VariableType.DATE -> Template.parse("{d}")
                .render(mapOf("d" to value), mapOf("d" to VariableType.DATE), now(), locale)
            VariableType.TIME_AHEAD, VariableType.TIME_AGO -> if (value.isEmpty()) value else relativePhrase(variable, value, now())
            else -> value
        }
    }

    /**
     * The shortcut with what was learned this time: last used values, and new list options
     * the user typed (when the blank is set to remember them).
     */
    fun updatedShortcut(): Shortcut {
        val learned = allVariables.map { variable ->
            if (isFixed(variable)) return@map variable
            val value = values[variable.key]?.trim().orEmpty()
            when (variable.type) {
                VariableType.TIME, VariableType.DATE -> variable
                VariableType.TIME_AHEAD, VariableType.TIME_AGO -> {
                    // Remember the amount as it was typed in, not the printed phrase (the clock would be stale).
                    val amount = RelativeAmount.parse(value) ?: return@map variable
                    variable.copy(lastValue = amount.text())
                }
                VariableType.CHOICE -> {
                    if (value.isEmpty()) return@map variable
                    val known = variable.options.firstOrNull { it.equals(value, ignoreCase = true) }
                    variable.copy(
                        options = if (variable.remember && known == null) variable.options + value else variable.options,
                        lastValue = known ?: value,
                    )
                }
                else -> if (value.isEmpty()) variable else variable.copy(lastValue = value)
            }
        }
        val learnedByKey = learned.associateBy { it.key }
        val merged = shortcut.variables.map { learnedByKey[it.key] ?: it }
        val added = learned.filter { new -> shortcut.variables.none { it.key == new.key } }
        return shortcut.copy(variables = merged + added)
    }

    private fun initialValue(variable: VariableDef, start: LocalDateTime): String? = when (variable.type) {
        VariableType.TIME -> Template.formatTimeValue(start.toLocalTime())
        VariableType.DATE -> start.toLocalDate().toString()
        VariableType.CHOICE, VariableType.NUMBER -> variable.lastValue?.takeIf { it.isNotBlank() }
        VariableType.TEXT -> null
        VariableType.TIME_AHEAD, VariableType.TIME_AGO ->
            (if (isFixed(variable)) variable.options.firstOrNull() else variable.lastValue)?.takeIf { RelativeAmount.parse(it) != null }
    }

    companion object {
        /** A "how long" blank with one saved amount that is always used and never asked. */
        fun isFixed(variable: VariableDef): Boolean =
            variable.fixed && variable.type.isRelative && RelativeAmount.parse(variable.options.firstOrNull()) != null

        /**
         * The blanks a template asks for. Uses the saved settings for each blank where there are
         * some, and a guess from the blank's name otherwise.
         */
        fun resolveVariables(shortcut: Shortcut, template: Template = Template.parse(shortcut.template)): List<VariableDef> =
            template.variableNames.map { name ->
                shortcut.variable(name)
                    ?: VariableDef(name = name, type = Template.guessType(name, template.usesOffset(name)))
            }
    }
}
