package io.github.selementpecher.shortcutkeyboard.ime

/**
 * Where the cells of a tile grid are, so a finger position can be turned into a slot.
 * Coordinates are in pixels, with (0, 0) at the top left of the grid.
 */
class GridGeometry(
    val cols: Int,
    val rows: Int,
    val width: Float,
    val height: Float,
    val gap: Float,
) {
    val cellWidth: Float = (width - gap * (cols - 1)) / cols
    val cellHeight: Float = (height - gap * (rows - 1)) / rows

    /** The slot under the point, or null when it is outside the grid. */
    fun slotAt(x: Float, y: Float): Int? {
        if (x < 0f || y < 0f || x >= width || y >= height) return null
        val col = (x / (cellWidth + gap)).toInt().coerceIn(0, cols - 1)
        val row = (y / (cellHeight + gap)).toInt().coerceIn(0, rows - 1)
        return row * cols + col
    }

    /** True when the point is in the middle half (both ways) of the cell for [slot]. */
    fun isCentre(slot: Int, x: Float, y: Float): Boolean {
        val col = slot % cols
        val row = slot / cols
        val centreX = col * (cellWidth + gap) + cellWidth / 2
        val centreY = row * (cellHeight + gap) + cellHeight / 2
        return Math.abs(x - centreX) <= cellWidth / 4 && Math.abs(y - centreY) <= cellHeight / 4
    }
}

/** What a dragged tile is over. */
sealed interface DragTarget {
    /** A slot of the grid; [centre] is true over the middle of the tile. */
    data class Slot(val slot: Int, val centre: Boolean) : DragTarget

    /** The back tile ("move up a level"). */
    data object Back : DragTarget

    /** The left (-1) or right (+1) edge, where holding on flips the page. */
    data class Edge(val direction: Int) : DragTarget

    data object None : DragTarget
}

object DragGeometry {
    /** Fraction of the grid width, at each side, that flips the page. */
    const val EDGE_FRACTION = 0.14f

    fun target(
        geometry: GridGeometry,
        x: Float,
        y: Float,
        overBack: Boolean,
        canFlipPrevious: Boolean,
        canFlipNext: Boolean,
    ): DragTarget {
        if (overBack) return DragTarget.Back
        if (y >= 0f && y < geometry.height) {
            if (canFlipPrevious && x <= geometry.width * EDGE_FRACTION) return DragTarget.Edge(-1)
            if (canFlipNext && x >= geometry.width * (1f - EDGE_FRACTION)) return DragTarget.Edge(1)
        }
        val slot = geometry.slotAt(x, y) ?: return DragTarget.None
        return DragTarget.Slot(slot, geometry.isCentre(slot, x, y))
    }
}

/** Turns "finger held at an edge" into page flips: the first after [delayMs], then one per [delayMs]. */
class EdgeDwell(private val delayMs: Long = 600L) {
    private var direction = 0
    private var since = 0L

    /** Call often with the current edge (0 = none). Returns true when the page should flip now. */
    fun update(edge: Int, nowMs: Long): Boolean {
        if (edge == 0) {
            direction = 0
            return false
        }
        if (edge != direction) {
            direction = edge
            since = nowMs
            return false
        }
        if (nowMs - since >= delayMs) {
            since = nowMs
            return true
        }
        return false
    }

    fun reset() {
        direction = 0
    }
}
