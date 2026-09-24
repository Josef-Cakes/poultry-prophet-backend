# Poultry Prophet UI/UX Improvement Implementation Plan

**Document status:** Implementation guide and adviser review record  
**Implementation status:** Phase 1–3 frontend work implemented; Phase 4 manual verification remains  
**Audit date:** September 24, 2026  
**Audit basis:** Current Next.js frontend, Spring Boot API contracts, implemented stakeholder-feedback modules, and the Selection Review Report direction  
**Primary users:** Gamefowl farm handlers and farm owners/managers using phones in real farm conditions

### Implementation record — September 24, 2026

The approved direction has now been implemented in the frontend without changing the REST/API architecture:

- Shared controls use larger phone-safe targets, readable input sizing, visible focus states, reduced-motion support, and improved selected states for choice controls.
- Handler navigation has a role-specific mobile bottom bar with a batch-aware Quick Record sheet; desktop navigation remains available.
- Dashboard batch cards retain the important population/stage facts, add search and stage filtering, and use clearer `population changes` wording rather than combining all changes under `losses`.
- The handler dashboard surfaces open work, and Tasks are grouped into Overdue, Today, Upcoming, and Completed with larger Start/Done actions.
- Product records use the Farm Input Log path, expose batch/date/type context, provide recent-product shortcuts, and avoid requiring quantity when the farm does not measure it.
- Finance requires an explicit batch or Farm-wide choice, shows batch names, provides filters, and labels totals as recorded cash flow rather than profit.
- Incubation hatch completion is progressively disclosed and validates `hatched + unhatched + removed = loaded` before saving.
- Selection Review is available as a read-only factual preview for handlers while report creation/finalization and finance remain manager-only.
- Optional measurements are separated from the main event choices and no longer use daily-score language; no current frontend route presents BHI, BSI, WFR, threshold cut-lines, predictive wording, or an automatic grade.
- Offline behavior is intentionally limited to a visible connection-loss banner and local drafts for the finance and product-record forms. The application does not claim full offline synchronization.

Automated verification completed: `npm run lint` and `npm run build` both pass. Manual verification still required: representative handler/manager walkthroughs, phone-width checks, keyboard/screen-reader checks, and stakeholder confirmation that the revised labels and workflows match actual farm practice. The current daily-record API still requires temperature for the optional measurement-record endpoint; this remains an explicit backend follow-up rather than being hidden by the UI.

## 1. Quick decision summary

The system does not need a new architecture or a visual redesign for its own sake. It needs a **role-based workflow redesign on top of the existing architecture**.

The recommended first implementation should do these things in order:

1. Remove role dead ends and inconsistent record paths.
2. Make every frequent handler action reachable in no more than three primary interactions.
3. Enlarge controls, labels, and feedback for reliable phone use.
4. Simplify forms through progressive disclosure, sensible defaults, and recent-value shortcuts.
5. Make the handler dashboard about today's work and quick recording.
6. Make the manager experience about exceptions, batch review, and batch-linked costs.
7. Add clear online, saving, saved, failed, and retry states without claiming unsupported offline capability.
8. Verify the result through observed farm tasks, accessibility checks, and representative phone sizes.

This plan preserves the existing product purpose: incubation, batch monitoring, health and population events, product use, tasks, finance, descriptive analytics, and human-led batch review. It does not restore BHI, BSI, WFR, thresholds, predictions, or automatic selection grades.

## 2. Product outcome

The improved interface should feel like a **farm logbook and work assistant**, not a data-entry system.

A handler should be able to answer three questions immediately:

- What do I need to do today?
- Which batch am I working on?
- How do I record what just happened?

A manager should be able to answer four questions immediately:

- Which batch needs attention?
- What happened and when?
- What has this batch cost or earned based on recorded transactions?
- Is the batch history complete enough for human review?

## 3. Guardrails

### 3.1 Architecture to preserve

