-- Every product now belongs to a household; there is no shared catalogue any more.
--
-- Before households were required, a product saved by a user without one got no household. The
-- creator was never recorded, so each such product goes to the households that use it (in food
-- logs, meals, meal plans or shopping lists):
--   * used by one household: it becomes that household's product;
--   * used by several households: each of them gets its own copy, and its rows are pointed at it;
--   * used by nobody: it is deleted.
-- Usage by a user who had no household at the time counts for the household they are in now.
-- Rows that belong to nobody (no household, and no user with one) are invisible to every user;
-- they are deleted so that products.household_id can become NOT NULL.

-- 1. Which households use each household-less product.
CREATE TEMPORARY TABLE orphan_product_usage ON COMMIT DROP AS
SELECT fl.product_id, COALESCE(fl.household_id, u.household_id) AS household_id
FROM food_logs fl
JOIN products p ON p.id = fl.product_id AND p.household_id IS NULL
LEFT JOIN app_users u ON u.id = fl.user_id
UNION
SELECT mi.product_id, COALESCE(m.household_id, u.household_id)
FROM meal_ingredients mi
JOIN products p ON p.id = mi.product_id AND p.household_id IS NULL
JOIN meals m ON m.id = mi.meal_id
LEFT JOIN app_users u ON u.id = m.user_id
UNION
SELECT mpp.product_id, COALESCE(mp.household_id, u.household_id)
FROM meal_plan_products mpp
JOIN products p ON p.id = mpp.product_id AND p.household_id IS NULL
JOIN meal_plans mp ON mp.id = mpp.meal_plan_id
LEFT JOIN app_users u ON u.id = mp.user_id
UNION
SELECT sli.product_id, sli.household_id
FROM shopping_list_items sli
JOIN products p ON p.id = sli.product_id AND p.household_id IS NULL;

DELETE FROM orphan_product_usage WHERE household_id IS NULL;

-- 2. The first household (lowest id) takes the product itself; every other one gets a copy.
--    The id sequence may lag behind the table (e.g. after restoring a backup); move it past the
--    highest id so that the copies cannot collide with existing products.
SELECT setval(pg_get_serial_sequence('products', 'id'), (SELECT COALESCE(MAX(id), 0) + 1 FROM products), false);

DO $$
DECLARE
    r      record;
    target bigint;
BEGIN
    FOR r IN
        SELECT product_id, household_id,
               ROW_NUMBER() OVER (PARTITION BY product_id ORDER BY household_id) AS n
        FROM orphan_product_usage
        ORDER BY product_id, household_id
    LOOP
        IF r.n = 1 THEN
            UPDATE products SET household_id = r.household_id WHERE id = r.product_id;
            CONTINUE;
        END IF;

        INSERT INTO products (name, category, unit, price_per_unit, barcode,
                              calories, protein, carbs, fat, fiber, sugar, household_id)
        SELECT name, category, unit, price_per_unit, barcode,
               calories, protein, carbs, fat, fiber, sugar, r.household_id
        FROM products WHERE id = r.product_id
        RETURNING id INTO target;

        UPDATE food_logs fl SET product_id = target
        FROM food_logs f LEFT JOIN app_users u ON u.id = f.user_id
        WHERE fl.id = f.id AND fl.product_id = r.product_id
          AND COALESCE(f.household_id, u.household_id) = r.household_id;

        UPDATE meal_ingredients mi SET product_id = target
        FROM meals m LEFT JOIN app_users u ON u.id = m.user_id
        WHERE m.id = mi.meal_id AND mi.product_id = r.product_id
          AND COALESCE(m.household_id, u.household_id) = r.household_id;

        UPDATE meal_plan_products mpp SET product_id = target
        FROM meal_plans mp LEFT JOIN app_users u ON u.id = mp.user_id
        WHERE mp.id = mpp.meal_plan_id AND mpp.product_id = r.product_id
          AND COALESCE(mp.household_id, u.household_id) = r.household_id;

        UPDATE shopping_list_items SET product_id = target
        WHERE product_id = r.product_id AND household_id = r.household_id;
    END LOOP;
END $$;

-- 3. Shopping list items V3 could not attribute (no household): if exactly one household uses
--    their product, they are that household's.
UPDATE shopping_list_items i
SET household_id = p.household_id
FROM products p
WHERE p.id = i.product_id AND i.household_id IS NULL
  AND i.product_id IN (SELECT product_id FROM orphan_product_usage
                       GROUP BY product_id HAVING COUNT(*) = 1);

-- 4. What is left without a household is used only by rows nobody can see. Remove those rows,
--    then the products.
DELETE FROM food_logs WHERE product_id IN (SELECT id FROM products WHERE household_id IS NULL);
DELETE FROM meal_ingredients WHERE product_id IN (SELECT id FROM products WHERE household_id IS NULL);
DELETE FROM meal_plan_products WHERE product_id IN (SELECT id FROM products WHERE household_id IS NULL);
DELETE FROM shopping_list_items WHERE product_id IN (SELECT id FROM products WHERE household_id IS NULL);
DELETE FROM products WHERE household_id IS NULL;

ALTER TABLE products ALTER COLUMN household_id SET NOT NULL;
