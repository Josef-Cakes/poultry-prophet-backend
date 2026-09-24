# Poultry Prophet Stakeholder-Feedback Implementation Guide

**Document status:** Proposed plan for adviser review  
**Implementation status:** Not started; this document does not authorize deployment or database migration  
**Source:** Formative interview with one gamefowl farm owner  
**System baseline:** Spring Boot backend, PostgreSQL, Next.js frontend, manager/handler roles, batch records, typed population events, explainable BHI/BSI/WFR indicators, alerts, and handler assignments

## 1. Executive recommendation

The farm owner's comments identify six operational needs:

1. record when eggs are loaded into an incubator;
2. record the number loaded, hatch date, hatched count, and non-hatched count;
3. record feed brands, vitamins, and medicines instead of requiring unreliable consumption estimates;
4. add finance tracking;
5. allow managers to assign tasks to handlers; and
6. add useful analytics.

The recommended solution is an **operational farm-management extension**, not a predictive or diagnostic system. Implementation should be divided into bounded modules: Incubation, Farm Inputs, Handler Tasks, Basic Finance, and Descriptive Analytics.

The numeric feed and water fields must not be deleted immediately. They are inputs to the existing provisional BHI and WFR calculations. Instead, numeric quantities should become optional and explicitly marked as measured, estimated, or unavailable. Product/brand records should be stored separately. A brand must never increase or decrease a health score merely because of its name; there is no validated evidence in the current research to support that inference.

Because the requirements came from one farm owner, they should be treated as high-value formative evidence rather than proof that all farms share the same workflow. The adviser should approve the scope, and at least one handler should confirm that the proposed entry screens are practical before implementation begins.

## 2. Scope recommendation

### Must have for the revised MVP

- Incubation-cycle records with dates and reconciled egg outcomes.
- Conversion of a completed hatch into a bird batch without duplicate creation.
- Feed/product, vitamin, medicine, and vaccine-use records.
- Optional measured feed/water amounts with honest data-quality labels.
- Manager-to-handler task assignment and completion tracking.
- Descriptive incubation and task analytics.
- Farm scoping, role permissions, audit information, validation, and tests.

### Should have after the core workflow is stable

- Basic expense and income transaction logging.
- Finance summaries by date, category, batch, and incubation cycle.
- Cost per recorded hatched chick and recorded net cash-flow summaries.
- CSV/PDF export through the existing reporting foundation.

### Could have in a later release

- Reusable product catalog and supplier list.
- Recurring task templates and reminders.
- Receipt/photo attachments.
- Incubator maintenance logs and candling observations.
- Cross-cycle comparisons with data-completeness thresholds.

### Explicitly out of scope

- Full accounting, tax, payroll, inventory valuation, or audited profit statements.
- Automatic diagnosis, treatment recommendations, or medicine dosage advice.
- Ranking feed or medicine brands by effectiveness.
- Machine-learning prediction, biological forecasting, or fighting-performance prediction.
- Claiming causation from product use and hatch/health outcomes.

## 3. Requirement-to-solution mapping

| Stakeholder comment | Interpreted requirement | Proposed system response | Priority |
|---|---|---|---|
| Add date when eggs are loaded into incubators | Track the beginning of each incubation cycle | Incubation Cycle record with incubator code, loaded date, expected hatch date, and status | Must |
| Add number of eggs loaded, when loaded, when hatched, and how many hatched/did not | Track reconciled hatch outcomes | Loaded, hatched, unhatched, and removed/damaged counts; actual hatch date; validation that totals reconcile | Must |
| Replace measured feed/water with brand of feed, vitamins, and medicine | Make data entry match actual farm practice | Product-use log plus qualitative feed/water status; numeric amounts remain optional when genuinely measured | Must |
| Add finance | Track operational cash entries without becoming accounting software | Manager-controlled income/expense ledger linked to farm, batch, or incubation cycle | Should |
| Add task on handler | Let managers organize farm work | Task assignment, due date, priority, status, completion note, and audit trail | Must |
| Add analytics | Summarize collected operational data | Descriptive hatch, task, finance, loss, and data-quality analytics; retain existing explainable health indicators | Must/Should |

## 4. Functional design

### 4.1 Incubation Cycle module

An incubation cycle exists before a bird batch. A batch should begin only after a hatch outcome is recorded.

#### Required fields

