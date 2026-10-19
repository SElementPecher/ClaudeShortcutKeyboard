package io.github.selementpecher.shortcutkeyboard.data

import io.github.selementpecher.shortcutkeyboard.model.Folder
import io.github.selementpecher.shortcutkeyboard.model.Library
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Plain JVM tests for the save file and backup format.
 *
 * These run against two org.json versions (Android's own one and the Maven one used by Gradle unit
 * tests), so they only check behaviour both agree on: no exact spacing or key order.
 */
class ShortcutJsonTest {

    private val full = Shortcut(
        id = "medicine",
        name = "Medicine given",
        template = "{child}, {medicine}, {dose}, {time} next dose not before {time+4h}",
        variables = listOf(
            VariableDef("child", VariableType.CHOICE, options = listOf("Sam", "Alex"), lastValue = "Sam"),
            VariableDef("medicine", VariableType.CHOICE, options = listOf("Calpol", "Nurofen"), remember = false),
            VariableDef("dose", VariableType.NUMBER, lastValue = "5"),
            VariableDef("note", VariableType.TEXT),
            VariableDef("time", VariableType.TIME),
            VariableDef("date", VariableType.DATE),
        ),
    )

    private val tricky = Shortcut(
        id = "tricky",
        name = "Quotes \"and\" slashes \\ / and émoji 🙂",
        template = "Line one\nLine two\t{{literal}} {name|h:mm a} £5",
        variables = listOf(VariableDef("name", VariableType.CHOICE, options = listOf("O'Brien", "", "a,b"))),
    )

    private val empty = Shortcut(id = "empty", name = "", template = "")

    // ---- Round trip ----

    @Test
    fun `round trip keeps every field`() {
        val list = listOf(full, tricky, empty)
        assertEquals(list, ShortcutJson.fromJson(ShortcutJson.toJson(list)))
    }

    @Test
    fun `round trip of an empty list`() {
        assertEquals(emptyList<Shortcut>(), ShortcutJson.fromJson(ShortcutJson.toJson(emptyList())))
    }

    @Test
    fun `round trip of the sample shortcuts`() {
        assertEquals(SampleShortcuts.all, ShortcutJson.fromJson(ShortcutJson.toJson(SampleShortcuts.all)))
    }

    @Test
    fun `a second round trip gives the same text`() {
        val once = ShortcutJson.toJson(listOf(full, tricky))
        val twice = ShortcutJson.toJson(ShortcutJson.fromJson(once))
        assertEquals(once, twice)
    }

    @Test
    fun `output is an object with a version and the shortcuts`() {
        val json = JSONObject(ShortcutJson.toJson(listOf(full)))
        assertEquals(ShortcutJson.VERSION, json.getInt("version"))
        assertEquals(2, json.getInt("version"))
        val shortcuts = json.getJSONArray("shortcuts")
        assertEquals(1, shortcuts.length())
        val first = shortcuts.getJSONObject(0)
        assertEquals("medicine", first.getString("id"))
        val child = first.getJSONArray("variables").getJSONObject(0)
        assertEquals("choice", child.getString("type"))
        assertEquals("Sam", child.getString("lastValue"))
        assertTrue(child.getBoolean("remember"))
    }

    @Test
    fun `a missing last value is left out of the file`() {
        val json = JSONObject(ShortcutJson.toJson(listOf(full)))
        val note = json.getJSONArray("shortcuts").getJSONObject(0).getJSONArray("variables").getJSONObject(3)
        assertEquals("note", note.getString("name"))
        assertFalse(note.has("lastValue"))
    }

    @Test
    fun `type ids are written in lower case`() {
        val json = JSONObject(ShortcutJson.toJson(listOf(full)))
        val variables = json.getJSONArray("shortcuts").getJSONObject(0).getJSONArray("variables")
        val types = (0 until variables.length()).map { variables.getJSONObject(it).getString("type") }
        assertEquals(listOf("choice", "choice", "number", "text", "time", "date"), types)
    }

    // ---- Reading ----

    @Test
    fun `reads a bare list`() {
        val list = ShortcutJson.fromJson("""[{"id":"a","name":"A","template":"Hi {child}"}]""")
        assertEquals(listOf(Shortcut(id = "a", name = "A", template = "Hi {child}")), list)
    }

    @Test
    fun `reads with spaces and line breaks around it`() {
        val list = ShortcutJson.fromJson("  \n [ {\"id\":\"a\",\"name\":\"A\",\"template\":\"x\"} ] \n ")
        assertEquals(1, list.size)
        val wrapped = ShortcutJson.fromJson("\n {\"shortcuts\": [{\"id\":\"b\",\"template\":\"y\"}]} ")
        assertEquals("b", wrapped.single().id)
    }

    @Test
    fun `reads a file without a version`() {
        val list = ShortcutJson.fromJson("""{"shortcuts":[{"id":"a","name":"A","template":"x"}]}""")
        assertEquals("a", list.single().id)
    }

    @Test
    fun `missing fields get sensible defaults`() {
        val list = ShortcutJson.fromJson(
            """[{"id":"a","template":"{child}","variables":[{"name":"child"}]}]""",
        )
        val shortcut = list.single()
        assertEquals("", shortcut.name)
        val child = shortcut.variables.single()
        assertEquals(VariableDef("child", VariableType.CHOICE, emptyList(), remember = true, lastValue = null), child)
    }

    @Test
    fun `a shortcut without variables has none`() {
        assertTrue(ShortcutJson.fromJson("""[{"id":"a","template":"x"}]""").single().variables.isEmpty())
    }

