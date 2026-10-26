CREATE TABLE IF NOT EXISTS accounts (id BIGINT PRIMARY KEY, role VARCHAR(16) NOT NULL);

CREATE TABLE IF NOT EXISTS credentials (
 account_id BIGINT PRIMARY KEY, username VARCHAR(32) NOT NULL UNIQUE,
 display_name VARCHAR(40) NOT NULL, password_hash VARCHAR(100) NOT NULL,
 FOREIGN KEY (account_id) REFERENCES accounts(id)
);
CREATE TABLE IF NOT EXISTS login_sessions (
 token_id VARCHAR(36) PRIMARY KEY, account_id BIGINT NOT NULL,
 expires_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY (account_id) REFERENCES accounts(id)
);
CREATE TABLE IF NOT EXISTS pets (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL,
 name VARCHAR(80) NOT NULL, notes VARCHAR(1000) NOT NULL,
 FOREIGN KEY (owner_id) REFERENCES accounts(id)
);
CREATE TABLE IF NOT EXISTS slots (
 id VARCHAR(36) PRIMARY KEY, sitter_id BIGINT NOT NULL,
 starts_at TIMESTAMP(6) NOT NULL, ends_at TIMESTAMP(6) NOT NULL,
 price_cents INT NOT NULL CHECK(price_cents > 0), booked BOOLEAN NOT NULL DEFAULT FALSE,
 CHECK(ends_at > starts_at),
 FOREIGN KEY (sitter_id) REFERENCES accounts(id)
);
CREATE TABLE IF NOT EXISTS orders (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL,
 pet_id VARCHAR(36) NOT NULL, slot_id VARCHAR(36) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('PENDING','ACCEPTED','COMPLETED','CANCELLED')),
 request_key VARCHAR(100) NOT NULL, price_cents INT NOT NULL,
 UNIQUE(owner_id, request_key),
 FOREIGN KEY (owner_id) REFERENCES accounts(id),
 FOREIGN KEY (pet_id) REFERENCES pets(id),
 FOREIGN KEY (slot_id) REFERENCES slots(id)
);
CREATE TABLE IF NOT EXISTS service_records (
 id VARCHAR(36) PRIMARY KEY, order_id VARCHAR(36) NOT NULL,
 note VARCHAR(2000) NOT NULL, created_at TIMESTAMP(6) NOT NULL,
 FOREIGN KEY (order_id) REFERENCES orders(id)
);
