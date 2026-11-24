package com.example.greetingcard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.greetingcard.FoodDraft
import com.example.greetingcard.FoodViewModel
import com.example.greetingcard.data.Guidelines
import com.example.greetingcard.data.LimitNutrient
import com.example.greetingcard.data.Meal
import com.example.greetingcard.data.Nutrient
import com.example.greetingcard.data.Nutrients

private const val LIMIT_HELP =
    "Leave blank if you don't know. On a label, added sugars are the \"Includes … Added Sugars\" line."

/** Number fields start empty instead of showing "0". */
private fun Double.field(): String = if (this == 0.0) "" else fmt()

private class NutrientField(
    val nutrient: Nutrient,
    val value: String,
    val onChange: (String) -> Unit,
    val label: String,
    val suffix: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodFormScreen(
    vm: FoodViewModel,
    meal: Meal?,
    draft: FoodDraft,
    snackbarHost: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val n = draft.perServing
    var name by rememberSaveable { mutableStateOf(draft.name) }
    var servingLabel by rememberSaveable { mutableStateOf(draft.servingLabel) }
    var servings by rememberSaveable { mutableStateOf("1") }
    var protein by rememberSaveable { mutableStateOf(n.protein.field()) }
    var fiber by rememberSaveable { mutableStateOf(n.fiber.field()) }
    var fruit by rememberSaveable { mutableStateOf(n.fruit.field()) }
    var veg by rememberSaveable { mutableStateOf(n.veg.field()) }
    var calories by rememberSaveable { mutableStateOf(n.calories?.fmt().orEmpty()) }
    // Blank means unknown, and 0 is a real value, so these show "0" rather than starting empty.
    var sodium by rememberSaveable { mutableStateOf(n.sodium?.fmt().orEmpty()) }
    var addedSugars by rememberSaveable { mutableStateOf(n.addedSugars?.fmt().orEmpty()) }
    var saveForLater by rememberSaveable { mutableStateOf(true) }

    val editing = draft.editingSavedId != null
    val servingsValue = servings.toNumberOrNull()
    val valid = name.isNotBlank() &&
        listOf(protein, fiber, fruit, veg, calories, sodium, addedSugars)
            .all { it.isBlank() || it.toNumberOrNull() != null } &&
        (meal == null || (servingsValue != null && servingsValue > 0))

    val tracked = vm.trackedNutrients

    // Goals that are turned off have no field, so they keep whatever the draft came with, like hidden calories.
    fun valueOf(nutrient: Nutrient, text: String): Double =
        if (nutrient in tracked) text.toNumberOrNull() ?: 0.0 else n[nutrient]

    // The same for limits that are off. A blank field stays unknown rather than becoming 0.
    val limits = vm.trackedLimits

    fun valueOf(limit: LimitNutrient, text: String): Double? = if (limit in limits) text.toNumberOrNull() else n[limit]

    fun submit() {
        if (!valid) return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        vm.submitFood(
            meal = meal,
            draft = draft,
            name = name.trim(),
            servingLabel = servingLabel.trim(),
            servings = servingsValue ?: 1.0,
            perServing = Nutrients(
                protein = valueOf(Nutrient.PROTEIN, protein),
                fiber = valueOf(Nutrient.FIBER, fiber),
                fruit = valueOf(Nutrient.FRUIT, fruit),
                veg = valueOf(Nutrient.VEG, veg),
                // Hidden calories keep whatever the barcode provided.
                calories = if (vm.showCalories) calories.toNumberOrNull() else n.calories,
                sodium = valueOf(LimitNutrient.SODIUM, sodium),
                addedSugars = valueOf(LimitNutrient.ADDED_SUGARS, addedSugars),
            ),
            saveForLater = saveForLater,
        )
    }

    val fields = listOf(
        NutrientField(Nutrient.PROTEIN, protein, { protein = it }, "Protein", "g"),
        NutrientField(Nutrient.FIBER, fiber, { fiber = it }, "Fiber", "g"),
        NutrientField(Nutrient.FRUIT, fruit, { fruit = it }, "Fruit servings"),
        NutrientField(Nutrient.VEG, veg, { veg = it }, "Veg servings"),
    ).filter { it.nutrient in tracked }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            editing -> "Edit saved food"
                            meal == null -> "New saved food"
                            else -> "Add to ${meal.label.lowercase()}"
                        },
                    )
                },
                navigationIcon = { BackButton(onClick = { vm.back() }) },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            // Shrinks above the keyboard, so the fields and the Add button can be scrolled to while typing.
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            if (draft.barcode != null) {
                val fromOpenFoodFacts = draft.name.isNotBlank()
                SecondaryText(
                    "Barcode ${draft.barcode}" +
                        if (fromOpenFoodFacts) " · from Open Food Facts. Check the numbers against the label." else "",
                )
            }
            draft.note?.let { SecondaryText(it) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Food name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = servingLabel,
                    onValueChange = { servingLabel = it },
                    label = { Text("Serving size") },
                    placeholder = { Text("e.g. 1 cup, 30 g") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f),
                )
                if (meal != null) {
                    NumberField(servings, { servings = it }, "Servings", Modifier.weight(1f))
                }
            }

            Text("Per serving", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            fields.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach {
                        NumberField(it.value, it.onChange, it.label, Modifier.weight(1f), suffix = it.suffix)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (Nutrient.FRUIT in tracked || Nutrient.VEG in tracked) SecondaryText(Guidelines.SERVING_HELP)
            if (limits.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (LimitNutrient.SODIUM in limits) {
                        NumberField(sodium, { sodium = it }, "Sodium", Modifier.weight(1f), suffix = "mg")
                    }
                    if (LimitNutrient.ADDED_SUGARS in limits) {
                        NumberField(addedSugars, {
                            addedSugars = it
                        }, "Added sugars", Modifier.weight(1f), suffix = "g")
                    }
                    if (limits.size == 1) Spacer(Modifier.weight(1f))
                }
                SecondaryText(LIMIT_HELP)
            }
            if (vm.showCalories) {
                NumberField(calories, { calories = it }, "Calories (optional)", Modifier.fillMaxWidth())
            }

            if (meal != null && !editing) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Save to my foods", style = MaterialTheme.typography.bodyLarge)
                        SecondaryText("Add it again later with one tap")
                    }
                    Switch(checked = saveForLater, onCheckedChange = { saveForLater = it })
                }
            }

            Button(onClick = ::submit, enabled = valid, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(
                    when {
                        editing -> "Save changes"
                        meal == null -> "Save food"
                        else -> "Add to ${meal.label.lowercase()}"
                    },
                )
            }
        }
    }
}
