package com.powerqueue.service.impl;

import com.powerqueue.service.MapDistanceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Haversine 大圆弧公式的距离提供者(默认实现,可离线运行)。
 * <p>结果按起终点坐标(精度截断到 4 位,约 11m)缓存到 Redis,避免高频计算,
 * 也避免生产环境高频调用第三方地图 API 产生费用。
 * <p>
 * <b>替换为真实地图距离:</b> 新增一个实现(如 {@code GaodeDistanceProvider}),
 * 调用高德「距离测量」Web API 返回行驶距离,并在该 Bean 标注 {@code @Primary} 即可覆盖默认实现。
 * <pre>
 * // 伪代码示例:
 * String url = String.format(
 *     "https://restapi.amap.com/v3/distance?key=%s&origins=%s,%s&destination=%s,%s&type=1",
 *     apiKey, lng1, lat1, lng2, lat2);
 * // 解析 JSON results[0].distance(米)→ /1000 km
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class HaversineDistanceProvider implements MapDistanceProvider {

    /** 地球半径(km) */
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${powerqueue.recommend.distance-cache-ttl-seconds:600}")
    private long cacheTtlSeconds;

    @Override
    public double distanceKm(double lng1, double lat1, double lng2, double lat2) {
        // 坐标截断到 4 位小数,防止缓存 Key 爆炸
        String key = String.format("distance:%.4f:%.4f:%.4f:%.4f", lng1, lat1, lng2, lat2);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof Number n) {
            return n.doubleValue();
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double km = EARTH_RADIUS_KM * c;

        redisTemplate.opsForValue().set(key, km, cacheTtlSeconds, TimeUnit.SECONDS);
        return km;
    }
}
