-- #################################################################
-- 1. PREP: Drop Constraints
-- #################################################################

-- Drop the Foreign Key constraint on the child table (diagnoses) first.
-- This is necessary to allow data manipulation and type changes on the parent.
ALTER TABLE diagnoses
    DROP CONSTRAINT diagnoses_submission_id_fkey;

-- Drop the Unique constraint involving submission_id, as it will be recreated.
ALTER TABLE diagnoses
    DROP CONSTRAINT diagnoses_submission_id_condition_id_key;


-- #################################################################
-- 2. DATA DELETION (The Crucial Step for Type Change)
-- #################################################################

-- 2.1. Delete all rows from the child table (diagnoses)
DELETE FROM diagnoses;

-- 2.2. Delete all rows from the parent table (submissions)
DELETE FROM submissions;


-- #################################################################
-- 3. ALTER: Change Data Types and Defaults
-- #################################################################

-- 3.1. Alter the Parent Table (submissions)

-- A. Remove the old BIGINT IDENTITY property
ALTER TABLE submissions
    ALTER COLUMN id DROP IDENTITY;

-- B. Change the column type to UUID and set the new default generator
ALTER TABLE submissions
    ALTER COLUMN id TYPE UUID USING (id::text::UUID),
    ALTER COLUMN id SET DEFAULT uuidv7(); 


-- 3.2. Alter the Child Table (diagnoses)

-- Change the foreign key column type to UUID
ALTER TABLE diagnoses
    ALTER COLUMN submission_id TYPE UUID USING (submission_id::text::UUID);


-- #################################################################
-- 4. POST: Recreate Constraints
-- #################################################################

-- 4.1. Recreate the Foreign Key constraint (UUID to UUID)
ALTER TABLE diagnoses
ADD CONSTRAINT diagnoses_submission_id_fkey
FOREIGN KEY (submission_id)
REFERENCES submissions(id);

-- 4.2. Recreate the Unique Constraint
ALTER TABLE diagnoses
ADD CONSTRAINT diagnoses_submission_id_condition_id_key
UNIQUE (submission_id, condition_id);