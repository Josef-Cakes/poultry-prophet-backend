-- Farm-specific whole-batch sex composition and versioned vaccination plans.
-- All additions are additive; existing batch events and input logs remain the source of truth.
ALTER TABLE batch ADD COLUMN IF NOT EXISTS hatch_date_confirmed_at timestamptz;
ALTER TABLE batch ADD COLUMN IF NOT EXISTS hatch_date_confirmed_by_user_id bigint;

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
    status varchar(32) NOT NULL DEFAULT 'CURRENT',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sex_counts_nonnegative CHECK (male_count >= 0 AND female_count >= 0 AND unclassified_count >= 0),
    CONSTRAINT ck_sex_population_nonnegative CHECK (population_as_of_observation >= 0),
    CONSTRAINT ck_sex_counts_match_population CHECK (male_count + female_count + unclassified_count = population_as_of_observation),
    CONSTRAINT ck_sex_status CHECK (status IN ('CURRENT', 'SUPERSEDED'))
);
CREATE INDEX IF NOT EXISTS idx_sex_composition_batch_date ON batch_sex_composition(farm_id, batch_id, observed_on DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_current_sex_composition ON batch_sex_composition(batch_id) WHERE status = 'CURRENT';

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
CREATE INDEX IF NOT EXISTS idx_handler_task_batch_source ON handler_task(farm_id, batch_id, source_type, source_id);
