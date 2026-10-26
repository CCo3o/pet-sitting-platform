CREATE TABLE agent_tool_traces (
 id VARCHAR(36) PRIMARY KEY,
 run_id VARCHAR(36) NOT NULL,
 sequence_no INT NOT NULL,
 tool_name VARCHAR(64) NOT NULL,
 arguments_json TEXT NOT NULL,
 result_json TEXT NOT NULL,
 outcome VARCHAR(16) NOT NULL,
 error_code VARCHAR(64) NOT NULL DEFAULT '',
 duration_ms BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL,
 FOREIGN KEY (run_id) REFERENCES agent_runs(id),
 UNIQUE (run_id, sequence_no)
);
CREATE INDEX idx_agent_runs_session ON agent_runs(session_id,created_at);
ALTER TABLE agent_runs ADD COLUMN error_code VARCHAR(64) NOT NULL DEFAULT '';
