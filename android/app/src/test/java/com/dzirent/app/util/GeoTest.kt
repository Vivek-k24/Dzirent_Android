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
        assertEquals("Under 1 km", formatDistance(0.4))
        assertEquals("2.4 km", formatDistance(2.36))
        assertEquals("13 km", formatDistance(12.6))
    }
}