- Next.js routes and React Query data layer.
- Spring Boot REST API and PostgreSQL data model.
- JWT authentication, manager/handler roles, and farm scoping.
- Append-only typed population ledger and idempotent event operations.
- Existing incubation, task, farm-input, finance, alert, analytics, and selection-review modules.
- Automatic lifecycle stage derived from batch age.
- Human decision-making for batch review and final bird selection.

Most work in Phases 1–3 is presentation, navigation, validation, and use of fields already supported by the API. Backend work should be limited to specifically identified gaps, not a platform rewrite.

### 3.2 Product boundaries to preserve

- No disease diagnosis or treatment recommendation.
- No biological or fighting-performance prediction.
- No automatic advance/reject decision.
- No unsupported 0–100 health or stress score.
- No full accounting, inventory valuation, payroll, or tax workflow.
- No claim that missing records mean a healthy batch.
- No receipt OCR, barcode scanning, or full offline synchronization in the first UI/UX pass.

### 3.3 Design principle

Do not remove a valid feature merely to make a screen appear simpler. **Reorder, group, label, and progressively reveal it.** Simplicity means showing the right control at the right time, not hiding required farm records.

## 4. Current-state findings

### 4.1 What already works well

- The system is responsive and primarily uses mobile-friendly cards instead of wide tables.
- Batch cards already expose large `Log now` and `View batch` actions.
- Event logging uses typed categories and dates rather than one unstructured notes field.
- Dates default to today where appropriate.
- Dialogs provide focus containment and return focus when closed.
- Empty, loading, and some error states already exist.
- Stage changes are automatic and visible.
- Role-based desktop navigation already exists.
- The revised Selection Review Report is factual and avoids unsupported scoring.
- Dangerous population operations already use idempotent operation IDs.

These strengths should be retained.

### 4.2 Critical usability and integrity findings

| Priority | Finding | User impact | Required response |
|---|---|---|---|
| P0 | A handler can see `Review report`, but the destination page is manager-only. | Dead end, confusion, and loss of trust. | Either provide the intended non-financial read-only handler view or hide the action. The implemented report contract favors read-only access. |
| P0 | Finance creation does not expose `batchId`, although the API supports it and the research goal is batch-specific cost/income. | Transactions cannot reliably support a batch report. | Add a required batch selector for batch transactions, with a clearly labeled farm-wide option only when appropriate. |
| P0 | Medicine/product use can be recorded in both the event flow and Farm Inputs. | Duplicate entry and conflicting histories. | Establish one canonical write path before redesigning the screens. Recommended: Farm Input Log for products; Batch Event for health, behavior, and population facts. |
| P0 | The daily-readings screen still says readings should be logged every day to calculate health scores. | Creates work the stakeholder does not perform and refers to retired indicators. | Remove score language and daily pressure. Move genuinely measured temperature/feed/water under optional measurements. |
| P0 | Form failures mainly appear as toast messages. | Users may not know which field is wrong; screen-reader support is weak. | Add inline field errors, an error summary, focus to the first invalid field, and toast only as supplemental feedback. |
| P1 | Shared buttons and inputs default to roughly 32–36 px high; some actions are 28–32 px. | Difficult phone use, especially one-handed or outdoors. | Adopt a 44 px minimum interaction target and 48 px for primary field controls/actions. |
| P1 | Mobile navigation requires opening a hamburger menu for frequent routes. | Repeated navigation burden. | Add role-specific mobile bottom navigation while retaining the desktop sidebar. |
| P1 | Numerous labels and metadata use 9–11 px text. | Reduced readability, especially for older users and low-quality phones. | Use 14 px for normal labels/body, 16 px for inputs, and reserve 12 px for secondary metadata. |
| P1 | The population-change form initially presents eleven event types in one long list. | High cognitive load and scrolling. | Ask the user's intent first, then reveal the relevant category group. |
| P1 | Tasks are not organized around Today, Overdue, Upcoming, and Completed; handler controls are small. | Work priorities are unclear. | Group by urgency, show batch context, and provide large status actions. |
| P1 | Input records show a numeric batch ID rather than the batch name and lack filters/date entry. | Records are difficult to recognize and backdate. | Show batch name, expose supported `recordedAt`, and add simple filters. |
| P1 | Manager hatch-result forms are expanded inside every open incubation card. | Long and intimidating page when cycles grow. | Collapse completion forms behind a clear `Record hatch result` action. |
| P1 | Success feedback disappears in a toast and does not summarize what changed. | Users may resubmit or doubt whether data saved. | Show a persistent in-context confirmation with the recorded fact and affected batch. |
| P2 | Batch cards are tall and paginated every five records, with no search/filter. | Slow scanning as the farm grows. | Use compact responsive cards, search, stage filter, and `Load more` or a longer mobile list. |
| P2 | `Losses` combines categories with different meanings. | Can be mistaken for mortality. | Show birds remaining, health-related deaths, and other population changes separately or through a labeled breakdown. |
| P2 | Analytics can show zero when there are no records. | Zero may be interpreted as a measured result. | Display `No records` or `Unavailable` and expose source records. |

