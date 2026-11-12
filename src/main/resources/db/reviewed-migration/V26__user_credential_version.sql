-- Invalidates access tokens whenever a user's password changes.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS credential_version bigint NOT NULL DEFAULT 0;
