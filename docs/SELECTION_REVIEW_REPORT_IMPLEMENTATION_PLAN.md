# Poultry Prophet Selection Review Report Implementation Plan

**Document status:** Implemented in code; adviser approval and research-document alignment remain required  
**Change type:** Requirements refinement within the approved gamefowl farm-management domain  
**Primary replacement:** BHI, BSI, WFR, CRS ranking, and unsupported numerical grades → traceable Batch Selection Review Report  
**Prepared for:** Capstone adviser, research consultant, development team, and stakeholder representatives

## 1. Executive decision

Poultry Prophet has not abandoned its original topic. The system still serves gamefowl farm owners, managers, and handlers through incubation records, batch monitoring, health-event history, population accounting, medicine and product-use records, tasks, and batch-linked financial records.

The required change is to the **decision-support method**, not the research domain:

- The current method presents BHI, BSI, WFR, thresholds, CRS ranking, and numerical recommendations.
- Stakeholder feedback shows that several required measurements are not routinely or consistently collected.
- No veterinarian or poultry expert has validated the formulas, weights, thresholds, or relationship between the scores and actual farm outcomes.
- Stakeholders do not understand what the current 0–100 values mean.
- The revised method will process recorded farm data into a transparent, batch-level Selection Review Report for human interpretation.

This is an evidence-based requirements refinement. However, it is a **material research change**, not merely a label or UI change. The adviser should approve the revised objectives, SRS/SDD, evaluation framework, and validation questionnaire before implementation is treated as final.

## 2. Scope statement

### 2.1 Scope retained

The implementation remains limited to:

- gamefowl farm operations;
- farm owners/managers and handlers;
- incubation and hatch records;
- batch lifecycle and population monitoring;
- health, symptom, medicine, vaccination, and product-use history;
- batch-specific expense and income records;
- descriptive, traceable summaries; and
- manager-led batch review that can support later in-person selection.

### 2.2 Scope explicitly excluded

The revised system will not:

- diagnose disease;
- prescribe treatment;
- predict fighting performance, health outcomes, or mortality;
- automatically select or reject an individual bird;
- claim that a product or bloodline caused a result;
- present unvalidated BHI, BSI, WFR, CRS, readiness grades, rankings, or cut-lines as decision evidence;
- treat missing records as evidence that a batch was healthy;
- become a general accounting, payroll, inventory, e-commerce, attendance, or project-management system.

## 3. Current-state assessment

| Current component | Current problem | Planned disposition |
|---|---|---|
| BHI card | Combines measurements that are not consistently collected and uses unvalidated weights | Remove from active batch UI; retain historical values as legacy data only |
| BSI card | Attempts to quantify stress without a validated observation instrument or expert-reviewed formula | Remove from active UI and active decision logic |
| WFR card | Requires feed and water quantities that the stakeholder does not measure reliably | Remove from active UI; retain raw feed/water entries only when genuinely recorded |
| Threshold settings | A configurable threshold has no defensible basis if its metric is unvalidated | Remove BHI/BSI/WFR threshold controls from the active workflow |
| Indicator alerts | May imply a health judgment from unsupported scores | Replace with factual event notices and review reminders |
| CRS selection view | Applies numerical recommendations and ranking without validated individual-level data | Hide and deprecate; do not use for stakeholder decisions |
| Existing report | Aggregates average BHI/WFR and readiness score | Replace with a versioned, factual Selection Review Report |
| Data-entry explanation | Tells users that entries feed BHI/BSI/WFR | Rewrite to explain which report section each record supports |
| Validation questionnaire | Several questions validate BHI/BSI/WFR formulas and thresholds | Version and revise before the next respondent group |

The screenshot's **Health Indicators** section should be replaced during implementation, not merely renamed. Renaming an unsupported score would preserve the underlying validity problem.

## 4. Proposed product claim

> Poultry Prophet is a gamefowl farm operations and batch-monitoring system that organizes incubation, population, health-event, product-use, task, and financial records into a traceable Batch Selection Review Report. The report supports the manager's review, while the final selection decision remains based on the manager's in-person assessment and judgment.

## 5. Proposed revised SMART objective

The final wording must be approved by the adviser. Recommended draft:

> By the end of the implementation and validation period, Poultry Prophet shall generate a batch-level Selection Review Report from recorded population events, health observations, medicine/product-use records, incubation outcomes, and batch-linked financial transactions. The report shall show source dates, category breakdowns, data-availability warnings, and manager review notes without generating a disease diagnosis, performance prediction, automatic selection decision, or unvalidated numerical grade.

