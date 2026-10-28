package com.dzirent.app.util

/**
 * Styles a person can be interested in (Profile > My measurements) and a listing can have
 * (Create/Edit listing). One list for both, the same as the checks in
 * supabase/007_search_styles.sql. Used only to rank suggestions and for the "For your style"
 * row; nothing is ever hidden because of it.
 */
object FashionStyles {
    val ALL = listOf(
        "South Asian",
        "East Asian",
        "Southeast Asian",
        "Middle Eastern",
        "African",
        "Mexican & Latin American",
        "Country & Western",
        "Western formal",
        "Western casual"
    )

    /** A listing can have up to this many. */
    const val LISTING_MAX = 3
}

/** How many of [listing]'s styles are in [interests]. */
fun styleOverlap(listingStyles: List<String>, interests: Set<String>): Int =
    if (interests.isEmpty()) 0 else listingStyles.count { it in interests }
