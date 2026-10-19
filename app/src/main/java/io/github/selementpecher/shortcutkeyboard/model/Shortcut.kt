package io.github.selementpecher.shortcutkeyboard.model

/** How the keyboard asks for a blank's value. */
enum class VariableType(val id: String) {
    /** Tap one of a saved list of options (or type a new one). */
    CHOICE("choice"),

    /** Type free text. */
    TEXT("text"),

    /** Type a number. */
    NUMBER("number"),

    /** A clock time, starts at "now" and can be nudged. Supports {name+4h}. */
    TIME("time"),

    /** A calendar date, starts at "today". Supports {name+1d}. */
    DATE("date");

    companion object {
        fun fromId(id: String?): VariableType = entries.firstOrNull { it.id == id } ?: CHOICE
    }
}

/**
 * One blank in a shortcut, e.g. `child` in "{child} had {medicine}".
 *
 * Names are matched case-insensitively; [key] is the normalised form used for lookups.
 */
data class VariableDef(
    val name: String,
    val type: VariableType = VariableType.CHOICE,
    val options: List<String> = emptyList(),
    /** For [VariableType.CHOICE]: add newly typed values to [options]. */
    val remember: Boolean = true,
    /** The value used last time, offered first next time. */
    val lastValue: String? = null,
) {
    val key: String get() = keyOf(name)

    companion object {
        fun keyOf(name: String): String = name.trim().lowercase()
    }
}

/** A saved message template with blanks to fill in. */
data class Shortcut(
    override val id: String,
    override val name: String,
    /** The message, with blanks in curly brackets, e.g. "{child} had {medicine} at {time}". */
    val template: String,
    val variables: List<VariableDef> = emptyList(),
    /** The folder this sits in, or null for the top level. */
    override val parentId: String? = null,
    /** Position among its neighbours (lowest first). */
    override val order: Int = 0,
) : Node {
    fun variable(name: String): VariableDef? {
        val key = VariableDef.keyOf(name)
        return variables.firstOrNull { it.key == key }
    }
}
