UPDATE plants
SET plant_identifier = plant_code
WHERE plant_identifier IS NULL OR plant_identifier = '';

ALTER TABLE plants
    ALTER COLUMN plant_identifier SET NOT NULL;
