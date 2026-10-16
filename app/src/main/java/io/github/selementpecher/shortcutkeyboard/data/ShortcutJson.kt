package io.github.selementpecher.shortcutkeyboard.data

import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import io.github.selementpecher.shortcutkeyboard.model.VariableDef
import io.github.selementpecher.shortcutkeyboard.model.VariableType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** Reads and writes shortcuts as JSON, for the save file and for backups. */
object ShortcutJson {
    const val VERSION = 1

    fun toJson(shortcuts: List<Shortcut>): String {
        val list = JSONArray()
        shortcuts.forEach { list.put(shortcutToJson(it)) }
        return JSONObject()
            .put("version", VERSION)
            .put("shortcuts", list)
            .toString(2)
    }

    /** Accepts `{"shortcuts": [...]}` or a bare `[...]`. Throws [JSONException] if it isn't valid. */
    fun fromJson(text: String): List<Shortcut> {
        val trimmed = text.trim()
        val list = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("shortcuts")
        return (0 until list.length()).map { shortcutFromJson(list.getJSONObject(it)) }
    }

    private fun shortcutToJson(shortcut: Shortcut): JSONObject {
        val variables = JSONArray()
        shortcut.variables.forEach { variables.put(variableToJson(it)) }
        return JSONObject()
            .put("id", shortcut.id)
            .put("name", shortcut.name)
            .put("trigger", shortcut.trigger)
            .put("template", shortcut.template)
            .put("variables", variables)
    }

    private fun variableToJson(variable: VariableDef): JSONObject {
        val options = JSONArray()
        variable.options.forEach { options.put(it) }
        val json = JSONObject()
            .put("name", variable.name)
            .put("type", variable.type.id)
            .put("options", options)
            .put("remember", variable.remember)
        variable.lastValue?.let { json.put("lastValue", it) }
        return json
    }

    private fun shortcutFromJson(json: JSONObject): Shortcut {
        val variables = json.optJSONArray("variables") ?: JSONArray()
        return Shortcut(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = json.optString("name"),
            trigger = json.optString("trigger"),
            template = json.getString("template"),
            variables = (0 until variables.length()).map { variableFromJson(variables.getJSONObject(it)) },
        )
    }

    private fun variableFromJson(json: JSONObject): VariableDef {
        val options = json.optJSONArray("options") ?: JSONArray()
        return VariableDef(
            name = json.getString("name"),
            type = VariableType.fromId(json.optString("type")),
            options = (0 until options.length()).map { options.getString(it) },
            remember = json.optBoolean("remember", true),
            lastValue = if (json.has("lastValue") && !json.isNull("lastValue")) json.getString("lastValue") else null,
        )
    }
}
