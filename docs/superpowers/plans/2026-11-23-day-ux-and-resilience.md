# Day UX And Resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Вес в «Дне», надёжные копии БД и обновления, двухстрочные и ранжированные подсказки, редактирование веса записей, сворачиваемые группы приёмов.

**Architecture:** Чистые функции в `Calculation.kt`/`BackupPolicy.kt`; SQLite миграция с backup; кастомный adapter; DayView-контроллер.

**Tech Stack:** AGP 9.5.2, Gradle 9.9.2, SDK 37, SQLite, JUnit 4.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-23-day-ux-and-resilience-design.md`

## Global Constraints

- APK офлайн; данные остаются в приватном хранилище приложения.
- Схема БД версии 6; изменения только через `ALTER/CREATE IF NOT EXISTS`, без `DROP`.
- Новые строки — в `res/values/strings.xml`.
- Команда проверки: `gradlew test assembleDebug --no-daemon` с `JAVA_HOME=C:\Users\veles\.jdks\ms-21.0.13.0.2`, `ANDROID_HOME=E:\AndroidSdk`.

## Review Focus

- `rankProducts`: startsWith-совпадения идут первыми; регистронезависимо.
- `rescale`: вес 100→200 удваивает КБЖУ; 0/отрицание не ломает.
- `keepLatest`: остаются 3 последние копии, остальные удаляются.
- Обновление 5→6 не трогает строки продуктов, записей и целей.
- Редактирование веса записи сохраняет id и дату.

---

### Task 1: Чистые функции ранжирования, пересчёта и резервных копий

**Files:** `Calculation.kt`, `BackupPolicy.kt`, тесты.

- [ ] RED-тесты на `rankProductNames`, `rescale`, `keepLatest`.
- [ ] Реализация: startsWith → contains; `value * to/from`; сортировка имён копий по убыванию и взятие первых N.
- [ ] GREEN: `gradlew test`, все тесты зелёные.
- [ ] Коммит `feat: add ranking, rescale and backup policy helpers`.

### Task 2: Надёжная база: backup и обновление

**Files:** `Db.kt`.

- [ ] `DB_VERSION = 6`; `onUpgrade` сначала вызывает `backupDatabase(db, "upgrade")`, затем existing ALTER/CREATE без удаления данных.
- [ ] `onOpen` создаёт ежедневную копию `diary-<timestamp>.db` в `files/backups`, хранит последние 3.
- [ ] `updateEntry(id, grams, values)` для редактирования записи.
- [ ] Проверка: `gradlew assembleDebug`, код 0.
- [ ] Коммит `feat: add database backups and entry update`.

### Task 3: Двухстрочные и ранжированные подсказки

**Files:** `item_suggestion.xml`, `SuggestionAdapter.kt`, `DayView.kt`, `DishDialog.kt`, `strings.xml`.

- [ ] `item_suggestion.xml`: имя 14sp, КБЖУ 11sp под ним.
- [ ] `SuggestionAdapter` с `Filter`, использующим `rankProductNames`; getName из `Suggestion.toString()`.
- [ ] Подключить в оба диалога; убрать старый `product_suggestion` из адаптеров.
- [ ] Проверка сборки; коммит `feat: improve product suggestions`.

### Task 4: Вес в «Дне», редактирование записи, группы

**Files:** `fragment_day.xml`, `drawable/meal_group_border.xml`, `DayView.kt`, `strings.xml`.

- [ ] Строка веса под месяцем из SharedPreferences.
- [ ] Клик по строку еды → диалог веса, `rescale`, `db.updateEntry`, refresh.
- [ ] Группы приёмов: рамка, заголовок кликабелен, состояние свернутости в map.
- [ ] Проверка сборки; коммит `feat: improve day tab interactions`.

### Task 5: Финальная сборка и учёт

- [ ] `gradlew test assembleDebug` — код 0.
- [ ] Скопировать APK в `apk\CalorieDiary.apk`.
- [ ] Закрыть BL-069–BL-074 в `BACKLOG-closed.md`, обновить `CONTEXT.md`/`PLAN.md`.
- [ ] Коммит `docs: record day UX delivery`.
