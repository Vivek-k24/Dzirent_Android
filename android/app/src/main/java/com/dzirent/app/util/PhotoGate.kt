package com.dzirent.app.util

/** How far the checks on one photo have got. */
enum class ReviewState {
    CHECKING,
    CHECKED,
    /** The photo couldn't be decoded, so it can't be checked or uploaded. */
    UNREADABLE,
    /** Already on the saved listing (published before these checks); counts as passed. */
    SAVED
}

/** Where a photo stands: what its thumbnail shows and whether it holds up publishing. */
enum class PhotoVerdict { CHECKING, PASSED, WARNING, USED_ANYWAY, BLOCKED }

const val UNREADABLE_PHOTO = "Couldn't read this photo: remove it and pick another"
const val DIFFERENT_ITEM = "This photo looks like a different item"

/**
 * What the editor knows about one photo. [differentItem] is set when the photo doesn't look like
 * the front; [usedAnyway] when the lender accepted the warnings with "Use anyway". With
 * [fullChecks] false (Close-up, Size tag) only sharpness and lighting count.
 */
data class PhotoReview(
    val state: ReviewState,
    val checks: PhotoChecks? = null,
    val differentItem: Boolean = false,
    val usedAnyway: Boolean = false,
    val fullChecks: Boolean = true
) {
    val issues: List<PhotoIssue>
        get() = checks?.let { c -> listOfNotNull(c.sharp.issue, c.lighting.issue, c.wholeItem.issue.takeIf { fullChecks }) }.orEmpty()

    private val blocked get() = state == ReviewState.UNREADABLE || issues.any { it.severity == Severity.BLOCK }
    private val warned get() = (differentItem && fullChecks) || issues.any { it.severity == Severity.WARN }

    val verdict: PhotoVerdict
        get() = when (state) {
            ReviewState.SAVED -> PhotoVerdict.PASSED
            ReviewState.CHECKING -> PhotoVerdict.CHECKING
            ReviewState.UNREADABLE -> PhotoVerdict.BLOCKED
            ReviewState.CHECKED -> when {
                blocked -> PhotoVerdict.BLOCKED
                warned -> if (usedAnyway) PhotoVerdict.USED_ANYWAY else PhotoVerdict.WARNING
                else -> PhotoVerdict.PASSED
            }
        }

    /** The lines under the thumbnail: what blocks first, then the warnings. */
    val messages: List<String>
        get() = when (state) {
            ReviewState.UNREADABLE -> listOf(UNREADABLE_PHOTO)
            ReviewState.CHECKED -> issues.sortedBy { it.severity }.map { it.message } +
                listOfNotNull(DIFFERENT_ITEM.takeIf { differentItem && fullChecks })
            else -> emptyList()
        }

    companion object {
        val Saved = PhotoReview(ReviewState.SAVED)
        val Checking = PhotoReview(ReviewState.CHECKING)
    }
}

const val POLICY_PENDING = "Choose another photo, or tap \"This is wrong\" if we misread the item."

/** Something that holds up a step: [button] is what the Next / Publish button says, [message] the longer line. */
data class Missing(val button: String, val message: String)

/** "Add 1 more photo", "Add 2 more photos". */
fun morePhotos(n: Int): String = "Add $n more photo" + if (n == 1) "" else "s"

/**
 * Why the photos hold up the listing, or null when they don't: with [minimumApplies], at least
 * [MIN_PHOTOS] photos and every required slot filled (otherwise at least one photo); every check
 * finished; a not-offered warning ([policyPending]) answered; nothing blocked; every warning
 * accepted with "Use anyway" (or the photo removed); and at least one photo that passes every check.
 */
fun photoGate(reviews: Map<PhotoSlot, PhotoReview>, minimumApplies: Boolean = true, policyPending: Boolean = false): Missing? {
    val verdicts = reviews.values.map { it.verdict }
    val missingRequired = PhotoSlot.REQUIRED.firstOrNull { it !in reviews }
    return when {
        reviews.isEmpty() -> Missing(morePhotos(if (minimumApplies) MIN_PHOTOS else 1), "Add your photos: front, back and a close-up.")
        minimumApplies && reviews.size < MIN_PHOTOS ->
            Missing(morePhotos(MIN_PHOTOS - reviews.size), "Add at least $MIN_PHOTOS photos: front, back and a close-up.")
        minimumApplies && missingRequired != null ->
            Missing("Add a ${missingRequired.label.lowercase()} photo", "A ${missingRequired.label.lowercase()} photo is required.")
        PhotoVerdict.CHECKING in verdicts -> Missing("Checking photos…", "Still checking your photos. Try again in a moment.")
        policyPending -> Missing("Answer the photo note", POLICY_PENDING)
        PhotoVerdict.BLOCKED in verdicts ->
            Missing("Fix the photos in red", "Some photos can't be used. Retake or remove the ones marked in red.")
        PhotoVerdict.WARNING in verdicts ->
            Missing("Check the photo warnings", "Some photos have a warning. Tap \"Use anyway\" or remove them.")
        PhotoVerdict.PASSED !in verdicts ->
            Missing("Add a photo that passes", "Add at least one photo that passes every check.")
        else -> null
    }
}
