package com.dzirent.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The app's own copies of the photos in a listing being made, one folder per user. Camera and
 * gallery photos both end up here as plain JPEG files (EXIF kept) before anything else reads
 * them, so they go through exactly the same checks and suggestions, and a draft's photos are
 * still readable after the app restarts (Photo Picker access doesn't last).
 */
class PhotoStore(context: Context, userId: String) {
    private val dir = File(context.applicationContext.filesDir, "listing_photos/${safe(userId)}").apply { mkdirs() }
    private val resolver = context.applicationContext.contentResolver

    /** Copies a gallery photo in. Null if it can't be read. */
    suspend fun copyIn(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        try {
            val copied = resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            if (copied == null || file.length() == 0L) {
                file.delete()
                null
            } else {
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    /** Moves a camera capture (cacheDir/camera, written through the FileProvider) in. Null if it's empty. */
    suspend fun moveIn(capture: File): Uri? = withContext(Dispatchers.IO) {
        if (!capture.isFile || capture.length() == 0L) {
            capture.delete()
            return@withContext null
        }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        if (!capture.renameTo(file)) {
            capture.copyTo(file, overwrite = true)
            capture.delete()
        }
        Uri.fromFile(file)
    }

    /** Deletes every copy except [keep] (photo URIs still in use). */
    fun prune(keep: Set<String>) {
        dir.listFiles()?.forEach { if (Uri.fromFile(it).toString() !in keep) it.delete() }
    }

    /** Deletes the copies at these URIs (photos now uploaded). */
    fun delete(uris: Collection<String>) {
        dir.listFiles()?.forEach { if (Uri.fromFile(it).toString() in uris) it.delete() }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' }.ifBlank { "anonymous" }
    }
}
