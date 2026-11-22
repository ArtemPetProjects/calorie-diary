package com.veles.caloriediary

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DayView(private val activity: Activity, initialDate: String? = null) {

    val view: View = LayoutInflater.from(activity).inflate(R.layout.fragment_day, null)
    private val db = Db(activity)
    private val monthLabel: TextView = view.findViewById(R.id.monthLabel)
    private val dayStrip: LinearLayout = view.findViewById(R.id.dayStrip)
    private val entryList: LinearLayout = view.findViewById(R.id.entryList)
    private val weightButton: Button = view.findViewById(R.id.weightButton)
    private val continueDishButton: Button = view.findViewById(R.id.continueDishButton)
    private val collapsedMeals = mutableMapOf<Long, Boolean>()
    private val months = arrayOf(
        "январь", "февраль", "март", "апрель", "май", "июнь",
        "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"
    )
    private lateinit var selectedDate: String

    init {
        val now = Calendar.getInstance()
        selectedDate = initialDate ?: dateKey(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )
        view.findViewById<Button>(R.id.prevWeek).setOnClickListener { shiftWeek(-7) }
        view.findViewById<Button>(R.id.nextWeek).setOnClickListener { shiftWeek(7) }
        view.findViewById<Button>(R.id.addEntryButton).setOnClickListener { showAddDialog() }
        view.findViewById<Button>(R.id.addDishButton).setOnClickListener { openDishDialog() }
        continueDishButton.setOnClickListener { openDishDialog(draft = true) }
        continueDishButton.setOnLongClickListener {
            db.clearDraft()
            Toast.makeText(activity, R.string.draft_deleted, Toast.LENGTH_SHORT).show()
            refresh()
            true
        }
        refresh()
    }

    private fun shiftWeek(days: Int) {
        val cal = calendarOf(selectedDate)
        cal.add(Calendar.DAY_OF_MONTH, days)
        selectedDate = dateKey(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        refresh()
    }

    private fun calendarOf(key: String): Calendar {
        val parts = key.split("-")
        return Calendar.getInstance().apply {
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
    }

    val selectedDateKey: String get() = selectedDate

    fun restoreDate(key: String) {
        selectedDate = key
        refresh()
    }

    fun refresh() {
        val cal = calendarOf(selectedDate)
        monthLabel.text = "${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
        renderWeek()

        val entries = db.entries(selectedDate)
        val totals = sum(entries.map { it.values })
        val targets = db.targets()
        bindBar(R.id.kcalBar, R.id.kcalValue, totals.kcal, targets.kcal)
        bindBar(R.id.proteinBar, R.id.proteinValue, totals.protein, targets.protein)
        bindBar(R.id.fatBar, R.id.fatValue, totals.fat, targets.fat)
        bindBar(R.id.carbsBar, R.id.carbsValue, totals.carbs, targets.carbs)

        val weight = db.weightFor(selectedDate)
        weightButton.text = if (weight != null) {
            activity.getString(R.string.weight_today, fmt(weight))
        } else {
            activity.getString(R.string.weight_unknown)
        }
        weightButton.setOnClickListener { openWeightDialog() }
        continueDishButton.visibility =
            if (db.hasDraft()) View.VISIBLE else View.GONE

        entryList.removeAllViews()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        for (group in groupByMeal(entries)) {
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = activity.getDrawable(R.drawable.meal_group_border)
                setPadding(8, 4, 8, 4)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 0) }
            }
            val collapsed = collapsedMeals[group.startMs] ?: false
            val baseText = if (group.startMs <= 0L) {
                activity.getString(R.string.meal_unknown)
            } else {
                val kcal = sum(group.entries.map { it.values }).kcal
                activity.getString(R.string.meal_time, timeFormat.format(Date(group.startMs)), fmt(kcal))
            }
            val header = TextView(activity)
            header.text = (if (collapsed) "▶ " else "▼ ") + baseText
            header.textSize = 15f
            val items = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            items.visibility = if (collapsed) View.GONE else View.VISIBLE
            header.setOnClickListener {
                val next = !(collapsedMeals[group.startMs] ?: false)
                collapsedMeals[group.startMs] = next
                items.visibility = if (next) View.GONE else View.VISIBLE
                header.text = (if (next) "▶ " else "▼ ") + baseText
            }
            box.addView(header)
            box.addView(items)

            for (entry in group.entries) {
                val row = LayoutInflater.from(activity)
                    .inflate(R.layout.item_entry, entryList, false)
                row.findViewById<TextView>(R.id.entryText).text =
                    "${entry.productName} • ${fmt(entry.grams)} г • ${fmt(entry.values.kcal)} ккал"
                row.findViewById<TextView>(R.id.entryMacros).text = activity.getString(
                    R.string.entry_macros,
                    fmt(entry.values.protein), fmt(entry.values.fat), fmt(entry.values.carbs)
                )
                row.setOnClickListener { openEditWeightDialog(entry) }
                row.findViewById<Button>(R.id.deleteEntryButton).setOnClickListener {
                    android.app.AlertDialog.Builder(activity)
                        .setTitle(R.string.confirm_delete)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            db.deleteEntry(entry.id)
                            refresh()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
                items.addView(row)
            }
            entryList.addView(box)
        }
    }

    private fun openWeightDialog() {
        val input = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = activity.getString(R.string.weight_dialog)
            db.weightFor(selectedDate)?.let {
                setText(fmt(it))
                setSelection(text.length)
            }
        }
        val dialog = android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.weight_dialog)
            .setView(input)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val raw = input.text.toString().trim()
                if (raw.isEmpty()) {
                    db.deleteWeight(selectedDate)
                    dialog.dismiss()
                    refresh()
                    return@setOnClickListener
                }
                val kg = parseGrams(raw)
                if (kg == null || kg > 500.0) {
                    Toast.makeText(activity, R.string.enter_weight, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                db.saveWeight(selectedDate, kg)
                activity.getSharedPreferences(MainActivity.PREFS, Activity.MODE_PRIVATE)
                    .edit().putFloat(ProfileView.KEY_WEIGHT, kg.toFloat()).apply()
                dialog.dismiss()
                refresh()
            }
        }
        dialog.show()
    }

    private fun openEditWeightDialog(entry: Entry) {
        val input = EditText(activity).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(fmt(entry.grams))
            hint = activity.getString(R.string.grams)
        }
        val dialog = android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.edit_weight)
            .setView(input)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val grams = parseGrams(input.text.toString())
                val scaled = if (grams != null) rescale(entry.values, entry.grams, grams) else null
                if (grams == null || scaled == null) {
                    Toast.makeText(activity, R.string.enter_weight, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                db.updateEntry(entry.id, grams, scaled)
                dialog.dismiss()
                refresh()
            }
        }
        dialog.show()
    }

    private fun bindBar(barId: Int, valueId: Int, current: Double, target: Double) {
        view.findViewById<ProgressBar>(barId).progress = progress(current, target)
        view.findViewById<TextView>(valueId).text = "${fmt(current)} / ${fmt(target)}"
    }

    private fun renderWeek() {
        dayStrip.removeAllViews()
        weekDates(selectedDate).forEachIndexed { i, key ->
            val parts = key.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].toInt()
            val button = Button(activity)
            button.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            button.minWidth = 0
            button.textSize = 12f
            button.text = "${weekDayName(i)}\n$day"
            button.setBackgroundColor(
                if (key == selectedDate) Color.rgb(180, 220, 180) else Color.TRANSPARENT
            )
            button.setOnClickListener {
                selectedDate = key
                refresh()
            }
            button.setOnLongClickListener {
                DatePickerDialog(
                    activity,
                    { _, y, m, d ->
                        selectedDate = dateKey(y, m + 1, d)
                        refresh()
                    },
                    year, month - 1, day
                ).show()
                true
            }
            dayStrip.addView(button)
        }
    }

    private fun weekDayName(index: Int) = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")[index]

    private fun fmt(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

    fun openDishDialog(dishId: Long = 0L, draft: Boolean = false) {
        DishDialog(activity, db, dishId, draft = draft, onDraftSaved = { refresh() }) { id, grams, addToDay ->
            if (addToDay) {
                db.products().firstOrNull { it.id == id }?.let { dish ->
                    db.addEntry(
                        Entry(0, selectedDate, id, dish.name, grams, forWeight(dish.per100, grams))
                    )
                    refresh()
                }
            }
        }.show()
    }

    private fun showAddDialog() {
        val content = LayoutInflater.from(activity).inflate(R.layout.dialog_entry, null)
        val productInput = content.findViewById<AutoCompleteTextView>(R.id.productInput)
        val weightInput = content.findViewById<EditText>(R.id.weightInput)
        val preview = content.findViewById<TextView>(R.id.previewText)
        val productInfo = content.findViewById<TextView>(R.id.selectedProductInfo)
        val newFields = content.findViewById<LinearLayout>(R.id.newProductFields)

        val products = db.products()
        productInput.setAdapter(SuggestionAdapter(activity, products, db.recentProductIds()))

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val name = ProductLabels.nameOf(productInput.text.toString())
                val product = products.firstOrNull { it.name.equals(name, ignoreCase = true) }
                newFields.visibility =
                    if (name.isNotBlank() && product == null) View.VISIBLE else View.GONE
                productInfo.text = if (product != null) {
                    val n = product.per100
                    activity.getString(
                        R.string.per_100_info, fmt(n.kcal), fmt(n.protein), fmt(n.fat), fmt(n.carbs)
                    )
                } else {
                    ""
                }
                val grams = parseGrams(weightInput.text.toString())
                preview.text = if (product != null && grams != null) {
                    val n = forWeight(product.per100, grams)
                    activity.getString(
                        R.string.portion_preview, fmt(n.kcal), fmt(n.protein), fmt(n.fat), fmt(n.carbs)
                    )
                } else {
                    ""
                }
            }
        }
        productInput.addTextChangedListener(watcher)
        weightInput.addTextChangedListener(watcher)

        val dialog = android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.add_product)
            .setView(content)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val name = ProductLabels.nameOf(productInput.text.toString())
                val grams = parseGrams(weightInput.text.toString())
                if (name.isBlank() || grams == null) {
                    toast(activity.getString(R.string.enter_weight))
                    return@setOnClickListener
                }

                var product = products.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (product == null) {
                    val kcal = readNumber(content, R.id.newKcalInput)
                    val protein = readNumber(content, R.id.newProteinInput)
                    val fat = readNumber(content, R.id.newFatInput)
                    val carbs = readNumber(content, R.id.newCarbsInput)
                    if (kcal == null || protein == null || fat == null || carbs == null) {
                        toast(activity.getString(R.string.fill_targets))
                        return@setOnClickListener
                    }
                    val id = db.saveProduct(Product(0, name, Nutrients(kcal, protein, fat, carbs)))
                    if (id < 0) {
                        toast(activity.getString(R.string.duplicate_product))
                        return@setOnClickListener
                    }
                    product = db.products().first { it.id == id }
                }

                val values = forWeight(product.per100, grams)
                db.addEntry(Entry(0, selectedDate, product.id, product.name, grams, values))
                dialog.dismiss()
                refresh()
            }
        }
        dialog.show()
    }

    private fun readNumber(content: View, id: Int): Double? {
        val raw = content.findViewById<EditText>(id).text.toString().trim()
        if (raw.isBlank()) return null
        val value = raw.replace(',', '.').toDoubleOrNull() ?: return null
        return if (value >= 0.0) value else null
    }

    private fun toast(text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
    }
}
