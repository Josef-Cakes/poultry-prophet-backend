-- Keep scheduled vaccination tasks out of the handler action queue until their reminder date.
BEGIN;

UPDATE handler_task t
SET visible_from = p.remind_on::timestamp AT TIME ZONE 'Asia/Manila'
FROM batch_vaccination_plan_item p
WHERE t.source_type = 'VACCINATION_PLAN'
  AND t.visible_from IS NULL
  AND (t.source_id = p.id OR p.task_id = t.id);

ALTER TABLE handler_task
    DROP CONSTRAINT IF EXISTS ck_vaccination_task_visibility;

ALTER TABLE handler_task
    ADD CONSTRAINT ck_vaccination_task_visibility
    CHECK (upper(source_type) IS DISTINCT FROM 'VACCINATION_PLAN' OR visible_from IS NOT NULL);

COMMIT;
