package com.dzirent.app.util

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Style tags a studio can pick, up to [STYLE_TAGS_MAX]. Same list as profiles_style_tags_check. */
object StyleTags {
    val ALL = listOf(
        "Minimalist", "Classic", "Vintage", "Streetwear", "Boho", "Glam", "Traditional",
        "Workwear", "Designer", "Plus size", "Petite", "Maternity"
    )
}

const val STYLE_TAGS_MAX = 5
const val STUDIO_NAME_MAX = 40
const val STUDIO_BIO_MAX = 200
const val PICKUP_AREA_MAX = 60
const val HANDLE_MIN = 3
const val HANDLE_MAX = 30

private val handleRegex = Regex("^[a-z0-9._]{$HANDLE_MIN,$HANDLE_MAX}$")

/** What the handle field keeps as you type: lowercase, no "@", spaces become dots. */
fun cleanHandleInput(input: String): String =
    input.lowercase().removePrefix("@").replace(' ', '.').filter { it.isLetterOrDigit() && it.code < 128 || it == '.' || it == '_' }
        .take(HANDLE_MAX)

/** Matches profiles_studio_handle_format: 3-30 lowercase letters, numbers, dots, underscores. */
fun isValidHandle(handle: String): Boolean = handleRegex.matches(handle)

/** A starting handle from a name: "Ana López" -> "ana.lopez". Empty if nothing usable. */
fun suggestHandle(name: String): String {
    val ascii = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    val base = cleanHandleInput(ascii.trim().replace(Regex("\\s+"), " "))
        .replace(Regex("\\.{2,}"), ".")
        .trim('.')
    return if (base.length >= HANDLE_MIN) base.take(HANDLE_MAX) else ""
}

/** The studio page's reply line from studio_reply_minutes, or null when unknown. */
fun replyTimeText(minutes: Int?): String? = when {
    minutes == null -> null
    minutes < 60 -> "Usually replies within an hour"
    minutes < 6 * 60 -> "Usually replies within a few hours"
    minutes < 24 * 60 -> "Usually replies within a day"
    else -> "Usually replies within a few days"
}

private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)

/** "Since Oct 2026" from the profile's created_at, or null if it can't be read. */
fun sinceText(createdAt: String?): String? =
    createdAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }?.let { "Since ${it.format(monthYear)}" }

/** The message prefilled when asking about a piece for the current dates. */
fun datesQuestion(title: String, dates: DateRange): String =
    "Hi! Is “$title” available ${formatDateChip(dates)}?"
