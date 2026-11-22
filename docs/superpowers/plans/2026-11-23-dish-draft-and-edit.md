# Dish Draft And Edit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Отложенное создание блюда с плиткой продолжения, редактирование своих продуктов/блюд, крупнее список еды.

**Architecture:** SQLite таблица `dish_drafts` с JSON-снимками; DishDialog режим черновика; DayView плитка; ресурсы разметки.

**Tech Stack:** AGP 9.5.2, Gradle 9.9.2, SDK 37, SQLite, JUnit 4.

**Spec:** `E:\Projects\CalorieDiary\docs\superpowers\specs\2026-11-23-dish-draft-and-edit-design.md`

## Global Constraints
- APK офлайн; миграция только `CREATE TABLE IF NOT EXISTS`.
- Новые строки — в `strings.xml`.
- Проверка: `gradlew test assembleDebug`.

## Review Focus
- Черновик сохраняется и восстанавливается с ингредиентами.
- После сохранения блюда черновик удаляется.
- Итоговый вес не требуется при отложенном создании.
- Редактирование блюда сохраняет id и вес.

### Task 1: Черновик в БД
- [x] `DB_VERSION = 7`; таблица `dish_drafts`; `saveDraft/draftItems/clearDraft`.
- [x] Коммит `feat: store dish drafts`.

### Task 2: DishDialog и плитка
- [x] Кнопка «Отложить», режим черновика, плитка «Продолжить создание» в «Дне».
- [x] Коммит `feat: add deferred dish creation`.

### Task 3: Редактирование и визуал
- [x] Проверить формы редактирования; шрифты 15/13sp, заголовок 15sp, рамка 2dp.
- [x] Коммит `feat: enlarge meal list and border`.

### Task 4: Финал
- [x] `gradlew test assembleDebug`, APK, закрыть BL-079–BL-082, документы.