Suggested measurable criteria:

- 100% of displayed report figures have an identifiable source category and reporting period.
- Health-related deaths are never combined with predation, missing birds, sales, transfers, culling, or corrections.
- Missing or unavailable measurements are displayed as unavailable, not zero and not a perfect score.
- A manager can generate and review a report for one batch in three or fewer primary interactions from the batch page.
- A manager can record a human review status, notes, reviewer, and timestamp.
- Cross-farm access tests return no report or source data.

## 6. Input–process–output model

This model addresses the consultant's requirement that the system must do more than accept input and show charts.

| Input | Processing | Output |
|---|---|---|
| Batch start/hatch date and population ledger | Derive current age/stage; replay signed population changes; separate causes | Current population, `alive / initial`, stage, days old, and population-change breakdown |
| Health-related death events | Filter only health-death categories; aggregate counts by date and period | Recorded health-related deaths and descriptive rate with source events |
| Predation, missing, accident, culling, sale, transfer, return, and correction events | Group each category separately; do not call all reductions mortality | Population-change table by cause and unresolved missing count |
| Health concerns and observable symptoms | Group by date, title/tag, affected count, and recurrence | Health-event timeline and repeated-event summary without diagnosis |
| Medicine, vitamin, vaccine, feed, and other product-use records | Group by product type, brand, purpose, date, and batch | Product-use and intervention history; no causal claim |
| Incubation cycle linked to the batch | Reconcile loaded, hatched, unhatched, and removed counts | Hatch summary and hatch rate, when applicable |
| Batch-specific financial transactions | Sum posted income and expense by category and period | Recorded expenses, recorded income, and recorded net cash flow—not accounting profit |
| Manager's observation date and notes | Freeze the reporting period; save reviewer, status, notes, and payload version | Reproducible Selection Review Report and optional PDF |
| Missing or incomplete source records | Evaluate availability per section without imputing values | Clear “No records,” “Partial records,” or “Available” labels and limitations |

## 7. Target Selection Review Report

### 7.1 Report identity

Each report should include:

- farm and batch identifiers;
- batch name and descriptive bloodline/source metadata;
- report period and “as of” date;
- batch age and automatically derived lifecycle stage;
- generated by and generated timestamp;
- reviewed by and reviewed timestamp, when finalized;
- report schema/formula version; and
- a visible statement that it is descriptive decision support, not a diagnosis or prediction.

### 7.2 Report sections

1. **Batch overview**
   - Initial population.
   - Current population displayed as `current / initial`.
   - Start/hatch date, age, and stage.
   - Last recorded farm event.

2. **Population-change summary**
   - Health-related deaths.
   - Accidental deaths.
   - Suspected and confirmed predation.
   - Missing and returned birds.
   - Transfers, sales, culling, and count corrections.
   - Source-event count and link to the event timeline.

3. **Health-event history**
   - Observed symptoms or concerns.
   - Affected count where recorded.
   - Event dates and recorder.
   - Repeated descriptions/tags presented as factual recurrence, not a diagnosis.

4. **Medicine and farm-input history**
   - Product type, brand/product, purpose, date, and recorded quantity/unit if available.
   - “Quantity not measured” when the farm only records the pack, sachet, or product name.
   - No effectiveness or brand-quality ranking.

5. **Incubation and hatch context**
   - Included only when the batch originated from an incubation cycle.
   - Eggs loaded, hatch date, hatched, unhatched, removed/damaged, hatch rate, and cycle reference.

6. **Recorded financial summary**
   - Manager-only by default.
   - Posted expenses and income by category.
   - Recorded net cash flow = recorded income − recorded expenses.
   - Display “Incomplete financial records” when the manager has not confirmed that all relevant transactions were entered.
   - Never label the result “profit” unless the research scope later adds complete cost accounting.

7. **Data-availability statement**
   - Source record counts by section.
   - Date of latest record.
   - Missing sections and known limitations.
   - Required warning: “No recorded event does not prove that no event occurred.”

8. **Manager review**
   - Review status: `NOT_REVIEWED`, `FOR_IN_PERSON_ASSESSMENT`, `CONTINUE_OBSERVATION`, or `REVIEW_COMPLETED`.
   - Manager notes and rationale.
   - Optional next review date.
   - No system-generated “good batch,” “bad batch,” “advance,” or “reject” recommendation.

### 7.3 Descriptive calculations

