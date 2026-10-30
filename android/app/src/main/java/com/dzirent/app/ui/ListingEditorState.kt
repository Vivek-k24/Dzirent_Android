package com.dzirent.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import android.net.Uri
import com.dzirent.app.data.PhotoAnalysis
import com.dzirent.app.data.PhotoItem
import com.dzirent.app.data.model.Categories
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.SuggestionEvent
import com.dzirent.app.util.ColorVotes
import com.dzirent.app.util.DateRange
import com.dzirent.app.util.DraftMeasurement
import com.dzirent.app.util.DraftRange
import com.dzirent.app.util.FashionStyles
import com.dzirent.app.util.FieldOrigin
import com.dzirent.app.util.FieldState
import com.dzirent.app.util.GarmentKind
import com.dzirent.app.util.ItemGuess
import com.dzirent.app.util.ListingSuggestions
import com.dzirent.app.util.PhotoReview
import com.dzirent.app.util.PhotoSlot
import com.dzirent.app.util.PhotoVerdict
import com.dzirent.app.util.ReviewState
import com.dzirent.app.util.SavedDraft
import com.dzirent.app.util.SlotPhoto
import com.dzirent.app.util.Step2Input
import com.dzirent.app.util.SuggestField
import com.dzirent.app.util.TierVerdict
import com.dzirent.app.util.Sizes
import com.dzirent.app.util.averageEmbedding
import com.dzirent.app.util.clearedState
import com.dzirent.app.util.defaultAvailability
import com.dzirent.app.util.editedState
import com.dzirent.app.util.fieldsToFill
import com.dzirent.app.util.fieldsToFillForChip
import com.dzirent.app.util.garmentKindFor
import com.dzirent.app.util.jewelryFound
import com.dzirent.app.util.looksLikeDifferentItem
import com.dzirent.app.util.measurementLabelsFor
import com.dzirent.app.util.parseDateRange
import com.dzirent.app.util.photoGate
import com.dzirent.app.util.photoMinimumApplies
import com.dzirent.app.util.priceInputText
import com.dzirent.app.util.suggestedState
import com.dzirent.app.util.suggestionOutcome
import com.dzirent.app.util.tierVerdict
import com.dzirent.app.util.notOfferedWarning
import com.dzirent.app.util.validateMeasurements
import java.time.LocalDate

/** A photo taken out by the tier check, kept so "It's fashion, keep it" can put it back. */
data class RemovedPhoto(val slot: PhotoSlot, val item: PhotoItem, val verdict: TierVerdict.Remove)

/**
 * Everything the two-step listing form holds, and the rules that change it. Snapshot state, so
 * the screens recompose; the decisions themselves are the pure functions in util (tested there).
 */
class ListingEditorState(val original: Listing?, defaultUnit: String, today: LocalDate) {
    val isNew = original == null

    var step by mutableIntStateOf(1)

    // ---- Photos ----
    val slots = mutableStateMapOf<PhotoSlot, PhotoItem>()
    /** Checks per photo key, without the same-item flag (that depends on the current front). */
    val reviews = mutableStateMapOf<String, PhotoReview>()
    val analyses = mutableStateMapOf<String, PhotoAnalysis>()
    /** Photos the lender kept after a removal ("It's fashion, keep it"). */
    val kept = mutableStateListOf<String>()
    /** Photos whose everyday-basics warning was answered "This is wrong". */
    val overruled = mutableStateListOf<String>()
    var photosChanged by mutableStateOf(false)

    // ---- Details ----
    var title by mutableStateOf(textValue(original?.title.orEmpty()))
    var category by mutableStateOf(original?.category)
    var itemType by mutableStateOf(original?.itemType)
    var description by mutableStateOf(textValue(original?.description.orEmpty()))
    var styles by mutableStateOf(original?.listingStyles.orEmpty().filter { it in FashionStyles.ALL })
    val fields = mutableStateMapOf<SuggestField, FieldState>()
    var guess by mutableStateOf<ItemGuess>(ItemGuess.Unsure)
    /** The "Is it a: …?" chips were answered for this front photo key. */
    var chipsAnsweredFor by mutableStateOf<String?>(null)
    var recognizedKind by mutableStateOf<GarmentKind?>(null)
    var pickedKind by mutableStateOf(GarmentKind.fromKey(original?.garmentType))
    var fabricHintScores by mutableStateOf<DoubleArray?>(null)
    var colorVotes by mutableStateOf(ColorVotes.EMPTY)

