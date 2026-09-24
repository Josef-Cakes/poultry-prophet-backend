ALTER TABLE app_user ALTER COLUMN farm_id DROP NOT NULL;

-- Stage auto-derivation flag. Hibernate's ddl-auto:update cannot add a NOT NULL column to a
-- table that already has rows (Postgres rejects it without a default), so we add it here with a
-- default. Idempotent so it is safe to run on every startup.
ALTER TABLE batch ADD COLUMN IF NOT EXISTS stage_manual boolean NOT NULL DEFAULT false;
ALTER TABLE batch DROP CONSTRAINT IF EXISTS batch_current_population_bounds;
ALTER TABLE batch
    ADD CONSTRAINT batch_current_population_bounds
    CHECK (current_population >= 0 AND current_population <= initial_population);

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