- Farm ID, inherited from the authenticated user.
- Incubator name or code.
- Cycle name/reference number, unique within the farm.
- Egg source or breeding group, optional descriptive text.
- Bloodline/line, optional descriptive metadata only.
- Date eggs were loaded.
- Number of eggs loaded.
- Expected hatch date, suggested from a configurable duration but editable.
- Status: `LOADED`, `INCUBATING`, `HATCHING`, `COMPLETED`, or `CANCELLED`.

#### Completion fields

- Actual hatch date.
- Hatched count.
- Unhatched count.
- Removed/damaged count, default zero.
- Notes.
- User who closed the cycle and closure timestamp.
- Created batch ID, nullable until conversion.

#### Business rules

- All counts must be zero or greater.
- `hatched + unhatched + removed/damaged` must equal `eggs loaded` when the cycle is completed.
- Actual hatch date cannot be earlier than the load date.
- Expected hatch date is a planning date, not a guaranteed prediction.
- Only a manager can complete/cancel a cycle or convert it to a batch.
- Conversion creates one batch with `initialPopulation = hatchedCount` and `startDate = actualHatchDate`.
- Conversion must be idempotent: the same cycle cannot create a second batch.
- Historical hatch outcomes are corrected through an auditable correction action, not silent overwriting.

#### First-version screens

- Manager: Incubation list with status, incubator, dates, eggs loaded, and expected hatch date.
- Manager: New cycle form.
- Manager/handler: Cycle detail and timeline.
- Manager: Complete hatch and create batch action.
- Dashboard: Due-to-hatch and overdue-for-review cards. “Overdue” means the expected date passed without a completed outcome; it does not mean the hatch failed.

### 4.2 Farm Inputs module

The interview suggests that product identity is easier to record than exact feed and water consumption. These are different data types and should not be merged into one free-text field.

#### Product-use record

- Farm ID and batch ID.
- Incubation cycle ID, optional where relevant.
- Record date/time.
- Product type: `FEED`, `VITAMIN`, `MEDICINE`, `VACCINE`, or `OTHER`.
- Brand name.
- Product name/variant, optional.
- Quantity and unit, optional.
- Route/method, optional for medicine/vaccine.
- Purpose/reason, free text.
- Notes.
- Recorded by and recorded timestamp.

#### Daily feed/water redesign

- Keep numeric feed and water amounts for farms that genuinely measure them.
- Make numeric amounts nullable and pair each with `MEASURED`, `ESTIMATED`, or `UNAVAILABLE`.
- Add practical observations such as feed status (`NORMAL`, `LOW`, `REFUSED`, `NOT_OBSERVED`) and water status (`AVAILABLE`, `LOW`, `EMPTY`, `NOT_OBSERVED`).
- Retain temperature, especially for incubation/brooding, with the same quality label.
- Do not use brand names as health-score inputs.
- Compute WFR only when both feed and water amounts are positively measured under an approved unit convention.
- If required BHI inputs are missing, display “insufficient data” rather than substituting a perfect or estimated score.
- Preserve historical V2 indicator results. Any approved formula change must use a new formula version and be documented in the framework/SDD.

### 4.3 Handler Task module

#### Task fields

- Farm ID.
- Related batch ID or incubation cycle ID, optional but at least one context recommended.
- Title and instructions.
- Assigned handler ID.
- Assigned manager ID.
- Priority: `LOW`, `NORMAL`, `HIGH`, or `URGENT`.
- Due date/time in the farm's configured time zone.
- Status: `TODO`, `IN_PROGRESS`, `COMPLETED`, `OVERDUE`, or `CANCELLED`.
- Completion note and completed timestamp.
- Created and updated timestamps.

#### Permissions and behavior

- Managers create, reassign, cancel, and review tasks.
- Handlers see only tasks assigned to them within their farm.
- Handlers can mark tasks in progress/completed and add a completion note.
- A handler may only be assigned a batch-specific task when assigned to that batch, unless the manager explicitly assigns a farm-wide task.
- Overdue status is derived from due date and completion state; it should not require manual editing.
- First release uses in-app task lists and badges. Push notifications, SMS, and recurring schedules are deferred.

#### First-version screens

- Manager: Task board with filters for handler, batch, due date, priority, and status.
- Handler: “My Tasks” mobile-first list and task detail.
- Dashboard: due today, overdue, and recently completed summaries.

### 4.4 Basic Finance module

The term “finance” is too broad for implementation without a boundary. The recommended first release is a transaction ledger, not an accounting system.

#### Transaction fields

