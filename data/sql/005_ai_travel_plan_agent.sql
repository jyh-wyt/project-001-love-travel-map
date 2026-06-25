USE love_travel;

ALTER TABLE app_user
  ADD COLUMN member_level VARCHAR(20) NOT NULL DEFAULT 'FREE' AFTER status;

CREATE TABLE IF NOT EXISTS ai_agent_run (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id VARCHAR(64) NOT NULL,
  space_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  agent_type VARCHAR(32) NOT NULL,
  model_name VARCHAR(64) NOT NULL,
  prompt_version VARCHAR(64) NOT NULL,
  input_json LONGTEXT NOT NULL,
  output_json LONGTEXT NULL,
  status VARCHAR(32) NOT NULL,
  error_message VARCHAR(1000) NULL,
  token_input INT NOT NULL DEFAULT 0,
  token_output INT NOT NULL DEFAULT 0,
  duration_ms INT NOT NULL DEFAULT 0,
  accepted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_ai_agent_run_run_id (run_id),
  KEY idx_ai_agent_run_space_created (space_id, created_at),
  KEY idx_ai_agent_run_user_created (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS ai_agent_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  event_message VARCHAR(500) NOT NULL,
  event_json LONGTEXT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ai_agent_event_run_id (run_id)
);

CREATE TABLE IF NOT EXISTS ai_plan_day_draft (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  run_id VARCHAR(64) NOT NULL,
  space_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  plan_day_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  morning_json LONGTEXT NULL,
  afternoon_json LONGTEXT NULL,
  evening_json LONGTEXT NULL,
  recommendations_json LONGTEXT NULL,
  tips_json LONGTEXT NULL,
  status VARCHAR(32) NOT NULL,
  applied_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_ai_plan_day_draft_run_id (run_id),
  KEY idx_ai_plan_day_draft_day_created (plan_day_id, created_at)
);
