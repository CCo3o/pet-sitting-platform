CREATE TABLE service_catalog (
 id VARCHAR(36) PRIMARY KEY, name VARCHAR(100) NOT NULL,
 description VARCHAR(1000) NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO service_catalog VALUES ('daily-care','日常上门喂养','喂粮、换水、清理猫砂，记录宠物状态与服务照片。',TRUE);
ALTER TABLE orders ADD COLUMN service_snapshot VARCHAR(1200) NOT NULL DEFAULT '日常上门喂养：喂粮、换水、清理猫砂';
