-- Separate the biological hatch date from the date the flock entered the farm.
-- Legacy start_date values represented hatch date, so backfill only hatch_date.
-- farm_entry_date intentionally remains NULL for historical rows when it is unknown.
ALTER TABLE batch ADD COLUMN IF NOT EXISTS hatch_date date;
ALTER TABLE batch ADD COLUMN IF NOT EXISTS farm_entry_date date;

UPDATE batch
SET hatch_date = start_date
WHERE hatch_date IS NULL;

CREATE INDEX IF NOT EXISTS idx_batch_hatch_date ON batch(farm_id, hatch_date);
CREATE INDEX IF NOT EXISTS idx_batch_farm_entry_date ON batch(farm_id, farm_entry_date);
