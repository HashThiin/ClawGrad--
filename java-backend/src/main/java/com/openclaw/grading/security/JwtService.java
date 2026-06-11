package com.openclaw.grading.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expiresMinutes;

    public JwtService(ObjectMapper objectMapper,
                      @Value("${app.security.jwt.secret}") String secret,
                      @Value("${app.security.jwt.expires-minutes:720}") long expiresMinutes) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expiresMinutes = expiresMinutes;
    }

    public String generateToken(UserPrincipal user) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.getUsername());
        payload.put("uid", user.getId());
        payload.put("role", user.getRole());
        payload.put("iat", now);
        payload.put("exp", now + expiresMinutes * 60);

        String headerPart = encodeJson(header);
        String payloadPart = encodeJson(payload);
        String signingInput = headerPart + "." + payloadPart;
        return signingInput + "." + sign(signingInput);
    }

    public String extractUsername(String token) {
        Map<String, Object> payload = parseAndValidate(token);
        Object subject = payload.get("sub");
        return subject == null ? null : subject.toString();
    }

    public boolean isTokenValid(String token, UserPrincipal user) {
        String username = extractUsername(token);
        return username != null && username.equals(user.getUsername());
    }

    private Map<String, Object> parseAndValidate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("JWT 格式错误");
            }
            String signingInput = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(signingInput).getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("JWT 签名无效");
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(
                    payloadBytes,
                    new TypeReference<Map<String, Object>>() {}
            );
            long exp = ((Number) payload.getOrDefault("exp", 0)).longValue();
            if (exp <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("JWT 已过期");
            }
            return payload;
        } catch (Exception e) {
            throw new IllegalArgumentException("JWT 无效", e);
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 编码失败", e);
        }
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }
}
