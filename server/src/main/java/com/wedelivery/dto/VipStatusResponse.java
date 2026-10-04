package com.wedelivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VipStatusResponse {
    @JsonProperty("isVip")
    private Boolean isVip;
    private String role;
    private LocalDateTime vipExpireAt;
    private Long daysRemaining;
    private BigDecimal totalSaved;
    private BigDecimal discountRate;
    private int maxModificationsAllowed;
    private double capacityTolerancePercent;
    private List<String> benefits;
}
