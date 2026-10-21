package io.github.selementpecher.shortcutkeyboard.look

import android.content.Context
import android.graphics.Typeface
import io.github.selementpecher.shortcutkeyboard.R

/**
 * The handwriting font (Patrick Hand, SIL Open Font Licence, see docs/licenses) the Exercise book uses for notes written
 * "by hand", like the privacy promise. Null if it cannot be loaded, so the usual font is used.
 */
internal fun handwritingFont(context: Context): Typeface? =
    runCatching { context.resources.getFont(R.font.patrick_hand) }.getOrNull()

/** Handwriting looks smaller than the usual font at the same size, so it is set this much bigger (sp). */
internal const val HANDWRITING_EXTRA_SP = 4f
