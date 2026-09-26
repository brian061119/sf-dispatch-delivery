package com.wedelivery;

import com.wedelivery.entity.Station;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.repository.StationRepository;
import com.wedelivery.repository.VehicleRepository;
import com.wedelivery.service.RouteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * demo 模拟器：代替真实心跳推进实时信息。写库，故整类回滚。
 *
 * 种子里的 ROBOT-SS-02 处于「配送中但无在途订单」，正好命中模拟器的返站分支；
 * DRONE-DT-03 处于充电中，命中补电分支。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("模拟器 · simulate/tick")
// 调动模块写接口仅限管理员 (见 SecurityConfig)，本类以管理员身份调用
@WithMockUser(roles = "ADMIN")
class DispatchSimulationApiTest {

    private static final String TICK = "/api/dispatch/simulate/tick";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private RouteService routeService;

    @Test
    @DisplayName("tick 让脱离任务的在途载具朝所属站点推进，并消耗电量")
    void tickMovesStrandedVehicleTowardStation() throws Exception {
        Vehicle before = requireVehicle("ROBOT-SS-02");
        assertThat(before.getStatus()).isEqualTo(VehicleStatus.IN_DELIVERY);

        double distanceBefore = distanceToOwnStation(before);
        BigDecimal batteryBefore = before.getBatteryLevel();

        mvc.perform(post(TICK))
                .andExpect(status().isOk())
                // 全程只在途、无订单，故尚未返抵站点
                .andExpect(jsonPath("$.returnedVehicles").value(0))
                .andExpect(jsonPath("$.vehicles", hasSize(15)));

        Vehicle after = requireVehicle("ROBOT-SS-02");
        assertThat(distanceToOwnStation(after)).isLessThan(distanceBefore);
        assertThat(after.getStatus()).isEqualTo(VehicleStatus.IN_DELIVERY);
        assertThat(after.getLocationCode()).isEqualTo(Vehicle.LOCATION_NOT_AT_STATION);
        assertThat(after.getCurrentSpeed()).isGreaterThan(BigDecimal.ZERO);
        assertThat(after.getBatteryLevel()).isLessThan(batteryBefore);
    }

    @Test
    @DisplayName("tick 给充电中的载具补电，未满电则保持充电状态")
    void tickChargesChargingVehicle() throws Exception {
        Vehicle before = requireVehicle("DRONE-DT-03");
        assertThat(before.getStatus()).isEqualTo(VehicleStatus.CHARGING);
        BigDecimal batteryBefore = before.getBatteryLevel();

        mvc.perform(post(TICK)).andExpect(status().isOk());

        Vehicle after = requireVehicle("DRONE-DT-03");
        assertThat(after.getBatteryLevel()).isGreaterThan(batteryBefore);
        assertThat(after.getStatus()).isEqualTo(VehicleStatus.CHARGING);
    }

    private Vehicle requireVehicle(String code) {
        return vehicleRepository.findByVehicleCode(code).orElseThrow(AssertionError::new);
    }

    private double distanceToOwnStation(Vehicle v) {
        Station station = stationRepository.findById(v.getStationId()).orElseThrow(AssertionError::new);
        return routeService.calculateStraightDistance(
                v.getCurrentLat().doubleValue(), v.getCurrentLng().doubleValue(),
                station.getLatitude().doubleValue(), station.getLongitude().doubleValue());
    }
}
