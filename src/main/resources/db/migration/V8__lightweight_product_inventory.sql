-- Manual/reviewed migration for the lightweight product inventory revision.
-- Apply to the isolated validation database first, after a backup.

CREATE TABLE IF NOT EXISTS farm_product (
    id bigserial PRIMARY KEY,
    farm_id bigint NOT NULL,
    product_type varchar(32) NOT NULL,
    brand_name varchar(255) NOT NULL,
    product_name varchar(255),
    package_description varchar(255),
    stock_unit varchar(32) NOT NULL,
    stock_on_hand numeric(14,3) NOT NULL DEFAULT 0,
    reorder_level numeric(14,3),
    allow_fractional_quantity boolean NOT NULL DEFAULT true,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_farm_product_stock_nonnegative CHECK (stock_on_hand >= 0),
    CONSTRAINT ck_farm_product_reorder_nonnegative CHECK (reorder_level IS NULL OR reorder_level >= 0)
);

CREATE INDEX IF NOT EXISTS idx_farm_product_farm_active ON farm_product(farm_id, active);
CREATE INDEX IF NOT EXISTS idx_farm_product_name ON farm_product(farm_id, brand_name);

CREATE TABLE IF NOT EXISTS inventory_movement (
    id bigserial PRIMARY KEY,
    farm_id bigint NOT NULL,
    farm_product_id bigint NOT NULL,
    movement_type varchar(32) NOT NULL,
    quantity_delta numeric(14,3) NOT NULL,
    balance_after numeric(14,3) NOT NULL,
    occurred_at timestamptz NOT NULL,
    batch_id bigint,
    farm_input_log_id bigint,
    financial_transaction_id bigint,
    reverses_movement_id bigint,
    reason text,
    recorded_by bigint NOT NULL,
    operation_id uuid UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_inventory_movement_delta_nonzero CHECK (quantity_delta <> 0),
    CONSTRAINT ck_inventory_movement_balance_nonnegative CHECK (balance_after >= 0)
);

CREATE INDEX IF NOT EXISTS idx_inventory_movement_product_date
    ON inventory_movement(farm_product_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_inventory_movement_batch_date
    ON inventory_movement(batch_id, occurred_at);

ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS farm_product_id bigint;
ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS inventory_movement_id bigint;
ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS inventory_status varchar(32);
ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS affected_bird_count integer;

UPDATE farm_input_log
SET inventory_status = COALESCE(inventory_status, 'LEGACY')
WHERE inventory_status IS NULL;

CREATE INDEX IF NOT EXISTS idx_farm_input_log_product
    ON farm_input_log(farm_id, farm_product_id, recorded_at);
