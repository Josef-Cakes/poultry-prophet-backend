#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_URL:?Set DATABASE_URL to the exact Supabase PostgreSQL connection URI}"

backup_dir="${1:-./backups}"
mkdir -p "$backup_dir"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="$backup_dir/poultry-prophet-mortality-$timestamp.dump"

pg_dump "$DATABASE_URL" \
  --format=custom \
  --no-owner \
  --no-privileges \
  --table=public.batch \
  --table=public.batch_event \
  --table=public.daily_record \
  --table=public.alert \
  --file="$backup_file"

test -s "$backup_file"
echo "Backup written to: $backup_file"
echo "Record this file path and its checksum before reviewing the migration preview."
sha256sum "$backup_file"
