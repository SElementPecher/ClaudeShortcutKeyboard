package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.ImageView
import io.github.selementpecher.shortcutkeyboard.look.Icons

/**
 * A ghost icon button: just the icon, a 48dp touch area and a soft round highlight while pressed.
 * [setHighlighted] shows the drag-over state (a tile would be dropped here).
 */
internal class IconButton(
    context: Context,
    theme: KeyboardTheme,
    kind: Icons.Kind,
    description: String,
    onClick: () -> Unit,
) : ImageView(context) {

    init {
        val palette = theme.palette
        scaleType = ScaleType.CENTER
        isClickable = true
        isFocusable = true
        isSoundEffectsEnabled = false
        contentDescription = description
        setImageDrawable(Icons.drawable(kind, palette.ink, theme.dp(ICON_DP)))
        fun disc(fill: Int, ring: Int = 0) = InsetDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
                if (ring != 0) setStroke(theme.dp(2), ring)
            },
            theme.dp(4),
        )
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), disc((palette.accent and 0x00FFFFFF) or 0x33000000, palette.accent))
            addState(intArrayOf(android.R.attr.state_pressed), disc((palette.ink and 0x00FFFFFF) or 0x24000000))
            addState(intArrayOf(), disc(Color.TRANSPARENT))
        }
        setOnClickListener { onClick() }
    }

    fun setHighlighted(on: Boolean) {
        isSelected = on
    }

    /** Dimmed and untappable, e.g. a page arrow at the end of the pages. */
    fun setActive(active: Boolean) {
        isEnabled = active
        alpha = if (active) 1f else 0.35f
    }

    companion object {
        private const val ICON_DP = 24
    }
}
