package com.veles.caloriediary

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Db(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    private val appContext = context.applicationContext

    private fun prefs() = appContext.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)

    fun activeProfileId(): Long = prefs().getLong(KEY_ACTIVE_PROFILE, 1L)

    fun setActiveProfile(id: Long) {
        prefs().edit().putLong(KEY_ACTIVE_PROFILE, id).apply()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE products (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT UNIQUE NOT NULL, " +
                "kcal REAL, protein REAL, fat REAL, carbs REAL, " +
                "is_dish INTEGER NOT NULL DEFAULT 0, " +
                "is_custom INTEGER NOT NULL DEFAULT 0, " +
                "final_grams REAL NOT NULL DEFAULT 0, " +
                "search_name TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE profiles (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL UNIQUE, " +
                "body_kg REAL NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE entries (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "profile_id INTEGER NOT NULL DEFAULT 1, " +
                "date TEXT NOT NULL, " +
                "product_id INTEGER, " +
                "product_name TEXT, " +
                "grams REAL, " +
                "created_at INTEGER NOT NULL DEFAULT 0, " +
                "kcal REAL, protein REAL, fat REAL, carbs REAL)"
        )
        db.execSQL(
            "CREATE TABLE targets (" +
                "profile_id INTEGER PRIMARY KEY, " +
                "kcal REAL, protein REAL, fat REAL, carbs REAL)"
        )
        createDishItems(db)
        db.execSQL(
            "CREATE TABLE weights (profile_id INTEGER NOT NULL, date TEXT NOT NULL, " +
                "kg REAL NOT NULL, PRIMARY KEY(profile_id, date))"
        )
        db.execSQL(
            "CREATE TABLE dish_drafts (profile_id INTEGER PRIMARY KEY, items_json TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE dish_drafts_v2 (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "profile_id INTEGER NOT NULL, name TEXT NOT NULL, items_json TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE product_usage (profile_id INTEGER NOT NULL, product_id INTEGER NOT NULL, " +
                "used_at INTEGER NOT NULL, PRIMARY KEY(profile_id, product_id))"
        )
        db.execSQL(
            "CREATE TABLE adjustments (profile_id INTEGER NOT NULL, date TEXT NOT NULL, " +
                "kcal_delta REAL NOT NULL DEFAULT 0, PRIMARY KEY(profile_id, date))"
        )

        for (p in STARTER_PRODUCTS) {
            db.insertWithOnConflict("products", null, productValues(p), SQLiteDatabase.CONFLICT_IGNORE)
        }
        importExtendedProducts(db)
        importBelarusProducts(db)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_search ON products(search_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_profile_date ON entries(profile_id, date)")
        createDefaultProfile(db)
        val t = ContentValues().apply {
            put("profile_id", 1L)
            putNutrients(this, DEFAULT_TARGETS)
        }
        db.insert("targets", null, t)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        Thread({ backupDatabase(db, "open") }, "db-backup").start()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        backupDatabase(db, "upgrade")
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE products ADD COLUMN is_dish INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE products ADD COLUMN is_custom INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE products ADD COLUMN final_grams REAL NOT NULL DEFAULT 0")
            createDishItems(db)

            for (p in STARTER_PRODUCTS) {
                db.insertWithOnConflict(
                    "products", null, productValues(p), SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            val names = STARTER_PRODUCTS.map { it.name }
            val placeholders = names.joinToString(",") { "?" }
            db.execSQL(
                "UPDATE products SET is_custom = 1 WHERE name NOT IN ($placeholders)",
                names.toTypedArray()
            )
        }
        if (oldVersion < 3) {
            importExtendedProducts(db)
        }
        if (oldVersion < 4 && !hasColumn(db, "entries", "created_at")) {
            db.execSQL("ALTER TABLE entries ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 5 && !hasColumn(db, "products", "search_name")) {
            db.execSQL("ALTER TABLE products ADD COLUMN search_name TEXT NOT NULL DEFAULT ''")
            val cursor = db.rawQuery("SELECT _id, name FROM products", null)
            val update = db.compileStatement("UPDATE products SET search_name = ? WHERE _id = ?")
            cursor.use { c ->
                while (c.moveToNext()) {
                    update.bindString(1, c.getString(1).lowercase())
                    update.bindLong(2, c.getLong(0))
                    update.executeUpdateDelete()
                    update.clearBindings()
                }
            }
            update.close()
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_search ON products(search_name)")
        }
        if (oldVersion < 6) {
            db.execSQL("CREATE TABLE IF NOT EXISTS weights (date TEXT PRIMARY KEY, kg REAL NOT NULL)")
        }
        if (oldVersion < 7) {
            db.execSQL("CREATE TABLE IF NOT EXISTS dish_drafts (_id INTEGER PRIMARY KEY CHECK(_id = 1), items_json TEXT NOT NULL)")
        }
        if (oldVersion < 8) {
            db.execSQL("CREATE TABLE IF NOT EXISTS product_usage (product_id INTEGER PRIMARY KEY, used_at INTEGER NOT NULL)")
        }
        if (oldVersion < 9) {
            importBelarusProducts(db)
        }
        if (oldVersion < 10) {
            migrateToProfiles(db)
        }
        if (oldVersion < 11) {
            migrateDraftsV2(db)
        }
    }

    private fun migrateDraftsV2(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS dish_drafts_v2 (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "profile_id INTEGER NOT NULL, name TEXT NOT NULL, items_json TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL)"
        )
        if (!hasTable(db, "dish_drafts")) return
        val cursor = db.rawQuery("SELECT profile_id, items_json FROM dish_drafts", null)
        val old = mutableListOf<Pair<Long, String>>()
        cursor.use { c ->
            while (c.moveToNext()) old.add(c.getLong(0) to c.getString(1))
        }
        for ((profileId, json) in old) {
            val values = ContentValues().apply {
                put("profile_id", profileId)
                put("name", appContext.getString(R.string.unnamed_dish))
                put("items_json", json)
                put("created_at", System.currentTimeMillis())
            }
            db.insert("dish_drafts_v2", null, values)
        }
    }

    private fun createDefaultProfile(db: SQLiteDatabase) {
        val weight = appContext.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .getFloat(ProfileView.KEY_WEIGHT, 0f).toDouble()
        val values = ContentValues().apply {
            put("_id", 1L)
            put("name", appContext.getString(R.string.default_profile))
            put("body_kg", weight)
        }
        db.insertWithOnConflict("profiles", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun hasTable(db: SQLiteDatabase, table: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)
        )
        return cursor.use { it.moveToFirst() }
    }

    private fun renameBelarusProducts(db: SQLiteDatabase) {
        val suffixes = listOf(
            "Молочная капелька", "Асаблівае", "Савушкин продукт", "Молочный мир", "Бабушкина крынка"
        )
        val cursor = db.rawQuery("SELECT _id, name FROM products", null)
        val update = db.compileStatement("UPDATE products SET name = ?, search_name = ? WHERE _id = ?")
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1)
                for (suffix in suffixes) {
                    val oldSuffix = " $suffix"
                    if (name.endsWith(oldSuffix)) {
                        val newName = name.removeSuffix(oldSuffix) + " ($suffix)"
                        update.bindString(1, newName)
                        update.bindString(2, newName.lowercase())
                        update.bindLong(3, id)
                        update.executeUpdateDelete()
                        update.clearBindings()
                        break
                    }
                }
            }
        }
        update.close()
    }

    private fun migrateToProfiles(db: SQLiteDatabase) {
        val ownTransaction = !db.inTransaction()
        if (ownTransaction) db.beginTransaction()
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS profiles (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL UNIQUE, body_kg REAL NOT NULL DEFAULT 0)"
            )
            createDefaultProfile(db)
            renameBelarusProducts(db)
            importBelarusProducts(db)
            if (!hasColumn(db, "entries", "profile_id")) {
                db.execSQL("ALTER TABLE entries ADD COLUMN profile_id INTEGER NOT NULL DEFAULT 1")
            }

            val weights = mutableListOf<Pair<String, Double>>()
            if (hasTable(db, "weights")) {
                val c = db.rawQuery("SELECT date, kg FROM weights", null)
                c.use { cur ->
                    while (cur.moveToNext()) weights.add(cur.getString(0) to cur.getDouble(1))
                }
            }
            db.execSQL("DROP TABLE IF EXISTS weights")
            db.execSQL(
                "CREATE TABLE weights (profile_id INTEGER NOT NULL, date TEXT NOT NULL, " +
                    "kg REAL NOT NULL, PRIMARY KEY(profile_id, date))"
            )
            for ((date, kg) in weights) {
                val v = ContentValues().apply {
                    put("profile_id", 1L); put("date", date); put("kg", kg)
                }
                db.insert("weights", null, v)
            }

            var oldTargets: Targets? = null
            if (hasTable(db, "targets")) {
                val c = db.rawQuery("SELECT kcal, protein, fat, carbs FROM targets LIMIT 1", null)
                c.use { cur ->
                    if (cur.moveToFirst()) {
                        oldTargets = Targets(
                            cur.getDouble(0), cur.getDouble(1), cur.getDouble(2), cur.getDouble(3)
                        )
                    }
                }
            }
            db.execSQL("DROP TABLE IF EXISTS targets")
            db.execSQL("CREATE TABLE targets (profile_id INTEGER PRIMARY KEY, kcal REAL, protein REAL, fat REAL, carbs REAL)")
            val tv = ContentValues().apply {
                put("profile_id", 1L)
                putNutrients(this, oldTargets ?: DEFAULT_TARGETS)
            }
            db.insert("targets", null, tv)

            val usage = mutableListOf<Pair<Long, Long>>()
            if (hasTable(db, "product_usage")) {
                val c = db.rawQuery("SELECT product_id, used_at FROM product_usage", null)
                c.use { cur ->
                    while (cur.moveToNext()) usage.add(cur.getLong(0) to cur.getLong(1))
                }
            }
            db.execSQL("DROP TABLE IF EXISTS product_usage")
            db.execSQL(
                "CREATE TABLE product_usage (profile_id INTEGER NOT NULL, product_id INTEGER NOT NULL, " +
                    "used_at INTEGER NOT NULL, PRIMARY KEY(profile_id, product_id))"
            )
            for ((productId, usedAt) in usage) {
                val v = ContentValues().apply {
                    put("profile_id", 1L); put("product_id", productId); put("used_at", usedAt)
                }
                db.insert("product_usage", null, v)
            }

            var oldDraft: String? = null
            if (hasTable(db, "dish_drafts")) {
                val c = db.rawQuery("SELECT items_json FROM dish_drafts LIMIT 1", null)
                c.use { cur -> if (cur.moveToFirst()) oldDraft = cur.getString(0) }
            }
            db.execSQL("DROP TABLE IF EXISTS dish_drafts")
            db.execSQL("CREATE TABLE dish_drafts (profile_id INTEGER PRIMARY KEY, items_json TEXT NOT NULL)")
            if (oldDraft != null) {
                val v = ContentValues().apply {
                    put("profile_id", 1L); put("items_json", oldDraft)
                }
                db.insert("dish_drafts", null, v)
            }

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS adjustments (profile_id INTEGER NOT NULL, date TEXT NOT NULL, " +
                    "kcal_delta REAL NOT NULL DEFAULT 0, PRIMARY KEY(profile_id, date))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_profile_date ON entries(profile_id, date)")
            if (ownTransaction) db.setTransactionSuccessful()
        } finally {
            if (ownTransaction) db.endTransaction()
        }
    }

    private fun backupDatabase(db: SQLiteDatabase, reason: String) {
        val dir = File(appContext.filesDir, "backups")
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles { f -> f.name.startsWith("diary-") && f.name.endsWith(".db") }
            ?: emptyArray()
        val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        if (reason == "open" && files.any { it.name.contains("-$day-") }) return

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val dest = File(dir, "diary-$stamp-$reason.db")
        try {
            if (dest.exists()) dest.delete()
            db.execSQL("VACUUM INTO '${dest.absolutePath}'")
        } catch (e: Exception) {
            try {
                FileInputStream(db.path).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
            } catch (e2: Exception) {
                dest.delete()
                return
            }
        }
        val current = dir.listFiles { f -> f.name.startsWith("diary-") && f.name.endsWith(".db") }
            ?: emptyArray()
        val keep = BackupPolicy.keepLatest(current.map { it.name }, 3)
        current.filter { it.name !in keep }.forEach { it.delete() }
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        val cursor = db.rawQuery("PRAGMA table_info($table)", null)
        return cursor.use { c ->
            val index = c.getColumnIndexOrThrow("name")
            var found = false
            while (c.moveToNext()) {
                if (c.getString(index) == column) {
                    found = true
                    break
                }
            }
            found
        }
    }

    private fun importExtendedProducts(db: SQLiteDatabase) =
        importProductsFromAsset(db, ASSET_PRODUCTS)

    private fun importBelarusProducts(db: SQLiteDatabase) =
        importProductsFromAsset(db, ASSET_BELARUS)

    private fun importProductsFromAsset(db: SQLiteDatabase, assetName: String) {
        val stream = appContext.assets.open(assetName)
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        val array = JSONArray(text)
        val statement = db.compileStatement(
            "INSERT OR IGNORE INTO products (name, search_name, kcal, protein, fat, carbs) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
        )
        // onUpgrade уже выполняется в транзакции SQLiteOpenHelper: вложенная транзакция
        // выбросит исключение и сорвёт обновление. Импорт работает в обоих режимах.
        val ownTransaction = !db.inTransaction()
        if (ownTransaction) db.beginTransaction()
        try {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.getString("name")
                statement.bindString(1, name)
                statement.bindString(2, name.lowercase())
                statement.bindDouble(3, item.getDouble("kcal"))
                statement.bindDouble(4, item.getDouble("protein"))
                statement.bindDouble(5, item.getDouble("fat"))
                statement.bindDouble(6, item.getDouble("carbs"))
                statement.executeInsert()
                statement.clearBindings()
            }
            if (ownTransaction) db.setTransactionSuccessful()
        } finally {
            if (ownTransaction) db.endTransaction()
            statement.close()
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
        if (onlyCustom) {
            conditions.add("is_custom = 1")
            conditions.add("is_dish = 0")
        }
        if (onlyDishes) conditions.add("is_dish = 1")
        var selection: String? = null
        var args: Array<String>? = null
        if (query.isNotBlank()) {
            conditions.add("search_name LIKE ?")
            selection = conditions.joinToString(" AND ")
            args = arrayOf("%${query.lowercase()}%")
        } else if (conditions.isNotEmpty()) {
            selection = conditions.joinToString(" AND ")
        }
        val cursor = readableDatabase.query("products", null, selection, args, null, null, "name")
        return cursor.use { c ->
            val result = mutableListOf<Product>()
            while (c.moveToNext()) {
                result.add(c.toProduct())
            }
            result
        }
    }

    fun saveProduct(p: Product): Long {
        val values = productValues(p)
        return if (p.id <= 0) {
            writableDatabase.insert("products", null, values)
        } else {
            val rows = writableDatabase.updateWithOnConflict(
                "products", values, "_id = ?", arrayOf(p.id.toString()),
                SQLiteDatabase.CONFLICT_IGNORE
            )
            if (rows == 0) -1L else p.id
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
                val rows = writableDatabase.updateWithOnConflict(
                    "products", values, "_id = ?", arrayOf(dishId.toString()),
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (rows == 0) -1L else dishId
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
                    if (writableDatabase.insert("dish_items", null, iv) > 0L) {
                        markUsed(item.productId)
                    }
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
            "entries", null, "profile_id = ? AND date = ?",
            arrayOf(activeProfileId().toString(), date), null, null, "_id"
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
            put("profile_id", activeProfileId())
            put("date", e.date)
            put("product_id", e.productId)
            put("product_name", e.productName)
            put("grams", e.grams)
            putNutrients(this, e.values)
            put("created_at", if (e.createdAt > 0L) e.createdAt else System.currentTimeMillis())
        }
        val id = writableDatabase.insert("entries", null, values)
        if (id > 0L) markUsed(e.productId)
        return id
    }

    fun updateEntry(id: Long, grams: Double, values: Nutrients): Int {
        val v = ContentValues().apply {
            put("grams", grams)
            putNutrients(this, values)
        }
        return writableDatabase.update("entries", v, "_id = ?", arrayOf(id.toString()))
    }

    fun deleteEntry(id: Long) {
        writableDatabase.delete("entries", "_id = ?", arrayOf(id.toString()))
    }

    fun targets(): Targets {
        val cursor = readableDatabase.query(
            "targets", null, "profile_id = ?", arrayOf(activeProfileId().toString()), null, null, null
        )
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
        val values = ContentValues().apply {
            put("profile_id", activeProfileId())
            putNutrients(this, t)
        }
        writableDatabase.insertWithOnConflict(
            "targets", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun weightFor(date: String): Double? {
        val cursor = readableDatabase.query(
            "weights", arrayOf("kg"), "profile_id = ? AND date = ?",
            arrayOf(activeProfileId().toString(), date), null, null, null
        )
        return cursor.use { c -> if (c.moveToFirst()) c.getDouble(0) else null }
    }

    fun saveWeight(date: String, kg: Double): Long {
        val values = ContentValues().apply {
            put("profile_id", activeProfileId())
            put("date", date)
            put("kg", kg)
        }
        return writableDatabase.insertWithOnConflict(
            "weights", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun markUsed(productId: Long) {
        if (productId <= 0L) return
        val values = ContentValues().apply {
            put("profile_id", activeProfileId())
            put("product_id", productId)
            put("used_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "product_usage", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun recentProductIds(): List<Long> {
        val cursor = readableDatabase.query(
            "product_usage", arrayOf("product_id"), "profile_id = ?",
            arrayOf(activeProfileId().toString()), null, null, "used_at DESC"
        )
        return cursor.use { c ->
            val result = mutableListOf<Long>()
            while (c.moveToNext()) result.add(c.getLong(0))
            result
        }
    }

    fun drafts(): List<DishDraft> {
        val cursor = readableDatabase.query(
            "dish_drafts_v2", null, "profile_id = ?",
            arrayOf(activeProfileId().toString()), null, null, "created_at DESC"
        )
        return cursor.use { c ->
            val result = mutableListOf<DishDraft>()
            while (c.moveToNext()) {
                result.add(
                    DishDraft(
                        c.getLong(c.getColumnIndexOrThrow("_id")),
                        c.getString(c.getColumnIndexOrThrow("name")),
                        parseDraftItems(c.getString(c.getColumnIndexOrThrow("items_json"))),
                        c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
            result
        }
    }

    fun draft(id: Long): DishDraft? = drafts().firstOrNull { it.id == id }

    fun saveDraft(name: String, items: List<DishIngredient>): Long {
        val array = JSONArray()
        for (item in items) {
            val o = JSONObject().apply {
                put("product_id", item.productId)
                put("product_name", item.productName)
                put("grams", item.grams)
                put("kcal", item.per100.kcal)
                put("protein", item.per100.protein)
                put("fat", item.per100.fat)
                put("carbs", item.per100.carbs)
            }
            array.put(o)
        }
        val values = ContentValues().apply {
            put("profile_id", activeProfileId())
            put("name", draftTitle(name, drafts().size + 1))
            put("items_json", array.toString())
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("dish_drafts_v2", null, values)
    }

    fun deleteDraft(id: Long) {
        writableDatabase.delete(
            "dish_drafts_v2", "_id = ? AND profile_id = ?",
            arrayOf(id.toString(), activeProfileId().toString())
        )
    }

    private fun parseDraftItems(json: String): List<DishIngredient> {
        val array = try {
            JSONArray(json)
        } catch (e: Exception) {
            return emptyList()
        }
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            DishIngredient(
                productId = o.getLong("product_id"),
                productName = o.getString("product_name"),
                grams = o.getDouble("grams"),
                per100 = Nutrients(
                    kcal = o.getDouble("kcal"),
                    protein = o.getDouble("protein"),
                    fat = o.getDouble("fat"),
                    carbs = o.getDouble("carbs")
                )
            )
        }
    }

    fun saveDraft(items: List<DishIngredient>) {
        val array = JSONArray()
        for (item in items) {
            val o = JSONObject().apply {
                put("product_id", item.productId)
                put("product_name", item.productName)
                put("grams", item.grams)
                put("kcal", item.per100.kcal)
                put("protein", item.per100.protein)
                put("fat", item.per100.fat)
                put("carbs", item.per100.carbs)
            }
            array.put(o)
        }
        val values = ContentValues().apply {
            put("profile_id", activeProfileId())
            put("items_json", array.toString())
        }
        writableDatabase.insertWithOnConflict(
            "dish_drafts", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun hasDraft(): Boolean {
        val cursor = readableDatabase.query(
            "dish_drafts", arrayOf("1"), "profile_id = ?",
            arrayOf(activeProfileId().toString()), null, null, null
        )
        return cursor.use { it.moveToFirst() }
    }

    fun draftItems(): List<DishIngredient> {
        val cursor = readableDatabase.query(
            "dish_drafts", arrayOf("items_json"), "profile_id = ?",
            arrayOf(activeProfileId().toString()), null, null, null
        )
        val json = cursor.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: return emptyList()
        val array = try {
            JSONArray(json)
        } catch (e: Exception) {
            return emptyList()
        }
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            DishIngredient(
                productId = o.getLong("product_id"),
                productName = o.getString("product_name"),
                grams = o.getDouble("grams"),
                per100 = Nutrients(
                    kcal = o.getDouble("kcal"),
                    protein = o.getDouble("protein"),
                    fat = o.getDouble("fat"),
                    carbs = o.getDouble("carbs")
                )
            )
        }
    }

    fun clearDraft() {
        writableDatabase.delete("dish_drafts", "profile_id = ?", arrayOf(activeProfileId().toString()))
    }

    fun deleteWeight(date: String) {
        writableDatabase.delete(
            "weights", "profile_id = ? AND date = ?",
            arrayOf(activeProfileId().toString(), date)
        )
    }

    fun profiles(): List<Profile> {
        val cursor = readableDatabase.query("profiles", null, null, null, null, null, "_id")
        return cursor.use { c ->
            val result = mutableListOf<Profile>()
            while (c.moveToNext()) {
                result.add(
                    Profile(
                        c.getLong(c.getColumnIndexOrThrow("_id")),
                        c.getString(c.getColumnIndexOrThrow("name")),
                        c.getDouble(c.getColumnIndexOrThrow("body_kg"))
                    )
                )
            }
            result
        }
    }

    fun addProfile(name: String): Long {
        val id = writableDatabase.insert(
            "profiles", null, ContentValues().apply {
                put("name", name.trim())
                put("body_kg", 0.0)
            }
        )
        if (id > 0) {
            writableDatabase.insert(
                "targets", null, ContentValues().apply {
                    put("profile_id", id)
                    putNutrients(this, DEFAULT_TARGETS)
                }
            )
        }
        return id
    }

    fun renameProfile(id: Long, name: String): Boolean {
        if (name.isBlank()) return false
        return writableDatabase.update(
            "profiles", ContentValues().apply { put("name", name.trim()) },
            "_id = ?", arrayOf(id.toString())
        ) > 0
    }

    fun deleteProfile(id: Long): Boolean {
        if (profiles().size <= 1) return false
        writableDatabase.beginTransaction()
        return try {
            for (table in listOf("entries", "weights", "targets", "product_usage", "dish_drafts", "adjustments")) {
                writableDatabase.delete(table, "profile_id = ?", arrayOf(id.toString()))
            }
            writableDatabase.delete("profiles", "_id = ?", arrayOf(id.toString()))
            if (activeProfileId() == id) setActiveProfile(1L)
            writableDatabase.setTransactionSuccessful()
            true
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun bodyWeight(): Double = profiles().firstOrNull { it.id == activeProfileId() }?.bodyKg ?: 0.0

    fun saveBodyWeight(kg: Double) {
        writableDatabase.update(
            "profiles", ContentValues().apply { put("body_kg", kg) },
            "_id = ?", arrayOf(activeProfileId().toString())
        )
    }

    fun adjustmentFor(date: String): Double {
        val cursor = readableDatabase.query(
            "adjustments", arrayOf("kcal_delta"), "profile_id = ? AND date = ?",
            arrayOf(activeProfileId().toString(), date), null, null, null
        )
        return cursor.use { c -> if (c.moveToFirst()) c.getDouble(0) else 0.0 }
    }

    fun saveAdjustment(date: String, delta: Double) {
        writableDatabase.insertWithOnConflict(
            "adjustments", null, ContentValues().apply {
                put("profile_id", activeProfileId())
                put("date", date)
                put("kcal_delta", delta)
            }, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun productValues(p: Product) = ContentValues().apply {
        put("name", p.name)
        put("search_name", p.name.lowercase())
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
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        values = Nutrients(
            kcal = getDouble(getColumnIndexOrThrow("kcal")),
            protein = getDouble(getColumnIndexOrThrow("protein")),
            fat = getDouble(getColumnIndexOrThrow("fat")),
            carbs = getDouble(getColumnIndexOrThrow("carbs"))
        )
    )

    companion object {
        private const val DB_NAME = "diary.db"
        private const val DB_VERSION = 11
        private const val ASSET_PRODUCTS = "products_extended.json"
        private const val ASSET_BELARUS = "products_belarus.json"
        private const val KEY_ACTIVE_PROFILE = "active_profile_id"
        val DEFAULT_TARGETS = Targets(2000.0, 120.0, 60.0, 250.0)
    }
}
