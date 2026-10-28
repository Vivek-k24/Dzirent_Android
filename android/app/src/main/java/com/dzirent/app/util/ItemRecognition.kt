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

/** Softmax of [logits], shifted by the max so large values don't overflow. */
fun softmax(logits: DoubleArray): DoubleArray {
    val max = logits.maxOrNull() ?: return logits
    val e = DoubleArray(logits.size) { exp(logits[it] - max) }
    val sum = e.sum()
    return DoubleArray(e.size) { e[it] / sum }
}

/** A label and how likely the photo is to show it. */
data class LabelScore(val label: ItemLabel, val probability: Double)

/**
 * CLIP zero-shot: the cosine similarity of the image embedding with each label's text embedding,
 * times the logit scale, through a softmax. Best first.
 */
fun scoreLabels(image: FloatArray, text: List<FloatArray>, labels: List<ItemLabel>): List<LabelScore> {
    require(text.size == labels.size) { "one text embedding per label" }
    val probabilities = softmax(DoubleArray(labels.size) { T.LOGIT_SCALE * cosineSimilarity(image, text[it]) })
    return labels.indices.map { LabelScore(labels[it], probabilities[it]) }.sortedByDescending { it.probability }
}

/** True when [other] doesn't look like the same item as the cover photo. */
fun looksLikeDifferentItem(cover: FloatArray, other: FloatArray): Boolean =
    cosineSimilarity(cover, other) < T.SAME_ITEM_MIN_SIMILARITY

// ---- What to do with the scores -----------------------------------------------------------

/** What the editor shows for the cover photo. */
sealed class Recognition {
    /** Not confident enough: show nothing. */
    object Unsure : Recognition()

    /** An allowed item, confident enough to suggest the title, category and description. */
    data class Suggest(val label: ItemLabel, val probability: Double, val suggestions: ListingSuggestions) : Recognition()

    /** A not-allowed or not-offered item, with the message to show. */
    data class Policy(val label: ItemLabel, val probability: Double, val message: String) : Recognition()
}

/** What accepting each suggestion card fills in. */
data class ListingSuggestions(val title: String, val category: String, val itemType: String?, val description: String)

/**
 * Decides from the label scores: a not-allowed or not-offered group at or above
 * [PhotoThresholds.POLICY_CONFIDENCE] (its labels' probabilities added up, so "hat", "cap" and
 * "cowboy hat" count together) warns; otherwise an allowed top label at or above
 * [PhotoThresholds.SUGGEST_CONFIDENCE] suggests; otherwise nothing.
 */
fun recognize(scores: List<LabelScore>, color: ColorName?): Recognition {
    val byGroup = scores.filter { it.label.policyGroup != null }.groupBy { it.label.policyGroup }
    val policy = byGroup.values
        .map { group -> group.maxBy { it.probability } to group.sumOf { it.probability } }
        .filter { (_, total) -> total >= T.POLICY_CONFIDENCE }
        .maxByOrNull { (_, total) -> total }
    if (policy != null) {
        val (top, total) = policy
        return Recognition.Policy(top.label, total, policyMessage(top.label))
    }
    val top = scores.maxByOrNull { it.probability } ?: return Recognition.Unsure
    if (top.label.tier != LabelTier.ALLOWED || top.probability < T.SUGGEST_CONFIDENCE) return Recognition.Unsure
    return Recognition.Suggest(top.label, top.probability, suggestionsFor(top.label, color))
}

/** The warning for a not-allowed or not-offered item. */
fun policyMessage(label: ItemLabel): String = when (label.tier) {
    LabelTier.NOT_ALLOWED -> "This looks like ${label.looksLike}. Dzirent doesn't allow ${label.plural}."
    LabelTier.NOT_OFFERED ->
        "This looks like ${label.looksLike}. Dzirent focuses on pieces worth renting, so everyday basics aren't listed."
    LabelTier.ALLOWED -> error("${label.name} is allowed")
}

/** "Maroon lehenga", its category (and item type), and a starter description to edit. */
fun suggestionsFor(label: ItemLabel, color: ColorName?): ListingSuggestions {
    require(label.tier == LabelTier.ALLOWED && label.category != null) { "${label.name} can't be listed" }
    val title = if (color == null) label.titleNoun.replaceFirstChar { it.uppercase() } else "${color.display} ${label.titleNoun}"
    return ListingSuggestions(
        title = title,
        category = label.category,
        itemType = label.itemType,
        description = "$title. Add the fabric, fit and any flaws."
    )
}

// ---- Color ------------------------------------------------------------------------------

/**
 * The 16 color names a suggested title can use. Each has a few typical sRGB values (flattened
 * r, g, b triples), so a name covers its common shades: navy is both inky and bright, and a
 * pixel takes the name of the closest value.
 */
enum class ColorName(val display: String, private vararg val rgb: Int) {
    BLACK("Black", 20, 20, 20, 40, 40, 45),
    WHITE("White", 245, 245, 245, 235, 235, 228),
    GREY("Grey", 128, 128, 128, 90, 90, 95, 165, 165, 165),
    NAVY("Navy", 25, 35, 85, 0, 0, 128, 30, 40, 70),
    BLUE("Blue", 40, 95, 195, 70, 130, 180, 30, 60, 170),
    GREEN("Green", 60, 145, 60, 34, 139, 34, 110, 140, 60),
    EMERALD("Emerald", 0, 115, 80, 0, 150, 110),
    RED("Red", 205, 30, 35, 230, 20, 20, 180, 20, 30),
    MAROON("Maroon", 120, 15, 35, 128, 0, 32, 90, 15, 30),
    PINK("Pink", 236, 130, 170, 255, 182, 193, 255, 105, 180),
    PURPLE("Purple", 110, 45, 140, 128, 0, 128, 150, 100, 180),
    YELLOW("Yellow", 242, 210, 50, 255, 240, 120),
    GOLD("Gold", 200, 160, 60, 212, 175, 55, 180, 140, 70),
    BEIGE("Beige", 220, 200, 165, 235, 220, 190, 200, 180, 150),
    BROWN("Brown", 115, 75, 45, 90, 55, 30, 150, 100, 60),
    SILVER("Silver", 192, 192, 198, 210, 212, 216);

    /** The typical values as (r, g, b). */
    val samples: List<Triple<Int, Int, Int>> get() = rgb.toList().chunked(3).map { (r, g, b) -> Triple(r, g, b) }

    internal val labs: List<DoubleArray> by lazy { samples.map { (r, g, b) -> rgbToLab(r, g, b) } }
}

/** The color name closest (in L*a*b*) to one pixel. */
fun nearestColorName(r: Int, g: Int, b: Int): ColorName {
    val lab = rgbToLab(r, g, b)
    return ColorName.entries.minBy { name ->
        name.labs.minOf { ref ->
            val dl = lab[0] - ref[0]
            val da = lab[1] - ref[1]
            val db = lab[2] - ref[2]
            dl * dl + da * da + db * db
        }
    }
}

/**
 * The dominant color of the middle of the photo, where the item usually is: every pixel in the
 * center box votes for its nearest color name and the most votes win.
 */
fun dominantColorName(image: RgbImage): ColorName {
    val (x0, y0, x1, y1) = centerBox(image.width, image.height)
    val votes = IntArray(ColorName.entries.size)
    // Every other pixel is plenty for a vote and halves the work.
    for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) {
        val i = y * image.width + x
        votes[nearestColorName(image.red(i), image.green(i), image.blue(i)).ordinal]++
    }
    return ColorName.entries[votes.indices.maxBy { votes[it] }]
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