    // ---- Step 2 ----
    var sizeLabel by mutableStateOf(original?.sizeLabel.orEmpty())
    var unit by mutableStateOf(original?.measurements?.firstOrNull()?.unit ?: defaultUnit)
    var measurementRows by mutableStateOf(
        original?.measurements.orEmpty().map { MeasurementRow(it.id, it.label, formatNumber(it.value)) }
    )
    var sameAsAnswered by mutableStateOf(!isNew)
    var priceText by mutableStateOf(priceInputText(original?.priceUsd))
    var priceNote by mutableStateOf(original?.priceNote.orEmpty())
    var retailValueText by mutableStateOf(original?.retailValue?.let { formatNumber(it) }.orEmpty())
    var depositNote by mutableStateOf(original?.depositNote.orEmpty())
    var ranges by mutableStateOf(
        if (original == null) listOf(defaultAvailability(today))
        else original.availability.mapNotNull { parseDateRange(it.startDate, it.endDate) }
    )
    var listingDeclarationChecked by mutableStateOf(false)
    var aiReviewNeeded by mutableStateOf(original?.aiReviewNeeded ?: false)

    init {
        original?.let { listing ->
            val photos = listing.photos.sortedBy { it.position }
            com.dzirent.app.util.slotsForSaved(photos.map { it.slot }).zip(photos).forEach { (slot, photo) ->
                slots[slot] = PhotoItem.Existing(photo)
            }
            // What was saved counts as the lender's own: suggestions only fill what's still empty.
            if (listing.title.isNotBlank()) fields[SuggestField.TITLE] = FieldState(FieldOrigin.EDITED)
            fields[SuggestField.CATEGORY] = FieldState(FieldOrigin.EDITED)
            if (listing.description.isNotBlank()) fields[SuggestField.DESCRIPTION] = FieldState(FieldOrigin.EDITED)
            if (listing.listingStyles.isNotEmpty()) fields[SuggestField.STYLE] = FieldState(FieldOrigin.EDITED)
        }
    }

    val isAccessory: Boolean get() = category == Categories.ACCESSORIES
    val front: PhotoItem? get() = slots[PhotoSlot.FRONT]
    val kind: GarmentKind? get() = garmentKindFor(recognizedKind, pickedKind, category)

    fun field(f: SuggestField) = fields[f] ?: FieldState()

    // ---- Photo reviews and tiers ----

    /** The review for the photo in [slot], with the same-item check against the front photo. */
    fun reviewOf(slot: PhotoSlot): PhotoReview? {
        val item = slots[slot] ?: return null
        return when (item) {
            is PhotoItem.Existing -> PhotoReview.Saved
            is PhotoItem.New -> {
                val base = reviews[item.key] ?: PhotoReview.Checking
                val frontEmbedding = front?.let { analyses[it.key]?.embedding }
                val embedding = analyses[item.key]?.embedding
                val different = slot != PhotoSlot.FRONT && slot.usedForType && frontEmbedding != null && embedding != null &&
                    looksLikeDifferentItem(frontEmbedding, embedding)
                base.copy(differentItem = different, fullChecks = slot.fullChecks)
            }
        }
    }

    val verdicts: Map<PhotoSlot, PhotoVerdict> get() = slots.keys.associateWith { reviewOf(it)!!.verdict }

    /** Each photo with its tier check, for the removal, warning and jewelry rules. */
    val slotPhotos: List<SlotPhoto>
        get() = slots.map { (slot, item) -> SlotPhoto(item.key, slot, analyses[item.key]?.scores?.let(::tierVerdict)) }

    val notOffered: SlotPhoto? get() = notOfferedWarning(slotPhotos, overruled.toSet())
    val jewelry: Boolean get() = jewelryFound(slotPhotos)

    fun photosGate() = photoGate(
        slots.keys.associateWith { reviewOf(it)!! },
        minimumApplies = photoMinimumApplies(isNew, photosChanged),
        policyPending = notOffered != null
    )

    fun reviewFor(analysis: PhotoAnalysis): PhotoReview =
        analysis.checks?.let { PhotoReview(ReviewState.CHECKED, it) } ?: PhotoReview(ReviewState.UNREADABLE)

    fun removePhoto(slot: PhotoSlot) {
        slots.remove(slot)
        photosChanged = true
    }

    /** Puts a removed photo back where it was (or in the first free slot). */
    fun restore(removed: RemovedPhoto): Boolean {
        val slot = if (removed.slot !in slots) removed.slot else PhotoSlot.FILL_ORDER.firstOrNull { it !in slots } ?: return false
        slots[slot] = removed.item
        kept += removed.item.key
        aiReviewNeeded = true
        photosChanged = true
        return true
    }

    // ---- Recognition across the photos ----

