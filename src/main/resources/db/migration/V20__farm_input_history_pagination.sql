BEGIN;

CREATE INDEX IF NOT EXISTS idx_farm_input_log_farm_recorded
    ON farm_input_log(farm_id, recorded_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_farm_input_log_farm_batch_recorded
    ON farm_input_log(farm_id, batch_id, recorded_at DESC, id DESC);

COMMIT;
