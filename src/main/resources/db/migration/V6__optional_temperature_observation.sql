-- Temperature is an optional field because the target farm does not use a thermometer every day.
ALTER TABLE daily_record ALTER COLUMN temperature_c DROP NOT NULL;
