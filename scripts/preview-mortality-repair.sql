-- Read-only preview for V2__repair_existing_mortality_accounting.sql.
-- Run this after taking the backup and save the complete output for review.

SELECT current_database() AS database_name, now() AS previewed_at;

SELECT 'backup source row counts' AS section,
       (SELECT count(*) FROM batch) AS batch_rows,
       (SELECT count(*) FROM batch_event) AS batch_event_rows,
       (SELECT count(*) FROM daily_record) AS daily_record_rows,
       (SELECT count(*) FROM alert) AS alert_rows;

WITH event_totals AS (
    SELECT batch_id, event_date, SUM(affected_count)::bigint AS event_total
    FROM batch_event
    WHERE event_type = 'MORTALITY'
    GROUP BY batch_id, event_date
), legacy_gaps AS (
    SELECT r.batch_id,
           r.record_date,
           r.mortality_count,
           COALESCE(e.event_total, 0) AS event_total,
           (r.mortality_count - COALESCE(e.event_total, 0))::bigint AS missing_count
    FROM daily_record r
    LEFT JOIN event_totals e
      ON e.batch_id = r.batch_id AND e.event_date = r.record_date
    WHERE r.mortality_count > COALESCE(e.event_total, 0)
)
SELECT 'synthetic events to create' AS section,
       count(*) AS daily_rows_with_gaps,
       COALESCE(sum(missing_count), 0) AS affected_count_to_create
FROM legacy_gaps;

WITH event_totals AS (
    SELECT batch_id, SUM(affected_count)::bigint AS event_total
    FROM batch_event
    WHERE event_type = 'MORTALITY'
    GROUP BY batch_id
), legacy_gaps AS (
    SELECT r.batch_id,
           (r.mortality_count - COALESCE(e.event_total, 0))::bigint AS missing_count
    FROM daily_record r
    LEFT JOIN (
        SELECT batch_id, event_date, SUM(affected_count)::bigint AS event_total
        FROM batch_event
        WHERE event_type = 'MORTALITY'
        GROUP BY batch_id, event_date
    ) e ON e.batch_id = r.batch_id AND e.event_date = r.record_date
    WHERE r.mortality_count > COALESCE(e.event_total, 0)
), gaps_by_batch AS (
    SELECT batch_id, SUM(missing_count)::bigint AS missing_count
    FROM legacy_gaps
    GROUP BY batch_id
)
SELECT b.id AS batch_id,
       b.name,
       b.initial_population,
       b.current_population AS current_population_before,
       COALESCE(events.event_total, 0) AS mortality_events_before,
       COALESCE(gaps.missing_count, 0) AS synthetic_mortality_to_add,
       b.initial_population
           - COALESCE(events.event_total, 0)
           - COALESCE(gaps.missing_count, 0) AS expected_population_after,
       CASE
           WHEN b.initial_population
                    - COALESCE(events.event_total, 0)
                    - COALESCE(gaps.missing_count, 0) < 0
               THEN 'BLOCKED: mortality exceeds initial population'
           WHEN b.current_population = b.initial_population
                    - COALESCE(events.event_total, 0)
                    - COALESCE(gaps.missing_count, 0)
               THEN 'ALREADY CONSISTENT'
           ELSE 'WILL REPAIR'
       END AS review_status
FROM batch b
LEFT JOIN event_totals events ON events.batch_id = b.id
LEFT JOIN gaps_by_batch gaps ON gaps.batch_id = b.id
ORDER BY b.id;

SELECT 'invariant mismatches before migration' AS section,
       count(*) AS mismatched_batches
FROM batch b
LEFT JOIN (
    SELECT batch_id, SUM(affected_count)::bigint AS mortality_total
    FROM batch_event
    WHERE event_type = 'MORTALITY'
    GROUP BY batch_id
) totals ON totals.batch_id = b.id
WHERE b.initial_population - b.current_population
      <> COALESCE(totals.mortality_total, 0);
