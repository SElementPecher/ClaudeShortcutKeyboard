package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.look.LookStore
import io.github.selementpecher.shortcutkeyboard.look.TileColors

/** Remembers whether the owner has seen (or skipped) the short tour. */
internal object Onboarding {
    private const val PREFS = "onboarding"
    private const val KEY_DONE = "done"

    fun isDone(context: Context): Boolean = try {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
    } catch (e: RuntimeException) {
        true
    }

    private const val KEY_EXAMPLES_ASKED = "examples_asked"

    /** True once the home screen has offered the example quiKeys (taken or turned down). */
    fun examplesAsked(context: Context): Boolean = try {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_EXAMPLES_ASKED, false)
    } catch (e: RuntimeException) {
        true
    }

    fun setExamplesAsked(context: Context) {
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_EXAMPLES_ASKED, true).apply()
        } catch (e: RuntimeException) {
            // Being asked once more is harmless.
        }
    }

    fun setDone(context: Context) {
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
        } catch (e: RuntimeException) {
            // Seeing the tour once more is harmless.
        }
    }
}

/**
 * A short tour of four screens, one idea each. It can be skipped on every screen and is opened again from
 * Settings. Shown once, the first time the app opens.
 */
class OnboardingActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var screen: UiScreen
    private lateinit var skip: View
    private var page = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this, LookStore.current(this))
        page = savedInstanceState?.getInt(STATE_PAGE) ?: 0
        skip = ui.textButton(getString(R.string.tour_skip)) { finishTour() }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(48))
        }
        screen = ui.screen("", trailing = listOf(skip), withBottomBar = true)
        screen.install(this)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (ui.isStale()) {
            recreate()
            return
        }
        // Coming back from the phone's keyboard settings: show the tick.
        if (page == LAST) render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, page)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page > 0) {
            page--
            render()
        } else {
            finishTour()
        }
    }

    private fun finishTour() {
        Onboarding.setDone(this)
        finish()
    }

    private fun render() {
        val content = screen.content
        content.removeAllViews()
        with(ui) {
            val view = when (page) {
                0 -> pageOne()
                1 -> pageTwo()
                2 -> pageThree()
                3 -> pageExamples()
                else -> pageFour()
            }
            content.addView(view, lp())
            Motion.pop(view)
            screen.bottomBar.removeAllViews()
            screen.bottomBar.add(dots(), bottom = 14)
            val last = page == LAST
            screen.bottomBar.addView(primaryButton(getString(if (last) R.string.tour_done else R.string.tour_next)) {
                if (last) finishTour() else { page++; render(); screen.scroll.scrollTo(0, 0) }
            })
        }
        skip.visibility = if (page == LAST) View.INVISIBLE else View.VISIBLE
    }

    // ---------------------------------------------------------------- pages

    private fun intro(glyph: String, titleRes: Int, textRes: Int): LinearLayout = with(ui) {
        val box = column()
        box.add(
            TextView(this@OnboardingActivity).apply {
                text = glyph
                gravity = Gravity.CENTER
                textSize = 34f
                includeFontPadding = false
                background = shape(withAlpha(palette.accent, 0x26), 999f)
                layoutParams = lp(width = dp(76), height = dp(76))
            },
            top = 8,
        )
        box.add(title(getString(titleRes)), top = 20)
        box.add(body(getString(textRes)), top = 12)
        box
    }

    private fun pageOne(): View = with(ui) {
        val box = intro("👋", R.string.tour_1_title, R.string.tour_1_text)
        val parts = listOf(
            ExampleParts.Part(getString(R.string.tour_1_fixed_start), ExampleParts.FIXED),
            ExampleParts.Part("10 mins (18:40)", 0),
            ExampleParts.Part(".", ExampleParts.FIXED),
        )
        box.add(exampleCard(parts), top = 24)
        box
    }

    private fun pageTwo(): View = with(ui) {
        val box = intro("🧩", R.string.tour_2_title, R.string.tour_2_text)
        val parts = listOf(
            ExampleParts.Part("18:13", 0), ExampleParts.Part(" ", ExampleParts.FIXED),
            ExampleParts.Part("Sam", 1), ExampleParts.Part(", ", ExampleParts.FIXED),
            ExampleParts.Part("Calpol", 2), ExampleParts.Part(", ", ExampleParts.FIXED),
            ExampleParts.Part("5ml", 3), ExampleParts.Part(" next dose not before ", ExampleParts.FIXED),
            ExampleParts.Part("22:13", 0),
        )
        box.add(exampleCard(parts), top = 24)
        box.add(small(getString(R.string.tour_2_note)), top = 12)
        box
    }

    private fun pageThree(): View = with(ui) {
        val box = intro("📁", R.string.tour_3_title, R.string.tour_3_text)
        val folder = tile(
            name = getString(R.string.tour_3_folder), icon = "🧒", kind = TileKind.FOLDER, subtitle = getString(R.string.tour_3_folder_items),
        )
        val shortcut = tile(name = getString(R.string.tour_3_shortcut), icon = "⏰", kind = TileKind.SHORTCUT)
        box.add(grid(2, listOf(folder, shortcut)), top = 24)
        box
    }

    /** Offers the example quiKies; a tick shows once they are added. */
    private fun pageExamples(): View = with(ui) {
        val box = intro("✨", R.string.tour_5_title, R.string.tour_5_text)
        val library = io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository.library(this@OnboardingActivity)
        val added = io.github.selementpecher.shortcutkeyboard.data.SampleShortcuts.all.any { library.shortcut(it.id) != null }
        if (added) {
            box.add(
                row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = shape(blend(palette.bg, palette.success, 0.12f), 16f)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    addView(
                        TextView(this@OnboardingActivity).apply {
                            text = "✓"
                            gravity = Gravity.CENTER
                            setTextColor(readableOn(palette.success))
                            textSize = 16f
                            includeFontPadding = false
                            background = shape(palette.success, 999f)
                            layoutParams = lp(width = dp(28), height = dp(28))
                        },
                    )
                    addView(text(getString(R.string.tour_5_added), look.type.bodySp, 700, palette.ink).apply { layoutParams = lp(width = 0, weight = 1f, start = 12) })
                },
                top = 24,
            )
        } else {
            box.add(
                primaryButton(getString(R.string.examples_button)) {
                    try {
                        io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository.addExamples(this@OnboardingActivity)
                        Onboarding.setExamplesAsked(this@OnboardingActivity)
                    } catch (e: Exception) {
                        screen.snack(getString(R.string.edit_save_failed), error = true)
                    }
                    render()
                },
                top = 24,
            )
            box.add(small(getString(R.string.tour_5_later)), top = 12)
        }
        box
    }

    private fun pageFour(): View = with(ui) {
        val box = intro("⌨️", R.string.tour_4_title, R.string.tour_4_text)
        val on = KeyboardSetup.isEnabled(this@OnboardingActivity)
        if (on) {
            box.add(
                row().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    background = shape(blend(palette.bg, palette.success, 0.12f), 16f)
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    addView(
                        TextView(this@OnboardingActivity).apply {
                            text = "✓"
                            gravity = Gravity.CENTER
                            setTextColor(readableOn(palette.success))
                            textSize = 16f
                            includeFontPadding = false
                            background = shape(palette.success, 999f)
                            layoutParams = lp(width = dp(28), height = dp(28))
                        },
                    )
                    addView(text(getString(R.string.tour_4_on), look.type.bodySp, 700, palette.ink).apply { layoutParams = lp(width = 0, weight = 1f, start = 12) })
                },
                top = 24,
            )
        } else {
            box.add(primaryButton(getString(R.string.tour_4_turn_on)) { openKeyboardSettings() }, top = 24)
        }
        box.add(body(getString(R.string.tour_4_use)), top = 20)
        box
    }

    private fun openKeyboardSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            screen.snack(getString(R.string.setup_no_settings), error = true)
        }
    }

    // ---------------------------------------------------------------- pieces

    /** An example message with the changing words highlighted, like the keyboard shows it. */
    private fun exampleCard(parts: List<ExampleParts.Part>): View = with(ui) {
        val card = card(20)
        val shown = ExampleText.build(
            parts = parts,
            colors = palette.tiles,
            neutral = TileColors(palette.surfaceAlt, palette.ink),
            paddingPx = dpf(6f),
            radiusPx = dpf(8f),
            verticalPx = dpf(2f),
        )
        card.addView(text(shown, look.type.bodySp + 1, 500, palette.ink, 1.45f))
        card
    }

    /** Small dots: the page you are on is a longer pill in the accent colour. */
    private fun dots(): View = with(ui) {
        val line = row().apply { gravity = Gravity.CENTER }
        line.contentDescription = getString(R.string.tour_page, page + 1, LAST + 1)
        for (i in 0..LAST) {
            val current = i == page
            line.addView(
                View(this@OnboardingActivity).apply {
                    background = shape(if (current) palette.accent else palette.outline, 999f)
                    layoutParams = lp(width = dp(if (current) 22 else 8), height = dp(8), start = if (i == 0) 0 else 6)
                },
            )
        }
        line
    }

    companion object {
        private const val STATE_PAGE = "page"
        private const val LAST = 4

        fun open(context: Context) {
            context.startActivity(Intent(context, OnboardingActivity::class.java))
        }
    }
}
