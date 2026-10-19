package io.github.selementpecher.shortcutkeyboard.ui

import io.github.selementpecher.shortcutkeyboard.ime.DragPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DropPlanTest {
    private val ids = listOf("a", "b", "c", "folder")
    private val folders = setOf("folder")

    @Test fun `dropping on yourself changes nothing`() {
        assertNull(DropPlan.outcome(ids, folders, "b", 1, centre = true, overUp = false))
        assertNull(DropPlan.outcome(ids, folders, "b", null, centre = false, overUp = false))
    }

    @Test fun `dropping on another tile moves to its place`() {
        assertEquals(DragPlanner.Outcome.Reorder(0), DropPlan.outcome(ids, folders, "c", 0, centre = true, overUp = false))
        assertEquals(DragPlanner.Outcome.Reorder(2), DropPlan.outcome(ids, folders, "a", 2, centre = true, overUp = false))
    }

    @Test fun `the middle of a folder takes the tile in`() {
        assertEquals(DragPlanner.Outcome.IntoFolder("folder"), DropPlan.outcome(ids, folders, "a", 3, centre = true, overUp = false))
        assertEquals(DragPlanner.Outcome.Reorder(3), DropPlan.outcome(ids, folders, "a", 3, centre = false, overUp = false))
    }

    @Test fun `the up target moves the tile one level up`() {
        assertEquals(DragPlanner.Outcome.UpOneLevel, DropPlan.outcome(ids, folders, "a", null, centre = false, overUp = true))
    }
}
