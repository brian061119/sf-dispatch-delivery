package com.wedelivery.service;

import com.wedelivery.dto.PlanOptionDto;
import com.wedelivery.dto.QuoteRequest;
import com.wedelivery.dto.QuoteResponse;
import com.wedelivery.entity.Station;
import com.wedelivery.entity.User;
import com.wedelivery.entity.Vehicle;
import com.wedelivery.entity.enums.PlanType;
import com.wedelivery.entity.enums.Role;
import com.wedelivery.entity.enums.VehicleStatus;
import com.wedelivery.entity.enums.VehicleType;
import com.wedelivery.repository.StationRepository;
import com.wedelivery.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final StationRepository stationRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteService routeService;

    // 计价常量
    private static final BigDecimal DRONE_BASE_PRICE = new BigDecimal("15.00");
    private static final BigDecimal DRONE_PER_KM = new BigDecimal("1.80");
    private static final BigDecimal DRONE_PER_KG = new BigDecimal("2.00");
    private static final double DRONE_ENERGY_RATE = 4.0; // 4% / km
    private static final double DRONE_MAX_WEIGHT = 3.0;
    private static final double DRONE_MAX_VOLUME = 0.05;

    private static final BigDecimal ROBOT_BASE_PRICE = new BigDecimal("6.00");
    private static final BigDecimal ROBOT_PER_KM = new BigDecimal("0.90");
    private static final BigDecimal ROBOT_PER_KG = new BigDecimal("0.80");
    private static final double ROBOT_ENERGY_RATE = 2.0; // 2% / km
    private static final double ROBOT_MAX_WEIGHT = 15.0;
    private static final double ROBOT_MAX_VOLUME = 0.30;

    private static final BigDecimal OFF_PEAK_DISCOUNT_RATE = new BigDecimal("0.15"); // 15% off
    private static final BigDecimal VIP_DISCOUNT_RATE = new BigDecimal("0.10"); // VIP 额外 9 折 (10% off)

    public QuoteResponse generateRecommendations(QuoteRequest request, User currentUser) {
        boolean isVip = currentUser != null && currentUser.getRole() == Role.VIP;
        List<Station> stations = stationRepository.findAll();
        List<PlanOptionDto> options = new ArrayList<>();

        if (stations.isEmpty()) {
            return QuoteResponse.builder().plans(options).isVipUser(isVip).build();
        }

        // 查找或指定最优服务分配站
        Station bestStation = selectBestStation(stations, request);

        // 1. 评估方案一：Fastest (无人机极速达)
        PlanOptionDto dronePlan = evaluateDroneOption(request, bestStation, isVip);
        if (dronePlan != null) {
            options.add(dronePlan);
        }

        // 2. 评估方案二：Best Value (地面机器人经济达)
        PlanOptionDto robotPlan = evaluateRobotOption(request, bestStation, isVip);
        if (robotPlan != null) {
            options.add(robotPlan);
        }

        // 3. 评估方案三：Off-Peak Eco (错峰低碳延时方案)
        PlanOptionDto offPeakPlan = evaluateOffPeakOption(request, bestStation, robotPlan, isVip);
        if (offPeakPlan != null) {
            options.add(offPeakPlan);
        }

        return QuoteResponse.builder()
                .plans(options)
                .isVipUser(isVip)
                .vipDiscountRate(isVip ? VIP_DISCOUNT_RATE : BigDecimal.ZERO)
                .build();
    }

    private Station selectBestStation(List<Station> stations, QuoteRequest request) {
        if (Boolean.TRUE.equals(request.getIsStationPickup()) && request.getStationId() != null) {
            return stations.stream()
                    .filter(s -> s.getId().equals(request.getStationId()))
                    .findFirst()
                    .orElse(stations.get(0));
        }

        // 寻找离取件地最近的站点
        double pLat = request.getPickupLat().doubleValue();
        double pLng = request.getPickupLng().doubleValue();

        return stations.stream()
                .min(Comparator.comparingDouble(s ->
                        routeService.calculateStraightDistance(
                                s.getLatitude().doubleValue(), s.getLongitude().doubleValue(),
                                pLat, pLng
                        )
                ))
                .orElse(stations.get(0));
    }

    private PlanOptionDto evaluateDroneOption(QuoteRequest req, Station station, boolean isVip) {
        double weight = req.getPackageWeight().doubleValue();
        double volume = req.getPackageVolume().doubleValue();

        // 约束检查 (VIP 容差 10%)
        double maxW = isVip ? DRONE_MAX_WEIGHT * 1.1 : DRONE_MAX_WEIGHT;
        double maxV = isVip ? DRONE_MAX_VOLUME * 1.1 : DRONE_MAX_VOLUME;
        if (weight > maxW || volume > maxV) {
            return null;
        }

        BigDecimal closedDistance = routeService.calculateClosedLoopDistance(
                station.getLatitude(), station.getLongitude(),
                req.getPickupLat(), req.getPickupLng(),
                req.getDropoffLat(), req.getDropoffLng(),
                Boolean.TRUE.equals(req.getIsStationPickup()),
                VehicleType.DRONE
        );

        // 校验 10% 电量冗余模型
        double weightFactor = 1.0 + (weight / DRONE_MAX_WEIGHT) * 0.2;
        double energyDelta = closedDistance.doubleValue() * DRONE_ENERGY_RATE * weightFactor;

        if (energyDelta > 90.0) {
            return null; // 超出 90% 能耗上限
        }

        List<Vehicle> availableDrones = vehicleRepository.findByStationIdAndVehicleTypeAndStatus(
                station.getId(), VehicleType.DRONE, VehicleStatus.IDLE
        );

        // 筛选电量满足：当前电量 - 预测耗电 >= 10%
        Vehicle matchedDrone = availableDrones.stream()
                .filter(v -> v.getBatteryLevel().doubleValue() - energyDelta >= 10.0)
                .max(Comparator.comparing(Vehicle::getBatteryLevel))
                .orElse(null);

        if (matchedDrone == null) {
            return null;
        }

        // 计算价格: $15 + dist*1.8 + weight*2.0
        BigDecimal originPrice = DRONE_BASE_PRICE
                .add(closedDistance.multiply(DRONE_PER_KM))
                .add(req.getPackageWeight().multiply(DRONE_PER_KG))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = BigDecimal.ZERO;
        if (isVip) {
            discount = originPrice.multiply(VIP_DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal finalPrice = originPrice.subtract(discount);

        double speed = matchedDrone.getCruiseSpeed().doubleValue();
        int minutes = routeService.estimateTravelTimeMinutes(closedDistance.doubleValue() * 0.65, speed);
        LocalDateTime now = LocalDateTime.now();

        return PlanOptionDto.builder()
                .planType(PlanType.FASTEST)
                .vehicleType(VehicleType.DRONE)
                .stationId(station.getId())
                .stationName(station.getName())
                .totalDistance(closedDistance)
                .estimatedMinutes(minutes)
                .originPrice(originPrice)
                .discountAmount(discount)
                .finalPrice(finalPrice)
                .scheduledStartTime(now)
                .estimatedDeliveryTime(now.plusMinutes(minutes))
                .vehicleCode(matchedDrone.getVehicleCode())
                .description("无人机极速航程，不受地面拥堵限制，直线即刻送达。")
                .build();
    }

    private PlanOptionDto evaluateRobotOption(QuoteRequest req, Station station, boolean isVip) {
        double weight = req.getPackageWeight().doubleValue();
        double volume = req.getPackageVolume().doubleValue();

        double maxW = isVip ? ROBOT_MAX_WEIGHT * 1.1 : ROBOT_MAX_WEIGHT;
        double maxV = isVip ? ROBOT_MAX_VOLUME * 1.1 : ROBOT_MAX_VOLUME;
        if (weight > maxW || volume > maxV) {
            return null;
        }

        BigDecimal closedDistance = routeService.calculateClosedLoopDistance(
                station.getLatitude(), station.getLongitude(),
                req.getPickupLat(), req.getPickupLng(),
                req.getDropoffLat(), req.getDropoffLng(),
                Boolean.TRUE.equals(req.getIsStationPickup()),
                VehicleType.ROBOT
        );

        double weightFactor = 1.0 + (weight / ROBOT_MAX_WEIGHT) * 0.2;
        double energyDelta = closedDistance.doubleValue() * ROBOT_ENERGY_RATE * weightFactor;

        if (energyDelta > 90.0) {
            return null;
        }

        List<Vehicle> availableRobots = vehicleRepository.findByStationIdAndVehicleTypeAndStatus(
                station.getId(), VehicleType.ROBOT, VehicleStatus.IDLE
        );

        Vehicle matchedRobot = availableRobots.stream()
                .filter(v -> v.getBatteryLevel().doubleValue() - energyDelta >= 10.0)
                .max(Comparator.comparing(Vehicle::getBatteryLevel))
                .orElse(null);

        if (matchedRobot == null) {
            return null;
        }

        // 计算价格: $6 + dist*0.9 + weight*0.8
        BigDecimal originPrice = ROBOT_BASE_PRICE
                .add(closedDistance.multiply(ROBOT_PER_KM))
                .add(req.getPackageWeight().multiply(ROBOT_PER_KG))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = BigDecimal.ZERO;
        if (isVip) {
            discount = originPrice.multiply(VIP_DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal finalPrice = originPrice.subtract(discount);

        double speed = matchedRobot.getCruiseSpeed().doubleValue();
        int minutes = routeService.estimateTravelTimeMinutes(closedDistance.doubleValue() * 0.65, speed);
        LocalDateTime now = LocalDateTime.now();

        return PlanOptionDto.builder()
                .planType(PlanType.BEST_VALUE)
                .vehicleType(VehicleType.ROBOT)
                .stationId(station.getId())
                .stationName(station.getName())
                .totalDistance(closedDistance)
                .estimatedMinutes(minutes)
                .originPrice(originPrice)
                .discountAmount(discount)
                .finalPrice(finalPrice)
                .scheduledStartTime(now)
                .estimatedDeliveryTime(now.plusMinutes(minutes))
                .vehicleCode(matchedRobot.getVehicleCode())
                .description("地面智能机器人经济配送，容量充足，性价比之选。")
                .build();
    }

    private PlanOptionDto evaluateOffPeakOption(QuoteRequest req, Station station, PlanOptionDto basePlan, boolean isVip) {
        if (basePlan == null) {
            return null;
        }

        BigDecimal originPrice = basePlan.getOriginPrice();
        BigDecimal offPeakDiscount = originPrice.multiply(OFF_PEAK_DISCOUNT_RATE);
        BigDecimal discount = offPeakDiscount;

        if (isVip) {
            // 叠加 VIP 9 折
            BigDecimal afterOffPeak = originPrice.subtract(offPeakDiscount);
            BigDecimal vipDiscount = afterOffPeak.multiply(VIP_DISCOUNT_RATE);
            discount = discount.add(vipDiscount);
        }
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalPrice = originPrice.subtract(discount).setScale(2, RoundingMode.HALF_UP);

        LocalDateTime scheduledStart = LocalDateTime.now().plusHours(1);
        LocalDateTime estimatedDelivery = scheduledStart.plusMinutes(basePlan.getEstimatedMinutes());

        return PlanOptionDto.builder()
                .planType(PlanType.OFF_PEAK)
                .vehicleType(basePlan.getVehicleType())
                .stationId(station.getId())
                .stationName(station.getName())
                .totalDistance(basePlan.getTotalDistance())
                .estimatedMinutes(basePlan.getEstimatedMinutes() + 60)
                .originPrice(originPrice)
                .discountAmount(discount)
                .finalPrice(finalPrice)
                .scheduledStartTime(scheduledStart)
                .estimatedDeliveryTime(estimatedDelivery)
                .vehicleCode(basePlan.getVehicleCode())
                .description("错峰出行低碳专享：计划延后 1 小时启动，享 15% 运费折扣。")
                .build();
    }

    @SuppressWarnings("unchecked")
    public com.wedelivery.dto.RecommendationContractDto.Response generateContractRecommendations(
            java.util.Map<String, Object> body, User currentUser
    ) {
        // 从契约 payload 提取
        java.util.Map<String, Object> pickup = (java.util.Map<String, Object>) body.getOrDefault("pickup", java.util.Collections.emptyMap());
        java.util.Map<String, Object> dropoff = (java.util.Map<String, Object>) body.getOrDefault("dropoff", java.util.Collections.emptyMap());
        java.util.Map<String, Object> pkg = (java.util.Map<String, Object>) body.getOrDefault("package", java.util.Collections.emptyMap());

        BigDecimal pLat = pickup.get("lat") != null ? new BigDecimal(pickup.get("lat").toString()) : new BigDecimal("37.7858");
        BigDecimal pLng = pickup.get("lng") != null ? new BigDecimal(pickup.get("lng").toString()) : new BigDecimal("-122.4065");
        BigDecimal dLat = dropoff.get("lat") != null ? new BigDecimal(dropoff.get("lat").toString()) : new BigDecimal("37.7596");
        BigDecimal dLng = dropoff.get("lng") != null ? new BigDecimal(dropoff.get("lng").toString()) : new BigDecimal("-122.4269");

        BigDecimal weight = pkg.get("weightKg") != null ? new BigDecimal(pkg.get("weightKg").toString()) : new BigDecimal("1.5");
        BigDecimal vol = new BigDecimal("0.02");
        if (pkg.get("lengthCm") != null && pkg.get("widthCm") != null && pkg.get("heightCm") != null) {
            double l = Double.parseDouble(pkg.get("lengthCm").toString()) / 100.0;
            double w = Double.parseDouble(pkg.get("widthCm").toString()) / 100.0;
            double h = Double.parseDouble(pkg.get("heightCm").toString()) / 100.0;
            vol = BigDecimal.valueOf(l * w * h).setScale(4, RoundingMode.HALF_UP);
        }

        QuoteRequest quoteReq = QuoteRequest.builder()
                .pickupAddress((String) pickup.getOrDefault("line1", "San Francisco Pickup"))
                .pickupLat(pLat)
                .pickupLng(pLng)
                .dropoffAddress((String) dropoff.getOrDefault("line1", "San Francisco Dropoff"))
                .dropoffLat(dLat)
                .dropoffLng(dLng)
                .packageWeight(weight)
                .packageVolume(vol)
                .build();

        QuoteResponse quoteRes = generateRecommendations(quoteReq, currentUser);
        List<PlanOptionDto> plans = quoteRes.getPlans();

        if (plans.isEmpty()) {
            return com.wedelivery.dto.RecommendationContractDto.Response.builder()
                    .candidates(java.util.Collections.emptyList())
                    .build();
        }

        // 计算最低价格和最短耗时
        BigDecimal minCost = plans.stream().map(PlanOptionDto::getFinalPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        int minTime = plans.stream().mapToInt(PlanOptionDto::getEstimatedMinutes).min().orElse(999);

        List<com.wedelivery.dto.RecommendationContractDto.CandidateDto> candidates = new ArrayList<>();
        for (PlanOptionDto p : plans) {
            boolean isCheapest = p.getFinalPrice().compareTo(minCost) == 0;
            boolean isFastest = p.getEstimatedMinutes() == minTime;
            double score = (isFastest ? 50.0 : 30.0) + (isCheapest ? 50.0 : 30.0);

            candidates.add(com.wedelivery.dto.RecommendationContractDto.CandidateDto.builder()
                    .candidateId("CAND-" + p.getPlanType().name())
                    .stationId(String.valueOf(p.getStationId()))
                    .stationName(p.getStationName())
                    .vehicleType(p.getVehicleType().name())
                    .estimatedTimeMinutes(p.getEstimatedMinutes())
                    .estimatedCost(p.getFinalPrice())
                    .availableUnits(2)
                    .score(score)
                    .isFastest(isFastest)
                    .isCheapest(isCheapest)
                    .build());
        }

        return com.wedelivery.dto.RecommendationContractDto.Response.builder()
                .candidates(candidates)
                .build();
    }
}
