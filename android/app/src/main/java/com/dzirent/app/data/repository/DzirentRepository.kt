package com.dzirent.app.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import com.dzirent.app.data.SupabaseConfig
import com.dzirent.app.data.model.*
import com.dzirent.app.util.haversineKm
import com.dzirent.app.util.roundCoord
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.*

/**
 * An error with a short, user-safe message. Raw Supabase errors can contain request
 * URLs, headers and login tokens, so they are never shown on screen.
 */
class DzirentException(message: String) : Exception(message)

/** Embedded select for a listing with all of its child rows and the lender's public profile. */
private const val LISTING_WITH_CHILDREN =
    "*, photos:listing_photos(*), measurements:listing_measurements(*), availability:availability(*), " +
        "lender:profiles!listings_lender_id_fkey(id, display_name, city)"

@Serializable
private data class ListingIdRow(@SerialName("listing_id") val listingId: String)

/** Photos in display order, ranges by start date. */
private fun Listing.sorted(): Listing = copy(
    photos = photos.sortedBy { it.position },
    availability = availability.sortedBy { it.startDate }
)

class DzirentRepository {

    private val auth = SupabaseConfig.auth
    private val postgrest = SupabaseConfig.postgrest
    private val storage = SupabaseConfig.storage
    private val realtime = SupabaseConfig.realtime

    private fun logError(operation: String, throwable: Throwable) {
        // First line only: later lines of Supabase errors include URLs and auth headers.
        val firstLine = throwable.message?.lineSequence()?.firstOrNull().orEmpty()
        Log.e("Dzirent", "Supabase error during $operation: ${throwable.javaClass.simpleName} - $firstLine")
    }

    /** Logs the technical error and returns a friendly exception for the UI. */
    private fun fail(operation: String, throwable: Throwable): DzirentException {
        if (throwable is DzirentException) return throwable
        logError(operation, throwable)
        val raw = throwable.message.orEmpty().lowercase()
        val message = when {
            "invalid login credentials" in raw -> "Email or password is incorrect."
            "email not confirmed" in raw -> "Please confirm your email first. Check your inbox for the link."
            "already registered" in raw || "already been registered" in raw || "user_already_exists" in raw ->
                "An account with this email already exists. Try logging in."
            "password should" in raw || "weak_password" in raw ->
                "That password is too weak. Use at least 6 characters."
            "rate limit" in raw || "too many requests" in raw ->
                "Too many attempts. Please try again later."
            "unable to resolve host" in raw || "failed to connect" in raw || "timeout" in raw || "timed out" in raw ->
                "Can't reach the server. Check your internet connection and try again."
            "row-level security" in raw || "permission denied" in raw || "jwt" in raw ->
                "Your session may have expired. Please log out and log in again."
            else -> "Couldn't $operation. Please try again."
        }
        return DzirentException(message)
    }

    // --- AUTH ---

    /**
     * Creates the account. A database trigger creates the profiles row from the metadata
     * below, so the app never inserts into profiles itself.
     *
     * Returns null when Supabase requires email confirmation (no session yet). The caller
     * should tell the user to confirm their email and then log in.
     */
    suspend fun signUp(
        emailStr: String,
        passwordStr: String,
        displayName: String,
        role: String,
        adultConfirmed: Boolean,
        city: String,
        lat: Double,
        lng: Double
    ): Profile? {
        try {
            auth.signUpWith(Email) {
                email = emailStr
                password = passwordStr
                data = buildJsonObject {
                    put("display_name", displayName)
                    put("role", role)
                    put("adult_confirmed", adultConfirmed)
                    put("city", city)
                }
            }
        } catch (e: Exception) {
            throw fail("create your account", e)
        }

        // No session = email confirmation is on. The profile row already exists via the trigger.
        if (auth.currentSessionOrNull() == null) return null
        val userId = auth.currentUserOrNull()?.id ?: return null

        return try {
            postgrest.from("profiles").update(
                buildJsonObject {
                    put("city", city)
                    if (lat != 0.0 || lng != 0.0) {
                        put("approx_lat", roundCoord(lat))
                        put("approx_lng", roundCoord(lng))
                    }
                }
            ) {
                filter { eq("id", userId) }
            }
            getCurrentProfile()
        } catch (e: Exception) {
            throw fail("finish setting up your profile", e)
        }
    }

    suspend fun signIn(emailStr: String, passwordStr: String) {
        try {
            auth.signInWith(Email) {
                email = emailStr
                password = passwordStr
            }
        } catch (e: Exception) {
            throw fail("log in", e)
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            // The server call failed (e.g. offline); still forget the session on this device.
            logError("log out", e)
            try {
                auth.clearSession()
            } catch (clearEx: Exception) {
                throw fail("log out", clearEx)
            }
        }
    }

