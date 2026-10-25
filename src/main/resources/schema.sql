ALTER TABLE app_user ALTER COLUMN farm_id DROP NOT NULL;

-- Stage auto-derivation flag. Hibernate's ddl-auto:update cannot add a NOT NULL column to a
-- table that already has rows (Postgres rejects it without a default), so we add it here with a
-- default. Idempotent so it is safe to run on every startup.
ALTER TABLE batch ADD COLUMN IF NOT EXISTS stage_manual boolean NOT NULL DEFAULT false;
ALTER TABLE batch DROP CONSTRAINT IF EXISTS batch_current_population_bounds;
ALTER TABLE batch
    ADD CONSTRAINT batch_current_population_bounds
    CHECK (current_population >= 0 AND current_population <= initial_population);

-- Keep the lifecycle allow-list aligned with BatchStatus. Existing databases may still have
-- the original ACTIVE/CLOSED-only constraint, which causes archive requests to fail with
-- PostgreSQL SQLState 23514 even though the application enum supports ARCHIVED.
ALTER TABLE batch DROP CONSTRAINT IF EXISTS batch_status_check;
ALTER TABLE batch
    ADD CONSTRAINT batch_status_check
    CHECK (status IN ('ACTIVE', 'CLOSED', 'ARCHIVED'));

-- Keep the database allow-list aligned with com.poultryprophet.event.EventType.
-- The older constraint rejected supported population-event choices (for example,
-- ACCIDENTAL_DEATH) after the application had already accepted them.
ALTER TABLE batch_event DROP CONSTRAINT IF EXISTS batch_event_event_type_check;
ALTER TABLE batch_event
    ADD CONSTRAINT batch_event_event_type_check
    CHECK (event_type IN (
        'MORTALITY',
        'HEALTH_DEATH',
        'ACCIDENTAL_DEATH',
        'SUSPECTED_PREDATION',
        'CONFIRMED_PREDATION',
        'MISSING',
        'FOUND_RETURNED',
        'TRANSFER_OUT',
        'TRANSFER_IN',
        'SALE',
        'CULLING',
        'COUNT_CORRECTION',
        'HEALTH_CONCERN',
        'VACCINE_MEDICINE',
        'BEHAVIOR_OBSERVATION'
    ));

-- Sex-specific population deltas. Historical events remain NULL rather than being guessed.
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS male_delta integer;
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS female_delta integer;
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS unclassified_delta integer;
ALTER TABLE batch_event DROP CONSTRAINT IF EXISTS ck_batch_event_sex_delta_sum;
ALTER TABLE batch_event
    ADD CONSTRAINT ck_batch_event_sex_delta_sum CHECK (
        (male_delta IS NULL AND female_delta IS NULL AND unclassified_delta IS NULL)
        OR (male_delta IS NOT NULL AND female_delta IS NOT NULL AND unclassified_delta IS NOT NULL
            AND population_delta IS NOT NULL
            AND male_delta + female_delta + unclassified_delta = population_delta)
    );
CREATE INDEX IF NOT EXISTS idx_batch_event_batch_projection
    ON batch_event(batch_id, event_date, id);
CREATE INDEX IF NOT EXISTS idx_batch_event_report_order
    ON batch_event(batch_id, event_date DESC, created_at DESC, id DESC);

-- Batch retirement compatibility. The versioned SQL file remains the reviewed
-- migration record; these additive IF-NOT-EXISTS statements keep validation and
-- deployments that use Spring SQL initialization from failing on the first archive.
ALTER TABLE batch ADD COLUMN IF NOT EXISTS archived_at timestamptz;
ALTER TABLE batch ADD COLUMN IF NOT EXISTS archived_by_user_id bigint;
ALTER TABLE batch ADD COLUMN IF NOT EXISTS archive_reason varchar(500);
ALTER TABLE batch ADD COLUMN IF NOT EXISTS pre_archive_status varchar(32);

CREATE TABLE IF NOT EXISTS batch_lifecycle_audit (
    id bigserial PRIMARY KEY,
    farm_id bigint NOT NULL,
    batch_id bigint NOT NULL,
    batch_name_snapshot varchar(255) NOT NULL,
    action varchar(32) NOT NULL,
    previous_status varchar(32),
    new_status varchar(32),
    reason varchar(500),
    performed_by_user_id bigint,
    performed_at timestamptz NOT NULL DEFAULT now(),
    metadata_json text
);

CREATE INDEX IF NOT EXISTS idx_batch_lifecycle_audit_farm_batch
    ON batch_lifecycle_audit(farm_id, batch_id, performed_at);
CREATE INDEX IF NOT EXISTS idx_batch_status_archived_at
    ON batch(status, archived_at);

