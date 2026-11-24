package com.example.greetingcard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class HistoryTest {
    private val goals = listOf(
        Goal(Nutrient.PROTEIN, 50.0, "", isCustom = false),
        Goal(Nutrient.FIBER, 28.0, "", isCustom = false),
    )

    private fun entry(date: String, protein: Double = 0.0, fiber: Double = 0.0, servings: Double = 1.0) = FoodEntry(
        date = date,
        meal = Meal.LUNCH,
        name = "Food",
        servings = servings,
        perServing = Nutrients(protein = protein, fiber = fiber),
    )

    private fun month(month: YearMonth, entries: List<FoodEntry>, goals: List<Goal>) =
        History.days(month.atDay(1), month.atEndOfMonth(), History.totalsByDate(entries))
            .map { History.summarize(it, goals) }

    @Test
    fun daysAddUpEveryEntryAndCountGoalsMet() {
        val entries = listOf(
            entry("2026-10-01", protein = 30.0),
            entry("2026-10-01", protein = 10.0, servings = 2.0), // 30 + 20 = 50: protein met
            entry("2026-10-02", protein = 60.0, fiber = 30.0),
            entry("2026-09-30", protein = 100.0), // Other month: ignored.
        )
        val days = month(YearMonth.of(2026, 10), entries, goals)
        assertEquals(31, days.size)
        assertEquals(DaySummary(LocalDate.of(2026, 10, 1), logged = true, met = 1, total = 2), days[0])
        assertTrue(days[1].allMet)
        assertEquals(DaySummary(LocalDate.of(2026, 10, 3), logged = false, met = 0, total = 2), days[2])
        assertFalse(days[2].allMet)
    }

    @Test
    fun aDayWithFoodButNoGoalsReachedIsLoggedNotEmpty() {
        val day = month(YearMonth.of(2026, 10), listOf(entry("2026-10-05", protein = 5.0)), goals)[4]
        assertTrue(day.logged)
        assertEquals(0, day.met)
    }

    @Test
    fun statsCountOnlyLoggedDays() {
        val entries = listOf(
            entry("2026-10-12", protein = 60.0),
            entry("2026-10-13", protein = 20.0),
            entry("2026-10-15", protein = 55.0),
        )
        val days = History.days(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 17), History.totalsByDate(entries))
        assertFalse(days[3].logged) // Oct 14: a day off.
        assertEquals(7, days.size)
        val protein = History.stats(goals[0], days)
        assertEquals(2, protein.metDays)
        assertEquals(3, protein.loggedDays)
        assertEquals(45.0, protein.average!!, 1e-9) // (60 + 20 + 55) / 3, days off left out
        assertNull(History.stats(goals[0], days.filter { !it.logged }).average)
    }

    @Test
    fun streakCountsDaysInARowAndWaitsForToday() {
        val today = LocalDate.of(2026, 10, 18)
        val protein = listOf(goals[0])
        val entries = listOf(
            entry("2026-10-14", protein = 50.0),
            entry("2026-10-15", protein = 10.0), // Breaks the streak.
            entry("2026-10-16", protein = 50.0),
            entry("2026-10-17", protein = 70.0),
            entry("2026-10-18", protein = 10.0), // Today, not met yet: streak runs to yesterday.
        )
        val totals = History.totalsByDate(entries)
        assertEquals(2, History.streak(protein, totals, today))
        val todayMet = History.totalsByDate(entries + entry("2026-10-18", protein = 40.0))
        assertEquals(3, History.streak(protein, todayMet, today))
        // Every goal has to be met, and a day off ends the streak.
        assertEquals(0, History.streak(goals, totals, today))
        assertEquals(0, History.streak(protein, totals, LocalDate.of(2026, 10, 20)))
        assertEquals(0, History.streak(emptyList(), totals, today))
    }

    @Test
    fun limitStatsSkipDaysWithoutDataForThatLimit() {
        val sodium = Limit(LimitNutrient.SODIUM, 2300.0, "", isCustom = false)
        val entries = listOf(
            entry("2026-10-12").copy(perServing = Nutrients(sodium = 1800.0)),
            entry("2026-10-13").copy(perServing = Nutrients(sodium = 2600.0)),
            entry("2026-10-14", protein = 20.0), // Logged, but no sodium data: says nothing about sodium.
        )
        val totals = History.totalsByDate(entries)
        val days = History.days(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 17), totals)
        val stats = History.limitStats(sodium, days)
        assertEquals(1, stats.underDays)
        assertEquals(2, stats.knownDays)
        assertEquals(2200.0, stats.average!!, 1e-9)

        val summaries = days.map { History.summarize(it, goals, listOf(sodium)) }
        assertEquals(listOf(LimitNutrient.SODIUM), summaries[2].overLimits) // Oct 13
        assertTrue(summaries[1].overLimits.isEmpty())
        assertTrue(summaries[3].overLimits.isEmpty()) // Unknown is never "over".
    }

    @Test
    fun limitStreakEndsOnADayOverOrWithoutData() {
        val sugar = Limit(LimitNutrient.ADDED_SUGARS, 50.0, "", isCustom = false)
        fun day(date: String, grams: Double?) = entry(date).copy(perServing = Nutrients(addedSugars = grams))
        val totals = History.totalsByDate(
            listOf(day("2026-10-14", 80.0), day("2026-10-15", 20.0), day("2026-10-16", 50.0), day("2026-10-17", 10.0)),
        )
        val today = LocalDate.of(2026, 10, 18) // Nothing logged yet today: the streak runs to yesterday.
        assertEquals(3, History.limitStreak(sugar, totals, today))
        val withUnknownDay = totals + ("2026-10-16" to Nutrients(protein = 5.0))
        assertEquals(1, History.limitStreak(sugar, withUnknownDay, today))
    }

    @Test
    fun rangeCoversTheWeekOrMonthAroundTheAnchor() {
        val sunday = DayOfWeek.SUNDAY
        val anchor = LocalDate.of(2026, 10, 1) // A Thursday.
        assertEquals(
            LocalDate.of(2026, 9, 27)..LocalDate.of(2026, 10, 3),
            History.range(HistoryPeriod.WEEK, anchor, sunday),
        )
        assertEquals(
            LocalDate.of(2026, 10, 1)..LocalDate.of(2026, 10, 31),
            History.range(HistoryPeriod.MONTH, anchor, sunday),
        )
        // The anchor itself can be the first day.
        val sundayAnchor = LocalDate.of(2026, 10, 18)
        assertEquals(sundayAnchor, History.range(HistoryPeriod.WEEK, sundayAnchor, sunday).start)
    }

    @Test
    fun weeksStartOnTheGivenDayAndPadWithBlanks() {
        // October 2026 starts on a Thursday.
        val sundayFirst = History.weeks(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(5, sundayFirst.size)
        assertTrue(sundayFirst.all { it.size == 7 })
        assertEquals(listOf(null, null, null, null), sundayFirst[0].take(4))
        assertEquals(LocalDate.of(2026, 10, 1), sundayFirst[0][4])
        assertEquals(LocalDate.of(2026, 10, 31), sundayFirst[4][6])

        val mondayFirst = History.weeks(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 1), mondayFirst[0][3])
        assertNull(mondayFirst[4][6])
    }

    @Test
    fun monthStartingOnTheFirstDayOfWeekHasNoLeadingBlanks() {
        // February 2026 starts on a Sunday and has exactly 4 weeks.
        val weeks = History.weeks(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)
        assertEquals(4, weeks.size)
        assertEquals(LocalDate.of(2026, 2, 1), weeks[0][0])
    }
}
