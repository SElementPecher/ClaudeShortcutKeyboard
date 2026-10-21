package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.look.Icons
import io.github.selementpecher.shortcutkeyboard.look.Look
import io.github.selementpecher.shortcutkeyboard.look.handwritingFont
import io.github.selementpecher.shortcutkeyboard.look.HANDWRITING_EXTRA_SP
import io.github.selementpecher.shortcutkeyboard.look.LookStore
import io.github.selementpecher.shortcutkeyboard.look.Looks
import io.github.selementpecher.shortcutkeyboard.look.typefaceOf
import io.github.selementpecher.shortcutkeyboard.model.Library
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Settings: pick a Look, back up and restore shortcuts, try the keyboard, short help, version.
 * Picking a Look applies at once (the screen redraws itself).
 */
class SettingsActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var screen: UiScreen
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Reads and writes backup files off the main thread. */
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private var panel: UiPanel? = null

    /** A backup file picked for restore whose question is still open; kept across rotation. */
    private var pendingRestore: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this, LookStore.current(this))
        screen = ui.screen(getString(R.string.settings_title), onBack = { finish() })
        build()
        screen.install(this)
        savedInstanceState?.getString(STATE_PENDING_RESTORE)?.let { readBackup(Uri.parse(it)) }
    }

    override fun onResume() {
        super.onResume()
        if (ui.isStale()) recreate() else refreshKeyboardStatus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingRestore?.let { outState.putString(STATE_PENDING_RESTORE, it.toString()) }
    }

    override fun onDestroy() {
        panel?.dismiss()
        panel = null
        mainHandler.removeCallbacksAndMessages(null)
        // Work already queued (e.g. writing a backup) still finishes.
        io.shutdown()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- screen

    private lateinit var keyboardStatus: LinearLayout

    private fun build() = with(ui) {
        val content = screen.content
        keyboardStatus = column()
        content.add(keyboardStatus, top = 8)
        refreshKeyboardStatus()
        content.add(grid(2, tiles()), top = 16)
        content.add(aboutBox(), top = 20)
        content.add(deleteLink(), top = 8)
    }

    /** First thing on the screen: is the keyboard on? Tap to turn it on, or to switch to it. */
    private fun refreshKeyboardStatus() = with(ui) {
        keyboardStatus.removeAllViews()
        val on = KeyboardSetup.isEnabled(this@SettingsActivity)
        val bar = row().apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setPadding(dp(16), dp(8), dp(8), dp(8))
            background = shape(blend(palette.bg, if (on) palette.success else palette.danger, if (on) 0.14f else 0.10f), 16f)
        }
        bar.addView(
            ImageView(this@SettingsActivity).apply {
                setImageDrawable(Icons.drawable(Icons.Kind.KEYBOARD, palette.ink, dp(28)))
            },
            LinearLayout.LayoutParams(dp(32), dp(32)),
        )
        bar.addView(
            text(getString(if (on) R.string.settings_keyboard_on else R.string.settings_keyboard_off), look.type.headingSp, 700, palette.ink)
                .apply { layoutParams = lp(width = 0, weight = 1f, start = 12) },
        )
        bar.addView(
            textButton(getString(if (on) R.string.settings_keyboard_switch else R.string.settings_keyboard_turn_on)) {
                if (on) showKeyboardPicker() else openKeyboardSettings()
            }.apply { layoutParams = lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT) },
        )
        keyboardStatus.addView(bar, lp())
    }

    private fun showKeyboardPicker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    private fun openKeyboardSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No keyboard settings screen", e)
        }
    }

    /** Six big tiles, the same kind as the rest of the app: a picture and a short name each. */
    private fun tiles(): List<View> = with(ui) {
        fun settingsTile(name: String, icon: String, subtitle: String? = null, onClick: () -> Unit) =
            tile(name = name, icon = icon, kind = TileKind.SHORTCUT, subtitle = subtitle, onClick = onClick)
        listOf(
            settingsTile(getString(R.string.settings_tile_prefs), "🎨") {
                startActivity(Intent(this@SettingsActivity, PreferencesActivity::class.java))
            },
            settingsTile(getString(R.string.tour_again_button), "▶️") { OnboardingActivity.open(this@SettingsActivity) },
            settingsTile(getString(R.string.cheat_settings_title), "📋") {
                startActivity(Intent(this@SettingsActivity, CheatSheetActivity::class.java))
            },
            settingsTile(getString(R.string.examples_title), "✨") { addExamples() },
            settingsTile(getString(R.string.settings_tile_backup), "💾") { startBackup() },
            settingsTile(getString(R.string.settings_tile_restore), "📂") { startRestore() },
        )
    }

    /** Adds the starter quiKeys and folders that are missing, and says how many were added. */
    private fun addExamples() {
        val before = ShortcutRepository.library(this).shortcuts.size
        try {
            ShortcutRepository.addExamples(this)
            val added = ShortcutRepository.library(this).shortcuts.size - before
            screen.snack(if (added > 0) resources.getQuantityString(R.plurals.examples_added, added, added) else getString(R.string.examples_none))
        } catch (e: Exception) {
            screen.snack(getString(R.string.edit_save_failed), error = true)
        }
    }

    /** The privacy promise, set off in a tinted box so it is easy to find, with the version under it. */
    private fun aboutBox(): View = with(ui) {
        val box = column()
        box.add(
            text(getString(R.string.settings_about_privacy), look.type.bodySp, 600, palette.ink, 1.45f).apply {
                background = shape(blend(palette.bg, palette.success, 0.14f), 14f)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                // Exercise book: written by hand.
                if (look.ruled) handwritingFont(this@SettingsActivity)?.let { font ->
                    typeface = font
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, look.type.bodySp + HANDWRITING_EXTRA_SP)
                }
            },
        )
        box.add(small(getString(R.string.settings_about_version, versionName())), top = 8)
        box
    }

    /** The one thing that deletes, last and on its own. Asks first. */
    private fun deleteLink(): View = with(ui) {
        dangerButton(getString(R.string.again_button), Icons.Kind.TRASH) {
            ui.confirm(
                this@SettingsActivity,
                title = getString(R.string.again_confirm_title),
                message = getString(R.string.again_confirm_message),
                confirmText = getString(R.string.again_confirm),
                cancelText = getString(R.string.dialog_cancel),
                danger = true,
            ) {
                try {
                    ShortcutRepository.replaceAll(this@SettingsActivity, Library())
                    screen.snack(getString(R.string.again_done))
                } catch (e: Exception) {
                    screen.snack(getString(R.string.edit_save_failed), error = true)
                }
            }.show()
        }
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    // ---------------------------------------------------------------- backup and restore

    private fun startBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
            .putExtra(Intent.EXTRA_TITLE, getString(R.string.backup_file_name))
        startForResult(intent, REQUEST_BACKUP)
    }

    private fun startRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, RESTORE_MIME_TYPES)
        startForResult(intent, REQUEST_RESTORE)
    }

    private fun startForResult(intent: Intent, requestCode: Int) {
        try {
            startActivityForResult(intent, requestCode)
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.no_file_app), error = true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_BACKUP -> writeBackup(uri)
            REQUEST_RESTORE -> readBackup(uri)
        }
    }

    private fun writeBackup(uri: Uri) {
        val library = ShortcutRepository.library(this)
        val count = library.shortcuts.size
        val resolver = applicationContext.contentResolver
        val appContext = applicationContext
        inBackground(work = { Backup.write(resolver, uri, library) }) { result ->
            // Shown even if this screen was closed meanwhile, so the user always hears back.
            val message = if (result.isSuccess) {
                appContext.resources.getQuantityString(R.plurals.backup_saved, count, count)
            } else {
                Log.w(TAG, "Backup failed", result.exceptionOrNull())
                appContext.getString(R.string.backup_failed)
            }
            if (isDestroyed) Snackbars.later(message, error = result.isFailure) else screen.snack(message, error = result.isFailure)
        }
    }

    private fun readBackup(uri: Uri) {
        pendingRestore = uri
        val resolver = applicationContext.contentResolver
        inBackground(work = { Backup.read(resolver, uri) }) { result ->
            // If the screen was rotated meanwhile, the new one reads the file again.
            if (isDestroyed) return@inBackground
            when (val outcome = result.getOrElse { RestoreOutcome.ReadFailed }) {
                is RestoreOutcome.Found -> askHowToRestore(outcome.library)
                RestoreOutcome.Empty -> restoreFinished(R.string.restore_empty)
                RestoreOutcome.NotABackup -> restoreFinished(R.string.restore_bad_file)
                RestoreOutcome.ReadFailed -> restoreFinished(R.string.restore_failed)
            }
        }
    }

    private fun restoreFinished(message: Int) {
        pendingRestore = null
        toast(getString(message), error = true)
    }

    private fun askHowToRestore(library: Library) {
        val count = library.shortcuts.size
        val ask = ui.ask(
            this,
            title = resources.getQuantityString(R.plurals.restore_found, count, count),
            message = getString(R.string.restore_message),
            buttons = listOf(
                DialogButton(getString(R.string.restore_add)) { applyRestore(library, replace = false); true },
                DialogButton(getString(R.string.restore_replace), ButtonStyle.DANGER) { applyRestore(library, replace = true); true },
                DialogButton(getString(R.string.dialog_cancel), ButtonStyle.TEXT) { pendingRestore = null; true },
            ),
        )
        ask.dialog.setOnCancelListener { pendingRestore = null }
        panel?.dismiss()
        panel = ask
        ask.show()
    }

    private fun applyRestore(library: Library, replace: Boolean) {
        pendingRestore = null
        try {
            if (replace) ShortcutRepository.replaceAll(this, library) else ShortcutRepository.merge(this, library)
        } catch (e: Exception) {
            Log.w(TAG, "Restore failed", e)
            toast(getString(R.string.restore_save_failed), error = true)
            return
        }
        val total = ShortcutRepository.all(this).size
        toast(
            if (replace) {
                resources.getQuantityString(R.plurals.restore_replaced, total, total)
            } else {
                resources.getQuantityString(R.plurals.restore_added, library.shortcuts.size, library.shortcuts.size)
            },
        )
    }

    /** Runs [work] on the background thread, then [done] on the main thread. */
    private fun <T> inBackground(work: () -> T, done: (Result<T>) -> Unit) {
        try {
            io.execute {
                val result = runCatching(work)
                mainHandler.post { done(result) }
            }
        } catch (e: RejectedExecutionException) {
            // The screen is closing.
            Log.w(TAG, "Background work refused", e)
        }
    }

    private fun toast(message: String, error: Boolean = false) {
        screen.snack(message, error)
    }

    companion object {
        private const val TAG = "SettingsActivity"
        private const val REQUEST_BACKUP = 1
        private const val REQUEST_RESTORE = 2
        private const val STATE_PENDING_RESTORE = "pendingRestore"
        private val RESTORE_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
    }
}
