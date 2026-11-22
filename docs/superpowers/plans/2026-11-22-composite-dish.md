# Composite dish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить конструктор своего блюда с пересчётом по итоговому весу, каталог своих продуктов/блюд, расширить локальную базу продуктов и готовых блюд, добавить тёмную тему и визуально улучшить кнопки и полоски.

**Architecture:** Блюдо хранится в таблице `products` с флагом `is_dish`; ингредиенты — в `dish_items` снимками; расчёт вынесен в чистую функцию; существующий диалог добавления в день и полоски не меняются.

**Tech Stack:** AGP 9.5.2 встроенный Kotlin, Gradle 9.9.2 wrapper, SDK 37, platform View, SQLite, JUnit 4.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-22-composite-dish-design.md`

## Global Constraints

- `DB_VERSION = 2`; миграция из версии 1 сохраняет продукты, записи дня и цели.
- APK остаётся офлайн, без AndroidX/Compose/сети; только platform View и SQLite.
- Все новые строки интерфейса — в `res/values/strings.xml`.
- Итоговый вес блюда > 0; вес ингредиента > 0; пустой список ингредиентов не сохраняется.
- Команда проверки: `gradlew test assembleDebug --no-daemon` с `JAVA_HOME=C:\Users\veles\.jdks\ms-21.0.13.0.2`, `ANDROID_HOME=E:\AndroidSdk`.

## Review Focus

- Итоговый вес 0 или отрицательный → `dishPer100` возвращает null, record не создаётся, нет NaN/деления на ноль. Тест: `CalculationTest.dishPer100RejectsNonPositiveFinalWeight`.
- Пустой список ингредиентов → null, блюдо не сохраняется. Тест: `CalculationTest.dishPer100RejectsEmptyIngredients`.
- Итоговый вес отличается от суммы ингредиентов → пересчёт по новому весу. Тест: `CalculationTest.dishPer100ScalesToFinalWeight`.
-catalog сохранённого блюда: редактирование/удаление блюда не меняет прошедшие записи дня — записи хранят снимок. Тест/проверка: ручной smoke + код `Db.addEntry`.
- Миграция БД сохраняет старые данные. Проверка: открыть существующий diary.db после обновления приложения, продукты и записи на месте.

---

### Task 1: Чистый расчёт блюда и модель ингредиента

**Files:**
- Modify: `app\src\main\java\com\veles\caloriediary\Calculation.kt`
- Modify: `app\src\main\java\com\veles\caloriediary\Models.kt`
- Test: `app\src\test\java\com\veles\caloriediary\CalculationTest.kt`

**Interfaces:**
- Consumes: `Nutrients`, `forWeight`, `sum`, `round1` из текущего `Calculation.kt`.
- Produces: `data class DishIngredient(val productId: Long, val productName: String, val grams: Double, val per100: Nutrients)`; `fun dishPer100(items: List<DishIngredient>, finalGrams: Double): Nutrients?`.

- [ ] **Step 1: RED-тесты**

```
@Test fun dishPer100ScalesToFinalWeight() {
  val items = listOf(
    DishIngredient(1, "Молоко", 200.0, Nutrients(60.0, 3.0, 3.2, 4.7)),
    DishIngredient(2, "Рис", 100.0, Nutrients(116.0, 2.6, 0.3, 25.0))
  )
  val r = dishPer100(items, 150.0)!!
  assertEquals(157.3, r.kcal, 0.05)
}
@Test fun dishPer100RejectsNonPositiveFinalWeight() {
  assertNull(dishPer100(listOf(DishIngredient(1,"Молоко",100.0,Nutrients(60.0,3.0,3.2,4.7))), 0.0))
  assertNull(dishPer100(listOf(DishIngredient(1,"Молоко",100.0,Nutrients(60.0,3.0,3.2,4.7))), -5.0))
}
@Test fun dishPer100RejectsEmptyIngredients() { assertNull(dishPer100(emptyList(), 200.0)) }
```

- [ ] **Step 2: Показать падение**

Run: `gradlew :app:testDebugUnitTest --tests '*CalculationTest'`
Expected: FAIL — unresolved `DishIngredient`/`dishPer100`.

- [ ] **Step 3: Реализовать**

`dishPer100`: если `items.isEmpty()` или `finalGrams <= 0` → null; иначе `sum(items.map { forWeight(it.per100, it.grams) })` поделить на `finalGrams / 100` и округлить до 1 знака. Добавить `DishIngredient` в `Models.kt`.

- [ ] **Step 4: GREEN**

Run: `gradlew test`
Expected: BUILD SUCCESSFUL, 10/10 тестов.

- [ ] **Step 5: Коммит**

`git commit -m "feat: add pure dish calculation with tests"`.

---

### Task 2: Миграция БД и CRUD блюд

**Files:**
- Modify: `app\src\main\java\com\veles\caloriediary\Db.kt`
- Modify: `app\src\main\java\com\veles\caloriediary\Models.kt`

**Interfaces:**
- Consumes: `DishIngredient`, `Product`, `Nutrients`, `STARTER_PRODUCTS`.
- Produces: `fun products(query: String = "", onlyCustom: Boolean = false, onlyDishes: Boolean = false): List<Product>`; `fun saveDish(name: String, finalGrams: Double, per100: Nutrients, items: List<DishIngredient>): Long`; `fun dishItems(dishId: Long): List<DishIngredient>`; `fun deleteProduct(id: Long)` удаляет связанные `dish_items`.

- [ ] **Step 1: Схема версии 2**

`DB_VERSION = 2`. В `onCreate` добавить колонки `is_dish INTEGER NOT NULL DEFAULT 0`, `is_custom INTEGER NOT NULL DEFAULT 0` и таблицу `dish_items(_id INTEGER PRIMARY KEY AUTOINCREMENT, dish_id INTEGER, product_id INTEGER, product_name TEXT, grams REAL, kcal REAL, protein REAL, fat REAL, carbs REAL)`. В `onUpgrade` выполнить два `ALTER TABLE` и `CREATE TABLE`, не удаляя данные.

- [ ] **Step 2: CRUD и фильтры**

`products` принимает флаги и фильтрует `is_custom=1` / `is_dish=1`. `saveDish` сохраняет продукт с флагами и снимки ингредиентов в `dish_items` в транзакции; при ошибке имени возвращает -1. `dishItems` читает снимки по `dish_id`. `deleteProduct` удаляет и `dish_items` для этого id.

- [ ] **Step 3: Проверка**

Run: `gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Коммит**

