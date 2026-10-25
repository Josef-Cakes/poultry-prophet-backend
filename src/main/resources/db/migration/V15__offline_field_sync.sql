-- Offline-first field recording: preserve observation time separately from server receipt time,
-- make sale proceeds atomic with a sale event, and make task replay auditable/idempotent.
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS occurred_at timestamptz;
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS sale_amount numeric(14,2);
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS sale_currency varchar(3);
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS sale_buyer varchar(255);
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS sale_reference varchar(255);

ALTER TABLE handler_task_status_history ADD COLUMN IF NOT EXISTS previous_status varchar(32);
ALTER TABLE handler_task_status_history ADD COLUMN IF NOT EXISTS operation_id uuid;
ALTER TABLE handler_task_status_history ADD COLUMN IF NOT EXISTS occurred_at timestamptz;
CREATE UNIQUE INDEX IF NOT EXISTS uq_task_status_history_operation
    ON handler_task_status_history(operation_id)
    WHERE operation_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_financial_source_operation
    ON farm_financial_transaction(farm_id, source_operation_id)
    WHERE source_operation_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS offline_sync_receipt (
    operation_id uuid PRIMARY KEY,
    farm_id bigint NOT NULL,
    entity_type varchar(64) NOT NULL,
    server_id bigint,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_offline_sync_receipt_farm ON offline_sync_receipt(farm_id, created_at);
