package io.github.selementpecher.shortcutkeyboard.look

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build

/** How a shortcut or folder tile is laid out. */
enum class TileLayout {
    /** A wide rounded tile: picture at the top left, name below. */
    WIDE,

    /** A square tile: a big picture in the middle and a small name underneath. */
    SQUARE,
}

/** Background and text colour of one kind of tile. */
data class TileColors(val bg: Int, val fg: Int)

/** Every colour a screen needs, for one of light or dark. Plain ARGB ints. */
class Palette(
    val bg: Int,
    val surface: Int,
    val surfaceAlt: Int,
    val ink: Int,
    val inkSoft: Int,
    val accent: Int,
    val onAccent: Int,
    val outline: Int,
    val success: Int,
    val danger: Int,
    /** Colours for shortcut tiles; a shortcut always gets the same one (see [tileColorsFor]). */
    val tiles: List<TileColors>,
    val folder: TileColors,
    /** The 16 colours an owner can pick for a tile (see [tileColorsFor]). */
    val swatches: List<TileColors>,
)

/** Rounded corners and lines, in dp. */
data class Shape(val tileRadiusDp: Int, val cardRadiusDp: Int, val buttonRadiusDp: Int, val strokeDp: Float, val elevationDp: Float)

/** Text sizes in sp and the weight (100–900) of tile names. */
data class Type(val titleSp: Float, val headingSp: Float, val bodySp: Float, val smallSp: Float, val tileSp: Float, val tileWeight: Int, val headingWeight: Int)

/** The same sizes multiplied by [factor] (weights stay as they are). */
fun Type.scaled(factor: Float): Type = copy(
    titleSp = titleSp * factor,
    headingSp = headingSp * factor,
    bodySp = bodySp * factor,
    smallSp = smallSp * factor,
    tileSp = tileSp * factor,
)

/** The same Look with other text sizes (e.g. [Type.scaled] for "Large" text). */
fun Look.withType(type: Type): Look =
    Look(id, displayName, tagline, light, dark, shape, type, tileLayout, keyboardColumns, keyboardRows, ruled)

/**
 * One complete design: colours (light and dark), shapes, text sizes and the tile layout.
 *
 * The app and the keyboard both build their views in code from a [Look], so changing the look is
 * just a matter of picking another one in [LookStore].
 */
