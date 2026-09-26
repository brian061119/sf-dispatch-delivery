package com.wedelivery.service.impl;

import com.wedelivery.entity.Payment;
import com.wedelivery.entity.enums.PaymentStatus;
import com.wedelivery.repository.PaymentRepository;
import com.wedelivery.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockPaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    @Override
    public Payment processPayment(Long orderId, Long userId, BigDecimal amount, String cardNumber) {
        String cleanCard = cardNumber != null ? cardNumber.replaceAll("\\s+", "") : "";

        // 模拟 0000 余额不足异常
        if (cleanCard.endsWith("0000")) {
            log.warn("Mock Payment Declined: Card ending with 0000 has insufficient funds. OrderId: {}", orderId);
            Payment failedPayment = Payment.builder()
                    .orderId(orderId)
                    .userId(userId)
                    .transactionNo("TXN-FAILED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .paymentMethod("MOCK_CARD")
                    .amount(amount)
                    .status(PaymentStatus.FAILED)
                    .failureCode("INSUFFICIENT_BALANCE")
                    .paidAt(LocalDateTime.now())
                    .build();
            paymentRepository.save(failedPayment);
            throw new RuntimeException("Payment Failed: Card ending with 0000 has insufficient funds.");
        }

        String transactionNo = "TXN-MOCK-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        Payment payment = Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .transactionNo(transactionNo)
                .paymentMethod("MOCK_CARD")
                .amount(amount)
                .status(PaymentStatus.SUCCESS)
                .paidAt(LocalDateTime.now())
                .build();

        log.info("Mock Payment Succeeded: {} for amount ${}", transactionNo, amount);
        return paymentRepository.save(payment);
    }
}
