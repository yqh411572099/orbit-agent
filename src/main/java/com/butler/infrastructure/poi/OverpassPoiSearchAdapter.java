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
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 OpenStreetMap Overpass API 的周边 POI 检索，免费、无需密钥。
 * 多个镜像轮询 + 失败重试；任何异常安全降级为空结果，绝不阻断对话。
 */
public class OverpassPoiSearchAdapter implements PoiSearchPort {

    private static final Logger log = LoggerFactory.getLogger(OverpassPoiSearchAdapter.class);
    private static final List<String> ENDPOINTS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper;

    public OverpassPoiSearchAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Poi> searchNearby(double latitude, double longitude, int radiusMeters,
                                  String keyword, List<String> amenities) {
        int baseRadius = radiusMeters <= 0 ? 8000 : Math.min(radiusMeters, 50000);
        for (int radius : new int[]{baseRadius, Math.min(baseRadius * 2, 25000), Math.min(baseRadius * 4, 50000)}) {
            List<Poi> pois = queryOnce(latitude, longitude, radius, keyword, amenities);
            if (!pois.isEmpty()) return pois;
        }
        return List.of();
    }

    private HttpResponse<String> post(String endpoint, String query) throws Exception {
        String form = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .header("User-Agent", "ButlerLifeAssistant/1.0")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private List<Poi> queryOnce(double latitude, double longitude, int radius,
                                String keyword, List<String> amenities) {
        String query = buildQuery(latitude, longitude, radius, keyword, amenities);
        for (String endpoint : ENDPOINTS) {
            try {
                HttpResponse<String> resp = post(endpoint, query);
                if (resp.statusCode() / 100 != 2 || !resp.body().trim().startsWith("{")) {
                    log.debug("Overpass 返回非 JSON status={} body={}", resp.statusCode(),
                            resp.body().length() > 120 ? resp.body().substring(0, 120) : resp.body());
                    continue;
                }
                List<Poi> pois = parse(resp.body(), latitude, longitude, endpoint);
                if (!pois.isEmpty()) return pois;
            } catch (Exception e) {
                log.debug("Overpass 检索失败 endpoint={} err={}", endpoint, e.getMessage());
            }
        }
        return List.of();
    }

    private String buildQuery(double lat, double lon, int radius, String keyword, List<String> amenities) {
        StringBuilder sb = new StringBuilder("[out:json][timeout:15];(");
        // 按产科/妇科专科标签命中（不依赖名称）
        sb.append("nwr[\"healthcare\"~\"obstetrics|gynaecology|midwife|birth_center\"](around:")
                .append(radius).append(',').append(lat).append(',').append(lon).append(");");
        sb.append("nwr[\"healthcare:speciality\"~\"obstetrics|gynaecology|maternity\"](around:")
                .append(radius).append(',').append(lat).append(',').append(lon).append(");");
        appendAmenity(sb, lat, lon, radius, keyword);
        if (amenities != null) {
            for (String a : amenities) {
                if (a == null || a.isBlank()) continue;
                sb.append("nwr[\"amenity\"=\"").append(a).append("\"](around:")
                        .append(radius).append(',').append(lat).append(',').append(lon).append(");");
            }
        }
        sb.append(");out center 40;");
        return sb.toString();
    }

    private void appendAmenity(StringBuilder sb, double lat, double lon, int radius, String keyword) {
        if (keyword == null || keyword.isBlank()) return;
        for (String tag : new String[]{"hospital", "clinic"}) {
            for (String kw : keyword.split("\\s+")) {
                if (kw.isBlank()) continue;
                sb.append("nwr[\"amenity\"=\"").append(tag).append("\"][\"name\"~\"")
                        .append(kw.replace("\"", "")).append("\",i](around:")
                        .append(radius).append(',').append(lat).append(',').append(lon).append(");");
            }
        }
    }

    private List<Poi> parse(String body, double lat, double lon, String source) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode elements = root.path("elements");
        if (!elements.isArray()) return List.of();
        List<Poi> pois = new ArrayList<>();
        for (JsonNode el : elements) {
            JsonNode tags = el.path("tags");
            if (tags.isMissingNode()) continue;
            String name = text(tags, "name");
            if (name == null) continue;
            double elat = el.has("lat") ? el.get("lat").asDouble() : el.path("center").path("lat").asDouble(lat);
            double elon = el.has("lon") ? el.get("lon").asDouble() : el.path("center").path("lon").asDouble(lon);
            String category = firstText(tags, "healthcare:speciality", "healthcare", "amenity");
            String address = firstText(tags, "addr:full", "addr:street");
            if (address != null) {
                String hn = text(tags, "addr:housenumber");
                if (hn != null && !address.contains(hn)) address = address + hn;
            }
            double dist = distanceMeters(lat, lon, elat, elon);
            pois.add(new Poi(name, category, address, elat, elon, Math.round(dist), hostOf(source)));
        }
        pois.sort(Comparator.comparingDouble(Poi::distanceMeters));
        return pois;
    }

    private String hostOf(String url) {
        try { return URI.create(url).getHost(); } catch (Exception e) { return "openstreetmap"; }
    }

    private String firstText(JsonNode tags, String... keys) {
        for (String k : keys) {
            String v = text(tags, k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String text(JsonNode tags, String key) {
        JsonNode n = tags.get(key);
        return n == null || n.isNull() ? null : n.asText();
    }

    /** 两点间球面距离（米）。 */
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
