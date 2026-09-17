ALTER TABLE app_user ALTER COLUMN farm_id DROP NOT NULL;

-- Stage auto-derivation flag. Hibernate's ddl-auto:update cannot add a NOT NULL column to a
-- table that already has rows (Postgres rejects it without a default), so we add it here with a
-- default. Idempotent so it is safe to run on every startup.
ALTER TABLE batch ADD COLUMN IF NOT EXISTS stage_manual boolean NOT NULL DEFAULT false;
ALTER TABLE batch DROP CONSTRAINT IF EXISTS batch_current_population_bounds;
ALTER TABLE batch
    ADD CONSTRAINT batch_current_population_bounds
    CHECK (current_population >= 0 AND current_population <= initial_population);
