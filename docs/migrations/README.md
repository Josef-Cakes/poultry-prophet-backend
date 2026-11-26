# Database migration safety and baseline runbook

## Current state

Normal startup uses Hibernate `validate`, disables Spring SQL initialization, and disables
Flyway. Validation checks mapped entities, not all indexes, constraints, data, or backfills.
Application seeders can still write data on startup. A blank or incompatible schema can prevent
startup; this change intentionally provides no fresh-database schema bootstrap for production.

The `validation` profile deliberately retains Hibernate `update` and `schema.sql` for a
disposable local database. It is not a migration test and does not enforce a local host. Use
the isolated Compose workflow with a local Docker daemon; never select this profile for a
remote or shared database. The validation initializer mirrors V34's correction foreign key
and unique index so fresh local validation databases exercise the same basic safeguards;
it does not create a Flyway history or verify production migration compatibility.

`db/migration` is a frozen collection of legacy scripts, not a Flyway history. Some SQL is
duplicated in `schema.sql`, some depends on tables created by Hibernate or previous changes,
and V2 performs a data repair with its own `app_schema_migration` record. The repository does
not establish which scripts, pieces of `schema.sql`, or manual changes any database received.
No Flyway history is implied by a script existing here. Do not add this directory to Flyway
locations, replay it wholesale, alter previously executed files, or use `repair` to hide
discrepancies.

## Reviewed track and version 15

Flyway dependencies are installed, but its configured location is only
`classpath:db/reviewed-migration`, which contains V26–V34: credential versions, daily receipts, report-linked
selection sessions, derby dates, transfer population safeguards, unique open review
follow-ups, shared command receipts, backend-only application table access, and the proposed
expense-correction links. V34 still requires target-specific review and rollout approval.
`baseline-on-migrate` is false,
`baseline-version` is 25, validation is enabled, and clean is disabled. Flyway is disabled
by default and explicitly in Render and the validation profile. The first reviewed migration
must have a unique integer version of **26 or higher**; `python scripts/check-migration-track.py`
checks naming and collisions without connecting to any database. This check is not SQL review.

Both `V15__auth_refresh_tokens.sql` and `V15__offline_field_sync.sql` remain unchanged in the
legacy directory. A Flyway version can identify only one migration: renumbering either file
would misrepresent any existing history. Instead, the proposed baseline covers the legacy
period through V25, and any missing effects of either V15 require separately reviewed,
uniquely versioned forward migrations after determining each database's actual state. A V25
baseline is a statement that a specifically audited schema is suitable as the starting point;
it does **not** assert that every legacy script ran. Do not baseline a database until the
two V15 effects, later versions, manual changes, and data invariants have been reconciled.

## Before any baseline or rollout

This is an operational procedure for a future authorized operator, not an instruction to
connect or make changes now:

1. Identify the exact database, schema, application release, and owner; take a verified backup
   and rehearse restoration. Collect a schema-only snapshot and existing migration records,
   including `flyway_schema_history` and `app_schema_migration` if present. Treat absence of
   a record as unknown, not evidence that SQL is safe to replay.
2. Compare the actual tables, columns, types, constraints, indexes, and data invariants against
   the intended application release, `schema.sql`, and each relevant legacy change including
   **both** V15 files and V16–V25. Document evidence, differences, and any required data repair.
   Review and test separate forward fixes on a disposable restored copy; preserve a rollback
   and recovery plan. Do not assume Hibernate `validate` covers this audit.
3. If the database already has Flyway history, stop and reconcile its versions, descriptions,
   checksums and location with a migration owner. Do not baseline over or rewrite that history;
   this proposed V25 adoption applies only after a compatible history strategy is agreed.
4. For an audited existing nonempty schema with **no** Flyway history, the proposed adoption is
   a one-time, explicit Flyway `baseline` at version 25 against the correct schema, with the
   reviewed location configured and no legacy locations. Record the database identity, backup,
   audit, approvals, exact Flyway version, command, output, and resulting history entry. A
   baseline creates migration metadata; it does not create or repair application objects.
5. On a disposable restored copy, verify the baseline and run `validate`, then test reviewed
   V26+ migrations and a second migrate (no repeated changes). Check schema and data invariants,
   startup compatibility, and the recovery procedure. An empty reviewed track alone does not
   exercise migration execution.
