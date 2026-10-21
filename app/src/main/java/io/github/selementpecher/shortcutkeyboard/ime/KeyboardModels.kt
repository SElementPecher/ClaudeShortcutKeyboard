package io.github.selementpecher.shortcutkeyboard.ime

import io.github.selementpecher.shortcutkeyboard.fill.ResultPiece

/** What the grid screen shows. The service builds this; [KeyboardView] draws it. */
internal class GridModel(
    val inFolder: Boolean,
    val pathText: String,
    val page: Int,
    val pageCount: Int,
    val tiles: List<TileSpec?>,
    /** Nothing in this folder yet: a friendly message and one big "+ New" tile instead of the grid. */
    val isEmpty: Boolean,
    val backHighlighted: Boolean,
    val onBack: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onAbc: () -> Unit,
    val onSettings: () -> Unit,
)

/** One question of the fill-in flow. */
internal class StepModel(
    val shortcutName: String,
    val step: Int,
    val steps: Int,
    val question: String,
    val onBack: () -> Unit,
    val onCancel: () -> Unit,
    val body: StepBody,
    /** The whole message as it would be typed right now (defaults filled in), shown as a tile at the top. */
    val preview: List<ResultPiece> = emptyList(),
    /** Pressing the preview tile types the message now. */
    val onAddNow: () -> Unit = {},
    /** Adding will press the app's Send button too, so the preview shows a send arrow instead of a tick. */
    val sends: Boolean = false,
)

internal sealed interface StepBody {
    /** Answers as tiles, six to a page. [hint] is a short line above them. */
    class Tiles(
        val tiles: List<TileSpec?>,
        val page: Int,
        val pageCount: Int,
        val hint: String?,
        val onPrevious: () -> Unit,
        val onNext: () -> Unit,
    ) : StepBody

    /** The number pad. With [relative] it is the "Other" screen of a "how long" question: digits only, plus unit tiles. */
    class Number(
        val value: String,
        val onKey: (Char) -> Unit,
        val onBackspace: () -> Unit,
        val onOk: () -> Unit,
        val relative: RelativeEntry? = null,
    ) : StepBody

    /** A big value with nudge buttons and OK (the "Other" time and date screens). */
    class Adjust(
        val value: String,
        val later: String?,
        val buttons: List<TileSpec>,
        val ok: TileSpec,
    ) : StepBody
}

/**
 * What the "Other" screen adds to the number pad: the Mins / Hours / Days tiles, the amount typed as it
 * will print (shown big and live), and whether OK may be pressed yet.
 */
internal class RelativeEntry(
    val units: List<TileSpec>,
    val result: String,
    /** The typed amount on its own ("30 mins"), for when [result] shows only the clock time. */
    val typed: String?,
    val okEnabled: Boolean,
)

/** The last screen: the finished message and the Add button. */
internal class CheckModel(
    val shortcutName: String,
    /** The message in pieces: fixed words and the values that were filled in. */
    val pieces: List<ResultPiece>,
    val onBack: () -> Unit,
    val onCancel: () -> Unit,
    /** Pressing the message card adds it. */
    val onAdd: () -> Unit,
    /** Adding will press the app's Send button too, so the card shows a send arrow instead of a tick. */
    val sends: Boolean = false,
    /** A short line under the message, e.g. how to let quiKey send in this app. Null for none. */
    val hint: String? = null,
)