    suspend fun resetPassword(emailStr: String) {
        try {
            auth.resetPasswordForEmail(emailStr)
        } catch (e: Exception) {
            throw fail("send the reset email", e)
        }
    }

    suspend fun getCurrentProfile(): Profile? {
        val uid = auth.currentUserOrNull()?.id ?: return null
        return try {
            postgrest.from("profiles")
                .select { filter { eq("id", uid) } }
                .decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            throw fail("load your profile", e)
        }
    }

    suspend fun updateProfile(profile: Profile) {
        try {
            postgrest.from("profiles").update(profile) {
                filter { eq("id", profile.id) }
            }
        } catch (e: Exception) {
            throw fail("save your profile", e)
        }
    }

    /** Changes only the role column, so other profile fields are never overwritten. */
    suspend fun updateRole(userId: String, role: String): Profile {
        try {
            postgrest.from("profiles").update(buildJsonObject { put("role", role) }) {
                filter { eq("id", userId) }
            }
            return getCurrentProfile() ?: throw DzirentException("Couldn't load your profile. Please try again.")
        } catch (e: Exception) {
            throw fail("change what you want to do", e)
        }
    }

    /** Saves my approximate location (rounded to 2 decimals) to my own profile row. */
    suspend fun updateLocation(userId: String, lat: Double, lng: Double, city: String): Profile {
        try {
            postgrest.from("profiles").update(
                buildJsonObject {
                    put("approx_lat", roundCoord(lat))
                    put("approx_lng", roundCoord(lng))
                    put("city", city)
                }
            ) {
                filter { eq("id", userId) }
            }
            return getCurrentProfile() ?: throw DzirentException("Couldn't load your profile. Please try again.")
        } catch (e: Exception) {
            throw fail("save your location", e)
        }
    }

    /** Users I blocked plus users who blocked me (ids only). */
    suspend fun getBlockPeerIds(): Set<String> {
        return try {
            postgrest.rpc("my_block_peer_ids").decodeList<String>().toSet()
        } catch (e: Exception) {
            throw fail("load your blocked users", e)
        }
    }

    // --- LISTINGS & NEARBY SEARCH ---

