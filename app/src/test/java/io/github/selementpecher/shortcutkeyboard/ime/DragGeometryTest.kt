package io.github.selementpecher.shortcutkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragGeometryTest {
    // 2 columns x 3 rows, 200 x 320 px, 8 px gaps: cells are 96 x 100.
    private val grid = GridGeometry(cols = 2, rows = 3, width = 200f, height = 320f, gap = 8f)

    @Test
    fun cellSizeAccountsForGaps() {
        assertEquals(96f, grid.cellWidth, 0.01f)
        assertEquals(101.33f, grid.cellHeight, 0.01f)
    }

    @Test
    fun slotAtFindsTheCell() {
        assertEquals(0, grid.slotAt(10f, 10f))
        assertEquals(1, grid.slotAt(150f, 10f))
        assertEquals(2, grid.slotAt(10f, 150f))
        assertEquals(5, grid.slotAt(150f, 300f))
    }

    @Test
    fun slotAtIsNullOutsideTheGrid() {
        assertNull(grid.slotAt(-1f, 10f))
        assertNull(grid.slotAt(10f, 400f))
        assertNull(grid.slotAt(200f, 10f))
    }

    @Test
    fun centreIsTheMiddleHalf() {
        // Slot 0 centre is (48, 50.6): half-width 24, half-height 25.3.
        assertTrue(grid.isCentre(0, 48f, 50f))
        assertTrue(grid.isCentre(0, 30f, 40f))
        assertFalse(grid.isCentre(0, 10f, 50f))
        assertFalse(grid.isCentre(0, 48f, 5f))
        assertTrue(grid.isCentre(1, 152f, 50f))
    }

    @Test
    fun backWinsOverEverything() {
        val target = DragGeometry.target(grid, 10f, 10f, overBack = true, canFlipPrevious = true, canFlipNext = true)
        assertEquals(DragTarget.Back, target)
    }

    @Test
    fun edgesFlipOnlyWhenThereIsAPageThere() {
        // 14% of 200 = 28 px.
        assertEquals(DragTarget.Edge(-1), DragGeometry.target(grid, 20f, 100f, false, true, true))
        assertEquals(DragTarget.Edge(1), DragGeometry.target(grid, 185f, 100f, false, true, true))
        assertEquals(DragTarget.Slot(0, false), DragGeometry.target(grid, 20f, 10f, false, false, true))
        assertEquals(DragTarget.Slot(1, false), DragGeometry.target(grid, 185f, 10f, false, true, false))
    }

    @Test
    fun targetReportsSlotAndCentre() {
        assertEquals(DragTarget.Slot(1, true), DragGeometry.target(grid, 152f, 50f, false, false, false))
        assertEquals(DragTarget.Slot(3, false), DragGeometry.target(grid, 120f, 125f, false, false, false))
    }

    @Test
    fun outsideTheGridIsNone() {
        assertEquals(DragTarget.None, DragGeometry.target(grid, 100f, 500f, false, true, true))
    }

    @Test
    fun edgeDwellFlipsAfterTheDelayAndRepeats() {
        val dwell = EdgeDwell(600)
        assertFalse(dwell.update(1, 0))
        assertFalse(dwell.update(1, 599))
        assertTrue(dwell.update(1, 600))
        assertFalse(dwell.update(1, 900))
        assertTrue(dwell.update(1, 1200))
    }

    @Test
    fun edgeDwellResetsWhenTheFingerLeavesOrSwitchesSide() {
        val dwell = EdgeDwell(600)
        dwell.update(1, 0)
        assertFalse(dwell.update(0, 500))
        assertFalse(dwell.update(1, 600))
        assertFalse(dwell.update(-1, 1000))
        assertFalse(dwell.update(-1, 1500))
        assertTrue(dwell.update(-1, 1600))
    }
}
