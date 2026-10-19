package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.look.LookStore
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf

/** What each kind of changing word does, in plain words, with an example. Opened from Settings and from the editor. */
class CheatSheetActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var screen: UiScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this, LookStore.current(this))
        screen = ui.screen(getString(R.string.cheat_title), onBack = { finish() })
        build()
        screen.install(this)
    }

    override fun onResume() {
        super.onResume()
        if (ui.isStale()) recreate()
    }

    private fun build() = with(ui) {
        val content = screen.content
        content.add(body(getString(R.string.cheat_intro)), top = 4, bottom = 4)
        for ((index, entry) in ENTRIES.withIndex()) content.add(kindCard(index, entry), top = 16)
        content.add(plainCard(), top = 16)
        content.add(tipsCard(), top = 16)
    }

    private fun kindCard(index: Int, entry: Entry): View = with(ui) {
        val tile = palette.tiles[index % palette.tiles.size]
        val card = card()
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this@CheatSheetActivity).apply {
            text = entry.glyph
            gravity = Gravity.CENTER
            textSize = 24f
            includeFontPadding = false
            background = shape(tile.bg, 999f)
            layoutParams = lp(width = dp(52), height = dp(52))
        })
        header.addView(heading(getString(entry.name)).apply { layoutParams = lp(width = 0, weight = 1f, start = 14) })
        card.addView(header, lp())

        card.add(label(getString(R.string.cheat_label_keyboard)), top = 16)
        for (line in entry.keyboard) card.add(bullet(getString(line)), top = 6)
        card.add(label(getString(R.string.cheat_label_good_for)), top = 16)
        card.add(body(getString(entry.goodFor)), top = 4)
        card.add(label(getString(R.string.cheat_label_example)), top = 16)
        card.add(column().apply {
            background = shape(tile.bg, look.shape.cardRadiusDp / 1.5f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(text(getString(entry.example), look.type.bodySp, 600, tile.fg))
        }, top = 6)
        card
    }

    private fun plainCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.cheat_plain_name)))
        card.add(body(getString(R.string.cheat_plain_text)), top = 6)
        card
    }

    private fun tipsCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.cheat_tips_title)))
        for (tip in listOf(R.string.cheat_tip_1, R.string.cheat_tip_2, R.string.cheat_tip_3)) card.add(bullet(getString(tip)), top = 10)
        card
    }

    /** A line with a round dot in front; wraps cleanly under the dot. */
    private fun bullet(text: String): View = with(ui) {
        val line = row().apply { gravity = Gravity.TOP }
        line.addView(TextView(this@CheatSheetActivity).apply {
            this.text = "•"
            textSize = look.type.bodySp
            typeface = typefaceOf(700)
            setTextColor(palette.inkSoft)
            layoutParams = lp(width = dp(16))
        })
        line.addView(body(text).apply { layoutParams = lp(width = 0, weight = 1f) })
        line
    }

    private class Entry(
        val glyph: String,
        val name: Int,
        val keyboard: List<Int>,
        val goodFor: Int,
        val example: Int,
    )

    private companion object {
        val ENTRIES = listOf(
            Entry(
                "☰", R.string.cheat_list_name,
                listOf(R.string.cheat_list_keyboard_1, R.string.cheat_list_keyboard_2, R.string.cheat_list_keyboard_3),
                R.string.cheat_list_good_for, R.string.cheat_list_example,
            ),
            Entry(
                "🕒", R.string.cheat_time_name,
                listOf(R.string.cheat_time_keyboard_1, R.string.cheat_time_keyboard_2, R.string.cheat_time_keyboard_3),
                R.string.cheat_time_good_for, R.string.cheat_time_example,
            ),
            Entry(
                "⏩", R.string.cheat_ahead_name,
                listOf(
                    R.string.cheat_ahead_keyboard_1, R.string.cheat_ahead_keyboard_2,
                    R.string.cheat_ahead_keyboard_3, R.string.cheat_ahead_keyboard_4,
                ),
                R.string.cheat_ahead_good_for, R.string.cheat_ahead_example,
            ),
            Entry(
                "⏪", R.string.cheat_ago_name,
                listOf(R.string.cheat_ago_keyboard_1, R.string.cheat_ago_keyboard_2),
                R.string.cheat_ago_good_for, R.string.cheat_ago_example,
            ),
            Entry(
                "📅", R.string.cheat_date_name,
                listOf(R.string.cheat_date_keyboard_1, R.string.cheat_date_keyboard_2),
                R.string.cheat_date_good_for, R.string.cheat_date_example,
            ),
            Entry(
                "🔢", R.string.cheat_number_name,
                listOf(R.string.cheat_number_keyboard_1, R.string.cheat_number_keyboard_2, R.string.cheat_number_keyboard_3),
                R.string.cheat_number_good_for, R.string.cheat_number_example,
            ),
        )
    }
}
