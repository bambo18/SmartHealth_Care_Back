CREATE TABLE walks (
    id BIGSERIAL PRIMARY KEY,
    pet_id BIGINT NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    distance_km NUMERIC(7, 2) NOT NULL,
    path_coordinates TEXT,

    CONSTRAINT fk_walks_pet
        FOREIGN KEY (pet_id)
        REFERENCES pets (id) -- Assuming the Pet entity corresponds to a 'pets' table
);

-- Optional: Add index for foreign key for performance
CREATE INDEX idx_walks_pet_id ON walks (pet_id);