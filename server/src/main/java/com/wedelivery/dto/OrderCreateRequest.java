package com.wedelivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreateRequest {

    @NotBlank
    private String candidateId;

    @NotNull
    private RecommendationContractDto.LocationDto pickup;

    @NotNull
    private RecommendationContractDto.LocationDto dropoff;

    @JsonProperty("package")
    private RecommendationContractDto.PackageDto packageInfo;

    // 备用兼容字段名
    private RecommendationContractDto.PackageDto packageDetails;

    private String priority; // STANDARD | EXPRESS

    private String paymentMethodId;

    private String cardNumber;

    public RecommendationContractDto.PackageDto getEffectivePackage() {
        return packageInfo != null ? packageInfo : packageDetails;
    }
}
