package com.veles.caloriediary

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
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

    init {
        view.findViewById<Button>(R.id.addProductButton).setOnClickListener { showDialog(null) }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshList()
        })
        listView.setOnItemClickListener { _, _, position, _ ->
            showDialog(listView.adapter.getItem(position) as Product)
        }
        refreshList()
    }

    private fun refreshList() {
        listView.adapter = ProductAdapter(db.products(searchInput.text.toString()))
    }

    private fun fmt(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

    private inner class ProductAdapter(private val items: List<Product>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): Product = items[position]
        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: LayoutInflater.from(activity)
                .inflate(R.layout.item_product, parent, false)
            val product = items[position]
            val n = product.per100
            row.findViewById<TextView>(R.id.productText).text =
                "${product.name} • ${fmt(n.kcal)} ккал / Б ${fmt(n.protein)} / " +
                    "Ж ${fmt(n.fat)} / У ${fmt(n.carbs)} (на 100 г)"
            row.findViewById<Button>(R.id.deleteProductButton).setOnClickListener {
                db.deleteProduct(product.id)
                refreshList()
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

        val title = if (product == null) R.string.add_product else R.string.product_name
        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(content)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
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
                db.saveProduct(Product(id, name, Nutrients(kcal, protein, fat, carbs)))
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
}
