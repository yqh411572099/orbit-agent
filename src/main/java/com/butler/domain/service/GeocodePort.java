package com.butler.domain.service;

/** 地理编码端口：逆地理解析经纬度为城市/区县。换地理服务只换实现。 */
public interface GeocodePort {

    GeoPlace reverse(double latitude, double longitude);

    /** 正向地理编码：文本地址/地点名 -> 坐标与行政区划（含街道）。解析不到返回 null。 */
    GeoPlace forward(String address);

    /**
     * @param latitude  纬度
     * @param longitude 经度
     * @param township  街道/乡镇
     */
    record GeoPlace(String province, String city, String district, String label,
                    Double latitude, Double longitude, String township) {
        public GeoPlace(String province, String city, String district, String label) {
            this(province, city, district, label, null, null, null);
        }
    }
}
