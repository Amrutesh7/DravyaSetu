ALTER TABLE identifications
ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_identifications_updated_at
BEFORE UPDATE ON identifications
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
