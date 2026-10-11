package com.wedelivery.service;

import com.wedelivery.dto.VipStatusResponse;
import com.wedelivery.dto.VipSubscribeRequest;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.User;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.Role;
import com.wedelivery.repository.OrderRepository;
import com.wedelivery.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VipService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    private static final List<String> VIP_BENEFITS = Arrays.asList(
            "10% discount on all delivery orders",
            "Waived dispatch service fee on cancellations ($2.50 waived)",
            "2 order modifications permitted per order",
            "+10% cargo weight and volume tolerance",
            "Priority fleet dispatching and high-battery allocation"
    );

    public VipStatusResponse getVipStatus(User user) {
        boolean isVip = user.isVip();
        LocalDateTime expireAt = user.getVipExpireAt();
        Long daysRemaining = null;

        if (isVip && expireAt != null) {
            daysRemaining = Math.max(0, Duration.between(LocalDateTime.now(), expireAt).toDays());
        }

        // Sum up discount amounts from all non-cancelled orders placed by this user
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        BigDecimal totalSaved = orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED && o.getDiscountAmount() != null)
                .map(Order::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return VipStatusResponse.builder()
                .isVip(isVip)
                .role(user.getRole().name())
                .vipExpireAt(expireAt)
                .daysRemaining(daysRemaining)
                .totalSaved(totalSaved)
                .discountRate(new BigDecimal("0.10"))
                .maxModificationsAllowed(isVip ? 2 : 1)
                .capacityTolerancePercent(isVip ? 10.0 : 0.0)
                .benefits(VIP_BENEFITS)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public VipStatusResponse subscribe(User user, VipSubscribeRequest request) {
        // VIP is tracked through the role, so subscribing would replace ADMIN with VIP
        // and lock the account out of the admin console. Admins are staff, not customers.
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalStateException("Admin accounts can't subscribe to VIP. Use a customer account.");
        }

        String planType = request.getPlanType() != null ? request.getPlanType().trim().toUpperCase() : "MONTHLY";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseTime = (user.isVip() && user.getVipExpireAt() != null && user.getVipExpireAt().isAfter(now))
                ? user.getVipExpireAt()
                : now;

        LocalDateTime newExpireAt;
        if ("ANNUAL".equals(planType)) {
            newExpireAt = baseTime.plusYears(1);
        } else {
            newExpireAt = baseTime.plusMonths(1);
        }

        user.setRole(Role.VIP);
        user.setVipExpireAt(newExpireAt);
        userRepository.save(user);

        log.info("User {} successfully subscribed to VIP {} plan until {}", user.getUsername(), planType, newExpireAt);
        return getVipStatus(user);
    }
}
