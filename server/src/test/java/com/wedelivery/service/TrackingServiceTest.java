package com.wedelivery.service;

import com.wedelivery.dto.TrackingResponse;
import com.wedelivery.entity.Order;
import com.wedelivery.entity.Station;
import com.wedelivery.entity.TrackingEvent;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.OrderStatus;
import com.wedelivery.entity.enums.TrackingStage;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
import com.wedelivery.repository.OrderRepository;
import com.wedelivery.repository.StationRepository;
import com.wedelivery.repository.TrackingEventRepository;
import com.wedelivery.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the "skip backfill" fix: if nobody polled while the order was in an
 * earlier stage, the first poll that lands later should still write a
 * milestone for every stage in between, not just the current one.
 */
@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    private static final String ORDER_NUMBER = "SFORD-TEST-001";
    private static final Long ORDER_ID = 1L;
    private static final Long STATION_ID = 10L;
    private static final Long VEHICLE_ID = 100L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private TrackingEventRepository trackingEventRepository;

    private TrackingService trackingService;

    private void setUp() {
        trackingService = new TrackingService(orderRepository, stationRepository, vehicleRepository, trackingEventRepository);
    }

    // Builds an order whose scheduled window puts overallRatio at ~0.8, i.e.
    // squarely inside the RETURNING bucket (0.75 ~ 1.00), well past the
    // TO_PICKUP and TO_DROPOFF windows it already lived through unpolled.
    private Order buildLateOrder() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(20);
        LocalDateTime delivery = start.plusMinutes(25);
        return Order.builder()
                .id(ORDER_ID)
                .orderNumber(ORDER_NUMBER)
                .stationId(STATION_ID)
                .vehicleId(VEHICLE_ID)
                .vehicleType(VehicleType.ROBOT)
                .status(OrderStatus.PAID)
                .isStationPickup(false)
                .pickupLat(new BigDecimal("37.7800000"))
                .pickupLng(new BigDecimal("-122.4100000"))
                .dropoffLat(new BigDecimal("37.7600000"))
                .dropoffLng(new BigDecimal("-122.4300000"))
                .scheduledStartTime(start)
                .estimatedDeliveryTime(delivery)
                .build();
    }

    private Station buildStation() {
        return Station.builder()
                .id(STATION_ID)
                .name("Test Station")
                .address("123 Test St, San Francisco, CA")
                .latitude(new BigDecimal("37.7900000"))
                .longitude(new BigDecimal("-122.4000000"))
                .totalDroneBays(5)
                .totalRobotBays(5)
                .build();
    }

    private Vehicle buildVehicle() {
        return Vehicle.builder()
                .id(VEHICLE_ID)
                .stationId(STATION_ID)
                .vehicleCode("ROBOT-TEST-01")
                .vehicleType(VehicleType.ROBOT)
                .status(VehicleStatus.BUSY)
                .batteryLevel(new BigDecimal("80.00"))
                .maxWeight(new BigDecimal("15.00"))
                .maxVolume(new BigDecimal("0.30"))
                .cruiseSpeed(new BigDecimal("15.00"))
                .build();
    }

    @Test
    void trackOrder_backfillsEverySkippedStage_whenFirstPollLandsLate() {
        setUp();
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(buildLateOrder()));
        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(buildStation()));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(buildVehicle()));
        // Nobody ever polled before: no milestones exist yet for this order.
        when(trackingEventRepository.findByOrderIdOrderByEventTimeAsc(ORDER_ID)).thenReturn(Collections.emptyList());

        TrackingResponse response = trackingService.trackOrder(ORDER_NUMBER);

        assertThat(response.getCurrentStage()).isEqualTo(TrackingStage.RETURNING);

        ArgumentCaptor<TrackingEvent> captor = ArgumentCaptor.forClass(TrackingEvent.class);
        verify(trackingEventRepository, times(3)).saveAndFlush(captor.capture());

        List<TrackingStage> savedStages = captor.getAllValues().stream()
                .map(TrackingEvent::getStage)
                .collect(Collectors.toList());

        // TO_PICKUP and TO_DROPOFF were skipped in real time but must still be
        // backfilled, in order, alongside the genuinely-current RETURNING stage.
        assertThat(savedStages).containsExactly(
                TrackingStage.TO_PICKUP,
                TrackingStage.TO_DROPOFF,
                TrackingStage.RETURNING
        );
    }

    @Test
    void trackOrder_doesNotReinsertStagesAlreadyRecorded() {
        setUp();
        when(orderRepository.findByOrderNumber(ORDER_NUMBER)).thenReturn(Optional.of(buildLateOrder()));
        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(buildStation()));
        when(vehicleRepository.findById(VEHICLE_ID)).thenReturn(Optional.of(buildVehicle()));

        // TO_PICKUP was already recorded (e.g. by OrderService at checkout time).
        TrackingEvent alreadyRecorded = TrackingEvent.builder()
                .orderId(ORDER_ID)
                .stage(TrackingStage.TO_PICKUP)
                .statusDescription("Order paid, vehicle dispatched.")
                .eventLat(new BigDecimal("37.7900000"))
                .eventLng(new BigDecimal("-122.4000000"))
                .eventTime(LocalDateTime.now().minusMinutes(20))
                .build();
        when(trackingEventRepository.findByOrderIdOrderByEventTimeAsc(ORDER_ID))
                .thenReturn(Collections.singletonList(alreadyRecorded));

        trackingService.trackOrder(ORDER_NUMBER);

        ArgumentCaptor<TrackingEvent> captor = ArgumentCaptor.forClass(TrackingEvent.class);
        // Only the two still-missing stages get written; TO_PICKUP is left alone.
        verify(trackingEventRepository, times(2)).saveAndFlush(captor.capture());

        List<TrackingStage> savedStages = captor.getAllValues().stream()
                .map(TrackingEvent::getStage)
                .collect(Collectors.toList());

        assertThat(savedStages).containsExactly(TrackingStage.TO_DROPOFF, TrackingStage.RETURNING);
    }
}
