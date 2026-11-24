package com.example.greetingcard.data

import java.util.UUID

enum class Meal(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snacks"),
    ;

    companion object {
        /**
         * The meal people are most likely logging at [hour] (0–23): breakfast from 5 (when the greeting
         * says "Good morning") until 10, lunch until 3, dinner until 8, snacks late at night.
         */
        fun forHour(hour: Int): Meal = when (hour) {
            in 5..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 16..20 -> DINNER
            else -> SNACK
        }
    }
}

/** How calories are treated across the app. */
enum class CalorieMode(val label: String, val description: String) {
    SHOW("Show calories", "Track and display calories like any other number."),
    HIDE("Hide calories", "Keep calorie data in the background, but never show it."),
    OFF("Ignore calories", "Don't record calories at all, not even from barcodes."),
}

enum class Sex(val label: String) { FEMALE("Female"), MALE("Male"), UNSPECIFIED("Not set") }

/** The nutrients the app sets goals for. Calories are deliberately not one of them. */
enum class Nutrient(val label: String, val unit: String) {
    PROTEIN("Protein", "g"),
    FIBER("Fiber", "g"),
    FRUIT("Fruit", "servings"),
    VEG("Vegetables", "servings"),
}

/** Things to stay under each day, unlike goals, which are things to reach. Off until the user turns them on. */
enum class LimitNutrient(val label: String, val unit: String) {
    SODIUM("Sodium", "mg"),
    ADDED_SUGARS("Added sugars", "g"),
}

data class Nutrients(
    val protein: Double = 0.0,
    val fiber: Double = 0.0,
    val fruit: Double = 0.0,
    val veg: Double = 0.0,
    val calories: Double? = null,
    /** Milligrams. Null means unknown (no data), which is different from 0. */
    val sodium: Double? = null,
    /** Grams of added sugars, not total sugars, so fruit and plain milk don't count. Null means unknown. */
    val addedSugars: Double? = null,
) {
    operator fun plus(other: Nutrients) = Nutrients(
        protein = protein + other.protein,
        fiber = fiber + other.fiber,
        fruit = fruit + other.fruit,
        veg = veg + other.veg,
        calories = sumKnown(calories, other.calories),
        sodium = sumKnown(sodium, other.sodium),
        addedSugars = sumKnown(addedSugars, other.addedSugars),
    )

    operator fun times(factor: Double) = Nutrients(
        protein = protein * factor,
        fiber = fiber * factor,
        fruit = fruit * factor,
        veg = veg * factor,
        calories = calories?.times(factor),
        sodium = sodium?.times(factor),
        addedSugars = addedSugars?.times(factor),
    )

    operator fun get(limit: LimitNutrient): Double? = when (limit) {
        LimitNutrient.SODIUM -> sodium
        LimitNutrient.ADDED_SUGARS -> addedSugars
    }

    operator fun get(nutrient: Nutrient): Double = when (nutrient) {
        Nutrient.PROTEIN -> protein
        Nutrient.FIBER -> fiber
        Nutrient.FRUIT -> fruit
        Nutrient.VEG -> veg
    }

    companion object {
        val ZERO = Nutrients()
    }
}

fun Iterable<Nutrients>.total(): Nutrients = fold(Nutrients.ZERO, Nutrients::plus)

/** Adds what's known: unknown only if both are unknown. Callers that care count the unknowns separately. */
private fun sumKnown(a: Double?, b: Double?): Double? = if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)

data class FoodEntry(
    val id: String = newId(),
    /** ISO-8601 date, e.g. 2026-10-11. */
    val date: String,
    val meal: Meal,
    val name: String,
    val servingLabel: String = "",
    val servings: Double = 1.0,
    val perServing: Nutrients,
) {
    val total: Nutrients get() = perServing * servings
}

/** A food or a whole meal the user saved for quick add. */
data class SavedFood(
    val id: String = newId(),
    val name: String,
    val servingLabel: String = "",
    val perServing: Nutrients,
    val isMeal: Boolean = false,
    val barcode: String? = null,
    val useCount: Int = 0,
    val lastUsed: Long = 0,
)

data class Profile(
    val sex: Sex = Sex.UNSPECIFIED,
    val age: Int? = null,
    val weightKg: Double? = null,
    val useLbs: Boolean = true,
)

data class Settings(
    /** What the user wants to be called. Blank until they finish the welcome screen. */
    val name: String = "",
    val calorieMode: CalorieMode = CalorieMode.SHOW,
    val profile: Profile = Profile(),
    /** Goals the user typed in themselves, replacing the guideline value. */
    val customGoals: Map<Nutrient, Double> = emptyMap(),
    /** Limits the user turned on. Stored as what's on, so older files and any limit added later start off. */
    val enabledLimits: Set<LimitNutrient> = emptySet(),
    /** Limits the user typed in themselves, replacing the guideline value. */
    val customLimits: Map<LimitNutrient, Double> = emptyMap(),
    /**
     * Goals the user turned off. Like hidden calories, the numbers are still recorded, just never shown.
     * Stored as what's hidden rather than what's tracked, so older files and new nutrients start out tracked.
     */
    val hiddenNutrients: Set<Nutrient> = emptySet(),
    /** ISO date of the last backup the user saved, for the reminder in the Goals tab. Null if never. */
    val lastBackup: String? = null,
) {
    val trackedNutrients: List<Nutrient> get() = Nutrient.entries.filter { it !in hiddenNutrients }
    val trackedLimits: List<LimitNutrient> get() = LimitNutrient.entries.filter { it in enabledLimits }
}

data class AppData(
    val entries: List<FoodEntry> = emptyList(),
    val saved: List<SavedFood> = emptyList(),
    val settings: Settings = Settings(),
    /** One free-text note per day, keyed by ISO-8601 date. Days without a note have no key. */
    val notes: Map<String, String> = emptyMap(),
)

fun newId(): String = UUID.randomUUID().toString()
