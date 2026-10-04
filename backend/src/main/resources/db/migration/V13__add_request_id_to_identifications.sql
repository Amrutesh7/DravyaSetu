ALTER TABLE identifications ADD COLUMN request_id VARCHAR(255);
CREATE INDEX idx_identifications_request_id ON identifications(request_id);