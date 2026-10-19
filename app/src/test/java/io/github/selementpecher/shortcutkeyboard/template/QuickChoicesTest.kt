package io.github.selementpecher.shortcutkeyboard.template

import io.github.selementpecher.shortcutkeyboard.model.VariableType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickChoicesTest {
    @Test
    fun noSavedOptionsMeansTheUsualOnes() {
        assertEquals(listOf(0L, -15L, -30L, -60L), QuickChoices.parse(emptyList(), VariableType.TIME))
        assertEquals(listOf(0L, -1L, 1L), QuickChoices.parse(listOf(""), VariableType.DATE))
    }

    @Test
    fun savedOptionsAreKeptInOrderWithoutRepeatsOrJunk() {
        assertEquals(listOf(-30L, 0L), QuickChoices.parse(listOf("-30", "0", "-30", "abc", "99999"), VariableType.TIME))
    }

    @Test
    fun atMostSix() {
        val many = QuickChoices.presets(VariableType.TIME).map { it.toString() }
        assertEquals(6, QuickChoices.parse(many, VariableType.TIME).size)
    }

    @Test
    fun toggleNeverEmptiesOrOverfills() {
        assertEquals(listOf(0L), QuickChoices.toggle(listOf(0L), 0L))
        assertEquals(listOf(0L, -5L), QuickChoices.toggle(listOf(0L), -5L))
        assertEquals(listOf(-5L), QuickChoices.toggle(listOf(0L, -5L), 0L))
        val full = listOf(0L, -5L, -10L, -15L, -30L, -45L)
        assertEquals(full, QuickChoices.toggle(full, -60L))
    }

    @Test
    fun oddCountsGetAWideFirstTile() {
        assertTrue(QuickChoices.firstIsWide(5))
        assertFalse(QuickChoices.firstIsWide(4))
    }
}
