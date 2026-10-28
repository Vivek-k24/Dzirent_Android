package com.dzirent.app.data.model

import com.dzirent.app.util.BodyMeasurement
import com.dzirent.app.util.DateRange
import com.dzirent.app.util.FitProfile
import com.dzirent.app.util.formatPrice
import com.dzirent.app.util.parseDateRange
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Columns that can be NULL in the database have defaults here. Together with
// coerceInputValues in SupabaseConfig, a NULL value decodes to the default
// instead of crashing.

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    val role: String = "renter", // 'renter' | 'lender' | 'both'
    val city: String = "",
    @SerialName("approx_lat") val approxLat: Double? = null,
    @SerialName("approx_lng") val approxLng: Double? = null,
    @SerialName("adult_confirmed") val adultConfirmed: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    // Studio page, set in Profile > My studio (lenders). All optional.
    @SerialName("studio_name") val studioName: String? = null,
    @SerialName("studio_handle") val studioHandle: String? = null,
    @SerialName("studio_bio") val studioBio: String? = null,
    @SerialName("style_tags") val styleTags: List<String> = emptyList(),
    @SerialName("pickup_area") val pickupArea: String? = null
) {
    val hasLocation: Boolean get() = approxLat != null && approxLng != null

    /** The studio's name, falling back to the person's display name. */
    val studioTitle: String get() = studioName?.ifBlank { null } ?: displayName.ifBlank { "Studio" }
}

/** Everything the studio page shows about one lender. */
data class StudioPage(
    val lender: Profile,
    /** Active listings, newest first. */
    val pieces: List<Listing>,
    /** Rentals marked active or returned (studio_rental_count). */
    val rentedCount: Int,
    /** Median first-reply time, or null with too few chats (studio_reply_minutes). */
    val replyMinutes: Int?,
    val isSaved: Boolean
)

/**
 * My row of public.private_profiles: data only I can read (RLS), unlike [Profile].
 * The row may not exist yet; it is created on the first save.
 */
@Serializable
data class PrivateProfile(
    @SerialName("user_id") val userId: String,
    // Body measurements in cm (Profile > My measurements).
    @SerialName("chest_cm") val chestCm: Double? = null,
    @SerialName("waist_cm") val waistCm: Double? = null,
    @SerialName("hips_cm") val hipsCm: Double? = null,
    @SerialName("shoulder_cm") val shoulderCm: Double? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("inseam_cm") val inseamCm: Double? = null,
    /** When I last opened Inbox > Activity. */
    @SerialName("activity_seen_at") val activitySeenAt: String? = null,
    /** "I'm a" on My measurements: 'man', 'woman' or null. See [FitProfile]. */
    @SerialName("fit_profile") val fitProfile: String? = null,
    /** "Styles you're interested in" (007): only ranks suggestions and fills "For your style". */
    @SerialName("style_interests") val styleInterests: List<String> = emptyList()
) {
    val fit: FitProfile? get() = FitProfile.fromDb(fitProfile)

    fun bodyCm(measurement: BodyMeasurement): Double? = when (measurement) {
        BodyMeasurement.CHEST -> chestCm
        BodyMeasurement.WAIST -> waistCm
        BodyMeasurement.HIPS -> hipsCm
        BodyMeasurement.SHOULDER -> shoulderCm
        BodyMeasurement.HEIGHT -> heightCm
        BodyMeasurement.INSEAM -> inseamCm
    }
}

/** A row of public.us_cities: a US city, town or CDP from the Census Gazetteer, rounded to ~1 km. */
@Serializable
data class UsCity(
    val id: String,
    val name: String,
    val state: String,
    val lat: Double,
    val lng: Double
) {
    /** "Springfield, IL", the form saved as a profile's city. */
    val label: String get() = "$name, $state"
}

@Serializable
data class Listing(
    val id: String,
    @SerialName("lender_id") val lenderId: String,
    val title: String,
    val category: String,
    /** Only for [Categories.ACCESSORIES]: one of [AccessoryTypes.ALL]. */
    @SerialName("item_type") val itemType: String? = null,
    @SerialName("size_label") val sizeLabel: String = "",
    val description: String = "",
    /** Price per rental in USD; null only on listings created before prices were required. */
    @SerialName("price_usd") val priceUsd: Double? = null,
    /** Optional price details next to [priceUsd] (was the only price field before). */
    @SerialName("price_note") val priceNote: String = "",
    val city: String = "",
    @SerialName("approx_lat") val approxLat: Double,
    @SerialName("approx_lng") val approxLng: Double,
    val status: String, // 'active' | 'paused'
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("retail_value") val retailValue: Double? = null,
    @SerialName("deposit_note") val depositNote: String? = null,
    /** Up to [com.dzirent.app.util.FashionStyles.LISTING_MAX] from FashionStyles.ALL (007). Not shown on cards. */
    @SerialName("listing_styles") val listingStyles: List<String> = emptyList(),
    val photos: List<ListingPhoto> = emptyList(),
    val measurements: List<ListingMeasurement> = emptyList(),
    val availability: List<Availability> = emptyList(),
    val lender: Profile? = null,
    val distanceKm: Double? = null
) {
    /** "Sunglasses" for accessories with a type, otherwise the category. For compact rows. */
    val typeLabel: String get() = itemType ?: category

    /** "$25" when priced, otherwise the old free-text note. */
    val priceLabel: String get() = priceUsd?.let { formatPrice(it) } ?: priceNote
}

/** One of my saved pieces (Bag > Pieces), with the listing embedded. */
@Serializable
data class SavedListing(
    @SerialName("user_id") val userId: String,
    @SerialName("listing_id") val listingId: String,
    @SerialName("created_at") val createdAt: String? = null,
    // Embedded via select(), never written.
    val listing: Listing? = null
)

