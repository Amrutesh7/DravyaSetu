CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_plants_search_trgm ON plants USING GIN (
    common_name gin_trgm_ops,
    scientific_name gin_trgm_ops
);