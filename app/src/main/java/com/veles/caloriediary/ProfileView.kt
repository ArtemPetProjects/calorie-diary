package com.veles.caloriediary

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
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
    private val schemeSpinner: Spinner = view.findViewById(R.id.schemeSpinner)
    private var updating = false
    private val profileSpinner: Spinner = view.findViewById(R.id.profileSpinner)
    private var profiles = db.profiles()

    private fun selectedProfile(): Profile? =
        profiles.getOrNull(profileSpinner.selectedItemPosition)

    private fun showNameDialog(title: String, initial: String, onSave: (String) -> Unit) {
        val field = EditText(activity).apply { setText(initial) }
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(field)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = field.text.toString().trim()
                if (name.isNotBlank()) {
                    onSave(name)
                    activity.recreate()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    init {
        profileSpinner.adapter = ArrayAdapter(
            activity, android.R.layout.simple_spinner_dropdown_item, profiles.map { it.name }
        )
        val activeIndex = profiles.indexOfFirst { it.id == db.activeProfileId() }.coerceAtLeast(0)
        updating = true
        profileSpinner.setSelection(activeIndex)
        updating = false

        val purposes = activity.resources.getStringArray(R.array.scheme_purposes)
        val schemeOptions = MACRO_SCHEMES.mapIndexed { index, scheme ->
            SchemeOption(
                activity.getString(R.string.scheme_title, scheme.name, purposes[index]),
                activity.getString(R.string.scheme_details, scheme.proteinPct, scheme.fatPct, scheme.carbsPct)
            )
        }
        schemeSpinner.adapter = SchemeAdapter(activity, schemeOptions)
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {}
        }

        view.findViewById<Button>(R.id.addProfileButton).setOnClickListener {
            showNameDialog(activity.getString(R.string.add_profile), "") { db.addProfile(it) }
        }
        view.findViewById<Button>(R.id.renameProfileButton).setOnClickListener {
            val profile = selectedProfile() ?: return@setOnClickListener
            showNameDialog(activity.getString(R.string.rename_profile), profile.name) {
                db.renameProfile(profile.id, it)
            }
        }
        view.findViewById<Button>(R.id.switchProfileButton).setOnClickListener {
            selectedProfile()?.let { profile ->
                db.setActiveProfile(profile.id)
                activity.recreate()
            }
        }
        view.findViewById<Button>(R.id.deleteProfileButton).setOnClickListener {
            val profile = selectedProfile() ?: return@setOnClickListener
            AlertDialog.Builder(activity)
                .setMessage(R.string.delete_profile_confirm)
                .setPositiveButton(R.string.delete) { _, _ ->
                    db.deleteProfile(profile.id)
                    activity.recreate()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

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
                schemeSpinner.visibility = if (visible) View.VISIBLE else View.GONE
            }
        })
        schemeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!updating && readNumber(kcalInput) != null) applyScheme(position)
            }
        }

        val prefs = activity.getSharedPreferences(MainActivity.PREFS, Activity.MODE_PRIVATE)
        val darkSwitch = view.findViewById<Switch>(R.id.darkThemeSwitch)
        darkSwitch.isChecked = prefs.getBoolean(MainActivity.KEY_DARK, false)
        darkSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(MainActivity.KEY_DARK, checked).apply()
            activity.recreate()
        }

        val savedWeight = db.bodyWeight()
        if (savedWeight > 0.0) weightInput.setText(fmt(savedWeight))

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
            db.saveBodyWeight(weight)
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
            readNumber(weightInput)?.let { weight ->
                val now = Calendar.getInstance()
                val today = dateKey(
                    now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
                    now.get(Calendar.DAY_OF_MONTH)
                )
                db.saveWeight(today, weight)
            }
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

    private class SchemeAdapter(
        activity: Activity,
        private val options: List<SchemeOption>
    ) : ArrayAdapter<SchemeOption>(activity, R.layout.item_scheme, options) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_scheme, parent, false)
            val option = getItem(position) ?: return row
            row.findViewById<TextView>(R.id.schemeTitle).text = option.title
            row.findViewById<TextView>(R.id.schemeDetails).text = option.details
            return row
        }
    }

    companion object {
        const val KEY_WEIGHT = "body_weight"
    }
}
