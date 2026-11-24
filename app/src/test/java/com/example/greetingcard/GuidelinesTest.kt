package com.example.greetingcard

import com.example.greetingcard.data.Guidelines
import com.example.greetingcard.data.LimitNutrient
import com.example.greetingcard.data.Nutrient
import com.example.greetingcard.data.Nutrients
import com.example.greetingcard.data.Profile
import com.example.greetingcard.data.Settings
import com.example.greetingcard.data.Sex
import com.example.greetingcard.data.total
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidelinesTest {
    @Test
    fun proteinUsesBodyWeightWhenKnown() {
        assertEquals(84.0, Guidelines.protein(Profile(weightKg = 70.0)).first, 0.0)
    }

    @Test
    fun proteinFallsBackToRdaBySex() {
        assertEquals(46.0, Guidelines.protein(Profile(sex = Sex.FEMALE)).first, 0.0)
        assertEquals(56.0, Guidelines.protein(Profile(sex = Sex.MALE)).first, 0.0)
        assertEquals(50.0, Guidelines.protein(Profile()).first, 0.0)
    }

    @Test
    fun fiberFollowsAdequateIntakeTable() {
        assertEquals(25.0, Guidelines.fiber(Profile(sex = Sex.FEMALE, age = 30)).first, 0.0)
        assertEquals(21.0, Guidelines.fiber(Profile(sex = Sex.FEMALE, age = 60)).first, 0.0)
        assertEquals(26.0, Guidelines.fiber(Profile(sex = Sex.FEMALE, age = 16)).first, 0.0)
        assertEquals(38.0, Guidelines.fiber(Profile(sex = Sex.MALE, age = 30)).first, 0.0)
        assertEquals(30.0, Guidelines.fiber(Profile(sex = Sex.MALE, age = 51)).first, 0.0)
        assertEquals(31.0, Guidelines.fiber(Profile(sex = Sex.MALE, age = 12)).first, 0.0)
        assertEquals(28.0, Guidelines.fiber(Profile(age = 30)).first, 0.0)
    }

    @Test
    fun customGoalReplacesGuideline() {
        val goals = Guidelines.goals(Settings(customGoals = mapOf(Nutrient.FIBER to 35.0)))
        val fiber = goals.first { it.nutrient == Nutrient.FIBER }
        assertEquals(35.0, fiber.value, 0.0)
        assertTrue(fiber.isCustom)
        assertFalse(goals.first { it.nutrient == Nutrient.VEG }.isCustom)
        assertEquals(3.0, goals.first { it.nutrient == Nutrient.VEG }.value, 0.0)
        assertEquals(2.0, goals.first { it.nutrient == Nutrient.FRUIT }.value, 0.0)
    }

    @Test
    fun nutrientMathScalesAndSums() {
        val apple = Nutrients(fiber = 4.0, fruit = 1.0, calories = 95.0)
        val eggs = Nutrients(protein = 6.0)
        val total = listOf(apple * 2.0, eggs * 3.0).total()
        assertEquals(18.0, total.protein, 0.0)
        assertEquals(8.0, total.fiber, 0.0)
        assertEquals(2.0, total.fruit, 0.0)
        assertEquals(190.0, total.calories!!, 0.0)
    }

    @Test
    fun sodiumLimitFollowsAge() {
        assertEquals(2300.0, Guidelines.sodium(Profile()).first, 0.0)
        assertEquals(2300.0, Guidelines.sodium(Profile(age = 14)).first, 0.0)
        assertEquals(1800.0, Guidelines.sodium(Profile(age = 13)).first, 0.0)
        assertEquals(1800.0, Guidelines.sodium(Profile(age = 9)).first, 0.0)
        assertEquals(1500.0, Guidelines.sodium(Profile(age = 6)).first, 0.0)
        assertEquals(1200.0, Guidelines.sodium(Profile(age = 2)).first, 0.0)
    }

    @Test
    fun limitsUseGuidelinesUnlessOverridden() {
        val limits = Guidelines.limits(Settings(customLimits = mapOf(LimitNutrient.SODIUM to 1500.0)))
        val sodium = limits.first { it.nutrient == LimitNutrient.SODIUM }
        assertEquals(1500.0, sodium.value, 0.0)
        assertTrue(sodium.isCustom)
        val sugar = limits.first { it.nutrient == LimitNutrient.ADDED_SUGARS }
        assertEquals(50.0, sugar.value, 0.0)
        assertTrue(sugar.isRespectedBy(50.0))
        assertFalse(sugar.isRespectedBy(50.1))
    }

    @Test
    fun unknownSodiumAndSugarsStayUnknownUnlessSomethingHasThem() {
        val known = Nutrients(sodium = 400.0, addedSugars = 5.0)
        val unknown = Nutrients(protein = 10.0)
        assertNull(listOf(unknown, unknown).total().sodium)
        val mixed = listOf(known * 2.0, unknown).total()
        assertEquals(800.0, mixed.sodium!!, 0.0)
        assertEquals(10.0, mixed.addedSugars!!, 0.0)
    }

    @Test
    fun caloriesStayNullWhenNothingHasThem() {
        assertNull(listOf(Nutrients(protein = 5.0), Nutrients(fiber = 2.0)).total().calories)
    }
}
