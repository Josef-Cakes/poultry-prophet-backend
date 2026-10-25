-- Normalize legacy manual tasks created with the old "Any handler" control.
-- Batch-linked tasks become visible to the batch team; farm-wide tasks become
-- visible to every handler in the farm.
BEGIN;

UPDATE handler_task
SET assignment_scope = CASE WHEN batch_id IS NULL THEN 'FARM_TEAM' ELSE 'BATCH_TEAM' END
WHERE assigned_handler_id IS NULL
  AND (assignment_scope IS NULL OR btrim(assignment_scope) NOT IN ('BATCH_TEAM', 'FARM_TEAM'));

-- Preserve the explicit handler when older rows combined it with a team scope.
UPDATE handler_task
SET assignment_scope = 'HANDLER'
WHERE assigned_handler_id IS NOT NULL
  AND (assignment_scope IS NULL OR btrim(assignment_scope) NOT IN ('HANDLER'));

ALTER TABLE handler_task
    DROP CONSTRAINT IF EXISTS ck_handler_task_assignment_consistency;

ALTER TABLE handler_task
    ADD CONSTRAINT ck_handler_task_assignment_consistency
    CHECK (
        (assignment_scope = 'HANDLER' AND assigned_handler_id IS NOT NULL)
        OR
        (assignment_scope IN ('BATCH_TEAM', 'FARM_TEAM') AND assigned_handler_id IS NULL)
    );

COMMIT;