## 5. Users and operating conditions

### 5.1 Handler

Assume the handler:

- primarily uses an Android phone;
- may have limited experience with business software;
- may be standing, moving, using one hand, or working in bright light;
- knows farm terminology better than technical terminology;
- records events when something happens rather than completing a long daily form;
- may share responsibility for batches with other handlers; and
- needs confidence that a submission was saved once, to the correct batch and date.

### 5.2 Manager or owner

Assume the manager:

- reviews several batches rather than entering every field personally;
- needs exceptions and incomplete records surfaced first;
- assigns work to any available handler;
- records or reviews expenses and income by batch;
- uses reports to support, not replace, in-person selection judgment; and
- may use either a phone or a larger screen.

### 5.3 Language

Use plain farm language rather than internal system language. Examples:

| Avoid | Prefer |
|---|---|
| Create event | Record what happened |
| Population delta | Number of birds changed |
| Snapshot | Create report |
| Farm input | Feeds, vitamins, and medicines |
| Data unavailable | No record for this period |
| Manager rationale | Manager notes and reason |
| Acknowledge | Mark as seen |

Do not assume that full Tagalog is correct for every target user. During formative testing, compare plain English with short Filipino helper text for critical actions. Store interface copy centrally so a later bilingual interface does not require component rewrites.

## 6. Task-step targets

There is no universal usability rule that every task must have a fixed number of clicks. The project should instead use measurable limits for its own frequent workflows. A **primary interaction** below means a navigation or decision action; typing values is measured separately.

| Role and task | Current route burden | Target | Required-input target |
|---|---:|---:|---:|
| Handler records a health concern from Home | `Log now` → choose type → save | No more than 3 primary interactions | Date defaulted; affected count, concern level, and at least one sign |
| Handler records a repeated medicine/product | Menu → Inputs → save, or separate medicine event path | No more than 3; batch preselected and recent products offered | Product type and product/brand; quantity remains optional |
| Handler completes a task from Home | Menu → Tasks → Done | No more than 2 | Completion note optional unless task requires evidence |
| Handler loads eggs into an incubator | Menu → Incubation → save | No more than 2 | Cycle/reference, incubator, loaded date, eggs loaded |
| Manager records a batch expense | Menu → Finance → save, currently without batch context | No more than 2 from a persistent destination or batch shortcut | Batch, date, category, amount; description optional |
| Manager creates and finalizes a batch review | Open report → create snapshot → choose review → finalize | No more than 3 primary actions from batch detail; do not remove meaningful human review | Period defaults; review status required; notes encouraged |
| Manager opens and resolves an alert | Menu → Alerts → batch → mark seen | No more than 2 for acknowledgement; batch detail remains one explicit action | No extra entry unless a note is needed |

Rare administration may take up to five primary interactions. No frequent handler workflow should require navigating through Settings.

