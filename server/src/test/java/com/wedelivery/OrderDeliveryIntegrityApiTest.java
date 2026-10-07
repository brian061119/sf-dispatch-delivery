package com.wedelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.repository.OrderRepository;
import com.wedelivery.repository.VehicleRepository;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Delivery lifecycle integrity: only an explicit, timely confirm-receipt marks an order DELIVERED,
 * tracking polls never promote it on their own, and a stale order's tracking page never
 * overwrites a vehicle that has since been reassigned to a newer order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Order delivery integrity")
class OrderDeliveryIntegrityApiTest {

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
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private VehicleRepository vehicleRepository;

    @Test
    @DisplayName("confirm-receipt before the package is delivered is rejected and the vehicle stays assigned")
    void confirmBeforeDeliveryIsRejected() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);
        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();

        mvc.perform(patch("/api/orders/" + orderNumber + "/confirm-receipt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        Order after = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(after.getStatus()).isNotEqualTo(OrderStatus.DELIVERED);
        assertThat(vehicleRepository.findById(order.getVehicleId()).orElseThrow().getStatus())
                .isEqualTo(VehicleStatus.IN_DELIVERY);
    }

    @Test
    @DisplayName("tracking past the delivery point never flips the order to DELIVERED on its own")
    void trackingNeverMarksDelivered() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);
        shiftTimeline(orderNumber, 0.9);

        mvc.perform(get("/api/orders/" + orderNumber + "/tracking")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStage").value("RETURNING"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        shiftTimeline(orderNumber, 1.5);
        mvc.perform(get("/api/orders/" + orderNumber + "/tracking")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.currentStage").value("COMPLETED"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test
    @DisplayName("confirm-receipt after delivery marks DELIVERED and leaves a still-returning vehicle to the simulator")
    void confirmAfterDeliveryWhileVehicleStillReturning() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);
        Order order = shiftTimeline(orderNumber, 0.9);

        mvc.perform(patch("/api/orders/" + orderNumber + "/confirm-receipt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // Vehicle is still on the way back: it must not be teleported into the idle pool.
        assertThat(vehicleRepository.findById(order.getVehicleId()).orElseThrow().getStatus())
                .isEqualTo(VehicleStatus.IN_DELIVERY);
    }

    @Test
    @DisplayName("confirm-receipt after the vehicle is docked releases it to its resting state")
    void confirmAfterVehicleDockedReleasesVehicle() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);
        Order order = shiftTimeline(orderNumber, 1.5);

        mvc.perform(patch("/api/orders/" + orderNumber + "/confirm-receipt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(vehicleRepository.findById(order.getVehicleId()).orElseThrow().getStatus())
                .isIn(VehicleStatus.IDLE, VehicleStatus.CHARGING);
    }

    @Test
    @DisplayName("tracking a DELIVERED order does not touch a vehicle now serving a newer order")
    void trackingDeliveredOrderLeavesReassignedVehicleAlone() throws Exception {
        String token = login("normal_user", "password123");
        Order stale = reassignedVehicleScenario(token, OrderStatus.DELIVERED);
        Vehicle before = snapshot(stale.getVehicleId());

        mvc.perform(get("/api/orders/" + stale.getOrderNumber() + "/tracking")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertVehicleUntouched(before, stale.getVehicleId());
    }

    @Test
    @DisplayName("tracking an old, still-open order does not touch a vehicle now serving a newer order")
    void trackingStaleOpenOrderLeavesReassignedVehicleAlone() throws Exception {
        String token = login("normal_user", "password123");
        Order stale = reassignedVehicleScenario(token, OrderStatus.IN_TRANSIT);
        Vehicle before = snapshot(stale.getVehicleId());

        mvc.perform(get("/api/orders/" + stale.getOrderNumber() + "/tracking")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertVehicleUntouched(before, stale.getVehicleId());
    }

    /**
     * Order A is finished long ago; its vehicle is now (re)assigned to the newer order B and
     * in flight. Returns A, whose vehicleId points at that same vehicle.
     */
    private Order reassignedVehicleScenario(String token, OrderStatus staleStatus) throws Exception {
        String orderA = createOrder(token);
        String orderB = createOrder(token);
        Order b = orderRepository.findByOrderNumber(orderB).orElseThrow();

        Order a = shiftTimeline(orderA, 1.5);
        a.setVehicleId(b.getVehicleId());
        a.setStatus(staleStatus);
        return orderRepository.save(a);
    }

    private Vehicle snapshot(Long vehicleId) {
        Vehicle v = vehicleRepository.findById(vehicleId).orElseThrow();
        return Vehicle.builder()
                .status(v.getStatus())
                .currentLat(v.getCurrentLat())
                .currentLng(v.getCurrentLng())
                .currentSpeed(v.getCurrentSpeed())
                .batteryLevel(v.getBatteryLevel())
                .build();
    }

    private void assertVehicleUntouched(Vehicle before, Long vehicleId) {
        Vehicle after = vehicleRepository.findById(vehicleId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(before.getStatus());
        assertThat(after.getCurrentLat()).isEqualByComparingTo(before.getCurrentLat());
        assertThat(after.getCurrentLng()).isEqualByComparingTo(before.getCurrentLng());
        assertThat(after.getCurrentSpeed()).isEqualByComparingTo(before.getCurrentSpeed());
        assertThat(after.getBatteryLevel()).isEqualByComparingTo(before.getBatteryLevel());
    }

    /** Moves the order's schedule so that elapsed/total equals {@code ratio} right now (20-minute trip). */
    private Order shiftTimeline(String orderNumber, double ratio) {
        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        long totalSeconds = 20 * 60;
        LocalDateTime start = LocalDateTime.now().minusSeconds((long) (totalSeconds * ratio));
        order.setScheduledStartTime(start);
        order.setEstimatedDeliveryTime(start.plusSeconds(totalSeconds));
        return orderRepository.save(order);
    }

    private String createOrder(String token) throws Exception {
        String json = mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_ORDER))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(json).path("orderId").asText();
    }

    private String login(String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        String json = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(json);
        return node.path("token").asText();
    }
}
