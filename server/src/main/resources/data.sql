-- ==========================================================
-- WeDelivery Seed Data Initialization
-- 对应《系统架构与数据库设计说明书》第 4.3 节
-- 全部使用 MERGE 幂等写入（H2 内存库每次启动重建，可安全重复执行）
-- ==========================================================

-- 1. 初始化 3 个旧金山核心分配站 (Distribution Centers)
--    站点编号即 id；最大容量由 total_drone_bays + total_robot_bays 派生
MERGE INTO stations KEY(id) VALUES
(1, 'Station 1 - SF Downtown Hub', '500 Howard St, San Francisco, CA 94105', 37.7891720, -122.3970420, 10, 15, '(415) 555-0101'),
(2, 'Station 2 - Sunset District Hub', '1900 Irving St, San Francisco, CA 94122', 37.7638420, -122.4789120, 8, 12, '(415) 555-0102'),
(3, 'Station 3 - Mission District Hub', '2400 Mission St, San Francisco, CA 94110', 37.7588920, -122.4191340, 10, 15, '(415) 555-0103');

-- 2. 初始化各站点示例载具 (Drones & Robots)
--    基础信息: vehicle_type / max_weight / max_volume / cruise_speed / endurance_minutes
--    实时信息: status / battery_level / location_code(0=不在站点, 1/2/3=站点 id) /
--              current_lat / current_lng / current_speed / *_updated_at
--    覆盖全部 5 种状态与 location_code=0 的站外场景，便于 demo 展示
MERGE INTO vehicles (
    id, station_id, vehicle_code, vehicle_type, status, battery_level,
    max_weight, max_volume, cruise_speed, endurance_minutes,
    location_code, current_lat, current_lng, current_speed,
    position_updated_at, status_updated_at, speed_updated_at, updated_at
) KEY(vehicle_code) VALUES
-- 站点 1 - Downtown：含短续航无人机(DRONE-DT-02, 仅 14 分钟续航 -> 10.5km)便于演示续航里程准入
(1,  1, 'DRONE-DT-01', 'DRONE', 'IDLE',       100.00, 3.00,  0.05, 45.00, 30.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2,  1, 'DRONE-DT-02', 'DRONE', 'IDLE',        92.00, 3.00,  0.05, 45.00, 14.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3,  1, 'DRONE-DT-03', 'DRONE', 'CHARGING',    46.00, 3.00,  0.05, 45.00, 30.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(4,  1, 'ROBOT-DT-01', 'ROBOT', 'IDLE',       100.00, 15.00, 0.30, 15.00, 240.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(5,  1, 'ROBOT-DT-02', 'ROBOT', 'IDLE',        88.00, 15.00, 0.30, 15.00, 240.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(6,  1, 'ROBOT-DT-03', 'ROBOT', 'FAULT',       73.00, 15.00, 0.30, 15.00, 240.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(7,  1, 'ROBOT-DT-04', 'ROBOT', 'OFFLINE',     12.00, 15.00, 0.30, 15.00, 240.00, 1, 37.7891720, -122.3970420,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- 站点 2 - Sunset
(8,  2, 'DRONE-SS-01', 'DRONE', 'IDLE',       100.00, 3.00,  0.05, 45.00, 32.00, 2, 37.7638420, -122.4789120,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(9,  2, 'DRONE-SS-02', 'DRONE', 'IDLE',        97.00, 3.00,  0.05, 45.00, 30.00, 2, 37.7638420, -122.4789120,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10, 2, 'ROBOT-SS-01', 'ROBOT', 'IDLE',        92.00, 15.00, 0.30, 15.00, 260.00, 2, 37.7638420, -122.4789120,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- 配送途中示例：location_code=0(不在任何站点)，带实时经纬度与速度
(11, 2, 'ROBOT-SS-02', 'ROBOT', 'IN_DELIVERY', 81.00, 15.00, 0.30, 15.00, 240.00, 0, 37.7701100, -122.4500800, 14.40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
-- 站点 3 - Mission
(12, 3, 'DRONE-MS-01', 'DRONE', 'IDLE',       100.00, 3.00,  0.05, 45.00, 30.00, 3, 37.7588920, -122.4191340,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(13, 3, 'DRONE-MS-02', 'DRONE', 'IDLE',        99.00, 3.00,  0.05, 45.00, 30.00, 3, 37.7588920, -122.4191340,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(14, 3, 'ROBOT-MS-01', 'ROBOT', 'IDLE',        96.00, 15.00, 0.30, 15.00, 240.00, 3, 37.7588920, -122.4191340,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(15, 3, 'ROBOT-MS-02', 'ROBOT', 'IDLE',        90.00, 15.00, 0.30, 15.00, 220.00, 3, 37.7588920, -122.4191340,  0.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 3. 初始化测试账号 (密码均为 raw: password123, 使用 BCrypt 编码)
--    原占位 hash 与 password123 并不匹配，会导致 /api/auth/login 报 500，此处已换为可验证的真实 BCrypt 串
MERGE INTO users KEY(username) VALUES
(1, 'admin', '$2a$10$uMxiKAqMJD7mZiKuOtKKqORd1wU.2XnnXu3iZP0BQNYho9G9nxUbC', 'admin@wedelivery.com', 'System', 'Admin', 'ADMIN', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 'vip_user', '$2a$10$uMxiKAqMJD7mZiKuOtKKqORd1wU.2XnnXu3iZP0BQNYho9G9nxUbC', 'vip@gmail.com', 'Alice', 'Smith', 'VIP', '2027-12-31 23:59:59', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(3, 'normal_user', '$2a$10$uMxiKAqMJD7mZiKuOtKKqORd1wU.2XnnXu3iZP0BQNYho9G9nxUbC', 'bob@gmail.com', 'Bob', 'Jones', 'USER', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