## 7. Proposed information architecture

### 7.1 Mobile handler navigation

Use a persistent bottom bar on phone widths:

1. **Home**
2. **Tasks**
3. **Record** — visually prominent central action
4. **Incubation**
5. **More**

`Record` opens a bottom sheet:

- If one active batch exists, show it as the default and allow changing it.
- If several exist, require a batch choice before showing record types.
- Continue into the existing batch data-entry route so APIs and authorization remain unchanged.

`More` contains Feeds & medicines, Alerts, Settings, account, and logout. Inputs should also be reachable from the Record flow so the handler does not have to understand two different recording systems.

### 7.2 Mobile manager navigation

Use:

1. **Home**
2. **Alerts**
3. **Finance**
4. **Farm summary**
5. **More**

Batch reports remain inside batch detail because they require batch context. Tasks, incubation, handlers, and settings live under More but may also appear as dashboard shortcuts when urgent.

### 7.3 Desktop

Retain the existing sidebar. Rename destinations for clarity, apply the same role ordering as mobile, and preserve the user's current location. A navigation change must not reset an in-progress form without warning.

## 8. Role-based home experience

### 8.1 Handler Home

Order the page as follows:

1. **Today**: overdue tasks, tasks due today, and expected hatch checks.
2. **Record what happened**: one large button with a batch-aware path.
3. **Active batches**: compact cards with batch name, age/stage, birds alive out of initial count, and a clear action.
4. **Recent records**: last three successful entries with time and batch.

Do not lead with analytics, report availability, or configuration. These are secondary to field work.

### 8.2 Manager Home

Order the page as follows:

1. **Needs attention**: overdue tasks, factual alerts, hatch outcomes due, incomplete batch reviews.
2. **Batch overview**: compact cards with stage, birds remaining, health-related deaths, other changes, and recorded cash-flow status.
3. **Quick actions**: record transaction, assign task, start incubation cycle, add batch.
4. **Farm summary**: descriptive totals with a date range and data-availability labels.

### 8.3 Batch-card redesign

- Use a vertical card on phones and a compact grid card on larger screens; do not force a square shape when it creates empty space.
- Keep the whole non-action body clickable, but retain explicit action buttons.
- Primary facts: name, stage, age, birds alive/initial.
- Secondary facts: health-related deaths and other population changes. Never label the combined value as mortality.
- Use one strong primary action and one secondary action at most.
- Remove tiny helper text and avoid placing text beneath overlapping footer buttons.
- Add search and stage/status filters before increasing pagination complexity.

## 9. Screen-by-screen implementation specification

### 9.1 Login and onboarding

- Increase fields and primary button to 48 px high.
- Add show/hide password.
- Associate every label with its field and retain browser password-manager support.
- Show login errors beside the form, not only in a transient message.
- Use a concise explanation: `Record batches, farm work, and costs in one place.`
- Keep account registration and farm joining separate so the first screen remains simple.

### 9.2 Batch detail

For handlers:

- Put `Record what happened` first and make it sticky above the mobile bottom bar.
- Follow it with Today/tasks, current population, and recent records.
- Keep the Selection Review preview read-only and exclude financial data.

For managers:

- Put `Review batch history`, `Record transaction`, and `Assign task` near the top.
- Show factual alerts and data gaps before long historical sections.
- Allow sections such as incubation, product use, finance, and event history to collapse independently.

For both roles:

- Add retry actions to failed sections.
- Show full population categories on request rather than combining them into an unexplained total.
- Keep stage derived from date and explain it in one line; never present an editable stage control.

### 9.3 Record-activity flow

Replace the long first screen with four plain-language choices:

- **Bird count changed**
- **Bird looks sick / health concern**
- **Medicine, vitamin, vaccine, or feed given**
- **Behavior observed**

Place less frequent options under `More records`.

#### Bird count changed

First ask what happened, grouped as:

