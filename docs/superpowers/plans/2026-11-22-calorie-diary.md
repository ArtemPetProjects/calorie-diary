# Android calorie diary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Собрать устанавливаемый APK офлайн-дневника калорий с календарём, добавлением продукта по весу, полосками КБЖУ и целями в профиле.

**Architecture:** Одно Activity с тремя view-контроллерами, переключаемыми через видимость; вся логика расчёта в чистых Kotlin-функциях; данные в локальной SQLite через `SQLiteOpenHelper`; без AndroidX, Compose, deprecated Fragment API и сетевых разрешений.

**Tech Stack:** Kotlin через встроенную поддержку AGP 9.5.2 (без отдельного KGP-плагина), Gradle 9.9.2 (wrapper), JDK 21, Android SDK platform 37 / build-tools 37.0.0, minSdk 24, JUnit 4.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-22-calorie-diary-design.md`

## Global Constraints

- Пакет: `com.veles.caloriediary`; проект: `E:\Projects\CalorieDiary`.
- `minSdk 24`, `compileSdk 37`, `targetSdk 37`, Java 17, `namespace = "com.veles.caloriediary"`.
- Никаких зависимостей AndroidX/Compose/сети; только platform View и SQLite.
- Все строки интерфейса — на русском в `res/values/strings.xml`.
- Значения продуктов и итогов хранятся на 100 г и округляются до 1 знака.
- `ANDROID_HOME=E:\AndroidSdk`; SDK ставится только по данному пользователем разрешению.
- Куда класть APK: `E:\Projects\CalorieDiary\apk\CalorieDiary.apk`.

## Review Focus

- Вес пустой или 0 → запись не создаётся, полоски не меняются, нет NaN. Тест: `CalculationTest.gramsRejectZeroAndBlank`.
- Десятичная запятая «12,5» → parses как 12.5. Тест: `CalculationTest.parseGramsAcceptsComma`.
- Цель 0 → прогресс 0, без деления на ноль. Тест: `CalculationTest.progressZeroTarget`.
- Граница месяца/високосный год → ключ даты и недельная полоска переходят корректно. Тест: `CalculationTest.dateKeyPadsAndHandlesLeapDay`.
- Удалённый продукт, который уже был в записях → прошлая запись сохраняет имя и итоги. Проверка: ручной smoke в Task 7.

---

### Task 1: Инструменты и каркас проекта

**Files:**
- Create: `E:\Projects\CalorieDiary\settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `app\build.gradle.kts`, `app\src\main\AndroidManifest.xml`
- Create: `E:\Projects\CalorieDiary\gradle\wrapper\gradle-wrapper.properties`
- Create: `E:\Projects\CalorieDiary\.gitignore`

**Interfaces:**
- Produces: пустой проект, который собирается в `app\build\outputs\apk\debug\app-debug.apk`.

- [ ] **Step 1: Установить Android SDK command-line tools**

Скачать `https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip` (154 957 218 байт) через `Invoke-WebRequest`, распаковать так, чтобы получился `E:\AndroidSdk\cmdline-tools\latest\bin\sdkmanager.bat`. Затем выполнить:

