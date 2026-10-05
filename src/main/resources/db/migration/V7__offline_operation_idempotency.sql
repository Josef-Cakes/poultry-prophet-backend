-- Offline outbox idempotency. Apply to the isolated validation database first;
-- production rollout must use the normal reviewed migration process.
ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS operation_id uuid;
CREATE UNIQUE INDEX IF NOT EXISTS uq_farm_input_log_operation_id
    ON farm_input_log(operation_id) WHERE operation_id IS NOT NULL;
