package com.veles.caloriediary

import java.util.Calendar
import kotlin.math.round

data class Nutrients(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

fun forWeight(per100: Nutrients, grams: Double): Nutrients {
    val k = grams / 100.0
    return Nutrients(
        kcal = round1(per100.kcal * k),
        protein = round1(per100.protein * k),
        fat = round1(per100.fat * k),
        carbs = round1(per100.carbs * k)
    )
}

fun sum(items: List<Nutrients>): Nutrients = Nutrients(
    kcal = round1(items.sumOf { it.kcal }),
    protein = round1(items.sumOf { it.protein }),
    fat = round1(items.sumOf { it.fat }),
    carbs = round1(items.sumOf { it.carbs })
)

// Итог блюда делится на вес готового блюда, который отличается от суммы ингредиентов.
fun dishPer100(items: List<DishIngredient>, finalGrams: Double): Nutrients? {
    if (items.isEmpty() || finalGrams <= 0.0) return null
    val total = sum(items.map { forWeight(it.per100, it.grams) })
    val k = 100.0 / finalGrams
    return Nutrients(
        kcal = round1(total.kcal * k),
        protein = round1(total.protein * k),
        fat = round1(total.fat * k),
        carbs = round1(total.carbs * k)
    )
}

data class MacroScheme(val name: String, val proteinPct: Int, val fatPct: Int, val carbsPct: Int)

val MACRO_SCHEMES = listOf(
    MacroScheme("Поддержание", 15, 30, 55),
    MacroScheme("Похудение", 28, 27, 45),
    MacroScheme("Набор мышц", 25, 25, 50),
    MacroScheme("Низкоуглеводная", 25, 45, 30),
    MacroScheme("Кето", 10, 70, 20)
)

fun kcalFromMacros(protein: Double, fat: Double, carbs: Double): Double =
    round1(protein * 4.0 + fat * 9.0 + carbs * 4.0)

fun gramsFromScheme(kcal: Double, scheme: MacroScheme): Targets = Targets(
    kcal = kcal,
    protein = round1(kcal * scheme.proteinPct / 100.0 / 4.0),
    fat = round1(kcal * scheme.fatPct / 100.0 / 9.0),
    carbs = round1(kcal * scheme.carbsPct / 100.0 / 4.0)
)

data class MealGroup(val startMs: Long, val entries: List<Entry>)

fun groupByMeal(entries: List<Entry>, windowMs: Long = 3_600_000L): List<MealGroup> {
    if (entries.isEmpty()) return emptyList()
    val result = mutableListOf<MealGroup>()
    val sorted = entries.sortedBy { it.createdAt }
    var current = mutableListOf<Entry>()
    var start = 0L
    for (entry in sorted) {
        if (current.isEmpty()) {
            start = entry.createdAt
            current.add(entry)
        } else if (entry.createdAt - start <= windowMs) {
            current.add(entry)
        } else {
            result.add(MealGroup(start, current))
            start = entry.createdAt
            current = mutableListOf(entry)
        }
    }
    if (current.isNotEmpty()) result.add(MealGroup(start, current))
    return result
}

fun parseGrams(raw: String): Double? {
    val value = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return if (value > 0.0) value else null
}

fun progress(current: Double, target: Double): Int {
    if (target <= 0.0) return 0
    return ((current / target) * 100.0).toInt().coerceIn(0, 100)
}

fun dateKey(year: Int, month: Int, day: Int): String =
    "%04d-%02d-%02d".format(year, month, day)

// Понедельник — воскресенье недели, содержащей дату key.
fun weekDates(key: String): List<String> {
    val parts = key.split("-")
    val cal = Calendar.getInstance().apply {
        clear()
        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        firstDayOfWeek = Calendar.MONDAY
    }
    val offset = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
    cal.add(Calendar.DAY_OF_MONTH, -offset)
    return (0..6).map {
        val result = dateKey(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
        result
    }
}

private fun round1(value: Double): Double = round(value * 10.0) / 10.0
