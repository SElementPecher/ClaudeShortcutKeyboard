package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.look.TileColors
import io.github.selementpecher.shortcutkeyboard.look.folderColors
import io.github.selementpecher.shortcutkeyboard.look.tileColorsFor
import io.github.selementpecher.shortcutkeyboard.ui.FolderDrawable
import io.github.selementpecher.shortcutkeyboard.ui.TileSpec as AppTile

/** What a tile looks like. */
internal data class TileLook(
    val kind: Kind,
    /** For [Kind.SHORTCUT]: the shortcut's id, which picks a stable colour. */
    val colourKey: String = "",
    /** For shortcuts and folders: the colour the owner picked (0–15), or −1 to let the Look choose. */
    val custom: Int = -1,
    /** For [Kind.ANSWER]: which of the Look's tile colours. */
    val colourIndex: Int = 0,
    /** A ring round the tile: it would take the dragged tile. */
    val highlighted: Boolean = false,
    /** The answer used last time: a stronger fill. */
    val selected: Boolean = false,
    /** Rounded like the Look's buttons (a pill in Calm) instead of like its tiles. */
    val pill: Boolean = false,
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

        /** Plain buttons: the ABC pill and the number keys. */
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
    /** A quiet second line under the name, e.g. "3 items" on a folder. */
    val subtitle: String? = null,
    /** A line icon. On a [TileLook.Kind.NEW] tile it is the picture; on buttons it sits before the name. */
    val icon: Icons.Kind? = null,
    /** Can be picked up with a long press (grid screen only). */
    val draggable: Boolean = false,
    val id: String? = null,
    val enabled: Boolean = true,
    /** Overrides the usual text size (sp, before the owner's text size), e.g. for the number keys. */
    val textSp: Float? = null,
    val onClick: (() -> Unit)? = null,
    /** Twice as wide, so a page with an odd number of tiles has full rows. Only the first tile of a page. */
    val wide: Boolean = false,
)

/**
 * One tile or button, drawn in the current Look: a picture and a name (beside each other or
 * stacked), an optional second line, a folder sign, quiet press feedback.
 *
 * The name is as big as it can be while it fits in two lines (never smaller than the Look's small size).
 */
internal class TileView(context: Context, private val theme: KeyboardTheme) : FrameLayout(context) {
    private val inner = LinearLayout(context)
    private val glyphBox = FrameLayout(context)
    private val glyphView = TextView(context)
    private val iconView = ImageView(context)
    private val textBox = LinearLayout(context)
    private val nameView = TextView(context)
    private val subView = TextView(context)
    private var onTap: (() -> Unit)? = null
    private val look = theme.look

    private enum class Mode { BESIDE, STACKED, PLAIN }

    private var mode = Mode.PLAIN
    private var maxSp = 0f
    private var minSp = 0f
    private var isFolder = false
    private var subtitleWanted = false
    private var appliedSp = 0f
    private val fitPaint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        isSoundEffectsEnabled = false
        clipChildren = false