```
$env:ANDROID_HOME='E:\AndroidSdk'
$sdkmgr='E:\AndroidSdk\cmdline-tools\latest\bin\sdkmanager.bat'
$yes=(1..40 | ForEach-Object {'y'}) -join "`n"
$yes | & $sdkmgr --licenses | Out-Null
```
Затем поставить пакеты через актуальный Android CLI (старый `sdkmanager` объявлен устаревшим и не понимает старый синтаксис аргументов):
```
$android='E:\AndroidSdk\cmdline-tools\latest\bin\android.exe'
& $android --sdk='E:\AndroidSdk' sdk install 'platforms;android-37.0'
& $android --sdk='E:\AndroidSdk' sdk install 'build-tools;37.0.0'
& $android --sdk='E:\AndroidSdk' sdk install 'platform-tools'
```

- [ ] **Step 2: Проверить установку SDK**

Run: `Get-ChildItem 'E:\AndroidSdk\platforms' -Name; Get-ChildItem 'E:\AndroidSdk\build-tools' -Name`
Expected: в списке есть `android-37.0` и `37.0.0`.

- [ ] **Step 3: Создать Gradle-проект**

`settings.gradle.kts`: `pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }`, `dependencyResolutionManagement { repositories { google(); mavenCentral() } }`, `rootProject.name = "CalorieDiary"`, `include(":app")`.

`build.gradle.kts` (root): plugin `com.android.application` version `9.5.2` apply false. Отдельный Kotlin-плагин не объявлять: AGP 9.5 включает Kotlin по умолчанию и отвергает `org.jetbrains.kotlin.android`.

`app\build.gradle.kts`: plugin `com.android.application`; `android { namespace = "com.veles.caloriediary"; compileSdk = 37; defaultConfig { applicationId = "com.veles.caloriediary"; minSdk = 24; targetSdk = 37; versionCode = 1; versionName = "1.0" } }`; `java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }`; dependencies: `testImplementation("junit:junit:4.13.2")`.

`gradle.properties`: только `org.gradle.jvmargs=-Xmx2g`. Не задавать `android.useAndroidX=false`: в AGP 9 это deprecated, по умолчанию true; AndroidX-зависимостей в проекте нет.

- [ ] **Step 4: Создать wrapper и проверить сборку**

Run:
```
$env:JAVA_HOME='C:\Users\veles\.jdks\ms-21.0.13.0.2'
$env:ANDROID_HOME='E:\AndroidSdk'
& gradle wrapper --gradle-version 9.9.2
& '.\gradlew.bat' assembleDebug
```
Expected: BUILD SUCCESSFUL, файл `app\build\outputs\apk\debug\app-debug.apk` существует.

- [ ] **Step 5: Инициализировать git и закоммитить**

`git init`, `git add .`, `git commit -m "chore: bootstrap Android project and SDK toolchain"`.

---

### Task 2: Чистая логика расчёта с JVM-тестами

**Files:**
- Create: `app\src\main\java\com\veles\caloriediary\Calculation.kt`
- Test: `app\src\test\java\com\veles\caloriediary\CalculationTest.kt`

**Interfaces:**
- Consumes: ничего.
- Produces: `data class Nutrients(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)`; `fun forWeight(per100: Nutrients, grams: Double): Nutrients`; `fun sum(items: List<Nutrients>): Nutrients`; `fun progress(current: Double, target: Double): Int`; `fun parseGrams(raw: String): Double?`; `fun dateKey(year: Int, month: Int, day: Int): String`. Месяц в `dateKey` — 1-based (`Calendar.MONTH + 1`).

- [ ] **Step 1: Написать падающие тесты**

В `CalculationTest.kt`:
```
@Test fun forWeightScalesPer100() {
  val r = forWeight(Nutrients(200.0, 12.0, 10.0, 30.0), 150.0)
  assertEquals(300.0, r.kcal, 0.01)
  assertEquals(18.0, r.protein, 0.01)
  assertEquals(15.0, r.fat, 0.01)
  assertEquals(45.0, r.carbs, 0.01)
}
@Test fun sumAddsAllFields() { /* Nutrients(100,1,2,3) + Nutrients(50,4,5,6) = 150,5,7,9 */ }
@Test fun gramsRejectZeroAndBlank() {
  assertNull(parseGrams("")); assertNull(parseGrams("0")); assertNull(parseGrams("-5"))
}
@Test fun parseGramsAcceptsComma() { assertEquals(12.5, parseGrams("12,5")!!, 0.01) }
@Test fun progressZeroTarget() { assertEquals(0, progress(500.0, 0.0)); assertEquals(25, progress(50.0, 200.0)); assertEquals(100, progress(2500.0, 2000.0)) }
@Test fun dateKeyPadsAndHandlesLeapDay() {
  assertEquals("2024-02-29", dateKey(2024, 2, 29))
  assertEquals("2026-11-02", dateKey(2026, 11, 2))
}
```

- [ ] **Step 2: Показать, что тесты падают**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests '*CalculationTest'`
Expected: FAIL — unresolved reference `forWeight`.

- [ ] **Step 3: Реализовать Calculation.kt**

`forWeight` умножает каждый field на `grams / 100` и округляет до 1 знака (`kotlin.math.round(x * 10) / 10`). `parseGrams` заменяет запятую на точку, parsers через `toDoubleOrNull`, возвращает null для ≤ 0. `progress` возвращает 0 при `target <= 0`, иначе `(current / target * 100).toInt().coerceIn(0, 100)`. `dateKey` — `%04d-%02d-%02d`.

