package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {

    @Test
    fun samePointIsZero() {
        assertEquals(0.0, haversineKm(30.28, -97.74, 30.28, -97.74), 1e-9)
    }

    @Test
    fun austinToDallas() {
        // Downtown Austin to downtown Dallas, ~293 km.
        assertEquals(292.97, haversineKm(30.27, -97.74, 32.78, -96.80), 0.05)
    }

    @Test
    fun oneHundredthOfLatitudeIsAboutOneKm() {
        assertEquals(1.112, haversineKm(30.28, -97.74, 30.29, -97.74), 0.001)
    }

    @Test
    fun isSymmetric() {
        val there = haversineKm(30.24, -97.73, 30.34, -97.75)
        val back = haversineKm(30.34, -97.75, 30.24, -97.73)
        assertEquals(there, back, 1e-9)
    }

    @Test
    fun antipodesAreHalfTheEarthAround() {
        assertEquals(20015.09, haversineKm(0.0, 0.0, 0.0, 180.0), 0.01)
    }

    @Test
    fun roundsCoordinatesToTwoDecimals() {
        assertEquals(30.28, roundCoord(30.2849), 1e-9)
        assertEquals(-97.74, roundCoord(-97.7351), 1e-9)
        assertEquals(30.29, roundCoord(30.2851), 1e-9)
    }

    @Test
    fun formatsDistances() {
        assertEquals("Under 1 km", formatDistance(0.4, DistanceUnit.KILOMETERS))
        assertEquals("2.4 km", formatDistance(2.36, DistanceUnit.KILOMETERS))
        assertEquals("13 km", formatDistance(12.6, DistanceUnit.KILOMETERS))
    }

    @Test
    fun distancesInMiles() {
        assertEquals("Under 1 mi", formatDistance(1.5, DistanceUnit.MILES))
        assertEquals("2.4 mi", formatDistance(3.86, DistanceUnit.MILES))
        assertEquals("8.1 mi", formatDistance(13.0, DistanceUnit.MILES))
        assertEquals("20 mi", formatDistance(32.2, DistanceUnit.MILES))
    }

    @Test
    fun filterOptionsLineUpByStep() {
        assertEquals(listOf("1 mi", "3 mi", "5 mi", "15 mi"), DISTANCE_STEPS.map { DistanceUnit.MILES.optionLabel(it) })
        assertEquals(listOf("2 km", "5 km", "10 km", "25 km"), DISTANCE_STEPS.map { DistanceUnit.KILOMETERS.optionLabel(it) })
        assertEquals(8.04672, DistanceUnit.MILES.radiusKm(DEFAULT_DISTANCE_STEP), 1e-9)
        assertEquals(10.0, DistanceUnit.KILOMETERS.radiusKm(DEFAULT_DISTANCE_STEP), 1e-9)
        assertEquals(25.0, DistanceUnit.KILOMETERS.radiusKm(99), 1e-9)
        assertEquals(DistanceUnit.MILES, DistanceUnit.fromKey(null))
        assertEquals(DistanceUnit.KILOMETERS, DistanceUnit.fromKey("km"))
    }
}
