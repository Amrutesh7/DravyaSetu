ALTER TABLE identifications DROP CONSTRAINT chk_identifications_status;
UPDATE identifications
SET status = 'KNOWN'
WHERE status IN ('SUCCESS', 'LOW_CONFIDENCE')
  AND plant_id IS NOT NULL;
UPDATE identifications
SET status = 'UNKNOWN'
WHERE status IS NULL OR status NOT IN ('KNOWN', 'UNKNOWN');
ALTER TABLE identifications
    ADD CONSTRAINT chk_identifications_status
        CHECK (status IN ('KNOWN', 'UNKNOWN'));
