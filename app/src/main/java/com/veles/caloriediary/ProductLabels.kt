package com.veles.caloriediary

import android.app.Activity
import java.util.Locale

object ProductLabels {

    fun suggestion(activity: Activity, product: Product): String {
        val n = product.per100
        return activity.getString(
            R.string.product_suggestion,
            product.name, fmt(n.kcal), fmt(n.protein), fmt(n.fat), fmt(n.carbs)
        )
    }

    fun nameOf(text: String): String = text.substringBefore(" • ").trim()

    private fun fmt(value: Double) = String.format(Locale.getDefault(), "%.1f", value)
}
