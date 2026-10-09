package com.wedelivery.service;

import com.wedelivery.dto.AdminUserDto;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.User;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.exception.ResourceNotFoundException;
import com.wedelivery.repository.OrderRepository;
import com.wedelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only user overview for the admin console. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    /** Orders that are still in progress (not DELIVERED / CANCELLED). */
    private static final List<OrderStatus> ACTIVE_STATUSES = Arrays.asList(
            OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.PICKING_UP, OrderStatus.IN_TRANSIT);

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public List<AdminUserDto.Summary> listUsers() {
        // One grouped query for all counts instead of one query per user.
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : orderRepository.countOrdersByUser(ACTIVE_STATUSES)) {
            long total = ((Number) row[1]).longValue();
            long active = row[2] == null ? 0 : ((Number) row[2]).longValue();
            counts.put((Long) row[0], new long[]{total, active});
        }
        return userRepository.findAll(Sort.by("id")).stream()
                .map(u -> toSummary(u, counts.getOrDefault(u.getId(), new long[]{0, 0})))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminUserDto.Detail getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        long active = orders.stream().filter(o -> ACTIVE_STATUSES.contains(o.getStatus())).count();
        return AdminUserDto.Detail.builder()
                .user(toSummary(user, new long[]{orders.size(), active}))
                .orders(orders.stream().map(AdminUserService::toOrderItem).collect(Collectors.toList()))
                .build();
    }

    private static AdminUserDto.Summary toSummary(User u, long[] counts) {
        return AdminUserDto.Summary.builder()
                .id(u.getId())
                .username(u.getUsername())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .email(u.getEmail())
                .role(u.getRole() != null ? u.getRole().name() : null)
                .isVip(u.isVip())
                .vipExpireAt(u.getVipExpireAt())
                .createdAt(u.getCreatedAt())
                .orderCount(counts[0])
                .activeOrderCount(counts[1])
                .build();
    }

    private static AdminUserDto.OrderItem toOrderItem(Order o) {
        return AdminUserDto.OrderItem.builder()
                .orderNumber(o.getOrderNumber())
                .trackingCode(o.getTrackingCode())
                .status(contractStatus(o.getStatus()))
                .detailStatus(o.getStatus() != null ? o.getStatus().name() : null)
                .vehicleType(o.getVehicleType())
                .pickupAddress(o.getPickupAddress())
                .dropoffAddress(o.getDropoffAddress())
                .finalPrice(o.getFinalPrice())
                .createdAt(o.getCreatedAt())
                .actualDeliveryTime(o.getActualDeliveryTime())
                .build();
    }

    /** Same 6-state → 4-state mapping as GET /api/orders. */
    private static String contractStatus(OrderStatus status) {
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED || status == OrderStatus.IN_TRANSIT) {
            return status.name();
        }
        return "PENDING";
    }
}
