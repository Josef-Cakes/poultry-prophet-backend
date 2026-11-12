# Poultry Prophet MVP validation environment runbook

This runbook is for the isolated, synthetic-data environment only. It does not authorize a production migration, deployment, or data repair.

## Preconditions

- Docker Compose, `curl`, `jq`, and `uuidgen` are installed.
- Use a disposable validation database name such as `poultry_prophet_validation`.
- Test Lab requires explicit `APP_TEST_LAB_ENABLED=true` and a database name ending in `_validation` or `_test`. These checks reduce accidents but do not prove that a local database or tunnel contains disposable data.
- Generate a private JWT secret for this environment; do not reuse a production secret.
- Copy `.env.validation.example` to a local, ignored shell environment or export the variables directly. The example intentionally contains names only.
- Use a local Docker daemon, not a remote Docker context. Compose binds the database and API
  ports to loopback. The validation profile updates schema and runs legacy `schema.sql`; it
  must never point at a remote or shared database. The profile itself does not enforce this.

## Start

```bash
cd poultry-prophet-backend
export VALIDATION_DB_NAME=poultry_prophet_validation
export VALIDATION_DB_USER=validation_user
export VALIDATION_DB_PASSWORD='<local-only-password>'
export VALIDATION_JWT_SECRET='<base64-random-secret>'
export VALIDATION_MANAGER_PASSWORD='<local-only-password>'
export VALIDATION_API_BASE_URL=http://localhost:18080/api
./scripts/validation-up.sh
curl -fsS http://localhost:18080/api/health
curl -fsS http://localhost:18080/api/version | jq -e '.application == "poultry-prophet-backend" and .environment == "validation"'
./scripts/validation-seed.sh
```

Run the frontend separately with `.env.validation.local` values:

```text
NEXT_PUBLIC_API_BASE_URL=http://localhost:18080/api
BACKEND_API_URL=http://localhost:18080/api
NEXT_PUBLIC_APP_VERSION=0.1.0-validation
NEXT_PUBLIC_APP_ENVIRONMENT=validation
NEXT_PUBLIC_BACKEND_VERSION=0.1.0-validation
```

The UI must show `VALIDATION ENVIRONMENT — SYNTHETIC DATA` and the release footer. Never set the API URL to the deployed Render or Vercel services while using the seed script.

This bootstrap is for disposable validation data, not a test of the reviewed Flyway track or
proof that an existing production schema is compatible. Normal startup uses schema validation
and disables SQL initialization. See [migration runbook](migrations/README.md).

## Opt-in integration checks

The backend integration tests only run when `POULTRY_DISPOSABLE_DB_URL` is exactly
`jdbc:postgresql://127.0.0.1:25432/poultry_prophet_validation`, with a local `poultry_validation`
user. Before setting it, create a **new, empty, disposable** PostgreSQL cluster at that address;
the address and database name alone do not prove a database is disposable. Apply the reviewed
V27 receipt SQL to that disposable database, then run `./mvnw test`. The tests create synthetic
accounts and farm records and also create a separate schema to preview the V25 baseline and
V26–V27 migrations. CI creates its own temporary PostgreSQL service for the same tests.

For the frontend proxy smoke test, start the validation API against that disposable database on
`127.0.0.1:18081`, verify `/api/version` identifies the validation environment, then run
`POULTRY_DISPOSABLE_BACKEND_URL=http://127.0.0.1:18081/api node --test tests/disposable-bff.test.cjs`
from the frontend. This creates a synthetic account. Never point either check at real farm data.

## Seed contract

`validation-seed.sh` is idempotent by scenario batch name and stable operation IDs. It creates ten named scenario batches:

1. Baseline observations with enough history for BHI.
2. One observation, which must show insufficient data rather than 100.
3. Health death, included in the BHI mortality factor.
4. Accidental death, population effect only.
5. Suspected predation, population effect only.
6. Missing followed by found-returned.
7. Transfer-out and sale.
8. Backdated observations, requiring later indicators to recompute by observation date.
9. Zero feed, where WFR is unavailable.
10. Replayed operation ID, which must return the original event without double deduction.

## Reset and stop

Reset is intentionally interactive and volume-scoped:

```bash
./scripts/validation-reset.sh
```

It removes only the Compose project’s validation volume. Reseed after reset. `docker compose ... stop` may be used when the volume must be retained. There is no reset command for a production database in this repository.

## Evidence to capture

For every stakeholder session record the git commit, `/api/version` response, environment name, seed timestamp, account role, scenario name, and pass/fail result. Preserve screenshots or JSON responses in the session folder; do not include passwords or JWTs.
