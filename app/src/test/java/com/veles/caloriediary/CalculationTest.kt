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
}
