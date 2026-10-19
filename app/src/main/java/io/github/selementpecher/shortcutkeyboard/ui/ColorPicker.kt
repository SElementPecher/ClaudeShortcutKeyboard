package io.github.selementpecher.shortcutkeyboard.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.look.SWATCH_COUNT

/**
 * A tile colour chooser: 16 colours in a 4 by 4 grid, each shown as the tile would look, plus
 * "Automatic colour" (the app picks one). The chosen colour has a ring and a tick.
 */
internal object ColorPicker {
    private const val COLUMNS = 4

    /**
     * @param current 0–15 for a picked colour, −1 for automatic.
     * @param onPicked called with the new choice; the picker redraws itself.
     */
    fun view(ui: Ui, current: Int, onPicked: (Int) -> Unit): View {
        val holder = ui.column()
        var chosen = current

        fun render() {
            holder.removeAllViews()
            val names = ui.context.resources.getStringArray(R.array.color_names)
            val cells = (0 until SWATCH_COUNT).map { index -> swatch(ui, index, names.getOrElse(index) { "" }, index == chosen) {
                chosen = index
                onPicked(index)
                render()
            } }
            holder.addView(ui.grid(COLUMNS, cells, gapDp = 10), ui.lp())
            val auto = ui.textButton(ui.context.getString(if (chosen < 0) R.string.color_auto_chosen else R.string.color_auto)) {
                chosen = -1
                onPicked(-1)
                render()
            }
            with(ui) { holder.add(auto, top = 6) }
        }
        render()
        return holder
    }

    private fun swatch(ui: Ui, index: Int, name: String, selected: Boolean, onClick: () -> Unit): View {
        val colors = ui.palette.swatches[index]
        val radius = 14f
        val ring = ui.palette.ink
        val frame = FrameLayout(ui.context).apply {
            minimumHeight = ui.dp(56)
            background = ui.rippled(
                ui.shape(colors.bg, radius, if (selected) ring else ui.palette.outline, if (selected) 3f else 1f),
                radius,
            )
            isClickable = true
            isFocusable = true
            contentDescription = if (selected) ui.context.getString(R.string.color_chosen, name) else name
            setOnClickListener { onClick() }
        }
        frame.addView(
            TextView(ui.context).apply {
                text = if (selected) "✓" else "Aa"
                gravity = Gravity.CENTER
                setTextColor(colors.fg)
                textSize = if (selected) 22f else 16f
                typeface = io.github.selementpecher.shortcutkeyboard.look.typefaceOf(700)
                includeFontPadding = false
            },
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, ui.dp(56)),
        )
        return frame
    }
}
