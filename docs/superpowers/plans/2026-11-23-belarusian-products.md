# Belarusian Products Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Р”РѕР±Р°РІРёС‚СЊ РІ РѕС„Р»Р°Р№РЅ-Р±Р°Р·Сѓ РґРЅРµРІРЅРёРєР° РєР°Р»РѕСЂРёР№РЅРѕСЃС‚Рё РЅРµ РјРµРЅРµРµ 30 Р±РµР»РѕСЂСѓСЃСЃРєРёС… РјРѕР»РѕС‡РЅС‹С… РїСЂРѕРґСѓРєС‚РѕРІ СЃ Р±СЂРµРЅРґРѕРј РІ РЅР°Р·РІР°РЅРёРё Рё РїСЂРѕРІРµСЂРµРЅРЅС‹РјРё РљР‘Р–РЈ.

**Architecture:** РќРѕРІС‹Р№ JSON-Р°СЃСЃРµС‚ СЃ С‚Р°РєРѕР№ Р¶Рµ СЃС…РµРјРѕР№, РєР°Рє Сѓ СЂР°СЃС€РёСЂРµРЅРЅРѕР№ Р±Р°Р·С‹; СЂРµРµСЃС‚СЂ РёСЃС‚РѕС‡РЅРёРєРѕРІ РІ CSV; РёРјРїРѕСЂС‚ Р°СЃСЃРµС‚Р° РІ `Db.kt` С‡РµСЂРµР· `INSERT OR IGNORE` РїСЂРё СЃРѕР·РґР°РЅРёРё Рё РїСЂРё РѕР±РЅРѕРІР»РµРЅРёРё РґРѕ РІРµСЂСЃРёРё 9. РЎС…РµРјР° С‚Р°Р±Р»РёС† РЅРµ РјРµРЅСЏРµС‚СЃСЏ; РїРѕРёСЃРє СЂР°Р±РѕС‚Р°РµС‚ С‡РµСЂРµР· СЃСѓС‰РµСЃС‚РІСѓСЋС‰РµРµ РїРѕР»Рµ `search_name`.

