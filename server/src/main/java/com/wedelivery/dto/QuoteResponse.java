package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuoteResponse {
    private List<PlanOptionDto> plans;
    private Boolean isVipUser;
    private BigDecimal vipDiscountRate;
}
