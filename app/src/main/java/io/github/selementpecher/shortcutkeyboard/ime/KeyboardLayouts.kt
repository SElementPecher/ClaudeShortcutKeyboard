package io.github.selementpecher.shortcutkeyboard.ime

/** What a key does. */
enum class KeyKind {
    /** Types [Key.text]. */
    CHARACTER,
    SHIFT,
    DELETE,
    ENTER,
    SPACE,

    /** Shows the first page of numbers and symbols ("?123"). */
    TO_SYMBOLS,

    /** Shows the second page of symbols ("=\<"). */
    TO_MORE_SYMBOLS,

    /** Back to the letters ("ABC"). */
    TO_LETTERS,

    /** Switches to the next keyboard app (the globe key). */
    NEXT_KEYBOARD,
}

/** The pages of keys. */
enum class KeyboardPage { LETTERS, SYMBOLS, MORE_SYMBOLS }

/**
 * One key. Plain data with no Android types, so layouts can be checked in plain unit tests.
 *
 * @property label what the key shows; icon keys (shift, delete, enter, globe) leave it empty.
 * @property text what a [KeyKind.CHARACTER] key types (before shift is applied).
 * @property weight width compared with one letter key; every row adds up to [ROW_WEIGHT].
 * @property padStart empty space before the visible key, in key widths. It still belongs to the
 *   key's touch area, so a tap near the edge of a short row is not lost.
 * @property padEnd the same after the visible key.
 */
data class Key(
    val kind: KeyKind,
    val label: String = "",
    val text: String = label,
    val weight: Float = 1f,
    val padStart: Float = 0f,
    val padEnd: Float = 0f,
) {
    companion object {
        const val ROW_WEIGHT = 10f
    }
}

/** The key rows for each page: QWERTY letters plus two pages of numbers and symbols. */
object KeyboardLayouts {

    /** Rows from top to bottom. The globe key is left out when [showNextKeyboard] is false. */
    fun rows(page: KeyboardPage, showNextKeyboard: Boolean): List<List<Key>> = when (page) {
        KeyboardPage.LETTERS -> listOf(
            characters("qwertyuiop"),
            characters("asdfghjkl").mapIndexed { i, key ->
                when (i) {
                    0 -> key.copy(weight = 1.5f, padStart = 0.5f)
                    8 -> key.copy(weight = 1.5f, padEnd = 0.5f)
                    else -> key
                }
            },
            listOf(Key(KeyKind.SHIFT, weight = 1.5f)) + characters("zxcvbnm") + Key(KeyKind.DELETE, weight = 1.5f),
            bottomRow(Key(KeyKind.TO_SYMBOLS, label = "?123", weight = 1.5f), showNextKeyboard),
        )
        KeyboardPage.SYMBOLS -> listOf(
            characters("1234567890"),
            characters("@#£_&-+()/"),
            listOf(Key(KeyKind.TO_MORE_SYMBOLS, label = "=\\<", weight = 1.5f)) +
                characters("*\"':;!?") + Key(KeyKind.DELETE, weight = 1.5f),
            bottomRow(Key(KeyKind.TO_LETTERS, label = "ABC", weight = 1.5f), showNextKeyboard),
        )
        KeyboardPage.MORE_SYMBOLS -> listOf(
            characters("~`|€\$¥^°=%"),
            characters("{}[]<>\\©®™"),
            listOf(Key(KeyKind.TO_SYMBOLS, label = "?123", weight = 2f)) +
                characters("•…✓×÷¶") + Key(KeyKind.DELETE, weight = 2f),
            bottomRow(Key(KeyKind.TO_LETTERS, label = "ABC", weight = 1.5f), showNextKeyboard),
        )
    }

    private fun bottomRow(pageKey: Key, showNextKeyboard: Boolean): List<Key> = buildList {
        add(pageKey)
        add(Key(KeyKind.CHARACTER, label = ","))
        if (showNextKeyboard) add(Key(KeyKind.NEXT_KEYBOARD))
        add(Key(KeyKind.SPACE, text = " ", weight = if (showNextKeyboard) 4f else 5f))
        add(Key(KeyKind.CHARACTER, label = "."))
        add(Key(KeyKind.ENTER, weight = 1.5f))
    }

    /** One character key per character (code point) of [chars]. */
    private fun characters(chars: String): List<Key> {
        val keys = ArrayList<Key>()
        var i = 0
        while (i < chars.length) {
            val end = i + Character.charCount(chars.codePointAt(i))
            keys += Key(KeyKind.CHARACTER, label = chars.substring(i, end))
            i = end
        }
        return keys
    }
}
