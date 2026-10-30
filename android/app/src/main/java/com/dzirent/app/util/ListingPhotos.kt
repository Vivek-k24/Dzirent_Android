package com.dzirent.app.util

/**
 * The photo slots of a listing, in the order they're shown and saved. Front, Back and Extra
 * get every check (sharp, light, framing, same item, what it is) and are what the item is
 * recognized from. Close-up and Size tag are details, so they only get the sharpness and
 * lighting checks.
 */
enum class PhotoSlot(val key: String, val label: String, val required: Boolean, val fullChecks: Boolean) {
    FRONT("front", "Front", required = true, fullChecks = true),
    BACK("back", "Back", required = true, fullChecks = true),
    CLOSE_UP("close_up", "Close-up", required = true, fullChecks = false),
    SIZE_TAG("size_tag", "Size tag", required = false, fullChecks = false),
    EXTRA_1("extra_1", "Extra", required = false, fullChecks = true),
    EXTRA_2("extra_2", "Extra", required = false, fullChecks = true);

    /** Used for the item type, the tier check and the same-item check. */
    val usedForType: Boolean get() = fullChecks

    companion object {
        fun fromKey(key: String?): PhotoSlot? = entries.firstOrNull { it.key == key }

        /** Photos picked without choosing a slot fill these, in this order. */
        val FILL_ORDER = listOf(FRONT, BACK, CLOSE_UP, EXTRA_1, EXTRA_2, SIZE_TAG)

        val REQUIRED: List<PhotoSlot> = entries.filter { it.required }
    }
}

const val MIN_PHOTOS = 3
const val MAX_PHOTOS = 6

/** The one tip line above the photo slots. */
const val PHOTO_TIP = "Lay it flat or hang it in daylight. Front, back, then a close-up of fabric or detail."

/**
 * Puts [incoming] photos into the empty slots: [target] first when it's empty, then
 * [PhotoSlot.FILL_ORDER]. Returns the new slots and how many photos didn't fit.
 */
fun <T> assignSlots(current: Map<PhotoSlot, T>, incoming: List<T>, target: PhotoSlot? = null): Pair<Map<PhotoSlot, T>, Int> {
    val result = current.toMutableMap()
    val order = (listOfNotNull(target) + PhotoSlot.FILL_ORDER).distinct()
    var left = 0
    incoming.forEach { photo ->
        val slot = order.firstOrNull { it !in result }
        if (slot == null) left++ else result[slot] = photo
    }
    return result to left
}

/**
 * Saved photos without a slot (listed before slots existed) get one by position: Front, Back,
 * Close-up, then the extras and finally Size tag.
 */
fun slotsForSaved(slotKeys: List<String?>): List<PhotoSlot> {
    val taken = mutableSetOf<PhotoSlot>()
    val fromKeys = slotKeys.map { key -> PhotoSlot.fromKey(key)?.takeIf { taken.add(it) } }
    return fromKeys.map { known -> known ?: PhotoSlot.FILL_ORDER.first { it !in taken }.also { taken += it } }
}

/** "2 of 3 ready": required slots holding a photo that passed (or was used anyway). */
fun readyCount(verdicts: Map<PhotoSlot, PhotoVerdict>): Int =
    PhotoSlot.REQUIRED.count { verdicts[it] == PhotoVerdict.PASSED || verdicts[it] == PhotoVerdict.USED_ANYWAY }

fun readyLine(verdicts: Map<PhotoSlot, PhotoVerdict>): String = "${readyCount(verdicts)} of ${PhotoSlot.REQUIRED.size} ready"

/**
 * Whether the 3-photo minimum applies: always for a new listing, and for an edited one only
 * once its photos change (older listings may have fewer and can still be edited).
 */
fun photoMinimumApplies(isNew: Boolean, photosChanged: Boolean): Boolean = isNew || photosChanged

// ---- Turning a decoded photo the right way up ------------------------------------------------

/**
 * [raw] (pixels as stored in the file) turned the way the EXIF orientation tag (1-8) says, so a
 * camera photo stored sideways reads exactly like the same photo from the gallery. Unknown
 * values count as 1 (as stored).
 */
fun applyExifOrientation(raw: RgbImage, orientation: Int): RgbImage {
    val w = raw.width
    val h = raw.height
    val swap = orientation in 5..8
    val outW = if (swap) h else w
    val outH = if (swap) w else h
    if (orientation !in 2..8) return raw
    val out = IntArray(outW * outH)
    for (y in 0 until outH) for (x in 0 until outW) {
        // The stored pixel shown at (x, y).
        val (sx, sy) = when (orientation) {
            2 -> (w - 1 - x) to y               // mirrored
            3 -> (w - 1 - x) to (h - 1 - y)     // 180
            4 -> x to (h - 1 - y)               // mirrored vertically
            5 -> y to x                         // transposed
            6 -> y to (h - 1 - x)               // 90 clockwise
            7 -> (w - 1 - y) to (h - 1 - x)     // transversed
            else -> (w - 1 - y) to x            // 8: 270 clockwise
        }
        out[y * outW + x] = raw.pixels[sy * w + sx]
    }
    return RgbImage(outW, outH, out)
}

/** The checks and the color votes for one upright photo; [slot] decides which checks count. */
class ImageAnalysis(val checks: PhotoChecks, val colors: ColorVotes)

/**
 * Every check on one upright photo, plus color votes measured inside the garment the framing
 * check found. The same function runs for camera and gallery photos alike.
 */
fun analyzeImage(image: RgbImage): ImageAnalysis {
    val gray = image.gray()
    val lighting = checkLighting(gray, image.width, image.height)
    var sharp = checkSharpness(gray, image.width, image.height)
    if (lighting.status == CheckStatus.FAIL && sharp.status == CheckStatus.FAIL) {
        sharp = CheckResult(CheckStatus.UNSURE, "not_judged_due_to_exposure")
    }
    val item = findItem(image)
    return ImageAnalysis(
        PhotoChecks(sharp = sharp, lighting = lighting, wholeItem = item.result),
        colorVotes(image, item.region, item.background)
    )
}

// ---- What can be listed, across the photos -----------------------------------------------------

/** One photo in a slot, with its tier check (null until the model has run, or if it can't). */
data class SlotPhoto(val key: String, val slot: PhotoSlot, val verdict: TierVerdict?)

/**
 * The photos to take out of the listing: Front, Back and Extra photos the tier check says are
 * not fashion or not allowed, unless the lender kept them ([kept], "It's fashion, keep it").
 * The same rule runs as each photo is checked and again at Publish (the backstop). Close-up and
 * Size tag photos aren't used for the type, so they're never removed.
 */
fun photosToRemove(photos: List<SlotPhoto>, kept: Set<String>): List<SlotPhoto> =
    photos.filter { it.slot.usedForType && it.verdict is TierVerdict.Remove && it.key !in kept }

/** The first everyday-basics warning still open (answered ones are in [overruled]). */
fun notOfferedWarning(photos: List<SlotPhoto>, overruled: Set<String>): SlotPhoto? =
    photos.firstOrNull { it.slot.usedForType && it.verdict is TierVerdict.NotOffered && it.key !in overruled }

/** True when any photo looks like jewelry: the declaration is needed, nothing is removed. */
fun jewelryFound(photos: List<SlotPhoto>): Boolean = photos.any { it.slot.usedForType && it.verdict is TierVerdict.Jewelry }
