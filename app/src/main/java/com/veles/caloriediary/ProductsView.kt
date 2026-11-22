package com.veles.caloriediary

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.database.sqlite.SQLiteConstraintException
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class ProductsView(private val activity: Activity) {

    val view: View = LayoutInflater.from(activity).inflate(R.layout.fragment_products, null)
    private val db = Db(activity)
    private val listView: ListView = view.findViewById(R.id.productList)
    private val searchInput: EditText = view.findViewById(R.id.searchInput)
    private var mode = MODE_ALL

    init {
        view.findViewById<Button>(R.id.addProductButton).setOnClickListener { showDialog(null) }
        view.findViewById<Button>(R.id.addDishButton).setOnClickListener {
            DishDialog(activity, db, showAddToDay = false) { _, _, _ -> refreshList() }.show()
        }
        view.findViewById<Button>(R.id.filterAll).setOnClickListener {
            mode = MODE_ALL
            refreshList()
        }
        view.findViewById<Button>(R.id.filterMine).setOnClickListener {
            mode = MODE_CUSTOM
            refreshList()
        }
        view.findViewById<Button>(R.id.filterDishes).setOnClickListener {
            mode = MODE_DISHES
            refreshList()
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshList()
        })
        listView.setOnItemClickListener { _, _, position, _ ->
            openEditor(listView.adapter.getItem(position) as Product)
        }
        refreshList()
    }

    private fun openEditor(product: Product) {
        if (product.isDish) {
            DishDialog(activity, db, product.id, showAddToDay = false) { _, _, _ ->
                refreshList()
            }.show()
        } else {
            showDialog(product)
        }
    }

    fun refreshList() {
        listView.adapter = ProductAdapter(
            db.products(
                query = searchInput.text.toString(),
                onlyCustom = mode == MODE_CUSTOM,
                onlyDishes = mode == MODE_DISHES
            ),
            db.recentProductIds().toSet()
        )
    }

    private fun fmt(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

    private inner class ProductAdapter(
        private val items: List<Product>,
        private val recentIds: Set<Long>
    ) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): Product = items[position]
        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: LayoutInflater.from(activity)
                .inflate(R.layout.item_product, parent, false)
            val product = items[position]
            val n = product.per100
            val mark = when {
                product.isDish -> activity.getString(R.string.dish_mark)
                product.isCustom -> activity.getString(R.string.custom_mark)
                else -> ""
            }
            val nameView = row.findViewById<TextView>(R.id.productText)
            nameView.text = activity.getString(
                R.string.product_row,
                product.name, mark, fmt(n.kcal), fmt(n.protein), fmt(n.fat), fmt(n.carbs)
            )
            val attrs = activity.obtainStyledAttributes(
                intArrayOf(R.attr.recentDishText, android.R.attr.textColorPrimary)
            )
            val recentColor = attrs.getColor(0, Color.parseColor("#0288D1"))
            val normalColor = attrs.getColor(1, Color.parseColor("#212121"))
            attrs.recycle()
            nameView.setTextColor(
                if (product.id in recentIds) recentColor else normalColor
            )
            row.findViewById<Button>(R.id.editProductButton).setOnClickListener {
                openEditor(product)
            }
            row.findViewById<Button>(R.id.deleteProductButton).setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle(R.string.confirm_delete)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        db.deleteProduct(product.id)
                        refreshList()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            return row
        }
    }

    private fun showDialog(product: Product?) {
        val content = LayoutInflater.from(activity).inflate(R.layout.dialog_product, null)
        val nameInput = content.findViewById<EditText>(R.id.productNameInput)
        val kcalInput = content.findViewById<EditText>(R.id.kcalInput)
        val proteinInput = content.findViewById<EditText>(R.id.proteinInput)
        val fatInput = content.findViewById<EditText>(R.id.fatInput)
        val carbsInput = content.findViewById<EditText>(R.id.carbsInput)

        if (product != null) {
            nameInput.setText(product.name)
            kcalInput.setText(fmt(product.per100.kcal))
            proteinInput.setText(fmt(product.per100.protein))
            fatInput.setText(fmt(product.per100.fat))
            carbsInput.setText(fmt(product.per100.carbs))
        }

        var updatingKcal = false
        val macroWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingKcal) return
                val value = autoFilledKcal(
                    true,
                    kcalInput.text.toString(),
                    readNumber(proteinInput),
                    readNumber(fatInput),
                    readNumber(carbsInput)
                )
                if (value != null) {
                    updatingKcal = true
                    kcalInput.setText(fmt(value))
                    updatingKcal = false
                }
            }
        }
        proteinInput.addTextChangedListener(macroWatcher)
        fatInput.addTextChangedListener(macroWatcher)
        carbsInput.addTextChangedListener(macroWatcher)
        val title = if (product == null) R.string.add_product else R.string.product_name
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(content)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.focusAndShowKeyboard(nameInput, selectAll = product != null)
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val kcal = readNumber(kcalInput)
                val protein = readNumber(proteinInput)
                val fat = readNumber(fatInput)
                val carbs = readNumber(carbsInput)
                if (name.isBlank() || kcal == null || protein == null || fat == null || carbs == null) {
                    Toast.makeText(activity, R.string.fill_targets, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val id = product?.id ?: 0L
                val savedId = try {
                    db.saveProduct(
                        Product(
                            id = id,
                            name = name,
                            per100 = Nutrients(kcal, protein, fat, carbs),
                            finalGrams = product?.finalGrams ?: 0.0,
                            isDish = product?.isDish ?: false,
                            isCustom = true
                        )
                    )
                } catch (e: SQLiteConstraintException) {
                    Toast.makeText(activity, R.string.duplicate_product, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (savedId < 0) {
                    Toast.makeText(activity, R.string.duplicate_product, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                refreshList()
            }
        }
        dialog.show()
    }

    private fun readNumber(input: EditText): Double? {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) return null
        val value = raw.replace(',', '.').toDoubleOrNull() ?: return null
        return if (value >= 0.0) value else null
    }

    companion object {
        private const val MODE_ALL = 0
        private const val MODE_CUSTOM = 1
        private const val MODE_DISHES = 2
    }
}
