# Known MVP limitations

Poultry Prophet is a rule-based batch-monitoring and decision-support prototype. It records farm observations, compares them with configured ranges, calculates provisional indicators, and flags conditions for manager review. It does not diagnose disease or predict future biological or fighting performance.

The controlled formative MVP includes:

- daily observation entry for temperature, feed, water, notes, and health-death totals;
- an append-only typed population/loss ledger with operation-id idempotency;
- configurable BHI, BSI, and WFR ranges;
- explainable indicator inputs, component scores, weighted contributions, quality, formula version, and missing-data warnings;
- handler entry and manager review/acknowledgement workflows;
- deterministic context-aware return navigation;
- isolated validation environment and version compatibility visibility.

The following are deliberately not stakeholder decision features in this MVP:

- ML, predictive analytics, fighting-performance prediction, or biological forecasting;
- disease diagnosis or treatment recommendations;
- individual bird CRS, ranking, advancement, or selection decisions;
- offline/PWA claims or offline write synchronization as a validated capability;
- report-generation UI and advanced charting.

The legacy CRS/selection and report code remains in the repository for later work, but primary navigation hides it or marks it as not included. Existing production data is not automatically converted by the validation changes; `APP_ALLOW_LEGACY_MORTALITY_RECONCILIATION` is fail-closed by default. Any legacy mortality repair requires a separately reviewed backup-and-rollback procedure.
