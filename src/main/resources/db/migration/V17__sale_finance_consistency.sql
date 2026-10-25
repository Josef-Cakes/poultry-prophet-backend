-- Link sale proceeds to the exact batch event so replay cannot create duplicate income.
-- Existing legacy sale rows remain readable; rows that can be matched by operation id are linked.
BEGIN;

ALTER TABLE farm_financial_transaction ADD COLUMN IF NOT EXISTS source_event_id bigint;

UPDATE farm_financial_transaction tx
SET source_event_id = event.id
FROM batch_event event
WHERE tx.source_event_id IS NULL
  AND tx.source_type = 'SALE_EVENT'
  AND tx.source_operation_id IS NOT NULL
  AND event.operation_id::text = tx.source_operation_id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_financial_sale_source_event
    ON farm_financial_transaction(source_event_id)
    WHERE source_event_id IS NOT NULL;

ALTER TABLE batch_event DROP CONSTRAINT IF EXISTS ck_batch_event_sale_amount_positive;
ALTER TABLE batch_event ADD CONSTRAINT ck_batch_event_sale_amount_positive
    CHECK (sale_amount IS NULL OR sale_amount > 0);

COMMIT;
