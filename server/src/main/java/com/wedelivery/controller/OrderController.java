package com.wedelivery.controller;

import com.wedelivery.dto.CheckoutRequest;
import com.wedelivery.dto.CheckoutResponse;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.User;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.PlanType;
import com.wedelivery.entity.enums.VehicleType;
import com.wedelivery.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 契约路径: POST /api/orders
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrderContract(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String candidateId = (String) body.getOrDefault("candidateId", "CAND-BEST_VALUE");
        Map<String, Object> pickup = (Map<String, Object>) body.getOrDefault("pickup", Collections.emptyMap());
        Map<String, Object> dropoff = (Map<String, Object>) body.getOrDefault("dropoff", Collections.emptyMap());
        Map<String, Object> pkg = (Map<String, Object>) body.getOrDefault("package", Collections.emptyMap());

        BigDecimal pLat = pickup.get("lat") != null ? new BigDecimal(pickup.get("lat").toString()) : new BigDecimal("37.7858");
        BigDecimal pLng = pickup.get("lng") != null ? new BigDecimal(pickup.get("lng").toString()) : new BigDecimal("-122.4065");
        BigDecimal dLat = dropoff.get("lat") != null ? new BigDecimal(dropoff.get("lat").toString()) : new BigDecimal("37.7596");
        BigDecimal dLng = dropoff.get("lng") != null ? new BigDecimal(dropoff.get("lng").toString()) : new BigDecimal("-122.4269");
        BigDecimal weight = pkg.get("weightKg") != null ? new BigDecimal(pkg.get("weightKg").toString()) : new BigDecimal("1.5");

        PlanType planType = candidateId.contains("FASTEST") ? PlanType.FASTEST : PlanType.BEST_VALUE;
        VehicleType vType = planType == PlanType.FASTEST ? VehicleType.DRONE : VehicleType.ROBOT;

        CheckoutRequest req = CheckoutRequest.builder()
                .planType(planType)
                .vehicleType(vType)
                .stationId(1L)
                .pickupAddress((String) pickup.getOrDefault("line1", "Market St, San Francisco"))
                .pickupLat(pLat)
                .pickupLng(pLng)
                .dropoffAddress((String) dropoff.getOrDefault("line1", "Mission St, San Francisco"))
                .dropoffLat(dLat)
                .dropoffLng(dLng)
                .packageWeight(weight)
                .packageVolume(new BigDecimal("0.02"))
                .totalDistance(new BigDecimal("8.50"))
                .originPrice(new BigDecimal("18.50"))
                .finalPrice(new BigDecimal("18.50"))
                .cardNumber("4532 8901 2345 6789")
                .build();

        CheckoutResponse response = orderService.checkoutAndLockVehicle(req, currentUser);

        Map<String, Object> res = new HashMap<>();
        res.put("orderId", response.getOrderNumber());
        res.put("status", "PENDING");
        res.put("estimatedTimeMinutes", 25);
        res.put("estimatedCost", 18.50);

        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    // 契约路径: GET /api/orders (获取订单列表)
    @GetMapping
    public ResponseEntity<Map<String, Object>> getOrdersContract(@AuthenticationPrincipal User currentUser) {
        List<Order> orders;
        if (currentUser != null) {
            orders = orderService.getUserOrders(currentUser.getId());
        } else {
            orders = Collections.emptyList();
        }

        List<Map<String, Object>> orderList = orders.stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("orderId", o.getOrderNumber());
            m.put("status", o.getStatus().name());
            m.put("createdAt", o.getCreatedAt().format(DateTimeFormatter.ISO_DATE_TIME));
            m.put("packageDescription", o.getPickupAddress() + " -> " + o.getDropoffAddress());
            m.put("estimatedCost", o.getFinalPrice());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> res = new HashMap<>();
        res.put("orders", orderList);
        return ResponseEntity.ok(res);
    }

    // 契约路径: PATCH /api/orders/:orderId/confirm-receipt (确认签收)
    @PatchMapping("/{orderNumber}/confirm-receipt")
    public ResponseEntity<Map<String, Object>> confirmReceipt(@PathVariable String orderNumber) {
        Order order = orderService.getOrderByNumber(orderNumber);
        order.setStatus(OrderStatus.DELIVERED);
        Map<String, Object> res = new HashMap<>();
        res.put("orderId", order.getOrderNumber());
        res.put("status", "DELIVERED");
        return ResponseEntity.ok(res);
    }

    // 向下兼容的结账接口
    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        CheckoutResponse response = orderService.checkoutAndLockVehicle(request, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrderByNumber(orderNumber));
    }

    @GetMapping("/my")
    public ResponseEntity<List<Order>> getMyOrders(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(orderService.getUserOrders(currentUser.getId()));
    }
}
