package com.example.greetingcard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.greetingcard.FoodViewModel
import com.example.greetingcard.R
import com.example.greetingcard.data.DaySummary
import com.example.greetingcard.data.History
import com.example.greetingcard.data.HistoryPeriod
import com.example.greetingcard.ui.theme.card
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Bars stop at 150% of the goal or limit, so one huge day doesn't flatten the rest of the chart. */
private const val CHART_MAX = 1.5f

@Composable
fun HistoryScreen(vm: FoodViewModel, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val period = vm.historyPeriod
    val range = vm.historyRange
    val today = vm.today
    val goals = vm.goals
    val days = vm.historyDays
    val limits = vm.limits
    val summaries = days.map { History.summarize(it, goals, limits) }
    val periodName = period.label.lowercase()
    // Streaks are about now, so they only show while looking at the current week or month.
    val showStreaks = today in range

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PeriodSwitch(period, onChange = vm::selectHistoryPeriod)
        PeriodHeader(
            label = when (period) {
                HistoryPeriod.WEEK -> weekLabel(range.start, range.endInclusive, today.year)
                HistoryPeriod.MONTH -> range.start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            },
            periodName = periodName,
            onPrevious = vm::previousHistoryPeriod,
            onNext = vm::nextHistoryPeriod,
            canGoNext = vm.canGoForwardInHistory,
        )
        SecondaryText(
            listOfNotNull(
                periodSummary(summaries.count { it.logged }, summaries.count { it.allMet }, periodName),
                if (showStreaks) streakText(vm.streak(goals)) else null,
            ).joinToString(" "),
        )
        CalendarCard(
            weeks = when (period) {
                HistoryPeriod.WEEK -> listOf(days.map { it.date })
                HistoryPeriod.MONTH -> History.weeks(YearMonth.from(range.start), vm.firstDayOfWeek)
            },
            firstDayOfWeek = vm.firstDayOfWeek,
            summaries = summaries,
            today = today,
            onDayClick = vm::openDay,
        )
        if (summaries.any { it.logged }) {
            val dates = days.map { it.date }
            goals.forEach { goal ->
                val stats = History.stats(goal, days)
                val streak = if (showStreaks) vm.streak(listOf(goal)) else 0
                ChartCard(
                    title = goal.nutrient.label,
                    targetText = "Goal ${amountWithUnit(goal.value, goal.nutrient)}",
                    summary = goalStatsLine(stats.metDays, stats.loggedDays, stats.average, goal.nutrient, streak),
                    // Null for days with nothing logged: no bar, rather than a zero that looks like a miss.
                    values = days.map { if (it.logged) it.totals[goal.nutrient] else null },
                    target = goal.value,
                    highlightColor = MaterialTheme.colorScheme.tertiary,
                    isHighlighted = goal::isMetBy,
                    dates = dates,
                    period = period,
                )
            }
            limits.forEach { limit ->
                val stats = History.limitStats(limit, days)
                val streak = if (showStreaks) vm.limitStreak(limit) else 0
                ChartCard(
                    title = limit.nutrient.label,
                    targetText = "Limit ${amountText(limit.value, limit.nutrient.unit)}",
                    summary = limitStatsLine(
                        stats.underDays,
                        stats.knownDays,
                        stats.average,
                        limit.nutrient.unit,
                        streak,
                    ),
                    // Also null for days where no food had data for this limit.
                    values = days.map { if (it.logged) it.totals[limit.nutrient] else null },
                    target = limit.value,
                    highlightColor = MaterialTheme.colorScheme.error,
                    isHighlighted = { !limit.isRespectedBy(it) },
                    dates = dates,
                    period = period,
                )
            }
        }
        SecondaryText(
            "Rings fill a little for every goal met that day and turn citrus when they all are. Bars show each " +
                "day against the goal line, citrus when it was reached" +
                (if (limits.isEmpty()) "" else ", and against each limit, red when it was gone over") +
                ". Days are judged against your current goals. Tap a day to see or change it.",
        )
    }
}

@Composable
private fun PeriodSwitch(period: HistoryPeriod, onChange: (HistoryPeriod) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        HistoryPeriod.entries.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == period,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(i, HistoryPeriod.entries.size),
            ) { Text(option.label) }
        }
    }
}

@Composable
private fun PeriodHeader(
    label: String,
    periodName: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous $periodName")
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next $periodName")
        }
    }
}

