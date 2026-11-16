CREATE EXTENSION IF NOT EXISTS postgis;
ALTER TABLE professional ADD COLUMN location geography(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;
CREATE INDEX professional_location_idx ON professional USING GIST (location);
