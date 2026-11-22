-- Review and rehearse before deployment. Never enable automatic live baseline/migrate.
-- No FK cascade or TTL: a later archive/delete must not erase accepted command evidence.
CREATE TABLE offline_command_receipt (
    operation_id uuid PRIMARY KEY,
    schema_version integer NOT NULL CHECK (schema_version >= 1),
    farm_id bigint NOT NULL,
    user_id bigint NOT NULL,
    entity_type varchar(64) NOT NULL,
    resource_type varchar(64) NOT NULL,
    resource_id bigint NOT NULL,
    payload jsonb NOT NULL,
    outcome jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Receipts contain scoped command details and are not a browser Data API table.
-- No browser policies: deny-by-default even if a hosted database grants tables
-- to anon/authenticated through default privileges. Backend owner access only;
-- a separate server role needs an explicitly reviewed privilege/RLS arrangement.
ALTER TABLE offline_command_receipt ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE offline_command_receipt FROM PUBLIC;
