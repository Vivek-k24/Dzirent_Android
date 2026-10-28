package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceTest {

    @Test
    fun parsesWholeAndDecimalPrices() {
        assertEquals(25.0, parsePrice("25")!!, 0.0)
        assertEquals(25.5, parsePrice("25.5")!!, 0.0)
        assertEquals(25.99, parsePrice(" \$25.99 ")!!, 0.0)
        assertEquals(0.5, parsePrice(".50")!!, 0.0)
    }

    @Test
    fun rejectsInvalidPrices() {
        listOf("", "0", "0.00", "-5", "abc", "25.999", "1.2.3", "25,00", "10000.01").forEach {
            assertNull(it, parsePrice(it))
        }
    }

    @Test
    fun acceptsTheMaximum() {
        assertEquals(PRICE_MAX_USD, parsePrice("10000")!!, 0.0)
    }

    @Test
    fun formatsWholeDollarsWithoutCents() {
        assertEquals("\$25", formatPrice(25.0))
        assertEquals("\$1000", formatPrice(1000.0))
    }

    @Test
    fun formatsCentsWithTwoDigits() {
        assertEquals("\$25.50", formatPrice(25.5))
        assertEquals("\$25.99", formatPrice(25.99))
        assertEquals("\$0.50", formatPrice(0.5))
    }

    @Test
    fun inputTextDropsTrailingZeros() {
        assertEquals("25", priceInputText(25.0))
        assertEquals("25.5", priceInputText(25.5))
        assertEquals("", priceInputText(null))
    }
}