class Look(
    val id: String,
    val displayName: String,
    val tagline: String,
    val light: Palette,
    val dark: Palette,
    val shape: Shape,
    val type: Type,
    val tileLayout: TileLayout,
    /** Keyboard grid shape when the phone is upright (always 6 tiles per page). */
    val keyboardColumns: Int,
    val keyboardRows: Int,
    /** Ruled paper with a margin behind screens and the keyboard, instead of a plain colour. */
    val ruled: Boolean = false,
) {
    init {
        require(keyboardColumns * keyboardRows == 6) { "The keyboard shows 6 tiles at a time" }
    }

    /** The palette for the phone's current light or dark setting. */
    fun palette(context: Context): Palette =
        if (isNight(context)) dark else light

    companion object {
        fun isNight(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}

/** How many colours an owner can pick from. */
const val SWATCH_COUNT = Swatches.COUNT

/**
 * A tile's colours. If the owner picked one ([color] 0–15) it is that swatch; otherwise (−1) it is
 * always the same colour for the same [key] (a shortcut's id).
 */
fun Palette.tileColorsFor(key: String, color: Int = -1): TileColors {
    swatches.getOrNull(color)?.let { return it }
    // Not String.hashCode alone: spread it so neighbouring ids do not share a colour so often.
    val spread = (key.hashCode().toLong() * 2654435761L) ushr 8
    return tiles[(spread % tiles.size).toInt().let { if (it < 0) it + tiles.size else it }]
}

/** A typeface of the given weight (100–900). Below Android 9 only normal and bold exist. */
fun typefaceOf(weight: Int): Typeface =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, weight, false)
    else Typeface.create(Typeface.DEFAULT, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)

/** A folder's colours: the owner's pick if there is one, otherwise the Look's folder colour. */
fun Palette.folderColors(color: Int = -1): TileColors = swatches.getOrNull(color) ?: folder

private fun c(hex: Long) = (0xFF000000L or hex).toInt()
private fun t(bg: Long, fg: Long) = TileColors(c(bg), c(fg))

/** The designs the owner can try. */
object Looks {

    /** Soft and friendly: warm paper, pastel tiles, big rounded corners. */
    val CALM = Look(
        id = "calm",
        displayName = "Calm",
        tagline = "Soft colours, round corners",
        light = Palette(
            bg = c(0xF6F4EF), surface = c(0xFFFFFF), surfaceAlt = c(0xECE8E0),
            ink = c(0x1E2430), inkSoft = c(0x5B6270), accent = c(0x0F766E), onAccent = c(0xFFFFFF),
            outline = c(0xE0DBD0), success = c(0x15803D), danger = c(0xB42318),
            tiles = listOf(
                t(0xCFE8DC, 0x17382D), t(0xCFE3F7, 0x12304F), t(0xF9E6B0, 0x4A3A06),
                t(0xF8D5D5, 0x5A1E1E), t(0xDDD6F5, 0x2F2563), t(0xFBDCC4, 0x5C2E0E),
            ),
            folder = t(0xE6DFCF, 0x3A3426),
            swatches = Swatches.build(Swatches.Style.CALM_LIGHT),
        ),
        dark = Palette(
            // Dark, but still soft: warm charcoal, quiet pastel tiles at low strength, a gentle teal.
            bg = c(0x181A19), surface = c(0x222524), surfaceAlt = c(0x2C2F2D),
            ink = c(0xE8E6DF), inkSoft = c(0xA3A59E), accent = c(0x7FBFB3), onAccent = c(0x0C2623),
            outline = c(0x363A37), success = c(0x8FC7A0), danger = c(0xE59A92),
            tiles = listOf(
                t(0x2C4038, 0xD3E6DD), t(0x2C3B4A, 0xD4E1EE), t(0x464029, 0xEEE5C6),
                t(0x4A3636, 0xEFD9D7), t(0x393650, 0xDEDAEE), t(0x4A3C31, 0xF0DECE),
            ),
            folder = t(0x39362D, 0xE6E0D0),
            swatches = Swatches.build(Swatches.Style.CALM_DARK),
        ),
        shape = Shape(tileRadiusDp = 24, cardRadiusDp = 24, buttonRadiusDp = 999, strokeDp = 0f, elevationDp = 2f),
        type = Type(titleSp = 28f, headingSp = 20f, bodySp = 17f, smallSp = 14f, tileSp = 19f, tileWeight = 600, headingWeight = 700),
        tileLayout = TileLayout.WIDE,
        keyboardColumns = 2,
        keyboardRows = 3,
    )

    /** Loud and clear: near-black or white, vivid tiles, heavy type, sharp contrast. */
    val BOLD = Look(
        id = "bold",
        displayName = "Bold",
        tagline = "Bright colours, big text",
        light = Palette(
            bg = c(0xFFFFFF), surface = c(0xF3F4F7), surfaceAlt = c(0xE6E8EE),
            ink = c(0x0B0C10), inkSoft = c(0x4B5162), accent = c(0x0B0C10), onAccent = c(0xC6FF3D),
            outline = c(0xD2D6DF), success = c(0x0F7A3B), danger = c(0xC0261E),
            tiles = listOf(
                t(0xFF6B5E, 0x1A0705), t(0xFFB62E, 0x1E1300), t(0xB8F23D, 0x101A00),
                t(0x3DD9F2, 0x00181D), t(0xB69CFF, 0x150A3A), t(0xFF7AC6, 0x2B0A1C),
            ),
            folder = t(0x0B0C10, 0xFFFFFF),
            swatches = Swatches.build(Swatches.Style.BOLD),
        ),
        dark = Palette(
            bg = c(0x0B0C10), surface = c(0x16181F), surfaceAlt = c(0x22252E),
            ink = c(0xFFFFFF), inkSoft = c(0xB4B9C6), accent = c(0xC6FF3D), onAccent = c(0x0B0C10),
            outline = c(0x343846), success = c(0x3DDC84), danger = c(0xFF6B6B),
            tiles = listOf(
                t(0xFF6B5E, 0x1A0705), t(0xFFB62E, 0x1E1300), t(0xB8F23D, 0x101A00),
                t(0x3DD9F2, 0x00181D), t(0xB69CFF, 0x150A3A), t(0xFF7AC6, 0x2B0A1C),
            ),
            folder = t(0x2A2D38, 0xFFFFFF),
            swatches = Swatches.build(Swatches.Style.BOLD),
        ),
        shape = Shape(tileRadiusDp = 20, cardRadiusDp = 20, buttonRadiusDp = 16, strokeDp = 0f, elevationDp = 0f),
        type = Type(titleSp = 32f, headingSp = 22f, bodySp = 18f, smallSp = 15f, tileSp = 21f, tileWeight = 800, headingWeight = 800),
        tileLayout = TileLayout.WIDE,
        keyboardColumns = 2,
        keyboardRows = 3,
    )

    /** Quiet and tidy: white, thin lines, one blue accent, square picture tiles in a 3 by 2 grid. */
    val SQUARES = Look(
        id = "squares",
        displayName = "Squares",
        tagline = "Big pictures, clean lines",
        light = Palette(
            bg = c(0xFAFAFA), surface = c(0xFFFFFF), surfaceAlt = c(0xF0F1F3),
            ink = c(0x111827), inkSoft = c(0x586072), accent = c(0x2563EB), onAccent = c(0xFFFFFF),
            outline = c(0xD3D7DE), success = c(0x15803D), danger = c(0xC81E1E),
            tiles = listOf(
                t(0xE8F0FE, 0x111827), t(0xE6F4EA, 0x111827), t(0xFEF3C7, 0x111827),
                t(0xFDE8EC, 0x111827), t(0xEFE9FE, 0x111827), t(0xE0F4F4, 0x111827),
            ),
            folder = t(0xEEF0F3, 0x111827),
            swatches = Swatches.build(Swatches.Style.SQUARES_LIGHT),
        ),
        dark = Palette(
            bg = c(0x0F1115), surface = c(0x181B21), surfaceAlt = c(0x20242B),
            ink = c(0xF3F4F6), inkSoft = c(0x9CA3AF), accent = c(0x6EA8FF), onAccent = c(0x07162F),
            outline = c(0x353B46), success = c(0x6FD08C), danger = c(0xFF8A8A),
            tiles = listOf(
                t(0x1E2A44, 0xF3F4F6), t(0x1B3326, 0xF3F4F6), t(0x44361A, 0xF3F4F6),
                t(0x44212B, 0xF3F4F6), t(0x2E2650, 0xF3F4F6), t(0x173B3B, 0xF3F4F6),
            ),
            folder = t(0x262A32, 0xF3F4F6),
            swatches = Swatches.build(Swatches.Style.SQUARES_DARK),
        ),
        shape = Shape(tileRadiusDp = 14, cardRadiusDp = 14, buttonRadiusDp = 12, strokeDp = 1.5f, elevationDp = 0f),
        type = Type(titleSp = 24f, headingSp = 18f, bodySp = 16f, smallSp = 13f, tileSp = 14f, tileWeight = 500, headingWeight = 600),
        tileLayout = TileLayout.SQUARE,
        keyboardColumns = 3,
        keyboardRows = 2,
    )

    /** Ruled school paper: cool white page, navy ink, highlighter-pen tiles, small corners. */
    val EXERCISE = Look(
        id = "exercise",
        displayName = "Exercise book",
        tagline = "Ruled paper, highlighter pens",
        light = Palette(
            bg = c(0xF3F7F9), surface = c(0xFFFFFF), surfaceAlt = c(0xE6EEF3),
            ink = c(0x1B2A4E), inkSoft = c(0x44527A), accent = c(0x1B2A4E), onAccent = c(0xFFFFFF),
            outline = c(0xBFD6E8), success = c(0x2E7D32), danger = c(0xB3261E),
            tiles = listOf(
                t(0xFFE55C, 0x1B2A4E), t(0xFFA6C9, 0x1B2A4E), t(0xA9EA7B, 0x1B2A4E),
                t(0x94DEFF, 0x1B2A4E), t(0xFFC36B, 0x1B2A4E), t(0xD9C7FF, 0x1B2A4E),
            ),
            folder = t(0xFFFFFF, 0x1B2A4E),
            swatches = Swatches.build(Swatches.Style.EXERCISE_LIGHT),
        ),
        dark = Palette(
            bg = c(0x141A2E), surface = c(0x1D2440), surfaceAlt = c(0x28315A),
            ink = c(0xE8ECF7), inkSoft = c(0xA9B4D6), accent = c(0x94DEFF), onAccent = c(0x0B1630),
            outline = c(0x3A4670), success = c(0x8FD19A), danger = c(0xF0968E),
            tiles = listOf(
                t(0x4A4520, 0xFFF2A8), t(0x5A2C42, 0xFFD3E6), t(0x35502A, 0xD8F5C5),
                t(0x24485A, 0xCDEEFF), t(0x5A4222, 0xFFE0B3), t(0x3E3560, 0xE5DAFF),
            ),
            folder = t(0x2A3358, 0xE8ECF7),
            swatches = Swatches.build(Swatches.Style.EXERCISE_DARK),
        ),
        shape = Shape(tileRadiusDp = 8, cardRadiusDp = 10, buttonRadiusDp = 12, strokeDp = 0f, elevationDp = 2f),
        type = Type(titleSp = 28f, headingSp = 20f, bodySp = 17f, smallSp = 14f, tileSp = 19f, tileWeight = 700, headingWeight = 800),
        tileLayout = TileLayout.WIDE,
        keyboardColumns = 2,
        keyboardRows = 3,
        ruled = true,
    )

    val all: List<Look> = listOf(CALM, BOLD, SQUARES, EXERCISE)

    fun byId(id: String?): Look = all.firstOrNull { it.id == id } ?: CALM
}
