package com.powerqueue.service;

/**
 * 地图距离提供者(L1/L4 共用)。
 * <p>默认实现 {@code HaversineDistanceProvider} 用大圆弧距离,可离线运行、便于演示;
 * 生产可替换为接入高德/百度地图 Web API 的实现(返回真实行驶距离),替换 Bean 即可,调用方无感。
 */
public interface MapDistanceProvider {

    /**
     * 两点间距离(km)。
     *
     * @param lng1 起点经度
     * @param lat1 起点纬度
     * @param lng2 终点经度
     * @param lat2 终点纬度
     */
    double distanceKm(double lng1, double lat1, double lng2, double lat2);
}
