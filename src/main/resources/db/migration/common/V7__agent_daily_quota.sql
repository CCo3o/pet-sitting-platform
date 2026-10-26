CREATE TABLE agent_quotas (
 owner_id BIGINT NOT NULL,
 quota_date DATE NOT NULL,
 calls INT NOT NULL,
 PRIMARY KEY(owner_id,quota_date),
 FOREIGN KEY(owner_id) REFERENCES accounts(id)
);