-- Sex composition and vaccination scheduling additions. These statements mirror V11 and are
-- idempotent because this deployment currently uses Spring SQL initialization rather than Flyway.
ALTER TABLE batch ADD COLUMN IF NOT EXISTS hatch_date_confirmed_at timestamptz;
ALTER TABLE batch ADD COLUMN IF NOT EXISTS hatch_date_confirmed_by_user_id bigint;

-- Hatch date drives age, lifecycle stage, and vaccine due dates. Farm entry date is kept
-- separately. Legacy rows inherit hatch_date from start_date; unknown historical farm-entry
-- dates remain NULL rather than being fabricated.
ALTER TABLE batch ADD COLUMN IF NOT EXISTS hatch_date date;
ALTER TABLE batch ADD COLUMN IF NOT EXISTS farm_entry_date date;
UPDATE batch SET hatch_date = start_date WHERE hatch_date IS NULL;
CREATE INDEX IF NOT EXISTS idx_batch_hatch_date ON batch(farm_id, hatch_date);
CREATE INDEX IF NOT EXISTS idx_batch_farm_entry_date ON batch(farm_id, farm_entry_date);

CREATE TABLE IF NOT EXISTS batch_sex_composition (
    id bigserial PRIMARY KEY,
    farm_id bigint NOT NULL,
    batch_id bigint NOT NULL,
    observed_on date NOT NULL,
    population_as_of_observation integer NOT NULL,
    male_count integer NOT NULL,
    female_count integer NOT NULL,
    unclassified_count integer NOT NULL,
    recorded_by bigint NOT NULL,
    revision_reason varchar(500),
    notes text,
    operation_id uuid NOT NULL UNIQUE,
    supersedes_record_id bigint,
    baseline_event_id bigint,
    status varchar(32) NOT NULL DEFAULT 'CURRENT',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sex_counts_nonnegative CHECK (male_count >= 0 AND female_count >= 0 AND unclassified_count >= 0),
    CONSTRAINT ck_sex_population_nonnegative CHECK (population_as_of_observation >= 0),
    CONSTRAINT ck_sex_counts_match_population CHECK (male_count + female_count + unclassified_count = population_as_of_observation),
    CONSTRAINT ck_sex_status CHECK (status IN ('CURRENT', 'SUPERSEDED'))
);
CREATE INDEX IF NOT EXISTS idx_sex_composition_batch_date ON batch_sex_composition(farm_id, batch_id, observed_on DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_current_sex_composition ON batch_sex_composition(batch_id) WHERE status = 'CURRENT';

ALTER TABLE batch_sex_composition ADD COLUMN IF NOT EXISTS baseline_event_id bigint;
UPDATE batch_sex_composition s
SET baseline_event_id = COALESCE((
    SELECT MAX(e.id) FROM batch_event e
    WHERE e.batch_id = s.batch_id AND e.event_date <= s.observed_on
), 0)
WHERE s.baseline_event_id IS NULL;

CREATE TABLE IF NOT EXISTS vaccination_program (
    id bigserial PRIMARY KEY,
    farm_id bigint NOT NULL,
    series_id uuid NOT NULL,
    name varchar(160) NOT NULL,
    description text,
    version_number integer NOT NULL,
    supersedes_program_id bigint,
    active boolean NOT NULL DEFAULT true,
    default_for_new_batches boolean NOT NULL DEFAULT false,
    created_by bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_vaccination_program_version UNIQUE (farm_id, series_id, version_number)
);
CREATE INDEX IF NOT EXISTS idx_vaccination_program_farm_active ON vaccination_program(farm_id, active);

CREATE TABLE IF NOT EXISTS vaccination_program_item (
    id bigserial PRIMARY KEY,
    program_id bigint NOT NULL,
    sequence_number integer NOT NULL,
    vaccine_name varchar(160) NOT NULL,
    farm_product_id bigint,
    age_offset_value integer NOT NULL,
    age_offset_unit varchar(16) NOT NULL,
    normalized_offset_days integer NOT NULL,
    reminder_lead_days integer NOT NULL DEFAULT 1,
    route varchar(80),
    dose_guidance varchar(500),
    instructions text,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT ck_vaccine_age_offset CHECK (age_offset_value >= 0 AND normalized_offset_days >= 0),
    CONSTRAINT ck_vaccine_age_unit CHECK (age_offset_unit IN ('DAY', 'WEEK')),
    CONSTRAINT ck_vaccine_reminder CHECK (reminder_lead_days >= 0)
);
CREATE INDEX IF NOT EXISTS idx_vaccine_program_item_program ON vaccination_program_item(program_id, sequence_number);

CREATE TABLE IF NOT EXISTS batch_vaccination_plan_item (
    id bigserial PRIMARY KEY,
    farm_id bigint NOT NULL,
    batch_id bigint NOT NULL,
    program_id bigint NOT NULL,
    program_item_id bigint NOT NULL,
    vaccine_name varchar(160) NOT NULL,
    farm_product_id bigint,
    hatch_date_snapshot date NOT NULL,
    normalized_offset_days integer NOT NULL,
    due_date date NOT NULL,
    remind_on date NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'SCHEDULED',
    task_id bigint,
    completed_input_log_id bigint,
    completed_by bigint,
    completed_at timestamptz,
    completion_operation_id uuid UNIQUE,
    skipped_reason varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_batch_vaccine_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'SKIPPED', 'CANCELLED')),
    CONSTRAINT uq_batch_vaccine_item UNIQUE (batch_id, program_item_id)
);
CREATE INDEX IF NOT EXISTS idx_batch_vaccine_due ON batch_vaccination_plan_item(farm_id, batch_id, due_date, status);

