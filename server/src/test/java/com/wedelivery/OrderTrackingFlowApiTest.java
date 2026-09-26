package com.wedelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端：下单锁车 → 载具转「配送中」→ 追踪坐标与机器实时坐标一致。
 *
 * 这条链路横跨订单、追踪与调动三个模块，正是本模块对 OrderService / TrackingService
 * 那两处连带改动的关键验收点。写库，故整类回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("端到端 · 下单与追踪同步")
class OrderTrackingFlowApiTest {

    private static final String NEW_ORDER = "{"
            + "\"candidateId\":\"CAND-BEST_VALUE\","
            + "\"pickup\":{\"line1\":\"Downtown\",\"lat\":37.7891720,\"lng\":-122.3970420},"
            + "\"dropoff\":{\"line1\":\"Mission\",\"lat\":37.7596000,\"lng\":-122.4269000},"
            + "\"package\":{\"description\":\"books\",\"weightKg\":1.5},"
            + "\"priority\":\"STANDARD\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("下单后载具转入配送中、离开站点并带上速度与状态时间戳")
    void checkoutLocksVehicleAndMarksInDelivery() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);

        JsonNode order = getJson("/api/orders/" + orderNumber, token);
        long vehicleId = order.path("vehicleId").asLong();
        assertThat(vehicleId).isPositive();

        JsonNode vehicle = findVehicleById(vehicleId);
        assertThat(vehicle.path("status").asText()).isEqualTo("IN_DELIVERY");
        assertThat(vehicle.path("statusLabel").asText()).isEqualTo("配送中");
        // 已离开站点
        assertThat(vehicle.path("locationCode").asInt()).isZero();
        assertThat(vehicle.path("currentSpeed").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(vehicle.path("statusUpdatedAt").isNull()).isFalse();
        assertThat(vehicle.path("positionUpdatedAt").isNull()).isFalse();
    }

    @Test
    @DisplayName("追踪返回的坐标与机器实时信息完全一致（取数同源）")
    void trackingCoordinatesMatchVehicleRealtime() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);

        long vehicleId = getJson("/api/orders/" + orderNumber, token).path("vehicleId").asLong();

        // 追踪侧（本模块供数给实时追踪系统）
        // 按订单号追踪需登录且为下单本人；公开的 /api/tracking/{trackingCode} 只接受随机追踪码
        JsonNode tracking = getJson("/api/orders/" + orderNumber + "/tracking", token);
        BigDecimal trackLat = tracking.path("currentLat").decimalValue();
        BigDecimal trackLng = tracking.path("currentLng").decimalValue();

        // 机器实时信息侧
        JsonNode vehicle = findVehicleById(vehicleId);

        assertThat(vehicle.path("currentLat").decimalValue()).isEqualByComparingTo(trackLat);
        assertThat(vehicle.path("currentLng").decimalValue()).isEqualByComparingTo(trackLng);
        assertThat(tracking.path("status").asText()).isNotEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("下单后站点可调度数下降一台")
    void checkoutReducesStationDispatchableCount() throws Exception {
        mvc.perform(get("/api/stations/1/availability"))
                .andExpect(jsonPath("$.robotUnitsAvailable").value(2));

        String token = login("normal_user", "password123");
        createOrder(token);

        mvc.perform(get("/api/stations/1/availability"))
                .andExpect(jsonPath("$.robotUnitsAvailable").value(1));
    }

    @Test
    @DisplayName("未登录下单被拒绝")
    void checkoutRequiresAuthentication() throws Exception {
        // SecurityConfig 已配置 HttpStatusEntryPoint，缺凭证时返回 401 (已登录但无权限才是 403)
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(NEW_ORDER))
                .andExpect(status().isUnauthorized());
    }

    private String createOrder(String token) throws Exception {
        String json = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_ORDER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).path("orderId").asText();
    }

    /**
     * 实时信息 DTO 不带数据库 id，故先按 id 在基础信息里换出载具编号，再取实时信息。
     */
    private JsonNode findVehicleById(long vehicleId) throws Exception {
        String code = null;
        for (JsonNode v : getJson("/api/vehicles", null)) {
            if (v.path("id").asLong() == vehicleId) {
                code = v.path("vehicleCode").asText();
                break;
            }
        }
        if (code == null) {
            throw new AssertionError("未在机器基础信息中找到 id=" + vehicleId);
        }
        return getJson("/api/vehicles/" + code, null);
    }

    private JsonNode getJson(String path, String token) throws Exception {
        var request = get(path);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        String json = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
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
