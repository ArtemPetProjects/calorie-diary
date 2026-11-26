package com.veles.caloriediary.domain.usecase

import com.veles.caloriediary.domain.model.Entry
import com.veles.caloriediary.domain.model.MealGroup
import com.veles.caloriediary.domain.model.Nutrients
import com.veles.caloriediary.domain.model.Targets

data class DayOverview(
    val entries: List<Entry>,
    val totals: Nutrients,
    val targets: Targets,
    val adjustment: Double,
    val weight: Double?,
    val groups: List<MealGroup>,
    val hasDrafts: Boolean
)