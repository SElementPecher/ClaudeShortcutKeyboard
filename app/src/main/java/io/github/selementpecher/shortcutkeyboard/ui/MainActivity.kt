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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import io.github.selementpecher.shortcutkeyboard.R
import io.github.selementpecher.shortcutkeyboard.data.ShortcutJson
import io.github.selementpecher.shortcutkeyboard.data.ShortcutRepository
import io.github.selementpecher.shortcutkeyboard.model.Folder
import io.github.selementpecher.shortcutkeyboard.model.Library
import io.github.selementpecher.shortcutkeyboard.model.Node
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
 * Shows the two setup steps (with ticks that update live), a box to try the keyboard, the
 * shortcuts and folders (one folder level at a time), and backup / restore through the system
 * file picker.
 *
 * Start with [EXTRA_FOLDER_ID] to open inside a folder.
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
    private lateinit var tipText: View
    private lateinit var folderHeader: View
    private lateinit var folderBack: TextView
    private lateinit var folderPath: TextView

    /** The folder being shown (null = top level, called "Home"). */
    private var currentFolderId: String? = null

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
        tipText = findViewById(R.id.shortcuts_tip)
        folderHeader = findViewById(R.id.folder_header)
        folderBack = findViewById(R.id.folder_back)
        folderPath = findViewById(R.id.folder_path)
        currentFolderId = if (savedInstanceState != null) {
            savedInstanceState.getString(STATE_FOLDER)
        } else {
            intent?.getStringExtra(EXTRA_FOLDER_ID)
        }

        step1Button.setOnClickListener { openKeyboardSettings() }
        step2Button.setOnClickListener { showKeyboardPicker() }
        findViewById<View>(R.id.new_shortcut_button).setOnClickListener { openEditor(null) }
        findViewById<View>(R.id.new_folder_button).setOnClickListener { askFolderName(null) }
        folderBack.setOnClickListener { goUp() }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Singleton: the keyboard opens us again, maybe for another folder.
        if (intent.hasExtra(EXTRA_FOLDER_ID)) {
            currentFolderId = intent.getStringExtra(EXTRA_FOLDER_ID)
            if (!isDestroyed) refreshShortcuts()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back goes up a folder first, then leaves.
        if (currentFolderId != null) goUp() else super.onBackPressed()
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
        currentFolderId?.let { outState.putString(STATE_FOLDER, it) }
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
        val library = ShortcutRepository.library(this)
        // A folder deleted elsewhere (e.g. from the keyboard): fall back to the top level.
        currentFolderId = FolderLogic.existingFolder(library, currentFolderId)
        val children = library.children(currentFolderId)
        val now = LocalDateTime.now()
        val locale = Locale.getDefault()
        shortcutList.removeAllViews()
        for (node in children) {
            when (node) {
                is Folder -> shortcutList.addView(folderRow(node, library))
                is Shortcut -> shortcutList.addView(shortcutRow(node, now, locale))
            }
        }
        val inFolder = currentFolderId != null
        emptyText.setText(if (inFolder) R.string.folder_empty else R.string.shortcuts_empty)
        emptyText.visibility = if (children.isEmpty()) View.VISIBLE else View.GONE
        tipText.visibility = if (children.isEmpty()) View.GONE else View.VISIBLE
        showHeader(library)
        tryTip.setText(R.string.try_tip)
    }

    /** The back row and the "Home › Kids" line, only inside a folder. */
    private fun showHeader(library: Library) {
        val folder = library.folder(currentFolderId)
        folderHeader.visibility = if (folder == null) View.GONE else View.VISIBLE
        if (folder == null) return
        val home = getString(R.string.home_name)
        val noName = getString(R.string.shortcut_no_name)
        val parentName = library.folder(folder.parentId)?.name?.ifBlank { noName } ?: home
        folderBack.text = getString(R.string.folder_back, parentName)
        folderBack.contentDescription = getString(R.string.folder_back_description, parentName)
        folderPath.text = FolderLogic.pathText(library, currentFolderId, home, noName)
    }

    private fun goUp() {
        val library = ShortcutRepository.library(this)
        currentFolderId = library.folder(currentFolderId)?.parentId
        refreshShortcuts()
        findViewById<View>(R.id.main_scroll).scrollTo(0, 0)
    }

    private fun openFolder(id: String) {
        currentFolderId = id
        refreshShortcuts()
        findViewById<View>(R.id.main_scroll).scrollTo(0, 0)
    }

    private fun shortcutRow(shortcut: Shortcut, now: LocalDateTime, locale: Locale): View {
        val row = layoutInflater.inflate(R.layout.item_shortcut, shortcutList, false)
        val name = shortcut.name.ifBlank { getString(R.string.shortcut_no_name) }
        val preview = ShortcutPreview.render(shortcut, now, locale).replace('\n', ' ')
        row.findViewById<TextView>(R.id.shortcut_name).text = name
        row.findViewById<TextView>(R.id.shortcut_preview).text = preview
        row.contentDescription = getString(R.string.shortcut_row_description, name, preview)
        row.setOnClickListener { openEditor(shortcut.id) }
        row.setOnLongClickListener {
            showRowMenu(shortcut)
            true
        }
        return row
    }

    private fun folderRow(folder: Folder, library: Library): View {
        val row = layoutInflater.inflate(R.layout.item_shortcut, shortcutList, false)
        val name = folder.name.ifBlank { getString(R.string.shortcut_no_name) }
        val count = FolderLogic.itemCount(library, folder.id)
        val items = if (count == 0) getString(R.string.folder_empty_row) else resources.getQuantityString(R.plurals.folder_items, count, count)
        row.findViewById<TextView>(R.id.shortcut_name).text = getString(R.string.folder_row_name, name)
        row.findViewById<TextView>(R.id.shortcut_preview).text = items
        row.contentDescription = getString(R.string.folder_row_description, name, items)
        row.setOnClickListener { openFolder(folder.id) }
        row.setOnLongClickListener {
            showRowMenu(folder)
            true
        }
        return row
    }

    // Folder and shortcut actions

    /** Press and hold: rename (folders only), move, delete. */
    private fun showRowMenu(node: Node) {
        val name = node.name.ifBlank { getString(R.string.shortcut_no_name) }
        val actions = ArrayList<Pair<String, () -> Unit>>()
        if (node is Folder) actions += getString(R.string.row_rename) to { askFolderName(node) }
        actions += getString(R.string.row_move) to { showMoveDialog(node) }
        actions += getString(R.string.row_delete) to { confirmDelete(node) }
        showDialog(
            AlertDialog.Builder(this)
                .setTitle(if (node is Folder) getString(R.string.folder_row_name, name) else name)
                .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
                .setNegativeButton(R.string.dialog_cancel, null)
                .create(),
        )
    }

    /** A box for the name of a new folder ([existing] = null) or to rename one. */
    private fun askFolderName(existing: Folder?) {
        val view = layoutInflater.inflate(R.layout.dialog_folder_name, null)
        val field = view.findViewById<EditText>(R.id.folder_name_field)
        val error = view.findViewById<TextView>(R.id.folder_name_error)
        existing?.let {
            field.setText(it.name)
            field.setSelection(field.length())
        }
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                error.visibility = View.GONE
            }
        })
        val ask = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.folder_new_title else R.string.folder_rename_title)
            .setView(view)
            .setPositiveButton(if (existing == null) R.string.folder_create else R.string.folder_rename_save, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        ask.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        showDialog(ask)
        // Set after show() so an empty name keeps the box open instead of closing it.
        ask.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = field.text.toString().trim()
            if (name.isEmpty()) {
                error.visibility = View.VISIBLE
                field.requestFocus()
            } else if (saveFolder(existing, name)) {
                ask.dismiss()
            }
        }
        field.requestFocus()
    }

    private fun saveFolder(existing: Folder?, name: String): Boolean {
        try {
            if (existing == null) {
                ShortcutRepository.saveFolder(this, Folder(ShortcutRepository.newId(), name, currentFolderId))
            } else {
                // Use the stored copy, in case it was moved meanwhile.
                val latest = ShortcutRepository.findFolder(this, existing.id) ?: existing
                ShortcutRepository.saveFolder(this, latest.copy(name = name))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not save folder", e)
            toast(R.string.folder_save_failed)
            return false
        }
        refreshShortcuts()
        return true
    }

    private fun showMoveDialog(node: Node) {
        val library = ShortcutRepository.library(this)
        val targets = FolderLogic.moveTargets(library, node.id, getString(R.string.home_name))
        val noName = getString(R.string.shortcut_no_name)
        val labels = targets.map { target ->
            val base = target.name.ifBlank { noName }.let { if (target.folderId == null) it else getString(R.string.folder_row_name, it) }
            val text = if (target.isCurrent) getString(R.string.move_here, base) else base
            INDENT.repeat(target.depth) + text
        }
        showDialog(
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.move_title, node.name.ifBlank { noName }))
                .setItems(labels.toTypedArray()) { _, which ->
                    val target = targets[which]
                    if (!target.isCurrent) moveTo(node, target.folderId)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .create(),
        )
    }

    private fun moveTo(node: Node, folderId: String?) {
        val moved = try {
            ShortcutRepository.move(this, node.id, folderId, Int.MAX_VALUE)
        } catch (e: Exception) {
            Log.w(TAG, "Could not move", e)
            false
        }
        if (!moved) toast(R.string.move_failed)
        refreshShortcuts()
    }

    private fun confirmDelete(node: Node) {
        val name = node.name.ifBlank { getString(R.string.shortcut_no_name) }
        val isFolder = node is Folder
        showDialog(
            AlertDialog.Builder(this)
                .setTitle(if (isFolder) getString(R.string.folder_delete_title, name) else getString(R.string.delete_title, name))
                .setMessage(if (isFolder) R.string.folder_delete_message else R.string.delete_message)
                .setPositiveButton(R.string.delete_confirm) { _, _ -> delete(node) }
                .setNegativeButton(R.string.dialog_cancel, null)
                .create(),
        )
    }

    private fun delete(node: Node) {
        try {
            if (node is Folder) ShortcutRepository.deleteFolder(this, node.id) else ShortcutRepository.delete(this, node.id)
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete", e)
            toast(R.string.edit_save_failed)
            return
        }
        toast(if (node is Folder) R.string.folder_deleted else R.string.shortcut_deleted)
        refreshShortcuts()
    }

    private fun openEditor(id: String?) {
        val intent = Intent(this, EditShortcutActivity::class.java)
        if (id != null) intent.putExtra(EditShortcutActivity.EXTRA_ID, id)
        else currentFolderId?.let { intent.putExtra(EditShortcutActivity.EXTRA_PARENT_ID, it) }
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
        val library = ShortcutRepository.library(this)
        val shortcuts = library.shortcuts
        val json = ShortcutJson.toJson(library)
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
                is RestoreOutcome.Found -> askHowToRestore(outcome.library)
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

    private fun askHowToRestore(library: Library) {
        val count = library.shortcuts.size
        val ask = AlertDialog.Builder(this)
            .setTitle(resources.getQuantityString(R.plurals.restore_found, count, count))
            .setMessage(R.string.restore_message)
            .setPositiveButton(R.string.restore_add) { _, _ -> applyRestore(library, replace = false) }
            .setNegativeButton(R.string.restore_replace) { _, _ -> applyRestore(library, replace = true) }
            .setNeutralButton(R.string.dialog_cancel) { _, _ -> pendingRestore = null }
            .setOnCancelListener { pendingRestore = null }
            .create()
        showDialog(ask)
    }

    private fun applyRestore(library: Library, replace: Boolean) {
        pendingRestore = null
        try {
            if (replace) ShortcutRepository.replaceAll(this, library) else ShortcutRepository.merge(this, library)
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
                resources.getQuantityString(R.plurals.restore_added, library.shortcuts.size, library.shortcuts.size)
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
        class Found(val library: Library) : RestoreOutcome
        data object Empty : RestoreOutcome
        data object NotABackup : RestoreOutcome
        data object ReadFailed : RestoreOutcome
    }

    companion object {
        /** The id of the folder to open. Leave it out for the top level. */
        const val EXTRA_FOLDER_ID = "folder_id"

        private const val TAG = "MainActivity"
        private const val REQUEST_BACKUP = 1
        private const val REQUEST_RESTORE = 2
        private const val STATE_PENDING_RESTORE = "pendingRestore"
        private const val STATE_FOLDER = "folder"
        private const val DISABLED_ALPHA = 0.45f
        private const val INDENT = "\u2003"
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
            val library = try {
                FolderLogic.withoutRepeats(ShortcutJson.libraryFromJson(text))
            } catch (e: Exception) {
                Log.w(TAG, "Not a backup file", e)
                return RestoreOutcome.NotABackup
            }
            return if (FolderLogic.isEmpty(library)) RestoreOutcome.Empty else RestoreOutcome.Found(library)
        }
    }
}