`git commit -m "feat: migrate database and add dish storage"`.

---

### Task 3: Конструктор блюда в «Дне»

**Files:**
- Create: `app\src\main\res\layout\dialog_dish.xml`, `item_dish_ingredient.xml`
- Create: `app\src\main\java\com\veles\caloriediary\DishDialog.kt`
- Modify: `app\src\main\java\com\veles\caloriediary\DayView.kt`, `res\values\strings.xml`

**Interfaces:**
- Consumes: `Db`, `DishIngredient`, `dishPer100`, `forWeight`, `parseGrams`.
- Produces: `class DishDialog(private val activity: Activity, private val db: Db, private val dishId: Long = 0L, private val onSaved: (Long, Double) -> Unit)` с методом `show()`; `onSaved` получает id блюда и итоговый вес.

- [ ] **Step 1: Разметка**

`item_dish_ingredient.xml`: автодополнение продукта, поле веса, кнопка удаления строки. `dialog_dish.xml`: заголовок, LinearLayout списка, кнопка «Добавить ингредиент», поле «Итоговый вес блюда, г», TextView предпросмотра.

- [ ] **Step 2: Логика диалога**

При `dishId > 0` предзаполнить ингредиенты из `db.dishItems(dishId)` и имя из продукта. При добавлении/изменении строки пересчитать `dishPer100` и показать «Ккал/Б/Ж/У на 100 г». Сохранение: проверить имя, непустой список, итоговый вес > 0; вызвать `db.saveDish`, при `id < 0` toast «Такой продукт уже есть», иначе `onSaved(id, finalGrams)` и закрыть.

- [ ] **Step 3: Кнопка в DayView**

Добавить кнопку «Собрать блюдо» в `fragment_day.xml`; в `DayView` по клику открыть `DishDialog`; в `onSaved` опционально добавить запись в выбранный день по итоговому весу и обновить полоски. Предусмотреть флаг «добавить в день» в диалоге.

- [ ] **Step 4: Проверка и коммит**

Run: `gradlew assembleDebug`; Expected: BUILD SUCCESSFUL.
`git commit -m "feat: add composite dish dialog"`.

---

### Task 4: Каталог «Мои продукты/блюда»

**Files:**
- Modify: `app\src\main\res\layout\fragment_products.xml`, `strings.xml`
- Modify: `app\src\main\java\com\veles\caloriediary\ProductsView.kt`

**Interfaces:**
- Consumes: `Db.products(query, onlyCustom, onlyDishes)`, `DishDialog`, `db.deleteProduct`.
- Produces: переключатель трёх режимов; редактирование блюда через `DishDialog`.

- [ ] **Step 1: Переключатель**

Добавить три кнопки: «Все», «Мои продукты», «Мои блюда». При клике вызвать `refreshList()` с нужными флагами.

- [ ] **Step 2: Редактирование и удаление**

