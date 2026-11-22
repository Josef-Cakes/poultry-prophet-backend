-- Reviewed rollout required. Finalization also locks the batch while scheduling.
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_open_review_followup_per_batch
    ON handler_task (farm_id, batch_id)
    WHERE source_type = 'SELECTION_REVIEW_FOLLOWUP' AND status IN ('TODO', 'IN_PROGRESS', 'BLOCKED');
