package com.dzirent.app.util

import com.dzirent.app.data.model.Categories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class SearchRelatedTest {

    private val json = File("src/main/assets/search_related.json").readText()
    private val related = SearchRelated.parse(json)

    @Test
    fun keywordFindsItsRelatedWordsAndCategory() {
        val match = related.lookup("lehenga")!!
        assertEquals("lehenga", match.keyword)
        assertEquals(listOf("ghagra", "chaniya choli", "saree", "anarkali", "maxi skirt"), match.words)
        assertEquals("Cultural & Traditional", match.category)
    }

    @Test
    fun matchingIgnoresCaseAccentsAndPlurals() {
        assertEquals("lehenga", related.lookup("LEHENGAS")!!.keyword)
        assertEquals("saree", related.lookup("silk sarees")!!.keyword)
        assertEquals("tux", related.lookup("Tuxes")!!.keyword)
        assertEquals("quinceanera", related.lookup("Quinceañera dress")!!.keyword)
        assertEquals("watch", related.lookup("vintage watches")!!.keyword)
    }

    @Test
    fun misspellingGivesTheCorrectKeyword() {
        val match = related.lookup("red lehnga")!!
        assertEquals("lehenga", match.keyword)
        assertEquals("ghagra", match.words.first())
        assertEquals("Cultural & Traditional", match.category)
        assertEquals("tuxedo", related.lookup("tuxido")!!.keyword)
    }

    @Test
    fun longestPhraseWins() {
        assertEquals("wedding guest", related.lookup("outfit for a wedding guest")!!.keyword)
        assertEquals("cocktail dress", related.lookup("black cocktail dresses")!!.keyword)
        assertEquals("Occasion & Party", related.lookup("wedding guest")!!.category)
    }

    @Test
    fun unknownWordsFindNothing() {
        assertNull(related.lookup("zzz"))
        assertNull(related.lookup(""))
    }

    @Test
    fun theExamplesFromTheBrief() {
        assertEquals(listOf("tuxedo", "suit", "blazer"), related.lookup("tux")!!.words)
        assertEquals(listOf("blazer", "suit", "dress shirt", "trousers"), related.lookup("interview")!!.words)
        assertEquals(listOf("gown", "cocktail dress", "saree", "lehenga", "suit"), related.lookup("wedding guest")!!.words)
    }

    @Test
    fun theFileCoversEveryCategoryWithValidHints() {
        val root = Json.parseToJsonElement(json).jsonObject
        val hints = root.getValue("category_hints").jsonObject
        val values = hints.values.map { it.jsonPrimitive.content }.toSet()
        assertTrue("unknown category in hints: ${values - Categories.ALL.toSet()}", Categories.ALL.containsAll(values))
        assertEquals(Categories.ALL.toSet(), values)
        val keys = root.getValue("related").jsonObject.keys
        assertTrue("about 30 keywords", keys.size >= 30)
        // Every hint has related words, so no category is suggested without them.
        assertTrue((hints.keys - keys).isEmpty())
    }
}
