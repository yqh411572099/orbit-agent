package com.butler.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.butler.application.tool.GeocodeTool;
import com.butler.application.tool.NearbyPoiTool;
import com.butler.domain.agent.ToolContext;
import com.butler.domain.service.GeocodePort;
import com.butler.domain.service.PoiSearchPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 地理类工具：不连网络，用桩端口验证参数解析、坐标来源与兜底文案。 */
class GeoToolsTest {

    private ToolContext ctx(Map<String, String> collected) {
        return new ToolContext(1L, "sub", 99L, "pregnancy", collected, LocalDate.now());
    }

    @Test
    void geocodeRequiresAddress() {
        GeocodePort port = mock(GeocodePort.class);
        GeocodeTool tool = new GeocodeTool(port);
        String out = assertDoesNotThrow(() -> tool.execute("{\"address\":\"\"}", ctx(Map.of())));
        assertTrue(out.contains("请给出"));
        verify(port, never()).forward(anyString());
    }

    @Test
    void geocodeUsesCityHintAndReturnsDistrict() throws Exception {
        GeocodePort port = mock(GeocodePort.class);
        when(port.forward(anyString())).thenReturn(new GeocodePort.GeoPlace(
                "浙江省", "杭州市", "余杭区", "杭州市余杭区中泰街道新明半岛",
                30.25, 120.02, "中泰街道"));
        GeocodeTool tool = new GeocodeTool(port);

        String out = tool.execute("{\"address\":\"新明半岛小区\",\"cityHint\":\"杭州市余杭区\"}", ctx(Map.of()));

        verify(port).forward("杭州市余杭区新明半岛小区");
        assertTrue(out.contains("余杭区") && out.contains("中泰街道"));
    }

    @Test
    void geocodeNullPlaceGivesFriendlyHint() throws Exception {
        GeocodePort port = mock(GeocodePort.class);
        when(port.forward(anyString())).thenReturn(null);
        GeocodeTool tool = new GeocodeTool(port);
        String out = tool.execute("{\"address\":\"不存在的地方xyz\"}", ctx(Map.of()));
        assertTrue(out.contains("未能解析"));
    }

    @Test
    void nearbyRequiresQuery() {
        PoiSearchPort poi = mock(PoiSearchPort.class);
        GeocodePort geo = mock(GeocodePort.class);
        NearbyPoiTool tool = new NearbyPoiTool(poi, geo);
        String out = assertDoesNotThrow(() -> tool.execute("{\"query\":\"\"}", ctx(Map.of())));
        assertTrue(out.contains("请说明"));
    }

    @Test
    void nearbyUsesExplicitLocationViaGeocode() throws Exception {
        PoiSearchPort poi = mock(PoiSearchPort.class);
        GeocodePort geo = mock(GeocodePort.class);
        when(geo.forward("杭州东站")).thenReturn(new GeocodePort.GeoPlace(
                null, "杭州市", null, "杭州东站", 30.29, 120.21, null));
        when(poi.searchNearby(anyDouble(), anyDouble(), anyInt(), eq("公交站"), anyList()))
                .thenReturn(List.of(new PoiSearchPort.Poi("杭州东站公交站", "bus", "杭州东站旁",
                        30.29, 120.21, 300, "amap")));
        NearbyPoiTool tool = new NearbyPoiTool(poi, geo);

        String out = tool.execute("{\"query\":\"公交站\",\"location\":\"杭州东站\"}", ctx(Map.of()));

        verify(geo).forward("杭州东站");
        assertTrue(out.contains("杭州东站公交站"));
    }

    @Test
    void nearbyFallsBackToUserLocation() throws Exception {
        PoiSearchPort poi = mock(PoiSearchPort.class);
        GeocodePort geo = mock(GeocodePort.class);
        when(poi.searchNearby(anyDouble(), anyDouble(), anyInt(), anyString(), anyList()))
                .thenReturn(List.of());
        NearbyPoiTool tool = new NearbyPoiTool(poi, geo);

        Map<String, String> withLoc = Map.of("latitude", "30.25", "longitude", "120.02");
        String out = tool.execute("{\"query\":\"医院\"}", ctx(withLoc));

        verify(geo, never()).forward(anyString());
        assertTrue(out.contains("未检索到"));
    }

    @Test
    void nearbyWithoutLocationAsksToEnableLocation() {
        PoiSearchPort poi = mock(PoiSearchPort.class);
        GeocodePort geo = mock(GeocodePort.class);
        NearbyPoiTool tool = new NearbyPoiTool(poi, geo);
        String out = assertDoesNotThrow(() -> tool.execute("{\"query\":\"医院\"}", ctx(Map.of())));
        assertTrue(out.contains("定位"));
    }
}
