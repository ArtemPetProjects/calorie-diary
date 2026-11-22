# Product Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Быстрый выбор продукта: недавние сверху, длинный список, диалог сверху с клавиатурой и поиском с первого символа.

**Architecture:** SQLite `product_usage`; чистая функция ранжирования; SuggestionAdapter; настройки диалога.

**Tech Stack:** AGP 9.5.2, Gradle 9.9.2, SDK 37, SQLite, JUnit 4.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-23-product-picker-design.md`

## Global Constraints
- APK офлайн; миграция только `CREATE TABLE IF NOT EXISTS`.
- Новые строки — в `strings.xml`.
- Проверка: `gradlew test assembleDebug`.

## Review Focus
- Недавние продукты идут первыми только при совпадении с запросом.
- Список открывается сразу, курсор и клавиатура активны.
- Использование продукта сохраняется при добавлении записи и блюда.

### Task 1: История использования
- [x] `DB_VERSION = 8`; `product_usage`; `markUsed`, `recentProductIds`.
- [x] Коммит `feat: track used products`.

### Task 2: Ранжирование с недавними
- [x] `rankProductNamesWithRecent` + RED/GREEN тест; подключить в SuggestionAdapter.
- [x] Коммит `feat: prioritize recently used products`.

### Task 3: Диалог и список
- [x] Gravity.TOP, match parent, SOFT_INPUT_STATE_VISIBLE, requestFocus, showDropDown, dropDownHeight.
- [x] Коммит `feat: open product picker immediately`.

### Task 4: Финал
- [x] `gradlew test assembleDebug`, APK, закрыть BL-083–BL-086, документы.
