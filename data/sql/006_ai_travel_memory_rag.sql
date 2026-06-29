USE love_travel;

CREATE TABLE IF NOT EXISTS ai_travel_memory_index (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  memory_id VARCHAR(80) NOT NULL,
  space_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  source_type VARCHAR(30) NOT NULL,
  source_id BIGINT NOT NULL,
  city_code VARCHAR(30) NULL,
  city_name VARCHAR(80) NULL,
  content_hash VARCHAR(64) NOT NULL,
  indexed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ai_memory_id (memory_id),
  KEY idx_ai_memory_space_type (space_id, source_type),
  KEY idx_ai_memory_source (source_type, source_id)
);
