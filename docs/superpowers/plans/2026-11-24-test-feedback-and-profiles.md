# Test Feedback And Profiles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Исправить клавиатуру, подсветку и базу после теста APK, расширить белорусский ассортимент до 60 позиций, улучшить внешний вид и добавить профили с раздельными целями и весом.

**Architecture:** Точечные правки UI в `Dialogs`, `DayView`, `ProductsView`; данные в JSON-ассете и CSV; стили через тему и drawable; профили в одной базе: новая таблица `profiles`, `profile_id` в пользовательских данных, активный профиль в SharedPreferences.

**Tech Stack:** AGP 9.5.2, Gradle 9.9.2, SDK 37, SQLite, JUnit 4; без AndroidX/Compose.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-24-test-feedback-and-profiles-design.md`

## Global Constraints
- Приложение остаётся офлайн; источники используются только при подготовке данных.
- Все названия белорусских продуктов имеют вид «Название (Бренд)».
- Позиция добавляется только при полном КБЖУ и непустом URL в реестре.
- Версия БД поднимается с 9 до 10; перед обновлением сохраняется резервная копия; схема мигрируется с копированием старых строк в профиль id=1.
- Каталог продуктов общий; `entries`, `weights`, `targets`, `product_usage`, `dish_drafts`, вес тела — по активному профилю.
- Новые строки — в `strings.xml`; проверка каждой задачи — `gradlew test assembleDebug`.

## Review Focus
- Клавиатура поднимается при первом нажатии на вес и на поле продукта; ручной smoke, Task 1.
- Любой недавно использованный продукт, а не только блюдо, выделяется голубым; проверка условия в Task 1 и smoke.
- Поиск находит «Молоко 3,2% (Савушкин продукт)»; тест ассета и smoke, Task 2.
- При переключении профиля не смешиваются записи, цели и вес; проверка запросов и smoke, Task 4/5.
- Обновление с версии 9 не теряет данные и не создаёт дубли; проверка миграции по коду, Task 4.
- Пустой БЖУ не мешает сохранить продукт с ручной калорийностью; тест в Task 7.
- Профицит и дефицит меняют цель и БЖУ пропорционально, не портя базовые цели; тест в Task 8.
- Превышение нормы показывается со знаком «+» и процентом больше 100; тест в Task 9.

---

### Task 1: Клавиатура, подсветка и строка еды

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/Dialogs.kt`
- Modify: `app/src/main/java/com/veles/caloriediary/DayView.kt`
- Modify: `app/src/main/java/com/veles/caloriediary/ProductsView.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `Dialog.focusAndShowKeyboard(field: View, selectAll: Boolean = false)` — теперь с явным `InputMethodManager.showSoftInput` в `post`.
- Produces: строка `entry_details` с пятью аргументами: вес, калории, белки, жиры, углеводы.

- [ ] **Step 1: Добавить строку деталей**

В `strings.xml`: `<string name="entry_details">%1$s г • %2$s ккал • Б %3$s / Ж %4$s / У %5$s</string>`.

- [ ] **Step 2: Исправить клавиатуру**

В `Dialogs.kt` импортировать `android.content.Context` и `android.view.inputmethod.InputMethodManager`. После настройки фокуса добавить:
```kotlin
field.post {
    field.requestFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
}
```
В `DayView.openWeightDialog` и `openEditWeightDialog` дополнительно вызвать `dialog.window?.setSoftInputMode(SOFT_INPUT_STATE_VISIBLE)` до `dialog.show()`.

- [ ] **Step 3: Исправить подсветку**

В `ProductsView.ProductAdapter` переименовать конструкторный аргумент `recentDishIds` в `recentIds` и убрать `product.isDish &&` из условия `setTextColor`. В `refreshList()` передавать `db.recentProductIds().toSet()` как есть.

- [ ] **Step 4: Перекомпоновать строку еды**

В `DayView.refresh()` вместо текущего `entryText` ставить только `entry.productName`, а в `entryMacros` — `getString(R.string.entry_details, fmt(entry.grams), fmt(entry.values.kcal), fmt(protein), fmt(fat), fmt(carbs))`.

- [ ] **Step 5: Проверить**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0, все тесты зелёные. Клавиатура и цвет подтверждаются ручным smoke на телефоне.

- [ ] **Step 6: Коммит**

`git commit -m "fix: show keyboard, highlight used products, reorder entry row"`

---

### Task 2: Расширение белорусской базы и скобки в названиях

**Files:**
- Modify: `app/src/main/assets/products_belarus.json`
- Modify: `docs/data/belarusian-products.csv`
- Modify: `app/src/test/java/com/veles/caloriediary/BelarusProductsTest.kt`

**Interfaces:**
- Consumes: те же форматы JSON и CSV, что и в предыдущем плане.
- Produces: не менее 60 позиций; каждое имя содержит `(` и `)`; CSV содержит ровно те же имена.

- [ ] **Step 1: Обновить тесты**

В `belarusAssetHasCompleteNutrients` заменить `rows.size >= 30` на `rows.size >= 60`. Добавить тест:
```kotlin
@Test fun belarusNamesUseParenthesizedBrand() {
    val names = rowRegex.findAll(json).map { it.groupValues[1] }.toList()
    assertTrue(names.all { it.contains('(') && it.endsWith(')') })
}
```

- [ ] **Step 2: RED**

Run: `gradlew :app:testDebugUnitTest --tests '*BelarusProductsTest' --no-daemon`
Expected: FAIL — сейчас 37 позиций и имена без скобок.

- [ ] **Step 3: Переименовать текущие 37 позиций**

Заменить конец названия на скобки: `… Молочная капелька` → `… (Молочная капелька)`, `… Асаблівае` → `… (Асаблівае)`, `… Савушкин продукт` → `… (Савушкин продукт)`, `… Молочный мир` → `… (Молочный мир)`, `… Бабушкина крынка` → `… (Бабушкина крынка)`. В CSV обновить те же имена.

- [ ] **Step 4: Добавить 23 позиции с источниками**

| Название | ккал | Б | Ж | У | Источник |
|---|---|---|---|---|---|
| Молоко 3,2% (Савушкин продукт) | 60 | 2.8 | 3.2 | 4.7 | health-diet.ru/base_of_food/sostav/177751.php |
| Молоко 2,5% (Савушкин продукт) | 53 | 3 | 2.5 | 4.7 | health-diet.ru/base_of_food/sostav/177697.php |
| Сырок (Савушкин продукт) | 251 | 9.8 | 10.7 | 28.8 | health-diet.ru/base_of_food/sostav/343545.php |
| Сырок Сгущенка Вареная (Савушкин продукт) | 359 | 6.5 | 23 | 31.3 | .../343530.php |
| Сырок Коровка (Савушкин продукт) | 368 | 5.6 | 25.6 | 28.8 | .../343528.php |
| Сыр Выдержанный (Савушкин продукт) | 313 | 24.7 | 23.8 | 0 | .../177802.php |
| Сыр Голландский Брусковый (Савушкин продукт) | 332 | 26.3 | 25.2 | 0 | .../177629.php |
| Творог Обезжиренный 0% (Савушкин продукт) | 79 | 18.3 | 0.1 | 1.3 | .../123525.php |
| Сырок Халва (Савушкин продукт) | 352 | 6 | 23 | 30.3 | .../343538.php |
| Сыр Творожный Сливочный (Савушкин продукт) | 195 | 10 | 16.5 | 1.6 | .../343546.php |
| Оптималь Питьевой 0% (Савушкин продукт) | 62 | 3.1 | 0 | 12.5 | .../177817.php |
| Сыр Белый (Савушкин продукт) | 195 | 10 | 16.5 | 1.6 | .../343527.php |
| СОМ (Молочный мир) | 337 | 32 | 1 | 50 | .../102863.php |
| Молоко 1% (Молочный мир) | 40 | 3.1 | 1 | 4.7 | .../168817.php |
| Молоко 3,2% (Молочный мир) | 60 | 3.2 | 3.2 | 4.7 | .../228988.php |
| Молоко 2,7% (Молочный мир) | 54 | 2.8 | 2.7 | 4.7 | .../102859.php |
| Молоко 2,5% (Молочный мир) | 55 | 3.1 | 2.5 | 4.7 | .../168800.php |
| Молоко 3,7% (Молочный мир) | 63 | 2.8 | 3.7 | 4.7 | .../102864.php |
| Мороженое (Молочный мир) | 280 | 4 | 20 | 23 | .../338788.php |
| Творог 1% (Бабушкина крынка) | 80 | 16 | 1 | 1.3 | .../149040.php |
| Сырок Творожный Глазированный (Бабушкина крынка) | 380 | 7 | 25.2 | 32.5 | .../149046.php |
| Фруктовый Наполнитель Инжир (Бабушкина крынка) | 297 | 0 | 0 | 71.9 | .../149073.php |
| Маасдам (Бабушкина крынка) | 330 | 26.8 | 25.2 | 0 | .../149039.php |

Для сокращённых URL в таблице использовать полный префикс `https://health-diet.ru/base_of_food/sostav/`. Тип источника — `calorie_table`, дата `2026-11-24`.

