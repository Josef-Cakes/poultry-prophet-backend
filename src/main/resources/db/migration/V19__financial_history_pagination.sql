BEGIN;

CREATE INDEX IF NOT EXISTS idx_financial_transaction_farm_date
    ON farm_financial_transaction(farm_id, transaction_date DESC, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_financial_transaction_farm_batch_date
    ON farm_financial_transaction(farm_id, batch_id, transaction_date DESC, created_at DESC, id DESC);

COMMIT;
