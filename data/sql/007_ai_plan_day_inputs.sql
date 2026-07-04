USE love_travel;

ALTER TABLE travel_plan_day
  ADD COLUMN ai_places_json LONGTEXT NULL AFTER detail,
  ADD COLUMN ai_must_visit_places_json LONGTEXT NULL AFTER ai_places_json,
  ADD COLUMN ai_hotel_location VARCHAR(120) NULL AFTER ai_must_visit_places_json;
