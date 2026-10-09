-- Batch lifecycle metadata and an append-only audit trail.
-- This migration is additive and deliberately does not cascade-delete farm history.
ALTER TABLE batch ADD COLUMN IF NOT EXISTS archived_at timestamptz;
ALTER TABLE batch ADD COLUMN IF NOT EXISTS archived_by_user_id bigint;
ALTER TABLE batch ADD COLUMN IF NOT EXISTS archive_reason varchar(500);
ALTER TABLE batch ADD COLUMN IF NOT EXISTS pre_archive_status varchar(32);

CREATE TABLE IF NOT EXISTS batch_lifecycle_audit (
    id bigserial PRIMARY KEY,
    farm_id bigint NOT NULL,
    batch_id bigint NOT NULL,
    batch_name_snapshot varchar(255) NOT NULL,
    action varchar(32) NOT NULL,
    previous_status varchar(32),
    new_status varchar(32),
    reason varchar(500),
    performed_by_user_id bigint,
    performed_at timestamptz NOT NULL DEFAULT now(),
    metadata_json text
);

CREATE INDEX IF NOT EXISTS idx_batch_lifecycle_audit_farm_batch
    ON batch_lifecycle_audit(farm_id, batch_id, performed_at);
CREATE INDEX IF NOT EXISTS idx_batch_status_archived_at
    ON batch(status, archived_at);
