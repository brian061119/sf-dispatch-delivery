package com.wedelivery.entity;

import com.wedelivery.entity.enums.TrackingStage;
import lombok.*;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tracking_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TrackingStage stage;

    @Column(name = "status_description", nullable = false)
    private String statusDescription;

    @Column(name = "event_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal eventLat;

    @Column(name = "event_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal eventLng;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @PrePersist
    protected void onCreate() {
        if (eventTime == null) {
            eventTime = LocalDateTime.now();
        }
    }
}