- [ ] **Step 5: GREEN**

Run: `gradlew :app:testDebugUnitTest --tests '*BelarusProductsTest' --no-daemon`
Expected: PASS, не менее 60 позиций.

- [ ] **Step 6: Коммит**

`git commit -m "feat: expand belarusian products and parenthesize brands"`

---

### Task 3: Стилизация

**Files:**
- Create: `app/src/main/res/drawable/meal_border_0.xml` … `meal_border_3.xml`
- Modify: `app/src/main/res/values/attrs.xml`, `colors.xml`, `values-night/colors.xml`, `styles.xml`, `DayView.kt`, `fragment_day.xml`

**Interfaces:**
- Produces: атрибуты `meal0`…`meal3`; `DayView` выбирает рамку группы по индексу приёма пищи.

- [ ] **Step 1: Цвета и атрибуты**

Добавить в `attrs.xml` четыре атрибута `meal0`…`meal3`; в светлой и тёмной темах задать пастельные оттенки для завтра/обеда/ужина/перекуса. Создать четыре drawable с скруглением 8dp и обводкой 2dp в эти цвета.

- [ ] **Step 2: Применить к группам**

В `DayView.refresh()` при создании `box` для группы брать `meal_border_${index % 4}` вместо общего `meal_group_border`. Заголовок группы сделать 16sp полужирным.

