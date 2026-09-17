# MVP validation implementation plan

Status: local/staging implementation only. No production database or deployment is in scope.

## Baseline

- Frontend worktree: dirty with the earlier context-aware logging, daily-vitals, shell, and API changes. Baseline build, lint, and TypeScript checks pass.
- Backend worktree: dirty with the earlier mortality-accounting, date-validation, alert, authorization, and sync changes. Baseline `./mvnw test` passes 10 tests.
- Existing production configuration is not a validation fixture. No production rows will be written and no migration will be run against it.

## Implementation slices

1. Add a typed, append-only population ledger with operation IDs, explicit population effects, actor/timestamp/reason metadata, and safe idempotent retries. Preserve legacy `MORTALITY` rows for owner-reviewed reconciliation; do not auto-convert them.
2. Fix analytics association and observation-date ordering. Recompute the affected observation and dependent later observations, expose an explainable factor breakdown, and stop treating missing baselines as a perfect score.
3. Replace wildcard REST/WebSocket origins with a configured allowlist and keep farm-scoped STOMP authentication/authorization.
4. Add a read-only version endpoint and a visible validation/build banner. Hide selection and unfinished predictive/offline/report claims from the validation task flow.
5. Add an isolated PostgreSQL validation compose file, named environment templates, idempotent synthetic seed/reset tooling, deployment checklist, rollback notes, and the requested dry-run/test documents.

## Files likely to change

- Backend event entities/DTOs/services/repositories, analytics entities/worker/query DTOs, security/WebSocket configuration, version endpoint, validation scripts, migration SQL, tests, and documentation.
- Frontend DTO types/API hooks, metric cards/status presentation, app shell/version banner, batch navigation/scope labels, event forms, and documentation.

## Database/migration approach

- Create a reviewed, manual `V3__validation_ledger_and_explainable_indicators.sql` for validation databases only. It adds nullable compatibility columns, typed-event indexes/constraints, operation-id uniqueness, indicator explanation columns, and safe checks.
- The migration will not rewrite existing production `MORTALITY` rows or population counts. A read-only reconciliation report will identify legacy rows for later owner approval.
- Normal daily-record writes do not reconcile legacy mortality. The compatibility importer is disabled by default and requires the explicit `APP_ALLOW_LEGACY_MORTALITY_RECONCILIATION` opt-in during a reviewed repair.
- Validation reset/reseed will target only the compose/local validation database and will fail closed if its database name does not match the validation name.

## Risks and decisions deferred to the farm expert

- Whether accidental death, suspected predation, confirmed predation, missing, transfer, sale, and culling should affect population in the same way and whether any should influence a health indicator.
- Stage-specific temperature/WFR ranges, measurement method, and whether a numeric amount can truthfully be entered when water is unavailable.
- Meaning of quality/selection ratings. The individual CRS workflow is out of scope and will be hidden for this validation.

## Test strategy

- Add unit tests for ledger effects, negative-population prevention, operation-id retries, concurrent writes, threshold boundaries, missing baselines, factor output, and backdated recomputation.
- Add security tests for unauthenticated/cross-farm STOMP subscriptions and configured CORS origins.
- Use the isolated seed to execute the ten synthetic scenarios and write actual results to `docs/SYNTHETIC_TEST_REPORT.md`.
- Run backend tests, frontend build/lint/type checks, and a local version/banner smoke test after each major slice.

## Deferred or hidden functionality

- Machine learning, disease diagnosis, fighting-performance prediction, offline UI, advanced charts, report-generation UI, and individual CRS/selection are not part of this MVP validation. Existing routes are retained only where removing them would risk unrelated dirty work; they are marked out of scope or hidden from the validation task flow.
