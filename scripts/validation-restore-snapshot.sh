#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT/docker-compose.validation.yml"
PROJECT="${COMPOSE_PROJECT_NAME:-poultry-prophet-validation}"
SNAPSHOT="${1:-}"

case "$PROJECT" in
  *production*|*prod*) echo "Refusing a production-looking compose project name: $PROJECT" >&2; exit 1 ;;
esac
test -n "${VALIDATION_DB_NAME:-}" || { echo "Set VALIDATION_DB_NAME" >&2; exit 1; }
test -n "${VALIDATION_DB_USER:-}" || { echo "Set VALIDATION_DB_USER" >&2; exit 1; }
test -n "${VALIDATION_DB_PASSWORD:-}" || { echo "Set VALIDATION_DB_PASSWORD" >&2; exit 1; }
case "$VALIDATION_DB_NAME" in
  *validation*|*test*) ;;
  *) echo "Refusing a non-validation database name: $VALIDATION_DB_NAME" >&2; exit 1 ;;
esac
test -f "$SNAPSHOT" || { echo "Snapshot file not found: $SNAPSHOT" >&2; exit 1; }

read -r -p "Type RESTORE LOCAL VALIDATION SNAPSHOT to continue: " confirmation
[[ "$confirmation" == "RESTORE LOCAL VALIDATION SNAPSHOT" ]] || { echo "Restore cancelled" >&2; exit 1; }

docker compose -p "$PROJECT" -f "$COMPOSE_FILE" stop validation-backend >/dev/null 2>&1 || true
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d validation-db >/dev/null
until docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T validation-db pg_isready -U "$VALIDATION_DB_USER" -d "$VALIDATION_DB_NAME" >/dev/null 2>&1; do
  sleep 2
done

case "$SNAPSHOT" in
  *.sql)
    docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T validation-db \
      psql --single-transaction -v ON_ERROR_STOP=1 -U "$VALIDATION_DB_USER" -d "$VALIDATION_DB_NAME" < "$SNAPSHOT"
    ;;
  *)
    docker compose -p "$PROJECT" -f "$COMPOSE_FILE" exec -T validation-db \
      pg_restore --clean --if-exists --no-owner --no-acl -U "$VALIDATION_DB_USER" -d "$VALIDATION_DB_NAME" < "$SNAPSHOT"
    ;;
esac

echo "Snapshot restored into the local validation database. Run validation-sanitize-local-data.sh before starting the backend."
