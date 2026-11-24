package com.example.greetingcard.ui

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.greetingcard.FoodViewModel
import com.example.greetingcard.R
import com.example.greetingcard.data.FoodEntry
import com.example.greetingcard.data.Goal
import com.example.greetingcard.data.History
import com.example.greetingcard.data.Limit
import com.example.greetingcard.data.LimitNutrient
import com.example.greetingcard.data.Meal
import com.example.greetingcard.data.Nutrient
import com.example.greetingcard.data.Nutrients
import com.example.greetingcard.ui.theme.card
import kotlinx.coroutines.delay
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayTopBar(vm: FoodViewModel, modifier: Modifier = Modifier) {
    val today = vm.today
    val title = dayLabel(vm.date, today)
    TopAppBar(
        modifier = modifier,
        title = {
            Text(title, modifier = Modifier.clickable(enabled = vm.date != today) { vm.goToToday() })
        },
        actions = {
            IconButton(onClick = vm::previousDay) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = "Previous day")
            }
            IconButton(onClick = vm::nextDay) {
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Next day")
            }
        },
    )
}

@Composable
fun TodayScreen(vm: FoodViewModel, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val entries = vm.entriesFor(vm.date)
    val totals = vm.totalsFor(vm.date)
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    var savingMeal by remember { mutableStateOf<Meal?>(null) }
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var notesFocused by remember { mutableStateOf(false) }
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    val focusManager = LocalFocusManager.current
    var keyboardWasOpen by remember { mutableStateOf(false) }

    // Closing the keyboard (back gesture or the keyboard's own button) also ends editing the note,
    // so the field doesn't stay highlighted.
    LaunchedEffect(keyboardHeight > 0) {
        val open = keyboardHeight > 0
        if (keyboardWasOpen && !open && notesFocused) focusManager.clearFocus()
        keyboardWasOpen = open
    }

    // The notes card is last in the list. Keep it scrolled into view while the keyboard opens:
    // this reruns on every frame of the keyboard animation, as the list gets shorter.
    LaunchedEffect(notesFocused, keyboardHeight) {
        if (notesFocused && keyboardHeight > 0) listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
    }

    LazyColumn(
        state = listState,
        // Keeps the notes field above the keyboard. The bottom bar's padding is already
        // counted in contentPadding, so it's subtracted from the keyboard height first.
        modifier = modifier
            .consumeWindowInsets(contentPadding)
            .imePadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            // Leaves room for the floating "Add to …" button.
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(greeting(LocalTime.now().hour, vm.name), style = MaterialTheme.typography.titleMedium)
                SecondaryText(
                    goalSummary(
                        met = History.goalsMet(vm.goals, totals),
                        total = vm.goals.size,
                        anythingLogged = entries.isNotEmpty(),
                    ),
                )
            }
        }
        item {
            DailyProgress(vm.goals, totals, vm.showCalories)
        }
        if (vm.limits.isNotEmpty()) {
            item(key = "limits") {
                LimitsCard(
                    limits = vm.limits,
                    totals = totals,
                    unknownCount = { limit -> vm.unknownCount(vm.date, limit.nutrient) },
                )
            }
        }
        items(Meal.entries, key = { it.name }) { meal ->
            MealCard(
                meal = meal,
                entries = entries.filter { it.meal == meal },
                showCalories = vm.showCalories,
                tracked = vm.trackedNutrients,
                limits = vm.trackedLimits,
                onAdd = { vm.openAddFood(meal) },
                onEntryClick = { editing = it },
                onSaveMeal = { savingMeal = meal },
                onRepeatYesterday = if (vm.yesterdaysEntries(meal).isEmpty()) {
                    null
                } else {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        vm.repeatYesterday(meal)
                    }
                },
            )
        }
        item(key = "notes") {
            NotesCard(
                note = vm.noteFor(vm.date),
                onNoteChange = { vm.setNote(vm.date, it) },
                savedAtMillis = vm.noteSaved?.takeIf { it.day == vm.date }?.atMillis,
                onDone = { focusManager.clearFocus() },
                modifier = Modifier.onFocusChanged { notesFocused = it.hasFocus },
            )
        }
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { servings, meal ->
                vm.updateEntry(entry.id, servings, meal)
                editing = null
            },
            onDelete = {
                vm.deleteEntry(entry.id)
                editing = null
            },
        )
    }

    savingMeal?.let { meal ->
        SaveMealDialog(
            meal = meal,
            onDismiss = { savingMeal = null },
            onSave = { name ->
                vm.saveMealAsFavorite(meal, name)
                savingMeal = null
            },
        )
    }
}

/**
 * The goal rings in one row, so the meals start on the first screen, with the calorie total underneath when
 * calories are shown.
 */
