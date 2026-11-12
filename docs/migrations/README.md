# Database migration safety and baseline runbook

## Current state

Normal startup uses Hibernate `validate`, disables Spring SQL initialization, and disables
Flyway. Validation checks mapped entities, not all indexes, constraints, data, or backfills.
Application seeders can still write data on startup. A blank or incompatible schema can prevent
startup; this change intentionally provides no fresh-database schema bootstrap for production.

The `validation` profile deliberately retains Hibernate `update` and `schema.sql` for a
disposable local database. It is not a migration test and does not enforce a local host. Use
the isolated Compose workflow with a local Docker daemon; never select this profile for a
remote or shared database.

`db/migration` is a frozen collection of legacy scripts, not a Flyway history. Some SQL is
duplicated in `schema.sql`, some depends on tables created by Hibernate or previous changes,
and V2 performs a data repair with its own `app_schema_migration` record. The repository does
not establish which scripts, pieces of `schema.sql`, or manual changes any database received.
No Flyway history is implied by a script existing here. Do not add this directory to Flyway
locations, replay it wholesale, alter previously executed files, or use `repair` to hide
discrepancies.

## Reviewed track and version 15

Flyway dependencies are installed, but its configured location is only
`classpath:db/reviewed-migration`, which contains the proposed V26 credential-version and V27
offline daily-receipt migrations. `baseline-on-migrate` is false,
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
