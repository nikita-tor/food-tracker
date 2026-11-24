package com.example.greetingcard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RestaurantsTest {
    private val fiveGuys = Chain("Five Guys", "https://example.com", "2026-10-19", aliases = listOf("5 guys"))
    private val chilis = Chain("Chili's", "https://example.com", "2026-10-19")
    private val roadhouse = Chain("Texas Roadhouse", "https://example.com", "2026-10-19")
    private val chickFilA = Chain("Chick-fil-A", "https://example.com", "2026-10-19")

    private fun item(chain: Chain, name: String) = MenuItem(chain, name, "1 order", Nutrients.ZERO)

    private val items = listOf(
        item(fiveGuys, "Bacon Cheeseburger"),
        item(fiveGuys, "Cheeseburger"),
        item(fiveGuys, "Little Cheeseburger"),
        item(fiveGuys, "Fries, Regular"),
        item(chilis, "Triple Dipper pick: Big Mouth Bites"),
        item(chilis, "Triple Dipper pick: Southwestern Eggrolls"),
        item(chilis, "Bacon Cheeseburger"),
        item(roadhouse, "Texas Red Chili (no beans) Side - Cup"),
        item(chickFilA, "Chick-fil-A Chicken Sandwich"),
    )

    private fun names(query: String) = Restaurants.search(items, query).map { "${it.chain.name}: ${it.name}" }

    @Test
    fun chainWordsNarrowToThatChain() {
        assertEquals(
            listOf("Five Guys: Cheeseburger", "Five Guys: Bacon Cheeseburger", "Five Guys: Little Cheeseburger"),
            names("five guys cheeseburger"),
        )
        assertEquals("Five Guys: Cheeseburger", names("5 guys cheeseburgers").first())
    }

    @Test
    fun itemNamesMatchAcrossChains() {
        assertEquals(
            listOf(
                "Five Guys: Cheeseburger",
                "Five Guys: Bacon Cheeseburger",
                "Five Guys: Little Cheeseburger",
                "Chili's: Bacon Cheeseburger",
            ),
            names("cheeseburger"),
        )
        // Shorter names first, as for common foods.
        assertEquals(
            listOf(
                "Chili's: Triple Dipper pick: Southwestern Eggrolls",
                "Chili's: Triple Dipper pick: Big Mouth Bites",
            ),
            names("triple dipper"),
        )
    }

    @Test
    fun aChainNameListsItsMenuFirstInMenuOrder() {
        // "chilis" and "chili's" both mean the chain, ahead of another chain's chili.
        val expected = listOf(
            "Chili's: Triple Dipper pick: Big Mouth Bites",
            "Chili's: Triple Dipper pick: Southwestern Eggrolls",
            "Chili's: Bacon Cheeseburger",
            "Texas Roadhouse: Texas Red Chili (no beans) Side - Cup",
        )
        assertEquals(expected, names("chilis"))
        assertEquals(expected, names("Chili's"))
        assertEquals(listOf("Chick-fil-A: Chick-fil-A Chicken Sandwich"), names("chick fil a sandwich"))
    }

    @Test
    fun everyWordHasToMatch() {
        assertTrue(names("five guys taco").isEmpty())
        assertTrue(names("  ").isEmpty())
    }

    @Test
    fun displayNameAddsTheChainOnce() {
        assertEquals("Five Guys Cheeseburger", Restaurants.displayName(item(fiveGuys, "Cheeseburger")))
        assertEquals("Chick-fil-A Chicken Sandwich", Restaurants.displayName(items.last()))
    }

    @Test
    fun sourceNoteSaysWhereAndWhen() {
        val note = Restaurants.sourceNote(item(chilis, "Bacon Cheeseburger"))
        assertTrue(note, note.startsWith("From Chili's nutrition info (October 2026)."))
    }

    /** Checks the real bundled list (unit tests run from the app module folder). */
    @Test
    fun bundledListLoadsAndFindsTheFeedbackExamples() {
        val all = Restaurants.parse(File("src/main/assets/${Restaurants.ASSET}").readText())
        assertTrue(all.size > 1000)
        assertTrue(all.map { it.chain.name }.toSet().containsAll(listOf("Five Guys", "Chili's", "Chick-fil-A")))
        assertTrue(all.all { it.nutrients.calories != null && it.nutrients.sodium != null })
        assertTrue(all.all { it.nutrients.fruit == 0.0 && it.nutrients.veg == 0.0 })

        val burger = Restaurants.search(all, "five guys cheeseburger").first()
        assertEquals("Five Guys Cheeseburger", Restaurants.displayName(burger))
        assertEquals(47.0, burger.nutrients.protein, 0.0)

        val picks = Restaurants.search(all, "triple dipper")
        assertTrue(picks.size >= 10)
        assertTrue(picks.all { it.chain.name == "Chili's" && it.serving == "1 of your 3 picks" })
        // Chili's lists total sugars only: added sugars stay unknown instead of counting as 0.
        assertNull(picks.first().nutrients.addedSugars)
        // Taco Bell does publish added sugars.
        assertTrue(all.any { it.chain.name == "Taco Bell" && it.nutrients.addedSugars != null })

        // Jersey Mike's: Regular subs only, found with or without the apostrophe.
        val sub = Restaurants.search(all, "jersey mikes turkey").first()
        assertEquals("Jersey Mike's #7 Turkey and Provolone", Restaurants.displayName(sub))
        assertEquals("1 Regular sub, white bread, Mike's Way", sub.serving)
        assertEquals(Restaurants.search(all, "jersey mike's philly"), Restaurants.search(all, "jersey mikes philly"))
        assertTrue(all.filter { it.chain.name == "Jersey Mike's" }.all { it.serving.startsWith("1 Regular sub") })
    }
}
