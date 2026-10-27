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
    fun rangeMatchesDatabaseCheck() {
        assertTrue(isValidBodyCm(0.1))
        assertTrue(isValidBodyCm(299.9))
        assertFalse(isValidBodyCm(0.0))
        assertFalse(isValidBodyCm(300.0))
    }
}
