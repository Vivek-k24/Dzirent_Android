package com.dzirent.app.util

/** One row of the size picker: what it asks ("Waist") and the choices, in order. */
data class SizeStep(val title: String, val options: List<String>)

/**
 * A way to give a size: one or more [steps] (waist, then inseam) whose picks [format] joins into
 * the size label that's saved ("32 x 30").
 */
class SizeSystem(val key: String, val label: String, val steps: List<SizeStep>, private val formatter: (List<String>) -> String) {
    fun format(picks: List<String>): String {
        require(picks.size == steps.size && picks.indices.all { picks[it] in steps[it].options }) { "pick one option per step" }
        return formatter(picks)
    }

    /** The picks that give [label], or null if this system can't (for editing an existing listing). */
    fun parse(label: String): List<String>? {
        val wanted = label.trim()
        var combos = listOf(emptyList<String>())
        steps.forEach { step -> combos = combos.flatMap { c -> step.options.map { c + it } } }
        return combos.firstOrNull { formatter(it).equals(wanted, ignoreCase = true) }
    }
}

object Sizes {
    const val ONE_SIZE = "One size"

    private fun range(from: Int, to: Int, step: Int = 1) = (from..to step step).map { it.toString() }

    val LETTERS = SizeSystem("letter", "Letter", listOf(SizeStep("Size", listOf("XS", "S", "M", "L", "XL", "XXL", "XXXL")))) { it[0] }
    val US = SizeSystem("us", "US", listOf(SizeStep("US size", range(0, 24, 2)))) { "US ${it[0]}" }
    val WAIST_INSEAM = SizeSystem(
        "waist_inseam", "Waist x inseam",
        listOf(SizeStep("Waist", range(26, 58)), SizeStep("Inseam", range(26, 36)))
    ) { "${it[0]} x ${it[1]}" }
    val CHEST_LENGTH = SizeSystem(
        "chest_length", "Chest and length",
        listOf(SizeStep("Chest", range(34, 56, 2)), SizeStep("Length", listOf("S", "R", "L")))
    ) { "${it[0]}${it[1]}" }
    val BLOUSE = SizeSystem("blouse", "Blouse", listOf(SizeStep("Blouse", range(30, 46, 2)))) { "Blouse ${it[0]}" }

    /** The size systems for a kind of garment, the usual one first. Empty for one-size items. */
    fun systemsFor(kind: GarmentKind): List<SizeSystem> = when (kind) {
        GarmentKind.TOP -> listOf(LETTERS)
        GarmentKind.TROUSERS -> listOf(WAIST_INSEAM)
        GarmentKind.SUIT -> listOf(CHEST_LENGTH)
        GarmentKind.DRESS, GarmentKind.SKIRT -> listOf(LETTERS, US)
        GarmentKind.SAREE -> listOf(BLOUSE)
        GarmentKind.ONE_SIZE -> emptyList()
    }

    /** Which system (and picks) an existing size label belongs to, for editing; null if none. */
    fun parse(kind: GarmentKind, label: String): Pair<SizeSystem, List<String>>? =
        systemsFor(kind).firstNotNullOfOrNull { system -> system.parse(label)?.let { system to it } }

    /** One-size kinds need no picker; their size label is fixed. */
    fun fixedLabel(kind: GarmentKind): String? = if (kind == GarmentKind.ONE_SIZE) ONE_SIZE else null
}
