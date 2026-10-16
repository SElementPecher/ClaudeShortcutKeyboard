package io.github.selementpecher.shortcutkeyboard.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutJson
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.model.Shortcut
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * The app's home screen, also opened from the keyboard's settings button.
 *
 * Shows the two setup steps (with ticks that update live), a box to try the keyboard, the list
 * of shortcuts, and backup / restore through the system file picker.
 */
class MainActivity : Activity() {

    private lateinit var setupCard: View
    private lateinit var setupReady: View
    private lateinit var step1Row: View
    private lateinit var step1Badge: TextView
    private lateinit var step1Button: Button
    private lateinit var step2Row: View
    private lateinit var step2Badge: TextView
    private lateinit var step2Button: Button
    private lateinit var tryTip: TextView
    private lateinit var shortcutList: LinearLayout
    private lateinit var emptyText: TextView

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Reads and writes backup files off the main thread. */
    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    private var dialog: Dialog? = null

    /** A backup file picked for restore whose question is still open; kept across rotation. */
    private var pendingRestore: Uri? = null

    private val repositoryListener: () -> Unit = {
        // May be called from any thread, e.g. when the keyboard saves what it learned.
        mainHandler.post { if (!isDestroyed) refreshShortcuts() }
    }

    /** Ticks the setup steps as soon as the keyboard is turned on or picked. */
    private val keyboardSettingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            if (!isDestroyed) refreshSetup()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setActionBar(findViewById<Toolbar>(R.id.main_toolbar))
        EdgeToEdge.apply(this, findViewById(R.id.main_root), findViewById(R.id.main_top_bar))

        setupCard = findViewById(R.id.setup_card)
        setupReady = findViewById(R.id.setup_ready)
        step1Row = findViewById(R.id.setup_step1_row)
        step1Badge = findViewById(R.id.setup_step1_badge)
        step1Button = findViewById(R.id.setup_step1_button)
        step2Row = findViewById(R.id.setup_step2_row)
        step2Badge = findViewById(R.id.setup_step2_badge)
        step2Button = findViewById(R.id.setup_step2_button)
        tryTip = findViewById(R.id.try_tip)
        shortcutList = findViewById(R.id.shortcut_list)
        emptyText = findViewById(R.id.shortcuts_empty)

        step1Button.setOnClickListener { openKeyboardSettings() }
        step2Button.setOnClickListener { showKeyboardPicker() }
        findViewById<View>(R.id.new_shortcut_button).setOnClickListener { openEditor(null) }

        ShortcutRepository.addListener(repositoryListener)

