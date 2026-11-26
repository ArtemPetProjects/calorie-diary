package com.veles.caloriediary.domain.usecase

import com.veles.caloriediary.domain.model.Entry
import com.veles.caloriediary.domain.model.Product
import com.veles.caloriediary.domain.repository.DiaryRepository

class AddEntryUseCase(private val repository: DiaryRepository) {
    fun execute(date: String, product: Product, grams: Double): Long {
        val entry = Entry(0, date, product.id, product.name, grams, forWeight(product.per100, grams))
        return repository.addEntry(entry)
    }
}