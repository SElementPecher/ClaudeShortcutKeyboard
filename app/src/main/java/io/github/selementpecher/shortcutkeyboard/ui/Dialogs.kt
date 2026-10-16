package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.util.TypedValue
import android.view.View
import io.github.selementpecher.shortcutkeyboard.R

/** The "How blanks work" box, with a few short examples. */
internal object HelpDialog {

    /** Builds the dialog. The caller shows it, and dismisses it if the screen closes. */
    fun create(activity: Activity): AlertDialog =
        AlertDialog.Builder(activity)
            .setTitle(R.string.help_title)
            .setView(View.inflate(activity, R.layout.dialog_help, null))
            .setPositiveButton(R.string.dialog_got_it, null)
            .create()
}

/**
 * Shows [dialog] with easier-to-read buttons: normal case instead of ALL CAPS, and a bit bigger.
 */
internal fun showReadable(dialog: Dialog) {
    dialog.show()
    if (dialog !is AlertDialog) return
    for (which in intArrayOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
        dialog.getButton(which)?.apply {
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, DIALOG_BUTTON_TEXT_SP)
        }
    }
}

private const val DIALOG_BUTTON_TEXT_SP = 17f
