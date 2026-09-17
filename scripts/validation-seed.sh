#!/usr/bin/env bash
set -euo pipefail

# Idempotent synthetic data seed for the isolated validation API.
# Required tools: curl, jq, uuidgen. Never point VALIDATION_API_BASE_URL at a deployed service.
API_BASE="${VALIDATION_API_BASE_URL:-http://localhost:18080/api}"
MANAGER_EMAIL="${VALIDATION_MANAGER_EMAIL:-manager.validation@example.test}"
MANAGER_NAME="${VALIDATION_MANAGER_NAME:-Validation Manager}"
HANDLER_EMAIL="${VALIDATION_HANDLER_EMAIL:-handler.validation@example.test}"
HANDLER_NAME="${VALIDATION_HANDLER_NAME:-Validation Handler}"

require_env() {
  local variable_name="$1"
  [[ -n "${!variable_name:-}" ]] || {
    echo "Set $variable_name in your untracked .env.validation file" >&2
    exit 1
  }
}

require_env VALIDATION_MANAGER_PASSWORD
require_env VALIDATION_HANDLER_PASSWORD
MANAGER_PASSWORD="$VALIDATION_MANAGER_PASSWORD"
HANDLER_PASSWORD="$VALIDATION_HANDLER_PASSWORD"

case "$API_BASE" in
  *vercel.app*|*render.com*|*supabase.co*)
    echo "Refusing to seed a deployed or hosted URL: $API_BASE" >&2
    exit 1
    ;;
esac

require_command() { command -v "$1" >/dev/null || { echo "Missing required command: $1" >&2; exit 1; }; }
require_command curl
require_command jq
require_command uuidgen

login_payload=$(jq -nc --arg email "$MANAGER_EMAIL" --arg password "$MANAGER_PASSWORD" \
  '{email:$email,password:$password}')
if manager_json=$(curl -fsS "$API_BASE/auth/login" -H 'Content-Type: application/json' -d "$login_payload"); then
  :
else
  register_payload=$(jq -nc --arg email "$MANAGER_EMAIL" --arg password "$MANAGER_PASSWORD" --arg name "$MANAGER_NAME" \
    '{email:$email,password:$password,fullName:$name}')
  manager_json=$(curl -fsS "$API_BASE/auth/register" -H 'Content-Type: application/json' -d "$register_payload")
fi
TOKEN=$(jq -r '.token // empty' <<<"$manager_json")
test -n "$TOKEN" || { echo "Validation manager login did not return a token" >&2; exit 1; }

