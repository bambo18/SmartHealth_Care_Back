-- V2__alter_pet_weight_kg.sql (or similar filename)

-- ALTER COLUMN TYPE command in PostgreSQL to change the 'weight_kg'
-- column to a numeric/decimal type with a total of 5 digits, 
-- and 1 of those digits after the decimal point (e.g., 999.9).

ALTER TABLE pets
RENAME COLUMN weight TO weight_kg;

ALTER TABLE pets
ALTER COLUMN weight_kg TYPE NUMERIC(4, 2);

-- 2. Remove the 'mbti' column
ALTER TABLE pets
DROP COLUMN mbti;

-- Add the 'name' column to the pets table
ALTER TABLE pets
ADD COLUMN name VARCHAR(255) NOT NULL;

-- Set 'species' column to NOT NULL
ALTER TABLE pets
ALTER COLUMN species SET NOT NULL;

-- Set 'gender' column to NOT NULL
ALTER TABLE pets
ALTER COLUMN gender SET NOT NULL;

-- Set 'is_neutered' column to NOT NULL
ALTER TABLE pets
ALTER COLUMN is_neutered SET NOT NULL;