package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R

/** A grid of pictures to choose from for a shortcut or folder, in groups (people, health, food...). */
internal object IconPicker {

    /** The pictures on offer, in groups with a heading each. Every picture the examples use is here. */
    val GROUPS: List<Pair<Int, List<String>>> = listOf(
        R.string.icon_group_people to listOf(
            "👶", "🧒", "👧", "👦", "👩", "👨", "👵", "👴", "👪", "🙋", "👋", "👍",
            "🙏", "😀", "😊", "😢", "😴", "🥳",
        ),
        R.string.icon_group_health to listOf("💊", "🌡️", "🤒", "🤧", "😷", "🩹", "🩺", "🧪", "💉", "🦷", "🏥", "🧼"),
        R.string.icon_group_food to listOf("🍎", "🍌", "🍞", "🥪", "🍽️", "🍕", "🍝", "🍰", "☕", "🥛", "🍼", "🧃"),
        R.string.icon_group_home to listOf("🏠", "🛏️", "🛁", "🚿", "🧺", "🧹", "🔑", "🛒", "🛍️", "📦", "💡", "🔧"),
        R.string.icon_group_time to listOf("⏰", "🕒", "⏳", "📅", "🗓️", "🎂", "🔔", "☀️", "🌙", "🌧️", "❄️", "☂️"),
        R.string.icon_group_travel to listOf("🚶", "🏃", "🚗", "🚕", "🚌", "🚆", "🚲", "✈️", "⛽", "📍", "🏫", "🏢"),
        R.string.icon_group_work to listOf("🎒", "📚", "✏️", "📝", "💼", "📋", "💻", "📞", "📧", "💬", "📷", "💰"),
        R.string.icon_group_nature to listOf("🐶", "🐱", "🐰", "🐠", "🐦", "🐴", "🌱", "🌸", "🌳", "🌻", "🍂", "🌈"),
        R.string.icon_group_fun to listOf("⚽", "🏀", "🎾", "🏊", "🚴", "🎵", "🎮", "🎨", "🎉", "🎁", "📺", "🎬"),
        R.string.icon_group_signs to listOf(
            "❤️", "💙", "💚", "⭐", "✨", "🔥", "✅", "❌", "⚠️", "❗", "❓", "💯",
            "🔢", "⏩", "⏪", "⏭️", "🔄", "➕",
        ),
    )

    /** Every picture on offer, in order. */
    val ICONS: List<String> = GROUPS.flatMap { it.second }

    private const val COLUMNS = 6

    /**
     * Shows the picker. [current] is the picture now (or empty). [onPicked] gets the chosen emoji, or an
     * empty string for "No picture".
     */
    fun show(activity: Activity, ui: Ui, current: String, onPicked: (String) -> Unit) {
        val panel = ui.sheet(activity, activity.getString(R.string.icon_picker_title))
        with(ui) {
            GROUPS.forEachIndexed { index, (heading, emojis) ->
                panel.body.add(label(activity.getString(heading)), top = if (index == 0) 4 else 20, bottom = 8)
                val cells = emojis.map { emoji -> cell(activity, ui, emoji, emoji == current) { panel.dismiss(); onPicked(emoji) } }
                panel.body.addView(grid(COLUMNS, cells, gapDp = 6))
            }
            panel.body.add(
                secondaryButton(activity.getString(R.string.icon_picker_none)) { panel.dismiss(); onPicked("") },
                top = 20,
            )
        }
        panel.show()
    }

    private fun cell(activity: Activity, ui: Ui, emoji: String, selected: Boolean, onTap: () -> Unit): View =
        TextView(activity).apply {
            text = emoji
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            minimumHeight = ui.dp(52)
            val bg = if (selected) ui.shape(if (ui.look.ruled) ui.blend(ui.palette.surfaceAlt, ui.palette.accent, 0.19f) else ui.withAlpha(ui.palette.accent, 0x30), 14f, ui.palette.accent, 2f)
            else ui.shape(ui.palette.surfaceAlt, 14f)
            background = ui.rippled(bg, 14f)
            isClickable = true
            isFocusable = true
            contentDescription = emoji
            setOnClickListener { onTap() }
        }
}
