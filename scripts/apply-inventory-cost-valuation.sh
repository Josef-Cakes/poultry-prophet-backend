#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_URL:?Set DATABASE_URL to the exact PostgreSQL connection URI}"
: "${BACKUP_FILE:?Set BACKUP_FILE to the reviewed pg_dump output}"

if [[ "${INVENTORY_MIGRATION_APPROVED:-}" != "YES" ]]; then
  echo "Refusing to run. Set INVENTORY_MIGRATION_APPROVED=YES only after reviewing the backup and preflight output." >&2
  exit 2
fi

test -s "$BACKUP_FILE" || {
  echo "Backup file is missing or empty: $BACKUP_FILE" >&2
  exit 2
}

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migration_file="$project_root/src/main/resources/db/migration/V14__inventory_cost_valuation.sql"
test -s "$migration_file"

echo "Preflight: inventory valuation columns"
psql "$DATABASE_URL" --set=ON_ERROR_STOP=1 --command="
SELECT table_name, column_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND ((table_name = 'farm_product' AND column_name IN ('average_unit_cost', 'currency', 'valuation_status'))
    OR (table_name = 'farm_financial_transaction' AND column_name = 'source_type'))
ORDER BY table_name, column_name;
"

echo "Applying reviewed inventory valuation migration using backup: $BACKUP_FILE"
psql "$DATABASE_URL" --set=ON_ERROR_STOP=1 --file="$migration_file"

echo "Postflight: valuation status and source type null counts"
psql "$DATABASE_URL" --set=ON_ERROR_STOP=1 --command="
SELECT
    (SELECT COUNT(*) FROM farm_product WHERE valuation_status IS NULL) AS null_valuation_status,
    (SELECT COUNT(*) FROM farm_product WHERE currency IS NULL OR btrim(currency) = '') AS blank_currency,
    (SELECT COUNT(*) FROM farm_financial_transaction WHERE source_type IS NULL OR btrim(source_type) = '') AS blank_source_type;
"

echo "Inventory valuation migration completed."
