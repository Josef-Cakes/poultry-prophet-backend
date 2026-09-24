# Stakeholder dry run

## Roles

- Handler: records one daily observation and one event, then returns to the originating task.
- Manager: reviews the batch overview, thresholds, active alerts, and event history; acknowledges an alert.
- Observer: records timestamps, labels, clicks, focus behavior, and any terminology confusion without editing data.

## Handler script

1. Sign in with the validation handler account.
2. From Dashboard choose `Log now`.
3. Enter a daily reading. Confirm the death count is read-only and comes from typed health-death events.
4. Save, confirm success, and enter a population-loss event using `Accidental death` or `Suspected predation`.
5. Choose `Back to dashboard` from the dashboard-origin flow. Confirm one click returns to Dashboard.
6. Open a batch overview, choose `Log an event`, save a typed event, and choose `Back to batch overview`. Confirm one click returns to that batch.
7. Refresh the logging page before returning. Confirm the destination remains the same.

Ask: “Can you tell which events affect the population, which affect the health indicator, and where this action will take you?”

## Manager script

1. Confirm the validation banner, version footer, and no version mismatch warning.
2. Open scenario 01 and explain BHI using the visible raw inputs, configured range, status, formula version, and factor contributions.
3. Open scenario 02 and confirm the UI says `Insufficient data`, not 100 or `Good`.
4. Compare scenarios 03–07. Confirm only health deaths are used for health analytics while all typed ledger effects are visible.
5. Review scenario 08 after the backdated insert and verify later observation dates reflect recomputation.
6. Review scenario 09 and confirm WFR is unavailable with a missing-data explanation.
7. Review scenario 10 and confirm a retry does not double-deduct the population.
8. Edit one BHI/BSI/WFR threshold, record the configured range shown on the overview, and acknowledge a resulting alert.

## Observer worksheet

Capture: scenario, role, start/end time, click count for return, destination label, observed population before/after, indicator status, alert status, version/commit, keyboard issues, focus issues, terminology issue, severity, notes.

Stop the session for any cross-farm data visibility, unauthorized WebSocket access, duplicate population deduction, health-death misclassification, or unsafe redirect.
