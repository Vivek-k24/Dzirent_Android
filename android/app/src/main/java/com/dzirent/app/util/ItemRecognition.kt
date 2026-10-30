package com.dzirent.app.util

import com.dzirent.app.util.PhotoThresholds as T
import kotlin.math.exp
import kotlin.math.sqrt

// ---- Embedding math ---------------------------------------------------------------------

/** Cosine similarity of two embeddings (0 if either is all zeros). */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
    require(a.size == b.size) { "embeddings differ in size" }
    var dot = 0.0
    var na = 0.0
    var nb = 0.0
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    if (na == 0.0 || nb == 0.0) return 0.0
    return dot / (sqrt(na) * sqrt(nb))
}

/** [v] scaled to length 1 (unchanged if it's all zeros). */
fun normalized(v: FloatArray): FloatArray {
    val norm = sqrt(v.sumOf { (it * it).toDouble() })
    return if (norm == 0.0) v.copyOf() else FloatArray(v.size) { (v[it] / norm).toFloat() }
}

/** The normalized mean of normalized embeddings: several photos of one item, as one. Null if there are none. */
fun averageEmbedding(embeddings: List<FloatArray>): FloatArray? {
    if (embeddings.isEmpty()) return null
    val sum = FloatArray(embeddings.first().size)
    embeddings.forEach { e ->
        val n = normalized(e)
        for (i in sum.indices) sum[i] += n[i]
    }
    return normalized(sum)
}

/** Softmax of [logits], shifted by the max so large values don't overflow. */
fun softmax(logits: DoubleArray): DoubleArray {
    val max = logits.maxOrNull() ?: return logits
    val e = DoubleArray(logits.size) { exp(logits[it] - max) }
    val sum = e.sum()
    return DoubleArray(e.size) { e[it] / sum }
}

/** CLIP zero-shot probabilities of [image] against each text embedding, in [text]'s order. */
fun zeroShot(image: FloatArray, text: List<FloatArray>): DoubleArray =
    softmax(DoubleArray(text.size) { T.LOGIT_SCALE * cosineSimilarity(image, text[it]) })

/** A label and how likely the photo is to show it. */
data class LabelScore(val label: ItemLabel, val probability: Double)

/**
 * CLIP zero-shot: the cosine similarity of the image embedding with each label's text embedding,
 * times the logit scale, through a softmax. Best first.
 */
fun scoreLabels(image: FloatArray, text: List<FloatArray>, labels: List<ItemLabel>): List<LabelScore> {
    require(text.size == labels.size) { "one text embedding per label" }
    val probabilities = zeroShot(image, text)
    return labels.indices.map { LabelScore(labels[it], probabilities[it]) }.sortedByDescending { it.probability }
}

/** True when [other] doesn't look like the same item as the cover photo. */
fun looksLikeDifferentItem(cover: FloatArray, other: FloatArray): Boolean =
    cosineSimilarity(cover, other) < T.SAME_ITEM_MIN_SIMILARITY

/**
 * Everything the model says about one embedding (one photo, or several averaged): the item
 * labels (best first), how likely it is not fashion at all (the general check), and the
 * pattern and fabric probabilities in [ItemLabels.PATTERNS] / [ItemLabels.FABRICS] order.
 */
data class PhotoScores(
    val labels: List<LabelScore>,
    val notFashion: Double = 0.0,
    val patterns: DoubleArray = DoubleArray(0),
    val fabrics: DoubleArray = DoubleArray(0)
) {
    fun tierTotal(tier: LabelTier): Double = labels.filter { it.label.tier == tier }.sumOf { it.probability }
    fun top(tier: LabelTier): LabelScore? = labels.filter { it.label.tier == tier }.maxByOrNull { it.probability }

    /** Each policy group's labels added up ("hat", "cap" and "cowboy hat" count together), with the likeliest label. */
    fun groupTotals(tier: LabelTier): List<Pair<LabelScore, Double>> =
        labels.filter { it.label.tier == tier && it.label.policyGroup != null }
            .groupBy { it.label.policyGroup }
            .values.map { group -> group.maxBy { it.probability } to group.sumOf { it.probability } }
}

// ---- What can be listed (per photo) -------------------------------------------------------

/** What the tier check decides for one photo. */
sealed class TierVerdict {
    /** Clothing, a bag or an accessory Dzirent lists. */
    object Fashion : TierVerdict()

    /** Taken out of the listing at once; [label] is null when only the general check named it. */
    data class Remove(val tier: LabelTier, val label: ItemLabel?, val probability: Double) : TierVerdict() {
        val message: String get() = removalMessage(tier, label)
    }

    /** Jewelry: never removed, but the accessory declaration is needed. */
    data class Jewelry(val label: ItemLabel, val probability: Double) : TierVerdict()

    /** An everyday basic: a warning with "Choose another photo" / "This is wrong". */
    data class NotOffered(val label: ItemLabel, val probability: Double) : TierVerdict() {
        val message: String get() = policyMessage(label)
    }
}

