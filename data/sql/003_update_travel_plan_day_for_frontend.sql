USE love_travel;

ALTER TABLE travel_plan_day
  MODIFY COLUMN plan_date DATE NULL;

ALTER TABLE travel_plan_day
  ADD COLUMN detail TEXT NULL AFTER title,
  ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER detail,
  ADD COLUMN created_by_user_id BIGINT NULL AFTER sort_order;

UPDATE travel_plan_day
SET detail = COALESCE(detail, time_arrangement),
    created_by_user_id = COALESCE(created_by_user_id, updated_by_user_id),
    sort_order = id
WHERE deleted = 0;
