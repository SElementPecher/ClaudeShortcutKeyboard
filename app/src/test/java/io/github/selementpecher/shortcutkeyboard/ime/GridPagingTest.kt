package io.github.selementpecher.shortcutkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridPagingTest {
    @Test
    fun pageCountAlwaysHasAtLeastOnePage() {
        assertEquals(1, GridPaging.pageCount(0))
        assertEquals(1, GridPaging.pageCount(6))
        assertEquals(2, GridPaging.pageCount(7))
        assertEquals(3, GridPaging.pageCount(13))
    }

    @Test
    fun globalIndexAndBack() {
        assertEquals(0, GridPaging.globalIndex(0, 0))
        assertEquals(8, GridPaging.globalIndex(1, 2))
        assertEquals(1, GridPaging.pageOf(8))
        assertEquals(2, GridPaging.slotOf(8))
    }

    @Test
    fun pageItemsSlicesSixAtATime() {
        val items = (1..8).toList()
        assertEquals(listOf(1, 2, 3, 4, 5, 6), GridPaging.pageItems(items, 0))
        assertEquals(listOf(7, 8), GridPaging.pageItems(items, 1))
        assertEquals(emptyList<Int>(), GridPaging.pageItems(items, 5))
    }

    @Test
    fun clampKeepsPageInRange() {
        assertEquals(0, GridPaging.clampPage(-1, 10))
        assertEquals(1, GridPaging.clampPage(4, 10))
        assertEquals(0, GridPaging.clampPage(3, 0))
    }

    @Test
    fun previousAndNext() {
        assertFalse(GridPaging.hasPrevious(0))
        assertTrue(GridPaging.hasPrevious(1))
        assertTrue(GridPaging.hasNext(0, 7))
        assertFalse(GridPaging.hasNext(1, 7))
    }
}
