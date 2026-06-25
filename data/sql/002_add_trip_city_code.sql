USE love_travel;

ALTER TABLE trip
  ADD COLUMN city_code VARCHAR(20) NULL AFTER province_name;

UPDATE trip
SET city_code = city_name
WHERE city_code IS NULL;

ALTER TABLE trip
  MODIFY COLUMN city_code VARCHAR(20) NOT NULL;

ALTER TABLE trip
  DROP INDEX idx_region;

ALTER TABLE trip
  ADD INDEX idx_region (province_code, city_code);

ALTER TABLE trip
  ADD UNIQUE KEY uk_space_city (space_id, city_code, deleted);
