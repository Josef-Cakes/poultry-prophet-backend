#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT/docker-compose.validation.yml"
PROJECT="${COMPOSE_PROJECT_NAME:-poultry-prophet-validation}"

case "$PROJECT" in
  *production*|*prod*) echo "Refusing a production-looking compose project name: $PROJECT" >&2; exit 1 ;;
esac
test -n "${VALIDATION_DB_NAME:-}" || { echo "Set VALIDATION_DB_NAME" >&2; exit 1; }
test -n "${VALIDATION_DB_USER:-}" || { echo "Set VALIDATION_DB_USER" >&2; exit 1; }
test -n "${VALIDATION_DB_PASSWORD:-}" || { echo "Set VALIDATION_DB_PASSWORD" >&2; exit 1; }
test -n "${VALIDATION_MANAGER_EMAIL:-}" || { echo "Set VALIDATION_MANAGER_EMAIL" >&2; exit 1; }
test -n "${VALIDATION_HANDLER_EMAIL:-}" || { echo "Set VALIDATION_HANDLER_EMAIL" >&2; exit 1; }
case "$VALIDATION_DB_NAME" in
  *validation*|*test*) ;;
  *) echo "Refusing a non-validation database name: $VALIDATION_DB_NAME" >&2; exit 1 ;;
esac

read -r -p "Type SANITIZE LOCAL SNAPSHOT to continue: " confirmation
[[ "$confirmation" == "SANITIZE LOCAL SNAPSHOT" ]] || { echo "Sanitization cancelled" >&2; exit 1; }

docker compose -p "$PROJECT" -f "$COMPOSE_FILE" stop validation-backend >/dev/null 2>&1 || true
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d validation-db >/dev/null
until docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T validation-db pg_isready -U "$VALIDATION_DB_USER" -d "$VALIDATION_DB_NAME" >/dev/null 2>&1; do
  sleep 2
done

docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T validation-db \
  psql --single-transaction -v ON_ERROR_STOP=1 \
  -v validation_manager_email="$VALIDATION_MANAGER_EMAIL" \
  -v validation_handler_email="$VALIDATION_HANDLER_EMAIL" \
  -U "$VALIDATION_DB_USER" -d "$VALIDATION_DB_NAME" <<'SQL'
BEGIN;

UPDATE farm
SET name = 'Local snapshot farm ' || id,
    location = NULL,
    description = 'Sanitized local validation snapshot.';

UPDATE app_user
SET email = 'snapshot-user-' || id || '@local.test',
    full_name = 'Snapshot User ' || id
WHERE lower(email) NOT IN (lower(:'validation_manager_email'),
                           lower(:'validation_handler_email'));

UPDATE handler_invite
SET token = 'local-invalid-' || id,
    email = 'invite-' || id || '@local.test';

COMMIT;
SQL

echo "Local snapshot sanitized. ValidationAccountSeeder will restore the configured manager and handler accounts on backend startup."
