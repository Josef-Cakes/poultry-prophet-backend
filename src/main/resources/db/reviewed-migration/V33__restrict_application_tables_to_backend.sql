-- Spring Boot owns application authorization. Browser Data API roles must not
-- bypass it. Run in the explicitly audited application schema; table owners and
-- the backend BYPASSRLS role retain access. Never add permissive browser policies.
DO $$
DECLARE
    target_schema text := current_schema();
    table_name text;
    browser_role text;
BEGIN
    FOR table_name IN
        SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = target_schema AND c.relkind = 'r' AND c.relname IN (
            'alert','app_user','auth_refresh_token','batch','batch_event',
            'batch_handler_assignment','batch_lifecycle_audit','batch_selection_review',
            'batch_selection_session','batch_selection_session_criterion','batch_sex_composition',
            'batch_vaccination_plan_item','bird','bird_score','daily_record','farm',
            'farm_financial_transaction','farm_input_log','farm_product','handler_invite',
            'handler_task','handler_task_status_history','incubation_cycle','indicator',
            'inventory_movement','lifecycle_stage','offline_command_receipt',
            'offline_daily_record_receipt','offline_sync_receipt','ranging_record','report',
            'selection_record','sync_attempt','threshold_config','vaccination_program',
            'vaccination_program_item')
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', target_schema, table_name);
        EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM PUBLIC', target_schema, table_name);
        FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
                EXECUTE format('REVOKE ALL ON TABLE %I.%I FROM %I', target_schema, table_name, browser_role);
            END IF;
        END LOOP;
    END LOOP;
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON TABLES FROM PUBLIC', target_schema);
    FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA %I REVOKE ALL ON TABLES FROM %I', target_schema, browser_role);
        END IF;
    END LOOP;
END $$;
