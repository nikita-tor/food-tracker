package com.example.greetingcard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.greetingcard.R
import com.example.greetingcard.data.Goal
import com.example.greetingcard.data.LimitNutrient
import com.example.greetingcard.data.Meal
import com.example.greetingcard.data.Nutrient
import com.example.greetingcard.data.SavedFood
import com.example.greetingcard.ui.theme.card

/** Small, muted text for hints, explanations, and secondary details. */
@Composable
fun SecondaryText(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A small progress ring for one goal, sized so all of them fit in one row: the amount so far in the middle,
 * the goal under it, and what's left below. A met goal turns citrus with a filled center.
 */
@Composable
fun GoalRing(goal: Goal, current: Double, modifier: Modifier = Modifier) {
    val met = goal.isMetBy(current)
    val target = if (goal.value <= 0) 1f else (current / goal.value).toFloat().coerceIn(0f, 1f)
    // Starts empty and fills up each time the ring appears, then follows changes smoothly.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) { progress.animateTo(target, tween(durationMillis = 700)) }
    val color by animateColorAsState(
        if (met) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        label = "ring color",
    )
    val fill by animateColorAsState(
        if (met) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
        label = "ring fill",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val toGo = toGoText(goal, current)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${goal.nutrient.label}: ${amountWithUnit(current, goal.nutrient)} of " +
                "${amountWithUnit(goal.value, goal.nutrient)}. $toGo"
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(RING_SIZE)) {
            Canvas(modifier = Modifier.size(RING_SIZE)) {
                val strokeWidth = 7.dp.toPx()
                drawCircle(fill, radius = size.minDimension / 2 - strokeWidth)
                val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
                drawArc(color, startAngle = -90f, sweepAngle = 360f * progress.value, useCenter = false, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    current.fmt(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.run { if (met) onTertiaryContainer else onSurface },
                )
                Text(
                    "of ${goal.value.fmt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(goal.nutrient.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(
            toGo,
            style = MaterialTheme.typography.bodySmall,
            color = if (met) color else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val RING_SIZE = 68.dp

/** Picks one of the four meals. Wraps onto a second line where it doesn't fit (narrow dialogs). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealChips(selected: Meal, onSelect: (Meal) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Meal.entries.forEach { meal ->
            FilterChip(
                selected = meal == selected,
                onClick = { onSelect(meal) },
                label = { Text(meal.label) },
            )
        }
    }
}

/** A card for a saved food or meal: name, serving size, and what it contains. */
@Composable
fun SavedFoodRow(
    food: SavedFood,
    showCalories: Boolean,
    tracked: List<Nutrient>,
    limits: List<LimitNutrient>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    (if (food.isMeal) "🍽 " else "") + food.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (food.servingLabel.isNotBlank()) {
                    SecondaryText(food.servingLabel, maxLines = 1)
                }
                Text(
                    nutrientLine(food.perServing, showCalories, tracked, limits),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            trailing()
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    supportingText: String? = null,
    /** Focus the field and select its text when it first appears, so typing replaces the old number. */
    selectAllOnOpen: Boolean = false,
) {
    // Selection is tracked here so the initial "select all" works; the text itself stays owned by the caller.
    var field by remember {
        mutableStateOf(
            TextFieldValue(value, if (selectAllOnOpen) TextRange(0, value.length) else TextRange(value.length)),
        )
    }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    val focusRequester = remember { FocusRequester() }
    if (selectAllOnOpen) LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedTextField(
        value = field,
        onValueChange = { new ->
            if (new.text.all { it.isDigit() || it == '.' || it == ',' }) {
                field = new
                if (new.text != value) onValueChange(new.text)
            }
        },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = value.isNotBlank() && value.toNumberOrNull() == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier.focusRequester(focusRequester),
    )
}

@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
    }
}
