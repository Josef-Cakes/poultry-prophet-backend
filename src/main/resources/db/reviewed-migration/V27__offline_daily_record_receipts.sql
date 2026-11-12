-- Apply only after review. Keep each receipt in the same transaction as its daily record.
-- The primary key serializes concurrent retries, including retries from another account.
CREATE TABLE offline_daily_record_receipt (
    operation_id uuid PRIMARY KEY,
    farm_id bigint NOT NULL,
    user_id bigint NOT NULL,
    batch_id bigint NOT NULL,
    occurred_at timestamptz NOT NULL,
    payload jsonb NOT NULL,
    server_id bigint,
    created_at timestamptz NOT NULL DEFAULT now()
);
