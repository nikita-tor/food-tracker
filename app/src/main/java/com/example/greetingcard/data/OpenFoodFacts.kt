package com.example.greetingcard.data

import com.example.greetingcard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Product(val barcode: String, val name: String, val servingLabel: String, val perServing: Nutrients)

sealed interface LookupResult {
    data class Found(val product: Product) : LookupResult
    data object NotFound : LookupResult
    data class Failed(val reason: String) : LookupResult
}

/** A product from a text search. Its nutrients come from a barcode [OpenFoodFacts.lookup] once it's picked. */
data class SearchHit(val barcode: String, val name: String, val brand: String, val quantity: String)

sealed interface SearchResult {
    data class Found(val hits: List<SearchHit>) : SearchResult
    data class Failed(val reason: String) : SearchResult
}

/**
 * Open Food Facts, a free, open product database (no API key): barcode lookup, and text search
 * for packaged foods. Search runs only when the user asks for it, never as they type, which the
 * service doesn't allow.
 */
object OpenFoodFacts {
    private const val FIELDS = "product_name,brands,serving_size,nutriments"
    private const val SEARCH_FIELDS = "code,product_name,brands,quantity,countries_tags"
    private const val SEARCH_PAGE_SIZE = 25
    private const val US = "en:united-states"

    suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        val code = barcode.filter(Char::isDigit)
        if (code.isEmpty()) return@withContext LookupResult.NotFound
        val connection = open("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$FIELDS")
        try {
            if (connection.responseCode == 404) return@withContext LookupResult.NotFound
            if (connection.responseCode != 200) {
                return@withContext LookupResult.Failed("Server returned ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            parse(code, json)?.let { LookupResult.Found(it) } ?: LookupResult.NotFound
        } catch (e: Exception) {
            LookupResult.Failed(e.message ?: "Network error")
        } finally {
            connection.disconnect()
        }
    }

    fun parse(code: String, json: JSONObject): Product? {
        if (json.optInt("status") != 1) return null
        val product = json.optJSONObject("product") ?: return null
        val nutriments = product.optJSONObject("nutriments") ?: JSONObject()
        fun value(key: String): Double? =
            if (nutriments.has(key)) nutriments.optDouble(key).takeUnless { it.isNaN() } else null

        // Prefer per-serving numbers; fall back to per 100 g.
        val hasServing = listOf("proteins_serving", "fiber_serving", "energy-kcal_serving").any(nutriments::has)
        val suffix = if (hasServing) "_serving" else "_100g"
        val servingLabel = if (hasServing) product.optString("serving_size").ifBlank { "1 serving" } else "100 g"

        val name = product.optString("product_name").trim()
        val brand = brandUnlessInName(product.optString("brands").split(',').first(), name)
        return Product(
            barcode = code,
            name = listOf(brand, name).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Product $code" },
            servingLabel = servingLabel,
            perServing = Nutrients(
                protein = value("proteins$suffix") ?: 0.0,
                fiber = value("fiber$suffix") ?: 0.0,
                calories = value("energy-kcal$suffix"),
                // Open Food Facts stores sodium in grams.
                sodium = value("sodium$suffix")?.times(1000),
                addedSugars = value("added-sugars$suffix"),
            ),
        )
    }

    /** Packaged foods matching [query], products sold in the US first. */
    suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val connection =
            open("https://search.openfoodfacts.org/search?q=$q&page_size=$SEARCH_PAGE_SIZE&fields=$SEARCH_FIELDS")
        try {
            if (connection.responseCode != 200) {
                return@withContext SearchResult.Failed("Server returned ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            SearchResult.Found(parseSearch(json))
        } catch (e: Exception) {
            SearchResult.Failed(e.message ?: "Network error")
        } finally {
            connection.disconnect()
        }
    }

    fun parseSearch(json: JSONObject): List<SearchHit> {
        val hits = json.optJSONArray("hits") ?: return emptyList()
        val parsed = (0 until hits.length()).mapNotNull { i ->
            val hit = hits.optJSONObject(i) ?: return@mapNotNull null
            val code = hit.optString("code").filter(Char::isDigit)
            val name = hit.optString("product_name").trim()
            if (code.isEmpty() || name.isEmpty()) return@mapNotNull null
            val countries = hit.optJSONArray("countries_tags")
            val inUs = countries != null && (0 until countries.length()).any { countries.optString(it) == US }
            // "brands" is a list here, unlike in product lookups.
            val brand = hit.optJSONArray("brands")?.optString(0) ?: hit.optString("brands")
            inUs to SearchHit(code, name, brandUnlessInName(brand, name), hit.optString("quantity").trim())
        }
        return parsed
            // The same product is sometimes listed under its barcode with and without leading zeros.
            .distinctBy { (_, hit) -> hit.barcode.trimStart('0') }
            .sortedByDescending { (inUs, _) -> inUs }
            .map { (_, hit) -> hit }
    }

    /** Skips the brand when the product name already says it ("Nutella" by Nutella). */
    private fun brandUnlessInName(brand: String, name: String): String =
        brand.trim().takeUnless { name.contains(it, ignoreCase = true) }.orEmpty()

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        // Open Food Facts asks apps to identify themselves.
        setRequestProperty("User-Agent", "FoodTracker-Android/${BuildConfig.VERSION_NAME}")
    }
}
