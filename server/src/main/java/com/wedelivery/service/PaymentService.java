package com.wedelivery.service;

import com.wedelivery.entity.Payment;

import java.math.BigDecimal;

public interface PaymentService {

    /**
     * 执行支付扣款处理
     * @param orderId 关联订单 ID
     * @param userId 用户 ID
     * @param amount 支付扣款金额
     * @param cardNumber 信用卡号 (末尾 0000 触发余额不足模拟)
     * @return 支付记录实体
     */
    Payment processPayment(Long orderId, Long userId, BigDecimal amount, String cardNumber);
}
