package com.example.greetingcard.ui

import com.example.greetingcard.data.AppData
import com.example.greetingcard.data.FoodEntry
import com.example.greetingcard.data.Goal
import com.example.greetingcard.data.LimitNutrient
import com.example.greetingcard.data.Meal
import com.example.greetingcard.data.Nutrient
import com.example.greetingcard.data.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FormatTest {
    @Test
    fun greetingDependsOnTimeOfDay() {
        assertEquals("Good morning, Sam", greeting(hour = 8, name = "Sam"))
        assertEquals("Good afternoon, Sam", greeting(hour = 12, name = "Sam"))
        assertEquals("Good evening, Sam", greeting(hour = 21, name = "Sam"))
        assertEquals("Good evening, Sam", greeting(hour = 2, name = "Sam"))
        assertEquals("Good morning", greeting(hour = 8, name = ""))
    }

    @Test
    fun servingsTextUsesLabelWhenThereIsOne() {
        assertEquals("1 serving", servingsText(1.0, ""))
        assertEquals("2.5 servings", servingsText(2.5, ""))
        assertEquals("2 × 1 cup", servingsText(2.0, "1 cup"))
    }

    @Test
    fun nutrientLineListsOnlyWhatTheFoodHas() {
        val yogurt = Nutrients(protein = 17.5, fiber = 2.0, calories = 150.0)
        val all = Nutrient.entries
        val none = emptyList<LimitNutrient>()
        assertEquals("17.5 g protein · 2 g fiber · 150 cal", nutrientLine(yogurt, showCalories = true, all, none))
        assertEquals("17.5 g protein · 2 g fiber", nutrientLine(yogurt, showCalories = false, all, none))
        assertEquals("No tracked nutrients", nutrientLine(Nutrients(), showCalories = false, all, none))
        val tiny = Nutrients(protein = 0.04, fiber = 0.1)
        assertEquals("0.1 g fiber", nutrientLine(tiny, showCalories = false, all, none))
    }

    @Test
    fun nutrientLineLeavesOutGoalsTheUserTurnedOff() {
        val yogurt = Nutrients(protein = 17.5, fiber = 2.0)
        val none = emptyList<LimitNutrient>()
        assertEquals("17.5 g protein", nutrientLine(yogurt, showCalories = false, listOf(Nutrient.PROTEIN), none))
        assertEquals("No tracked nutrients", nutrientLine(yogurt, showCalories = false, listOf(Nutrient.FRUIT), none))
    }

    @Test
    fun nutrientLineShowsKnownLimitsThatAreOn() {
        val soup = Nutrients(protein = 8.0, sodium = 1480.0, addedSugars = 2.0)
        val protein = listOf(Nutrient.PROTEIN)
        assertEquals(
            "8 g protein · 1,480 mg sodium · 2 g added sugars",
            nutrientLine(soup, showCalories = false, protein, LimitNutrient.entries),
        )
        assertEquals("8 g protein", nutrientLine(soup, showCalories = false, protein, emptyList()))
        // Unknown sodium shows nothing, rather than "0 mg".
        assertEquals(
            "8 g protein",
            nutrientLine(Nutrients(protein = 8.0), showCalories = false, protein, listOf(LimitNutrient.SODIUM)),
        )
    }

    @Test
    fun limitTextSaysWhatIsLeftOrHowFarOver() {
        assertEquals("2,300 mg", amountText(2300.0, "mg"))
        assertEquals("12.5 g", amountText(12.5, "g"))
        assertEquals("1,240 of 2,300 mg", limitProgress(1240.0, 2300.0, "mg"))
        assertEquals("12.5 of 50 g", limitProgress(12.5, 50.0, "g"))
        assertEquals("1,060 mg left", limitStatus(1240.0, 2300.0, "mg"))
        assertEquals("Close to the limit · 100 mg left", limitStatus(2200.0, 2300.0, "mg"))
        assertEquals("0 g left", limitStatus(50.0, 50.0, "g").substringAfter("· "))
        assertEquals("Over by 210 mg", limitStatus(2510.0, 2300.0, "mg"))
    }

    @Test
    fun unknownNoteCountsFoodsWithoutData() {
        assertNull(unknownNote(0, "Sodium"))
        assertEquals("1 food has no sodium info, so the total may be low.", unknownNote(1, "Sodium"))
        assertEquals("3 foods have no added sugars info, so the total may be low.", unknownNote(3, "Added sugars"))
    }

    @Test
    fun limitStatsLineSummarizesALimit() {
        assertEquals(
            "Under on 9 of 12 days · 1,840 mg a day on average · 4-day streak",
            limitStatsLine(underDays = 9, knownDays = 12, average = 1840.0, unit = "mg", streak = 4),
        )
        assertEquals("No data yet.", limitStatsLine(0, 0, null, "g", 0))
    }

    @Test
    fun goalSummaryEncouragesWithoutNagging() {
        assertEquals("Log a meal to start filling your rings.", goalSummary(met = 0, total = 4, anythingLogged = false))
        assertEquals("0 of 4 goals met", goalSummary(met = 0, total = 4, anythingLogged = true))
        assertEquals("2 of 4 goals met", goalSummary(met = 2, total = 4, anythingLogged = true))
        assertEquals("All 4 goals met. Nice work!", goalSummary(met = 4, total = 4, anythingLogged = true))
    }

    @Test
    fun periodSummaryCountsLoggedDaysAndPerfectDays() {
        assertEquals("Nothing logged this month.", periodSummary(logged = 0, allMet = 0, "month"))
        assertEquals("Nothing logged this week.", periodSummary(logged = 0, allMet = 0, "week"))
        assertEquals("1 day logged.", periodSummary(logged = 1, allMet = 0, "month"))
        assertEquals("12 days logged, every goal met on 1 day.", periodSummary(logged = 12, allMet = 1, "month"))
        assertEquals("12 days logged, every goal met on 5 days.", periodSummary(logged = 12, allMet = 5, "month"))
    }

    @Test
    fun streakTextStartsAtTwoDays() {
        assertNull(streakText(0))
        assertNull(streakText(1))
        assertEquals("Every goal met 3 days in a row so far.", streakText(3))
    }

    @Test
    fun goalStatsLineSummarizesAGoal() {
        assertEquals(
            "Met on 9 of 14 days · 42 g a day on average · 3-day streak",
            goalStatsLine(metDays = 9, loggedDays = 14, average = 42.04, Nutrient.PROTEIN, streak = 3),
        )
        assertEquals(
            "Met on 0 of 1 day · 1.5 servings a day on average",
            goalStatsLine(metDays = 0, loggedDays = 1, average = 1.5, Nutrient.VEG, streak = 0),
        )
        assertEquals("Nothing logged.", goalStatsLine(0, 0, null, Nutrient.FIBER, 0))
    }

    @Test
    fun weekLabelShortensWhereItCan() {
        val d = { m: Int, day: Int, y: Int -> LocalDate.of(y, m, day) }
        assertEquals("Oct 12 – 18", weekLabel(d(10, 12, 2026), d(10, 18, 2026), thisYear = 2026))
        assertEquals("Sep 28 – Oct 4", weekLabel(d(9, 28, 2026), d(10, 4, 2026), thisYear = 2026))
        assertEquals("Dec 28 – Jan 3, 2027", weekLabel(d(12, 28, 2026), d(1, 3, 2027), thisYear = 2026))
    }

    @Test
    fun dayDescriptionSaysHowTheDayWent() {
        assertEquals("October 5: nothing logged", dayDescription("October 5", logged = false, met = 0, total = 4))
        assertEquals("October 5: 0 of 4 goals met", dayDescription("October 5", logged = true, met = 0, total = 4))
        assertEquals("October 5: all goals met", dayDescription("October 5", logged = true, met = 4, total = 4))
        assertEquals(
            "October 5: 2 of 4 goals met, over the sodium limit",
            dayDescription("October 5", logged = true, met = 2, total = 4, overLimits = listOf("Sodium")),
        )
        assertEquals(
            "October 5: all goals met, over the sodium and added sugars limits",
            dayDescription("October 5", true, 4, 4, overLimits = listOf("Sodium", "Added sugars")),
        )
    }

    @Test
    fun goalSummaryReadsNaturallyWithOneGoal() {
        assertEquals("Log a meal to start filling your ring.", goalSummary(met = 0, total = 1, anythingLogged = false))
        assertEquals("Goal not met yet", goalSummary(met = 0, total = 1, anythingLogged = true))
        assertEquals("Goal met. Nice work!", goalSummary(met = 1, total = 1, anythingLogged = true))
    }

    @Test
    fun toGoTextFitsUnderASmallRing() {
        val protein = Goal(Nutrient.PROTEIN, 50.0, basis = "", isCustom = false)
        val fruit = Goal(Nutrient.FRUIT, 2.0, basis = "", isCustom = false)
        assertEquals("38 g to go", toGoText(protein, 12.0))
        assertEquals("1.5 to go", toGoText(fruit, 0.5))
        assertEquals("Goal met", toGoText(protein, 50.0))
    }

    @Test
    fun dayLabelNamesNearbyDays() {
        val today = LocalDate.of(2026, 10, 19)
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today))
        assertEquals("Tomorrow", dayLabel(today.plusDays(1), today))
        assertEquals("Thu, Oct 15", dayLabel(LocalDate.of(2026, 10, 15), today))
    }

    @Test
    fun mealIsGuessedFromTheTimeOfDay() {
        assertEquals(Meal.BREAKFAST, Meal.forHour(7))
        assertEquals(Meal.LUNCH, Meal.forHour(12))
        assertEquals(Meal.DINNER, Meal.forHour(18))
        assertEquals(Meal.SNACK, Meal.forHour(22))
        assertEquals(Meal.SNACK, Meal.forHour(1))
        assertEquals(Meal.SNACK, Meal.forHour(4))
    }

    @Test
    fun backupTextsSayWhatAndWhen() {
        val today = LocalDate.of(2026, 10, 19)
        assertEquals("No backup yet.", lastBackupText(null, today))
        assertEquals("Last backup: today", lastBackupText("2026-10-19", today))
        assertEquals("Last backup: yesterday", lastBackupText("2026-10-18", today))
        assertEquals("Last backup: Oct 3 (16 days ago)", lastBackupText("2026-10-03", today))
        assertEquals("Oct 19, 2026", backupDate("2026-10-19T12:30:00"))
        assertNull(backupDate(null))

        val data = AppData(
            entries = listOf(
                FoodEntry(date = "2026-10-18", meal = Meal.LUNCH, name = "A", perServing = Nutrients.ZERO),
                FoodEntry(date = "2026-10-18", meal = Meal.DINNER, name = "B", perServing = Nutrients.ZERO),
                FoodEntry(date = "2026-10-19", meal = Meal.LUNCH, name = "C", perServing = Nutrients.ZERO),
            ),
            notes = mapOf("2026-10-19" to "Hi"),
        )
        assertEquals("2 days of food, 0 saved foods, 1 note", backupSummary(data))
    }
}
