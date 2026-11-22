# Belarusian Products Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить в офлайн-базу дневника калорийности не менее 30 белорусских молочных продуктов с брендом в названии и проверенными КБЖУ.

**Architecture:** Новый JSON-ассет с такой же схемой, как у расширенной базы; реестр источников в CSV; импорт ассета в `Db.kt` через `INSERT OR IGNORE` при создании и при обновлении до версии 9. Схема таблиц не меняется; поиск работает через существующее поле `search_name`.

**Tech Stack:** AGP 9.5.2, Gradle 9.9.2, SDK 37, SQLite, JUnit 4; без AndroidX/Compose и без сетевых запросов из приложения.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-23-belarusian-products-design.md`

## Global Constraints
- Приложение остаётся офлайн; сеть используется только при подготовке данных на этапе разработки.
- Бренд входит в `name`, поле `brand` не добавляется; `name` уникально.
- Любая позиция имеет все четыре числа: `kcal`, `protein`, `fat`, `carbs`. Частичные данные запрещены.
- Каждая позиция имеет строку в реестре источников с непустым URL.
- Версия БД поднимается с 8 до 9; схема не меняется; существующие строки не перезаписываются.
- Новые пользовательские строки — в `strings.xml`; новых строк для этой задачи не требуется.
- Проверка каждой задачи: `gradlew test assembleDebug`; финальный APK — `apk/CalorieDiary.apk`.

## Review Focus
- Позиция с неполными КБЖУ не попадает в базу — тест `belarusAssetHasCompleteNutrients` в Task 1.
- Дубликаты имён не создаются — тест `belarusAssetNamesAreUnique` в Task 1.
- У каждой позиции есть источник — тест `belarusProductsHaveSourceRows` в Task 1.
- Пользователь с существующей БД версии 8 получает новые продукты после обновления — проверка кода в Task 2, реальный smoke на устройстве за пользователем (adb devices пусто).
- Поиск по названию бренда работает без правок UI — шаг проверки в Task 3.

---

### Task 1: Данные и реестр источников

**Files:**
- Create: `app/src/main/assets/products_belarus.json`
- Create: `docs/data/belarusian-products.csv`
- Test: `app/src/test/java/com/veles/caloriediary/BelarusProductsTest.kt`

**Interfaces:**
- Produces: JSON-ассет; каждая строка — объект `{"name":"...","kcal":N,"protein":N,"fat":N,"carbs":N}` в одну строку файла, разделитель объектов `,`.
- Produces: CSV с разделителем `|` и заголовком `name|brand|source_url|source_type|kcal|protein|fat|carbs|checked_on`; имена не содержат `|`.
- Produces: тесты читают файлы по путям от каталога модуля `app`: `src/main/assets/products_belarus.json` и `../docs/data/belarusian-products.csv`.

- [ ] **Step 1: Write the failing tests**

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

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew :app:testDebugUnitTest --tests '*BelarusProductsTest' --no-daemon`
Expected: FAIL — файлы не найдены.

- [ ] **Step 3: Collect data and create both files**

Собрать не менее 30 позиций из официального каталога `lncmilk.by` и открытых карточек/таблиц для брендов «Савушкин продукт», «Молочный мир», «Бабушкина крынка». Обязательные четыре подтверждённых значения уже найдены:
- «Ряженка 3,2% Савушкин продукт» — 56,4 / 2,8 / 3,2 / 4,1, `https://proe.info/ru/food/ryazhenka-tm-savushkin-produkt-3-2`;
- «Ряженка 4% Молочный мир» — 65 / 2,9 / 4 / 4,1, `https://health-diet.ru/base_of_food/sostav/293011.php`;
- «Ряженка 3% Бабушкина крынка» — 54,6 / 3 / 2,8 / 4,1, `https://health-diet.ru/table_calorie_users/713909/`;
- «Ряженка 4% Молочная капелька» — 60 / 2,9 / 4 / 4,1, `https://lncmilk.by/katalog/molochnaya-kapelka/ryazhenka-4/`.

Приоритет источников по спецификации; при расхождении в реестр заносится выбранное значение и URL. Позиции без полного КБЖУ не добавлять.

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew :app:testDebugUnitTest --tests '*BelarusProductsTest' --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

`git add app/src/main/assets/products_belarus.json docs/data/belarusian-products.csv app/src/test/java/com/veles/caloriediary/BelarusProductsTest.kt && git commit -m "feat: add sourced belarusian dairy products"`

---

### Task 2: Импорт ассета в базу

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/Db.kt`

**Interfaces:**
- Consumes: `app/src/main/assets/products_belarus.json` из Task 1.
- Produces: `private fun importBelarusProducts(db: SQLiteDatabase)`; константа `DB_VERSION = 9`.

- [ ] **Step 1: Add the asset name constant**

В companion object `Db` добавить `private const val ASSET_BELARUS = "products_belarus.json"` рядом с `ASSET_PRODUCTS`.

- [ ] **Step 2: Implement the import method**

Метод `importBelarusProducts(db: SQLiteDatabase)` повторяет структуру `importExtendedProducts(db)`: читает ассет как UTF-8, парсит `JSONArray`, готовит `INSERT OR IGNORE INTO products (name, search_name, kcal, protein, fat, carbs) VALUES (?, ?, ?, ?, ?, ?)`, связывает `name.lowercase()` в `search_name`, выполняет в транзакции.

- [ ] **Step 3: Wire it into create and upgrade**

В `onCreate` вызвать `importBelarusProducts(db)` после `importExtendedProducts(db)`. Поднять `DB_VERSION` до 9. В `onUpgrade` добавить `if (oldVersion < 9) { importBelarusProducts(db) }`. Никаких `ALTER TABLE` и переписывания существующих строк.

- [ ] **Step 4: Verify**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0, все тесты зелёные. Проверить по коду, что `importBelarusProducts(db)` вызывается и в `onCreate`, и в ветке `oldVersion < 9`, и что он не изменяет существующие строки. Подключённого устройства нет (`adb devices` пусто), поэтому фактическое обновление с версии 8 остаётся ручным smoke за пользователем.

- [ ] **Step 5: Commit**

`git commit -m "feat: import belarusian products on create and upgrade"`

---

### Task 3: Финал

**Files:**
- Modify: `E:\OpenCodeModel\BACKLOG.md`, `BACKLOG-closed.md`, `CONTEXT.md`, `PLAN.md`

**Interfaces:**
- Consumes: результаты Task 1 и Task 2.

- [ ] **Step 1: Final verification and APK**

Run: `gradlew test assembleDebug --no-daemon`, посчитать тесты, скопировать APK в `apk/CalorieDiary.apk`, зафиксировать размер и SHA256.
Expected: BUILD SUCCESSFUL, exit 0, все тесты зелёные, APK совпадает со сборкой.

- [ ] **Step 2: Smoke checks**

Проверить поиск по «Савушкин», «Молочный мир», «Бабушкина крынка» и «Молочная капелька»; убедиться, что в подсказках видны брендовые названия и КБЖУ. Проверить, что после обновления с версии 8 старые данные на месте.

- [ ] **Step 3: Close the task**

Закрыть BL-097 в `BACKLOG.md`/`BACKLOG-closed.md` с доказательствами; добавить решение в `CONTEXT.md`, обновить `PLAN.md`, перегенерировать `context-digest.ps1`.

- [ ] **Step 4: Commit**

`git commit -m "docs: close belarusian products task"`
