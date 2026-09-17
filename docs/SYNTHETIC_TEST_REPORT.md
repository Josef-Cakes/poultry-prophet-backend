# Synthetic MVP test report

Status: automated implementation checks passed; manual stakeholder cases remain to be executed and signed off in the isolated validation environment.

Validation stack evidence: `GET /api/health` returned `{"status":"UP"}` and `GET /api/version` returned backend version `0.1.0-validation`, commit `validation-local`, build time `2026-09-13T18:46:29Z`, environment `validation`, and compatible frontend version `0.1.0-validation`. Docker Compose startup was successful with a dedicated PostgreSQL 16 validation volume and ports `18080`/`55432`.

## Automated checks

| Area | Check | Expected evidence |
|---|---|---|
| Build | `./mvnw test` | 18 tests passed; 0 failures/errors |
| Frontend | `npm run build && npm run lint && npx tsc --noEmit` | Build, lint, and type-check passed; 13 routes compiled |
| Ledger | Unit/integration tests for each population event type | Signed delta is stored; current population cannot go below zero or above initial |
| Health accounting | Health death vs accidental/predation/missing | Only `HEALTH_DEATH` contributes to health-death analytics |
| Idempotency | Same UUID replay and concurrent replay | One persisted event and one population deduction |
| Backdating | Insert earlier observation after later observations | Later rows are recomputed in `recordDate` order |
| Insufficient data | First observation and zero-feed observation | BHI/WFR are null with an explicit warning; no fabricated 100 |
| Explainability | Latest indicator response | Raw values, units, range, status, formula version, factors, contributions, quality, computedAt, and observation date present |
| WebSocket | Missing/invalid JWT, wrong farm topic/destination, valid own farm | Unauthorized and cross-farm frames rejected |

## Seeded validation evidence

The idempotent seed completed successfully against the isolated Compose stack. The ten named scenario batches were created under the validation manager account. Read-only database checks confirmed:

- typed event effects: health death `-2`, accidental death `-1`, suspected predation `-2`, missing `-2`, found returned `+1`, transfer out `-1`, and sale `-1`;
- current populations: scenarios 03–07 ended at 98, 99, 98, 99, and 98 respectively; scenario 10 ended at 99 after two submissions of the same operation ID;
- scenario 02 and scenario 09 have `bhi = null` with an explicit missing-data warning; scenario 09 also has `wfr = null`;
- scenario 08 has three indicators ordered by observation date and the later rows contain rule-based formula version `RULE_BASED_BHI_BSI_WFR_V2`.

These are automated seed/database checks. The browser, keyboard, focus, and stakeholder wording cases below still require named manual testers.

## Manual scenario acceptance

| Scenario | Result to record |
|---|---|
| Dashboard → Log now → Logging screen → Back to dashboard | One click; destination label matches destination |
| Batch overview → Log an event → Logging screen → Back to batch overview | One click; query survives refresh |
| Health death | Batch population decreases and BHI mortality input changes |
| Accidental/predation/missing/sale/transfer | Population changes, but health-death input does not |
| Found returned | Population increases without exceeding initial population |
| Invalid count correction | Rejected with no saved event |
| Repeated save / network retry | No duplicate event or population change |
| Browser Back | Browser history behavior remains normal |
| Keyboard and focus | Buttons/links reachable, visible focus, dialog returns focus to trigger |
| Unfinished CRS/selection/report/offline claims | Hidden from primary MVP flow or visibly marked not included |

## Exit criteria

Stakeholder access is approved only when all ten seeded scenarios have a named tester, evidence link, pass/fail result, and no open severity-1 or severity-2 defect affecting population, health-death classification, authorization, or navigation.
