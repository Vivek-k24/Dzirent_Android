package com.dzirent.app.util

import com.dzirent.app.data.model.AccessoryTypes
import com.dzirent.app.data.model.Categories

/** What Dzirent does with a kind of item. */
enum class LabelTier {
    /** Can be listed; mapped to a category (and an item type for accessories). */
    ALLOWED,
    /** Hygiene, safety or policy: never listed. */
    NOT_ALLOWED,
    /** Everyday basics: not what Dzirent is for. */
    NOT_OFFERED
}

/**
 * One kind of item the model can recognize. [name] is the label the text embeddings were made
 * for (tools/ml/make_text_embeddings.py, same order as [ItemLabels.ALL]). [looksLike] reads
 * after "This looks like" ("a hat", "jeans"); [plural] after "Dzirent doesn't allow".
 * [policyGroup] is the wording in the policy lists that covers this label.
 */
data class ItemLabel(
    val name: String,
    val tier: LabelTier,
    val category: String? = null,
    val itemType: String? = null,
    val looksLike: String = "a $name",
    val plural: String = "${name}s",
    val policyGroup: String? = null
) {
    /** "lehenga", the item word in a suggested title ("Maroon lehenga"). */
    val titleNoun: String get() = name
}

/** One wording in the "Not allowed" / "Not offered" lists; labels point at it with [ItemLabel.policyGroup]. */
data class PolicyGroup(val text: String, val tier: LabelTier)

/**
 * The single list of item labels, their tiers, and the policy lists built from them. The
 * Community policy screen and the listing-form note use [notAllowedText] and [notOfferedText],
 * so the policy and the photo warnings can never drift apart.
 */
object ItemLabels {
    private const val CAREER = "Career & Professional"
    private const val OCCASION = "Occasion & Party"
    private const val CULTURAL = "Cultural & Traditional"
    private const val EVERYDAY = "Everyday"
    private const val OUTERWEAR = "Outerwear"
    private const val ACCESSORIES = Categories.ACCESSORIES

    /** In the order the policy text lists them. */
    val NOT_ALLOWED_GROUPS = listOf(
        "lingerie", "underwear", "swimwear", "socks and hosiery", "shoes and boots", "hats and caps", "smartwatches"
    ).map { PolicyGroup(it, LabelTier.NOT_ALLOWED) }

    val NOT_OFFERED_GROUPS = listOf(
        "T-shirts", "track pants", "sweatpants", "joggers", "shorts", "jeans"
    ).map { PolicyGroup(it, LabelTier.NOT_OFFERED) }

    private fun allowed(name: String, category: String, itemType: String? = null) =
        ItemLabel(name, LabelTier.ALLOWED, category = category, itemType = itemType)

    private fun notAllowed(name: String, group: String, looksLike: String = "a $name", plural: String = "${name}s") =
        ItemLabel(name, LabelTier.NOT_ALLOWED, looksLike = looksLike, plural = plural, policyGroup = group)

    private fun notOffered(name: String, group: String, looksLike: String = "a $name") =
        ItemLabel(name, LabelTier.NOT_OFFERED, looksLike = looksLike, plural = group, policyGroup = group)

    val ALL: List<ItemLabel> = listOf(
        // ALLOWED
        allowed("blazer", CAREER),
        allowed("suit", CAREER),
        allowed("dress shirt", CAREER),
        allowed("trousers", CAREER),
        allowed("chinos", CAREER),
        allowed("tie", ACCESSORIES, "Tie"),
        allowed("cocktail dress", OCCASION),
        allowed("gown", OCCASION),
        allowed("jumpsuit", OCCASION),
        allowed("slip dress", OCCASION),
        allowed("maxi dress", OCCASION),
        allowed("skirt", EVERYDAY),
        allowed("blouse", EVERYDAY),
        allowed("button-up shirt", EVERYDAY),
        allowed("hoodie", EVERYDAY),
        allowed("denim jacket", OUTERWEAR),
        allowed("bomber jacket", OUTERWEAR),
        allowed("overcoat", OUTERWEAR),
        allowed("trench coat", OUTERWEAR),
        allowed("saree", CULTURAL),
        allowed("lehenga", CULTURAL),
        allowed("anarkali", CULTURAL),
        allowed("kurta", CULTURAL),
        allowed("sherwani", CULTURAL),
        allowed("tuxedo", OCCASION),
        allowed("clutch", ACCESSORIES, "Handbag"),
        allowed("potli bag", ACCESSORIES, "Handbag"),
        allowed("crossbody bag", ACCESSORIES, "Handbag"),
        allowed("handbag", ACCESSORIES, "Handbag"),
        allowed("sunglasses", ACCESSORIES, "Sunglasses"),
        allowed("watch", ACCESSORIES, "Watch (non-smart)"),
        allowed("belt buckle", ACCESSORIES, "Belt buckle"),
        // NOT_ALLOWED (hygiene, safety, policy)
        notAllowed("lingerie", "lingerie", looksLike = "lingerie", plural = "lingerie"),
        notAllowed("underwear", "underwear", looksLike = "underwear", plural = "underwear"),
        notAllowed("swimwear", "swimwear", looksLike = "swimwear", plural = "swimwear"),
        notAllowed("socks", "socks and hosiery", looksLike = "socks", plural = "socks"),
        notAllowed("pantyhose", "socks and hosiery", looksLike = "pantyhose", plural = "pantyhose or other hosiery"),
        notAllowed("shoes", "shoes and boots", looksLike = "shoes", plural = "shoes"),
        notAllowed("boots", "shoes and boots", looksLike = "boots", plural = "boots"),
        notAllowed("hat", "hats and caps"),
        notAllowed("cap", "hats and caps"),
        notAllowed("cowboy hat", "hats and caps", plural = "hats"),
        notAllowed("smartwatch", "smartwatches", plural = "smartwatches"),
        // NOT_OFFERED (everyday basics)
        notOffered("t-shirt", "T-shirts", looksLike = "a T-shirt"),
        notOffered("track pants", "track pants", looksLike = "track pants"),
        notOffered("sweatpants", "sweatpants", looksLike = "sweatpants"),
        notOffered("joggers", "joggers", looksLike = "joggers"),
        notOffered("shorts", "shorts", looksLike = "shorts"),
        notOffered("jean shorts", "shorts", looksLike = "jean shorts"),
        notOffered("jeans", "jeans", looksLike = "jeans")
    )

    val NAMES: List<String> = ALL.map { it.name }

    fun byName(name: String): ItemLabel? = ALL.firstOrNull { it.name == name }

    /** "Not allowed: lingerie, underwear, swimwear, socks and hosiery, shoes and boots, hats and caps, smartwatches." */
    val notAllowedText: String = "Not allowed: " + NOT_ALLOWED_GROUPS.joinToString(", ") { it.text } + "."

    /** "Not offered: everyday basics like T-shirts, track pants, sweatpants, joggers, shorts and jeans." */
    val notOfferedText: String = "Not offered: everyday basics like " + joinWithAnd(NOT_OFFERED_GROUPS.map { it.text }) + "."

    init {
        // A label's category and item type must be ones the listing form offers.
        ALL.forEach { label ->
            check(label.tier != LabelTier.ALLOWED || label.category in Categories.ALL) { "Unknown category for ${label.name}" }
            check(label.itemType == null || label.itemType in AccessoryTypes.ALL) { "Unknown item type for ${label.name}" }
        }
    }
}

/** "a, b and c" (no serial comma). */
fun joinWithAnd(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}
