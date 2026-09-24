-- MANUAL VALIDATION MIGRATION.
-- Apply only to the isolated validation database after review. This file is intentionally
-- not wired into application startup or production deployment.

ALTER TABLE batch_selection_review ADD COLUMN IF NOT EXISTS version_number integer;
ALTER TABLE batch_selection_review ADD COLUMN IF NOT EXISTS purpose varchar(64);
ALTER TABLE batch_selection_review ADD COLUMN IF NOT EXISTS snapshot_note text;
ALTER TABLE batch_selection_review ADD COLUMN IF NOT EXISTS source_cutoff_at timestamp with time zone;
ALTER TABLE batch_selection_review ADD COLUMN IF NOT EXISTS idempotency_key varchar(128);

CREATE INDEX IF NOT EXISTS idx_selection_review_snapshot_key
    ON batch_selection_review(farm_id, batch_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

UPDATE batch_selection_review
SET version_number = COALESCE(version_number, 1),
    purpose = COALESCE(purpose, 'ROUTINE_REVIEW'),
    source_cutoff_at = COALESCE(source_cutoff_at, generated_at)
WHERE version_number IS NULL
   OR purpose IS NULL
   OR source_cutoff_at IS NULL;
