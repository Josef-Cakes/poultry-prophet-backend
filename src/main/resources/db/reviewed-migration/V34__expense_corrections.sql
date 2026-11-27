-- Review the target schema and rehearse before an explicitly authorized rollout.
-- Fail rather than silently accept pre-existing columns or an index with unknown semantics.
-- Flyway runs this PostgreSQL migration transactionally; do not enable auto-migration.
ALTER TABLE farm_financial_transaction
    ADD COLUMN correction_of_id bigint,
    ADD COLUMN correction_expected_updated_at timestamptz;

ALTER TABLE farm_financial_transaction
    ADD CONSTRAINT fk_expense_correction_original
    FOREIGN KEY (correction_of_id) REFERENCES farm_financial_transaction(id);

-- Each original transaction may have at most one directly linked correction.
CREATE UNIQUE INDEX uq_expense_correction_original
    ON farm_financial_transaction(correction_of_id)
    WHERE correction_of_id IS NOT NULL;
