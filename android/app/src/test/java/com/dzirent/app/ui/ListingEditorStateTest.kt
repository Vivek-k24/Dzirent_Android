package com.dzirent.app.ui

import com.dzirent.app.data.PhotoItem
import com.dzirent.app.data.model.Availability
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.ListingPhoto
import com.dzirent.app.util.ColorName
import com.dzirent.app.util.ColorResult
import com.dzirent.app.util.FieldOrigin
import com.dzirent.app.util.GarmentKind
import com.dzirent.app.util.ItemLabels
import com.dzirent.app.util.LabelTier
import com.dzirent.app.util.PhotoSlot
import com.dzirent.app.util.SuggestField
import com.dzirent.app.util.TierVerdict
import com.dzirent.app.util.defaultAvailability
import com.dzirent.app.util.suggestionsFor
import com.dzirent.app.util.suggestionsForChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ListingEditorStateTest {

    private val today = LocalDate.of(2026, 10, 30)
    private fun newState() = ListingEditorState(original = null, defaultUnit = "in", today = today)
    private fun photo(id: String) = PhotoItem.Existing(ListingPhoto(id, "l1", "https://x/$id.jpg", 0))

    @Test
    fun aNewListingStartsBlankWithTheNext30Days() {
        val s = newState()
        assertNull(s.category)
        assertEquals("", s.title.text)
        assertEquals(listOf(defaultAvailability(today)), s.ranges)
        assertEquals(1, s.step)
    }

    @Test
    fun keepingARemovedPhotoPutsItBackAndFlagsTheListing() {
        val s = newState()
        val bike = TierVerdict.Remove(LabelTier.NOT_FASHION, ItemLabels.byName("bicycle"), 0.9)
        val back = photo("back")
        s.slots[PhotoSlot.BACK] = back
        s.removePhoto(PhotoSlot.BACK)
        assertFalse(PhotoSlot.BACK in s.slots)
        assertFalse(s.aiReviewNeeded)

        assertTrue(s.restore(RemovedPhoto(PhotoSlot.BACK, back, bike)))
        assertEquals(back, s.slots[PhotoSlot.BACK])
        assertTrue(back.key in s.kept)
        assertTrue(s.aiReviewNeeded)
    }

    @Test
    fun aKeptPhotoGoesToAFreeSlotIfItsOwnWasTaken() {
        val s = newState()
        val bike = TierVerdict.Remove(LabelTier.NOT_FASHION, ItemLabels.byName("bicycle"), 0.9)
        s.slots[PhotoSlot.FRONT] = photo("other")
        assertTrue(s.restore(RemovedPhoto(PhotoSlot.FRONT, photo("bike"), bike)))
        assertEquals("bike", s.slots[PhotoSlot.BACK]!!.key)
    }

    @Test
    fun suggestionsFillOnceAndAcceptedTextHasTheCursorAtTheEnd() {
        val s = newState()
        val saree = suggestionsFor(ItemLabels.byName("saree")!!, ColorResult(ColorName.YELLOW, ColorName.RED))
        s.applySuggestion(saree)
        assertEquals("Yellow saree", s.title.text)
        assertEquals(s.title.text.length, s.title.selection.start)
        assertEquals("Yellow saree with red border.", s.description.text)
        assertEquals(s.description.text.length, s.description.selection.end)
        assertEquals("Cultural & Traditional", s.category)
        assertEquals(listOf("South Asian"), s.styles)
        assertEquals(GarmentKind.SAREE, s.kind)
        assertEquals(FieldOrigin.SUGGESTED, s.field(SuggestField.TITLE).origin)

        // A second suggestion (the front photo changed) doesn't overwrite or duplicate anything.
        s.applySuggestion(suggestionsFor(ItemLabels.byName("lehenga")!!, ColorResult(ColorName.MAROON)))
        assertEquals("Yellow saree", s.title.text)
        assertEquals("Yellow saree with red border.", s.description.text)

        // Cleared with ✕: stays empty.
        s.clear(SuggestField.DESCRIPTION)
        s.applySuggestion(saree)
        assertEquals("", s.description.text)
    }

    @Test
    fun publishLogsKeptClearedAndEdited() {
        val s = newState()
        s.applySuggestion(suggestionsFor(ItemLabels.byName("saree")!!, ColorResult(ColorName.YELLOW)))
        s.editTitle(textValue("Yellow silk saree"))
        s.clear(SuggestField.STYLE)
        val events = s.outcomeEvents("l1").associate { it.field to it.action }
        assertEquals(mapOf("title" to "edited", "category" to "kept", "description" to "kept", "style" to "cleared"), events)
        assertTrue(s.outcomeEvents("l1").all { it.label == "saree" && it.listingId == "l1" })
    }

    @Test
    fun aChipPickFillsTitleCategoryAndStyle() {
        val s = newState()
        s.applyChip(suggestionsForChip(ItemLabels.byName("blazer")!!, ColorResult(ColorName.NAVY)))
        assertEquals("Navy blazer", s.title.text)
        assertEquals("Career & Professional", s.category)
        assertEquals(listOf("Western formal"), s.styles)
        assertEquals(GarmentKind.SUIT, s.kind)
    }

    @Test
    fun accessoriesGetTheirItemTypeAndOneSize() {
        val s = newState()
        s.applySuggestion(suggestionsFor(ItemLabels.byName("necklace")!!))
        assertEquals("Accessories", s.category)
        assertEquals("Costume jewelry", s.itemType)
        assertTrue(s.isAccessory)
        s.refreshFixedSize()
        assertEquals("One size", s.sizeLabel)
    }

    @Test
    fun aDraftRestoresEverythingTyped() {
        val s = newState()
        s.applySuggestion(suggestionsFor(ItemLabels.byName("trousers")!!, ColorResult(ColorName.GREY)))
        s.sizeLabel = "32 x 30"
        s.priceText = "25"
        s.refreshMeasurementTemplate()
        s.measurementRows = s.measurementRows.map { if (it.label == "Inseam") it.copy(valueText = "30") else it }
        s.step = 2
        val draft = s.toDraft()

        val restored = newState().also { it.restore(draft) }
        assertEquals("Grey trousers", restored.title.text)
        assertEquals("Career & Professional", restored.category)
        assertEquals("32 x 30", restored.sizeLabel)
        assertEquals("25", restored.priceText)
        assertEquals(GarmentKind.TROUSERS, restored.kind)
        assertEquals(listOf("Inseam" to "30"), restored.usedMeasurementRows.map { it.label to it.valueText })
        assertEquals(2, restored.step)
        // Suggest-once survives the restore.
        assertEquals(FieldOrigin.SUGGESTED, restored.field(SuggestField.TITLE).origin)
    }

    @Test
    fun editingUsesTheSavedListingAndNeverOverwritesIt() {
        val original = Listing(
            id = "l1", lenderId = "me", title = "My lehenga", category = "Cultural & Traditional", approxLat = 0.0, approxLng = 0.0,
            status = "active", description = "Worn once.", sizeLabel = "Blouse 36", garmentType = "saree",
            photos = listOf(ListingPhoto("p1", "l1", "u", 0, slot = "front"), ListingPhoto("p2", "l1", "u", 1)),
            availability = listOf(Availability("a", "l1", "2026-11-01", "2026-11-10"))
        )
        val s = ListingEditorState(original, "in", today)
        assertEquals(setOf(PhotoSlot.FRONT, PhotoSlot.BACK), s.slots.keys)
        assertEquals(GarmentKind.SAREE, s.kind)
        s.applySuggestion(suggestionsFor(ItemLabels.byName("saree")!!, ColorResult(ColorName.RED)))
        assertEquals("My lehenga", s.title.text)
        assertEquals("Worn once.", s.description.text)
        // Two saved photos and nothing changed: the 3-photo minimum doesn't apply yet.
        assertNull(s.photosGate())
        s.removePhoto(PhotoSlot.BACK)
        assertEquals("Add 2 more photos", s.photosGate()!!.button)

        // Rotated mid-edit: the edit's draft brings back the saved photos in their slots and the typing.
        s.editTitle(textValue("My red lehenga"))
        val again = ListingEditorState(original, "in", today).also { it.restore(s.toDraft()) }
        assertEquals("My red lehenga", again.title.text)
        assertEquals(mapOf(PhotoSlot.FRONT to "p1"), again.slots.mapValues { it.value.key })
        assertTrue(again.photosChanged)
    }
}