    /**
     * Active listings within [radiusKm] of the given point. The listings_nearby RPC hides
     * blocked users itself; the fallback query applies blocks and the radius in the app.
     */
    suspend fun getNearbyListings(
        lat: Double,
        lng: Double,
        radiusKm: Double = 25.0,
        category: String? = null
    ): List<Listing> {
        val rpcParams = buildJsonObject {
            put("p_lat", lat)
            put("p_lng", lng)
            put("p_radius_km", radiusKm)
            category?.let { put("p_category", it) }
        }

        val listings = try {
            postgrest.rpc("listings_nearby", rpcParams).decodeList<Listing>()
        } catch (e: Exception) {
            logError("getNearbyListings RPC", e)
            try {
                val blockPeers = getBlockPeerIds()
                postgrest.from("listings")
                    .select {
                        filter {
                            eq("status", "active")
                            category?.let { eq("category", it) }
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Listing>()
                    .filter { it.lenderId !in blockPeers }
            } catch (fallbackEx: Exception) {
                throw fail("load listings", fallbackEx)
            }
        }
        return listings
            .map { it.copy(distanceKm = haversineKm(lat, lng, it.approxLat, it.approxLng)) }
            .filter { (it.distanceKm ?: 0.0) <= radiusKm }
    }

    /** Listing with its photos, measurements, availability and lender, in one request. */
    suspend fun getListingDetail(id: String): Listing {
        return try {
            postgrest.from("listings").select(Columns.raw(LISTING_WITH_CHILDREN)) {
                filter { eq("id", id) }
            }.decodeSingle<Listing>().sorted()
        } catch (e: Exception) {
            throw fail("load this listing", e)
        }
    }

    /** My listings with their photos embedded (for thumbnails). */
    suspend fun getMyListings(lenderId: String): List<Listing> {
        return try {
            postgrest.from("listings").select(Columns.raw("*, photos:listing_photos(*)")) {
                filter { eq("lender_id", lenderId) }
                order("created_at", Order.DESCENDING)
            }.decodeList<Listing>().map { it.sorted() }
        } catch (e: Exception) {
            throw fail("load your closet", e)
        }
    }

    /** Ids of my listings that currently have an active rental. */
    suspend fun getRentedOutListingIds(lenderId: String): Set<String> {
        return try {
            postgrest.from("rentals").select(Columns.list("listing_id")) {
                filter {
                    eq("lender_id", lenderId)
                    eq("status", "active")
                }
            }.decodeList<ListingIdRow>().map { it.listingId }.toSet()
        } catch (e: Exception) {
            throw fail("load your closet", e)
        }
    }

    /** Inserts the listing row only. Callers insert it as 'paused' and activate it last. */
    suspend fun createListing(listing: Listing): Listing {
        return try {
            postgrest.from("listings").insert(listing)
            listing
        } catch (e: Exception) {
            throw fail("save your listing", e)
        }
    }

    /** Updates the fields the lender can edit (location and owner never change here). */
    suspend fun updateListingDetails(listing: Listing, status: String) {
        try {
            postgrest.from("listings").update(
                buildJsonObject {
                    put("title", listing.title)
                    put("category", listing.category)
                    put("size_label", listing.sizeLabel)
                    put("description", listing.description)
                    put("price_note", listing.priceNote)
                    put("retail_value", listing.retailValue)
                    put("deposit_note", listing.depositNote)
                    put("status", status)
                }
            ) {
                filter { eq("id", listing.id) }
            }
        } catch (e: Exception) {
            throw fail("save your listing", e)
        }
    }

    /** Replaces all measurements of a listing (safe to repeat on retry). */
    suspend fun replaceMeasurements(listingId: String, measurements: List<ListingMeasurement>) {
        try {
            postgrest.from("listing_measurements").delete { filter { eq("listing_id", listingId) } }
            if (measurements.isNotEmpty()) postgrest.from("listing_measurements").insert(measurements)
        } catch (e: Exception) {
            throw fail("save the measurements", e)
        }
    }

    /** Replaces all availability ranges of a listing (safe to repeat on retry). */
    suspend fun replaceAvailability(listingId: String, ranges: List<Availability>) {
        try {
            postgrest.from("availability").delete { filter { eq("listing_id", listingId) } }
            if (ranges.isNotEmpty()) postgrest.from("availability").insert(ranges)
        } catch (e: Exception) {
            throw fail("save the available dates", e)
        }
    }

    suspend fun toggleListingStatus(id: String, newStatus: String) {
        try {
            postgrest.from("listings").update(mapOf("status" to newStatus)) {
                filter { eq("id", id) }
            }
        } catch (e: Exception) {
            throw fail("update your listing", e)
        }
    }

    suspend fun deleteListing(listingId: String) {
        try {
            val photos = postgrest.from("listing_photos").select {
                filter { eq("listing_id", listingId) }
            }.decodeList<ListingPhoto>()

            if (photos.isNotEmpty()) {
                storage.from("listing-photos").delete(photos.map { objectPath(it) })
            }

            postgrest.from("listings").delete {
                filter { eq("id", listingId) }
            }
        } catch (e: Exception) {
            throw fail("delete your listing", e)
        }
    }

    // --- PHOTO UPLOAD & COMPRESSION ---
    suspend fun uploadListingPhoto(
        userId: String,
        listingId: String,
        imageBytes: ByteArray,
        position: Int
    ): ListingPhoto {
        val compressed = withContext(Dispatchers.Default) { compressImage(imageBytes) }
            ?: throw DzirentException("One of the photos couldn't be read. Remove it or pick a different one.")
        val photoUuid = UUID.randomUUID().toString()
        val path = "$userId/$listingId/$photoUuid.jpg"
        try {
            // Filenames are fresh UUIDs; upsert would need an UPDATE policy the bucket doesn't grant.
            storage.from("listing-photos").upload(path, compressed) {
                upsert = false
            }
        } catch (e: Exception) {
            throw fail("upload the photo", e)
        }

        val photo = ListingPhoto(
            id = photoUuid,
            listingId = listingId,
            storagePath = storage.from("listing-photos").publicUrl(path),
            position = position
        )
        try {
            postgrest.from("listing_photos").insert(photo)
        } catch (e: Exception) {
            // Don't leave an orphaned file behind if the row couldn't be saved.
            runCatching { storage.from("listing-photos").delete(listOf(path)) }
            throw fail("upload the photo", e)
        }
        return photo
    }

    suspend fun updatePhotoPosition(photoId: String, position: Int) {
        try {
            postgrest.from("listing_photos").update(buildJsonObject { put("position", position) }) {
                filter { eq("id", photoId) }
            }
        } catch (e: Exception) {
            throw fail("save the photo order", e)
        }
    }

    /** Deletes the photo row, then its file. A leftover file is only logged. */
    suspend fun deleteListingPhoto(photo: ListingPhoto) {
        try {
            postgrest.from("listing_photos").delete { filter { eq("id", photo.id) } }
        } catch (e: Exception) {
            throw fail("remove the photo", e)
        }
        try {
            storage.from("listing-photos").delete(listOf(objectPath(photo)))
        } catch (e: Exception) {
            logError("delete photo file", e)
        }
    }

    /** storage_path holds the full public URL; storage calls need the object path. */
    private fun objectPath(photo: ListingPhoto): String =
        photo.storagePath.substringAfter("/listing-photos/")

    /**
     * Downscales to at most 1600 px on the long side, applies the EXIF rotation (camera
     * photos are often stored sideways) and encodes as JPEG 80%. Null if not an image.
     */
    private fun compressImage(originalBytes: ByteArray): ByteArray? {
        val maxDim = 1600
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Decode at a power-of-two reduction first so large camera photos don't run out of memory.
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDim) sampleSize *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            originalBytes, 0, originalBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return null

        val width = bitmap.width
        val height = bitmap.height
        val ratio = min(1f, maxDim.toFloat() / max(width, height))
        val matrix = Matrix().apply {
            postScale(ratio, ratio)
            postRotate(exifRotationDegrees(originalBytes))
        }
        val output = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)

        val out = ByteArrayOutputStream()
        output.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    private fun exifRotationDegrees(bytes: ByteArray): Float = try {
        when (ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }

    // --- CHAT & REALTIME ---
    suspend fun getConversations(userId: String): List<Conversation> {
        return try {
            postgrest.from("conversations").select {
                filter {
                    or {
                        eq("renter_id", userId)
                        eq("lender_id", userId)
                    }
                }
                order("created_at", Order.DESCENDING)
            }.decodeList<Conversation>()
        } catch (e: Exception) {
            throw fail("load your messages", e)
        }
    }

    suspend fun getOrCreateConversation(listingId: String, renterId: String, lenderId: String): Conversation {
        return try {
            val existing = postgrest.from("conversations").select {
                filter {
                    eq("listing_id", listingId)
                    eq("renter_id", renterId)
                    eq("lender_id", lenderId)
                }
            }.decodeSingleOrNull<Conversation>()

            if (existing != null) return existing

            val newConv = Conversation(
                id = UUID.randomUUID().toString(),
                listingId = listingId,
                renterId = renterId,
                lenderId = lenderId
            )
            postgrest.from("conversations").insert(newConv)
            newConv
        } catch (e: Exception) {
            throw fail("open the chat", e)
        }
    }

    suspend fun getMessages(conversationId: String): List<Message> {
        return try {
            postgrest.from("messages").select {
                filter { eq("conversation_id", conversationId) }
                order("created_at", Order.ASCENDING)
            }.decodeList<Message>()
        } catch (e: Exception) {
            throw fail("load messages", e)
        }
    }

    suspend fun sendMessage(conversationId: String, senderId: String, text: String): Message {
        return try {
            val msg = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = senderId,
                body = text
            )
            postgrest.from("messages").insert(msg)
            msg
        } catch (e: Exception) {
            throw fail("send your message", e)
        }
    }

    fun observeRealtimeMessages(conversationId: String): Flow<Message> {
        val channel = realtime.channel("chat_$conversationId")
        return channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
        }.map { action -> action.decodeRecord() }
    }

