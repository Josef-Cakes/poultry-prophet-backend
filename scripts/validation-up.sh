#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT/docker-compose.validation.yml"
PROJECT="${COMPOSE_PROJECT_NAME:-poultry-prophet-validation}"

case "$PROJECT" in
  *production*|*prod*) echo "Refusing a production-looking compose project name: $PROJECT" >&2; exit 1 ;;
esac

test -n "${VALIDATION_DB_NAME:-}" || { echo "Set validation environment variables first; see .env.validation.example" >&2; exit 1; }
test -n "${VALIDATION_DB_USER:-}" || { echo "Set VALIDATION_DB_USER" >&2; exit 1; }
test -n "${VALIDATION_DB_PASSWORD:-}" || { echo "Set VALIDATION_DB_PASSWORD" >&2; exit 1; }
test -n "${VALIDATION_JWT_SECRET:-}" || { echo "Set VALIDATION_JWT_SECRET" >&2; exit 1; }
test -n "${VALIDATION_MANAGER_EMAIL:-}" || { echo "Set VALIDATION_MANAGER_EMAIL" >&2; exit 1; }
test -n "${VALIDATION_MANAGER_PASSWORD:-}" || { echo "Set VALIDATION_MANAGER_PASSWORD" >&2; exit 1; }
test -n "${VALIDATION_HANDLER_EMAIL:-}" || { echo "Set VALIDATION_HANDLER_EMAIL" >&2; exit 1; }
test -n "${VALIDATION_HANDLER_PASSWORD:-}" || { echo "Set VALIDATION_HANDLER_PASSWORD" >&2; exit 1; }
case "$VALIDATION_DB_NAME" in
  *validation*|*test*) ;;
  *) echo "Refusing a non-validation database name: $VALIDATION_DB_NAME" >&2; exit 1 ;;
esac

compose=(docker compose -p "$PROJECT" -f "$COMPOSE_FILE")
"${compose[@]}" up -d --wait --wait-timeout 60 validation-db

# This service is the local disposable database named above, never the .env
# application datasource. Prepare the receipt boundary before starting writers.
receipt_table=$("${compose[@]}" exec -T validation-db psql -v ON_ERROR_STOP=1 \
  -U "$VALIDATION_DB_USER" -d "$VALIDATION_DB_NAME" -Atqc \
  "SELECT to_regclass('public.offline_command_receipt') IS NOT NULL")
if [[ "$receipt_table" == "f" ]]; then
  "${compose[@]}" exec -T validation-db psql -v ON_ERROR_STOP=1 \
    -U "$VALIDATION_DB_USER" -d "$VALIDATION_DB_NAME" \
    < "$ROOT/src/main/resources/db/reviewed-migration/V32__shared_command_receipts.sql"
elif [[ "$receipt_table" != "t" ]]; then
  echo "Could not verify local command receipt storage; backend was not started." >&2
  exit 1
fi

"${compose[@]}" up --build -d validation-backend
echo "Validation API: ${VALIDATION_API_BASE_URL:-http://localhost:18080/api}"
echo "Check /api/version for commandReceipts=true and schemaReady=true."
echo "Run scripts/validation-seed.sh after the health endpoint responds."
