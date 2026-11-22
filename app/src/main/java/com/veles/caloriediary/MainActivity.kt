package com.veles.caloriediary

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout

class MainActivity : Activity() {

    private lateinit var dayView: DayView
    private lateinit var productsView: ProductsView
    private lateinit var profileView: ProfileView
    private var activeTab = TAB_DAY

    override fun onCreate(savedInstanceState: Bundle?) {
        val dark = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DARK, false)
        setTheme(if (dark) R.style.AppTheme_Dark else R.style.AppTheme_Light)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dayView = DayView(this, savedInstanceState?.getString(KEY_DATE))
        productsView = ProductsView(this)
        profileView = ProfileView(this)

        val container = findViewById<FrameLayout>(R.id.container)
        container.addView(dayView.view)
        container.addView(productsView.view)
        container.addView(profileView.view)

        findViewById<Button>(R.id.tabDay).setOnClickListener { select(TAB_DAY) }
        findViewById<Button>(R.id.tabProducts).setOnClickListener { select(TAB_PRODUCTS) }
        findViewById<Button>(R.id.tabProfile).setOnClickListener { select(TAB_PROFILE) }

        select(savedInstanceState?.getString(KEY_TAB) ?: TAB_DAY)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, activeTab)
        outState.putString(KEY_DATE, dayView.selectedDateKey)
    }

    private fun select(tab: String) {
        activeTab = tab
        val ta = obtainStyledAttributes(
            intArrayOf(R.attr.blockDay, R.attr.blockProducts, R.attr.blockProfile, android.R.attr.textColorSecondary)
        )
        val dayColor = ta.getColor(0, Color.GRAY)
        val productsColor = ta.getColor(1, Color.GRAY)
        val profileColor = ta.getColor(2, Color.GRAY)
        val inactiveColor = ta.getColor(3, Color.GRAY)
        ta.recycle()
        findViewById<Button>(R.id.tabDay).setTextColor(if (tab == TAB_DAY) dayColor else inactiveColor)
        findViewById<Button>(R.id.tabProducts).setTextColor(if (tab == TAB_PRODUCTS) productsColor else inactiveColor)
        findViewById<Button>(R.id.tabProfile).setTextColor(if (tab == TAB_PROFILE) profileColor else inactiveColor)
        dayView.view.visibility = if (tab == TAB_DAY) View.VISIBLE else View.GONE
        productsView.view.visibility = if (tab == TAB_PRODUCTS) View.VISIBLE else View.GONE
        profileView.view.visibility = if (tab == TAB_PROFILE) View.VISIBLE else View.GONE
        when (tab) {
            TAB_DAY -> dayView.refresh()
            TAB_PRODUCTS -> productsView.refreshList()
        }
    }

    companion object {
        const val PREFS = "settings"
        const val KEY_DARK = "dark_theme"
        private const val KEY_TAB = "activeTab"
        private const val KEY_DATE = "selectedDate"
        private const val TAB_DAY = "day"
        private const val TAB_PRODUCTS = "products"
        private const val TAB_PROFILE = "profile"
    }
}