        glyphView.gravity = Gravity.CENTER
        glyphView.includeFontPadding = false
        glyphView.setTypeface(theme.headingTypeface)
        glyphBox.addView(glyphView, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        glyphBox.addView(iconView, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        nameView.includeFontPadding = false
        nameView.setTypeface(theme.nameTypeface)
        nameView.letterSpacing = KeyboardTheme.LETTER_SPACING
        nameView.setLineSpacing(0f, NAME_LINE_SPACING)
        nameView.ellipsize = TextUtils.TruncateAt.END
        noHyphens(nameView)

        subView.includeFontPadding = false
        subView.setTypeface(theme.bodyTypeface)
        subView.maxLines = 1
        subView.ellipsize = TextUtils.TruncateAt.END
        subView.alpha = SUBTITLE_ALPHA

        textBox.orientation = LinearLayout.VERTICAL
        textBox.addView(nameView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        textBox.addView(subView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = theme.dp(2) })

        inner.addView(glyphBox)
        inner.addView(textBox)
        addView(inner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

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

        isFolder = spec.look.kind == TileLook.Kind.FOLDER
        val colours = coloursFor(spec.look)

        val hasGlyph = !spec.glyph.isNullOrEmpty()
        val iconAsPicture = spec.icon != null && spec.look.kind == TileLook.Kind.NEW
        val picture = hasGlyph || iconAsPicture
        val inlineIcon = spec.icon != null && !iconAsPicture
        mode = when {
            !picture -> Mode.PLAIN
            theme.pictureBesideName -> Mode.BESIDE
            else -> Mode.STACKED
        }
        layoutFor(mode, picture || inlineIcon, spec.look.kind == TileLook.Kind.SPECIAL || spec.look.kind == TileLook.Kind.OK)
        applyBackground(spec.look, colours)

        // The picture: an emoji or letter, or a line icon; on a soft round disc beside the name.
        glyphView.visibility = if (hasGlyph) VISIBLE else GONE
        glyphView.text = spec.glyph.orEmpty()
        glyphView.setTextColor(colours.fg)
        val emoji = spec.glyph?.let { it.isNotEmpty() && it.codePointAt(0) > EMOJI_FROM } == true
        glyphView.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(if (mode == Mode.STACKED) AppTile.SQUARE_PICTURE_SP else if (emoji) AppTile.WIDE_PICTURE_SP else GLYPH_SP_LETTER))
        iconView.visibility = if (spec.icon != null) VISIBLE else GONE
        if (spec.icon != null) {
            val size = theme.dp(if (inlineIcon) INLINE_ICON_DP else if (mode == Mode.STACKED) STACKED_ICON_DP else PICTURE_ICON_DP)
            iconView.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            iconView.setImageDrawable(Icons.drawable(spec.icon, colours.fg, size))
        }
        glyphBox.background = if (mode == Mode.BESIDE) {
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(withAlpha(colours.fg, CIRCLE_ALPHA))
            }
        } else {
            null
        }

        nameView.text = spec.label
        nameView.setTextColor(colours.fg)
        // Tiles with a picture use the Look's tile size; plain answers and buttons have room for bigger words.
        val baseSp = spec.textSp ?: when {
            mode == Mode.STACKED -> maxOf(look.type.tileSp, STACKED_MIN_SP)
            picture -> look.type.tileSp
            else -> maxOf(look.type.tileSp, PLAIN_MIN_SP)
        }
        val shownSp = theme.sp(if (spec.textSp == null && theme.compact && mode != Mode.STACKED) maxOf(baseSp - 2f, look.type.smallSp) else baseSp)
        maxSp = shownSp
        // The floor is the Look's small size as the phone's font size makes it, not made bigger again by Large text.
        // A fixed size (the number keys) still gives way when the tile is too short to hold it, so nothing is cut off.
        minSp = if (spec.textSp != null) minOf(shownSp, maxOf(shownSp * 0.6f, 14f)) else minOf(shownSp, look.type.smallSp)
        appliedSp = 0f
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, shownSp)
        val centred = mode != Mode.BESIDE
        nameView.gravity = if (centred) Gravity.CENTER_HORIZONTAL else Gravity.START
        nameView.maxLines = if (!picture && '\n' !in spec.label && (spec.look.kind == TileLook.Kind.SPECIAL || spec.look.kind == TileLook.Kind.OK)) 1 else 2

        val showSub = !spec.subtitle.isNullOrEmpty()
        subtitleWanted = showSub
        subView.visibility = if (showSub) VISIBLE else GONE
        subView.text = spec.subtitle.orEmpty()
        subView.setTextColor(colours.fg)
        subView.setTextSize(TypedValue.COMPLEX_UNIT_SP, theme.sp(look.type.smallSp))
        subView.gravity = nameView.gravity

