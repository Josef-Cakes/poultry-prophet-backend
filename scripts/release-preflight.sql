-- Read-only checks for the audited existing Poultry Prophet application database.
-- Does not establish migration provenance by itself; review the release evidence.
BEGIN TRANSACTION READ ONLY;
DO $$
DECLARE expected text; actual text;
BEGIN
  FOREACH expected IN ARRAY ARRAY[
    'auth_refresh_token.user_id','auth_refresh_token.token_hash','auth_refresh_token.revoked_at',
    'batch_event.operation_id','batch_event.occurred_at','batch_event.sale_amount','batch_event.sale_currency',
    'handler_task_status_history.operation_id','handler_task_status_history.previous_status',
    'handler_task_status_history.occurred_at','farm_financial_transaction.source_operation_id',
    'offline_sync_receipt.operation_id','batch.hatch_date','batch.farm_entry_date',
    'handler_task.visible_from','handler_task.assignment_scope'] LOOP
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
      AND table_name=split_part(expected,'.',1) AND column_name=split_part(expected,'.',2)) THEN
      RAISE EXCEPTION 'Required audited legacy field missing: %',expected;
    END IF;
  END LOOP;
  IF EXISTS(SELECT 1 FROM batch WHERE current_population<0) THEN
    RAISE EXCEPTION 'Negative stored population needs review before rollout';
  END IF;
  IF EXISTS(SELECT 1 FROM handler_task WHERE source_type='SELECTION_REVIEW_FOLLOWUP'
    AND status IN ('TODO','IN_PROGRESS','BLOCKED') GROUP BY farm_id,batch_id HAVING count(*)>1) THEN
    RAISE EXCEPTION 'Duplicate open selection follow-ups need review before V31';
  END IF;
  IF EXISTS(SELECT 1 FROM handler_task WHERE upper(source_type)='VACCINATION_PLAN' AND visible_from IS NULL) THEN
    RAISE EXCEPTION 'Legacy vaccination visibility backfill needs review before baseline';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname=current_user AND rolbypassrls) THEN
    RAISE EXCEPTION 'Rehearse the actual backend role before enabling deny-by-default RLS';
  END IF;
END $$;
SELECT 'preflight passed' AS result, current_database(), current_user;
COMMIT;