| Result | Calculation/rule | Required wording or limitation |
|---|---|---|
| Current population | Initial population + signed ledger deltas | Must reconcile to the population ledger |
| Health-related loss percentage | Recorded health-related deaths ÷ initial population × 100 | Descriptive batch-to-date figure; keep other reductions separate |
| Hatch rate | Hatched ÷ eggs loaded × 100 | Do not call non-hatch infertility without fertility/candling data |
| Recorded expense | Sum of posted batch expenses in the period | May be incomplete |
| Recorded income | Sum of posted batch income in the period | May be incomplete |
| Recorded net cash flow | Recorded income − recorded expense | Not accounting profit |
| Event recurrence | Count of matching event category/tag in the report period | Not proof of disease or causation |

There will be no overall grade, 0–100 score, weighted composite, hidden cut-line, or predictive label in version 1 of the report.

## 8. Roles and permissions

### Handler

- Record incubation loads, population events, health observations, medicine/product use, and assigned task completion.
- View non-financial batch history and report sections needed for field work.
- Cannot finalize a Selection Review Report or view manager-only financial data.

### Manager/owner

- View all batches and source records within the same farm.
- Generate, review, finalize, and export the Selection Review Report.
- Enter review status, rationale, and next review date.
- View batch-linked financial sections.
- Cannot access another farm's records.

## 9. Proposed technical design

### 9.1 Backend services

Create a dedicated `selectionreview` or `batchreview` module rather than modifying the old indicator formulas in place.

Recommended responsibilities:

- `SelectionReviewQueryService`: loads farm-scoped source records for a batch and period.
- `SelectionReviewAggregationService`: performs deterministic grouping and calculations.
- `SelectionReviewService`: generates drafts, finalizes reviews, and enforces permissions.
- `SelectionReviewExportService`: renders a finalized snapshot to PDF.
- `SelectionReviewController`: exposes manager and read-only handler endpoints.

The aggregation service must not depend on `AnalyticsService`, `IndicatorJobWorker`, BHI/BSI/WFR thresholds, `BirdScore`, or CRS cut-lines.

### 9.2 Proposed endpoints

- `GET /api/batches/{batchId}/selection-review/preview?periodStart=&periodEnd=`
- `POST /api/batches/{batchId}/selection-reviews`
- `GET /api/batches/{batchId}/selection-reviews`
- `GET /api/batches/{batchId}/selection-reviews/{reviewId}`
- `POST /api/batches/{batchId}/selection-reviews/{reviewId}/finalize`
- `GET /api/batches/{batchId}/selection-reviews/{reviewId}/pdf`

All endpoints must derive `farmId` from the authenticated principal and verify that the batch belongs to that farm.

### 9.3 Persistence

Recommended new entity: `batch_selection_review`.

Minimum fields:

- `id`, `farm_id`, and `batch_id`;
- `period_start`, `period_end`, and `as_of_date`;
- `status` (`DRAFT` or `FINALIZED`);
- `review_status`;
- `manager_notes` and `next_review_date`;
- `payload_version`;
- immutable JSON report snapshot or equivalent versioned payload;
- `generated_by`, `generated_at`, `reviewed_by`, and `reviewed_at`.

The finalized payload must be a snapshot so a later correction does not silently rewrite what the manager reviewed. A newly generated report may reflect corrected records while the prior finalized report remains auditable.

### 9.4 Data access additions

Add farm- and batch-scoped repository queries for:

- population events by event date and type;
- health observations and medicine events;
- product-use records by batch and date;
- linked incubation cycle by resulting batch ID;
- posted financial transactions by batch and period; and
- report history by batch.

### 9.5 Legacy data treatment

- Do not delete indicator, threshold, report, or score tables in the first release.
- Mark BHI/BSI/WFR/CRS outputs as legacy and remove them from active navigation and decision workflows.
- Preserve historical records for audit and research comparison.
- Do not recompute old scores using a new meaning.
- Do not migrate a legacy numerical score into a new review status.
- Remove the legacy tables only after adviser approval, backup, and a separate migration review.

## 10. Frontend implementation plan

### 10.1 Batch overview

Replace the screenshot's **Health Indicators** block with a mobile-first **Selection Review Summary** containing factual cards:

- population: `current / initial`;
- health-related deaths;
- predation/other reductions;
- recorded health events;
- medicine/product-use count;
- recorded expenses and income for managers; and
- data-availability warning.

Each summary card should open the filtered source timeline. Cards must not display a grade or color that implies healthy/unhealthy unless the color represents a factual state such as missing data or unresolved missing birds.

### 10.2 Selection review page

Replace the current ranked CRS table with:

- report period controls;
- report preview;
- source-record drill-down;
- data limitations;
- manager notes and review status;
- finalize action with confirmation; and
- PDF export for finalized reports.

