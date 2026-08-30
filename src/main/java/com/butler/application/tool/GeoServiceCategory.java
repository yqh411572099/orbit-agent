package com.butler.application.tool;

import com.butler.domain.agent.ToolCategory;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 地图/地理检索工具大类：周边 POI 检索与地址解析。
 *
 * <p>不在后端用关键词判断该用哪个子工具：子工具的 description 已说明各自适用场景，
 * 模型根据用户问题自行选择 sub_tool；模型未指定时默认走周边检索（最常用）。</p>
 * 名字与描述面向“地图/地理检索”这一能力，不绑定具体地图厂商（底层可切换数据源）。</p>
 */
@Component
public class GeoServiceCategory extends ToolCategory {
    public GeoServiceCategory(NearbyPoiTool nearbyPoiTool, GeocodeTool geocodeTool) {
        super("GeoSearch",
                "地图/地理检索：获取精准、实时的真实地点信息——周边 POI（医院、考点、站点、机构等）、地址解析、"
                        + "地点归属的区划/街道、坐标、距离。凡涉及具体地理位置的问题优先用本工具拿到权威结果，不要先用联网搜索猜测。",
                List.of(nearbyPoiTool, geocodeTool));
    }
}
