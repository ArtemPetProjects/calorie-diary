package com.veles.caloriediary

import java.util.Calendar
import kotlin.math.abs
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

// Итог блюда делится на вес готового блюда, который отличается от суммы ингредиентов.
fun dishPer100(items: List<DishIngredient>, finalGrams: Double): Nutrients? {
    if (items.isEmpty() || finalGrams <= 0.0) return null
    val total = sum(items.map { forWeight(it.per100, it.grams) })
    val k = 100.0 / finalGrams
    return Nutrients(
        kcal = round1(total.kcal * k),
        protein = round1(total.protein * k),
        fat = round1(total.fat * k),
        carbs = round1(total.carbs * k)
    )
}

data class MacroScheme(val name: String, val proteinPct: Int, val fatPct: Int, val carbsPct: Int)

val MACRO_SCHEMES = listOf(
    MacroScheme("ВОЗ — сбалансированная", 15, 25, 60),
    MacroScheme("IOM — средняя AMDR", 20, 30, 50),
    MacroScheme("DASH", 18, 27, 55),
    MacroScheme("Средиземноморская", 20, 35, 45),
    MacroScheme("Зона", 30, 30, 40),
    MacroScheme("Аткинс", 30, 60, 10),
    MacroScheme("Низкожировая", 20, 15, 65),
    MacroScheme("Высокобелковая", 35, 25, 40),
    MacroScheme("Низкоуглеводная", 25, 45, 30),
    MacroScheme("Кето", 10, 70, 20)
)

fun kcalFromMacros(protein: Double, fat: Double, carbs: Double): Double =
    round1(protein * 4.0 + fat * 9.0 + carbs * 4.0)

// Автоподстановка калорий из БЖУ: ручное значение пользователя не перетирается.
fun autoFilledKcal(
    autoFilled: Boolean,
    current: String,
    protein: Double?,
    fat: Double?,
    carbs: Double?
): Double? {
    if (protein == null || fat == null || carbs == null) return null
    if (!autoFilled && current.isNotBlank()) return null
    // Нулевой БЖУ при ручной калорийности — алкоголь; не обнуляем его при вводе.
    if (protein == 0.0 && fat == 0.0 && carbs == 0.0 && current.isNotBlank()) return null
    return kcalFromMacros(protein, fat, carbs)
}

fun fontScaleForDelta(delta: Int): Float = 1f + delta.coerceIn(-5, 5) * 0.06f

fun draftTitle(name: String, index: Int): String =
    name.ifBlank { "Блюдо без названия $index" }

enum class EntryError { NONE, NAME, WEIGHT }

fun entryValidationError(name: String, grams: Double?): EntryError = when {
    name.isBlank() -> EntryError.NAME
    grams == null -> EntryError.WEIGHT
    else -> EntryError.NONE
}

// Пустые поля БЖУ сохраняются как нули, но ручная калорийность сохраняется.
fun productNutrients(kcal: Double, protein: Double?, fat: Double?, carbs: Double?): Nutrients =
    Nutrients(kcal, protein ?: 0.0, fat ?: 0.0, carbs ?: 0.0)

// Профицит/дефицит меняет цель по калориям и пропорционально пересчитывает БЖУ.
fun adjustTargets(targets: Targets, delta: Double): Targets {
    if (targets.kcal <= 0.0) return targets
    val newKcal = targets.kcal + delta
    if (newKcal <= 0.0) return targets
    val k = newKcal / targets.kcal
    return Targets(
        kcal = newKcal,
        protein = round1(targets.protein * k),
        fat = round1(targets.fat * k),
        carbs = round1(targets.carbs * k)
    )
}

fun gramsFromScheme(kcal: Double, scheme: MacroScheme): Targets = Targets(
    kcal = kcal,
    protein = round1(kcal * scheme.proteinPct / 100.0 / 4.0),
    fat = round1(kcal * scheme.fatPct / 100.0 / 9.0),
    carbs = round1(kcal * scheme.carbsPct / 100.0 / 4.0)
)

