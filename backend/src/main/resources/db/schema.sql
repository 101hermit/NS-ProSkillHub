-- ============================================================================
-- EasySkillHub (ESKH 1.0) · 数据库 schema
-- 可移植 DDL：H2 2.2.224 (MODE=MySQL) 与 MySQL 8.0.45 均已实测通过
-- 规范见 docs/02-统一数据模型与DDL.md
-- 纪律：不使用 ENGINE= / DEFAULT CHARSET= / 前缀索引 / ON DUPLICATE KEY / JSON 函数
-- ---------------------------------------------------------------------------
-- 登录认证（三系统共有）：账号 + 会话令牌
-- 说明：密码用 PBKDF2WithHmacSHA256 加盐哈希（12 万次迭代），不存明文；
--       令牌是随机 32 位十六进制串，默认 7 天有效，可随时撤销。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_account (
  id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(64)  NOT NULL,
  display_name  VARCHAR(96)  NOT NULL DEFAULT '',
  password_hash VARCHAR(255) NOT NULL,
  password_salt VARCHAR(64)  NOT NULL,
  role          VARCHAR(24)  NOT NULL DEFAULT 'viewer',
  status        VARCHAR(16)  NOT NULL DEFAULT 'active',
  last_login_at DATETIME,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_account_username (username)
);

CREATE TABLE IF NOT EXISTS auth_token (
  id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
  token      VARCHAR(64)  NOT NULL,
  username   VARCHAR(64)  NOT NULL,
  role       VARCHAR(24)  NOT NULL DEFAULT 'viewer',
  expires_at DATETIME     NOT NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_auth_token_token (token),
  KEY idx_auth_token_user (username)
);

-- ============================================================================

