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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 三方案智能推荐 / 智能调动引擎。
 *
 * 载具准入以 machines 自身的基础信息为准（最大载荷、默认最大速度、续航），
 * 不使用类型级硬编码常量；类型默认值只在机器未上报能力字段时兜底（见 VehicleType）。
 *
 * 站点选择：按「站点→起点 + 站点→终点」的直线距离之和升序，作为「耗时最短优先」的候选顺序；
 *          再按顾客所需载具类型逐站查找首个可派发该类型的站点，耗时最短的站点无可用载具时
 *          自动退到次优站点，以此类推（顾客自投并指定站点时除外）。
 *
 * 单台载具需同时通过三重准入：
 *   1. 载荷准入：包裹重量 / 体积不超过载具最大载荷（VIP 宽免 10%）
 *   2. 续航准入：闭环总里程不超过「续航 × 最大速度」的 90%（10% 续航冗余）
 *   3. 电量准入：载重加权预测耗电 ≤ 90% 且到站后剩余电量 ≥ 10%
 *
 * 候选池为站内「待命 + 充电」的载具：充电中的载具只要当前电量扣除全程耗电后仍 ≥ 10%
 * 即可被临时抽调派单（充电可随时中断）。同类型准入全部满足时，优先派电量最低的一台，
 * 让电量高的留在站里待命，均衡整队电量。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final StationRepository stationRepository;
    private final VehicleRepository vehicleRepository;
    private final RouteService routeService;

    /** 派单候选状态：待命与充电中的载具都可被抽调。 */
    public static final List<VehicleStatus> DISPATCHABLE_STATUSES =
            Collections.unmodifiableList(Arrays.asList(VehicleStatus.IDLE, VehicleStatus.CHARGING));

    /** 计价模型（类型级，与《说明书》2.4 一致） */
    private static final Pricing DRONE_PRICING = new Pricing("15.00", "1.80", "2.00");
    private static final Pricing ROBOT_PRICING = new Pricing("6.00", "0.90", "0.80");

    private static final String DRONE_DESC = "无人机极速航程，不受地面拥堵限制，直线即刻送达。";
    private static final String ROBOT_DESC = "地面智能机器人经济配送，容量充足，性价比之选。";

    /** 续航冗余：最大可配送路程只允许用掉 90% */
    private static final double RANGE_SAFETY_RATIO = 0.9;
    /** 电量冗余：载重加权预测耗电上限（%）与返站后剩余电量下限（%），安全值不低于 10% */
    private static final double MAX_ENERGY_DELTA_PERCENT = 90.0;
    private static final double MIN_RESERVE_BATTERY_PERCENT = 10.0;
    /** 载重对能耗的加权幅度 */
    private static final double LOAD_WEIGHT_FACTOR_SPAN = 0.2;

    private static final BigDecimal OFF_PEAK_DISCOUNT_RATE = new BigDecimal("0.15"); // 15% off
    private static final BigDecimal VIP_DISCOUNT_RATE = new BigDecimal("0.10"); // VIP 额外 9 折

    /**
     * 载重对能耗的加权系数：空载为 1.0，满载为 1 + LOAD_WEIGHT_FACTOR_SPAN。
     * 派单准入与行程实际扣电共用同一系数，保证「预测耗电」与「真实耗电」一致。
     */
    public static double loadWeightFactor(double packageWeight, double maxWeight) {
        if (maxWeight <= 0) {
            return 1.0;
        }
        return 1.0 + (packageWeight / maxWeight) * LOAD_WEIGHT_FACTOR_SPAN;
    }

    /**
     * Calculates the minimum initial battery level required for a vehicle to execute
     * the closed-loop delivery and retain at least MIN_RESERVE_BATTERY_PERCENT (10%) safety reserve.
     */
    public BigDecimal calculateRequiredInitialBattery(BigDecimal closedDistance, VehicleType vehicleType, BigDecimal packageWeight) {
        if (closedDistance == null || vehicleType == null) {
            return BigDecimal.valueOf(MIN_RESERVE_BATTERY_PERCENT).setScale(2, RoundingMode.HALF_UP);
        }
        double distance = closedDistance.doubleValue();
        double energyRate = vehicleType.getEnergyRatePercentPerKm().doubleValue();
        double weight = packageWeight != null ? packageWeight.doubleValue() : 1.5;
        double maxWeight = vehicleType.getDefaultMaxWeight().doubleValue();
        double weightFactor = loadWeightFactor(weight, maxWeight);
        double energyDelta = distance * energyRate * weightFactor;
        double minRequired = energyDelta + MIN_RESERVE_BATTERY_PERCENT;
        return BigDecimal.valueOf(minRequired).setScale(2, RoundingMode.HALF_UP);
    }

    public QuoteResponse generateRecommendations(QuoteRequest request, User currentUser) {
        boolean isVip = currentUser != null && currentUser.getRole() == Role.VIP;
        List<Station> stations = stationRepository.findAll();
        List<PlanOptionDto> options = new ArrayList<>();

        if (stations.isEmpty()) {
            return QuoteResponse.builder().plans(options).isVipUser(isVip).build();
        }

        // 候选站点：自选站点时仅该站点；否则按「站点→起点 + 站点→终点」距离之和升序
        List<Station> candidateStations = candidateStations(stations, request);

        // 1. 方案一：Fastest (无人机极速达)，逐站寻找首个可派发无人机的站点
        Candidate drone = selectForType(candidateStations, request, VehicleType.DRONE, isVip);
        PlanOptionDto dronePlan = drone == null ? null : buildOption(request, VehicleType.DRONE, drone, isVip);
        if (dronePlan != null) {
            options.add(dronePlan);
        }

        // 2. 方案二：Best Value (地面机器人经济达)，逐站寻找首个可派发机器人的站点
        Candidate robot = selectForType(candidateStations, request, VehicleType.ROBOT, isVip);
        PlanOptionDto robotPlan = robot == null ? null : buildOption(request, VehicleType.ROBOT, robot, isVip);
        if (robotPlan != null) {
            options.add(robotPlan);
        }

        // 3. 方案三：Off-Peak Eco (错峰低碳延时方案)，以经济达为基准，无则退化为极速达
        PlanOptionDto offPeakBase = robotPlan != null ? robotPlan : dronePlan;
        PlanOptionDto offPeakPlan = evaluateOffPeakOption(offPeakBase, isVip);
        if (offPeakPlan != null) {
            options.add(offPeakPlan);
        }

        log.info("Recommendation generated: {} plan(s)", options.size());
        return QuoteResponse.builder()
                .plans(options)
                .isVipUser(isVip)
                .vipDiscountRate(isVip ? VIP_DISCOUNT_RATE : BigDecimal.ZERO)
                .build();
    }

    /**
     * 候选服务分配站：
     *   1. 顾客自投并指定站点时，仅该站点参与；
     *   2. 否则按「站点→起点 + 站点→终点」直线距离之和升序排列，
     *      作为「耗时最短优先」的候选顺序，供逐站兜底查找。
     */
    private List<Station> candidateStations(List<Station> stations, QuoteRequest request) {
        if (Boolean.TRUE.equals(request.getIsStationPickup()) && request.getStationId() != null) {
            List<Station> forced = stations.stream()
                    .filter(s -> s.getId().equals(request.getStationId()))
                    .collect(Collectors.toList());
            if (!forced.isEmpty()) {
                return forced;
            }
        }

        double pLat = request.getPickupLat().doubleValue();
        double pLng = request.getPickupLng().doubleValue();
        double dLat = request.getDropoffLat().doubleValue();
        double dLng = request.getDropoffLng().doubleValue();

        return stations.stream()
                .sorted(Comparator.comparingDouble(s -> distanceToTrip(s, pLat, pLng, dLat, dLng)))
                .collect(Collectors.toList());
    }

    /** 站点到起点与终点的直线距离之和（km）——配送站耗时排序的判断标准 */
    private double distanceToTrip(Station station, double pickupLat, double pickupLng,
                                  double dropoffLat, double dropoffLng) {
        double lat = station.getLatitude().doubleValue();
        double lng = station.getLongitude().doubleValue();
        return routeService.calculateStraightDistance(lat, lng, pickupLat, pickupLng)
                + routeService.calculateStraightDistance(lat, lng, dropoffLat, dropoffLng);
    }

    /**
     * 按候选顺序逐站做该类型载具的全量准入，返回首个能派发的站点筛选结果；
     * 耗时最短的站点无可用载具时自动退到次优站点，全部站点都不可派发时返回 null。
     */
    private Candidate selectForType(List<Station> candidateStations, QuoteRequest req,
                                    VehicleType type, boolean isVip) {
        for (Station station : candidateStations) {
            Candidate candidate = evaluate(req, station, type, isVip);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 对某站点某类型载具做全量准入筛选，返回可用载具数与被选中的那台。
     * 无任何载具通过准入时返回 null。候选含待命与充电中的载具。
     */
    private Candidate evaluate(QuoteRequest req, Station station, VehicleType type, boolean isVip) {
        List<Vehicle> candidates = vehicleRepository.findByStationIdAndVehicleTypeAndStatusIn(
                station.getId(), type, DISPATCHABLE_STATUSES);
        if (candidates.isEmpty()) {
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
        for (Vehicle v : candidates) {
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

            // 3. 电量准入（全程载重加权预测耗电，到站后须留 ≥ 10% 安全电量）
            double weightFactor = loadWeightFactor(weight, maxWeight);
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
        candidate.station = station;
        candidate.closedDistance = closedDistance;
        candidate.admissibleCount = admissible.size();
        // 起点、终点、站点已定的前提下，优先派电量最低的一台，让高电量载具留站兜底
        candidate.planned = admissible.stream()
                .min(Comparator.comparing(Vehicle::getBatteryLevel))
                .orElse(admissible.get(0));
        return candidate;
    }

    private PlanOptionDto buildOption(QuoteRequest req, VehicleType type,
                                      Candidate candidate, boolean isVip) {
        Pricing pricing = type == VehicleType.DRONE ? DRONE_PRICING : ROBOT_PRICING;
        Station station = candidate.station;
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

    private PlanOptionDto evaluateOffPeakOption(PlanOptionDto basePlan, boolean isVip) {
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
                .stationId(basePlan.getStationId())
                .stationName(basePlan.getStationName())
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
        QuoteRequest quoteReq = buildContractQuoteRequest(body);
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
                    .candidateId(candidateIdOf(p))
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

    /**
     * 下单时在服务端按同一份请求重新计算推荐，返回用户所选的方案。
     * 价格、站点、载具类型、里程与时间均以服务端计算为准，不信任客户端传值；
     * 所选方案当前已不可用（例如载具刚被订走）时返回 empty。
     */
    public java.util.Optional<SelectedPlan> resolveContractCandidate(
            java.util.Map<String, Object> body, User currentUser, String candidateId
    ) {
        QuoteRequest quoteReq = buildContractQuoteRequest(body);
        return generateRecommendations(quoteReq, currentUser).getPlans().stream()
                .filter(p -> candidateIdOf(p).equals(candidateId))
                .findFirst()
                .map(p -> new SelectedPlan(quoteReq, p));
    }

    /** 下单所选方案：计算推荐时使用的请求 + 对应方案 */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class SelectedPlan {
        private final QuoteRequest request;
        private final PlanOptionDto plan;
    }

    private static String candidateIdOf(PlanOptionDto plan) {
        return "CAND-" + plan.getPlanType().name();
    }

    public static boolean isWithinSanFrancisco(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) return false;
        double dLat = lat.doubleValue();
        double dLng = lng.doubleValue();
        return dLat >= 37.708 && dLat <= 37.84 && dLng >= -122.53 && dLng <= -122.35;
    }

    /** 从契约 payload (pickup / dropoff / package) 构造推荐请求 */
    private QuoteRequest buildContractQuoteRequest(java.util.Map<String, Object> body) {
        // 从契约 payload 提取
        java.util.Map<String, Object> pickup = (java.util.Map<String, Object>) body.getOrDefault("pickup", java.util.Collections.emptyMap());
        java.util.Map<String, Object> dropoff = (java.util.Map<String, Object>) body.getOrDefault("dropoff", java.util.Collections.emptyMap());
        java.util.Map<String, Object> pkg = (java.util.Map<String, Object>) body.getOrDefault("package", java.util.Collections.emptyMap());

        if (pickup.get("lat") == null || pickup.get("lng") == null) {
            throw new IllegalArgumentException("Pickup coordinates (lat, lng) are required.");
        }
        if (dropoff.get("lat") == null || dropoff.get("lng") == null) {
            throw new IllegalArgumentException("Dropoff coordinates (lat, lng) are required.");
        }

        BigDecimal pLat = new BigDecimal(pickup.get("lat").toString());
        BigDecimal pLng = new BigDecimal(pickup.get("lng").toString());
        BigDecimal dLat = new BigDecimal(dropoff.get("lat").toString());
        BigDecimal dLng = new BigDecimal(dropoff.get("lng").toString());

        if (!isWithinSanFrancisco(pLat, pLng) || !isWithinSanFrancisco(dLat, dLng)) {
            throw new IllegalArgumentException("Pickup or destination address is outside the San Francisco service area.");
        }

        BigDecimal weight = pkg.get("weightKg") != null ? new BigDecimal(pkg.get("weightKg").toString()) : new BigDecimal("1.5");
        BigDecimal vol = new BigDecimal("0.02");
        if (pkg.get("lengthCm") != null && pkg.get("widthCm") != null && pkg.get("heightCm") != null) {
            double l = Double.parseDouble(pkg.get("lengthCm").toString()) / 100.0;
            double w = Double.parseDouble(pkg.get("widthCm").toString()) / 100.0;
            double h = Double.parseDouble(pkg.get("heightCm").toString()) / 100.0;
            vol = BigDecimal.valueOf(l * w * h).setScale(4, RoundingMode.HALF_UP);
        }

        return QuoteRequest.builder()
                .pickupAddress((String) pickup.getOrDefault("line1", "San Francisco Pickup"))
                .pickupLat(pLat)
                .pickupLng(pLng)
                .dropoffAddress((String) dropoff.getOrDefault("line1", "San Francisco Dropoff"))
                .dropoffLat(dLat)
                .dropoffLng(dLng)
                .packageWeight(weight)
                .packageVolume(vol)
                .build();
    }

    /** 某类型载具的筛选结果：所在站点、闭环里程、通过准入的台数、被选中的那台 */
    private static class Candidate {
        private Station station;
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
