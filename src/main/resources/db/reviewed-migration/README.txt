Reviewed Flyway track. V26 through V33 require review and a coordinated rollout.
New migrations must use a unique version. See docs/migrations/README.md.

V34 adds expense-correction links. It deliberately fails if the target already
has either column or the index; audit any manually changed schema and prepare a
separately reviewed forward strategy instead of silently accepting a mismatch.

V30 permits the stored population to exceed its starting count after transfers in;
the application validates the chronological ledger and retains the nonnegative constraint.
V31 prevents multiple open selection-review follow-ups for a batch. Both require a
coordinated backend/frontend rollout and a reviewed migration on each target database.
V26–V33 were also rehearsed against an isolated PostgreSQL 17 restoration of the
existing Supabase public-schema backup on November 22, 2026. No live rollout is
implied by that rehearsal. See docs/RELEASE_VERIFICATION_2026-11-22.md.