    /**
     * The Front, Back and Extra photos that are the same item as the front (and weren't
     * removed), the ones recognition is run on together. Empty until the front is analyzed.
     */
    fun typedAnalyses(): List<Pair<PhotoSlot, PhotoAnalysis>> {
        val frontItem = front as? PhotoItem.New ?: return emptyList()
        val frontAnalysis = analyses[frontItem.key]?.takeIf { it.embedding != null } ?: return emptyList()
        return listOf(PhotoSlot.FRONT to frontAnalysis) + slots.filterKeys { it != PhotoSlot.FRONT && it.usedForType }
            .mapNotNull { (slot, item) ->
                val analysis = analyses[item.key]?.takeIf { it.embedding != null } ?: return@mapNotNull null
                if (looksLikeDifferentItem(frontAnalysis.embedding!!, analysis.embedding!!)) null else slot to analysis
            }
    }

    fun combinedEmbedding(): FloatArray? = averageEmbedding(typedAnalyses().map { it.second.embedding!! })

    /** Fills empty, never-touched fields from [s] (suggest once). */
    fun applySuggestion(s: ListingSuggestions) {
        applyFills(fieldsToFill(fields, currentValues(), s), s)
        if (pickedKind == null) recognizedKind = s.kind
    }

    /** "Is it a: …?" picked: fills title, category and style (not over what the lender typed). */
    fun applyChip(s: ListingSuggestions) {
        applyFills(fieldsToFillForChip(fields, currentValues(), s), s)
        recognizedKind = s.kind
        pickedKind = null
        chipsAnsweredFor = front?.key
    }

    private fun currentValues() = mapOf(
        SuggestField.TITLE to title.text,
        SuggestField.CATEGORY to category.orEmpty(),
        SuggestField.DESCRIPTION to description.text,
        SuggestField.STYLE to styles.joinToString(", ")
    )

    private fun applyFills(fills: Map<SuggestField, String>, s: ListingSuggestions) {
        fills.forEach { (field, value) ->
            when (field) {
                SuggestField.TITLE -> title = textValue(value.take(80))
                SuggestField.DESCRIPTION -> description = textValue(value)
                SuggestField.CATEGORY -> {
                    category = value
                    itemType = if (value == Categories.ACCESSORIES) s.itemType else null
                }
                SuggestField.STYLE -> styles = listOf(value)
            }
            fields[field] = suggestedState(value, s.label)
        }
    }

    // ---- The lender's own edits ----

    fun editTitle(value: TextFieldValue) {
        if (value.text.length > 80) return
        if (value.text != title.text) fields[SuggestField.TITLE] = editedState(field(SuggestField.TITLE), value.text)
        title = value
    }

    fun editDescription(value: TextFieldValue) {
        if (value.text.length > 1000) return
        if (value.text != description.text) fields[SuggestField.DESCRIPTION] = editedState(field(SuggestField.DESCRIPTION), value.text)
        description = value
    }

    fun pickCategory(value: String) {
        fields[SuggestField.CATEGORY] = editedState(field(SuggestField.CATEGORY), value)
        category = value
        if (value != Categories.ACCESSORIES) itemType = null
    }

    fun editStyles(value: List<String>) {
        fields[SuggestField.STYLE] = editedState(field(SuggestField.STYLE), value.joinToString(", "))
        styles = value
    }

    fun clear(f: SuggestField) {
        fields[f] = clearedState(field(f))
        when (f) {
            SuggestField.TITLE -> title = textValue("")
            SuggestField.DESCRIPTION -> description = textValue("")
            SuggestField.CATEGORY -> {
                category = null
                itemType = null
            }
            SuggestField.STYLE -> styles = emptyList()
        }
    }

    // ---- Step 2 ----

    /** Template rows for the kind, when nothing has been typed into the measurements yet. */
    fun refreshMeasurementTemplate() {
        if (measurementRows.any { !it.isUnused }) return
        val labels = measurementLabelsFor(kind, itemType)
        if (measurementRows.map { it.label } != labels) measurementRows = labels.map { MeasurementRow(label = it, template = true) }
    }

    /** One-size kinds get their size label without a picker. */
    fun refreshFixedSize() {
        val k = kind ?: return
        Sizes.fixedLabel(k)?.let { if (sizeLabel != it) sizeLabel = it }
        if (Sizes.fixedLabel(k) == null && sizeLabel == Sizes.ONE_SIZE) sizeLabel = ""
    }

    val usedMeasurementRows: List<MeasurementRow> get() = measurementRows.filterNot { it.isUnused }

    val measurementsOk: Boolean get() = validateMeasurements(usedMeasurementRows.map { it.toInput() }).all { it == null }

    fun step2Input(hasLocation: Boolean) = Step2Input(
        sizeLabel = sizeLabel,
        priceText = priceText,
        isAccessory = isAccessory,
        listingDeclarationChecked = listingDeclarationChecked,
        retailValueText = retailValueText,
        priceNote = priceNote,
        description = description.text,
        measurementsOk = measurementsOk,
        ranges = ranges,
        hasLocation = hasLocation
    )

