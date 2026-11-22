-- Reviewed rollout required: transferred birds can raise the count above the initial population.
-- Chronological ledger validation remains an application responsibility.
ALTER TABLE batch DROP CONSTRAINT IF EXISTS batch_current_population_bounds;
ALTER TABLE batch ADD CONSTRAINT batch_current_population_bounds
    CHECK (current_population >= 0);
