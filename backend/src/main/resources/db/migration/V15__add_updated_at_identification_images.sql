ALTER TABLE identification_images 
ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TRIGGER update_identification_images_updated_at 
BEFORE UPDATE ON identification_images 
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();