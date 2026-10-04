ALTER TABLE plants
    ADD COLUMN plant_identifier VARCHAR(255);

ALTER TABLE plants
    ADD COLUMN ayurvedic_regional_names TEXT[] NOT NULL DEFAULT '{}';