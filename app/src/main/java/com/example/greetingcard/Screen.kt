package com.example.greetingcard

import com.example.greetingcard.data.Meal
import com.example.greetingcard.data.Nutrients
import com.example.greetingcard.data.SavedFood

sealed interface Screen {
    data object Today : Screen

    data object Saved : Screen

    data object History : Screen

    data object Settings : Screen

    data class AddFood(val meal: Meal) : Screen

    /** [meal] is null when the form only edits or creates a saved food without logging it. */
    data class FoodForm(val meal: Meal?, val draft: FoodDraft) : Screen
}

/** What the food form starts out with: a blank food, a barcode or search result, or a saved food to edit. */
data class FoodDraft(
    val name: String = "",
    val servingLabel: String = "",
    val perServing: Nutrients = Nutrients.ZERO,
    val barcode: String? = null,
    val editingSavedId: String? = null,
    /** Where the numbers came from, shown at the top of the form. */
    val note: String? = null,
) {
    companion object {
        fun editing(food: SavedFood) = FoodDraft(
            name = food.name,
            servingLabel = food.servingLabel,
            perServing = food.perServing,
            barcode = food.barcode,
            editingSavedId = food.id,
        )
    }
}
