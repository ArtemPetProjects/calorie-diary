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
                "kcal REAL, protein REAL, fat REAL, carbs REAL, " +
                "is_dish INTEGER NOT NULL DEFAULT 0, " +
                "is_custom INTEGER NOT NULL DEFAULT 0, " +
                "final_grams REAL NOT NULL DEFAULT 0)"
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
        createDishItems(db)

        for (p in STARTER_PRODUCTS) {
            db.insertWithOnConflict("products", null, productValues(p), SQLiteDatabase.CONFLICT_IGNORE)
        }
        val t = ContentValues().apply {
            put("_id", 1L)
            putNutrients(this, DEFAULT_TARGETS)
        }
        db.insert("targets", null, t)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE products ADD COLUMN is_dish INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE products ADD COLUMN is_custom INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE products ADD COLUMN final_grams REAL NOT NULL DEFAULT 0")
            createDishItems(db)
        }
    }

    private fun createDishItems(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS dish_items (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "dish_id INTEGER, " +
                "product_id INTEGER, " +
                "product_name TEXT, " +
                "grams REAL, " +
                "kcal REAL, protein REAL, fat REAL, carbs REAL)"
        )
    }

    fun products(
        query: String = "",
        onlyCustom: Boolean = false,
        onlyDishes: Boolean = false
    ): List<Product> {
        val conditions = mutableListOf<String>()
        if (onlyCustom) conditions.add("is_custom = 1")
        if (onlyDishes) conditions.add("is_dish = 1")
        val where = if (conditions.isEmpty()) null else conditions.joinToString(" AND ")
        val cursor = readableDatabase.query("products", null, where, null, null, null, "name")
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

    fun saveDish(
        name: String,
        finalGrams: Double,
        per100: Nutrients,
        items: List<DishIngredient>,
        dishId: Long = 0L
    ): Long {
        var savedId = -1L
        writableDatabase.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("name", name)
                putNutrients(this, per100)
                put("is_dish", 1)
                put("is_custom", 1)
                put("final_grams", finalGrams)
            }
            savedId = if (dishId > 0) {
                writableDatabase.update("products", values, "_id = ?", arrayOf(dishId.toString()))
                dishId
            } else {
                writableDatabase.insert("products", null, values)
            }
            if (savedId > 0) {
                writableDatabase.delete("dish_items", "dish_id = ?", arrayOf(savedId.toString()))
                for (item in items) {
                    val iv = ContentValues().apply {
                        put("dish_id", savedId)
                        put("product_id", item.productId)
                        put("product_name", item.productName)
                        put("grams", item.grams)
                        putNutrients(this, item.per100)
                    }
                    writableDatabase.insert("dish_items", null, iv)
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
        return savedId
    }

    fun dishItems(dishId: Long): List<DishIngredient> {
        val cursor = readableDatabase.query(
            "dish_items", null, "dish_id = ?", arrayOf(dishId.toString()), null, null, "_id"
        )
        return cursor.use { c ->
            val result = mutableListOf<DishIngredient>()
            while (c.moveToNext()) {
                result.add(
                    DishIngredient(
                        productId = c.getLong(c.getColumnIndexOrThrow("product_id")),
                        productName = c.getString(c.getColumnIndexOrThrow("product_name")),
                        grams = c.getDouble(c.getColumnIndexOrThrow("grams")),
                        per100 = Nutrients(
                            kcal = c.getDouble(c.getColumnIndexOrThrow("kcal")),
                            protein = c.getDouble(c.getColumnIndexOrThrow("protein")),
                            fat = c.getDouble(c.getColumnIndexOrThrow("fat")),
                            carbs = c.getDouble(c.getColumnIndexOrThrow("carbs"))
                        )
                    )
                )
            }
            result
        }
    }

    fun deleteProduct(id: Long) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("dish_items", "dish_id = ?", arrayOf(id.toString()))
            writableDatabase.delete("products", "_id = ?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
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
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("targets", null, null)
            val values = ContentValues().apply {
                put("_id", 1L)
                putNutrients(this, t)
            }
            writableDatabase.insert("targets", null, values)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun productValues(p: Product) = ContentValues().apply {
        put("name", p.name)
        putNutrients(this, p.per100)
        put("is_dish", if (p.isDish) 1 else 0)
        put("is_custom", if (p.isCustom) 1 else 0)
        put("final_grams", p.finalGrams)
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
        ),
        finalGrams = getDouble(getColumnIndexOrThrow("final_grams")),
        isDish = getInt(getColumnIndexOrThrow("is_dish")) == 1,
        isCustom = getInt(getColumnIndexOrThrow("is_custom")) == 1
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
        private const val DB_VERSION = 2
        val DEFAULT_TARGETS = Targets(2000.0, 120.0, 60.0, 250.0)
    }
}
