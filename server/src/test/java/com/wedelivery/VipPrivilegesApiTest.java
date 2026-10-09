package com.wedelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.repository.OrderRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("VIP User Privileges and Subscription End-to-End Tests")
class VipPrivilegesApiTest {

    private static final String NEW_ORDER = "{"
            + "\"candidateId\":\"CAND-BEST_VALUE\","
            + "\"pickup\":{\"line1\":\"Downtown\",\"lat\":37.7891720,\"lng\":-122.3970420},"
            + "\"dropoff\":{\"line1\":\"Mission\",\"lat\":37.7596000,\"lng\":-122.4269000},"
            + "\"package\":{\"description\":\"electronics\",\"weightKg\":2.0},"
            + "\"priority\":\"STANDARD\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    private String login(String username, String password) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        String res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(res).path("token").asText();
    }

    private String createOrder(String token, String payload) throws Exception {
        String res = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(res).path("orderId").asText();
    }

    @Test
    @DisplayName("VIP status API returns privileges, role, and discount rate")
    void vipStatusEndpointReturnsBenefits() throws Exception {
        String vipToken = login("vip_user", "password123");

        mvc.perform(get("/api/vip/status")
                        .header("Authorization", "Bearer " + vipToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVip").value(true))
                .andExpect(jsonPath("$.role").value("VIP"))
                .andExpect(jsonPath("$.discountRate").value(0.10))
                .andExpect(jsonPath("$.maxModificationsAllowed").value(2))
                .andExpect(jsonPath("$.capacityTolerancePercent").value(10.0))
                .andExpect(jsonPath("$.benefits").isArray());
    }

    @Test
    @DisplayName("Normal user can subscribe to VIP monthly plan and upgrade immediately")
    void normalUserCanSubscribeToVip() throws Exception {
        String normalToken = login("normal_user", "password123");

        mvc.perform(get("/api/vip/status")
                        .header("Authorization", "Bearer " + normalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVip").value(false));

        String subscribePayload = "{\"planType\":\"MONTHLY\",\"paymentMethodId\":\"pm_card_mock\"}";
        mvc.perform(post("/api/vip/subscribe")
                        .header("Authorization", "Bearer " + normalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subscribePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVip").value(true))
                .andExpect(jsonPath("$.role").value("VIP"))
                .andExpect(jsonPath("$.maxModificationsAllowed").value(2));
    }

    @Test
    @DisplayName("Admin cannot subscribe to VIP and keeps the ADMIN role and admin access")
    void adminCannotSubscribeAndKeepsAdminRole() throws Exception {
        String adminToken = login("admin", "password123");

        mvc.perform(post("/api/vip/subscribe")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planType\":\"ANNUAL\",\"paymentMethodId\":\"pm_card_mock\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Admin accounts can't subscribe")));

        // Fresh login: the stored role must still be ADMIN, not VIP.
        String freshToken = login("admin", "password123");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.isVip").value(false));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("VIP user enjoys dispatch service fee waiver ($0 fee, 100% refund) when vehicle is en route to pickup")
    void vipUserEnjoysCancellationFeeWaiverWhenVehicleEnRoute() throws Exception {
        String vipToken = login("vip_user", "password123");
        String orderId = createOrder(vipToken, NEW_ORDER);

        Order order = orderRepository.findByOrderNumber(orderId).orElseThrow();
        order.setStatus(OrderStatus.PICKING_UP);
        orderRepository.save(order);

        String cancelRes = mvc.perform(patch("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + vipToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode json = objectMapper.readTree(cancelRes);
        // Fee must be waived (0.00) and refund equals the full order final price
        assertThat(json.path("cancellationFee").asDouble()).isEqualTo(0.0);
        assertThat(json.path("refundAmount").asDouble()).isEqualTo(order.getFinalPrice().doubleValue());
    }

    @Test
    @DisplayName("VIP user is permitted to modify order up to 2 times, while 3rd modification is rejected")
    void vipUserCanModifyOrderTwice() throws Exception {
        String vipToken = login("vip_user", "password123");
        String orderId = createOrder(vipToken, NEW_ORDER);

        // 1st modification: should succeed
        String mod1 = "{\"packageWeight\":2.2,\"dropoffAddress\":\"100 Market St, San Francisco, CA\"}";
        mvc.perform(patch("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + vipToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mod1))
                .andExpect(status().isOk());

        Order orderAfterMod1 = orderRepository.findByOrderNumber(orderId).orElseThrow();
        assertThat(orderAfterMod1.getModifiedCount()).isEqualTo(1);
        assertThat(orderAfterMod1.getHasBeenModified()).isFalse(); // Still has 1 modification remaining for VIP

        // 2nd modification: should also succeed for VIP
        String mod2 = "{\"packageWeight\":2.4,\"dropoffAddress\":\"200 Mission St, San Francisco, CA\"}";
        mvc.perform(patch("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + vipToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mod2))
                .andExpect(status().isOk());

        Order orderAfterMod2 = orderRepository.findByOrderNumber(orderId).orElseThrow();
        assertThat(orderAfterMod2.getModifiedCount()).isEqualTo(2);
        assertThat(orderAfterMod2.getHasBeenModified()).isTrue();

        // 3rd modification: should be rejected
        String mod3 = "{\"packageWeight\":2.5}";
        mvc.perform(patch("/api/orders/" + orderId)
                        .header("Authorization", "Bearer " + vipToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mod3))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("VIP user has +10% capacity tolerance, allowing packages up to 3.3kg for drone delivery")
    void vipUserHasTenPercentCapacityToleranceForDrone() throws Exception {
        String vipToken = login("vip_user", "password123");
        String normalToken = login("normal_user", "password123");

        String normalOrder = createOrder(normalToken, NEW_ORDER);
        String vipOrder = createOrder(vipToken, NEW_ORDER);

        // Normal user attempting to upgrade to drone with 3.2kg (exceeds 3.0kg limit) -> Rejected
        String normalUpgrade = "{\"upgradeToDrone\":true,\"vehicleType\":\"DRONE\",\"packageWeight\":3.2}";
        mvc.perform(patch("/api/orders/" + normalOrder)
                        .header("Authorization", "Bearer " + normalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(normalUpgrade))
                .andExpect(status().isBadRequest());

        // VIP user attempting to upgrade to drone with 3.2kg (within 3.3kg VIP tolerance) -> Accepted
        String vipUpgrade = "{\"upgradeToDrone\":true,\"vehicleType\":\"DRONE\",\"packageWeight\":3.2}";
        mvc.perform(patch("/api/orders/" + vipOrder)
                        .header("Authorization", "Bearer " + vipToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(vipUpgrade))
                .andExpect(status().isOk());

        // Exceeding even VIP tolerance (> 3.3kg, e.g. 3.4kg) -> Rejected
        String overLimit = "{\"upgradeToDrone\":true,\"vehicleType\":\"DRONE\",\"packageWeight\":3.4}";
        mvc.perform(patch("/api/orders/" + vipOrder)
                        .header("Authorization", "Bearer " + vipToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overLimit))
                .andExpect(status().isBadRequest());
    }
}
