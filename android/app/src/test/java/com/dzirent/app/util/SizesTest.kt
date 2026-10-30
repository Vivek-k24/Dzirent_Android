package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SizesTest {

    @Test
    fun theSizesFollowTheType() {
        fun kind(name: String) = ItemLabels.byName(name)!!.kind!!
        listOf("button-up shirt", "hoodie", "sweatshirt", "overcoat", "kurta").forEach { assertEquals(it, listOf(Sizes.LETTERS), Sizes.systemsFor(kind(it))) }
        listOf("trousers", "chinos").forEach { assertEquals(it, listOf(Sizes.WAIST_INSEAM), Sizes.systemsFor(kind(it))) }
        listOf("suit", "blazer", "tuxedo").forEach { assertEquals(it, listOf(Sizes.CHEST_LENGTH), Sizes.systemsFor(kind(it))) }
        listOf("gown", "cocktail dress").forEach { assertEquals(it, listOf(Sizes.LETTERS, Sizes.US), Sizes.systemsFor(kind(it))) }
        assertEquals(listOf(Sizes.LETTERS, Sizes.US), Sizes.systemsFor(kind("skirt")))
        listOf("saree", "lehenga").forEach { assertEquals(it, listOf(Sizes.BLOUSE), Sizes.systemsFor(kind(it))) }
        listOf("handbag", "sunglasses", "necklace").forEach { assertEquals(it, emptyList<SizeSystem>(), Sizes.systemsFor(kind(it))) }
        assertEquals("One size", Sizes.fixedLabel(GarmentKind.ONE_SIZE))
        assertNull(Sizes.fixedLabel(GarmentKind.TOP))
    }

    @Test
    fun rangesMatchTheBrief() {
        assertEquals(listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL"), Sizes.LETTERS.steps.single().options)
        val (waist, inseam) = Sizes.WAIST_INSEAM.steps
        assertEquals("26", waist.options.first()); assertEquals("58", waist.options.last())
        assertEquals("26", inseam.options.first()); assertEquals("36", inseam.options.last())
        val (chest, length) = Sizes.CHEST_LENGTH.steps
        assertEquals("34", chest.options.first()); assertEquals("56", chest.options.last())
        assertEquals(listOf("S", "R", "L"), length.options)
        assertEquals("0", Sizes.US.steps.single().options.first()); assertEquals("24", Sizes.US.steps.single().options.last())
        assertEquals("30", Sizes.BLOUSE.steps.single().options.first()); assertEquals("46", Sizes.BLOUSE.steps.single().options.last())
    }

    @Test
    fun sizeLabelFormats() {
        assertEquals("32 x 30", Sizes.WAIST_INSEAM.format(listOf("32", "30")))
        assertEquals("40R", Sizes.CHEST_LENGTH.format(listOf("40", "R")))
        assertEquals("Blouse 36", Sizes.BLOUSE.format(listOf("36")))
        assertEquals("US 8", Sizes.US.format(listOf("8")))
        assertEquals("M", Sizes.LETTERS.format(listOf("M")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aSizeNeedsEveryStep() {
        Sizes.WAIST_INSEAM.format(listOf("32"))
    }

    @Test
    fun existingSizeLabelsAreReadBack() {
        assertEquals(Sizes.WAIST_INSEAM to listOf("32", "30"), Sizes.parse(GarmentKind.TROUSERS, "32 x 30"))
        assertEquals(Sizes.CHEST_LENGTH to listOf("42", "L"), Sizes.parse(GarmentKind.SUIT, "42l"))
        assertEquals(Sizes.US to listOf("10"), Sizes.parse(GarmentKind.DRESS, "US 10"))
        assertEquals(Sizes.LETTERS to listOf("S"), Sizes.parse(GarmentKind.DRESS, "S"))
        assertNull(Sizes.parse(GarmentKind.TROUSERS, "M / US 8"))
    }
}