Клик по строке блюда открывает `DishDialog` с `dishId`; удаление — подтверждение и `db.deleteProduct`. Для обычного продукта оставить текущий диалог. Обновить список после сохранения.

- [ ] **Step 3: Проверка и коммит**

Run: `gradlew test assembleDebug`; Expected: BUILD SUCCESSFUL.
`git commit -m "feat: add own products and dishes catalog"`.

---

### Task 5: Расширение локальной базы продуктов и блюд (BL-051)

**Files:**
- Modify: `app\src\main\java\com\veles\caloriediary\StarterProducts.kt`

**Interfaces:**
- Produces: расширенный `STARTER_PRODUCTS`: не менее 200 позиций, включая готовые блюда (супы, каши, салаты, выпечка, напитки), с примерными значениями на 100 г.

- [ ] **Step 1: Дополнить набор**

Добавить позиции локально, без сети и внешних датасетов. Сохранить уникальные русские названия и формат `Product(id, name, Nutrients)`. Значения примерные; пользователь может править их в приложении. Не добавлять продукты с нулевой калорийностью там, где она реально есть.

- [ ] **Step 2: Проверить уникальность**

Run: `gradlew :app:testDebugUnitTest`; Expected: BUILD SUCCESSFUL, тесты зелёные. Проверить, что `onCreate` не падает на дубли имён: при необходимости заменить `db.insert` на `insertWithOnConflict(..., CONFLICT_IGNORE)` для стартовых продуктов.

- [ ] **Step 3: Коммит**

`git commit -m "feat: expand local product database"`.

---

### Task 6: Тёмная тема (BL-052)

**Files:**
- Create: `app\src\main\res\values-night\colors.xml`
- Create: `app\src\main\res\values\colors.xml`
- Modify: `app\src\main\res\values\styles.xml`, `ProfileView.kt`, `strings.xml`

**Interfaces:**
- Produces: тема через платформенные ресурсы `values`/`values-night`; переключатель в профиле, сохраняемый в `SharedPreferences`.

- [ ] **Step 1: Ресурсы темы**

Описать цвета фона, текста, кнопок и полосок в `values/colors.xml` и `values-night/colors.xml`. Стили кнопок и `ProgressBar` ссылаются на эти цвета.

- [ ] **Step 2: Переключатель**

В `fragment_profile.xml` добавить `Switch` «Тёмная тема»; в `ProfileView` читать/писать флаг в `SharedPreferences` и вызывать `activity.recreate()` при изменении.

- [ ] **Step 3: Проверка и коммит**

Run: `gradlew assembleDebug`; Expected: BUILD SUCCESSFUL.
`git commit -m "feat: add dark theme"`.

---

### Task 7: Визуальная полировка кнопок и полосок (BL-053)

**Files:**
- Modify: `app\src\main\res\values\styles.xml`, `res\layout\fragment_day.xml`, `res\layout\activity_main.xml`

**Interfaces:**
- Consumes: цвета из Task 6.
- Produces: кнопки со скруглёнными углами и отступами, полоски с цветом по типу поля (ккал/белки/жиры/углеводы), подписи «current / target» читаемыми.

- [ ] **Step 1: Стили**

Добавить drawable с `shape` и `corners` для кнопок и фона полосок; в `styles.xml` задать стили `PrimaryButton`, `TabButton`, `ProgressTrack`. Использовать `android:progressTint` для цвета заполнения.

- [ ] **Step 2: Применить**

Назначить стили кнопкам вкладок, «Добавить продукт», «Собрать блюдо», «Сохранить» и четырём `ProgressBar`. Не менять логику расчёта.

- [ ] **Step 3: Проверка и коммит**

Run: `gradlew test assembleDebug`; Expected: BUILD SUCCESSFUL.
`git commit -m "feat: polish buttons and progress bars"`.

---

### Task 8: Финальная сборка и учёт

**Files:**
- Modify: `E:\OpenCodeModel\BACKLOG.md`, `CONTEXT.md`, `PLAN.md`

- [ ] **Step 1: Полный прогон**

Run: `gradlew test assembleDebug --no-daemon`; Expected: BUILD SUCCESSFUL, код 0, 10/10 тестов.

- [ ] **Step 2: Скопировать APK**

`app\build\outputs\apk\debug\app-debug.apk` → `E:\Projects\CalorieDiary\apk\CalorieDiary.apk`.

- [ ] **Step 3: Закрыть задачи**

BL-055/BL-056 перенести в `BACKLOG-closed.md` с датой, командой, кодом, путём APK; в `CONTEXT.md` — решение хранить блюдо как продукт с флагом; в `PLAN.md` — текущий шаг.

- [ ] **Step 4: Коммит**

`git commit -m "docs: record BL-055 and BL-056 delivery"`.