- [ ] **Step 3: Подсветить значения**

В `bindBar` красивить `valueId` в цвет соответствующего макроса из атрибутов; подписи значений оставить стандартными.

- [ ] **Step 4: Проверить**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0.

- [ ] **Step 5: Коммит**

`git commit -m "feat: colorize meal groups and progress values"`

---

### Task 4: Профили в базе

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/Db.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: `fun profiles(): List<Profile>` where `Profile(id: Long, name: String, bodyKg: Double)`; `fun activeProfileId(): Long`; `fun setActiveProfile(id: Long)`; `fun addProfile(name: String): Long`; `fun renameProfile(id: Long, name: String): Boolean`; `fun deleteProfile(id: Long): Boolean`; `fun bodyWeight(): Double`; `fun saveBodyWeight(kg: Double)`; `fun adjustmentFor(date: String): Double`; `fun saveAdjustment(date: String, delta: Double)`.

- [ ] **Step 1: Модель и константы**

Добавить `data class Profile` в `Models.kt`. В `Db` поднять `DB_VERSION` до 10.

- [ ] **Step 2: Миграция**

В `onUpgrade` при `oldVersion < 10`: создать `profiles(_id, name UNIQUE, body_kg)`; вставить профиль id=1 с именем из `R.string.default_profile` и весом из старой настройки `body_weight`; добавить `profile_id` в `entries`; пересоздать `weights`, `targets`, `product_usage`, `dish_drafts` с составными ключами по `profile_id`, скопировав старые строки с profile_id=1; создать `adjustments(profile_id INTEGER, date TEXT, kcal_delta REAL NOT NULL DEFAULT 0, PRIMARY KEY(profile_id, date))`. Создать индекс по `entries(profile_id, date)`.

- [ ] **Step 3: Фильтрация запросов**

Во всех методах работы с пользовательскими данными добавить условие `profile_id = activeProfileId()`. В `saveEntry`, `markUsed`, `saveWeight`, `saveDraft` подставлять активный профиль. В `saveTargets` писать `__id = activeProfileId` в таблице `targets` (первичный ключ — profile_id).

- [ ] **Step 4: Методы профилей**

Реализовать контракт Interfaces. `addProfile` создаёт для нового профиля цели по умолчанию. `deleteProfile` запрещает удалять последний профиль и удаляет связанные записи; `renameProfile` ловит конфликт имени.

- [ ] **Step 4а: Переименование старых белорусских позиций**

