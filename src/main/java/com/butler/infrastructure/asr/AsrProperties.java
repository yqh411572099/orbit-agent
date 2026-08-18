package com.butler.infrastructure.asr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 火山引擎流式语音识别（大模型单流）配置。
 *
 * <p>对应文档：火山方舟 → 语音识别单流 WebSocket 接口（二进制帧协议）。
 * 通过 ASR_API_KEY / ASR_RESOURCE_ID / ASR_WS_URL 等环境变量覆盖。</p>
 */
@Component
@ConfigurationProperties(prefix = "asr")
public class AsrProperties {
    private String wsUrl = "wss://openspeech.bytedance.com/api/v3/plan/sauc/bigmodel_nostream";
    private String resourceId = "volc.seedasr.sauc.duration";
    private String accessKey = System.getenv().getOrDefault("ASR_API_KEY", System.getenv("ARK_API_KEY"));
    private String model = "bigmodel";
    private int sampleRate = 16000;
    private int bits = 16;
    private int channel = 1;
    private int segmentMs = 200;

    public String getWsUrl() { return wsUrl; }
    public void setWsUrl(String wsUrl) { this.wsUrl = wsUrl; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getSampleRate() { return sampleRate; }
    public void setSampleRate(int sampleRate) { this.sampleRate = sampleRate; }
    public int getBits() { return bits; }
    public void setBits(int bits) { this.bits = bits; }
    public int getChannel() { return channel; }
    public void setChannel(int channel) { this.channel = channel; }
    public int getSegmentMs() { return segmentMs; }
    public void setSegmentMs(int segmentMs) { this.segmentMs = segmentMs; }

    public boolean isConfigured() {
        return accessKey != null && !accessKey.isBlank();
    }
}
