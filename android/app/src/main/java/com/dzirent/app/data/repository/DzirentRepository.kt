package com.dzirent.app.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import com.dzirent.app.data.SupabaseConfig
import com.dzirent.app.data.model.*
import com.dzirent.app.util.ActivityItem
import com.dzirent.app.util.BodyMeasurement
import com.dzirent.app.util.DateRange
import com.dzirent.app.util.FitProfile
import com.dzirent.app.util.buildActivity
import com.dzirent.app.util.hasUnread
import com.dzirent.app.util.haversineKm
import com.dzirent.app.util.roundCoord
import com.dzirent.app.util.timestampMillis
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.HasRecord
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

/** Shown when RLS rejects a chat because one side blocked the other. */
const val CANT_MESSAGE_USER = "You can't message this user."

/** A conversation with its listing (and photos) and both people's public profiles. */
private const val CONVERSATION_WITH_PEOPLE =
    "*, listing:listings(*, photos:listing_photos(*)), " +
        "renter:profiles!conversations_renter_id_fkey(id, display_name, city), " +
        "lender:profiles!conversations_lender_id_fkey(id, display_name, city)"

/** Live chat updates; [close] leaves the realtime channel. */
class ChatSubscription(
    val messages: Flow<Message>,
    val rentals: Flow<Rental>,
    val close: suspend () -> Unit
)

/** Embedded select for a listing with all of its child rows and the lender's public profile. */
private const val LISTING_WITH_CHILDREN =
    "*, photos:listing_photos(*), measurements:listing_measurements(*), availability:availability(*), " +
        "lender:profiles!listings_lender_id_fkey(id, display_name, city)"

@Serializable
private data class ListingIdRow(@SerialName("listing_id") val listingId: String)

@Serializable
private data class ConversationIdRow(@SerialName("conversation_id") val conversationId: String)

@Serializable
private data class LenderIdRow(@SerialName("lender_id") val lenderId: String)