        savedInstanceState?.getString(STATE_PENDING_RESTORE)?.let { readBackup(Uri.parse(it)) }
    }

    override fun onStart() {
        super.onStart()
        val resolver = contentResolver
        for (key in listOf(Settings.Secure.ENABLED_INPUT_METHODS, Settings.Secure.DEFAULT_INPUT_METHOD)) {
            try {
                resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, keyboardSettingsObserver)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not watch $key", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
        refreshShortcuts()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The keyboard picker is a dialog over this screen, so there is no onResume after it.
        if (hasFocus) refreshSetup()
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(keyboardSettingsObserver)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingRestore?.let { outState.putString(STATE_PENDING_RESTORE, it.toString()) }
    }

    override fun onDestroy() {
        ShortcutRepository.removeListener(repositoryListener)
        dialog?.dismiss()
        dialog = null
        mainHandler.removeCallbacksAndMessages(null)
        // Work already queued (e.g. writing a backup) still finishes.
        io.shutdown()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_backup -> {
            startBackup()
            true
        }
        R.id.menu_restore -> {
            startRestore()
            true
        }
        R.id.menu_help -> {
            showDialog(HelpDialog.create(this))
            true
        }
        else -> super.onOptionsItemSelected(item)
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

    // Setup steps

    private fun refreshSetup() {
        val enabled = KeyboardSetup.isEnabled(this)
        val selected = enabled && KeyboardSetup.isSelected(this)
        val ready = enabled && selected
        setupCard.visibility = if (ready) View.GONE else View.VISIBLE
        setupReady.visibility = if (ready) View.VISIBLE else View.GONE
        if (ready) return
        showStep(step1Row, step1Badge, step1Button, R.string.setup_step1_number, R.string.setup_step1, done = enabled, available = true)
        showStep(step2Row, step2Badge, step2Button, R.string.setup_step2_number, R.string.setup_step2, done = selected, available = enabled)
    }

    private fun showStep(
        row: View,
        badge: TextView,
        button: Button,
        number: Int,
        label: Int,
        done: Boolean,
        available: Boolean,
    ) {
        badge.setText(if (done) R.string.setup_tick else number)
        badge.setBackgroundResource(if (done) R.drawable.app_step_done else R.drawable.app_step_todo)
        badge.setTextColor(getColor(if (done) R.color.on_primary else R.color.primary))
        button.visibility = if (done) View.GONE else View.VISIBLE
        button.isEnabled = available
        button.alpha = if (available) 1f else DISABLED_ALPHA
        row.contentDescription = getString(if (done) R.string.setup_step_done else R.string.setup_step_todo, getString(label))
    }

    private fun openKeyboardSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            toast(R.string.setup_no_settings)
        }
    }

    private fun showKeyboardPicker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    // Shortcut list

    private fun refreshShortcuts() {
        val shortcuts = ShortcutRepository.all(this)
        val now = LocalDateTime.now()
        val locale = Locale.getDefault()
        shortcutList.removeAllViews()
        for (shortcut in shortcuts) shortcutList.addView(shortcutRow(shortcut, now, locale))
        emptyText.visibility = if (shortcuts.isEmpty()) View.VISIBLE else View.GONE

        val trigger = shortcuts.firstOrNull { it.trigger.isNotBlank() }?.trigger?.trim()
        tryTip.text = if (trigger != null) getString(R.string.try_tip_trigger, unbreakable(trigger)) else getString(R.string.try_tip)
    }

    private fun shortcutRow(shortcut: Shortcut, now: LocalDateTime, locale: Locale): View {
        val row = layoutInflater.inflate(R.layout.item_shortcut, shortcutList, false)
        val name = shortcut.name.ifBlank { getString(R.string.shortcut_no_name) }
        val preview = ShortcutPreview.render(shortcut, now, locale).replace('\n', ' ')
        row.findViewById<TextView>(R.id.shortcut_name).text = name
        row.findViewById<TextView>(R.id.shortcut_preview).text = preview
        row.findViewById<TextView>(R.id.shortcut_trigger).apply {
            text = shortcut.trigger.trim()
            visibility = if (shortcut.trigger.isBlank()) View.GONE else View.VISIBLE
        }
        row.contentDescription = getString(R.string.shortcut_row_description, name, preview)
        row.setOnClickListener { openEditor(shortcut.id) }
        return row
    }

    /** Stops a line break inside a word like "/meds" (Android may break after the slash). */
    private fun unbreakable(word: String): String = word.toCharArray().joinToString(WORD_JOINER)

    private fun openEditor(id: String?) {
        val intent = Intent(this, EditShortcutActivity::class.java)
        if (id != null) intent.putExtra(EditShortcutActivity.EXTRA_ID, id)
        startActivity(intent)
    }

    // Backup and restore

    private fun startBackup() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(BACKUP_MIME_TYPE)
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
            toast(R.string.no_file_app)
        }
    }

    private fun writeBackup(uri: Uri) {
        val shortcuts = ShortcutRepository.all(this)
        val json = ShortcutJson.toJson(shortcuts)
        val resolver = applicationContext.contentResolver
        val appContext = applicationContext
        inBackground(work = { writeText(resolver, uri, json) }) { result ->
            // Shown even if this screen was closed meanwhile, so the user always hears back.
            val message = if (result.isSuccess) {
                appContext.resources.getQuantityString(R.plurals.backup_saved, shortcuts.size, shortcuts.size)
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
        inBackground(work = { parseBackup(resolver, uri) }) { result ->
            // If the screen was rotated meanwhile, the new one reads the file again.
            if (isDestroyed) return@inBackground
            when (val outcome = result.getOrElse { RestoreOutcome.ReadFailed }) {
                is RestoreOutcome.Found -> askHowToRestore(outcome.shortcuts)
                RestoreOutcome.Empty -> restoreFinished(R.string.restore_empty)
                RestoreOutcome.NotABackup -> restoreFinished(R.string.restore_bad_file)
                RestoreOutcome.ReadFailed -> restoreFinished(R.string.restore_failed)
            }
        }
    }

    private fun restoreFinished(message: Int) {
        pendingRestore = null
        toast(message)
    }

    private fun askHowToRestore(shortcuts: List<Shortcut>) {
        val count = shortcuts.size
        val ask = AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.restore_found, count, count))
            .setMessage(R.string.restore_message)
            .setPositiveButton(R.string.restore_add) { _, _ -> applyRestore(shortcuts, replace = false) }
            .setNegativeButton(R.string.restore_replace) { _, _ -> applyRestore(shortcuts, replace = true) }
            .setNeutralButton(R.string.dialog_cancel) { _, _ -> pendingRestore = null }
            .setOnCancelListener { pendingRestore = null }
            .create()
        showDialog(ask)
    }

    private fun applyRestore(shortcuts: List<Shortcut>, replace: Boolean) {
        pendingRestore = null
        try {
            if (replace) ShortcutRepository.replaceAll(this, shortcuts) else ShortcutRepository.merge(this, shortcuts)
        } catch (e: Exception) {
            Log.w(TAG, "Restore failed", e)
            toast(R.string.restore_save_failed)
            return
        }
        val total = ShortcutRepository.all(this).size
        toast(
            if (replace) {
                resources.getQuantityString(R.plurals.restore_replaced, total, total)
            } else {
                resources.getQuantityString(R.plurals.restore_added, shortcuts.size, shortcuts.size)
            },
        )
        refreshShortcuts()
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

    // Helpers

    private fun showDialog(newDialog: Dialog) {
        dialog?.dismiss()
        dialog = newDialog
        showReadable(newDialog)
    }

    private fun toast(message: Int) = toast(getString(message))

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** What reading a backup file found. */
    private sealed interface RestoreOutcome {
        class Found(val shortcuts: List<Shortcut>) : RestoreOutcome
        data object Empty : RestoreOutcome
        data object NotABackup : RestoreOutcome
        data object ReadFailed : RestoreOutcome
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_BACKUP = 1
        private const val REQUEST_RESTORE = 2
        private const val STATE_PENDING_RESTORE = "pendingRestore"
        private const val DISABLED_ALPHA = 0.45f
        private const val WORD_JOINER = "\u2060"
        private const val BACKUP_MIME_TYPE = "application/json"
        private val RESTORE_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")

        /** Far bigger than any real backup; stops a wrong file from using up memory. */
        private const val MAX_BACKUP_BYTES = 5 * 1024 * 1024

        private fun writeText(resolver: ContentResolver, uri: Uri, text: String) {
            // "wt" empties the file first; a few file providers only accept "w".
            val stream: OutputStream = try {
                resolver.openOutputStream(uri, "wt")
            } catch (e: Exception) {
                null
            } ?: resolver.openOutputStream(uri, "w") ?: throw IOException("Could not open $uri")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }

        private fun readText(resolver: ContentResolver, uri: Uri): String {
            val stream = resolver.openInputStream(uri) ?: throw IOException("Could not open $uri")
            val bytes = ByteArrayOutputStream()
            stream.use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (bytes.size() + read > MAX_BACKUP_BYTES) throw IOException("File too big")
                    bytes.write(buffer, 0, read)
                }
            }
            return String(bytes.toByteArray(), Charsets.UTF_8).removePrefix("﻿")
        }

        private fun parseBackup(resolver: ContentResolver, uri: Uri): RestoreOutcome {
            val text = try {
                readText(resolver, uri)
            } catch (e: Exception) {
                Log.w(TAG, "Could not read backup", e)
                return RestoreOutcome.ReadFailed
            }
            val shortcuts = try {
                ShortcutJson.fromJson(text)
            } catch (e: Exception) {
                Log.w(TAG, "Not a backup file", e)
                return RestoreOutcome.NotABackup
            }
            // A file with the same id twice would otherwise add both copies.
            val unique = shortcuts.asReversed().distinctBy { it.id }.asReversed()
            return if (unique.isEmpty()) RestoreOutcome.Empty else RestoreOutcome.Found(unique)
        }
    }
}
