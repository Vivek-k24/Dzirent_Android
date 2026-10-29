package com.dzirent.app.util

import com.dzirent.app.util.PhotoThresholds as T
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A downscaled photo: [pixels] holds 0xRRGGBB per pixel, row by row. Plain Kotlin, so the
 * checks below run (and are tested) on the JVM.
 */
class RgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "pixels must be width * height" }
    }

    fun red(i: Int) = (pixels[i] shr 16) and 0xFF
    fun green(i: Int) = (pixels[i] shr 8) and 0xFF
    fun blue(i: Int) = pixels[i] and 0xFF

    /** Gray as OpenCV computes it (0.299 R + 0.587 G + 0.114 B), 0-255. */
    fun gray(): IntArray = IntArray(pixels.size) { i ->
        (0.299 * red(i) + 0.587 * green(i) + 0.114 * blue(i)).roundToInt().coerceIn(0, 255)
    }
}

/** What a check concluded. UNSURE (not enough to judge) never blocks or warns. */
enum class CheckStatus { PASS, UNSURE, FAIL }

/** BLOCK: the photo can't be used (retake or remove). WARN: it can, after "Use anyway". */
enum class Severity { BLOCK, WARN }

/** A reason a photo failed a check, with the line shown under its thumbnail. */
enum class PhotoIssue(val message: String, val severity: Severity) {
    BLURRY("Too blurry: hold steady and retake", Severity.BLOCK),
    TOO_DARK("Too dark: try daylight", Severity.BLOCK),
    TOO_BRIGHT("Too bright: avoid direct sun and flash", Severity.WARN),
    GLARE("Strong glare: move out of direct light", Severity.WARN),
    CUT_OFF_TOP("Top of the item is cut off", Severity.WARN),
    CUT_OFF_BOTTOM("Bottom of the item is cut off", Severity.WARN),
    CUT_OFF_LEFT("Left side of the item is cut off", Severity.WARN),
    CUT_OFF_RIGHT("Right side of the item is cut off", Severity.WARN)
}

/** One check's result; [issue] is set when [status] is FAIL. [note] says why, for tests and tuning. */
data class CheckResult(val status: CheckStatus, val note: String, val issue: PhotoIssue? = null)

/** The three checks for one photo: Sharp, Lighting, Whole item visible. */
data class PhotoChecks(val sharp: CheckResult, val lighting: CheckResult, val wholeItem: CheckResult) {
    val issues: List<PhotoIssue> get() = listOfNotNull(sharp.issue, lighting.issue, wholeItem.issue)
}

/**
 * Runs the three checks, ported from the website's quality-rules-0.1.0 (analysis.py) without the
 * paper-sheet check. As there, a "blurry" result on a photo that is also too dark is not trusted:
 * darkness hides edges, so only the lighting is reported.
 */
fun checkPhoto(image: RgbImage): PhotoChecks {
    val gray = image.gray()
    val lighting = checkLighting(gray, image.width, image.height)
    var sharp = checkSharpness(gray, image.width, image.height)
    if (lighting.status == CheckStatus.FAIL && sharp.status == CheckStatus.FAIL) {
        sharp = CheckResult(CheckStatus.UNSURE, "not_judged_due_to_exposure")
    }
    return PhotoChecks(sharp = sharp, lighting = lighting, wholeItem = checkWholeItem(image))
}

// ---- Sharp ------------------------------------------------------------------------------

/**
 * Laplacian variance per tile (8 x 8 tiles); flat tiles (plain paper, floor) are skipped and the
 * 90th percentile of the rest is the score.
 */
fun checkSharpness(gray: IntArray, width: Int, height: Int): CheckResult {
    val laplacian = laplacian(gray, width, height)
    val scores = mutableListOf<Double>()
    val tiles = T.BLUR_TILES
    for (r in 0 until tiles) for (c in 0 until tiles) {
        val y0 = r * height / tiles
        val y1 = (r + 1) * height / tiles
        val x0 = c * width / tiles
        val x1 = (c + 1) * width / tiles
        if (y1 <= y0 || x1 <= x0) continue
        if (std(gray, width, x0, y0, x1, y1) { it.toDouble() } > T.BLUR_TEXTURE_STD) {
            scores += variance(laplacian, width, x0, y0, x1, y1)
        }
    }
    if (scores.size < T.BLUR_MIN_TILES) return CheckResult(CheckStatus.UNSURE, "insufficient_detail")
    val score = percentile(scores.toDoubleArray(), T.BLUR_PERCENTILE)
    return when {
        score >= T.SHARP_SCORE -> CheckResult(CheckStatus.PASS, "sharp")
        score < T.BLURRY_SCORE -> CheckResult(CheckStatus.FAIL, "blurry", PhotoIssue.BLURRY)
        else -> CheckResult(CheckStatus.UNSURE, "borderline_sharpness")
    }
}