- [ ] **Step 4: Прогнать тесты**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests '*CalculationTest'`
Expected: PASS, 6 тестов.

- [ ] **Step 5: Закоммитить**

`git add app/src/main app/src/test` + `git commit -m "feat: add nutrient calculation with JVM tests"`.

---

### Task 3: Модели и локальная база SQLite

**Files:**
- Create: `app\src\main\java\com\veles\caloriediary\Models.kt`
- Create: `app\src\main\java\com\veles\caloriediary\Db.kt`
- Create: `app\src\main\java\com\veles\caloriediary\StarterProducts.kt`

**Interfaces:**
- Consumes: `Nutrients` из Task 2 (для полей product и entry).
- Produces:
```
data class Product(val id: Long, val name: String, val per100: Nutrients)
data class Entry(val id: Long, val date: String, val productId: Long, val productName: String, val grams: Double, val values: Nutrients)
data class Targets(val kcal: Double, val protein: Double, val fat: Double, val carbs: Double)
class Db(context: Context) {
  fun products(query: String = ""): List<Product>
  fun saveProduct(p: Product): Long          // id <= 0 → insert, иначе update
  fun deleteProduct(id: Long)
  fun entries(date: String): List<Entry>
  fun addEntry(e: Entry): Long
  fun deleteEntry(id: Long)
  fun targets(): Targets
  fun saveTargets(t: Targets)
}
```

- [ ] **Step 1: Написать модели и помощника базы**

`Models.kt` — три data class выше. `StarterProducts.kt` — `val STARTER_PRODUCTS: List<Product>` из ~60 позиций с русскими именами и значениями на 100 г (молоко, хлеб, яйца, курица, рис, макароны, картофель, яблоко, банан, творог и т.д.), `id = 0`. Значения примерные, редактируемые пользователем.

- [ ] **Step 2: Реализовать схему и CRUD в Db.kt**

`SQLiteOpenHelper("diary.db", null, 1)`: таблицы `products(_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, kcal REAL, protein REAL, fat REAL, carbs REAL)`, `entries(_id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, product_id INTEGER, product_name TEXT, grams REAL, kcal REAL, protein REAL, fat REAL, carbs REAL)`, `targets(_id INTEGER PRIMARY KEY CHECK(_id=1), kcal REAL, protein REAL, fat REAL, carbs REAL)`. В `onCreate` вставить `STARTER_PRODUCTS` и цели по умолчанию 2000/120/60/250. Методы выше работают через `writableDatabase`/`readableDatabase` с `use {}`. `products(query)` фильтрует `name LIKE ?` с `%query%`, регистронезависимо. `addEntry` сохраняет снимок `productName` и всех итогов.

- [ ] **Step 3: Проверить компиляцию**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Закоммитить**

`git commit -m "feat: add SQLite storage and starter products"`.

---

### Task 4: Вкладка «День» и диалог добавления

**Files:**
- Create: `app\src\main\res\layout\fragment_day.xml`, `dialog_entry.xml`, `item_entry.xml`
- Create: `app\src\main\java\com\veles\caloriediary\DayView.kt`
- Modify: `app\src\main\AndroidManifest.xml` (MainActivity as launcher)

**Interfaces:**
- Consumes: `Db`, `Entry`, `Nutrients`, `forWeight`, `parseGrams`, `progress`, `dateKey`, `sum`.
- Produces: `DayView(activity: Activity)` со свойством `view: View`; метод `refresh()` обновляет полоски и список.

- [ ] **Step 1: Сделать каркас MainActivity с тремя кнопками**

`activity_main.xml`: вертикальный `LinearLayout`, `FrameLayout android:id="@+id/container"`, нижний `LinearLayout` с тремя `Button`: `День`, `Продукты`, `Профиль`. `MainActivity.kt`: `onCreate` создаёт `DayView`, `ProductsView`, `ProfileView`, добавляет их `view` в контейнер и переключает видимостью. `android.app.Fragment` не использовать: API помечен deprecated.

- [ ] **Step 2: Сверстать fragment_day.xml**

Вверху `TextView` с месяцем, кнопки «<» и «>», `LinearLayout android:id="@+id/dayStrip"` с семью `Button` (Пн–Вс). Ниже четыре строки: `TextView` подписи, `ProgressBar style="?android:attr/progressBarStyleHorizontal"` и `TextView` «current / target» для калорий, белков, жиров, углеводов. Кнопка «Добавить продукт». `LinearLayout android:id="@+id/entryList"` для строк еды.

- [ ] **Step 3: Реализовать DayView**

При создании выбрать сегодня через `Calendar.getInstance()`: `dateKey(year, month+1, day)`, `selectedDate`. `renderWeek()` рисует 7 кнопок недели, содержащей выбранный день; стрелки сдвигают неделю на ±7 дней; клик по дню выбирает его; долгий клик открывает `DatePickerDialog` с initial year/month/day. `refresh()` берёт `db.entries(selectedDate)`, считает `sum`, для каждого поля вызывает `progress(current, target)` из `db.targets()`, обновляет four ProgressBar и подписи, строит строки из `item_entry.xml`: «название • N г • M ккал» и кнопку «×» → `db.deleteEntry(id)` + `refresh()`.

- [ ] **Step 4: Реализовать диалог добавления**

Кнопка «Добавить продукт» открывает `AlertDialog` из `dialog_entry.xml`: `AutoCompleteTextView` с именами из `db.products()`, `EditText` веса, `TextView` предпросмотра, кнопки «Сохранить»/«Отмена». При вводе веса и выборе продукта предпросмотр показывает `forWeight`. Если введённого имени нет в базе, показать дополнительные поля «ккал/белки/жиры/углеводы на 100 г». Сохранение: при новом продукте сначала `db.saveProduct`, затем `db.addEntry` с `date=selectedDate`, `productName` и снимком; при пустом/нулевом весе — toast «Укажите вес» без записи.

- [ ] **Step 5: Проверить сборку и зафиксировать**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL; `git commit -m "feat: add day tab with calendar and entry dialog"`.

---

### Task 5: Вкладка «Продукты»

**Files:**
- Create: `app\src\main\res\layout\fragment_products.xml`, `dialog_product.xml`, `item_product.xml`
- Create: `app\src\main\java\com\veles\caloriediary\ProductsView.kt`

**Interfaces:**
- Consumes: `Db`, `Product`, `Nutrients`.
- Produces: `ProductsView(activity: Activity)` со свойством `view: View`.

- [ ] **Step 1: Сверстать экран и диалог**

`fragment_products.xml`: `EditText` поиска, `Button` «Добавить продукт», `ListView android:id="@+id/productList"`. `dialog_product.xml`: поля «Название», «Ккал», «Белки», «Жиры», «Углеводы» (все на 100 г) и кнопки «Сохранить»/«Отмена». `item_product.xml`: название и «Н ккал / Б Ж У на 100 г».

- [ ] **Step 2: Реализовать ProductsView**

`onCreateView`: загрузить `db.products("")`, показать в `ListView`. Поиск фильтрует через `db.products(query)`. Клик по строке открывает `dialog_product.xml`, заполненный значениями продукта; «Сохранить» вызывает `db.saveProduct` с тем же id. Кнопка «Добавить продукт» открывает пустой диалог. В каждой строке кнопка «×» вызывает `db.deleteProduct(id)` — прошлая история не меняется, потому что `entries` хранит снимок.

- [ ] **Step 3: Проверить сборку и зафиксировать**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL; `git commit -m "feat: add products tab with editable base"`.

---

### Task 6: Вкладка «Профиль» и цели

**Files:**
- Create: `app\src\main\res\layout\fragment_profile.xml`
- Create: `app\src\main\java\com\veles\caloriediary\ProfileView.kt`

**Interfaces:**
- Consumes: `Db.targets()`, `Db.saveTargets()`.
- Produces: `ProfileView(activity: Activity)` со свойством `view: View`.

- [ ] **Step 1: Сверстать профиль**

Четыре `EditText` с `inputType="numberDecimal"`: калории, белки, жиры, углеводы; подписи на русском; кнопка «Сохранить цели».

- [ ] **Step 2: Реализовать сохранение**

При создании фрагмента заполнить поля из `db.targets()`. По кнопке распарсить четыре числа (запятая → точка), при любом пустом/zero значении показать toast «Заполните все цели» и не сохранять; иначе `db.saveTargets` и toast «Цели сохранены».

- [ ] **Step 3: Проверить сборку и зафиксировать**

Run: `.\gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL; `git commit -m "feat: add profile tab with daily targets"`.

