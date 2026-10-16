package io.github.selementpecher.shortcutkeyboard.data

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.fill.TriggerDetector
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
    fun `there are samples with unique ids and triggers`() {
        val samples = SampleShortcuts.all
        assertTrue(samples.isNotEmpty())
        assertEquals(samples.size, samples.map { it.id }.toSet().size)
        val triggers = samples.map { it.trigger.lowercase() }.filter { it.isNotBlank() }
        assertEquals(triggers.size, triggers.toSet().size)
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
    fun `sample triggers have no spaces`() {
        for (sample in SampleShortcuts.all) {
            assertTrue(sample.trigger.none { it.isWhitespace() })
        }
    }

    @Test
    fun `each sample trigger opens its own shortcut`() {
        for (sample in SampleShortcuts.all.filter { it.trigger.isNotBlank() }) {
            val match = TriggerDetector.find("Hi ${sample.trigger}", SampleShortcuts.all)
            assertEquals(sample, match?.shortcut)
            assertEquals(sample.trigger.length, match?.length)
        }
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
