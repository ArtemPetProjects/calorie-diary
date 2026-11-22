# Day Weight And Suggestion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Кнопка назначения веса на выбранную дату, стрелки групп, шрифт подсказок +1.

**Architecture:** SQLite таблица `weights`; DayView-контроллер; ресурсы разметки.

**Tech Stack:** AGP 9.5.2, Gradle 9.9.2, SDK 37, SQLite.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-23-day-weight-and-suggestion-design.md`

## Global Constraints

- APK офлайн; миграция только `CREATE TABLE IF NOT EXISTS`, без удаления данных.
- Новые строки — в `strings.xml`.
- Проверка: `gradlew test assembleDebug`.

## Review Focus

- Вес сохраняется по дате и не смешивается с весом профиля.
- Стрелка меняется при сворачивании/разворачивании.
- Шрифт подсказок увеличен ровно на 1 sp.

### Task 1: Таблица весов

**Files:** `Db.kt`
- [ ] `DB_VERSION = 6`; `weights(date TEXT PRIMARY KEY, kg REAL NOT NULL)` в `onCreate` и `onUpgrade`.
- [ ] `weightFor(date)` и `saveWeight(date, kg)`.
- [ ] Коммит `feat: store weight per date`.

### Task 2: Кнопка веса в «Дне»

**Files:** `fragment_day.xml`, `DayView.kt`, `strings.xml`
- [ ] Замена TextView на Button, диалог ввода, сохранение по `selectedDate`.
- [ ] Коммит `feat: make day weight editable`.

### Task 3: Стрелки и шрифт

**Files:** `DayView.kt`, `item_suggestion.xml`
- [ ] Префикс «▼/▶» в заголовке группы; 14→15sp и 11→12sp.
- [ ] Коммит `feat: add group arrows and larger suggestions`.

### Task 4: Финал

- [ ] `gradlew test assembleDebug`, APK, закрыть BL-075–BL-077, обновить документы.
