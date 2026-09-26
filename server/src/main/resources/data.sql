-- ==========================================================
-- WeDelivery Seed Data Initialization
-- 对应《系统架构与数据库设计说明书》第 4.3 节
-- 使用 ON CONFLICT DO NOTHING 以同时兼容 PostgreSQL (AWS RDS) 与 H2 (MODE=PostgreSQL)，
-- 重复启动时已存在的唯一键记录会被跳过，不会报错。
-- 注意: 不显式插入 id —— PostgreSQL 的 IDENTITY 序列不会因显式 id 而前进，
-- 否则之后新注册用户/新站点会发生主键冲突。载具通过站点名称关联 station_id。
-- ==========================================================

-- 1. 初始化 3 个旧金山核心分配站 (Distribution Centers)
--    最大容量由 total_drone_bays + total_robot_bays 派生
-- 插入顺序决定 id: Station 1 -> 1, Station 2 -> 2, Station 3 -> 3 (OrderController 默认使用 stationId=1)
INSERT INTO stations (name, address, latitude, longitude, total_drone_bays, total_robot_bays, contact_phone) VALUES
('Station 1 - SF Downtown Hub', '500 Howard St, San Francisco, CA 94105', 37.7891720, -122.3970420, 10, 15, '(415) 555-0101')
ON CONFLICT DO NOTHING;
INSERT INTO stations (name, address, latitude, longitude, total_drone_bays, total_robot_bays, contact_phone) VALUES
('Station 2 - Sunset District Hub', '1900 Irving St, San Francisco, CA 94122', 37.7638420, -122.4789120, 8, 12, '(415) 555-0102')
ON CONFLICT DO NOTHING;
INSERT INTO stations (name, address, latitude, longitude, total_drone_bays, total_robot_bays, contact_phone) VALUES
('Station 3 - Mission District Hub', '2400 Mission St, San Francisco, CA 94110', 37.7588920, -122.4191340, 10, 15, '(415) 555-0103')
ON CONFLICT DO NOTHING;

-- 2. 初始化各站点示例载具 (Drones & Robots)
--    基础信息: vehicle_type / max_weight / max_volume / cruise_speed / endurance_minutes
--    实时信息: status / battery_level / location_code(0=不在站点, 1/2/3=站点 id) /
--              current_lat / current_lng / current_speed / *_updated_at
--    覆盖全部 5 种状态与 location_code=0 的站外场景，便于 demo 展示
INSERT INTO vehicles (
    station_id, vehicle_code, vehicle_type, status, battery_level,
    max_weight, max_volume, cruise_speed, endurance_minutes,
    location_code, current_lat, current_lng, current_speed,
    position_updated_at, status_updated_at, speed_updated_at, updated_at
) VALUES
-- 站点 1 - Downtown：含短续航无人机(DRONE-DT-02, 仅 14 分钟续航 -> 10.5km)便于演示续航里程准入
((SELECT id FROM stations WHERE name = 'Station 1 - SF Downtown Hub'), 'DRONE-DT-01', 'DRONE', 'IDLE',       100.00, 3.00,  0.05, 45.00, 30.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 1 - SF Downtown Hub'), 'DRONE-DT-02', 'DRONE', 'IDLE',        92.00, 3.00,  0.05, 45.00, 14.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 1 - SF Downtown Hub'), 'DRONE-DT-03', 'DRONE', 'CHARGING',    46.00, 3.00,  0.05, 45.00, 30.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 1 - SF Downtown Hub'), 'ROBOT-DT-01', 'ROBOT', 'IDLE',       100.00, 15.00, 0.30, 15.00, 240.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 1 - SF Downtown Hub'), 'ROBOT-DT-02', 'ROBOT', 'IDLE',        88.00, 15.00, 0.30, 15.00, 240.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 1 - SF Downtown Hub'), 'ROBOT-DT-03', 'ROBOT', 'FAULT',       73.00, 15.00, 0.30, 15.00, 240.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 1 - SF Downtown Hub'), 'ROBOT-DT-04', 'ROBOT', 'OFFLINE',     12.00, 15.00, 0.30, 15.00, 240.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- 站点 2 - Sunset
((SELECT id FROM stations WHERE name = 'Station 2 - Sunset District Hub'), 'DRONE-SS-01', 'DRONE', 'IDLE',       100.00, 3.00,  0.05, 45.00, 32.00, 2, 37.7638420, -122.4789120,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 2 - Sunset District Hub'), 'DRONE-SS-02', 'DRONE', 'IDLE',        97.00, 3.00,  0.05, 45.00, 30.00, 2, 37.7638420, -122.4789120,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 2 - Sunset District Hub'), 'ROBOT-SS-01', 'ROBOT', 'IDLE',        92.00, 15.00, 0.30, 15.00, 260.00, 2, 37.7638420, -122.4789120,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- 配送途中示例：location_code=0(不在任何站点)，带实时经纬度与速度
((SELECT id FROM stations WHERE name = 'Station 2 - Sunset District Hub'), 'ROBOT-SS-02', 'ROBOT', 'IN_DELIVERY', 81.00, 15.00, 0.30, 15.00, 240.00, 0, 37.7701100, -122.4500800, 14.40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- 站点 3 - Mission
((SELECT id FROM stations WHERE name = 'Station 3 - Mission District Hub'), 'DRONE-MS-01', 'DRONE', 'IDLE',       100.00, 3.00,  0.05, 45.00, 30.00, 3, 37.7588920, -122.4191340,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 3 - Mission District Hub'), 'DRONE-MS-02', 'DRONE', 'IDLE',        99.00, 3.00,  0.05, 45.00, 30.00, 3, 37.7588920, -122.4191340,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 3 - Mission District Hub'), 'ROBOT-MS-01', 'ROBOT', 'IDLE',        96.00, 15.00, 0.30, 15.00, 240.00, 3, 37.7588920, -122.4191340,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
((SELECT id FROM stations WHERE name = 'Station 3 - Mission District Hub'), 'ROBOT-MS-02', 'ROBOT', 'IDLE',        90.00, 15.00, 0.30, 15.00, 220.00, 3, 37.7588920, -122.4191340,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- 3. 初始化测试账号 (密码均为 raw: password123, 使用 BCrypt 编码)
INSERT INTO users (username, password_hash, email, first_name, last_name, role, vip_expire_at, created_at, updated_at) VALUES
('admin', '$2a$10$WnTqgnUTouER2/TCOfh5fOnZFqS3vS9ykd20DA30qJ3.0YdLT5FF2', 'admin@wedelivery.com', 'System', 'Admin', 'ADMIN', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('vip_user', '$2a$10$WnTqgnUTouER2/TCOfh5fOnZFqS3vS9ykd20DA30qJ3.0YdLT5FF2', 'vip@gmail.com', 'Alice', 'Smith', 'VIP', '2027-12-31 23:59:59', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('normal_user', '$2a$10$WnTqgnUTouER2/TCOfh5fOnZFqS3vS9ykd20DA30qJ3.0YdLT5FF2', 'bob@gmail.com', 'Bob', 'Jones', 'USER', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
