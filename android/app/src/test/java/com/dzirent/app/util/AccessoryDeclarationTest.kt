package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessoryDeclarationTest {

    private val all = List(AccessoryDeclaration.STATEMENTS.size) { true }

    @Test
    fun theWordingIsInOnePlaceWithAVersion() {
        assertEquals("Accessory declaration", AccessoryDeclaration.TITLE)
        assertEquals(4, AccessoryDeclaration.STATEMENTS.size)
        assertEquals(
            "This accessory or jewelry is not real silver, gold or platinum, has no pearls and no diamonds or precious gemstones.",
            AccessoryDeclaration.STATEMENTS[0]
        )
        assertEquals("It is worth less than \$300.", AccessoryDeclaration.STATEMENTS[2])
        assertEquals("I agree and sign", AccessoryDeclaration.SIGN_BUTTON)
        assertEquals("Full legal name", AccessoryDeclaration.NAME_LABEL)
        assertTrue(AccessoryDeclaration.VERSION >= 1)
        assertTrue(AccessoryDeclaration.TEXT.contains("(v${AccessoryDeclaration.VERSION})"))
        AccessoryDeclaration.STATEMENTS.forEach { assertTrue(AccessoryDeclaration.TEXT.contains(it)) }
        assertEquals("This item isn't fine jewelry or precious metal and is worth under \$300.", AccessoryDeclaration.LISTING_CHECKBOX)
    }

    @Test
    fun everyBoxAndTheFullNameAreRequired() {
        assertTrue(AccessoryDeclaration.canSign(all, "Alex Example"))
        for (i in all.indices) {
            assertFalse("box $i unticked", AccessoryDeclaration.canSign(all.toMutableList().also { it[i] = false }, "Alex Example"))
        }
        assertFalse(AccessoryDeclaration.canSign(all, ""))
        assertFalse(AccessoryDeclaration.canSign(all, "   "))
        assertFalse(AccessoryDeclaration.canSign(all, "A."))
        assertFalse(AccessoryDeclaration.canSign(all.dropLast(1), "Alex Example"))
        assertNull(AccessoryDeclaration.nameError("Alex Example"))
        assertEquals("Type your full legal name.", AccessoryDeclaration.nameError("1"))
    }

    @Test
    fun neededOncePerUser() {
        // First accessory listing, or jewelry found: needed until signed.
        assertTrue(declarationNeeded("Accessories", jewelryFound = false, signed = false))
        assertTrue(declarationNeeded("Everyday", jewelryFound = true, signed = false))
        // Signed once: never again.
        assertFalse(declarationNeeded("Accessories", jewelryFound = true, signed = true))
        // Nothing to declare.
        assertFalse(declarationNeeded("Everyday", jewelryFound = false, signed = false))
        assertFalse(declarationNeeded(null, jewelryFound = false, signed = false))
    }
}
