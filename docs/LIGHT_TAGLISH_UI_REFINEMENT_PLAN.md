# Poultry Prophet Light and Taglish UI Refinement Plan

**Status:** Implemented in frontend; manual stakeholder validation pending  
**Date:** September 24, 2026  
**Goal:** Make the system faster to read on a phone, easier for farm users to understand, and clearer under bright outdoor conditions without removing important safeguards or farm records.

## 1. Decisions

1. Use a light, white-based interface as the default.
2. Keep dark mode available for evening use, but do not follow the phone's dark setting by default during farm use.
3. Provide a language setting with **English** and **Taglish**. Show only the selected language, not both versions on every line.
4. Shorten repeated explanations and move secondary guidance into `More info`, expandable sections, or first-use help.
5. Keep safety-critical text visible: selected batch, record date, count/amount limits, save status, destructive-action warnings, and validation errors.
6. Preserve the current architecture, API values, database fields, permissions, and research purpose. Translation changes display text only.

## 2. Why not show English and Tagalog together?

Displaying two complete versions of every label would double the text and recreate the same overwhelming experience. The recommended design is:

- **English mode:** concise plain English.
- **Taglish mode:** natural farm-friendly Taglish, not a literal word-for-word translation.
- The selected preference is saved for the user.
- Standard farm terms such as `batch`, `handler`, `feed`, `vitamins`, and `incubator` may stay in English when they are already familiar to users.

## 3. Text-reduction rules

### Always visible

- Page or task title.
- Batch name and date.
- Required field labels.
- Current bird count, amount, or reconciliation value.
- Validation error and save status.
- Warning before an irreversible action.

### Shorten

- Page introductions to one short sentence or remove them when the title is already clear.
- Button labels to a verb plus object, such as `Save expense` or `Record hatch result`.
- Helper text to one line only when it prevents a likely mistake.
- Empty states to one sentence plus one action.

### Hide until requested

- Research disclaimers and detailed explanations.
- Formula or data-source explanations.
- Why-logging-matters content.
- Less common population categories.
- Optional measurements and advanced filters.
- Report limitations that belong to a specific report section.

### Do not remove

- `Validation environment / synthetic data` warning, but shorten it to one compact banner.
- Human-review and no-diagnosis statements where users could misunderstand the system.
- Missing-data labels such as `No record`.
- Confirmation for finalizing reports, count corrections, and other irreversible actions.

## 4. Proposed language examples

| Current English | Short English | Taglish |
|---|---|---|
| Record what happened | Record activity | Mag-record ng nangyari |
| Which batch? | Batch | Anong batch? |
| Population change | Bird count change | Pagbabago sa bilang |
| Health concern | Health concern | May sakit o sintomas |
| Product used | Feed or medicine used | Feed, vitamins, o gamot |
| What symptoms do you see? | Symptoms seen | Anong sintomas ang nakita? |
| How serious is it? | Concern level | Gaano kaseryoso? |
| Save product record | Save product | I-save ang ginamit |
| Today's work | Today's tasks | Gawain ngayong araw |
| Mark as seen | Mark seen | Nakita ko na |
| Birds alive | Birds alive | Buhay na manok |
| No records | No records yet | Wala pang record |
| Farm-wide | Whole farm | Para sa buong farm |
| Recorded net | Recorded net cash | Naka-record na net cash |

Before implementation, one handler and one owner should review these terms. Their familiar vocabulary takes priority over a formal translation.

## 5. Light outdoor color system

The interface should be white-ish, but not pure white everywhere. A slightly warm or green-tinted background reduces glare while preserving strong contrast.

| Use | Proposed direction |
|---|---|
| Page background | Soft off-white `#F7F9F6` |
| Cards and inputs | White `#FFFFFF` |
| Main text | Near-black green `#17211A` |
| Secondary text | Dark gray-green `#566158` |
| Borders | Visible gray-green `#D5DED4` |
| Primary action | Farm green `#28633F` |
| Selected/active background | Pale green `#E8F2EA` |
| Attention | Amber with dark text |
| Error/danger | Red with icon and label |
| Sidebar | White or very pale green instead of charcoal |

Outdoor-specific rules:

- Use solid surfaces instead of low-opacity or blurred backgrounds.
- Use visible borders; do not rely on subtle shadows.
- Keep body text dark and avoid pale gray text.
- Keep buttons strongly filled and at least 48 px high for main actions.
- Use icons plus words so color is never the only signal.
- Default to light mode; keep a manual `Light / Dark` setting for evening use.

## 6. Screen changes

### Handler home

- Show only greeting, today's tasks, Quick Record, and active batches first.
- Remove repeated farm and role descriptions already visible in the header.
- Use compact batch cards: name, age/stage, birds alive, and one main action.
- Put secondary figures inside `View details`.

### Record activity

- Keep four main choices with short Taglish labels.
- Show selected batch at the top and keep it visible.
- Display only fields needed for the chosen record.
- Put explanations for rare categories behind `Ano ito?` or expandable help.
- After saving, show one concise confirmation: what, batch, date.

### Tasks

