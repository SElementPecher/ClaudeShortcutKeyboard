package io.github.selementpecher.shortcutkeyboard.ime

import io.github.selementpecher.shortcutkeyboard.look.Looks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardMetricsTest {
    @Test
    fun gridShapeFollowsTheLookWhenUpright() {
        assertEquals(2 to 3, KeyboardMetrics.gridShape(Looks.CALM, compact = false))
        assertEquals(2 to 3, KeyboardMetrics.gridShape(Looks.BOLD, compact = false))
        assertEquals(3 to 2, KeyboardMetrics.gridShape(Looks.SQUARES, compact = false))
    }

    @Test
    fun shortScreensAlwaysGetThreeByTwo() {
        for (look in Looks.all) assertEquals(3 to 2, KeyboardMetrics.gridShape(look, compact = true))
        assertTrue(KeyboardMetrics.isCompact(360))
        assertFalse(KeyboardMetrics.isCompact(800))
        // Unknown height (0) is treated as an ordinary phone.
        assertFalse(KeyboardMetrics.isCompact(0))
    }

    @Test
    fun everyShapeHoldsSixTiles() {
        for (look in Looks.all) for (compact in listOf(true, false)) {
            val (c, r) = KeyboardMetrics.gridShape(look, compact)
            assertEquals(GridPaging.PER_PAGE, c * r)
        }
    }

    @Test
    fun pictureSitsBesideTheNameExceptInSquaresUpright() {
        assertTrue(KeyboardMetrics.pictureBesideName(Looks.CALM, compact = false))
        assertTrue(KeyboardMetrics.pictureBesideName(Looks.BOLD, compact = false))
        assertFalse(KeyboardMetrics.pictureBesideName(Looks.SQUARES, compact = false))
        assertTrue(KeyboardMetrics.pictureBesideName(Looks.SQUARES, compact = true))
    }

    @Test
    fun bottomPaddingClearsTheSystemSwitcherIcons() {
        // 3x density: 48 + 16 dp on Android 13+, 48 + 8 dp before.
        assertEquals(144 + 48, KeyboardMetrics.bottomPaddingPx(144, 3f, 34))
        assertEquals(144 + 24, KeyboardMetrics.bottomPaddingPx(144, 3f, 30))
        // The slim gesture bar (24dp) still keeps the whole 56dp switcher zone free on Android 13+.
        assertEquals(168, KeyboardMetrics.bottomPaddingPx(72, 3f, 35))
        // No navigation inset still leaves the gap; a negative inset never shrinks it.
        assertEquals(168, KeyboardMetrics.bottomPaddingPx(0, 3f, 35))
        assertEquals(168, KeyboardMetrics.bottomPaddingPx(-20, 3f, 35))
        assertEquals(24, KeyboardMetrics.bottomPaddingPx(0, 3f, 30))
    }
}
