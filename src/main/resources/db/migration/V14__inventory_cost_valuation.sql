-- Inventory cost valuation and batch consumption-cost snapshots.
-- Apply after a database backup. Existing records without a defensible price remain UNVALUED.
-- This migration is intentionally safe when an earlier startup partially added a nullable column.

BEGIN;

ALTER TABLE farm_product ADD COLUMN IF NOT EXISTS average_unit_cost numeric(16,6);
ALTER TABLE farm_product ADD COLUMN IF NOT EXISTS currency varchar(3);
ALTER TABLE farm_product ADD COLUMN IF NOT EXISTS valuation_status varchar(16);

UPDATE farm_product
SET currency = 'PHP'
WHERE currency IS NULL OR btrim(currency) = '';

UPDATE farm_product
SET valuation_status = CASE
    WHEN average_unit_cost IS NOT NULL AND average_unit_cost > 0 THEN 'VALUED'
    ELSE 'UNVALUED'
END
WHERE valuation_status IS NULL;

ALTER TABLE farm_product ALTER COLUMN currency SET DEFAULT 'PHP';
ALTER TABLE farm_product ALTER COLUMN currency SET NOT NULL;
ALTER TABLE farm_product ALTER COLUMN valuation_status SET DEFAULT 'UNVALUED';
ALTER TABLE farm_product ALTER COLUMN valuation_status SET NOT NULL;
ALTER TABLE farm_product DROP CONSTRAINT IF EXISTS ck_farm_product_valuation_status;
ALTER TABLE farm_product ADD CONSTRAINT ck_farm_product_valuation_status
    CHECK (valuation_status IN ('VALUED', 'UNVALUED', 'FREE'));

ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS unit_cost_snapshot numeric(16,6);
ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS inventory_value_delta numeric(16,2);
ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS cost_status varchar(16);
ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS costed_at timestamptz;
ALTER TABLE inventory_movement DROP CONSTRAINT IF EXISTS ck_inventory_movement_cost_status;
ALTER TABLE inventory_movement ADD CONSTRAINT ck_inventory_movement_cost_status
    CHECK (cost_status IS NULL OR cost_status IN ('VALUED', 'UNVALUED', 'FREE'));

ALTER TABLE farm_financial_transaction ADD COLUMN IF NOT EXISTS source_type varchar(32);
ALTER TABLE farm_financial_transaction ADD COLUMN IF NOT EXISTS source_operation_id varchar(80);
UPDATE farm_financial_transaction
SET source_type = 'MANUAL'
WHERE source_type IS NULL OR btrim(source_type) = '';
ALTER TABLE farm_financial_transaction ALTER COLUMN source_type SET DEFAULT 'MANUAL';
ALTER TABLE farm_financial_transaction ALTER COLUMN source_type SET NOT NULL;

ALTER TABLE farm_input_log ALTER COLUMN quantity TYPE numeric(14,3) USING quantity::numeric;
ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS unit_cost_snapshot numeric(16,6);
ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS calculated_cost numeric(16,2);
ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS cost_status varchar(16);

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_movement_input_log
    ON inventory_movement(farm_id, farm_input_log_id)
    WHERE farm_input_log_id IS NOT NULL;

UPDATE farm_financial_transaction
SET source_type = 'INVENTORY_PURCHASE'
WHERE source_type = 'MANUAL'
  AND id IN (
      SELECT financial_transaction_id
      FROM inventory_movement
      WHERE movement_type = 'STOCK_IN' AND financial_transaction_id IS NOT NULL
  );

COMMIT;
