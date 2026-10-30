package com.dzirent.app.data

import android.content.Context
import com.dzirent.app.util.SavedDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The draft of one user's new listing ([listingId] null) or of one listing being edited, saved
 * on the phone as it changes. Another account signing in on the same phone never sees it.
 */
class DraftStore(context: Context, userId: String, listingId: String? = null) {
    private val name = listOfNotNull(userId, listingId?.let { "edit_$it" }).joinToString("_") { id ->
        id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
    private val file = File(context.applicationContext.filesDir, "drafts/$name.json")

    suspend fun load(): SavedDraft? = withContext(Dispatchers.IO) {
        if (file.isFile) SavedDraft.decode(runCatching { file.readText() }.getOrNull()) else null
    }

    suspend fun save(draft: SavedDraft) = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            // Write then rename, so a crash mid-write never leaves half a draft.
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(SavedDraft.encode(draft))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}

/** Remembers on this phone which users signed the current accessory declaration, so it isn't fetched every time. */
class DeclarationCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dzirent_prefs", Context.MODE_PRIVATE)

    fun isSigned(userId: String, version: Int): Boolean = prefs.getBoolean(key(userId, version), false)

    fun markSigned(userId: String, version: Int) {
        prefs.edit().putBoolean(key(userId, version), true).apply()
    }

    private fun key(userId: String, version: Int) = "accessory_declaration_${version}_$userId"
}
