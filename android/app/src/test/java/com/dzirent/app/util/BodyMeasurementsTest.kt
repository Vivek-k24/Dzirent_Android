package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMeasurementsTest {

    @Test
    fun centimetersAreKept() {
        assertEquals(91.5, bodyInputToCm("91.5", "cm")!!, 0.0)
    }

    @Test
    fun inchesConvertToCentimeters() {
        assertEquals(91.4, bodyInputToCm("36", "in")!!, 0.0)
    }

    @Test
    fun notANumberIsNull() {
        assertNull(bodyInputToCm("abc", "cm"))
        assertNull(bodyInputToCm("", "in"))
    }

    @Test
    fun storedValueShowsInEitherUnit() {
        assertEquals("91.4", cmToBodyInput(91.4, "cm"))
        assertEquals("36", cmToBodyInput(91.44, "in"))
        assertEquals("", cmToBodyInput(null, "in"))
    }

    @Test
    fun chestSaysBustForWoman() {
        assertEquals("Bust", BodyMeasurement.CHEST.labelFor(FitProfile.WOMAN))
        assertEquals("Chest", BodyMeasurement.CHEST.labelFor(FitProfile.MAN))
        assertEquals("Chest", BodyMeasurement.CHEST.labelFor(null))
        assertEquals("Around the fullest part of your bust, under your arms.", BodyMeasurement.CHEST.howToFor(FitProfile.WOMAN))
        assertEquals(BodyMeasurement.CHEST.howTo, BodyMeasurement.CHEST.howToFor(FitProfile.MAN))
    }

    @Test
    fun otherMeasurementsDontChange() {
        BodyMeasurement.entries.filter { it != BodyMeasurement.CHEST }.forEach {
            assertEquals(it.label, it.labelFor(FitProfile.WOMAN))
            assertEquals(it.howTo, it.howToFor(FitProfile.WOMAN))
        }
    }

    @Test
    fun fitProfileDatabaseValues() {
        assertEquals(FitProfile.MAN, FitProfile.fromDb("man"))
        assertEquals(FitProfile.WOMAN, FitProfile.fromDb("woman"))
        assertNull(FitProfile.fromDb(null))
        assertNull(FitProfile.fromDb("Man"))
    }

    @Test
    fun rangeMatchesDatabaseCheck() {
        assertTrue(isValidBodyCm(0.1))
        assertTrue(isValidBodyCm(299.9))
        assertFalse(isValidBodyCm(0.0))
        assertFalse(isValidBodyCm(300.0))
    }
}
