package com.dzirent.app.util

import com.dzirent.app.data.model.PrivateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTest {

    @Test
    fun rentersSeeItemsOneAndTwoOnly() {
        assertEquals(listOf(SetupItem.LOCATION, SetupItem.MEASUREMENTS), setupItems(canLend = false))
    }

    @Test
    fun lendersSeeAllFour() {
        assertEquals(
            listOf(SetupItem.LOCATION, SetupItem.MEASUREMENTS, SetupItem.STUDIO, SetupItem.FIRST_PIECE),
            setupItems(canLend = true)
        )
    }

    @Test
    fun basicsNeedLocationAndMeasurements() {
        assertFalse(SetupStatus(location = true, measurements = false, studio = true, firstPiece = true).basicsDone)
        assertFalse(SetupStatus(location = false, measurements = true, studio = false, firstPiece = false).basicsDone)
        assertTrue(SetupStatus(location = true, measurements = true, studio = false, firstPiece = false).basicsDone)
    }

    @Test
    fun anyOneMeasurementCounts() {
        assertFalse(hasAnyBodyMeasurement(PrivateProfile(userId = "me")))
        assertTrue(hasAnyBodyMeasurement(PrivateProfile(userId = "me", heightCm = 170.0)))
    }

    @Test
    fun guideHasFiveStepsInOrder() {
        assertEquals(
            listOf(GuideTarget.DATE_CHIP, GuideTarget.AVATAR, GuideTarget.SEARCH, GuideTarget.CLOSET, GuideTarget.BAG),
            guideSteps(canLend = true).map { it.target }
        )
    }

    @Test
    fun closetTextDependsOnRole() {
        assertEquals("Your listings and rentals live here.", guideSteps(canLend = true)[3].text)
        assertEquals("Your rentals live here.", guideSteps(canLend = false)[3].text)
    }
}
