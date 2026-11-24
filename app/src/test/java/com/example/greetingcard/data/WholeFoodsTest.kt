package com.example.greetingcard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WholeFoodsTest {
    private fun food(name: String, vararg aliases: String) =
        WholeFood(name, Nutrients.ZERO, listOf(Portion("1 cup", 100.0)), aliases.toList())

    private val foods = listOf(
        food("Egg, fried", "eggs"),
        food("Egg noodles, cooked", "noodles"),
        food("Eggplant, raw", "aubergine"),
        food("Vegetable oil"),
        food("Vegetable juice", "v8"),
        food("Olive oil"),
        food("Blueberries"),
        food("Tomato", "tomatoes"),
        food("Chickpeas, canned", "garbanzo"),
    )

    private fun names(query: String) = WholeFoods.search(foods, query).map { it.name }

    @Test
    fun wordsStartingWithTheQueryComeBeforeLongerWords() {
        assertEquals(listOf("Egg, fried", "Egg noodles, cooked", "Eggplant, raw"), names("egg"))
    }

    @Test
    fun everyWordHasToMatch() {
        assertEquals(listOf("Vegetable oil"), names("vegetable oil"))
        assertEquals(listOf("Vegetable oil", "Olive oil"), names("oil").sortedDescending())
    }

    @Test
    fun pluralsAndPartialWordsMatch() {
        assertEquals(listOf("Blueberries"), names("blueberry"))
        assertEquals(listOf("Blueberries"), names("bluebe"))
        assertEquals("Egg, fried", names("eggs").first())
        assertEquals(listOf("Tomato"), names("tomatoes"))
    }

    @Test
    fun aliasesMatchButRankLast() {
        assertEquals(listOf("Chickpeas, canned"), names("garbanzo"))
        assertEquals(listOf("Egg noodles, cooked"), names("noodles"))
        assertEquals(listOf("Vegetable juice"), names("V8"))
    }

    @Test
    fun blankQueryFindsNothing() {
        assertTrue(names("  ").isEmpty())
        assertTrue(names("pizza").isEmpty())
    }

    @Test
    fun portionsScaleTheValuesPer100Grams() {
        val banana = WholeFood("Banana", Nutrients(protein = 1.0, fruit = 0.67, calories = 97.0), emptyList())
        val n = WholeFoods.nutrientsFor(banana, Portion("1 banana", 126.0))
        assertEquals(1.26, n.protein, 1e-9)
        assertEquals(0.8442, n.fruit, 1e-9)
        assertEquals(122.22, n.calories!!, 1e-9)
    }

    @Test
    fun hundredGramsIsAlwaysOffered() {
        val egg = food("Egg").copy(portions = listOf(Portion("1 egg", 50.0)))
        assertEquals(listOf("1 egg", "100 g"), WholeFoods.portionsFor(egg).map { it.label })
        assertEquals(listOf("1 cup"), WholeFoods.portionsFor(food("Milk")).map { it.label })
    }

    @Test
    fun servingLabelShowsTheWeight() {
        assertEquals("1 medium (200 g)", WholeFoods.servingLabel(Portion("1 medium", 200.0)))
        assertEquals("1 oz (28 g)", WholeFoods.servingLabel(Portion("1 oz", 28.4)))
        assertEquals("1 nut (1.2 g)", WholeFoods.servingLabel(Portion("1 nut", 1.2)))
        val egg = food("Egg").copy(portions = listOf(Portion("1 egg", 50.0)))
        assertEquals("100 g", WholeFoods.servingLabel(WholeFoods.portionsFor(egg).last()))
    }

    /** Checks the real bundled list (unit tests run from the app module folder). */
    @Test
    fun bundledListLoadsAndFindsCommonFoods() {
        val all = WholeFoods.parse(File("src/main/assets/${WholeFoods.ASSET}").readText())
        assertTrue(all.size > 300)
        assertEquals(all.size, all.map { it.name.lowercase() }.toSet().size)
        assertTrue(all.all { it.portions.isNotEmpty() && it.portions.all { p -> p.grams > 0 } })

        fun first(query: String) = WholeFoods.search(all, query).first()
        assertEquals("Banana", first("banana").name)
        assertEquals("Vegetable oil", first("vegetable oil").name)
        assertEquals("Orange juice", first("oj").name)
        assertTrue(first("eggs").name.startsWith("Egg,"))

        // Fruit and vegetable servings come from USDA's cup-equivalents: a cup of berries is about one serving.
        val berries = first("strawberries")
        val cup = berries.portions.first { it.label == "1 cup" }
        assertEquals(1.0, WholeFoods.nutrientsFor(berries, cup).fruit, 0.1)
        assertEquals(1.0, WholeFoods.nutrientsFor(first("broccoli raw"), Portion("1 cup", 90.0)).veg, 0.2)
        // 100% juice counts as fruit.
        assertTrue(first("orange juice").per100g.fruit > 0)
        // Every common food knows its sodium and added sugars, so limits work without typing anything.
        assertTrue(all.all { it.per100g.sodium != null && it.per100g.addedSugars != null })
        assertEquals(0.0, first("banana").per100g.addedSugars!!, 0.0)
    }
}
