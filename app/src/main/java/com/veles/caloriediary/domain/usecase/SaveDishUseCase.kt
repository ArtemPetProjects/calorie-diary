package com.veles.caloriediary.domain.usecase

import com.veles.caloriediary.domain.model.DishIngredient
import com.veles.caloriediary.domain.model.Nutrients
import com.veles.caloriediary.domain.repository.DiaryRepository

class SaveDishUseCase(private val repository: DiaryRepository) {
    fun execute(
        name: String,
        finalGrams: Double,
        items: List<DishIngredient>,
        dishId: Long = 0L
    ): Long? {
        val per100 = dishPer100(items, finalGrams) ?: return null
        val id = repository.saveDish(name, finalGrams, per100, items, dishId)
        return if (id < 0) null else id
    }
}