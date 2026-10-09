-- Sex-specific population attribution for the append-only batch event ledger.
-- Existing events remain NULL because their sex allocation is not known.
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

ALTER TABLE batch_sex_composition ADD COLUMN IF NOT EXISTS baseline_event_id bigint;
UPDATE batch_sex_composition s
SET baseline_event_id = COALESCE((
    SELECT MAX(e.id) FROM batch_event e
    WHERE e.batch_id = s.batch_id AND e.event_date <= s.observed_on
), 0)
WHERE s.baseline_event_id IS NULL;
