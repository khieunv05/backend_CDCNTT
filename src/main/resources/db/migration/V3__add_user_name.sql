-- Adds the display name of an account (required at register, max 100 characters).
-- Existing development rows get '' so the NOT NULL column can be added without a backfill.
ALTER TABLE users
    ADD COLUMN name VARCHAR(100) NOT NULL DEFAULT '' AFTER username;
