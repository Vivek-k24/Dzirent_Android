"""The item labels the app recognizes, in the same order as ItemLabels.ALL in
android/app/src/main/java/com/dzirent/app/util/ItemLabels.kt.

Each label has the phrase the text embeddings are made from. CLIP knows common English
clothing words well; less common ones (saree, potli bag) get a few words of description.
The unit test ModelAssetsTest checks that the shipped embeddings list exactly these names,
in this order (and the general, pattern and fabric lists too), so a label added here must be
added there too (with its tier and category).
"""

# (label name as in ItemLabels.kt, phrase used in the prompts)
ALLOWED = [
    ("blazer", "a blazer"),
    ("suit", "a suit with a jacket and matching trousers"),
    ("dress shirt", "a dress shirt with a collar"),
    ("trousers", "a pair of trousers"),
    ("chinos", "a pair of chinos"),
    ("tie", "a necktie"),
    ("cocktail dress", "a cocktail dress"),
    ("gown", "an evening gown"),
    ("jumpsuit", "a jumpsuit"),
    ("slip dress", "a satin slip dress"),
    ("maxi dress", "a maxi dress"),
    ("skirt", "a skirt"),
    ("blouse", "a blouse"),
    ("button-up shirt", "a button-up shirt"),
    ("western shirt", "a western cowboy shirt with snap buttons and a pointed yoke"),
    ("hoodie", "a hoodie"),
    ("sweatshirt", "a crewneck sweatshirt"),
    ("denim jacket", "a denim jacket"),
    ("bomber jacket", "a bomber jacket"),
    ("overcoat", "a wool overcoat"),
    ("trench coat", "a trench coat"),
    ("saree", "a saree, an Indian draped dress"),
    ("lehenga", "a lehenga, an embroidered Indian skirt and blouse"),
    ("anarkali", "an anarkali, a long flared Indian dress"),
    ("kurta", "a kurta, a long Indian tunic"),
    ("sherwani", "a sherwani, a long Indian coat for men"),
    ("dupatta", "a dupatta, a long Indian scarf"),
    ("tuxedo", "a tuxedo"),
    ("clutch", "a clutch purse"),
    ("potli bag", "a potli bag, a small drawstring pouch purse"),
    ("crossbody bag", "a crossbody bag"),
    ("handbag", "a handbag"),
    ("sunglasses", "a pair of sunglasses"),
    ("watch", "a wristwatch with hands"),
    ("belt buckle", "a belt buckle"),
]
# JEWELRY (needs the accessory declaration; never removed automatically)
JEWELRY = [
    ("necklace", "a necklace"),
    ("earrings", "a pair of earrings"),
    ("bracelet", "a bracelet"),
    ("ring", "a finger ring, a piece of jewelry"),
    ("pendant", "a pendant on a chain"),
    ("brooch", "a brooch pin"),
    ("gold jewelry", "gold jewelry"),
    ("silver jewelry", "silver jewelry"),
    ("platinum jewelry", "platinum jewelry"),
    ("diamond jewelry", "diamond jewelry"),
    ("pearl jewelry", "pearl jewelry"),
    ("gemstone jewelry", "gemstone jewelry"),
]
# NOT_FASHION (not clothing, a bag or a fashion accessory)
NOT_FASHION = [
    ("bicycle", "a bicycle"),
    ("tent", "a camping tent"),
    ("kayak", "a kayak"),
    ("paddleboard", "a stand-up paddleboard"),
    ("ski or snowboard gear", "skis or a snowboard"),
    ("lawn mower", "a lawn mower"),
    ("power tool", "a power tool such as a drill"),
    ("toolbox", "a toolbox"),
    ("furniture", "a piece of furniture such as a chair or table"),
    ("phone", "a mobile phone"),
    ("laptop", "a laptop computer"),
    ("electronics", "an electronic device"),
    ("car", "a car"),
    ("pet", "a pet dog or cat"),
    ("food", "a plate of food"),
    ("document or card", "a paper document or an ID card"),
]
# NOT_ALLOWED (hygiene, safety, policy)
NOT_ALLOWED = [
    ("lingerie", "lingerie"),
    ("underwear", "underwear"),
    ("swimwear", "a swimsuit"),
    ("socks", "a pair of socks"),
    ("pantyhose", "pantyhose tights"),
    ("shoes", "a pair of shoes"),
    ("boots", "a pair of boots"),
    ("hat", "a hat"),
    ("cap", "a baseball cap"),
    ("cowboy hat", "a cowboy hat"),
    ("smartwatch", "a smartwatch with a touch screen"),
]
# NOT_OFFERED (everyday basics)
NOT_OFFERED = [
    ("t-shirt", "a t-shirt"),
    ("track pants", "a pair of track pants"),
    ("sweatpants", "a pair of sweatpants"),
    ("joggers", "a pair of joggers"),
    ("shorts", "a pair of shorts"),
    ("jean shorts", "a pair of denim jean shorts"),
    ("jeans", "a pair of blue jeans"),
]
LABELS = ALLOWED + JEWELRY + NOT_FASHION + NOT_ALLOWED + NOT_OFFERED

