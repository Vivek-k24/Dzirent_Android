package com.dzirent.app.data.model

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
    @SerialName("approx_lat") val approxLat: Double = 0.0,
    @SerialName("approx_lng") val approxLng: Double = 0.0,
    @SerialName("adult_confirmed") val adultConfirmed: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Listing(
    val id: String,
    @SerialName("lender_id") val lenderId: String,
    val title: String,
    val category: String,
    @SerialName("size_label") val sizeLabel: String = "",
    val description: String = "",
    @SerialName("price_note") val priceNote: String = "",
    val city: String = "",
    @SerialName("approx_lat") val approxLat: Double,
    @SerialName("approx_lng") val approxLng: Double,
    val status: String, // 'active' | 'paused'
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("retail_value") val retailValue: Double? = null,
    @SerialName("deposit_note") val depositNote: String? = null,
    val photos: List<ListingPhoto> = emptyList(),
    val measurements: List<ListingMeasurement> = emptyList(),
    val availability: List<Availability> = emptyList(),
    val lender: Profile? = null,
    val distanceKm: Double? = null
)

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
    val listing: Listing? = null,
    val otherUser: Profile? = null,
    val lastMessage: Message? = null
)

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
    @SerialName("blocked_id") val blockedId: String
)

@Serializable
data class Report(
    val id: String? = null,
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("target_type") val targetType: String, // 'listing' | 'user' | 'message'
    @SerialName("target_id") val targetId: String,
    val reason: String
)

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
    val lender: Profile? = null
)

@Serializable
data class RentalOutcome(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    val happened: Boolean,
    @SerialName("answered_at") val answeredAt: String
)

object Roles {
    const val RENTER = "renter"
    const val LENDER = "lender"
    const val BOTH = "both"

    fun canLend(role: String) = role == LENDER || role == BOTH
}

object Categories {
    val ALL = listOf(
        "Career & Professional",
        "Occasion & Party",
        "Cultural & Traditional",
        "Everyday",
        "Outerwear",
        "Bags & Accessories"
    )

    val SUGGESTIONS = mapOf(
        "Career & Professional" to listOf("Shoulder", "Chest", "Sleeve", "Length", "Waist"),
        "Occasion & Party" to listOf("Bust", "Waist", "Hips", "Total Length", "Shoulder"),
        "Cultural & Traditional" to listOf("Chest/Bust", "Waist", "Kurta/Top Length", "Bottom Length", "Shoulder"),
        "Everyday" to listOf("Chest", "Length", "Waist", "Inseam"),
        "Outerwear" to listOf("Chest", "Shoulder", "Sleeve Length", "Total Length"),
        "Bags & Accessories" to listOf("Width", "Height", "Depth", "Strap Drop")
    )
}
