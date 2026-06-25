USE love_travel;

ALTER TABLE app_user
  ADD COLUMN active_space_id BIGINT NULL AFTER status;

ALTER TABLE couple_space
  ADD COLUMN space_type VARCHAR(20) NOT NULL DEFAULT 'COUPLE' AFTER cover_image_url,
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER space_type;

CREATE INDEX idx_active_space_id ON app_user (active_space_id);
CREATE INDEX idx_space_type_status ON couple_space (space_type, status);
