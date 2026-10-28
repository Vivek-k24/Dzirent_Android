package com.dzirent.app.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.Normalizer

/** What a search query matched in search_related.json. */
data class RelatedMatch(
    /** The keyword, with a misspelling already corrected ("lehnga" gives "lehenga"). */
    val keyword: String,
    /** Words to look for instead, in order. */
    val words: List<String>,
    /** One of [com.dzirent.app.data.model.Categories.ALL], or null. */
    val category: String?
)

/** Longest phrase looked up, in words ("chaniya choli", "wedding guest"). */
private const val MAX_PHRASE_WORDS = 3

/**
 * The related-words dictionary (assets/search_related.json), no AI involved. Lookups
 * ignore case, accents and plurals, and prefer the longest phrase in the query.
 */
class SearchRelated(
    private val related: Map<String, List<String>>,
    private val categoryHints: Map<String, String>
) {
    /** The best keyword in [query] and what goes with it, or null if none is known. */
    fun lookup(query: String): RelatedMatch? {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        for (n in minOf(MAX_PHRASE_WORDS, words.size) downTo 1) {
            for (start in 0..words.size - n) {
                for (phrase in phraseForms(words.subList(start, start + n))) {
                    if (phrase in related || phrase in categoryHints) return matchFor(phrase)
                }
            }
        }
        return null
    }

    private fun matchFor(key: String): RelatedMatch {
        val keyword = aliasTarget(key) ?: key
        return RelatedMatch(
            keyword = keyword,
            words = related[keyword].orEmpty(),
            category = categoryHints[keyword] ?: categoryHints[key]
        )
    }

    /** A key whose only related word is another key is a spelling of that key. */
    private fun aliasTarget(key: String): String? =
        related[key]?.singleOrNull()?.takeIf { it != key && it in related }

    companion object {
        /** Parses search_related.json (unknown top-level fields such as "_about" are ignored). */
        fun parse(json: String): SearchRelated {
            val root = Json.parseToJsonElement(json).jsonObject
            val related = (root["related"] as? JsonObject).orEmpty().mapKeys { normalize(it.key) }
                .mapValues { (_, v) -> v.jsonArray.map { normalize(it.jsonPrimitive.content) } }
            val hints = (root["category_hints"] as? JsonObject).orEmpty().mapKeys { normalize(it.key) }
                .mapValues { (_, v) -> v.jsonPrimitive.content }
            return SearchRelated(related, hints)
        }

        /** Lowercase, no accents, letters and digits only, single spaces. */
        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

        /** The phrase as typed, then with each word made singular ("sarees" -> "saree"). */
        private fun phraseForms(words: List<String>): List<String> =
            listOf(words.joinToString(" "), words.joinToString(" ") { singular(it) }).distinct()

        /** dresses -> dress, tuxes -> tux, accessories -> accessory, sarees -> saree. */
        private fun singular(word: String): String = when {
            word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
            word.length > 4 && listOf("sses", "xes", "ches", "shes").any { word.endsWith(it) } -> word.dropLast(2)
            word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
            else -> word
        }
    }
}
