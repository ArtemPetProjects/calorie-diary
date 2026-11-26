package com.veles.caloriediary

import com.veles.caloriediary.domain.usecase.MACRO_SCHEMES as DOMAIN_MACRO_SCHEMES
import com.veles.caloriediary.domain.usecase.adjustTargets as domainAdjustTargets
import com.veles.caloriediary.domain.usecase.autoFilledKcal as domainAutoFilledKcal
import com.veles.caloriediary.domain.usecase.dateKey as domainDateKey
import com.veles.caloriediary.domain.usecase.dishPer100 as domainDishPer100
import com.veles.caloriediary.domain.usecase.draftTitle as domainDraftTitle
import com.veles.caloriediary.domain.usecase.entryValidationError as domainEntryValidationError
import com.veles.caloriediary.domain.usecase.excess as domainExcess
import com.veles.caloriediary.domain.usecase.fontScaleForDelta as domainFontScaleForDelta
import com.veles.caloriediary.domain.usecase.forWeight as domainForWeight
import com.veles.caloriediary.domain.usecase.gramsFromScheme as domainGramsFromScheme
import com.veles.caloriediary.domain.usecase.groupByMeal as domainGroupByMeal
import com.veles.caloriediary.domain.usecase.kcalFromMacros as domainKcalFromMacros
import com.veles.caloriediary.domain.usecase.parseGrams as domainParseGrams
import com.veles.caloriediary.domain.usecase.productNutrients as domainProductNutrients
import com.veles.caloriediary.domain.usecase.progress as domainProgress
import com.veles.caloriediary.domain.usecase.progressPercent as domainProgressPercent
import com.veles.caloriediary.domain.usecase.rankProductNames as domainRankProductNames
import com.veles.caloriediary.domain.usecase.rankProductNamesWithRecent as domainRankProductNamesWithRecent
import com.veles.caloriediary.domain.usecase.rescale as domainRescale
import com.veles.caloriediary.domain.usecase.sum as domainSum
import com.veles.caloriediary.domain.usecase.weekDates as domainWeekDates
import com.veles.caloriediary.domain.usecase.weightScheme as domainWeightScheme

typealias Nutrients = com.veles.caloriediary.domain.model.Nutrients
typealias MacroScheme = com.veles.caloriediary.domain.model.MacroScheme
typealias MealGroup = com.veles.caloriediary.domain.model.MealGroup
typealias EntryError = com.veles.caloriediary.domain.model.EntryError

val MACRO_SCHEMES: List<MacroScheme> = DOMAIN_MACRO_SCHEMES

fun forWeight(per100: Nutrients, grams: Double): Nutrients = domainForWeight(per100, grams)

fun sum(items: List<Nutrients>): Nutrients = domainSum(items)

fun dishPer100(items: List<DishIngredient>, finalGrams: Double): Nutrients? = domainDishPer100(items, finalGrams)

fun kcalFromMacros(protein: Double, fat: Double, carbs: Double): Double = domainKcalFromMacros(protein, fat, carbs)

fun autoFilledKcal(autoFilled: Boolean, current: String, protein: Double?, fat: Double?, carbs: Double?): Double? =
    domainAutoFilledKcal(autoFilled, current, protein, fat, carbs)

fun fontScaleForDelta(delta: Int): Float = domainFontScaleForDelta(delta)

fun draftTitle(name: String, index: Int): String = domainDraftTitle(name, index)

fun entryValidationError(name: String, grams: Double?): EntryError = domainEntryValidationError(name, grams)

fun productNutrients(kcal: Double, protein: Double?, fat: Double?, carbs: Double?): Nutrients =
    domainProductNutrients(kcal, protein, fat, carbs)

fun adjustTargets(targets: Targets, delta: Double): Targets = domainAdjustTargets(targets, delta)

fun gramsFromScheme(kcal: Double, scheme: MacroScheme): Targets = domainGramsFromScheme(kcal, scheme)

fun weightScheme(weightKg: Double, kcal: Double): Targets? = domainWeightScheme(weightKg, kcal)

fun groupByMeal(entries: List<Entry>, windowMs: Long = 3_600_000L): List<MealGroup> = domainGroupByMeal(entries, windowMs)

fun rankProductNames(names: List<String>, query: String): List<String> = domainRankProductNames(names, query)

fun rankProductNamesWithRecent(names: List<String>, query: String, recent: Collection<String>): List<String> =
    domainRankProductNamesWithRecent(names, query, recent)

fun rescale(values: Nutrients, fromGrams: Double, toGrams: Double): Nutrients? = domainRescale(values, fromGrams, toGrams)

fun parseGrams(raw: String): Double? = domainParseGrams(raw)

fun progress(current: Double, target: Double): Int = domainProgress(current, target)

fun progressPercent(current: Double, target: Double): Int = domainProgressPercent(current, target)

fun excess(current: Double, target: Double): Pair<Boolean, Double> = domainExcess(current, target)

fun dateKey(year: Int, month: Int, day: Int): String = domainDateKey(year, month, day)

fun weekDates(key: String): List<String> = domainWeekDates(key)
