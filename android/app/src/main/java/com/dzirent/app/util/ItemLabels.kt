package com.dzirent.app.util

import com.dzirent.app.data.model.AccessoryTypes
import com.dzirent.app.data.model.Categories

/** What Dzirent does with a kind of item. */
enum class LabelTier {
    /** Can be listed; mapped to a category (and an item type for accessories). */
    ALLOWED,
    /** Not clothing, a bag or a fashion accessory (a bike, a tent): removed automatically. */
    NOT_FASHION,
    /** Hygiene, safety or policy: never listed; removed automatically. */
    NOT_ALLOWED,
    /**
     * Jewelry, or anything that looks like gold, silver, platinum, diamonds, pearls or gemstones.
     * A camera can't tell real from plated, so it's never removed: it asks for the accessory
     * declaration instead and lists as costume jewelry.
     */
    JEWELRY,
    /** Everyday basics: not what Dzirent is for. A warning, not a removal. */
    NOT_OFFERED
}

/**
 * The kind of garment, which decides the size picker and the measurement rows. Stored on the
 * listing as garment_type (008), so "Same as your …" can find the lender's last one.
 */
enum class GarmentKind(val key: String, val label: String) {
    TOP("top", "Top or coat"),
    TROUSERS("trousers", "Trousers"),
    SUIT("suit", "Suit or blazer"),
    DRESS("dress", "Dress"),
    SKIRT("skirt", "Skirt"),
    SAREE("saree", "Saree or lehenga"),
    ONE_SIZE("one_size", "Accessory");