/** Everything Inbox and its dot need: my chats, which are unread, and Activity. */
data class InboxSnapshot(
    val conversations: List<Conversation>,
    val unreadIds: Set<String>,
    val activity: List<ActivityItem>
) {
    /** Shows the dot on the Inbox tab. */
    val hasNews: Boolean get() = unreadIds.isNotEmpty() || activity.any { it.isNew }
}

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

    /**
     * Like [fail], but an RLS rejection here means one of the two people blocked the other
     * (the policies check is_blocked), so say that instead of "session expired".
     */
    private fun failBlockAware(operation: String, throwable: Throwable): DzirentException {
        val raw = throwable.message.orEmpty().lowercase()
        if ("row-level security" in raw) {
            logError(operation, throwable)
            return DzirentException(CANT_MESSAGE_USER)
        }
        return fail(operation, throwable)
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
     * below (including the picked city's rounded location), so the app never inserts into
     * profiles itself. [city] is optional; without it the app asks for a location after login.
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
        city: UsCity?
    ): Profile? {
        try {
            auth.signUpWith(Email) {
                email = emailStr
                password = passwordStr
                data = buildJsonObject {
                    put("display_name", displayName)
                    put("role", role)
                    put("adult_confirmed", adultConfirmed)
                    if (city != null) {
                        put("city", city.label)
                        put("approx_lat", roundCoord(city.lat))
                        put("approx_lng", roundCoord(city.lng))
                    }
                }
            }
        } catch (e: Exception) {
            throw fail("create your account", e)
        }

        // No session = email confirmation is on. The profile row already exists via the trigger.
        if (auth.currentSessionOrNull() == null) return null
        val userId = auth.currentUserOrNull()?.id ?: return null

        return try {
            // Also saved by the trigger; repeated here in case the database runs an older trigger.
            if (city != null) {
                postgrest.from("profiles").update(
                    buildJsonObject {
                        put("city", city.label)
                        put("approx_lat", roundCoord(city.lat))
                        put("approx_lng", roundCoord(city.lng))
                    }
                ) {
                    filter { eq("id", userId) }
                }
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

    /** The signed-in account's email, shown read-only in Settings > Account. */
    fun currentEmail(): String? = auth.currentUserOrNull()?.email

    /**
     * Changes the password after checking [current]: Supabase's update alone doesn't ask
     * for it, so signing in again with it proves the person is the account owner.
     */
    suspend fun changePassword(current: String, new: String) {
        val emailStr = currentEmail()
            ?: throw DzirentException("Your session may have expired. Please log out and log in again.")
        try {
            auth.signInWith(Email) {
                email = emailStr
                password = current
            }
        } catch (e: Exception) {
            if ("invalid login credentials" in e.message.orEmpty().lowercase()) {
                logError("check the current password", e)
                throw DzirentException("Your current password is incorrect.")
            }
            throw fail("check your password", e)
        }
        try {
            auth.updateUser { password = new }
        } catch (e: Exception) {
            if ("different from the old" in e.message.orEmpty().lowercase()) {
                logError("change the password", e)
                throw DzirentException("Choose a password that's different from your current one.")
            }
            throw fail("change your password", e)
        }
    }

    /** True if I'm the renter or lender of an active rental (account deletion waits for it). */
    suspend fun hasActiveRental(userId: String): Boolean {
        return try {
            postgrest.from("rentals").select(Columns.raw("id")) {
                filter {
                    eq("status", "active")
                    or {
                        eq("renter_id", userId)
                        eq("lender_id", userId)
                    }
                }
                limit(1)
            }.decodeList<JsonObject>().isNotEmpty()
        } catch (e: Exception) {
            throw fail("check your rentals", e)
        }
    }

    /**
     * Deletes my account through the delete-account Edge Function, which takes the user
     * from my access token, refuses during an active rental, removes my photos and then
     * the auth user (the database cascades the rest). Sign out afterwards.
     */
    suspend fun deleteAccount() {
        val token = auth.currentSessionOrNull()?.accessToken
            ?: throw DzirentException("Your session may have expired. Please log out and log in again.")
        val (status, body) = try {
            HttpClient(Android).use { http ->
                val response = http.post("${SupabaseConfig.SUPABASE_URL}/functions/v1/delete-account") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }
                response.status.value to response.bodyAsText()
            }
        } catch (e: Exception) {
            throw fail("delete your account", e)
        }
        if (status in 200..299) return
        Log.e("Dzirent", "delete-account returned HTTP $status")
        // Only the function's own replies (they carry "error") have a message meant for people.
        val reply = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        val message = reply?.takeIf { "error" in it }?.get("message")?.jsonPrimitive?.contentOrNull
        throw DzirentException(
            when {
                message != null -> message
                status == 401 -> "Your session may have expired. Please log out and log in again."
                status == 404 -> "Account deletion isn't available yet. Please contact us (Settings > About Dzirent)."
                else -> "Couldn't delete your account. Please try again."
            }
        )
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

    /** My private_profiles row, or an empty one if I never saved anything there. */
    suspend fun getPrivateProfile(userId: String): PrivateProfile {
        return try {
            postgrest.from("private_profiles")
                .select { filter { eq("user_id", userId) } }
                .decodeSingleOrNull<PrivateProfile>() ?: PrivateProfile(userId = userId)
        } catch (e: Exception) {
            throw fail("load your measurements", e)
        }
    }

    /**
     * Saves my body measurements (cm, null clears one) and my "I'm a" choice. The upsert
     * creates my row the first time and otherwise changes only these columns.
     */
    suspend fun saveBodyMeasurements(
        userId: String,
        valuesCm: Map<BodyMeasurement, Double?>,
        fitProfile: FitProfile?
    ): PrivateProfile {
        return try {
            postgrest.from("private_profiles").upsert(
                buildJsonObject {
                    put("user_id", userId)
                    valuesCm.forEach { (measurement, cm) -> put(measurement.column, cm) }
                    put("fit_profile", fitProfile?.dbValue)
                }
            ) { select() }.decodeSingle<PrivateProfile>()
        } catch (e: Exception) {
            throw fail("save your measurements", e)
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

    /**
     * Cities whose name starts with [query] (case-insensitive), by name then state. Every
     * city with exactly that name is included even past [limit]. Works before login.
     */
    suspend fun searchCities(query: String, limit: Int = 20): List<UsCity> {
        return try {
            postgrest.rpc(
                "search_cities",
                buildJsonObject {
                    put("p_query", query)
                    put("p_limit", limit)
                }
            ).decodeList<UsCity>()
        } catch (e: Exception) {
            throw fail("search cities", e)
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
        val nearby = listings
            .map { it.copy(distanceKm = haversineKm(lat, lng, it.approxLat, it.approxLng)) }
            .filter { (it.distanceKm ?: 0.0) <= radiusKm }
        return withPhotos(nearby)
    }

    /**
     * Nearby listings for Home and Search, with photos, availability, measurements and the
     * lender's public profile filled in (one batched query each).
     */
    suspend fun getBrowseListings(lat: Double, lng: Double, radiusKm: Double): List<Listing> {
        val listings = getNearbyListings(lat, lng, radiusKm)
        if (listings.isEmpty()) return listings
        val ids = listings.map { it.id }
        return try {
            val availability = postgrest.from("availability").select {
                filter { isIn("listing_id", ids) }
            }.decodeList<Availability>().groupBy { it.listingId }
            val measurements = postgrest.from("listing_measurements").select {
                filter { isIn("listing_id", ids) }
            }.decodeList<ListingMeasurement>().groupBy { it.listingId }
            val lenders = postgrest.from("profiles").select(Columns.list("id", "display_name", "city")) {
                filter { isIn("id", listings.map { it.lenderId }.distinct()) }
            }.decodeList<Profile>().associateBy { it.id }
            listings.map {
                it.copy(
                    availability = availability[it.id].orEmpty().sortedBy { range -> range.startDate },
                    measurements = measurements[it.id].orEmpty(),
                    lender = lenders[it.lenderId]
                )
            }
        } catch (e: Exception) {
            throw fail("load listings", e)
        }
    }

    // --- STUDIOS ---

    /** A lender's studio page: public profile, active pieces, counts and whether I saved it. */
    suspend fun getStudio(lenderId: String, userId: String): StudioPage {
        return try {
            val lender = postgrest.from("profiles").select { filter { eq("id", lenderId) } }.decodeSingle<Profile>()
            val pieces = postgrest.from("listings").select(Columns.raw("*, photos:listing_photos(*)")) {
                filter {
                    eq("lender_id", lenderId)
                    eq("status", "active")
                }
                order("created_at", Order.DESCENDING)
            }.decodeList<Listing>().map { it.sorted() }
            val rented = postgrest.rpc("studio_rental_count", buildJsonObject { put("p_lender_id", lenderId) })
                .decodeAs<Int>()
            val replyMinutes = postgrest.rpc("studio_reply_minutes", buildJsonObject { put("p_lender_id", lenderId) })
                .decodeAsOrNull<Int>()
            val saved = lenderId != userId && lenderId in getSavedStudioIds(userId)
            StudioPage(lender, pieces, rented, replyMinutes, saved)
        } catch (e: Exception) {
            throw fail("load this studio", e)
        }
    }

    /** Adds a studio to my Bag (saving twice is harmless). */
    suspend fun saveStudio(userId: String, lenderId: String) {
        try {
            postgrest.from("saved_studios").upsert(
                buildJsonObject {
                    put("user_id", userId)
                    put("lender_id", lenderId)
                }
            ) { ignoreDuplicates = true }
        } catch (e: Exception) {
            throw fail("save this studio", e)
        }
    }

    suspend fun unsaveStudio(userId: String, lenderId: String) {
        try {
            postgrest.from("saved_studios").delete {
                filter {
                    eq("user_id", userId)
                    eq("lender_id", lenderId)
                }
            }
        } catch (e: Exception) {
            throw fail("remove this studio", e)
        }
    }

    /**
     * Saves Profile > My studio. Blank text clears a field. A handle someone else has
     * gets its own message.
     */
    suspend fun updateStudio(
        userId: String,
        name: String,
        handle: String,
        bio: String,
        styleTags: List<String>,
        pickupArea: String
    ): Profile {
        try {
            postgrest.from("profiles").update(
                buildJsonObject {
                    put("studio_name", name.ifBlank { null })
                    put("studio_handle", handle.ifBlank { null })
                    put("studio_bio", bio.ifBlank { null })
                    put("style_tags", JsonArray(styleTags.map { JsonPrimitive(it) }))
                    put("pickup_area", pickupArea.ifBlank { null })
                }
            ) {
                filter { eq("id", userId) }
            }
            return getCurrentProfile() ?: throw DzirentException("Couldn't load your profile. Please try again.")
        } catch (e: Exception) {
            val raw = e.message.orEmpty()
            if ("profiles_studio_handle_key" in raw || "duplicate key" in raw.lowercase()) {
                logError("save studio", e)
                throw DzirentException("@$handle is taken. Try another handle.")
            }
            throw fail("save your studio", e)
        }
    }

    /** Lender ids in my saved studios. */
    suspend fun getSavedStudioIds(userId: String): Set<String> {
        return try {
            postgrest.from("saved_studios").select(Columns.list("lender_id")) {
                filter { eq("user_id", userId) }
            }.decodeList<LenderIdRow>().map { it.lenderId }.toSet()
        } catch (e: Exception) {
            throw fail("load your saved studios", e)
        }
    }

    /** Fills in photos for many listings with one batched "in" query. */
    private suspend fun withPhotos(listings: List<Listing>): List<Listing> {
        if (listings.isEmpty()) return listings
        val byListing = try {
            postgrest.from("listing_photos").select {
                filter { isIn("listing_id", listings.map { it.id }) }
                order("position", Order.ASCENDING)
            }.decodeList<ListingPhoto>().groupBy { it.listingId }
        } catch (e: Exception) {
            throw fail("load listing photos", e)
        }
        return listings.map { it.copy(photos = byListing[it.id].orEmpty()) }
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
                    put("item_type", listing.itemType)
                    put("size_label", listing.sizeLabel)
                    put("description", listing.description)
                    put("price_usd", listing.priceUsd)
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

    // --- SAVED PIECES & EVENTS ---

    /** True if [listingId] is in my saved pieces. */
    suspend fun isListingSaved(userId: String, listingId: String): Boolean {
        return try {
            postgrest.from("saved_listings").select(Columns.list("listing_id")) {
                filter {
                    eq("user_id", userId)
                    eq("listing_id", listingId)
                }
            }.decodeList<ListingIdRow>().isNotEmpty()
        } catch (e: Exception) {
            throw fail("load your saved pieces", e)
        }
    }

    /** Adds a listing to my saved pieces (saving twice is harmless) and records a save event. */
    suspend fun saveListing(userId: String, listingId: String) {
        try {
            postgrest.from("saved_listings").upsert(
                buildJsonObject {
                    put("user_id", userId)
                    put("listing_id", listingId)
                }
            ) { ignoreDuplicates = true }
        } catch (e: Exception) {
            throw fail("save this piece", e)
        }
        recordListingEvent(userId, listingId, ListingEvents.SAVE)
    }

    suspend fun unsaveListing(userId: String, listingId: String) {
        try {
            postgrest.from("saved_listings").delete {
                filter {
                    eq("user_id", userId)
                    eq("listing_id", listingId)
                }
            }
        } catch (e: Exception) {
            throw fail("remove this piece", e)
        }
    }

    /**
     * Bag > Pieces, newest first. The listing is null when it was paused or deleted
     * (RLS hides other people's paused listings).
     */
    suspend fun getSavedListings(userId: String): List<SavedListing> {
        return try {
            postgrest.from("saved_listings").select(Columns.raw("*, listing:listings(*, photos:listing_photos(*))")) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }.decodeList<SavedListing>().map { saved -> saved.copy(listing = saved.listing?.sorted()) }
        } catch (e: Exception) {
            throw fail("load your Bag", e)
        }
    }

    /** Bag > Studios, newest first, with each lender's public studio details. */
    suspend fun getSavedStudios(userId: String): List<SavedStudio> {
        return try {
            postgrest.from("saved_studios").select(
                Columns.raw(
                    "*, lender:profiles!saved_studios_lender_id_fkey(id, display_name, city, studio_name, studio_handle)"
                )
            ) {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
            }.decodeList<SavedStudio>()
        } catch (e: Exception) {
            throw fail("load your Bag", e)
        }
    }

    /**
     * Adds a row to listing_events (data for future personalization). Never fails the
     * caller: a lost event only gets logged.
     */
    suspend fun recordListingEvent(userId: String, listingId: String, type: String) {
        try {
            postgrest.from("listing_events").insert(
                buildJsonObject {
                    put("user_id", userId)
                    put("listing_id", listingId)
                    put("type", type)
                }
            )
        } catch (e: Exception) {
            logError("record $type event", e)
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

    /**
     * My conversations with listing (and photos), both people and the latest message,
     * newest activity first.
     */
    suspend fun getConversations(userId: String): List<Conversation> {
        return try {
            postgrest.from("conversations").select(
                Columns.raw("$CONVERSATION_WITH_PEOPLE, messages(id, conversation_id, sender_id, body, created_at)")
            ) {
                filter {
                    or {
                        eq("renter_id", userId)
                        eq("lender_id", userId)
                    }
                }
                order("created_at", Order.DESCENDING, referencedTable = "messages")
                limit(1, referencedTable = "messages")
            }.decodeList<Conversation>()
                .sortedByDescending { timestampMillis(it.lastMessage?.createdAt ?: it.createdAt) }
        } catch (e: Exception) {
            throw fail("load your messages", e)
        }
    }

    /**
     * Conversations with unread flags plus Activity (rental changes and follow-ups).
     * Read times come from conversation_reads, last Activity visit from private_profiles.
     */
    suspend fun getInboxSnapshot(userId: String): InboxSnapshot {
        val conversations = getConversations(userId)
        return try {
            val reads = postgrest.from("conversation_reads").select(Columns.list("conversation_id", "last_read_at")) {
                filter { eq("user_id", userId) }
            }.decodeList<ConversationRead>().associate { it.conversationId to it.lastReadAt }
            val rentals = postgrest.from("rentals").select(
                Columns.raw(
                    "*, listing:listings(*), " +
                        "renter:profiles!rentals_renter_id_fkey(id, display_name, city), " +
                        "lender:profiles!rentals_lender_id_fkey(id, display_name, city)"
                )
            ) {
                filter {
                    or {
                        eq("renter_id", userId)
                        eq("lender_id", userId)
                    }
                }
            }.decodeList<Rental>()
            val answered = postgrest.from("rental_outcomes").select(Columns.list("conversation_id")) {
                filter { eq("user_id", userId) }
            }.decodeList<ConversationIdRow>().map { it.conversationId }.toSet()
            val seenAt = getPrivateProfile(userId).activitySeenAt
            InboxSnapshot(
                conversations = conversations,
                unreadIds = conversations.filter { hasUnread(it, userId, reads[it.id]) }.map { it.id }.toSet(),
                activity = buildActivity(userId, conversations, rentals, answered, seenAt)
            )
        } catch (e: Exception) {
            throw fail("load your messages", e)
        }
    }

    /** Marks a chat as read now (server time). Failures are only logged. */
    suspend fun markConversationRead(conversationId: String) {
        try {
            postgrest.rpc("mark_conversation_read", buildJsonObject { put("p_conversation_id", conversationId) })
        } catch (e: Exception) {
            logError("mark chat read", e)
        }
    }

    /**
     * Saves the dates the renter asked about, so both people see them under the pinned
     * listing. Only the renter can (set_conversation_dates). Failures are only logged.
     */
    suspend fun setConversationDates(conversationId: String, dates: DateRange): Boolean {
        return try {
            postgrest.rpc("set_conversation_dates", buildJsonObject {
                put("p_conversation_id", conversationId)
                put("p_start", dates.start.toString())
                put("p_end", dates.end.toString())
            })
            true
        } catch (e: Exception) {
            logError("save chat dates", e)
            false
        }
    }

    /** Records that I opened Inbox > Activity now (server time). Failures are only logged. */
    suspend fun markActivitySeen() {
        try {
            postgrest.rpc("mark_activity_seen")
        } catch (e: Exception) {
            logError("mark activity seen", e)
        }
    }

    suspend fun getConversation(conversationId: String): Conversation {
        return try {
            postgrest.from("conversations").select(Columns.raw(CONVERSATION_WITH_PEOPLE)) {
                filter { eq("id", conversationId) }
            }.decodeSingle<Conversation>()
        } catch (e: Exception) {
            throw fail("open the chat", e)
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
            recordListingEvent(renterId, listingId, ListingEvents.MESSAGE)
            newConv
        } catch (e: Exception) {
            throw failBlockAware("open the chat", e)
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

    /** Sends a message and returns the saved row (with the server's id and time). */
    suspend fun sendMessage(conversationId: String, senderId: String, text: String): Message {
        return try {
            val msg = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderId = senderId,
                body = text
            )
            postgrest.from("messages").insert(msg) { select() }.decodeSingle<Message>()
        } catch (e: Exception) {
            throw failBlockAware("send your message", e)
        }
    }

    /**
     * Live inserts on this conversation's messages and changes to its rental. Collect the
     * flows, and call [ChatSubscription.close] when the chat closes.
     */
    suspend fun subscribeToChat(conversationId: String): ChatSubscription {
        val channel = realtime.channel("chat_$conversationId")
        val messages = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }.mapNotNull { action ->
            runCatching { action.decodeRecord<Message>() }.onFailure { logError("decode live message", it) }.getOrNull()
        }
        val rentals = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "rentals"
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }.mapNotNull { action -> (action as? HasRecord)?.decodeRecordOrNull<Rental>() }
        try {
            channel.subscribe()
        } catch (e: Exception) {
            runCatching { realtime.removeChannel(channel) }
            throw fail("connect to live chat", e)
        }
        return ChatSubscription(messages, rentals) {
            try {
                realtime.removeChannel(channel)
            } catch (e: Exception) {
                logError("leave live chat", e)
            }
        }
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

    suspend fun getRental(conversationId: String): Rental? {
        return try {
            postgrest.from("rentals").select {
                filter { eq("conversation_id", conversationId) }
            }.decodeSingleOrNull<Rental>()
        } catch (e: Exception) {
            throw fail("load the rental status", e)
        }
    }

    /**
     * Creates or updates the rental row for a conversation. Dates are only changed when
     * given. If the other person created the row a moment earlier, this updates it instead.
     */
    suspend fun saveRental(
        conversation: Conversation,
        status: String,
        startDate: String?,
        endDate: String?,
        userId: String
    ): Rental {
        val changes = buildJsonObject {
            put("status", status)
            put("updated_by", userId)
            if (startDate != null && endDate != null) {
                put("start_date", startDate)
                put("end_date", endDate)
            }
        }
        suspend fun update(rentalId: String): Rental =
            postgrest.from("rentals").update(changes) {
                filter { eq("id", rentalId) }
                select()
            }.decodeSingle<Rental>()

        return try {
            val existing = getRental(conversation.id)
            if (existing?.id != null) {
                update(existing.id)
            } else {
                try {
                    postgrest.from("rentals").insert(
                        buildJsonObject {
                            put("conversation_id", conversation.id)
                            put("listing_id", conversation.listingId)
                            put("renter_id", conversation.renterId)
                            put("lender_id", conversation.lenderId)
                            changes.forEach { (key, value) -> put(key, value) }
                        }
                    ) { select() }.decodeSingle<Rental>()
                } catch (insertEx: Exception) {
                    val raced = getRental(conversation.id)?.id ?: throw insertEx
                    update(raced)
                }
            }
        } catch (e: Exception) {
            throw fail("update the rental", e)
        }
    }

    /** My answer to "Did this rental happen?" for a conversation, if any. */
    suspend fun getMyRentalOutcome(conversationId: String, userId: String): RentalOutcome? {
        return try {
            postgrest.from("rental_outcomes").select {
                filter {
                    eq("conversation_id", conversationId)
                    eq("user_id", userId)
                }
            }.decodeSingleOrNull<RentalOutcome>()
        } catch (e: Exception) {
            throw fail("load your answer", e)
        }
    }

    // --- MODERATION & OUTCOMES ---
    suspend fun blockUser(blockerId: String, blockedId: String) {
        try {
            // Blocking twice is harmless: the primary key makes the second insert a no-op.
            postgrest.from("blocks").upsert(Block(blockerId, blockedId)) { ignoreDuplicates = true }
        } catch (e: Exception) {
            throw fail("block this user", e)
        }
    }

    /** Deletes my own blocks row ("manage own blocks" RLS only allows mine). */
    suspend fun unblockUser(blockerId: String, blockedId: String) {
        try {
            postgrest.from("blocks").delete {
                filter {
                    eq("blocker_id", blockerId)
                    eq("blocked_id", blockedId)
                }
            }
        } catch (e: Exception) {
            throw fail("unblock this user", e)
        }
    }

    /** People I blocked, with their public profile. */
    suspend fun getBlockedUsers(blockerId: String): List<Block> {
        return try {
            postgrest.from("blocks").select(
                Columns.raw("blocker_id, blocked_id, blocked:profiles!blocks_blocked_id_fkey(id, display_name, city)")
            ) {
                filter { eq("blocker_id", blockerId) }
            }.decodeList<Block>()
        } catch (e: Exception) {
            throw fail("load your blocked users", e)
        }
    }

    /** True if I blocked this user (not whether they blocked me). */
    suspend fun haveIBlocked(blockerId: String, otherId: String): Boolean {
        return try {
            postgrest.from("blocks").select(Columns.list("blocker_id")) {
                filter {
                    eq("blocker_id", blockerId)
                    eq("blocked_id", otherId)
                }
            }.decodeList<JsonObject>().isNotEmpty()
        } catch (e: Exception) {
            throw fail("load your blocked users", e)
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
