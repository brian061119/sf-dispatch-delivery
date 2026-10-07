package com.wedelivery.service;

import com.wedelivery.entity.enums.VehicleType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public interface RouteService {

    /**
     * 计算两点间直线距离 (Haversine 公式，单位 km)
     */
    double calculateStraightDistance(double lat1, double lon1, double lat2, double lon2);

    /**
     * 计算单段航程距离（无人机直线，地面机器人 1.35x 曼哈顿系数）
     */
    double calculateSegmentDistance(double lat1, double lon1, double lat2, double lon2, VehicleType vehicleType);

    /**
     * 计算全闭环总里程 (Door-to-Door 或 Station-Pickup)
     */
    BigDecimal calculateClosedLoopDistance(
            BigDecimal stationLat, BigDecimal stationLng,
            BigDecimal pickupLat, BigDecimal pickupLng,
            BigDecimal dropoffLat, BigDecimal dropoffLng,
            boolean isStationPickup,
            VehicleType vehicleType
    );

    /**
     * 根据里程和巡航速度推算耗时 (分钟)
     */
    int estimateTravelTimeMinutes(double distanceKm, double cruiseSpeedKmH);

    /**
     * 三段航程（站→取件→送达→返站）按里程等比换算出的两个里程碑占比：
     * 返回 double[2]：
     *   [0] 取件完成占比 = 站→取件 / 整程（包裹上车的时间点）
     *   [1] 包裹送达占比 = (站→取件 + 取件→送达) / 整程（客户视角的「送达时刻」）
     * 整程时间线（含返站段）据此切分，各航段车辆匀速推进。
     *
     * <p>这里只做几何，<b>不做任何业务兜底</b>：顾客自投（取件点即站点）时首段为 0、
     * 占比返回 0 是真实情况；「占比为 0 时还能不能取消」属于取消政策，
     * 由 OrderService 的时间阈值决定，不在这里伪造里程。
     * 只有三段全为 0（坐标完全重合）时无法计算，退回旧的固定权重 0.25 / 0.75。
     */
    default double[] milestoneFractions(BigDecimal stationLat, BigDecimal stationLng,
                                        BigDecimal pickupLat, BigDecimal pickupLng,
                                        BigDecimal dropoffLat, BigDecimal dropoffLng,
                                        VehicleType vehicleType) {
        double legToPickup = calculateSegmentDistance(stationLat.doubleValue(), stationLng.doubleValue(),
                pickupLat.doubleValue(), pickupLng.doubleValue(), vehicleType);
        double legToDropoff = calculateSegmentDistance(pickupLat.doubleValue(), pickupLng.doubleValue(),
                dropoffLat.doubleValue(), dropoffLng.doubleValue(), vehicleType);
        double legReturning = calculateSegmentDistance(dropoffLat.doubleValue(), dropoffLng.doubleValue(),
                stationLat.doubleValue(), stationLng.doubleValue(), vehicleType);
        double total = legToPickup + legToDropoff + legReturning;
        if (total <= 0) {
            return new double[]{0.25, 0.75};
        }
        return new double[]{legToPickup / total, (legToPickup + legToDropoff) / total};
    }

    /**
     * 返回一段行程的沿道路几何折线，每个元素为 {lat, lng}。
     *
     * <p>默认实现（纯直线方案）只返回起终两点，画出来就是一条穿楼的直线。
     * 接入真实路网的实现（OSRM）会返回沿街道的密集折线，车辆沿此折线推进，
     * 地图上就不会再横穿房屋。
     *
     * @return 至少包含起点与终点的坐标列表；失败时退化为两点直线
     */
    default List<double[]> routeGeometry(double lat1, double lon1, double lat2, double lon2, VehicleType vehicleType) {
        return Arrays.asList(new double[]{lat1, lon1}, new double[]{lat2, lon2});
    }

    /**
     * 在一条折线上按行程比例 t (0.0~1.0) 取点，返回 {lat, lng}。
     *
     * <p>直线方案的折线只有两点，结果等价于线性插值；接入路网后车辆会沿着
     * 街道折线推进，而不是从起点直接穿到终点。
     *
     * @return 折线上的插值点；折线为空时返回 null
     */
    default double[] pointAlongRoute(List<double[]> geometry, double t) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (geometry.size() == 1) {
            return geometry.get(0);
        }
        double clamped = Math.max(0.0, Math.min(1.0, t));

        double[] cumulative = new double[geometry.size()];
        double total = 0.0;
        for (int i = 1; i < geometry.size(); i++) {
            double[] prev = geometry.get(i - 1);
            double[] curr = geometry.get(i);
            total += calculateStraightDistance(prev[0], prev[1], curr[0], curr[1]);
            cumulative[i] = total;
        }
        if (total <= 0.0) {
            return geometry.get(0);
        }

        double target = clamped * total;
        for (int i = 1; i < geometry.size(); i++) {
            if (target <= cumulative[i]) {
                double[] prev = geometry.get(i - 1);
                double[] curr = geometry.get(i);
                double segLen = cumulative[i] - cumulative[i - 1];
                double frac = segLen <= 0.0 ? 0.0 : (target - cumulative[i - 1]) / segLen;
                double lat = prev[0] + frac * (curr[0] - prev[0]);
                double lng = prev[1] + frac * (curr[1] - prev[1]);
                return new double[]{lat, lng};
            }
        }
        return geometry.get(geometry.size() - 1);
    }
}