- Use short section names: `Late`, `Today`, `Next`, `Done` / `Late`, `Ngayon`, `Susunod`, `Tapos`.
- Hide `No extra instructions` instead of displaying it repeatedly.
- Keep instructions visible only when instructions exist.

### Incubation

- Keep egg-loading fields on one screen.
- Replace long helper paragraphs with field labels and one reconciliation line.
- Keep hatch-result details collapsed until needed.

### Feed, vitamins, and medicine

- Shorten the page title to `Products used` / `Mga ginamit`.
- Keep batch, type, product name, and date visible.
- Keep quantity and notes under `Optional details` when not routinely measured.

### Finance

- Use `Income`, `Expenses`, and `Net cash recorded`.
- Keep the batch selector, date, category, and amount visible.
- Move the accounting limitation to an info icon or compact note near the summary.

### Batch detail and report

- Lead with batch name, stage, age, and birds alive.
- Collapse long report sections on phones.
- Keep detailed limitations inside their relevant section.
- Use plain human-review wording once, instead of repeating it on several cards.

### Settings

- Add `Language: English / Taglish`.
- Add `Appearance: Light / Dark` and default new users to Light.
- Hide development tools in non-development builds.

## 7. Technical implementation approach

1. Create one centralized copy dictionary instead of translating hardcoded text independently in every component.
2. Use stable keys such as `dashboard.todayTasks`, `record.batchQuestion`, and `common.save`.
3. Add English and Taglish dictionaries and persist the selected language per user.
4. Set the document language to `en` or `fil` based on the selected mode.
5. Keep API enums and stored values in English; translate only their display labels.
6. Replace the current color tokens with the light outdoor tokens so all existing components update consistently.
7. Set Light as the application default and retain an explicit Dark option.
8. Remove repeated copy screen by screen after the dictionary is in place.

## 8. Implementation phases

### Phase 1 — Copy audit and vocabulary approval

- Classify every visible sentence as Keep, Shorten, Move, or Remove.
- Review Taglish terminology with one handler and one owner.
- Approve the final English/Taglish vocabulary list.

**Exit:** No disputed farm term remains.

### Phase 2 — Light design foundation

- Replace global color tokens.
- Make the sidebar, mobile navigation, cards, inputs, and dialogs light and solid.
- Set Light as default while retaining Dark in Settings.
- Verify contrast in direct sunlight and at 320–412 px widths.

**Exit:** Main tasks remain readable without relying on shadows or faint gray text.

### Phase 3 — Language system

- Add centralized English and Taglish dictionaries.
- Add and persist the language selector.
- Translate shared navigation, statuses, validation, and confirmation messages first.
- Translate the five critical workflows: record activity, tasks, incubation, products, and finance.

**Exit:** No critical workflow mixes untranslated system text with the selected language.

### Phase 4 — Content simplification

- Remove duplicate subtitles and repeated disclaimers.
- Move optional explanations and fields behind progressive disclosure.
- Collapse long report sections on phones.
- Keep all required validation and safety information visible.

**Exit:** Every critical screen has one clear primary action and no unnecessary paragraph before that action.

### Phase 5 — Validation

- Test English and Taglish with a handler and owner.
- Test light mode outdoors or under strong light.
- Test 320, 360, 390, and 412 px phone widths.
- Check long Taglish labels, text wrapping, contrast, keyboard focus, and screen-reader names.
- Record task time, wrong selections, help requests, and participant comments.

**Exit:** Users complete the main tasks without needing explanations from the demonstrator.

## 9. Acceptance criteria

- Light mode is the default and all text remains readable in bright conditions.
- Users can switch between English and Taglish; their choice persists.
- The interface shows only one selected language at a time.
- Critical screens contain no repeated introductory paragraph.
- Optional guidance is available without blocking the task.
- Batch, date, quantity/count, and save status remain visible when required.
- No feature, permission, data field, or research safeguard is removed merely to reduce text.
- No BHI, BSI, WFR, predictive, diagnostic, or automatic-selection claim is reintroduced.

## 10. Recommended implementation order

Implement the light color foundation first, then the language infrastructure, and only then shorten screen copy. This prevents duplicated translation work and lets the team review text changes consistently across the entire application.

## 11. Implementation record — September 24, 2026

Completed in `/home/sefcurity/Desktop/CAPSTONE/poultry-prophet-frontend`:

- Added a light white/green theme as the default and retained an explicit Dark option.
- Added a centralized English/Taglish dictionary and persisted language preference.
- Added the Settings → Language and appearance screen and document language updates.
- Localized shared navigation, dashboard, record activity, tasks, products, finance, and incubation workflows.
- Shortened repeated helper copy, hid empty task instructions, and kept optional form details progressively disclosed.
- Collapsed long Selection Review Report sections so the phone view leads with the factual summary.
- Kept API values, database fields, permissions, validation safeguards, and research boundaries unchanged.
- Kept the development query tool out of production rendering.

Automated verification passed with `npm run lint` and `npm run build`. Manual review remains for the final farm vocabulary, outdoor readability, phone widths, keyboard focus, and stakeholder task completion.