/**
 * The tier check, strongest first:
 * - not fashion, when the general check is at least [PhotoThresholds.GENERAL_NOT_FASHION_CONFIDENCE]
 *   *and* the not-fashion labels add up to [PhotoThresholds.NOT_FASHION_LABEL_CONFIDENCE] (both
 *   have to agree, so a dress is never taken out because one signal misfired);
 * - a not-allowed group at [PhotoThresholds.REMOVE_CONFIDENCE];
 * - jewelry at [PhotoThresholds.JEWELRY_CONFIDENCE] (the tier added up);
 * - a not-offered group at [PhotoThresholds.POLICY_CONFIDENCE].
 */
fun tierVerdict(scores: PhotoScores): TierVerdict {
    val notFashionLabels = scores.tierTotal(LabelTier.NOT_FASHION)
    if (scores.notFashion >= T.GENERAL_NOT_FASHION_CONFIDENCE && notFashionLabels >= T.NOT_FASHION_LABEL_CONFIDENCE) {
        return TierVerdict.Remove(LabelTier.NOT_FASHION, scores.top(LabelTier.NOT_FASHION)?.label, scores.notFashion)
    }
    scores.groupTotals(LabelTier.NOT_ALLOWED).filter { (_, total) -> total >= T.REMOVE_CONFIDENCE }.maxByOrNull { it.second }
        ?.let { (top, total) -> return TierVerdict.Remove(LabelTier.NOT_ALLOWED, top.label, total) }
    val jewelry = scores.tierTotal(LabelTier.JEWELRY)
    if (jewelry >= T.JEWELRY_CONFIDENCE) return TierVerdict.Jewelry(scores.top(LabelTier.JEWELRY)!!.label, jewelry)
    scores.groupTotals(LabelTier.NOT_OFFERED).filter { (_, total) -> total >= T.POLICY_CONFIDENCE }.maxByOrNull { it.second }
        ?.let { (top, total) -> return TierVerdict.NotOffered(top.label, total) }
    return TierVerdict.Fashion
}

/** "Removed: this looks like a bike. Dzirent is for clothing and fashion accessories." */
fun removalMessage(tier: LabelTier, label: ItemLabel?): String = when (tier) {
    LabelTier.NOT_FASHION ->
        "Removed: ${label?.let { "this looks like ${it.looksLike}" } ?: "this doesn't look like clothing"}. " +
            "Dzirent is for clothing and fashion accessories."
    LabelTier.NOT_ALLOWED -> "Removed: this looks like ${label!!.looksLike}. Dzirent doesn't allow ${label.plural}."
    else -> error("$tier is never removed")
}

/** The snackbar button that puts the photo back. */
fun keepLabel(tier: LabelTier): String = if (tier == LabelTier.NOT_FASHION) "It's fashion, keep it" else "That's wrong, keep it"

/** The warning for a not-offered item. */
fun policyMessage(label: ItemLabel): String = when (label.tier) {
    LabelTier.NOT_OFFERED ->
        "This looks like ${label.looksLike}. Dzirent focuses on pieces worth renting, so everyday basics aren't listed."
    LabelTier.NOT_ALLOWED -> "This looks like ${label.looksLike}. Dzirent doesn't allow ${label.plural}."
    else -> error("${label.name} has no warning")
}

// ---- What it is (all the item's photos together) ------------------------------------------

/** What the form fills in from the photos. [label] is what it was suggested from (for tracking). */
data class ListingSuggestions(
    val label: ItemLabel,
    val title: String,
    val category: String,
    val itemType: String?,
    val description: String,
    val style: String?,
    val kind: GarmentKind?
)

/** What the photos say the item is. */
sealed class ItemGuess {
    /** Not confident enough: nothing to fill, nothing to ask. */
    object Unsure : ItemGuess()

    /** Confident: fill the empty fields. */
    data class Suggest(val label: ItemLabel, val probability: Double, val suggestions: ListingSuggestions) : ItemGuess()

    /** Not sure which: "Is it a: Shirt · Blazer · Jacket?" Picking one fills title, category and style. */
    data class Choices(val labels: List<ItemLabel>) : ItemGuess()
}

/**
 * A listable top label (allowed or jewelry) at or above [PhotoThresholds.SUGGEST_CONFIDENCE]
 * suggests. Otherwise, when the three likeliest allowed labels add up to
 * [PhotoThresholds.CHIPS_MIN_TOTAL], they're offered as chips. Otherwise nothing.
 */
