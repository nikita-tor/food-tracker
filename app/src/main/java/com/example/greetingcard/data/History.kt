package com.example.greetingcard.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** What was eaten on one day. A day with no entries is not logged, which is different from logging nothing useful. */
data class Day(val date: LocalDate, val logged: Boolean, val totals: Nutrients)

/** How one day went: whether anything was logged, how many goals were met, and which limits were gone over. */
data class DaySummary(
    val date: LocalDate,
    val logged: Boolean,
    val met: Int,
    val total: Int,
    val overLimits: List<LimitNutrient> = emptyList(),
) {
    /** About goals only: limits are shown separately. */
    val allMet: Boolean get() = logged && total > 0 && met == total
}

/** One goal over a period: on how many logged days it was met, and the average on logged days. */
data class GoalStats(val goal: Goal, val metDays: Int, val loggedDays: Int, val average: Double?)

/**
 * One limit over a period. Only days whose total for it is known count: a day where no food had
 * sodium data says nothing about sodium.
 */
data class LimitStats(val limit: Limit, val underDays: Int, val knownDays: Int, val average: Double?)

enum class HistoryPeriod(val label: String) { WEEK("Week"), MONTH("Month") }

/**
 * Per-day results, stats and streaks for the History tab. Past days are graded against the goals
 * as they are now (current weight, overrides and turned-off goals), so changing a goal re-grades
 * old days too.
 */
object History {
    fun isMet(goal: Goal, totals: Nutrients): Boolean = goal.isMetBy(totals[goal.nutrient])

    fun goalsMet(goals: List<Goal>, totals: Nutrients): Int = goals.count { isMet(it, totals) }

    /** What was eaten each day, keyed by ISO date. Only days with entries have a key. */
    fun totalsByDate(entries: List<FoodEntry>): Map<String, Nutrients> =
        entries.groupBy { it.date }.mapValues { (_, day) -> day.map { it.total }.total() }

    /** Every day from [from] to [to], inclusive, with what was logged. */
    fun days(from: LocalDate, to: LocalDate, totalsByDate: Map<String, Nutrients>): List<Day> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.map { date ->
            val totals = totalsByDate[date.toString()]
            Day(date, logged = totals != null, totals = totals ?: Nutrients.ZERO)
        }.toList()

    fun summarize(day: Day, goals: List<Goal>, limits: List<Limit> = emptyList()) = DaySummary(
        date = day.date,
        logged = day.logged,
        met = if (day.logged) goalsMet(goals, day.totals) else 0,
        total = goals.size,
        overLimits = if (day.logged) limits.filter { isOver(it, day.totals) }.map { it.nutrient } else emptyList(),
    )

    /** Only a known total can be over: unknown is not counted as over or under. */
    fun isOver(limit: Limit, totals: Nutrients): Boolean =
        totals[limit.nutrient]?.let { !limit.isRespectedBy(it) } ?: false

    fun stats(goal: Goal, days: List<Day>): GoalStats {
        val logged = days.filter { it.logged }
        return GoalStats(
            goal = goal,
            metDays = logged.count { isMet(goal, it.totals) },
            loggedDays = logged.size,
            average = if (logged.isEmpty()) null else logged.sumOf { it.totals[goal.nutrient] } / logged.size,
        )
    }

    fun limitStats(limit: Limit, days: List<Day>): LimitStats {
        val amounts = days.filter { it.logged }.mapNotNull { it.totals[limit.nutrient] }
        return LimitStats(
            limit = limit,
            underDays = amounts.count(limit::isRespectedBy),
            knownDays = amounts.size,
            average = if (amounts.isEmpty()) null else amounts.average(),
        )
    }

    /** Days in a row, up to [today], spent within [limit]. A day with no data for it ends the streak. */
    fun limitStreak(limit: Limit, totalsByDate: Map<String, Nutrients>, today: LocalDate): Int =
        streakOf(totalsByDate, today) { t -> t[limit.nutrient]?.let(limit::isRespectedBy) ?: false }

    /**
     * Days in a row, up to [today], on which [goals] were all met. Today only counts once it's met:
     * until then the streak runs to yesterday, so it doesn't reset every morning.
     */
    fun streak(goals: List<Goal>, totalsByDate: Map<String, Nutrients>, today: LocalDate): Int =
        if (goals.isEmpty()) 0 else streakOf(totalsByDate, today) { t -> goals.all { isMet(it, t) } }

    private fun streakOf(totalsByDate: Map<String, Nutrients>, today: LocalDate, good: (Nutrients) -> Boolean): Int {
        fun isGoodDay(date: LocalDate) = totalsByDate[date.toString()]?.let(good) ?: false
        var day = if (isGoodDay(today)) today else today.minusDays(1)
        var count = 0
        while (isGoodDay(day)) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** The first and last day of the week or month that contains [anchor]. */
    fun range(period: HistoryPeriod, anchor: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> =
        when (period) {
            HistoryPeriod.WEEK -> {
                val start = anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                start..start.plusDays(6)
            }

            HistoryPeriod.MONTH -> YearMonth.from(anchor).let { it.atDay(1)..it.atEndOfMonth() }
        }

    /**
     * The month laid out as calendar rows of 7, starting on [firstDayOfWeek].
     * Cells before the 1st and after the last day are null.
     */
    fun weeks(month: YearMonth, firstDayOfWeek: DayOfWeek): List<List<LocalDate?>> {
        val leading = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val cells = List<LocalDate?>(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
        return cells.chunked(7).map { week -> week + List(7 - week.size) { null } }
    }
}