- Farm ID.
- Batch ID and incubation cycle ID, both optional.
- Transaction date.
- Type: `EXPENSE` or `INCOME`.
- Category: feed, vitamin/medicine, eggs, utilities, labor, equipment, bird sale, other.
- Amount and currency; default currency proposed as PHP.
- Vendor/payee/source, optional.
- Description/reference.
- Status: `POSTED` or `VOIDED`.
- Entered by, entered timestamp, and void reason where applicable.

#### Rules

- Manager-only access for the first release unless the adviser approves handler-submitted expense drafts.
- Amount must be greater than zero.
- Posted transactions are not hard-deleted; corrections are made by voiding and entering a replacement.
- Sale events do not automatically create income records until the duplicate-handling and confirmation workflow is approved.
- The interface must use “recorded net cash flow,” not “profit,” because unrecorded costs, depreciation, and inventory valuation are outside scope.
- Financial amounts must not be visible to handlers by default.

### 4.5 Descriptive Analytics module

Analytics must answer operational questions using recorded facts. Every value should link back to its source records and show a data-completeness warning when appropriate.

#### Incubation analytics

- Hatch rate = `hatched count / eggs loaded × 100`.
- Non-hatch rate = `unhatched count / eggs loaded × 100`.
- Removed/damaged rate.
- Incubation duration = `actual hatch date − load date`.
- Trends by cycle, incubator, and date range.
- Do not label non-hatch rate as infertility unless candling/fertility data are collected.

#### Task analytics

- Assigned, completed, open, and overdue counts.
- Completion rate for tasks due in the selected period.
- Median/average completion time, with outliers visible.
- Breakdown by handler, batch, and priority for manager review—not employee performance scoring.

#### Finance analytics

- Recorded expenses and income by category, period, batch, and incubation cycle.
- Recorded net cash flow = income − expenses.
- Cost per recorded hatched chick = eligible recorded cycle expenses / hatched count.
- Show “incomplete financial data” when categories or periods lack entries.

#### Product-use analytics

- Frequency of recorded feed/vitamin/medicine use by product and batch.
- Product-use timeline beside farm events.
- No claim that a brand caused a better hatch rate, health score, or outcome without a separate controlled study.

#### Existing health analytics

- Keep BHI/BSI/WFR explainable and versioned.
- Keep health mortality separate from predation and other losses.
- WFR remains unavailable when measurements are missing or unsuitable.
- No predictive wording, diagnosis, or fighting-performance recommendations.

## 5. Proposed data model

| Entity/table | Purpose | Key relationships |
|---|---|---|
| `incubation_cycle` | Tracks incubator loading, dates, status, and reconciled hatch outcomes | Farm; optional resulting batch |
| `farm_input_log` | Records feed brands, vitamins, medicines, vaccines, quantities, and context | Farm; batch; optional incubation cycle; user |
| `handler_task` | Stores assignments, due dates, status, priority, and completion | Farm; manager; handler; optional batch/cycle |
| `task_status_history` | Audits status changes and actors | Handler task; user |
| `financial_transaction` | Stores basic income/expense entries and corrections | Farm; optional batch/cycle; user |
| `daily_record` changes | Makes feed/water values optional and adds practical status fields | Existing batch and handler relationships |

All new entities must include farm ownership or inherit it through a verified parent, timestamps, actor metadata, database constraints, and farm-scoped indexes.

## 6. Proposed API surface

### Incubation

- `POST /api/incubation-cycles`
- `GET /api/incubation-cycles`
- `GET /api/incubation-cycles/{id}`
- `PATCH /api/incubation-cycles/{id}`
- `POST /api/incubation-cycles/{id}/complete`
- `POST /api/incubation-cycles/{id}/create-batch`

### Farm inputs

- `POST /api/batches/{batchId}/inputs`
- `GET /api/batches/{batchId}/inputs`
- `GET /api/incubation-cycles/{cycleId}/inputs`

### Tasks

- `POST /api/tasks`
- `GET /api/tasks` for manager filters
- `GET /api/tasks/mine` for the authenticated handler
- `PATCH /api/tasks/{id}` for manager edits
- `POST /api/tasks/{id}/status` for controlled transitions

### Finance

- `POST /api/financial-transactions`
- `GET /api/financial-transactions`
- `POST /api/financial-transactions/{id}/void`
- `GET /api/finance/summary`

### Operational analytics

- `GET /api/analytics/incubation`
- `GET /api/analytics/tasks`
- `GET /api/analytics/finance`
- `GET /api/analytics/data-quality`

Every endpoint must derive farm scope from the authenticated user, not from a trusted client-supplied farm ID.

