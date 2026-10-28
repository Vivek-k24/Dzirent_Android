package com.dzirent.app.util

import com.dzirent.app.data.model.PrivateProfile

/** Rows of the welcome checklist, in order. */
enum class SetupItem(val title: String) {
    LOCATION("Set your location (City, ST)"),
    MEASUREMENTS("Add your measurements"),
    STUDIO("Set up your studio"),
    FIRST_PIECE("List your first piece")
}

/** What is already done. Location comes from the profile; the rest is loaded. */
data class SetupStatus(
    val location: Boolean,
    val measurements: Boolean,
    /** A handle is set, which saving Profile > My studio does. */
    val studio: Boolean,
    val firstPiece: Boolean
) {
    fun isDone(item: SetupItem): Boolean = when (item) {
        SetupItem.LOCATION -> location
        SetupItem.MEASUREMENTS -> measurements
        SetupItem.STUDIO -> studio
        SetupItem.FIRST_PIECE -> firstPiece
    }

    /** Items 1-2 (everyone's): until both are done, Profile shows "Finish setting up". */
    val basicsDone: Boolean get() = location && measurements
}

/** Renters get location and measurements; lenders and both also get studio and first piece. */
fun setupItems(canLend: Boolean): List<SetupItem> =
    if (canLend) SetupItem.entries.toList() else listOf(SetupItem.LOCATION, SetupItem.MEASUREMENTS)

/** True if any of the six body measurements is saved. */
fun hasAnyBodyMeasurement(body: PrivateProfile): Boolean = BodyMeasurement.entries.any { body.bodyCm(it) != null }

/** The real UI elements the "Show me around" guide points at. */
enum class GuideTarget { DATE_CHIP, AVATAR, SEARCH, CLOSET, SAVED }

data class GuideStep(val target: GuideTarget, val text: String)

/** The five guide steps; the Closet text depends on whether the user can lend. */
fun guideSteps(canLend: Boolean): List<GuideStep> = listOf(
    GuideStep(GuideTarget.DATE_CHIP, "Pick your dates. You'll only see pieces available then."),
    GuideStep(GuideTarget.AVATAR, "Tap here for categories, filters, your profile and settings."),
    GuideStep(GuideTarget.SEARCH, "Search for anything by name."),
    GuideStep(
        GuideTarget.CLOSET,
        if (canLend) "Your listings and rentals live here." else "Your rentals live here."
    ),
    GuideStep(GuideTarget.SAVED, "Pieces and studios you save show up here. Messages are in Inbox.")
)