**Tech Stack:** AGP 9.5.2, Gradle 9.9.2, SDK 37, SQLite, JUnit 4; Р±РµР· AndroidX/Compose Рё Р±РµР· СЃРµС‚РµРІС‹С… Р·Р°РїСЂРѕСЃРѕРІ РёР· РїСЂРёР»РѕР¶РµРЅРёСЏ.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-23-belarusian-products-design.md`

## Global Constraints
- РџСЂРёР»РѕР¶РµРЅРёРµ РѕСЃС‚Р°С‘С‚СЃСЏ РѕС„Р»Р°Р№РЅ; СЃРµС‚СЊ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ С‚РѕР»СЊРєРѕ РїСЂРё РїРѕРґРіРѕС‚РѕРІРєРµ РґР°РЅРЅС‹С… РЅР° СЌС‚Р°РїРµ СЂР°Р·СЂР°Р±РѕС‚РєРё.
- Р‘СЂРµРЅРґ РІС…РѕРґРёС‚ РІ `name`, РїРѕР»Рµ `brand` РЅРµ РґРѕР±Р°РІР»СЏРµС‚СЃСЏ; `name` СѓРЅРёРєР°Р»СЊРЅРѕ.
- Р›СЋР±Р°СЏ РїРѕР·РёС†РёСЏ РёРјРµРµС‚ РІСЃРµ С‡РµС‚С‹СЂРµ С‡РёСЃР»Р°: `kcal`, `protein`, `fat`, `carbs`. Р§Р°СЃС‚РёС‡РЅС‹Рµ РґР°РЅРЅС‹Рµ Р·Р°РїСЂРµС‰РµРЅС‹.
- РљР°Р¶РґР°СЏ РїРѕР·РёС†РёСЏ РёРјРµРµС‚ СЃС‚СЂРѕРєСѓ РІ СЂРµРµСЃС‚СЂРµ РёСЃС‚РѕС‡РЅРёРєРѕРІ СЃ РЅРµРїСѓСЃС‚С‹Рј URL.
- Р’РµСЂСЃРёСЏ Р‘Р” РїРѕРґРЅРёРјР°РµС‚СЃСЏ СЃ 8 РґРѕ 9; СЃС…РµРјР° РЅРµ РјРµРЅСЏРµС‚СЃСЏ; СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёРµ СЃС‚СЂРѕРєРё РЅРµ РїРµСЂРµР·Р°РїРёСЃС‹РІР°СЋС‚СЃСЏ.
- РќРѕРІС‹Рµ РїРѕР»СЊР·РѕРІР°С‚РµР»СЊСЃРєРёРµ СЃС‚СЂРѕРєРё вЂ” РІ `strings.xml`; РЅРѕРІС‹С… СЃС‚СЂРѕРє РґР»СЏ СЌС‚РѕР№ Р·Р°РґР°С‡Рё РЅРµ С‚СЂРµР±СѓРµС‚СЃСЏ.
- РџСЂРѕРІРµСЂРєР° РєР°Р¶РґРѕР№ Р·Р°РґР°С‡Рё: `gradlew test assembleDebug`; С„РёРЅР°Р»СЊРЅС‹Р№ APK вЂ” `apk/CalorieDiary.apk`.

## Review Focus
- РџРѕР·РёС†РёСЏ СЃ РЅРµРїРѕР»РЅС‹РјРё РљР‘Р–РЈ РЅРµ РїРѕРїР°РґР°РµС‚ РІ Р±Р°Р·Сѓ вЂ” С‚РµСЃС‚ `belarusAssetHasCompleteNutrients` РІ Task 1.
- Р”СѓР±Р»РёРєР°С‚С‹ РёРјС‘РЅ РЅРµ СЃРѕР·РґР°СЋС‚СЃСЏ вЂ” С‚РµСЃС‚ `belarusAssetNamesAreUnique` РІ Task 1.
- РЈ РєР°Р¶РґРѕР№ РїРѕР·РёС†РёРё РµСЃС‚СЊ РёСЃС‚РѕС‡РЅРёРє вЂ” С‚РµСЃС‚ `belarusProductsHaveSourceRows` РІ Task 1.
- РџРѕР»СЊР·РѕРІР°С‚РµР»СЊ СЃ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РµР№ Р‘Р” РІРµСЂСЃРёРё 8 РїРѕР»СѓС‡Р°РµС‚ РЅРѕРІС‹Рµ РїСЂРѕРґСѓРєС‚С‹ РїРѕСЃР»Рµ РѕР±РЅРѕРІР»РµРЅРёСЏ вЂ” РїСЂРѕРІРµСЂРєР° РєРѕРґР° РІ Task 2, СЂРµР°Р»СЊРЅС‹Р№ smoke РЅР° СѓСЃС‚СЂРѕР№СЃС‚РІРµ Р·Р° РїРѕР»СЊР·РѕРІР°С‚РµР»РµРј (adb devices РїСѓСЃС‚Рѕ).
- РџРѕРёСЃРє РїРѕ РЅР°Р·РІР°РЅРёСЋ Р±СЂРµРЅРґР° СЂР°Р±РѕС‚Р°РµС‚ Р±РµР· РїСЂР°РІРѕРє UI вЂ” С€Р°Рі РїСЂРѕРІРµСЂРєРё РІ Task 3.

---

### Task 1: Р”Р°РЅРЅС‹Рµ Рё СЂРµРµСЃС‚СЂ РёСЃС‚РѕС‡РЅРёРєРѕРІ

**Files:**
- Create: `app/src/main/assets/products_belarus.json`
- Create: `docs/data/belarusian-products.csv`
- Test: `app/src/test/java/com/veles/caloriediary/BelarusProductsTest.kt`

**Interfaces:**
- Produces: JSON-Р°СЃСЃРµС‚; РєР°Р¶РґР°СЏ СЃС‚СЂРѕРєР° вЂ” РѕР±СЉРµРєС‚ `{"name":"...","kcal":N,"protein":N,"fat":N,"carbs":N}` РІ РѕРґРЅСѓ СЃС‚СЂРѕРєСѓ С„Р°Р№Р»Р°, СЂР°Р·РґРµР»РёС‚РµР»СЊ РѕР±СЉРµРєС‚РѕРІ `,`.
- Produces: CSV СЃ СЂР°Р·РґРµР»РёС‚РµР»РµРј `|` Рё Р·Р°РіРѕР»РѕРІРєРѕРј `name|brand|source_url|source_type|kcal|protein|fat|carbs|checked_on`; РёРјРµРЅР° РЅРµ СЃРѕРґРµСЂР¶Р°С‚ `|`.
- Produces: С‚РµСЃС‚С‹ С‡РёС‚Р°СЋС‚ С„Р°Р№Р»С‹ РїРѕ РїСѓС‚СЏРј РѕС‚ РєР°С‚Р°Р»РѕРіР° РјРѕРґСѓР»СЏ `app`: `src/main/assets/products_belarus.json` Рё `../docs/data/belarusian-products.csv`.

- [x] **Step 1: Write the failing tests**

```kotlin
class BelarusProductsTest {
    private val json = File("src/main/assets/products_belarus.json").readText()
    private val csv = File("../docs/data/belarusian-products.csv").readLines()

