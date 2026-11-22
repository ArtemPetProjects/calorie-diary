package com.veles.caloriediary

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import java.util.Locale

data class Suggestion(val product: Product) {
    override fun toString(): String = product.name
}

class SuggestionAdapter(activity: Activity, products: List<Product>) :
    ArrayAdapter<Suggestion>(activity, R.layout.item_suggestion, products.map { Suggestion(it) }) {

    private val all = products.map { Suggestion(it) }
    private val byName = all.associateBy { it.product.name.lowercase(Locale.getDefault()) }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_suggestion, parent, false)
        val item = getItem(position) ?: return row
        val n = item.product.per100
        row.findViewById<TextView>(R.id.suggestionName).text = item.product.name
        row.findViewById<TextView>(R.id.suggestionInfo).text = context.getString(
            R.string.suggestion_info, fmt(n.kcal), fmt(n.protein), fmt(n.fat), fmt(n.carbs)
        )
        return row
    }

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString() ?: ""
            val names = all.map { it.product.name }
            val ranked = rankProductNames(names, query)
            val list = ranked.mapNotNull { byName[it.lowercase(Locale.getDefault())] }
                .take(50)
            return FilterResults().apply {
                values = list
                count = list.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            clear()
            val list = results.values as? List<Suggestion> ?: emptyList()
            addAll(list)
            notifyDataSetChanged()
        }
    }

    private fun fmt(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
}
