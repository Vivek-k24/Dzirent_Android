package com.dzirent.app.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.dzirent.app.data.SupabaseConfig
import com.dzirent.app.data.model.*
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.*

/**
 * An error with a short, user-safe message. Raw Supabase errors can contain request
 * URLs, headers and login tokens, so they are never shown on screen.
 */
class DzirentException(message: String) : Exception(message)

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
                "Too many attempts. Please wait a minute and try again."
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
                        put("approx_lat", round2(lat))
                        put("approx_lng", round2(lng))
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
            throw fail("log out", e)
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

    // --- LISTINGS & NEARBY SEARCH ---
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

        return try {
            postgrest.rpc("listings_nearby", rpcParams).decodeList<Listing>()
        } catch (e: Exception) {
            logError("getNearbyListings RPC", e)
            try {
                postgrest.from("listings")
                    .select {
                        filter {
                            eq("status", "active")
                            category?.let { eq("category", it) }
                        }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Listing>()
            } catch (fallbackEx: Exception) {
                throw fail("load listings", fallbackEx)
            }
        }
    }

    suspend fun getListingDetail(id: String): Listing {
        return try {
            postgrest.from("listings").select {
                filter { eq("id", id) }
            }.decodeSingle<Listing>()
        } catch (e: Exception) {
            throw fail("load this listing", e)
        }
    }

    suspend fun getMyListings(lenderId: String): List<Listing> {
        return try {
            postgrest.from("listings").select {
                filter { eq("lender_id", lenderId) }
                order("created_at", Order.DESCENDING)
            }.decodeList<Listing>()
        } catch (e: Exception) {
            throw fail("load your closet", e)
        }
    }

    suspend fun createListing(listing: Listing): Listing {
        return try {
            postgrest.from("listings").insert(listing)
            listing
        } catch (e: Exception) {
            throw fail("publish your listing", e)
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
                // storage_path may hold a full public URL; storage.delete needs the object path.
                val paths = photos.map { it.storagePath.substringAfter("/listing-photos/") }
                storage.from("listing-photos").delete(paths)
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
        return try {
            val compressed = compressImage(imageBytes)
            val photoUuid = UUID.randomUUID().toString()
            val path = "$userId/$listingId/$photoUuid.jpg"

            storage.from("listing-photos").upload(path, compressed) {
                upsert = true
            }

            val publicUrl = storage.from("listing-photos").publicUrl(path)
            val photo = ListingPhoto(
                id = photoUuid,
                listingId = listingId,
                storagePath = publicUrl,
                position = position
            )
            postgrest.from("listing_photos").insert(photo)
            photo
        } catch (e: Exception) {
            throw fail("upload the photo", e)
        }
    }

    private fun compressImage(originalBytes: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
        val maxDim = 1600
        val width = bitmap.width
        val height = bitmap.height

        val scaledBitmap = if (width > maxDim || height > maxDim) {
            val ratio = if (width > height) maxDim.toFloat() / width else maxDim.toFloat() / height
            Bitmap.createScaledBitmap(
                bitmap,
                (width * ratio).roundToInt(),
                (height * ratio).roundToInt(),
                true
            )
        } else {
            bitmap
        }

        val out = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
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

    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0
}
