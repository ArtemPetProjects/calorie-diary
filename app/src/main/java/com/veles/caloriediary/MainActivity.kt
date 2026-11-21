package com.veles.caloriediary

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val container = findViewById<FrameLayout>(R.id.container)
        val dayView = DayView(this).view
        val productsView = ProductsView(this).view
        val profileView = ProfileView(this).view
        container.addView(dayView)
        container.addView(productsView)
        container.addView(profileView)

        findViewById<Button>(R.id.tabDay).setOnClickListener { select(dayView, productsView, profileView) }
        findViewById<Button>(R.id.tabProducts).setOnClickListener { select(productsView, dayView, profileView) }
        findViewById<Button>(R.id.tabProfile).setOnClickListener { select(profileView, dayView, productsView) }

        select(dayView, productsView, profileView)
    }

    private fun select(active: View, first: View, second: View) {
        active.visibility = View.VISIBLE
        first.visibility = if (first === active) View.VISIBLE else View.GONE
        second.visibility = if (second === active) View.VISIBLE else View.GONE
    }
}
