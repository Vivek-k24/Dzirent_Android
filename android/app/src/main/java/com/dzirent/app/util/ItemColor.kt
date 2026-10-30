package com.dzirent.app.util

import com.dzirent.app.util.PhotoThresholds as T

/**
 * The color names a suggested title can use. Each has a few typical sRGB values (flattened
 * r, g, b triples), so a name covers its common shades, and a pixel takes the name of the
 * closest value. There is no gold or silver: a photo can't tell metal from fabric, so mustard
 * and gold-ish shades are yellow, silvery ones grey.
 */
enum class ColorName(val display: String, private vararg val rgb: Int) {
    BLACK("Black", 20, 20, 20, 40, 40, 45),
    WHITE("White", 245, 245, 245, 235, 235, 228),
    GREY("Grey", 128, 128, 128, 90, 90, 95, 165, 165, 165, 192, 192, 198, 210, 212, 216),
    NAVY("Navy", 25, 35, 85, 0, 0, 128, 30, 40, 70),
    BLUE("Blue", 40, 95, 195, 70, 130, 180, 30, 60, 170),
    GREEN("Green", 60, 145, 60, 34, 139, 34, 110, 140, 60),
    EMERALD("Emerald", 0, 115, 80, 0, 150, 110),
    RED("Red", 205, 30, 35, 230, 20, 20, 180, 20, 30),
    MAROON("Maroon", 120, 15, 35, 128, 0, 32, 90, 15, 30),
    PINK("Pink", 236, 130, 170, 255, 182, 193, 255, 105, 180),
    PURPLE("Purple", 110, 45, 140, 128, 0, 128, 150, 100, 180),
    ORANGE("Orange", 235, 125, 35, 255, 140, 0, 210, 105, 30),
    YELLOW("Yellow", 242, 210, 50, 255, 240, 120, 200, 160, 60, 212, 175, 55),
    BEIGE("Beige", 220, 200, 165, 235, 220, 190, 200, 180, 150),
    BROWN("Brown", 115, 75, 45, 90, 55, 30, 150, 100, 60);

    /** "red", for "with red border". */
    val word: String get() = display.lowercase()

    /** The typical values as (r, g, b). */
    val samples: List<Triple<Int, Int, Int>> get() = rgb.toList().chunked(3).map { (r, g, b) -> Triple(r, g, b) }

    internal val labs: List<DoubleArray> by lazy { samples.map { (r, g, b) -> rgbToLab(r, g, b) } }

    /** Shades that are light, shadow or a neighbor of this color on the same item, never "a second color". */
    val related: Set<ColorName> get() = RELATED[this].orEmpty()

    private companion object {
        val PAIRS = listOf(
            BLACK to NAVY, BLACK to GREY, GREY to WHITE, WHITE to BEIGE, BEIGE to BROWN, BEIGE to YELLOW,
            BROWN to MAROON, BROWN to ORANGE, MAROON to RED, MAROON to PURPLE, RED to PINK, RED to ORANGE,
            ORANGE to YELLOW, PINK to PURPLE, NAVY to BLUE, BLUE to PURPLE, GREEN to EMERALD
        )
        val RELATED: Map<ColorName, Set<ColorName>> = PAIRS.flatMap { (a, b) -> listOf(a to b, b to a) }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    }
}

/** The color names closest (in L*a*b*) to one pixel. */
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
 * True for typical skin tones (YCbCr, Chai & Ngan), so a model's arms and face don't name the
 * garment. Beige and brown fabric falls in the same range; [colorVotes] keeps those pixels when
 * they make up most of the item.
 */
fun looksLikeSkin(r: Int, g: Int, b: Int): Boolean {
    val cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b
    val cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b
    val y = 0.299 * r + 0.587 * g + 0.114 * b
    return y > 40 && cb in 77.0..127.0 && cr in 133.0..173.0
}

/**
 * Pixel votes for each [ColorName] (by ordinal), plus how blue the dark pixels are: black and
 * navy votes are pooled and [blueExcess] (each dark pixel's blue minus the mean of its red and
 * green, added up) decides which one they are. Votes from several photos of the item add up.
 */
class ColorVotes(val counts: IntArray = IntArray(ColorName.entries.size), val darkCount: Int = 0, val blueExcess: Double = 0.0) {
    val total: Int get() = counts.sum()

    operator fun plus(other: ColorVotes) =
        ColorVotes(IntArray(counts.size) { counts[it] + other.counts[it] }, darkCount + other.darkCount, blueExcess + other.blueExcess)

    companion object {
        val EMPTY = ColorVotes()
    }
}

