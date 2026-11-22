package com.veles.caloriediary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculationTest {

    @Test
    fun forWeightScalesPer100() {
        val r = forWeight(Nutrients(200.0, 12.0, 10.0, 30.0), 150.0)
        assertEquals(300.0, r.kcal, 0.01)
        assertEquals(18.0, r.protein, 0.01)
        assertEquals(15.0, r.fat, 0.01)
        assertEquals(45.0, r.carbs, 0.01)
    }

    @Test
    fun sumAddsAllFields() {
        val r = sum(listOf(Nutrients(100.0, 1.0, 2.0, 3.0), Nutrients(50.0, 4.0, 5.0, 6.0)))
        assertEquals(150.0, r.kcal, 0.01)
        assertEquals(5.0, r.protein, 0.01)
        assertEquals(7.0, r.fat, 0.01)
        assertEquals(9.0, r.carbs, 0.01)
    }

    @Test
    fun gramsRejectZeroAndBlank() {
        assertNull(parseGrams(""))
        assertNull(parseGrams("0"))
        assertNull(parseGrams("-5"))
    }

    @Test
    fun parseGramsAcceptsComma() {
        assertEquals(12.5, parseGrams("12,5")!!, 0.01)
    }

    @Test
    fun progressZeroTarget() {
        assertEquals(0, progress(500.0, 0.0))
        assertEquals(25, progress(50.0, 200.0))
        assertEquals(100, progress(2500.0, 2000.0))
    }

    @Test
    fun dateKeyPadsAndHandlesLeapDay() {
        assertEquals("2024-02-29", dateKey(2024, 2, 29))
        assertEquals("2026-11-02", dateKey(2026, 11, 2))
    }

    @Test
    fun dishPer100ScalesToFinalWeight() {
        val items = listOf(
            DishIngredient(1, "Молоко", 200.0, Nutrients(60.0, 3.0, 3.2, 4.7)),
            DishIngredient(2, "Рис", 100.0, Nutrients(116.0, 2.6, 0.3, 25.0))
        )
        val r = dishPer100(items, 150.0)!!
        assertEquals(157.3, r.kcal, 0.05)
    }

    @Test
    fun dishPer100RejectsNonPositiveFinalWeight() {
        val item = DishIngredient(1, "Молоко", 100.0, Nutrients(60.0, 3.0, 3.2, 4.7))
        assertNull(dishPer100(listOf(item), 0.0))
        assertNull(dishPer100(listOf(item), -5.0))
    }

    @Test
    fun dishPer100RejectsEmptyIngredients() {
        assertNull(dishPer100(emptyList(), 200.0))
    }

    @Test
    fun weekDatesCrossesMonthAndLeapDay() {
        assertEquals(
            listOf(
                "2024-02-26", "2024-02-27", "2024-02-28", "2024-02-29",
                "2024-03-01", "2024-03-02", "2024-03-03"
            ),
            weekDates("2024-02-29")
        )
        assertEquals(
            listOf(
                "2025-12-29", "2025-12-30", "2025-12-31", "2026-01-01",
                "2026-01-02", "2026-01-03", "2026-01-04"
            ),
            weekDates("2026-01-01")
        )
    }
}
