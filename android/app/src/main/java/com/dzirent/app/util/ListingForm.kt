package com.dzirent.app.util

import com.dzirent.app.data.model.AccessoryTypes
import com.dzirent.app.data.model.Categories
import com.dzirent.app.data.model.Listing
import kotlinx.serialization.Serializable
import java.time.LocalDate

// ---- Suggest once -------------------------------------------------------------------------

/** The fields the photos can fill. [key] is suggestion_events.field. */
enum class SuggestField(val key: String) {
    TITLE("title"),
    CATEGORY("category"),
    DESCRIPTION("description"),
    STYLE("style")
}

/** Who last set a field: nobody yet, a suggestion, or the lender (typing or ✕). */
enum class FieldOrigin { UNTOUCHED, SUGGESTED, EDITED, CLEARED }

/** One field's history: who set it, and what was suggested from which label (for tracking). */
@Serializable
data class FieldState(val origin: FieldOrigin = FieldOrigin.UNTOUCHED, val suggested: String? = null, val label: String? = null)

/** What a suggestion would put in each field (null: nothing for that field). */
fun suggestedValues(s: ListingSuggestions): Map<SuggestField, String?> = mapOf(
    SuggestField.TITLE to s.title,
    SuggestField.CATEGORY to s.category,
    SuggestField.DESCRIPTION to s.description,
    SuggestField.STYLE to s.style
)

/**
 * Suggest once: only fields that are empty and were never filled by a suggestion, edited or
 * cleared get the suggestion. Returns what to put in each of them.
 */
fun fieldsToFill(states: Map<SuggestField, FieldState>, current: Map<SuggestField, String>, s: ListingSuggestions): Map<SuggestField, String> =
    suggestedValues(s).mapNotNull { (field, value) ->
        val state = states[field] ?: FieldState()
        if (value == null || state.origin != FieldOrigin.UNTOUCHED || current[field].orEmpty().isNotBlank()) null else field to value
    }.toMap()

/**
 * Picking an "Is it a: …?" chip is the lender's own choice, so it fills title, category and
 * style even over an earlier suggestion or a cleared field; only what the lender typed stays.
 * Description still follows suggest-once.
 */
fun fieldsToFillForChip(states: Map<SuggestField, FieldState>, current: Map<SuggestField, String>, s: ListingSuggestions): Map<SuggestField, String> {
    val picked = suggestedValues(s).filterKeys { it != SuggestField.DESCRIPTION }.mapNotNull { (field, value) ->
        val origin = (states[field] ?: FieldState()).origin
        if (value == null || origin == FieldOrigin.EDITED) null else field to value
    }.toMap()
    return picked + fieldsToFill(states, current, s).filterKeys { it == SuggestField.DESCRIPTION }
}

/** The state after a suggestion filled the field. */
fun suggestedState(value: String, label: ItemLabel) = FieldState(FieldOrigin.SUGGESTED, value, label.name)

/** The state after the lender typed in the field (typing it empty counts as clearing). */
fun editedState(state: FieldState, newValue: String) =
    state.copy(origin = if (newValue.isBlank()) FieldOrigin.CLEARED else FieldOrigin.EDITED)

/** The state after ✕. */
fun clearedState(state: FieldState) = state.copy(origin = FieldOrigin.CLEARED)

/** suggestion_events.action at publish for a field that got a suggestion: kept, cleared or edited (null if none). */
fun suggestionOutcome(state: FieldState, finalValue: String): String? {
    val suggested = state.suggested ?: return null
    return when {
        finalValue.isBlank() -> "cleared"
        finalValue.trim() == suggested.trim() -> "kept"
        else -> "edited"
    }
}

/** The category field starts blank; this is its placeholder. Required to publish. */
const val CHOOSE_CATEGORY = "Choose a category"

// ---- What each step still needs ---------------------------------------------------------------

/**
 * What step 1 (photos and details) still needs, or null: the photos ([photos], from
 * [photoGate]), a 3-80 character title, a category, an item type for accessories, and the
 * accessory declaration when it's needed and not signed yet.
 */
fun step1Missing(photos: Missing?, title: String, category: String?, itemType: String?, declarationPending: Boolean): Missing? = when {
    photos != null -> photos
    title.trim().length !in 3..80 -> Missing("Add a title", "Title must be 3 to 80 characters.")
    category == null -> Missing(CHOOSE_CATEGORY, "Choose a category.")
    category == Categories.ACCESSORIES && itemType == null -> Missing("Choose an item type", "Choose an item type for Accessories.")
    declarationPending -> Missing("Sign the declaration", "Sign the accessory declaration to list jewelry or accessories.")
    else -> null
}

/** Everything step 2 checks, as typed. */
data class Step2Input(
    val sizeLabel: String,
    val priceText: String,
    val isAccessory: Boolean,
    val listingDeclarationChecked: Boolean,
    val retailValueText: String,
    val priceNote: String,
    val description: String,
    val measurementsOk: Boolean,
    val ranges: List<DateRange>,
    val hasLocation: Boolean
)

const val PRICE_NOTE_MAX = 60
const val DESCRIPTION_MAX = 1000

/** The plain message when an accessory is worth too much. */
val ACCESSORY_VALUE_TOO_HIGH =
    "Accessories worth more than \$${ItemLabels.ACCESSORY_MAX_VALUE_USD} can't be listed on Dzirent. Lower the retail value if it's worth less."

/** Null, or [ACCESSORY_VALUE_TOO_HIGH] when an accessory's retail value is above the limit. */
fun accessoryValueError(isAccessory: Boolean, retailValueText: String): String? {
    val value = retailValueText.toDoubleOrNull() ?: return null
    return ACCESSORY_VALUE_TOO_HIGH.takeIf { isAccessory && value > ItemLabels.ACCESSORY_MAX_VALUE_USD }
}

