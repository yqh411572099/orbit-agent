package com.butler.application.tool;

import com.butler.domain.agent.AgentTool;
import com.butler.domain.agent.ToolContext;
import com.butler.domain.service.PoiSearchPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 周边 POI 检索：模型用自然语言描述要找什么（如“妇产医院”），
 * 坐标从子对话定位（context）获取，不需要模型传经纬度。
 */
@Component
public class NearbyPoiTool implements AgentTool {

    private static final int RADIUS = 12000;
    private static final int LIMIT = 10;
    private final PoiSearchPort poiSearchPort;
    private final com.butler.domain.service.GeocodePort geocodePort;
    private final ObjectMapper mapper = new ObjectMapper();

    public NearbyPoiTool(PoiSearchPort poiSearchPort, com.butler.domain.service.GeocodePort geocodePort) {
        this.poiSearchPort = poiSearchPort;
        this.geocodePort = geocodePort;
    }

    @Override
    public String name() { return "nearby_search"; }

    @Override
    public String description() { return "在某个地点周边检索真实 POI（医院、卫生服务中心、公交站、考点、机构等），返回名称、距离、地址；可指定参照地点，不指定则用用户当前定位"; }

    @Override
    public String parametersSchema() {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("query").put("type", "string")
                .put("description", "要找的地点类型或关键词，尽量简短，如：医院、社区卫生服务中心、公交站、考点。不要传整句话。");
        props.putObject("location").put("type", "string")
                .put("description", "可选。检索的参照地点或地标（如“北京西站”“杭州市余杭区中泰街道”）；省略时默认以用户当前定位为中心。当用户说“某地附近/在某地周边找”时必须传该地点。");
        root.putArray("required").add("query");
        return root.toString();
    }

    @Override
    public String execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = mapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String raw = args.path("query").asText("");
            if (raw == null || raw.isBlank()) return "请说明要找哪类地点。";
            String location = args.path("location").asText("");

            // 确定检索中心：若指定了参照地点，先解析其坐标；否则用用户当前定位
            Double lat; Double lon; String centerLabel;
            if (location != null && !location.isBlank()) {
                var place = geocodePort.forward(location);
                if (place == null || place.latitude() == null || place.longitude() == null) {
                    return "无法在地图上定位到“" + location + "”，请确认地点名称或换个更明确的地名。";
                }
                lat = place.latitude(); lon = place.longitude();
                centerLabel = place.label();
            } else {
                lat = parseDouble(context.collected("latitude"));
                lon = parseDouble(context.collected("longitude"));
                if (lat == null || lon == null) {
                    return "尚未获取用户精确定位。可让用户点击页面顶部的“定位”按钮，或在问题里说明要以哪个地点为中心。";
                }
                centerLabel = "用户当前定位";
            }
            // query 由模型按工具描述给出简短关键词（如“妇产医院”“公交站”），这里直接使用，不在后端做场景化改写。
            String query = raw.replaceAll("[/、，,;；|]+", " ").trim();
            List<PoiSearchPort.Poi> pois = poiSearchPort.searchNearby(lat, lon, RADIUS, query,
                    java.util.List.of());
            if (pois.isEmpty()) {
                return "以“" + centerLabel + "”为中心" + (RADIUS / 1000) + "公里内未检索到“" + raw + "”相关地点；可扩大范围或在地图应用中核实，不要编造。";
            }
            StringBuilder sb = new StringBuilder("以“" + centerLabel + "”为中心，检索到以下真实地点（按距离由近到远）：\n");
            int i = 1;
            for (PoiSearchPort.Poi p : pois.stream().limit(LIMIT).toList()) {
                sb.append(i++).append(". ").append(p.name());
                sb.append("｜距定位约").append(Math.round(p.distanceMeters() / 100.0) / 10.0).append("公里");
                if (p.address() != null && !p.address().isBlank()) sb.append("｜").append(p.address());
                sb.append("\n");
            }
            sb.append("数据来自地图服务，可能不全，请以实际地图/电话核实。");
            return sb.toString();
        } catch (Exception e) {
            return "周边检索失败：" + e.getMessage();
        }
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException e) { return null; }
    }
}
