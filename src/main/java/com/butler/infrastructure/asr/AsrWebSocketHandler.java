package com.butler.infrastructure.asr;

import com.butler.infrastructure.auth.SessionTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * 浏览器 ↔ 火山流式语音识别 的 WebSocket 代理（二进制协议）。
 *
 * <p>浏览器侧：连 {@code /ws/asr?token=xxx}，发送 16k/16bit/mono PCM，结束发 {"action":"stop"}。
 * 服务端返回 JSON：{type:"ready"|"result"|"end"|"error", text, final}。</p>
 */
@Component
public class AsrWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AsrWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AsrProperties props;
    private final SessionTokenService sessionTokenService;
    private final StandardWebSocketClient client = new StandardWebSocketClient();

    public AsrWebSocketHandler(AsrProperties props, SessionTokenService sessionTokenService) {
        this.props = props;
        this.sessionTokenService = sessionTokenService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession browser) throws Exception {
        String query = browser.getUri() == null ? "" : browser.getUri().getQuery();
        String token = queryParam(query, "token");
        SessionTokenService.SessionPayload payload = token == null ? null : sessionTokenService.validate(token);
        if (payload == null) {
            sendError(browser, "未登录或会话已过期");
            browser.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (!props.isConfigured()) {
            sendError(browser, "语音识别未配置（缺少 ASR_API_KEY）");
            browser.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        Bridge bridge = new Bridge(browser, "u" + payload.userId());
        browser.getAttributes().put("bridge", bridge);
        try {
            bridge.connect();
        } catch (Exception e) {
            log.warn("ASR upstream connect failed: {}", e.getMessage());
            sendError(browser, "连接语音识别服务失败：" + e.getMessage());
            browser.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession browser, BinaryMessage message) {
        Bridge bridge = (Bridge) browser.getAttributes().get("bridge");
        if (bridge != null) bridge.feedPcm(message.getPayload());
    }

    @Override
    protected void handleTextMessage(WebSocketSession browser, TextMessage message) throws Exception {
        Bridge bridge = (Bridge) browser.getAttributes().get("bridge");
        if (bridge == null) return;
        try {
            JsonNode node = MAPPER.readTree(message.getPayload());
            if ("stop".equalsIgnoreCase(node.path("action").asText(""))) bridge.finish();
        } catch (Exception ignored) {}
    }

    @Override
    public void afterConnectionClosed(WebSocketSession browser, CloseStatus status) {
        Bridge bridge = (Bridge) browser.getAttributes().get("bridge");
        if (bridge != null) bridge.close();
    }

    private String queryParam(String query, String name) {
        if (query == null || query.isEmpty()) return null;
        for (String pair : query.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0 && pair.substring(0, i).equals(name)) {
                try { return java.net.URLDecoder.decode(pair.substring(i + 1), "UTF-8"); }
                catch (Exception e) { return pair.substring(i + 1); }
            }
        }
        return null;
    }

    static void sendError(WebSocketSession s, String msg) {
        send(s, Map.of("type", "error", "message", msg == null ? "" : msg));
    }

    static void send(WebSocketSession s, Object payload) {
        try {
            if (s.isOpen()) {
                synchronized (s) {
                    s.sendMessage(new TextMessage(MAPPER.writeValueAsString(payload)));
                }
            }
        } catch (IOException e) {
            log.debug("ws send failed: {}", e.getMessage());
        }
    }

    /** 与上游火山 ASR 的连接，负责协议帧封装与 PCM 分段。 */
    private class Bridge extends AbstractWebSocketHandler {
        private final WebSocketSession browser;
        private final String uid;
        private volatile WebSocketSession upstream;
        private volatile boolean finished = false;
        private int seq = 1;
        private final ByteArrayOutputStream pcmBuffer = new ByteArrayOutputStream();
        private final int segmentBytes;
        private final CountDownLatch sessionAck = new CountDownLatch(1);
        private volatile boolean sessionReady = false;
        private volatile String setupError;

        Bridge(WebSocketSession browser, String uid) {
            this.browser = browser;
            this.uid = uid;
            // sampleRate * bytesPerFrame(2) * channel * segmentMs/1000
            this.segmentBytes = props.getSampleRate() * (props.getBits() / 8) * props.getChannel()
                    * props.getSegmentMs() / 1000;
        }

        void connect() throws Exception {
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.add("X-Api-Key", props.getAccessKey());
            headers.add("X-Api-Resource-Id", props.getResourceId());
            String reqId = UUID.randomUUID().toString();
            headers.add("X-Api-Request-Id", reqId);
            headers.add("X-Api-Connect-Id", reqId);
            headers.add("X-Api-Sequence", "-1");
            upstream = client.execute(this, headers, URI.create(props.getWsUrl()))
                    .get(10, TimeUnit.SECONDS);
            sendFullRequest();
            // 等待服务端对 full request 的 ACK（建立会话）后再让浏览器推流，
            // 否则音频帧会先到达而报 Load sess: missing session。
            if (!sessionAck.await(5, TimeUnit.SECONDS) || !sessionReady) {
                throw new IOException(setupError != null ? setupError : "语音识别会话建立超时");
            }
            AsrWebSocketHandler.send(browser, Map.of("type", "ready"));
        }

        private void sendFullRequest() throws IOException {
            ObjectNode root = MAPPER.createObjectNode();
            root.putObject("user").put("uid", uid);
            ObjectNode audio = root.putObject("audio");
            audio.put("format", "pcm");
            audio.put("codec", "raw");
            audio.put("rate", props.getSampleRate());
            audio.put("bits", props.getBits());
            audio.put("channel", props.getChannel());
            ObjectNode request = root.putObject("request");
            request.put("model_name", props.getModel());
            request.put("enable_itn", true);
            request.put("enable_punc", true);
            request.put("enable_ddc", true);
            request.put("show_utterances", true);
            request.put("enable_nonstream", true);
            byte[] json = MAPPER.writeValueAsBytes(root);
            ByteBuffer frame = AsrFrameCodec.build(AsrFrameCodec.FULL_REQUEST,
                    AsrFrameCodec.POS_SEQUENCE, seq, AsrFrameCodec.gzip(json));
            upstream.sendMessage(new BinaryMessage(frame));
            seq++;
        }

        void feedPcm(ByteBuffer pcm) {
            if (finished || upstream == null || !upstream.isOpen() || !sessionReady) return;
            try {
                byte[] chunk = toBytes(pcm);
                synchronized (pcmBuffer) {
                    pcmBuffer.write(chunk);
                    while (pcmBuffer.size() >= segmentBytes) {
                        byte[] seg = new byte[segmentBytes];
                        System.arraycopy(pcmBuffer.toByteArray(), 0, seg, 0, segmentBytes);
                        byte[] remain = new byte[pcmBuffer.size() - segmentBytes];
                        System.arraycopy(pcmBuffer.toByteArray(), segmentBytes, remain, 0, remain.length);
                        pcmBuffer.reset();
                        pcmBuffer.write(remain);
                        sendAudio(seg, false);
                    }
                }
            } catch (Exception e) {
                log.debug("feed pcm failed: {}", e.getMessage());
            }
        }

        private void sendAudio(byte[] pcm, boolean last) throws IOException {
            int flags = last ? AsrFrameCodec.NEG_WITH_SEQUENCE : AsrFrameCodec.POS_SEQUENCE;
            int sendSeq = last ? -seq : seq;
            ByteBuffer frame = AsrFrameCodec.build(AsrFrameCodec.AUDIO_ONLY, flags, sendSeq,
                    AsrFrameCodec.gzip(pcm));
            upstream.sendMessage(new BinaryMessage(frame));
            if (!last) seq++;
        }

        void finish() {
            if (finished) return;
            finished = true;
            try {
                byte[] tail;
                synchronized (pcmBuffer) { tail = pcmBuffer.toByteArray(); pcmBuffer.reset(); }
                // 最后一帧携带剩余 PCM，负序号结束
                if (upstream != null && upstream.isOpen()) {
                    if (tail.length > 0) sendAudio(tail, true);
                    else {
                        // 无剩余数据也发一个空的结束帧
                        ByteBuffer frame = AsrFrameCodec.build(AsrFrameCodec.AUDIO_ONLY,
                                AsrFrameCodec.NEG_WITH_SEQUENCE, -seq,
                                AsrFrameCodec.gzip(new byte[0]));
                        upstream.sendMessage(new BinaryMessage(frame));
                    }
                }
            } catch (Exception e) {
                log.debug("finish failed: {}", e.getMessage());
            }
        }

        void close() {
            try { if (upstream != null && upstream.isOpen()) upstream.close(); } catch (Exception ignored) {}
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
            AsrFrameCodec.Parsed parsed = AsrFrameCodec.parse(toBytes(message.getPayload()));
            if (parsed.messageType() == AsrFrameCodec.ERROR_RESPONSE) {
                String err = parsed.json();
                if (!sessionReady) { sessionReady = false; sessionAck.countDown(); }
                setupError = "识别错误(" + parsed.code() + ")：" + err;
                AsrWebSocketHandler.sendError(browser, "识别错误(" + parsed.code() + ")：" + err);
                return;
            }
            // 首个响应为服务端对 full request 的确认，标志会话建立
            if (!sessionReady && parsed.messageType() == AsrFrameCodec.FULL_RESPONSE) {
                sessionReady = true;
                sessionAck.countDown();
                log.info("ASR session ready: {}", parsed.json());
            }
            String text = extractText(parsed.json());
            if (text != null && !text.isBlank()) {
                AsrWebSocketHandler.send(browser, Map.of(
                        "type", "result", "text", text, "final", parsed.last()));
            }
            if (parsed.last()) {
                AsrWebSocketHandler.send(browser, Map.of("type", "end"));
            }
        }

        private String extractText(String json) {
            if (json == null || json.isBlank()) return null;
            try {
                JsonNode node = MAPPER.readTree(json);
                JsonNode result = node.path("result");
                if (result.isMissingNode() || result.isNull()) return null;
                StringBuilder sb = new StringBuilder();
                JsonNode utterances = result.path("utterances");
                if (utterances.isArray() && !utterances.isEmpty()) {
                    for (JsonNode u : utterances) {
                        sb.append(u.path("text").asText(""));
                    }
                } else {
                    sb.append(result.path("text").asText(""));
                }
                return sb.toString();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.warn("ASR upstream error: {}", exception.getMessage());
            if (!sessionReady) { sessionReady = false; sessionAck.countDown(); }
            AsrWebSocketHandler.sendError(browser, "识别连接异常：" + exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            if (!sessionReady) { sessionReady = false; sessionAck.countDown(); }
            AsrWebSocketHandler.send(browser, Map.of("type", "end"));
        }
    }

    private static byte[] toBytes(ByteBuffer buf) {
        ByteBuffer dup = buf.duplicate();
        byte[] bytes = new byte[dup.remaining()];
        dup.get(bytes);
        return bytes;
    }
}
