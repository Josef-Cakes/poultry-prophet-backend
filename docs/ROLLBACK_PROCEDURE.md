# MVP rollback procedure

Rollback is an operator-approved production procedure. It was not executed here.

1. Announce maintenance/read-only mode to the owner and record the incident/change ID.
2. Stop writes or place the application behind the approved maintenance control.
3. Confirm the exact deployed frontend/backend versions and the database migration state.
4. If the application binary is faulty, restore the previously approved frontend/backend artifacts without changing the database.
5. If the schema migration must be reverted, use the database owner’s reviewed reverse migration or restore the pre-deployment backup. Do not guess or run ad-hoc destructive SQL.
6. Verify batch population, typed events, daily records, indicators, alerts, authentication, CORS, and WebSocket authorization with smoke tests.
7. Re-enable traffic only after owner sign-off.
8. Preserve logs, version responses, backup identifiers, and the validation evidence.

The validation reset script is not a production rollback. It only removes the named validation Compose volume after interactive confirmation.
