package com.veles.caloriediary

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Db(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE products (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT UNIQUE NOT NULL, " +
                "kcal REAL, protein REAL, fat REAL, carbs REAL)"
        )
        db.execSQL(
            "CREATE TABLE entries (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "date TEXT NOT NULL, " +
                "product_id INTEGER, " +
                "product_name TEXT, " +
                "grams REAL, " +
                "kcal REAL, protein REAL, fat REAL, carbs REAL)"
        )
        db.execSQL(
            "CREATE TABLE targets (" +
                "_id INTEGER PRIMARY KEY CHECK(_id = 1), " +
                "kcal REAL, protein REAL, fat REAL, carbs REAL)"
        )

        for (p in STARTER_PRODUCTS) {
            db.insert("products", null, productValues(p))
        }
        val t = ContentValues().apply {
            put("_id", 1L)
            putNutrients(this, DEFAULT_TARGETS)
        }
        db.insert("targets", null, t)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Первая версия схемы: миграций пока нет.
    }

    fun products(query: String = ""): List<Product> {
        val cursor = readableDatabase.query("products", null, null, null, null, null, "name")
        return cursor.use { c ->
            val result = mutableListOf<Product>()
            while (c.moveToNext()) {
                val product = c.toProduct()
                if (query.isBlank() || product.name.contains(query, ignoreCase = true)) {
                    result.add(product)
                }
            }
            result
        }
    }

    fun saveProduct(p: Product): Long {
        val values = productValues(p)
        return if (p.id <= 0) {
            writableDatabase.insert("products", null, values)
        } else {
            writableDatabase.update("products", values, "_id = ?", arrayOf(p.id.toString()))
            p.id
        }
    }

    fun deleteProduct(id: Long) {
        writableDatabase.delete("products", "_id = ?", arrayOf(id.toString()))
    }

    fun entries(date: String): List<Entry> {
        val cursor = readableDatabase.query(
            "entries", null, "date = ?", arrayOf(date), null, null, "_id"
        )
        return cursor.use { c ->
            val result = mutableListOf<Entry>()
            while (c.moveToNext()) {
                result.add(c.toEntry())
            }
            result
        }
    }

    fun addEntry(e: Entry): Long {
        val values = ContentValues().apply {
            put("date", e.date)
            put("product_id", e.productId)
            put("product_name", e.productName)
            put("grams", e.grams)
            putNutrients(this, e.values)
        }
        return writableDatabase.insert("entries", null, values)
    }

    fun deleteEntry(id: Long) {
        writableDatabase.delete("entries", "_id = ?", arrayOf(id.toString()))
    }

    fun targets(): Targets {
        val cursor = readableDatabase.query("targets", null, "_id = 1", null, null, null, null)
        return cursor.use { c ->
            if (!c.moveToFirst()) return DEFAULT_TARGETS
            Targets(
                kcal = c.getDouble(c.getColumnIndexOrThrow("kcal")),
                protein = c.getDouble(c.getColumnIndexOrThrow("protein")),
                fat = c.getDouble(c.getColumnIndexOrThrow("fat")),
                carbs = c.getDouble(c.getColumnIndexOrThrow("carbs"))
            )
        }
    }

    fun saveTargets(t: Targets) {
        writableDatabase.delete("targets", null, null)
        val values = ContentValues().apply {
            put("_id", 1L)
            putNutrients(this, t)
        }
        writableDatabase.insert("targets", null, values)
    }

    private fun productValues(p: Product) = ContentValues().apply {
        put("name", p.name)
        putNutrients(this, p.per100)
    }

    private fun putNutrients(values: ContentValues, n: Nutrients) {
        values.put("kcal", n.kcal)
        values.put("protein", n.protein)
        values.put("fat", n.fat)
        values.put("carbs", n.carbs)
    }

    private fun putNutrients(values: ContentValues, t: Targets) {
        values.put("kcal", t.kcal)
        values.put("protein", t.protein)
        values.put("fat", t.fat)
        values.put("carbs", t.carbs)
    }

    private fun Cursor.toProduct() = Product(
        id = getLong(getColumnIndexOrThrow("_id")),
        name = getString(getColumnIndexOrThrow("name")),
        per100 = Nutrients(
            kcal = getDouble(getColumnIndexOrThrow("kcal")),
            protein = getDouble(getColumnIndexOrThrow("protein")),
            fat = getDouble(getColumnIndexOrThrow("fat")),
            carbs = getDouble(getColumnIndexOrThrow("carbs"))
        )
    )

    private fun Cursor.toEntry() = Entry(
        id = getLong(getColumnIndexOrThrow("_id")),
        date = getString(getColumnIndexOrThrow("date")),
        productId = getLong(getColumnIndexOrThrow("product_id")),
        productName = getString(getColumnIndexOrThrow("product_name")),
        grams = getDouble(getColumnIndexOrThrow("grams")),
        values = Nutrients(
            kcal = getDouble(getColumnIndexOrThrow("kcal")),
            protein = getDouble(getColumnIndexOrThrow("protein")),
            fat = getDouble(getColumnIndexOrThrow("fat")),
            carbs = getDouble(getColumnIndexOrThrow("carbs"))
        )
    )

    companion object {
        private const val DB_NAME = "diary.db"
        private const val DB_VERSION = 1
        val DEFAULT_TARGETS = Targets(2000.0, 120.0, 60.0, 250.0)
    }
}
