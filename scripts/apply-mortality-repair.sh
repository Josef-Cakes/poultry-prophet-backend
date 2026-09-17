#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_URL:?Set DATABASE_URL to the exact Supabase PostgreSQL connection URI}"
: "${BACKUP_FILE:?Set BACKUP_FILE to the reviewed pg_dump output}"
: "${PREVIEW_FILE:?Set PREVIEW_FILE to the reviewed preview output}"

if [[ "${MORTALITY_MIGRATION_APPROVED:-}" != "YES" ]]; then
  echo "Refusing to run. Set MORTALITY_MIGRATION_APPROVED=YES only after backup and preview review." >&2
  exit 2
fi

test -s "$BACKUP_FILE" || { echo "Backup file is missing or empty: $BACKUP_FILE" >&2; exit 2; }
test -s "$PREVIEW_FILE" || { echo "Preview file is missing or empty: $PREVIEW_FILE" >&2; exit 2; }

migration_file="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/src/main/resources/db/migration/V2__repair_existing_mortality_accounting.sql"
test -s "$migration_file"

echo "Applying reviewed mortality repair using backup: $BACKUP_FILE"
echo "Applying reviewed preview: $PREVIEW_FILE"
psql "$DATABASE_URL" --set=ON_ERROR_STOP=1 --file="$migration_file"
echo "Migration completed. Re-run the preview and retain its output as post-migration evidence."
