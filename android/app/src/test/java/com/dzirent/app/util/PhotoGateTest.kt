package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoGateTest {

    private val pass = CheckResult(CheckStatus.PASS, "ok")
    private val unsure = CheckResult(CheckStatus.UNSURE, "borderline")
    private fun fail(issue: PhotoIssue) = CheckResult(CheckStatus.FAIL, issue.name, issue)

    private fun checked(
        sharp: CheckResult = pass,
        lighting: CheckResult = pass,
        wholeItem: CheckResult = pass,
        differentItem: Boolean = false,
        usedAnyway: Boolean = false,
        fullChecks: Boolean = true
    ) = PhotoReview(ReviewState.CHECKED, PhotoChecks(sharp, lighting, wholeItem), differentItem, usedAnyway, fullChecks)

    private val good = checked()
    private val blurry = checked(sharp = fail(PhotoIssue.BLURRY))
    private val dark = checked(lighting = fail(PhotoIssue.TOO_DARK))
    private val cutOff = checked(wholeItem = fail(PhotoIssue.CUT_OFF_TOP))

    /** Front, Back and Close-up, all good, with [changes] on top. */
    private fun three(vararg changes: Pair<PhotoSlot, PhotoReview>) =
        mapOf(PhotoSlot.FRONT to good, PhotoSlot.BACK to good, PhotoSlot.CLOSE_UP to good) + changes

    @Test
    fun verdicts() {
        assertEquals(PhotoVerdict.PASSED, good.verdict)
        assertEquals(PhotoVerdict.PASSED, checked(sharp = unsure, wholeItem = unsure).verdict) // unsure never blocks or warns
        assertEquals(PhotoVerdict.BLOCKED, blurry.verdict)
        assertEquals(PhotoVerdict.BLOCKED, dark.verdict)
        assertEquals(PhotoVerdict.BLOCKED, blurry.copy(usedAnyway = true).verdict) // "Use anyway" can't unblock
        assertEquals(PhotoVerdict.WARNING, cutOff.verdict)
        assertEquals(PhotoVerdict.USED_ANYWAY, cutOff.copy(usedAnyway = true).verdict)
        assertEquals(PhotoVerdict.WARNING, checked(lighting = fail(PhotoIssue.GLARE)).verdict)
        assertEquals(PhotoVerdict.WARNING, good.copy(differentItem = true).verdict)
        assertEquals(PhotoVerdict.CHECKING, PhotoReview.Checking.verdict)
        assertEquals(PhotoVerdict.BLOCKED, PhotoReview(ReviewState.UNREADABLE).verdict)
        assertEquals(PhotoVerdict.PASSED, PhotoReview.Saved.verdict)
    }

    @Test
    fun closeUpAndSizeTagOnlyGetBlurAndLighting() {
        // A close-up is cut off by design, and isn't the same framing as the front: neither counts.
        val closeUp = checked(wholeItem = fail(PhotoIssue.CUT_OFF_LEFT), differentItem = true, fullChecks = false)
        assertEquals(PhotoVerdict.PASSED, closeUp.verdict)
        assertEquals(emptyList<String>(), closeUp.messages)
        // Blur and lighting still do.
        assertEquals(PhotoVerdict.BLOCKED, checked(sharp = fail(PhotoIssue.BLURRY), fullChecks = false).verdict)
        assertEquals(PhotoVerdict.WARNING, checked(lighting = fail(PhotoIssue.TOO_BRIGHT), fullChecks = false).verdict)
        // The slots themselves.
        assertEquals(listOf(PhotoSlot.FRONT, PhotoSlot.BACK, PhotoSlot.EXTRA_1, PhotoSlot.EXTRA_2), PhotoSlot.entries.filter { it.fullChecks })
        assertEquals(listOf(PhotoSlot.FRONT, PhotoSlot.BACK, PhotoSlot.CLOSE_UP), PhotoSlot.REQUIRED)
    }

    @Test
    fun messagesPutWhatBlocksFirst() {
        val both = checked(sharp = unsure, lighting = fail(PhotoIssue.TOO_DARK), wholeItem = fail(PhotoIssue.CUT_OFF_LEFT))
        assertEquals(listOf("Too dark: try daylight", "Left side of the item is cut off"), both.messages)
        assertEquals(listOf("Top of the item is cut off", DIFFERENT_ITEM), cutOff.copy(differentItem = true).messages)
        assertEquals(listOf(UNREADABLE_PHOTO), PhotoReview(ReviewState.UNREADABLE).messages)
        assertEquals(emptyList<String>(), good.messages)
    }

    @Test
    fun atLeastThreePhotosWithFrontBackAndCloseUp() {
        assertEquals("Add 3 more photos", photoGate(emptyMap())!!.button)
        assertEquals("Add 2 more photos", photoGate(mapOf(PhotoSlot.FRONT to good))!!.button)
        assertEquals("Add 1 more photo", photoGate(mapOf(PhotoSlot.FRONT to good, PhotoSlot.BACK to good))!!.button)
        // Three photos, but no close-up.
        assertEquals(
            "Add a close-up photo",
            photoGate(mapOf(PhotoSlot.FRONT to good, PhotoSlot.BACK to good, PhotoSlot.EXTRA_1 to good))!!.button
        )
        assertNull(photoGate(three()))
        assertNull(photoGate(three(PhotoSlot.SIZE_TAG to good, PhotoSlot.EXTRA_1 to good, PhotoSlot.EXTRA_2 to good)))
        assertEquals(6, PhotoSlot.entries.size)
        assertEquals(MAX_PHOTOS, PhotoSlot.entries.size)
    }

    @Test
    fun theMinimumAppliesToNewListingsAndChangedPhotos() {
        assertEquals(true, photoMinimumApplies(isNew = true, photosChanged = false))
        assertEquals(true, photoMinimumApplies(isNew = false, photosChanged = true))
        assertEquals(false, photoMinimumApplies(isNew = false, photosChanged = false))
        // An older listing with one saved photo can still be edited...
        assertNull(photoGate(mapOf(PhotoSlot.FRONT to PhotoReview.Saved), minimumApplies = false))
        // ...but not with none.
        assertEquals("Add 1 more photo", photoGate(emptyMap(), minimumApplies = false)!!.button)
    }

    @Test
    fun blurryOrDarkPhotosBlockUntilRemoved() {
        assertEquals("Some photos can't be used. Retake or remove the ones marked in red.", photoGate(three(PhotoSlot.BACK to blurry))!!.message)
        assertEquals("Fix the photos in red", photoGate(three(PhotoSlot.FRONT to dark))!!.button)
    }

    @Test
    fun warningsHoldUntilUseAnyway() {
        assertEquals("Some photos have a warning. Tap \"Use anyway\" or remove them.", photoGate(three(PhotoSlot.BACK to cutOff))!!.message)
        assertNull(photoGate(three(PhotoSlot.BACK to cutOff.copy(usedAnyway = true))))
    }

    @Test
    fun warnedPhotosAloneAreNotEnough() {
        val warned = cutOff.copy(usedAnyway = true)
        assertEquals(
            "Add at least one photo that passes every check.",
            photoGate(mapOf(PhotoSlot.FRONT to warned, PhotoSlot.BACK to warned, PhotoSlot.CLOSE_UP to warned))!!.message
        )
    }

    @Test
    fun checksStillRunningAndOpenWarningsHoldTheStep() {
        assertEquals("Still checking your photos. Try again in a moment.", photoGate(three(PhotoSlot.BACK to PhotoReview.Checking))!!.message)
        assertEquals(POLICY_PENDING, photoGate(three(), policyPending = true)!!.message)
    }

    @Test
    fun readyCountsTheRequiredSlots() {
        val verdicts = mapOf(PhotoSlot.FRONT to PhotoVerdict.PASSED, PhotoSlot.BACK to PhotoVerdict.USED_ANYWAY, PhotoSlot.CLOSE_UP to PhotoVerdict.CHECKING)
        assertEquals("2 of 3 ready", readyLine(verdicts))
        assertEquals("3 of 3 ready", readyLine(verdicts + (PhotoSlot.CLOSE_UP to PhotoVerdict.PASSED) + (PhotoSlot.EXTRA_1 to PhotoVerdict.BLOCKED)))
    }
}