В той же миграции до версии 10 обновить имена уже импортированных белорусских продуктов: для строк, чьё имя заканчивается на « Молочная капелька», « Асаблівае», « Савушкин продукт», « Молочный мир» или « Бабушкина крынка», заменить суффикс на « (Бренд)» и пересчитать `search_name`. Это нужно, чтобы у обновившихся пользователей бренд оказался в скобках; `INSERT OR IGNORE` сам old строки не изменит.

- [ ] **Step 5: Проверить**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0, все тесты зелёные. Проверить по коду, что нет запроса без фильтра по профилю.

- [ ] **Step 6: Коммит**

`git commit -m "feat: add profiles to database"`

---

### Task 5: Управление профилями в интерфейсе

**Files:**
- Modify: `app/src/main/res/layout/fragment_profile.xml`
- Modify: `app/src/main/java/com/veles/caloriediary/ProfileView.kt`
- Modify: `app/src/main/java/com/veles/caloriediary/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: методы `Db` из Task 4.

- [ ] **Step 1: Элементы управления**

Добавить в `fragment_profile.xml` `Spinner` со списком профилей и четыре кнопки: «Добавить», «Переименовать», «Удалить», «Переключить». Добавить строки в `strings.xml`, включая `default_profile`.

- [ ] **Step 2: Логика ProfileView**

При создании заполнять Spinner из `db.profiles()`, выбирать активный. «Добавить» и «Переименовать» показывают диалог с полем ввода; «Удалить» — диалог подтверждения с предупреждением о потере данных профиля; «Переключить» вызывает `db.setActiveProfile(id)`.

- [ ] **Step 3: Перезагрузка экранов**

После любого изменения профиля вызывать `activity.recreate()` — `MainActivity.onSaveInstanceState` уже сохраняет вкладку и дату. Заменить чтение веса из SharedPreferences на `db.bodyWeight()` и запись на `db.saveBodyWeight(kg)` в обоих местах ProfileView.

- [ ] **Step 4: Проверить**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0.

- [ ] **Step 5: Коммит**

`git commit -m "feat: add profile switcher to profile tab"`

---

### Task 7: Продукт с пустым БЖУ

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/Calculation.kt`
- Modify: `app/src/main/java/com/veles/caloriediary/ProductsView.kt`
- Modify: `app/src/main/java/com/veles/caloriediary/DayView.kt`
- Test: `app/src/test/java/com/veles/caloriediary/CalculationTest.kt`

**Interfaces:**
- Produces: `fun productNutrients(kcal: Double, protein: Double?, fat: Double?, carbs: Double?): Nutrients`.

- [ ] **Step 1: RED-тест**

```kotlin
@Test fun emptyMacrosBecomeZero() {
    val n = productNutrients(235.0, null, null, null)
    assertEquals(235.0, n.kcal, 0.01)
    assertEquals(0.0, n.protein, 0.01)
    assertEquals(0.0, n.fat, 0.01)
    assertEquals(0.0, n.carbs, 0.01)
}
```

- [ ] **Step 2: RED**

Run: `gradlew :app:testDebugUnitTest --tests '*CalculationTest' --no-daemon`
Expected: FAIL — функция не определена.

- [ ] **Step 3: Реализовать и применить**

`productNutrients` подставляет `0.0` вместо `null`. В `ProductsView.showDialog` и в блоке нового продукта `DayView.showAddDialog` требовать только название и калорийность; БЖУ читать через `readNumber`, который уже возвращает `null` для пустого поля, и передавать в `productNutrients`. Сохранение вида `Nutrients(kcal, p, f, c)` заменить на вызов функции.

- [ ] **Step 4: GREEN**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0, все тесты зелёные.

- [ ] **Step 5: Коммит**

`git commit -m "feat: allow custom product without macros"`

---

