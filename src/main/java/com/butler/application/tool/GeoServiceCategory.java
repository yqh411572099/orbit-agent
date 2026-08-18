package com.butler.application.tool;

import com.butler.domain.agent.ToolCategory;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 地理/位置类工具：周边 POI 检索与地址解析。
 *
 * <p>不在后端用关键词判断该用哪个子工具：子工具的 description 已说明各自适用场景，
 * 模型根据用户问题自行选择 sub_tool；模型未指定时默认走周边检索（最常用）。</p>
 */
@Component
public class GeoServiceCategory extends ToolCategory {
    public GeoServiceCategory(NearbyPoiTool nearbyPoiTool, GeocodeTool geocodeTool) {
        super("GeoService",
                "高德地图服务：获取精准、实时的真实地点信息。涉及地理位置的问题优先使用本工具，不要先用联网搜索猜测。",
                List.of(nearbyPoiTool, geocodeTool));
    }
}