## 7. Frontend information architecture

### Manager navigation

- Dashboard
- Batches
- Incubation
- Tasks
- Finance
- Analytics
- Alerts
- Settings

### Handler navigation

- Dashboard
- My Tasks
- Assigned Batches
- Quick Log

### Interaction principles

- Mobile-first forms with short required sections.
- Progressive disclosure for optional quantity, dose, reference, and notes.
- Clear separation between observed data, estimated data, and unavailable data.
- Plain-language labels with technical terms shown only where useful.
- Confirmation before completing a hatch, creating a batch, voiding finance entries, or cancelling tasks.
- Empty states explain what data are needed before analytics can appear.

## 8. Migration and compatibility plan

1. Create additive, reviewed migrations rather than modifying production tables manually.
2. Suggested sequence:
   - V4: incubation cycles;
   - V5: farm input logs and daily-record quality/status compatibility;
   - V6: handler tasks and history;
   - V7: financial transactions;
   - V8: analytics indexes/views if needed.
3. Preserve existing daily feed/water values and V2 indicator rows.
4. Make legacy numeric columns nullable only after backend and frontend compatibility is deployed and tested.
5. Do not recompute historical indicator rows under a new formula without an approved migration policy.
6. Test migration against a copy of validation data; take a backup and document rollback before any shared deployment.
7. Add a visible build/formula version so adviser and stakeholders know which revision they tested.

## 9. Implementation sequence and estimated timeline

The estimate assumes a small student team, one backend and one frontend contributor working partly in parallel. A single developer should expect the longer end of the range.

| Phase | Work | Estimate | Exit condition |
|---|---|---:|---|
| 0. Adviser decisions | Approve scope, terms, roles, formulas, and validation changes | 2–3 days | Signed/recorded decisions and updated requirements |
| 1. Technical design | Final ERD, API contracts, migrations, UI wireframes, test fixtures | 3–4 days | Reviewed design package; no code ambiguity |
| 2. Incubation | Backend, forms, completion workflow, batch conversion | 5–7 days | Reconciled cycle creates exactly one correct batch |
| 3. Farm inputs | Product-use records and optional measurement redesign | 5–7 days | Product logging works; missing measurements do not fabricate analytics |
| 4. Handler tasks | Assignment, status transitions, manager/handler screens | 4–6 days | Role and overdue scenarios pass |
| 5. Basic finance | Transaction ledger, permissions, summaries | 5–7 days | Seeded totals and void corrections reconcile exactly |
| 6. Descriptive analytics | Hatch, task, finance, product-use, data-quality views | 5–7 days | All displayed values trace to source records |
| 7. Hardening/revalidation | Security, migration, UI, accessibility, synthetic and stakeholder tests | 5–7 days | Acceptance suite passes; owner and handler feedback documented |

**Realistic total:** approximately 5–7 weeks sequentially, or 4–5 weeks with disciplined parallel work. If the capstone schedule is shorter, release Incubation, Farm Inputs, Handler Tasks, and hatch/task analytics first; move Finance to the next approved iteration.

## 10. Test and acceptance plan

### Incubation acceptance scenarios

- Create a cycle with valid dates and counts.
- Reject negative counts and impossible dates.
- Reject a completed cycle whose outcome counts do not equal eggs loaded.
- Complete a cycle and create a batch with the correct hatch date/population.
- Retry conversion and confirm no duplicate batch is created.
- Confirm users from another farm cannot view or mutate the cycle.

### Farm-input acceptance scenarios

- Record feed, vitamin, medicine, vaccine, and other products.
- Record a product with and without optional quantity.
- Mark feed/water as measured, estimated, and unavailable.
- Confirm WFR appears only with valid measured data.
- Confirm product brand does not change BHI/BSI/WFR.

### Task acceptance scenarios

- Manager assigns a task to an eligible handler.
- Ineligible/cross-farm assignment is rejected.
- Handler sees only assigned tasks and changes only permitted statuses.
- Overdue status is derived correctly in Asia/Manila time.
- Completion history records actor and timestamp.

### Finance acceptance scenarios

- Totals equal a seeded set of expenses and income exactly.
- Voided entries leave an audit trail and are excluded from active totals.
- Cross-farm and handler access is rejected.
- Cost-per-hatched-chick handles zero hatch count without division by zero.
- Reports use “recorded net cash flow,” not “profit.”

### Analytics acceptance scenarios

