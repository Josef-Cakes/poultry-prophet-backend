-- Keep the reviewed migration history aligned with schema.sql for environments that run
-- versioned migrations. Hibernate ddl-auto:update does not widen existing CHECK constraints.
ALTER TABLE batch DROP CONSTRAINT IF EXISTS batch_status_check;
ALTER TABLE batch
    ADD CONSTRAINT batch_status_check
    CHECK (status IN ('ACTIVE', 'CLOSED', 'ARCHIVED'));
