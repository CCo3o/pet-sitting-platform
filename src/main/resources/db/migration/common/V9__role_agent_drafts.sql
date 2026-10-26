CREATE TABLE agent_action_drafts (
 id VARCHAR(36) PRIMARY KEY, actor_id BIGINT NOT NULL, session_id VARCHAR(36) NOT NULL,
 kind VARCHAR(24) NOT NULL, target_id VARCHAR(36) NOT NULL, content VARCHAR(1000) NOT NULL,
 expires_at TIMESTAMP NOT NULL, result_id VARCHAR(36),
 FOREIGN KEY (actor_id) REFERENCES accounts(id), FOREIGN KEY (session_id) REFERENCES agent_sessions(id)
);
