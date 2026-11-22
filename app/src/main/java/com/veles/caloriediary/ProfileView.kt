package com.veles.caloriediary

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import java.util.Locale

class ProfileView(private val activity: Activity) {

    val view: View = LayoutInflater.from(activity).inflate(R.layout.fragment_profile, null)
    private val db = Db(activity)
    private val kcalInput: EditText = view.findViewById(R.id.targetKcalInput)
    private val proteinInput: EditText = view.findViewById(R.id.targetProteinInput)
    private val fatInput: EditText = view.findViewById(R.id.targetFatInput)
    private val carbsInput: EditText = view.findViewById(R.id.targetCarbsInput)

    init {
        val targets = db.targets()
        kcalInput.setText(fmt(targets.kcal))
        proteinInput.setText(fmt(targets.protein))
        fatInput.setText(fmt(targets.fat))
        carbsInput.setText(fmt(targets.carbs))

        val prefs = activity.getSharedPreferences(MainActivity.PREFS, Activity.MODE_PRIVATE)
        val darkSwitch = view.findViewById<Switch>(R.id.darkThemeSwitch)
        darkSwitch.isChecked = prefs.getBoolean(MainActivity.KEY_DARK, false)
        darkSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(MainActivity.KEY_DARK, checked).apply()
            activity.recreate()
        }

        view.findViewById<Button>(R.id.saveTargetsButton).setOnClickListener {
            val kcal = readNumber(kcalInput)
            val protein = readNumber(proteinInput)
            val fat = readNumber(fatInput)
            val carbs = readNumber(carbsInput)
            if (kcal == null || protein == null || fat == null || carbs == null) {
                Toast.makeText(activity, R.string.fill_targets, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            db.saveTargets(Targets(kcal, protein, fat, carbs))
            Toast.makeText(activity, R.string.targets_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun fmt(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

    private fun readNumber(input: EditText): Double? {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) return null
        val value = raw.replace(',', '.').toDoubleOrNull() ?: return null
        return if (value > 0.0) value else null
    }
}
