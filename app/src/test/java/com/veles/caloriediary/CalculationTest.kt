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
        assertNull(parseGrams("99999"))
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
    fun kcalFromMacrosUses4_9_4() {
        assertEquals(1260.0, kcalFromMacros(30.0, 60.0, 150.0), 0.01)
    }

    @Test
    fun macroSchemesGiveGrams() {
        assertEquals(5, MACRO_SCHEMES.size)
        for (scheme in MACRO_SCHEMES) {
            assertEquals(100, scheme.proteinPct + scheme.fatPct + scheme.carbsPct)
        }
        val targets = gramsFromScheme(2000.0, MACRO_SCHEMES.first())
        assertEquals(2000.0, targets.kcal, 0.01)
        assertEquals(75.0, targets.protein, 0.01)
        assertEquals(66.7, targets.fat, 0.01)
        assertEquals(275.0, targets.carbs, 0.01)
    }

    @Test
    fun groupByMealUsesOneHourWindow() {
        fun entry(id: Long, at: Long) = Entry(
            id, "2026-11-22", 1, "Продукт", 100.0, Nutrients(10.0, 1.0, 1.0, 1.0), at
        )
        val groups = groupByMeal(
            listOf(entry(1, 0L), entry(2, 3_600_000L), entry(3, 3_660_000L))
        )
        assertEquals(2, groups.size)
        assertEquals(2, groups[0].entries.size)
        assertEquals(1, groups[1].entries.size)
        assertEquals(0, groupByMeal(emptyList()).size)
    }

    @Test
    fun groupByMealHandlesLegacyZeroTimestamp() {
        fun entry(id: Long, at: Long) = Entry(
            id, "2026-11-22", 1, "Продукт", 100.0, Nutrients(10.0, 1.0, 1.0, 1.0), at
        )
        val groups = groupByMeal(listOf(entry(1, 1_000L), entry(2, 0L)))
        assertEquals(1, groups.size)
        assertEquals(0L, groups[0].startMs)
        assertEquals(2, groups[0].entries.size)
    }

    @Test
    fun weightSchemeUsesBodyWeight() {
        val t = weightScheme(70.0, 2000.0)!!
        assertEquals(140.0, t.protein, 0.01)
        assertEquals(35.0, t.fat, 0.01)
        assertEquals(281.0, t.carbs, 0.01)
        assert(kotlin.math.abs(t.kcal - 2000.0) <= 2.0)
    }

    @Test
    fun weightSchemeRejectsZeroInputs() {
        assertNull(weightScheme(0.0, 2000.0))
        assertNull(weightScheme(70.0, 0.0))
    }

    @Test
    fun productNameIsRestoredFromSuggestion() {
        assertEquals(
            "Хурма",
            ProductLabels.nameOf("Хурма • 70 ккал / Б 0,6 / Ж 0,2 / У 18,6 на 100 г")
        )
        assertEquals("Хурма", ProductLabels.nameOf("Хурма"))
    }

    @Test
    fun weightSchemeRejectsImpossibleCalories() {
        assertNull(weightScheme(100.0, 500.0))
    }

    @Test
    fun rankProductNamesPrefersPrefixMatches() {
        val names = listOf("Булка со сливой", "Слива", "Сливы сушёные", "Яблоко")
        assertEquals(
            listOf("Слива", "Сливы сушёные", "Булка со сливой"),
            rankProductNames(names, "сли")
        )
        assertEquals(names, rankProductNames(names, ""))
    }

    @Test
    fun recentlyUsedProductsGoFirst() {
        val names = listOf("Слива", "Булка со сливой", "Яблоко", "Сливы сушёные")
        val ranked = rankProductNamesWithRecent(
            names, "сли", listOf("булка со сливой")
        )
        assertEquals("Булка со сливой", ranked.first())
        assertEquals(3, ranked.size)
    }

    @Test
    fun recentProductsFollowUsageOrderEvenForEmptyQuery() {
        val names = listOf("Банан", "Молоко", "Хурма", "Яблоко")
        val ranked = rankProductNamesWithRecent(
            names, "", listOf("хурма", "молоко")
        )
        assertEquals(listOf("Хурма", "Молоко", "Банан", "Яблоко"), ranked)
    }

    @Test
    fun rescaleRecalculatesEntryMacros() {
        val values = Nutrients(100.0, 10.0, 20.0, 30.0)
        val scaled = rescale(values, 100.0, 200.0)!!
        assertEquals(200.0, scaled.kcal, 0.01)
        assertEquals(20.0, scaled.protein, 0.01)
        assertEquals(40.0, scaled.fat, 0.01)
        assertEquals(60.0, scaled.carbs, 0.01)
        assertNull(rescale(values, 0.0, 100.0))
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

    @Test
    fun manualKcalIsNeverOverwritten() {
        assertNull(autoFilledKcal(false, "235", 0.0, 0.0, 0.0))
    }

    @Test
    fun incompleteMacrosDoNotProduceKcal() {
        assertNull(autoFilledKcal(false, "", 10.0, null, 30.0))
    }

    @Test
    fun autoFilledKcalIsRecalculatedWhenMacrosChange() {
        assertEquals(85.0, autoFilledKcal(true, "340.0", 5.0, 5.0, 5.0))
    }

    @Test
    fun blankKcalIsFilledFromMacros() {
        assertEquals(340.0, autoFilledKcal(false, "", 10.0, 20.0, 30.0))
    }

    @Test
    fun progressPercentHandlesZeroAndOverflow() {
        assertEquals(0, progressPercent(100.0, 0.0))
        assertEquals(25, progressPercent(50.0, 200.0))
        assertEquals(100, progressPercent(250.0, 200.0))
    }

    @Test
    fun macroEditingRecalculatesKcalImmediately() {
        assertEquals(4.0, autoFilledKcal(true, "", 0.0, 0.0, 1.0))
        assertEquals(40.0, autoFilledKcal(true, "4.0", 0.0, 0.0, 10.0))
    }

    @Test
    fun allZeroMacrosKeepManualKcal() {
        assertNull(autoFilledKcal(true, "235", 0.0, 0.0, 0.0))
    }

    @Test
    fun emptyMacrosBecomeZero() {
        val n = productNutrients(235.0, null, null, null)
        assertEquals(235.0, n.kcal, 0.01)
        assertEquals(0.0, n.protein, 0.01)
        assertEquals(0.0, n.fat, 0.01)
        assertEquals(0.0, n.carbs, 0.01)
    }
}
