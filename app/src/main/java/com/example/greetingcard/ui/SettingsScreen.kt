package com.example.greetingcard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.example.greetingcard.BuildConfig
import com.example.greetingcard.FoodViewModel
import com.example.greetingcard.R
import com.example.greetingcard.data.Backup
import com.example.greetingcard.data.CalorieMode
import com.example.greetingcard.data.Goal
import com.example.greetingcard.data.Guidelines
import com.example.greetingcard.data.Limit
import com.example.greetingcard.data.Profile
import com.example.greetingcard.data.Sex
import com.example.greetingcard.data.Source
import com.example.greetingcard.data.WholeFoods
import com.example.greetingcard.ui.theme.card
import kotlin.math.roundToInt

private const val LB_PER_KG = 2.20462

@Composable
fun SettingsScreen(vm: FoodViewModel, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val settings = vm.data.settings
    val profile = settings.profile
    var editingGoal by remember { mutableStateOf<Goal?>(null) }
    var editingLimit by remember { mutableStateOf<Limit?>(null) }
    val saveBackup = rememberBackupSaver(onPick = vm::saveBackup)
    val pickBackup = rememberBackupPicker(onPick = vm::openBackup)

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Section("Calories") {
            CalorieMode.entries.forEach { mode ->
                CalorieModeOption(
                    mode = mode,
                    selected = settings.calorieMode == mode,
                    onSelect = { vm.setCalorieMode(mode) },
                )
            }
        }

        Section("About you", subtitle = "Used to pick your guideline goals. Everything stays on this phone.") {
            NameField(name = vm.name, onChange = vm::setName)
            Spacer(Modifier.height(12.dp))
            Text("Sex", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { sex ->
                    FilterChip(
                        selected = profile.sex == sex,
                        onClick = { vm.setProfile(profile.copy(sex = sex)) },
                        label = { Text(sex.label) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            ProfileNumbers(profile, onChange = vm::setProfile)
        }

        Section(
            "Daily goals",
            subtitle = "Tap a goal to set your own number. Switch off any you don't want to track.",
        ) {
            val tracked = vm.trackedNutrients
            vm.allGoals.forEachIndexed { i, goal ->
                if (i > 0) HorizontalDivider()
                TargetRow(
                    label = goal.nutrient.label,
                    valueText = "${goal.value.fmt()} ${goal.nutrient.unit}",
                    basis = goal.basis,
                    offText = "Not shown on Today or on foods. Still recorded.",
                    on = goal.nutrient in tracked,
                    // The last goal stays on, so Today always has something to show.
                    canTurnOff = tracked.size > 1,
                    onClick = { editingGoal = goal },
                    onToggle = { vm.setTracked(goal.nutrient, it) },
                )
            }
            SecondaryText(Guidelines.SERVING_HELP, modifier = Modifier.padding(top = 8.dp))
        }

        Section(
            "Daily limits",
            subtitle = "Things to stay under each day. Switch one on to see it on Today and add it to foods.",
        ) {
            val enabled = vm.trackedLimits
            vm.allLimits.forEachIndexed { i, limit ->
                if (i > 0) HorizontalDivider()
                TargetRow(
                    label = limit.nutrient.label,
                    valueText = amountText(limit.value, limit.nutrient.unit),
                    basis = limit.basis,
                    offText = "Off. Foods still keep their ${limit.nutrient.label.lowercase()} numbers.",
                    on = limit.nutrient in enabled,
                    canTurnOff = true,
                    onClick = { editingLimit = limit },
                    onToggle = { vm.setLimitEnabled(limit.nutrient, it) },
                )
            }
        }

        Section(
            "Backup",
            subtitle = "Save a copy of everything (food log, saved foods, goals and notes) to Google Drive, " +
                "Downloads, or anywhere else. Restore it after reinstalling the app or on a new phone.",
        ) {
            BackupButtons(
                lastBackup = lastBackupText(settings.lastBackup, vm.today),
                onSave = { saveBackup(Backup.fileName(vm.today)) },
                onRestore = pickBackup,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SourcesText(Guidelines.SOURCES)

        SecondaryText(
            "${stringResource(R.string.app_name)} ${BuildConfig.VERSION_NAME} · © 2026 Nikita Torosyan",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
    }

    editingGoal?.let { goal ->
        TargetDialog(
            title = "${goal.nutrient.label} goal",
            fieldLabel = "Daily goal",
            unit = goal.nutrient.unit,
            value = goal.value,
            guideline = Guidelines.guideline(goal.nutrient, profile).first.let { "${it.fmt()} ${goal.nutrient.unit}" },
            isCustom = goal.isCustom,
            onDismiss = { editingGoal = null },
            onSave = { value ->
                vm.setCustomGoal(goal.nutrient, value)
                editingGoal = null
            },
        )
    }

    editingLimit?.let { limit ->
        TargetDialog(
            title = "${limit.nutrient.label} limit",
            fieldLabel = "Daily limit",
            unit = limit.nutrient.unit,
            value = limit.value,
            guideline = amountText(Guidelines.limitGuideline(limit.nutrient, profile).first, limit.nutrient.unit),
            isCustom = limit.isCustom,
            onDismiss = { editingLimit = null },
            onSave = { value ->
                vm.setCustomLimit(limit.nutrient, value)
                editingLimit = null
            },
        )
    }
}

/** Each source name is a tappable link; Text opens it in the browser via LocalUriHandler. */
@Composable
private fun SourcesText(sources: List<Source>, modifier: Modifier = Modifier) {
    val links = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    val text = buildAnnotatedString {
        append("Sources: ")
        sources.forEachIndexed { i, source ->
            if (i > 0) append(", ")
            withLink(LinkAnnotation.Url(source.url, links)) { append(source.name) }
        }
        append(". Common foods from ")
        withLink(LinkAnnotation.Url(WholeFoods.SOURCE_URL, links)) { append("USDA FoodData Central") }
        append(". Restaurant meals from each chain's own nutrition info. Packaged food data from ")
        withLink(LinkAnnotation.Url(Guidelines.OPEN_FOOD_FACTS_URL, links)) { append("Open Food Facts") }
        append(". This app gives general guidance, not medical advice.")
    }
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) SecondaryText(subtitle)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun CalorieModeOption(
    mode: CalorieMode,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
            SecondaryText(mode.description)
        }
    }
}

/** A goal or limit with its number and source. When off, it's dimmed and only the switch responds. */
@Composable
private fun TargetRow(
    label: String,
    valueText: String,
    basis: String,
    offText: String,
    on: Boolean,
    canTurnOff: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = on, onClick = onClick)
                .padding(vertical = 10.dp)
                .alpha(if (on) 1f else 0.5f),
        ) {
            Row {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    if (on) valueText else "Off",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SecondaryText(if (on) basis else offText)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = on,
            onCheckedChange = onToggle,
            enabled = !on || canTurnOff,
            modifier = Modifier.semantics { contentDescription = "Track ${label.lowercase()}" },
        )
    }
}

/** Saves as you type, but never a blank name: that would send the user back to the welcome screen. */
@Composable
private fun NameField(name: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(name) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            if (it.isNotBlank()) onChange(it)
        },
        label = { Text("Name") },
        isError = text.isBlank(),
        supportingText = if (text.isBlank()) ({ Text("Your name can't be empty") }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProfileNumbers(profile: Profile, onChange: (Profile) -> Unit, modifier: Modifier = Modifier) {
    // Local text state so partially typed numbers aren't reformatted mid-edit.
    var age by remember { mutableStateOf(profile.age?.toString().orEmpty()) }
    var weight by remember(profile.useLbs) {
        val shown = profile.weightKg?.let { kg -> if (profile.useLbs) kg * LB_PER_KG else kg }
        mutableStateOf(shown?.roundToInt()?.toString().orEmpty())
    }
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                value = age,
                onValueChange = {
                    age = it
                    onChange(profile.copy(age = it.toNumberOrNull()?.roundToInt()?.takeIf { a -> a in 1..120 }))
                },
                label = "Age",
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = weight,
                onValueChange = {
                    weight = it
                    val number = it.toNumberOrNull()?.takeIf { w -> w > 0 }
                    onChange(profile.copy(weightKg = number?.let { w -> if (profile.useLbs) w / LB_PER_KG else w }))
                },
                label = "Weight",
                suffix = if (profile.useLbs) "lb" else "kg",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = profile.useLbs,
                onClick = { onChange(profile.copy(useLbs = true)) },
                label = { Text("lb") },
            )
            FilterChip(
                selected = !profile.useLbs,
                onClick = { onChange(profile.copy(useLbs = false)) },
                label = { Text("kg") },
            )
        }
    }
}

/** Sets your own number for a goal or limit, or goes back to the guideline. */
@Composable
private fun TargetDialog(
    title: String,
    fieldLabel: String,
    unit: String,
    value: Double,
    guideline: String,
    isCustom: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit,
) {
    var text by remember { mutableStateOf(value.fmt()) }
    val newValue = text.toNumberOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                NumberField(text, { text = it }, fieldLabel, suffix = unit, selectAllOnOpen = true)
                SecondaryText("Guideline: $guideline")
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(newValue) }, enabled = newValue != null) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (isCustom) {
                    TextButton(onClick = { onSave(null) }) { Text("Use guideline") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
