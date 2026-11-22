# Release verification — November 22, 2026

Status: **live database rollout and local release checks verified**.
The user approved the existing Supabase rollout on November 22, 2026. Baseline V25
and migrations V26–V33 are applied and validated. Deployment verification remains
a separate gate. Nothing has been pushed or deployed by this work.

## Changes made during this release investigation

- Fixed Test Lab event UUIDs so two synthetic copies made with the same seed/date
  cannot collide globally across batches or farms. Integration coverage creates a
  second copy and verifies that neither copy edits the source batch.
- Added reviewed V33: enable RLS and revoke PUBLIC/anon/authenticated privileges on
  the explicit application table list. Spring Boot remains the authorization boundary.
  Tested idempotence, actual browser-role privileges, owner access, and future-table ACLs.
- Patched the frontend minimatch dependency to 11.0.1 for affected modern consumers;
  retained older ESLint consumer compatibility. Moved shadcn to development dependencies.
  Both production and full dependency audits report zero known advisories at execution.
- Fixed the persistent first-install offline setup notification: dismissible, timed, and non-blocking at the bottom, with mobile navigation clearance.
- Updated stale browser fixtures to use valid UUID operation IDs and advertise the
  backend receipt protocol they exercise. Archive deletion assertions now await the request.
- Frontend CI runs all unit tests and a production dependency audit. Backend CI opts
  into the disposable integration database, including the receipt and migration tests.

## Executed verification

- Backend: **243 tests, zero failures/errors/skips**, 49 current-run suites. Historical
  temporary read-only report files were excluded from this total.
- Frontend: **93 tests, zero failures/skips**, including the real disposable API proxy
  test covering registration, HttpOnly/SameSite cookie storage, token rotation, and logout.
- Frontend lint passed. Production build and browser results are recorded below after
  the final notification patch.
- Migration filename checks and all four checker tests passed.
- PostgreSQL 17 restore rehearsal: baseline V25, eight migrations V26–V33, successful
  validate, second migrate zero changes. Every original public application table retained
  its row count. RLS and browser-role denial checks passed. The actual backend also started with
  Hibernate validate against that restored schema; health was UP and `/api/version`
  reported production mode, command receipts enabled, and schema readiness.

Disposable application integration database: localhost:25432. Restored-copy rehearsal:
localhost:25433. Browser fixture preview: localhost:3100 with an intentionally unreachable
backend target; unmocked writes cannot reach the farm database. Real API proxy verification
used only the disposable backend at localhost:18081.

## Existing Supabase target evidence

The user selected the existing Supabase database. A read-only audit found no Flyway
history, no daily/shared command receipt tables, missing forward safeguards, and
application tables with RLS disabled and SELECT privileges granted to browser roles.
The backend JDBC role has BYPASSRLS, as required by the reviewed access model.
The legacy V15 refresh-token/offline effects and later application fields were checked;
read-only preflight passed against both source and restored copy. Equivalent generated
unique constraints were recognized instead of blindly replaying legacy index scripts.
This is an audited V25 starting point, not a claim that each legacy script was executed.

A private backup and separate schema/ACL snapshot are outside the repository:
`~/.local/state/poultry-prophet/release-2026-11-22/` (directory 0700, files 0600).
Initial rehearsed backup SHA-256: `3773e7a3bd7a71b39d1e98d0dc743815c6b17b84c7b834e79cbf490e01aa880c`.
Fresh approved pre-rollout backup (`approved-pre-rollout.dump`) SHA-256:
`a0fa9d344f1b351c901e7255dfef683c7484603f9fbdedfc0e3b6681e4076176`.
The custom archive was successfully restored before migration rehearsal. It covers the
public application schema, not a full export of Supabase-managed authentication/storage.
The app uses Spring authentication and public application tables.

## Remaining release gates

1. **Complete:** explicit user approval, fresh backup, same-target check, read-only
   preflight, V25 baseline, eight V26–V33 migrations, checksum validation, and empty replay.
2. **Complete:** all 34 original table counts preserved; every application table has
   RLS and no browser-role SELECT privileges. Actual backend starts with Hibernate
   validate against Supabase; health is UP. Local backend and frontend proxy report
   command receipts enabled and schemaReady=true. Private local configuration enables
   receipts; Render manifest prepares the same gate for the matching release.
3. **After push/deploy:** verify the actual Render settings and `/api/version`, including
   the release commit and receipt capabilities, before releasing the frontend.
   Keep automatic baselining disabled. No push or deployment has been performed.
