package com.dzirent.app.util

import com.dzirent.app.data.model.AccessoryTypes
import com.dzirent.app.data.model.Categories
import com.dzirent.app.ui.DzirentPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemRecognitionTest {

    private fun label(name: String) = ItemLabels.byName(name)!!

    /** Label scores with [top] at [p] and the rest spread evenly over the other labels. */
    private fun labels(top: Map<String, Double>): List<LabelScore> {
        val rest = (1 - top.values.sum()) / (ItemLabels.ALL.size - top.size)
        return ItemLabels.ALL.map { LabelScore(it, top[it.name] ?: rest) }.sortedByDescending { it.probability }
    }

    private fun scores(top: String, p: Double, notFashion: Double = 0.0) = PhotoScores(labels(mapOf(top to p)), notFashion)

    // ---- One constant set: tiers, categories, styles ----------------------------------------

    @Test
    fun labelsMapToTiersCategoriesItemTypesAndStyles() {
        assertEquals(LabelTier.ALLOWED, label("lehenga").tier)
        assertEquals("Cultural & Traditional", label("lehenga").category)
        assertEquals("Career & Professional", label("blazer").category)
        assertEquals("Handbag", label("potli bag").itemType)
        assertEquals("Watch (non-smart)", label("watch").itemType)
        assertEquals(LabelTier.NOT_FASHION, label("bicycle").tier)
        assertEquals(LabelTier.NOT_ALLOWED, label("smartwatch").tier)
        assertEquals(LabelTier.NOT_ALLOWED, label("cowboy hat").tier)
        assertEquals(LabelTier.JEWELRY, label("necklace").tier)
        assertEquals(AccessoryTypes.COSTUME_JEWELRY, label("pearl jewelry").itemType)
        assertEquals(Categories.ACCESSORIES, label("brooch").category)
        assertEquals(LabelTier.NOT_OFFERED, label("jeans").tier)
    }

    @Test
    fun hoodiesAndSweatshirtsAreAllowedEveryday() {
        listOf("hoodie", "sweatshirt").forEach {
            assertEquals(LabelTier.ALLOWED, label(it).tier)
            assertEquals("Everyday", label(it).category)
        }
    }

    @Test
    fun theTiersMatchTheBrief() {
        fun names(tier: LabelTier) = ItemLabels.ALL.filter { it.tier == tier }.map { it.name }
        assertEquals(
            listOf("bicycle", "tent", "kayak", "paddleboard", "ski or snowboard gear", "lawn mower", "power tool", "toolbox",
                "furniture", "phone", "laptop", "electronics", "car", "pet", "food", "document or card"),
            names(LabelTier.NOT_FASHION)
        )
        assertEquals(
            listOf("lingerie", "underwear", "swimwear", "socks", "pantyhose", "shoes", "boots", "hat", "cap", "cowboy hat", "smartwatch"),
            names(LabelTier.NOT_ALLOWED)
        )
        assertTrue(names(LabelTier.JEWELRY).containsAll(listOf("necklace", "earrings", "bracelet", "ring", "pendant", "brooch")))
        assertEquals(
            listOf("t-shirt", "track pants", "sweatpants", "joggers", "shorts", "jean shorts", "jeans"),
            names(LabelTier.NOT_OFFERED)
        )
        assertEquals(ItemLabels.NAMES.size, ItemLabels.NAMES.toSet().size)
    }

    @Test
    fun styleFollowsTheType() {
        listOf("saree", "lehenga", "kurta").forEach { assertEquals("South Asian", label(it).style) }
        listOf("western shirt", "belt buckle").forEach { assertEquals("Country & Western", label(it).style) }
        listOf("suit", "blazer", "tuxedo").forEach { assertEquals("Western formal", label(it).style) }
        listOf("hoodie", "sweatshirt", "button-up shirt", "denim jacket").forEach { assertEquals("Western casual", label(it).style) }
        assertEquals("South Asian", suggestionsFor(label("saree")).style)
    }

    @Test
    fun accessoryItemTypesIncludeCostumeJewelry() {
        assertEquals(
            listOf("Sunglasses", "Handbag", "Tie", "Watch (non-smart)", "Belt buckle", "Costume jewelry"),
            AccessoryTypes.ALL
        )
    }

    // ---- Policy text from the same constants --------------------------------------------------

    @Test
    fun policyTextIsBuiltFromTheLabelConstants() {
        assertEquals(
            "Not allowed: lingerie, underwear, swimwear, socks and hosiery, shoes and boots, hats and caps, smartwatches, " +
                "real gold, silver, platinum, diamonds, pearls or gemstones, and accessories worth more than \$300.",
            ItemLabels.notAllowedText
        )
        assertEquals(
            "Not offered: everyday basics like T-shirts, track pants, sweatpants, joggers, shorts and jeans.",
            ItemLabels.notOfferedText
        )
        // Every not-allowed, jewelry and not-offered label is covered by a group in the text, and every group has a label.
        val groups = (ItemLabels.NOT_ALLOWED_GROUPS + ItemLabels.NOT_OFFERED_GROUPS + ItemLabels.JEWELRY_GROUP).associateBy { it.text }
        ItemLabels.ALL.filter { it.tier in setOf(LabelTier.NOT_ALLOWED, LabelTier.NOT_OFFERED, LabelTier.JEWELRY) }.forEach { l ->
            assertEquals("${l.name} has no policy group", l.tier, groups[l.policyGroup]?.tier)
        }
        groups.keys.forEach { text -> assertTrue("$text covers no label", ItemLabels.ALL.any { it.policyGroup == text }) }
        assertTrue(ItemLabels.notAllowedText.contains(ItemLabels.ACCESSORY_MAX_VALUE_USD.toString()))
    }

    @Test
    fun communityPolicyAndWhatCanIListUseTheSameText() {
        assertEquals(
            "Dzirent is for clothing, bags and fashion accessories only.\n" + ItemLabels.notAllowedText + "\n" + ItemLabels.notOfferedText,
            DzirentPolicy.ITEM_RULES
        )
        assertTrue(DzirentPolicy.PROHIBITED_ITEMS.startsWith(DzirentPolicy.ITEM_RULES))
        assertTrue(DzirentPolicy.CAN_LIST.contains("costume jewelry"))
        assertTrue(DzirentPolicy.JEWELRY_NOTE.contains(ItemLabels.JEWELRY_GROUP.text))
        assertTrue(DzirentPolicy.CHAT_SAFETY_TIP.endsWith("We recommend washing anything worn against the skin before wearing."))
    }

    // ---- Tier check per photo ------------------------------------------------------------------

    @Test
    fun notFashionIsRemovedWhenBothSignalsAgree() {
        val r = tierVerdict(scores("bicycle", 0.8, notFashion = 0.9))
        assertTrue(r is TierVerdict.Remove)
        r as TierVerdict.Remove
        assertEquals(LabelTier.NOT_FASHION, r.tier)
        assertEquals("Removed: this looks like a bike. Dzirent is for clothing and fashion accessories.", r.message)
        assertEquals("It's fashion, keep it", keepLabel(r.tier))
    }

    @Test
    fun oneNotFashionSignalAloneRemovesNothing() {
        // The labels say bike but the general check says fashion (a cycling jersey on a bike, say).
        assertEquals(TierVerdict.Fashion, tierVerdict(scores("bicycle", 0.8, notFashion = 0.3)))
        // The general check alone, with the labels on a gown.
        assertEquals(TierVerdict.Fashion, tierVerdict(scores("gown", 0.8, notFashion = 0.9)))
    }

    @Test
    fun notAllowedIsRemovedAtHighConfidence() {
        val r = tierVerdict(scores("hat", 0.8)) as TierVerdict.Remove
        assertEquals(LabelTier.NOT_ALLOWED, r.tier)
        assertEquals("Removed: this looks like a hat. Dzirent doesn't allow hats.", r.message)
        // Hat 0.40 + cap 0.35 count together for "hats and caps".
        val split = tierVerdict(PhotoScores(labels(mapOf("hat" to 0.40, "cap" to 0.35))))
        assertEquals("hat", (split as TierVerdict.Remove).label!!.name)
        // Below the removal cutoff: nothing.
        assertEquals(TierVerdict.Fashion, tierVerdict(scores("hat", PhotoThresholds.REMOVE_CONFIDENCE - 0.05)))
    }

    @Test
    fun jewelryAsksForTheDeclarationAndIsNeverRemoved() {
        val r = tierVerdict(scores("necklace", 0.7))
        assertTrue(r is TierVerdict.Jewelry)
        // Even looking very much like gold, it's jewelry, not a removal: a camera can't tell plated from real.
        assertTrue(tierVerdict(scores("gold jewelry", 0.99, notFashion = 0.0)) is TierVerdict.Jewelry)
    }

    @Test
    fun notOfferedIsOnlyAWarning() {
        val r = tierVerdict(scores("t-shirt", 0.9)) as TierVerdict.NotOffered
        assertEquals(
            "This looks like a T-shirt. Dzirent focuses on pieces worth renting, so everyday basics aren't listed.",
            r.message
        )
        assertEquals(TierVerdict.Fashion, tierVerdict(scores("jeans", 0.5)))
    }

    // ---- What it is, from all the photos --------------------------------------------------------

    @Test
    fun confidentSuggestionFillsTitleCategoryDescriptionStyle() {
        val g = guessItem(PhotoScores(labels(mapOf("lehenga" to 0.6))), ColorResult(ColorName.MAROON)) as ItemGuess.Suggest
        val s = g.suggestions
        assertEquals("Maroon lehenga", s.title)
        assertEquals("Cultural & Traditional", s.category)
        assertEquals("Maroon lehenga.", s.description)
        assertEquals("South Asian", s.style)
        assertEquals(GarmentKind.SAREE, s.kind)
    }

    @Test
    fun descriptionIsOnlyTheRealSentence() {
        val s = suggestionsFor(label("saree"), ColorResult(ColorName.YELLOW, ColorName.RED))
        assertEquals("Yellow saree", s.title)
        assertEquals("Yellow saree with red border.", s.description)
        assertFalse(s.description.contains(DESCRIPTION_TIP))
        assertFalse(s.description.contains("["))
        // The tip and the fabric guesses are only the hint under the field.
        assertEquals(DESCRIPTION_TIP, descriptionHint(null))
        val fabrics = DoubleArray(ItemLabels.FABRICS.size).also {
            it[ItemLabels.FABRICS.indexOf("silk")] = 0.5
            it[ItemLabels.FABRICS.indexOf("satin")] = 0.3
        }
        assertEquals("Might be silk or satin. Add the fabric, fit and any flaws.", descriptionHint(fabrics))
    }

    @Test
    fun titleNamesAConfidentPattern() {
        val shirt = label("button-up shirt")
        assertEquals("Navy polka-dot shirt", suggestionsFor(shirt, ColorResult(ColorName.NAVY), Pattern.POLKA_DOT).title)
        assertEquals("Navy shirt", suggestionsFor(shirt, ColorResult(ColorName.NAVY), Pattern.SOLID).title)
        assertEquals("Striped shirt", suggestionsFor(shirt, ColorResult.NONE, Pattern.STRIPED).title)
        fun withPattern(p: Pattern, prob: Double) = PhotoScores(
            labels(mapOf("button-up shirt" to 0.7)),
            patterns = DoubleArray(Pattern.entries.size) { if (it == p.ordinal) prob else (1 - prob) / (Pattern.entries.size - 1) }
        )
        assertEquals(Pattern.POLKA_DOT, patternOf(withPattern(Pattern.POLKA_DOT, 0.8)))
        assertNull(patternOf(withPattern(Pattern.POLKA_DOT, 0.5)))
        val guessed = guessItem(withPattern(Pattern.FLORAL, 0.9), ColorResult(ColorName.PINK)) as ItemGuess.Suggest
        assertEquals("Pink floral shirt", guessed.suggestions.title)
        assertEquals(listOf("solid", "striped", "polka dot", "floral", "plaid", "embroidered", "sequined"), ItemLabels.PATTERNS.map { it.key })
    }

    @Test
    fun secondColorIsABorderOnlyForSareesAndDupattas() {
        val colors = ColorResult(ColorName.BLUE, ColorName.WHITE)
        assertEquals("Blue dupatta with white border.", suggestionsFor(label("dupatta"), colors).description)
        assertEquals("Blue shirt with white accents.", suggestionsFor(label("button-up shirt"), colors).description)
        // Jewelry gets no color, so a title never suggests gold or silver.
        assertEquals("Necklace", suggestionsFor(label("necklace"), ColorResult(ColorName.YELLOW)).title)
    }

    @Test
    fun lowConfidenceOffersTheTopThreeAsChips() {
        val g = guessItem(PhotoScores(labels(mapOf("suit" to 0.35, "blazer" to 0.30, "tuxedo" to 0.20))))
        assertEquals(listOf("suit", "blazer", "tuxedo"), (g as ItemGuess.Choices).labels.map { it.name })
        assertEquals(listOf("Suit", "Blazer", "Tuxedo"), g.labels.map { it.chip })
        // Picking one fills title, category and style.
        val picked = suggestionsForChip(label("blazer"), ColorResult(ColorName.NAVY))
        assertEquals("Navy blazer", picked.title)
        assertEquals("Career & Professional", picked.category)
        assertEquals("Western formal", picked.style)
        // Spread thin: nothing at all.
        assertEquals(ItemGuess.Unsure, guessItem(PhotoScores(labels(emptyMap()))))
        assertEquals(ItemGuess.Unsure, guessItem(PhotoScores(emptyList())))
    }

    @Test
    fun suggestionsOnlyAboveTheConfidenceCutoff() {
        assertTrue(guessItem(scores("lehenga", PhotoThresholds.SUGGEST_CONFIDENCE)) is ItemGuess.Suggest)
        assertFalse(guessItem(scores("lehenga", PhotoThresholds.SUGGEST_CONFIDENCE - 0.01)) is ItemGuess.Suggest)
        // A not-offered label on top never suggests.
        assertFalse(guessItem(scores("jeans", 0.9)) is ItemGuess.Suggest)
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
    fun severalPhotosAverageIntoOneSteadierEmbedding() {
        val front = floatArrayOf(1f, 0.2f)
        val back = floatArrayOf(0.8f, -0.2f)
        val avg = averageEmbedding(listOf(front, back))!!
        assertEquals(1.0, kotlin.math.sqrt(avg.sumOf { (it * it).toDouble() }), 1e-6)
        assertTrue(cosineSimilarity(avg, floatArrayOf(1f, 0f)) > cosineSimilarity(front, floatArrayOf(1f, 0f)))
        assertNull(averageEmbedding(emptyList()))
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
        val ls = listOf(label("saree"), label("blazer"), label("hat"))
        val text = listOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f))
        val ranked = scoreLabels(normalized(floatArrayOf(0.2f, 0.9f, 0.1f)), text, ls)
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