6. Only after explicit rollout approval, arrange the corresponding database operation and
   separately change deployment configuration to enable the reviewed track. Render currently
   pins `SPRING_FLYWAY_ENABLED=false`; changing the YAML default alone cannot enable it there.
   Never set `baseline-on-migrate=true` as a shortcut.

Fresh production databases are blocked until a separately reviewed, reproducible initial schema
and upgrade path exist. Do not use `schema.sql`, Hibernate `update`, or baseline an empty
database as a substitute. The existing V2 mortality repair and inventory runner have separate
backup, preview, and approval requirements; their presence does not authorize execution.

## Proposed V32 shared command receipt boundary

V32 adds a server-only `offline_command_receipt` table for selection draft update and
vaccination schedule commands. It is additive; existing domain IDs, old schedule receipts,
and V27 daily receipts remain untouched. Do not backfill hashes from mutable current drafts.
See [the receipt contract](../offline-command-receipts.md) for exact identity/legacy handling.

`APP_COMMAND_RECEIPTS_ENABLED` remains false until the target is audited, migration/privileges
are reviewed and rehearsed, and the owner approves deployment. The new table is not a JPA
entity and is not added to `schema.sql`; startup cannot silently create it. No live migration
is authorized by implementing this source change. Disabling the gate retains protected queued
commands with RETRYABLE/503 responses. It does not route them through an unsafe legacy write.

Rehearse V32 and a second Flyway migrate on a disposable restored copy. Verify one effect under
concurrent duplicate UUIDs, rollback of receipt plus domain work, original outcome after lost
responses, and opaque conflicts for changed scope/payload. Rehearse the backend database role
with RLS/privileges before enabling; browser API roles must not read receipt payloads. No TTL or
receipt cleanup is approved. A rollback must retain the new receipt evidence and avoid routing
already accepted commands through an older unprotected application build.


## Existing Supabase target rehearsal — November 22, 2026

See [release verification](../RELEASE_VERIFICATION_2026-11-22.md). The existing target
has no Flyway history; the audited baseline is V25. A private public-schema backup
was restored into isolated PostgreSQL 17, V26–V33 applied successfully, a second
migrate applied zero changes, and every original table row count remained unchanged.
The user explicitly approved rollout on November 22, 2026. The existing Supabase
target was then baselined at V25 and upgraded through V33; validation and replay
checks passed, and all 34 original table row counts were preserved. A fresh private
backup was taken immediately before applying the rollout. Run
`scripts/release-preflight.sql` read-only before future operations.

V33 enables RLS and removes PUBLIC/anon/authenticated access to application tables.
Spring Boot remains the authorization boundary. The audited backend database role
has BYPASSRLS; browser roles must have no application-table policies or grants.
Do not add broad browser policies to work around deployment failures.

### Proposed V34 expense corrections

V34 adds two nullable columns, a self-referencing foreign key without cascading deletes,
and a partial unique index so an expense has at most one direct correction. Existing
expenses are not rewritten. It fails if either column or the index already exists: an
operator must inspect its types, constraints, index definition, existing values, and
Flyway history and agree on a separate forward strategy for any manually changed schema.
Do not mark a conflicting migration applied or use `repair` to bypass this review.

Before any future rollout, take and verify a backup, test V34 and a second no-op migrate
on a disposable restoration of the audited target, and check existing rows, constraints,
indexes, and Hibernate validation. Plan a maintenance window: `ALTER TABLE` and the
transactional index build acquire locks. Keep Flyway disabled at normal startup. Never
drop correction columns or records merely to roll back application code; coordinate any
recovery with the database owner and reconcile later financial writes.

After approval, explicitly baseline the audited nonempty public schema at V25 using
only `db/reviewed-migration`; then migrate V26–V33 and validate. Never scan the legacy
track, repair checksums, or enable automatic baselining. Recheck row counts, history,
RLS, browser privileges, and Spring Hibernate `validate` before enabling command receipts.
Enable `APP_COMMAND_RECEIPTS_ENABLED=true` only for the matching verified backend;
verify `/api/version` reports command receipts and schema readiness. Until then,
protected queued commands must stay on the device with retryable responses.

Keep the additive receipt tables and records when reverting application binaries.
Do not revert to an older write path that ignores receipts. If recovery requires
restoration, pause all writers and restore into a separate database first; reconcile
post-backup writes before any cutover. Retain the private schema/ACL dump so grant
changes can be reviewed exactly; never regrant anonymous application access as a shortcut.
