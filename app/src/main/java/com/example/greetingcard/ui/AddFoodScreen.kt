package com.example.greetingcard.ui

import android.content.Context
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.greetingcard.FoodDraft
import com.example.greetingcard.FoodViewModel
import com.example.greetingcard.FoodViewModel.OnlineSearch
import com.example.greetingcard.R
import com.example.greetingcard.Screen
import com.example.greetingcard.data.LimitNutrient
import com.example.greetingcard.data.Meal
import com.example.greetingcard.data.Nutrient
import com.example.greetingcard.data.Portion
import com.example.greetingcard.data.Restaurants
import com.example.greetingcard.data.SearchHit
import com.example.greetingcard.data.WholeFood
import com.example.greetingcard.data.WholeFoods
import com.example.greetingcard.ui.theme.card
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(vm: FoodViewModel, meal: Meal, snackbarHost: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val query = vm.addFoodQuery
    var typingBarcode by remember { mutableStateOf(false) }
    var choosingPortion by remember { mutableStateOf<WholeFood?>(null) }
    val searching = query.isNotBlank()
    val foods = vm.savedFoods.filter { !searching || it.name.contains(query.trim(), ignoreCase = true) }
    val common = if (searching) WholeFoods.search(vm.wholeFoods, query) else emptyList()
    val restaurant = if (searching) Restaurants.search(vm.menuItems, query) else emptyList()
    // Online results only show while the box still says what was searched for.
    val online = vm.onlineSearch.takeIf { it.query == query.trim() }
    val inMeal = vm.entriesFor(vm.date).filter { it.meal == meal }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (vm.date == vm.today) "Add food" else "Add food · ${dayLabel(vm.date, vm.today)}") },
                navigationIcon = { BackButton(onClick = { vm.back() }) },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(
            // Results stay reachable above the keyboard while searching.
            modifier = Modifier.consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column {
                    MealChips(selected = meal, onSelect = vm::switchAddFoodMeal)
                    // What's already in this meal, so it's clear what's been added while adding several things.
                    if (inMeal.isNotEmpty()) {
                        SecondaryText(
                            "In ${meal.label.lowercase()}: " + inMeal.joinToString(", ") { it.name },
                            maxLines = 2,
                        )
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.addFoodQuery = it },
                    label = { Text("Search foods") },
                    placeholder = { Text("e.g. banana, Five Guys burger, Gushers") },
                    singleLine = true,
                    trailingIcon = {
                        Row {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { vm.addFoodQuery = "" }) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear search")
                                }
                            }
                            IconButton(
                                enabled = !vm.lookingUpBarcode,
                                onClick = {
                                    scanBarcode(
                                        context,
                                        onResult = { vm.onBarcode(it, meal) },
                                        onError = {
                                            vm.showMessage(
                                                "Scanner unavailable: $it. You can type the barcode instead.",
                                            )
                                        },
                                    )
                                },
                            ) {
                                Icon(painterResource(R.drawable.ic_barcode), contentDescription = "Scan barcode")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OtherWaysToAdd(
                    lookingUp = vm.lookingUpBarcode,
                    onTypeBarcode = { typingBarcode = true },
                    onNewFood = { vm.navigate(Screen.FoodForm(meal, FoodDraft())) },
                )
            }
            if (foods.isNotEmpty()) {
                item { SectionLabel(if (searching) "My foods · tap to add one serving" else "Tap to add one serving") }
                items(foods, key = { it.id }) { food ->
                    SavedFoodRow(
                        food,
                        vm.showCalories,
                        vm.trackedNutrients,
                        vm.trackedLimits,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            vm.quickAdd(food, meal)
                        },
                    )
                }
            } else if (!searching) {
                item {
                    SecondaryText(
                        "Search for common foods like banana or eggs, restaurant meals like a Chili's Triple " +
                            "Dipper, or packaged foods by name, or scan a barcode. " +
                            "Foods you add show up here for one-tap adding.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
            if (searching) {
                if (common.isNotEmpty()) {
                    item { SectionLabel("Common foods") }
                    items(common, key = { "common:" + it.name }) { food ->
                        SearchResultRow(food.name, onClick = { choosingPortion = food })
                    }
                }
                if (restaurant.isNotEmpty()) {
                    item { SectionLabel("Restaurants") }
                    items(restaurant, key = { "restaurant:" + it.chain.name + ":" + it.name }) { menuItem ->
                        SearchResultRow(
                            name = menuItem.name,
                            detail = "${menuItem.chain.name} · ${menuItem.serving}",
                            onClick = { vm.pickMenuItem(menuItem, meal) },
                        )
                    }
                }
                item { SectionLabel("Packaged foods") }
                onlineResults(
                    query = query.trim(),
                    search = online,
                    lookingUp = vm.lookingUpBarcode,
                    onSearch = {
                        focusManager.clearFocus()
                        vm.searchOnline(query)
                    },
                    onPick = { vm.onBarcode(it.barcode, meal) },
                )
            }
        }
    }

    if (typingBarcode) {
        BarcodeDialog(
            onDismiss = { typingBarcode = false },
            onLookup = {
                typingBarcode = false
                vm.onBarcode(it, meal)
            },
        )
    }

    choosingPortion?.let { food ->
        PortionDialog(
            food = food,
            showCalories = vm.showCalories,
            tracked = vm.trackedNutrients,
            limits = vm.trackedLimits,
            onDismiss = { choosingPortion = null },
            onPick = { portion ->
                choosingPortion = null
                vm.pickWholeFood(food, portion, meal)
            },
        )
    }
}

