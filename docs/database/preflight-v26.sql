-- Read-only audit for databases at V25. Every count should be zero before upgrading.
SELECT 'users_email' AS issue, count(*) FROM users WHERE email <> lower(btrim(email)) OR email = ''
UNION ALL SELECT 'user_token_version', count(*) FROM users WHERE token_version < 0
UNION ALL SELECT 'plant_identity', count(*) FROM plants WHERE scientific_name IS NULL
    OR btrim(scientific_name) = '' OR btrim(common_name) = '' OR btrim(plant_code) = '' OR btrim(plant_identifier) = ''
UNION ALL SELECT 'identification_result', count(*) FROM identifications
    WHERE confidence IS NULL OR model_version IS NULL OR btrim(model_version) = ''
        OR (status = 'KNOWN' AND plant_id IS NULL) OR (status = 'UNKNOWN' AND plant_id IS NOT NULL)
UNION ALL SELECT 'duplicate_image_position', count(*) FROM (
    SELECT identification_id, sequence_number FROM identification_images GROUP BY 1,2 HAVING count(*) > 1) duplicates
UNION ALL SELECT 'heatmap_parent', count(*) FROM identification_heatmaps h
    JOIN identification_images i ON i.id = h.identification_image_id WHERE h.identification_id <> i.identification_id
UNION ALL SELECT 'duplicate_heatmap_position', count(*) FROM (
    SELECT identification_image_id, sequence_number FROM identification_heatmaps GROUP BY 1,2 HAVING count(*) > 1) duplicates
UNION ALL SELECT 'heatmap_reference', count(*) FROM identification_heatmaps
    WHERE nullif(btrim(storage_key),'') IS NULL AND nullif(btrim(source_url),'') IS NULL
UNION ALL SELECT 'pending_registration', count(*) FROM pending_registrations
    WHERE email <> lower(btrim(email)) OR email = '' OR role <> 'USER' OR attempts < 0
UNION ALL SELECT 'multiple_reset_tickets', count(*) FROM (
    SELECT user_id FROM password_reset_tokens GROUP BY user_id HAVING count(*) > 1) duplicates;