CREATE TABLE IF NOT EXISTS app_meta (
  meta_key    VARCHAR(64)  NOT NULL PRIMARY KEY,
  meta_value  LONGTEXT,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS category (
  code        VARCHAR(48)  NOT NULL PRIMARY KEY,
  label       VARCHAR(64)  NOT NULL,
  kind        VARCHAR(16)  NOT NULL DEFAULT 'general',
  scene_code  VARCHAR(24)  NOT NULL DEFAULT 'general',
  sort_no     INT          NOT NULL DEFAULT 0,
  skill_count INT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS skill (
  id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
  skill_id       VARCHAR(191) NOT NULL,
  slug           VARCHAR(191) NOT NULL,
  name           VARCHAR(191) NOT NULL,
  title          VARCHAR(191) NOT NULL DEFAULT '',
  description    TEXT,
  category_code  VARCHAR(48)  NOT NULL DEFAULT 'general',
  scene_code     VARCHAR(24)  NOT NULL DEFAULT 'general',
  tags           VARCHAR(512) NOT NULL DEFAULT '',
  version        VARCHAR(48)  NOT NULL DEFAULT '1.0.0',
  source         VARCHAR(24)  NOT NULL DEFAULT 'library',
  source_url     VARCHAR(512),
  source_repo    VARCHAR(191),
  license        VARCHAR(191) NOT NULL DEFAULT 'UNKNOWN',
  author         VARCHAR(191) NOT NULL DEFAULT '',
  author_kind    VARCHAR(16)  NOT NULL DEFAULT 'department',
  risk_score     INT          NOT NULL DEFAULT 0,
  risk_level     VARCHAR(16)  NOT NULL DEFAULT 'unrated',
  status         VARCHAR(16)  NOT NULL DEFAULT 'published',
  content        LONGTEXT,
  content_bytes  INT          NOT NULL DEFAULT 0,
  file_count     INT          NOT NULL DEFAULT 0,
  package_bytes  BIGINT       NOT NULL DEFAULT 0,
  content_sha256 VARCHAR(64)  NOT NULL DEFAULT '',
  download_count INT          NOT NULL DEFAULT 0,
  install_count  INT          NOT NULL DEFAULT 0,
  star_count     INT          NOT NULL DEFAULT 0,
  eval_count     INT          NOT NULL DEFAULT 0,
  avg_score      DECIMAL(6,2) NOT NULL DEFAULT 0,
  skillset_code  VARCHAR(64)  NOT NULL DEFAULT 'default',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_skill_skill_id (skill_id),
  KEY idx_skill_category (category_code),
  KEY idx_skill_status (status),
  KEY idx_skill_scene (scene_code),
  KEY idx_skill_risk (risk_level),
  KEY idx_skill_updated (updated_at),
  KEY idx_skill_skillset (skillset_code)
);

CREATE TABLE IF NOT EXISTS skill_file (
  id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
  skill_id   VARCHAR(191) NOT NULL,
  path       VARCHAR(512) NOT NULL,
  bytes      BIGINT       NOT NULL DEFAULT 0,
  sha256     VARCHAR(64)  NOT NULL DEFAULT '',
  is_primary BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_skill_file_skill (skill_id)
);

CREATE TABLE IF NOT EXISTS skill_version (
  id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
  skill_id   VARCHAR(191) NOT NULL,
  version    VARCHAR(48)  NOT NULL,
  content    LONGTEXT,
  notes      VARCHAR(512) NOT NULL DEFAULT '',
  size_bytes INT          NOT NULL DEFAULT 0,
  origin     VARCHAR(24)  NOT NULL DEFAULT 'library',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_skill_version_skill (skill_id),
  UNIQUE KEY uk_skill_version (skill_id, version)
);

CREATE TABLE IF NOT EXISTS risk_audit (
  id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
  skill_id        VARCHAR(191) NOT NULL,
  risk_score      INT          NOT NULL DEFAULT 0,
  risk_level      VARCHAR(16)  NOT NULL DEFAULT 'unrated',
  engine          VARCHAR(32)  NOT NULL DEFAULT 'rule-1.0',
  dimension_json  LONGTEXT,
  signal_total    INT          NOT NULL DEFAULT 0,
  summary         VARCHAR(512) NOT NULL DEFAULT '',
  conclusion      TEXT,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_risk_audit_skill (skill_id)
);

CREATE TABLE IF NOT EXISTS risk_signal (
  id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
  audit_id      BIGINT       NOT NULL,
  signal_code   VARCHAR(32)  NOT NULL,
  indicator     VARCHAR(191) NOT NULL DEFAULT '',
  dimension     VARCHAR(64)  NOT NULL DEFAULT '',
  severity      VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM',
  weight        INT          NOT NULL DEFAULT 0,
  file_path     VARCHAR(512) NOT NULL DEFAULT '',
  line_no       INT          NOT NULL DEFAULT 0,
  remediation   TEXT,
  KEY idx_risk_signal_audit (audit_id)
);

CREATE TABLE IF NOT EXISTS contributor (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  source      VARCHAR(24)  NOT NULL DEFAULT 'local',
  author      VARCHAR(191) NOT NULL,
  name        VARCHAR(191) NOT NULL DEFAULT '',
  kind        VARCHAR(16)  NOT NULL DEFAULT 'department',
  description TEXT,
  git_profile VARCHAR(512) NOT NULL DEFAULT '',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_contributor (source, author)
);

CREATE TABLE IF NOT EXISTS contribution (
  id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
  kind             VARCHAR(16)  NOT NULL DEFAULT 'department',
  contributor_name VARCHAR(191) NOT NULL,
  name             VARCHAR(191) NOT NULL,
  title            VARCHAR(191) NOT NULL DEFAULT '',
  version          VARCHAR(48)  NOT NULL DEFAULT '1.0.0',
  description      TEXT,
  source_type      VARCHAR(24)  NOT NULL DEFAULT 'upload',
  source_url       VARCHAR(512),
  storage_dir      VARCHAR(512) NOT NULL DEFAULT '',
  status           VARCHAR(16)  NOT NULL DEFAULT 'draft',
  cla_signed       BOOLEAN      NOT NULL DEFAULT FALSE,
  cla_signature    VARCHAR(191) NOT NULL DEFAULT '',
  cla_signed_at    DATETIME,
  skill_id         VARCHAR(191),
  risk_score       INT          NOT NULL DEFAULT 0,
  risk_level       VARCHAR(16)  NOT NULL DEFAULT 'unrated',
  notes            VARCHAR(1024) NOT NULL DEFAULT '',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_contribution_status (status),
  KEY idx_contribution_skill (skill_id)
);

CREATE TABLE IF NOT EXISTS doc_page (
  id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
  doc_key    VARCHAR(96)  NOT NULL,
  title      VARCHAR(191) NOT NULL,
  section    VARCHAR(64)  NOT NULL DEFAULT 'guide',
  summary    VARCHAR(512) NOT NULL DEFAULT '',
  body       LONGTEXT,
  sort_no    INT          NOT NULL DEFAULT 0,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_doc_page_key (doc_key)
);

CREATE TABLE IF NOT EXISTS op_log (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  actor       VARCHAR(96)  NOT NULL DEFAULT 'guest',
  role        VARCHAR(24)  NOT NULL DEFAULT 'viewer',
  action      VARCHAR(64)  NOT NULL,
  target_type VARCHAR(48)  NOT NULL DEFAULT '',
  target_id   VARCHAR(191) NOT NULL DEFAULT '',
  detail      TEXT,
  trace_id    VARCHAR(48)  NOT NULL DEFAULT '',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_op_log_created (created_at),
  KEY idx_op_log_action (action)
);

-- ============================================================================
-- Skill-MS 库语义 schema（SSKH / PSKH 共有）
-- 定义见 docs/02-统一数据模型与DDL.md §4
-- 可移植子集：H2 2.2.224 (MODE=MySQL) 与 MySQL 8.0.45 均已实测通过
-- ============================================================================

CREATE TABLE IF NOT EXISTS skillset (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(64)  NOT NULL,
  name        VARCHAR(191) NOT NULL,
  description VARCHAR(512) NOT NULL DEFAULT '',
  owner       VARCHAR(96)  NOT NULL DEFAULT 'guest',
  skill_count INT          NOT NULL DEFAULT 0,
  status      VARCHAR(16)  NOT NULL DEFAULT 'active',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_skillset_code (code)
);

CREATE TABLE IF NOT EXISTS ms_index_def (
  id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(64)  NOT NULL,
  name          VARCHAR(191) NOT NULL,
  skillset_code VARCHAR(64)  NOT NULL DEFAULT 'default',
  columns_col   VARCHAR(191) NOT NULL,
  index_type    VARCHAR(24)  NOT NULL DEFAULT 'BTREE',
  unique_flag   BOOLEAN      NOT NULL DEFAULT FALSE,
  status        VARCHAR(16)  NOT NULL DEFAULT 'BUILDING',
  entry_count   INT          NOT NULL DEFAULT 0,
  hit_count     BIGINT       NOT NULL DEFAULT 0,
  last_built_at DATETIME,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ms_index_code (code)
);

CREATE TABLE IF NOT EXISTS ms_index_entry (
  id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
  index_code VARCHAR(64)  NOT NULL,
  skill_id   VARCHAR(191) NOT NULL,
  key_value  VARCHAR(512) NOT NULL DEFAULT '',
  KEY idx_ms_index_entry_code (index_code),
  KEY idx_ms_index_entry_val (key_value)
);

CREATE TABLE IF NOT EXISTS ms_constraint_def (
  id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code             VARCHAR(64)  NOT NULL,
  name             VARCHAR(191) NOT NULL,
  skillset_code    VARCHAR(64)  NOT NULL DEFAULT 'default',
  constraint_type  VARCHAR(24)  NOT NULL,
  target_column    VARCHAR(96)  NOT NULL DEFAULT '',
  expression       VARCHAR(512) NOT NULL DEFAULT '',
  severity         VARCHAR(16)  NOT NULL DEFAULT 'ENFORCED',
  status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  violation_count  INT          NOT NULL DEFAULT 0,
  last_violated_at DATETIME,
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ms_constraint_code (code)
);

CREATE TABLE IF NOT EXISTS ms_event_def (
  id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code           VARCHAR(64)  NOT NULL,
  name           VARCHAR(191) NOT NULL,
  skillset_code  VARCHAR(64)  NOT NULL DEFAULT 'default',
  trigger_event  VARCHAR(32)  NOT NULL,
  condition_expr VARCHAR(512) NOT NULL DEFAULT '',
  action_type    VARCHAR(24)  NOT NULL,
  action_payload VARCHAR(1024) NOT NULL DEFAULT '',
  enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
  fired_count    BIGINT       NOT NULL DEFAULT 0,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ms_event_code (code)
);

CREATE TABLE IF NOT EXISTS event_log (
  id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
  event_code   VARCHAR(64)  NOT NULL,
  trigger_on   VARCHAR(32)  NOT NULL,
  skill_id     VARCHAR(191) NOT NULL DEFAULT '',
  action_type  VARCHAR(24)  NOT NULL,
  payload      VARCHAR(1024) NOT NULL DEFAULT '',
  status       VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  attempts     INT          NOT NULL DEFAULT 0,
  last_error   VARCHAR(1024) NOT NULL DEFAULT '',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_event_log_status (status),
  KEY idx_event_log_event (event_code)
);

CREATE TABLE IF NOT EXISTS ms_tx_log (
  id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
  tx_id           VARCHAR(48)  NOT NULL,
  op_type         VARCHAR(16)  NOT NULL,
  skill_id        VARCHAR(191) NOT NULL DEFAULT '',
  actor           VARCHAR(96)  NOT NULL DEFAULT 'guest',
  verdict         VARCHAR(16)  NOT NULL DEFAULT 'PASS',
  before_json     LONGTEXT,
  after_json      LONGTEXT,
  constraint_hits TEXT,
  index_touched   TEXT,
  events_fired    TEXT,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ms_tx_skill (skill_id),
  KEY idx_ms_tx_created (created_at)
);

CREATE TABLE IF NOT EXISTS agent_registry (
  id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(48)  NOT NULL,
  name          VARCHAR(191) NOT NULL,
  provider      VARCHAR(48)  NOT NULL DEFAULT 'openai-compatible',
  base_url      VARCHAR(512) NOT NULL DEFAULT '',
  model         VARCHAR(96)  NOT NULL DEFAULT '',
  api_key       VARCHAR(512) NOT NULL DEFAULT '',
  temperature   DECIMAL(4,2) NOT NULL DEFAULT 0.30,
  top_p         DECIMAL(4,2) NOT NULL DEFAULT 1.00,
  max_tokens    INT          NOT NULL DEFAULT 2048,
  enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
  status        VARCHAR(16)  NOT NULL DEFAULT 'unknown',
  last_probe_at DATETIME,
  last_probe_ms INT          NOT NULL DEFAULT 0,
  last_error    VARCHAR(1024) NOT NULL DEFAULT '',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_agent_registry_code (code)
);

CREATE TABLE IF NOT EXISTS mcp_registry (
  id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(48)  NOT NULL,
  name          VARCHAR(191) NOT NULL,
  transport     VARCHAR(24)  NOT NULL DEFAULT 'HTTP',
  endpoint      VARCHAR(512) NOT NULL DEFAULT '',
  tool_name     VARCHAR(96)  NOT NULL DEFAULT '',
  description   VARCHAR(512) NOT NULL DEFAULT '',
  headers_json  LONGTEXT,
  enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
  status        VARCHAR(16)  NOT NULL DEFAULT 'unknown',
  last_probe_at DATETIME,
  last_probe_ms INT          NOT NULL DEFAULT 0,
  last_error    VARCHAR(1024) NOT NULL DEFAULT '',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_mcp_registry_code (code)
);

CREATE TABLE IF NOT EXISTS eval_metric_def (
  id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(48)  NOT NULL,
  label         VARCHAR(96)  NOT NULL,
  dimension     VARCHAR(24)  NOT NULL DEFAULT 'EFFICACY',
  unit          VARCHAR(24)  NOT NULL DEFAULT 'score',
  higher_better BOOLEAN      NOT NULL DEFAULT TRUE,
  weight        DECIMAL(6,3) NOT NULL DEFAULT 1.000,
  formula       VARCHAR(512) NOT NULL DEFAULT '',
  description   TEXT,
  sort_no       INT          NOT NULL DEFAULT 0,
  UNIQUE KEY uk_eval_metric_code (code)
);

CREATE TABLE IF NOT EXISTS eval_run (
  id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code              VARCHAR(48)  NOT NULL,
  skill_id          VARCHAR(191) NOT NULL DEFAULT '',
  agent_code        VARCHAR(48)  NOT NULL DEFAULT '',
  mcp_code          VARCHAR(48)  NOT NULL DEFAULT '',
  params_json       LONGTEXT,
  scene_code        VARCHAR(24)  NOT NULL DEFAULT 'general',
  input_text        LONGTEXT,
  expected_text     LONGTEXT,
  output_text       LONGTEXT,
  reasoning_text    LONGTEXT,
  status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  latency_ms        INT          NOT NULL DEFAULT 0,
  ttft_ms           INT          NOT NULL DEFAULT 0,
  prompt_tokens     INT          NOT NULL DEFAULT 0,
  completion_tokens INT          NOT NULL DEFAULT 0,
  score_total       DECIMAL(6,2) NOT NULL DEFAULT 0,
  score_json        LONGTEXT,
  error             VARCHAR(1024) NOT NULL DEFAULT '',
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at       DATETIME,
  UNIQUE KEY uk_eval_run_code (code),
  KEY idx_eval_run_skill (skill_id),
  KEY idx_eval_run_status (status)
);

CREATE TABLE IF NOT EXISTS eval_score (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  run_id      BIGINT       NOT NULL,
  metric_code VARCHAR(48)  NOT NULL,
  metric_value DOUBLE      NOT NULL DEFAULT 0,
  weight      DOUBLE       NOT NULL DEFAULT 1,
  detail      VARCHAR(1024) NOT NULL DEFAULT '',
  KEY idx_eval_score_run (run_id)
);

-- ============================================================================
-- 专业版扩展 schema（仅 PSKH）：实验矩阵 / 知识图谱 / 分析报告
-- 定义见 docs/02-统一数据模型与DDL.md §5
-- 可移植子集：H2 2.2.224 (MODE=MySQL) 与 MySQL 8.0.45 均已实测通过
-- ============================================================================

CREATE TABLE IF NOT EXISTS mx_matrix (
  id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(48)  NOT NULL,
  name          VARCHAR(191) NOT NULL,
  matrix_type   VARCHAR(24)  NOT NULL,
  skill_ids     VARCHAR(1024) NOT NULL DEFAULT '',
  agent_codes   VARCHAR(512)  NOT NULL DEFAULT '',
  mcp_codes     VARCHAR(512)  NOT NULL DEFAULT '',
  param_grid    LONGTEXT,
  scene_code    VARCHAR(24)  NOT NULL DEFAULT 'general',
  case_json     LONGTEXT,
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  planned_cells INT          NOT NULL DEFAULT 0,
  done_cells    INT          NOT NULL DEFAULT 0,
  summary_json  LONGTEXT,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at   DATETIME,
  UNIQUE KEY uk_mx_matrix_code (code)
);

CREATE TABLE IF NOT EXISTS mx_cell (
  id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
  matrix_code    VARCHAR(48)  NOT NULL,
  skill_id       VARCHAR(191) NOT NULL DEFAULT '',
  agent_code     VARCHAR(48)  NOT NULL DEFAULT '',
  mcp_code       VARCHAR(48)  NOT NULL DEFAULT '',
  params_json    LONGTEXT,
  scene_code     VARCHAR(24)  NOT NULL DEFAULT 'general',
  case_code      VARCHAR(48)  NOT NULL DEFAULT '',
  input_text     LONGTEXT,
  expected_text  LONGTEXT,
  output_text    LONGTEXT,
  reasoning_text LONGTEXT,
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  latency_ms     INT          NOT NULL DEFAULT 0,
  ttft_ms        INT          NOT NULL DEFAULT 0,
  prompt_tokens  INT          NOT NULL DEFAULT 0,
  completion_tokens INT       NOT NULL DEFAULT 0,
  score_total    DECIMAL(6,2) NOT NULL DEFAULT 0,
  score_json     LONGTEXT,
  error          VARCHAR(1024) NOT NULL DEFAULT '',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_mx_cell_matrix (matrix_code),
  KEY idx_mx_cell_status (status)
);

CREATE TABLE IF NOT EXISTS kg_entity (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(96)  NOT NULL,
  entity_type VARCHAR(24)  NOT NULL,
  name        VARCHAR(191) NOT NULL,
  label       VARCHAR(191) NOT NULL DEFAULT '',
  weight      DOUBLE       NOT NULL DEFAULT 1,
  props_json  LONGTEXT,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_kg_entity_code (code),
  KEY idx_kg_entity_type (entity_type)
);

CREATE TABLE IF NOT EXISTS kg_relation (
  id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
  src_code      VARCHAR(96)  NOT NULL,
  dst_code      VARCHAR(96)  NOT NULL,
  relation_type VARCHAR(24)  NOT NULL,
  weight        DOUBLE       NOT NULL DEFAULT 1,
  evidence      VARCHAR(512) NOT NULL DEFAULT '',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_kg_relation (src_code, dst_code, relation_type),
  KEY idx_kg_relation_src (src_code),
  KEY idx_kg_relation_dst (dst_code)
);

CREATE TABLE IF NOT EXISTS kg_snapshot (
  id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code           VARCHAR(48)  NOT NULL,
  name           VARCHAR(191) NOT NULL,
  scope          VARCHAR(24)  NOT NULL DEFAULT 'GLOBAL',
  scene_code     VARCHAR(24)  NOT NULL DEFAULT 'general',
  entity_count   INT          NOT NULL DEFAULT 0,
  relation_count INT          NOT NULL DEFAULT 0,
  payload_json   LONGTEXT,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_kg_snapshot_code (code)
);

CREATE TABLE IF NOT EXISTS ix_report (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(48)  NOT NULL,
  title       VARCHAR(191) NOT NULL,
  method      VARCHAR(24)  NOT NULL,
  scope       VARCHAR(191) NOT NULL DEFAULT '',
  params_json LONGTEXT,
  result_json LONGTEXT,
  conclusion  TEXT,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ix_report_code (code)
);
