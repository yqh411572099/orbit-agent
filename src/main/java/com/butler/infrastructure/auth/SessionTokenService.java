package com.butler.infrastructure.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 无状态会话令牌：base64url(JSON payload) + "." + base64url(HMAC-SHA256 签名)。
 * payload 含用户信息与过期时间；服务端只校验签名与 exp，不落库。
 * 默认有效期 24 小时；续期在原 token 未过期时重新签发。
 */
public class SessionTokenService {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final byte[] secret;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public SessionTokenService(String secret, Duration ttl) {
        byte[] key = (secret == null || secret.isBlank() ? "butler-dev-secret-change-me" : secret)
                .getBytes(StandardCharsets.UTF_8);
        this.secret = key;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.ttl = ttl == null ? Duration.ofDays(1) : ttl;
    }

    public record SessionPayload(Long userId, String username, String nickname,
                                 String userType, String userTypeLabel,
                                 Instant issuedAt, Instant expiresAt) {}

    public String issue(SessionPayload p) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        SessionPayload full = new SessionPayload(p.userId(), p.username(), p.nickname(),
                p.userType(), p.userTypeLabel(), now, exp);
        try {
            String payload = base64Url(mapper.writeValueAsBytes(full));
            return payload + "." + sign(payload);
        } catch (Exception e) {
            throw new IllegalStateException("签发会话失败", e);
        }
    }

    public SessionPayload validate(String token) {
        if (token == null || token.isBlank()) return null;
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot >= token.length() - 1) return null;
        String payloadB64 = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        if (!constantTimeEquals(sig, sign(payloadB64))) return null;
        try {
            JsonNode node = mapper.readTree(Base64.getUrlDecoder().decode(payloadB64));
            Instant exp = node.has("expiresAt") ? Instant.parse(node.get("expiresAt").asText()) : null;
            if (exp == null || !Instant.now().isBefore(exp)) return null;
            return new SessionPayload(
                    node.has("userId") ? node.get("userId").asLong() : null,
                    text(node, "username"), text(node, "nickname"),
                    text(node, "userType"), text(node, "userTypeLabel"),
                    node.has("issuedAt") ? Instant.parse(node.get("issuedAt").asText()) : null,
                    exp);
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String payloadB64) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            return base64Url(mac.doFinal(payloadB64.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("签名失败", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int diff = 0;
        for (int i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
        return diff == 0;
    }

    private static String text(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }

    public Duration getTtl() { return ttl; }
}
