package com.butler.infrastructure.geocode;

import com.butler.domain.service.GeocodePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 逆地理解析：优先使用国内可直连、无需密钥的高德网页接口，失败时回退到 Nominatim。
 * 任一网络/解析失败都安全降级为 null，不阻断对话。
 */
public class WebGeocodeAdapter implements GeocodePort {

    private static final Logger log = LoggerFactory.getLogger(WebGeocodeAdapter.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper mapper;
    private final String amapKey;

    public WebGeocodeAdapter(ObjectMapper mapper) {
        this(mapper, null);
    }

    public WebGeocodeAdapter(ObjectMapper mapper, String amapKey) {
        this.mapper = mapper;
        this.amapKey = amapKey == null ? "" : amapKey.trim();
    }

    @Override
    public GeoPlace reverse(double latitude, double longitude) {
        GeoPlace place = amap(latitude, longitude);
        if (place == null) place = nominatim(latitude, longitude);
        return place;
    }

    private GeoPlace amap(double latitude, double longitude) {
        try {
            String url = "https://www.amap.com/service/regeo?longitude=" + longitude + "&latitude=" + latitude;
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://www.amap.com/")
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) return null;
            JsonNode data = mapper.readTree(resp.body()).path("data");
            if (data.isMissingNode()) return null;
            String province = text(data, "province");
            String city = text(data, "city");
            String district = text(data, "district");
            String township = text(data, "township");
            String label = joinCn(province, city, district, township);
            if (label == null || label.isBlank()) return null;
            Double lat = parseCoord(text(data, "lat"));
            Double lon = parseCoord(text(data, "lng"));
            return new GeoPlace(province, city, district, label, lat, lon, township);
        } catch (Exception e) {
            log.debug("高德逆地理失败 lat={} lon={} err={}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    private GeoPlace nominatim(double latitude, double longitude) {
        try {
            String url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat="
                    + latitude + "&lon=" + longitude + "&zoom=14&accept-language=zh-CN";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "ButlerLifeAssistant/1.0 (self-hosted)")
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) return null;
            JsonNode a = mapper.readTree(resp.body()).path("address");
            if (a.isMissingNode()) return null;
            String province = text(a, "state");
            String city = firstNonBlank(a, "city", "town", "county", "state");
            String district = firstNonBlank(a, "city_district", "district", "suburb", "county");
            if (district != null && district.equals(city)) district = null;
            String label = joinCn(province, city, district, null);
            if (label == null || label.isBlank()) return null;
            Double lat = null, lon = null;
            try { lat = parseCoord(a.path("lat").asText(null)); lon = parseCoord(a.path("lon").asText(null)); } catch (Exception ignored) {}
            return new GeoPlace(province, city, district, label, lat, lon, null);
        } catch (Exception e) {
            log.debug("Nominatim逆地理失败 lat={} lon={} err={}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    private String firstNonBlank(JsonNode a, String... keys) {
        for (String k : keys) {
            String v = text(a, k);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String text(JsonNode a, String key) {
        JsonNode n = a.get(key);
        if (n == null || n.isNull()) return null;
        String v = n.asText();
        return (v == null || v.isBlank() || "[]".equals(v)) ? null : v;
    }

    /** 拼接中文地址，直辖市的省/市不重复。 */
    private String joinCn(String province, String city, String district, String township) {
        StringBuilder sb = new StringBuilder();
        if (province != null && !province.isBlank()) sb.append(province);
        if (city != null && !city.isBlank() && !city.equals(province)) sb.append(city);
        if (district != null && !district.isBlank()
                && !district.equals(city) && !district.equals(province)) sb.append(district);
        if (township != null && !township.isBlank()) sb.append(township);
        return sb.toString();
    }

    private Double parseCoord(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    @Override
    public GeoPlace forward(String address) {
        if (address == null || address.isBlank()) return null;
        if (!amapKey.isBlank()) {
            GeoPlace p = amapForward(address);
            if (p != null) return p;
        }
        return nominatimForward(address);
    }

    private GeoPlace amapForward(String address) {
        try {
            String url = "https://restapi.amap.com/v3/geocode/geo?key=" + amapKey
                    + "&address=" + java.net.URLEncoder.encode(address, java.nio.charset.StandardCharsets.UTF_8)
                    + "&extensions=base";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) return null;
            JsonNode geocodes = mapper.readTree(resp.body()).path("geocodes");
            if (!geocodes.isArray() || geocodes.isEmpty()) return null;
            JsonNode g = geocodes.get(0);
            String location = text(g, "location");
            if (location == null || !location.contains(",")) return null;
            String[] ll = location.split(",");
            double lon = Double.parseDouble(ll[0]);
            double lat = Double.parseDouble(ll[1]);
            String province = text(g, "province");
            String city = text(g, "city");
            String district = text(g, "district");
            String township = text(g, "township");
            if (township == null || township.isBlank()) {
                // geo 接口不直接给街道，用 restapi 逆地理（带 key）补齐 township
                township = amapRegeoTownship(lon, lat);
            }
            String label = joinCn(province, city, district, township);
            return new GeoPlace(province, city, district, label, lat, lon, township);
        } catch (Exception e) {
            log.debug("高德正向地理编码失败 address={} err={}", address, e.getMessage());
            return null;
        }
    }

    private String amapRegeoTownship(double lon, double lat) {
        try {
            String url = "https://restapi.amap.com/v3/geocode/regeo?key=" + amapKey
                    + "&location=" + lon + "," + lat + "&extensions=base";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) return null;
            JsonNode ac = mapper.readTree(resp.body()).path("regeocode").path("addressComponent");
            String t = text(ac, "township");
            if (t != null && t.isBlank()) t = null;
            if (t != null && t.endsWith("街道")) return t;
            if (t != null) return t;
            return null;
        } catch (Exception e) {
            log.debug("高德restapi逆地理失败 lon={} lat={} err={}", lon, lat, e.getMessage());
            return null;
        }
    }

    private GeoPlace nominatimForward(String address) {
        try {
            String url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&accept-language=zh-CN&q="
                    + java.net.URLEncoder.encode(address, java.nio.charset.StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "ButlerLifeAssistant/1.0 (self-hosted)")
                    .timeout(Duration.ofSeconds(6)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) return null;
            JsonNode arr = mapper.readTree(resp.body());
            if (!arr.isArray() || arr.isEmpty()) return null;
            JsonNode a = arr.get(0);
            double lat = Double.parseDouble(a.path("lat").asText());
            double lon = Double.parseDouble(a.path("lon").asText());
            return reverse(lat, lon);
        } catch (Exception e) {
            log.debug("Nominatim正向地理编码失败 address={} err={}", address, e.getMessage());
            return null;
        }
    }
}