4. Check authenticated manager/handler smoke flows on the actual HTTPS deployment, including
   cookies/CORS, offline recovery, role restrictions, and one real supported phone/browser.
   Automated Chromium emulation does not establish physical-device or Safari compatibility.

Rollback: retain additive receipt evidence and the matching protected write path. Pause
writers before any database recovery. Restore into a separate target and reconcile writes
made after the backup before cutover. Never regrant anonymous application access or
restore over the live database as an ad-hoc rollback.

See [migration runbook](migrations/README.md) and [receipt contract](offline-command-receipts.md).

## Final browser/build verification

Fresh production build passed. Full mobile Chromium 360px suite: **167 passed,
5 desktop-only cases skipped**. Desktop authentication/report/sidebar run: **17 passed**;
all five mobile exclusions were exercised successfully there. The remaining 44
desktop exclusions in that selected run are explicitly mobile-only report cases,
covered by the full mobile run. The previously failing sign-in, notification,
archive deletion, interrupted recovery, and batch-picker cases all passed.

Fresh frontend/API verification: 93 passed, zero skipped; lint passed; full frontend
dependency audit returned zero known advisories. The backend was rerun with 243
passing tests and zero skips before the framework dependency investigation below.


## Live read-only smoke evidence

The signed-in Sunrise handler session loaded batch-0012 population (117/500), recorded
deaths (340), vaccination (4/7), product quantities (including 20 packs partial and
70 sachets), and saved report versions after rollout. Handler financial restrictions
were respected. Navigation Tasks → Home worked; both the task queue and dashboard
showed 12 open tasks, and batch cards displayed their distinct open/overdue counts.
No domain transactions were created by this smoke check. Local frontend/backend
remain available at localhost:3000 and localhost:8080.


## Additional backend dependency gate

A direct OSV batch check of all 82 Maven runtime dependencies found known advisories
in the original Boot 3.4.1 runtime, including Spring/Tomcat, Jackson, logging, and the
PostgreSQL driver. These were not covered by the frontend pnpm audit. Do not interpret
the clean frontend audit as backend dependency clearance. The supported Boot 4.0.10 upgrade was first verified in an isolated source copy,
then applied to the project. Both runs passed all 243 tests with zero failures/skips.
The final resolved runtime audit checks 104 dependencies and finds zero affected
packages. This is advisory coverage at audit time, not a guarantee of no vulnerabilities.

The upgrade uses Spring Framework 7.0.11, Tomcat 11.0.27, PostgreSQL JDBC 42.7.14,
and Flyway 11.14.1. Existing JSON command deserializers and stored report JSON retain
the Jackson 2 contract using Boot's documented compatibility module and explicit HTTP/
WebSocket mapper preferences. MockMvc test imports and starter dependencies follow the
new module layout. The obsolete Byte Buddy pin was removed so the BOM controls it.

The packaged application started with Hibernate validate against the restored PostgreSQL
17 farm schema. The upgraded Flyway independently validated the live V25–V33 history
with no pending migrations and no migrate/baseline/repair operation.

CI now audits resolved Maven runtime dependencies through OSV using
`scripts/audit-runtime-dependencies.py`. The gate was verified to fail on the old
82-dependency inventory and pass on the upgraded 104-dependency inventory. Incomplete
responses or network failures do not count as successful audits.

References: [official migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide),
[JSON compatibility configuration](https://docs.spring.io/spring-boot/4.0/reference/features/json.html),
[Spring release information](https://spring.io/projects/). Plan a supported-line update
before the 4.0 support window ends; dependency checks must remain part of release work.


## Final result after the dependency upgrade

The project backend was packaged successfully and restarted against the approved Supabase
target. Health is UP, and both the backend and frontend proxy report receipt support and
schema readiness. The final frontend-to-upgraded-backend regression run passed all
93 tests with zero skipped tests. Local application servers remain running on ports
3000/8080; disposable verification servers have been stopped after verification.

Frontend CI now exercises the production build/server and the mobile authentication/offline
recovery regressions. Backend CI runs the disposable integration suite and runtime dependency
audit. XML/YAML parsing and `git diff --check` passed. A credential-pattern check of
changed/untracked repository files found no matching private-key or common service-token
patterns; private environment files remain ignored. This is not an exhaustive secret scan.

Pre-push checks are complete. Actual hosted HTTPS configuration, release identity, and
post-deployment smoke checks remain required before opening the deployed system to users.
No commit, push, or deployment was performed by this work.
