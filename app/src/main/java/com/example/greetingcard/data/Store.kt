package com.example.greetingcard.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Keeps all app data in one small JSON file in app-private storage. */
class Store(context: Context) {
    private val file = File(context.filesDir, "food_tracker.json")

    suspend fun load(): AppData = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext AppData()
        try {
            AppDataJson.decode(file.readText())
        } catch (e: Exception) {
            // Keep the unreadable file instead of overwriting it on the next save.
            Log.e(TAG, "Could not read saved data", e)
            file.renameTo(File(file.parentFile, "food_tracker.corrupt-${System.currentTimeMillis()}.json"))
            AppData()
        }
    }

    suspend fun save(data: AppData) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(AppDataJson.encode(data))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val TAG = "Store"
    }
}

/**
 * The JSON format of the data file. New fields must be optional when decoding,
 * so files written by older versions of the app still load.
 */
internal object AppDataJson {
    fun encode(data: AppData): String = JSONObject()
        .put("version", 1)
        .put("entries", JSONArray(data.entries.map(::encodeEntry)))
        .put("saved", JSONArray(data.saved.map(::encodeSaved)))
        .put("settings", encodeSettings(data.settings))
        .put("notes", JSONObject(data.notes))
        .toString()

    fun decode(text: String): AppData {
        val json = JSONObject(text)
        return AppData(
            entries = json.optJSONArray("entries").objects().map(::decodeEntry),
            saved = json.optJSONArray("saved").objects().map(::decodeSaved),
            settings = json.optJSONObject("settings")?.let(::decodeSettings) ?: Settings(),
            notes = json.optJSONObject("notes")?.let { notes ->
                notes.keys().asSequence().associateWith { notes.optString(it) }
            }.orEmpty(),
        )
    }

    private fun encodeNutrients(n: Nutrients) = JSONObject()
        .put("protein", n.protein)
        .put("fiber", n.fiber)
        .put("fruit", n.fruit)
        .put("veg", n.veg)
        .apply {
            n.calories?.let { put("calories", it) }
            n.sodium?.let { put("sodium", it) }
            n.addedSugars?.let { put("addedSugars", it) }
        }

    private fun decodeNutrients(json: JSONObject?) = if (json == null) {
        Nutrients.ZERO
    } else {
        Nutrients(
            protein = json.optDouble("protein", 0.0),
            fiber = json.optDouble("fiber", 0.0),
            fruit = json.optDouble("fruit", 0.0),
            veg = json.optDouble("veg", 0.0),
            calories = json.doubleOrNull("calories"),
            sodium = json.doubleOrNull("sodium"),
            addedSugars = json.doubleOrNull("addedSugars"),
        )
    }

    private fun encodeEntry(e: FoodEntry) = JSONObject()
        .put("id", e.id)
        .put("date", e.date)
        .put("meal", e.meal.name)
        .put("name", e.name)
        .put("servingLabel", e.servingLabel)
        .put("servings", e.servings)
        .put("perServing", encodeNutrients(e.perServing))

    private fun decodeEntry(json: JSONObject) = FoodEntry(
        id = json.getString("id"),
        date = json.getString("date"),
        meal = enumOr(json.optString("meal"), Meal.SNACK),
        name = json.optString("name"),
        servingLabel = json.optString("servingLabel"),
        servings = json.optDouble("servings", 1.0),
        perServing = decodeNutrients(json.optJSONObject("perServing")),
    )

    private fun encodeSaved(s: SavedFood) = JSONObject()
        .put("id", s.id)
        .put("name", s.name)
        .put("servingLabel", s.servingLabel)
        .put("perServing", encodeNutrients(s.perServing))
        .put("isMeal", s.isMeal)
        .put("barcode", s.barcode ?: JSONObject.NULL)
        .put("useCount", s.useCount)
        .put("lastUsed", s.lastUsed)

    private fun decodeSaved(json: JSONObject) = SavedFood(
        id = json.getString("id"),
        name = json.optString("name"),
        servingLabel = json.optString("servingLabel"),
        perServing = decodeNutrients(json.optJSONObject("perServing")),
        isMeal = json.optBoolean("isMeal"),
        barcode = if (json.isNull("barcode")) null else json.optString("barcode"),
        useCount = json.optInt("useCount"),
        lastUsed = json.optLong("lastUsed"),
    )

    private fun encodeSettings(s: Settings) = JSONObject()
        .put("name", s.name)
        .put("calorieMode", s.calorieMode.name)
        .put("sex", s.profile.sex.name)
        .put("age", s.profile.age ?: JSONObject.NULL)
        .put("weightKg", s.profile.weightKg ?: JSONObject.NULL)
        .put("useLbs", s.profile.useLbs)
        .put("customGoals", JSONObject().apply { s.customGoals.forEach { (k, v) -> put(k.name, v) } })
        .put("hiddenNutrients", JSONArray(s.hiddenNutrients.map { it.name }))
        .put("enabledLimits", JSONArray(s.enabledLimits.map { it.name }))
        .put("customLimits", JSONObject().apply { s.customLimits.forEach { (k, v) -> put(k.name, v) } })
        .put("lastBackup", s.lastBackup ?: JSONObject.NULL)

    private fun decodeSettings(json: JSONObject): Settings {
        val goals = json.optJSONObject("customGoals")
        val limits = json.optJSONObject("customLimits")
        return Settings(
            name = json.optString("name"),
            calorieMode = enumOr(json.optString("calorieMode"), CalorieMode.SHOW),
            profile = Profile(
                sex = enumOr(json.optString("sex"), Sex.UNSPECIFIED),
                age = if (json.isNull("age")) null else json.optInt("age"),
                weightKg = json.doubleOrNull("weightKg"),
                useLbs = json.optBoolean("useLbs", true),
            ),
            customGoals = Nutrient.entries
                .mapNotNull { n -> goals?.doubleOrNull(n.name)?.let { n to it } }
                .toMap(),
            hiddenNutrients = json.optJSONArray("hiddenNutrients").strings()
                .mapNotNull { name -> Nutrient.entries.firstOrNull { it.name == name } }
                .toSet(),
            enabledLimits = json.optJSONArray("enabledLimits").strings()
                .mapNotNull { name -> LimitNutrient.entries.firstOrNull { it.name == name } }
                .toSet(),
            customLimits = LimitNutrient.entries
                .mapNotNull { n -> limits?.doubleOrNull(n.name)?.let { n to it } }
                .toMap(),
            lastBackup = if (json.isNull("lastBackup")) null else json.optString("lastBackup").ifEmpty { null },
        )
    }
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).ifEmpty { null } }

private fun JSONObject.doubleOrNull(key: String): Double? =
    if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
