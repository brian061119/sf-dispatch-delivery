package com.wedelivery;

import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接入方向：机器心跳（状态/电量/速度）与地图位置（经纬度 → 位置编码推导）。
 *
 * 本类会写库，故标注 {@link Transactional}：每个用例结束后回滚，
 * 保证种子数据在其余用例眼中始终是干净的。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("接入 · 机器心跳与地图位置")
class MachineAccessApiTest {

    private static final String TELEMETRY = "/api/dispatch/vehicles/%s/telemetry";
    private static final String LOCATION = "/api/dispatch/vehicles/%s/location";

    private static final String DRONE = "DRONE-DT-01";
    /** 站点 1 的坐标，用于验证「落在站点内」 */
    private static final String STATION_1_COORDS = "{\"latitude\":37.7891720,\"longitude\":-122.3970420}";
    /** 远离任何站点的坐标，用于验证「不在任何站点」 */
    private static final String NOWHERE_COORDS = "{\"latitude\":37.7000000,\"longitude\":-122.5000000}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    @DisplayName("机器接入上报故障：状态与状态更新时间同步落库")
    void telemetryUpdatesStatusAndTimestamp() throws Exception {
        LocalDateTime before = LocalDateTime.now();

        mvc.perform(post(String.format(TELEMETRY, DRONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAULT\",\"batteryLevel\":50.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAULT"))
                .andExpect(jsonPath("$.statusLabel").value("故障"))
                .andExpect(jsonPath("$.batteryLevel").value(50.5));

        Vehicle v = requireVehicle(DRONE);
        assertThat(v.getStatus()).isEqualTo(VehicleStatus.FAULT);
        assertThat(v.getStatusUpdatedAt()).isNotNull();
        assertThat(v.getStatusUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("地图接入上报坐标：落在站点内位置编码为站点号，远离则为 0")
    void mapReportDerivesLocationCode() throws Exception {
        mvc.perform(post(String.format(LOCATION, DRONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STATION_1_COORDS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationCode").value(1));

        LocalDateTime before = LocalDateTime.now();

        mvc.perform(post(String.format(LOCATION, DRONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOWHERE_COORDS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationCode").value(0))
                .andExpect(jsonPath("$.locationLabel").value("不在任何站点"));

        Vehicle v = requireVehicle(DRONE);
        assertThat(v.getLocationCode()).isEqualTo(Vehicle.LOCATION_NOT_AT_STATION);
        assertThat(v.getPositionUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("非配送状态上报速度会被归零，避免出现「待命但高速行驶」的脏数据")
    void speedIsZeroedWhenNotInDelivery() throws Exception {
        mvc.perform(post(String.format(TELEMETRY, DRONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IDLE\",\"currentSpeed\":12.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSpeed").value(0.0));
    }

    @Test
    @DisplayName("配送中上报的速度会被保留")
    void speedIsKeptWhenInDelivery() throws Exception {
        mvc.perform(post(String.format(TELEMETRY, DRONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_DELIVERY\",\"currentSpeed\":33.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSpeed").value(33.0));
    }

    @Test
    @DisplayName("上报故障后该机从站点可调度数中掉出，但归属数量不变")
    void faultRemovesVehicleFromDispatchableCount() throws Exception {
        mvc.perform(get("/api/stations/1/availability"))
                .andExpect(jsonPath("$.droneUnitsAvailable").value(2));

        mvc.perform(post(String.format(TELEMETRY, DRONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAULT\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/stations/1/availability"))
                .andExpect(jsonPath("$.droneUnitsAvailable").value(1))
                .andExpect(jsonPath("$.droneCount").value(3));
    }

    @Test
    @DisplayName("越界或缺失的入参返回 400")
    void rejectsInvalidPayloads() throws Exception {
        mvc.perform(post(String.format(TELEMETRY, DRONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batteryLevel\":150}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(String.format(TELEMETRY, DRONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentSpeed\":-3}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(String.format(LOCATION, DRONE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longitude\":-122.0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("未知编号上报返回 404")
    void unknownVehicleReturns404() throws Exception {
        mvc.perform(post(String.format(TELEMETRY, "NO-SUCH-VEHICLE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IDLE\"}"))
                .andExpect(status().isNotFound());
    }

    private Vehicle requireVehicle(String code) {
        return vehicleRepository.findByVehicleCode(code).orElseThrow(AssertionError::new);
    }
}
