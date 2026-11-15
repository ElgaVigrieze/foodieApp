-- Shopping list items had no owner: every household saw, and could change, every other household's
-- list for the same week. Items now belong to a household (or, without one, to their owner).
ALTER TABLE shopping_list_items ADD COLUMN IF NOT EXISTS owner_id bigint REFERENCES app_users (id);
ALTER TABLE shopping_list_items ADD COLUMN IF NOT EXISTS household_id bigint REFERENCES households (id);

-- Existing rows can only be attributed through their product. Items whose product is a global
-- (household-less) product stay unassigned and are no longer shown to anyone; the list is rebuilt
-- from the meal plan on the next "regenerate".
UPDATE shopping_list_items i
SET household_id = p.household_id
FROM products p
WHERE p.id = i.product_id;

CREATE INDEX IF NOT EXISTS idx_shopping_list_items_household_week ON shopping_list_items (household_id, week_start);
CREATE INDEX IF NOT EXISTS idx_shopping_list_items_owner_week ON shopping_list_items (owner_id, week_start);