@Composable
private fun DailyProgress(goals: List<Goal>, totals: Nutrients, showCalories: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
            Row {
                goals.forEach { goal -> GoalRing(goal, totals[goal.nutrient], Modifier.weight(1f)) }
            }
            if (showCalories) {
                Text(
                    "${(totals.calories ?: 0.0).fmtCalories()} calories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
    }
}

/** One bar per limit: how close the day is to it. Green while under, red once over; no reward color. */
@Composable
private fun LimitsCard(
    limits: List<Limit>,
    totals: Nutrients,
    unknownCount: (Limit) -> Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(16.dp)) {
            Text("Daily limits", style = MaterialTheme.typography.titleMedium)
            limits.forEach { limit ->
                LimitBar(limit, totals[limit.nutrient] ?: 0.0, unknownCount(limit))
            }
        }
    }
}

@Composable
private fun LimitBar(limit: Limit, amount: Double, unknown: Int, modifier: Modifier = Modifier) {
    val unit = limit.nutrient.unit
    val over = !limit.isRespectedBy(amount)
    val color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(limit.nutrient.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(limitProgress(amount, limit.value, unit), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (limit.value <= 0) 1f else (amount / limit.value).toFloat().coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            limitStatus(amount, limit.value, unit),
            style = MaterialTheme.typography.bodySmall,
            color = if (over) color else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        unknownNote(unknown, limit.nutrient.label)?.let { SecondaryText(it) }
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<FoodEntry>,
    showCalories: Boolean,
    tracked: List<Nutrient>,
    limits: List<LimitNutrient>,
    onAdd: () -> Unit,
    onEntryClick: (FoodEntry) -> Unit,
    onSaveMeal: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown on an empty meal when the same meal had food the day before. */
    onRepeatYesterday: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 0.dp),
        ) {
            Text(meal.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (entries.size > 1) {
                TextButton(onClick = onSaveMeal) { Text("Save as meal") }
            }
            if (entries.isEmpty() && onRepeatYesterday != null) {
                TextButton(onClick = onRepeatYesterday) { Text("Repeat yesterday") }
            }
            IconButton(onClick = onAdd) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Add to ${meal.label}")
            }
        }
        // An empty meal is just its title row, so the day's food takes the space.
        entries.forEachIndexed { i, entry ->
            if (i > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            EntryRow(entry, showCalories, tracked, limits, onClick = { onEntryClick(entry) })
        }
        Spacer(Modifier.height(if (entries.isEmpty()) 4.dp else 6.dp))
    }
}

@Composable
private fun EntryRow(
    entry: FoodEntry,
    showCalories: Boolean,
    tracked: List<Nutrient>,
    limits: List<LimitNutrient>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            SecondaryText(
                servingsText(entry.servings, entry.servingLabel),
                maxLines = 1,
                // The name matters more than a long serving label ("1 burger (2 patties, …").
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
        SecondaryText(nutrientLine(entry.total, showCalories, tracked, limits))
    }
}

/** Change how much of an entry was eaten, move it to another meal, or remove it. */
@Composable
private fun EditEntryDialog(
    entry: FoodEntry,
    onDismiss: () -> Unit,
    onSave: (Double, Meal) -> Unit,
    onDelete: () -> Unit,
) {
    var servings by remember { mutableStateOf(entry.servings.fmt()) }
    var meal by remember { mutableStateOf(entry.meal) }
    val parsed = servings.toNumberOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column {
                NumberField(
                    value = servings,
                    onValueChange = { servings = it },
                    label = "Servings",
                    selectAllOnOpen = true,
                    supportingText = entry.servingLabel.ifBlank { null }?.let { "1 serving = $it" },
                )
                Spacer(Modifier.height(8.dp))
                MealChips(selected = meal, onSelect = { meal = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onSave(it, meal) } }, enabled = parsed != null) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SaveMealDialog(meal: Meal, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("My ${meal.label.lowercase()}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as meal") },
        text = {
            Column {
                Text(
                    "Everything in ${meal.label.lowercase()} becomes one item you can add in a single tap.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A small free-text note for the day: how it went, what to remember. Saved as you type, with a brief
 * "Saved" once it's written ([savedAtMillis]). The keyboard's Done key closes it, so a note has no line breaks.
 */
@Composable
private fun NotesCard(
    note: String,
    onNoteChange: (String) -> Unit,
    savedAtMillis: Long?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only right after a save: coming back to Today later doesn't show it again.
    var showSaved by remember { mutableStateOf(false) }
    LaunchedEffect(savedAtMillis) {
        val left = savedAtMillis?.let { SAVED_HINT_MS - (SystemClock.elapsedRealtime() - it) } ?: 0L
        showSaved = left > 0
        if (left > 0) {
            delay(left)
            showSaved = false
        }
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)) {
            Text("Notes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = {
                    // Typing again means it isn't saved yet.
                    showSaved = false
                    onNoteChange(it.take(NOTE_MAX_LENGTH))
                },
                placeholder = { Text("How did the day go? Anything to remember?") },
                supportingText = {
                    Row {
                        Text(
                            if (showSaved) "Saved" else "",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Text("${note.length} / $NOTE_MAX_LENGTH")
                    }
                },
                minLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val NOTE_MAX_LENGTH = 500
private const val SAVED_HINT_MS = 2000L

/** "Good morning, Sam" — or just "Good morning" if there's no name. */
fun greeting(hour: Int, name: String): String {
    val timeOfDay = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    return if (name.isBlank()) timeOfDay else "$timeOfDay, $name"
}