/** OpenCV's 3 x 3 Laplacian (0 1 0 / 1 -4 1 / 0 1 0) with BORDER_REFLECT_101. */
internal fun laplacian(gray: IntArray, width: Int, height: Int): DoubleArray {
    fun at(x: Int, y: Int): Int = gray[reflect101(y, height) * width + reflect101(x, width)]
    return DoubleArray(gray.size) { i ->
        val x = i % width
        val y = i / width
        (at(x - 1, y) + at(x + 1, y) + at(x, y - 1) + at(x, y + 1) - 4 * gray[i]).toDouble()
    }
}

private fun reflect101(i: Int, n: Int): Int = when {
    n == 1 -> 0
    i < 0 -> -i
    i >= n -> 2 * n - 2 - i
    else -> i
}

private inline fun std(values: IntArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int, f: (Int) -> Double): Double {
    var sum = 0.0
    var sumSq = 0.0
    var n = 0
    for (y in y0 until y1) for (x in x0 until x1) {
        val v = f(values[y * width + x])
        sum += v
        sumSq += v * v
        n++
    }
    val mean = sum / n
    return sqrt(max(0.0, sumSq / n - mean * mean))
}

private fun variance(values: DoubleArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int): Double {
    var sum = 0.0
    var sumSq = 0.0
    var n = 0
    for (y in y0 until y1) for (x in x0 until x1) {
        val v = values[y * width + x]
        sum += v
        sumSq += v * v
        n++
    }
    val mean = sum / n
    return max(0.0, sumSq / n - mean * mean)
}

/** numpy.percentile with linear interpolation. */
internal fun percentile(values: DoubleArray, p: Double): Double {
    require(values.isNotEmpty())
    val sorted = values.sortedArray()
    val rank = p / 100.0 * (sorted.size - 1)
    val lo = rank.toInt()
    val hi = min(lo + 1, sorted.size - 1)
    return sorted[lo] + (sorted[hi] - sorted[lo]) * (rank - lo)
}

// ---- Lighting ---------------------------------------------------------------------------

/** Too dark blocks; too bright and strong glare warn; low contrast or uneven light is UNSURE. */
fun checkLighting(gray: IntArray, width: Int, height: Int): CheckResult {
    val n = gray.size.toDouble()
    val mean = gray.sum() / n
    val dark = gray.count { it <= T.DARK_PIXEL } / n
    val bright = gray.count { it >= T.BRIGHT_PIXEL } / n
    if (mean < T.DARK_MEAN || dark > T.DARK_SHARE) return CheckResult(CheckStatus.FAIL, "underexposed", PhotoIssue.TOO_DARK)
    if (mean > T.BRIGHT_MEAN || bright > T.BRIGHT_SHARE) return CheckResult(CheckStatus.FAIL, "overexposed", PhotoIssue.TOO_BRIGHT)

    // Glare (not in the website rules): blown-out spots in the middle of an item that is
    // otherwise not white, like flash on satin or a window reflected in sequins.
    val center = centerValues(gray, width, height)
    val clipped = center.count { it >= T.BRIGHT_PIXEL } / center.size.toDouble()
    if (clipped >= T.GLARE_SHARE && percentile(center.map { it.toDouble() }.toDoubleArray(), 50.0) < T.GLARE_MAX_CENTER_MEDIAN) {
        return CheckResult(CheckStatus.FAIL, "glare", PhotoIssue.GLARE)
    }

    val sorted = gray.map { it.toDouble() }.toDoubleArray()
    val p5 = percentile(sorted, 5.0)
    val p95 = percentile(sorted, 95.0)
    if (p95 - p5 < 15) return CheckResult(CheckStatus.UNSURE, "low_contrast")
    if (mean in 70.0..200.0 && dark < 0.10 && bright < 0.12) return CheckResult(CheckStatus.PASS, "acceptable_light")
    return CheckResult(CheckStatus.UNSURE, "borderline_exposure")
}

