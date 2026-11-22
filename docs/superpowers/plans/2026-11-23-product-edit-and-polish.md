# Product Edit And Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Явное редактирование продуктов и блюд, автокалории из БЖУ для своих продуктов, подсветка недавних блюд, удобные диалоги с клавиатурой, проценты в полосках и точечная визуальная доводка.

**Architecture:** Чистые функции в `Calculation.kt` (`autoFilledKcal`, `progressPercent`) покрываются JVM-тестами; общий helper `Dialogs.kt` поднимает клавиатуру во всех диалогах; UI-правки локализуются в `ProductsView`, `DishDialog`, `DayView`, `SuggestionAdapter` и ресурсах. Правка данных базы — только изменение JSON-ассета, без миграции БД.

**Tech Stack:** AGP 9.5.2, Gradle 9.9.2, SDK 37, minSdk 24, SQLite, JUnit 4, без AndroidX/Compose.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-23-product-edit-and-polish-design.md`

## Global Constraints
- Приложение остаётся офлайн; сеть, синхронизация и облако не добавляются.
- Версия БД не меняется; миграция не нужна. Изменения ассета влияют только на новые установки.
- Новые пользовательские строки — только в `res/values/strings.xml`.
- Новые цвета — в `res/values/colors.xml` и `res/values-night/colors.xml`, где нужны разные значения; темовые атрибуты — в `res/values/attrs.xml`.
- Проверка каждого task: `gradlew test assembleDebug`; финальный APK копируется в `apk/CalorieDiary.apk`.
- Коммиты после каждой рабочей версии; только свои файлы.

## Review Focus
- Ручная калорийность не перетирается автоподстановкой — тест `manualKcalIsNeverOverwritten` в Task 1.
- Неполный БЖУ не создаёт ложные калории — тест `incompleteMacrosDoNotProduceKcal` в Task 1.
- Процент при нулевой цели и превышении цели — тест `progressPercentHandlesZeroAndOverflow` в Task 1.
- Клавиатура появляется при открытии каждого окна с полем — проверка в Task 4/5 и ручной smoke.
- Старые пользовательские данные и «Водка» 235 ккал не портятся — проверка в Task 6.

---

### Task 1: Чистые функции и helper клавиатуры

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/Calculation.kt`
- Test: `app/src/test/java/com/veles/caloriediary/CalculationTest.kt`
- Create: `app/src/main/java/com/veles/caloriediary/Dialogs.kt`