- Hatch rates and task rates match hand calculations.
- Filters do not leak records from other farms.
- Every chart/table exposes date range, units, source count, and missing-data state.
- Backdated corrections refresh affected summaries deterministically.
- No screen uses predictive, diagnostic, or causal product-effectiveness wording.

### Verification gates

- Backend unit/integration/security tests pass.
- Frontend build, lint, type checks, and responsive UI checks pass.
- Migration up/down or documented rollback rehearsal passes in the isolated validation environment.
- Synthetic scenarios are recorded in the test report.
- Internal dry run is completed before stakeholder revalidation.
- At least the farm owner and one handler perform the revised workflows; respondent count remains adviser-approved.

## 11. Proposed research and validation alignment

These additions require updates to the SRS, SDD, traceability matrix, and validation instrument. If validation responses have already been collected under the current instrument, preserve them as Version 1 and use a clearly labelled Version 2 instrument for the revised features.

### Proposed measurable objectives for adviser approval

1. **Incubation completeness:** users can record and close a cycle with reconciled outcomes and create the resulting batch without duplicate records.
2. **Practical farm-input capture:** handlers can record product identity and observation quality without being forced to invent numeric consumption values.
3. **Task accountability:** managers can assign work and handlers can update completion with an auditable history.
4. **Financial traceability:** authorized users can record transactions and reproduce period/category totals from source entries.
5. **Explainable analytics:** users can identify the records and formula behind every displayed operational indicator.

### Suggested validation measurements

- Task completion success/failure.
- Time required per workflow.
- Number of errors or requests for assistance.
- Reconciliation accuracy against synthetic expected values.
- 1–5 ratings for ease of use, clarity, usefulness, and trust.
- Open comments on missing fields, misleading labels, impractical steps, and recommended changes.

Targets should be approved before testing rather than selected after results are known.

## 12. Major risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Directly removing feed/water measurements breaks BHI/WFR | Incorrect or unavailable indicators | Keep optional measured values, quality labels, missing-data behavior, and versioned formulas |
| One stakeholder's workflow is treated as universal | Poor generalizability | Adviser review plus confirmation with at least one handler/another relevant stakeholder |
| Finance becomes a full accounting project | Schedule failure | Limit scope to recorded transactions and descriptive summaries |
| Brand data is interpreted as effectiveness evidence | Invalid research conclusion | Report usage only; no causal ranking or score contribution |
| Hatch counts do not reconcile | Unreliable batches and analytics | Database/service constraints and completion validation |
| Task feature becomes employee surveillance | Adoption and ethical concerns | Use for workflow coordination; restrict visibility and avoid performance rankings |
| Sensitive finance data leaks | Trust/security failure | Manager-only default, farm-scoped authorization, audit trail, no client-trusted farm IDs |
| New questions are added during active validation | Incomparable responses | Version the instrument and analyze versions separately |
| Too many modules are implemented at once | Regression and incomplete testing | Phase delivery with acceptance gates and a finance deferral option |

## 13. Decisions required from the adviser

Implementation should not start until the following are resolved:

1. Approve the proposed core scope and whether Finance is in the revised MVP or a second release.
2. Confirm that an incubation cycle converts to one batch with hatch date as batch start date.
3. Confirm whether removed/damaged eggs must be recorded separately from unhatched eggs.
4. Approve editable expected hatch dates and the farm's default incubation duration.
5. Approve optional feed/water measurements and the behavior of BHI/WFR when unavailable.
6. Confirm that product brands are descriptive only and do not affect health indicators.
7. Decide whether handlers may submit finance drafts or whether Finance is manager-only.
8. Approve task status/priority definitions and defer recurring tasks/notifications if necessary.
9. Approve descriptive analytics and retain the ban on prediction, diagnosis, causal brand claims, and fighting-performance recommendations.
10. Approve the revised research objectives, validation-instrument versioning, and revalidation respondents.

## 14. Definition of done

The stakeholder request is considered implemented only when:

- the approved requirements and diagrams are reflected in the SRS/SDD;
- migrations are reviewed, reversible, and verified in the isolated environment;
- all role and farm-scope authorization tests pass;
- incubation counts reconcile and batch conversion is idempotent;
- handlers can record products without inventing measurements;
- tasks work end to end for manager and handler;
- finance totals, if included, reconcile to source transactions;
- analytics are descriptive, explainable, versioned, and show missing-data limitations;
- existing data and indicator history remain intact;
- internal synthetic/dry-run tests pass; and
- revised workflows are validated by the farm owner and at least one handler, with findings documented in the MVP Validation Highlights.

