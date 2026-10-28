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
        usedAnyway: Boolean = false
    ) = PhotoReview(ReviewState.CHECKED, PhotoChecks(sharp, lighting, wholeItem), differentItem, usedAnyway)

    private val good = checked()
    private val blurry = checked(sharp = fail(PhotoIssue.BLURRY))
    private val dark = checked(lighting = fail(PhotoIssue.TOO_DARK))
    private val cutOff = checked(wholeItem = fail(PhotoIssue.CUT_OFF_TOP))

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
    fun messagesPutWhatBlocksFirst() {
        val both = checked(sharp = unsure, lighting = fail(PhotoIssue.TOO_DARK), wholeItem = fail(PhotoIssue.CUT_OFF_LEFT))
        assertEquals(listOf("Too dark: try daylight", "Left side of the item is cut off"), both.messages)
        assertEquals(listOf("Top of the item is cut off", DIFFERENT_ITEM), cutOff.copy(differentItem = true).messages)
        assertEquals(listOf(UNREADABLE_PHOTO), PhotoReview(ReviewState.UNREADABLE).messages)
        assertEquals(emptyList<String>(), good.messages)
    }

    @Test
    fun onePassingPhotoPublishes() {
        assertNull(photoGate(listOf(good)))
        assertNull(photoGate(listOf(good, cutOff.copy(usedAnyway = true))))
        assertNull(photoGate(listOf(PhotoReview.Saved)))
    }

    @Test
    fun blurryOrDarkPhotosBlockUntilRemoved() {
        assertEquals("Some photos can't be used. Retake or remove the ones marked in red.", photoGate(listOf(good, blurry)))
        assertEquals("Some photos can't be used. Retake or remove the ones marked in red.", photoGate(listOf(dark, good)))
    }

    @Test
    fun warningsHoldUntilUseAnyway() {
        assertEquals("Some photos have a warning. Tap \"Use anyway\" or remove them.", photoGate(listOf(good, cutOff)))
    }

    @Test
    fun warnedPhotosAloneAreNotEnough() {
        assertEquals("Add at least one photo that passes every check.", photoGate(listOf(cutOff.copy(usedAnyway = true))))
    }

    @Test
    fun checksStillRunningHoldPublishing() {
        assertEquals("Still checking your photos. Try again in a moment.", photoGate(listOf(good, PhotoReview.Checking)))
        assertEquals("Add at least one photo.", photoGate(emptyList()))
    }
}
