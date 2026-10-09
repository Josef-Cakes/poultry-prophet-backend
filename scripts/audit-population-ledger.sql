-- READ-ONLY population reconciliation audit.
-- Run this against a backup or a read-only database role and save the complete output.
-- This file deliberately contains SELECT statements only; it does not repair or delete data.

SELECT current_database() AS database_name, now() AS audited_at;

WITH ordered_events AS (
    SELECT b.id AS batch_id,
           b.name AS batch_name,
           b.initial_population,
           b.current_population,
           e.id AS event_id,
           e.event_date,
           e.created_at,
           e.event_type,
           e.affected_count,
           e.population_delta,
           e.operation_id,
           e.title,
           CASE
               WHEN e.population_delta IS NOT NULL THEN e.population_delta::bigint
               WHEN e.event_type IN ('MORTALITY', 'HEALTH_DEATH', 'ACCIDENTAL_DEATH',
                                     'SUSPECTED_PREDATION', 'CONFIRMED_PREDATION',
                                     'MISSING', 'TRANSFER_OUT', 'SALE', 'CULLING')
                   THEN -e.affected_count::bigint
               WHEN e.event_type IN ('FOUND_RETURNED', 'TRANSFER_IN')
                   THEN e.affected_count::bigint
               ELSE 0::bigint
           END AS signed_delta,
           CASE
               WHEN e.event_type = 'COUNT_CORRECTION' AND e.population_delta IS NULL
                   THEN 'INVALID_COUNT_CORRECTION'
               ELSE 'OK'
           END AS ledger_status
    FROM batch b
    JOIN batch_event e ON e.batch_id = b.id
), replay AS (
    SELECT ordered_events.*,
           initial_population::bigint
               + SUM(signed_delta) OVER (
                   PARTITION BY batch_id
                   ORDER BY event_date ASC NULLS LAST, created_at ASC NULLS LAST, event_id ASC
                   ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
               ) AS running_population
    FROM ordered_events
)
SELECT batch_id,
       batch_name,
       initial_population,
       current_population,
       event_id,
       event_date,
       event_type,
       affected_count,
       population_delta,
       signed_delta,
       running_population,
       operation_id,
       title,
       ledger_status,
       CASE
           WHEN ledger_status <> 'OK' THEN 'INVALID_EVENT'
           WHEN running_population < 0 THEN 'BELOW_ZERO'
           WHEN running_population > initial_population THEN 'ABOVE_INITIAL'
           ELSE NULL
       END AS issue_code
FROM replay
WHERE ledger_status <> 'OK'
   OR running_population < 0
   OR running_population > initial_population
ORDER BY batch_id, event_date, created_at, event_id;

WITH ordered_events AS (
    SELECT b.id AS batch_id,
           b.name AS batch_name,
           b.initial_population,
           b.current_population,
           e.id AS event_id,
           e.event_date,
           e.created_at,
           CASE
               WHEN e.population_delta IS NOT NULL THEN e.population_delta::bigint
               WHEN e.event_type IN ('MORTALITY', 'HEALTH_DEATH', 'ACCIDENTAL_DEATH',
                                     'SUSPECTED_PREDATION', 'CONFIRMED_PREDATION',
                                     'MISSING', 'TRANSFER_OUT', 'SALE', 'CULLING')
                   THEN -e.affected_count::bigint
               WHEN e.event_type IN ('FOUND_RETURNED', 'TRANSFER_IN')
                   THEN e.affected_count::bigint
               ELSE 0::bigint
           END AS signed_delta
    FROM batch b
    LEFT JOIN batch_event e ON e.batch_id = b.id
), replay AS (
    SELECT ordered_events.*,
           initial_population::bigint
               + COALESCE(SUM(signed_delta) OVER (
                   PARTITION BY batch_id
                   ORDER BY event_date ASC NULLS LAST, created_at ASC NULLS LAST, event_id ASC
                   ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
               ), 0) AS running_population
    FROM ordered_events
), latest AS (
    SELECT DISTINCT ON (batch_id) batch_id, running_population
    FROM replay
    ORDER BY batch_id, event_date DESC NULLS LAST, created_at DESC NULLS LAST, event_id DESC NULLS LAST
)
SELECT b.id AS batch_id,
       b.name AS batch_name,
       b.initial_population,
       b.current_population AS stored_current_population,
       COALESCE(latest.running_population, b.initial_population) AS replayed_population,
       CASE
           WHEN b.current_population < 0 OR b.current_population > b.initial_population
               THEN 'STORED_COUNT_OUT_OF_RANGE'
           WHEN COALESCE(latest.running_population, b.initial_population) < 0
                OR COALESCE(latest.running_population, b.initial_population) > b.initial_population
               THEN 'EVENT_PROJECTION_OUT_OF_RANGE'
           WHEN b.current_population <> COALESCE(latest.running_population, b.initial_population)
               THEN 'STORED_COUNT_MISMATCH'
           ELSE 'VALID'
       END AS reconciliation_status
FROM batch b
LEFT JOIN latest ON latest.batch_id = b.id
WHERE b.current_population < 0
   OR b.current_population > b.initial_population
   OR COALESCE(latest.running_population, b.initial_population) < 0
   OR COALESCE(latest.running_population, b.initial_population) > b.initial_population
   OR b.current_population <> COALESCE(latest.running_population, b.initial_population)
ORDER BY b.id;
