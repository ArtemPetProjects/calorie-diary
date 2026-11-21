package com.veles.caloriediary

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

private fun round1(value: Double): Double = round(value * 10.0) / 10.0
