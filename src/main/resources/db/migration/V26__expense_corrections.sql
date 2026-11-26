ALTER TABLE farm_financial_transaction ADD COLUMN IF NOT EXISTS correction_of_id bigint REFERENCES farm_financial_transaction(id);
ALTER TABLE farm_financial_transaction ADD COLUMN IF NOT EXISTS correction_expected_updated_at timestamptz;
CREATE UNIQUE INDEX IF NOT EXISTS uq_expense_correction_original ON farm_financial_transaction(correction_of_id) WHERE correction_of_id IS NOT NULL;
