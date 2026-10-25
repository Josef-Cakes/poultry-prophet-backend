-- Supports farm alert filtering before pagination. Legacy indicator rows are
-- excluded in the query; this index keeps the common active feed bounded.
CREATE INDEX IF NOT EXISTS idx_alert_batch_ack_created
    ON alert(batch_id, acknowledged_at, created_at DESC, id DESC);
