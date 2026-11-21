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
import java.util.Calendar
import java.util.Locale

class DayView(private val activity: Activity, initialDate: String? = null) {

    val view: View = LayoutInflater.from(activity).inflate(R.layout.fragment_day, null)
    private val db = Db(activity)
    private val monthLabel: TextView = view.findViewById(R.id.monthLabel)
    private val dayStrip: LinearLayout = view.findViewById(R.id.dayStrip)
    private val entryList: LinearLayout = view.findViewById(R.id.entryList)
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

        entryList.removeAllViews()
        for (entry in entries) {
            val row = LayoutInflater.from(activity).inflate(R.layout.item_entry, entryList, false)
            row.findViewById<TextView>(R.id.entryText).text =
                "${entry.productName} • ${fmt(entry.grams)} г • ${fmt(entry.values.kcal)} ккал"
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
            entryList.addView(row)
        }
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

    private fun showAddDialog() {
        val content = LayoutInflater.from(activity).inflate(R.layout.dialog_entry, null)
        val productInput = content.findViewById<AutoCompleteTextView>(R.id.productInput)
        val weightInput = content.findViewById<EditText>(R.id.weightInput)
        val preview = content.findViewById<TextView>(R.id.previewText)
        val newFields = content.findViewById<LinearLayout>(R.id.newProductFields)

        val products = db.products()
        productInput.setAdapter(
            ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, products.map { it.name })
        )

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val name = productInput.text.toString().trim()
                val product = products.firstOrNull { it.name.equals(name, ignoreCase = true) }
                newFields.visibility =
                    if (name.isNotBlank() && product == null) View.VISIBLE else View.GONE
                val grams = parseGrams(weightInput.text.toString())
                preview.text = if (product != null && grams != null) {
                    val n = forWeight(product.per100, grams)
                    "${fmt(n.kcal)} ккал • Б ${fmt(n.protein)} / Ж ${fmt(n.fat)} / У ${fmt(n.carbs)}"
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
                val name = productInput.text.toString().trim()
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