    @Test
    fun `a null last value reads as no last value`() {
        val list = ShortcutJson.fromJson(
            """[{"id":"a","template":"{child}","variables":[{"name":"child","lastValue":null}]}]""",
        )
        assertNull(list.single().variables.single().lastValue)
    }

    @Test
    fun `an unknown type reads as a list`() {
        val list = ShortcutJson.fromJson(
            """[{"id":"a","template":"{x}","variables":[{"name":"x","type":"colour"}]}]""",
        )
        assertEquals(VariableType.CHOICE, list.single().variables.single().type)
    }

    @Test
    fun `remember false is kept`() {
        val list = ShortcutJson.fromJson(
            """[{"id":"a","template":"{x}","variables":[{"name":"x","remember":false}]}]""",
        )
        assertFalse(list.single().variables.single().remember)
    }

    // ---- Ids ----

    @Test
    fun `a given id is kept`() {
        assertEquals("my-id", ShortcutJson.fromJson("""[{"id":"my-id","template":"x"}]""").single().id)
    }

    @Test
    fun `a missing id gets a new one`() {
        val list = ShortcutJson.fromJson("""[{"name":"A","template":"x"},{"name":"B","template":"y"}]""")
        assertEquals(2, list.size)
        assertTrue(list[0].id.isNotBlank())
        assertTrue(list[1].id.isNotBlank())
        assertNotEquals(list[0].id, list[1].id)
    }

    @Test
    fun `a blank id gets a new one`() {
        val list = ShortcutJson.fromJson("""[{"id":"","template":"x"},{"id":"   ","template":"y"}]""")
        assertTrue(list[0].id.isNotBlank())
        assertTrue(list[1].id.isNotBlank())
        assertNotEquals(list[0].id, list[1].id)
    }

    @Test
    fun `null text fields read as empty and a null id gets a new one`() {
        val shortcut = ShortcutJson.fromJson("""[{"id":null,"name":null,"parentId":null,"template":"x"}]""").single()
        assertTrue(shortcut.id.isNotBlank())
        assertNotEquals("null", shortcut.id)
        assertEquals("", shortcut.name)
    }

    // ---- Folders ----

    private val library = Library(
        folders = listOf(Folder("kids", "Kids", order = 0), Folder("meds", "Meds", parentId = "kids", order = 2)),
        shortcuts = listOf(full.copy(parentId = "meds", order = 3), empty),
    )

    @Test
    fun `a library with folders survives a round trip`() {
        assertEquals(library, ShortcutJson.libraryFromJson(ShortcutJson.toJson(library)))
    }

    @Test
    fun `tile colours and pictures survive a round trip and old files default to automatic`() {
        val lib = Library(
            folders = listOf(Folder("f", "F", icon = "💊", color = 7)),
            shortcuts = listOf(empty.copy(icon = "⏰", color = 15)),
        )
        val back = ShortcutJson.libraryFromJson(ShortcutJson.toJson(lib))
        assertEquals(7, back.folders.single().color)
        assertEquals(15, back.shortcuts.single().color)
        assertEquals(-1, ShortcutJson.libraryFromJson("""{"shortcuts":[{"id":"a","template":"x"}]}""").shortcuts.single().color)
    }

    @Test
    fun `an old file without folders loads at the top level`() {
        val old = """{"version":1,"shortcuts":[{"id":"a","name":"A","trigger":"/a","template":"Hi"}]}"""
        val loaded = ShortcutJson.libraryFromJson(old)
        assertTrue(loaded.folders.isEmpty())
        assertEquals(null, loaded.shortcuts.single().parentId)
    }

    @Test
    fun `a tile pointing at a missing folder goes to the top level`() {
        val text = """{"folders":[],"shortcuts":[{"id":"a","name":"A","template":"Hi","parentId":"gone"}]}"""
        assertEquals(null, ShortcutJson.libraryFromJson(text).shortcuts.single().parentId)
    }

    @Test
    fun `folders that point at each other are untangled`() {
        val text = """{"folders":[{"id":"a","name":"A","parentId":"b"},{"id":"b","name":"B","parentId":"a"}],"shortcuts":[]}"""
        val loaded = ShortcutJson.libraryFromJson(text)
        assertTrue(loaded.folders.any { it.parentId == null })
        for (folder in loaded.folders) assertTrue(loaded.path(folder.id).isNotEmpty())
    }

    @Test
    fun `a bare list of shortcuts still loads as a library`() {
        assertEquals(1, ShortcutJson.libraryFromJson("""[{"id":"a","template":"x"}]""").shortcuts.size)
    }

    // ---- Bad input ----

    @Test
    fun `bad input throws`() {
        val bad = listOf(
            "",
            "   ",
            "not json",
            "{}",
            "{\"shortcuts\": 5}",
            "{\"shortcuts\": [",
            "[1, 2]",
            "[{}]",
            "[{\"id\":\"a\",\"name\":\"no message\"}]",
            "[{\"id\":\"a\",\"template\":\"{x}\",\"variables\":[{}]}]",
            "[{\"id\":\"a\",\"template\":\"x\",\"variables\":[5]}]",
        )
        for (text in bad) {
            try {
                ShortcutJson.fromJson(text)
                fail("Expected an error for: $text")
            } catch (expected: JSONException) {
                // Good: the app shows a friendly message for this.
            }
        }
    }

    @Test
    fun `bad input throws JSONException`() {
        assertThrows(JSONException::class.java) { ShortcutJson.fromJson("not json") }
    }
}
