CREATE TABLE account_counter (id INT PRIMARY KEY, next_id BIGINT NOT NULL);
INSERT INTO account_counter SELECT 1, COALESCE(MAX(id),999) + 1000 FROM accounts;
ALTER TABLE pets ADD COLUMN species VARCHAR(16) NOT NULL DEFAULT 'CAT';
ALTER TABLE pets ADD COLUMN breed VARCHAR(80) NOT NULL DEFAULT '';
ALTER TABLE pets ADD COLUMN age_months INT NOT NULL DEFAULT 0;
ALTER TABLE pets ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE addresses (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL,
 contact VARCHAR(40) NOT NULL, phone VARCHAR(20) NOT NULL, region VARCHAR(80) NOT NULL,
 detail VARCHAR(200) NOT NULL, deleted BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY (owner_id) REFERENCES accounts(id)
);
CREATE TABLE sitter_profiles (
 account_id BIGINT PRIMARY KEY, bio VARCHAR(1000) NOT NULL,
 region VARCHAR(80) NOT NULL, price_cents INT NOT NULL, status VARCHAR(16) NOT NULL,
 review_note VARCHAR(500) NOT NULL DEFAULT '',
 FOREIGN KEY (account_id) REFERENCES accounts(id)
);
ALTER TABLE slots ADD COLUMN region VARCHAR(80) NOT NULL DEFAULT '示范区';
ALTER TABLE orders ADD COLUMN workflow_state VARCHAR(24) NOT NULL DEFAULT 'PENDING';
UPDATE orders SET workflow_state=status;
ALTER TABLE orders ADD COLUMN address_id VARCHAR(36);
ALTER TABLE orders ADD COLUMN address_snapshot VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE orders ADD COLUMN pet_snapshot VARCHAR(1200) NOT NULL DEFAULT '';
ALTER TABLE orders ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE orders ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE orders ADD COLUMN payment_state VARCHAR(16) NOT NULL DEFAULT 'UNPAID';
ALTER TABLE orders ADD COLUMN cancel_reason VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE service_records ADD COLUMN photos VARCHAR(2000) NOT NULL DEFAULT '[]';
CREATE TABLE reviews (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL UNIQUE,
 owner_id BIGINT NOT NULL, rating INT NOT NULL CHECK(rating BETWEEN 1 AND 5),
 content VARCHAR(1000) NOT NULL, created_at TIMESTAMP NOT NULL,
 FOREIGN KEY (order_id) REFERENCES orders(id),
 FOREIGN KEY (owner_id) REFERENCES accounts(id)
);
CREATE TABLE complaints (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL,
 reporter_id BIGINT NOT NULL, content VARCHAR(1000) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'OPEN', resolution VARCHAR(1000) NOT NULL DEFAULT '',
 created_at TIMESTAMP NOT NULL,
 FOREIGN KEY (order_id) REFERENCES orders(id),
 FOREIGN KEY (reporter_id) REFERENCES accounts(id)
);
CREATE TABLE media_files (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL,
 order_id VARCHAR(36) NOT NULL, file_name VARCHAR(100) NOT NULL,
 content_type VARCHAR(40) NOT NULL,
 FOREIGN KEY (owner_id) REFERENCES accounts(id),
 FOREIGN KEY (order_id) REFERENCES orders(id)
);
CREATE TABLE agent_sessions (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL,
 history TEXT NOT NULL, updated_at TIMESTAMP NOT NULL,
 FOREIGN KEY (owner_id) REFERENCES accounts(id)
);
CREATE TABLE booking_drafts (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL,
 session_id VARCHAR(36) NOT NULL, pet_id VARCHAR(36) NOT NULL,
 slot_id VARCHAR(36) NOT NULL, address_id VARCHAR(36) NOT NULL, price_cents INT NOT NULL,
 expires_at TIMESTAMP NOT NULL, order_id VARCHAR(36),
 FOREIGN KEY (owner_id) REFERENCES accounts(id),
 FOREIGN KEY (session_id) REFERENCES agent_sessions(id)
);
CREATE TABLE agent_runs (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL,
 session_id VARCHAR(36) NOT NULL, outcome VARCHAR(24) NOT NULL, duration_ms BIGINT NOT NULL,
 input_tokens BIGINT NOT NULL DEFAULT 0, output_tokens BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL,
 FOREIGN KEY (owner_id) REFERENCES accounts(id)
);
CREATE INDEX idx_slots_sitter_time ON slots(sitter_id,starts_at,ends_at);
CREATE INDEX idx_orders_owner ON orders(owner_id,created_at);
CREATE INDEX idx_orders_state ON orders(workflow_state,created_at);
CREATE INDEX idx_sessions_expiry ON login_sessions(expires_at);
