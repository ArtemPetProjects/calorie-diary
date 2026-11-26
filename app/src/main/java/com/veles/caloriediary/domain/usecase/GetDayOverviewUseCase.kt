package com.veles.caloriediary.domain.usecase

import com.veles.caloriediary.domain.repository.DiaryRepository

class GetDayOverviewUseCase(private val repository: DiaryRepository) {
    fun execute(date: String): DayOverview {
        val entries = repository.entries(date)
        val totals = sum(entries.map { it.values })
        val adjustment = repository.adjustmentFor(date)
        val targets = adjustTargets(repository.targets(), adjustment)
        return DayOverview(
            entries = entries,
            totals = totals,
            targets = targets,
            adjustment = adjustment,
            weight = repository.weightFor(date),
            groups = groupByMeal(entries),
            hasDrafts = repository.drafts().isNotEmpty()
        )
    }
}