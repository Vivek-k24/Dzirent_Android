package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementsTest {

    private fun errorFor(label: String, value: String) = validateMeasurements(listOf(MeasurementInput(label, value)))[0]

    @Test
    fun validRowsPass() {
        assertEquals(
            listOf(null, null),
            validateMeasurements(listOf(MeasurementInput("Chest", "40"), MeasurementInput("Waist", "32.5")))
        )
    }

    @Test
    fun valueMustBeAboveZeroAndBelowOneThousand() {
        assertEquals("Must be more than 0 and less than 1000.", errorFor("Chest", "0"))
        assertEquals("Must be more than 0 and less than 1000.", errorFor("Chest", "-3"))
        assertEquals("Must be more than 0 and less than 1000.", errorFor("Chest", "1000"))
        assertNull(errorFor("Chest", "999.9"))
        assertNull(errorFor("Chest", "0.5"))
    }

    @Test
    fun valueMustBeANumber() {
        assertEquals("Enter a number.", errorFor("Chest", ""))
        assertEquals("Enter a number, like 38 or 38.5.", errorFor("Chest", "forty"))
    }

    @Test
    fun labelMustBeOneToFortyCharacters() {
        assertEquals("Add a label.", errorFor("   ", "40"))
        assertNull(errorFor("x".repeat(40), "40"))
        assertEquals("Label can be up to 40 characters.", errorFor("x".repeat(41), "40"))
    }

    @Test
    fun duplicateLabelsAreRejectedCaseInsensitively() {
        val errors = validateMeasurements(
            listOf(MeasurementInput("Chest", "40"), MeasurementInput(" chest ", "41"), MeasurementInput("Waist", "30"))
        )
        assertEquals(listOf(null, "\"chest\" is already listed.", null), errors)
    }

    @Test
    fun blankRowsAreIgnored() {
        assertEquals(
            listOf(null, null),
            validateMeasurements(listOf(MeasurementInput("", ""), MeasurementInput("Chest", "40")))
        )
    }
}
