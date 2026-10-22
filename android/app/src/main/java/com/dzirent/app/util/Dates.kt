package com.dzirent.app.util

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val timeOfDay = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.US)

/** An inclusive range of whole days. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "end must not be before start" }
    }

    /** True if the two ranges share at least one day (both ends inclusive). */
    fun overlaps(other: DateRange): Boolean = !start.isAfter(other.end) && !other.start.isAfter(end)

    fun format(): String = "${start.format(shortDate)} - ${end.format(shortDate)}"
}

/** The first existing range that [candidate] overlaps, or null if it fits. */
fun firstOverlap(candidate: DateRange, existing: List<DateRange>): DateRange? =
    existing.firstOrNull { it.overlaps(candidate) }

/** Parses two ISO dates (yyyy-MM-dd), or null if either is missing or invalid. */
fun parseDateRange(start: String?, end: String?): DateRange? {
    val s = start?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val e = end?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return if (e.isBefore(s)) null else DateRange(s, e)
}

/** "Nov 3 - Nov 10" from two ISO dates (yyyy-MM-dd). Null if either is missing or invalid. */
fun formatDateRange(start: String?, end: String?): String? = parseDateRange(start, end)?.format()

private fun parseTimestamp(iso: String?): OffsetDateTime? =
    iso?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

/** Time on a chat bubble: "3:42 PM" today, otherwise "Oct 3, 3:42 PM". */
fun formatMessageTime(iso: String?, now: OffsetDateTime = OffsetDateTime.now()): String {
    val local = parseTimestamp(iso)?.atZoneSameInstant(ZoneId.systemDefault()) ?: return ""
    val today = now.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    val time = local.format(timeOfDay)
    return if (local.toLocalDate() == today) time else "${local.format(shortDate)}, $time"
}

/** Inbox time: "3:42 PM" today, "Tue" within a week, otherwise "Oct 3". */
fun formatInboxTime(iso: String?, now: OffsetDateTime = OffsetDateTime.now()): String {
    val local = parseTimestamp(iso)?.atZoneSameInstant(ZoneId.systemDefault()) ?: return ""
    val today = now.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    val days = ChronoUnit.DAYS.between(local.toLocalDate(), today)
    return when {
        days <= 0L -> local.format(timeOfDay)
        days < 7L -> local.format(weekday)
        else -> local.format(shortDate)
    }
}

/** Epoch millis of an ISO timestamp, or 0 if missing or invalid (sorts last). */
fun timestampMillis(iso: String?): Long = parseTimestamp(iso)?.toInstant()?.toEpochMilli() ?: 0L

/** Whole days since an ISO timestamp, or null if it can't be parsed. */
fun daysSince(iso: String?, now: OffsetDateTime = OffsetDateTime.now()): Long? =
    parseTimestamp(iso)?.let { ChronoUnit.DAYS.between(it, now) }
