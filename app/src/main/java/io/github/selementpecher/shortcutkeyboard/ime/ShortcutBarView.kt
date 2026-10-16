package io.github.selementpecher.shortcutkeyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.model.Shortcut

/** The strip above the keys: one chip per shortcut, and a gear button that opens the app. */
@SuppressLint("ViewConstructor")
internal class ShortcutBarView(
    context: Context,
    private val listener: Listener,
) : LinearLayout(context) {

    interface Listener {
        fun onShortcutChosen(shortcut: Shortcut)
        fun onOpenApp()
    }

    private val chips = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val scroller = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(chips, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }
    private val chipHeight = context.px(R.dimen.kb_bar_chip_height)
    private val gap = context.px(R.dimen.kb_panel_gap)
    private var shown: List<Shortcut>? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        chips.setPadding(gap, 0, gap, 0)
        addView(scroller, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))

        val settings = ImageView(context).apply {
            setImageDrawable(context.tintedDrawable(R.drawable.kb_ic_settings, context.getColor(R.color.kb_key_text)))
            scaleType = ImageView.ScaleType.CENTER
            setBackgroundResource(R.drawable.kb_icon_button_bg)
            contentDescription = context.getString(R.string.kb_open_app)
            onTap { listener.onOpenApp() }
        }
        addView(settings, LayoutParams(context.px(R.dimen.kb_bar_height), LayoutParams.MATCH_PARENT).apply { marginEnd = gap / 2 })
    }

    fun setShortcuts(shortcuts: List<Shortcut>) {
        if (shortcuts == shown) return
        shown = shortcuts
        chips.removeAllViews()
        if (shortcuts.isEmpty()) {
            chips.addView(chip(context.getString(R.string.kb_add_shortcut)) { listener.onOpenApp() }, chipParams())
        } else {
            for (shortcut in shortcuts) {
                val name = shortcut.name.ifBlank { shortcut.trigger.ifBlank { context.getString(R.string.kb_untitled) } }
                chips.addView(chip(name) { listener.onShortcutChosen(shortcut) }, chipParams())
            }
        }
        scroller.scrollTo(0, 0)
    }

    private fun chipParams() = LayoutParams(LayoutParams.WRAP_CONTENT, chipHeight).apply {
        marginStart = gap / 2
        marginEnd = gap / 2
    }

    private fun chip(label: String, action: () -> Unit): TextView = TextView(context).apply {
        text = label
        setTextColor(context.getColor(R.color.kb_chip_text))
        setTextSizeRes(R.dimen.kb_bar_text_size)
        setBackgroundResource(R.drawable.kb_chip_bg)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = context.px(R.dimen.kb_bar_chip_max_width)
        val padding = context.px(R.dimen.kb_panel_padding) * 2
        setPadding(padding, 0, padding, 0)
        onTap(action)
    }
}
