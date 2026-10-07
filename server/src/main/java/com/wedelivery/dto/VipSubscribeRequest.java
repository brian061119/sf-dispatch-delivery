package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VipSubscribeRequest {
    @NotBlank(message = "Plan type is required (MONTHLY or ANNUAL)")
    private String planType;

    private String paymentMethodId;
}
