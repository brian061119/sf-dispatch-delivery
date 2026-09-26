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
 * 下单价格与推荐一致：POST /api/orders 在服务端按同一请求重新计算推荐，
 * 扣款价格、站点、载具类型与预计时间取自用户所选方案，而不是固定值或客户端传值。
 * 写库，故整类回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("下单 · 价格与推荐一致")
class OrderPricingApiTest {

    /** 取件点在站点 1 (Downtown) 附近 */
    private static final String DOWNTOWN_TRIP = ""
            + "\"pickup\":{\"line1\":\"Market St\",\"lat\":37.7858,\"lng\":-122.4065},"
            + "\"dropoff\":{\"line1\":\"Mission St\",\"lat\":37.7596,\"lng\":-122.4269},"
            + "\"package\":{\"description\":\"docs\",\"weightKg\":1.5},"
            + "\"priority\":\"STANDARD\"";

    /** 取件点在站点 3 (Mission) 附近：验证站点不再固定为 1 */
    private static final String MISSION_TRIP = ""
            + "\"pickup\":{\"line1\":\"24th St\",\"lat\":37.7590,\"lng\":-122.4185},"
            + "\"dropoff\":{\"line1\":\"Valencia St\",\"lat\":37.7650,\"lng\":-122.4215},"
            + "\"package\":{\"description\":\"books\",\"weightKg\":2.0},"
            + "\"priority\":\"STANDARD\"";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("普通用户：扣款价格、站点、载具与预计时间等于所选推荐方案")
    void orderUsesRecommendedPlan() throws Exception {
        assertOrderMatchesRecommendation(login("normal_user"), DOWNTOWN_TRIP, "CAND-BEST_VALUE");
    }

    @Test
    @DisplayName("VIP：扣款价格与推荐一致（含 VIP 折扣）")
    void vipOrderUsesDiscountedRecommendedPrice() throws Exception {
        JsonNode order = assertOrderMatchesRecommendation(login("vip_user"), DOWNTOWN_TRIP, "CAND-BEST_VALUE");
        assertThat(order.path("discountAmount").decimalValue()).isPositive();
    }

    @Test
    @DisplayName("无人机方案：价格与推荐一致，载具为 DRONE")
    void droneOrderUsesRecommendedPlan() throws Exception {
        JsonNode order = assertOrderMatchesRecommendation(login("normal_user"), DOWNTOWN_TRIP, "CAND-FASTEST");
        assertThat(order.path("vehicleType").asText()).isEqualTo("DRONE");
    }

    @Test
    @DisplayName("取件点靠近站点 3 时从站点 3 发车（不再固定站点 1）")
    void orderUsesRecommendedStation() throws Exception {
        JsonNode order = assertOrderMatchesRecommendation(login("normal_user"), MISSION_TRIP, "CAND-BEST_VALUE");
        assertThat(order.path("stationId").asLong()).isEqualTo(3L);
    }

    @Test
    @DisplayName("所选方案不存在时返回 409，不会按其他价格下单")
    void unknownPlanIsRejected() throws Exception {
        String token = login("normal_user");
        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"candidateId\":\"CAND-NO_SUCH_PLAN\"," + DOWNTOWN_TRIP + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no longer available")));
    }

    /**
     * 先取推荐，再用同一份请求下单，断言下单响应与订单详情都与所选方案一致。
     * @return 订单详情
     */
    private JsonNode assertOrderMatchesRecommendation(String token, String trip, String candidateId) throws Exception {
        JsonNode candidates = postJson("/api/recommendations", "{" + trip + "}", token, 200).path("candidates");
        JsonNode chosen = null;
        for (JsonNode c : candidates) {
            if (candidateId.equals(c.path("candidateId").asText())) {
                chosen = c;
            }
        }
        assertThat(chosen).as("recommendation contains " + candidateId).isNotNull();
        BigDecimal quotedCost = chosen.path("estimatedCost").decimalValue();

        JsonNode created = postJson("/api/orders", "{\"candidateId\":\"" + candidateId + "\"," + trip + "}", token, 201);
        assertThat(created.path("estimatedCost").decimalValue()).isEqualByComparingTo(quotedCost);
        assertThat(created.path("estimatedTimeMinutes").asInt()).isEqualTo(chosen.path("estimatedTimeMinutes").asInt());

        String json = mvc.perform(get("/api/orders/" + created.path("orderId").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode order = objectMapper.readTree(json);
        assertThat(order.path("finalPrice").decimalValue()).isEqualByComparingTo(quotedCost);
        assertThat(order.path("stationId").asText()).isEqualTo(chosen.path("stationId").asText());
        assertThat(order.path("vehicleType").asText()).isEqualTo(chosen.path("vehicleType").asText());
        return order;
    }

    private JsonNode postJson(String path, String body, String token, int expectedStatus) throws Exception {
        String json = mvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json);
    }

    private String login(String username) throws Exception {
        String json = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).path("token").asText();
    }
}
