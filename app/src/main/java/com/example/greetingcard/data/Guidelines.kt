package com.example.greetingcard.data

import kotlin.math.roundToInt

data class Goal(
    val nutrient: Nutrient,
    val value: Double,
    /** Where the number comes from, shown to the user. */
    val basis: String,
    val isCustom: Boolean,
) {
    /** A goal of 0 can't be met, so it never shows as done. */
    fun isMetBy(amount: Double): Boolean = value > 0 && amount >= value
}

/** A daily amount to stay under, such as sodium. The opposite of a [Goal]. */
data class Limit(
    val nutrient: LimitNutrient,
    val value: Double,
    /** Where the number comes from, shown to the user. */
    val basis: String,
    val isCustom: Boolean,
) {
    /** Exactly at the limit still counts as within it. */
    fun isRespectedBy(amount: Double): Boolean = amount <= value
}

/** A document a goal comes from, linked in the Goals tab. */
data class Source(val name: String, val url: String)

/**
 * Daily goals from US government guidance:
 * - Protein and fruit/vegetable servings: Dietary Guidelines for Americans 2025–2030.
 * - Fiber: National Academies Dietary Reference Intakes (Adequate Intake), which the
 *   Dietary Guidelines defer to for fiber.
 * - Serving sizes: USDA MyPlate cup-equivalents (the Dietary Guidelines say "servings" without defining them).
 */
object Guidelines {
    /** Keep in sync with "Where the goals come from" in DEVELOPER.md. */
    val SOURCES = listOf(
        Source("Dietary Guidelines for Americans 2025–2030 (USDA & HHS)", "https://cdn.realfood.gov/DGA.pdf"),
        Source(
            "National Academies Dietary Reference Intakes (fiber)",
            "https://nap.nationalacademies.org/read/11537/chapter/11",
        ),
        Source(
            "FDA Daily Values",
            "https://www.fda.gov/food/nutrition-facts-label/daily-value-nutrition-and-supplement-facts-labels",
        ),
        Source("MyPlate fruit servings", "https://www.myplate.gov/eat-healthy/fruits"),
        Source("MyPlate vegetable servings", "https://www.myplate.gov/eat-healthy/vegetables"),
    )
    const val OPEN_FOOD_FACTS_URL = "https://world.openfoodfacts.org/"

    const val PROTEIN_G_PER_KG = 1.2
    const val FRUIT_SERVINGS = 2.0
    const val VEG_SERVINGS = 3.0

    const val SERVING_HELP =
        "One serving is roughly 1 cup of fruit or vegetables, 2 cups of leafy greens, " +
            "or ½ cup of dried fruit."

    fun goals(settings: Settings): List<Goal> = Nutrient.entries.map { nutrient ->
        val custom = settings.customGoals[nutrient]
        if (custom != null) {
            Goal(nutrient, custom, "Your own goal", isCustom = true)
        } else {
            val (value, basis) = guideline(nutrient, settings.profile)
            Goal(nutrient, value, basis, isCustom = false)
        }
    }

    fun limits(settings: Settings): List<Limit> = LimitNutrient.entries.map { nutrient ->
        val custom = settings.customLimits[nutrient]
        if (custom != null) {
            Limit(nutrient, custom, "Your own limit", isCustom = true)
        } else {
            val (value, basis) = limitGuideline(nutrient, settings.profile)
            Limit(nutrient, value, basis, isCustom = false)
        }
    }

    fun limitGuideline(nutrient: LimitNutrient, profile: Profile): Pair<Double, String> = when (nutrient) {
        LimitNutrient.SODIUM -> sodium(profile)

        LimitNutrient.ADDED_SUGARS ->
            50.0 to
                "The FDA Daily Value used on nutrition labels. The 2025–2030 Dietary Guidelines recommend " +
                "as little added sugar as possible, with no daily amount."
    }

    /** Dietary Guidelines 2025–2030: less than 2,300 mg from age 14, less for younger children. */
    fun sodium(profile: Profile): Pair<Double, String> {
        val age = profile.age
        val (value, who) = when {
            age == null || age >= 14 -> 2300.0 to "ages 14 and up"
            age >= 9 -> 1800.0 to "ages 9–13"
            age >= 4 -> 1500.0 to "ages 4–8"
            else -> 1200.0 to "ages 1–3"
        }
        return value to "Dietary Guidelines 2025–2030: less than ${"%,d".format(value.toInt())} mg a day for $who."
    }

    fun guideline(nutrient: Nutrient, profile: Profile): Pair<Double, String> = when (nutrient) {
        Nutrient.PROTEIN -> protein(profile)

        Nutrient.FIBER -> fiber(profile)

        Nutrient.FRUIT ->
            FRUIT_SERVINGS to
                "Dietary Guidelines 2025–2030: about 2 servings of fruit a day on a 2,000-calorie pattern."

        Nutrient.VEG ->
            VEG_SERVINGS to
                "Dietary Guidelines 2025–2030: about 3 servings of vegetables a day on a 2,000-calorie pattern."
    }

    fun protein(profile: Profile): Pair<Double, String> {
        val weight = profile.weightKg
        return when {
            weight != null && weight > 0 ->
                (weight * PROTEIN_G_PER_KG).roundToInt().toDouble() to
                    "Dietary Guidelines 2025–2030 recommend 1.2–1.6 g per kg of body weight. " +
                    "This uses the low end, 1.2 g/kg."

            profile.sex == Sex.FEMALE ->
                46.0 to
                    "RDA for adult women (0.8 g/kg). Add your weight under About you for the " +
                    "higher 2025–2030 Dietary Guidelines target."

            profile.sex == Sex.MALE ->
                56.0 to
                    "RDA for adult men (0.8 g/kg). Add your weight under About you for the " +
                    "higher 2025–2030 Dietary Guidelines target."

            else ->
                50.0 to
                    "The FDA Daily Value used on nutrition labels. Add your weight under About you " +
                    "for a personal target."
        }
    }

    fun fiber(profile: Profile): Pair<Double, String> {
        val age = profile.age
        val group = when {
            age == null -> "adults 19–50"
            age < 14 -> "ages 9–13"
            age < 19 -> "ages 14–18"
            age < 51 -> "ages 19–50"
            else -> "ages 51+"
        }
        val value = when (profile.sex) {
            Sex.FEMALE -> when {
                age == null -> 25.0
                age < 19 -> 26.0
                age < 51 -> 25.0
                else -> 21.0
            }

            Sex.MALE -> when {
                age == null -> 38.0
                age < 14 -> 31.0
                age < 51 -> 38.0
                else -> 30.0
            }

            Sex.UNSPECIFIED ->
                return 28.0 to
                    "The FDA Daily Value used on nutrition labels (14 g per 1,000 calories)."
        }
        val who = if (profile.sex == Sex.FEMALE) "women/girls" else "men/boys"
        return value to "Adequate Intake for $who, $group (National Academies, about 14 g per 1,000 calories)."
    }
}
