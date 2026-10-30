package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemColorTest {

    private fun rgb(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b

    /** Votes from a garment made of [parts] (color to share), every pixel inside the item. */
    private fun garment(vararg parts: Pair<Int, Double>, n: Int = 4000): ColorVotes {
        val pixels = IntArray(n)
        var i = 0
        parts.forEach { (color, share) -> repeat((share * n).toInt()) { if (i < n) pixels[i++] = color } }
        while (i < n) pixels[i++] = parts.first().first
        // One row, two pixels per sample (colorVotes samples every other pixel and row).
        val image = RgbImage(n, 1, pixels)
        return colorVotes(image, BooleanArray(n) { true })
    }

    @Test
    fun everyTypicalValueNamesItsOwnColor() {
        ColorName.entries.forEach { name ->
            name.samples.forEach { (r, g, b) -> assertEquals("$r,$g,$b", name, nearestColorName(r, g, b)) }
        }
    }

    @Test
    fun neverGoldOrSilver() {
        assertTrue(ColorName.entries.none { it.display == "Gold" || it.display == "Silver" })
        assertEquals(ColorName.YELLOW, nearestColorName(212, 175, 55))  // "gold"
        assertEquals(ColorName.YELLOW, nearestColorName(200, 160, 60))
        assertEquals(ColorName.GREY, nearestColorName(192, 192, 198))   // "silver"
        assertEquals(ColorName.YELLOW, nameColors(garment(rgb(212, 175, 55) to 1.0)).primary)
    }

    @Test
    fun commonShadesGetTheExpectedName() {
        assertEquals(ColorName.MAROON, nearestColorName(128, 0, 32))
        assertEquals(ColorName.RED, nearestColorName(230, 20, 20))
        assertEquals(ColorName.WHITE, nearestColorName(255, 255, 255))
        assertEquals(ColorName.EMERALD, nearestColorName(0, 105, 75))
        assertEquals(ColorName.PINK, nearestColorName(255, 150, 190))
        assertEquals(ColorName.BEIGE, nearestColorName(225, 205, 170))
        assertEquals(ColorName.BROWN, nearestColorName(100, 60, 35))
        assertEquals(ColorName.ORANGE, nearestColorName(240, 130, 30))
    }

    @Test
    fun navyAndBlackAreToldApartByHowBlueTheDarkPixelsAre() {
        // Very dark, but clearly blue: navy, even if each pixel alone reads as black.
        assertEquals(ColorName.NAVY, nameColors(garment(rgb(12, 14, 42) to 1.0)).primary)
        assertEquals(ColorName.NAVY, nameColors(garment(rgb(25, 35, 85) to 1.0)).primary)
        // Neutral dark: black, even with a slight blue cast.
        assertEquals(ColorName.BLACK, nameColors(garment(rgb(18, 18, 24) to 1.0)).primary)
        assertEquals(ColorName.BLACK, nameColors(garment(rgb(30, 30, 34) to 1.0)).primary)
    }

    @Test
    fun skinTonesAreLeftOut() {
        val skin = rgb(224, 172, 140)
        assertTrue(looksLikeSkin(224, 172, 140))
        assertFalse(looksLikeSkin(25, 35, 85))
        // A navy dress worn by a model: 30% arms and face, never a second color.
        val dress = nameColors(garment(rgb(25, 35, 85) to 0.7, skin to 0.3))
        assertEquals(ColorResult(ColorName.NAVY, null), dress)
        // A beige dress: its "skin" pixels are the fabric.
        assertEquals(ColorName.BEIGE, nameColors(garment(rgb(220, 200, 165) to 1.0)).primary)
    }

    @Test
    fun theBackgroundIsLeftOut() {
        // A maroon item on a large white sheet: only the garment area found by the framing check counts.
        val w = 200
        val h = 160
        val image = RgbImage(w, h, IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x in 70 until 130 && y in 50 until 110) rgb(128, 0, 32) else rgb(240, 240, 240)
        })
        val item = findItem(image)
        assertTrue(item.region != null)
        assertEquals(ColorResult(ColorName.MAROON, null), dominantColors(image))
    }

    @Test
    fun colorsTooCloseToCallAreLeftOut() {
        assertEquals(ColorResult.NONE, nameColors(garment(rgb(205, 30, 35) to 0.5, rgb(40, 95, 195) to 0.5)))
        // Too few pixels to say anything.
        assertEquals(ColorResult.NONE, nameColors(ColorVotes()))
        // Nothing has a clear majority.
        assertEquals(
            ColorResult.NONE,
            nameColors(garment(rgb(205, 30, 35) to 0.3, rgb(40, 95, 195) to 0.25, rgb(60, 145, 60) to 0.25, rgb(242, 210, 50) to 0.2))
        )
    }

    @Test
    fun aClearSecondColorIsNamed() {
        val saree = nameColors(garment(rgb(242, 210, 50) to 0.75, rgb(205, 30, 35) to 0.25))
        assertEquals(ColorResult(ColorName.YELLOW, ColorName.RED), saree)
        assertEquals("Yellow saree with red border.", suggestionsFor(ItemLabels.byName("saree")!!, saree).description)
        // Shades of the same color (red with maroon shadows) are one color, not two.
        assertEquals(ColorResult(ColorName.RED, null), nameColors(garment(rgb(205, 30, 35) to 0.7, rgb(120, 15, 35) to 0.3)))
        // A small fleck is not a second color.
        assertEquals(ColorResult(ColorName.YELLOW, null), nameColors(garment(rgb(242, 210, 50) to 0.95, rgb(205, 30, 35) to 0.05)))
    }

    @Test
    fun votesFromSeveralPhotosAddUp() {
        val front = garment(rgb(40, 95, 195) to 1.0)
        val back = garment(rgb(40, 95, 195) to 1.0)
        assertEquals(front.total + back.total, (front + back).total)
        assertEquals(ColorName.BLUE, nameColors(front + back).primary)
    }
}
