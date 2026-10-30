package com.dzirent.app.data

import android.content.Context
import android.net.Uri
import com.dzirent.app.data.model.Availability
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.ListingMeasurement
import com.dzirent.app.data.model.ListingPhoto
import com.dzirent.app.data.repository.DzirentException
import com.dzirent.app.data.repository.DzirentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A photo in the editor: one already saved, or a new one picked from the gallery or camera. */
sealed class PhotoItem {
    abstract val key: String

    data class Existing(val photo: ListingPhoto) : PhotoItem() {
        override val key get() = photo.id
    }

    data class New(val uri: Uri) : PhotoItem() {
        override val key get() = uri.toString()
    }
}

/**
 * Everything the editor saves. [listing] carries the id, owner, location and text fields;
 * [photos] are in display order, with their slot keys ([slots], same order). With
 * [listingDeclarationVersion] set (accessories), the per-listing declaration is recorded too.
 */
data class ListingDraft(
    val listing: Listing,
    val photos: List<PhotoItem>,
    val measurements: List<ListingMeasurement>,
    val availability: List<Availability>,
    val slots: List<String?> = photos.map { null },
    val listingDeclarationVersion: Int? = null
)

/** Thrown when a publish step fails; [step] names it for the Retry screen. */
class PublishStepException(val step: String, cause: Throwable) :
    Exception(if (cause is DzirentException) cause.message else "Something went wrong. Please try again.", cause)

/**
 * Saves a new or edited listing so it is never left half-built and active:
 * 1. save details with status 'paused', 2. remove deleted photos, 3. upload new photos,
 * 4. save photo order, 5. replace measurements and availability, 6. set the final status.
 *
 * One instance per editor session. Completed steps are remembered, so calling [publish]
 * again after a failure resumes from the step that failed.
 */
class ListingPublisher(
    private val repository: DzirentRepository,
    context: Context,
    /** Null for a new listing. */
    private val original: Listing?
) {
    private val appContext = context.applicationContext
    private var listingCreated = original != null
    private val deletedPhotoIds = mutableSetOf<String>()
    private val uploaded = mutableMapOf<String, ListingPhoto>() // PhotoItem.New key -> saved photo
    private val savedPositions = mutableMapOf<String, Pair<Int, String?>>() // photo id -> (position, slot) in DB
    private var declarationRecorded = false

    init {
        original?.photos?.forEach { savedPositions[it.id] = it.position to it.slot }
    }

    /**
     * New listings end up 'active'. Edited listings return to the status they had, so an
     * active listing is paused only while its changes are being saved.
     */
    suspend fun publish(draft: ListingDraft, onProgress: (String) -> Unit) {
        val listing = draft.listing
        val finalStatus = original?.status ?: "active"

        step("Saving listing details", onProgress) {
            if (!listingCreated) {
                repository.createListing(listing.copy(status = "paused"))
                listingCreated = true
            } else {
                repository.updateListingDetails(listing, status = "paused")
            }
        }

        // Photos removed in the editor: saved ones, plus any uploaded on an earlier attempt.
        val keys = draft.photos.map { it.key }.toSet()
        val keptExistingIds = draft.photos.filterIsInstance<PhotoItem.Existing>().map { it.photo.id }.toSet()
        val toDelete = original?.photos.orEmpty().filter { it.id !in keptExistingIds && it.id !in deletedPhotoIds } +
            uploaded.filterKeys { it !in keys }.values
        if (toDelete.isNotEmpty()) {
            step("Removing photos", onProgress) {
                toDelete.forEach { photo ->
                    repository.deleteListingPhoto(photo)
                    deletedPhotoIds += photo.id
                    savedPositions.remove(photo.id)
                    uploaded.values.remove(photo)
                }
            }
        }

        val newPhotos = draft.photos.withIndex().filter { it.value is PhotoItem.New && it.value.key !in uploaded }
        newPhotos.forEachIndexed { i, (position, item) ->
            step("Uploading photo ${i + 1} of ${newPhotos.size}", onProgress) {
                val bytes = readBytes((item as PhotoItem.New).uri)
                val slot = draft.slots.getOrNull(position)
                val photo = repository.uploadListingPhoto(listing.lenderId, listing.id, bytes, position, slot)
                uploaded[item.key] = photo
                savedPositions[photo.id] = position to slot
            }
        }

        step("Saving photo order", onProgress) {
            draft.photos.forEachIndexed { position, item ->
                val photoId = when (item) {
                    is PhotoItem.Existing -> item.photo.id
                    is PhotoItem.New -> uploaded.getValue(item.key).id
                }
                val place = position to draft.slots.getOrNull(position)
                if (savedPositions[photoId] != place) {
                    repository.updatePhotoPosition(photoId, place.first, place.second)
                    savedPositions[photoId] = place
                }
            }
        }

        step("Saving measurements and dates", onProgress) {
            repository.replaceMeasurements(listing.id, draft.measurements)
            repository.replaceAvailability(listing.id, draft.availability)
        }

        val declarationVersion = draft.listingDeclarationVersion
        if (declarationVersion != null && !declarationRecorded) {
            step("Saving the declaration", onProgress) {
                repository.recordListingDeclaration(listing.id, declarationVersion)
                declarationRecorded = true
            }
        }

        step(if (finalStatus == "active") "Publishing" else "Finishing up", onProgress) {
            repository.toggleListingStatus(listing.id, finalStatus)
        }

        clearCameraCache(appContext)
    }

    private suspend fun step(name: String, onProgress: (String) -> Unit, block: suspend () -> Unit) {
        onProgress("$name...")
        try {
            block()
        } catch (e: Exception) {
            throw PublishStepException(name, e)
        }
    }

    private suspend fun readBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: throw DzirentException("One of the photos couldn't be read. Remove it or pick a different one.")
    }

    companion object {
        /** Folder for camera captures, shared with FileProvider (res/xml/file_paths.xml). */
        fun cameraDir(context: Context): File = File(context.cacheDir, "camera").apply { mkdirs() }

        fun clearCameraCache(context: Context) {
            cameraDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
