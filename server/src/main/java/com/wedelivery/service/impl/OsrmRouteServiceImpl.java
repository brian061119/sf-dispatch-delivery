package com.wedelivery.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedelivery.entity.enums.VehicleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接入真实路网（OSRM）的路径服务：地面机器人沿街道行驶，不再横穿房屋。
 *
 * <p>设计要点：
 * <ul>
 *   <li>继承 {@link HaversineRouteServiceImpl}，任何一次网络失败 / 解析失败都
 *       自动退回父级的直线估算，保证 demo 在断网时依然可跑。</li>
 *   <li>结果按「起点+终点+载具类型」缓存到内存，避免模拟器每 10 秒重复打外网。</li>
 *   <li>无人机 (DRONE) 本来就直线飞行，不走路网，直接走父级直线逻辑。</li>
 * </ul>
 *
 * <p>切换：{@code wedelivery.routing.provider=osrm|haversine}（默认 osrm）。
 */
@Service
@ConditionalOnProperty(name = "wedelivery.routing.provider", havingValue = "osrm", matchIfMissing = true)
public class OsrmRouteServiceImpl extends HaversineRouteServiceImpl {

    private static final Logger log = LoggerFactory.getLogger(OsrmRouteServiceImpl.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;
    private final boolean enabled;

    /** 缓存 key = "profile|lat1,lng1|lat2,lng2"（坐标取 4 位小数 ≈ 11 米精度） */
    private final Map<String, OsrmRoute> cache = new ConcurrentHashMap<>();

    public OsrmRouteServiceImpl(
            RestTemplateBuilder builder,
            @Value("${wedelivery.routing.osrm-base-url:https://router.project-osrm.org}") String baseUrl,
            @Value("${wedelivery.routing.enabled:true}") boolean enabled
    ) {
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public double calculateSegmentDistance(double lat1, double lon1, double lat2, double lon2, VehicleType vehicleType) {
        if (!useRoadNetwork(vehicleType)) {
            return super.calculateSegmentDistance(lat1, lon1, lat2, lon2, vehicleType);
        }
        OsrmRoute route = fetchRoute(lat1, lon1, lat2, lon2);
        if (route == null) {
            return super.calculateSegmentDistance(lat1, lon1, lat2, lon2, vehicleType);
        }
        return route.distanceKm;
    }

    @Override
    public List<double[]> routeGeometry(double lat1, double lon1, double lat2, double lon2, VehicleType vehicleType) {
        if (!useRoadNetwork(vehicleType)) {
            return super.routeGeometry(lat1, lon1, lat2, lon2, vehicleType);
        }
        OsrmRoute route = fetchRoute(lat1, lon1, lat2, lon2);
        if (route == null || route.geometry.isEmpty()) {
            return super.routeGeometry(lat1, lon1, lat2, lon2, vehicleType);
        }
        return route.geometry;
    }

    private boolean useRoadNetwork(VehicleType vehicleType) {
        return enabled && vehicleType == VehicleType.ROBOT;
    }

    private OsrmRoute fetchRoute(double lat1, double lon1, double lat2, double lon2) {
        String key = String.format("driving|%.4f,%.4f|%.4f,%.4f", lat1, lon1, lat2, lon2);
        OsrmRoute cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        String url = String.format(
                "%s/route/v1/driving/%s,%s;%s,%s?overview=full&geometries=geojson",
                baseUrl, trim(lon1), trim(lat1), trim(lon2), trim(lat2));
        try {
            String body = restTemplate.getForObject(url, String.class);
            if (body == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(body);
            if (!"Ok".equals(root.path("code").asText())) {
                log.warn("OSRM returned non-Ok code for {}: {}", key, root.path("code").asText());
                return null;
            }
            JsonNode routeNode = root.path("routes").path(0);
            double meters = routeNode.path("distance").asDouble(0);
            if (meters <= 0) {
                return null;
            }
            List<double[]> geometry = new ArrayList<>();
            for (JsonNode coord : routeNode.path("geometry").path("coordinates")) {
                // GeoJSON 顺序为 [经度, 纬度]，内部统一按 {lat, lng} 存放
                geometry.add(new double[]{coord.path(1).asDouble(), coord.path(0).asDouble()});
            }
            if (geometry.size() < 2) {
                return null;
            }
            OsrmRoute route = new OsrmRoute(meters / 1000.0, geometry);
            cache.put(key, route);
            return route;
        } catch (RestClientException | java.io.IOException e) {
            log.warn("OSRM lookup failed ({}), falling back to straight line: {}", key, e.getMessage());
            return null;
        }
    }

    private static String trim(double v) {
        return String.format("%.6f", v);
    }

    private static class OsrmRoute {
        private final double distanceKm;
        private final List<double[]> geometry;

        OsrmRoute(double distanceKm, List<double[]> geometry) {
            this.distanceKm = distanceKm;
            this.geometry = geometry;
        }
    }
}
