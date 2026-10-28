package com.dzirent.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StudioTest {

    @Test
    fun handleInputIsCleanedAsYouType() {
        assertEquals("ana.closet", cleanHandleInput("@Ana Closet"))
        assertEquals("ana_1", cleanHandleInput("ana_1!"))
        assertEquals(HANDLE_MAX, cleanHandleInput("a".repeat(40)).length)
    }

    @Test
    fun handleRules() {
        assertTrue(isValidHandle("ana.closet"))
        assertTrue(isValidHandle("a_1"))
        assertFalse(isValidHandle("an"))
        assertFalse(isValidHandle("Ana"))
        assertFalse(isValidHandle("ana closet"))
        assertFalse(isValidHandle("a".repeat(31)))
    }

    @Test
    fun suggestedHandles() {
        assertEquals("ana.lopez", suggestHandle("Ana López"))
        assertEquals("priya.s", suggestHandle("  Priya   S. "))
        assertEquals("", suggestHandle("A"))
        assertEquals("", suggestHandle("李"))
    }

    @Test
    fun replyTimeBuckets() {
        assertNull(replyTimeText(null))
        assertEquals("Usually replies within an hour", replyTimeText(30))
        assertEquals("Usually replies within a few hours", replyTimeText(120))
        assertEquals("Usually replies within a day", replyTimeText(600))
        assertEquals("Usually replies within a few days", replyTimeText(3000))
    }

    @Test
    fun sinceMonthYear() {
        assertEquals("Since Oct 2026", sinceText("2026-10-24T15:03:00.123456+00:00"))
        assertNull(sinceText(null))
        assertNull(sinceText("not a date"))
    }

    @Test
    fun datesQuestionUsesTheChipFormat() {
        val range = DateRange(LocalDate.parse("2026-10-26"), LocalDate.parse("2026-10-30"))
        assertEquals("Hi! Is “Gown” available Oct 26 - 30?", datesQuestion("Gown", range))
    }
}
