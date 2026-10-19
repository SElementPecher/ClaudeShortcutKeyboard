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
            holder.addView(content(ui, chosen) { picked ->
                chosen = picked
                onPicked(picked)
                render()
            }, ui.lp())
        }
        render()
        return holder
    }

    /**
     * One quiet row: a dot in the current colour, "Colour", its name and a chevron. Tapping it opens the
     * 16 colours underneath (and closes them again). The owner decides whether to look at it at all.
     */
    fun collapsible(ui: Ui, current: Int, onPicked: (Int) -> Unit): View {
        val holder = ui.column()
        var chosen = current
        var open = false
        val names = ui.context.resources.getStringArray(R.array.color_names)

        fun render() {
            holder.removeAllViews()
            val valueName = names.getOrNull(chosen) ?: ui.context.getString(R.string.color_automatic)
            val radius = 14f
            val header = ui.row().apply {
                minimumHeight = ui.dp(56)
                setPadding(ui.dp(14), ui.dp(8), ui.dp(12), ui.dp(8))
                background = ui.rippled(ui.shape(ui.palette.surface, radius, ui.palette.outline, 1.5f), radius)
                isClickable = true
                isFocusable = true
                contentDescription = ui.context.getString(R.string.color_row_description, valueName)
                setOnClickListener { open = !open; render() }
            }
            val dotColors = ui.palette.swatches.getOrNull(chosen)
            header.addView(
                View(ui.context).apply {
                    background = if (dotColors != null) ui.shape(dotColors.bg, 999f, ui.palette.outline, 1.5f)
                    else ui.shape(android.graphics.Color.TRANSPARENT, 999f, ui.palette.inkSoft, 2f, dashed = true)
                },
                LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)),
            )
            header.addView(ui.body(ui.context.getString(R.string.color_label)).apply {
                typeface = io.github.selementpecher.shortcutkeyboard.look.typefaceOf(600)
                layoutParams = ui.lp(width = 0, weight = 1f, start = 12)
            })
            header.addView(ui.small(valueName).apply { layoutParams = ui.lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, end = 6) })
            header.addView(
                android.widget.ImageView(ui.context).apply {
                    setImageDrawable(io.github.selementpecher.shortcutkeyboard.look.Icons.drawable(
                        io.github.selementpecher.shortcutkeyboard.look.Icons.Kind.FORWARD, ui.palette.inkSoft, ui.dp(20)))
                    rotation = if (open) 270f else 90f
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)),
            )
            holder.addView(header, ui.lp())
            if (open) {
                holder.addView(content(ui, chosen) { picked ->
                    chosen = picked
                    onPicked(picked)
                    render()
                }, ui.lp(top = 12))
            }
        }
        render()
        return holder
    }

    /** The 16 swatches and the "Automatic colour" button. */
    private fun content(ui: Ui, chosen: Int, pick: (Int) -> Unit): View {
        val box = ui.column()
        val names = ui.context.resources.getStringArray(R.array.color_names)
        val cells = (0 until SWATCH_COUNT).map { index ->
            swatch(ui, index, names.getOrElse(index) { "" }, index == chosen) { pick(index) }
        }
        box.addView(ui.grid(COLUMNS, cells, gapDp = 10), ui.lp())
        val auto = ui.textButton(ui.context.getString(if (chosen < 0) R.string.color_auto_chosen else R.string.color_auto)) { pick(-1) }
        with(ui) { box.add(auto, top = 6) }
        return box
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
