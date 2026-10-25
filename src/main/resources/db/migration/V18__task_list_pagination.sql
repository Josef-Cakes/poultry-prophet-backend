-- Supporting indexes for farm-scoped task filtering and pagination.
BEGIN;

CREATE INDEX IF NOT EXISTS idx_handler_task_farm_status_due
    ON handler_task(farm_id, status, due_at, id);
CREATE INDEX IF NOT EXISTS idx_handler_task_handler_status_due
    ON handler_task(farm_id, assigned_handler_id, status, due_at, id);
CREATE INDEX IF NOT EXISTS idx_handler_task_batch_status_due
    ON handler_task(farm_id, batch_id, status, due_at, id);
CREATE INDEX IF NOT EXISTS idx_handler_task_team_visibility
    ON handler_task(farm_id, assignment_scope, visible_from, status, due_at, id);

COMMIT;
