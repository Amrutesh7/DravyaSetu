ALTER TABLE identifications
DROP CONSTRAINT chk_identifications_status;

ALTER TABLE identifications
    ADD CONSTRAINT chk_identifications_status
        CHECK (
            status IN (
                       'SUCCESS',
                       'LOW_CONFIDENCE',
                       'UNKNOWN',
                       'REJECTED',
                       'PROCESSING',
                       'FAILED',
                       'KNOWN'
                )
            );