---

### Task 7: Финальная сборка и передача APK

**Files:**
- Modify: `E:\OpenCodeModel\BACKLOG.md`, `E:\OpenCodeModel\CONTEXT.md`

**Interfaces:**
- Consumes: результат `assembleDebug` из Task 6.

- [ ] **Step 1: Прогнать все тесты и сборку**

Run: `.\gradlew.bat test assembleDebug`
Expected: BUILD SUCCESSFUL, 6 JVM-тестов PASS.

- [ ] **Step 2: Скопировать APK**

```
New-Item -ItemType Directory 'E:\Projects\CalorieDiary\apk' -Force | Out-Null
Copy-Item 'app\build\outputs\apk\debug\app-debug.apk' 'E:\Projects\CalorieDiary\apk\CalorieDiary.apk' -Force
Get-Item 'E:\Projects\CalorieDiary\apk\CalorieDiary.apk' | Select-Object FullName,Length
```

- [ ] **Step 3: Передать чек-лист ручной проверки пользователю**

Проверить на телефоне: APK ставится; открывается сегодня; добавление «молоко 200 г» меняет полоски; запись удаляется; новая цель в профиле сохраняется и влияет на полоски; продукт можно создать из диалога; удалённый продукт не портит старую запись; приложение работает без сети.

- [ ] **Step 4: Зафиксировать результат**

В `BACKLOG.md`: BL-012 → `[x]`, дата, путь APK, команда сборки, код возврата. В `CONTEXT.md`: решение о стеке и пути APK.

- [ ] **Step 5: Закоммитить документы**

`git commit -m "docs: record BL-012 delivery"`.
