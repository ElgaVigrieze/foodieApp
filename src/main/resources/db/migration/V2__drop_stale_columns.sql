-- Columns that were removed from the entities over time. Hibernate's ddl-auto=update never drops
-- columns, so older databases may still have them (this replaces the old SchemaMigrationService).
-- A fresh database built from V1 does not have them, hence IF EXISTS.
ALTER TABLE workouts DROP COLUMN IF EXISTS completed;
ALTER TABLE workouts DROP COLUMN IF EXISTS date;
ALTER TABLE app_users DROP COLUMN IF EXISTS brain_last_seen_agent;
ALTER TABLE app_users DROP COLUMN IF EXISTS brain_last_seen_page;