# The general check: is the photo fashion at all? Each side is a few whole sentences, averaged
# (no templates), compared with their own two-way softmax. Same order as ItemLabels.GENERAL.
# The negative side names kinds of things instead of saying "not clothing": CLIP doesn't
# understand "not", so "a photo of something that is not clothing" scored 40 clothing photos
# as 87% (median) "not clothing". With these sentences the highest was 21%.
GENERAL = [
    ("fashion", [
        "a photo of clothing, a bag or a fashion accessory",
        "a photo of a garment",
        "a photo of a person wearing clothes",
    ]),
    ("not fashion", [
        "a photo of a vehicle",
        "a photo of a tool",
        "a photo of furniture",
        "a photo of an electronic device",
        "a photo of an animal",
        "a photo of food",
        "a photo of sports equipment",
        "a photo of a document",
    ]),
]

# Patterns for the title ("Navy polka-dot shirt"), same order as ItemLabels.PATTERNS.
PATTERNS = [
    ("solid", "plain solid-colored"),
    ("striped", "striped"),
    ("polka dot", "polka dot"),
    ("floral", "floral print"),
    ("plaid", "plaid checked"),
    ("embroidered", "embroidered"),
    ("sequined", "sequined"),
]
PATTERN_TEMPLATES = [
    "a photo of {} clothing.",
    "a photo of a {} garment.",
    "a close-up photo of {} fabric.",
]

# Fabric guesses: only ever a faint hint under Description, never saved. Same order as
# ItemLabels.FABRICS.
FABRICS = [
    ("silk", "silk"),
    ("satin", "satin"),
    ("cotton", "cotton"),
    ("linen", "linen"),
    ("wool", "wool"),
    ("denim", "denim"),
    ("velvet", "velvet"),
    ("chiffon", "chiffon"),
    ("leather", "leather"),
    ("lace", "lace"),
]
FABRIC_TEMPLATES = [
    "a photo of clothing made of {}.",
    "a close-up photo of {} fabric.",
]

# Averaged per label, as in CLIP's prompt ensembling.
TEMPLATES = [
    "a photo of {}.",
    "a product photo of {}.",
    "a photo of {} laid flat.",
    "a photo of {} on a hanger.",
    "a photo of a person wearing {}.",
]

# The same numbers as PhotoThresholds.kt, for check_model.py's "the app would..." line.
LOGIT_SCALE = 50.0
SUGGEST_CONFIDENCE = 0.45
POLICY_CONFIDENCE = 0.60
REMOVE_CONFIDENCE = 0.70
GENERAL_NOT_FASHION_CONFIDENCE = 0.70
NOT_FASHION_LABEL_CONFIDENCE = 0.50
JEWELRY_CONFIDENCE = 0.50
SAME_ITEM_MIN_SIMILARITY = 0.65

# Label -> tier for every label that isn't ALLOWED, as in ItemLabels.kt.
TIERS = {
    name: tier
    for tier, rows in (("JEWELRY", JEWELRY), ("NOT_FASHION", NOT_FASHION), ("NOT_ALLOWED", NOT_ALLOWED), ("NOT_OFFERED", NOT_OFFERED))
    for name, _ in rows
}

# Label -> policy group (NOT_ALLOWED / NOT_OFFERED only), as in ItemLabels.kt.
POLICY_GROUPS = {
    "lingerie": "lingerie", "underwear": "underwear", "swimwear": "swimwear",
    "socks": "socks and hosiery", "pantyhose": "socks and hosiery",
    "shoes": "shoes and boots", "boots": "shoes and boots",
    "hat": "hats and caps", "cap": "hats and caps", "cowboy hat": "hats and caps",
    "smartwatch": "smartwatches",
    "t-shirt": "T-shirts", "track pants": "track pants", "sweatpants": "sweatpants",
    "joggers": "joggers", "shorts": "shorts", "jean shorts": "shorts", "jeans": "jeans",
}
