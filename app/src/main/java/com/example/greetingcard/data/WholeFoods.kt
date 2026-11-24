package com.example.greetingcard.data

import org.json.JSONObject
import kotlin.math.roundToInt

/** An amount people actually eat, e.g. "1 medium" = 200 g. */
data class Portion(val label: String, val grams: Double)

/** A food from the bundled common-foods list, with nutrients per 100 g. */
data class WholeFood(
    val name: String,
    val per100g: Nutrients,
    val portions: List<Portion>,
    val aliases: List<String> = emptyList(),
) {
    internal val nameWords = WholeFoods.words(name)
    internal val aliasWords = aliases.flatMap(WholeFoods::words)
}

/**
 * Common whole foods (banana, egg, olive oil…) searched offline. The list lives in `assets/foods.json`,
 * generated from USDA FNDDS and FPED data by `tools/foods/build_food_list.py`. Fruit and vegetable
 * servings come from FPED's cup-equivalents, so they match what the goals count.
 */
object WholeFoods {
    const val ASSET = "foods.json"
    const val SOURCE_URL = "https://fdc.nal.usda.gov/"
    const val SOURCE_NOTE = "Average values from USDA. Adjust them if yours is different."
    private val GENERIC_PORTION = Portion("100 g", 100.0)

    /** Reads foods.json. Nutrients the app doesn't use (carbs, fat, iron…) are in the file but skipped here. */
    fun parse(text: String): List<WholeFood> {
        val foods = JSONObject(text).getJSONArray("foods")
        return (0 until foods.length()).map { i ->
            val food = foods.getJSONObject(i)
            val n = food.getJSONObject("per100g")
            val portions = food.getJSONArray("portions")
            val aliases = food.optJSONArray("aliases")
            WholeFood(
                name = food.getString("name"),
                per100g = Nutrients(
                    protein = n.optDouble("protein", 0.0),
                    fiber = n.optDouble("fiber", 0.0),
                    fruit = n.optDouble("fruit", 0.0),
                    veg = n.optDouble("veg", 0.0),
                    calories = n.optDouble("calories").takeUnless { it.isNaN() },
                    sodium = n.optDouble("sodium").takeUnless { it.isNaN() },
                    addedSugars = n.optDouble("addedSugars").takeUnless { it.isNaN() },
                ),
                portions = (0 until portions.length()).map { p ->
                    portions.getJSONObject(p).let { Portion(it.getString("label"), it.getDouble("grams")) }
                },
                aliases = if (aliases == null) emptyList() else (0 until aliases.length()).map(aliases::getString),
            )
        }
    }

    /**
     * Foods where every word of [query] starts a word of the name or an alias, best first:
     * an exact name, then names starting with the query, then other name matches, then alias matches.
     */
    fun search(foods: List<WholeFood>, query: String, limit: Int = 20): List<WholeFood> {
        val terms = words(query)
        if (terms.isEmpty()) return emptyList()
        return foods
            .mapNotNull { food -> rank(food, terms)?.let { food to it } }
            .sortedWith(compareBy({ it.second }, { it.first.nameWords.size }, { it.first.name }))
            .take(limit)
            .map { it.first }
    }

    private fun rank(food: WholeFood, terms: List<String>): Int? {
        val inName = terms.all { t -> food.nameWords.any { it.startsWith(t) } }
        val inAliases = terms.all { t -> (food.nameWords + food.aliasWords).any { it.startsWith(t) } }
        val first = food.nameWords.firstOrNull() ?: return null
        return when {
            inName && food.nameWords == terms -> 0
            inName && first == terms.first() -> 1
            inName && first.startsWith(terms.first()) -> 2
            inName -> 3
            inAliases -> 4
            else -> null
        }
    }

    /** The food's own portions plus 100 g, for weighing. */
    fun portionsFor(food: WholeFood): List<Portion> =
        if (food.portions.any { it.grams == 100.0 }) food.portions else food.portions + GENERIC_PORTION

    fun nutrientsFor(food: WholeFood, portion: Portion): Nutrients = food.per100g * (portion.grams / 100)

    /** "1 medium (200 g)", or just "100 g". */
    fun servingLabel(portion: Portion): String =
        if (portion == GENERIC_PORTION) portion.label else "${portion.label} (${grams(portion.grams)} g)"

    private fun grams(value: Double): String =
        if (value >= 10) value.roundToInt().toString() else value.toString().removeSuffix(".0")

    /**
     * Lowercase words with simple plurals removed, so "Blueberries" matches "blueberry" and "eggs" matches "Egg".
     * Apostrophes are dropped rather than splitting words, so "chilis" matches "Chili's".
     */
    internal fun words(text: String): List<String> =
        text.lowercase().replace(Regex("['’]"), "").split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            .map(::singular)

    private fun singular(word: String): String = when {
        word.length > 4 && word.endsWith("ies") -> word.dropLast(3) + "y"
        word.length > 4 && word.endsWith("oes") -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }
}
