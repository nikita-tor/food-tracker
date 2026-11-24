package com.example.greetingcard.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsTest {
    @Test
    fun searchHitsPutUsProductsFirstAndSkipDuplicates() {
        // Shaped like a search.openfoodfacts.org response.
        val json = JSONObject(
            """
            {"hits": [
              {"code": "79754644", "brands": ["Golden Curry"], "product_name": "Golden Curry (Medium Hot)"},
              {"code": "0012579", "brands": ["S&B"], "quantity": "220g",
               "countries_tags": ["en:united-states"], "product_name": "Golden Curry Mild"},
              {"code": "00012579", "brands": ["S&B"], "product_name": "Golden Curry Mild"},
              {"code": "123", "brands": ["No name"]},
              {"product_name": "No barcode"}
            ]}
            """,
        )
        val hits = OpenFoodFacts.parseSearch(json)
        assertEquals(listOf("0012579", "79754644"), hits.map { it.barcode })
        assertEquals(SearchHit("0012579", "Golden Curry Mild", "S&B", "220g"), hits[0])
        // The brand is left out when the name already says it.
        assertEquals("", hits[1].brand)
    }

    @Test
    fun productLookupReadsSodiumInMilligramsAndAddedSugars() {
        val json = JSONObject(
            """
            {"status": 1, "product": {"product_name": "Ramen", "serving_size": "1 package (85 g)",
              "nutriments": {"proteins_serving": 9, "sodium_serving": 1.58, "added-sugars_serving": 1}}}
            """,
        )
        val n = OpenFoodFacts.parse("123", json)!!.perServing
        assertEquals(1580.0, n.sodium!!, 1e-9)
        assertEquals(1.0, n.addedSugars!!, 0.0)
        // Missing values stay unknown.
        val bare = JSONObject("""{"status": 1, "product": {"nutriments": {"proteins_100g": 3}}}""")
        assertNull(OpenFoodFacts.parse("1", bare)!!.perServing.sodium)
    }

    @Test
    fun emptySearchResponseHasNoHits() {
        assertTrue(OpenFoodFacts.parseSearch(JSONObject("{}")).isEmpty())
    }
}
