package com.example.greetingcard.ui

import com.example.greetingcard.data.AppData
import com.example.greetingcard.data.Goal
import com.example.greetingcard.data.LimitNutrient
import com.example.greetingcard.data.Nutrient
import com.example.greetingcard.data.Nutrients
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** 12.0 -> "12", 12.25 -> "12.3". */
fun Double.fmt(): String {
    val rounded = (this * 10).roundToLong() / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

fun Double.fmtCalories(): String = "%,d".format(roundToLong())

/** Parses user input, accepting a comma as the decimal separator. */
fun String.toNumberOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

/** "12 g protein", "1.5 fruit". */
fun nutrientSummary(nutrient: Nutrient, value: Double): String = when (nutrient) {
    Nutrient.PROTEIN -> "${value.fmt()} g protein"
    Nutrient.FIBER -> "${value.fmt()} g fiber"
    Nutrient.FRUIT -> "${value.fmt()} fruit"
    Nutrient.VEG -> "${value.fmt()} veg"
}

/** "12 g protein · 4 g fiber · 480 mg sodium · 210 cal", listing only what the food has and the user tracks. */
fun nutrientLine(
    nutrients: Nutrients,
    showCalories: Boolean,
    tracked: Collection<Nutrient>,
    limits: Collection<LimitNutrient>,
): String {
    val parts = buildList {
        // Amounts that round to zero (a slice of banana's 0.04 g protein) would read as "0 g protein".
        tracked.forEach { n -> if (nutrients[n].fmt() != "0") add(nutrientSummary(n, nutrients[n])) }
        limits.forEach { l ->
            nutrients[l]?.takeIf { it.fmt() != "0" }?.let { add("${amountText(it, l.unit)} ${l.label.lowercase()}") }
        }
        if (showCalories) nutrients.calories?.let { add("${it.fmtCalories()} cal") }
    }
    return parts.joinToString(" · ").ifEmpty { "No tracked nutrients" }
}

/** "12 g", "1 serving", "2.5 servings". */
fun amountWithUnit(value: Double, nutrient: Nutrient): String {
    val unit = if (nutrient.unit == "servings" && value.fmt() == "1") "serving" else nutrient.unit
    return "${value.fmt()} $unit"
}

/**
 * What's left for a goal, short enough for a small ring: "38 g to go", or "1 to go" for servings (the ring
 * says "of 2" and its label says "Fruit"), or "Goal met".
 */
fun toGoText(goal: Goal, current: Double): String = when {
    goal.isMetBy(current) -> "Goal met"
    goal.nutrient.unit == "servings" -> "${(goal.value - current).fmt()} to go"
    else -> "${amountWithUnit(goal.value - current, goal.nutrient)} to go"
}

/** "Today", "Yesterday", "Tomorrow", or "Mon, Oct 12" for any other day. */
fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> day.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
}

/** "1 serving", "2 servings", or "2 × 1 cup" when the serving has a label. */
fun servingsText(servings: Double, label: String): String = when {
    label.isNotBlank() -> "${servings.fmt()} × $label"
    servings == 1.0 -> "1 serving"
    else -> "${servings.fmt()} servings"
}

/** "2,300 mg" with a thousands separator for milligrams, "12.5 g" otherwise. */
fun amountText(value: Double, unit: String): String =
    if (unit == "mg") "${value.fmtCalories()} mg" else "${value.fmt()} $unit"

/** "1,240 of 2,300 mg", for a limit on Today. */
fun limitProgress(amount: Double, limit: Double, unit: String): String =
    "${if (unit == "mg") amount.fmtCalories() else amount.fmt()} of ${amountText(limit, unit)}"

/** How much room is left under a limit, or how far over it the day went. */
fun limitStatus(amount: Double, limit: Double, unit: String): String = when {
    amount > limit -> "Over by ${amountText(amount - limit, unit)}"
    amount >= limit * CLOSE_TO_LIMIT -> "Close to the limit · ${amountText(limit - amount, unit)} left"
    else -> "${amountText(limit - amount, unit)} left"
}

/** From 90% of a limit, Today says so before it's too late. */
private const val CLOSE_TO_LIMIT = 0.9

/** "2 foods have no sodium info": the day's total for that limit may be too low. */
fun unknownNote(count: Int, label: String): String? = when (count) {
    0 -> null
    1 -> "1 food has no ${label.lowercase()} info, so the total may be low."
    else -> "$count foods have no ${label.lowercase()} info, so the total may be low."
}

