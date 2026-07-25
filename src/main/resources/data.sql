-- Seed data: all products and meals backup
-- Uses MERGE to avoid duplicates on restart

-- ═══ PRODUCTS ═══════════════════════════════════════════════════════════════
MERGE INTO products (id, name, category, unit, price_per_unit, calories, protein, carbs, fat, fiber, sugar) KEY(id) VALUES
(1,  'Salmon',                'FISH_SEA_PRODUCTS', 'KG',    16.00, 154.00, 21.00, NULL,   6.00, NULL, NULL),
(3,  'Sweet potato',          'VEGETABLES',        'KG',     5.00,  86.00,  1.60, 20.10,  0.00,  3.00,  5.10),
(4,  'Potato',                'VEGETABLES',        'KG',     2.00,  77.00,  2.00, 17.50,  0.00,  2.10,  0.00),
(9,  'Green peas',            'VEGETABLES',        'KG',    10.00, 150.91,  3.20, 29.00,  2.20,  1.30,  0.90),
(10, 'Cucumber',              'VEGETABLES',        'KG',     5.00,  13.00,  0.60,  2.00,  0.50,  0.50,  0.70),
(11, 'Tomato',                'VEGETABLES',        'KG',     6.00,  18.00,  1.02,  4.00,  0.08,  1.00,  0.06),
(12, 'Seasoning',             NULL,                'KG',    25.00, 457.00,  7.00,  6.00, 45.00,  2.00,  2.00),
(13, 'Cacao powder',          'CONDIMENTS',        'KG',    10.00, 228.06, 20.00, 58.00, 11.00, 33.00,  1.00),
(14, 'Biezpiens 0.5%',       'DAIRY',             'KG',     5.00,  98.00, 18.00,  3.50,  0.50,  0.00,  0.50),
(15, 'Biezpiens 9%',         'DAIRY',             'KG',     5.00, 149.00, 16.40,  0.80,  9.00,  0.00,  0.50),
(16, 'Erytritol',            NULL,                'KG',    10.00,   0.00,  0.00,100.00,  0.00,  0.00,  0.00),
(17, 'Allulose',             'CONDIMENTS',        'KG',    20.00,   0.00,  0.00, 62.50,  0.00,  0.00,  0.00),
(18, 'Milk 2.5%',            'DAIRY',             'LITER',  1.60,  54.00,  3.20,  4.70,  2.50,  0.00,  4.70),
(19, 'Honey',                NULL,                'KG',    10.00, 384.00,  5.20, 85.00,  1.50,  4.60, 22.00),
(20, 'Extra virgin olive oil','CONDIMENTS',       'LITER', 18.00, 821.00,  0.00,  0.00, 91.00,  0.00,  0.00),
(21, 'Avocado',              'VEGETABLES',        'KG',    14.00,   5.00,  0.00,  0.00,  0.50,  0.00,  0.00),
(22, 'Soy sauce',            NULL,                'LITER', 18.00, 325.00, 10.00,  3.20,  0.00,  0.00,  0.60),
(23, 'Egg',                  'DAIRY',             'KG',     4.55, 143.00, 13.00,  2.80,  9.50,  0.00,  2.00),
(24, 'Flounder',             'FISH_SEA_PRODUCTS', 'KG',     6.00,  70.00, 10.50,  2.10,  7.70,  0.70,  0.70),
(25, 'Red beans',            'NUTS_SEEDS',        'KG',     2.00, 115.00,  7.00,  5.30,  6.20,  4.80,  3.60),
(26, 'White beans',          'NUTS_SEEDS',        'KG',     2.00, 104.00,  6.60, 15.00,  0.80,  5.40,  0.20),
(27, 'Beef steak',           'MEAT',              'KG',    16.00, 209.00, 19.00,  0.00, 15.00,  0.00,  0.00),
(28, 'Ground beef 85%',      'MEAT',              'KG',    14.00, 214.29, 18.58,  0.00,  6.19,  0.00,  0.00),
(29, 'Cheddar cheese',       'DAIRY',             'KG',     8.00, 270.00,  9.00,  6.00, 21.00,  0.00,  6.00),
(30, 'Heavy cream 35%',      'DAIRY',             'LITER',  7.00, 337.00,  2.30,  3.10, 35.00,  0.00,  3.10);

-- ═══ MEALS ══════════════════════════════════════════════════════════════════
MERGE INTO meals (id, name, category, servings, recipe, favorite) KEY(id) VALUES
(1, 'Biezpiena krēms',                     'DESSERT',     8, NULL, false),
(3, 'Spicy salmon with Sweet potato fries', 'MAIN_COURSE', 4, NULL, false),
(4, 'Sweet potato tacos',                   'MAIN_COURSE', 8, NULL, false),
(5, 'Scrambled eggs',                       'MAIN_COURSE', 3, NULL, false),
(6, 'Chilli my style',                      'MAIN_COURSE', 8, NULL, false);

-- ═══ MEAL INGREDIENTS ═══════════════════════════════════════════════════════
-- Meal 1: Biezpiena krēms
MERGE INTO meal_ingredients (id, meal_id, product_id, quantity) KEY(id) VALUES
(1, 1, 14, 1.00),
(2, 1, 13, 0.08),
(3, 1, 17, 0.08),
(4, 1, 18, 0.30);

-- Meal 3: Spicy salmon with Sweet potato fries
MERGE INTO meal_ingredients (id, meal_id, product_id, quantity) KEY(id) VALUES
(5,  3, 1,  0.45),
(6,  3, 12, 0.02),
(7,  3, 20, 0.05),
(8,  3, 22, 0.02),
(9,  3, 19, 0.02),
(10, 3, 3,  0.50);

-- Meal 4: Sweet potato tacos
MERGE INTO meal_ingredients (id, meal_id, product_id, quantity) KEY(id) VALUES
(11, 4, 28, 1.00),
(12, 4, 3,  0.80),
(13, 4, 20, 0.08),
(14, 4, 29, 0.12),
(15, 4, 12, 0.10);

-- Meal 5: Scrambled eggs
MERGE INTO meal_ingredients (id, meal_id, product_id, quantity) KEY(id) VALUES
(16, 5, 23, 0.28),
(17, 5, 30, 0.10),
(18, 5, 20, 0.02);

-- Meal 6: Chilli my style
MERGE INTO meal_ingredients (id, meal_id, product_id, quantity) KEY(id) VALUES
(19, 6, 28, 1.00),
(20, 6, 20, 0.02),
(21, 6, 12, 0.10),
(22, 6, 26, 0.30);

-- Reset auto-increment sequences to avoid PK conflicts with new inserts
ALTER TABLE products ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE meals ALTER COLUMN id RESTART WITH 1000;
ALTER TABLE meal_ingredients ALTER COLUMN id RESTART WITH 1000;
