#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/docker-compose.validation.yml"
PROJECT="${COMPOSE_PROJECT_NAME:-poultry-prophet-validation}"

case "$PROJECT" in
  *production*|*prod*) echo "Refusing a production-looking compose project name: $PROJECT" >&2; exit 1 ;;
esac

echo "This removes only the Poultry Prophet validation database volume for project $PROJECT."
read -r -p "Type RESET VALIDATION to continue: " confirmation
test "$confirmation" = "RESET VALIDATION" || { echo "Reset cancelled."; exit 1; }
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" down --volumes
echo "Validation database removed. It is recoverable only by reseeding the synthetic environment."
