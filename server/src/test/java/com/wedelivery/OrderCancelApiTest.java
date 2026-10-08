package com.wedelivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.Payment;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.PaymentStatus;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.repository.OrderRepository;
import com.wedelivery.repository.PaymentRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Order Cancellation, Modification, Return Rerouting, and Review End-to-End Tests")
class OrderCancelApiTest {

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

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("Cancel order before departure: 100% full refund with $0 fee, vehicle released back to IDLE")
    void cancelOrderBeforeTransitReleasesVehicleAndFullRefund() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);

        Order orderBefore = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        Long vehicleId = orderBefore.getVehicleId();
        assertThat(vehicleId).isNotNull();

        // Perform cancellation before transit
        mvc.perform(patch("/api/orders/" + orderNumber + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderNumber))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationFee").value(0))
                .andExpect(jsonPath("$.refundAmount").value(orderBefore.getFinalPrice().doubleValue()));

        // Validate order status and refund amount
        Order orderAfter = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(orderAfter.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderAfter.getCancellationFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(orderAfter.getRefundAmount()).isEqualByComparingTo(orderBefore.getFinalPrice());

        // Validate vehicle release
        Vehicle vehicleAfter = vehicleRepository.findById(vehicleId).orElseThrow();
        assertThat(vehicleAfter.getStatus()).isEqualTo(VehicleStatus.IDLE);

        Payment payment = paymentRepository.findByOrderId(orderAfter.getId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("Cancel order before pickup during delivery: charges $2.50 dispatch fee and reroutes to nearest available station")
    void cancelBeforePickupChargesDispatchFeeAndReroutesToNearestStation() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);

        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        order.setStatus(OrderStatus.PICKING_UP);
        orderRepository.save(order);

        // Simulate vehicle position in Mission district (near Station 2)
        Vehicle v = vehicleRepository.findById(order.getVehicleId()).orElseThrow();
        v.setCurrentLat(new BigDecimal("37.7650000"));
        v.setCurrentLng(new BigDecimal("-122.4200000"));
        vehicleRepository.save(v);

        // Execute cancellation before pickup
        mvc.perform(patch("/api/orders/" + orderNumber + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderNumber))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationFee").value(2.50))
                .andExpect(jsonPath("$.returnStationId").isNumber());

        Order orderAfter = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(orderAfter.getCancellationFee()).isEqualByComparingTo(new BigDecimal("2.50"));
        assertThat(orderAfter.getRefundAmount()).isEqualByComparingTo(order.getFinalPrice().subtract(new BigDecimal("2.50")));

        // Vehicle should be assigned a targetStationId with available bays
        Vehicle vehicleAfter = vehicleRepository.findById(order.getVehicleId()).orElseThrow();
        assertThat(vehicleAfter.getTargetStationId()).isNotNull();
    }

    @Test
    @DisplayName("Cancel order after pickup is prohibited (returns 409 Conflict)")
    void cannotCancelOrderAfterPackagePickedUp() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);

        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        order.setStatus(OrderStatus.IN_TRANSIT);
        orderRepository.save(order);

        // Execute cancellation after pickup -> should be rejected
        mvc.perform(patch("/api/orders/" + orderNumber + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The package has already been picked up and is in transit. Cancellation is not allowed."));
    }

    @Test
    @DisplayName("Modify order before departure succeeds, modification during transit is prohibited")
    void updateOrderDetailsBeforeTransit() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);

        // 1. Modify dropoff address before dispatch
        String updatePayload = "{\"dropoffAddress\":\"100 Market St, San Francisco\",\"packageDescription\":\"Leave with concierge\"}";
        mvc.perform(patch("/api/orders/" + orderNumber)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dropoffAddress").value("100 Market St, San Francisco"));

        // 2. Attempt second modification -> rejected by 1-modification limit (409 Conflict)
        mvc.perform(patch("/api/orders/" + orderNumber)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Upgrade robot delivery to drone: recalculates price, charges surcharge, locks drone, strictly enforces 1 modification limit")
    void upgradeRobotToDroneChargesSurchargeAndReassignsDrone() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token); // CAND-BEST_VALUE is ROBOT

        Order orderBefore = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(orderBefore.getVehicleType()).isEqualTo(com.wedelivery.entity.enums.VehicleType.ROBOT);
        BigDecimal oldPrice = orderBefore.getFinalPrice();

        // Perform one-click upgrade to Drone Express
        String upgradePayload = "{\"upgradeToDrone\":true}";
        mvc.perform(patch("/api/orders/" + orderNumber)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upgradePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleType").value("DRONE"))
                .andExpect(jsonPath("$.hasBeenModified").value(true));

        Order orderAfter = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        assertThat(orderAfter.getVehicleType()).isEqualTo(com.wedelivery.entity.enums.VehicleType.DRONE);
        assertThat(orderAfter.getFinalPrice()).isGreaterThan(oldPrice);
        assertThat(orderAfter.getHasBeenModified()).isTrue();

        // Second modification attempt -> rejected (409 Conflict)
        mvc.perform(patch("/api/orders/" + orderNumber)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dropoffAddress\":\"200 Pine St, San Francisco\"}"))
                .andExpect(status().isConflict());
    }


    @Test
    @DisplayName("Drone express upgrade requires backend idle drone verification: reject upgrade if no idle drones (409 Conflict)")
    void cannotUpgradeToDroneWhenNoIdleDronesAvailable() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token); // CAND-BEST_VALUE is ROBOT

        // Simulate all drones in the city being unavailable: both idle and charging drones are dispatchable,
        // so take both out of the pool (no drone plan should be available)
        for (VehicleStatus s : new VehicleStatus[]{VehicleStatus.IDLE, VehicleStatus.CHARGING}) {
            vehicleRepository.findByVehicleTypeAndStatus(com.wedelivery.entity.enums.VehicleType.DRONE, s)
                    .forEach(d -> {
                        d.setStatus(VehicleStatus.IN_DELIVERY);
                        vehicleRepository.save(d);
                    });
        }

        // Attempt upgrade to drone -> backend verification finds no available drone, returns 409 Conflict
        String upgradePayload = "{\"upgradeToDrone\":true}";
        mvc.perform(patch("/api/orders/" + orderNumber)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upgradePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No idle drones with sufficient battery (estimated remaining >= 10% after delivery) are currently available in the fleet. Upgrade to Drone Express cannot be completed at this time."));
    }

    @Test
    @DisplayName("Drone express upgrade requires estimated battery after delivery >= 10%: reject if drone battery drops below 10% upon delivery")
    void cannotUpgradeToDroneWhenEstimatedBatteryAfterDeliveryIsLessThan10Percent() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token); // CAND-BEST_VALUE is ROBOT

        // Set all idle drones to low battery (e.g. 15%), which is insufficient to retain >= 10% after delivery
        vehicleRepository.findByVehicleTypeAndStatus(com.wedelivery.entity.enums.VehicleType.DRONE, VehicleStatus.IDLE)
                .forEach(d -> {
                    d.setBatteryLevel(new BigDecimal("12.00"));
                    vehicleRepository.save(d);
                });

        // Query order -> droneUpgradeAvailable should be false because remaining battery after delivery < 10%
        mvc.perform(get("/api/orders/" + orderNumber)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.droneUpgradeAvailable").value(false));

        // Attempt upgrade to drone -> rejected (409 Conflict)
        String upgradePayload = "{\"upgradeToDrone\":true}";
        mvc.perform(patch("/api/orders/" + orderNumber)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(upgradePayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No idle drones with sufficient battery (estimated remaining >= 10% after delivery) are currently available in the fleet. Upgrade to Drone Express cannot be completed at this time."));

        // Now charge one drone to 95% battery -> estimated battery after delivery will be >= 10%
        Vehicle drone = vehicleRepository.findByVehicleTypeAndStatus(com.wedelivery.entity.enums.VehicleType.DRONE, VehicleStatus.IDLE).get(0);
        drone.setBatteryLevel(new BigDecimal("95.00"));
        vehicleRepository.save(drone);

        // Query order again -> droneUpgradeAvailable should now be true
        mvc.perform(get("/api/orders/" + orderNumber)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.droneUpgradeAvailable").value(true));
    }


    @Test
    @DisplayName("Submit and retrieve order review after delivery, prevent review before delivery or duplicate reviews")
    void submitAndGetOrderReview() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);

        String reviewPayload = "{\"rating\":5,\"comment\":\"Fast drone delivery!\",\"damageReported\":false}";

        // 1. Review before delivery -> rejected (409 Conflict)
        mvc.perform(post("/api/orders/" + orderNumber + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload))
                .andExpect(status().isConflict());

        // 2. Confirm receipt to transition to DELIVERED (only possible once the package is delivered)
        markPackageDelivered(orderNumber);
        mvc.perform(patch("/api/orders/" + orderNumber + "/confirm-receipt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 3. Review submission succeeds
        mvc.perform(post("/api/orders/" + orderNumber + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderNumber))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.reviewId").isNotEmpty());

        // 4. Duplicate review -> rejected (409 Conflict)
        mvc.perform(post("/api/orders/" + orderNumber + "/review")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload))
                .andExpect(status().isConflict());

        // 5. Query review record
        mvc.perform(get("/api/orders/" + orderNumber + "/review")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("Fast drone delivery!"));
    }

    @Test
    @DisplayName("Delivered order cannot be cancelled (returns 409 Conflict)")
    void cannotCancelDeliveredOrder() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);

        // Confirm delivery receipt
        markPackageDelivered(orderNumber);
        mvc.perform(patch("/api/orders/" + orderNumber + "/confirm-receipt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // Attempt to cancel delivered order
        mvc.perform(patch("/api/orders/" + orderNumber + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Cancelling another user's order is rejected (returns 403 Forbidden)")
    void cannotCancelOtherUsersOrder() throws Exception {
        String user1Token = login("normal_user", "password123");
        String orderNumber = createOrder(user1Token);

        String user2Token = login("vip_user", "password123");
        mvc.perform(patch("/api/orders/" + orderNumber + "/cancel")
                        .header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated cancellation returns 401 Unauthorized")
    void cancelWithoutAuthReturnsUnauthorized() throws Exception {
        mvc.perform(patch("/api/orders/SFORD12345678/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Duplicate cancellation is idempotent")
    void cancelOrderIsIdempotent() throws Exception {
        String token = login("normal_user", "password123");
        String orderNumber = createOrder(token);

        // First cancellation
        mvc.perform(patch("/api/orders/" + orderNumber + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // Second cancellation (idempotent success)
        mvc.perform(patch("/api/orders/" + orderNumber + "/cancel")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    /** Moves the schedule into the past so the package counts as delivered (20-minute trip, 90% elapsed). */
    private void markPackageDelivered(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber).orElseThrow();
        java.time.LocalDateTime start = java.time.LocalDateTime.now().minusMinutes(18);
        order.setScheduledStartTime(start);
        order.setEstimatedDeliveryTime(start.plusMinutes(20));
        orderRepository.save(order);
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
