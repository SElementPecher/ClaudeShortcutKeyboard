package io.github.selementpecher.shortcutkeyboard.fill

import org.junit.Assert.assertEquals
import org.junit.Test

class PillChunksTest {
    @Test fun `a short value is one pill`() {
        assertEquals(listOf("10 mins (18:40)"), PillChunks.of("10 mins (18:40)"))
        assertEquals(listOf("Sam"), PillChunks.of("Sam"))
    }

    @Test fun `nothing gives no pill`() {
        assertEquals(emptyList<String>(), PillChunks.of(""))
    }

    @Test fun `a long value is cut at spaces into whole pills`() {
        assertEquals(listOf("the school gate", "by the doors"), PillChunks.of("the school gate by the doors"))
    }

    @Test fun `no word is ever split and no pill is empty`() {
        val chunks = PillChunks.of("an extraordinarily-long-single-word-that-does-not-fit and more")
        assertEquals(listOf("an", "extraordinarily-long-single-word-that-does-not-fit", "and more"), chunks)
        assertEquals("an extraordinarily-long-single-word-that-does-not-fit and more", chunks.joinToString(" "))
    }
}
