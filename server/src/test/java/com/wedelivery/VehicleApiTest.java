package com.wedelivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 需求 3（机器基础信息）与需求 4（机器实时信息）的只读查询。全部只读。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("需求3/4 · 机器基础信息与实时信息")
class VehicleApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("需求3：编号、类型、载荷、默认最大速度、续航与派生的可配送路程")
    void exposesVehicleBasicInfo() throws Exception {
        mvc.perform(get("/api/vehicles/DRONE-DT-01/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleCode").value("DRONE-DT-01"))
                .andExpect(jsonPath("$.vehicleType").value("DRONE"))
                .andExpect(jsonPath("$.vehicleTypeLabel").value("无人机"))
                .andExpect(jsonPath("$.stationId").value(1))
                .andExpect(jsonPath("$.maxWeight").value(3.0))
                .andExpect(jsonPath("$.maxVolume").value(0.05))
                .andExpect(jsonPath("$.cruiseSpeed").value(45.0))
                .andExpect(jsonPath("$.enduranceMinutes").value(30.0))
                // 30 分钟 ÷ 60 × 45 km/h = 22.5 km
                .andExpect(jsonPath("$.maxDeliverableDistanceKm").value(22.5));
    }

    @Test
    @DisplayName("需求3：全量机器基础信息共 15 台")
    void listsAllVehicles() throws Exception {
        mvc.perform(get("/api/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(15)));
    }

    @Test
    @DisplayName("需求4：位置编码、经纬度、状态、状态中文名与三个独立更新时间")
    void exposesVehicleRealtimeInfo() throws Exception {
        mvc.perform(get("/api/vehicles/DRONE-DT-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"))
                .andExpect(jsonPath("$.statusLabel").value("待命"))
                .andExpect(jsonPath("$.locationCode").value(1))
                .andExpect(jsonPath("$.locationLabel").value("Station 1 - SF Downtown Hub"))
                .andExpect(jsonPath("$.currentLat").value(37.7891720))
                .andExpect(jsonPath("$.currentLng").value(-122.3970420))
                .andExpect(jsonPath("$.currentSpeed").value(0.0))
                .andExpect(jsonPath("$.batteryLevel").value(100.0))
                // 位置 / 状态 / 速度各自的更新时间，外加兜底的 updatedAt
                .andExpect(jsonPath("$.positionUpdatedAt").exists())
                .andExpect(jsonPath("$.statusUpdatedAt").exists())
                .andExpect(jsonPath("$.speedUpdatedAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("需求4：配送途中的机器位置编码为 0、状态为配送中且带当前速度")
    void exposesInDeliveryVehicle() throws Exception {
        mvc.perform(get("/api/vehicles/ROBOT-SS-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_DELIVERY"))
                .andExpect(jsonPath("$.statusLabel").value("配送中"))
                .andExpect(jsonPath("$.locationCode").value(0))
                .andExpect(jsonPath("$.locationLabel").value("不在任何站点"))
                .andExpect(jsonPath("$.currentSpeed").value(14.4));
    }

    @Test
    @DisplayName("需求4：五种状态枚举中文标签均可序列化")
    void allFiveStatusLabelsAreExposed() throws Exception {
        mvc.perform(get("/api/vehicles/DRONE-DT-03")).andExpect(jsonPath("$.statusLabel").value("充电"));
        mvc.perform(get("/api/vehicles/ROBOT-DT-03")).andExpect(jsonPath("$.statusLabel").value("故障"));
        mvc.perform(get("/api/vehicles/ROBOT-DT-04")).andExpect(jsonPath("$.statusLabel").value("关机"));
    }

    @Test
    @DisplayName("兼容端点 GET /api/dispatch/vehicles 返回全部 15 台实时信息")
    void dispatchListEndpointReturnsAll() throws Exception {
        mvc.perform(get("/api/dispatch/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(15)));
    }

    @Test
    @DisplayName("机器只读接口无需登录，未知编号返回 404")
    void accessRulesAndNotFound() throws Exception {
        mvc.perform(get("/api/vehicles/NO-SUCH-VEHICLE")).andExpect(status().isNotFound());
        mvc.perform(get("/api/vehicles/NO-SUCH-VEHICLE/info")).andExpect(status().isNotFound());
        mvc.perform(get("/api/dispatch/vehicles/NO-SUCH-VEHICLE")).andExpect(status().isNotFound());
    }
}
