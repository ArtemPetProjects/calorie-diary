package com.veles.caloriediary

data class Product(val id: Long, val name: String, val per100: Nutrients)

data class DishIngredient(
    val productId: Long,
    val productName: String,
    val grams: Double,
    val per100: Nutrients
)

data class Entry(
    val id: Long,
    val date: String,
    val productId: Long,
    val productName: String,
    val grams: Double,
    val values: Nutrients
)

data class Targets(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)
