# Selection Review Report — Implementation Status

**Status:** Code implementation complete for the MVP report workflow and non-closing report snapshots. Stakeholder sign-off, deployment, and capstone-document updates remain.

## Implemented

- Added the farm-scoped `selectionreview` backend module.
- Added deterministic, period-based aggregation from:
  - batch population events;
  - health-related events and observations;
  - product-use records;
  - linked incubation cycles; and
  - posted batch-linked finance entries.
- Separated health-related deaths from accidental death, predation, missing, returned, transfers, sales, culling, corrections, and legacy ambiguous mortality.
- Added explicit data availability and limitation messages. Missing records are not converted to zero or a score.
- Added descriptive calculations only: current/initial population, health-related loss percentage, hatch rate where available, recorded expense, recorded income, and recorded net cash flow.
- Added draft/finalized report snapshots with manager status, notes, reviewer, timestamp, version number, purpose, source cutoff, and immutable payload captured at generation.
- Added idempotent snapshot creation and newer-source-data detection for later or backdated records.
- Added manager-only PDF export for saved snapshots and manager-reviewed reports.
- Added handler-safe preview and saved-report responses with financial data excluded.
- Moved legacy CRS selection, BHI/BSI/WFR reports, and threshold endpoints under `/api/legacy/**`.
- Stopped the legacy indicator worker from creating score-based alerts and filtered old score alerts from active dashboard feeds.
- Replaced the active batch health-indicator cards with a factual Selection Review Summary.
- Replaced the active CRS ranking screen with the live-report, saved-snapshot, optional manager-review, history, and PDF workflow. Generating a report never closes the batch or stops handler records.
- Replaced active threshold editing with an archived legacy explanation.
- Rewrote field-entry guidance so handlers see the report sections their records support rather than unsupported score claims.

## Active API

- `GET /api/batches/{batchId}/selection-review/preview`
- `POST /api/batches/{batchId}/selection-reviews`
- `GET /api/batches/{batchId}/selection-reviews`
- `GET /api/batches/{batchId}/selection-reviews/{reviewId}`
- `POST /api/batches/{batchId}/selection-reviews/{reviewId}/finalize`
- `GET /api/batches/{batchId}/selection-reviews/{reviewId}/pdf`

All endpoints use the authenticated farm scope. Managers can generate, finalize, and export. Handlers can view non-financial report data but cannot finalize or export.

## Verification completed

- Backend Maven tests: passed.
- Added `SelectionReviewServiceTest` for population-cause separation, handler finance isolation, non-closing snapshot creation, and idempotent retry behavior.
- Frontend ESLint: passed.
- Frontend production build and TypeScript checks: passed.

## Required before claiming the project is fully validated

1. Obtain adviser approval for the revised product claim, SMART objective, report name, and replacement of unsupported indicators.
2. Update the SRS, SDD, proposal/presentation, validation framework, and questionnaire to remove active BHI/BSI/WFR/CRS/threshold claims or mark them as legacy history.
3. Deploy the matching frontend/backend release and verify the release/version banner.
4. Run the ten synthetic validation scenarios in the isolated validation environment.
5. Complete the manager and handler dry run on a phone and desktop.
6. Conduct stakeholder testing and interview follow-up; collect the Google Form, response sheet, highlights PDF, and evidence folder.

The implementation does not make a disease diagnosis, performance prediction, individual-bird ranking, or automatic selection decision. The manager remains responsible for in-person selection.