        val raised = look.shape.elevationDp > 0f && spec.look.kind in RAISED && spec.enabled
        elevation = if (raised) theme.elevationPx else 0f
        requestLayout()
    }

    private fun layoutFor(mode: Mode, showsPicture: Boolean, isButton: Boolean) {
        val padX = theme.dp(if (mode == Mode.STACKED) AppTile.SQUARE_PADDING_DP else AppTile.WIDE_PADDING_H_DP)
        val padY = theme.dp(if (theme.compact) 4 else 6)
        // A folder's tab takes a little room at the top.
        val tab = if (isFolder) theme.dp(tabDp()) else 0
        inner.setPadding(padX, padY + tab, padX, padY)
        glyphBox.visibility = if (showsPicture) VISIBLE else GONE
        when (mode) {
            Mode.BESIDE -> {
                inner.orientation = LinearLayout.HORIZONTAL
                inner.gravity = Gravity.CENTER_VERTICAL
                val size = theme.dp((if (theme.compact) 30 else AppTile.WIDE_PICTURE_DP) * minOf(theme.textScale, 1.15f))
                inner.updateChild(glyphBox, LinearLayout.LayoutParams(size, size).apply { marginEnd = theme.dp(AppTile.WIDE_GAP_DP) })
                inner.updateChild(textBox, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                textBox.gravity = Gravity.START
            }
            Mode.STACKED -> {
                inner.orientation = LinearLayout.VERTICAL
                inner.gravity = Gravity.CENTER
                inner.updateChild(glyphBox, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
                inner.updateChild(textBox, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = theme.dp(4) })
                textBox.gravity = Gravity.CENTER_HORIZONTAL
            }
            Mode.PLAIN -> {
                inner.orientation = LinearLayout.HORIZONTAL
                inner.gravity = Gravity.CENTER
                inner.updateChild(glyphBox, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = if (showsPicture) theme.dp(8) else 0
                })
                inner.updateChild(textBox, LinearLayout.LayoutParams(if (isButton) LayoutParams.WRAP_CONTENT else 0, LayoutParams.WRAP_CONTENT, if (isButton) 0f else 1f))
                textBox.gravity = Gravity.CENTER_HORIZONTAL
            }
        }
    }

    private fun LinearLayout.updateChild(child: android.view.View, params: LinearLayout.LayoutParams) {
        child.layoutParams = params
    }

    private fun coloursFor(l: TileLook): TileColors {
        val p = theme.palette
        return when (l.kind) {
            TileLook.Kind.SHORTCUT -> p.tileColorsFor(l.colourKey, l.custom)
            TileLook.Kind.FOLDER -> p.folderColors(l.custom)
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
        val dashColour = dash(p.outline, p.inkSoft)
        val strokeColour: Int
        val strokeWidth: Int
        when {
            l.highlighted -> { strokeColour = p.accent; strokeWidth = theme.dp(3) }
            dashed -> { strokeColour = dashColour; strokeWidth = theme.dp(2) }
            l.kind == TileLook.Kind.FOLDER && look.id == "bold" -> { strokeColour = p.accent; strokeWidth = theme.dp(2) }
            theme.strokePx > 0 && !(l.kind == TileLook.Kind.OK || l.selected) -> { strokeColour = p.outline; strokeWidth = theme.strokePx }
            else -> { strokeColour = Color.TRANSPARENT; strokeWidth = 0 }
        }
        val radius = if (l.pill) theme.buttonRadius else theme.tileRadius
        fun shape(fill: Int) = GradientDrawable().apply {
            cornerRadius = radius
            setColor(fill)
            if (strokeWidth > 0) {
                if (dashed && !l.highlighted) setStroke(strokeWidth, strokeColour, theme.dp(7).toFloat(), theme.dp(5).toFloat())
                else setStroke(strokeWidth, strokeColour)
            }
        }
        val pressedFill = if (dashed) withAlpha(p.inkSoft, 0x22) else TileStyle.pressed(colours.bg)
        // Folders have a little tab, as in the app.
        fun body(fill: Int): Drawable =
            if (l.kind == TileLook.Kind.FOLDER) FolderDrawable(fill, strokeColour, strokeWidth.toFloat(), radius, theme.dp(tabDp()).toFloat())
            else shape(fill)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), body(pressedFill))
            addState(intArrayOf(), body(colours.bg))
        }
    }

    // ---- Fitting the name ----

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            fitName(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * The biggest size (between [minSp] and [maxSp]) at which the name fits its lines and the room
     * left, with no word broken in the middle. The quiet second line is dropped if it cannot fit.
     */
    private fun fitName(width: Int, height: Int) {
        val text = nameView.text
        if (text.isNullOrEmpty() || minSp >= maxSp) return
        val lp = glyphBox.layoutParams as? LinearLayout.LayoutParams
        var availW = width - inner.paddingLeft - inner.paddingRight
        var availH = height - inner.paddingTop - inner.paddingBottom
        when (mode) {
            Mode.BESIDE -> availW -= (lp?.width ?: 0) + (lp?.marginEnd ?: 0)
            Mode.STACKED -> {
                val pictureH = if (iconView.visibility == VISIBLE) theme.dp(STACKED_ICON_DP) else (glyphView.textSize * GLYPH_LINE).toInt()
                availH -= pictureH + theme.dp(4)
            }
            Mode.PLAIN -> if (glyphBox.visibility == VISIBLE) availW -= theme.dp(INLINE_ICON_DP + 8)
        }
        if (availW <= 0 || availH <= 0) return
        val subH = if (subtitleWanted) (subView.textSize * SUB_LINE).toInt() + theme.dp(2) else 0

        var withSub = subtitleWanted
        var chosen = fitSize(text, availW, availH - subH)
        if (chosen == null && subtitleWanted) {
            withSub = false
            chosen = fitSize(text, availW, availH)
        }
        // Last resort: rather a little smaller than a word cut in the middle.
        val size = chosen ?: rescueSize(text, availW)
        subView.visibility = if (withSub) VISIBLE else GONE
        if (size != appliedSp) {
            appliedSp = size
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        }
    }

    private fun rescueSize(text: CharSequence, availW: Int): Float {
        var sp = minSp
        while (sp > RESCUE_MIN_SP) {
            val layout = layoutAt(text, sp, availW)
            val wordsFit = text.split(' ', '\n').none { it.isNotEmpty() && fitPaint.measureText(it) > availW }
            if (wordsFit && layout.lineCount <= nameView.maxLines) return sp
            sp -= FIT_STEP_SP
        }
        return RESCUE_MIN_SP
    }

    /** The biggest fitting size, or null if even [minSp] does not fit. Prefers a smaller size to a lonely last word. */
    private fun fitSize(text: CharSequence, availW: Int, availH: Int): Float? {
        var sp = maxSp
        while (true) {
            val layout = layoutAt(text, sp, availW)
            if (fits(layout, text, availW, availH)) return avoidOrphan(text, sp, layout, availW, availH)
            if (sp <= minSp) return null
            sp = maxOf(minSp, sp - FIT_STEP_SP)
        }
    }

    private fun avoidOrphan(text: CharSequence, sp: Float, layout: StaticLayout, availW: Int, availH: Int): Float {
        if (layout.lineCount != 2 || nameView.maxLines < 2) return sp
        val last = text.subSequence(layout.getLineStart(1), layout.getLineEnd(1)).trim()
        if (last.length > ORPHAN_CHARS) return sp
        var smaller = sp - FIT_STEP_SP
        while (smaller >= maxOf(minSp, sp - ORPHAN_SHRINK_SP)) {
            val one = layoutAt(text, smaller, availW)
            if (one.lineCount == 1 && fits(one, text, availW, availH)) return smaller
            smaller -= FIT_STEP_SP
        }
        return sp
    }

    private fun layoutAt(text: CharSequence, sp: Float, availW: Int): StaticLayout {
        fitPaint.set(nameView.paint)
        fitPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        return StaticLayout.Builder.obtain(text, 0, text.length, fitPaint, availW)
            .setLineSpacing(0f, NAME_LINE_SPACING)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .build()
    }

    private fun fits(layout: StaticLayout, text: CharSequence, availW: Int, availH: Int): Boolean {
        if (layout.lineCount > nameView.maxLines || layout.height > availH) return false
        // A word wider than the line would be cut in the middle.
        for (word in text.split(' ', '\n')) {
            if (word.isNotEmpty() && fitPaint.measureText(word) > availW) return false
        }
        return true
    }

    private fun noHyphens(view: TextView) {
        // Words are never split with a hyphen: harder to read.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
            view.breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        }
    }

    /** A quiet 120 ms press: the tile shrinks a touch (not when the phone has animations off). */
    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val scale = if (pressed && isEnabled) PRESSED_SCALE else 1f
        if (theme.animations) animate().scaleX(scale).scaleY(scale).setDuration(Motion.PRESS_MS).start()
        else { scaleX = scale; scaleY = scale }
    }

    private fun tabDp(): Float = if (mode == Mode.STACKED) AppTile.FOLDER_TAB_SQUARE_DP else AppTile.FOLDER_TAB_WIDE_DP

    private fun withAlpha(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)

    private fun dash(outline: Int, soft: Int): Int = TileStyle.mix(outline, soft, 0.65f)

    companion object {
        private const val PLAIN_MIN_SP = 18f
        private const val STACKED_MIN_SP = 16f
        private const val GLYPH_SP_LETTER = 20f
        private const val EMOJI_FROM = 0x2000
        private const val GLYPH_LINE = 1.2f
        private const val SUB_LINE = 1.25f
        private const val CIRCLE_ALPHA = 0x26
        private const val SUBTITLE_ALPHA = 0.78f
        private const val INLINE_ICON_DP = 24
        private const val PICTURE_ICON_DP = 22
        private const val STACKED_ICON_DP = 32
        private const val NAME_LINE_SPACING = 1.1f
        private const val FIT_STEP_SP = 0.5f
        private const val ORPHAN_CHARS = 2
        private const val RESCUE_MIN_SP = 11f
        private const val ORPHAN_SHRINK_SP = 2.5f
        private const val PRESSED_SCALE = 0.97f
        private val RAISED = setOf(TileLook.Kind.SHORTCUT, TileLook.Kind.FOLDER, TileLook.Kind.ANSWER, TileLook.Kind.OK, TileLook.Kind.SPECIAL)
    }
}
