-- Nullable so existing batches remain unchanged. Roll out through the reviewed
-- migration process before starting a backend that maps this column.
ALTER TABLE batch ADD COLUMN IF NOT EXISTS derby_date date;
