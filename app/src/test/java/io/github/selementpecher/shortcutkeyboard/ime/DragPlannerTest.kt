package io.github.selementpecher.shortcutkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragPlannerTest {
    // a b c(folder) d e f g h
    private val ids = listOf("a", "b", "c", "d", "e", "f", "g", "h")
    private val folders = setOf("c")

    private fun planner(dragged: String) = DragPlanner(ids, dragged, folders)

    @Test
    fun startsUnchanged() {
        val p = planner("a")
        assertEquals(ids, p.order)
        assertEquals(0, p.originalIndex)
        assertTrue(p.isUnchanged())
    }

    @Test
    fun overAnotherTileTakesItsSlotAndOthersShift() {
        val p = planner("a")
        p.update(0, DragTarget.Slot(1, centre = false))
        assertEquals(listOf("b", "a", "c", "d", "e", "f", "g", "h"), p.order)
        assertEquals(DragPlanner.Outcome.Reorder(1), p.outcome())
        assertFalse(p.isUnchanged())
    }

    @Test
    fun movingBackwardsWorksToo() {
        val p = planner("d")
        p.update(0, DragTarget.Slot(0, centre = true))
        assertEquals(listOf("d", "a", "b", "c", "e", "f", "g", "h"), p.order)
        assertEquals(DragPlanner.Outcome.Reorder(0), p.outcome())
    }

    @Test
    fun pageNumberCountsTowardsTheGlobalIndex() {
        val p = planner("a")
        p.update(1, DragTarget.Slot(0, centre = false))
        assertEquals(listOf("b", "c", "d", "e", "f", "g", "a", "h"), p.order)
        assertEquals(DragPlanner.Outcome.Reorder(6), p.outcome())
    }

    @Test
    fun slotsPastTheEndMeanTheLastPlace() {
        val p = planner("a")
        p.update(1, DragTarget.Slot(5, centre = true))
        assertEquals("a", p.order.last())
        assertEquals(DragPlanner.Outcome.Reorder(7), p.outcome())
    }

    @Test
    fun centreOfAFolderMeansDropInside() {
        val p = planner("a")
        p.update(0, DragTarget.Slot(2, centre = true))
        assertEquals("c", p.dropFolderId)
        assertEquals(ids, p.order)
        assertEquals(DragPlanner.Outcome.IntoFolder("c"), p.outcome())
    }

    @Test
    fun edgeOfAFolderJustReorders() {
        val p = planner("a")
        p.update(0, DragTarget.Slot(2, centre = false))
        assertNull(p.dropFolderId)
        assertEquals(listOf("b", "c", "a", "d", "e", "f", "g", "h"), p.order)
    }

    @Test
    fun aFolderCannotBeDroppedOnItself() {
        val p = planner("c")
        p.update(0, DragTarget.Slot(2, centre = true))
        assertNull(p.dropFolderId)
        assertTrue(p.isUnchanged())
    }

    @Test
    fun leavingTheFolderClearsTheHighlight() {
        val p = planner("a")
        p.update(0, DragTarget.Slot(2, centre = true))
        p.update(0, DragTarget.Slot(3, centre = false))
        assertNull(p.dropFolderId)
    }

    @Test
    fun backTileMeansUpOneLevel() {
        val p = planner("b")
        p.update(0, DragTarget.Back)
        assertTrue(p.overBack)
        assertEquals(DragPlanner.Outcome.UpOneLevel, p.outcome())
        p.update(0, DragTarget.Slot(0, centre = false))
        assertFalse(p.overBack)
    }

    @Test
    fun edgesAndNothingDoNotMoveAnything() {
        val p = planner("b")
        p.update(0, DragTarget.Slot(0, centre = false))
        val moved = p.order
        p.update(0, DragTarget.Edge(1))
        p.update(0, DragTarget.None)
        assertEquals(moved, p.order)
        assertNull(p.dropFolderId)
    }

    @Test
    fun dragAcrossPagesKeepsTheOrderPreview() {
        val p = planner("a")
        p.update(0, DragTarget.Edge(1))
        p.update(1, DragTarget.Slot(1, centre = false))
        assertEquals(listOf("b", "c", "d", "e", "f", "g", "h", "a"), p.order)
        assertEquals(DragPlanner.Outcome.Reorder(7), p.outcome())
    }
}