### Task 8: Профицит и дефицит

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/Calculation.kt`
- Modify: `app/src/main/java/com/veles/caloriediary/DayView.kt`
- Modify: `app/src/main/res/layout/fragment_day.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/veles/caloriediary/CalculationTest.kt`

**Interfaces:**
- Consumes: `Db.adjustmentFor(date)`, `Db.saveAdjustment(date, delta)` из Task 4.
- Produces: `fun adjustTargets(targets: Targets, delta: Double): Targets`.

- [ ] **Step 1: RED-тесты**

```kotlin
@Test fun surplusScalesMacros() {
    val t = adjustTargets(Targets(2000.0, 120.0, 60.0, 250.0), 200.0)
    assertEquals(2200.0, t.kcal, 0.01)
    assertEquals(132.0, t.protein, 0.01)
    assertEquals(66.0, t.fat, 0.01)
    assertEquals(275.0, t.carbs, 0.01)
}
@Test fun deficitScalesMacros() {
    val t = adjustTargets(Targets(2000.0, 120.0, 60.0, 250.0), -200.0)
    assertEquals(1800.0, t.kcal, 0.01)
    assertEquals(108.0, t.protein, 0.01)
    assertEquals(54.0, t.fat, 0.01)
    assertEquals(225.0, t.carbs, 0.01)
}
```

- [ ] **Step 2: RED**

Run: `gradlew :app:testDebugUnitTest --tests '*CalculationTest' --no-daemon`
Expected: FAIL — функция не определена.

- [ ] **Step 3: Реализовать функцию**

`adjustTargets`: если `targets.kcal <= 0` вернуть `targets`; иначе `newKcal = targets.kcal + delta`, коэффициент `newKcal / targets.kcal`, все macro умножить на коэффициент и округлить через `round1`.

- [ ] **Step 4: Поле в «Дне»**

Добавить в `fragment_day.xml` числовое поле `adjustmentInput` с подсказкой «Корректировка нормы, ккал (+/-)» и кнопку сохранения. В `DayView.refresh()` брать `targets = adjustTargets(db.targets(), db.adjustmentFor(selectedDate))`. Кнопка сохраняет введённое значение через `db.saveAdjustment`. Базовые цели в профиле не меняются.

- [ ] **Step 5: GREEN**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0, все тесты зелёные.

- [ ] **Step 6: Коммит**

`git commit -m "feat: add daily calorie surplus and deficit"`

---

### Task 9: Превышение в полосках

**Files:**
- Modify: `app/src/main/java/com/veles/caloriediary/Calculation.kt`
- Modify: `app/src/main/java/com/veles/caloriediary/DayView.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/veles/caloriediary/CalculationTest.kt`

**Interfaces:**
- Produces: `fun excess(current: Double, target: Double): Pair<Boolean, Double>` — первый признак превышения, вторая величина по модулю.

- [ ] **Step 1: RED-тесты**

```kotlin
@Test fun excessIsReportedWhenTargetIsExceeded() {
    assertEquals(Pair(true, 200.0), excess(2200.0, 2000.0))
    assertEquals(Pair(false, 200.0), excess(1800.0, 2000.0))
}
@Test fun percentCanExceedHundred() {
    assertEquals(125, progressPercent(250.0, 200.0))
}
```

- [ ] **Step 2: RED**

Run: `gradlew :app:testDebugUnitTest --tests '*CalculationTest' --no-daemon`
Expected: FAIL — нет функции или старое ожидание 100.

- [ ] **Step 3: Реализовать**

`progressPercent` больше не использует `progress`: `if (target <= 0) 0 else maxOf(0, (current / target * 100).toInt())`. `excess` возвращает `Pair(current > target, abs(current - target))`.

- [ ] **Step 4: Показать в полосках**

В `DayView.bindBar` вместо «осталось» выводить «+X» при превышении. Старая строка `progress_value` заменяется на две: `progress_value` для остатка и `progress_excess` для превышения.

- [ ] **Step 5: GREEN**

Run: `gradlew test assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL, exit 0, все тесты зелёные.

- [ ] **Step 6: Коммит**

`git commit -m "feat: show calorie excess with plus sign"`

---

### Task 10: Финал

**Files:**
- Modify: `E:\OpenCodeModel\BACKLOG.md`, `BACKLOG-closed.md`, `CONTEXT.md`, `PLAN.md`

- [ ] **Step 1: Финальная проверка**

Run: `gradlew test assembleDebug --no-daemon`; посчитать тесты, скопировать APK, зафиксировать размер и SHA256.
Expected: BUILD SUCCESSFUL, exit 0, все тесты зелёные, APK совпадает со сборкой.

- [ ] **Step 2: Smoke**

Проверить на телефоне: клавиатура с первого тапа, голубые использованные продукты, поиск молока Савушкина, новый вид строки еды, цвета групп, переключение профилей и независимость целей/веса.

- [ ] **Step 3: Закрыть задачи**

Закрыть BL-099, BL-100, BL-101, BL-102 и BL-103 с доказательствами, обновить документы и дайджест.

- [ ] **Step 4: Коммит**

`git commit -m "docs: close test feedback and profiles"`
