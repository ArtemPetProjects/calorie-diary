# Product Cleanup And Weight Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Очистить базу продуктов от абсурдных значений, показать КБЖУ в автодополнении, добавить схему целей по массе тела.

**Architecture:** Чистая функция `weightScheme`; генератор `E:\Tools\build-products.ps1` пересобирает asset; UI-контроллеры форматируют подсказки; цели хранятся в SQLite и SharedPreferences.

**Tech Stack:** AGP 9.5.2, Gradle 9.9.2, SDK 37, SQLite, JUnit 4, USDA SR Legacy CC0, Erech14 MIT.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-23-product-cleanup-and-weight-design.md`

## Global Constraints

- APK офлайн; база в `assets/products_extended.json`; атрибуция в `NOTICE.md`.
- Новые строки интерфейса — в `res/values/strings.xml`.
- Команда проверки: `gradlew test assembleDebug --no-daemon` с `JAVA_HOME=C:\Users\veles\.jdks\ms-21.0.13.0.2`, `ANDROID_HOME=E:\AndroidSdk`.

## Review Focus

- `weightScheme(weight=70, kcal=2000)`: белки 140, жиры 35, углеводы ≈273, итоговые калории отличаются не более чем на 2.
- При весе 0 или калориях 0 функция возвращает null.
- После очистки нет записей «Хурма свежая»; «Хурма» = 70 ккал.
- Нет записей с расхождением калорий и БЖУ >20%.
- Выбор строки автодополнения восстанавливает исходное имя продукта по префиксу до « • ».

---

### Task 1: Чистая функция схемы по весу

**Files:** `Calculation.kt`, `CalculationTest.kt`
**Produces:** `fun weightScheme(weightKg: Double, kcal: Double): Targets?`

- [ ] RED-тесты на 70 кг/2000 ккал и на нулевые значения → FAIL.
- [ ] Реализация: белки `вес×2`, жиры `вес/2`, углеводы `(ккал − Б×4 − Ж×9)/4`, округление углеводов до целого, калории пересчитать из БЖУ.
- [ ] GREEN: `gradlew test`, 16/16 тестов.
- [ ] Коммит `feat: add body weight macro scheme`.

### Task 2: Очистка базы продуктов

**Files:** `E:\Tools\build-products.ps1`, `assets/products_extended.json`, `NOTICE.md`

- [ ] Добавить в генератор таблицу канонических значений для 19 конфликтных групп.
- [ ] Пропускать варианты «свежая/сырая/raw/fresh», если канон уже добавлен.
- [ ] Для записей с расхождением >20% заменять калории значением `Б×4 + Ж×9 + У×4`.
- [ ] Пересобрать asset, проверить: нет «Хурма свежая», «Хурма»=70, расхождений >20% нет.
- [ ] Коммит `feat: clean product database`.

### Task 3: КБЖУ в автодополнении

**Files:** `DayView.kt`, `DishDialog.kt`, `strings.xml`

- [ ] Добавить строку `product_suggestion` и функцию форматирования подсказки.
- [ ] В адаптерах заменить имена на отформатированные строки; при поиске продукта брать префикс до « • ».
- [ ] Проверка сборки; коммит `feat: show macros in autocomplete`.

### Task 4: Вес и «Моя схема» в профиле

**Files:** `fragment_profile.xml`, `ProfileView.kt`, `strings.xml`

- [ ] Поле веса, кнопка «Моя схема», сохранение веса в SharedPreferences.
- [ ] При нажатии считать `weightScheme`, сохранять в `Db.targets()`, перезаполнять поля; вкладка «День» подхватит новые цели при переключении.
- [ ] Проверка сборки; коммит `feat: add weight based macro scheme`.

### Task 5: Финальная сборка и отчётность

- [ ] `gradlew test assembleDebug` — код 0.
- [ ] Скопировать APK в `E:\Projects\CalorieDiary\apk\CalorieDiary.apk`.
- [ ] Закрыть BL-065–BL-067 в `BACKLOG-closed.md`, обновить `CONTEXT.md`/`PLAN.md`.
- [ ] Коммит `docs: record product cleanup delivery`.
