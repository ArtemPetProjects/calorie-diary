package com.veles.caloriediary.domain.model

data class Nutrients(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

data class Product(
    val id: Long,
    val name: String,
    val per100: Nutrients,
    val finalGrams: Double = 0.0,
    val isDish: Boolean = false,
    val isCustom: Boolean = false
)

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
    val values: Nutrients,
    val createdAt: Long = 0L
)

data class Targets(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)

data class Profile(val id: Long, val name: String, val bodyKg: Double)

data class SchemeOption(val title: String, val details: String)

data class SchemeEntry(val key: String, val title: String, val details: String, val ratio: String)

data class CustomScheme(
    val id: Long,
    val name: String,
    val purpose: String,
    val proteinPct: Int,
    val fatPct: Int,
    val carbsPct: Int
)

data class DishDraft(
    val id: Long,
    val name: String,
    val items: List<DishIngredient>,
    val createdAt: Long
)

data class MacroScheme(val name: String, val proteinPct: Int, val fatPct: Int, val carbsPct: Int)

data class MealGroup(val startMs: Long, val entries: List<Entry>)

enum class EntryError { NONE, NAME, WEIGHT }