// Своя схема: белки = вес × 2, жиры = вес / 2, остаток калорий — углеводы.
fun weightScheme(weightKg: Double, kcal: Double): Targets? {
    if (weightKg <= 0.0 || kcal <= 0.0) return null
    val protein = round1(weightKg * 2.0)
    val fat = round1(weightKg / 2.0)
    val carbs = round((kcal - protein * 4.0 - fat * 9.0) / 4.0)
    if (carbs < 0.0) return null
    return Targets(round1(protein * 4.0 + fat * 9.0 + carbs * 4.0), protein, fat, carbs)
}

data class MealGroup(val startMs: Long, val entries: List<Entry>)

fun groupByMeal(entries: List<Entry>, windowMs: Long = 3_600_000L): List<MealGroup> {
    if (entries.isEmpty()) return emptyList()
    val result = mutableListOf<MealGroup>()
    val sorted = entries.sortedBy { it.createdAt }
    var current = mutableListOf<Entry>()
    var start = 0L
    for (entry in sorted) {
        if (current.isEmpty()) {
            start = entry.createdAt
            current.add(entry)
        } else if (entry.createdAt - start <= windowMs) {
            current.add(entry)
        } else {
            result.add(MealGroup(start, current))
            start = entry.createdAt
            current = mutableListOf(entry)
        }
    }
    if (current.isNotEmpty()) result.add(MealGroup(start, current))
    return result
}

// Сначала точные начала совпадений, затем остальные.
fun rankProductNames(names: List<String>, query: String): List<String> {
    if (query.isBlank()) return names
    val q = query.trim().lowercase()
    val lowered = names.map { it.lowercase() }
    return names.indices.filter { lowered[it].startsWith(q) }.map { names[it] } +
        names.indices.filter { !lowered[it].startsWith(q) && lowered[it].contains(q) }.map { names[it] }
}

// Недавние продукты идут первыми строго в порядке использования — даже при пустом запросе.
fun rankProductNamesWithRecent(
    names: List<String>,
    query: String,
    recent: Collection<String>
): List<String> {
    val ranked = rankProductNames(names, query)
    if (recent.isEmpty()) return ranked
    val usageOrder = LinkedHashMap<String, Int>()
    recent.forEachIndexed { index, name -> usageOrder.putIfAbsent(name.lowercase(), index) }
    val q = query.trim().lowercase()
    val recentNames = mutableListOf<String>()
    val otherStarts = mutableListOf<String>()
    val otherContains = mutableListOf<String>()
    for (name in ranked) {
        when {
            usageOrder.containsKey(name.lowercase()) -> recentNames.add(name)
            q.isBlank() || name.lowercase().startsWith(q) -> otherStarts.add(name)
            else -> otherContains.add(name)
        }
    }
    return recentNames.sortedBy { usageOrder[it.lowercase()] } + otherStarts + otherContains
}

fun rescale(values: Nutrients, fromGrams: Double, toGrams: Double): Nutrients? {
    if (fromGrams <= 0.0 || toGrams <= 0.0) return null
    val k = toGrams / fromGrams
    return Nutrients(
        kcal = round1(values.kcal * k),
        protein = round1(values.protein * k),
        fat = round1(values.fat * k),
        carbs = round1(values.carbs * k)
    )
}

fun parseGrams(raw: String): Double? {
    val value = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return if (value > 0.0 && value <= 10_000.0) value else null
}

fun progress(current: Double, target: Double): Int {
    if (target <= 0.0) return 0
    return ((current / target) * 100.0).toInt().coerceIn(0, 100)
}

fun progressPercent(current: Double, target: Double): Int {
    if (target <= 0.0) return 0
    return ((current / target) * 100.0).toInt().coerceAtLeast(0)
}

// Признак превышения нормы и величина отклонения по модулю.
fun excess(current: Double, target: Double): Pair<Boolean, Double> =
    (current > target) to abs(current - target)

fun dateKey(year: Int, month: Int, day: Int): String =
    "%04d-%02d-%02d".format(year, month, day)

// Понедельник — воскресенье недели, содержащей дату key.
fun weekDates(key: String): List<String> {
    val parts = key.split("-")
    val cal = Calendar.getInstance().apply {
        clear()
        set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        firstDayOfWeek = Calendar.MONDAY
    }
    val offset = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
    cal.add(Calendar.DAY_OF_MONTH, -offset)
    return (0..6).map {
        val result = dateKey(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
        result
    }
}

private fun round1(value: Double): Double = round(value * 10.0) / 10.0
