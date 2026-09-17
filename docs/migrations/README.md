# Database migration runbook

The backend currently uses Hibernate `ddl-auto=update`; it does not automatically execute the
versioned SQL files in `src/main/resources/db/migration`. This is deliberate for the mortality
repair: the migration must not run against Supabase before its backup and preview are reviewed.

## V2 mortality repair

`V2__repair_existing_mortality_accounting.sql` is idempotent. It records `V2` in
`app_schema_migration`, creates synthetic mortality events only for legacy Daily Vitals values
that exceed the event total for the same batch and date, projects Daily Vitals mortality from
events, rebuilds every batch population from the mortality ledger, and verifies the required
invariant before marking itself applied.

The migration fails and rolls back if mortality events would produce a negative population.

Run the following only against an explicitly selected database:

```bash
export DATABASE_URL='postgresql://...'

# 1. Back up the four in-scope tables.
bash scripts/backup-mortality-tables.sh ./backups \
  | tee ./backups/mortality-backup-manifest.txt

# 2. Produce a read-only preview and review every count/status before proceeding.
psql "$DATABASE_URL" --set=ON_ERROR_STOP=1 \
  --file=scripts/preview-mortality-repair.sql \
  | tee ./backups/mortality-preview.txt

# 3. After review, apply through the approval gate.
export BACKUP_FILE="./backups/<reviewed-backup-file>.dump"
export PREVIEW_FILE="./backups/mortality-preview.txt"
export MORTALITY_MIGRATION_APPROVED=YES
bash scripts/apply-mortality-repair.sh

# 4. Re-run the preview and retain the output as post-migration evidence.
```

Do not skip the backup, preview review, or approval environment variable. The migration has not
been executed against Supabase as part of this change.