fun guessItem(scores: PhotoScores, colors: ColorResult = ColorResult.NONE): ItemGuess {
    val top = scores.labels.maxByOrNull { it.probability } ?: return ItemGuess.Unsure
    val listable = top.label.tier == LabelTier.ALLOWED || top.label.tier == LabelTier.JEWELRY
    if (listable && top.probability >= T.SUGGEST_CONFIDENCE) {
        return ItemGuess.Suggest(top.label, top.probability, suggestionsFor(top.label, colors, patternOf(scores)))
    }
    val allowed = scores.labels.filter { it.label.tier == LabelTier.ALLOWED }.sortedByDescending { it.probability }.take(3)
    if (allowed.size == 3 && allowed.sumOf { it.probability } >= T.CHIPS_MIN_TOTAL) return ItemGuess.Choices(allowed.map { it.label })
    return ItemGuess.Unsure
}

/** The pattern when the model is at least [PhotoThresholds.PATTERN_CONFIDENCE] sure of it, else null. */
fun patternOf(scores: PhotoScores): Pattern? {
    if (scores.patterns.size != ItemLabels.PATTERNS.size) return null
    val best = scores.patterns.indices.maxBy { scores.patterns[it] }
    return ItemLabels.PATTERNS[best].takeIf { scores.patterns[best] >= T.PATTERN_CONFIDENCE }
}

/** Items whose second color is a border ("Yellow saree with red border"); others get "accents". */
private val BORDERED = setOf("saree", "dupatta")

/**
 * "Navy polka-dot shirt", its category, item type, style and kind, and the description: only the
 * real sentence ("Yellow saree with red border."). Jewelry gets no color: metal reads as yellow
 * or grey, and the title shouldn't suggest gold or silver.
 */
fun suggestionsFor(label: ItemLabel, colors: ColorResult = ColorResult.NONE, pattern: Pattern? = null): ListingSuggestions {
    require((label.tier == LabelTier.ALLOWED || label.tier == LabelTier.JEWELRY) && label.category != null) {
        "${label.name} can't be listed"
    }
    val shown = if (label.tier == LabelTier.JEWELRY) ColorResult.NONE else colors
    val words = listOfNotNull(shown.primary?.display?.lowercase(), pattern?.adjective, label.noun)
    val title = words.joinToString(" ").replaceFirstChar { it.uppercase() }
    val second = shown.secondary?.let { if (label.name in BORDERED) " with ${it.word} border" else " with ${it.word} accents" }.orEmpty()
    return ListingSuggestions(
        label = label,
        title = title,
        category = label.category,
        itemType = label.itemType,
        description = "$title$second.",
        style = label.style,
        kind = label.kind
    )
}

/** What picking an "Is it a: …?" chip fills in: the same as a suggestion for that label. */
fun suggestionsForChip(label: ItemLabel, colors: ColorResult = ColorResult.NONE, scores: PhotoScores? = null) =
    suggestionsFor(label, colors, scores?.let(::patternOf))

/** The tip under Description; never saved. */
const val DESCRIPTION_TIP = "Add the fabric, fit and any flaws."

/**
 * The faint hint under Description: a fabric guess when the model has one ("Might be silk or
 * satin."), then [DESCRIPTION_TIP]. Never part of the description itself.
 */
fun descriptionHint(fabrics: DoubleArray?): String {
    if (fabrics == null || fabrics.size != ItemLabels.FABRICS.size) return DESCRIPTION_TIP
    val ranked = fabrics.indices.sortedByDescending { fabrics[it] }
    val first = ranked[0]
    if (fabrics[first] < T.FABRIC_HINT_CONFIDENCE) return DESCRIPTION_TIP
    val second = ranked[1].takeIf { fabrics[it] >= T.FABRIC_SECOND_CONFIDENCE }
    val guess = listOfNotNull(ItemLabels.FABRICS[first], second?.let { ItemLabels.FABRICS[it] }).joinToString(" or ")
    return "Might be $guess. $DESCRIPTION_TIP"
}

// ---- Model input --------------------------------------------------------------------------

/** CLIP's input size and normalization (OpenAI CLIP / TinyCLIP preprocessing). */
object ClipInput {
    const val SIZE = 224
    val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
    val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

    /** The centered square crop of a [width] x [height] photo, as (x, y, side). */
    fun centerSquare(width: Int, height: Int): IntArray {
        val side = minOf(width, height)
        return intArrayOf((width - side) / 2, (height - side) / 2, side)
    }

    /**
     * A [SIZE] x [SIZE] photo as normalized floats, channels last (NHWC) or first (NCHW), the
     * layout the model's input tensor asks for.
     */
    fun toFloats(image: RgbImage, channelsFirst: Boolean): FloatArray {
        require(image.width == SIZE && image.height == SIZE) { "resize to $SIZE x $SIZE first" }
        val n = SIZE * SIZE
        val out = FloatArray(3 * n)
        for (i in 0 until n) {
            for (c in 0 until 3) {
                val channel = (image.pixels[i] shr (16 - 8 * c)) and 0xFF // R, G, B
                out[if (channelsFirst) c * n + i else i * 3 + c] = (channel / 255f - MEAN[c]) / STD[c]
            }
        }
        return out
    }
}
