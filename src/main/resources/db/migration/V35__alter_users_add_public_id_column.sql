-- V35__alter_users_add_public_id_column.sql

ALTER TABLE users ADD COLUMN public_id UUID DEFAULT uuidv7() NOT NULL;