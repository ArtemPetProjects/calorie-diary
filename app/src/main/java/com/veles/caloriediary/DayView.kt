package com.veles.caloriediary

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Color
import android.text.Editable
import android.util.TypedValue
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
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
    private val adjustmentInput: EditText = view.findViewById(R.id.adjustmentInput)
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
        view.findViewById<Button>(R.id.saveAdjustmentButton).setOnClickListener {
            val raw = adjustmentInput.text.toString().trim().replace(',', '.')
            val delta = if (raw.isEmpty()) 0.0 else raw.toDoubleOrNull()
            if (delta == null) {
                toast(activity.getString(R.string.invalid_adjustment))
                return@setOnClickListener
            }
            db.saveAdjustment(selectedDate, delta)
            refresh()
        }
        continueDishButton.setOnClickListener { showDraftPicker() }
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
        val adjustment = db.adjustmentFor(selectedDate)
        adjustmentInput.setText(if (adjustment == 0.0) "" else fmt(adjustment))
        val targets = adjustTargets(db.targets(), adjustment)
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
            if (db.drafts().isNotEmpty()) View.VISIBLE else View.GONE

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
            header.textSize = 17f
            header.setTypeface(null, android.graphics.Typeface.BOLD)
            header.background = activity.getDrawable(R.drawable.meal_header_bg)
            header.setPadding(10, 8, 10, 8)
            val headerTa = activity.obtainStyledAttributes(intArrayOf(R.attr.progressKcal))
            header.setTextColor(headerTa.getColor(0, Color.parseColor("#EF6C00")))
            headerTa.recycle()
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
                row.findViewById<TextView>(R.id.entryText).text = entry.productName
                row.findViewById<TextView>(R.id.entryMacros).text = activity.getString(
                    R.string.entry_details,
                    fmt(entry.grams), fmt(entry.values.kcal),
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
        dialog.prepareForKeyboard()
        dialog.setOnShowListener {
            dialog.focusAndShowKeyboard(input, selectAll = true)
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
        dialog.prepareForKeyboard()
        dialog.setOnShowListener {
            dialog.focusAndShowKeyboard(input, selectAll = true)
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
        val percent = progressPercent(current, target)
        val (over, amount) = excess(current, target)
        val valueView = view.findViewById<TextView>(valueId)
        val colorAttr = when (valueId) {
            R.id.kcalValue -> R.attr.progressKcal
            R.id.proteinValue -> R.attr.progressProtein
            R.id.fatValue -> R.attr.progressFat
            else -> R.attr.progressCarbs
        }
        val ta = activity.obtainStyledAttributes(intArrayOf(colorAttr))
        valueView.setTextColor(ta.getColor(0, Color.GRAY))
        ta.recycle()
        valueView.text = if (over) {
            activity.getString(R.string.progress_excess, fmt(current), fmt(target), percent, fmt(amount))
        } else {
            activity.getString(R.string.progress_value, fmt(current), fmt(target), percent, fmt(amount))
        }
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
            button.textSize = 13f
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

    private fun showDraftPicker() {
        val drafts = db.drafts()
        if (drafts.isEmpty()) return
        var selected = 0
        val names = drafts.map { it.name }.toTypedArray()
        android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.choose_deferred_dish)
            .setSingleChoiceItems(names, 0) { _, which -> selected = which }
            .setPositiveButton(R.string.continue_dish) { _, _ ->
                openDishDialog(draftId = drafts[selected].id)
            }
            .setNeutralButton(R.string.delete) { _, _ ->
                val draft = drafts[selected]
                db.deleteDraft(draft.id)
                Toast.makeText(activity, R.string.draft_deleted, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun openDishDialog(dishId: Long = 0L, draftId: Long = 0L) {
        DishDialog(activity, db, dishId, draftId = draftId, onDraftSaved = { refresh() }) { id, grams, addToDay ->
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

        val products = db.products().toMutableList()
        val adapter = SuggestionAdapter(activity, products, db.recentProductIds())
        adapter.showInitial()
        productInput.setAdapter(adapter)
        productInput.threshold = 0
        val edge = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 4f, activity.resources.displayMetrics
        ).toInt()
        productInput.dropDownWidth =
            (activity.resources.displayMetrics.widthPixels - 2 * edge).coerceAtLeast(1)
        productInput.dropDownHeight = minOf(
            (320f * activity.resources.displayMetrics.density).toInt(),
            (activity.resources.displayMetrics.heightPixels * 0.45f).toInt()
        )
        productInput.dropDownVerticalOffset = 0
        productInput.setOnItemClickListener { _, _, position, _ ->
            val product = adapter.getItem(position) ?: return@setOnItemClickListener
            newFields.visibility = View.GONE
            productInfo.text = activity.getString(
                R.string.per_100_info,
                fmt(product.product.per100.kcal), fmt(product.product.per100.protein),
                fmt(product.product.per100.fat), fmt(product.product.per100.carbs)
            )
        }

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

        val newKcalInput = content.findViewById<EditText>(R.id.newKcalInput)
        var updatingNewKcal = false
        val newMacroWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingNewKcal) return
                val value = autoFilledKcal(
                    true, newKcalInput.text.toString(),
                    readNumber(content, R.id.newProteinInput),
                    readNumber(content, R.id.newFatInput),
                    readNumber(content, R.id.newCarbsInput)
                )
                if (value != null) {
                    updatingNewKcal = true
                    newKcalInput.setText(fmt(value))
                    updatingNewKcal = false
                }
            }
        }
        content.findViewById<EditText>(R.id.newProteinInput).addTextChangedListener(newMacroWatcher)
        content.findViewById<EditText>(R.id.newFatInput).addTextChangedListener(newMacroWatcher)
        content.findViewById<EditText>(R.id.newCarbsInput).addTextChangedListener(newMacroWatcher)

        val dialog = android.app.AlertDialog.Builder(activity)
            .setTitle(R.string.add_product)
            .setView(content)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.prepareForKeyboard()
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        dialog.window?.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.setOnShowListener {
            dialog.focusAndShowKeyboard(productInput, showDropdown = true)
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val name = ProductLabels.nameOf(productInput.text.toString())
                val grams = parseGrams(weightInput.text.toString())
                val error = entryValidationError(name, grams)
                if (error != EntryError.NONE) {
                    val message = if (error == EntryError.NAME) R.string.enter_product_name else R.string.enter_weight
                    toast(activity.getString(message))
                    return@setOnClickListener
                }
                if (grams == null) return@setOnClickListener

                var product = products.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (product == null) {
                    val kcal = readNumber(content, R.id.newKcalInput)
                    if (kcal == null) {
                        toast(activity.getString(R.string.enter_kcal))
                        return@setOnClickListener
                    }
                    val id = db.saveProduct(
                        Product(
                            0, name,
                            productNutrients(
                                kcal,
                                readNumber(content, R.id.newProteinInput),
                                readNumber(content, R.id.newFatInput),
                                readNumber(content, R.id.newCarbsInput)
                            )
                        )
                    )
                    if (id < 0) {
                        toast(activity.getString(R.string.duplicate_product))
                        return@setOnClickListener
                    }
                    product = db.products().first { it.id == id }
                }

                db.addEntry(Entry(0, selectedDate, product.id, product.name, grams, forWeight(product.per100, grams)))
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
