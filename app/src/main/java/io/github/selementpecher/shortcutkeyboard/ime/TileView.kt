package io.github.selementpecher.shortcutkeyboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.TextView
import io.github.selementpecher.shortcutkeyboard.R

/** What a tile looks like. */
internal data class TileLook(
    val kind: Kind,
    /** For [Kind.SHORTCUT]: which of the strong colours. */
    val colourIndex: Int = 0,
    /** A ring round the tile: it would take the dragged tile. */
    val highlighted: Boolean = false,
) {
    enum class Kind {
        /** A shortcut: one of six strong colours, white text. */
        SHORTCUT,

        /** A folder: pale yellow. */
        FOLDER,

        /** A dashed "+ New" tile. */
        NEW,

        /** An answer to a question. */
        ANSWER,

        /** The green OK / Add / Now tile. */
        OK,

        /** Plain buttons: back, pages, ABC, the settings gear. */
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
    /** Can be picked up with a long press (grid screen only). */
    val draggable: Boolean = false,
    val id: String? = null,
    val enabled: Boolean = true,
    /** Overrides the usual text size, e.g. for small buttons. */
    val textSizeRes: Int? = null,
    val onClick: (() -> Unit)? = null,
)

/** A big rounded rectangle with a short label. Used for every tile and button. */
internal class TileView(context: Context) : TextView(context) {
    private var appliedLook: TileLook? = null
    private var onTap: (() -> Unit)? = null

    init {
        gravity = Gravity.CENTER
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(6), dp(4), dp(6), dp(4))
        isClickable = true
        isFocusable = true
        isSoundEffectsEnabled = false
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onTap?.invoke()
        }
    }

    fun bind(spec: TileSpec, defaultTextSizeRes: Int) {
        text = spec.label
        contentDescription = spec.description
        onTap = spec.onClick
        isEnabled = spec.enabled
        alpha = if (spec.enabled) 1f else 0.35f
        isClickable = spec.onClick != null
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(spec.textSizeRes ?: defaultTextSizeRes))
        applyLook(spec.look)
    }

    fun applyLook(look: TileLook) {
        if (look == appliedLook) return
        appliedLook = look
        val radius = resources.getDimension(R.dimen.kb_tile_radius)
        val stroke = resources.getDimensionPixelSize(R.dimen.kb_tile_stroke)
        val ring = resources.getDimensionPixelSize(R.dimen.kb_tile_stroke_drop)
        val white = context.getColor(R.color.kb_tile_text)

        var fill = Color.TRANSPARENT
        var pressedFill = context.getColor(R.color.kb_key_pressed)
        var textColour = white
        var outline = Color.TRANSPARENT
        var dashed = false
        when (look.kind) {
            TileLook.Kind.SHORTCUT -> fill = context.getColor(COLOURS[look.colourIndex.coerceIn(0, COLOURS.size - 1)])
            TileLook.Kind.FOLDER -> {
                fill = context.getColor(R.color.kb_tile_folder)
                textColour = context.getColor(R.color.kb_tile_folder_text)
            }
            TileLook.Kind.ANSWER -> fill = context.getColor(R.color.kb_tile_answer)
            TileLook.Kind.OK -> fill = context.getColor(R.color.kb_tile_ok)
            TileLook.Kind.SPECIAL -> {
                fill = context.getColor(R.color.kb_key_special)
                pressedFill = context.getColor(R.color.kb_key_special_pressed)
                textColour = context.getColor(R.color.kb_key_text)
            }
            TileLook.Kind.NEW -> {
                outline = context.getColor(R.color.kb_tile_new_stroke)
                textColour = context.getColor(R.color.kb_tile_new_text)
                dashed = true
            }
            TileLook.Kind.EMPTY -> {
                outline = context.getColor(R.color.kb_tile_new_stroke)
                textColour = Color.TRANSPARENT
                dashed = true
            }
        }
        if (look.kind != TileLook.Kind.NEW && look.kind != TileLook.Kind.SPECIAL && look.kind != TileLook.Kind.EMPTY) {
            pressedFill = TileStyle.darken(fill)
        }
        fun shape(color: Int) = GradientDrawable().apply {
            cornerRadius = radius
            setColor(color)
            when {
                look.highlighted -> setStroke(ring, context.getColor(R.color.kb_tile_drop))
                dashed -> setStroke(stroke, outline, dp(8).toFloat(), dp(5).toFloat())
                outline != Color.TRANSPARENT -> setStroke(stroke, outline)
            }
        }
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressedFill))
            addState(intArrayOf(), shape(fill))
        }
        setTextColor(textColour)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        val COLOURS = intArrayOf(
            R.color.kb_tile_1,
            R.color.kb_tile_2,
            R.color.kb_tile_3,
            R.color.kb_tile_4,
            R.color.kb_tile_5,
            R.color.kb_tile_6,
        )
    }
}
