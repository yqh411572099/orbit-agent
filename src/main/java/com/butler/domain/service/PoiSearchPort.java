package com.butler.domain.service;

import java.util.List;

/** 周边 POI 检索端口。换地图服务商只换实现。 */
public interface PoiSearchPort {

    /**
     * @param latitude      中心纬度
     * @param longitude     中心经度
     * @param radiusMeters  检索半径（米）
     * @param keyword       名称关键词（如“妇产医院”），可为空
     * @param amenities     OSM amenity 类别（如 hospital/clinic），作为兜底过滤
     */
    List<Poi> searchNearby(double latitude, double longitude, int radiusMeters,
                           String keyword, List<String> amenities);

    record Poi(String name, String category, String address, double latitude, double longitude,
               double distanceMeters, String source) {}
}
