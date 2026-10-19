package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.look.Look
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
        if (ui.isStale()) recreate()
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

    private fun build() = with(ui) {
        val content = screen.content
        content.add(lookCard(), top = 8)
        content.add(cheatCard(), top = 16)
        content.add(backupCard(), top = 16)
        content.add(tryCard(), top = 16)
        content.add(helpCard(), top = 16)
        content.add(aboutCard(), top = 16)
    }

    private fun cheatCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.cheat_settings_title)))
        card.add(small(getString(R.string.cheat_settings_help)), top = 4)
        card.add(secondaryButton(getString(R.string.cheat_open_button)) {
            startActivity(Intent(this@SettingsActivity, CheatSheetActivity::class.java))
        }, top = 14)
        card
    }

    private fun lookCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.settings_look_title)))
        card.add(small(getString(R.string.settings_look_help)), top = 4)
        for (option in Looks.all) card.add(lookOption(option), top = 14)
        card
    }

    /** One selectable Look: a live mini preview, its name and tagline, a ring when chosen. */
    private fun lookOption(option: Look): View = with(ui) {
        val chosen = option.id == look.id
        val box = column().apply {
            val r = 18f
            background = rippled(
                shape(palette.surfaceAlt.let { if (chosen) blend(palette.surface, palette.accent, 0.06f) else palette.surface }, r, if (chosen) palette.accent else palette.outline, if (chosen) 3f else 1.5f),
                r,
            )
            setPadding(dp(12), dp(12), dp(12), dp(14))
            isClickable = true
            isFocusable = true
            contentDescription = getString(if (chosen) R.string.look_chosen else R.string.look_choose, option.displayName)
            setOnClickListener { chooseLook(option) }
        }
        val labels = listOf(
            getString(R.string.look_preview_shortcut_1),
            getString(R.string.look_preview_shortcut_2),
            getString(R.string.look_preview_folder),
        )
        box.addView(LookPreview(this@SettingsActivity, option, labels).apply { layoutParams = lp() })
        val line = row()
        val names = column().apply { layoutParams = lp(width = 0, weight = 1f) }
        names.addView(text(option.displayName, look.type.headingSp, look.type.headingWeight, palette.ink))
        names.add(small(option.tagline), top = 2)
        line.addView(names)
        if (chosen) {
            line.addView(TextView(this@SettingsActivity).apply {
                text = "✓"
                gravity = Gravity.CENTER
                textSize = 18f
                typeface = typefaceOf(800)
                setTextColor(palette.onAccent)
                background = shape(palette.accent, 999f)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            })
        }
        box.add(line, top = 12)
        box
    }

    private fun chooseLook(option: Look) {
        if (option.id == ui.look.id) return
        LookStore.set(this, option)
        recreate()
    }

    private fun backupCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.settings_backup_title)))
        card.add(small(getString(R.string.settings_backup_help)), top = 4)
        card.add(secondaryButton(getString(R.string.settings_backup_button)) { startBackup() }, top = 16)
        card.add(secondaryButton(getString(R.string.settings_restore_button)) { startRestore() }, top = 10)
        card
    }

    private fun tryCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.settings_try_title)))
        card.add(small(getString(R.string.try_tip)), top = 4)
        card.add(field(getString(R.string.try_hint)), top = 14)
        card
    }

    private fun helpCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.settings_help_title)))
        val steps = listOf(
            R.string.settings_help_1, R.string.settings_help_2, R.string.settings_help_3,
            R.string.settings_help_4, R.string.settings_help_5,
        )
        steps.forEachIndexed { index, res ->
            val line = LinearLayout(this@SettingsActivity).apply { orientation = LinearLayout.HORIZONTAL }
            line.addView(TextView(this@SettingsActivity).apply {
                text = (index + 1).toString()
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = typefaceOf(700)
                setTextColor(palette.ink)
                background = shape(palette.surfaceAlt, 999f)
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { topMargin = dp(1) }
            })
            line.addView(body(getString(res)).apply { layoutParams = lp(width = 0, weight = 1f, start = 12) })
            card.add(line, top = if (index == 0) 14 else 12)
        }
        card
    }

    private fun aboutCard(): View = with(ui) {
        val card = card()
        card.add(heading(getString(R.string.settings_about_title)))
        card.add(body(getString(R.string.app_name)), top = 8)
        card.add(small(getString(R.string.settings_about_version, versionName())), top = 2)
        card.add(small(getString(R.string.settings_about_privacy)), top = 10)
        card
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
            toast(getString(R.string.no_file_app))
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
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
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
        toast(getString(message))
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
            toast(getString(R.string.restore_save_failed))
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

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "SettingsActivity"
        private const val REQUEST_BACKUP = 1
        private const val REQUEST_RESTORE = 2
        private const val STATE_PENDING_RESTORE = "pendingRestore"
        private val RESTORE_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")
    }
}
