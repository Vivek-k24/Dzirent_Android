package com.dzirent.app.util

import com.dzirent.app.data.model.Categories

/**
 * The accessory declaration, in one place with its version: the full-screen declaration signed
 * once per user, and the checkbox on every accessory listing after that. The version goes into
 * accessory_declarations / listing_declarations (008); change [VERSION] whenever the wording does.
 */
object AccessoryDeclaration {
    const val VERSION = 1

    const val TITLE = "Accessory declaration"

    const val INTRO = "Before you list jewelry or accessories, please confirm:"

    /** Every one must be ticked. */
    val STATEMENTS = listOf(
        "This accessory or jewelry is not real silver, gold or platinum, has no pearls and no diamonds or precious gemstones.",
        "It is not made of precious or expensive metals or materials.",
        "It is worth less than \$${ItemLabels.ACCESSORY_MAX_VALUE_USD}.",
        "I understand a false declaration breaks the Dzirent Terms and that I am responsible for this item."
    )

    const val NAME_LABEL = "Full legal name"

    const val SIGN_BUTTON = "I agree and sign"

    /** Under the button: what the signature is. */
    const val SIGNATURE_NOTE =
        "Your typed name and these ticks are your signature. We keep this record with your email, the time and your city."

    /** The whole declaration as signed, version included. */
    val TEXT: String = "$TITLE (v$VERSION)\n" + STATEMENTS.joinToString("\n") { "- $it" }

    /** Step 2 of every accessory listing after the first declaration. Required. */
    val LISTING_CHECKBOX = "This item isn't fine jewelry or precious metal and is worth under \$${ItemLabels.ACCESSORY_MAX_VALUE_USD}."

    const val NAME_MAX = 120

    /** Null when [name] can be the signature, otherwise why not. */
    fun nameError(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.count { it.isLetter() } < 2 -> "Type your full legal name."
            trimmed.length > NAME_MAX -> "Up to $NAME_MAX characters."
            else -> null
        }
    }

    /** "I agree and sign" works only with every box ticked and a name typed. */
    fun canSign(checked: List<Boolean>, name: String): Boolean =
        checked.size == STATEMENTS.size && checked.all { it } && nameError(name) == null
}

/**
 * The full-screen declaration is needed when the listing is in Accessories or a photo looks like
 * jewelry, and this user hasn't signed the current version yet.
 */
fun declarationNeeded(category: String?, jewelryFound: Boolean, signed: Boolean): Boolean =
    !signed && (category == Categories.ACCESSORIES || jewelryFound)
