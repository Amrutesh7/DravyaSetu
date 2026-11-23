-- Fail validation on inconsistent historical data; never silently delete or invent results.
-- Existing migration checksums stay unchanged.
ALTER TABLE users
    ADD CONSTRAINT chk_users_normalized_email CHECK (email = lower(btrim(email)) AND email <> ''),
    ADD CONSTRAINT chk_users_token_version CHECK (token_version >= 0);

ALTER TABLE pending_registrations
    ADD COLUMN send_window_started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN send_count INTEGER NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_pending_normalized_email CHECK (email = lower(btrim(email)) AND email <> ''),
    ADD CONSTRAINT chk_pending_role CHECK (role = 'USER'),
    ADD CONSTRAINT chk_pending_attempts CHECK (attempts >= 0),
    ADD CONSTRAINT chk_pending_send_count CHECK (send_count >= 0);

ALTER TABLE plants
    ALTER COLUMN scientific_name SET NOT NULL,
    ADD CONSTRAINT chk_plants_identity_nonempty CHECK (
        btrim(plant_code) <> '' AND btrim(plant_identifier) <> ''
        AND btrim(common_name) <> '' AND btrim(scientific_name) <> '');

-- Unconstrained NUMERIC preserves the decimal AI confidence instead of rounding to 4 places.
ALTER TABLE identifications
    ALTER COLUMN confidence TYPE NUMERIC,
    ALTER COLUMN confidence SET NOT NULL,
    ALTER COLUMN model_version SET NOT NULL,
    ADD CONSTRAINT chk_identifications_plant_status CHECK (
        (status = 'KNOWN' AND plant_id IS NOT NULL)
        OR (status = 'UNKNOWN' AND plant_id IS NULL)),
    ADD CONSTRAINT chk_identifications_model_nonempty CHECK (btrim(model_version) <> '');

-- Recover precision from valid original response snapshots where available.
DO $$
DECLARE item RECORD; payload JSONB; original_confidence NUMERIC;
BEGIN
    FOR item IN SELECT id, response_payload FROM identifications WHERE response_payload IS NOT NULL LOOP
        BEGIN
            payload := item.response_payload::jsonb;
            IF jsonb_typeof(payload->'confidence') = 'number' THEN
                original_confidence := (payload->>'confidence')::numeric;
                IF original_confidence BETWEEN 0 AND 1 THEN
                    UPDATE identifications SET confidence = original_confidence WHERE id = item.id;
                END IF;
            END IF;
        EXCEPTION WHEN invalid_text_representation OR numeric_value_out_of_range THEN
            -- Malformed legacy snapshots must not prevent migration of valid relational data.
            NULL;
        END;
    END LOOP;
END $$;

ALTER TABLE password_reset_tokens ADD CONSTRAINT uq_password_reset_user UNIQUE (user_id);

ALTER TABLE identification_images
    ADD CONSTRAINT uq_identification_images_sequence UNIQUE (identification_id, sequence_number),
    ADD CONSTRAINT uq_identification_images_parent UNIQUE (id, identification_id);

ALTER TABLE identification_heatmaps
    ADD CONSTRAINT fk_heatmap_image_parent FOREIGN KEY (identification_image_id, identification_id)
        REFERENCES identification_images(id, identification_id) ON DELETE CASCADE,
    ADD CONSTRAINT uq_heatmap_image_sequence UNIQUE (identification_image_id, sequence_number),
    ADD CONSTRAINT chk_heatmap_nonempty_reference CHECK (
        nullif(btrim(storage_key), '') IS NOT NULL OR nullif(btrim(source_url), '') IS NOT NULL);

-- Supports deterministic chronological reads when timestamps are equal.
CREATE INDEX idx_identifications_user_created_id ON identifications(user_id, created_at DESC, id DESC);
CREATE INDEX idx_chat_messages_session_created_id ON chat_messages(session_id, created_at, id);
