package com.dzirent.app.util

import com.dzirent.app.data.model.Conversation
import com.dzirent.app.data.model.Listing
import com.dzirent.app.data.model.Message
import com.dzirent.app.data.model.Profile
import com.dzirent.app.data.model.Rental
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class ActivityTest {

    private val me = "me"
    private val ben = Profile(id = "ben", displayName = "Ben")
    private val ana = Profile(id = "me", displayName = "Ana")
    private val gown = Listing(
        id = "l1", lenderId = "ben", title = "Gown", category = "Everyday",
        approxLat = 0.0, approxLng = 0.0, status = "active"
    )
    private val now = OffsetDateTime.parse("2026-10-27T12:00:00Z")

    private fun conversation(id: String, createdAt: String, last: Message? = null) = Conversation(
        id = id, listingId = "l1", renterId = me, lenderId = "ben", createdAt = createdAt,
        listing = gown, renter = ana, lender = ben, messages = listOfNotNull(last)
    )

    private fun rental(status: String, updatedBy: String, updatedAt: String) = Rental(
        id = "r1", conversationId = "c1", listingId = "l1", renterId = me, lenderId = "ben",
        status = status, startDate = "2026-10-26", endDate = "2026-10-30", updatedBy = updatedBy,
        createdAt = "2026-10-20T10:00:00Z", updatedAt = updatedAt, listing = gown, lender = ben, renter = ana
    )

    @Test
    fun otherPersonsRentalChangeIsNewAfterLastSeen() {
        val items = buildActivity(
            me, emptyList(), listOf(rental("active", "ben", "2026-10-26T09:00:00Z")),
            emptySet(), seenAt = "2026-10-25T00:00:00Z", now = now
        )
        assertEquals(1, items.size)
        assertTrue(items[0].isNew)
        assertEquals("Ben marked “Gown” as rented for Oct 26 - Oct 30", items[0].text)
    }

    @Test
    fun changeSeenAlreadyIsNotNew() {
        val items = buildActivity(
            me, emptyList(), listOf(rental("returned", "ben", "2026-10-26T09:00:00Z")),
            emptySet(), seenAt = "2026-10-26T10:00:00Z", now = now
        )
        assertFalse(items.single().isNew)
    }

    @Test
    fun myOwnChangeIsNeverNew() {
        val items = buildActivity(
            me, emptyList(), listOf(rental("cancelled", me, "2026-10-26T09:00:00Z")),
            emptySet(), seenAt = null, now = now
        )
        assertFalse(items.single().isNew)
        assertEquals("You said the deal for “Gown” didn't happen", items.single().text)
    }

    @Test
    fun followUpAppearsAfterThreeDaysUntilAnswered() {
        val old = conversation("c2", "2026-10-23T12:00:00Z")
        val fresh = conversation("c3", "2026-10-26T12:00:00Z")
        val items = buildActivity(me, listOf(old, fresh), emptyList(), emptySet(), seenAt = null, now = now)
        assertEquals(listOf("c2"), items.map { it.conversationId })
        assertEquals(ActivityKind.FOLLOW_UP, items.single().kind)

        val answered = buildActivity(me, listOf(old), emptyList(), setOf("c2"), seenAt = null, now = now)
        assertTrue(answered.isEmpty())
    }

    @Test
    fun noFollowUpWhenTheChatHasARental() {
        val conv = conversation("c1", "2026-10-01T12:00:00Z")
        val items = buildActivity(
            me, listOf(conv), listOf(rental("active", "ben", "2026-10-02T09:00:00Z")),
            emptySet(), seenAt = null, now = now
        )
        assertEquals(listOf(ActivityKind.RENTED), items.map { it.kind })
    }

    @Test
    fun newestFirst() {
        val conv = conversation("c2", "2026-10-20T12:00:00Z") // follow-up due Oct 23
        val items = buildActivity(
            me, listOf(conv), listOf(rental("active", "ben", "2026-10-26T09:00:00Z")),
            emptySet(), seenAt = null, now = now
        )
        assertEquals(listOf(ActivityKind.RENTED, ActivityKind.FOLLOW_UP), items.map { it.kind })
    }

    @Test
    fun unreadOnlyForTheOtherPersonsNewerMessage() {
        val fromBen = Message("m1", "c1", "ben", "Hi", "2026-10-27T10:00:00Z")
        val fromMe = Message("m2", "c1", me, "Hi", "2026-10-27T10:00:00Z")
        assertTrue(hasUnread(conversation("c1", "2026-10-27T09:00:00Z", fromBen), me, null))
        assertTrue(hasUnread(conversation("c1", "2026-10-27T09:00:00Z", fromBen), me, "2026-10-27T09:30:00Z"))
        assertFalse(hasUnread(conversation("c1", "2026-10-27T09:00:00Z", fromBen), me, "2026-10-27T10:00:01Z"))
        assertFalse(hasUnread(conversation("c1", "2026-10-27T09:00:00Z", fromMe), me, null))
        assertFalse(hasUnread(conversation("c1", "2026-10-27T09:00:00Z"), me, null))
    }
}
