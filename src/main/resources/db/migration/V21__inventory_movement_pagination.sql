BEGIN;

CREATE INDEX IF NOT EXISTS idx_inventory_movement_farm_occurred
    ON inventory_movement(farm_id, occurred_at DESC, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_movement_farm_product_occurred
    ON inventory_movement(farm_id, farm_product_id, occurred_at DESC, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_movement_farm_batch_occurred
    ON inventory_movement(farm_id, batch_id, occurred_at DESC, created_at DESC, id DESC);

COMMIT;
