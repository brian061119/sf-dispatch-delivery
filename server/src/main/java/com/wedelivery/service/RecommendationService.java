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

/**
 * 三方案智能推荐 / 智能调动引擎。
 *
 * 载具准入以 machines 自身的基础信息为准（最大载荷、默认最大速度、续航），
 * 不使用类型级硬编码常量；类型默认值只在机器未上报能力字段时兜底（见 VehicleType）。
 *
 * 单台载具需同时通过三重准入：
 *   1. 载荷准入：包裹重量 / 体积不超过载具最大载荷（VIP 宽免 10%）
 *   2. 续航准入：闭环总里程不超过「续航 × 最大速度」的 90%（10% 续航冗余）
 *   3. 电量准入：载重加权预测耗电 ≤ 90% 且到站后剩余电量 ≥ 10%
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final StationRepository stationRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteService routeService;

    /** 计价模型（类型级，与《说明书》2.4 一致） */
    private static final Pricing DRONE_PRICING = new Pricing("15.00", "1.80", "2.00");
    private static final Pricing ROBOT_PRICING = new Pricing("6.00", "0.90", "0.80");

    private static final String DRONE_DESC = "无人机极速航程，不受地面拥堵限制，直线即刻送达。";
    private static final String ROBOT_DESC = "地面智能机器人经济配送，容量充足，性价比之选。";

    /** 续航冗余：最大可配送路程只允许用掉 90% */
    private static final double RANGE_SAFETY_RATIO = 0.9;
    /** 电量冗余：载重加权预测耗电上限（%）与返站后剩余电量下限（%） */
    private static final double MAX_ENERGY_DELTA_PERCENT = 90.0;
    private static final double MIN_RESERVE_BATTERY_PERCENT = 10.0;
    /** 载重对能耗的加权幅度 */
    private static final double LOAD_WEIGHT_FACTOR_SPAN = 0.2;

    private static final BigDecimal OFF_PEAK_DISCOUNT_RATE = new BigDecimal("0.15"); // 15% off
    private static final BigDecimal VIP_DISCOUNT_RATE = new BigDecimal("0.10"); // VIP 额外 9 折

    public QuoteResponse generateRecommendations(QuoteRequest request, User currentUser) {
        boolean isVip = currentUser != null && currentUser.getRole() == Role.VIP;
        List<Station> stations = stationRepository.findAll();
        List<PlanOptionDto> options = new ArrayList<>();

        if (stations.isEmpty()) {
            return QuoteResponse.builder().plans(options).isVipUser(isVip).build();
        }

        // 查找或指定最优服务分配站
        Station bestStation = selectBestStation(stations, request);

        // 1. 方案一：Fastest (无人机极速达)
        Candidate drone = evaluate(request, bestStation, VehicleType.DRONE, isVip);
        PlanOptionDto dronePlan = drone == null ? null : buildOption(request, bestStation, VehicleType.DRONE, drone, isVip);
        if (dronePlan != null) {
            options.add(dronePlan);
        }

        // 2. 方案二：Best Value (地面机器人经济达)
        Candidate robot = evaluate(request, bestStation, VehicleType.ROBOT, isVip);
        PlanOptionDto robotPlan = robot == null ? null : buildOption(request, bestStation, VehicleType.ROBOT, robot, isVip);
        if (robotPlan != null) {
            options.add(robotPlan);
        }

        // 3. 方案三：Off-Peak Eco (错峰低碳延时方案)，以经济达为基准，无则退化为极速达
        PlanOptionDto offPeakBase = robotPlan != null ? robotPlan : dronePlan;
        PlanOptionDto offPeakPlan = evaluateOffPeakOption(bestStation, offPeakBase, isVip);
        if (offPeakPlan != null) {
            options.add(offPeakPlan);
        }

        log.info("Recommendation for station {}: {} plan(s) generated", bestStation.getId(), options.size());
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

    /**
     * 对某站点某类型载具做全量准入筛选，返回可用载具数与被选中的那台。
     * 无任何载具通过准入时返回 null。
     */
    private Candidate evaluate(QuoteRequest req, Station station, VehicleType type, boolean isVip) {
        List<Vehicle> idle = vehicleRepository.findByStationIdAndVehicleTypeAndStatus(
                station.getId(), type, VehicleStatus.IDLE);
        if (idle.isEmpty()) {
            return null;
        }

        BigDecimal closedDistance = routeService.calculateClosedLoopDistance(
                station.getLatitude(), station.getLongitude(),
                req.getPickupLat(), req.getPickupLng(),
                req.getDropoffLat(), req.getDropoffLng(),
                Boolean.TRUE.equals(req.getIsStationPickup()),
                type
        );

        double weight = req.getPackageWeight().doubleValue();
        double volume = req.getPackageVolume().doubleValue();
        double vipTolerance = isVip ? 1.1 : 1.0;
        double distance = closedDistance.doubleValue();
        double energyRate = type.getEnergyRatePercentPerKm().doubleValue();

        List<Vehicle> admissible = new ArrayList<>();
        for (Vehicle v : idle) {
            double maxWeight = v.getMaxWeight().doubleValue();
            double maxVolume = v.getMaxVolume().doubleValue();
            if (maxWeight <= 0 || maxVolume <= 0) {
                continue;
            }

            // 1. 载荷准入
            if (weight > maxWeight * vipTolerance || volume > maxVolume * vipTolerance) {
                continue;
            }

            // 2. 续航准入：可配送路程由载具自身的续航时间与默认最大速度决定
            double rangeLimit = v.getMaxDeliverableDistanceKm().doubleValue() * RANGE_SAFETY_RATIO;
            if (distance > rangeLimit) {
                continue;
            }

            // 3. 电量准入（10% 安全冗余 + 载重加权能耗）
            double weightFactor = 1.0 + (weight / maxWeight) * LOAD_WEIGHT_FACTOR_SPAN;
            double energyDelta = distance * energyRate * weightFactor;
            if (energyDelta > MAX_ENERGY_DELTA_PERCENT) {
                continue;
            }
            if (v.getBatteryLevel().doubleValue() - energyDelta < MIN_RESERVE_BATTERY_PERCENT) {
                continue;
            }

            admissible.add(v);
        }

        if (admissible.isEmpty()) {
            return null;
        }

        Candidate candidate = new Candidate();
        candidate.closedDistance = closedDistance;
        candidate.admissibleCount = admissible.size();
        candidate.planned = admissible.stream()
                .max(Comparator.comparing(Vehicle::getBatteryLevel))
                .orElse(admissible.get(0));
        return candidate;
    }

    private PlanOptionDto buildOption(QuoteRequest req, Station station, VehicleType type,
                                      Candidate candidate, boolean isVip) {
        Pricing pricing = type == VehicleType.DRONE ? DRONE_PRICING : ROBOT_PRICING;
        BigDecimal closedDistance = candidate.closedDistance;

        BigDecimal originPrice = pricing.base
                .add(closedDistance.multiply(pricing.perKm))
                .add(req.getPackageWeight().multiply(pricing.perKg))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal discount = BigDecimal.ZERO;
        if (isVip) {
            discount = originPrice.multiply(VIP_DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal finalPrice = originPrice.subtract(discount);

        double speed = candidate.planned.getCruiseSpeed().doubleValue();
        int minutes = routeService.estimateTravelTimeMinutes(closedDistance.doubleValue() * 0.65, speed);
        LocalDateTime now = LocalDateTime.now();

        return PlanOptionDto.builder()
                .planType(type == VehicleType.DRONE ? PlanType.FASTEST : PlanType.BEST_VALUE)
                .vehicleType(type)
                .stationId(station.getId())
                .stationName(station.getName())
                .totalDistance(closedDistance)
                .estimatedMinutes(minutes)
                .originPrice(originPrice)
                .discountAmount(discount)
                .finalPrice(finalPrice)
                .scheduledStartTime(now)
                .estimatedDeliveryTime(now.plusMinutes(minutes))
                .vehicleCode(candidate.planned.getVehicleCode())
                .availableUnits(candidate.admissibleCount)
                .description(type == VehicleType.DRONE ? DRONE_DESC : ROBOT_DESC)
                .build();
    }

    private PlanOptionDto evaluateOffPeakOption(Station station, PlanOptionDto basePlan, boolean isVip) {
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
                .availableUnits(basePlan.getAvailableUnits())
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
                    .availableUnits(p.getAvailableUnits() != null ? p.getAvailableUnits() : 0)
                    .score(score)
                    .isFastest(isFastest)
                    .isCheapest(isCheapest)
                    .build());
        }

        return com.wedelivery.dto.RecommendationContractDto.Response.builder()
                .candidates(candidates)
                .build();
    }

    /** 某类型载具的筛选结果：闭环里程、通过准入的台数、被选中的那台 */
    private static class Candidate {
        private BigDecimal closedDistance;
        private int admissibleCount;
        private Vehicle planned;
    }

    /** 类型级计价参数 */
    private static class Pricing {
        private final BigDecimal base;
        private final BigDecimal perKm;
        private final BigDecimal perKg;

        private Pricing(String base, String perKm, String perKg) {
            this.base = new BigDecimal(base);
            this.perKm = new BigDecimal(perKm);
            this.perKg = new BigDecimal(perKg);
        }
    }
}