/** What step 2 still needs, or null when the listing can be published. */
fun step2Missing(input: Step2Input): Missing? {
    val retail = input.retailValueText
    return when {
        !input.hasLocation -> Missing("Set your location", "Set your location before publishing.")
        input.sizeLabel.isBlank() -> Missing("Choose a size", "Choose a size.")
        !input.measurementsOk -> Missing("Fix the measurements", "Fix the highlighted measurements, or remove those rows.")
        parsePrice(input.priceText) == null -> Missing("Add a price", "Enter a price above \$0, like 25 or 25.50.")
        input.isAccessory && !input.listingDeclarationChecked ->
            Missing("Tick the declaration", "Tick the box confirming this isn't fine jewelry and is worth under \$${ItemLabels.ACCESSORY_MAX_VALUE_USD}.")
        retail.isNotBlank() && (retail.toDoubleOrNull() ?: 0.0) <= 0.0 ->
            Missing("Fix the retail value", "Retail value must be a number above 0, or leave it empty.")
        accessoryValueError(input.isAccessory, retail) != null -> Missing("Retail value over \$${ItemLabels.ACCESSORY_MAX_VALUE_USD}", ACCESSORY_VALUE_TOO_HIGH)
        input.priceNote.trim().length > PRICE_NOTE_MAX -> Missing("Shorten price details", "Price details can be up to $PRICE_NOTE_MAX characters.")
        input.description.trim().length > DESCRIPTION_MAX -> Missing("Shorten the description", "Description can be up to $DESCRIPTION_MAX characters.")
        input.ranges.isEmpty() -> Missing("Add dates", "Add the dates this piece is available.")
        else -> null
    }
}

/** One line of the short ready list above "Publish listing". */
data class ReadyItem(val label: String, val done: Boolean)

/** The short ready list: photos and details (step 1), size, price, dates, and the declaration for accessories. */
fun readyList(step1Done: Boolean, input: Step2Input): List<ReadyItem> = listOfNotNull(
    ReadyItem("Photos and details", step1Done),
    ReadyItem("Size", input.sizeLabel.isNotBlank()),
    ReadyItem("Price", parsePrice(input.priceText) != null),
    ReadyItem("Dates", input.ranges.isNotEmpty()),
    ReadyItem("Declaration", input.listingDeclarationChecked).takeIf { input.isAccessory }
)

// ---- Dates ------------------------------------------------------------------------------------

/** New listings start available for the next 30 days, today included. */
const val DEFAULT_AVAILABLE_DAYS = 30

fun defaultAvailability(today: LocalDate): DateRange = DateRange(today, today.plusDays(DEFAULT_AVAILABLE_DAYS - 1L))

/** "Next 30 days" for the starting range, otherwise "Nov 3 - Nov 10". */
fun availabilityChipLabel(range: DateRange, today: LocalDate): String =
    if (range == defaultAvailability(today)) "Next $DEFAULT_AVAILABLE_DAYS days" else range.format()

// ---- Garment kind, measurements and "Same as your …" -----------------------------------------------

/**
 * The garment kind for the size picker and measurements: what the photos (or a chip) said,
 * else what the lender picked, else Accessories are one size. Null: the lender picks one.
 */
fun garmentKindFor(recognized: GarmentKind?, picked: GarmentKind?, category: String?): GarmentKind? = when {
    picked != null -> picked
    recognized != null -> recognized
    category == Categories.ACCESSORIES -> GarmentKind.ONE_SIZE
    else -> null
}

/** Measurement rows a listing of this kind starts with (values empty). */
fun measurementLabelsFor(kind: GarmentKind?, itemType: String?): List<String> = when (kind) {
    GarmentKind.TOP -> listOf("Chest (flat)", "Shoulder", "Sleeve", "Length")
    GarmentKind.TROUSERS -> listOf("Waist (flat)", "Inseam", "Rise", "Leg opening")
    GarmentKind.SUIT -> listOf("Chest (flat)", "Shoulder", "Sleeve", "Jacket length", "Waist (flat)", "Inseam")
    GarmentKind.DRESS -> listOf("Bust (flat)", "Waist (flat)", "Hips (flat)", "Length")
    GarmentKind.SKIRT -> listOf("Waist (flat)", "Hips (flat)", "Length")
    GarmentKind.SAREE -> listOf("Blouse bust (flat)", "Blouse length", "Skirt waist (flat)", "Skirt length")
    GarmentKind.ONE_SIZE -> AccessoryTypes.SUGGESTIONS[itemType].orEmpty()
    null -> emptyList()
}

/**
 * "Same as your …?": the lender's most recent other listing of the same kind that has
 * measurements, or null. Only the lender's own listings: never body measurements.
 */
fun sameAsCandidate(mine: List<Listing>, kind: GarmentKind?, excludeId: String?): Listing? {
    if (kind == null) return null
    return mine.filter { it.id != excludeId && it.garmentType == kind.key && it.measurements.isNotEmpty() }
        .maxByOrNull { timestampMillis(it.createdAt) }
}

/** "Same as your Maroon lehenga?" */
fun sameAsQuestion(listing: Listing): String = "Same as your ${listing.title}?"

/** The rows and unit "Yes" copies: labels and values as saved, in the saved unit. */
fun copiedMeasurements(listing: Listing): Pair<List<Pair<String, Double>>, String?> =
    listing.measurements.map { it.label to it.value } to listing.measurements.firstOrNull()?.unit
