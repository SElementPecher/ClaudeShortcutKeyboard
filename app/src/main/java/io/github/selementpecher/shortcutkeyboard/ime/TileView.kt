package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.look.TileColors
import io.github.selementpecher.shortcutkeyboard.look.tileColorsFor

/** What a tile looks like. */
internal data class TileLook(
    val kind: Kind,
    /** For [Kind.SHORTCUT]: the shortcut's id, which picks a stable colour. */
    val colourKey: String = "",
    /** For [Kind.ANSWER]: which of the Look's tile colours. */
    val colourIndex: Int = 0,
    /** A ring round the tile: it would take the dragged tile. */
    val highlighted: Boolean = false,
    /** The answer used last time: a stronger fill. */
    val selected: Boolean = false,
) {
    enum class Kind {
        /** A shortcut: one of the Look's tile colours. */
        SHORTCUT,

        /** A folder: the Look's folder colours. */
        FOLDER,

        /** A dashed "+ New" tile. */
        NEW,

        /** An answer to a question. */
        ANSWER,

        /** The accent OK / Add / Now tile. */
        OK,

        /** Plain buttons: back, pages, ABC, the settings gear, the number keys. */
        SPECIAL,

        /** An empty outline: where the dragged tile came from. */
        EMPTY,
    }
}

