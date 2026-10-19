package io.github.selementpecher.shortcutkeyboard.look

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Text must be easy to read on every colour in every design (WCAG contrast of at least 4.5). */
class LooksTest {

    private fun luminance(color: Int): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color shr 16 and 0xFF) + 0.7152 * channel(color shr 8 and 0xFF) + 0.0722 * channel(color and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(what: String, fg: Int, bg: Int, min: Double = 4.5) {
        val ratio = contrast(fg, bg)
        assertTrue("$what has contrast %.2f, needs $min".format(ratio), ratio >= min)
    }

    private fun palettes() = Looks.all.flatMap { listOf("${it.id} light" to it.light, "${it.id} dark" to it.dark) }

    @Test
    fun `text is readable on every background`() {
        for ((name, p) in palettes()) {
            check("$name ink on bg", p.ink, p.bg)
            check("$name ink on surface", p.ink, p.surface)
            check("$name ink on surfaceAlt", p.ink, p.surfaceAlt)
            check("$name inkSoft on bg", p.inkSoft, p.bg)
            check("$name inkSoft on surface", p.inkSoft, p.surface)
            check("$name onAccent on accent", p.onAccent, p.accent)
            check("$name accent on bg (links, ticks)", p.accent, p.bg, 3.0)
            check("$name success on bg", p.success, p.bg, 3.0)
            check("$name danger on bg", p.danger, p.bg, 4.5)
            check("$name folder text", p.folder.fg, p.folder.bg)
            p.tiles.forEachIndexed { i, tile -> check("$name tile $i", tile.fg, tile.bg) }
            p.swatches.forEachIndexed { i, tile -> check("$name swatch $i", tile.fg, tile.bg) }
        }
    }

    @Test
    fun `every design has six tile colours and shows six tiles`() {
        for (look in Looks.all) {
            assertEquals(6, look.light.tiles.size)
            assertEquals(6, look.dark.tiles.size)
            assertEquals(6, look.keyboardColumns * look.keyboardRows)
        }
    }

    @Test
    fun `ids are unique and unknown ids fall back to Calm`() {
        assertEquals(Looks.all.size, Looks.all.map { it.id }.toSet().size)
        assertEquals(Looks.CALM, Looks.byId(null))
        assertEquals(Looks.CALM, Looks.byId("nope"))
        assertEquals(Looks.BOLD, Looks.byId("bold"))
    }

    @Test
    fun `a tile always gets the same colour and the colours are spread`() {
        val p = Looks.CALM.light
        assertEquals(p.tileColorsFor("abc"), p.tileColorsFor("abc"))
        val used = (1..60).map { p.tileColorsFor("shortcut-$it") }.toSet()
        assertTrue("only ${used.size} colours used", used.size >= 5)
    }

    @Test
    fun `there are 16 swatches in every palette and they are all different`() {
        for ((name, p) in palettes()) {
            assertEquals(name, SWATCH_COUNT, p.swatches.size)
            assertEquals("$name has repeated swatch backgrounds", SWATCH_COUNT, p.swatches.map { it.bg }.toSet().size)
        }
    }

    @Test
    fun `a picked colour wins and automatic stays stable`() {
        val p = Looks.BOLD.light
        assertEquals(p.swatches[5], p.tileColorsFor("anything", 5))
        assertEquals(p.swatches[0], p.folderColors(0))
        assertEquals(p.folder, p.folderColors(-1))
        assertEquals(p.folder, p.folderColors(99))
        assertEquals(p.tileColorsFor("abc"), p.tileColorsFor("abc", -1))
        assertEquals(p.tileColorsFor("abc"), p.tileColorsFor("abc", 16))
    }
}
