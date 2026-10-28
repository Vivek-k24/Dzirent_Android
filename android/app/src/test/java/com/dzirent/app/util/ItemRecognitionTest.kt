package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemRecognitionTest {

    private fun label(name: String) = ItemLabels.byName(name)!!

    /** Scores with [top] at [p] and the rest spread evenly over the other labels. */
    private fun scores(top: String, p: Double): List<LabelScore> {
        val rest = (1 - p) / (ItemLabels.ALL.size - 1)
        return ItemLabels.ALL.map { LabelScore(it, if (it.name == top) p else rest) }.sortedByDescending { it.probability }
    }

    // ---- Color naming --------------------------------------------------------------------

    @Test
    fun everyTypicalValueNamesItsOwnColor() {
        ColorName.entries.forEach { name ->
            name.samples.forEach { (r, g, b) -> assertEquals("$r,$g,$b", name, nearestColorName(r, g, b)) }
        }
    }

    @Test
    fun commonShadesGetTheExpectedName() {
        assertEquals(ColorName.MAROON, nearestColorName(128, 0, 32))
        assertEquals(ColorName.RED, nearestColorName(230, 20, 20))
        assertEquals(ColorName.NAVY, nearestColorName(0, 0, 110))
        assertEquals(ColorName.BLACK, nearestColorName(5, 5, 8))
        assertEquals(ColorName.WHITE, nearestColorName(255, 255, 255))
        assertEquals(ColorName.GOLD, nearestColorName(212, 175, 55))
        assertEquals(ColorName.EMERALD, nearestColorName(0, 105, 75))
        assertEquals(ColorName.PINK, nearestColorName(255, 150, 190))
        assertEquals(ColorName.BEIGE, nearestColorName(225, 205, 170))
        assertEquals(ColorName.BROWN, nearestColorName(100, 60, 35))
    }

    @Test
    fun dominantColorComesFromTheMiddle() {
        // A maroon item in the middle of a big white border: the border doesn't vote.
        val w = 200
        val h = 160
        val image = RgbImage(w, h, IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x in 50 until 150 && y in 40 until 120) 0x800020 else 0xFFFFFF
        })
        assertEquals(ColorName.MAROON, dominantColorName(image))
        assertEquals(16, ColorName.entries.size)
    }

    // ---- Labels, tiers, categories --------------------------------------------------------

    @Test
    fun labelsMapToTiersCategoriesAndItemTypes() {
        assertEquals(LabelTier.ALLOWED, label("lehenga").tier)
        assertEquals("Cultural & Traditional", label("lehenga").category)
        assertEquals("Career & Professional", label("blazer").category)
        assertEquals("Outerwear", label("trench coat").category)
        assertEquals("Occasion & Party", label("gown").category)
        assertEquals("Accessories", label("potli bag").category)
        assertEquals("Handbag", label("potli bag").itemType)
        assertEquals("Handbag", label("clutch").itemType)
        assertEquals("Watch (non-smart)", label("watch").itemType)
        assertEquals("Tie", label("tie").itemType)
        assertEquals(null, label("blazer").itemType)
        assertEquals(LabelTier.NOT_ALLOWED, label("smartwatch").tier)
        assertEquals(LabelTier.NOT_ALLOWED, label("cowboy hat").tier)
        assertEquals(LabelTier.NOT_OFFERED, label("jeans").tier)
        assertEquals(LabelTier.NOT_OFFERED, label("t-shirt").tier)
    }

    @Test
    fun theListMatchesTheBrief() {
        assertEquals(32, ItemLabels.ALL.count { it.tier == LabelTier.ALLOWED })
        assertEquals(
            listOf("lingerie", "underwear", "swimwear", "socks", "pantyhose", "shoes", "boots", "hat", "cap", "cowboy hat", "smartwatch"),
            ItemLabels.ALL.filter { it.tier == LabelTier.NOT_ALLOWED }.map { it.name }
        )
        assertEquals(
            listOf("t-shirt", "track pants", "sweatpants", "joggers", "shorts", "jean shorts", "jeans"),
            ItemLabels.ALL.filter { it.tier == LabelTier.NOT_OFFERED }.map { it.name }
        )
        assertEquals(ItemLabels.NAMES.size, ItemLabels.NAMES.toSet().size)
    }

    // ---- Suggestions and the confidence cutoff -----------------------------------------------

    @Test
    fun suggestionTextUsesColorAndType() {
        val s = suggestionsFor(label("lehenga"), ColorName.MAROON)
        assertEquals("Maroon lehenga", s.title)
        assertEquals("Cultural & Traditional", s.category)
        assertEquals(null, s.itemType)
        assertEquals("Maroon lehenga. Add the fabric, fit and any flaws.", s.description)

        val bag = suggestionsFor(label("potli bag"), ColorName.GOLD)
        assertEquals("Gold potli bag", bag.title)
        assertEquals("Accessories", bag.category)
        assertEquals("Handbag", bag.itemType)

        assertEquals("Blazer", suggestionsFor(label("blazer"), null).title)
    }

    @Test
    fun suggestionsOnlyAboveTheConfidenceCutoff() {
        val confident = recognize(scores("lehenga", PhotoThresholds.SUGGEST_CONFIDENCE), ColorName.MAROON)
        assertTrue(confident is Recognition.Suggest)
        assertEquals("Maroon lehenga", (confident as Recognition.Suggest).suggestions.title)

        val unsure = recognize(scores("lehenga", PhotoThresholds.SUGGEST_CONFIDENCE - 0.01), ColorName.MAROON)
        assertEquals(Recognition.Unsure, unsure)
    }

    @Test
    fun nothingAllowedOnTopMeansNoSuggestion() {
        // A not-offered label on top but below the policy cutoff: neither a warning nor a suggestion.
        assertEquals(Recognition.Unsure, recognize(scores("jeans", 0.5), null))
        assertEquals(Recognition.Unsure, recognize(emptyList(), null))
    }

    // ---- NOT_ALLOWED vs NOT_OFFERED ------------------------------------------------------------

    @Test
    fun notAllowedMessage() {
        val r = recognize(scores("hat", 0.9), ColorName.BLACK)
        assertTrue(r is Recognition.Policy)
        assertEquals("This looks like a hat. Dzirent doesn't allow hats.", (r as Recognition.Policy).message)
        assertEquals("This looks like a smartwatch. Dzirent doesn't allow smartwatches.", policyMessage(label("smartwatch")))
        assertEquals("This looks like lingerie. Dzirent doesn't allow lingerie.", policyMessage(label("lingerie")))
        assertEquals("This looks like socks. Dzirent doesn't allow socks.", policyMessage(label("socks")))
    }

    @Test
    fun notOfferedMessage() {
        val r = recognize(scores("t-shirt", 0.9), null)
        assertEquals(
            "This looks like a T-shirt. Dzirent focuses on pieces worth renting, so everyday basics aren't listed.",
            (r as Recognition.Policy).message
        )
        assertEquals(
            "This looks like jeans. Dzirent focuses on pieces worth renting, so everyday basics aren't listed.",
            policyMessage(label("jeans"))
        )
    }

    @Test
    fun policyConfidenceAddsUpAGroup() {
        // Hat 0.35 + cap 0.30 (+ a little cowboy hat) > 0.6 for "hats and caps": a warning,
        // named after the likelier label, though neither label alone is above the cutoff.
        val other = 0.35 / (ItemLabels.ALL.size - 2)
        val split = ItemLabels.ALL.map { LabelScore(it, when (it.name) { "hat" -> 0.35; "cap" -> 0.30; else -> other }) }
        val r = recognize(split, null)
        assertEquals("This looks like a hat. Dzirent doesn't allow hats.", (r as Recognition.Policy).message)
        assertEquals(0.65 + other, r.probability, 1e-9)

        // Below the cutoff: nothing.
        assertEquals(Recognition.Unsure, recognize(scores("hat", PhotoThresholds.POLICY_CONFIDENCE - 0.05), null))
    }

    @Test
    fun policyTextUsesTheSameLists() {
        assertEquals(
            "Not allowed: lingerie, underwear, swimwear, socks and hosiery, shoes and boots, hats and caps, smartwatches.",
            ItemLabels.notAllowedText
        )
        assertEquals(
            "Not offered: everyday basics like T-shirts, track pants, sweatpants, joggers, shorts and jeans.",
            ItemLabels.notOfferedText
        )
        // Every not-allowed / not-offered label is covered by a group in the text, and every group has a label.
        val groups = (ItemLabels.NOT_ALLOWED_GROUPS + ItemLabels.NOT_OFFERED_GROUPS).associateBy { it.text }
        ItemLabels.ALL.filter { it.tier != LabelTier.ALLOWED }.forEach { label ->
            val group = groups[label.policyGroup]
            assertTrue("${label.name} has no policy group", group != null)
            assertEquals(label.tier, group!!.tier)
        }
        groups.keys.forEach { text -> assertTrue("$text covers no label", ItemLabels.ALL.any { it.policyGroup == text }) }
    }

    // ---- Embedding math --------------------------------------------------------------------

    @Test
    fun cosineSimilarityAndNormalization() {
        val a = floatArrayOf(1f, 0f, 0f)
        assertEquals(1.0, cosineSimilarity(a, floatArrayOf(3f, 0f, 0f)), 1e-9)
        assertEquals(0.0, cosineSimilarity(a, floatArrayOf(0f, 2f, 0f)), 1e-9)
        assertEquals(-1.0, cosineSimilarity(a, floatArrayOf(-1f, 0f, 0f)), 1e-9)
        assertEquals(0.0, cosineSimilarity(a, floatArrayOf(0f, 0f, 0f)), 1e-9)
        val n = normalized(floatArrayOf(3f, 4f))
        assertEquals(0.6f, n[0], 1e-6f)
        assertEquals(0.8f, n[1], 1e-6f)
    }

    @Test
    fun softmaxIsStableAndSumsToOne() {
        val p = softmax(doubleArrayOf(1000.0, 1000.0, 999.0))
        assertEquals(1.0, p.sum(), 1e-9)
        assertEquals(p[0], p[1], 1e-12)
        assertTrue(p[2] < p[0])
    }

    @Test
    fun scoreLabelsPicksTheClosestText() {
        val labels = listOf(label("saree"), label("blazer"), label("hat"))
        val text = listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f))
        val ranked = scoreLabels(normalized(floatArrayOf(0.2f, 0.9f, 0.1f)), text, labels)
        assertEquals("blazer", ranked.first().label.name)
        assertEquals(1.0, ranked.sumOf { it.probability }, 1e-9)
    }

    @Test
    fun differentItemBelowTheSimilarityCutoff() {
        val cover = normalized(floatArrayOf(1f, 0f))
        val cut = PhotoThresholds.SAME_ITEM_MIN_SIMILARITY
        val same = floatArrayOf(cut.toFloat() + 0.01f, kotlin.math.sqrt(1 - (cut + 0.01) * (cut + 0.01)).toFloat())
        val other = floatArrayOf(cut.toFloat() - 0.01f, kotlin.math.sqrt(1 - (cut - 0.01) * (cut - 0.01)).toFloat())
        assertFalse(looksLikeDifferentItem(cover, same))
        assertTrue(looksLikeDifferentItem(cover, other))
    }

    // ---- Model input --------------------------------------------------------------------------

    @Test
    fun clipInputNormalizesInEitherLayout() {
        val n = ClipInput.SIZE * ClipInput.SIZE
        val image = RgbImage(ClipInput.SIZE, ClipInput.SIZE, IntArray(n) { 0xFF8000 }) // R 255, G 128, B 0
        val last = ClipInput.toFloats(image, channelsFirst = false)
        val first = ClipInput.toFloats(image, channelsFirst = true)
        val r = (1f - ClipInput.MEAN[0]) / ClipInput.STD[0]
        val g = (128 / 255f - ClipInput.MEAN[1]) / ClipInput.STD[1]
        val b = (0f - ClipInput.MEAN[2]) / ClipInput.STD[2]
        assertEquals(r, last[0], 1e-5f); assertEquals(g, last[1], 1e-5f); assertEquals(b, last[2], 1e-5f)
        assertEquals(r, first[0], 1e-5f); assertEquals(g, first[n], 1e-5f); assertEquals(b, first[2 * n], 1e-5f)
        assertEquals(listOf(40, 0, 160), ClipInput.centerSquare(240, 160).toList())
    }
}
