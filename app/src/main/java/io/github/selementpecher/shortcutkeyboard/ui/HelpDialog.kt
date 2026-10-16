package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.app.AlertDialog
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
