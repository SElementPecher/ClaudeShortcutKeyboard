package io.github.selementpecher.shortcutkeyboard.data

import io.github.selementpecher.shortcutkeyboard.fill.FillSession
import io.github.selementpecher.shortcutkeyboard.template.Template
import io.github.selementpecher.shortcutkeyboard.ui.IconPicker
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
    fun `every picture the examples use can be picked`() {
        val nodes = SampleShortcuts.all + SampleShortcuts.folders + DemoShortcuts.all + DemoShortcuts.folders
        for (node in nodes.filter { it.icon.isNotEmpty() }) {
            assertTrue("${node.name}: ${node.icon} is not in the picture picker", node.icon in IconPicker.ICONS)
        }
        assertEquals(IconPicker.ICONS.size, IconPicker.ICONS.toSet().size)
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

    @Test
    fun `the running late samples print what the owner expects`() {
        fun session(id: String) = FillSession(SampleShortcuts.all.first { it.id == id }, { now }, Locale.UK)
        assertEquals("Running late, sorry! I'll be there in 10 mins (20:30).", session("sample-running-late").result())
        val onMyWay = session("sample-on-my-way")
        assertTrue(onMyWay.isEmpty)
        assertEquals("On my way, I'll be there in 10 mins.", onMyWay.result())
        val meet = session("sample-meet-you-there")
        meet.setValue(meet.variables.first { it.name == "Place" }, "the station")
        assertEquals("I'll meet you at the station at 20:30.", meet.result())
        // Times in the future are picked as "in how long", never as "15 min ago".
        for (id in listOf("sample-pickup", "sample-be-there-by", "sample-call-back", "sample-meet-you-there")) {
            val s = session(id)
            assertTrue(id, s.variables.any { it.type == io.github.selementpecher.shortcutkeyboard.model.VariableType.TIME_AHEAD })
            assertTrue(id, s.variables.none { it.type == io.github.selementpecher.shortcutkeyboard.model.VariableType.TIME })
        }
        assertEquals("I'll be there by 20:30.", session("sample-be-there-by").result())
        assertEquals("Can't talk now, I'll call you back at 20:30.", session("sample-call-back").result())
        val train = session("sample-train-delay")
        train.useDefaults()
        assertEquals("My train is delayed. It is now due in 10 mins (20:30).", train.result())
    }
}
