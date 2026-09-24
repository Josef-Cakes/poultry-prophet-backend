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

docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up --build -d
echo "Validation API: ${VALIDATION_API_BASE_URL:-http://localhost:18080/api}"
echo "Run scripts/validation-seed.sh after the health endpoint responds."
