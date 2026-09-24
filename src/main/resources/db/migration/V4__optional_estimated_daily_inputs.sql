-- Stakeholder feedback: water and feed may be estimated or unavailable.
-- Preserve the fields for analytics, but do not force handlers to invent numbers.
ALTER TABLE daily_record ALTER COLUMN feed_intake_g DROP NOT NULL;
ALTER TABLE daily_record ALTER COLUMN water_intake_ml DROP NOT NULL;
