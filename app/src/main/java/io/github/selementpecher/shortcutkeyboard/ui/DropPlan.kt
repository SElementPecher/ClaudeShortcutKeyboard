package io.github.selementpecher.shortcutkeyboard.ui

import io.github.selementpecher.shortcutkeyboard.ime.DragPlanner
import io.github.selementpecher.shortcutkeyboard.ime.DragTarget

/**
 * What letting go of a tile does on the app's home screen. Plain Kotlin, so it can be tested.
 * It is the keyboard's [DragPlanner] with one page, so both screens follow the same rules.
 */
object DropPlan {

    /**
     * @param ids every tile of the folder in order.
     * @param hoverIndex the tile under the finger (a position in [ids]), or null.
     * @param centre true over the middle of that tile (a folder there takes the tile).
     * @param overUp true over the "move up a level" target.
     * @return what to do, or null when the tile would stay where it is.
     */
    fun outcome(
        ids: List<String>,
        folderIds: Set<String>,
        draggedId: String,
        hoverIndex: Int?,
        centre: Boolean,
        overUp: Boolean,
    ): DragPlanner.Outcome? {
        val planner = DragPlanner(ids, draggedId, folderIds)
        val target = when {
            overUp -> DragTarget.Back
            hoverIndex != null -> DragTarget.Slot(hoverIndex, centre)
            else -> DragTarget.None
        }
        planner.update(0, target)
        return if (planner.isUnchanged()) null else planner.outcome()
    }
}