- Lost: health-related death, accidental death, suspected predation, missing.
- Returned or moved: found/returned, transfer in, transfer out.
- Farm transaction: sold, culled.
- Correction: signed count correction with a clear explanation.

Then ask count, date, and notes. Use the user's selected verb in the confirmation: `Saved: 2 birds recorded as missing for Batch A on Sep 24.`

#### Health concern

- Default date to today.
- Ask affected count, concern level in plain language, and observable signs.
- Keep common signs as large selectable chips with `aria-pressed`; place less common signs under More.
- Add `Other sign` without forcing a long note.
- Do not diagnose or recommend medicine.

#### Product given

- Use Farm Input Log as the recommended canonical product record.
- Preselect the batch from context.
- Offer recently used products for one-tap reuse.
- Require product type and product/brand only.
- Keep quantity, unit, method, and notes optional and clearly marked.
- Expose record date/time because the API already supports `recordedAt`.
- Keep legacy medicine events visible in history but do not require duplicate entry.

#### Behavior

- Use observable wording such as `less active`, `not eating`, or `separating from the group`.
- Avoid interpreting behavior as a stress score.
- Date and observed sign are required; concern level and notes are optional unless the research instrument requires them.

#### Optional measurements

- Rename `Daily readings` to `Optional measurements`.
- Remove `log every day` and health-score language.
- Ask temperature, feed amount, or water amount only when genuinely measured.
- Label estimated values as estimated; never silently treat them as measured.
- Do not show warning-colored `No readings yet` states when the measurement is optional.

### 9.4 Tasks

Handler view:

- Group into Overdue, Today, Upcoming, and Completed.
- Show title, batch or `Farm-wide`, due time, and manager instruction.
- Use 44–48 px Start and Done actions.
- Preserve an easy correction path if a task is completed accidentally; otherwise add confirmation before an irreversible transition.
- Allow a task to link directly to the relevant record action when the task asks for a farm record.

Manager view:

- Label an empty assignee as `Any handler`, reflecting the farm's shared work practice.
- Show batch names and allow farm-wide tasks.
- Expose priority only if the list visually uses it.
- Offer recent task templates only after the basic workflow is validated.

### 9.5 Incubation

- Keep the handler's egg-loading form on one screen with four required fields.
- Clearly separate optional source/bloodline notes.
- Sort open cycles by expected hatch date and label `Due today` or `Needs result` without implying hatch failure.
- Collapse the manager's hatch-result form behind `Record hatch result`.
- Display a live reconciliation: `Hatched + unhatched + removed = loaded`.
- Put the validation error next to counts and prevent completion until totals match.
- Confirm batch creation from a completed cycle because it creates a durable population record.

### 9.6 Feeds, vitamins, and medicines

- Rename the page from `Inputs` to a phrase users recognize.
- Show batch names, not numeric IDs.
- Add date, batch, and product-type filters.
- Provide common units plus `Other`; do not force quantity.
- Add recent-product shortcuts before considering barcode or receipt scanning.
- If launched from a batch, prefill and lock the batch unless the user explicitly changes it.

### 9.7 Finance

- Add batch context to the creation form; the API already supports `batchId`.
- Use category choices: Feed, Medicine/vitamin, Eggs/incubation, Utilities, Labor, Transport, Equipment, Bird sale, Other.
- Keep amount and date required; counterparty and description optional.
- Show Recorded income, Recorded expenses, and Recorded net cash flow. Do not call this profit.
- Add batch/date/type filters and show whether the view is farm-wide or batch-specific.
- Expose the existing void/correction behavior if available; if the API lacks it, track that as a separate backend story instead of silently deleting records.
- Financial details remain manager-only.

### 9.8 Selection Review Report

- Rename `Save report snapshot` to `Create report`.
- Make the reporting period understandable and default it to batch start through today.
- Add a sticky section index on large screens and collapsible sections on phones.
- Put data-availability warnings beside their section, not in one dense cluster.
- Clearly distinguish Draft and Finalized states.
- Require an explicit confirmation before finalization because finalized reports are immutable.
- Give handlers the promised non-financial read-only preview or remove their report action. Do not leave an unauthorized path.
- Preserve the statement that the report supports human review and does not make the selection decision.

