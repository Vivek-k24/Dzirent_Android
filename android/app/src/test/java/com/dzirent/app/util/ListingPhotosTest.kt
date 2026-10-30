package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingPhotosTest {

    // ---- Camera = gallery ------------------------------------------------------------------

    private fun rgb(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b

    /** An upright photo: a navy shirt (with a white stripe near the top) on a light grey sheet. */
    private val upright: RgbImage = run {
        val w = 240
        val h = 180
        RgbImage(w, h, IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            when {
                x in 70 until 170 && y in 40 until 50 -> rgb(245, 245, 245)
                x in 70 until 170 && y in 40 until 140 -> rgb(25, 35, 85) xor ((x * 7 + y * 13) % 9) // a little texture
                else -> rgb(200, 200, 205)
            }
        })
    }

    private fun map(src: RgbImage, outW: Int, outH: Int, at: (Int, Int) -> Pair<Int, Int>): RgbImage =
        RgbImage(outW, outH, IntArray(outW * outH) { i ->
            val (sx, sy) = at(i % outW, i / outW)
            src.pixels[sy * src.width + sx]
        })

    // How a camera stores the upright photo for each EXIF orientation (the inverse of what the tag says to do).
    private fun rotateCcw(s: RgbImage) = map(s, s.height, s.width) { x, y -> (s.width - 1 - y) to x }
    private fun rotateCw(s: RgbImage) = map(s, s.height, s.width) { x, y -> y to (s.height - 1 - x) }
    private fun mirror(s: RgbImage) = map(s, s.width, s.height) { x, y -> (s.width - 1 - x) to y }
    private fun flip(s: RgbImage) = map(s, s.width, s.height) { x, y -> x to (s.height - 1 - y) }
    private fun transpose(s: RgbImage) = map(s, s.height, s.width) { x, y -> y to x }

    private fun stored(orientation: Int): RgbImage = when (orientation) {
        2 -> mirror(upright)
        3 -> rotateCw(rotateCw(upright))
        4 -> flip(upright)
        5 -> transpose(upright)
        6 -> rotateCcw(upright)
        7 -> rotateCw(rotateCw(transpose(upright)))
        8 -> rotateCw(upright)
        else -> upright
    }

    @Test
    fun everyExifOrientationComesOutUpright() {
        (1..8).forEach { o ->
            val turned = applyExifOrientation(stored(o), o)
            assertEquals("orientation $o width", upright.width, turned.width)
            assertEquals("orientation $o height", upright.height, turned.height)
            assertTrue("orientation $o pixels", upright.pixels.contentEquals(turned.pixels))
        }
        // Unknown values are left as stored.
        assertTrue(applyExifOrientation(upright, 0).pixels.contentEquals(upright.pixels))
    }

    @Test
    fun aCameraPhotoStoredSidewaysGetsTheSameChecksAndColorsAsTheGalleryPhoto() {
        val gallery = analyzeImage(applyExifOrientation(upright, 1))
        val camera = analyzeImage(applyExifOrientation(stored(6), 6))
        assertEquals(gallery.checks, camera.checks)
        assertTrue(gallery.colors.counts.contentEquals(camera.colors.counts))
        assertEquals(gallery.colors.blueExcess, camera.colors.blueExcess, 1e-9)
        assertEquals(nameColors(gallery.colors), nameColors(camera.colors))
        assertEquals(ColorName.NAVY, nameColors(camera.colors).primary)
        // ...and so the same suggestion.
        val shirt = ItemLabels.byName("button-up shirt")!!
        assertEquals(suggestionsFor(shirt, nameColors(gallery.colors)), suggestionsFor(shirt, nameColors(camera.colors)))
    }

    // ---- Slots ----------------------------------------------------------------------------

    @Test
    fun picksFillFrontBackCloseUpThenExtras() {
        val (slots, left) = assignSlots(emptyMap(), listOf("a", "b", "c", "d"))
        assertEquals(mapOf(PhotoSlot.FRONT to "a", PhotoSlot.BACK to "b", PhotoSlot.CLOSE_UP to "c", PhotoSlot.EXTRA_1 to "d"), slots)
        assertEquals(0, left)
        // A tapped slot is filled first.
        val (tagged, _) = assignSlots(slots, listOf("tag"), target = PhotoSlot.SIZE_TAG)
        assertEquals("tag", tagged[PhotoSlot.SIZE_TAG])
        // No more than 6.
        val (full, over) = assignSlots(tagged, listOf("e", "f", "g"))
        assertEquals(6, full.size)
        assertEquals(2, over)
    }

    @Test
    fun savedPhotosWithoutASlotGetOneByPosition() {
        assertEquals(listOf(PhotoSlot.FRONT, PhotoSlot.BACK, PhotoSlot.CLOSE_UP, PhotoSlot.EXTRA_1), slotsForSaved(listOf(null, null, null, null)))
        assertEquals(listOf(PhotoSlot.SIZE_TAG, PhotoSlot.FRONT, PhotoSlot.BACK), slotsForSaved(listOf("size_tag", null, null)))
    }

    // ---- Removals, the backstop, warnings, jewelry ---------------------------------------------

    private val bike = TierVerdict.Remove(LabelTier.NOT_FASHION, ItemLabels.byName("bicycle"), 0.9)
    private val hat = TierVerdict.Remove(LabelTier.NOT_ALLOWED, ItemLabels.byName("hat"), 0.8)
    private val tee = TierVerdict.NotOffered(ItemLabels.byName("t-shirt")!!, 0.8)
    private val necklace = TierVerdict.Jewelry(ItemLabels.byName("necklace")!!, 0.8)

    @Test
    fun notFashionAndNotAllowedPhotosAreRemoved() {
        val photos = listOf(
            SlotPhoto("front", PhotoSlot.FRONT, TierVerdict.Fashion),
            SlotPhoto("back", PhotoSlot.BACK, bike),
            SlotPhoto("extra", PhotoSlot.EXTRA_1, hat)
        )
        assertEquals(listOf("back", "extra"), photosToRemove(photos, kept = emptySet()).map { it.key })
    }

    @Test
    fun keptPhotosStayAndDetailSlotsAreNeverRemoved() {
        val photos = listOf(
            SlotPhoto("back", PhotoSlot.BACK, bike),
            SlotPhoto("close", PhotoSlot.CLOSE_UP, bike),
            SlotPhoto("tag", PhotoSlot.SIZE_TAG, hat)
        )
        assertEquals(emptyList<SlotPhoto>(), photosToRemove(photos, kept = setOf("back")))
    }

    @Test
    fun thePublishBackstopCatchesAnythingStillThere() {
        // A photo restored from a draft, or checked after the step was left: the same rule at Publish.
        val atPublish = listOf(
            SlotPhoto("front", PhotoSlot.FRONT, TierVerdict.Fashion),
            SlotPhoto("back", PhotoSlot.BACK, TierVerdict.Fashion),
            SlotPhoto("extra2", PhotoSlot.EXTRA_2, bike),
            SlotPhoto("unchecked", PhotoSlot.EXTRA_1, null)
        )
        assertEquals(listOf("extra2"), photosToRemove(atPublish, kept = emptySet()).map { it.key })
        assertEquals(bike.message, (photosToRemove(atPublish, emptySet()).single().verdict as TierVerdict.Remove).message)
    }

    @Test
    fun notOfferedIsOnlyAWarningUntilAnswered() {
        val photos = listOf(SlotPhoto("front", PhotoSlot.FRONT, tee))
        assertEquals(emptyList<SlotPhoto>(), photosToRemove(photos, emptySet()))
        assertEquals("front", notOfferedWarning(photos, overruled = emptySet())!!.key)
        assertNull(notOfferedWarning(photos, overruled = setOf("front")))
    }

    @Test
    fun jewelryTriggersTheDeclarationAndIsNeverRemoved() {
        val photos = listOf(SlotPhoto("front", PhotoSlot.FRONT, necklace))
        assertEquals(emptyList<SlotPhoto>(), photosToRemove(photos, emptySet()))
        assertTrue(jewelryFound(photos))
        assertTrue(declarationNeeded(category = "Occasion & Party", jewelryFound = true, signed = false))
        assertFalse(declarationNeeded(category = "Occasion & Party", jewelryFound = true, signed = true))
        assertFalse(jewelryFound(listOf(SlotPhoto("close", PhotoSlot.CLOSE_UP, necklace))))
    }
}
