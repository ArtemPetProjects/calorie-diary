package com.veles.caloriediary.domain.usecase

import com.veles.caloriediary.domain.model.Entry
import com.veles.caloriediary.domain.repository.DiaryRepository

class UpdateEntryWeightUseCase(private val repository: DiaryRepository) {
    fun execute(entry: Entry, grams: Double): Boolean {
        val scaled = rescale(entry.values, entry.grams, grams) ?: return false
        repository.updateEntry(entry.id, grams, scaled)
        return true
    }
}