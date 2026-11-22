-- Link a recorded aggregate selection outcome to the finalized evidence snapshot
-- that informed it. Existing rows intentionally remain unlinked legacy activity.
ALTER TABLE batch_selection_session
    ADD COLUMN IF NOT EXISTS selection_review_id bigint;

ALTER TABLE batch_selection_session
    ADD COLUMN IF NOT EXISTS source_data_acknowledged boolean NOT NULL DEFAULT false;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_selection_session_review') THEN
        ALTER TABLE batch_selection_session
            ADD CONSTRAINT fk_selection_session_review
            FOREIGN KEY (selection_review_id) REFERENCES batch_selection_review(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_selection_session_farm_batch_review
    ON batch_selection_session(farm_id, batch_id, selection_review_id);
