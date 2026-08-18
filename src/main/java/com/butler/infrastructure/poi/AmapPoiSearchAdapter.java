package com.butler.infrastructure.poi;

import com.butler.domain.service.PoiSearchPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 高德 Web 服务 API 周边 POI 检索。
 * 浏览器定位为 WGS-84(GPS)，高德使用 GCJ-02，先做坐标转换再 around 搜索，距离更准确。
 * 任何网络/配额/鉴权失败都安全降级为空结果（由上层回退到其他数据源）。
 */
public class AmapPoiSearchAdapter implements PoiSearchPort {

    private static final Logger log = LoggerFactory.getLogger(AmapPoiSearchAdapter.class);
    private static final String CONVERT = "https://restapi.amap.com/v3/assistant/coordinate/convert";
    private static final String AROUND = "https://restapi.amap.com/v3/place/around";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final ObjectMapper mapper;
    private final String key;

    public AmapPoiSearchAdapter(ObjectMapper mapper, String key) {
        this.mapper = mapper;
        this.key = key;
    }

    @Override
    public List<Poi> searchNearby(double latitude, double longitude, int radiusMeters,
                                  String keyword, List<String> amenities) {
        if (key == null || key.isBlank()) return List.of();
        int radius = radiusMeters <= 0 ? 8000 : Math.min(radiusMeters, 50000);
        try {
            // WGS-84 -> GCJ-02
            String gcj = convertGpsToGcj(longitude, latitude);
            String[] ll = gcj.split(",");
            double glon = Double.parseDouble(ll[0]);
            double glat = Double.parseDouble(ll[1]);

            String[] terms = keyword == null || keyword.isBlank()
                    ? new String[]{""} : keyword.trim().split("\\s+");
            List<Poi> all = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String term : terms) {
                String url = AROUND + "?key=" + key + "&location=" + glon + "," + glat
                        + "&citylimit=false&radius=" + radius + "&sortrule=distance&offset=15&page=1&extensions=base"
                        + (term.isBlank() ? "" : "&keywords=" + URLEncoder.encode(term, StandardCharsets.UTF_8));
                JsonNode root = getJson(url);
                if (!"1".equals(root.path("status").asText())) {
                    log.debug("高德周边检索失败 term={} info={} infocode={}", term,
                            root.path("info").asText(), root.path("infocode").asText());
                    continue;
                }
                for (JsonNode p : root.path("pois")) {
                    String name = text(p, "name");
                    if (name == null) continue;
                    String base = canonicalHospital(name);
                    if (!seen.add(base)) continue;
                    String[] loc = text(p, "location").split(",");
                    double plat = loc.length > 1 ? Double.parseDouble(loc[1]) : glat;
                    double plon = loc.length > 0 ? Double.parseDouble(loc[0]) : glon;
                    double dist = parseDouble(text(p, "distance"),
                            distanceMeters(latitude, longitude, plat, plon));
                    all.add(new Poi(name, text(p, "type"), text(p, "address"),
                            plat, plon, Math.round(dist), "amap"));
                }
            }
            all.sort(java.util.Comparator.comparingDouble(Poi::distanceMeters));
            return all.size() > 15 ? all.subList(0, 15) : all;
        } catch (Exception e) {
            log.warn("高德周边检索失败 lat={} lon={} err={}", latitude, longitude, e.getMessage());
            return List.of();
        }
    }

    /** 归并同一医院的不同科室/楼栋：取到“医院/保健院/中心”为止，并保留东院/西院等院区后缀。 */
    private String canonicalHospital(String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(".*?(医院(?:东院|西院|南院|北院|本部)?|保健院(?:东院|西院|南院|北院|本部)?|妇幼保健计划生育服务中心|中心)")
                .matcher(name);
        return m.find() ? m.group() : name;
    }

    private String convertGpsToGcj(double longitude, double latitude) throws Exception {
        String url = CONVERT + "?key=" + key + "&coordsys=gps&output=json&locations="
                + longitude + "," + latitude;
        JsonNode root = getJson(url);
        String locations = root.path("locations").asText("");
        if (!"1".equals(root.path("status").asText()) || locations.isBlank()) {
            return longitude + "," + latitude;
        }
        return locations;
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "ButlerLifeAssistant/1.0")
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return mapper.readTree(resp.body());
    }

    private String text(JsonNode n, String k) {
        JsonNode v = n.get(k);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return (s == null || s.isBlank() || "[]".equals(s)) ? null : s;
    }

    private double parseDouble(String s, double fallback) {
        try { return s == null ? fallback : Double.parseDouble(s); } catch (Exception e) { return fallback; }
    }

    private double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
