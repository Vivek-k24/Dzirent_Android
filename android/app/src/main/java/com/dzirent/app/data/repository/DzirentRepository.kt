package com.dzirent.app.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dzirent.app.data.SupabaseConfig
import com.dzirent.app.data.model.*
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.channel
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
        return profile
    }

    suspend fun signIn(emailStr: String, passwordStr: String) {
        auth.signInWith(Email) {
            email = emailStr
            password = passwordStr
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    suspend fun resetPassword(emailStr: String) {
        auth.resetPasswordForEmail(emailStr)
    }

    suspend fun getCurrentProfile(): Profile? {
        val uid = auth.currentUserOrNull()?.id ?: return null
        return postgrest.from("profiles")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<Profile>()
    }

    suspend fun updateProfile(profile: Profile) {
        postgrest.from("profiles").update(profile) {
            filter { eq("id", profile.id) }
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
            // Fallback direct query if RPC is pending
            postgrest.from("listings")
                .select {
                    filter {
                        eq("status", "active")
                        category?.let { eq("category", it) }
                    }
                }
                .decodeList<Listing>()
        }
    }

    suspend fun getListingDetail(id: String): Listing {
        return postgrest.from("listings").select {
            filter { eq("id", id) }
        }.decodeSingle<Listing>()
    }

    suspend fun getMyListings(lenderId: String): List<Listing> {
        return postgrest.from("listings").select {
            filter { eq("lender_id", lenderId) }
        }.decodeList<Listing>()
    }

    suspend fun toggleListingStatus(id: String, newStatus: String) {
        postgrest.from("listings").update(mapOf("status" to newStatus)) {
            filter { eq("id", id) }
        }
    }

    suspend fun deleteListing(listingId: String) {
        // Fetch photos to delete from storage
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
    }

    // --- PHOTO UPLOAD & COMPRESSION ---
    // Resizes to max 1600px on long side and compresses to JPEG ~80%
    suspend fun uploadListingPhoto(
        userId: String,
        listingId: String,
        imageBytes: ByteArray,
        position: Int
    ): ListingPhoto {
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
        return photo
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
        return postgrest.from("conversations").select {
            filter {
                or {
                    eq("renter_id", userId)
                    eq("lender_id", userId)
                }
            }
        }.decodeList<Conversation>()
    }

    suspend fun getOrCreateConversation(listingId: String, renterId: String, lenderId: String): Conversation {
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
        return newConv
    }

    suspend fun getMessages(conversationId: String): List<Message> {
        return postgrest.from("messages").select {
            filter { eq("conversation_id", conversationId) }
        }.decodeList<Message>()
    }

    suspend fun sendMessage(conversationId: String, senderId: String, text: String): Message {
        val msg = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            body = text
        )
        postgrest.from("messages").insert(msg)
        return msg
    }

    fun observeRealtimeMessages(conversationId: String): Flow<Message> {
        val channel = realtime.channel("chat_$conversationId")
        return channel.postgresChangeFlow<Message>(schema = "public") {
            table = "messages"
        }.map { it }
    }

    // --- MODERATION & OUTCOMES ---
    suspend fun blockUser(blockerId: String, blockedId: String) {
        postgrest.from("blocks").insert(Block(blockerId, blockedId))
    }

    suspend fun report(report: Report) {
        postgrest.from("reports").insert(report)
    }

    suspend fun submitRentalOutcome(outcome: RentalOutcome) {
        postgrest.from("rental_outcomes").upsert(outcome)
    }

    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0
}
