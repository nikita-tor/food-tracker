package com.example.greetingcard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.greetingcard.FoodDraft
import com.example.greetingcard.FoodViewModel
import com.example.greetingcard.R
import com.example.greetingcard.Screen

@Composable
fun SavedScreen(vm: FoodViewModel, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val foods = vm.savedFoods
    if (foods.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No saved foods yet.\n\nFoods are saved when you log them, or tap + to add one now. " +
                    "To save a whole meal, log two or more foods in it and tap \"Save as meal\" on the Today screen.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            // Leaves room for the floating "+" button.
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(foods, key = { it.id }) { food ->
            SavedFoodRow(
                food = food,
                showCalories = vm.showCalories,
                tracked = vm.trackedNutrients,
                limits = vm.trackedLimits,
                onClick = { vm.navigate(Screen.FoodForm(meal = null, draft = FoodDraft.editing(food))) },
                trailing = {
                    IconButton(onClick = { vm.deleteSaved(food.id) }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete ${food.name}")
                    }
                },
            )
        }
    }
}
