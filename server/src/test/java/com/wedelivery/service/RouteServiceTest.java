package com.wedelivery.service;

import com.wedelivery.entity.enums.VehicleType;
import com.wedelivery.service.impl.HaversineRouteServiceImpl;
import com.wedelivery.service.impl.OsrmRouteServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteServiceTest {

    private final RouteService straightLine = new HaversineRouteServiceImpl();

    /** 一条故意拐弯的折线：先向东走，再向北走。 */
    private List<double[]> bentGeometry() {
        return Arrays.asList(
                new double[]{37.7800, -122.4200},
                new double[]{37.7800, -122.4100},
                new double[]{37.7900, -122.4100});
    }

    @Test
    void straightLineProviderReturnsOnlyEndpoints() {
        List<double[]> geometry = straightLine.routeGeometry(37.78, -122.42, 37.79, -122.41, VehicleType.ROBOT);
        assertEquals(2, geometry.size(), "直线方案没有路网数据，只能给出起终两点");
    }

    @Test
    void pointAlongRouteReturnsEndpointsAtBoundaries() {
        List<double[]> geometry = bentGeometry();
        double[] start = straightLine.pointAlongRoute(geometry, 0.0);
        double[] end = straightLine.pointAlongRoute(geometry, 1.0);

        assertEquals(37.7800, start[0], 1e-6);
        assertEquals(-122.4200, start[1], 1e-6);
        assertEquals(37.7900, end[0], 1e-6);
        assertEquals(-122.4100, end[1], 1e-6);
    }

    /**
     * 核心回归：沿折线推进必须“拐弯”，不能是起终点之间的直线插值。
     * 在折线中点处，真实沿路点的纬度应明显高于直线插值点（后者会切过街区）。
     */
    @Test
    void pointAlongRouteFollowsTheGeometryInsteadOfCuttingCorners() {
        List<double[]> geometry = bentGeometry();
        double[] mid = straightLine.pointAlongRoute(geometry, 0.5);
        assertNotNull(mid);

        // 同一比例下的直线插值点
        double linearLat = 37.7800 + 0.5 * (37.7900 - 37.7800);
        double linearLng = -122.4200 + 0.5 * (-122.4100 - -122.4200);

        // 本质校验：沿折线走出来的点必须明显偏离「起终点直线插值」的那个点，
        // 偏离为 0 就说明又退化成穿楼直线了。
        double deviation = Math.abs(mid[0] - linearLat) + Math.abs(mid[1] - linearLng);
        assertTrue(deviation > 1e-3,
                "沿折线推进的点必须偏离直线插值点 (deviation=" + deviation + ")");

        // 按弧长走到一半时，较短的东西向路段已走完，经度应抵达拐点
        assertTrue(Math.abs(mid[1] - (-122.4100)) < 1e-3,
                "折线中点应已经走完整条向东的路段 (lng=" + mid[1] + ")");
    }

    @Test
    void pointAlongRouteClampsOutOfRangeProgress() {
        List<double[]> geometry = bentGeometry();
        double[] below = straightLine.pointAlongRoute(geometry, -0.5);
        double[] above = straightLine.pointAlongRoute(geometry, 1.5);

        assertEquals(37.7800, below[0], 1e-6);
        assertEquals(37.7900, above[0], 1e-6);
    }

    /**
     * 断网降级：OSRM 服务器不可达时必须退回直线估算，绝不能让下单/追踪报错。
     */
    @Test
    void osrmFallsBackToStraightLineWhenServerUnreachable() {
        OsrmRouteServiceImpl osrm = new OsrmRouteServiceImpl(
                new RestTemplateBuilder(), "http://127.0.0.1:1", true);

        double distance = osrm.calculateSegmentDistance(37.78, -122.42, 37.79, -122.41, VehicleType.ROBOT);
        double expected = new HaversineRouteServiceImpl()
                .calculateSegmentDistance(37.78, -122.42, 37.79, -122.41, VehicleType.ROBOT);

        assertTrue(distance > 0, "降级后仍要给出可用里程");
        assertEquals(expected, distance, 1e-6, "不可达时应完全退回父级的直线估算");

        List<double[]> geometry = osrm.routeGeometry(37.78, -122.42, 37.79, -122.41, VehicleType.ROBOT);
        assertEquals(2, geometry.size(), "降级后几何退化为两点直线");
    }

    @Test
    void droneKeepsFlyingStraightWhenRoutingDisabled() {
        OsrmRouteServiceImpl osrm = new OsrmRouteServiceImpl(
                new RestTemplateBuilder(), "http://127.0.0.1:1", false);

        // 无人机不走路网：即使 OSRM 可用，也应按直线计算
        double droneDistance = osrm.calculateSegmentDistance(37.78, -122.42, 37.79, -122.41, VehicleType.DRONE);
        double expected = new HaversineRouteServiceImpl()
                .calculateSegmentDistance(37.78, -122.42, 37.79, -122.41, VehicleType.DRONE);

        assertEquals(expected, droneDistance, 1e-6);
    }
}
