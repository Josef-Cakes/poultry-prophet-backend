# Improvement plan: implementation status

This work is not ready for production rollout. The prior changes were committed on the
`poultry-project-improvements` branches; subsequent work may be uncommitted. The currently
running local API may still be an older process. No supplied Supabase data has been
modified by the validation tests.

## Implemented and exercised

- Next.js was upgraded and dependency-audit findings were reduced; the frontend dependency
  audit details live in `poultry-prophet-frontend/docs/DEPENDENCY_AUDIT.md`.
- Access tokens are bound to immutable user IDs and a credential version. Refresh reuse and
  password-change revocation have been exercised against a disposable PostgreSQL database.
- The frontend session proxy uses an HttpOnly refresh cookie, memory-only access tokens,
  same-origin checks, cross-tab coordination and local-first sign-out. The offline profile is
  retained when authentication expires.
- Normal database startup validates schema and does not run legacy SQL. The proposed V25
  baseline and V26/V27 reviewed migrations have been previewed in a synthetic schema. This is
  not evidence that an existing farm database is compatible.
- Offline queue claims use cross-tab leases and per-batch ordering. Daily measurements have an
  idempotent replay path, but the frontend feature remains off by default pending coordinated
  deployment and the V27 migration.
- Handler-facing inventory, input and event responses redact financial values. Paid inventory
  purchase replay and the resulting expense have been checked against disposable PostgreSQL.
- Finance, operations, farm overview and Selection Review expose currency-qualified totals.
  Historical inventory usage follows the existing PHP valuation convention; historical Selection
  Review snapshots without currency metadata do not expose ambiguous financial totals.
- Liveness and bounded database readiness are separate. The frontend proxy supplies correlation
  IDs and a timeout; service-worker caching and recording-input labels have focused coverage.
- Both repositories contain CI checks. The backend CI uses disposable PostgreSQL for the
  opt-in integration suite; see the validation runbook for local commands and safety conditions.

## Work still required

1. Implement manager sale approval, including pending handler sales, audited correction or
   reversal, and protection against duplicate posting of ambiguous historical sales. Current
   sale events can still post income immediately.
2. Add a correction/resubmission and authenticated manager-review workflow for rejected or
   conflicting offline records. The sync indicator now displays server-provided reasons while
   leaving those records on the device; this is not a manager-review or correction workflow.
   Verify vaccination replay and all replay operations against
   PostgreSQL, including failure rollback and duplicate delivery.
3. Add login and refresh throttling after establishing a trustworthy client-identity boundary;
   see `AUTH_THROTTLING_TRUST_BOUNDARY.md`.
4. Complete concurrency and role-isolation coverage for inventory, handlers, tasks, vaccination
   reminders, backdated population updates and all financial response paths.
5. Complete per-farm time-zone configuration and boundary coverage. Current frontend date-only
   calculations use the Asia/Manila default, rather than a per-farm setting.
6. Add alerts for queue failures, conflicts and readiness loss, complete accessibility and
   screen-reader testing, and measure bootstrap, legacy finance and outbox performance.
7. Improve authentication-lock recovery: an abandoned IndexedDB lock currently fails closed
   and may require deleting only the separate lock database after all app tabs are closed. Never
   clear all site data because it can contain unsynced farm records.
8. Review the existing database and both legacy V15 scripts, rehearse backup and restoration,
   and test the approved migration sequence on a disposable restored copy. Coordinate schema,
   identity and frontend deployment, require users to sign in again, and preserve offline work.
   Rotate any supplied database password or JWT signing key used for real farm data.

The validation tests do not establish production compromise or production deployment readiness.