The existing individual-bird ranking route should remain hidden during transition. If individual bird decisions are later required, they must be recorded as manager decisions without system ranking unless a separate validated model is approved.

### 10.3 Settings and data entry

- Remove BHI/BSI/WFR threshold configuration from the active settings menu.
- Rewrite “What these records affect” text so it refers to report sections, population accounting, and timelines.
- Keep numeric feed, water, and temperature fields optional where they remain useful as raw observations.
- Never require water volume, feed remainder, or temperature merely to generate the report.
- Preserve measurement-quality labels (`MEASURED`, `ESTIMATED`, `UNAVAILABLE`) for any numeric observation retained.

### 10.4 Mobile and accessibility requirements

- Design for a handler using a phone outdoors.
- Minimum 44-pixel touch targets; preferred primary actions are 48 pixels high.
- One primary action per screen section.
- Plain-language labels and no unexplained acronyms.
- No horizontal table scrolling for the main report; use stacked sections/cards on phones.
- Visible loading, empty, error, and partial-data states.
- Screen-reader labels and keyboard focus states for report actions.

## 11. Existing components affected

### Backend

- `analytics/AnalyticsService`, `IndicatorJobWorker`, indicator DTOs, and threshold services: remove from the active selection path; preserve as legacy initially.
- `selection/SelectionService` and CRS-related entities: hide/deprecate the ranked recommendation workflow.
- `report/ReportService` and report DTOs: replace BHI/WFR/readiness payloads with versioned review snapshots.
- `dashboard/OverviewService`: provide factual summary data or a review-preview link.
- Event, input, incubation, and finance repositories: add batch-period aggregation queries.
- Alert processing: replace score alerts with factual event notices and review reminders.
- Database migrations and tests: add the review snapshot model without destructive legacy-table removal.

### Frontend

- `app/(app)/batches/[batchId]/page.tsx`: remove the three indicator cards and explanation block.
- `app/(app)/batches/[batchId]/selection/page.tsx`: replace CRS ranking with the review workflow.
- `app/(app)/batches/[batchId]/data-entry/page.tsx`: remove BHI/BSI/WFR claims.
- `components/settings/thresholds-section.tsx`: remove from active settings or clearly archive it.
- `lib/types.ts`, `lib/api.ts`, query keys, and hooks: add selection-review contracts.
- Report/download UI: connect to finalized snapshot and PDF endpoints.

### Research and project documents

- Project proposal and approved-title explanation.
- Revised SMART objectives.
- SRS functional and non-functional requirements.
- SDD architecture, data model, API, and IPO process.
- Validation framework and objective-question mapping.
- Google Form/question bank.
- User guide and presentation claims.

## 12. Research instrument revision

The current questionnaire includes direct validation of BHI, BSI, WFR, thresholds, and formula weights. Those questions will no longer align with the revised MVP.

Required actions:

1. Preserve the existing questionnaire and responses as version 1; do not silently edit completed responses.
2. Create version 2 for respondents who test the Selection Review Report.
3. Replace score/threshold questions with questions about:
   - correctness of population categories;
   - usefulness of the health and intervention timeline;
   - traceability of report figures;
   - clarity of missing-data warnings;
   - usefulness for batch review and in-person selection preparation;
   - understandability of recorded expenses, income, and net cash flow;
   - whether any displayed conclusion exceeds the available evidence; and
   - what additional source record the manager needs.
4. Keep role-specific branching for handlers, managers, experts, and technical evaluators.
5. Update the validation framework mapping before collecting the next set of responses.

## 13. Implementation phases and estimated timeline

Assumption: a small student team with one backend and one frontend contributor working partly in parallel.

| Phase | Work | Estimate | Exit criterion |
|---|---|---:|---|
| 0. Adviser decision gate | Approve revised claim, objective, report sections, terminology, and legacy-score treatment | 1–2 days | Signed/recorded adviser approval |
| 1. Contract and UX design | Final DTO, report schema, mobile wireframe, permissions, and questionnaire v2 | 2 days | Reviewed API/report contract |
| 2. Backend aggregation | Repository queries, deterministic aggregator, preview endpoint, completeness states, security tests | 3–4 days | Preview API passes unit/integration tests |
| 3. Review persistence | Draft/finalized snapshot, manager notes/status, audit metadata, migration | 2–3 days | Finalized report remains reproducible |
| 4. Frontend replacement | Remove active score cards; build summary, report view, source drill-down, and manager workflow | 3–4 days | Mobile and desktop acceptance checks pass |
| 5. PDF and legacy deprecation | PDF export, hide CRS/threshold routes, legacy labels/feature flag | 2 days | No unsupported score appears in active flow |
| 6. Verification and validation | Synthetic scenarios, internal dry run, stakeholder retest, documentation update | 2–3 days | Acceptance criteria and validation evidence complete |

