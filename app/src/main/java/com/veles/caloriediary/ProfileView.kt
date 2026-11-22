package com.veles.caloriediary

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class ProfileView(private val activity: Activity) {

    val view: View = LayoutInflater.from(activity).inflate(R.layout.fragment_profile, null)
    private val db = Db(activity)
    private val kcalInput: EditText = view.findViewById(R.id.targetKcalInput)
    private val proteinInput: EditText = view.findViewById(R.id.targetProteinInput)
    private val fatInput: EditText = view.findViewById(R.id.targetFatInput)
    private val carbsInput: EditText = view.findViewById(R.id.targetCarbsInput)
    private val weightInput: EditText = view.findViewById(R.id.weightInput)
    private val schemeHint: TextView = view.findViewById(R.id.schemeHint)
    private val schemeButtons = listOf(
        view.findViewById<Button>(R.id.scheme0),
        view.findViewById<Button>(R.id.scheme1),
        view.findViewById<Button>(R.id.scheme2),
        view.findViewById<Button>(R.id.scheme3),
        view.findViewById<Button>(R.id.scheme4)
    )
    private var updating = false

    init {
        val targets = db.targets()
        updating = true
        kcalInput.setText(fmt(targets.kcal))
        proteinInput.setText(fmt(targets.protein))
        fatInput.setText(fmt(targets.fat))
        carbsInput.setText(fmt(targets.carbs))
        updating = false

        listOf(proteinInput, fatInput, carbsInput).forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = recalcCalories()
            })
        }
        kcalInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                val visible = readNumber(kcalInput) != null
                schemeHint.visibility = if (visible) View.VISIBLE else View.GONE
                schemeButtons.forEach { it.visibility = if (visible) View.VISIBLE else View.GONE }
            }
        })
        schemeButtons.forEachIndexed { index, button ->
            button.setOnClickListener { applyScheme(index) }
        }

        val prefs = activity.getSharedPreferences(MainActivity.PREFS, Activity.MODE_PRIVATE)
        val darkSwitch = view.findViewById<Switch>(R.id.darkThemeSwitch)
        darkSwitch.isChecked = prefs.getBoolean(MainActivity.KEY_DARK, false)
        darkSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(MainActivity.KEY_DARK, checked).apply()
            activity.recreate()
        }

        val savedWeight = prefs.getFloat(KEY_WEIGHT, 0f)
        if (savedWeight > 0f) weightInput.setText(fmt(savedWeight.toDouble()))

        view.findViewById<Button>(R.id.mySchemeButton).setOnClickListener {
            val weight = readNumber(weightInput)
            val kcal = readNumber(kcalInput)
            if (weight == null || kcal == null) {
                Toast.makeText(activity, R.string.fill_targets, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val targets = weightScheme(weight, kcal)
            if (targets == null) {
                Toast.makeText(activity, R.string.weight_mismatch, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updating = true
            kcalInput.setText(fmt(targets.kcal))
            proteinInput.setText(fmt(targets.protein))
            fatInput.setText(fmt(targets.fat))
            carbsInput.setText(fmt(targets.carbs))
            updating = false
            prefs.edit().putFloat(KEY_WEIGHT, weight.toFloat()).apply()
            db.saveTargets(targets)
            Toast.makeText(activity, R.string.targets_saved, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<Button>(R.id.saveTargetsButton).setOnClickListener {
            val p = readNumber(proteinInput)
            val f = readNumber(fatInput)
            val c = readNumber(carbsInput)
            var kcal = readNumber(kcalInput)
            if (kcal == null && p != null && f != null && c != null) {
                kcal = kcalFromMacros(p, f, c)
            }
            if (kcal == null || p == null || f == null || c == null) {
                Toast.makeText(activity, R.string.fill_targets, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            db.saveTargets(Targets(kcal, p, f, c))
            Toast.makeText(activity, R.string.targets_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun recalcCalories() {
        if (updating) return
        val p = readNumber(proteinInput) ?: return
        val f = readNumber(fatInput) ?: return
        val c = readNumber(carbsInput) ?: return
        updating = true
        kcalInput.setText(fmt(kcalFromMacros(p, f, c)))
        updating = false
    }

    private fun applyScheme(index: Int) {
        val kcal = readNumber(kcalInput)
        if (kcal == null) {
            Toast.makeText(activity, R.string.fill_targets, Toast.LENGTH_SHORT).show()
            return
        }
        val targets = gramsFromScheme(kcal, MACRO_SCHEMES[index])
        updating = true
        proteinInput.setText(fmt(targets.protein))
        fatInput.setText(fmt(targets.fat))
        carbsInput.setText(fmt(targets.carbs))
        updating = false
    }

    private fun fmt(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

    private fun readNumber(input: EditText): Double? {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) return null
        val value = raw.replace(',', '.').toDoubleOrNull() ?: return null
        return if (value > 0.0) value else null
    }

    companion object {
        private const val KEY_WEIGHT = "body_weight"
    }
}
