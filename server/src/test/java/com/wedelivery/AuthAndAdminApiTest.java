package com.wedelivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证与运营大盘。大盘的五种状态计数同时校验了机器状态枚举与种子数据的完整性。
 *
 * 登录成功这条用例还顺带覆盖了 data.sql 里种子密码串是否可用
 * （原占位 hash 与 password123 不匹配会让登录直接 500）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("认证与运营大盘")
class AuthAndAdminApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("种子账号可用 password123 登录并拿到 token")
    void seededAccountsCanLogIn() throws Exception {
        assertThat(login("admin", "password123")).isNotBlank();
        assertThat(login("vip_user", "password123")).isNotBlank();
        assertThat(login("normal_user", "password123")).isNotBlank();
    }

    @Test
    @DisplayName("管理员登录后五类状态计数与种子数据一致")
    void adminDashboardCountsAllVehicleStatuses() throws Exception {
        String token = login("admin", "password123");

        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVehicles").value(15))
                // 11 待命 / 1 配送中 / 1 充电 / 1 故障 / 1 关机
                .andExpect(jsonPath("$.idleVehicles").value(11))
                .andExpect(jsonPath("$.busyVehicles").value(1))
                .andExpect(jsonPath("$.chargingVehicles").value(1))
                .andExpect(jsonPath("$.faultVehicles").value(1))
                .andExpect(jsonPath("$.offlineVehicles").value(1))
                // 大盘里站点也带上了容量与联系方式
                .andExpect(jsonPath("$.stations[0].maxCapacity").value(25))
                .andExpect(jsonPath("$.stations[0].contactPhone").value("(415) 555-0101"));
    }

    @Test
    @DisplayName("非管理员访问大盘返回 403，未带 token 返回 401/403")
    void dashboardRequiresAdminRole() throws Exception {
        String token = login("normal_user", "password123");

        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("智能推荐：availableUnits 反映真实可调度台数而非固定值")
    void recommendationReportsRealAvailableUnits() throws Exception {
        String token = login("normal_user", "password123");
        String request = "{"
                + "\"pickup\":{\"line1\":\"Downtown\",\"lat\":37.7891720,\"lng\":-122.3970420},"
                + "\"dropoff\":{\"line1\":\"Nearby\",\"lat\":37.7850000,\"lng\":-122.4100000},"
                + "\"package\":{\"description\":\"small parcel\",\"weightKg\":1.5},"
                + "\"priority\":\"STANDARD\"}";

        // 站点 1 有两台待命无人机（DRONE-DT-01、DRONE-DT-02）满足准入
        mvc.perform(post("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[?(@.vehicleType=='DRONE')].availableUnits")
                        .value(org.hamcrest.Matchers.hasItem(2)));

        // 让其中一台报故障，可调度台数应随之下降 —— 证明该值是算出来的
        mvc.perform(post("/api/dispatch/vehicles/DRONE-DT-01/telemetry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAULT\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[?(@.vehicleType=='DRONE')].availableUnits")
                        .value(org.hamcrest.Matchers.hasItem(1)));
    }

    private String login(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String json = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).path("token").asText();
    }
}
