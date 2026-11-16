ALTER TABLE professional ADD COLUMN latitude DOUBLE PRECISION;
ALTER TABLE professional ADD COLUMN longitude DOUBLE PRECISION;
ALTER TABLE professional ADD CONSTRAINT professional_latitude_range CHECK (latitude BETWEEN -90 AND 90);
ALTER TABLE professional ADD CONSTRAINT professional_longitude_range CHECK (longitude BETWEEN -180 AND 180);
ALTER TABLE professional ADD CONSTRAINT professional_both_coordinates_or_none CHECK ((latitude IS NULL) = (longitude IS NULL));
CREATE INDEX professional_position_idx ON professional (latitude, longitude);