/** Everything one tile needs, so a grid can be rebuilt from a list of these. */
internal class TileSpec(
    val label: String,
    val look: TileLook,
    val description: String = label,
    /** The picture: an emoji or a letter. Null for plain buttons and answers. */
    val glyph: String? = null,
    /** A small extra cue in the corner (the folder sign). */
    val badge: String? = null,
    /** Can be picked up with a long press (grid screen only). */
    val draggable: Boolean = false,
    val id: String? = null,
    val enabled: Boolean = true,
    /** Overrides the usual text size (sp), e.g. for the number keys. */
    val textSp: Float? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * One tile or button, drawn in the current Look: a picture and a name (beside each other or
 * stacked), a folder badge, quiet press feedback.
 */
internal class TileView(context: Context, private val theme: KeyboardTheme) : FrameLayout(context) {
    private val inner = LinearLayout(context)
    private val glyphView = TextView(context)
    private val nameView = TextView(context)
    private val badgeView = TextView(context)
    private var onTap: (() -> Unit)? = null
    private val look = theme.look

    init {
        isClickable = true
        isFocusable = true
        isSoundEffectsEnabled = false
        clipChildren = false
        inner.orientation = if (theme.pictureBesideName) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        inner.gravity = Gravity.CENTER
        val pad = theme.dp(if (theme.compact) 8 else 12)
        inner.setPadding(pad, theme.dp(6), pad, theme.dp(6))

        glyphView.gravity = Gravity.CENTER
        glyphView.includeFontPadding = false
        glyphView.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (theme.pictureBesideName) GLYPH_SP_WIDE else GLYPH_SP_SQUARE)
        glyphView.setTypeface(theme.headingTypeface)

        nameView.includeFontPadding = false
        nameView.setTypeface(theme.nameTypeface)
        nameView.maxLines = 2
        nameView.ellipsize = TextUtils.TruncateAt.END

        if (theme.pictureBesideName) {
            val size = theme.dp(if (theme.compact) 30 else 38)
            inner.addView(glyphView, LinearLayout.LayoutParams(size, size).apply { marginEnd = theme.dp(10) })
            // Full height, so a long name can shrink a little to fit on two lines instead of being cut.
            inner.addView(nameView, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        } else {
            inner.addView(glyphView, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            inner.addView(nameView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = theme.dp(4) })
        }
        addView(inner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        badgeView.includeFontPadding = false
        addView(badgeView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            setMargins(0, theme.dp(6), theme.dp(8), 0)
        })

        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onTap?.invoke()
        }
    }

    fun bind(spec: TileSpec) {
        onTap = spec.onClick
        contentDescription = spec.description
        isEnabled = spec.enabled
        alpha = if (spec.enabled) 1f else 0.4f
        isClickable = spec.onClick != null

        val colours = coloursFor(spec.look)
        applyBackground(spec.look, colours)

        val hasGlyph = !spec.glyph.isNullOrEmpty()
        glyphView.visibility = if (hasGlyph) VISIBLE else GONE
        glyphView.text = spec.glyph.orEmpty()
        glyphView.setTextColor(colours.fg)
        if (hasGlyph && theme.pictureBesideName) {
            glyphView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(StyleColours.withAlpha(colours.fg, CIRCLE_ALPHA))
            }
        } else {
            glyphView.background = null
        }

        nameView.text = spec.label
        nameView.setTextColor(colours.fg)
        // Tiles with a picture use the Look's tile size; plain answers and buttons have room for bigger words.
        val sizeSp = spec.textSp ?: if (hasGlyph) look.type.tileSp else maxOf(look.type.tileSp, PLAIN_MIN_SP)
        val shownSp = if (spec.textSp == null && theme.compact) maxOf(sizeSp - 2f, look.type.smallSp) else sizeSp
        nameView.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE)
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, shownSp)
        if (hasGlyph && theme.pictureBesideName && shownSp.toInt() > look.type.smallSp.toInt()) {
            nameView.setAutoSizeTextTypeUniformWithConfiguration(
                look.type.smallSp.toInt(), shownSp.toInt(), 1, TypedValue.COMPLEX_UNIT_SP,
            )
        }
        // Buttons and answers have no picture: centre the name. Otherwise it sits beside or under the picture.
        val centred = !hasGlyph || !theme.pictureBesideName
        nameView.gravity = if (centred) Gravity.CENTER else Gravity.START or Gravity.CENTER_VERTICAL
        nameView.maxLines = if (!hasGlyph && spec.look.kind == TileLook.Kind.SPECIAL) 1 else 2
        inner.gravity = Gravity.CENTER

        badgeView.visibility = if (spec.badge != null) VISIBLE else GONE
        badgeView.text = spec.badge.orEmpty()
        badgeView.setTextColor(colours.fg)

        val raised = look.shape.elevationDp > 0f && spec.look.kind in RAISED && spec.enabled
        elevation = if (raised) theme.elevationPx else 0f
    }

    private fun coloursFor(l: TileLook): TileColors {
        val p = theme.palette
        return when (l.kind) {
            TileLook.Kind.SHORTCUT -> p.tileColorsFor(l.colourKey)
            TileLook.Kind.FOLDER -> p.folder
            TileLook.Kind.ANSWER ->
                if (l.selected) TileColors(p.accent, p.onAccent) else p.tiles[Math.floorMod(l.colourIndex, p.tiles.size)]
            TileLook.Kind.OK -> TileColors(p.accent, p.onAccent)
            TileLook.Kind.SPECIAL -> TileColors(
                if (look.shape.strokeDp > 0f) p.surface else p.surfaceAlt,
                p.ink,
            )
            TileLook.Kind.NEW -> TileColors(Color.TRANSPARENT, p.inkSoft)
            TileLook.Kind.EMPTY -> TileColors(Color.TRANSPARENT, Color.TRANSPARENT)
        }
    }

    private fun applyBackground(l: TileLook, colours: TileColors) {
        val p = theme.palette
        val dashed = l.kind == TileLook.Kind.NEW || l.kind == TileLook.Kind.EMPTY
        // The plain outline colour is very faint on some Looks; a dashed line needs to be seen.
        val dashColour = StyleColours.dash(p.outline, p.inkSoft)
        val strokeColour: Int
        val strokeWidth: Int
        when {
            l.highlighted -> { strokeColour = p.accent; strokeWidth = theme.dp(3) }
            dashed -> { strokeColour = dashColour; strokeWidth = theme.dp(2) }
            l.kind == TileLook.Kind.FOLDER && look.id == "bold" -> { strokeColour = p.accent; strokeWidth = theme.dp(2) }
            theme.strokePx > 0 && !(l.kind == TileLook.Kind.OK || l.selected) -> { strokeColour = p.outline; strokeWidth = theme.strokePx }
            else -> { strokeColour = Color.TRANSPARENT; strokeWidth = 0 }
        }
        fun shape(fill: Int) = GradientDrawable().apply {
            cornerRadius = theme.tileRadius
            setColor(fill)
            if (strokeWidth > 0) {
                if (dashed && !l.highlighted) setStroke(strokeWidth, strokeColour, theme.dp(7).toFloat(), theme.dp(5).toFloat())
                else setStroke(strokeWidth, strokeColour)
            }
        }
        val pressedFill = if (dashed) StyleColours.withAlpha(p.inkSoft, 0x22) else TileStyle.pressed(colours.bg)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressedFill))
            addState(intArrayOf(), shape(colours.bg))
        }
    }

    /** A quiet 120 ms press: the tile shrinks a touch. */
    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed && isEnabled) PRESSED_SCALE else 1f
        animate().scaleX(scale).scaleY(scale).setDuration(PRESS_MS).start()
    }

    private object StyleColours {
        fun withAlpha(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)
        fun dash(outline: Int, soft: Int): Int = TileStyle.mix(outline, soft, 0.65f)
    }

    companion object {
        private const val PLAIN_MIN_SP = 18f
        private const val GLYPH_SP_WIDE = 20f
        private const val GLYPH_SP_SQUARE = 30f
        private const val CIRCLE_ALPHA = 0x26
        private const val PRESSED_SCALE = 0.97f
        private const val PRESS_MS = 120L
        private val RAISED = setOf(TileLook.Kind.SHORTCUT, TileLook.Kind.FOLDER, TileLook.Kind.ANSWER, TileLook.Kind.OK, TileLook.Kind.SPECIAL)
    }
}
