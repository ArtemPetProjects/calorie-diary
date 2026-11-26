package com.veles.caloriediary.domain.repository

import com.veles.caloriediary.domain.model.CustomScheme
import com.veles.caloriediary.domain.model.DishDraft
import com.veles.caloriediary.domain.model.DishIngredient
import com.veles.caloriediary.domain.model.Entry
import com.veles.caloriediary.domain.model.Nutrients
import com.veles.caloriediary.domain.model.Product
import com.veles.caloriediary.domain.model.Profile
import com.veles.caloriediary.domain.model.Targets

interface DiaryRepository {
    fun activeProfileId(): Long
    fun setActiveProfile(id: Long)
    fun schemeForProfile(): String
    fun saveScheme(scheme: String)
    fun customSchemes(): List<CustomScheme>
    fun addCustomScheme(name: String, purpose: String, p: Int, f: Int, c: Int): Long
    fun deleteCustomScheme(id: Long)

    fun products(query: String = "", onlyCustom: Boolean = false, onlyDishes: Boolean = false): List<Product>
    fun saveProduct(p: Product): Long
    fun saveDish(name: String, finalGrams: Double, per100: Nutrients, items: List<DishIngredient>, dishId: Long = 0L): Long
    fun dishItems(dishId: Long): List<DishIngredient>
    fun deleteProduct(id: Long)

    fun entries(date: String): List<Entry>
    fun addEntry(e: Entry): Long
    fun updateEntry(id: Long, grams: Double, values: Nutrients): Int
    fun deleteEntry(id: Long)

    fun targets(): Targets
    fun saveTargets(t: Targets)
    fun weightFor(date: String): Double?
    fun saveWeight(date: String, kg: Double): Long
    fun deleteWeight(date: String)

    fun markUsed(productId: Long)
    fun recentProductIds(): List<Long>

    fun drafts(): List<DishDraft>
    fun draft(id: Long): DishDraft?
    fun saveDraft(name: String, items: List<DishIngredient>): Long
    fun deleteDraft(id: Long)
    fun saveDraft(items: List<DishIngredient>)
    fun hasDraft(): Boolean
    fun draftItems(): List<DishIngredient>
    fun clearDraft()

    fun profiles(): List<Profile>
    fun addProfile(name: String): Long
    fun renameProfile(id: Long, name: String): Boolean
    fun deleteProfile(id: Long): Boolean
    fun bodyWeight(): Double
    fun saveBodyWeight(kg: Double)

    fun adjustmentFor(date: String): Double
    fun saveAdjustment(date: String, delta: Double)
}