    private val rowRegex = Regex("""\{"name":"([^"]+)","kcal":([\d.]+),"protein":([\d.]+),"fat":([\d.]+),"carbs":([\d.]+)\}""")

    @Test fun belarusAssetHasCompleteNutrients() {
        val rows = rowRegex.findAll(json).toList()
        assertTrue(rows.size >= 30)
        assertTrue(rows.all { it.groupValues.drop(2).all { v -> v.toDouble() >= 0.0 } })
    }

    @Test fun belarusAssetNamesAreUnique() {
        val names = rowRegex.findAll(json).map { it.groupValues[1] }.toList()
        assertEquals(names.size, names.toSet().size)
    }

    @Test fun belarusProductsHaveSourceRows() {
        val header = csv.first()
        assertEquals("name|brand|source_url|source_type|kcal|protein|fat|carbs|checked_on", header)
        val sources = csv.drop(1).map { it.split('|') }
        assertTrue(sources.all { it.size == 9 && it[2].startsWith("http") })
        val names = rowRegex.findAll(json).map { it.groupValues[1] }.toSet()
        assertTrue(sources.map { it[0] }.containsAll(names))
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `gradlew :app:testDebugUnitTest --tests '*BelarusProductsTest' --no-daemon`
Expected: FAIL вЂ” С„Р°Р№Р»С‹ РЅРµ РЅР°Р№РґРµРЅС‹.

- [x] **Step 3: Collect data and create both files**

РЎРѕР±СЂР°С‚СЊ РЅРµ РјРµРЅРµРµ 30 РїРѕР·РёС†РёР№ РёР· РѕС„РёС†РёР°Р»СЊРЅРѕРіРѕ РєР°С‚Р°Р»РѕРіР° `lncmilk.by` Рё РѕС‚РєСЂС‹С‚С‹С… РєР°СЂС‚РѕС‡РµРє/С‚Р°Р±Р»РёС† РґР»СЏ Р±СЂРµРЅРґРѕРІ В«РЎР°РІСѓС€РєРёРЅ РїСЂРѕРґСѓРєС‚В», В«РњРѕР»РѕС‡РЅС‹Р№ РјРёСЂВ», В«Р‘Р°Р±СѓС€РєРёРЅР° РєСЂС‹РЅРєР°В». РћР±СЏР·Р°С‚РµР»СЊРЅС‹Рµ С‡РµС‚С‹СЂРµ РїРѕРґС‚РІРµСЂР¶РґС‘РЅРЅС‹С… Р·РЅР°С‡РµРЅРёСЏ СѓР¶Рµ РЅР°Р№РґРµРЅС‹:
- В«Р СЏР¶РµРЅРєР° 3,2% РЎР°РІСѓС€РєРёРЅ РїСЂРѕРґСѓРєС‚В» вЂ” 56,4 / 2,8 / 3,2 / 4,1, `https://proe.info/ru/food/ryazhenka-tm-savushkin-produkt-3-2`;
- В«Р СЏР¶РµРЅРєР° 4% РњРѕР»РѕС‡РЅС‹Р№ РјРёСЂВ» вЂ” 65 / 2,9 / 4 / 4,1, `https://health-diet.ru/base_of_food/sostav/293011.php`;
- В«Р СЏР¶РµРЅРєР° 3% Р‘Р°Р±СѓС€РєРёРЅР° РєСЂС‹РЅРєР°В» вЂ” 54,6 / 3 / 2,8 / 4,1, `https://health-diet.ru/table_calorie_users/713909/`;
- В«Р СЏР¶РµРЅРєР° 4% РњРѕР»РѕС‡РЅР°СЏ РєР°РїРµР»СЊРєР°В» вЂ” 60 / 2,9 / 4 / 4,1, `https://lncmilk.by/katalog/molochnaya-kapelka/ryazhenka-4/`.

РџСЂРёРѕСЂРёС‚РµС‚ РёСЃС‚РѕС‡РЅРёРєРѕРІ РїРѕ СЃРїРµС†РёС„РёРєР°С†РёРё; РїСЂРё СЂР°СЃС…РѕР¶РґРµРЅРёРё РІ СЂРµРµСЃС‚СЂ Р·Р°РЅРѕСЃРёС‚СЃСЏ РІС‹Р±СЂР°РЅРЅРѕРµ Р·РЅР°С‡РµРЅРёРµ Рё URL. РџРѕР·РёС†РёРё Р±РµР· РїРѕР»РЅРѕРіРѕ РљР‘Р–РЈ РЅРµ РґРѕР±Р°РІР»СЏС‚СЊ.

- [x] **Step 4: Run tests to verify they pass**

Run: `gradlew :app:testDebugUnitTest --tests '*BelarusProductsTest' --no-daemon`
Expected: PASS.

- [x] **Step 5: Commit**

`git add app/src/main/assets/products_belarus.json docs/data/belarusian-products.csv app/src/test/java/com/veles/caloriediary/BelarusProductsTest.kt && git commit -m "feat: add sourced belarusian dairy products"`

---

### Task 2: РРјРїРѕСЂС‚ Р°СЃСЃРµС‚Р° РІ Р±Р°Р·Сѓ

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/Db.kt`

**Interfaces:**
- Consumes: `app/src/main/assets/products_belarus.json` РёР· Task 1.
- Produces: `private fun importBelarusProducts(db: SQLiteDatabase)`; РєРѕРЅСЃС‚Р°РЅС‚Р° `DB_VERSION = 9`.

- [x] **Step 1: Add the asset name constant**

Р’ companion object `Db` РґРѕР±Р°РІРёС‚СЊ `private const val ASSET_BELARUS = "products_belarus.json"` СЂСЏРґРѕРј СЃ `ASSET_PRODUCTS`.

- [x] **Step 2: Implement the import method**

РњРµС‚РѕРґ `importBelarusProducts(db: SQLiteDatabase)` РїРѕРІС‚РѕСЂСЏРµС‚ СЃС‚СЂСѓРєС‚СѓСЂСѓ `importExtendedProducts(db)`: С‡РёС‚Р°РµС‚ Р°СЃСЃРµС‚ РєР°Рє UTF-8, РїР°СЂСЃРёС‚ `JSONArray`, РіРѕС‚РѕРІРёС‚ `INSERT OR IGNORE INTO products (name, search_name, kcal, protein, fat, carbs) VALUES (?, ?, ?, ?, ?, ?)`, СЃРІСЏР·С‹РІР°РµС‚ `name.lowercase()` РІ `search_name`, РІС‹РїРѕР»РЅСЏРµС‚ РІ С‚СЂР°РЅР·Р°РєС†РёРё.

- [x] **Step 3: Wire it into create and upgrade**

Р’ `onCreate` РІС‹Р·РІР°С‚СЊ `importBelarusProducts(db)` РїРѕСЃР»Рµ `importExtendedProducts(db)`. РџРѕРґРЅСЏС‚СЊ `DB_VERSION` РґРѕ 9. Р’ `onUpgrade` РґРѕР±Р°РІРёС‚СЊ `if (oldVersion < 9) { importBelarusProducts(db) }`. РќРёРєР°РєРёС… `ALTER TABLE` Рё РїРµСЂРµРїРёСЃС‹РІР°РЅРёСЏ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёС… СЃС‚СЂРѕРє.

- [x] **Step 4: Verify**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0, РІСЃРµ С‚РµСЃС‚С‹ Р·РµР»С‘РЅС‹Рµ. РџСЂРѕРІРµСЂРёС‚СЊ РїРѕ РєРѕРґСѓ, С‡С‚Рѕ `importBelarusProducts(db)` РІС‹Р·С‹РІР°РµС‚СЃСЏ Рё РІ `onCreate`, Рё РІ РІРµС‚РєРµ `oldVersion < 9`, Рё С‡С‚Рѕ РѕРЅ РЅРµ РёР·РјРµРЅСЏРµС‚ СЃСѓС‰РµСЃС‚РІСѓСЋС‰РёРµ СЃС‚СЂРѕРєРё. РџРѕРґРєР»СЋС‡С‘РЅРЅРѕРіРѕ СѓСЃС‚СЂРѕР№СЃС‚РІР° РЅРµС‚ (`adb devices` РїСѓСЃС‚Рѕ), РїРѕСЌС‚РѕРјСѓ С„Р°РєС‚РёС‡РµСЃРєРѕРµ РѕР±РЅРѕРІР»РµРЅРёРµ СЃ РІРµСЂСЃРёРё 8 РѕСЃС‚Р°С‘С‚СЃСЏ СЂСѓС‡РЅС‹Рј smoke Р·Р° РїРѕР»СЊР·РѕРІР°С‚РµР»РµРј.

- [x] **Step 5: Commit**

`git commit -m "feat: import belarusian products on create and upgrade"`

---

### Task 3: Р¤РёРЅР°Р»

**Files:**
- Modify: `E:\OpenCodeModel\BACKLOG.md`, `BACKLOG-closed.md`, `CONTEXT.md`, `PLAN.md`

**Interfaces:**
- Consumes: СЂРµР·СѓР»СЊС‚Р°С‚С‹ Task 1 Рё Task 2.

- [x] **Step 1: Final verification and APK**

Run: `gradlew test assembleDebug --no-daemon`, РїРѕСЃС‡РёС‚Р°С‚СЊ С‚РµСЃС‚С‹, СЃРєРѕРїРёСЂРѕРІР°С‚СЊ APK РІ `apk/CalorieDiary.apk`, Р·Р°С„РёРєСЃРёСЂРѕРІР°С‚СЊ СЂР°Р·РјРµСЂ Рё SHA256.
Expected: BUILD SUCCESSFUL, exit 0, РІСЃРµ С‚РµСЃС‚С‹ Р·РµР»С‘РЅС‹Рµ, APK СЃРѕРІРїР°РґР°РµС‚ СЃРѕ СЃР±РѕСЂРєРѕР№.

- [x] **Step 2: Smoke checks**

РџСЂРѕРІРµСЂРёС‚СЊ РїРѕРёСЃРє РїРѕ В«РЎР°РІСѓС€РєРёРЅВ», В«РњРѕР»РѕС‡РЅС‹Р№ РјРёСЂВ», В«Р‘Р°Р±СѓС€РєРёРЅР° РєСЂС‹РЅРєР°В» Рё В«РњРѕР»РѕС‡РЅР°СЏ РєР°РїРµР»СЊРєР°В»; СѓР±РµРґРёС‚СЊСЃСЏ, С‡С‚Рѕ РІ РїРѕРґСЃРєР°Р·РєР°С… РІРёРґРЅС‹ Р±СЂРµРЅРґРѕРІС‹Рµ РЅР°Р·РІР°РЅРёСЏ Рё РљР‘Р–РЈ. РџСЂРѕРІРµСЂРёС‚СЊ, С‡С‚Рѕ РїРѕСЃР»Рµ РѕР±РЅРѕРІР»РµРЅРёСЏ СЃ РІРµСЂСЃРёРё 8 СЃС‚Р°СЂС‹Рµ РґР°РЅРЅС‹Рµ РЅР° РјРµСЃС‚Рµ.

- [x] **Step 3: Close the task**

Р—Р°РєСЂС‹С‚СЊ BL-097 РІ `BACKLOG.md`/`BACKLOG-closed.md` СЃ РґРѕРєР°Р·Р°С‚РµР»СЊСЃС‚РІР°РјРё; РґРѕР±Р°РІРёС‚СЊ СЂРµС€РµРЅРёРµ РІ `CONTEXT.md`, РѕР±РЅРѕРІРёС‚СЊ `PLAN.md`, РїРµСЂРµРіРµРЅРµСЂРёСЂРѕРІР°С‚СЊ `context-digest.ps1`.

- [x] **Step 4: Commit**

`git commit -m "docs: close belarusian products task"`
