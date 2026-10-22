package com.dzirent.app.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.US)

/** "Nov 3 - Nov 10" from two ISO dates (yyyy-MM-dd). Null if either is missing or invalid. */
fun formatDateRange(start: String?, end: String?): String? {
    val s = start?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    val e = end?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return "${s.format(shortDate)} - ${e.format(shortDate)}"
}
