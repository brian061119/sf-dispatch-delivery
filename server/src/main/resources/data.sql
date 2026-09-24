-- ==========================================================
-- WeDelivery Seed Data Initialization
-- 对应《系统架构与数据库设计说明书》第 4.3 节
-- ==========================================================

-- 1. 初始化 3 个旧金山核心分配站 (Distribution Centers)
MERGE INTO stations KEY(id) VALUES 
(1, 'Station 1 - SF Downtown Hub', '500 Howard St, San Francisco, CA 94105', 37.7891720, -122.3970420, 10, 15, '(415) 555-0101'),
(2, 'Station 2 - Sunset District Hub', '1900 Irving St, San Francisco, CA 94122', 37.7638420, -122.4789120, 8, 12, '(415) 555-0102'),
(3, 'Station 3 - Mission District Hub', '2400 Mission St, San Francisco, CA 94110', 37.7588920, -122.4191340, 10, 15, '(415) 555-0103');

-- 2. 初始化各站点示例载具 (Drones & Robots)
MERGE INTO vehicles KEY(vehicle_code) VALUES 
(1, 1, 'DRONE-DT-01', 'DRONE', 'IDLE', 100.00, 3.00, 0.05, 45.00, CURRENT_TIMESTAMP),
(2, 1, 'DRONE-DT-02', 'DRONE', 'IDLE', 95.00, 3.00, 0.05, 45.00, CURRENT_TIMESTAMP),
(3, 1, 'ROBOT-DT-01', 'ROBOT', 'IDLE', 100.00, 15.00, 0.30, 15.00, CURRENT_TIMESTAMP),
(4, 1, 'ROBOT-DT-02', 'ROBOT', 'IDLE', 88.00, 15.00, 0.30, 15.00, CURRENT_TIMESTAMP),
(5, 2, 'DRONE-SS-01', 'DRONE', 'IDLE', 100.00, 3.00, 0.05, 45.00, CURRENT_TIMESTAMP),
(6, 2, 'ROBOT-SS-01', 'ROBOT', 'IDLE', 92.00, 15.00, 0.30, 15.00, CURRENT_TIMESTAMP),
(7, 3, 'DRONE-MS-01', 'DRONE', 'IDLE', 100.00, 3.00, 0.05, 45.00, CURRENT_TIMESTAMP),
(8, 3, 'ROBOT-MS-01', 'ROBOT', 'IDLE', 96.00, 15.00, 0.30, 15.00, CURRENT_TIMESTAMP);

-- 3. 初始化测试账号 (密码均为 raw: password123, 使用 BCrypt 编码)
MERGE INTO users KEY(username) VALUES 
(1, 'admin', '$2a$10$78Kx0iE/K71rXk6oA2lQfeqG7q6L3C8WpB5o0M9v4bWzYw1mN5Y7G', 'admin@wedelivery.com', 'System', 'Admin', 'ADMIN', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'vip_user', '$2a$10$78Kx0iE/K71rXk6oA2lQfeqG7q6L3C8WpB5o0M9v4bWzYw1mN5Y7G', 'vip@gmail.com', 'Alice', 'Smith', 'VIP', '2027-12-31 23:59:59', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'normal_user', '$2a$10$78Kx0iE/K71rXk6oA2lQfeqG7q6L3C8WpB5o0M9v4bWzYw1mN5Y7G', 'bob@gmail.com', 'Bob', 'Jones', 'USER', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
