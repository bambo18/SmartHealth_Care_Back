CREATE TABLE check_methods (
    -- Primary Key: Auto-incrementing integer ID
    id SERIAL PRIMARY KEY,

    -- Name of the check method (e.g., 'Urine Analysis', 'Eye Examination')
    -- Corresponding to: private String name; (@Column(length = 255, nullable = false))
    name VARCHAR(255) NOT NULL,

    -- Optional detailed description of the check method
    -- Corresponding to: private String description; (@Column(columnDefinition = "TEXT"))
    description TEXT
);

CREATE TABLE condition_check_methods (
    -- Foreign Key referencing the Condition table
    condition_id INTEGER NOT NULL,

    -- Foreign Key referencing the CheckMethod table
    check_method_id INTEGER NOT NULL,

    -- Define the composite Primary Key to ensure uniqueness
    -- for a pair and optimize lookups.
    CONSTRAINT condition_check_methods_pkey PRIMARY KEY (condition_id, check_method_id),

    -- Define the Foreign Key constraint for the Condition.
    -- ON DELETE RESTRICT prevents the deletion of a condition
    -- if it is referenced in this table.
    CONSTRAINT fk_condition
        FOREIGN KEY (condition_id)
        REFERENCES conditions (id)
        ON DELETE RESTRICT,

    -- Define the Foreign Key constraint for the CheckMethod.
    -- ON DELETE RESTRICT prevents the deletion of a check_method
    -- if it is referenced in this table.
    CONSTRAINT fk_check_method
        FOREIGN KEY (check_method_id)
        REFERENCES check_methods (id)
        ON DELETE RESTRICT
);