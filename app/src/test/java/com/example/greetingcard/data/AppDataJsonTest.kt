package com.example.greetingcard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataJsonTest {
    @Test
    fun everythingSurvivesARoundTrip() {
        val data = AppData(
            entries = listOf(
                FoodEntry(
                    date = "2026-10-11",
                    meal = Meal.LUNCH,
                    name = "Lentil soup",
                    servingLabel = "1 bowl",
                    servings = 1.5,
                    perServing = Nutrients(
                        protein = 12.0,
                        fiber = 8.0,
                        veg = 1.0,
                        calories = 230.0,
                        sodium = 870.0,
                        addedSugars = 0.0,
                    ),
                ),
            ),
            saved = listOf(
                SavedFood(name = "Apple", perServing = Nutrients(fiber = 4.0, fruit = 1.0), barcode = "123"),
            ),
            settings = Settings(
                name = "Sam",
                calorieMode = CalorieMode.HIDE,
                profile = Profile(sex = Sex.FEMALE, age = 30, weightKg = 60.0, useLbs = false),
                customGoals = mapOf(Nutrient.FIBER to 35.0),
                hiddenNutrients = setOf(Nutrient.FRUIT, Nutrient.VEG),
                enabledLimits = setOf(LimitNutrient.SODIUM),
                customLimits = mapOf(LimitNutrient.ADDED_SUGARS to 30.0),
                lastBackup = "2026-10-19",
            ),
            notes = mapOf("2026-10-11" to "Walked 5k\nSlept well", "2026-10-10" to "Rest day \"quoted\""),
        )
        assertEquals(data, AppDataJson.decode(AppDataJson.encode(data)))
    }

    @Test
    fun filesFromBeforeNamesAndNotesStillLoad() {
        val old = """{"version":1,"entries":[],"saved":[],"settings":{"calorieMode":"OFF","sex":"MALE"}}"""
        val data = AppDataJson.decode(old)
        assertEquals("", data.settings.name)
        assertTrue(data.notes.isEmpty())
        assertEquals(CalorieMode.OFF, data.settings.calorieMode)
        assertEquals(Sex.MALE, data.settings.profile.sex)
        // From before backups too: "No backup yet".
        assertNull(data.settings.lastBackup)
    }

    @Test
    fun filesFromBeforeGoalTogglesTrackEverything() {
        val old = """{"version":1,"entries":[],"saved":[],"settings":{"name":"Sam","customGoals":{"FIBER":30}}}"""
        val settings = AppDataJson.decode(old).settings
        assertTrue(settings.hiddenNutrients.isEmpty())
        assertEquals(Nutrient.entries, settings.trackedNutrients)
    }

    @Test
    fun filesFromBeforeLimitsLoadWithLimitsOffAndUnknownAmounts() {
        val old = """
            {"version":1,
             "entries":[{"id":"a","date":"2026-10-11","meal":"LUNCH","name":"Soup","servings":1,
                         "perServing":{"protein":8,"fiber":2,"fruit":0,"veg":1}}],
             "saved":[],"settings":{"name":"Sam","hiddenNutrients":[]}}
        """
        val data = AppDataJson.decode(old)
        assertTrue(data.settings.enabledLimits.isEmpty())
        assertTrue(data.settings.customLimits.isEmpty())
        // Unknown, not zero: old foods never recorded sodium.
        assertNull(data.entries.single().perServing.sodium)
        assertNull(data.entries.single().perServing.addedSugars)
    }

    @Test
    fun unknownHiddenNutrientsAreIgnored() {
        val json = """{"settings":{"hiddenNutrients":["FIBER","SODIUM"]}}"""
        assertEquals(setOf(Nutrient.FIBER), AppDataJson.decode(json).settings.hiddenNutrients)
    }
}
