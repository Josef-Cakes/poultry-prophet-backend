# Local real-data validation runbook

> **Advanced workflow:** You do not need this environment-variable setup for normal testing.
> Use the manager-only Local Test Lab for the direct one-click path described in
> `docs/LOCAL_TEST_LAB_GUIDE.md`. Use this runbook only for database snapshots,
> destructive isolation, or clean-room validation.

## Purpose

This runbook creates a disposable local validation copy of the farm data. The copied data can be inspected and edited through the same REST API and frontend used by the application, but it is isolated from Render, production credentials, and the production database.

The validation environment is for internal testing and demonstration. Do not use it as a second production deployment.

## Safety rules

- Use a separate Docker Compose project and a database name containing `validation` or `test`.
- Use local API URLs only: `http://localhost:.../api` or `http://127.0.0.1:.../api`.
- Never put production passwords, JWT secrets, or stakeholder exports in Git.
- Always restore a backup before changing a copied real-data snapshot.
- Sanitize names, emails, locations, invite tokens, and free-text notes before showing the data to another person.
- Synthetic batches must be clearly labelled `[TEST COPY]` and `SYNTHETIC_VALIDATION`.

## Start a clean validation stack

From the backend repository:

```bash
cp .env.validation.example .env.validation
chmod 600 .env.validation
```

Fill `.env.validation` with disposable local values. The manager and handler emails/passwords must be present. Then load the values into the shell and start the stack:

```bash
set -a
source .env.validation
set +a
export COMPOSE_PROJECT_NAME=poultry-prophet-validation-local
export VALIDATION_API_BASE_URL="http://localhost:${VALIDATION_API_PORT:-18080}/api"
./scripts/validation-up.sh
```

Wait for the backend health endpoint, then log in with the configured manager account. The validation profile provisions the configured manager and handler into the selected local farm at startup.

```bash
curl http://localhost:${VALIDATION_API_PORT:-18080}/api/health
curl http://localhost:${VALIDATION_API_PORT:-18080}/api/version
```

The version response must report `environment: validation` before any data work begins.

## Seed deterministic baseline scenarios

```bash
./scripts/validation-seed.sh
```

The seed is idempotent for the named scenario batches and exercises health-related death, non-health losses, missing/returned birds, transfers, sale, backdated observations, zero-feed handling, and duplicate-operation protection.

## Restore a local snapshot

Create a local custom-format backup from the validation database when needed:

```bash
mkdir -p local-backups
docker compose -p "$COMPOSE_PROJECT_NAME" -f docker-compose.validation.yml \
  exec -T validation-db pg_dump -Fc -U "$VALIDATION_DB_USER" -d "$VALIDATION_DB_NAME" \
  > "local-backups/validation-$(date +%Y%m%d-%H%M%S).dump"
```

To restore an approved local snapshot, stop the backend, restore the database, sanitize it, and then start the backend again:

```bash
./scripts/validation-restore-snapshot.sh local-backups/approved-snapshot.dump
./scripts/validation-sanitize-local-data.sh
./scripts/validation-up.sh
```

Both scripts require an explicit confirmation phrase and reject production-looking Compose projects and database names. The restore script accepts custom-format dumps and plain `.sql` files.

The sanitizer changes farm labels and locations, anonymizes users except the configured validation accounts, invalidates invite tokens, and replaces invite emails. It does not guarantee that arbitrary notes or uploaded files contain no identifying information; review those manually before sharing.

## Run the 100-day API simulation

Use a real local batch as the source. The script creates a new batch and never edits the source batch:

```bash
node scripts/validation-simulate-100-days.mjs \
  --api-url "$VALIDATION_API_BASE_URL" \
  --source-batch-id 1 \
  --days 100 \
  --seed 4112026 \
  --profile realistic \
  --dry-run
```

Review the generated plan. Apply only when the API version is local validation and the source batch is safe to copy:

```bash
node scripts/validation-simulate-100-days.mjs \
  --api-url "$VALIDATION_API_BASE_URL" \
  --source-batch-id 1 \
  --days 100 \
  --seed 4112026 \
  --profile realistic \
  --apply --confirm SYNTHETIC-ONLY
```

The simulator uses the normal authentication and REST endpoints. It creates dated events, observations, feed/medicine input logs, expenses/income, a completed handler task, checkpoint Selection Review reports, and an artifact directory with a manifest, expected results, verification results, and the final PDF.

Do not use a hosted URL. The simulator rejects non-local API URLs, non-validation API versions, wrong roles, archived sources, and duplicate deterministic runs.

## Reset and repeat

For a clean repeat, use a new seed or restore the validation snapshot. Do not delete volumes casually. If a disposable validation volume must be removed, confirm the Compose project name first and remove only that validation project volume.

## Acceptance checklist

- `/api/version` reports `environment=validation`.
- The source batch is unchanged.
- The generated batch name includes `[TEST COPY]`.
- Events and observations appear through the normal UI and API.
- Checkpoint reports show the historical population as of each checkpoint date.
- The artifact verification report says `PASS`.
- No validation artifact, dump, password, or personal data is committed.
