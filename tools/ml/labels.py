"""The item labels the app recognizes, in the same order as ItemLabels.ALL in
android/app/src/main/java/com/dzirent/app/util/ItemLabels.kt.

Each label has the phrase the text embeddings are made from. CLIP knows common English
clothing words well; less common ones (saree, potli bag) get a few words of description.
The unit test ItemLabelsTest checks that the shipped embeddings list exactly these names,
in this order, so a label added here must be added there too (with its tier and category).
"""

# (label name as in ItemLabels.kt, phrase used in the prompts)
LABELS = [
    # ALLOWED
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
    ("hoodie", "a hoodie"),
    ("denim jacket", "a denim jacket"),
    ("bomber jacket", "a bomber jacket"),
    ("overcoat", "a wool overcoat"),
    ("trench coat", "a trench coat"),
    ("saree", "a saree, an Indian draped dress"),
    ("lehenga", "a lehenga, an embroidered Indian skirt and blouse"),
    ("anarkali", "an anarkali, a long flared Indian dress"),
    ("kurta", "a kurta, a long Indian tunic"),
    ("sherwani", "a sherwani, a long Indian coat for men"),
    ("tuxedo", "a tuxedo"),
    ("clutch", "a clutch purse"),
    ("potli bag", "a potli bag, a small drawstring pouch purse"),
    ("crossbody bag", "a crossbody bag"),
    ("handbag", "a handbag"),
    ("sunglasses", "a pair of sunglasses"),
    ("watch", "a wristwatch with hands"),
    ("belt buckle", "a belt buckle"),
    # NOT_ALLOWED (hygiene, safety, policy)
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
    # NOT_OFFERED (everyday basics)
    ("t-shirt", "a t-shirt"),
    ("track pants", "a pair of track pants"),
    ("sweatpants", "a pair of sweatpants"),
    ("joggers", "a pair of joggers"),
    ("shorts", "a pair of shorts"),
    ("jean shorts", "a pair of denim jean shorts"),
    ("jeans", "a pair of blue jeans"),
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
LOGIT_SCALE = 100.0
SUGGEST_CONFIDENCE = 0.45
POLICY_CONFIDENCE = 0.60
SAME_ITEM_MIN_SIMILARITY = 0.72

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
