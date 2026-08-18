package com.butler.infrastructure.poi;

import com.butler.domain.service.PoiSearchPort;
import java.util.List;

/** 高德优先，免费 Overpass 兜底；任一数据源返回即采用。 */
public class CompositePoiSearchAdapter implements PoiSearchPort {

    private final List<PoiSearchPort> delegates;

    public CompositePoiSearchAdapter(List<PoiSearchPort> delegates) {
        this.delegates = delegates;
    }

    @Override
    public List<Poi> searchNearby(double latitude, double longitude, int radiusMeters,
                                  String keyword, List<String> amenities) {
        for (PoiSearchPort port : delegates) {
            try {
                List<Poi> pois = port.searchNearby(latitude, longitude, radiusMeters, keyword, amenities);
                if (pois != null && !pois.isEmpty()) return pois;
            } catch (Exception ignored) {
            }
        }
        return List.of();
    }
}
