package com.dzirent.app.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.dzirent.app.data.SupabaseConfig
import com.dzirent.app.data.model.*
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
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

class DzirentRepository {

    private val auth = SupabaseConfig.auth
    private val postgrest = SupabaseConfig.postgrest
    private val storage = SupabaseConfig.storage
    private val realtime = SupabaseConfig.realtime

    private fun logError(operation: String, throwable: Throwable) {
        Log.e("Dzirent", "Supabase error during $operation: ${throwable.javaClass.simpleName} - ${throwable.message}")
    }

    // --- AUTH ---
    suspend fun signUp(
        emailStr: String,
        passwordStr: String,
        displayName: String,
        role: String,
        adultConfirmed: Boolean,
        city: String,
        lat: Double,
        lng: Double
    ): Profile {
        return try {
            auth.signUpWith(Email) {
                email = emailStr
                password = passwordStr
                data = buildJsonObject {
                    put("display_name", displayName)
                    put("role", role)
                    put("adult_confirmed", adultConfirmed)
                }
            }

            val userId = auth.currentUserOrNull()?.id ?: UUID.randomUUID().toString()
            val profile = Profile(
                id = userId,
                displayName = displayName,
                role = role,
                city = city,
                approxLat = round2(lat),
                approxLng = round2(lng),
                adultConfirmed = adultConfirmed
            )

            postgrest.from("profiles").upsert(profile)
            profile
        } catch (e: Exception) {
            logError("signUp", e)
            throw e
        }
    }

    suspend fun signIn(emailStr: String, passwordStr: String) {
        try {
            auth.signInWith(Email) {
                email = emailStr
                password = passwordStr
            }
        } catch (e: Exception) {
            logError("signIn", e)
            throw e
        }
    }

    suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            logError("signOut", e)
            throw e
        }
    }

    suspend fun resetPassword(emailStr: String) {
        try {
            auth.resetPasswordForEmail(emailStr)
        } catch (e: Exception) {
            logError("resetPassword", e)
            throw e
        }
    }

    suspend fun getCurrentProfile(): Profile? {
        val uid = auth.currentUserOrNull()?.id ?: return null
        return try {
            postgrest.from("profiles")
                .select { filter { eq("id", uid) } }
                .decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            logError("getCurrentProfile", e)
            throw e
        }
    }

    suspend fun updateProfile(profile: Profile) {
        try {
            postgrest.from("profiles").update(profile) {
                filter { eq("id", profile.id) }
            }
        } catch (e: Exception) {
            logError("updateProfile", e)
            throw e
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
                    }
                    .decodeList<Listing>()
            } catch (fallbackEx: Exception) {
                logError("getNearbyListings fallback", fallbackEx)
                throw fallbackEx
            }
        }
    }

    suspend fun getListingDetail(id: String): Listing {
        return try {
            postgrest.from("listings").select {
                filter { eq("id", id) }
            }.decodeSingle<Listing>()
        } catch (e: Exception) {
            logError("getListingDetail", e)
            throw e
        }
    }

    suspend fun getMyListings(lenderId: String): List<Listing> {
        return try {
            postgrest.from("listings").select {
                filter { eq("lender_id", lenderId) }
            }.decodeList<Listing>()
        } catch (e: Exception) {
            logError("getMyListings", e)
            throw e
        }
    }

    suspend fun createListing(listing: Listing): Listing {
        return try {
            postgrest.from("listings").insert(listing)
            listing
        } catch (e: Exception) {
            logError("createListing", e)
            throw e
        }
    }

    suspend fun toggleListingStatus(id: String, newStatus: String) {
        try {
            postgrest.from("listings").update(mapOf("status" to newStatus)) {
                filter { eq("id", id) }
            }
        } catch (e: Exception) {
            logError("toggleListingStatus", e)
            throw e
        }
    }

    suspend fun deleteListing(listingId: String) {
        try {
            val photos = postgrest.from("listing_photos").select {
                filter { eq("listing_id", listingId) }
            }.decodeList<ListingPhoto>()

            if (photos.isNotEmpty()) {
                val paths = photos.map { it.storagePath }
                storage.from("listing-photos").delete(paths)
            }

            postgrest.from("listings").delete {
                filter { eq("id", listingId) }
            }
        } catch (e: Exception) {
            logError("deleteListing", e)
            throw e
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
            logError("uploadListingPhoto", e)
            throw e
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
            }.decodeList<Conversation>()
        } catch (e: Exception) {
            logError("getConversations", e)
            throw e
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
            logError("getOrCreateConversation", e)
            throw e
        }
    }

    suspend fun getMessages(conversationId: String): List<Message> {
        return try {
            postgrest.from("messages").select {
                filter { eq("conversation_id", conversationId) }
            }.decodeList<Message>()
        } catch (e: Exception) {
            logError("getMessages", e)
            throw e
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
            logError("sendMessage", e)
            throw e
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
            logError("blockUser", e)
            throw e
        }
    }

    suspend fun report(report: Report) {
        try {
            postgrest.from("reports").insert(report)
        } catch (e: Exception) {
            logError("report", e)
            throw e
        }
    }

    suspend fun submitRentalOutcome(outcome: RentalOutcome) {
        try {
            postgrest.from("rental_outcomes").upsert(outcome)
        } catch (e: Exception) {
            logError("submitRentalOutcome", e)
            throw e
        }
    }

    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0
}
