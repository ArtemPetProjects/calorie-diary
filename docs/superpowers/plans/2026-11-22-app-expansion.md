# App Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Подсказки БЖУ, база ~9000 продуктов, БЖУ в списке еды, группировка по времени, автокалории и 5 схем в профиле, более толстые полоски.

**Architecture:** Чистые функции в `Calculation.kt`; данные — `assets/products_extended.json`, импорт при миграции БД до версии 3; UI — существующие view-контроллеры; без AndroidX/сети.

**Tech Stack:** AGP 9.5.2 встроенный Kotlin, Gradle 9.9.2, SDK 37, SQLite, JUnit 4, USDA SR Legacy (CC0), Erech14 (MIT).

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-22-app-expansion-design.md`

## Global Constraints

- APK остаётся офлайн; данные поставляются в `assets/`, лицензии фиксируются в `NOTICE.md`.
- `DB_VERSION = 3`; миграция сохраняет продукты, записи и цели.
- Новые строки интерфейса — в `res/values/strings.xml`.
- Команда проверки: `gradlew test assembleDebug --no-daemon` с `JAVA_HOME=C:\Users\veles\.jdks\ms-21.0.13.0.2`, `ANDROID_HOME=E:\AndroidSdk`.

## Review Focus

- `kcalFromMacros` считает 4/9/4 и округляет до 1 знака; тест на 30/60/150 г → 1380 ккал.
- Схемы дают ровно 100% и корректные граммы; тест на сумму процентов и grams.
- `groupByMeal`: разрыв ровно 1 час → одна группа, 1 час + 1 минута → две; пустой список → пусто.
- Старые записи с `created_at = 0` не ломают группировку.
- Импорт датасетов не создаёт дубли имён и не превышает разумный размер APK.

---

### Task 1: Чистые функции калорий, схем и приёмов пищи

**Files:**
- Modify: `Calculation.kt`, `Models.kt`
- Test: `CalculationTest.kt`

**Interfaces:**
- Produces: `fun kcalFromMacros(protein: Double, fat: Double, carbs: Double): Double`; `data class MacroScheme(val name: String, val proteinPct: Int, val fatPct: Int, val carbsPct: Int)`; `val MACRO_SCHEMES: List<MacroScheme>`; `fun gramsFromScheme(kcal: Double, scheme: MacroScheme): Targets`; `fun groupByMeal(entries: List<Entry>, windowMs: Long = 3_600_000L): List<MealGroup>`; `data class MealGroup(val startMs: Long, val entries: List<Entry>)`. `Entry` получает `createdAt: Long = 0L`.

- [ ] **RED-тесты** (3 теста: kcalFromMacros, схемы, groupByMeal) → FAIL.
- [ ] **Реализация** в `Calculation.kt`: `p*4 + f*9 + c*4`; граммы `kcal*pct/100/4` и `/9`; группировка по разрыву от начала группы.
- [ ] **GREEN**: `gradlew test`, 13/13 тестов.
- [ ] **Коммит**: `feat: add macro calculation and meal grouping`.

---

### Task 2: Импорт большой базы продуктов и схема БД v3

**Files:**
- Create: `app\src\main\assets\products_extended.json`, `NOTICE.md`, `E:\Tools\build-products.ps1`
- Modify: `Db.kt`

**Interfaces:**
- Consumes: датасеты USDA SR Legacy и Erech14.
- Produces: `Db.products()` возвращает ~9000 позиций; `entries` хранят `created_at`.

- [ ] **Шаг 1: Скачать датасеты** в `C:\Users\veles\AppData\Local\Temp\opencode\product-data\` (USDA zip 6 074 592 байт, Erech14 CSV 90 291 байт).
- [ ] **Шаг 2: Написать `build-products.ps1`**: распаковать USDA, собрать из `food.csv` + `nutrient.csv` значения Energy/Protein/Fat/Carbs на 100 г, добавить Erech14 и текущие 342 русских позиции, убрать дубли по нормализованному имени, записать JSON `{name,kcal,protein,fat,carbs}`.
- [ ] **Шаг 3: Сгенерировать asset** и проверить число записей ≥ 3000.
- [ ] **Шаг 4: БД v3**: `ALTER TABLE entries ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0`; при создании/миграции читать `assets/products_extended.json` через `org.json` и вставлять с `CONFLICT_IGNORE`; `addEntry` пишет время, `toEntry` читает его.
- [ ] **Шаг 5: LICENSE/NOTICE** с атрибуцией USDA CC0 и Erech14 MIT.
- [ ] **Шаг 6: Проверка**: `gradlew test assembleDebug`, BUILD SUCCESSFUL.
- [ ] **Шаг 7: Коммит**: `feat: import extended product database`.

---

### Task 3: Подсказки БЖУ и БЖУ в списке еды

**Files:**
- Modify: `dialog_entry.xml`, `item_dish_ingredient.xml`, `item_entry.xml`, `DayView.kt`, `DishDialog.kt`, `strings.xml`

- [ ] **Шаг 1:** добавить TextView под полем продукта в `dialog_entry.xml` и в строку ингредиента; показать «ккал/Б/Ж/У на 100 г» при выборе/вводе.
- [ ] **Шаг 2:** добавить в `item_entry.xml` вторую строку с БЖУ порции; заполнить в `DayView.refresh()`.
- [ ] **Шаг 3:** проверка `gradlew assembleDebug`; коммит `feat: show macros in dialogs and day list`.

---

### Task 4: Группировка еды по приёмам

**Files:**
- Modify: `fragment_day.xml`, `DayView.kt`, `strings.xml`

- [ ] **Шаг 1:** в `refresh()` вместо плоского списка вызывать `groupByMeal`, добавлять заголовок с временем и итогом группы.
- [ ] **Шаг 2:** записи с `created_at = 0` показывать в группе «Без времени».
- [ ] **Шаг 3:** проверка сборки; коммит `feat: group entries by meal time`.

---

### Task 5: Профиль — автокалории и пять схем

**Files:**
- Modify: `fragment_profile.xml`, `ProfileView.kt`, `strings.xml`

- [ ] **Шаг 1:** добавить TextWatcher: при изменении БЖУ пересчитывать `kcalFromMacros` в поле калорий; при изменении калорий показывать пять кнопок схем; флаг `updating` исключает цикл.
- [ ] **Шаг 2:** по кнопке схемы вызывать `gramsFromScheme` и заполнять БЖУ.
- [ ] **Шаг 3:** проверка сборки; коммит `feat: add automatic calories and macro schemes`.

---

### Task 6: Визуал и финал

**Files:**
- Modify: `res/layout/fragment_day.xml`, `res/drawable/*`, `res/values/styles.xml`
- Modify: `BACKLOG.md`, `CONTEXT.md`, `PLAN.md`

- [ ] **Шаг 1:** полоски высотой ~14 dp, скруглённый `progressDrawable` на 4 цвета; кнопки с selector/состоянием нажатия.
- [ ] **Шаг 2:** полный прогон `gradlew test assembleDebug`, код 0.
- [ ] **Шаг 3:** скопировать APK в `E:\Projects\CalorieDiary\apk\CalorieDiary.apk`.
- [ ] **Шаг 4:** закрыть BL-057–BL-062 в `BACKLOG-closed.md` с датой, командой, кодом, путём; обновить `CONTEXT.md` и `PLAN.md`.
- [ ] **Шаг 5:** коммит `docs: record app expansion delivery`.