/** One of my saved studios (Bag > Studios), with the lender's public profile embedded. */
@Serializable
data class SavedStudio(
    @SerialName("user_id") val userId: String,
    @SerialName("lender_id") val lenderId: String,
    @SerialName("created_at") val createdAt: String? = null,
    // Embedded via select(), never written.
    val lender: Profile? = null
)

/** Kinds of rows in public.listing_events. */
object ListingEvents {
    const val VIEW = "view"
    const val SAVE = "save"
    const val MESSAGE = "message"
}

@Serializable
data class ListingPhoto(
    val id: String,
    @SerialName("listing_id") val listingId: String,
    @SerialName("storage_path") val storagePath: String,
    val position: Int
)

@Serializable
data class ListingMeasurement(
    val id: String,
    @SerialName("listing_id") val listingId: String,
    val label: String,
    val value: Double,
    val unit: String // 'cm' | 'in'
)

@Serializable
data class Availability(
    val id: String,
    @SerialName("listing_id") val listingId: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String
)

@Serializable
data class Conversation(
    val id: String,
    @SerialName("listing_id") val listingId: String,
    @SerialName("renter_id") val renterId: String,
    @SerialName("lender_id") val lenderId: String,
    @SerialName("created_at") val createdAt: String? = null,
    // The dates the renter last asked about (006). Set only through set_conversation_dates.
    @SerialName("requested_start") val requestedStart: String? = null,
    @SerialName("requested_end") val requestedEnd: String? = null,
    // Embedded via select(), never written.
    val listing: Listing? = null,
    val renter: Profile? = null,
    val lender: Profile? = null,
    val messages: List<Message> = emptyList()
) {
    val requestedDates: DateRange? get() = parseDateRange(requestedStart, requestedEnd)
    fun otherUserId(me: String): String = if (me == renterId) lenderId else renterId
    fun otherUser(me: String): Profile? = if (me == renterId) lender else renter
    val lastMessage: Message? get() = messages.firstOrNull()
}

@Serializable
data class Message(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Block(
    @SerialName("blocker_id") val blockerId: String,
    @SerialName("blocked_id") val blockedId: String,
    // Embedded via select(), never written.
    val blocked: Profile? = null
)

@Serializable
data class Report(
    val id: String? = null,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_type") val targetType: String, // 'listing' | 'user' | 'message'
    @SerialName("target_id") val targetId: String,
    val reason: String,
    val details: String? = null
)

object ReportReasons {
    val ALL = listOf("Inappropriate photo", "Prohibited item", "Scam or fraud", "Harassment", "Other")
    const val DETAILS_MAX = 500
}

@Serializable
data class Rental(
    val id: String? = null,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("listing_id") val listingId: String,
    @SerialName("renter_id") val renterId: String,
    @SerialName("lender_id") val lenderId: String,
    val status: String = "active", // 'active' | 'returned' | 'cancelled'
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("updated_by") val updatedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    // Embedded via select(), never written.
    val listing: Listing? = null,
    val lender: Profile? = null,
    val renter: Profile? = null
)

/** My row of public.conversation_reads: when I last opened that chat. */
@Serializable
data class ConversationRead(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("last_read_at") val lastReadAt: String? = null
)

@Serializable
data class RentalOutcome(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    val happened: Boolean,
    // Filled in by the DB default on insert.
    @SerialName("answered_at") val answeredAt: String? = null
)

object Roles {
    const val RENTER = "renter"
    const val LENDER = "lender"
    const val BOTH = "both"

    fun canLend(role: String) = role == LENDER || role == BOTH
}

object Categories {
    const val ACCESSORIES = "Accessories"

    val ALL = listOf(
        "Career & Professional",
        "Occasion & Party",
        "Cultural & Traditional",
        "Everyday",
        "Outerwear",
        ACCESSORIES
    )

    // "(flat)" = measured across the garment laid flat; the fit rule doubles these.
    private val SUGGESTIONS = mapOf(
        "Career & Professional" to listOf("Shoulder", "Chest (flat)", "Sleeve", "Length", "Waist (flat)"),
        "Occasion & Party" to listOf("Bust (flat)", "Waist (flat)", "Hips (flat)", "Total Length", "Shoulder"),
        "Cultural & Traditional" to listOf("Chest/Bust (flat)", "Waist (flat)", "Kurta/Top Length", "Bottom Length", "Shoulder"),
        "Everyday" to listOf("Chest (flat)", "Length", "Waist (flat)", "Inseam"),
        "Outerwear" to listOf("Chest (flat)", "Shoulder", "Sleeve Length", "Total Length")
    )

    /** Quick-add measurement labels; accessories get them per item type (none until one is picked). */
    fun measurementSuggestions(category: String, itemType: String?): List<String> =
        if (category == ACCESSORIES) AccessoryTypes.SUGGESTIONS[itemType].orEmpty()
        else SUGGESTIONS[category].orEmpty()
}

/** Item types allowed in Accessories; the same list is in the listings_item_type_check constraint. */
object AccessoryTypes {
    val ALL = listOf("Sunglasses", "Handbag", "Tie", "Watch (non-smart)", "Belt buckle")

    val SUGGESTIONS = mapOf(
        "Sunglasses" to listOf("Lens width", "Bridge", "Temple length"),
        "Handbag" to listOf("Width", "Height", "Depth", "Strap drop"),
        "Tie" to listOf("Length", "Width"),
        "Watch (non-smart)" to listOf("Case diameter", "Band length"),
        "Belt buckle" to listOf("Width", "Height")
    )
}
