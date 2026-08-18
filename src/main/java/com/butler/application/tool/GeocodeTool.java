package com.butler.application.tool;

import com.butler.domain.agent.AgentTool;
import com.butler.domain.agent.ToolContext;
import com.butler.domain.service.GeocodePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * 地址/地点解析：把用户说的文本地址或地标（如“杭州市新明半岛小区”）确定性地解析为
 * 省/市/区/街道 + 经纬度。用于替代“联网搜这个小区属于哪个街道”的猜测式检索。
 */
@Component
public class GeocodeTool implements AgentTool {

    private final GeocodePort geocodePort;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeocodeTool(GeocodePort geocodePort) {
        this.geocodePort = geocodePort;
    }

    @Override public String name() { return "geocode_address"; }

    @Override public String description() {
        return "把文本地址或地标（小区/街道/医院等）解析为确切的省市区街道与经纬度。"
                + "判断“某小区属于哪个街道/社区/行政区划”时优先用本工具，不要靠联网搜索猜测。";
    }

    @Override
    public String parametersSchema() {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        props.putObject("address").put("type", "string")
                .put("description", "要解析的地址或地点名，尽量带上城市，如：杭州市新明半岛小区");
        props.putObject("cityHint").put("type", "string")
                .put("description", "可选，已知的城市/区县提示，如：杭州市余杭区");
        root.putArray("required").add("address");
        return root.toString();
    }

    @Override
    public String execute(String argumentsJson, ToolContext context) {
        try {
            JsonNode args = mapper.readTree(argumentsJson == null ? "{}" : argumentsJson);
            String address = args.path("address").asText("").trim();
            if (address.isBlank()) address = args.path("query").asText("").trim();
            String cityHint = args.path("cityHint").asText("").trim();
            if (address.isBlank()) return "请给出要解析的地址或地点名。";
            String query = cityHint.isBlank() ? address : cityHint + address;
            GeocodePort.GeoPlace place = geocodePort.forward(query);
            if (place == null) {
                return "未能解析该地址：" + address + "。请向用户确认更完整的地址（城市+区县+街道/小区）。";
            }
            StringBuilder sb = new StringBuilder("地址解析结果（来自地图服务，行政区划以此为准）：\n");
            if (place.province() != null) sb.append("- 省/直辖市：").append(place.province()).append("\n");
            if (place.city() != null) sb.append("- 城市：").append(place.city()).append("\n");
            if (place.district() != null) sb.append("- 区/县：").append(place.district()).append("\n");
            if (place.township() != null) sb.append("- 街道/乡镇：").append(place.township()).append("\n");
            if (place.latitude() != null && place.longitude() != null) {
                sb.append("- 坐标：纬度").append(place.latitude()).append("，经度").append(place.longitude()).append("\n");
            }
            sb.append("- 完整地址：").append(place.label()).append("\n");
            sb.append("后续找该地址附近的机构（社区卫生服务中心、建档医院等）时，可直接用上述坐标调用周边检索工具。");
            return sb.toString();
        } catch (Exception e) {
            return "地址解析失败：" + e.getMessage();
        }
    }
}