/**
 * Votes from the pixels of the garment: [region] (the item the framing check found) when there
 * is one, otherwise the center box minus pixels that match the border's color. Skin tones are
 * left out unless they're most of the item (a beige dress). Every other pixel is enough.
 */
fun colorVotes(image: RgbImage, region: BooleanArray?, background: DoubleArray? = null): ColorVotes {
    val w = image.width
    val box = centerBox(w, image.height)
    val inside: (Int, Int) -> Boolean = if (region != null) {
        { x, y -> region[y * w + x] }
    } else {
        { x, y -> x in box[0] until box[2] && y in box[1] until box[3] }
    }
    val counts = IntArray(ColorName.entries.size)
    val skinCounts = IntArray(ColorName.entries.size)
    var dark = 0
    var excess = 0.0
    var skinDark = 0
    var skinExcess = 0.0
    for (y in 0 until image.height step 2) for (x in 0 until w step 2) {
        if (!inside(x, y)) continue
        val i = y * w + x
        val r = image.red(i)
        val g = image.green(i)
        val b = image.blue(i)
        if (region == null && background != null && labDistance(rgbToLab(r, g, b), background) <= T.ITEM_MIN_DISTANCE) continue
        val name = nearestColorName(r, g, b)
        val isDark = name == ColorName.BLACK || name == ColorName.NAVY
        val blue = b - (r + g) / 2.0
        if (looksLikeSkin(r, g, b)) {
            skinCounts[name.ordinal]++
            if (isDark) { skinDark++; skinExcess += blue }
        } else {
            counts[name.ordinal]++
            if (isDark) { dark++; excess += blue }
        }
    }
    // A skin-colored item: its "skin" pixels are the garment.
    val skin = skinCounts.sum()
    if (skin > T.SKIN_MAX_SHARE * (skin + counts.sum())) {
        return ColorVotes(IntArray(counts.size) { counts[it] + skinCounts[it] }, dark + skinDark, excess + skinExcess)
    }
    return ColorVotes(counts, dark, excess)
}

/** The item's main color and a clear second color, either of which can be missing. */
data class ColorResult(val primary: ColorName?, val secondary: ColorName? = null) {
    companion object {
        val NONE = ColorResult(null)
    }
}

/**
 * Names the colors from [votes]:
 * - black and navy are one pool, named navy when the dark pixels are on average at least
 *   [PhotoThresholds.NAVY_BLUE_EXCESS] bluer than red and green;
 * - the top color takes in its [ColorName.related] shades; it needs
 *   [PhotoThresholds.COLOR_MIN_SHARE] of the votes;
 * - the next unrelated color is the second color at [PhotoThresholds.SECOND_COLOR_MIN_SHARE];
 *   if it comes within [PhotoThresholds.COLOR_TIE_RATIO] of the top, the colors are too close
 *   to call and both are left out.
 */
fun nameColors(votes: ColorVotes): ColorResult {
    if (votes.total < T.COLOR_MIN_PIXELS) return ColorResult.NONE
    val counts = votes.counts.copyOf()
    val pooled = counts[ColorName.BLACK.ordinal] + counts[ColorName.NAVY.ordinal]
    val navy = votes.darkCount > 0 && votes.blueExcess / votes.darkCount >= T.NAVY_BLUE_EXCESS
    counts[ColorName.BLACK.ordinal] = if (navy) 0 else pooled
    counts[ColorName.NAVY.ordinal] = if (navy) pooled else 0

    val total = counts.sum().toDouble()
    val ranked = ColorName.entries.sortedByDescending { counts[it.ordinal] }
    val top = ranked.first()
    val topShare = (counts[top.ordinal] + top.related.sumOf { counts[it.ordinal] }) / total
    if (topShare < T.COLOR_MIN_SHARE) return ColorResult.NONE
    val second = ranked.drop(1).firstOrNull { it !in top.related && counts[it.ordinal] > 0 } ?: return ColorResult(top)
    val secondShare = counts[second.ordinal] / total
    if (secondShare >= T.COLOR_TIE_RATIO * topShare) return ColorResult.NONE
    return ColorResult(top, second.takeIf { secondShare >= T.SECOND_COLOR_MIN_SHARE })
}

/** The main color of one photo, for callers that don't combine photos. */
fun dominantColors(image: RgbImage): ColorResult {
    val item = findItem(image)
    return nameColors(colorVotes(image, item.region, item.background))
}

internal fun labDistance(a: DoubleArray, b: DoubleArray): Double {
    val dl = a[0] - b[0]
    val da = a[1] - b[1]
    val db = a[2] - b[2]
    return kotlin.math.sqrt(dl * dl + da * da + db * db)
}
