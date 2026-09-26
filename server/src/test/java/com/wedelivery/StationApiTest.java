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
 * 需求 1（站点基础信息）与需求 2（站点实时信息）。全部只读。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("需求1/2 · 站点基础信息与实时信息")
class StationApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("需求1：名字、编号、经纬度、地址、电话、最大容量齐全")
    void listsStationBasicInfo() throws Exception {
        mvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].stationId").value(1))
                .andExpect(jsonPath("$[0].stationCode").value("1"))
                .andExpect(jsonPath("$[0].name").value("Station 1 - SF Downtown Hub"))
                .andExpect(jsonPath("$[0].address").value("500 Howard St, San Francisco, CA 94105"))
                .andExpect(jsonPath("$[0].latitude").value(37.7891720))
                .andExpect(jsonPath("$[0].longitude").value(-122.3970420))
                .andExpect(jsonPath("$[0].contactPhone").value("(415) 555-0101"))
                .andExpect(jsonPath("$[0].totalDroneBays").value(10))
                .andExpect(jsonPath("$[0].totalRobotBays").value(15))
                // 最大容量为「无人机坪位 + 机器人泊位」派生值，无独立列
                .andExpect(jsonPath("$[0].maxCapacity").value(25))
                .andExpect(jsonPath("$[1].maxCapacity").value(20))
                .andExpect(jsonPath("$[2].maxCapacity").value(25));
    }

    @Test
    @DisplayName("需求2：实时数量、可调度数与 available 标记")
    void reportsStationAvailability() throws Exception {
        mvc.perform(get("/api/stations/1/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stationId").value(1))
                .andExpect(jsonPath("$.name").value("Station 1 - SF Downtown Hub"))
                .andExpect(jsonPath("$.maxCapacity").value(25))
                // 归属本站的机器：3 台无人机、4 台机器人
                .andExpect(jsonPath("$.droneCount").value(3))
                .andExpect(jsonPath("$.robotCount").value(4))
                // 可调度需同时满足 IDLE 且物理停驻本站（CHARGING / FAULT / OFFLINE 不计入）
                .andExpect(jsonPath("$.droneUnitsAvailable").value(2))
                .andExpect(jsonPath("$.robotUnitsAvailable").value(2))
                .andExpect(jsonPath("$.available").value(true))
                // 在场 7 台，剩余容量 25 - 7
                .andExpect(jsonPath("$.onSiteCount").value(7))
                .andExpect(jsonPath("$.capacityRemaining").value(18))
                // 站级续航半径 = 可调度载具「续航 ÷ 60 × 最大速度」的最大值
                .andExpect(jsonPath("$.maxDroneRangeKm").value(22.5))
                .andExpect(jsonPath("$.maxRobotRangeKm").value(60.0));
    }

    @Test
    @DisplayName("附带：全量站点实时信息接口可用")
    void listsAllStationAvailability() throws Exception {
        mvc.perform(get("/api/dispatch/stations/realtime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("不存在的站点返回 404 而非 500")
    void unknownStationReturns404() throws Exception {
        mvc.perform(get("/api/stations/999/availability"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("站点只读接口无需登录（供订单系统内部调用）")
    void stationEndpointIsPermitAll() throws Exception {
        mvc.perform(get("/api/stations")).andExpect(status().isOk());
        mvc.perform(get("/api/stations/2/availability")).andExpect(status().isOk());
    }
}