**Interfaces:**
- Produces: `fun autoFilledKcal(autoFilled: Boolean, current: String, protein: Double?, fat: Double?, carbs: Double?): Double?` — новое значение калорий или `null`, если поле менять не надо.
- Produces: `fun progressPercent(current: Double, target: Double): Int` — 0 при цели ≤ 0, иначе `current/target*100`, ограниченное 0..100.
- Produces: `fun android.app.Dialog.focusAndShowKeyboard(field: android.view.View, selectAll: Boolean = false)` — в `OnShowListener` выставляет window soft input mode, переводит фокус на `field`, при `selectAll` выделяет весь текст.

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun manualKcalIsNeverOverwritten() {
    assertNull(autoFilledKcal(false, "235", 0.0, 0.0, 0.0))
}
@Test fun incompleteMacrosDoNotProduceKcal() {
    assertNull(autoFilledKcal(false, "", 10.0, null, 30.0))
}
@Test fun autoFilledKcalIsRecalculatedWhenMacrosChange() {
    assertEquals(90.0, autoFilledKcal(true, "270.0", 5.0, 5.0, 5.0))
}
@Test fun blankKcalIsFilledFromMacros() {
    assertEquals(270.0, autoFilledKcal(false, "", 10.0, 20.0, 30.0))
}
@Test fun progressPercentHandlesZeroAndOverflow() {
    assertEquals(0, progressPercent(100.0, 0.0))
    assertEquals(25, progressPercent(50.0, 200.0))
    assertEquals(100, progressPercent(250.0, 200.0))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew :app:testDebugUnitTest --tests '*CalculationTest' --no-daemon`
Expected: FAIL with unresolved references `autoFilledKcal`, `progressPercent`.

- [ ] **Step 3: Implement the helpers**

В `Calculation.kt`: `autoFilledKcal` возвращает `null`, если какой-то из БЖУ аргументов `null`; если `!autoFilled && current.isNotBlank()` — `null`; иначе `kcalFromMacros(protein!!, fat!!, carbs!!)`. `progressPercent` — по контракту выше, без округления до ближайшего целого (отсечение `toInt`).
В `Dialogs.kt`: extension function ставит `SOFT_INPUT_STATE_VISIBLE` в `dialog.window` и переводит фокус на поле; для `EditText` при `selectAll` дополнительно `setSelection(0, text.length)`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew :app:testDebugUnitTest --tests '*CalculationTest' --no-daemon`
Expected: PASS.

- [ ] **Step 5: Commit**

`git add app/src/main/java/com/veles/caloriediary/Calculation.kt app/src/main/java/com/veles/caloriediary/Dialogs.kt app/src/test/java/com/veles/caloriediary/CalculationTest.kt && git commit -m "feat: add calorie and progress helpers"`

---

### Task 2: Явное редактирование и подсветка недавних блюд

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/ProductsView.kt`
- Modify: `app/src/main/res/layout/item_product.xml`
- Modify: `app/src/main/res/values/attrs.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values-night/colors.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `Db.recentProductIds(): List<Long>` (порядок `used_at DESC`).
- Produces: в `item_product.xml` кнопка `editProductButton`; в `ProductAdapter` — аргумент `recentDishIds: Set<Long>`; атрибут темы `recentDishText`.

- [ ] **Step 1: Add strings and colors**

В `strings.xml`: `<string name="edit">Изменить</string>`. В `colors.xml`: `recent_dish_light = #0288D1`, `recent_dish_dark = #81D4FA`. В `attrs.xml`: `<attr name="recentDishText" format="color" />`. В обеих темах `styles.xml` связать `recentDishText` с нужным цветом.

- [ ] **Step 2: Add the edit button to the row**

В `item_product.xml` добавить `Button style="@style/AppButton"` с id `editProductButton`, текстом `@string/edit`, `minWidth=0dp`, рядом с к buttons удаления; существующий `productText` оставить с `layout_weight=1`.

- [ ] **Step 3: Wire edit and highlight in ProductsView**

В `ProductAdapter` принять `recentDishIds: Set<Long>`; в `getView` для `product.isDish && product.id in recentDishIds` присвоить `productText.setTextColor(...)` из атрибута `recentDishText`. Кнопка `editProductButton` открывает тот же диалог, что и тап по строке (`DishDialog` для блюда, `showDialog(product)` для продукта). `refreshList()` передаёт `db.recentProductIds().toSet()`.

- [ ] **Step 4: Verify**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0; все тесты зелёные.

- [ ] **Step 5: Commit**

`git commit -m "feat: add explicit product edit and recent dish highlight"`

---

### Task 3: Автокалории из БЖУ в окнах продукта

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/ProductsView.kt`
- Modify: `app/src/main/java/com/veles/caloriediary/DayView.kt`

**Interfaces:**
- Consumes: `autoFilledKcal(...)` из Task 1, `focusAndShowKeyboard(...)` из Task 1.

- [ ] **Step 1: Auto-fill in the product dialog**

В `ProductsView.showDialog(product)` завести `var autoKcal = product == null`, повесить `TextWatcher` на `proteinInput`, `fatInput`, `carbsInput`: прочитать числа через существующий `readNumber`, вызвать `autoFilledKcal(autoKcal, kcalInput.text.toString(), ...)`, при не-`null` результате записать `fmt(value)` в `kcalInput`. Повесить отдельный watcher на `kcalInput`, который выставляет `autoKcal = false`, если пользователь изменил поле вручную (не из кода watcher БЖУ — использовать флаг `updatingKcal`).

- [ ] **Step 2: Auto-fill for the new product inside the entry dialog**

В `DayView.showAddDialog()` повесить такой же watcher на `newProteinInput`, `newFatInput`, `newCarbsInput` с полем `newKcalInput` и своим флагом `newAutoKcal`; при create-пути сохраняется поведение «новый продукт» из текущего кода.

- [ ] **Step 3: Keyboard in product dialog**

В `showDialog` внутри `setOnShowListener` вызвать `dialog.focusAndShowKeyboard(nameInput, selectAll = product != null)`.

- [ ] **Step 4: Verify**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0.

- [ ] **Step 5: Commit**

`git commit -m "feat: derive calories from macros for custom products"`

---

### Task 4: Список ингредиентов блюда и клавиатура конструктора

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/SuggestionAdapter.kt`
- Modify: `app/src/main/java/com/veles/caloriediary/DishDialog.kt`

**Interfaces:**
- Consumes: `focusAndShowKeyboard(...)` из Task 1.
- Produces: `SuggestionAdapter(..., largerText: Boolean = false)`; при `largerText` размеры текста подсказки 16sp/13sp через `TextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, ...)`.

- [ ] **Step 1: Add the largerText option**

Добавить параметр конструктора `largerText: Boolean = false`; в `getView` при включённом флаге ставить 16sp для `suggestionName` и 13sp для `suggestionInfo`, иначе оставить значения из layout (15/12sp).

- [ ] **Step 2: Narrow the ingredient dropdown**

В `DishDialog` создавать адаптер с `largerText = true`. В `IngredientRow.init` после `setAdapter` в `productInput.post` взять фактическую ширину поля и задать `productInput.dropDownWidth = (width * 0.92f).toInt()` только если `width > 0`; поведение поиска (`threshold`) не менять.

- [ ] **Step 3: Keyboard in DishDialog**

В `setOnShowListener` конструктора вызвать `dialog.focusAndShowKeyboard(nameInput, selectAll = dishId > 0)`.

- [ ] **Step 4: Verify**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0.

- [ ] **Step 5: Commit**

`git commit -m "feat: tune dish ingredient suggestions and dialog keyboard"`

---

### Task 5: Клавиатура окон дня и проценты в полосках

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/DayView.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `progressPercent(...)` и `focusAndShowKeyboard(...)` из Task 1.
- Produces: строка `progress_value` с четырьмя аргументами: съедено, цель, процент, остаток.

- [ ] **Step 1: Add the progress string**

В `strings.xml`: `<string name="progress_value">%1$s / %2$s · %3$d%% · осталось %4$s</string>`.

- [ ] **Step 2: Show percent and remaining**

В `bindBar` вычислять `percent = progressPercent(current, target)` и `remaining = maxOf(0.0, target - current)`; setValue text через `getString(R.string.progress_value, fmt(current), fmt(target), percent, fmt(remaining))`.

- [ ] **Step 3: Keyboard in all day dialogs**

В `openWeightDialog`, `openEditWeightDialog` и `showAddDialog` внутри `setOnShowListener` вызвать `dialog.focusAndShowKeyboard(input, selectAll = true)` для числового поля; в `showAddDialog` — для `productInput` с `selectAll = false`. Существующий `SOFT_INPUT_STATE_VISIBLE` в `showAddDialog` оставить в `OnShowListener`, дубли вызовов убрать.

- [ ] **Step 4: Verify**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0.

- [ ] **Step 5: Commit**

`git commit -m "feat: show progress percent and open keyboards immediately"`

---

### Task 6: Правка базы продуктов

**Files:**
- Modify: `app/src/main/assets/products_extended.json`

**Interfaces:**
- Consumes: аудит из спеки: «Водка» 0 → 235; три позиции с 0,1–0,2 ккал при нулевом БЖУ → 0.

- [ ] **Step 1: Fix the three values**

В JSON заменить `{"name":"Водка","kcal":0,...}` на `kcal:235`; для трёх позиций с нулевым БЖУ и калорийностью 0,1/0,2 поставить 0. Остальные записи не трогать.

- [ ] **Step 2: Verify with a read-only check**

Run: PowerShell-проверка, которая парсит JSON и утверждает: `(@($items | Where-Object { $_.name -eq 'Водка' -and $_.kcal -ne 235 }).Count -eq 0)` и `(@($items | Where-Object { $_.protein -eq 0 -and $_.fat -eq 0 -and $_.carbs -eq 0 -and $_.kcal -gt 0 }).Count -eq 0)`.
Expected: True, True. Затем `gradlew test assembleDebug --no-daemon` — код 0.

- [ ] **Step 3: Commit**

`git commit -m "fix: correct vodka and rounding in product database"`

---

### Task 7: Визуальная доводка и финал

**Files:**
- Create: `app/src/main/res/drawable/row_card.xml`
- Modify: `app/src/main/res/layout/item_product.xml`, `app/src/main/res/layout/item_entry.xml`, `app/src/main/res/values/styles.xml`, `app/src/main/res/values/strings.xml`
- Modify: `E:\OpenCodeModel\BACKLOG.md`, `BACKLOG-closed.md`, `CONTEXT.md`, `PLAN.md`

**Interfaces:**
- Consumes: все предыдущие task.

- [ ] **Step 1: Card rows and spacing**

Создать `row_card.xml`: скруглённый прямоугольник 8dp с тонкой обводкой цвета `?attr/progressTrack` и прозрачным фоном; повесить как `android:background` на корневые `LinearLayout` в `item_product.xml` и `item_entry.xml`, добавить `layout_marginVertical=2dp`. В `styles.xml` поднять заголовки и второстепенный текст на 1sp в рамках существующих размеров.

- [ ] **Step 2: Final verification**

Run: `gradlew test assembleDebug --no-daemon`; посчитать результаты XML-тестов и скопировать APK.
Expected: BUILD SUCCESSFUL, exit 0, все тесты зелёные; `apk/CalorieDiary.apk` скопирован, размер и SHA256 зафиксированы.

- [ ] **Step 3: Close the work**

Закрыть BL-089–BL-096 в `BACKLOG.md`/`BACKLOG-closed.md` с доказательствами (команда, код возврата, путь APK), обновить `CONTEXT.md` и `PLAN.md`, перегенерировать `context-digest.ps1`.

- [ ] **Step 4: Commit**

`git commit -m "feat: polish product and meal lists"`
