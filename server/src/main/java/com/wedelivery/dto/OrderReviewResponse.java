package com.wedelivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderReviewResponse {
    private String orderId;
    private String reviewId;
    private Integer rating;
    private String comment;
    private Boolean damageReported;
    private LocalDateTime createdAt;
    private String message;
}