/** Weekday letters over rows of day rings: a whole month, or a single week. */
@Composable
private fun CalendarCard(
    weeks: List<List<LocalDate?>>,
    firstDayOfWeek: DayOfWeek,
    summaries: List<DaySummary>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byDate = summaries.associateBy { it.date }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row {
                (0L until 7L).map { firstDayOfWeek.plus(it) }.forEach { weekday ->
                    WeekdayLabel(weekday, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            weeks.forEach { week ->
                Row {
                    week.forEach { date ->
                        val day = date?.let(byDate::get)
                        if (day == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            DayCell(
                                day = day,
                                isToday = day.date == today,
                                isFuture = day.date > today,
                                onClick = { onDayClick(day.date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayLabel(weekday: DayOfWeek, modifier: Modifier = Modifier) {
    Text(
        weekday.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.semantics {
            contentDescription = weekday.getDisplayName(TextStyle.FULL, Locale.getDefault())
        },
    )
}

/**
 * A day of the month: its number inside a ring that fills by the share of goals met.
 * Unlogged days have no ring at all, so a day off doesn't look like a failure.
 */
@Composable
private fun DayCell(
    day: DaySummary,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val track = colors.surfaceVariant
    val progress = if (day.allMet) colors.tertiary else colors.primary
    val fill = if (day.allMet) colors.tertiaryContainer else Color.Transparent
    val label = dayDescription(
        date = day.date.format(DateTimeFormatter.ofPattern("MMMM d")),
        logged = day.logged,
        met = day.met,
        total = day.total,
        overLimits = day.overLimits.map { it.label },
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(2.dp).background(fill, CircleShape)) {
            if (!day.logged) return@Canvas
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = size.copy(width = size.width - stroke.width, height = size.height - stroke.width)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            if (day.met > 0) {
                val sweep = 360f * day.met / day.total
                drawArc(progress, -90f, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            }
        }
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) colors.primary else colors.onSurface,
            modifier = Modifier.alpha(if (isFuture) 0.38f else 1f),
        )
        if (day.overLimits.isNotEmpty()) {
            // Over a limit that day: a small dot, kept apart from the ring, which is about goals.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 9.dp)
                    .size(5.dp)
                    .background(colors.error, CircleShape),
            )
        }
    }
}

/**
 * One goal or limit over the period: a summary line and a bar per day against a dashed line at [target].
 * Bars where [isHighlighted] is true (goal reached, or limit gone over) use [highlightColor].
 */
@Composable
private fun ChartCard(
    title: String,
    targetText: String,
    summary: String,
    values: List<Double?>,
    target: Double,
    highlightColor: Color,
    isHighlighted: (Double) -> Boolean,
    dates: List<LocalDate>,
    period: HistoryPeriod,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SecondaryText(targetText)
            }
            SecondaryText(summary)
            Spacer(Modifier.height(12.dp))
            DailyBars(
                values = values,
                target = target,
                highlightColor = highlightColor,
                isHighlighted = isHighlighted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clearAndSetSemantics { contentDescription = "$title by day. $summary" },
            )
            Spacer(Modifier.height(4.dp))
            ChartAxis(dates, period)
        }
    }
}

@Composable
private fun DailyBars(
    values: List<Double?>,
    target: Double,
    highlightColor: Color,
    isHighlighted: (Double) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val normal = colors.primary
    val line = colors.onSurfaceVariant
    val baseline = colors.surfaceVariant
    Canvas(modifier) {
        val gap = (if (values.size > 10) 2.dp else 8.dp).toPx()
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        val goalY = size.height * (1 - 1 / CHART_MAX)
        drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        values.forEachIndexed { i, value ->
            if (value == null || value <= 0 || target <= 0) return@forEachIndexed
            val ratio = (value / target).toFloat().coerceAtMost(CHART_MAX)
            // At least a sliver, so small amounts still show.
            val height = (size.height * ratio / CHART_MAX).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = if (isHighlighted(value)) highlightColor else normal,
                topLeft = Offset(i * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(minOf(barWidth / 4, 4.dp.toPx())),
            )
        }
        drawLine(
            line,
            Offset(0f, goalY),
            Offset(size.width, goalY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/** Weekday letters under a week's bars, or the first and last date under a month's. */
@Composable
private fun ChartAxis(dates: List<LocalDate>, period: HistoryPeriod, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        when (period) {
            HistoryPeriod.WEEK -> dates.forEach { date ->
                AxisLabel(date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f))
            }

            HistoryPeriod.MONTH -> {
                val format = DateTimeFormatter.ofPattern("MMM d")
                AxisLabel(dates.first().format(format), textAlign = TextAlign.Start)
                Spacer(Modifier.weight(1f))
                AxisLabel(dates.last().format(format), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun AxisLabel(text: String, modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Center) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}