### 9.9 Alerts

- Increase `Mark as seen` to at least 44 px.
- Add an explicit `Open batch` action.
- Add Retry to the error state.
- Keep severity text and icons so color is never the only cue.
- Use alerts for factual events, overdue work, and review reminders—not unsupported health judgments.

### 9.10 Farm summary and analytics

- Add a clear date range and simple filters.
- Use `No records` instead of `0%` when a denominator or source set is absent.
- Let summary cards open their source records.
- Keep hatch rate, task status, input-use frequency, population categories, and recorded cash flow descriptive.
- Do not rank workers, feeds, medicine brands, bloodlines, or batches without an approved and valid method.

### 9.11 Settings

- Keep handler settings limited to account, appearance, and help.
- Keep farm profile, handler management, and administrative controls manager-only.
- Remove active navigation to retired threshold settings; historical configuration may remain available only as clearly labeled legacy information if required for audit.

## 10. Accessibility and mobile design standard

Use **WCAG 2.2 Level AA** as the implementation baseline. WCAG permits some 24 px targets under specific conditions, but Poultry Prophet should use a stricter project target because the interface is phone-first and may be used one-handed in farm conditions.

### 10.1 Component requirements

- Interactive target: 44 × 44 CSS px minimum; primary fields and actions: 48 px high.
- Input text: 16 px on phones; normal labels/body: at least 14 px; metadata: no smaller than 12 px.
- Body-text contrast: at least 4.5:1; large text and meaningful non-text UI boundaries: at least 3:1.
- Visible keyboard focus on every control in both light and dark themes.
- No action communicated only by color, icon, position, or shape.
- Support text resizing and reflow without horizontal scrolling at 320 CSS px and 400% zoom where applicable.
- Respect reduced-motion preferences.
- Keep a focused control visible above sticky headers, dialogs, and the proposed bottom navigation.

### 10.2 Forms

- Every input has a programmatically associated label.
- Groups use `fieldset` and `legend` or equivalent accessible group semantics.
- Choice chips expose selected state using `aria-pressed` or the correct native control.
- Helper and error text use `aria-describedby`.
- Invalid fields use `aria-invalid`.
- Submission errors produce an error summary and focus the first invalid field.
- Save progress announces `Saving`, `Saved`, `Failed`, or `Pending sync` through an accessible status region.
- Do not use placeholder text as the only label.

### 10.3 Content

- Use one instruction per sentence.
- Put the verb first: `Record hatch result`, `Add expense`, `Mark done`.
- Explain unfamiliar terms at the point of use.
- Keep critical instructions visible; do not place them only inside tooltips.
- Use consistent names for the same action across all screens.

Authoritative references:

