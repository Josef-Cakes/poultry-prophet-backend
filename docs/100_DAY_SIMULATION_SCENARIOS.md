# 100-day validation scenarios

## Objective

This scenario produces a completed-looking batch history in minutes for UI, API, report, and stakeholder-demo testing. It is synthetic data and must not be presented as an actual farm outcome.

The simulator copies the source batch's initial population, bloodline, and farm scope, then creates a new batch named `[TEST COPY]`. It never edits the source batch.

## Profiles

`realistic` creates observations on days 1, 10, 20, 30, 45, 60, 75, 90, and 100. Use this for a believable demo where the handler does not submit a form every day.

`coverage` creates one observation for every simulated day. Use this to test charts, date filters, backdated calculations, and long lists.

`dense` is an explicit alias for the daily coverage pattern. It is useful when manually testing scrolling, pagination, and report rendering.

The full acceptance scenario is 100 days. Shorter runs are supported for quick dry runs and create only events whose scheduled date falls within the requested duration.

## Generated data

The normal API endpoints receive:

- health concern and health-related death events;
- accidental death, suspected/confirmed predation, missing, returned, transfer, sale, culling, and count-correction events;
- behavior observations with feed, water, and temperature marked `UNAVAILABLE` rather than inventing measurements;
- one feed input and one soluble medicine input;
- batch expense and income transactions;
- a manager task completed by the validation handler;
- Selection Review snapshots at day 30, day 60, and day 100;
- a PDF of the final Selection Review report.

For the 100-day run, the expected population calculation is:

```text
initial population
- health-related deaths 2
- accidental death 1
- suspected predation 1
- confirmed predation 1
- missing 2
+ returned 1
- transfer out 3
+ transfer in 2
- sales 4
- culling 1
+ count correction 2
= initial population - 10
```

The generated run also produces PHP 2,500 feed expense, PHP 450 medicine expense, and PHP 1,000 synthetic sale income. These numbers are test fixtures, not recommendations or farm estimates.

## Safety and repeatability

Required flags:

```bash
--dry-run
```

or:

```bash
--apply --confirm SYNTHETIC-ONLY
```

The script requires a local API, a validation environment version, a manager account, a handler account, a non-archived source batch, and at least 20 initial birds. The run identifier is deterministic from start date, seed, and profile. Repeating the same run refuses to create a duplicate batch.

## Artifacts

Each run writes to `validation-artifacts/<run-id>/`:

- `simulation-plan.json` — intended dates and expected totals;
- `run-manifest.json` — dry-run or applied batch identity;
- `actual-results.json` — API-observed totals and check results;
- `verification-report.md` — PASS/FAIL summary;
- `selection-review-final.pdf` — report generated through the normal report endpoint.

The directory is ignored by Git. Keep it for the consultation evidence if needed, but do not commit personal data or credentials.
