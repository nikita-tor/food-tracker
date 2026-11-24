package com.example.greetingcard.data

import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A restaurant chain, with the page its nutrition numbers come from and when they were fetched (ISO date). */
data class Chain(val name: String, val page: String, val checked: String, val aliases: List<String> = emptyList()) {
    internal val words = WholeFoods.words(name) + aliases.flatMap(WholeFoods::words)
}

/**
 * One item from a chain's menu, as served. Restaurants don't publish fruit or vegetable servings, so those are 0,
 * and added sugars are null (unknown) for chains that only list total sugars.
 */
data class MenuItem(val chain: Chain, val name: String, val serving: String, val nutrients: Nutrients) {
    internal val nameWords = WholeFoods.words(name)
}

/**
 * Restaurant chain menus searched offline. The list lives in `assets/restaurants.json`, built by
 * `tools/restaurants/` from each chain's own published nutrition info.
 */
object Restaurants {
    const val ASSET = "restaurants.json"

    fun parse(text: String): List<MenuItem> {
        val json = JSONObject(text)
        val chainsJson = json.getJSONArray("chains")
        val chains = (0 until chainsJson.length()).map { i ->
            val c = chainsJson.getJSONObject(i)
            val aliases = c.optJSONArray("aliases")
            Chain(
                name = c.getString("name"),
                page = c.getString("page"),
                checked = c.getString("checked"),
                aliases = if (aliases == null) emptyList() else (0 until aliases.length()).map(aliases::getString),
            )
        }
        val items = json.getJSONArray("items")
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            MenuItem(
                chain = chains[item.getInt("chain")],
                name = item.getString("name"),
                serving = item.getString("serving"),
                nutrients = Nutrients(
                    protein = item.optDouble("protein", 0.0),
                    fiber = item.optDouble("fiber", 0.0),
                    calories = item.optDouble("calories").takeUnless { it.isNaN() },
                    sodium = item.optDouble("sodium").takeUnless { it.isNaN() },
                    addedSugars = item.optDouble("addedSugars").takeUnless { it.isNaN() },
                ),
            )
        }
    }

    /**
     * Items where every word of [query] starts a word of the item or its chain's name, best first. A query that is
     * just a chain lists its menu in order. Words that only match the chain ("five guys cheeseburger") narrow to
     * that chain; the rest rank as for common foods: an exact name, names starting with the query, other matches.
     */
    fun search(items: List<MenuItem>, query: String, limit: Int = 30): List<MenuItem> {
        val terms = WholeFoods.words(query)
        if (terms.isEmpty()) return emptyList()
        return items
            .mapIndexedNotNull { index, item -> rank(item, terms)?.let { Triple(item, it, index) } }
            .sortedWith(
                compareBy(
                    { it.second },
                    { if (it.second == CHAIN_MENU) 0 else it.first.nameWords.size },
                    { it.third },
                ),
            )
            .take(limit)
            .map { it.first }
    }

    /** Typing a chain's name shows its menu before other chains' items that happen to share the word ("chili"). */
    private const val CHAIN_MENU = 0

    private fun rank(item: MenuItem, terms: List<String>): Int? {
        fun inName(t: String) = item.nameWords.any { it.startsWith(t) }
        fun inChain(t: String) = item.chain.words.any { it.startsWith(t) }
        if (!terms.all { inName(it) || inChain(it) }) return null
        if (terms.all(::inChain)) return CHAIN_MENU
        // Rank on the name with the chain's words taken out, unless every word is in the name.
        val nameTerms = if (terms.all(::inName)) terms else terms.filterNot(::inChain)
        val first = item.nameWords.firstOrNull() ?: return null
        return when {
            item.nameWords == nameTerms -> 1
            first.startsWith(nameTerms.first()) -> 2
            else -> 3
        }
    }

    /** "Five Guys Cheeseburger", but "Chick-fil-A Chicken Sandwich" rather than repeating the chain. */
    fun displayName(item: MenuItem): String =
        if (item.name.contains(item.chain.name, ignoreCase = true)) item.name else "${item.chain.name} ${item.name}"

    /** Shown at the top of the food form. */
    fun sourceNote(item: MenuItem): String {
        val checked = runCatching {
            LocalDate.parse(item.chain.checked).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
        }.getOrDefault(item.chain.checked)
        // No possessive: "Chili's's" reads wrong, and "Five Guys nutrition info" reads fine.
        return "From ${item.chain.name} nutrition info ($checked). Restaurants don't list fruit or vegetable " +
            "servings, so add those if your order had some."
    }
}
