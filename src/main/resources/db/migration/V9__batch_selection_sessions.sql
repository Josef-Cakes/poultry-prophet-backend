-- Batch-level selection review records. This migration is additive and does not alter
-- population or legacy score tables.
CREATE TABLE IF NOT EXISTS batch_selection_session (
    id bigserial PRIMARY KEY,
    farm_id bigint NOT NULL,
    batch_id bigint NOT NULL,
    selection_date date NOT NULL,
    reviewer_id bigint NOT NULL,
    evaluated_count integer NOT NULL,
    accepted_count integer NOT NULL DEFAULT 0,
    continue_observation_count integer NOT NULL DEFAULT 0,
    not_accepted_count integer NOT NULL DEFAULT 0,
    other_count integer NOT NULL DEFAULT 0,
    status varchar(32) NOT NULL DEFAULT 'DRAFT',
    criteria_notes text,
    session_notes text,
    operation_id uuid,
    supersedes_session_id bigint,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    finalized_at timestamptz,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_selection_session_counts_nonnegative CHECK (
        evaluated_count >= 1 AND accepted_count >= 0 AND continue_observation_count >= 0
        AND not_accepted_count >= 0 AND other_count >= 0
    )
);

CREATE TABLE IF NOT EXISTS batch_selection_session_criterion (
    selection_session_id bigint NOT NULL,
    criterion_code varchar(64) NOT NULL,
    PRIMARY KEY (selection_session_id, criterion_code)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_selection_session_farm') THEN
        ALTER TABLE batch_selection_session ADD CONSTRAINT fk_selection_session_farm
            FOREIGN KEY (farm_id) REFERENCES farm(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_selection_session_batch') THEN
        ALTER TABLE batch_selection_session ADD CONSTRAINT fk_selection_session_batch
            FOREIGN KEY (batch_id) REFERENCES batch(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_selection_session_reviewer') THEN
        ALTER TABLE batch_selection_session ADD CONSTRAINT fk_selection_session_reviewer
            FOREIGN KEY (reviewer_id) REFERENCES app_user(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_selection_session_superseded') THEN
        ALTER TABLE batch_selection_session ADD CONSTRAINT fk_selection_session_superseded
            FOREIGN KEY (supersedes_session_id) REFERENCES batch_selection_session(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_selection_session_criterion') THEN
        ALTER TABLE batch_selection_session_criterion ADD CONSTRAINT fk_selection_session_criterion
            FOREIGN KEY (selection_session_id) REFERENCES batch_selection_session(id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_selection_session_operation_id
    ON batch_selection_session(operation_id) WHERE operation_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_selection_session_farm_batch_date
    ON batch_selection_session(farm_id, batch_id, selection_date);
CREATE INDEX IF NOT EXISTS idx_selection_session_batch_status
    ON batch_selection_session(batch_id, status);