- [WCAG 2.2](https://www.w3.org/TR/WCAG22/)
- [Understanding target size minimum](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum)
- [Understanding error identification](https://www.w3.org/WAI/WCAG22/Understanding/error-identification)
- [Understanding labels or instructions](https://www.w3.org/WAI/WCAG22/Understanding/labels-or-instructions)
- [Understanding status messages](https://www.w3.org/WAI/WCAG22/Understanding/status-messages)
- [Understanding reflow](https://www.w3.org/WAI/WCAG22/Understanding/reflow)

## 11. Connectivity, saving, and recovery

The current frontend makes online API requests and must not be presented as fully offline-capable.

### Phase-one behavior

- Show an unobtrusive `Offline` or `Connection lost` banner based on browser/network failures.
- Preserve unsent form values locally so navigation, refresh, or a weak connection does not erase work.
- Disable duplicate submissions while a request is in progress.
- Display a Retry action after failure.
- Say `Saved` only after the server confirms success.
- Keep the generated idempotency key for event retries so a retry does not double-deduct population.
- If a local draft exists, state clearly that it is a draft and not yet part of farm records.

### Deferred offline queue

A background write queue should be a separate, reviewed feature. If later approved, begin only with idempotent batch-event writes and display every queued, failed, or synced item. Do not silently queue finance or irreversible manager actions.

## 12. Design-system work

Implement shared foundations before editing individual pages:

1. Update Button, Input, Select, Textarea, Dialog close controls, Tabs, and chips to the target sizes.
2. Define semantic typography tokens for page title, section title, body, label, metadata, and numeric value.
3. Define factual statuses: neutral, information, caution, critical, success, and unavailable.
4. Create shared Field, FieldError, ErrorSummary, SaveStatus, EmptyState, RetryState, and ConfirmAction patterns.
5. Create MobileBottomNav, QuickRecordSheet, BatchPicker, and StickyMobileAction components.
6. Create consistent RecordCard and SummaryCard patterns.
7. Replace hardcoded one-off text sizes and heights incrementally.

The theme may keep its current warm farm identity. Visual polish should use spacing, hierarchy, and legibility rather than adding decorative gradients, animations, or dense dashboards.

## 13. Implementation phases

### Phase 0 — Confirm workflows and copy (1–2 working days)

- Walk one handler and one manager through the current critical tasks.
- Confirm the five most frequent handler records and the actual language they use.
- Approve the canonical product/medicine record path.
- Confirm whether handlers receive a non-financial report preview.
- Record baseline task time, errors, help requests, and abandoned tasks.
- Freeze the labels and priorities for Phase 1.

**Exit:** Signed task map and adviser-approved scope; no unresolved P0 workflow contradiction.

### Phase 1 — Foundations and blockers (2–3 working days)

- Fix unauthorized/dead-end actions.
- Implement accessible field/error/status patterns.
- Raise touch targets and text sizes.
- Remove retired score/daily-reading language.
- Add batch selection to finance.
- Resolve medicine/product duplicate-entry UX.
- Add server-confirmed success summaries and retry states.

**Exit:** No P0 issue remains; shared components pass keyboard, focus, and target-size checks.

### Phase 2 — Handler critical paths (3–5 working days)

- Add handler mobile bottom navigation and Quick Record.
- Reorder Handler Home around Today and active batches.
- Simplify population, health, behavior, and product forms.
- Redesign Tasks and Incubation for phone use.
- Add draft preservation and connection status.

**Exit:** Every frequent handler task meets its interaction target on a 360 px-wide phone.

### Phase 3 — Manager decision paths (3–5 working days)

- Reorder Manager Home around exceptions.
- Improve batch detail and Selection Review Report hierarchy.
- Redesign Finance, Alerts, Inputs history, and Farm Summary.
- Add filters and source-record drill-downs.

**Exit:** Manager can trace every report figure and complete batch review without unsupported claims.

### Phase 4 — Verification and refinement (2–3 working days)

- Test all role paths at 320, 360, 390, 412, 768, and desktop widths.
- Test keyboard-only navigation, screen-reader names/states, light/dark contrast, zoom/reflow, and reduced motion.
- Repeat the same observed tasks from Phase 0 with representative users.
- Fix critical and high-severity findings.
- Update screenshots, user guide, validation evidence, SRS/SDD UI descriptions, and test report.

**Exit:** Acceptance criteria below pass and stakeholder evidence is archived.

### Estimate

For one developer working sequentially, estimate **11–18 focused working days**, excluding stakeholder scheduling and major new backend endpoints. A team can parallelize screen work only after the shared components, copy, and canonical record paths are frozen.

## 14. Verification and acceptance criteria

### 14.1 Functional UX

- Every visible action is authorized for the signed-in role.
- No medicine/product workflow asks for the same fact twice.
- Every finance record clearly identifies its batch or explicitly says Farm-wide.
- Optional measurements are not presented as required daily work.
- Stage is visible and derived automatically.
- Backdated records preserve the user-selected date.
- A failed or repeated event submission cannot silently create a duplicate population change.
- No current screen displays BHI, BSI, WFR, threshold-based selection, predictive wording, or an automatic grade.

### 14.2 Efficiency

- Critical handler tasks meet the targets in Section 6.
- No critical phone workflow requires horizontal scrolling.
- A user never has to remember a numeric batch ID.
- The selected batch and record date remain visible at save time.
- A successful save states what, where, and when it recorded.

### 14.3 Accessibility

- WCAG 2.2 AA automated checks have no critical violations.
- Manual keyboard and focus-order tests pass every route.
- Every form can be completed with labels, instructions, inline errors, and non-color status cues.
- All frequent interactive controls meet the project's 44 px target.
- Essential content remains readable at 200% zoom and reflows at the required narrow viewport/zoom combinations.

### 14.4 Observed usability

Use the same scripted tasks before and after implementation:

1. Record a health concern for a named batch.
2. Record a medicine or vitamin used on that batch.
3. Complete an assigned task.
4. Load eggs into an incubator.
5. Record hatch results and create a batch as manager.
6. Record a batch expense.
7. Create and finalize a batch review.

For each task collect:

- completed without help: yes/no;
- time on task;
- wrong batch/date/category errors;
- duplicate attempts;
- number of facilitator hints;
- one difficulty rating from 1 to 5; and
- one open comment: `What made this difficult or easy?`

With a small capstone sample, report participant counts and observed problems rather than presenting percentages as population-wide proof.

## 15. Test matrix

| Area | Required tests |
|---|---|
| Roles | Manager and handler; direct URL access as well as visible navigation |
| Phones | 320, 360, 390, and 412 CSS px; Android Chrome first, then another available browser |
| Orientation | Portrait for all critical tasks; landscape smoke test |
| Input | Touch, keyboard-only, screen reader, browser autofill |
| Display | Light and dark themes, 200% zoom, narrow reflow, reduced motion |
| Network | Normal, slow request, timeout, disconnect before save, disconnect after request, retry |
| Data | Empty farm, one batch, many batches, long names, no records, partial records, backdated records |
| Safety | Double tap, repeated retry, wrong role, immutable finalization, population correction |

## 16. Deferred enhancements

Defer these until the core workflows pass real-user testing:

- Receipt scanning or OCR.
- Barcode/product scanning.
- Push notifications or SMS.
- Voice entry.
- Complete Filipino/local-language translation.
- Recurring task automation.
- Fully offline write synchronization.
- Advanced charts or configurable dashboards.
- User-customizable card layouts.

Recent-product shortcuts, default dates, batch preselection, and clear categories will produce more reliable MVP value than scanning features at much lower implementation and validation risk.

## 17. Adviser-facing rationale

> The proposed UI/UX work does not change Poultry Prophet's domain, users, data architecture, or research purpose. It reorganizes the implemented features around the actual work of farm handlers and managers. Frequent field tasks are shortened, optional measurements are represented honestly, batch context is made explicit, and accessibility is treated as a functional requirement. The redesign preserves traceability and human decision-making while reducing data-entry burden and avoidable errors.

## 18. Approval gates before code changes

The team and adviser should approve these decisions before implementation:

1. Farm Input Log is the canonical record for feed, vitamin, medicine, and vaccine use; event history remains canonical for symptoms, behavior, and population changes.
2. Handlers may view a non-financial, read-only Selection Review preview; only managers may create/finalize reports and view finance.
3. Finance defaults to batch-specific entry, with farm-wide entry explicitly selected.
4. Optional measurements are moved out of the main event path and do not create missing-data warnings.
5. Mobile navigation differs by role while desktop routes and APIs remain unchanged.
6. The first language pass uses plain English plus user-tested Filipino helper text rather than an unvalidated full translation.

Once these six decisions are approved, Phase 1 can begin without redesigning the architecture.