    // --- RENTALS ---

    /** Rentals where I am the renter, with the listing (and its photos) and the lender embedded. */
    suspend fun getMyRentals(renterId: String): List<Rental> {
        return try {
            postgrest.from("rentals").select(
                Columns.raw(
                    "*, listing:listings(*, photos:listing_photos(*)), " +
                        "lender:profiles!rentals_lender_id_fkey(id, display_name, city)"
                )
            ) {
                filter { eq("renter_id", renterId) }
                order("updated_at", Order.DESCENDING)
            }.decodeList<Rental>()
        } catch (e: Exception) {
            throw fail("load your rentals", e)
        }
    }

    // --- MODERATION & OUTCOMES ---
    suspend fun blockUser(blockerId: String, blockedId: String) {
        try {
            postgrest.from("blocks").insert(Block(blockerId, blockedId))
        } catch (e: Exception) {
            throw fail("block this user", e)
        }
    }

    suspend fun report(report: Report) {
        try {
            postgrest.from("reports").insert(report)
        } catch (e: Exception) {
            throw fail("send your report", e)
        }
    }

    suspend fun submitRentalOutcome(outcome: RentalOutcome) {
        try {
            postgrest.from("rental_outcomes").upsert(outcome)
        } catch (e: Exception) {
            throw fail("save your answer", e)
        }
    }
}