ALTER TABLE handler_task ADD COLUMN IF NOT EXISTS assignment_scope varchar(24) NOT NULL DEFAULT 'HANDLER';
ALTER TABLE handler_task ADD COLUMN IF NOT EXISTS visible_from timestamptz;
ALTER TABLE handler_task ADD COLUMN IF NOT EXISTS source_type varchar(48);
ALTER TABLE handler_task ADD COLUMN IF NOT EXISTS source_id bigint;
ALTER TABLE handler_task ADD COLUMN IF NOT EXISTS completed_by bigint;
UPDATE handler_task
SET assignment_scope = CASE WHEN batch_id IS NULL THEN 'FARM_TEAM' ELSE 'BATCH_TEAM' END
WHERE assigned_handler_id IS NULL
  AND (assignment_scope IS NULL OR btrim(assignment_scope) NOT IN ('BATCH_TEAM', 'FARM_TEAM'));
UPDATE handler_task
SET assignment_scope = 'HANDLER'
WHERE assigned_handler_id IS NOT NULL
  AND (assignment_scope IS NULL OR btrim(assignment_scope) NOT IN ('HANDLER'));
ALTER TABLE handler_task DROP CONSTRAINT IF EXISTS ck_handler_task_assignment_consistency;
ALTER TABLE handler_task ADD CONSTRAINT ck_handler_task_assignment_consistency
    CHECK ((assignment_scope = 'HANDLER' AND assigned_handler_id IS NOT NULL)
        OR (assignment_scope IN ('BATCH_TEAM', 'FARM_TEAM') AND assigned_handler_id IS NULL));
