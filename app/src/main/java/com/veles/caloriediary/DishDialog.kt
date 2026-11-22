package com.veles.caloriediary

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class DishDialog(
    private val activity: Activity,
    private val db: Db,
    private val dishId: Long = 0L,
    private val showAddToDay: Boolean = true,
    private val draft: Boolean = false,
    private val onDraftSaved: () -> Unit = {},
    private val onSaved: (Long, Double, Boolean) -> Unit
) {

    private val products = db.products()
    private val adapter = SuggestionAdapter(activity, products, db.recentProductIds(), largerText = true)
    private val content = LayoutInflater.from(activity).inflate(R.layout.dialog_dish, null)
    private val nameInput: EditText = content.findViewById(R.id.dishNameInput)
    private val ingredientList: LinearLayout = content.findViewById(R.id.ingredientList)
    private val finalWeightInput: EditText = content.findViewById(R.id.finalWeightInput)
    private val preview: TextView = content.findViewById(R.id.dishPreview)
    private val addToDayCheck: CheckBox = content.findViewById(R.id.addToDayCheck)
    private val rows = mutableListOf<IngredientRow>()

    fun show() {
        addToDayCheck.visibility = if (showAddToDay) View.VISIBLE else View.GONE
        if (draft) {
            for (item in db.draftItems()) {
                val name = products.firstOrNull { it.id == item.productId }?.name
                    ?: item.productName
                addIngredient().apply {
                    productInput.setText(name)
                    weightInput.setText(fmt(item.grams))
                }
            }
        } else if (dishId > 0) {
            val dish = products.firstOrNull { it.id == dishId }
            nameInput.setText(dish?.name ?: "")
            finalWeightInput.setText(fmt(dish?.finalGrams ?: 0.0))
            for (item in db.dishItems(dishId)) {
                addIngredient().apply {
                    productInput.setText(item.productName)
                    weightInput.setText(fmt(item.grams))
                }
            }
        }
        if (rows.isEmpty()) addIngredient()

        content.findViewById<Button>(R.id.addIngredientButton).setOnClickListener { addIngredient() }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.build_dish)
            .setView(content)
            .setPositiveButton(R.string.save, null)
            .setNeutralButton(R.string.defer_dish, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.prepareForKeyboard()
        dialog.setOnShowListener {
            dialog.focusAndShowKeyboard(nameInput, selectAll = dishId > 0)
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener { save(dialog) }
            dialog.getButton(Dialog.BUTTON_NEUTRAL).setOnClickListener {
                val items = collectItems()
                if (items.isEmpty() || rows.size != items.size) {
                    Toast.makeText(activity, R.string.invalid_dish, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                db.saveDraft(items)
                Toast.makeText(activity, R.string.draft_saved, Toast.LENGTH_SHORT).show()
                onDraftSaved()
                dialog.dismiss()
            }
        }
        dialog.show()
        updatePreview()
    }

    private fun addIngredient(): IngredientRow {
        val view = LayoutInflater.from(activity)
            .inflate(R.layout.item_dish_ingredient, ingredientList, false)
        val row = IngredientRow(view)
        ingredientList.addView(view)
        rows.add(row)
        return row
    }

    private inner class IngredientRow(val view: View) {
        val productInput: AutoCompleteTextView = view.findViewById(R.id.ingredientProduct)
        val weightInput: EditText = view.findViewById(R.id.ingredientWeight)
        private val info: TextView = view.findViewById(R.id.ingredientInfo)

        init {
            adapter.showInitial()
            productInput.setAdapter(adapter)
            productInput.post {
                val width = productInput.width
                if (width > 0) productInput.dropDownWidth = (width * 0.92f).toInt()
            }
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val name = ProductLabels.nameOf(productInput.text.toString())
                    val product = products.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    info.text = if (product != null) {
                        val n = product.per100
                        activity.getString(
                            R.string.per_100_info, fmt(n.kcal), fmt(n.protein), fmt(n.fat), fmt(n.carbs)
                        )
                    } else {
                        ""
                    }
                    updatePreview()
                }
            }
            productInput.addTextChangedListener(watcher)
            weightInput.addTextChangedListener(watcher)
            view.findViewById<Button>(R.id.removeIngredient).setOnClickListener {
                ingredientList.removeView(view)
                rows.remove(this)
                updatePreview()
            }
        }

        fun collect(): DishIngredient? {
            val name = ProductLabels.nameOf(productInput.text.toString())
            val grams = parseGrams(weightInput.text.toString()) ?: return null
            val product = products.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
            return DishIngredient(product.id, product.name, grams, product.per100)
        }
    }

    private fun collectItems(): List<DishIngredient> =
        rows.mapNotNull { it.collect() }

    private fun updatePreview() {
        val grams = parseGrams(finalWeightInput.text.toString())
        val n = if (grams != null) dishPer100(collectItems(), grams) else null
        preview.text = if (n != null) {
            activity.getString(
                R.string.dish_preview, fmt(n.kcal), fmt(n.protein), fmt(n.fat), fmt(n.carbs)
            )
        } else {
            ""
        }
    }

    private fun save(dialog: Dialog) {
        val name = nameInput.text.toString().trim()
        val items = collectItems()
        val finalGrams = parseGrams(finalWeightInput.text.toString())
        if (rows.size != items.size) {
            Toast.makeText(activity, R.string.invalid_dish, Toast.LENGTH_SHORT).show()
            return
        }
        if (name.isBlank() || items.isEmpty() || finalGrams == null) {
            Toast.makeText(activity, R.string.invalid_dish, Toast.LENGTH_SHORT).show()
            return
        }
        val n = dishPer100(items, finalGrams) ?: return
        val id = db.saveDish(name, finalGrams, n, items, dishId)
        if (id < 0) {
            Toast.makeText(activity, R.string.duplicate_product, Toast.LENGTH_SHORT).show()
            return
        }
        if (draft) db.clearDraft()
        Toast.makeText(activity, R.string.dish_saved, Toast.LENGTH_SHORT).show()
        onSaved(id, finalGrams, addToDayCheck.isChecked)
        dialog.dismiss()
    }

    private fun fmt(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
}
