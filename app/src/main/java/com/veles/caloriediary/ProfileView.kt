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
    private val chooseSchemeButton: Button = view.findViewById(R.id.chooseSchemeButton)
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

        updateSchemeButton()
        profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val profile = profiles.getOrNull(position) ?: return
                if (updating || profile.id == db.activeProfileId()) return
                db.setActiveProfile(profile.id)
                activity.recreate()
            }
        }

        view.findViewById<Button>(R.id.addProfileButton).setOnClickListener {
            showNameDialog(activity.getString(R.string.add_profile), "") { name ->
                val id = db.addProfile(name)
                if (id > 0) db.setActiveProfile(id)
            }
        }
        view.findViewById<Button>(R.id.renameProfileButton).setOnClickListener {
            val profile = selectedProfile() ?: return@setOnClickListener
            showNameDialog(activity.getString(R.string.rename_profile), profile.name) {
                db.renameProfile(profile.id, it)
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
        kcalInput.setText(if (targets.kcal > 0) fmt(targets.kcal) else "")
        proteinInput.setText(if (targets.protein > 0) fmt(targets.protein) else "")
        fatInput.setText(if (targets.fat > 0) fmt(targets.fat) else "")
        carbsInput.setText(if (targets.carbs > 0) fmt(targets.carbs) else "")
        updating = false
        listOf(proteinInput, fatInput, carbsInput).forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) = recalcCalories()
            })
        }
        chooseSchemeButton.setOnClickListener { showSchemeDialog() }

        val prefs = activity.getSharedPreferences(MainActivity.PREFS, Activity.MODE_PRIVATE)
        val profileId = db.activeProfileId()
        val darkSwitch = view.findViewById<Switch>(R.id.darkThemeSwitch)
        darkSwitch.isChecked = prefs.getBoolean(
            MainActivity.themeKey(profileId), prefs.getBoolean(MainActivity.KEY_DARK, false)
        )
        darkSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(MainActivity.themeKey(profileId), checked).apply()
            activity.recreate()
        }

        val fontValue = view.findViewById<TextView>(R.id.fontValue)
        var fontDelta = prefs.getInt(
            MainActivity.fontKey(profileId), prefs.getInt(MainActivity.KEY_FONT_DELTA, 1)
        ).coerceIn(-5, 5)
        fontValue.text = activity.getString(R.string.font_value, fontDelta)
        view.findViewById<Button>(R.id.fontMinusButton).setOnClickListener {
            fontDelta = (fontDelta - 1).coerceIn(-5, 5)
            prefs.edit().putInt(MainActivity.fontKey(profileId), fontDelta).apply()
            activity.recreate()
        }
        view.findViewById<Button>(R.id.fontPlusButton).setOnClickListener {
            fontDelta = (fontDelta + 1).coerceIn(-5, 5)
            prefs.edit().putInt(MainActivity.fontKey(profileId), fontDelta).apply()
            activity.recreate()
        }

        val savedWeight = db.bodyWeight()
        if (savedWeight > 0.0) weightInput.setText(fmt(savedWeight))

        var pendingSave: Runnable? = null
        val autosaveWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updating) return
                pendingSave?.let { view.removeCallbacks(it) }
                pendingSave = Runnable {
                    val p = readNumber(proteinInput) ?: return@Runnable
                    val f = readNumber(fatInput) ?: return@Runnable
                    val c = readNumber(carbsInput) ?: return@Runnable
                    val kcal = readNumber(kcalInput) ?: kcalFromMacros(p, f, c)
                    db.saveTargets(Targets(kcal, p, f, c))
                    readNumber(weightInput)?.let { weight ->
                        db.saveBodyWeight(weight)
                        val now = Calendar.getInstance()
                        val today = dateKey(
                            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
                            now.get(Calendar.DAY_OF_MONTH)
                        )
                        db.saveWeight(today, weight)
                    }
                }
                view.postDelayed(pendingSave, 600)
            }
        }
        listOf(kcalInput, proteinInput, fatInput, carbsInput, weightInput)
            .forEach { it.addTextChangedListener(autosaveWatcher) }
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

    private fun schemeEntries(): List<SchemeEntry> {
        val purposes = activity.resources.getStringArray(R.array.scheme_purposes)
        val entries = mutableListOf(
            SchemeEntry(
                Db.SCHEME_OWN,
                activity.getString(R.string.scheme_own_title),
                activity.getString(R.string.scheme_own_purpose),
                activity.getString(R.string.scheme_own_details)
            )
        )
        MACRO_SCHEMES.forEachIndexed { index, scheme ->
            entries.add(
                SchemeEntry(
                    "builtin:$index",
                    scheme.name,
                    purposes.getOrElse(index) { "" },
                    activity.getString(
                        R.string.scheme_details, scheme.proteinPct, scheme.fatPct, scheme.carbsPct
                    )
                )
            )
        }
        db.customSchemes().forEach { scheme ->
            entries.add(
                SchemeEntry(
                    "custom:${scheme.id}",
                    scheme.name,
                    scheme.purpose,
                    activity.getString(
                        R.string.scheme_ratio_short,
                        scheme.proteinPct, scheme.fatPct, scheme.carbsPct
                    )
                )
            )
        }
        return entries
    }

    private fun updateSchemeButton() {
        val current = db.schemeForProfile()
        val title = schemeEntries().firstOrNull { it.key == current }?.title
            ?: activity.getString(R.string.scheme_own_title)
        chooseSchemeButton.text = activity.getString(R.string.scheme_current, title)
    }

    private fun applyTargets(targets: Targets) {
        updating = true
        kcalInput.setText(fmt(targets.kcal))
        proteinInput.setText(fmt(targets.protein))
        fatInput.setText(fmt(targets.fat))
        carbsInput.setText(fmt(targets.carbs))
        updating = false
        db.saveTargets(targets)
    }

    private fun applySchemeEntry(entry: SchemeEntry) {
        try {
            when {
                entry.key == Db.SCHEME_OWN -> {
                    val weight = readNumber(weightInput)
                    val kcal = readNumber(kcalInput)
                    if (weight == null || kcal == null) {
                        Toast.makeText(activity, R.string.fill_targets, Toast.LENGTH_SHORT).show()
                        return
                    }
                    val targets = weightScheme(weight, kcal)
                    if (targets == null) {
                        Toast.makeText(activity, R.string.weight_mismatch, Toast.LENGTH_SHORT).show()
                        return
                    }
                    db.saveBodyWeight(weight)
                    applyTargets(targets)
                }
                entry.key.startsWith("builtin:") -> {
                    val index = entry.key.removePrefix("builtin:").toIntOrNull() ?: return
                    if (index in MACRO_SCHEMES.indices) applyScheme(index)
                }
                entry.key.startsWith("custom:") -> {
                    val id = entry.key.removePrefix("custom:").toLongOrNull() ?: return
                    val scheme = db.customSchemes().firstOrNull { it.id == id } ?: return
                    val kcal = readNumber(kcalInput) ?: return
                    applyTargets(
                        gramsFromScheme(
                            kcal,
                            MacroScheme(scheme.name, scheme.proteinPct, scheme.fatPct, scheme.carbsPct)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Toast.makeText(activity, R.string.scheme_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSchemeDialog() {
        val entries = schemeEntries() + SchemeEntry(
            "__add", activity.getString(R.string.add_custom_scheme), "", ""
        )
        val current = db.schemeForProfile()
        val checked = entries.indexOfFirst { it.key == current }.coerceAtLeast(0)
        AlertDialog.Builder(activity)
            .setTitle(R.string.choose_scheme)
            .setSingleChoiceItems(SchemeChoiceAdapter(activity, entries), checked) { dialog, which ->
                dialog.dismiss()
                val entry = entries.getOrNull(which) ?: return@setSingleChoiceItems
                if (entry.key == "__add") {
                    showCreateSchemeDialog()
                    return@setSingleChoiceItems
                }
                db.saveScheme(entry.key)
                applySchemeEntry(entry)
                updateSchemeButton()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCreateSchemeDialog() {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_custom_scheme, null)
        val nameField = view.findViewById<EditText>(R.id.customSchemeName)
        val purposeField = view.findViewById<EditText>(R.id.customSchemePurpose)
        val proteinField = view.findViewById<EditText>(R.id.customSchemeProtein)
        val fatField = view.findViewById<EditText>(R.id.customSchemeFat)
        val carbsField = view.findViewById<EditText>(R.id.customSchemeCarbs)
        AlertDialog.Builder(activity)
            .setTitle(R.string.add_custom_scheme)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
            .apply {
                prepareForKeyboard()
                setOnShowListener {
                    focusAndShowKeyboard(nameField)
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = nameField.text.toString().trim()
                        val p = proteinField.text.toString().trim().toIntOrNull()
                        val f = fatField.text.toString().trim().toIntOrNull()
                        val c = carbsField.text.toString().trim().toIntOrNull()
                        if (name.isBlank() || p == null || f == null || c == null || p + f + c != 100) {
                            Toast.makeText(activity, R.string.invalid_scheme_percent, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val id = db.addCustomScheme(name, purposeField.text.toString().trim(), p, f, c)
                        if (id > 0) {
                            val key = "custom:$id"
                            db.saveScheme(key)
                            applySchemeEntry(SchemeEntry(key, name, "", ""))
                            updateSchemeButton()
                            dismiss()
                        }
                    }
                }
                show()
            }
    }

    private class SchemeChoiceAdapter(
        activity: Activity,
        private val entries: List<SchemeEntry>
    ) : ArrayAdapter<SchemeEntry>(activity, R.layout.item_scheme_choice, entries) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_scheme_choice, parent, false)
            val entry = getItem(position) ?: return row
            row.findViewById<TextView>(R.id.schemeChoiceTitle).text = entry.title
            row.findViewById<TextView>(R.id.schemeChoicePurpose).text = entry.details
            row.findViewById<TextView>(R.id.schemeChoiceRatio).text = entry.ratio
            return row
        }
    }

    companion object {
        const val KEY_WEIGHT = "body_weight"
    }
}