Expected total: **12–18 working days**, depending on adviser turnaround and PDF/report complexity.

## 14. Test plan

### Backend tests

- Current population reconciles with all signed event categories.
- Health deaths exclude predation, missing, transfer, sale, culling, and correction.
- Returned birds and corrections are represented correctly.
- Report period boundaries use event/transaction dates consistently.
- Hatch rate is unavailable or safe when eggs loaded is zero.
- Voided finance entries are excluded.
- Net cash flow uses posted income minus posted expense.
- Missing source data remains unavailable and is never converted to zero.
- Finalized snapshots do not change after source records are corrected.
- Cross-farm preview, finalization, and PDF access are rejected.
- Handlers cannot finalize reports or view restricted financial sections.

### Frontend tests

- BHI, BSI, WFR, CRS, cut-line, ranking, and recommendation labels do not appear in active review screens.
- Every summary value links to or identifies its source section.
- Phone layouts require no horizontal scrolling for the main report.
- Empty and partial sections explain what is missing.
- Manager-only financial data is not rendered for handlers.
- Finalization requires confirmation and preserves manager notes.
- PDF/download failures show a recoverable error.

### Validation scenarios

1. Batch with no recorded events.
2. Batch with health-related death only.
3. Batch with predation but no health death.
4. Missing bird later returned.
5. Medicine recorded without measured quantity.
6. Shared feed pack recorded without consumption amount.
7. Complete incubation-to-batch history.
8. Partial financial records and no income.
9. Backdated event within the report period.
10. Cross-farm access attempt.

## 15. Acceptance criteria

Implementation is complete only when:

- active batch and selection screens no longer rely on BHI, BSI, WFR, CRS, or numerical grades;
- report figures are generated from recorded source data using documented rules;
- population changes are separated by cause;
- missing data is explicit and never silently imputed;
- the manager, not the system, records the review conclusion;
- finalized reports are immutable, auditable, and exportable;
- handlers have a usable phone workflow and cannot see restricted finance;
- farm isolation and role tests pass;
- the SRS, SDD, objectives, presentation, and questionnaire match the implemented behavior; and
- adviser and stakeholder review confirm that the revised report supports the real farm workflow.

## 16. Risks and controls

| Risk | Control |
|---|---|
| Adviser interprets the change as abandoning an objective | Submit the change matrix and unchanged-domain explanation before coding |
| Old and new questionnaire responses become incomparable | Version instruments and analyze them separately |
| A descriptive rate is interpreted as a grade | Use factual labels, source links, and limitations; no traffic-light “good/bad” status |
| No-event days are interpreted as healthy days | Display the event-based-record limitation prominently |
| Finance is interpreted as accounting profit | Use “recorded net cash flow” and incomplete-data warnings |
| Legacy scores continue to influence users | Hide routes/settings, disable active alerts, and mark stored values legacy |
| Scope expands into individual prediction | Keep the report batch-level and human-reviewed |
| Historical records are lost | Use additive migration and retain legacy tables read-only |

## 17. Decisions required before implementation

The adviser and stakeholder should approve:

1. The revised SMART objective and product claim.
2. The name **Batch Selection Review Report** or an approved alternative.
3. The report period rule: batch-to-date, selected observation period, or both.
4. The manager review-status choices and whether a next-review date is required.
5. Whether handlers may view the non-financial report preview.
6. Whether finance is included in the report or shown as a linked manager-only section.
7. Whether the old CRS/individual-selection route is hidden immediately or retained only in an archived demonstration build.
8. The version-2 validation questionnaire and respondent groups.

No implementation should begin until items 1–4 are approved. Items 5–8 may be finalized during phase 1 but must be settled before stakeholder testing.

## 18. Recommended adviser explanation

> We retained the original gamefowl farm-management domain, users, batch records, health-event monitoring, and selection-support purpose. Stakeholder validation showed that the original BHI, BSI, WFR, and numerical grade depended on measurements that the target farm does not consistently collect and on formulas that have not been expert-validated. We therefore propose replacing the unsupported score with a transparent Batch Selection Review Report. The system will process recorded population, health-event, intervention, incubation, and financial data into a traceable summary, while the manager remains responsible for interpreting the report and making the in-person selection decision.
