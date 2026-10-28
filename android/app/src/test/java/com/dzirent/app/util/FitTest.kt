package com.dzirent.app.util

import com.dzirent.app.data.model.Categories
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.ListingMeasurement
import com.dzirent.app.data.model.PrivateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitTest {

    // Chest 90, waist 72, hips 96, shoulder 40 cm.
    private val body = PrivateProfile(userId = "me", chestCm = 90.0, waistCm = 72.0, hipsCm = 96.0, shoulderCm = 40.0)

    private fun m(label: String, value: Double, unit: String = "cm") =
        ListingMeasurement(id = label, listingId = "l", label = label, value = value, unit = unit)

    private fun listing(category: String, vararg measurements: ListingMeasurement) = Listing(
        id = "l", lenderId = "b", title = "T", category = category,
        approxLat = 0.0, approxLng = 0.0, status = "active", measurements = measurements.toList()
    )

    @Test
    fun labelsMapToBodyParts() {
        assertEquals(BodyMeasurement.CHEST, bodyPartForLabel("Chest"))
        assertEquals(BodyMeasurement.CHEST, bodyPartForLabel("Bust (flat)"))
        assertEquals(BodyMeasurement.CHEST, bodyPartForLabel("Chest/Bust (flat)"))
        assertEquals(BodyMeasurement.CHEST, bodyPartForLabel("Pit to pit"))
        assertEquals(BodyMeasurement.WAIST, bodyPartForLabel("Waist (flat)"))
        assertEquals(BodyMeasurement.HIPS, bodyPartForLabel("Hips"))
        assertEquals(BodyMeasurement.SHOULDER, bodyPartForLabel("Shoulder"))
        assertNull(bodyPartForLabel("Sleeve Length"))
        assertNull(bodyPartForLabel("Kurta/Top Length"))
        assertNull(bodyPartForLabel("Inseam"))
    }

    @Test
    fun flatWidthsAreDoubledButShoulderIsNot() {
        assertEquals(96.0, garmentCm(m("Chest (flat)", 48.0), BodyMeasurement.CHEST), 1e-9)
        assertEquals(96.0, garmentCm(m("Chest", 96.0), BodyMeasurement.CHEST), 1e-9)
        assertEquals(40.0, garmentCm(m("Shoulder (flat)", 40.0), BodyMeasurement.SHOULDER), 1e-9)
    }

    @Test
    fun inchesAreConverted() {
        assertEquals(96.52, garmentCm(m("Chest (flat)", 19.0, "in"), BodyMeasurement.CHEST), 1e-9)
    }

    @Test
    fun twoMatchesMakeALikelyFit() {
        // Everyday: chest +6 (4..20 ok), waist +8 (0..14 ok).
        val shirt = listing("Everyday", m("Chest (flat)", 48.0), m("Waist (flat)", 40.0), m("Length", 70.0))
        assertEquals(setOf(BodyMeasurement.CHEST, BodyMeasurement.WAIST), fitMatches(shirt, body))
        assertTrue(isLikelyFit(shirt, body))
    }

    @Test
    fun oneMatchIsNotEnough() {
        // Chest +6 ok, waist +28 too loose.
        val shirt = listing("Everyday", m("Chest (flat)", 48.0), m("Waist (flat)", 50.0))
        assertFalse(isLikelyFit(shirt, body))
    }

    @Test
    fun tooSmallIsNotAFit() {
        // Occasion & Party: chest -2 and waist -2 are below 0.
        val dress = listing("Occasion & Party", m("Bust", 88.0), m("Waist", 70.0))
        assertFalse(isLikelyFit(dress, body))
    }

    @Test
    fun sameBodyPartCountsOnce() {
        val shirt = listing("Everyday", m("Chest", 96.0), m("Bust", 96.0))
        assertEquals(setOf(BodyMeasurement.CHEST), fitMatches(shirt, body))
        assertFalse(isLikelyFit(shirt, body))
    }

    @Test
    fun missingBodyMeasurementsAreSkipped() {
        val partial = PrivateProfile(userId = "me", chestCm = 90.0)
        val shirt = listing("Everyday", m("Chest (flat)", 48.0), m("Waist (flat)", 40.0))
        assertFalse(isLikelyFit(shirt, partial))
        assertTrue(hasFitMeasurements(partial))
        assertFalse(hasFitMeasurements(PrivateProfile(userId = "me", heightCm = 170.0)))
    }

    @Test
    fun accessoriesNeverFit() {
        val bag = listing(Categories.ACCESSORIES, m("Chest", 96.0), m("Waist", 80.0))
        assertTrue(fitMatches(bag, body).isEmpty())
    }

    @Test
    fun outerwearAllowsMoreEase() {
        // Chest +20 and shoulder +2: fine for a coat, too loose (chest) for a party dress.
        val coat = listing("Outerwear", m("Chest", 110.0), m("Shoulder", 42.0))
        assertTrue(isLikelyFit(coat, body))
        val dress = listing("Occasion & Party", m("Chest", 110.0), m("Shoulder", 42.0))
        assertFalse(isLikelyFit(dress, body))
    }
}