    companion object {
        fun fromKey(key: String?): GarmentKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One kind of item the model can recognize. [name] is the label the text embeddings were made
 * for (tools/ml/make_text_embeddings.py, same order as [ItemLabels.ALL]). [noun] is the item
 * word in a suggested title ("Maroon lehenga"), [chip] its name on an "Is it a: …?" chip.
 * [looksLike] reads after "this looks like" ("a hat", "jeans"); [plural] after "Dzirent doesn't
 * allow". [policyGroup] is the wording in the policy lists that covers this label. [style] is the
 * style a listing of this type gets (FashionStyles), [kind] its sizes and measurements.
 */
data class ItemLabel(
    val name: String,
    val tier: LabelTier,
    val category: String? = null,
    val itemType: String? = null,
    val style: String? = null,
    val kind: GarmentKind? = null,
    val noun: String = name,
    val looksLike: String = "a $name",
    val plural: String = "${name}s",
    val policyGroup: String? = null
) {
    val chip: String get() = noun.replaceFirstChar { it.uppercase() }
}

/** One wording in the "Not allowed" / "Not offered" lists; labels point at it with [ItemLabel.policyGroup]. */
data class PolicyGroup(val text: String, val tier: LabelTier)

/**
 * The single list of item labels and their tiers, and the policy lists built from them. The
 * recognition, the automatic removals, the warnings, the "What can I list?" sheet and the
 * Community policy all read these constants, so they can never drift apart.
 */
object ItemLabels {
    private const val CAREER = "Career & Professional"
    private const val OCCASION = "Occasion & Party"
    private const val CULTURAL = "Cultural & Traditional"
    private const val EVERYDAY = "Everyday"
    private const val OUTERWEAR = "Outerwear"
    private const val ACCESSORIES = Categories.ACCESSORIES

    private const val SOUTH_ASIAN = "South Asian"
    private const val COUNTRY = "Country & Western"
    private const val FORMAL = "Western formal"
    private const val CASUAL = "Western casual"

    /** Accessories worth more than this can't be listed (the declaration says "less than $300"). */
    const val ACCESSORY_MAX_VALUE_USD = 300

    /** What Dzirent is for; shown above the lists. */
    const val SCOPE = "Dzirent is for clothing, bags and fashion accessories only."

    /** In the order the policy text lists them. */
    val NOT_ALLOWED_GROUPS = listOf(
        "lingerie", "underwear", "swimwear", "socks and hosiery", "shoes and boots", "hats and caps", "smartwatches"
    ).map { PolicyGroup(it, LabelTier.NOT_ALLOWED) }

    /** Fine jewelry, listed under "Not allowed"; every JEWELRY label points here. */
    val JEWELRY_GROUP = PolicyGroup("real gold, silver, platinum, diamonds, pearls or gemstones", LabelTier.JEWELRY)

    val NOT_OFFERED_GROUPS = listOf(
        "T-shirts", "track pants", "sweatpants", "joggers", "shorts", "jeans"
    ).map { PolicyGroup(it, LabelTier.NOT_OFFERED) }

    private fun allowed(
        name: String,
        category: String,
        kind: GarmentKind,
        style: String? = null,
        itemType: String? = null,
        noun: String = name
    ) = ItemLabel(name, LabelTier.ALLOWED, category = category, itemType = itemType, style = style, kind = kind, noun = noun)

    private fun jewelry(name: String, noun: String = name) = ItemLabel(
        name, LabelTier.JEWELRY, category = ACCESSORIES, itemType = AccessoryTypes.COSTUME_JEWELRY,
        kind = GarmentKind.ONE_SIZE, noun = noun, policyGroup = JEWELRY_GROUP.text
    )

    private fun notFashion(name: String, looksLike: String = "a $name") =
        ItemLabel(name, LabelTier.NOT_FASHION, looksLike = looksLike)

    private fun notAllowed(name: String, group: String, looksLike: String = "a $name", plural: String = "${name}s") =
        ItemLabel(name, LabelTier.NOT_ALLOWED, looksLike = looksLike, plural = plural, policyGroup = group)

    private fun notOffered(name: String, group: String, looksLike: String = "a $name") =
        ItemLabel(name, LabelTier.NOT_OFFERED, looksLike = looksLike, plural = group, policyGroup = group)

    val ALL: List<ItemLabel> = listOf(
        // ALLOWED
        allowed("blazer", CAREER, GarmentKind.SUIT, FORMAL),
        allowed("suit", CAREER, GarmentKind.SUIT, FORMAL),
        allowed("dress shirt", CAREER, GarmentKind.TOP, FORMAL),
        allowed("trousers", CAREER, GarmentKind.TROUSERS, FORMAL),
        allowed("chinos", CAREER, GarmentKind.TROUSERS, CASUAL),
        allowed("tie", ACCESSORIES, GarmentKind.ONE_SIZE, FORMAL, itemType = "Tie"),
        allowed("cocktail dress", OCCASION, GarmentKind.DRESS),
        allowed("gown", OCCASION, GarmentKind.DRESS),
        allowed("jumpsuit", OCCASION, GarmentKind.DRESS),
        allowed("slip dress", OCCASION, GarmentKind.DRESS),
        allowed("maxi dress", OCCASION, GarmentKind.DRESS),
        allowed("skirt", EVERYDAY, GarmentKind.SKIRT, CASUAL),
        allowed("blouse", EVERYDAY, GarmentKind.TOP, CASUAL),
        allowed("button-up shirt", EVERYDAY, GarmentKind.TOP, CASUAL, noun = "shirt"),
        allowed("western shirt", EVERYDAY, GarmentKind.TOP, COUNTRY),
        allowed("hoodie", EVERYDAY, GarmentKind.TOP, CASUAL),
        allowed("sweatshirt", EVERYDAY, GarmentKind.TOP, CASUAL),
        allowed("denim jacket", OUTERWEAR, GarmentKind.TOP, CASUAL),
        allowed("bomber jacket", OUTERWEAR, GarmentKind.TOP, CASUAL),
        allowed("overcoat", OUTERWEAR, GarmentKind.TOP),
        allowed("trench coat", OUTERWEAR, GarmentKind.TOP),
        allowed("saree", CULTURAL, GarmentKind.SAREE, SOUTH_ASIAN),
        allowed("lehenga", CULTURAL, GarmentKind.SAREE, SOUTH_ASIAN),
        allowed("anarkali", CULTURAL, GarmentKind.DRESS, SOUTH_ASIAN),
        allowed("kurta", CULTURAL, GarmentKind.TOP, SOUTH_ASIAN),
        allowed("sherwani", CULTURAL, GarmentKind.SUIT, SOUTH_ASIAN),
        allowed("dupatta", CULTURAL, GarmentKind.ONE_SIZE, SOUTH_ASIAN),
        allowed("tuxedo", OCCASION, GarmentKind.SUIT, FORMAL),
        allowed("clutch", ACCESSORIES, GarmentKind.ONE_SIZE, itemType = "Handbag"),
        allowed("potli bag", ACCESSORIES, GarmentKind.ONE_SIZE, SOUTH_ASIAN, itemType = "Handbag"),
        allowed("crossbody bag", ACCESSORIES, GarmentKind.ONE_SIZE, itemType = "Handbag"),
        allowed("handbag", ACCESSORIES, GarmentKind.ONE_SIZE, itemType = "Handbag"),
        allowed("sunglasses", ACCESSORIES, GarmentKind.ONE_SIZE, itemType = "Sunglasses"),
        allowed("watch", ACCESSORIES, GarmentKind.ONE_SIZE, itemType = "Watch (non-smart)"),
        allowed("belt buckle", ACCESSORIES, GarmentKind.ONE_SIZE, COUNTRY, itemType = "Belt buckle"),
        // JEWELRY (declaration; never removed automatically)
        jewelry("necklace"),
        jewelry("earrings"),
        jewelry("bracelet"),
        jewelry("ring"),
        jewelry("pendant"),
        jewelry("brooch"),
        jewelry("gold jewelry", noun = "jewelry"),
        jewelry("silver jewelry", noun = "jewelry"),
        jewelry("platinum jewelry", noun = "jewelry"),
        jewelry("diamond jewelry", noun = "jewelry"),
        jewelry("pearl jewelry", noun = "jewelry"),
        jewelry("gemstone jewelry", noun = "jewelry"),
        // NOT_FASHION (removed automatically)
        notFashion("bicycle", looksLike = "a bike"),
        notFashion("tent"),
        notFashion("kayak"),
        notFashion("paddleboard"),
        notFashion("ski or snowboard gear", looksLike = "ski or snowboard gear"),
        notFashion("lawn mower"),
        notFashion("power tool"),
        notFashion("toolbox"),
        notFashion("furniture", looksLike = "furniture"),
        notFashion("phone"),
        notFashion("laptop"),
        notFashion("electronics", looksLike = "electronics"),
        notFashion("car"),
        notFashion("pet"),
        notFashion("food", looksLike = "food"),
        notFashion("document or card", looksLike = "a document or card"),
        // NOT_ALLOWED (hygiene, safety, policy; removed automatically)
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
        // NOT_OFFERED (everyday basics; a warning)
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

    /** The general check's two sides, in label_embeddings.json's "general" order. */
    val GENERAL = listOf("fashion", "not fashion")

    /** Patterns, in label_embeddings.json's "patterns" order. */
    val PATTERNS: List<Pattern> = Pattern.entries

    /** Fabric guesses (a hint only, never saved), in label_embeddings.json's "fabrics" order. */
    val FABRICS = listOf("silk", "satin", "cotton", "linen", "wool", "denim", "velvet", "chiffon", "leather", "lace")

    /** "accessories worth more than $300", the last entry of the "Not allowed" list. */
    val ACCESSORY_VALUE_RULE = "accessories worth more than \$$ACCESSORY_MAX_VALUE_USD"

    /**
     * "Not allowed: lingerie, underwear, swimwear, socks and hosiery, shoes and boots, hats and caps,
     * smartwatches, real gold, silver, platinum, diamonds, pearls or gemstones, and accessories
     * worth more than $300."
     */
    val notAllowedText: String = "Not allowed: " +
        (NOT_ALLOWED_GROUPS.map { it.text } + JEWELRY_GROUP.text).joinToString(", ") + ", and $ACCESSORY_VALUE_RULE."

    /** "Not offered: everyday basics like T-shirts, track pants, sweatpants, joggers, shorts and jeans." */
    val notOfferedText: String = "Not offered: everyday basics like " + joinWithAnd(NOT_OFFERED_GROUPS.map { it.text }) + "."

    init {
        // A label's category and item type must be ones the listing form offers.
        ALL.forEach { label ->
            val listable = label.tier == LabelTier.ALLOWED || label.tier == LabelTier.JEWELRY
            check(!listable || label.category in Categories.ALL) { "Unknown category for ${label.name}" }
            check(!listable || label.kind != null) { "No garment kind for ${label.name}" }
            check(label.itemType == null || label.itemType in AccessoryTypes.ALL) { "Unknown item type for ${label.name}" }
            check(label.style == null || label.style in FashionStyles.ALL) { "Unknown style for ${label.name}" }
        }
    }
}

/** A pattern the title can name ("Navy polka-dot shirt"); [adjective] is null for solid. */
enum class Pattern(val key: String, val adjective: String?) {
    SOLID("solid", null),
    STRIPED("striped", "striped"),
    POLKA_DOT("polka dot", "polka-dot"),
    FLORAL("floral", "floral"),
    PLAID("plaid", "plaid"),
    EMBROIDERED("embroidered", "embroidered"),
    SEQUINED("sequined", "sequined")
}

/** "a, b and c" (no serial comma). */
fun joinWithAnd(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}
