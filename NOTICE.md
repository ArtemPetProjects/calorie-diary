# Источники данных

База продуктов в `app/src/main/assets/products_extended.json` собрана из трёх источников.

1. USDA FoodData Central, SR Legacy, 2018 — https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip . Лицензия CC0 / public domain. Использованы названия и значения калорийности, белков, жиров и углеводов на 100 г.
2. Erech14, nutrition-table — https://github.com/Erech14/nutrition-table . Лицензия MIT, Copyright (c) 2026 Erech14. Использованы русские названия и КБЖУ на 100 г в рамках личного некоммерческого использования, разрешённого README проекта.
3. Собственный стартовый набор приложения — русские названия и примерные значения КБЖУ на 100 г.

Генератор ассета: `E:\Tools\build-products.ps1`. Приложение работает без сети; все данные поставляются внутри APK.
