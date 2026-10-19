package io.github.selementpecher.shortcutkeyboard.data

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.template.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

/** Checks the examples shown on first run work end to end. */
class SampleShortcutsTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 16, 20, 20)

    @Test
    fun `there are samples with unique ids`() {
        val samples = SampleShortcuts.all
        assertTrue(samples.isNotEmpty())
        assertEquals(samples.size, samples.map { it.id }.toSet().size)
        val all = samples.map { it.id } + SampleShortcuts.folders.map { it.id }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `the samples sit in folders that exist and need no repairs`() {
        assertEquals(SampleShortcuts.library, SampleShortcuts.library.repaired())
        val meds = SampleShortcuts.library.children("sample-folder-meds").map { it.id }
        assertTrue("sample-medicine" in meds)
        assertEquals(listOf("Kids", "Meds"), SampleShortcuts.library.path("sample-folder-meds").map { it.name })
    }

    @Test
    fun `every blank in a sample has saved settings`() {
        for (sample in SampleShortcuts.all) {
            for (name in Template.parse(sample.template).variableNames) {
                assertNotNull("${sample.name} has no settings for {$name}", sample.variable(name))
            }
        }
    }

    @Test
    fun `the fixed medicine sample only asks for the time`() {
        val fixed = SampleShortcuts.all.first { it.id == "sample-sam-calpol" }
        val session = FillSession(fixed, { now }, Locale.UK)
        assertEquals(listOf("time"), session.variables.map { it.name })
        assertEquals("Sam, Calpol, 5ml, 20:20 next dose not before 00:20", session.result())
    }

    @Test
    fun `the medicine sample makes the example message`() {
        val medicine = SampleShortcuts.all.first { it.id == "sample-medicine" }
        val session = FillSession(medicine, { now }, Locale.UK)
        for ((name, value) in listOf("child" to "Sam", "medicine" to "Calpol", "dose" to "5ml")) {
            session.setValue(session.variables.first { it.name == name }, value)
        }
        assertEquals("Sam, Calpol, 5ml, 20:20 next dose not before 00:20", session.result())
    }

    @Test
    fun `the medicine sample asks one question per blank in order`() {
        val medicine = SampleShortcuts.all.first { it.id == "sample-medicine" }
        val session = FillSession(medicine, { now }, Locale.UK)
        assertEquals(listOf("child", "medicine", "dose", "time"), session.variables.map { it.name })
    }
}
