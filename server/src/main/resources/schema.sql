-- ==========================================================
-- WeDelivery Database Schema Definition (v1.0)
-- 对应《系统架构与数据库设计说明书》第 4 节
-- ==========================================================

-- 1. 用户表 users
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(128) NOT NULL UNIQUE,
    first_name VARCHAR(64),
    last_name VARCHAR(64),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    vip_expire_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 分配站点表 stations
CREATE TABLE IF NOT EXISTS stations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    total_drone_bays INT NOT NULL DEFAULT 10,
    total_robot_bays INT NOT NULL DEFAULT 15,
    contact_phone VARCHAR(32)
);

-- 3. 配送载具表 vehicles
--    基础信息: vehicle_code / vehicle_type / max_weight / max_volume / cruise_speed / endurance_minutes
--    实时信息: status / battery_level / location_code / current_lat / current_lng / current_speed
--              + position_updated_at / status_updated_at / speed_updated_at / updated_at
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_id BIGINT NOT NULL,
    vehicle_code VARCHAR(32) NOT NULL UNIQUE,
    vehicle_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    battery_level DECIMAL(5, 2) NOT NULL DEFAULT 100.00,
    max_weight DECIMAL(5, 2) NOT NULL,
    max_volume DECIMAL(5, 2) NOT NULL,
    cruise_speed DECIMAL(5, 2) NOT NULL,
    endurance_minutes DECIMAL(6, 2) NOT NULL,
    -- 位置编码: 0=不在任何站点, 1/2/3=位于对应站点 id
    location_code INT NOT NULL DEFAULT 0,
    current_lat DECIMAL(10, 7),
    current_lng DECIMAL(10, 7),
    current_speed DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    position_updated_at DATETIME,
    status_updated_at DATETIME,
    speed_updated_at DATETIME,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicle_station FOREIGN KEY (station_id) REFERENCES stations (id)
);

-- 4. 配送订单表 orders
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    vehicle_id BIGINT,
    vehicle_type VARCHAR(20) NOT NULL,
    plan_type VARCHAR(20) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAYMENT',
    is_station_pickup BOOLEAN NOT NULL DEFAULT FALSE,
    pickup_address VARCHAR(255) NOT NULL,
    pickup_lat DECIMAL(10, 7) NOT NULL,
    pickup_lng DECIMAL(10, 7) NOT NULL,
    dropoff_address VARCHAR(255) NOT NULL,
    dropoff_lat DECIMAL(10, 7) NOT NULL,
    dropoff_lng DECIMAL(10, 7) NOT NULL,
    package_weight DECIMAL(5, 2) NOT NULL,
    package_volume DECIMAL(5, 2) NOT NULL,
    total_distance DECIMAL(8, 2) NOT NULL,
    origin_price DECIMAL(10, 2) NOT NULL,
    discount_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    final_price DECIMAL(10, 2) NOT NULL,
    scheduled_start_time DATETIME NOT NULL,
    estimated_delivery_time DATETIME NOT NULL,
    actual_delivery_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_order_station FOREIGN KEY (station_id) REFERENCES stations (id),
    CONSTRAINT fk_order_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id)
);

-- 5. 支付流水表 payments
CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    transaction_no VARCHAR(64) NOT NULL UNIQUE,
    payment_method VARCHAR(32) NOT NULL DEFAULT 'MOCK_CARD',
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(64),
    paid_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 6. 轨迹里程碑事件表 tracking_events
CREATE TABLE IF NOT EXISTS tracking_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    stage VARCHAR(32) NOT NULL,
    status_description VARCHAR(255) NOT NULL,
    event_lat DECIMAL(10, 7) NOT NULL,
    event_lng DECIMAL(10, 7) NOT NULL,
    event_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tracking_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
