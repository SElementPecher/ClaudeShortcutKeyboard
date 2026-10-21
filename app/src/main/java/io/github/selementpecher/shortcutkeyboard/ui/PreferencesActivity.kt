package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.LookStore
import io.github.selementpecher.shortcutkeyboard.look.Looks
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf

/**
 * Preferences: the Look and the text size. Opened from Settings. Picking one applies at once (the screen redraws itself).
 */
class PreferencesActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var screen: UiScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this, LookStore.current(this))
        screen = ui.screen(getString(R.string.preferences_title), onBack = { finish() })
        build()
        screen.install(this)
        restoreScroll(savedInstanceState?.getInt(STATE_SCROLL) ?: 0)
    }

    override fun onResume() {
        super.onResume()
        if (ui.isStale()) recreate()
    }

    /** Picking a Look or a size rebuilds the page; this keeps it where it was instead of jumping back to the top. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SCROLL, screen.scroll.scrollY)
    }

    /** Scrolls back to [y] as soon as the rebuilt page is tall enough (the page can take a few passes to settle). */
    private fun restoreScroll(y: Int) {
        if (y <= 0) return
        val scroll = screen.scroll
        var passes = 0
        scroll.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val room = (scroll.getChildAt(0)?.height ?: 0) - scroll.height
                passes++
                if (room >= y || passes > MAX_RESTORE_PASSES) {
                    scroll.viewTreeObserver.removeOnPreDrawListener(this)
                    scroll.scrollTo(0, minOf(y, maxOf(room, 0)))
                    return room < y && false
                }
                return true
            }
        })
    }

    private companion object {
        const val STATE_SCROLL = "scroll"
        const val MAX_RESTORE_PASSES = 30
    }

    private fun build() = with(ui) {
        val content = screen.content
        content.add(textSizeCard(), top = 8)
        content.add(lookCard(), top = 16)
    }

    private fun lookCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.settings_look_title)))
        card.add(small(getString(R.string.settings_look_help)), top = 4)
        for (option in Looks.all) card.add(lookOption(option), top = 14)
        card
    }

    /** One selectable Look: a live mini preview, its name and tagline, a ring when chosen. */
    private fun lookOption(option: Look): View = with(ui) {
        val chosen = option.id == look.id
        val box = column().apply {
            val r = 18f
            background = rippled(
                shape(palette.surfaceAlt.let { if (chosen) blend(palette.surface, palette.accent, 0.06f) else palette.surface }, r, if (chosen) palette.accent else palette.outline, if (chosen) 3f else 1.5f),
                r,
            )
            setPadding(dp(12), dp(12), dp(12), dp(14))
            isClickable = true
            isFocusable = true
            contentDescription = getString(if (chosen) R.string.look_chosen else R.string.look_choose, option.displayName)
            setOnClickListener { chooseLook(option) }
        }
        val labels = listOf(
            getString(R.string.look_preview_shortcut_1),
            getString(R.string.look_preview_shortcut_2),
            getString(R.string.look_preview_folder),
        )
        box.addView(LookPreview(this@PreferencesActivity, option, labels).apply { layoutParams = lp() })
        val line = row()
        val names = column().apply { layoutParams = lp(width = 0, weight = 1f) }
        names.addView(text(option.displayName, look.type.headingSp, look.type.headingWeight, palette.ink))
        names.add(small(option.tagline), top = 2)
        line.addView(names)
        if (chosen) line.addView(checkBadge(32))
        box.add(line, top = 12)
        box
    }

    /** A round accent badge with a tick: marks the chosen option. */
    private fun checkBadge(sizeDp: Int): View = with(ui) {
        ImageView(this@PreferencesActivity).apply {
            setImageDrawable(icon(Icons.Kind.CHECK, sizeDp * 3 / 4, palette.onAccent))
            scaleType = ImageView.ScaleType.CENTER
            background = shape(palette.accent, 999f)
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
    }

    /** "Text size": two big choices, each showing a sample line at its own size. */
    private fun textSizeCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.settings_text_title)))
        card.add(small(getString(R.string.settings_text_help)), top = 4)
        card.add(textChoice(large = false), top = 16)
        card.add(textChoice(large = true), top = 10)
        card
    }

    private fun textChoice(large: Boolean): View = with(ui) {
        val chosen = LookStore.isLargeText(this@PreferencesActivity) == large
        val name = getString(if (large) R.string.text_size_large else R.string.text_size_normal)
        // The Ui's sizes already include the current choice; take it out, then apply this option's.
        val base = look.type.bodySp / LookStore.textScale(this@PreferencesActivity)
        val sampleSp = base * (if (large) LookStore.LARGE_TEXT_SCALE else 1f)
        val box = column().apply {
            val r = 18f
            background = rippled(
                shape(if (chosen) blend(palette.surface, palette.accent, 0.06f) else palette.surface, r, if (chosen) palette.accent else buttonOutline(), if (chosen) 3f else 1.5f),
                r,
            )
            setPadding(dp(16), dp(14), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            contentDescription = getString(if (chosen) R.string.text_size_chosen else R.string.text_size_choose, name)
            setOnClickListener { chooseTextSize(large) }
            Motion.pressable(this, 0.98f)
        }
        val line = row()
        line.addView(text(name, look.type.headingSp, look.type.headingWeight, palette.ink, Ui.HEADING_LINE_SPACING).apply { layoutParams = lp(width = 0, weight = 1f) })
        if (chosen) line.addView(checkBadge(28))
        box.addView(line)
        box.add(text(getString(R.string.text_size_sample), sampleSp, 400, palette.ink), top = 8)
        box
    }

    private fun Ui.buttonOutline(): Int = blend(palette.outline, palette.inkSoft, 0.55f)

    private fun chooseTextSize(large: Boolean) {
        if (LookStore.isLargeText(this) == large) return
        LookStore.setLargeText(this, large)
        recreate()
    }

    private fun chooseLook(option: Look) {
        if (option.id == ui.look.id) return
        LookStore.set(this, option)
        recreate()
    }
}
