-- MANUAL VALIDATION MIGRATION.
-- Apply only to the isolated validation database after taking a backup. This file is
-- intentionally not wired into application startup and must not be run against production.

ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS operation_id uuid;
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS population_delta integer;
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS population_after integer;
CREATE UNIQUE INDEX IF NOT EXISTS uq_batch_event_operation_id
    ON batch_event(operation_id) WHERE operation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_batch_event_batch_date
    ON batch_event(batch_id, event_date);

ALTER TABLE daily_record ADD COLUMN IF NOT EXISTS temperature_quality varchar(32);
ALTER TABLE daily_record ADD COLUMN IF NOT EXISTS feed_quality varchar(32);
ALTER TABLE daily_record ADD COLUMN IF NOT EXISTS water_quality varchar(32);
UPDATE daily_record
SET temperature_quality = COALESCE(temperature_quality, 'UNKNOWN'),
    feed_quality = COALESCE(feed_quality, 'UNKNOWN'),
    water_quality = COALESCE(water_quality, 'UNKNOWN');
ALTER TABLE daily_record ALTER COLUMN temperature_quality SET DEFAULT 'UNKNOWN';
ALTER TABLE daily_record ALTER COLUMN feed_quality SET DEFAULT 'UNKNOWN';
ALTER TABLE daily_record ALTER COLUMN water_quality SET DEFAULT 'UNKNOWN';
ALTER TABLE daily_record ALTER COLUMN temperature_quality SET NOT NULL;
ALTER TABLE daily_record ALTER COLUMN feed_quality SET NOT NULL;
ALTER TABLE daily_record ALTER COLUMN water_quality SET NOT NULL;

ALTER TABLE indicator ALTER COLUMN bhi DROP NOT NULL;
ALTER TABLE indicator ALTER COLUMN readiness_score DROP NOT NULL;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS temperature_c double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS mortality_count integer;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS feed_intake_g double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS water_intake_ml double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS temperature_score double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS mortality_score double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS feed_score double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS water_score double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS temperature_contribution double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS mortality_contribution double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS feed_contribution double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS water_contribution double precision;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS temperature_quality varchar(32);
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS feed_quality varchar(32);
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS water_quality varchar(32);
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS formula_version varchar(64);
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS sufficient_data boolean;
ALTER TABLE indicator ADD COLUMN IF NOT EXISTS missing_data_warning text;
UPDATE indicator
SET sufficient_data = COALESCE(sufficient_data, false),
    formula_version = COALESCE(formula_version, 'LEGACY_UNEXPLAINED');
ALTER TABLE indicator ALTER COLUMN sufficient_data SET DEFAULT false;
ALTER TABLE indicator ALTER COLUMN sufficient_data SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_indicator_batch_record_date
    ON indicator(batch_id, record_id);