/** The middle [PhotoThresholds.CENTER_SHARE] of the photo, as (x0, y0, x1, y1). */
internal fun centerBox(width: Int, height: Int): IntArray {
    val marginX = (width * (1 - T.CENTER_SHARE) / 2).toInt()
    val marginY = (height * (1 - T.CENTER_SHARE) / 2).toInt()
    return intArrayOf(marginX, marginY, max(marginX + 1, width - marginX), max(marginY + 1, height - marginY))
}

private fun centerValues(values: IntArray, width: Int, height: Int): IntArray {
    val (x0, y0, x1, y1) = centerBox(width, height)
    val out = IntArray((x1 - x0) * (y1 - y0))
    var k = 0
    for (y in y0 until y1) for (x in x0 until x1) out[k++] = values[y * width + x]
    return out
}

// ---- Whole item visible -----------------------------------------------------------------

/**
 * Finds the item as the largest region that differs from the background (the median color of
 * the photo's border), then checks whether its outline runs along an edge of the frame.
 * A busy background (more than 30% of the border is not one surface) is UNSURE, not a failure.
 */
fun checkWholeItem(image: RgbImage): CheckResult {
    val w = image.width
    val h = image.height
    if (min(w, h) < 16) return CheckResult(CheckStatus.UNSURE, "too_small")
    val lab = toLab(image)
    val band = max(4, (T.BORDER_SHARE * min(w, h)).roundToInt()).coerceAtMost(min(w, h) / 2)

    val strip = ArrayList<Int>()
    for (y in 0 until band) for (x in 0 until w) strip += y * w + x
    for (y in h - band until h) for (x in 0 until w) strip += y * w + x
    for (y in 0 until h) for (x in 0 until band) strip += y * w + x
    for (y in 0 until h) for (x in w - band until w) strip += y * w + x

    val background = DoubleArray(3) { c -> median(DoubleArray(strip.size) { lab[strip[it] * 3 + c] }) }
    fun distance(i: Int): Double {
        val dl = lab[i * 3] - background[0]
        val da = lab[i * 3 + 1] - background[1]
        val db = lab[i * 3 + 2] - background[2]
        return sqrt(dl * dl + da * da + db * db)
    }
    val stripDistance = DoubleArray(strip.size) { distance(strip[it]) }
    if (stripDistance.count { it > T.BORDER_NOISE_DISTANCE } / stripDistance.size.toDouble() > T.BORDER_MAX_NOISY_SHARE) {
        return CheckResult(CheckStatus.UNSURE, "background_not_uniform")
    }
    val threshold = max(T.ITEM_MIN_DISTANCE, 3.0 * percentile(stripDistance, 75.0))
    var mask = BooleanArray(w * h) { distance(it) > threshold }
    mask = dilate(erode(mask, w, h, 5), w, h, 5)    // open: drop specks
    mask = erode(dilate(mask, w, h, 11), w, h, 11)  // close: fill small gaps

    val item = largestRegion(mask, w, h)
    if (item == null || item.size < T.ITEM_MIN_AREA * w * h) return CheckResult(CheckStatus.UNSURE, "garment_not_detected")

    val inItem = BooleanArray(w * h).also { m -> item.forEach { m[it] = true } }
    val m = T.EDGE_MARGIN_PX
    var outline = 0
    var onEdge = 0
    val touching = IntArray(4) // top, bottom, left, right
    for (i in item) {
        val x = i % w
        val y = i / w
        val isOutline = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
            !inItem[i - 1] || !inItem[i + 1] || !inItem[i - w] || !inItem[i + w]
        if (!isOutline) continue
        outline++
        val sides = booleanArrayOf(y <= m, y >= h - 1 - m, x <= m, x >= w - 1 - m)
        if (sides.any { it }) onEdge++
        sides.forEachIndexed { side, touches -> if (touches) touching[side]++ }
    }
    if (outline > 0 && onEdge.toDouble() / outline >= T.CUT_OFF_OUTLINE_SHARE) {
        val side = touching.indices.maxBy { touching[it] }
        val issue = listOf(PhotoIssue.CUT_OFF_TOP, PhotoIssue.CUT_OFF_BOTTOM, PhotoIssue.CUT_OFF_LEFT, PhotoIssue.CUT_OFF_RIGHT)[side]
        return CheckResult(CheckStatus.FAIL, "garment_cut_off", issue)
    }
    return CheckResult(CheckStatus.PASS, "garment_fully_visible")
}

