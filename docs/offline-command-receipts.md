# Shared command receipts: contract and staged rollout

This implementation covers all nine advertised v2 operation types: `BATCH_EVENT`, `FARM_INPUT`,
`SELECTION_SESSION`, `SELECTION_SESSION_UPDATE`, `SEX_COMPOSITION`, `VACCINATION_PLAN`,
`VACCINATION_SCHEDULE`, `TASK_STATUS`, and `DAILY_RECORD`. Domain validation and locks remain
inside the shared transaction. The rollout gate remains off until an approved schema rollout.

## Transaction and identity

The Spring `CommandReceiptService` reserves a global UUID in `offline_command_receipt`
with PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`, before executing business work.
The reservation, business writes and serialized successful outcome use the same Spring-managed
transaction. A competing transaction waits for the first commit/rollback. Failed work rolls
back the reservation and domain writes. A committed replay returns its stored outcome;
it does not re-run mutable draft, lifecycle or plan logic.

The binding includes authenticated farm/user, schema version, entity type, resource type/id
and canonical payload. Scope mismatches return an opaque conflict without fetching another
actor's outcome. Receipt equality uses JSONB: object key order and decimal scale are insignificant;
array order, nulls, strings and changed quantities remain significant. No quantity rounding is
introduced. Numeric precision rules for the remaining operations are a separate contract task.

Selections normalize criterion codes to sorted distinct uppercase values. Their canonical payload
includes batchId and expectedVersion. The selectionDate payload remains a farm calendar date.
The transport envelope occurredAt is not an edit's business date and is not part of selection
receipt equality: this lets an existing direct-online DTO and its outbox fallback identify the
same edit. The original DTO outcome includes the committed revision and updatedAt.

Schedules bind the original envelope instant as commandOccurredAt in the canonical payload,
plus action, original program input/ID and batch scope. CREATE_AND_ASSIGN creates the template
and assigns/replaces the intended plan atomically. Its outcome retains original program ID,
plan ID and server time. Existing completed-history replacement behavior is preserved.

Receipts have no expiry, cascading deletion or automatic reconstruction from current entities.
Receipt retention/privacy and a supported maximum offline age require architect decisions before
introducing cleanup. Domain callbacks must not call external services while holding reservations.

## Selection revisions and legacy work

Online PATCH and sync both enter SelectionSessionCommandService. Receipt replay is checked before
revision or finalized/archived-state checks. An unseen edit must supply expectedVersion and pass
the entity version check. Optimistic locking protects overlapping distinct edits. The response
exposes the committed version; the frontend captures it when opening the editor and assigns a
new UUID to each editing session. Existing pending commands are not rewritten.

Old queued updates without expectedVersion cannot prove whether a prior send overwrote an earlier
revision. They return CONFLICT for review and are retained on the phone. We do not fingerprint
an old operation from the draft's current mutable contents. Legacy offline_sync_receipt schedule
rows remain intact; a matching legacy UUID without verifiable payload/actor evidence also requires
review. Unattempted old schedule commands without a legacy receipt can use the new boundary after
rollout. No new UUID is invented to retry an uncertain accepted command.

## Rollout gate: currently off

`APP_COMMAND_RECEIPTS_ENABLED` defaults to false. No new mapped JPA entity or schema.sql bootstrap
is introduced for this table. Normal startup keeps SQL initialization and Flyway disabled and
Hibernate at validate. The additive proposed migration is
`db/reviewed-migration/V32__shared_command_receipts.sql` in the existing reviewed Flyway track.
No Supabase CLI migration track is introduced into this project.

Before enabling, follow docs/migrations/README.md: identify the target and migration provenance,
back up and rehearse on a disposable restored copy, review privileges and apply only the approved
forward migration through the existing migration process. Never baseline an unaudited database
or enable Hibernate update/SQL initialization on a live farm to create this table.

Even when configured on, runtime verifies required receipt column types/non-nullability and an
actual valid immediate unique index on operation_id. Missing or incompatible storage fails closed.
Direct protected updates return HTTP 503; sync returns RETRYABLE. A disabled gate does not query
new receipt storage or apply the business operation. The queue keeps UUID, payload and scope.
`GET /api/version` adds offlineCapabilities, including commandReceipts/schemaReady and the nine
receiptEntityTypes only when ready; existing release metadata fields are retained. These fields
report this boundary only, not a complete offline/schema-readiness audit.

The receipt table contains farm command data and is server-only. Its migration enables RLS and
revokes browser-role access. A deployment using a non-owner backend database role needs an
explicit reviewed server-only privilege/RLS arrangement and transaction rehearsal before enabling;
never grant anon/authenticated access as a shortcut. See the Supabase guidance on
[row-level security](https://supabase.com/docs/guides/database/postgres/row-level-security).

## Client behavior and remaining work

Events, inputs, selection creates/updates, sex composition, task statuses, vaccination records
and batch schedule intents are persisted before network delivery. Online and
offline delivery use the same claimed outbox sender. Standalone vaccination template editing
retains its separate direct workflow and is not advertised as a receipt-backed command.
The gate may intentionally leave new protected work pending until the schema rollout is approved.

Remaining work includes a practical manager resolution flow for old conflicts, atomic durable local projections after
acknowledgement, full offline shell preparation and physical-phone release tests. No claim is
made that this change guarantees closed-app delivery or completes all idempotency work.

### Mixed-version clients and finalization

The client checks `/api/version` with the same session fence before sending receipt-required
commands. Missing, null, malformed, disabled or incompatible capability metadata holds the
protected batch as RETRYABLE; unrelated batches may progress. A legacy backend is never assumed
to enforce revisions merely because it accepts a newer JSON field. Capability requests do not
replace server validation. Every non-empty v2 command batch now requires an explicit compatible entity capability.
Unsupported batch heads remain queued; supported independent batches can progress.

An unchanged, acknowledged selection draft can finalize directly without queuing an artificial
update. Current clients send the reviewed expectedVersion with finalization; unseen changed
revisions conflict, and pending changes must sync and be reviewed first. Dirty edits are captured
once and finalized after acknowledgement. The optional finalization body preserves compatibility
with legacy callers; finalization itself is a terminal domain action, not an additional shared
receipt entity type. Its first acknowledgement uses PostgreSQL microsecond timestamp precision,
so a terminal retry does not change the returned finalization timestamp through a database round trip.

## Remaining operation boundaries and legacy evidence

Typed online requests carrying an operation UUID and their v2 equivalents share the same
canonical DTO, resource and actor binding. Null optional timestamps remain null in the canonical
intent; only first application derives missing defaults from the envelope. A retry receives the
original result and server time. Task visibility is checked before returning a stored task result.
Task and vaccination envelope batch mismatches cannot commit domain side effects. Daily v2
commands additionally bind the original envelope instant and owner fields.

The legacy guard checks domain, stock, financial and prior receipt ledgers before applying an
unseen shared UUID. Evidence without a shared receipt returns CONFLICT for manager review; it
does not invent a receipt from mutable current state. V27 daily receipts are the exception: their
complete immutable scope, payload and time can be verified and adopted without another write.
The V27 table is optional for new daily commands once shared storage is ready. Retain old ledgers.
The global legacy lookup needs a production-scale query/index review on a restored copy, especially
financial source_operation_id lookups, before enabling the gate.

Direct legacy requests without operation IDs and plain measurement POSTs do not acquire a new
shared identity. The deprecated /api/sync/batch retains new inserts and identical acknowledgements,
but conflicting existing measurements require review even with a future client clock. External
legacy callers need a migration plan. No queued UUID, payload, owner or timestamp is rewritten.

New selection drafts now save through the durable outbox. Finalization waits for acknowledgement
and review of the saved draft; the existing acknowledged unchanged-draft path remains available.
Physical-phone offline/install, closed-app recovery, conflict resolution and durable local
projection work remain separate release gates.
