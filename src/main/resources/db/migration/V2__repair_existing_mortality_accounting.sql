-- Poultry Prophet V2: repair mortality accounting in existing data.
-- MANUAL MIGRATION: run only through scripts/apply-mortality-repair.sh after the
-- backup and preview outputs have been reviewed.
--
-- This migration is intentionally not loaded by Spring Boot. The application uses
-- Hibernate ddl-auto=update, so applying it automatically at startup would bypass
-- the required backup/review gate.

BEGIN;

CREATE TABLE IF NOT EXISTS app_schema_migration (
    version varchar(50) PRIMARY KEY,
    description text NOT NULL,
    applied_at timestamptz NOT NULL DEFAULT now()
);

DO $migration$
DECLARE
    migration_version constant varchar(50) := 'V2';
BEGIN
    IF EXISTS (
        SELECT 1 FROM app_schema_migration WHERE version = migration_version
    ) THEN
        RETURN;
    END IF;

    -- Capture the exact legacy gaps before inserting synthetic events. There is at most
    -- one daily_record per (batch, record_date) by the application's unique constraint.
    CREATE TEMP TABLE mortality_migration_gaps (
        batch_id bigint NOT NULL,
        record_date date NOT NULL,
        handler_id bigint NOT NULL,
        missing_count integer NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO mortality_migration_gaps (batch_id, record_date, handler_id, missing_count)
    SELECT r.batch_id,
           r.record_date,
           r.handler_id,
           (r.mortality_count - COALESCE(e.event_total, 0))::integer
    FROM daily_record r
    LEFT JOIN (
        SELECT batch_id, event_date, SUM(affected_count)::bigint AS event_total
        FROM batch_event
        WHERE event_type = 'MORTALITY'
        GROUP BY batch_id, event_date
    ) e ON e.batch_id = r.batch_id AND e.event_date = r.record_date
    WHERE r.mortality_count > COALESCE(e.event_total, 0);

    -- A legacy daily row has a required handler_id, which gives the synthetic event a
    -- valid actor while preserving the source of the imported value.
    INSERT INTO batch_event (
        batch_id,
        handler_id,
        event_date,
        event_type,
        severity_label,
        affected_count,
        title,
        details,
        tags,
        created_at
    )
    SELECT batch_id,
           handler_id,
           record_date,
           'MORTALITY',
           'MIGRATION',
           missing_count,
           'Legacy Daily Vitals mortality migration',
           'V2 migration: imported the mortality difference not represented by mortality events.',
           'MIGRATION',
           now()
    FROM mortality_migration_gaps
    WHERE missing_count > 0;

    -- Daily mortality is a projection of canonical mortality events after the import.
    UPDATE daily_record r
    SET mortality_count = totals.event_total::integer,
        updated_at = now()
    FROM (
        SELECT r2.id,
               COALESCE(SUM(e.affected_count), 0)::bigint AS event_total
        FROM daily_record r2
        LEFT JOIN batch_event e
          ON e.batch_id = r2.batch_id
         AND e.event_date = r2.record_date
         AND e.event_type = 'MORTALITY'
        GROUP BY r2.id
    ) totals
    WHERE r.id = totals.id
      AND r.mortality_count <> totals.event_total;

    -- Refuse to silently create an impossible population. The Phase 1 database check
    -- constraint should also reject this, but this message identifies the bad batches.
    IF EXISTS (
        SELECT 1
        FROM batch b
        LEFT JOIN (
            SELECT batch_id, SUM(affected_count)::bigint AS mortality_total
            FROM batch_event
            WHERE event_type = 'MORTALITY'
            GROUP BY batch_id
        ) totals ON totals.batch_id = b.id
        WHERE b.initial_population - COALESCE(totals.mortality_total, 0) < 0
    ) THEN
        RAISE EXCEPTION
            'V2 blocked: one or more batches have more mortality events than initial_population; review preview before retrying';
    END IF;

    -- Batch population is fully rebuilt from the event ledger, including batches with
    -- no mortality events.
    WITH mortality_totals AS (
        SELECT b.id AS batch_id,
               COALESCE(SUM(e.affected_count), 0)::bigint AS mortality_total
        FROM batch b
        LEFT JOIN batch_event e
          ON e.batch_id = b.id
         AND e.event_type = 'MORTALITY'
        GROUP BY b.id
    )
    UPDATE batch b
    SET current_population = (b.initial_population - totals.mortality_total)::integer
    FROM mortality_totals totals
    WHERE b.id = totals.batch_id;

    -- Verify the required invariant before recording the migration as applied.
    IF EXISTS (
        SELECT 1
        FROM batch b
        LEFT JOIN (
            SELECT batch_id, SUM(affected_count)::bigint AS mortality_total
            FROM batch_event
            WHERE event_type = 'MORTALITY'
            GROUP BY batch_id
        ) totals ON totals.batch_id = b.id
        WHERE b.initial_population - b.current_population
              <> COALESCE(totals.mortality_total, 0)
    ) THEN
        RAISE EXCEPTION 'V2 verification failed: batch population does not equal mortality ledger';
    END IF;

    INSERT INTO app_schema_migration (version, description)
    VALUES (
        migration_version,
        'Repair mortality events, daily mortality projections, and batch populations'
    );
END
$migration$;

COMMIT;
