package io.github.selementpecher.shortcutkeyboard.ime

/**
 * Decides what a drag does, one finger movement at a time. Plain Kotlin, no Android types.
 *
 * [ids] is every tile of the current folder in order (not the "+ New" tile). While the finger
 * moves, [order] is the live preview: the dragged tile takes the slot under the finger and the
 * others shift. Over the middle of a folder tile, or over the back tile, nothing shifts and the
 * drop target is remembered instead.
 */
class DragPlanner(
    ids: List<String>,
    val draggedId: String,
    private val folderIds: Set<String>,
) {
    sealed interface Outcome {
        data class IntoFolder(val folderId: String) : Outcome
        data object UpOneLevel : Outcome
        data class Reorder(val index: Int) : Outcome
    }

    val originalIndex: Int = ids.indexOf(draggedId)

    var order: List<String> = ids
        private set

    /** The folder that would take the tile if it were dropped now. */
    var dropFolderId: String? = null
        private set

    var overBack: Boolean = false
        private set

    /** Moves the preview for a finger over [target] while the grid shows [page]. */
    fun update(page: Int, target: DragTarget) {
        when (target) {
            is DragTarget.Slot -> {
                overBack = false
                val index = GridPaging.globalIndex(page, target.slot)
                val under = order.getOrNull(index)
                if (target.centre && under != null && under != draggedId && under in folderIds) {
                    dropFolderId = under
                    return
                }
                dropFolderId = null
                moveTo(index)
            }
            DragTarget.Back -> {
                overBack = true
                dropFolderId = null
            }
            is DragTarget.Edge, DragTarget.None -> {
                overBack = false
                dropFolderId = null
            }
        }
    }

    private fun moveTo(index: Int) {
        val current = order.indexOf(draggedId)
        if (current < 0) return
        val wanted = index.coerceIn(0, order.size - 1)
        if (wanted == current) return
        val rest = order.filter { it != draggedId }
        order = rest.take(wanted) + draggedId + rest.drop(wanted)
    }

    /** What letting go now would do. A [Outcome.Reorder] index counts the other tiles only. */
    fun outcome(): Outcome {
        dropFolderId?.let { return Outcome.IntoFolder(it) }
        if (overBack) return Outcome.UpOneLevel
        return Outcome.Reorder(order.indexOf(draggedId))
    }

    /** True if letting go now would leave the tile where it started. */
    fun isUnchanged(): Boolean = outcome().let { it is Outcome.Reorder && it.index == originalIndex }
}