/** "Under on 9 of 12 days · 1,840 mg a day on average · 4-day streak". Days with no data aren't counted. */
fun limitStatsLine(underDays: Int, knownDays: Int, average: Double?, unit: String, streak: Int): String {
    if (knownDays == 0 || average == null) return "No data yet."
    return listOfNotNull(
        "Under on $underDays of ${dayCount(knownDays)}",
        "${amountText(average, unit)} a day on average",
        if (streak > 0) "$streak-day streak" else null,
    ).joinToString(" · ")
}

/** The line under the week or month on the History tab. [period] is "week" or "month". */
fun periodSummary(logged: Int, allMet: Int, period: String): String = when {
    logged == 0 -> "Nothing logged this $period."
    allMet == 0 -> "${dayCount(logged)} logged."
    else -> "${dayCount(logged)} logged, every goal met on ${dayCount(allMet)}."
}

/** Shown from two days on: one day isn't much of a streak yet. */
fun streakText(streak: Int): String? = if (streak < 2) null else "Every goal met $streak days in a row so far."

/** "Met on 9 of 14 days · 42 g a day on average · 3-day streak". Days with nothing logged aren't counted. */
fun goalStatsLine(metDays: Int, loggedDays: Int, average: Double?, nutrient: Nutrient, streak: Int): String {
    if (loggedDays == 0 || average == null) return "Nothing logged."
    return listOfNotNull(
        "Met on $metDays of ${dayCount(loggedDays)}",
        "${amountWithUnit(average, nutrient)} a day on average",
        if (streak > 0) "$streak-day streak" else null,
    ).joinToString(" · ")
}

/** "Oct 12 – 18", "Sep 28 – Oct 4", with the year added when it isn't this year. */
fun weekLabel(start: LocalDate, end: LocalDate, thisYear: Int): String {
    val day = DateTimeFormatter.ofPattern("MMM d")
    val endText = if (end.month == start.month) end.dayOfMonth.toString() else end.format(day)
    val year = if (end.year != thisYear) ", ${end.year}" else ""
    return "${start.format(day)} – $endText$year"
}

/** What a screen reader says for a calendar day, e.g. "October 5: 3 of 4 goals met". */
fun dayDescription(
    date: String,
    logged: Boolean,
    met: Int,
    total: Int,
    overLimits: List<String> = emptyList(),
): String {
    val goals = when {
        !logged -> return "$date: nothing logged"
        met == total -> "$date: all goals met"
        else -> "$date: $met of $total goals met"
    }
    return when (overLimits.size) {
        0 -> goals
        1 -> "$goals, over the ${overLimits[0].lowercase()} limit"
        else -> "$goals, over the ${overLimits.joinToString(" and ") { it.lowercase() }} limits"
    }
}

private fun dayCount(count: Int) = if (count == 1) "1 day" else "$count days"

/** The line under the greeting: how the day is going, in words. */
fun goalSummary(met: Int, total: Int, anythingLogged: Boolean): String {
    val rings = if (total == 1) "ring" else "rings"
    return when {
        !anythingLogged -> "Log a meal to start filling your $rings."
        total == 1 -> if (met == 1) "Goal met. Nice work!" else "Goal not met yet"
        met == total -> "All $total goals met. Nice work!"
        else -> "$met of $total goals met"
    }
}

/** "12 days of food, 8 saved foods, 3 notes": what a backup holds, before restoring it. */
fun backupSummary(data: AppData): String {
    fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
    val days = data.entries.map { it.date }.toSet().size
    return listOf(
        count(days, "day of food", "days of food"),
        count(data.saved.size, "saved food", "saved foods"),
        count(data.notes.size, "note", "notes"),
    ).joinToString(", ")
}

/** "Oct 19, 2026" from a backup's ISO date or date-time, or null if it has no readable date. */
fun backupDate(isoDateTime: String?): String? = runCatching {
    LocalDate.parse(isoDateTime!!.take(10)).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}.getOrNull()

/** "Last backup: today", "Last backup: Oct 3 (16 days ago)", or a nudge if there's never been one. */
fun lastBackupText(lastBackup: String?, today: LocalDate): String {
    val day = lastBackup?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "No backup yet."
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    return when {
        daysAgo <= 0 -> "Last backup: today"
        daysAgo == 1L -> "Last backup: yesterday"
        else -> "Last backup: ${day.format(DateTimeFormatter.ofPattern("MMM d"))} ($daysAgo days ago)"
    }
}
