package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class RecommendationContractDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private LocationDto pickup;
        private LocationDto dropoff;
        private PackageDto packageInfo;
        // 支持前端传入以 "package" 为 key 的情况
        private PackageDto packageDetails;
        private String priority; // STANDARD | EXPRESS
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocationDto {
        private String addressId;
        private String line1;
        private String city;
        private String zip;
        private BigDecimal lat;
        private BigDecimal lng;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PackageDto {
        private String description;
        private BigDecimal weightKg;
        private BigDecimal lengthCm;
        private BigDecimal widthCm;
        private BigDecimal heightCm;
        private Boolean fragile;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private List<CandidateDto> candidates;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CandidateDto {
        private String candidateId;
        private String stationId;
        private String stationName;
        private String vehicleType; // "ROBOT" | "DRONE"
        private Integer estimatedTimeMinutes;
        private BigDecimal estimatedCost;
        private Integer availableUnits;
        private Double score;
        private Boolean isFastest;
        private Boolean isCheapest;
    }
}
