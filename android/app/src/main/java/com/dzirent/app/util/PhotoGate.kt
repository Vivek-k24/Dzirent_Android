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
 * the cover; [usedAnyway] when the lender accepted the warnings with "Use anyway".
 */
data class PhotoReview(
    val state: ReviewState,
    val checks: PhotoChecks? = null,
    val differentItem: Boolean = false,
    val usedAnyway: Boolean = false
) {
    val issues: List<PhotoIssue> get() = checks?.issues.orEmpty()

    private val blocked get() = state == ReviewState.UNREADABLE || issues.any { it.severity == Severity.BLOCK }
    private val warned get() = differentItem || issues.any { it.severity == Severity.WARN }

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
                listOfNotNull(DIFFERENT_ITEM.takeIf { differentItem })
            else -> emptyList()
        }

    companion object {
        val Saved = PhotoReview(ReviewState.SAVED)
        val Checking = PhotoReview(ReviewState.CHECKING)
    }
}

/**
 * Why the photos stop the listing from publishing, or null when they don't: every check has
 * finished, nothing is blocked, every warning was accepted with "Use anyway" (or the photo
 * removed), and at least one photo passes every check.
 */
fun photoGate(reviews: List<PhotoReview>): String? {
    val verdicts = reviews.map { it.verdict }
    return when {
        reviews.isEmpty() -> "Add at least one photo."
        PhotoVerdict.CHECKING in verdicts -> "Still checking your photos. Try again in a moment."
        PhotoVerdict.BLOCKED in verdicts -> "Some photos can't be used. Retake or remove the ones marked in red."
        PhotoVerdict.WARNING in verdicts -> "Some photos have a warning. Tap \"Use anyway\" or remove them."
        PhotoVerdict.PASSED !in verdicts -> "Add at least one photo that passes every check."
        else -> null
    }
}
