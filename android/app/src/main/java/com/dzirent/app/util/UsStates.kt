package com.dzirent.app.util

/** USPS codes for the states, DC and Puerto Rico (the places in us_cities), keyed by full name. */
private val US_STATE_CODES = mapOf(
    "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR",
    "California" to "CA", "Colorado" to "CO", "Connecticut" to "CT", "Delaware" to "DE",
    "District of Columbia" to "DC", "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI",
    "Idaho" to "ID", "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA",
    "Kansas" to "KS", "Kentucky" to "KY", "Louisiana" to "LA", "Maine" to "ME",
    "Maryland" to "MD", "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN",
    "Mississippi" to "MS", "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE",
    "Nevada" to "NV", "New Hampshire" to "NH", "New Jersey" to "NJ", "New Mexico" to "NM",
    "New York" to "NY", "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH",
    "Oklahoma" to "OK", "Oregon" to "OR", "Pennsylvania" to "PA", "Puerto Rico" to "PR",
    "Rhode Island" to "RI", "South Carolina" to "SC", "South Dakota" to "SD", "Tennessee" to "TN",
    "Texas" to "TX", "Utah" to "UT", "Vermont" to "VT", "Virginia" to "VA",
    "Washington" to "WA", "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY"
)

private val US_CODES = US_STATE_CODES.values.toSet()

/** "Texas" or "TX" -> "TX"; null if it isn't a US state, DC or Puerto Rico. */
fun usStateCode(stateOrCode: String?): String? {
    val value = stateOrCode?.trim().orEmpty()
    if (value.uppercase() in US_CODES) return value.uppercase()
    return US_STATE_CODES.entries.firstOrNull { it.key.equals(value, ignoreCase = true) }?.value
}

/** "City, ST" when the state is known, otherwise just the city. */
fun cityLabel(city: String, stateOrCode: String?): String =
    usStateCode(stateOrCode)?.let { "$city, $it" } ?: city
