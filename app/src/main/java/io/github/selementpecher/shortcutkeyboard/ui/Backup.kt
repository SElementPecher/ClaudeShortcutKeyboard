package io.github.selementpecher.shortcutkeyboard.ui

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import io.github.selementpecher.shortcutkeyboard.data.ShortcutJson
import io.github.selementpecher.shortcutkeyboard.model.Library
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/** What reading a backup file found. */
internal sealed interface RestoreOutcome {
    class Found(val library: Library) : RestoreOutcome
    data object Empty : RestoreOutcome
    data object NotABackup : RestoreOutcome
    data object ReadFailed : RestoreOutcome
}

/** Reading and writing backup files. Both block, so call them off the main thread. */
internal object Backup {
    private const val TAG = "Backup"

    /** Far bigger than any real backup; stops a wrong file from using up memory. */
    private const val MAX_BYTES = 5 * 1024 * 1024

    fun write(resolver: ContentResolver, uri: Uri, library: Library) {
        val text = ShortcutJson.toJson(library)
        // "wt" empties the file first; a few file providers only accept "w".
        val stream: OutputStream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: Exception) {
            null
        } ?: resolver.openOutputStream(uri, "w") ?: throw IOException("Could not open $uri")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun read(resolver: ContentResolver, uri: Uri): RestoreOutcome {
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

    private fun readText(resolver: ContentResolver, uri: Uri): String {
        val stream = resolver.openInputStream(uri) ?: throw IOException("Could not open $uri")
        val bytes = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (bytes.size() + read > MAX_BYTES) throw IOException("File too big")
                bytes.write(buffer, 0, read)
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8).removePrefix("﻿")
    }
}