private fun median(values: DoubleArray): Double {
    val sorted = values.sortedArray()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/** sRGB (D65) to CIE L*a*b*, three values per pixel: L 0-100, a and b about -128..127. */
internal fun toLab(image: RgbImage): DoubleArray {
    val out = DoubleArray(image.pixels.size * 3)
    for (i in image.pixels.indices) {
        val lab = rgbToLab(image.red(i), image.green(i), image.blue(i))
        out[i * 3] = lab[0]
        out[i * 3 + 1] = lab[1]
        out[i * 3 + 2] = lab[2]
    }
    return out
}

private val SRGB_TO_LINEAR = DoubleArray(256) { v ->
    val c = v / 255.0
    if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

internal fun rgbToLab(r: Int, g: Int, b: Int): DoubleArray {
    val rl = SRGB_TO_LINEAR[r]
    val gl = SRGB_TO_LINEAR[g]
    val bl = SRGB_TO_LINEAR[b]
    val x = (0.4124564 * rl + 0.3575761 * gl + 0.1804375 * bl) / 0.95047
    val y = 0.2126729 * rl + 0.7151522 * gl + 0.0721750 * bl
    val z = (0.0193339 * rl + 0.1191920 * gl + 0.9503041 * bl) / 1.08883
    fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (24389.0 / 27.0 * t + 16) / 116
    val fx = f(x)
    val fy = f(y)
    val fz = f(z)
    return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

/** Rectangular erosion with a k x k kernel; pixels outside the frame don't count (as in OpenCV). */
internal fun erode(mask: BooleanArray, w: Int, h: Int, k: Int): BooleanArray = morph(mask, w, h, k, erode = true)

internal fun dilate(mask: BooleanArray, w: Int, h: Int, k: Int): BooleanArray = morph(mask, w, h, k, erode = false)

/** Separable: rows, then columns. Erode = all set within the window, dilate = any set. */
private fun morph(mask: BooleanArray, w: Int, h: Int, k: Int, erode: Boolean): BooleanArray {
    val r = k / 2
    val rows = BooleanArray(mask.size)
    for (y in 0 until h) {
        // Running count of set pixels in the window, clipped to the frame.
        var count = 0
        for (x in 0..min(r, w - 1)) if (mask[y * w + x]) count++
        for (x in 0 until w) {
            val lo = max(0, x - r)
            val hi = min(w - 1, x + r)
            rows[y * w + x] = if (erode) count == hi - lo + 1 else count > 0
            if (x + r + 1 < w && mask[y * w + x + r + 1]) count++
            if (x - r >= 0 && mask[y * w + x - r]) count--
        }
    }
    val out = BooleanArray(mask.size)
    for (x in 0 until w) {
        var count = 0
        for (y in 0..min(r, h - 1)) if (rows[y * w + x]) count++
        for (y in 0 until h) {
            val lo = max(0, y - r)
            val hi = min(h - 1, y + r)
            out[y * w + x] = if (erode) count == hi - lo + 1 else count > 0
            if (y + r + 1 < h && rows[(y + r + 1) * w + x]) count++
            if (y - r >= 0 && rows[(y - r) * w + x]) count--
        }
    }
    return out
}

/** The pixel indices of the largest 8-connected region in [mask], or null if it's empty. */
internal fun largestRegion(mask: BooleanArray, w: Int, h: Int): IntArray? {
    val seen = BooleanArray(mask.size)
    var best: IntArray? = null
    val stack = IntArray(mask.size)
    val region = IntArray(mask.size)
    for (start in mask.indices) {
        if (!mask[start] || seen[start]) continue
        var top = 0
        var size = 0
        stack[top++] = start
        seen[start] = true
        while (top > 0) {
            val i = stack[--top]
            region[size++] = i
            val x = i % w
            val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                val j = ny * w + nx
                if (mask[j] && !seen[j]) {
                    seen[j] = true
                    stack[top++] = j
                }
            }
        }
        if (size > (best?.size ?: 0)) best = region.copyOf(size)
    }
    return best
}