CREATE INDEX IF NOT EXISTS idx_handler_task_batch_source ON handler_task(farm_id, batch_id, source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_handler_task_farm_status_due ON handler_task(farm_id, status, due_at, id);
CREATE INDEX IF NOT EXISTS idx_handler_task_handler_status_due ON handler_task(farm_id, assigned_handler_id, status, due_at, id);
CREATE INDEX IF NOT EXISTS idx_handler_task_batch_status_due ON handler_task(farm_id, batch_id, status, due_at, id);
CREATE INDEX IF NOT EXISTS idx_handler_task_team_visibility ON handler_task(farm_id, assignment_scope, visible_from, status, due_at, id);
UPDATE handler_task t
SET visible_from = p.remind_on::timestamp AT TIME ZONE 'Asia/Manila'
FROM batch_vaccination_plan_item p
WHERE t.source_type = 'VACCINATION_PLAN'
  AND t.visible_from IS NULL
  AND (t.source_id = p.id OR p.task_id = t.id);
ALTER TABLE handler_task DROP CONSTRAINT IF EXISTS ck_vaccination_task_visibility;
ALTER TABLE handler_task ADD CONSTRAINT ck_vaccination_task_visibility
    CHECK (upper(source_type) IS DISTINCT FROM 'VACCINATION_PLAN' OR visible_from IS NOT NULL);
CREATE INDEX IF NOT EXISTS idx_financial_transaction_farm_date ON farm_financial_transaction(farm_id, transaction_date DESC, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_financial_transaction_farm_batch_date ON farm_financial_transaction(farm_id, batch_id, transaction_date DESC, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_farm_input_log_farm_recorded ON farm_input_log(farm_id, recorded_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_farm_input_log_farm_batch_recorded ON farm_input_log(farm_id, batch_id, recorded_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_movement_farm_occurred ON inventory_movement(farm_id, occurred_at DESC, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_movement_farm_product_occurred ON inventory_movement(farm_id, farm_product_id, occurred_at DESC, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_movement_farm_batch_occurred ON inventory_movement(farm_id, batch_id, occurred_at DESC, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_alert_batch_ack_created ON alert(batch_id, acknowledged_at, created_at DESC, id DESC);

-- Inventory valuation and batch consumption-cost snapshots.
ALTER TABLE farm_product ADD COLUMN IF NOT EXISTS average_unit_cost numeric(16,6);
ALTER TABLE farm_product ADD COLUMN IF NOT EXISTS currency varchar(3);
ALTER TABLE farm_product ADD COLUMN IF NOT EXISTS valuation_status varchar(16);
UPDATE farm_product
SET currency = 'PHP'
WHERE currency IS NULL OR btrim(currency) = '';
UPDATE farm_product
SET valuation_status = CASE
    WHEN average_unit_cost IS NOT NULL AND average_unit_cost > 0 THEN 'VALUED'
    ELSE 'UNVALUED'
END
WHERE valuation_status IS NULL;
ALTER TABLE farm_product ALTER COLUMN currency SET DEFAULT 'PHP';
ALTER TABLE farm_product ALTER COLUMN currency SET NOT NULL;
ALTER TABLE farm_product ALTER COLUMN valuation_status SET DEFAULT 'UNVALUED';
ALTER TABLE farm_product ALTER COLUMN valuation_status SET NOT NULL;
ALTER TABLE farm_product DROP CONSTRAINT IF EXISTS ck_farm_product_valuation_status;
ALTER TABLE farm_product ADD CONSTRAINT ck_farm_product_valuation_status
    CHECK (valuation_status IN ('VALUED', 'UNVALUED', 'FREE'));
ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS unit_cost_snapshot numeric(16,6);
ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS inventory_value_delta numeric(16,2);
ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS cost_status varchar(16);
ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS costed_at timestamptz;
ALTER TABLE inventory_movement DROP CONSTRAINT IF EXISTS ck_inventory_movement_cost_status;
ALTER TABLE inventory_movement ADD CONSTRAINT ck_inventory_movement_cost_status
    CHECK (cost_status IS NULL OR cost_status IN ('VALUED', 'UNVALUED', 'FREE'));
ALTER TABLE farm_financial_transaction ADD COLUMN IF NOT EXISTS source_type varchar(32);
ALTER TABLE farm_financial_transaction ADD COLUMN IF NOT EXISTS source_operation_id varchar(80);
ALTER TABLE farm_financial_transaction ADD COLUMN IF NOT EXISTS source_event_id bigint;
UPDATE farm_financial_transaction
SET source_type = 'MANUAL'
WHERE source_type IS NULL OR btrim(source_type) = '';
ALTER TABLE farm_financial_transaction ALTER COLUMN source_type SET DEFAULT 'MANUAL';
ALTER TABLE farm_financial_transaction ALTER COLUMN source_type SET NOT NULL;
ALTER TABLE farm_input_log ALTER COLUMN quantity TYPE numeric(14,3) USING quantity::numeric;
ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS unit_cost_snapshot numeric(16,6);
ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS calculated_cost numeric(16,2);
ALTER TABLE farm_input_log ADD COLUMN IF NOT EXISTS cost_status varchar(16);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_movement_input_log
    ON inventory_movement(farm_id, farm_input_log_id)
    WHERE farm_input_log_id IS NOT NULL;

-- Offline-first field recording and atomic sale proceeds.
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS occurred_at timestamptz;
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS sale_amount numeric(14,2);
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS sale_currency varchar(3);
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS sale_buyer varchar(255);
ALTER TABLE batch_event ADD COLUMN IF NOT EXISTS sale_reference varchar(255);
ALTER TABLE handler_task_status_history ADD COLUMN IF NOT EXISTS previous_status varchar(32);
ALTER TABLE handler_task_status_history ADD COLUMN IF NOT EXISTS operation_id uuid;
ALTER TABLE handler_task_status_history ADD COLUMN IF NOT EXISTS occurred_at timestamptz;
CREATE UNIQUE INDEX IF NOT EXISTS uq_task_status_history_operation
    ON handler_task_status_history(operation_id) WHERE operation_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_financial_source_operation
    ON farm_financial_transaction(farm_id, source_operation_id)
    WHERE source_operation_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_financial_sale_source_event
    ON farm_financial_transaction(source_event_id)
    WHERE source_event_id IS NOT NULL;
ALTER TABLE batch_event DROP CONSTRAINT IF EXISTS ck_batch_event_sale_amount_positive;
ALTER TABLE batch_event ADD CONSTRAINT ck_batch_event_sale_amount_positive
    CHECK (sale_amount IS NULL OR sale_amount > 0);
CREATE TABLE IF NOT EXISTS offline_sync_receipt (
    operation_id uuid PRIMARY KEY,
    farm_id bigint NOT NULL,
    entity_type varchar(64) NOT NULL,
    server_id bigint,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_offline_sync_receipt_farm ON offline_sync_receipt(farm_id, created_at);