    /** kept / cleared / edited for each field a suggestion filled, at publish. */
    fun outcomeEvents(listingId: String): List<SuggestionEvent> = SuggestField.entries.mapNotNull { f ->
        val state = field(f)
        val final = when (f) {
            SuggestField.TITLE -> title.text
            SuggestField.CATEGORY -> category.orEmpty()
            SuggestField.DESCRIPTION -> description.text
            SuggestField.STYLE -> styles.joinToString(", ")
        }
        val action = suggestionOutcome(state, final) ?: return@mapNotNull null
        SuggestionEvent(f.key, state.label.orEmpty(), action, listingId)
    }

    // ---- Drafts ----

    fun toDraft() = SavedDraft(
        step = step,
        photos = slots.filterValues { it is PhotoItem.New }.map { (slot, item) -> slot.key to item.key }.toMap(),
        savedPhotos = slots.mapNotNull { (slot, item) -> (item as? PhotoItem.Existing)?.let { slot.key to it.photo.id } }.toMap(),
        photosChanged = photosChanged,
        kept = kept.toList(),
        overruled = overruled.toList(),
        title = title.text,
        category = category,
        itemType = itemType,
        description = description.text,
        styles = styles,
        fields = fields.mapKeys { it.key.key },
        recognizedKind = recognizedKind?.key,
        pickedKind = pickedKind?.key,
        sizeLabel = sizeLabel,
        unit = unit,
        measurements = measurementRows.map { DraftMeasurement(it.label, it.valueText, it.template) },
        sameAsAnswered = sameAsAnswered,
        priceText = priceText,
        priceNote = priceNote,
        retailValueText = retailValueText,
        depositNote = depositNote,
        ranges = ranges.map { DraftRange(it.start.toString(), it.end.toString()) },
        listingDeclarationChecked = listingDeclarationChecked,
        aiReviewNeeded = aiReviewNeeded
    )

    /**
     * Restores a saved draft. Its photos are checked again; nothing already answered is asked
     * again. A photo added meanwhile (a camera shot arriving after a restart) stays, in a free slot.
     */
    fun restore(draft: SavedDraft) {
        val meanwhile = slots.filterValues { it is PhotoItem.New }
        val saved = original?.photos.orEmpty().associateBy { it.id }
        slots.clear()
        draft.savedPhotos.forEach { (key, id) ->
            val slot = PhotoSlot.fromKey(key) ?: return@forEach
            saved[id]?.let { slots[slot] = PhotoItem.Existing(it) }
        }
        draft.photos.forEach { (key, uri) -> PhotoSlot.fromKey(key)?.let { slots[it] = PhotoItem.New(Uri.parse(uri)) } }
        meanwhile.forEach { (slot, item) ->
            if (slots.values.none { it.key == item.key }) {
                val free = if (slot !in slots) slot else PhotoSlot.FILL_ORDER.firstOrNull { it !in slots }
                if (free != null) slots[free] = item
            }
        }
        kept.clear(); kept += draft.kept
        overruled.clear(); overruled += draft.overruled
        title = textValue(draft.title)
        category = draft.category
        itemType = draft.itemType
        description = textValue(draft.description)
        styles = draft.styles.filter { it in FashionStyles.ALL }
        fields.clear()
        draft.fields.forEach { (key, state) -> SuggestField.entries.firstOrNull { it.key == key }?.let { fields[it] = state } }
        recognizedKind = GarmentKind.fromKey(draft.recognizedKind)
        pickedKind = GarmentKind.fromKey(draft.pickedKind)
        sizeLabel = draft.sizeLabel
        draft.unit?.let { unit = it }
        measurementRows = draft.measurements.map { MeasurementRow(label = it.label, valueText = it.value, template = it.template) }
        sameAsAnswered = draft.sameAsAnswered
        priceText = draft.priceText
        priceNote = draft.priceNote
        retailValueText = draft.retailValueText
        depositNote = draft.depositNote
        draft.dateRanges?.let { ranges = it }
        listingDeclarationChecked = draft.listingDeclarationChecked
        aiReviewNeeded = aiReviewNeeded || draft.aiReviewNeeded
        photosChanged = isNew || draft.photosChanged || meanwhile.isNotEmpty()
        step = draft.step.coerceIn(1, 2)
    }
}

/** Text with the cursor at the end, for text the app puts in a field. */
fun textValue(text: String) = TextFieldValue(text, TextRange(text.length))

/** 40.0 -> "40", 32.5 -> "32.5" for editable text fields. */
fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