auth_header=(-H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')
get() { curl -fsS "${auth_header[@]}" "$API_BASE$1"; }
post() { curl -fsS "${auth_header[@]}" -X POST "$API_BASE$1" -d "$2"; }

stage_id=$(get /lifecycle-stages | jq -r '.[0].id // empty')
test -n "$stage_id" || { echo "No lifecycle stage is available" >&2; exit 1; }
batches_json=$(get /batches)
handlers_json=$(get /handlers)
if ! jq -e --arg email "$HANDLER_EMAIL" '.[] | select(.email == $email)' <<<"$handlers_json" >/dev/null; then
  handler_payload=$(jq -nc --arg email "$HANDLER_EMAIL" --arg password "$HANDLER_PASSWORD" --arg name "$HANDLER_NAME" \
    '{email:$email,password:$password,fullName:$name}')
  post /handlers "$handler_payload" >/dev/null
fi

ensure_batch() {
  local name="$1"
  local existing
  existing=$(jq -r --arg name "$name" '.[] | select(.name == $name) | .id' <<<"$batches_json" | head -n 1)
  if [[ -n "$existing" ]]; then
    printf '%s\n' "$existing"
    return
  fi
  local payload
  payload=$(jq -nc --arg name "$name" --argjson stageId "$stage_id" \
    '{name:$name,initialPopulation:100,startDate:"2026-01-01",stageId:$stageId,bloodline:null,source:"synthetic validation"}')
  local created
  created=$(post /batches "$payload")
  local id
  id=$(jq -r '.id // empty' <<<"$created")
  test -n "$id" || { echo "Could not create batch $name" >&2; exit 1; }
  batches_json=$(jq --arg name "$name" --argjson id "$id" '. + [{name:$name,id:$id}]' <<<"$batches_json")
  printf '%s\n' "$id"
}

record() {
  local batch_id="$1" date="$2" temp="$3" feed="$4" water="$5" notes="${6:-}"
  local payload
  payload=$(jq -nc --arg date "$date" --argjson temp "$temp" --argjson feed "$feed" \
    --argjson water "$water" --arg notes "$notes" \
    '{recordDate:$date,temperatureC:$temp,feedIntakeG:$feed,waterIntakeMl:$water,behaviorNotes:($notes|if length==0 then null else . end)}')
  post "/batches/$batch_id/records" "$payload" >/dev/null
}

event() {
  local batch_id="$1" date="$2" type="$3" count="$4" title="$5" operation_id="${6:-$(uuidgen)}"
  local payload
  payload=$(jq -nc --arg date "$date" --arg type "$type" --arg title "$title" \
    --arg op "$operation_id" --argjson count "$count" \
    '{eventDate:$date,eventType:$type,title:$title,affectedCount:$count,operationId:$op,details:"Synthetic scenario; not a production record."}')
  post "/batches/$batch_id/events" "$payload"
}

D1=$(date -u -d '3 days ago' +%F)
D2=$(date -u -d '2 days ago' +%F)
D3=$(date -u -d '1 day ago' +%F)
TODAY=$(date -u +%F)

echo "Seeding scenario 01: baseline observations"
B=$(ensure_batch "VALIDATION – 01 Baseline")
record "$B" "$D1" 33 1000 1800
record "$B" "$D2" 33 1010 1810
record "$B" "$D3" 32.8 995 1790

echo "Seeding scenario 02: insufficient baseline"
B=$(ensure_batch "VALIDATION – 02 Insufficient data")
record "$B" "$TODAY" 33 1000 1800

echo "Seeding scenario 03: health death affects health analytics"
B=$(ensure_batch "VALIDATION – 03 Health death")
event "$B" "$TODAY" HEALTH_DEATH 2 "Health-related death" "00000000-0000-4000-8000-000000000301"
record "$B" "$TODAY" 33 980 1760 "Health-death category is included in BHI mortality input."

echo "Seeding scenario 04: accidental death affects population only"
B=$(ensure_batch "VALIDATION – 04 Accidental death")
event "$B" "$TODAY" ACCIDENTAL_DEATH 1 "Accidental death" "00000000-0000-4000-8000-000000000401"
record "$B" "$TODAY" 33 1000 1800

echo "Seeding scenario 05: suspected predation affects population only"
B=$(ensure_batch "VALIDATION – 05 Suspected predation")
event "$B" "$TODAY" SUSPECTED_PREDATION 2 "Suspected predation" "00000000-0000-4000-8000-000000000501"
record "$B" "$TODAY" 33 1000 1800

echo "Seeding scenario 06: missing then found returned"
B=$(ensure_batch "VALIDATION – 06 Missing and returned")
event "$B" "$D2" MISSING 2 "Missing birds" "00000000-0000-4000-8000-000000000601"
event "$B" "$D3" FOUND_RETURNED 1 "Found and returned" "00000000-0000-4000-8000-000000000602"
record "$B" "$D3" 33 1000 1800

echo "Seeding scenario 07: transfer out and sale"
B=$(ensure_batch "VALIDATION – 07 Transfer and sale")
event "$B" "$D2" TRANSFER_OUT 1 "Transfer out" "00000000-0000-4000-8000-000000000701"
event "$B" "$D3" SALE 1 "Sale" "00000000-0000-4000-8000-000000000702"
record "$B" "$D3" 33 1000 1800

echo "Seeding scenario 08: backdated observation recompute"
B=$(ensure_batch "VALIDATION – 08 Backdated observation")
record "$B" "$D3" 34 1000 1800
record "$B" "$D1" 31 700 1200 "Inserted after the later observation to test date-order recomputation."
record "$B" "$D2" 32 900 1600

echo "Seeding scenario 09: zero feed yields unavailable WFR"
B=$(ensure_batch "VALIDATION – 09 Zero feed")
record "$B" "$TODAY" 33 0 100 "Zero feed is intentional synthetic input."

echo "Seeding scenario 10: retry-safe operationId"
B=$(ensure_batch "VALIDATION – 10 Idempotent retry")
OP="00000000-0000-4000-8000-000000001001"
FIRST=$(event "$B" "$TODAY" ACCIDENTAL_DEATH 1 "Retry-safe accidental death" "$OP")
SECOND=$(event "$B" "$TODAY" ACCIDENTAL_DEATH 1 "Retry-safe accidental death" "$OP")
FIRST_ID=$(jq -r '.id' <<<"$FIRST")
SECOND_ID=$(jq -r '.id' <<<"$SECOND")
test "$FIRST_ID" = "$SECOND_ID" || { echo "operationId was not idempotent" >&2; exit 1; }
record "$B" "$TODAY" 33 1000 1800

echo "Synthetic validation seed complete. Ten scenario batches are available under the validation manager account."