/** The less common ways in, kept small under the search box. Scanning is the barcode icon in the box. */
@Composable
private fun OtherWaysToAdd(
    lookingUp: Boolean,
    onTypeBarcode: () -> Unit,
    onNewFood: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // No side padding on the first one, so it lines up with the search box.
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onNewFood, contentPadding = PaddingValues(vertical = 4.dp)) {
                Text("Enter a food yourself")
            }
            TextButton(onClick = onTypeBarcode, enabled = !lookingUp, contentPadding = PaddingValues(vertical = 4.dp)) {
                Text("Type a barcode")
            }
        }
        if (lookingUp) LoadingRow("Looking up product…", Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun LoadingRow(text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp),
    )
}

/** A search result: a name, and optionally a second line such as the brand. */
@Composable
private fun SearchResultRow(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String = "",
    enabled: Boolean = true,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail.isNotBlank()) SecondaryText(detail, maxLines = 1)
        }
    }
}

/** The packaged-food section: a button to search online, then the results or what went wrong. */
private fun LazyListScope.onlineResults(
    query: String,
    search: OnlineSearch?,
    lookingUp: Boolean,
    onSearch: () -> Unit,
    onPick: (SearchHit) -> Unit,
) {
    when (search) {
        null, OnlineSearch.Idle -> item {
            OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                Text("Search online for \"$query\"", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        is OnlineSearch.Loading -> item { LoadingRow("Searching Open Food Facts…", Modifier.padding(vertical = 8.dp)) }

        is OnlineSearch.Failed -> item {
            Column {
                SecondaryText("Search failed: ${search.reason}")
                TextButton(onClick = onSearch, contentPadding = PaddingValues(vertical = 8.dp)) { Text("Try again") }
            }
        }

        is OnlineSearch.Done -> if (search.hits.isEmpty()) {
            item { SecondaryText("Nothing found online for \"$query\". Try fewer words, or scan the barcode.") }
        } else {
            if (lookingUp) item { LoadingRow("Looking up product…") }
            items(search.hits, key = { "online:" + it.barcode }) { hit ->
                SearchResultRow(
                    name = hit.name,
                    detail = listOf(hit.brand, hit.quantity).filter { it.isNotBlank() }.joinToString(" · "),
                    onClick = { onPick(hit) },
                    enabled = !lookingUp,
                )
            }
            item { SecondaryText("From Open Food Facts. You'll see the numbers before anything is added.") }
        }
    }
}

/** Picks how much of a common food: its usual portions, or 100 g. Each shows what it adds. */
@Composable
private fun PortionDialog(
    food: WholeFood,
    showCalories: Boolean,
    tracked: List<Nutrient>,
    limits: List<LimitNutrient>,
    onDismiss: () -> Unit,
    onPick: (Portion) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SecondaryText("Pick a portion. You can change the number of servings next.")
                Spacer(Modifier.height(8.dp))
                WholeFoods.portionsFor(food).forEachIndexed { i, portion ->
                    if (i > 0) HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(portion) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(WholeFoods.servingLabel(portion), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            nutrientLine(WholeFoods.nutrientsFor(food, portion), showCalories, tracked, limits),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Opens Google's barcode scanner UI, limited to the formats used on food packaging. */
private fun scanBarcode(context: Context, onResult: (String) -> Unit, onError: (String?) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .enableAutoZoom()
        .build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onResult) }
        .addOnFailureListener { onError(it.message) }
}

@Composable
private fun BarcodeDialog(onDismiss: () -> Unit, onLookup: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Look up barcode") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { new -> code = new.filter(Char::isDigit) },
                label = { Text("Numbers under the barcode") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onLookup(code) }, enabled = code.length >= 8) { Text("Look up") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
