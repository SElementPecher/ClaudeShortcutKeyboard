package io.github.selementpecher.shortcutkeyboard.ime

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

    /** The number pad. */
    class Number(
        val value: String,
        val onKey: (Char) -> Unit,
        val onBackspace: () -> Unit,
        val onOk: () -> Unit,
    ) : StepBody

    /** A big value with nudge buttons and OK (the "Other" time and date screens). */
    class Adjust(
        val value: String,
        val later: String?,
        val buttons: List<TileSpec>,
        val ok: TileSpec,
    ) : StepBody
}

/** The last screen: the finished message and the Add button. */
internal class CheckModel(
    val shortcutName: String,
    val text: String,
    val onBack: () -> Unit,
    val onCancel: () -> Unit,
    val onAdd: () -> Unit,
)
